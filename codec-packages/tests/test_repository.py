#!/usr/bin/env python3
from __future__ import annotations

import gzip
import hashlib
import json
import os
import shutil
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path

SCRIPTS = Path(__file__).resolve().parents[1] / "scripts"
GENERATE = SCRIPTS / "generate-repository.py"
VALIDATE = SCRIPTS / "validate-repository.py"
SIGN = SCRIPTS / "sign-repository.sh"
KEYS = SCRIPTS.parent / "keys"
TEST_PASSPHRASE = "codec-test-signing-passphrase"


def make_deb(root: Path, output: Path, *, name: str = "codec-demo", arch: str = "aarch64", script: bool = False) -> Path:
    package_root = root / f"{name}-{arch}"
    data = package_root / "data/data/com.codeci.ide/files/usr/bin"
    data.mkdir(parents=True)
    (data / name).write_text("#!/system/bin/sh\necho demo\n")
    control = package_root / "DEBIAN"
    control.mkdir()
    (control / "control").write_text(
        "Package: %s\nVersion: 1.0\nArchitecture: %s\n"
        "Depends: busybox\nDescription: test CodeC package\n" % (name, arch)
    )
    if script:
        if name in {"coreutils", "less", "nano"}:
            prefix = "/data/data/com.codeci.ide/files/usr"
            values = {
                "coreutils": ("pager", "bin/pager", "libexec/coreutils/cat", "1", "share/man/man1/pager.1.gz", "pager.1.gz", "share/man/man1/cat.1.gz"),
                "less": ("pager", "bin/pager", "bin/less", "50", "share/man/man1/pager.1.gz", "pager.1.gz", "share/man/man1/less.1.gz"),
                "nano": ("editor", "bin/editor", "bin/nano", "50", "share/man/man1/editor.1.gz", "editor.1.gz", "share/man/man1/nano.1.gz"),
            }
            alt_name, alt_link, alt_target, priority, slave_link, slave_name, slave_target = values[name]
            postinst = control / "postinst"
            postinst.write_text(
                f"#!{prefix}/bin/sh\n"
                "# Automatically added by termux_step_create_alternatives\n"
                "if [ \"$1\" = 'configure' ] || [ \"$1\" = 'abort-upgrade' ] || [ \"$1\" = 'abort-deconfigure' ] || [ \"$1\" = 'abort-remove' ]; then\n"
                f"  if [ -x \"{prefix}/bin/update-alternatives\" ]; then\n"
                f"    update-alternatives \\\n      --install \"{prefix}/{alt_link}\" \"{alt_name}\" \"{prefix}/{alt_target}\" {priority} \\\n      --slave \"{prefix}/{slave_link}\" \"{slave_name}\" \"{prefix}/{slave_target}\"\n"
                "  fi\nfi\n"
                "# End automatically added section\n"
            )
            postinst.chmod(0o755)
            prerm = control / "prerm"
            prerm.write_text(
                f"#!{prefix}/bin/sh\n"
                "# Automatically added by termux_step_create_alternatives\n"
                "if [ \"$1\" = 'remove' ] || [ \"$1\" != 'upgrade' ]; then\n"
                f"  if [ -x \"{prefix}/bin/update-alternatives\" ]; then\n"
                f"    update-alternatives --remove \"{alt_name}\" \"{prefix}/{alt_target}\"\n"
                "  fi\nfi\n"
                "# End automatically added section\n"
            )
            prerm.chmod(0o755)
        else:
            postinst = control / "postinst"
            postinst.write_text("#!/system/bin/sh\necho unsafe\n")
            postinst.chmod(0o755)
    result = subprocess.run(
        ["dpkg-deb", "--build", str(package_root), str(output)],
        text=True,
        capture_output=True,
    )
    if result.returncode:
        raise AssertionError(result.stderr)
    return output


