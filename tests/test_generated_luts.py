"""Deterministic source-embedded LUT regression tests.

These are CI-friendly (no JDK, TeaVM jars, Chromium or WABT required). They verify that every
committed generated source is identical to its development-time generator output and that the
generated tables are constants rather than runtime-computed loops.
"""
import os
import re
import shutil
import subprocess
import sys
import unittest

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))


def rel(*parts):
    return os.path.join(ROOT, *parts)


def read(path):
    with open(path, "r", encoding="utf-8") as f:
        return f.read()


class GeneratedLutTests(unittest.TestCase):
    def test_java_gamma_generator_is_current(self):
        proc = subprocess.run(
            [sys.executable, rel("perfbench", "gen_luts.py"), "--check"],
            cwd=ROOT, stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True,
        )
        self.assertEqual(proc.returncode, 0, proc.stdout)
        self.assertIn("IDENTICAL", proc.stdout)

    def test_native_header_generator_is_current(self):
        proc = subprocess.run(
            [sys.executable, rel("perfbench", "native", "gen_header.py"), "--check"],
            cwd=ROOT, stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True,
        )
        self.assertEqual(proc.returncode, 0, proc.stdout)
        self.assertIn("IDENTICAL", proc.stdout)

    @unittest.skipUnless(shutil.which("node"), "Node.js not available")
    def test_wasm_wat_generator_is_current(self):
        proc = subprocess.run(
            ["node", rel("perfbench", "wasm", "gen_wat.js"), "--check"],
            cwd=ROOT, stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True,
        )
        self.assertEqual(proc.returncode, 0, proc.stdout)
        self.assertIn("IDENTICAL", proc.stdout)

    def test_java_gamma_source_has_only_constants(self):
        src = read(rel("sources", "main", "java", "net", "minecraft", "client",
                       "renderer", "texture", "GeneratedGammaTables.java"))
        body = src[src.index("public final class GeneratedGammaTables"):]
        self.assertIn("private static final float[] GAMMA_2_2_JAVA = {", body)
        self.assertIn("private static final float[] GAMMA_2_2_TEAVM = {", body)
        self.assertIn("EagRuntime.getPlatformType() == EnumPlatformType.JAVASCRIPT", body)
        self.assertIn("static float[] activeTable()", body)
        self.assertNotIn("Math.pow", body)
        self.assertNotIn("for (", body)
        self.assertNotIn("static {", body)
        self.assertNotIn("new float[", body)
        # 2 arrays x 256 float literals. Lines have 8 entries; count decimal F suffixes.
        literals = re.findall(r"(?<![A-Za-z0-9_])(?:\d+\.\d*(?:[eE][+-]?\d+)?|\d+[eE][+-]?\d+|\d+)F", body)
        self.assertGreaterEqual(len(literals), 512)

    def test_hot_filter_holds_stable_final_reference(self):
        src = read(rel("sources", "main", "java", "net", "minecraft", "client",
                       "renderer", "texture", "MipmapGammaBlend.java"))
        self.assertIn("private static final float[] GAMMA = GeneratedGammaTables.activeTable();", src)
        self.assertIn("return GAMMA[byteValue & 255];", src)
        self.assertNotIn("new float[256]", src)
        code = src[src.index("public final class MipmapGammaBlend"):]
        self.assertNotIn("Math.pow((float)", code)

    def test_native_header_is_256_exact_hex_floats(self):
        src = read(rel("perfbench", "native", "gamma_table.h"))
        self.assertIn("static const float GAMMA_2_2[256]", src)
        code = src[src.index("static const float GAMMA_2_2[256]"):]
        self.assertNotIn("pow(", code)
        self.assertNotIn("for (", src)
        literals = re.findall(r"0x[0-9a-fA-F.]+p[+-]\d+F", src)
        self.assertEqual(len(literals), 256, "expected 256 C hex float constants")

    def test_wasm_wat_embeds_256_f32_bytes(self):
        src = read(rel("perfbench", "wasm", "math.wat"))
        self.assertIn("(data (i32.const 0)", src)
        self.assertIn('(import "env" "pow"', src)
        # 1024 bytes encoded as \\xx escapes in the data string.
        data_line = next(l for l in src.splitlines() if l.strip().startswith("(data (i32.const 0)"))
        self.assertEqual(data_line.count("\\"), 1024)
        self.assertIn("(func (export \"batch_lut_f32\")", src)

    def test_new_shell_scripts_parse(self):
        scripts = [
            rel("perfbench", "run_all.sh"),
            rel("perfbench", "run_bench.sh"),
            rel("perfbench", "compile_main.sh"),
            rel("perfbench", "teavm", "build_probe.sh"),
            rel("perfbench", "wasm", "build.sh"),
            rel("perfbench", "native", "build.sh"),
            rel("perfbench", "jit_gc_profile.sh"),
        ]
        proc = subprocess.run(["bash", "-n"] + scripts, cwd=ROOT,
                              stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True)
        self.assertEqual(proc.returncode, 0, proc.stdout)


if __name__ == "__main__":
    unittest.main()
