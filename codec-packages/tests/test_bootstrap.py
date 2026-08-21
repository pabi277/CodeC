#!/usr/bin/env python3
from __future__ import annotations

import hashlib
import io
import subprocess
import sys
import tarfile
import tempfile
import unittest
from pathlib import Path
from typing import Callable, Optional

SCRIPTS = Path(__file__).resolve().parents[1] / "scripts"
VALIDATE = SCRIPTS / "validate-bootstrap.py"

CODEC_REPO_LINE = "deb https://pabi277.github.io/CodeC/dev stable main"

DPKG_STATUS = (
    "Package: apt\n"
    "Status: install ok installed\n"
    "Architecture: aarch64\n"
    "Version: 2.8.1\n\n"
    "Package: dpkg\n"
    "Status: install ok installed\n"
    "Architecture: aarch64\n"
    "Version: 1.22.6\n\n"
    "Package: bash\n"
    "Status: install ok installed\n"
    "Architecture: aarch64\n"
    "Version: 5.2\n\n"
    "Package: busybox\n"
    "Status: install ok installed\n"
    "Architecture: aarch64\n"
    "Version: 1.36.1\n\n"
)

ELF = b"\x7fELF" + b"\x02\x01\x01\x00" * 8


def _add_file(tar: tarfile.TarFile, name: str, data: bytes, mode: int = 0o755) -> None:
    info = tarfile.TarInfo(name)
    info.size = len(data)
    info.mode = mode
    tar.addfile(info, io.BytesIO(data))


def _add_dir(tar: tarfile.TarFile, name: str) -> None:
    info = tarfile.TarInfo(name + "/")
    info.type = tarfile.DIRTYPE
    info.mode = 0o755
    tar.addfile(info)


def _add_symlink(tar: tarfile.TarFile, name: str, target: str) -> None:
    info = tarfile.TarInfo(name)
    info.type = tarfile.SYMTYPE
    info.linkname = target
    tar.addfile(info)


def make_bootstrap(
    path: Path,
    *,
    arch: str = "aarch64",
    sources_list: str = "# CodeC development package repository (CodeC packages only).\n" + CODEC_REPO_LINE + "\n",
    status: str = DPKG_STATUS,
    omit: Optional[set] = None,
    extra_members: Optional[Callable[[tarfile.TarFile], None]] = None,
) -> Path:
    omit = omit or set()
    with tarfile.open(path, "w:gz") as tar:
        _add_dir(tar, "bin")
        _add_dir(tar, "lib")
        _add_dir(tar, "etc")
        _add_dir(tar, "var")
        _add_dir(tar, "var/lib")
        _add_dir(tar, "var/lib/dpkg")
        if "bin/bash" not in omit:
            _add_file(tar, "bin/bash", ELF + b"bash-payload")
        if "bin/busybox" not in omit:
            _add_file(tar, "bin/busybox", ELF + b"busybox-payload")
        if "bin/apt-get" not in omit:
            _add_file(tar, "bin/apt-get", ELF + b"apt-get-payload")
        if "bin/dpkg" not in omit:
            _add_file(tar, "bin/dpkg", ELF + b"dpkg-payload")
        if "bin/apt" not in omit:
            _add_symlink(tar, "bin/apt", "apt-get")
        if "lib/libtermux-exec-ld-preload.so" not in omit:
            _add_file(tar, "lib/libtermux-exec-ld-preload.so", b"PRELOADLIB")
        if "lib/libandroid-support.so" not in omit:
            _add_file(tar, "lib/libandroid-support.so", b"ANDROIDLIB")
        if "etc/apt" not in omit:
            _add_dir(tar, "etc/apt")
            if "etc/apt/sources.list" not in omit:
                _add_file(tar, "etc/apt/sources.list", sources_list.encode(), mode=0o644)
        if "var/lib/dpkg/status" not in omit:
            _add_file(tar, "var/lib/dpkg/status", status.encode(), mode=0o644)
        if "var/lib/dpkg/arch" not in omit:
            _add_file(tar, "var/lib/dpkg/arch", (arch + "\n").encode(), mode=0o644)
        if extra_members is not None:
            extra_members(tar)
    digest = hashlib.sha256(path.read_bytes()).hexdigest()
    path.with_name(path.name + ".sha256").write_text(f"{digest}  {path.name}\n")
    return path


def run_validator(*archives: Path) -> "subprocess.CompletedProcess[str]":
    return subprocess.run(
        [sys.executable, str(VALIDATE), *[str(path) for path in archives]],
        text=True,
        capture_output=True,
    )


