#!/usr/bin/env python3
"""Host tests for codec-packages/scripts/repair-bootstrap-status.sh.

The published userland-v2-dev bootstrap predates the dpkg-perl recipe fix and
seeds `Depends: perl, clang, make` into var/lib/dpkg/status. The repair
script must fix exactly that one line, prove nothing else changed, and refuse
on unexpected evidence. These tests exercise it against synthetic archives
(the full validate-bootstrap gate runs in the repair workflow on real
artifacts, so tests pass --skip-validate).
"""

from __future__ import annotations

import hashlib
import os
import shutil
import stat
import subprocess
import tarfile
import tempfile
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
SCRIPT = ROOT / "codec-packages" / "scripts" / "repair-bootstrap-status.sh"
ARCHIVE_NAME = "bootstrap-phase3-aarch64.tar.gz"

# The real seeded shape, from device evidence on the published bootstrap:
# the recipe appends a versioned cross-dependency after "make".
STALE_DEPENDS = "Depends: perl, clang, make, dpkg (= 1.22.6-5)"
FIXED_DEPENDS = "Depends: perl, make, dpkg (= 1.22.6-5)"
# A shorter variant (no versioned tail) must be repaired identically.
SHORT_STALE_DEPENDS = "Depends: perl, clang, make"
SHORT_FIXED_DEPENDS = "Depends: perl, make"

BASH_STANZA = (
    "Package: bash\n"
    "Version: 5.2.15\n"
    "Architecture: aarch64\n"
    "Maintainer: CodeC\n"
    "Depends: libandroid-support, ncurses\n"
    "Status: install ok installed\n"
)

DPKG_PERL_STANZA_TEMPLATE = (
    "Package: dpkg-perl\n"
    "Version: 1.22.6\n"
    "Architecture: aarch64\n"
    "Maintainer: CodeC\n"
    "{depends}\n"
    "Description: perl scripts for dpkg\n"
    "Status: install ok installed\n"
)

MAKE_STANZA = (
    "Package: make\n"
    "Version: 4.4\n"
    "Architecture: aarch64\n"
    "Maintainer: CodeC\n"
    "Status: install ok installed\n"
)


def write_status(root: Path, dpkg_perl_depends: str = STALE_DEPENDS) -> None:
    status = root / "var" / "lib" / "dpkg" / "status"
    status.parent.mkdir(parents=True, exist_ok=True)
    stanza = DPKG_PERL_STANZA_TEMPLATE.format(depends=dpkg_perl_depends)
    # Same canonical shape the assembler leaves behind: stanzas separated by
    # exactly one blank line, file ends with a blank line.
    status.write_text(BASH_STANZA + "\n" + stanza + "\n" + MAKE_STANZA + "\n")


def make_archive(work: Path, dpkg_perl_depends: str | None = STALE_DEPENDS,
                 extra_status_text: str = "") -> Path:
    """Build a minimal bootstrap-shaped tar.gz."""
    stage = work / "stage"
    if stage.exists():
        shutil.rmtree(stage)
    stage.mkdir(parents=True)
    (stage / "bin").mkdir()
    shell = stage / "bin" / "bash"
    shell.write_bytes(b"\x7fELF-fake-but-present")
    shell.chmod(shell.stat().st_mode | stat.S_IXUSR | stat.S_IXGRP | stat.S_IXOTH)
    if dpkg_perl_depends is not None:
        write_status(stage, dpkg_perl_depends)
        if extra_status_text:
            status = stage / "var" / "lib" / "dpkg" / "status"
            status.write_text(status.read_text() + extra_status_text)
    archive = work / ARCHIVE_NAME
    if archive.exists():
        archive.unlink()
    result = subprocess.run(
        ["tar", "-czf", str(archive), "-C", str(stage), "."],
        capture_output=True, text=True,
    )
    assert result.returncode == 0, result.stderr
    return archive


def run_repair(work: Path, archive: Path) -> subprocess.CompletedProcess:
    outdir = work / "out"
    outdir.mkdir(exist_ok=True)
    return subprocess.run(
        ["bash", str(SCRIPT), str(outdir), str(archive), "--skip-validate"],
        capture_output=True, text=True,
    )


def extract_status(archive: Path, dest: Path) -> str:
    with tarfile.open(archive, "r:gz") as tf:
        member = tf.extractfile("./var/lib/dpkg/status")
        assert member is not None
        return member.read().decode()


