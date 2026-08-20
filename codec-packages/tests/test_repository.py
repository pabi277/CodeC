#!/usr/bin/env python3
from __future__ import annotations

import gzip
import json
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path

SCRIPTS = Path(__file__).resolve().parents[1] / "scripts"
GENERATE = SCRIPTS / "generate-repository.py"
VALIDATE = SCRIPTS / "validate-repository.py"


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


class RepositoryTest(unittest.TestCase):
    def test_generates_and_validates_apt_tree(self) -> None:
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
            subprocess.run(
                [sys.executable, str(VALIDATE), str(repo), "--architectures", "aarch64"],
                check=True,
            )
            manifest = json.loads((repo / "repository.json").read_text())
            self.assertEqual(manifest["package"], "com.codeci.ide")
            self.assertEqual(manifest["prefix"], "/data/data/com.codeci.ide/files/usr")
            self.assertTrue((repo / "dists/stable/Release").is_file())
            packages = repo / "dists/stable/main/binary-aarch64/Packages"
            self.assertIn("SHA256:", packages.read_text())
            self.assertEqual(gzip.decompress((packages.parent / "Packages.gz").read_bytes()), packages.read_bytes())

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