class BootstrapValidationTest(unittest.TestCase):
    def _archive(self, root: Path, **kwargs) -> Path:
        root.mkdir(parents=True, exist_ok=True)
        path = root / f"bootstrap-phase3-{kwargs.get('arch', 'aarch64')}.tar.gz"
        return make_bootstrap(path, **kwargs)

    def test_valid_bootstrap_passes(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            archive = self._archive(root)
            result = run_validator(archive)
            self.assertEqual(result.returncode, 0, result.stderr)
            self.assertIn("validated bootstrap", result.stdout)

    def test_rejects_wrong_archive_name(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            archive = make_bootstrap(root / "bootstrap-aarch64.tar.gz")
            result = run_validator(archive)
            self.assertNotEqual(result.returncode, 0)
            self.assertIn("named", result.stderr)

    def test_rejects_missing_sidecar(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            archive = self._archive(root)
            archive.with_name(archive.name + ".sha256").unlink()
            result = run_validator(archive)
            self.assertNotEqual(result.returncode, 0)
            self.assertIn("sidecar", result.stderr)

    def test_rejects_sidecar_mismatch(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            archive = self._archive(root)
            sidecar = archive.with_name(archive.name + ".sha256")
            sidecar.write_text("0" * 64 + "  " + archive.name + "\n")
            result = run_validator(archive)
            self.assertNotEqual(result.returncode, 0)
            self.assertIn("mismatch", result.stderr)

    def test_rejects_missing_required_members(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            for missing in (
                "bin/dpkg",
                "lib/libandroid-support.so",
                "var/lib/dpkg/status",
            ):
                archive = self._archive(root, omit={missing})
                result = run_validator(archive)
                self.assertNotEqual(result.returncode, 0, missing)
                self.assertIn("missing", result.stderr)

    def test_preload_absent_warns_but_passes(self) -> None:
        # The standalone termux-exec preload build is best-effort in CI; a
        # bootstrap without it must still validate (with a warning).
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            archive = self._archive(root, omit={"lib/libtermux-exec-ld-preload.so"})
            result = run_validator(archive)
            self.assertEqual(result.returncode, 0, result.stderr)
            self.assertIn("WARNING", result.stderr)
            self.assertIn("termux-exec", result.stderr)

    def test_rejects_non_elf_shell(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)

            def corrupt(tar: tarfile.TarFile) -> None:
                # Overwrite bin/bash with a shell script (the userland-v1 bug).
                _add_file(tar, "bin/bash", b"#!/system/bin/sh\nexec /system/bin/sh\n")

            archive = self._archive(root, extra_members=corrupt)
            result = run_validator(archive)
            self.assertNotEqual(result.returncode, 0)
            self.assertIn("ELF", result.stderr)

    def test_rejects_nested_android_paths(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)

            def nested(tar: tarfile.TarFile) -> None:
                _add_file(tar, "data/data/com.codeci.ide/files/usr/bin/evil", b"bad")

            archive = self._archive(root, extra_members=nested)
            result = run_validator(archive)
            self.assertNotEqual(result.returncode, 0)
            self.assertIn("data/data", result.stderr)

    def test_rejects_path_traversal(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)

            def traversal(tar: tarfile.TarFile) -> None:
                _add_file(tar, "../escape.txt", b"bad")

            archive = self._archive(root, extra_members=traversal)
            result = run_validator(archive)
            self.assertNotEqual(result.returncode, 0)
            self.assertIn("traversal", result.stderr)

    def test_rejects_termux_contamination(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)

            def contaminated(tar: tarfile.TarFile) -> None:
                _add_file(tar, "lib/libcom.termux.so", b"bad")

            archive = self._archive(root, extra_members=contaminated)
            result = run_validator(archive)
            self.assertNotEqual(result.returncode, 0)
            self.assertIn("forbidden", result.stderr)

    def test_rejects_official_termux_repository_url(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            sources = "deb https://packages-cf.termux.dev/apt/termux-main/ stable main\n"
            archive = self._archive(root, sources_list=sources)
            result = run_validator(archive)
            self.assertNotEqual(result.returncode, 0)
            self.assertIn("forbidden", result.stderr)

    def test_rejects_unsafe_symlink(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)

            def bad_link(tar: tarfile.TarFile) -> None:
                _add_symlink(tar, "bin/evil", "/system/bin/sh")

            archive = self._archive(root, extra_members=bad_link)
            result = run_validator(archive)
            self.assertNotEqual(result.returncode, 0)
            self.assertIn("symlink", result.stderr)

    def test_rejects_incomplete_dpkg_status(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            status = DPKG_STATUS.replace(
                "Package: dpkg\nStatus: install ok installed\n"
                "Architecture: aarch64\nVersion: 1.22.6\n\n", ""
            )
            archive = self._archive(root, status=status)
            result = run_validator(archive)
            self.assertNotEqual(result.returncode, 0)
            self.assertIn("dpkg", result.stderr)

    def test_rejects_half_configured_dpkg_status(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            status = DPKG_STATUS.replace(
                "Status: install ok installed", "Status: install ok half-configured"
            )
            archive = self._archive(root, status=status)
            result = run_validator(archive)
            self.assertNotEqual(result.returncode, 0)
            self.assertIn("does not mark apt as installed", result.stderr)

    def test_validates_multiple_archives(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            good = self._archive(root, arch="aarch64")
            broken = self._archive(root / "x86_64", arch="x86_64", omit={"bin/dpkg"})
            broken = broken.with_name(f"bootstrap-phase3-x86_64.tar.gz")
            result = run_validator(good, broken)
            self.assertNotEqual(result.returncode, 0)
            self.assertIn("x86_64", result.stderr)


if __name__ == "__main__":
    unittest.main()
