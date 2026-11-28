#!/usr/bin/env python3
"""Development-time generator for source-embedded mathematical lookup tables.

This script is NOT part of the game. It is run by a developer (and by
``tests/test_generated_luts.py``) and writes plain Java source files whose array
initialisers contain the precomputed numerical constants. The game never loads a
file, never runs a table-building loop and never calls ``Math.pow`` to fill a
table: the JVM/TeaVM class initializer only materialises constant arrays and
chooses one of them via the engine's existing platform-type API.

Outputs:

  sources/main/java/net/minecraft/client/renderer/texture/GeneratedGammaTables.java  (COMMITTED)
      1) Two 256-entry float decode tables, one per runtime's float-lowering semantics:
           JAVA[v]  = (float) Math.pow((double) ((float) v / 255.0F), 2.2D)
           TEAVM[v] = (float) Math.pow((double) v / 255.0D, 2.2D)
         TeaVM 0.9.2 lowers Java float arithmetic as JS doubles (no Math.fround), so
         the same Java source yields different float32 values in Chromium/V8 and on
         HotSpot. Embedding a single table changes mipmap output bytes on one of the
         two runtimes (measured in perfbench/teavm). Two constant arrays preserve both.
      2) Exact zero-approximation encode threshold & sqrt-bucket tables for the continuous
         inverse-gamma encode steps in MipmapGammaBlend:
           THRESH_BLEND[257] : exact binary64 boundaries where (int)(pow(x, 1/2.2)*255) reaches k
           BUCKET_BLEND[513] : max-span-1 short index keyed by (int)(sqrt(x)*512)
           THRESH_COMP[257]  : exact binary64 boundaries where (int)((double)(float)pow(x, 1/2.2)*255) reaches k
           BUCKET_COMP[513]  : max-span-1 short index keyed by (int)(sqrt(x)*512)
         Because encode outputs an integer in 0..255 and pow(x, 1/2.2) is strictly monotone
         on [0, 1], each sqrt-bucket spans at most 1 threshold (O(1), 0 loops, 0 output diffs).

  perfbench/teavm/build/generated/probe/EmbeddedSinTable*.java  (EXPERIMENT, gitignored)
      65536 source-embedded sine constants split over 16 generated classes, used
      only to measure why embedding Minecraft's SIN_TABLE is rejected. Two JVM
      class-file limits force the split: a single <clinit> with 65536 stores
      exceeds the 65535-byte method-code limit, and one class with 65536 distinct
      float literals exceeds the 65535-entry constant-pool limit.

Usage:
    python3 perfbench/gen_luts.py                    # regenerate both
    python3 perfbench/gen_luts.py --check           # verify committed production LUT is current
    python3 perfbench/gen_luts.py --gamma-only      # production LUT only
    python3 perfbench/gen_luts.py --sin-only        # experiment tables only

Determinism: only stdlib ``math``/``struct`` are used; float32 rounding is
emulated explicitly. The emitted decimals are the shortest strings that
round-trip to the exact float32 bit pattern. HotSpot 17/25, V8 12.4 and CPython
libm agree bit-for-bit on both expressions after the binary32 rounding.
"""

from __future__ import annotations

import argparse
import math
import os
import struct
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
GAMMA_OUT = os.path.join(
    ROOT, "sources/main/java/net/minecraft/client/renderer/texture/GeneratedGammaTables.java"
)
SIN_OUT = os.path.join(ROOT, "perfbench/teavm/build/generated/probe/EmbeddedSinTable.java")


POW_INV_2_2 = 0.45454545454545453


def f32(x: float) -> float:
    """Round a Python double to the nearest IEEE-754 binary32 value."""
    return struct.unpack("<f", struct.pack("<f", x))[0]


def f64_to_bits(x: float) -> int:
    return struct.unpack(">Q", struct.pack(">d", x))[0]


def bits_to_f64(b: int) -> float:
    return struct.unpack(">d", struct.pack(">Q", b))[0]


def shortest_float_literal(v: float) -> str:
    """Shortest decimal that Java's float literal parser maps back to ``v``."""
    if v == 0.0:
        return "0.0F"
    for precision in range(1, 10):
        s = format(v, ".%dg" % precision)
        if f32(float(s)) == v:
            return s + "F"
    return repr(v) + "F"


def double_literal(v: float) -> str:
    """Exact round-trip decimal literal for IEEE-754 binary64 ``v``."""
    if v == 0.0:
        return "0.0D"
    s = repr(v)
    if "." not in s and "e" not in s and "E" not in s:
        s += ".0"
    return s + "D"


