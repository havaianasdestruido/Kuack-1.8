#!/usr/bin/env python3
"""Simplify and vertex-cache-optimize Eaglercraft FNAW (.mdl) meshes.

Applies quadric error metric (QEM) edge-collapse decimation with:
  - UV seam & texture-coordinate distortion protection
  - Open boundary loop preservation (for multi-part/skirt seams)
  - Manifold link-condition enforcement & triangle flip/sliver rejection
  - Per-collapse local Hausdorff error guard against the original surface
followed by Tom Forsyth's linear-speed post-transform vertex cache optimization
(32-entry FIFO) and pre-transform vertex buffer remapping.

Usage:
    python3 scripts/optimize_fnaw_meshes.py           # optimize meshes in-place & print report
    python3 scripts/optimize_fnaw_meshes.py --stats   # print current mesh statistics only
"""

import argparse
from collections import Counter, defaultdict
import glob
import heapq
import math
import os
import struct
import sys
import zlib

MAGIC = b"!EAG$mdl"
FOOTER = b"_:>+"


def load_mdl(path):
    with open(path, "rb") as f:
        data = f.read()
    if data[:8] != MAGIC:
        raise ValueError(f"{path}: invalid magic {data[:8]!r}")
    ct = chr(data[8])
    if ct not in ("C", "T"):
        raise ValueError(f"{path}: unsupported mesh type {ct!r}")
    skip_len = struct.unpack(">H", data[9:11])[0]
    meta = data[11 : 11 + skip_len]
    pos = 11 + skip_len
    vcount, icount = struct.unpack(">II", data[pos : pos + 8])
    pos += 8
    stride = 24 if ct == "T" else 16
    vdata = data[pos : pos + vcount * stride]
    pos += vcount * stride
    verts = []
    for i in range(vcount):
        chunk = vdata[i * stride : (i + 1) * stride]
        if ct == "T":
            verts.append(struct.unpack("<fffbbbbff", chunk))
        else:
            verts.append(struct.unpack("<fffbbbb", chunk) + (0.0, 0.0))
    indices = list(struct.unpack(f"<{icount}H", data[pos : pos + icount * 2]))
    footer = data[pos + icount * 2 :]
    if len(footer) < 2:
        raise ValueError(f"{path}: truncated footer {footer!r}")
    return ct, meta, verts, indices, len(data), len(zlib.compress(data, 9))


def encode_mdl(ct, meta, verts, indices):
    out = bytearray()
    out.extend(MAGIC)
    out.append(ord(ct))
    out.extend(struct.pack(">H", len(meta)))
    out.extend(meta)
    out.extend(struct.pack(">II", len(verts), len(indices)))
    for v in verts:
        x, y, z, nx, ny, nz, nw, tu, tv = v
        # Pack float32 rounded values cleanly
        if ct == "T":
            out.extend(struct.pack("<fffbbbbff", x, y, z, nx, ny, nz, nw, tu, tv))
        else:
            out.extend(struct.pack("<fffbbbb", x, y, z, nx, ny, nz, nw))
    out.extend(struct.pack(f"<{len(indices)}H", *indices))
    out.extend(FOOTER)
    return bytes(out)


def verify_eagler_loader(blob):
    """Exact replica of EaglerMeshLoader.reloadMesh binary stream read checks."""
    assert blob[:8] == MAGIC, "magic mismatch"
    ct = chr(blob[8])
    assert ct in ("C", "T"), f"bad CT {ct}"
    has_tex = ct == "T"
    skip = struct.unpack(">H", blob[9:11])[0]
    pos = 11 + skip
    vcount, icount = struct.unpack(">II", blob[pos : pos + 8])
    pos += 8
    assert vcount > 0 and icount > 0 and icount % 3 == 0, "invalid counts"
    byte_icount = icount + (1 if icount % 2 != 0 else 0)
    stride = 24 if has_tex else 16
    ints_of_vertex = vcount * stride // 4
    ints_of_index = byte_icount // 2
    ints_total = ints_of_vertex + ints_of_index
    needed_bytes = ints_total * 4
    assert pos + needed_bytes <= len(blob), (
        f"EOF in EaglerMeshLoader: need {pos + needed_bytes}, got {len(blob)}"
    )
    # Verify all indices in range
    idx_bytes = blob[pos + ints_of_vertex * 4 : pos + ints_of_vertex * 4 + icount * 2]
    idxs = struct.unpack(f"<{icount}H", idx_bytes)
    assert max(idxs) < vcount, f"index out of bounds: {max(idxs)} >= {vcount}"


