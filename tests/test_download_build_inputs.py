"""Offline checks for the CI input downloader; no Minecraft files are included."""
import json
import os
from pathlib import Path
import subprocess
import tempfile
import unittest
import zipfile

ROOT = Path(__file__).resolve().parents[1]
SCRIPT = ROOT / 'scripts/download-build-inputs.sh'


class DownloaderTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.bin = self.root / 'bin'
        self.bin.mkdir()
        for name in ('mcp918.zip', '1.8.8.jar'):
            with zipfile.ZipFile(self.root / name, 'w') as archive:
                archive.writestr('test', 'fixture')
        (self.root / '1.8.json').write_text(json.dumps({'objects': {'test': {}}}))
        curl = self.bin / 'curl'
        curl.write_text('''#!/usr/bin/env python3
import json, os, pathlib, shutil, sys
args = sys.argv[1:]
with open(os.environ['CALLS'], 'a') as log:
    log.write(json.dumps(args) + '\\n')
url = args[-1]
name = url.rsplit('/', 1)[-1]
if name == 'client.jar': name = '1.8.8.jar'
shutil.copyfile(pathlib.Path(os.environ['FIXTURES']) / name, args[args.index('--output') + 1])
''')
        curl.chmod(0o755)
        self.env = dict(os.environ, PATH=f'{self.bin}:{os.environ["PATH"]}',
                        CALLS=str(self.root / 'calls'), FIXTURES=str(self.root),
                        MCP918_URL='https://example.test/mcp918.zip',
                        MINECRAFT_JAR_URL='https://example.test/1.8.8.jar',
                        ASSETS_INDEX_URL='https://example.test/1.8.json',
                        BUILD_INPUTS_TOKEN='test-token')

    def run_script(self):
        return subprocess.run([str(SCRIPT), str(self.root / 'output')], env=self.env,
                              capture_output=True, text=True)

    def calls(self):
        return [json.loads(line) for line in (self.root / 'calls').read_text().splitlines()]

    def test_overrides_are_downloaded_and_validated(self):
        result = self.run_script()
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertEqual(len(self.calls()), 3)
        self.assertTrue(all('Authorization: Bearer test-token' in call for call in self.calls()))
        self.assertTrue((self.root / 'output/1.8.8.jar').is_file())

    def test_default_does_not_receive_token_and_checks_hash(self):
        self.env['MCP918_URL'] = ''
        result = self.run_script()
        self.assertNotEqual(result.returncode, 0)
        self.assertIn('Checksum mismatch', result.stderr)
        self.assertNotIn('Authorization: Bearer test-token', self.calls()[0])
        self.assertFalse((self.root / 'output/mcp918.zip').exists())

    def test_invalid_index_is_rejected(self):
        (self.root / '1.8.json').write_text('{}')
        result = self.run_script()
        self.assertNotEqual(result.returncode, 0)
        self.assertIn('non-empty objects', result.stderr)

    def test_corrupt_archive_is_rejected(self):
        (self.root / '1.8.8.jar').write_text('not a jar')
        result = self.run_script()
        self.assertNotEqual(result.returncode, 0)
        self.assertIn('BadZipFile', result.stderr)


if __name__ == '__main__':
    unittest.main()