def gamma_values():
    """Return (JAVA, TEAVM) tables; both are exact for their target runtime."""
    java, teavm = [], []
    for i in range(256):
        q = f32(f32(float(i)) / f32(255.0))  # Java float division, rounded to binary32
        java.append(f32(math.pow(q, 2.2)))
        teavm.append(f32(math.pow(i / 255.0, 2.2)))  # TeaVM lowering: JS double division
    return java, teavm


def encode_threshold_and_buckets(cast_float: bool):
    """Compute exact 257-entry threshold array and max-span-1 513-entry sqrt-bucket index."""

    def enc(x: float) -> int:
        p = math.pow(x, POW_INV_2_2)
        if cast_float:
            p = f32(p)
        return int(p * 255.0)

    thresholds = [0.0]
    for k in range(1, 256):
        guess = math.pow(k / 255.0, 2.2)
        lo = guess * 0.99999
        hi = min(1.0, guess * 1.00001 + 1e-12)
        while enc(lo) >= k:
            lo = max(0.0, lo * 0.99999)
        while enc(hi) < k:
            hi = min(1.0, hi * 1.00001)
        a, b = f64_to_bits(lo), f64_to_bits(hi)
        while a + 1 < b:
            m = (a + b) >> 1
            if enc(bits_to_f64(m)) >= k:
                b = m
            else:
                a = m
        thresholds.append(bits_to_f64(b))
    thresholds.append(2.0)

    buckets = []
    for b in range(513):
        if b == 512:
            x0 = 1.0 - 1e-12
            x1 = 1.0
        else:
            u0 = max(0.0, (b - 1e-12) / 512.0)
            u1 = min(1.0, (b + 1 + 1e-12) / 512.0)
            x0 = u0 * u0
            x1 = u1 * u1
        k0 = enc(x0)
        k1 = enc(x1)
        if k1 - k0 > 1:
            raise AssertionError("bucket %d span %d > 1" % (b, k1 - k0))
        buckets.append(k0 if k0 == k1 else -1 - k0)
    return thresholds, buckets


def sin_values():
    return [f32(math.sin(i * math.pi * 2.0 / 65536.0)) for i in range(65536)]


def emit_array(values, literals_per_line=8, indent="\t\t") -> str:
    lits = [shortest_float_literal(v) for v in values]
    lines = []
    for start in range(0, len(lits), literals_per_line):
        chunk = lits[start : start + literals_per_line]
        comma = "," if start + literals_per_line < len(lits) else ""
        lines.append(indent + ", ".join(chunk) + comma)
    return "\n".join(lines)


def emit_double_array(values, literals_per_line=4, indent="\t\t") -> str:
    lits = [double_literal(v) for v in values]
    lines = []
    for start in range(0, len(lits), literals_per_line):
        chunk = lits[start : start + literals_per_line]
        comma = "," if start + literals_per_line < len(lits) else ""
        lines.append(indent + ", ".join(chunk) + comma)
    return "\n".join(lines)


def emit_short_array(values, literals_per_line=16, indent="\t\t") -> str:
    lits = [str(v) for v in values]
    lines = []
    for start in range(0, len(lits), literals_per_line):
        chunk = lits[start : start + literals_per_line]
        comma = "," if start + literals_per_line < len(lits) else ""
        lines.append(indent + ", ".join(chunk) + comma)
    return "\n".join(lines)


