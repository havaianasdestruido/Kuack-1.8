"""Regression guard for the readable-name rename of the decompiled game sources.

The Minecraft 1.8 source in `sources/main/java` was decompiled with MCP and shipped
with a large number of machine-generated names (`func_181159_b`, `field_183018_l`, ...).
They were renamed to the MCP names (or, where MCP has none, to reviewed names), and
`patches/minecraft/rename_map.json` records the mapping.

These tests keep the tree readable: a new symbol may only appear in the sources if it
is listed in the map or has been added to the allowlist together with the reason it
cannot be renamed (an interface method with implementations, a member declared in a
class that is not part of this tree, or a symbol for which no mapping exists).
"""
import json
import os
import re
import unittest

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SOURCE_ROOT = os.path.join(ROOT, "sources", "main", "java")
MAP_PATH = os.path.join(ROOT, "patches", "minecraft", "rename_map.json")

SYMBOL = re.compile(r"\bfunc_[0-9]+_[a-zA-Z0-9_]+\b|\bfield_[0-9]+_[a-zA-Z0-9_]+\b")
IDENTIFIER = re.compile(r"[A-Za-z_$][A-Za-z0-9_$]*")
JAVA_KEYWORDS = {
    "abstract", "assert", "boolean", "break", "byte", "case", "catch", "char", "class",
    "const", "continue", "default", "do", "double", "else", "enum", "extends", "final",
    "finally", "float", "for", "goto", "if", "implements", "import", "instanceof", "int",
    "interface", "long", "native", "new", "package", "private", "protected", "public",
    "return", "short", "static", "strictfp", "super", "switch", "synchronized", "this",
    "throw", "throws", "transient", "try", "void", "volatile", "while", "true", "false",
    "null",
}


def load_map():
    with open(MAP_PATH, "r", encoding="utf-8") as f:
        return json.load(f)


def java_files():
    for dirpath, _, filenames in os.walk(SOURCE_ROOT):
        for name in sorted(filenames):
            if name.endswith(".java"):
                path = os.path.join(dirpath, name)
                yield os.path.relpath(path, SOURCE_ROOT), path


def read(path):
    with open(path, "r", encoding="utf-8", errors="replace") as f:
        return f.read()


class RenameMapTests(unittest.TestCase):
    def setUp(self):
        self.data = load_map()
        self.renamed = self.data["renamed"]
        self.allowlist = self.data["allowlist"]

    def test_map_is_well_formed(self):
        self.assertTrue(self.renamed, "rename map is empty")
        for old, new in self.renamed.items():
            self.assertRegex(old, r"^(func|field)_[0-9]+_[a-zA-Z0-9_]+$")
            self.assertRegex(new, r"^[A-Za-z_$][A-Za-z0-9_$]*$")
            self.assertNotIn(new, JAVA_KEYWORDS, "%s maps to the keyword %r" % (old, new))
            self.assertFalse(new.startswith("func_"), "%s still has a generated name" % old)
            self.assertFalse(new.startswith("field_"), "%s still has a generated name" % old)

    def test_allowlist_has_reasons(self):
        self.assertTrue(self.allowlist, "allowlist should document the symbols that stay")
        for path, entry in self.allowlist.items():
            self.assertTrue(entry["symbols"], "%s listed without symbols" % path)
            self.assertTrue(entry["reason"], "%s listed without a reason" % path)
            self.assertTrue(os.path.isfile(os.path.join(SOURCE_ROOT, path)),
                            "%s no longer exists, drop it from the allowlist" % path)

    def test_source_uses_only_mapped_names(self):
        """No generated name may appear unless the map/allowlist explains it."""
        allowed = set(self.renamed)
        for entry in self.allowlist.values():
            allowed.update(entry["symbols"])
        unexpected = []
        for relpath, path in java_files():
            for symbol in sorted(set(SYMBOL.findall(read(path)))):
                if symbol not in allowed:
                    unexpected.append("%s: %s" % (relpath, symbol))
        self.assertEqual(unexpected, [], "unmapped generated names in the sources:\n" +
                         "\n".join(unexpected[:40]))

    def test_allowlist_matches_the_sources(self):
        """The allowlist must not go stale: every listed symbol must still be used."""
        stale = []
        for relpath, entry in self.allowlist.items():
            present = set(SYMBOL.findall(read(os.path.join(SOURCE_ROOT, relpath))))
            for symbol in entry["symbols"]:
                if symbol not in present:
                    stale.append("%s: %s" % (relpath, symbol))
        self.assertEqual(stale, [], "allowlist entries that no longer occur:\n" +
                         "\n".join(stale[:40]))

    def test_renamed_symbols_are_gone(self):
        """A renamed symbol must not occur anywhere in the sources any more."""
        leftovers = []
        for relpath, path in java_files():
            text = read(path)
            for symbol in self.renamed:
                if symbol in text:
                    leftovers.append("%s: %s" % (relpath, symbol))
        self.assertEqual(leftovers, [], "renamed names still present:\n" +
                         "\n".join(leftovers[:40]))

    def test_renamed_names_are_not_generated_names(self):
        """A reviewed name must never look like a generated one, or the guard below breaks."""
        for old, new in self.renamed.items():
            self.assertNotRegex(new, r"^(func|field)_[0-9]+_",
                                "%s maps back to a generated name" % old)

    def test_target_names_are_valid_identifiers(self):
        for old, new in self.renamed.items():
            self.assertIsNotNone(IDENTIFIER.fullmatch(new))
            self.assertFalse(new[0].isdigit())


if __name__ == "__main__":
    unittest.main()