def make_signed_repository(root: Path, repo: Path) -> tuple[Path, str]:
    """Create an ephemeral test key, sign repo, and return public keyring/fpr."""
    home = root / "gnupg"
    home.mkdir(mode=0o700)
    env = dict(
        os.environ,
        GNUPGHOME=str(home),
        CODEC_SIGNING_KEY_PASSPHRASE=TEST_PASSPHRASE,
    )
    identity = "CodeC Repository Test <repository-test@codeci.invalid>"
    subprocess.run(
        [
            "gpg",
            "--batch",
            "--pinentry-mode",
            "loopback",
            "--passphrase",
            TEST_PASSPHRASE,
            "--quick-generate-key",
            identity,
            "rsa2048",
            "sign",
            "1d",
        ],
        env=env,
        check=True,
        text=True,
        capture_output=True,
    )
    listing = subprocess.run(
        ["gpg", "--batch", "--with-colons", "--fingerprint", "--list-secret-keys", identity],
        env=env,
        check=True,
        text=True,
        capture_output=True,
    ).stdout
    fingerprint = next(
        line.split(":")[9].upper()
        for line in listing.splitlines()
        if line.startswith("fpr:")
    )
    keyring = root / "codec-test-keyring.gpg"
    exported = subprocess.run(
        ["gpg", "--batch", "--export", fingerprint],
        env=env,
        check=True,
        capture_output=True,
    ).stdout
    keyring.write_bytes(exported)
    subprocess.run([str(SIGN), str(repo), fingerprint], env=env, check=True)
    return keyring, fingerprint


def signed_validator(repo: Path, keyring: Path, fingerprint: str) -> list[str]:
    return [
        sys.executable,
        str(VALIDATE),
        str(repo),
        "--architectures",
        "aarch64",
        "--keyring",
        str(keyring),
        "--signing-fingerprint",
        fingerprint,
    ]


