"""Performance presets configuration, UI, persistence, and engine hook tests.

These tests are CI-friendly (no JDK required): they parse the Java source
files and language resources directly to verify preset definitions, ordering,
GameSettings serialization, GUI wiring, translation coverage, and rendering
hooks.
"""
import os
import re
import unittest

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))


def rel(*parts):
    return os.path.join(ROOT, *parts)


def read(path):
    with open(path, "r", encoding="utf-8") as f:
        return f.read()


def parse_lang(path):
    keys = {}
    with open(path, "r", encoding="utf-8", newline="") as f:
        for raw in f:
            line = raw.rstrip("\n")
            if not line or line.startswith("#") or "=" not in line:
                continue
            key, _, value = line.partition("=")
            keys[key] = value
    return keys


def parse_int_array(java_src, field_name):
    m = re.search(
        r"%s\s*=\s*new\s+int\[\]\s*\{([^}]+)\}" % re.escape(field_name),
        java_src,
        re.DOTALL,
    )
    if not m:
        raise AssertionError("Could not find int[] %s" % field_name)
    tokens = [tok.strip() for tok in m.group(1).split(",") if tok.strip()]
    return tokens


def parse_string_array(java_src, field_name):
    m = re.search(
        r"%s\s*=\s*new\s+String\[\]\s*\{([^}]+)\}" % re.escape(field_name),
        java_src,
        re.DOTALL,
    )
    if not m:
        raise AssertionError("Could not find String[] %s" % field_name)
    return re.findall(r'"([^"]+)"', m.group(1))


class PerformancePresetsTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.presets_src = read(
            rel("sources", "main", "java", "net", "lax1dude", "eaglercraft",
                "v1_8", "PerformancePresets.java"))
        cls.game_settings_src = read(
            rel("sources", "main", "java", "net", "minecraft", "client",
                "settings", "GameSettings.java"))
        cls.video_gui_src = read(
            rel("sources", "main", "java", "net", "minecraft", "client",
                "gui", "GuiVideoSettings.java"))
        cls.presets_gui_src = read(
            rel("sources", "main", "java", "net", "minecraft", "client",
                "gui", "GuiPerformancePresets.java"))
        cls.en_us = parse_lang(
            rel("sources", "resources", "assets", "minecraft", "lang",
                "en_US.lang"))

    def test_all_expected_presets_defined_in_order(self):
        self.assertIn("public static final int PRESET_COUNT = 7;",
                      self.presets_src)
        keys = parse_string_array(self.presets_src, "KEYS")
        self.assertEqual(keys, [
            "bacteria",
            "abacus",
            "arduino_uno_r3",
            "toaster",
            "balanced",
            "high",
            "ultra",
        ])

    def test_preset_tables_have_matching_length_and_monotonic_progression(self):
        render_distances = [int(x) for x in parse_int_array(
            self.presets_src, "RENDER_DISTANCE")]
        max_particles = [int(x) for x in parse_int_array(
            self.presets_src, "MAX_PARTICLES")]
        mipmaps = [int(x) for x in parse_int_array(
            self.presets_src, "MIPMAP_LEVELS")]
        ao = [int(x) for x in parse_int_array(
            self.presets_src, "AMBIENT_OCCLUSION")]

        for arr in (render_distances, max_particles, mipmaps, ao):
            self.assertEqual(len(arr), 7)

        # Ultra low-end -> high-end should be monotonically non-decreasing
        self.assertEqual(render_distances, sorted(render_distances))
        self.assertEqual(max_particles, sorted(max_particles))
        self.assertEqual(mipmaps, sorted(mipmaps))
        self.assertEqual(ao, sorted(ao))
        self.assertEqual(max_particles[0], 0)  # Bacteria disables particles
        self.assertGreater(render_distances[-1], render_distances[0])

    def test_teavm_compatibility_no_threads_or_jvm_only_apis(self):
        for src in (self.presets_src, self.presets_gui_src):
            self.assertNotIn("java.lang.Thread", src)
            self.assertNotIn("new Thread", src)
            self.assertNotIn("java.util.concurrent", src)
            self.assertNotIn("java.nio.file", src)

    def test_en_us_translations_exist_for_all_preset_keys(self):
        required_keys = [
            "options.performancePreset",
            "options.performancePresetsButton",
            "options.performancePresets.title",
            "options.performancePresets.current",
            "options.performancePresets.summary",
            "options.performancePreset.custom",
            "options.performancePreset.custom.tier",
            "options.performancePreset.custom.desc",
        ]
        for key in parse_string_array(self.presets_src, "KEYS"):
            required_keys.extend([
                "options.performancePreset." + key,
                "options.performancePreset." + key + ".tier",
                "options.performancePreset." + key + ".desc",
            ])

        for lang_key in required_keys:
            self.assertIn(lang_key, self.en_us,
                          "en_US.lang is missing %s" % lang_key)
            self.assertTrue(self.en_us[lang_key].strip(),
                            "en_US.lang has empty value for %s" % lang_key)

    def test_game_settings_persistence_and_application(self):
        self.assertIn("public int performancePreset = PerformancePresets.PRESET_CUSTOM;",
                      self.game_settings_src)
        self.assertIn("public void applyPerformancePreset(int presetId)",
                      self.game_settings_src)
        self.assertIn('if (astring[0].equals("performancePreset")',
                      self.game_settings_src)
        self.assertIn('printwriter.println("performancePreset:" + PerformancePresets.getKey(this.performancePreset));',
                      self.game_settings_src)
        self.assertIn('PERFORMANCE_PRESET("options.performancePreset", false, false)',
                      self.game_settings_src)
        self.assertIn("isPresetManagedOption(parOptions)",
                      self.game_settings_src)

    def test_gui_wiring(self):
        self.assertIn("new GuiPerformancePresets(this, this.guiGameSettings)",
                      self.video_gui_src)
        self.assertIn("options.performancePresetsButton",
                      self.video_gui_src)
        self.assertIn("this.guiGameSettings.applyPerformancePreset(presetId);",
                      self.presets_gui_src)

    def test_engine_performance_hooks_wired(self):
        hooks = {
            rel("sources", "main", "java", "net", "minecraft", "client",
                "particle", "EffectRenderer.java"): "PerformancePresets.getMaxParticles",
            rel("sources", "main", "java", "net", "minecraft", "client",
                "renderer", "texture", "TextureMap.java"): "PerformancePresets.areTextureAnimationsEnabled",
            rel("sources", "main", "java", "net", "minecraft", "client",
                "renderer", "RenderGlobal.java"): "PerformancePresets.shouldRenderSkyExtras",
            rel("sources", "main", "java", "net", "minecraft", "client",
                "renderer", "EntityRenderer.java"): "PerformancePresets.shouldRenderWeather",
            rel("sources", "main", "java", "net", "minecraft", "client",
                "renderer", "tileentity", "TileEntityRendererDispatcher.java"): "PerformancePresets.getTileEntityDistanceScale",
            rel("sources", "main", "java", "net", "lax1dude", "eaglercraft",
                "v1_8", "minecraft", "ChunkUpdateManager.java"): "PerformancePresets.getMaxChunkUpdatesPerFrame",
        }
        for path, expected in hooks.items():
            src = read(path)
            self.assertIn(expected, src, "%s missing %s" % (path, expected))


if __name__ == "__main__":
    unittest.main()