def quadric_from_plane(a, b, c, d, weight=1.0):
    return (
        weight * a * a,
        weight * a * b,
        weight * a * c,
        weight * a * d,
        weight * b * b,
        weight * b * c,
        weight * b * d,
        weight * c * c,
        weight * c * d,
        weight * d * d,
    )


def add_q(q1, q2):
    return tuple(x + y for x, y in zip(q1, q2))


def eval_q(q, x, y, z):
    q00, q01, q02, q03, q11, q12, q13, q22, q23, q33 = q
    return (
        q00 * x * x
        + 2.0 * q01 * x * y
        + 2.0 * q02 * x * z
        + 2.0 * q03 * x
        + q11 * y * y
        + 2.0 * q12 * y * z
        + 2.0 * q13 * y
        + q22 * z * z
        + 2.0 * q23 * z
        + q33
    )


def solve_q3(q):
    a00, a01, a02, b0, a11, a12, b1, a22, b2, _ = q
    det = (
        a00 * (a11 * a22 - a12 * a12)
        - a01 * (a01 * a22 - a12 * a02)
        + a02 * (a01 * a12 - a11 * a02)
    )
    if abs(det) < 1e-9:
        return None
    inv = 1.0 / det
    c00 = (a11 * a22 - a12 * a12) * inv
    c01 = -(a01 * a22 - a02 * a12) * inv
    c02 = (a01 * a12 - a02 * a11) * inv
    c11 = (a00 * a22 - a02 * a02) * inv
    c12 = -(a00 * a12 - a01 * a02) * inv
    c22 = (a00 * a11 - a01 * a01) * inv
    x = -(c00 * b0 + c01 * b1 + c02 * b2)
    y = -(c01 * b0 + c11 * b1 + c12 * b2)
    z = -(c02 * b0 + c12 * b1 + c22 * b2)
    return (x, y, z)


def pt_tri_dist_sq(p, a, b, c):
    ab = (b[0] - a[0], b[1] - a[1], b[2] - a[2])
    ac = (c[0] - a[0], c[1] - a[1], c[2] - a[2])
    ap = (p[0] - a[0], p[1] - a[1], p[2] - a[2])
    d1 = ab[0] * ap[0] + ab[1] * ap[1] + ab[2] * ap[2]
    d2 = ac[0] * ap[0] + ac[1] * ap[1] + ac[2] * ap[2]
    if d1 <= 0.0 and d2 <= 0.0:
        return ap[0] ** 2 + ap[1] ** 2 + ap[2] ** 2
    bp = (p[0] - b[0], p[1] - b[1], p[2] - b[2])
    d3 = ab[0] * bp[0] + ab[1] * bp[1] + ab[2] * bp[2]
    d4 = ac[0] * bp[0] + ac[1] * bp[1] + ac[2] * bp[2]
    if d3 >= 0.0 and d4 <= d3:
        return bp[0] ** 2 + bp[1] ** 2 + bp[2] ** 2
    vc = d1 * d4 - d3 * d2
    if vc <= 0.0 and d1 >= 0.0 and d3 <= 0.0:
        v = d1 / (d1 - d3)
        proj = (a[0] + v * ab[0], a[1] + v * ab[1], a[2] + v * ab[2])
        return (p[0] - proj[0]) ** 2 + (p[1] - proj[1]) ** 2 + (p[2] - proj[2]) ** 2
    cp = (p[0] - c[0], p[1] - c[1], p[2] - c[2])
    d5 = ab[0] * cp[0] + ab[1] * cp[1] + ab[2] * cp[2]
    d6 = ac[0] * cp[0] + ac[1] * cp[1] + ac[2] * cp[2]
    if d6 >= 0.0 and d5 <= d6:
        return cp[0] ** 2 + cp[1] ** 2 + cp[2] ** 2
    vb = d5 * d2 - d1 * d6
    if vb <= 0.0 and d2 >= 0.0 and d6 <= 0.0:
        w = d2 / (d2 - d6)
        proj = (a[0] + w * ac[0], a[1] + w * ac[1], a[2] + w * ac[2])
        return (p[0] - proj[0]) ** 2 + (p[1] - proj[1]) ** 2 + (p[2] - proj[2]) ** 2
    va = d3 * d6 - d5 * d4
    if va <= 0.0 and (d4 - d3) >= 0.0 and (d5 - d6) >= 0.0:
        w = (d4 - d3) / ((d4 - d3) + (d5 - d6))
        proj = (
            b[0] + w * (c[0] - b[0]),
            b[1] + w * (c[1] - b[1]),
            b[2] + w * (c[2] - b[2]),
        )
        return (p[0] - proj[0]) ** 2 + (p[1] - proj[1]) ** 2 + (p[2] - proj[2]) ** 2
    denom = 1.0 / (va + vb + vc)
    v = vb * denom
    w = vc * denom
    proj = (
        a[0] + ab[0] * v + ac[0] * w,
        a[1] + ab[1] * v + ac[1] * w,
        a[2] + ab[2] * v + ac[2] * w,
    )
    return (p[0] - proj[0]) ** 2 + (p[1] - proj[1]) ** 2 + (p[2] - proj[2]) ** 2