class RepairBootstrapStatusTest(unittest.TestCase):

    def setUp(self) -> None:
        self.work = Path(tempfile.mkdtemp(prefix="repair-test-"))
        self.addCleanup(shutil.rmtree, self.work, True)

    def test_patches_exactly_one_line_and_regenerates_sidecar(self) -> None:
        archive = make_archive(self.work)
        before_bytes = archive.read_bytes()
        result = run_repair(self.work, archive)
        self.assertEqual(result.returncode, 0, result.stderr + result.stdout)

        # Original archive is untouched; the repair lands in the output dir.
        self.assertEqual(archive.read_bytes(), before_bytes)
        repaired = self.work / "out" / ARCHIVE_NAME
        self.assertTrue(repaired.exists())
        sidecar = Path(str(repaired) + ".sha256")
        self.assertTrue(sidecar.exists())

        digest = hashlib.sha256(repaired.read_bytes()).hexdigest()
        token, _, name = sidecar.read_text().partition(" ")
        self.assertEqual(token, digest)
        self.assertEqual(name.strip(), ARCHIVE_NAME)

        fixed_status = extract_status(repaired, self.work)
        self.assertIn(FIXED_DEPENDS, fixed_status)
        self.assertNotIn("clang", fixed_status)
        # All other stanza bytes survive verbatim.
        self.assertIn(BASH_STANZA, fixed_status)
        self.assertIn(MAKE_STANZA, fixed_status)

    def test_same_members_and_modes(self) -> None:
        archive = make_archive(self.work)
        result = run_repair(self.work, archive)
        self.assertEqual(result.returncode, 0, result.stderr + result.stdout)
        repaired = self.work / "out" / ARCHIVE_NAME

        def members(path: Path) -> dict:
            with tarfile.open(path, "r:gz") as tf:
                return {
                    m.name: (m.mode, stat.S_IFMT(m.mode) if m.isdir() else None)
                    for m in tf.getmembers()
                }

        original = members(archive)
        patched = members(repaired)
        self.assertEqual(sorted(original), sorted(patched))
        for name in original:
            self.assertEqual(original[name][0], patched[name][0],
                             f"mode drifted on {name}")

    def test_short_form_repaired_identically(self) -> None:
        short = self.work / "short"
        short.mkdir()
        archive = make_archive(short, dpkg_perl_depends=SHORT_STALE_DEPENDS)
        result = run_repair(short, archive)
        self.assertEqual(result.returncode, 0, result.stderr + result.stdout)
        fixed_status = extract_status(short / "out" / ARCHIVE_NAME, short)
        self.assertIn(SHORT_FIXED_DEPENDS, fixed_status)
        self.assertNotIn("clang", fixed_status)

    def test_idempotent_refusal_when_already_clean(self) -> None:
        clean = self.work / "clean"
        clean.mkdir()
        archive = make_archive(clean, dpkg_perl_depends=FIXED_DEPENDS)
        result = run_repair(clean, archive)
        self.assertEqual(result.returncode, 3, result.stderr + result.stdout)
        self.assertIn("nothing to do", result.stdout)
        self.assertFalse((clean / "out" / ARCHIVE_NAME).exists())

    def test_refuses_unexpected_depends_shape(self) -> None:
        archive = make_archive(self.work, dpkg_perl_depends="Depends: perl, clang")
        result = run_repair(self.work, archive)
        self.assertEqual(result.returncode, 4, result.stderr + result.stdout)
        self.assertFalse((self.work / "out" / ARCHIVE_NAME).exists())

    def test_refuses_clang_elsewhere_in_status(self) -> None:
        sneaky = self.work / "sneaky"
        sneaky.mkdir()
        archive = make_archive(sneaky)
        status = sneaky / "stage" / "var" / "lib" / "dpkg" / "status"
        status.write_text(status.read_text() + "Package: clang-doc\nDepends: clang\nStatus: install ok installed\n\n")
        rebuilt = sneaky / ARCHIVE_NAME
        rebuilt.unlink()
        subprocess.run(["tar", "-czf", str(rebuilt), "-C", str(sneaky / "stage"), "."], check=True)
        result = run_repair(sneaky, rebuilt)
        self.assertEqual(result.returncode, 4, result.stderr + result.stdout)
        self.assertFalse((sneaky / "out" / ARCHIVE_NAME).exists())


if __name__ == "__main__":
    unittest.main()
