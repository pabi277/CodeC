#!/usr/bin/env python3
"""Hermetic coverage for the narrowly scoped recipe transport overrides."""

from __future__ import annotations

import subprocess
import tempfile
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
OVERRIDES = ROOT / "scripts" / "apply-recipe-overrides.sh"


class RecipeOverrideTest(unittest.TestCase):
    def test_util_macros_uses_xorg_download_mirror(self) -> None:
        """The CI timeout host is replaced without changing the source file."""
        with tempfile.TemporaryDirectory() as tmp:
            tree = Path(tmp)
            recipe_dir = tree / "packages" / "util-macros"
            recipe_dir.mkdir(parents=True)
            recipe = recipe_dir / "build.sh"
            recipe.write_text(
                'TERMUX_PKG_SRCURL=https://xorg.freedesktop.org/releases/individual/'
                'util/util-macros-${TERMUX_PKG_VERSION}.tar.xz\n'
            )

            # The other required recipe fixtures exercise their existing paths
            # while keeping this test focused on the util-macros rewrite.
            for package in ("attr", "libacl"):
                path = tree / "packages" / package
                path.mkdir(parents=True)
                (path / "build.sh").write_text('TERMUX_PKG_SRCURL=https://example.invalid/source\n')
            dpkg = tree / "packages" / "dpkg"
            dpkg.mkdir(parents=True)
            (dpkg / "dpkg-perl.subpackage.sh").write_text(
                'TERMUX_SUBPKG_DEPENDS="perl, clang, make"\n'
            )
            # Verbatim TERMUX_PKG_DEPENDS from the pinned upstream revision
            # (termux-packages @ 1bbe66903526df2e8af51e704316bc68ede72603,
            # packages/apt/build.sh line 11).
            apt_depends = (
                "TERMUX_PKG_DEPENDS=\"coreutils, dpkg, findutils, gpgv, grep, "
                "libandroid-glob, libbz2, libc++, libiconv, libgcrypt, "
                "libgnutls, liblz4, liblzma, sed, termux-keyring, "
                "termux-licenses, xxhash, zlib, zstd\"\n"
            )
            apt = tree / "packages" / "apt"
            apt.mkdir(parents=True)
            (apt / "build.sh").write_text(
                apt_depends
                + '\t\techo "# The main termux repository, with cloudflare cache"\n'
                + '\t\techo "deb https://packages-cf.termux.dev/apt/termux-main/ stable main"\n'
                + '\t\techo "# The main termux repository, without cloudflare cache"\n'
                + '\t\techo "# deb https://packages.termux.dev/apt/termux-main/ stable main"\n'
            )

            subprocess.run([str(OVERRIDES), str(tree)], check=True, text=True)

            text = recipe.read_text()
            self.assertIn(
                "https://www.x.org/releases/individual/util/util-macros-",
                text,
            )
            self.assertNotIn("https://xorg.freedesktop.org/releases/", text)

            # Part B (2026-08-23): exactly `termux-keyring` is removed from
            # apt's runtime dependencies; every other dependency stays
            # byte-identical, and termux-licenses survives (it provides
            # $PREFIX/share/LICENSES/*, the target of packaged license
            # symlinks such as nano's share/licenses/nano).
            apt_text = (apt / "build.sh").read_text()
            self.assertNotIn("termux-keyring", apt_text)
            self.assertIn(
                "TERMUX_PKG_DEPENDS=\"coreutils, dpkg, findutils, gpgv, grep, "
                "libandroid-glob, libbz2, libc++, libiconv, libgcrypt, "
                "libgnutls, liblz4, liblzma, sed, "
                "termux-licenses, xxhash, zlib, zstd\"",
                apt_text,
            )
            # The sources.list rewrite still applies to the same recipe.
            self.assertIn(
                "deb https://pabi277.github.io/CodeC/dev stable main",
                apt_text,
            )

    def test_apt_override_fails_loud_without_termux_keyring(self) -> None:
        """A pinned-recipe drift that drops the expected dependency line
        shape must abort the build instead of silently skipping."""
        with tempfile.TemporaryDirectory() as tmp:
            tree = Path(tmp)
            for package in ("attr", "libacl"):
                path = tree / "packages" / package
                path.mkdir(parents=True)
                (path / "build.sh").write_text(
                    'TERMUX_PKG_SRCURL=https://example.invalid/source\n'
                )
            (tree / "packages" / "util-macros").mkdir(parents=True)
            (tree / "packages" / "util-macros" / "build.sh").write_text(
                'TERMUX_PKG_SRCURL=https://xorg.freedesktop.org/releases/'
                'individual/util/util-macros-${TERMUX_PKG_VERSION}.tar.xz\n'
            )
            dpkg = tree / "packages" / "dpkg"
            dpkg.mkdir(parents=True)
            (dpkg / "dpkg-perl.subpackage.sh").write_text(
                'TERMUX_SUBPKG_DEPENDS="perl, clang, make"\n'
            )
            apt = tree / "packages" / "apt"
            apt.mkdir(parents=True)
            (apt / "build.sh").write_text(
                'TERMUX_PKG_DEPENDS="coreutils, dpkg"\n'
                '\t\techo "# The main termux repository, with cloudflare cache"\n'
                '\t\techo "deb https://packages-cf.termux.dev/apt/termux-main/ stable main"\n'
                '\t\techo "# The main termux repository, without cloudflare cache"\n'
                '\t\techo "# deb https://packages.termux.dev/apt/termux-main/ stable main"\n'
            )

            result = subprocess.run(
                [str(OVERRIDES), str(tree)], text=True, capture_output=True
            )
            self.assertNotEqual(result.returncode, 0)
            self.assertIn("termux-keyring", result.stderr)


if __name__ == "__main__":
    unittest.main()