def decimate(verts, indices, ct, target_ratio=0.62, max_err_tol=0.025):
    pos_list = []
    pos_to_id = {}
    vert_to_pos = []
    for v in verts:
        p = (v[0], v[1], v[2])
        if p not in pos_to_id:
            pos_to_id[p] = len(pos_list)
            pos_list.append(p)
        vert_to_pos.append(pos_to_id[p])

    orig_tris = [
        [indices[i], indices[i + 1], indices[i + 2]]
        for i in range(0, len(indices), 3)
    ]
    target_tris = max(12, int(round(len(orig_tris) * target_ratio)))

    P = [list(p) for p in pos_list]
    merged_orig_pts = [[p] for p in pos_list]

    V = [list(v) for v in verts]
    VN = []
    for v in verts:
        nx, ny, nz = v[3], v[4], v[5]
        l = math.sqrt(nx * nx + ny * ny + nz * nz) or 1.0
        VN.append([nx / l, ny / l, nz / l])

    tris = [list(t) for t in orig_tris]
    tri_alive = [True] * len(tris)
    pos_tris = defaultdict(set)
    for ti, (a, b, c) in enumerate(tris):
        for vi in (a, b, c):
            pos_tris[vert_to_pos[vi]].add(ti)

    def tri_plane(ti):
        a, b, c = tris[ti]
        pa, pb, pc = P[vert_to_pos[a]], P[vert_to_pos[b]], P[vert_to_pos[c]]
        abx, aby, abz = pb[0] - pa[0], pb[1] - pa[1], pb[2] - pa[2]
        acx, acy, acz = pc[0] - pa[0], pc[1] - pa[1], pc[2] - pa[2]
        nx = aby * acz - abz * acy
        ny = abz * acx - abx * acz
        nz = abx * acy - aby * acx
        area2 = math.sqrt(nx * nx + ny * ny + nz * nz)
        if area2 < 1e-12:
            return (0.0, 0.0, 0.0, 0.0, 0.0)
        inv = 1.0 / area2
        nx *= inv
        ny *= inv
        nz *= inv
        d = -(nx * pa[0] + ny * pa[1] + nz * pa[2])
        return (nx, ny, nz, d, 0.5 * area2)

    Q = [(0.0,) * 10 for _ in range(len(P))]
    edge_count = Counter()
    for ti in range(len(tris)):
        nx, ny, nz, d, area = tri_plane(ti)
        if area > 0:
            q = quadric_from_plane(nx, ny, nz, d, weight=1.0)
            pts = {vert_to_pos[v] for v in tris[ti]}
            for pid in pts:
                Q[pid] = add_q(Q[pid], q)
        p0, p1, p2 = [vert_to_pos[v] for v in tris[ti]]
        for x, y in ((p0, p1), (p1, p2), (p2, p0)):
            if x != y:
                edge_count[(min(x, y), max(x, y))] += 1

    boundary_pos = set()
    for ti in range(len(tris)):
        pts = [vert_to_pos[v] for v in tris[ti]]
        nx, ny, nz, _, area = tri_plane(ti)
        if area <= 0:
            continue
        for i in range(3):
            p1, p2 = pts[i], pts[(i + 1) % 3]
            e = (min(p1, p2), max(p1, p2))
            if edge_count[e] == 1:
                boundary_pos.add(p1)
                boundary_pos.add(p2)
                ex, ey, ez = (
                    P[p2][0] - P[p1][0],
                    P[p2][1] - P[p1][1],
                    P[p2][2] - P[p1][2],
                )
                el = math.sqrt(ex * ex + ey * ey + ez * ez)
                if el > 1e-9:
                    bx = ny * ez - nz * ey
                    by = nz * ex - nx * ez
                    bz = nx * ey - ny * ex
                    bl = math.sqrt(bx * bx + by * by + bz * bz)
                    if bl > 1e-9:
                        bx /= bl
                        by /= bl
                        bz /= bl
                        bd = -(bx * P[p1][0] + by * P[p1][1] + bz * P[p1][2])
                        bq = quadric_from_plane(bx, by, bz, bd, weight=25.0)
                        Q[p1] = add_q(Q[p1], bq)
                        Q[p2] = add_q(Q[p2], bq)
                    td = -(nx * P[p1][0] + ny * P[p1][1] + nz * P[p1][2])
                    tq = quadric_from_plane(nx, ny, nz, td, weight=25.0)
                    Q[p1] = add_q(Q[p1], tq)
                    Q[p2] = add_q(Q[p2], tq)

    def pos_wedges(pid):
        wedges = set()
        for ti in pos_tris[pid]:
            if not tri_alive[ti]:
                continue
            for vi in tris[ti]:
                if vert_to_pos[vi] == pid:
                    wedges.add(vi)
        return wedges

    def is_seam_pos(pid):
        ws = pos_wedges(pid)
        if len(ws) <= 1:
            return False
        uvs = {(round(V[w][7], 4), round(V[w][8], 4)) for w in ws}
        ns = {
            (round(VN[w][0], 2), round(VN[w][1], 2), round(VN[w][2], 2))
            for w in ws
        }
        return len(uvs) > 1 or len(ns) > 1

    pos_version = [0] * len(P)
    max_err_sq = max_err_tol * max_err_tol

    def evaluate_edge(p1, p2):
        t1 = pos_tris[p1]
        t2 = pos_tris[p2]
        shared = t1 & t2
        if not shared:
            return None

        s1, s2 = is_seam_pos(p1), is_seam_pos(p2)
        b1, b2 = (p1 in boundary_pos), (p2 in boundary_pos)

        if b1 and not b2:
            candidates_t = [0.0]
        elif b2 and not b1:
            candidates_t = [1.0]
        elif b1 and b2:
            if len(shared) != 1:
                return None
            candidates_t = [0.0, 0.5, 1.0]
        elif s1 and not s2:
            candidates_t = [0.0]
        elif s2 and not s1:
            candidates_t = [1.0]
        elif s1 and s2:
            candidates_t = [0.0, 1.0]
        else:
            candidates_t = [0.0, 0.5, 1.0]

        qsum = add_q(Q[p1], Q[p2])
        x1, y1, z1 = P[p1]
        x2, y2, z2 = P[p2]
        best = None

        cand_pts = []
        for t in candidates_t:
            cand_pts.append(
                (x1 + t * (x2 - x1), y1 + t * (y2 - y1), z1 + t * (z2 - z1), t)
            )
        if not (s1 or s2 or b1 or b2):
            opt = solve_q3(qsum)
            if opt is not None:
                ox, oy, oz = opt
                dx, dy, dz = x2 - x1, y2 - y1, z2 - z1
                elen2 = dx * dx + dy * dy + dz * dz
                if elen2 > 1e-12:
                    t_proj = ((ox - x1) * dx + (oy - y1) * dy + (oz - z1) * dz) / elen2
                    if -0.1 <= t_proj <= 1.1:
                        px, py, pz = (
                            x1 + t_proj * dx,
                            y1 + t_proj * dy,
                            z1 + t_proj * dz,
                        )
                        if (ox - px) ** 2 + (oy - py) ** 2 + (oz - pz) ** 2 < 0.04 * elen2:
                            cand_pts.append((ox, oy, oz, max(0.0, min(1.0, t_proj))))

        wedge_map_2to1 = {}
        wedge_map_1to2 = {}
        for ti in shared:
            vs = tris[ti]
            w1 = [v for v in vs if vert_to_pos[v] == p1]
            w2 = [v for v in vs if vert_to_pos[v] == p2]
            if len(w1) == 1 and len(w2) == 1:
                wedge_map_2to1[w2[0]] = w1[0]
                wedge_map_1to2[w1[0]] = w2[0]

        w1_all = pos_wedges(p1)
        w2_all = pos_wedges(p2)

        nb1 = set()
        for ti in t1:
            for v in tris[ti]:
                nb1.add(vert_to_pos[v])
        nb2 = set()
        for ti in t2:
            for v in tris[ti]:
                nb2.add(vert_to_pos[v])
        nb1.discard(p1)
        nb1.discard(p2)
        nb2.discard(p1)
        nb2.discard(p2)
        shared_opp = set()
        for ti in shared:
            for v in tris[ti]:
                vp = vert_to_pos[v]
                if vp != p1 and vp != p2:
                    shared_opp.add(vp)
        if (nb1 & nb2) != shared_opp:
            return None

        rem_tris = sorted(t1 ^ t2)
        if not rem_tris:
            return None

        for cx, cy, cz, t in cand_pts:
            # Pack-round candidate f32 coordinates so checks match exact stored positions
            cx, cy, cz = struct.unpack("<fff", struct.pack("<fff", cx, cy, cz))
            if t > 0.0 and ct == "T":
                if not all(w in wedge_map_1to2 for w in w1_all):
                    continue
            if t < 1.0 and ct == "T":
                if not all(w in wedge_map_2to1 for w in w2_all):
                    continue

            uv_cost = 0.0
            if ct == "T":
                for w2, w1 in wedge_map_2to1.items():
                    du = V[w2][7] - V[w1][7]
                    dv = V[w2][8] - V[w1][8]
                    if (t == 0.0 and s2) or (t == 1.0 and s1):
                        uv_cost += (du * du + dv * dv) * 0.25
                    else:
                        uv_cost += (du * du + dv * dv) * 0.005

            flipped = False
            new_rem_tris_pts = []
            for ti in rem_tris:
                vs = tris[ti]
                pts_old = [P[vert_to_pos[v]] for v in vs]
                pts_new = [
                    (cx, cy, cz)
                    if vert_to_pos[v] in (p1, p2)
                    else tuple(P[vert_to_pos[v]])
                    for v in vs
                ]
                new_rem_tris_pts.append(pts_new)
                ox1, oy1, oz1 = (
                    pts_old[1][0] - pts_old[0][0],
                    pts_old[1][1] - pts_old[0][1],
                    pts_old[1][2] - pts_old[0][2],
                )
                ox2, oy2, oz2 = (
                    pts_old[2][0] - pts_old[0][0],
                    pts_old[2][1] - pts_old[0][1],
                    pts_old[2][2] - pts_old[0][2],
                )
                onx = oy1 * oz2 - oz1 * oy2
                ony = oz1 * ox2 - ox1 * oz2
                onz = ox1 * oy2 - oy1 * ox2
                ol = math.sqrt(onx * onx + ony * ony + onz * onz)

                nx1, ny1, nz1 = (
                    pts_new[1][0] - pts_new[0][0],
                    pts_new[1][1] - pts_new[0][1],
                    pts_new[1][2] - pts_new[0][2],
                )
                nx2, ny2, nz2 = (
                    pts_new[2][0] - pts_new[0][0],
                    pts_new[2][1] - pts_new[0][1],
                    pts_new[2][2] - pts_new[0][2],
                )
                nnx = ny1 * nz2 - nz1 * ny2
                nny = nz1 * nx2 - nx1 * nz2
                nnz = nx1 * ny2 - ny1 * nx2
                nl = math.sqrt(nnx * nnx + nny * nny + nnz * nnz)
                if nl < 1e-9 or ol < 1e-9:
                    flipped = True
                    break
                dot = (onx * nnx + ony * nny + onz * nnz) / (ol * nl)
                if dot < 0.35:
                    flipped = True
                    break
                e0 = (
                    (pts_new[1][0] - pts_new[0][0]) ** 2
                    + (pts_new[1][1] - pts_new[0][1]) ** 2
                    + (pts_new[1][2] - pts_new[0][2]) ** 2
                )
                e1 = (
                    (pts_new[2][0] - pts_new[1][0]) ** 2
                    + (pts_new[2][1] - pts_new[1][1]) ** 2
                    + (pts_new[2][2] - pts_new[1][2]) ** 2
                )
                e2 = (
                    (pts_new[0][0] - pts_new[2][0]) ** 2
                    + (pts_new[0][1] - pts_new[2][1]) ** 2
                    + (pts_new[0][2] - pts_new[2][2]) ** 2
                )
                compactness = (2.0 * math.sqrt(3.0) * nl) / (e0 + e1 + e2 + 1e-12)
                if compactness < 0.04:
                    flipped = True
                    break
            if flipped:
                continue

            too_far = False
            local_max_d2 = 0.0
            for op in merged_orig_pts[p1] + merged_orig_pts[p2]:
                d2 = min(pt_tri_dist_sq(op, ta, tb, tc) for ta, tb, tc in new_rem_tris_pts)
                if d2 > max_err_sq:
                    too_far = True
                    break
                if d2 > local_max_d2:
                    local_max_d2 = d2
            if too_far:
                continue

            q_err = max(0.0, eval_q(qsum, cx, cy, cz))
            elen2 = (x2 - x1) ** 2 + (y2 - y1) ** 2 + (z2 - z1) ** 2
            total_cost = q_err + local_max_d2 * 0.5 + uv_cost + 1e-5 * elen2
            if best is None or total_cost < best[0]:
                best = (total_cost, cx, cy, cz, t, wedge_map_2to1)
        return best

    heap = []
    seq = 0

    def push_edge(a, b):
        nonlocal seq
        if a == b:
            return
        p1, p2 = (a, b) if a < b else (b, a)
        res = evaluate_edge(p1, p2)
        if res is not None:
            seq += 1
            heapq.heappush(
                heap,
                (res[0], p1, p2, seq, pos_version[p1], pos_version[p2], res[1:]),
            )

    seen_edges = set()
    for ti in range(len(tris)):
        pts = [vert_to_pos[v] for v in tris[ti]]
        for i in range(3):
            p1, p2 = min(pts[i], pts[(i + 1) % 3]), max(pts[i], pts[(i + 1) % 3])
            if p1 != p2 and (p1, p2) not in seen_edges:
                seen_edges.add((p1, p2))
                push_edge(p1, p2)

    alive_count = len(tris)
    while heap and alive_count > target_tris:
        cost, p1, p2, _, v1, v2, payload = heapq.heappop(heap)
        if pos_version[p1] != v1 or pos_version[p2] != v2:
            continue
        if not pos_tris[p1] or not pos_tris[p2]:
            continue
        cx, cy, cz, t, wmap21 = payload

        P[p1] = [cx, cy, cz]
        Q[p1] = add_q(Q[p1], Q[p2])
        merged_orig_pts[p1].extend(merged_orig_pts[p2])
        merged_orig_pts[p2] = []
        if p2 in boundary_pos:
            boundary_pos.add(p1)

        merged_w2_to_w1 = {}
        for w2 in sorted(wmap21.keys()):
            w1 = wmap21[w2]
            nx = (1.0 - t) * VN[w1][0] + t * VN[w2][0]
            ny = (1.0 - t) * VN[w1][1] + t * VN[w2][1]
            nz = (1.0 - t) * VN[w1][2] + t * VN[w2][2]
            nl = math.sqrt(nx * nx + ny * ny + nz * nz) or 1.0
            VN[w1] = [nx / nl, ny / nl, nz / nl]
            tu, tv = struct.unpack(
                "<ff",
                struct.pack(
                    "<ff",
                    (1.0 - t) * V[w1][7] + t * V[w2][7],
                    (1.0 - t) * V[w1][8] + t * V[w2][8],
                ),
            )
            V[w1][7] = tu
            V[w1][8] = tv
            V[w1][0], V[w1][1], V[w1][2] = cx, cy, cz
            merged_w2_to_w1[w2] = w1

        for ti in sorted(pos_tris[p1] | pos_tris[p2]):
            if not tri_alive[ti]:
                continue
            vs = tris[ti]
            pts = [vert_to_pos[v] for v in vs]
            if p1 in pts and p2 in pts:
                tri_alive[ti] = False
                alive_count -= 1
                for vp in set(pts):
                    pos_tris[vp].discard(ti)
            else:
                for k in range(3):
                    vi = vs[k]
                    if vert_to_pos[vi] == p2:
                        if vi in merged_w2_to_w1:
                            vs[k] = merged_w2_to_w1[vi]
                        else:
                            vert_to_pos[vi] = p1
                            V[vi][0], V[vi][1], V[vi][2] = cx, cy, cz
                    elif vert_to_pos[vi] == p1:
                        V[vi][0], V[vi][1], V[vi][2] = cx, cy, cz
                pos_tris[p1].add(ti)
        pos_tris[p2].clear()
        pos_version[p1] += 1
        pos_version[p2] += 1

        ring = set()
        for ti in pos_tris[p1]:
            for v in tris[ti]:
                vp = vert_to_pos[v]
                if vp != p1:
                    ring.add(vp)
        ring_edges = set()
        for nb in sorted(ring):
            pos_version[nb] += 1
            ring_edges.add((min(p1, nb), max(p1, nb)))
            for ti in pos_tris[nb]:
                for v in tris[ti]:
                    vp = vert_to_pos[v]
                    if vp != nb:
                        ring_edges.add((min(nb, vp), max(nb, vp)))
        for ea, eb in sorted(ring_edges):
            push_edge(ea, eb)

    used_map = {}
    new_verts = []
    new_indices = []
    for ti, alive in enumerate(tri_alive):
        if not alive:
            continue
        for vi in tris[ti]:
            if vi not in used_map:
                used_map[vi] = len(new_verts)
                nx = max(-127, min(127, int(round(VN[vi][0] * 127.0))))
                ny = max(-127, min(127, int(round(VN[vi][1] * 127.0))))
                nz = max(-127, min(127, int(round(VN[vi][2] * 127.0))))
                if nx == 0 and ny == 0 and nz == 0:
                    ny = 127
                new_verts.append(
                    (V[vi][0], V[vi][1], V[vi][2], nx, ny, nz, 0, V[vi][7], V[vi][8])
                )
            new_indices.append(used_map[vi])
    return new_verts, new_indices