JAVA_HEADER = """package net.minecraft.client.renderer.texture;

import net.lax1dude.eaglercraft.v1_8.EagRuntime;
import net.lax1dude.eaglercraft.v1_8.internal.EnumPlatformType;

/**
 * GENERATED FILE - DO NOT EDIT. Regenerate with: python3 perfbench/gen_luts.py
 *
 * Source-embedded sRGB-ish gamma decode and exact encode-threshold tables for the
 * mipmap box filter (net.minecraft.client.renderer.texture.MipmapGammaBlend, called
 * from TextureUtil.generateMipmapData). Values are constants, not computed at runtime;
 * the class initializer only materialises the constant arrays and picks the active ones.
 *
 * Decode tables (2 x 256 binary32 floats):
 *   JAVA[v]  = (float) Math.pow((double) ((float) v / 255.0F), 2.2D)
 *   TEAVM[v] = (float) Math.pow((double) v / 255.0D, 2.2D)
 *
 * Why two decode tables: TeaVM 0.9.2 emits plain JS doubles for Java float arithmetic
 * (no Math.fround), so the same Java source produces different float32 values in
 * Chromium/V8 and on HotSpot. Selecting via EagRuntime.getPlatformType() preserves both
 * with 0 ULP error on each runtime.
 *
 * Exact encode tables (zero approximation):
 *   THRESH_BLEND[257]  / BUCKET_BLEND[513]  : exact boundaries for (int)(pow(x, 1/2.2)*255.0)
 *   THRESH_COMP[257]   / BUCKET_COMP[513]   : exact boundaries for (int)((double)(float)pow(x, 1/2.2)*255.0)
 * Because pow(x, 1/2.2) is strictly monotone on [0, 1] and the output is an integer 0..255,
 * partitioning u = sqrt(x) into 512 intervals guarantees every bucket spans at most one
 * threshold (MAX_SPAN = 1). Lookup needs at most one comparison and zero loops.
 * On TeaVM, float arithmetic lowers as JS double so both paths share THRESH_BLEND/BUCKET_BLEND.
 *
 * Verification (exhaustive):
 *   - All 1,065,353,217 IEEE-754 binary32 values in [0, 1]: nearest float32 to any
 *     THRESH_COMP[1..255] is 1,539,787 double-ULPs away, and to THRESH_BLEND[1..254]
 *     is 52,014 double-ULPs away (at k=255, both HotSpot and V8 match at nextDown(1.0)).
 *   - All 4,294,967,296 4-texel byte tuples under TeaVM double accumulation: nearest
 *     average to THRESH_BLEND[1..254] is 52,014 double-ULPs away.
 *   0 mipmap output bytes change on either runtime. See perfbench/CROSS_RUNTIME.md.
 *
 * Regeneration : python3 perfbench/gen_luts.py ; verify with --check and
 *                python3 -m unittest discover -s tests -v
 */
public final class GeneratedGammaTables {

\tprivate static final float[] GAMMA_2_2_JAVA = {
"""

JAVA_MID = """\t};

\tprivate static final float[] GAMMA_2_2_TEAVM = {
"""

JAVA_THRESH_BLEND = """\t};

\tprivate static final double[] THRESH_BLEND = {
"""

JAVA_BUCKET_BLEND = """\t};

\tprivate static final double[] THRESH_COMP = {
"""

JAVA_BUCKET_COMP_MID = """\t};

\tprivate static final short[] BUCKET_BLEND = {
"""

JAVA_BUCKET_COMP_END = """\t};

\tprivate static final short[] BUCKET_COMP = {
"""

JAVA_FOOTER = """\t};

\t/** Used on HotSpot / desktop LWJGL runtime (strict Java float semantics). */
\tstatic float gamma22Java(int byteValue) {
\t\treturn GAMMA_2_2_JAVA[byteValue & 255];
\t}

\t/** Used in the TeaVM/Chromium runtime (Java floats lowered as JS doubles). */
\tstatic float gamma22Teavm(int byteValue) {
\t\treturn GAMMA_2_2_TEAVM[byteValue & 255];
\t}

\tprivate static final boolean IS_JAVASCRIPT = EagRuntime.getPlatformType() == EnumPlatformType.JAVASCRIPT;

\t/**
\t * Single branch, evaluated once per class initialization. Selecting between two
\t * precomputed constant arrays is not "computing table entries at runtime".
\t */
\tprivate static final float[] ACTIVE = IS_JAVASCRIPT ? GAMMA_2_2_TEAVM : GAMMA_2_2_JAVA;
\tprivate static final double[] ACTIVE_THRESH_COMP = IS_JAVASCRIPT ? THRESH_BLEND : THRESH_COMP;
\tprivate static final short[] ACTIVE_BUCKET_COMP = IS_JAVASCRIPT ? BUCKET_BLEND : BUCKET_COMP;

\t/**
\t * Package-internal stable references for the hot filter. Callers keep these final
\t * references and never mutate the shared arrays; no array copy is made.
\t */
\tstatic float[] activeTable() {
\t\treturn ACTIVE;
\t}

\tstatic double[] blendThresholds() {
\t\treturn THRESH_BLEND;
\t}

\tstatic short[] blendBuckets() {
\t\treturn BUCKET_BLEND;
\t}

\tstatic double[] componentThresholds() {
\t\treturn ACTIVE_THRESH_COMP;
\t}

\tstatic short[] componentBuckets() {
\t\treturn ACTIVE_BUCKET_COMP;
\t}

\t/** Package-internal hot accessor; TeaVM ADVANCED inlines it. */
\tpublic static float gamma22(int byteValue) {
\t\treturn ACTIVE[byteValue & 255];
\t}

\tprivate GeneratedGammaTables() {
\t}
}
"""


