"""Host-only deployment safety tests; never installs/builds/signs packages."""
import hashlib
import json
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch

import compose_site as composition
import preserve_packages as preservation


class DeploymentSafety(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory(prefix='codec-deploy-test-')
        self.root = Path(self.tmp.name)
        self.site = self.root / 'website'
        self.site.mkdir()
        (self.site / 'index.html').write_text('<h1>CodeC</h1>')
        self.output = self.root / 'artifact'
        for name in ('dev/CODEC-REPOSITORY', 'dev/dists/stable/InRelease', 'dev/dists/stable/Release.gpg', 'keys/codec-archive-keyring-v1.gpg'):
            target = self.output / name
            target.parent.mkdir(parents=True, exist_ok=True)
            target.write_bytes(b'Composition test fixture, not signed metadata')

    def tearDown(self):
        self.tmp.cleanup()

    def test_compose_keeps_protected_bytes(self):
        before = composition.manifest(self.output)
        composition.compose(self.site, self.output)
        self.assertEqual(composition.manifest(self.output), before)
        self.assertEqual((self.output / 'index.html').read_bytes(), (self.site / 'index.html').read_bytes())
        self.assertTrue((self.output / '.nojekyll').is_file())

    def test_reserved_prefixes_rejected_before_copy(self):
        for name in ('dev', 'keys', 'DEV', 'KEYS'):
            p = self.site / name
            p.mkdir()
            (p / 'evil').write_text('no')
            with self.assertRaises(ValueError):
                composition.compose(self.site, self.output)
            self.assertFalse((self.output / 'index.html').exists())
            (p / 'evil').unlink()
            p.rmdir()

    def test_source_symlink_rejected(self):
        (self.site / 'escape').symlink_to(self.root)
        with self.assertRaises(ValueError):
            composition.compose(self.site, self.output)

    def test_destination_symlink_rejected(self):
        (self.output / 'assets').symlink_to(self.root)
        (self.site / 'assets').mkdir()
        with self.assertRaises(ValueError):
            composition.compose(self.site, self.output)

    def test_package_only_or_website_only_rejected(self):
        (self.output / 'dev/dists/stable/InRelease').unlink()
        with self.assertRaises(ValueError):
            composition.compose(self.site, self.output)
        with self.assertRaises(ValueError):
            composition.compose(self.site, self.root / 'missing')

    def test_collision_rejected(self):
        (self.output / 'index.html').write_text('operator file')
        with self.assertRaises(ValueError):
            composition.compose(self.site, self.output)
        self.assertEqual((self.output / 'index.html').read_text(), 'operator file')

    def test_safe_remote_paths(self):
        for value in ('../secret', '/absolute', 'https://evil/a', 'x//a', 'x/./a', 'x/../a', 'x?query', 'x#frag', 'x%2fa', 'x\\a', ''):
            with self.subTest(value=value), self.assertRaises(ValueError):
                preservation.safe_path(value)
        self.assertEqual(preservation.safe_path('dev/dists/stable/main/binary-aarch64/gcc_1+2~3.deb'), 'dev/dists/stable/main/binary-aarch64/gcc_1+2~3.deb')

    def test_hash_and_size_fail_closed(self):
        sha = hashlib.sha256(b'good').hexdigest()
        preservation.assert_hash(b'good', sha, 4)
        for data, expected, size in [(b'bad', sha, 4), (b'good', sha, 7), (b'good', 'bad', 4)]:
            with self.assertRaises(ValueError):
                preservation.assert_hash(data, expected, size)

    def test_snapshot_never_overwrites(self):
        with self.assertRaises(ValueError):
            preservation.preserve(self.output)

    def test_live_key_must_match_pinned_key(self):
        with patch.object(preservation, 'download', return_value=b'wrong key'):
            with self.assertRaisesRegex(ValueError, 'pinned trust'):
                preservation.preserve(self.root / 'new')

    def test_invalid_signature_rejected(self):
        release = self.output / 'dev/dists/stable/Release'
        release.write_text('Origin: CodeC\nSHA256:\n')
        with self.assertRaises(preservation.validator.PackageError):
            preservation.validator.verify_release_signatures(release, preservation.KEYS / 'codec-archive-keyring-v1.gpg', 'A' * 40)

    def test_signed_index_paths_cannot_escape(self):
        for arch in ('all', 'aarch64', 'x86_64'):
            p = self.output / f'dev/dists/stable/main/binary-{arch}/Packages'
            p.parent.mkdir(parents=True, exist_ok=True)
            p.write_text('')
        p = self.output / 'dev/dists/stable/main/binary-all/Packages'
        p.write_text('Package: example\nFilename: ../../escape.deb\nSize: 4\nSHA256: ' + 'a' * 64 + '\n\n')
        with self.assertRaises(ValueError):
            preservation.package_records(self.output / 'dev')

    def test_license_scope_and_all_footer_links(self):
        root = preservation.ROOT
        notice = (root / 'website/learn/licenses/SCOPE.txt').read_text()
        for required in ('CC BY 4.0', 'MIT', '2026 pabi277', 'does not relicense', 'third-party', 'trademarks'):
            self.assertIn(required, notice)
        for p in (root / 'website').rglob('*.html'):
            self.assertIn('>Course license</a>', p.read_text())
        self.assertIn('Creative Commons Attribution 4.0 International', (root / 'website/learn/licenses/CC-BY-4.0.txt').read_text())

    def test_workflow_publication_boundaries(self):
        root = preservation.ROOT
        web = (root / '.github/workflows/website-pages.yml').read_text()
        packages = (root / '.github/workflows/package-repository.yml').read_text()
        self.assertIn("github.ref == 'refs/heads/main'", web)
        self.assertIn('cancel-in-progress: false', web)
        self.assertIn('group: codec-pages', web)
        self.assertIn('group: codec-pages', packages)
        self.assertIn('web_docs/deploy/compose_site.py website packages', packages)
        self.assertNotIn('SIGNING_KEY', web)
        self.assertNotIn('workflow_run:', web)
        self.assertIn('preserve_packages.py', web)
        self.assertIn('verify_live.py', web)


if __name__ == '__main__':
    unittest.main()