def forsyth_optimize(verts, indices, cache_size=32):
    """Tom Forsyth linear-speed vertex cache optimization + vertex fetch remapping."""
    num_verts = len(verts)
    num_tris = len(indices) // 3
    if num_tris == 0:
        return verts, indices

    def calc_score(cpos, val):
        if val == 0:
            return -1.0
        score = 0.0
        if cpos < 0:
            pass
        elif cpos < 3:
            score = 0.75
        else:
            scaler = 1.0 / (cache_size - 3)
            score = (1.0 - (cpos - 3) * scaler) ** 1.5
        score += 2.0 * (val ** -0.5)
        return score

    tris = [indices[i * 3 : (i + 1) * 3] for i in range(num_tris)]
    vert_tris = [[] for _ in range(num_verts)]
    for ti, (a, b, c) in enumerate(tris):
        vert_tris[a].append(ti)
        vert_tris[b].append(ti)
        vert_tris[c].append(ti)

    valence = [len(vert_tris[v]) for v in range(num_verts)]
    cache_pos = [-1] * num_verts
    v_score = [calc_score(-1, valence[v]) for v in range(num_verts)]
    tri_added = [False] * num_tris
    tri_score = [v_score[a] + v_score[b] + v_score[c] for a, b, c in tris]

    cache = []
    out_tris = []
    best_ti = max(range(num_tris), key=lambda ti: tri_score[ti])

    for _ in range(num_tris):
        if best_ti is None or tri_added[best_ti]:
            best_ti = None
            best_s = -1e9
            for ti in range(num_tris):
                if not tri_added[ti] and tri_score[ti] > best_s:
                    best_s = tri_score[ti]
                    best_ti = ti
        tri_added[best_ti] = True
        a, b, c = tris[best_ti]
        out_tris.append((a, b, c))

        for v in (a, b, c):
            valence[v] -= 1
            if v in cache:
                cache.remove(v)
            cache.insert(0, v)

        if len(cache) > cache_size + 3:
            evicted = cache[cache_size:]
            cache = cache[:cache_size]
        else:
            evicted = []

        dirty_tris = set()
        for idx, v in enumerate(cache):
            cache_pos[v] = idx if idx < cache_size else -1
            ns = calc_score(cache_pos[v], valence[v])
            diff = ns - v_score[v]
            if diff != 0.0:
                v_score[v] = ns
                for ti in vert_tris[v]:
                    if not tri_added[ti]:
                        tri_score[ti] += diff
                        if idx < cache_size:
                            dirty_tris.add(ti)
        for v in evicted:
            if v not in cache:
                cache_pos[v] = -1
                ns = calc_score(-1, valence[v])
                diff = ns - v_score[v]
                if diff != 0.0:
                    v_score[v] = ns
                    for ti in vert_tris[v]:
                        if not tri_added[ti]:
                            tri_score[ti] += diff

        best_ti = None
        best_s = -1e9
        for ti in sorted(dirty_tris):
            if not tri_added[ti] and tri_score[ti] > best_s:
                best_s = tri_score[ti]
                best_ti = ti

    remap = {}
    new_verts = []
    new_indices = []
    for a, b, c in out_tris:
        for v in (a, b, c):
            if v not in remap:
                remap[v] = len(new_verts)
                new_verts.append(verts[v])
            new_indices.append(remap[v])
    return new_verts, new_indices


