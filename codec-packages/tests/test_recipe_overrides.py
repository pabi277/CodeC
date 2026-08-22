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
            apt = tree / "packages" / "apt"
            apt.mkdir(parents=True)
            (apt / "build.sh").write_text(
                '\t\techo "# The main termux repository, with cloudflare cache"\n'
                '\t\techo "deb https://packages-cf.termux.dev/apt/termux-main/ stable main"\n'
                '\t\techo "# The main termux repository, without cloudflare cache"\n'
                '\t\techo "# deb https://packages.termux.dev/apt/termux-main/ stable main"\n'
            )

            subprocess.run([str(OVERRIDES), str(tree)], check=True, text=True)

            text = recipe.read_text()
            self.assertIn(
                "https://www.x.org/releases/individual/util/util-macros-",
                text,
            )
            self.assertNotIn("https://xorg.freedesktop.org/releases/", text)


if __name__ == "__main__":
    unittest.main()
