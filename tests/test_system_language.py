"""System/browser language default detection tests.

These are CI-friendly (no JDK required): they verify from the sources that the
client resolves its default language from the system/browser language instead
of a hardcoded value, that the detection tables stay consistent with the
languages actually shipped in pack.mcmeta and lang/, and that a saved/explicit
`lang` option still overrides the detected default.
"""
import json
import os
import re
import unittest

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))


def rel(*parts):
    return os.path.join(ROOT, *parts)


def read(path):
    with open(path, "r", encoding="utf-8") as f:
        return f.read()


def load_pack_languages():
    with open(rel("sources", "resources", "pack.mcmeta"), "r", encoding="utf-8") as f:
        return set(json.load(f)["language"].keys())


def parse_java_map(java_src, map_name):
    """Parse `NAME.put("key", "value");` entries out of a Java source string."""
    pattern = re.compile(
        r"%s\.put\(\s*\"([^\"]+)\"\s*,\s*\"([^\"]+)\"\s*\)" % re.escape(map_name)
    )
    return {m.group(1): m.group(2) for m in pattern.finditer(java_src)}


class SystemLanguageDetectionTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.system_language_src = read(
            rel("sources", "main", "java", "net", "lax1dude", "eaglercraft",
                "v1_8", "internal", "SystemLanguage.java"))
        cls.adapter_src = read(
            rel("sources", "teavm", "java", "net", "lax1dude", "eaglercraft",
                "v1_8", "internal", "teavm", "TeaVMClientConfigAdapter.java"))
        cls.desktop_src = read(
            rel("sources", "lwjgl", "java", "net", "lax1dude", "eaglercraft",
                "v1_8", "internal", "lwjgl", "DesktopClientConfigAdapter.java"))
        cls.game_settings_src = read(
            rel("sources", "main", "java", "net", "minecraft", "client",
                "settings", "GameSettings.java"))
        cls.pack_languages = load_pack_languages()

    def test_shipped_lang_files_match_pack_mcmeta(self):
        """Every .lang file shipped in the site template is listed in
        pack.mcmeta, and en_US (bundled in assets.epk) is the only entry
        without a template file."""
        lang_dir = rel("sources", "setup", "workspace_template", "javascript",
                       "lang")
        files = {f[:-len(".lang")] for f in os.listdir(lang_dir)
                 if f.endswith(".lang")}
        self.assertEqual(files - self.pack_languages, set())
        self.assertEqual(self.pack_languages - files, {"en_US"})

    def test_en_US_language_file_is_bundled_in_assets(self):
        path = rel("sources", "resources", "assets", "minecraft", "lang",
                   "en_US.lang")
        self.assertTrue(os.path.isfile(path))
        self.assertGreater(os.path.getsize(path), 0)

    def test_system_language_class_falls_back_to_en_US(self):
        self.assertIn('public static final String DEFAULT_LOCALE = "en_US";',
                      self.system_language_src)
        self.assertIn("return DEFAULT_LOCALE;", self.system_language_src)
        # detection must never throw and prevent the game from booting
        self.assertIn("catch (Throwable t)", self.system_language_src)
        # before the EPK assets are loaded, detection reports "not ready yet"
        # instead of a guess, so callers retry rather than cache en_US
        self.assertIn("pack.mcmeta is not available yet", self.system_language_src)
        self.assertIn("return null;", self.system_language_src)

    def test_preferred_variants_cover_every_multivariant_language(self):
        """Every base language with more than one shipped variant must have a
        preferred variant, so bare tags like "en" resolve deterministically
        (and never to joke files like en_PT)."""
        preferred = parse_java_map(self.system_language_src,
                                   "PREFERRED_VARIANTS")
        bases = sorted({code.split("_", 1)[0] for code in self.pack_languages})
        multi = {b for b in bases
                 if sum(1 for c in self.pack_languages
                        if c.startswith(b + "_")) > 1}
        self.assertEqual(set(preferred), multi)
        for base, variant in preferred.items():
            self.assertIn(variant, self.pack_languages)
            self.assertTrue(variant.startswith(base + "_"))

    def test_language_aliases_point_at_shipped_languages(self):
        aliases = parse_java_map(self.system_language_src, "LANG_ALIASES")
        self.assertGreaterEqual(len(aliases), 5)
        for source, target in aliases.items():
            self.assertTrue(
                any(c.startswith(target + "_") for c in self.pack_languages),
                "alias %s -> %s has no shipped language" % (source, target))

    def test_teavm_adapter_reads_browser_language_when_lang_not_set(self):
        # explicit lang option in eaglercraftXOpts must still win; without it
        # the adapter resolves the default lazily from the browser language
        self.assertIn("eaglercraftXOpts.getLang(null)", self.adapter_src)
        self.assertNotIn('getLang("en_US")', self.adapter_src)
        self.assertIn("SystemLanguage.detect(getSystemLanguageTags())",
                      self.adapter_src)
        self.assertIn("private static native String getSystemLanguageTags()",
                      self.adapter_src)
        self.assertIn("typeof navigator", self.adapter_src)
        self.assertIn("nav.languages", self.adapter_src)

    def test_integrated_server_worker_receives_the_detected_language(self):
        # lang is injected when the worker start args are handed out, after
        # the default locale has been resolved on the main thread
        self.assertIn('integratedServerOpts.put("lang", getDefaultLocale())',
                      self.adapter_src)

    def test_desktop_adapter_detects_the_system_locale(self):
        self.assertIn("SystemLanguage.detect(Locale.getDefault().toLanguageTag())",
                      self.desktop_src)
        self.assertNotIn('return "en_US";', self.desktop_src)

    def test_game_settings_default_comes_from_config_and_saved_option_wins(self):
        # the default is resolved through IClientConfigAdapter.getDefaultLocale
        self.assertIn(
            "this.language = EagRuntime.getConfiguration().getDefaultLocale();",
            self.game_settings_src)
        # ...but a saved `lang:` entry in the options file still overrides it
        override = re.search(
            r'if \(astring\[0\]\.equals\("lang"\).*?\{\s*'
            r'this\.language = astring\[1\];',
            self.game_settings_src, re.DOTALL)
        self.assertIsNotNone(override)

    def test_no_hardcoded_en_US_default_remains_in_teavm_adapter(self):
        self.assertNotIn('optString("lang", "en_US")', self.adapter_src)


if __name__ == "__main__":
    unittest.main()