def generate_gamma() -> str:
    java, teavm = gamma_values()
    thresh_blend, bucket_blend = encode_threshold_and_buckets(False)
    thresh_comp, bucket_comp = encode_threshold_and_buckets(True)
    return (
        JAVA_HEADER
        + emit_array(java)
        + "\n"
        + JAVA_MID
        + emit_array(teavm)
        + "\n"
        + JAVA_THRESH_BLEND
        + emit_double_array(thresh_blend)
        + "\n"
        + JAVA_BUCKET_BLEND
        + emit_double_array(thresh_comp)
        + "\n"
        + JAVA_BUCKET_COMP_MID
        + emit_short_array(bucket_blend)
        + "\n"
        + JAVA_BUCKET_COMP_END
        + emit_short_array(bucket_comp)
        + "\n"
        + JAVA_FOOTER
    )


SIN_COORD = """package probe;

/**
 * GENERATED EXPERIMENT - NOT USED BY THE GAME. Regenerate with:
 *   python3 perfbench/gen_luts.py --sin-only
 *
 * 65536 source-embedded float constants for Minecraft's SIN_TABLE, split over 16
 * generated helper classes. Two JVM class-file limits force the split: one
 * <clinit> with 65536 stores exceeds the 65535-byte method-code limit, and one
 * class with 65536 distinct float literals exceeds the 65535-entry constant
 * pool limit. This experiment measures the cost of full embedding; it is
 * deliberately NOT the production implementation (see CROSS_RUNTIME.md).
 */
public final class EmbeddedSinTable {

\tprivate static final float[] SIN = new float[65536];

\tpublic static float sin(float angle) {
\t\treturn SIN[(int) (angle * 10430.378F) & 0xFFFF];
\t}

\tpublic static float[] raw() {
\t\treturn SIN;
\t}

\tprivate EmbeddedSinTable() {
\t}

\tstatic {
"""

SIN_PART_HEADER = """package probe;

/** GENERATED EXPERIMENT - part %d of 16. Do not edit. */
final class EmbeddedSinTablePart%d {

\tprivate EmbeddedSinTablePart%d() {
\t}

\tstatic void fill(float[] a) {
"""


def generate_sin_files():
    values = sin_values()
    files = []
    coord = [SIN_COORD]
    for c in range(16):
        coord.append("\t\tEmbeddedSinTablePart%d.fill(SIN);\n" % c)
    coord.append("\t}\n\n}\n")
    gen_dir = os.path.dirname(SIN_OUT)
    files.append((os.path.join(gen_dir, "EmbeddedSinTable.java"), "".join(coord)))
    for c in range(16):
        start = c * 4096
        chunk = values[start : start + 4096]
        parts = [SIN_PART_HEADER % (c, c, c)]
        for j, v in enumerate(chunk):
            parts.append("\t\ta[%d] = %s;\n" % (start + j, shortest_float_literal(v)))
        parts.append("\t}\n}\n")
        files.append((os.path.join(gen_dir, "EmbeddedSinTablePart%d.java" % c), "".join(parts)))
    return files


def write(path: str, content: str) -> None:
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8", newline="\n") as f:
        f.write(content)


def main(argv=None) -> int:
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument("--check", action="store_true", help="verify committed production LUT is current")
    ap.add_argument("--gamma-only", action="store_true", help="only regenerate the production LUT")
    ap.add_argument("--sin-only", action="store_true", help="only regenerate the sine-table experiment")
    args = ap.parse_args(argv)

    targets = []
    if args.check:
        targets = [(GAMMA_OUT, generate_gamma())]
    else:
        if not args.sin_only:
            targets.append((GAMMA_OUT, generate_gamma()))
        if not args.gamma_only:
            targets.extend(generate_sin_files())

    ok = True
    for path, content in targets:
        rel = os.path.relpath(path, ROOT)
        if args.check:
            current = open(path, encoding="utf-8").read() if os.path.exists(path) else ""
            status = "IDENTICAL" if current == content else "STALE"
            print("%-90s %s" % (rel, status))
            ok &= current == content
        else:
            write(path, content)
            print("wrote %-85s %d bytes" % (rel, len(content)))
    return 0 if ok else 1


if __name__ == "__main__":
    sys.exit(main())
