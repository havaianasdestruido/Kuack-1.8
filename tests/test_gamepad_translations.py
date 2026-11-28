"""Gamepad option translation coverage tests.

The gamepad option labels (GameSettings.Options.GAMEPAD_*) are rendered with
I18n.format("options.gamepad.*"). English lives in
sources/resources/assets/minecraft/lang/en_US.lang; the non-English languages
are served from the prebuilt files in
sources/setup/workspace_template/javascript/lang/ (packed verbatim into
minecraft_languages.zip and copied into the dev workspace). If a key is
missing from a shipped language file the label silently falls back to
English, which is what the "gamepad options are not translated" report was.

These tests are CI-friendly (no JDK required): they parse the .lang files as
text and assert coverage, format and ordering invariants.
"""
import json
import os
import unittest

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))

EN_US = os.path.join(ROOT, "sources", "resources", "assets", "minecraft",
                     "lang", "en_US.lang")
TEMPLATE_LANG_DIR = os.path.join(ROOT, "sources", "setup",
                                 "workspace_template", "javascript", "lang")
PACK_MCMETA = os.path.join(ROOT, "sources", "resources", "pack.mcmeta")

GAMEPAD_KEYS = (
    "options.gamepad.enable",
    "options.gamepad.preset.legacy",
    "options.gamepad.preset.xbox",
    "options.gamepad.preset.playstation",
    "options.gamepad.preset.switch",
    "options.gamepad.look",
    "options.gamepad.lookInvert",
    "options.gamepad.deadzone",
)


def parse_lang(path):
    """Mirror Locale.loadLocaleData: skip blank/# lines, keep split on the
    first '=', and silently drop lines without '=' (vanilla .lang files
    occasionally contain broken multi-line values)."""
    keys = {}
    with open(path, "r", encoding="utf-8", newline="") as f:
        for lineno, raw in enumerate(f, 1):
            line = raw.rstrip("\n")
            self_check_no_cr(line, path, lineno)
            if not line or line.startswith("#"):
                continue
            key, sep, value = line.partition("=")
            if not sep:
                continue
            keys[key] = value
    return keys


def self_check_no_cr(line, path, lineno):
    if "\r" in line:
        raise AssertionError("CR character in %s line %d" % (path, lineno))


def template_files():
    return sorted(f for f in os.listdir(TEMPLATE_LANG_DIR)
                  if f.endswith(".lang"))


class GamepadTranslationTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.en_us = parse_lang(EN_US)

    def test_en_US_defines_every_gamepad_key(self):
        for key in GAMEPAD_KEYS:
            self.assertIn(key, self.en_us,
                          "en_US.lang is missing %s" % key)
            self.assertTrue(self.en_us[key].strip(),
                            "en_US.lang has an empty value for %s" % key)

    def test_every_shipped_language_translates_every_gamepad_key(self):
        files = template_files()
        self.assertTrue(files, "no template language files found")
        # the union of pack.mcmeta languages minus en_US must be present
        with open(PACK_MCMETA, "r", encoding="utf-8") as f:
            pack_languages = set(json.load(f)["language"].keys())
        self.assertEqual(pack_languages - {"en_US"},
                         {f[:-len(".lang")] for f in files})
        for fname in files:
            path = os.path.join(TEMPLATE_LANG_DIR, fname)
            with self.subTest(lang=fname):
                entries = parse_lang(path)
                for key in GAMEPAD_KEYS:
                    self.assertIn(key, entries,
                                  "%s is missing %s" % (fname, key))
                    self.assertTrue(entries[key].strip(),
                                    "%s has an empty value for %s" %
                                    (fname, key))

    def test_template_files_stay_sorted_and_lf_terminated(self):
        for fname in template_files():
            path = os.path.join(TEMPLATE_LANG_DIR, fname)
            with self.subTest(lang=fname):
                with open(path, "r", encoding="utf-8", newline="") as f:
                    text = f.read()
                self.assertNotIn("\r", text)
                self.assertTrue(text.endswith("\n"),
                                "%s must end with a trailing newline" % fname)
                keys = [line.split("=", 1)[0]
                        for line in text.split("\n")
                        if line and not line.startswith("#") and
                        "=" in line]
                self.assertEqual(keys, sorted(keys),
                                 "%s is not sorted by key" % fname)


if __name__ == "__main__":
    unittest.main()
