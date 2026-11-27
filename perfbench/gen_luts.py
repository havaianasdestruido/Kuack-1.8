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
      Two 256-entry float tables, one per runtime's float-lowering semantics:
        JAVA[v]  = (float) Math.pow((double) ((float) v / 255.0F), 2.2D)
        TEAVM[v] = (float) Math.pow((double) v / 255.0D, 2.2D)
      TeaVM 0.9.2 lowers Java float arithmetic as JS doubles (no Math.fround), so
      the same Java source yields different float32 values in Chromium/V8 and on
      HotSpot. Embedding a single table changes mipmap output bytes on one of the
      two runtimes (measured in perfbench/teavm). Two constant arrays preserve both.

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


def f32(x: float) -> float:
    """Round a Python double to the nearest IEEE-754 binary32 value."""
    return struct.unpack("<f", struct.pack("<f", x))[0]


def shortest_float_literal(v: float) -> str:
    """Shortest decimal that Java's float literal parser maps back to ``v``."""
    if v == 0.0:
        return "0.0F"
    for precision in range(1, 10):
        s = format(v, ".%dg" % precision)
        if f32(float(s)) == v:
            return s + "F"
    return repr(v) + "F"


def gamma_values():
    """Return (JAVA, TEAVM) tables; both are exact for their target runtime."""
    java, teavm = [], []
    for i in range(256):
        q = f32(f32(float(i)) / f32(255.0))  # Java float division, rounded to binary32
        java.append(f32(math.pow(q, 2.2)))
        teavm.append(f32(math.pow(i / 255.0, 2.2)))  # TeaVM lowering: JS double division
    return java, teavm


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


JAVA_HEADER = """package net.minecraft.client.renderer.texture;

import net.lax1dude.eaglercraft.v1_8.EagRuntime;
import net.lax1dude.eaglercraft.v1_8.internal.EnumPlatformType;

/**
 * GENERATED FILE - DO NOT EDIT. Regenerate with: python3 perfbench/gen_luts.py
 *
 * Source-embedded sRGB-ish gamma decode tables for the mipmap box filter
 * (net.minecraft.client.renderer.texture.MipmapGammaBlend, called from
 * TextureUtil.generateMipmapData). Values are constants, not computed at runtime;
 * the class initializer only materialises the two constant arrays and picks one.
 *
 *   JAVA[v]  = (float) Math.pow((double) ((float) v / 255.0F), 2.2D)
 *   TEAVM[v] = (float) Math.pow((double) v / 255.0D, 2.2D)
 *
 * Why two tables: TeaVM 0.9.2 emits plain JS doubles for Java float arithmetic
 * (no Math.fround), so the same Java source produces different float32 values in
 * Chromium/V8 and on HotSpot. The pre-change runtime-generated table already had
 * this runtime-dependent behaviour. Embedding one table changes mipmap output
 * bytes on one of the two runtimes (measured: 84/203,072 browser cases with the
 * Java table, 66/4,000,000 JVM cases with the TeaVM table). Two embedded constant
 * arrays, selected once via the engine's existing EagRuntime.getPlatformType(),
 * preserve both exactly. No table entry is ever calculated at runtime.
 * See perfbench/CROSS_RUNTIME.md.
 *
 * Domain/index mapping : v = channel byte 0..255, index = (v & 255), so any int
 *                        (including negatives) is a valid index.
 * Entries/precision    : 2 x 256 binary32 floats = 2048 bytes of array data.
 * Error                : 0 ULP on HotSpot (JAVA) and 0 ULP in V8 (TEAVM); the two
 *                        tables differ on 132/256 entries by <= 1 ULP (max 5.96e-8).
 *                        0 mipmap output bytes changed on either runtime.
 * Boundaries           : v=0 -> 0.0F, v=255 -> 1.0F; monotone; NaN/Inf cannot reach
 *                        the table because inputs are '& 255' byte values.
 * Interpolation        : none; the input domain is finite and exact.
 * Approximation        : none (exact LUTs, one per runtime).
 * Sharing              : two private arrays, one owner class, never copied; the single
 *                        ACTIVE reference is chosen once and only read afterwards.
 * Transpilation        : TeaVM lowers float[] to Float32Array and emits JS array literals;
 *                        dead-code elimination keeps the reachable accessor/table.
 * Regeneration         : python3 perfbench/gen_luts.py ; verify with --check and
 *                        python3 -m unittest discover -s tests -v
 */
public final class GeneratedGammaTables {

\tprivate static final float[] GAMMA_2_2_JAVA = {
"""

JAVA_MID = """\t};

\tprivate static final float[] GAMMA_2_2_TEAVM = {
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

\t/**
\t * Single branch, evaluated once per class initialization. Selecting between two
\t * precomputed constant arrays is not "computing table entries at runtime".
\t */
\tprivate static final float[] ACTIVE = EagRuntime.getPlatformType() == EnumPlatformType.JAVASCRIPT
\t\t\t? GAMMA_2_2_TEAVM : GAMMA_2_2_JAVA;

\t/**
\t * Package-internal stable reference for the hot filter. Callers keep this final reference and
\t * never mutate the shared array; no array copy is made.
\t */
\tstatic float[] activeTable() {
\t\treturn ACTIVE;
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
    return (JAVA_HEADER + emit_array(java) + "\n" + JAVA_MID + emit_array(teavm) + "\n" + JAVA_FOOTER)


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