def simulate_vcache(indices, cache_size=32):
    fifo = []
    misses = 0
    for idx in indices:
        if idx not in fifo:
            misses += 1
            fifo.append(idx)
            if len(fifo) > cache_size:
                fifo.pop(0)
    tris = len(indices) / 3
    return misses, misses / tris if tris else 0.0


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "--stats",
        action="store_true",
        help="Print current mesh metrics without modifying files",
    )
    parser.add_argument(
        "--ratio",
        type=float,
        default=0.70,
        help="Target triangle ratio for textured meshes (default: 0.70)",
    )
    parser.add_argument(
        "--eye-ratio",
        type=float,
        default=0.70,
        help="Target triangle ratio for untextured eye meshes (default: 0.70)",
    )
    parser.add_argument(
        "--max-err",
        type=float,
        default=0.020,
        help="Maximum local geometric surface deviation in model units (default: 0.020)",
    )
    parser.add_argument(
        "--mesh-dir",
        default=os.path.join(
            os.path.dirname(os.path.dirname(os.path.abspath(__file__))),
            "sources",
            "resources",
            "assets",
            "eagler",
            "mesh",
        ),
    )
    args = parser.parse_args()

    paths = sorted(glob.glob(os.path.join(args.mesh_dir, "*.mdl")))
    if not paths:
        print(f"No .mdl files found in {args.mesh_dir}", file=sys.stderr)
        return 1

    if args.stats:
        tot_v = tot_t = tot_b = tot_gz = tot_m = 0
        for p in paths:
            ct, meta, verts, indices, raw_b, gz_b = load_mdl(p)
            misses, acmr = simulate_vcache(indices, 32)
            tot_v += len(verts)
            tot_t += len(indices) // 3
            tot_b += raw_b
            tot_gz += gz_b
            tot_m += misses
            print(
                f"{os.path.basename(p):18s} | CT={ct} | V={len(verts):4d} | T={len(indices)//3:4d} "
                f"| bytes={raw_b:6d} (gz={gz_b:5d}) | shaded={misses:4d} (ACMR={acmr:.3f})"
            )
        print(
            f"{'TOTAL':18s} |      | V={tot_v:4d} | T={tot_t:4d} "
            f"| bytes={tot_b:6d} (gz={tot_gz:5d}) | shaded={tot_m:4d} (ACMR={tot_m/tot_t:.3f})"
        )
        return 0

    tot_v0 = tot_v1 = 0
    tot_t0 = tot_t1 = 0
    tot_b0 = tot_b1 = 0
    tot_gz0 = tot_gz1 = 0
    tot_m0 = tot_m1 = 0

    for p in paths:
        ct, meta, verts, indices, raw_b0, gz_b0 = load_mdl(p)
        m0, acmr0 = simulate_vcache(indices, 32)
        ratio = args.eye_ratio if ct == "C" else args.ratio

        dec_v, dec_i = decimate(verts, indices, ct, target_ratio=ratio, max_err_tol=args.max_err)
        opt_v, opt_i = forsyth_optimize(dec_v, dec_i, cache_size=32)

        blob = encode_mdl(ct, meta, opt_v, opt_i)
        verify_eagler_loader(blob)

        raw_b1 = len(blob)
        gz_b1 = len(zlib.compress(blob, 9))
        m1, acmr1 = simulate_vcache(opt_i, 32)

        orig_pts = list({(v[0], v[1], v[2]) for v in verts})
        new_tris = [
            (opt_v[opt_i[k]][:3], opt_v[opt_i[k + 1]][:3], opt_v[opt_i[k + 2]][:3])
            for k in range(0, len(opt_i), 3)
        ]
        dists = [
            math.sqrt(min(pt_tri_dist_sq(pt, a, b, c) for a, b, c in new_tris))
            for pt in orig_pts
        ]
        rms = math.sqrt(sum(d * d for d in dists) / len(dists))
        max_d = max(dists)

        with open(p, "wb") as f:
            f.write(blob)

        tot_v0 += len(verts)
        tot_v1 += len(opt_v)
        tot_t0 += len(indices) // 3
        tot_t1 += len(opt_i) // 3
        tot_b0 += raw_b0
        tot_b1 += raw_b1
        tot_gz0 += gz_b0
        tot_gz1 += gz_b1
        tot_m0 += m0
        tot_m1 += m1

        print(
            f"{os.path.basename(p):18s} | V {len(verts):4d}->{len(opt_v):4d} "
            f"| T {len(indices)//3:4d}->{len(opt_i)//3:4d} "
            f"| B {raw_b0:5d}->{raw_b1:5d} "
            f"| VS {m0:4d}->{m1:4d} (ACMR {acmr0:.2f}->{acmr1:.2f}) "
            f"| RMS={rms:.4f} max={max_d:.4f}"
        )

    print("-" * 116)
    print(
        f"{'TOTAL':18s} | V {tot_v0:4d}->{tot_v1:4d} ({(tot_v1-tot_v0)/tot_v0*100:+.1f}%) "
        f"| T {tot_t0:4d}->{tot_t1:4d} ({(tot_t1-tot_t0)/tot_t0*100:+.1f}%) "
        f"| B {tot_b0:6d}->{tot_b1:6d} ({(tot_b1-tot_b0)/tot_b0*100:+.1f}%, gz {tot_gz0}->{tot_gz1}) "
        f"| VS {tot_m0:4d}->{tot_m1:4d} ({(tot_m1-tot_m0)/tot_m0*100:+.1f}%)"
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
