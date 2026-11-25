"""Offline checks for the checked in, patched Minecraft source and build inputs."""
import hashlib
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[1]
MINECRAFT = ROOT / 'sources/minecraft'
VENDOR = ROOT / 'vendor/minecraft'


class VendoredSourceTest(unittest.TestCase):
    def test_build_inputs_match_pinned_hashes(self):
        expected_sha1 = {
            '1.8.8.jar': '0983f08be6a4e624f5d85689d1aca869ed99c738',
            '1.8.json': 'f6ad102bcaa53b1a58358f16e376d548d44933ec',
        }
        for name, digest in expected_sha1.items():
            actual = hashlib.sha1((VENDOR / name).read_bytes()).hexdigest()
            self.assertEqual(actual, digest, f'unexpected hash for {name}')
        mcp = hashlib.sha256((VENDOR / 'mcp918.zip').read_bytes()).hexdigest()
        self.assertEqual(mcp, 'c936dffb3007110b24538da5f334c28ec83c6787a56cc4c63fd840cdff306eb0')

    def test_all_vendored_files_match_manifest(self):
        manifest = (VENDOR / 'SHA256SUMS').read_text().splitlines()
        self.assertGreater(len(manifest), 600)
        for line in manifest:
            digest, name = line.split(None, 1)
            path = VENDOR / name.strip()
            self.assertTrue(path.is_file(), f'missing vendored file {path}')
            self.assertEqual(hashlib.sha256(path.read_bytes()).hexdigest(), digest,
                             f'checksum mismatch for {path}')

    def test_patched_java_tree_is_complete(self):
        java = list((MINECRAFT / 'java').rglob('*.java'))
        self.assertGreater(len(java), 1400, 'patched Minecraft source is incomplete')
        self.assertTrue((MINECRAFT / 'java/net/minecraft/client/Minecraft.java').is_file())
        self.assertTrue((MINECRAFT / 'java/net/minecraft/client/main/Main.java').is_file())
        self.assertTrue((MINECRAFT / 'LICENSE').is_file())
        for name in ('net/minecraft/network/NetworkManager.java',
                     'net/minecraft/client/renderer/GlStateManager.java'):
            self.assertFalse((MINECRAFT / 'java' / name).exists(),
                             f'{name} should have been deleted by the patches')

    def test_patched_java_contains_eaglercraft_changes(self):
        main = (MINECRAFT / 'java/net/minecraft/client/main/Main.java').read_text(encoding='utf-8')
        self.assertIn('net.lax1dude.eaglercraft.v1_8.internal.PlatformRuntime', main)
        self.assertIn('deobfuscated Minecraft 1.8 source', main)

    def test_assets_and_languages_are_vendored(self):
        assets = list((MINECRAFT / 'assets').rglob('*'))
        self.assertGreater(len([a for a in assets if a.is_file()]), 3000)
        self.assertTrue((MINECRAFT / 'assets/assets/minecraft/sounds.json').is_file())
        self.assertGreater(len(list((MINECRAFT / 'assets/assets/minecraft/sounds').rglob('*.ogg'))), 500)
        self.assertEqual(len(list((MINECRAFT / 'lang').glob('*.lang'))), 74)
        self.assertTrue((MINECRAFT / 'lang/en_US.lang').is_file() or
                        (MINECRAFT / 'assets/assets/minecraft/lang/en_US.lang').is_file())

    def test_headless_compiler_can_use_vendored_tree(self):
        source = (ROOT / 'buildtools/src/main/java/net/lax1dude/eaglercraft/v1_8/buildtools/'
                  'gui/headless/CompileLatestClientHeadless.java').read_text(encoding='utf-8')
        for key in ('minecraftSourceDirectory', 'minecraftAssetsDirectory',
                    'minecraftLanguagesDirectory'):
            self.assertIn(key, source)

    def test_regeneration_scripts_exist(self):
        for name in ('scripts/regenerate-vendored-sources.sh',
                     'scripts/regenerate-vendored-sources.bat',
                     'scripts/build-vendored.sh',
                     'scripts/build-vendored.bat'):
            self.assertTrue((ROOT / name).is_file(), f'missing {name}')


if __name__ == '__main__':
    unittest.main()