class RepositoryTest(unittest.TestCase):
    def test_signer_keeps_ci_passphrase_out_of_process_arguments(self) -> None:
        script = SIGN.read_text()
        self.assertIn("--passphrase-fd 0", script)
        self.assertNotIn('--passphrase "$CODEC_SIGNING_KEY_PASSPHRASE"', script)

    @unittest.skipUnless(shutil.which("gpg"), "requires gpg")
    def test_committed_public_keyring_matches_pinned_fingerprints(self) -> None:
        expected = {
            line.split("=", 1)[1].strip().upper()
            for line in (KEYS / "codec-archive-keyring-v1.fingerprints").read_text().splitlines()
            if "=" in line
        }
        self.assertEqual(len(expected), 2)
        for key in (
            KEYS / "codec-archive-keyring-v1.gpg",
            KEYS / "codec-archive-keyring-v1.asc",
        ):
            result = subprocess.run(
                ["gpg", "--batch", "--show-keys", "--with-colons", "--fingerprint", "--fingerprint", str(key)],
                text=True,
                capture_output=True,
            )
            self.assertEqual(result.returncode, 0, result.stderr)
            records = result.stdout.splitlines()
            self.assertFalse(any(line.startswith(("sec:", "ssb:")) for line in records))
            actual = {
                line.split(":")[9].upper()
                for line in records
                if line.startswith("fpr:")
            }
            self.assertEqual(actual, expected)

    def test_generates_and_validates_apt_tree(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            debs = root / "debs"
            debs.mkdir()
            make_deb(root, debs / "codec-demo_1:0_aarch64.deb")
            repo = root / "repo"
            subprocess.run(
                [sys.executable, str(GENERATE), str(debs), str(repo), "--architectures", "aarch64"],
                check=True,
            )
            subprocess.run(
                [sys.executable, str(VALIDATE), str(repo), "--architectures", "aarch64"],
                check=True,
            )
            manifest = json.loads((repo / "repository.json").read_text())
            self.assertEqual(manifest["package"], "com.codeci.ide")
            self.assertEqual(manifest["prefix"], "/data/data/com.codeci.ide/files/usr")
            release_text = (repo / "dists/stable/Release").read_text()
            self.assertIn(" main/binary-aarch64/Packages", release_text)
            self.assertNotIn(" dists/stable/main/", release_text)
            published_debs = list((repo / "dists/stable/main/binary-aarch64").glob("*.deb"))
            self.assertEqual(len(published_debs), 1)
            self.assertNotIn(":", published_debs[0].name)
            packages = repo / "dists/stable/main/binary-aarch64/Packages"
            self.assertIn("SHA256:", packages.read_text())
            self.assertEqual(gzip.decompress((packages.parent / "Packages.gz").read_bytes()), packages.read_bytes())

    @unittest.skipUnless(shutil.which("gpg") and shutil.which("gpgv"), "requires gpg and gpgv")
    def test_signed_repository_verifies_with_required_key(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            debs = root / "debs"
            debs.mkdir()
            make_deb(root, debs / "codec-demo_1.0_aarch64.deb")
            repo = root / "repo"
            subprocess.run(
                [sys.executable, str(GENERATE), str(debs), str(repo), "--architectures", "aarch64"],
                check=True,
            )
            keyring, fingerprint = make_signed_repository(root, repo)
            result = subprocess.run(
                signed_validator(repo, keyring, fingerprint), text=True, capture_output=True
            )
            self.assertEqual(result.returncode, 0, result.stderr)
            self.assertIn("signed", result.stdout)
            self.assertTrue((repo / "dists/stable/InRelease").is_file())
            self.assertTrue((repo / "dists/stable/Release.gpg").is_file())

    @unittest.skipUnless(shutil.which("gpg") and shutil.which("gpgv"), "requires gpg and gpgv")
    def test_signed_validation_rejects_missing_or_tampered_metadata(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            debs = root / "debs"
            debs.mkdir()
            make_deb(root, debs / "codec-demo_1.0_aarch64.deb")
            repo = root / "repo"
            subprocess.run(
                [sys.executable, str(GENERATE), str(debs), str(repo), "--architectures", "aarch64"],
                check=True,
            )
            keyring, fingerprint = make_signed_repository(root, repo)
            inrelease = repo / "dists/stable/InRelease"
            inrelease.unlink()
            missing = subprocess.run(
                signed_validator(repo, keyring, fingerprint), text=True, capture_output=True
            )
            self.assertNotEqual(missing.returncode, 0)
            self.assertIn("missing signed repository metadata", missing.stderr)

            # Re-sign, then alter Release and its unsigned sidecar. A sidecar
            # from the same compromised host must never substitute for OpenPGP.
            subprocess.run(
                [str(SIGN), str(repo), fingerprint],
                env=dict(
                    os.environ,
                    GNUPGHOME=str(root / "gnupg"),
                    CODEC_SIGNING_KEY_PASSPHRASE=TEST_PASSPHRASE,
                ),
                check=True,
            )
            release = repo / "dists/stable/Release"
            release.write_text(release.read_text() + "X-Tampered: yes\n")
            sidecar = release.with_name("Release.sha256")
            sidecar.write_text(f"{hashlib.sha256(release.read_bytes()).hexdigest()}  Release\n")
            tampered = subprocess.run(
                signed_validator(repo, keyring, fingerprint), text=True, capture_output=True
            )
            self.assertNotEqual(tampered.returncode, 0)
            self.assertIn("InRelease cleartext does not exactly match Release", tampered.stderr)

    @unittest.skipUnless(shutil.which("gpg") and shutil.which("gpgv"), "requires gpg and gpgv")
    def test_signed_validation_rejects_changed_packages_index(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            debs = root / "debs"
            debs.mkdir()
            make_deb(root, debs / "codec-demo_1.0_aarch64.deb")
            repo = root / "repo"
            subprocess.run(
                [sys.executable, str(GENERATE), str(debs), str(repo), "--architectures", "aarch64"],
                check=True,
            )
            keyring, fingerprint = make_signed_repository(root, repo)
            packages = repo / "dists/stable/main/binary-aarch64/Packages"
            packages.write_text(packages.read_text() + "# changed\n")
            result = subprocess.run(
                signed_validator(repo, keyring, fingerprint), text=True, capture_output=True
            )
            self.assertNotEqual(result.returncode, 0)
            self.assertIn("Release SHA256 mismatch", result.stderr)

    def test_rejects_maintainer_scripts(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            debs = root / "debs"
            debs.mkdir()
            make_deb(root, debs / "unsafe_1.0_aarch64.deb", name="unsafe", script=True)
            result = subprocess.run(
                [sys.executable, str(GENERATE), str(debs), str(root / "repo"), "--architectures", "aarch64"],
                text=True,
                capture_output=True,
            )
            self.assertNotEqual(result.returncode, 0)
            self.assertIn("maintainer scripts", result.stderr)

    def test_allows_only_reviewed_alternative_scripts(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            debs = root / "debs"
            debs.mkdir()
            for name in ("coreutils", "less", "nano"):
                make_deb(root, debs / f"{name}_1.0_aarch64.deb", name=name, script=True)
            subprocess.run(
                [sys.executable, str(GENERATE), str(debs), str(root / "repo"), "--architectures", "aarch64"],
                check=True,
            )

    def test_rejects_wrong_prefix_and_traversal_helper(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            debs = root / "debs"
            debs.mkdir()
            package_root = root / "bad-aarch64"
            data = package_root / "data/data/com.termux/files/usr/bin"
            data.mkdir(parents=True)
            (data / "bad").write_text("bad")
            control = package_root / "DEBIAN"
            control.mkdir()
            (control / "control").write_text(
                "Package: bad\nVersion: 1.0\nArchitecture: aarch64\nDescription: bad\n"
            )
            make = subprocess.run(
                ["dpkg-deb", "--build", str(package_root), str(debs / "bad.deb")],
                text=True,
                capture_output=True,
            )
            self.assertEqual(make.returncode, 0, make.stderr)
            result = subprocess.run(
                [sys.executable, str(GENERATE), str(debs), str(root / "repo"), "--architectures", "aarch64"],
                text=True,
                capture_output=True,
            )
            self.assertNotEqual(result.returncode, 0)
            self.assertRegex(result.stderr, "prefix|Termux")

            sys.path.insert(0, str(SCRIPTS))
            from repository_lib import PackageError, _safe_relative

            with self.assertRaises(PackageError):
                _safe_relative("./data/data/com.codeci.ide/files/usr/../escape", package=Path("x.deb"))

    def test_validation_accepts_relative_repository_path(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            debs = root / "debs"
            debs.mkdir()
            make_deb(root, debs / "relative_1.0_aarch64.deb", name="relative")
            repo = root / "repo"
            subprocess.run(
                [sys.executable, str(GENERATE), str(debs), str(repo), "--architectures", "aarch64"],
                check=True,
            )
            subprocess.run(
                [sys.executable, str(VALIDATE), "repo", "--architectures", "aarch64"],
                cwd=root,
                check=True,
            )

    def test_validation_detects_changed_package(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            debs = root / "debs"
            debs.mkdir()
            deb = make_deb(root, debs / "codec-demo_1.0_aarch64.deb")
            repo = root / "repo"
            subprocess.run(
                [sys.executable, str(GENERATE), str(debs), str(repo), "--architectures", "aarch64"],
                check=True,
            )
            published = next((repo / "dists/stable/main/binary-aarch64").glob("*.deb"))
            with published.open("ab") as stream:
                stream.write(b"changed")
            result = subprocess.run(
                [sys.executable, str(VALIDATE), str(repo), "--architectures", "aarch64"],
                text=True,
                capture_output=True,
            )
            self.assertNotEqual(result.returncode, 0)
            self.assertIn("mismatch", result.stderr.lower())


if __name__ == "__main__":
    unittest.main()
