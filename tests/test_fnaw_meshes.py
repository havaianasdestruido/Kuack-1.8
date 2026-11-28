"""Integrity and resource-budget tests for the FNAW (.mdl) character meshes.

Validates that all 15 FNAW meshes referenced by `HighPolySkin.java` conform to the binary
format expected by `EaglerMeshLoader.java`, contain valid manifold geometry without degenerate
triangles or out-of-bounds indices, and stay within the reduced low-poly vertex/triangle budget.
"""
from collections import Counter
import os
import struct
import unittest

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
MESH_DIR = os.path.join(ROOT, "sources", "resources", "assets", "eagler", "mesh")

EXPECTED_MESHES = {
    "charles0.mdl": ("T", 1),
    "charles1.mdl": ("T", 2),
    "charles2.mdl": ("C", 2),
    "laxativedude0.mdl": ("T", 1),
    "laxativedude1.mdl": ("T", 1),
    "laxativedude2.mdl": ("T", 1),
    "laxativedude3.mdl": ("C", 2),
    "longarms0.mdl": ("T", 1),
    "longarms1.mdl": ("T", 1),
    "longarms2.mdl": ("C", 2),
    "weirdclimber0.mdl": ("T", 1),
    "weirdclimber1.mdl": ("T", 1),
    "weirdclimber2.mdl": ("C", 2),
    "winston0.mdl": ("T", 2),
    "winston1.mdl": ("C", 2),
}


class FnawMeshTests(unittest.TestCase):
    def test_all_expected_mdl_files_exist(self):
        actual = {f for f in os.listdir(MESH_DIR) if f.endswith(".mdl")}
        self.assertEqual(actual, set(EXPECTED_MESHES.keys()))

    def test_eagler_mesh_loader_binary_contract_and_topology(self):
        total_verts = 0
        total_tris = 0
        total_bytes = 0

        for name, (expected_ct, expected_comps) in sorted(EXPECTED_MESHES.items()):
            path = os.path.join(MESH_DIR, name)
            with open(path, "rb") as f:
                blob = f.read()
            total_bytes += len(blob)

            # Header "!EAG$mdl"
            self.assertEqual(blob[:8], b"!EAG$mdl", name)
            ct = chr(blob[8])
            self.assertEqual(ct, expected_ct, name)

            skip = struct.unpack(">H", blob[9:11])[0]
            pos = 11 + skip
            vcount, icount = struct.unpack(">II", blob[pos : pos + 8])
            pos += 8

            self.assertGreater(vcount, 0, name)
            self.assertGreater(icount, 0, name)
            self.assertEqual(icount % 3, 0, name)

            byte_icount = icount + (1 if icount % 2 != 0 else 0)
            stride = 24 if ct == "T" else 16
            ints_of_vertex = vcount * stride // 4
            ints_of_index = byte_icount // 2
            ints_total = ints_of_vertex + ints_of_index
            # EaglerMeshLoader reads exactly ints_total 32-bit little-endian words
            self.assertLessEqual(pos + ints_total * 4, len(blob), name)

            verts = []
            for i in range(vcount):
                chunk = blob[pos + i * stride : pos + (i + 1) * stride]
                if ct == "T":
                    verts.append(struct.unpack("<fffbbbbff", chunk))
                else:
                    verts.append(struct.unpack("<fffbbbb", chunk))
            idx_start = pos + ints_of_vertex * 4
            indices = struct.unpack(
                f"<{icount}H", blob[idx_start : idx_start + icount * 2]
            )

            self.assertLess(max(indices), vcount, name)

            # Check non-zero normals and non-degenerate manifold triangles
            for v in verts:
                nx, ny, nz, nw = v[3], v[4], v[5], v[6]
                self.assertEqual(nw, 0, name)
                self.assertGreater(nx * nx + ny * ny + nz * nz, 0, name)

            edge_counts = Counter()
            adj = {}
            for i in range(0, icount, 3):
                a, b, c = indices[i], indices[i + 1], indices[i + 2]
                pa, pb, pc = verts[a][:3], verts[b][:3], verts[c][:3]
                self.assertNotEqual(pa, pb, name)
                self.assertNotEqual(pb, pc, name)
                self.assertNotEqual(pa, pc, name)
                for p1, p2 in ((pa, pb), (pb, pc), (pc, pa)):
                    edge_counts[(min(p1, p2), max(p1, p2))] += 1
                    adj.setdefault(p1, set()).add(p2)
                    adj.setdefault(p2, set()).add(p1)

            # No non-manifold edges (>2 incident triangles)
            self.assertTrue(all(cnt <= 2 for cnt in edge_counts.values()), name)

            # Connected component count preserved
            visited = set()
            comps = 0
            for pt in adj:
                if pt not in visited:
                    comps += 1
                    stack = [pt]
                    visited.add(pt)
                    while stack:
                        cur = stack.pop()
                        for nb in adj[cur]:
                            if nb not in visited:
                                visited.add(nb)
                                stack.append(nb)
            self.assertEqual(comps, expected_comps, name)

            total_verts += vcount
            total_tris += icount // 3

        # Original high-poly totals were 3,470 vertices / 4,875 triangles / 112,785 bytes.
        # Ensure models remain lighter on resources (~23% fewer vertices, ~30% fewer tris).
        self.assertLessEqual(total_verts, 2800)
        self.assertLessEqual(total_tris, 3600)
        self.assertLessEqual(total_bytes, 90000)


if __name__ == "__main__":
    unittest.main()
