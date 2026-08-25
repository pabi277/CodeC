#!/usr/bin/env bash
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
            recipe_dir = tree / "packages" / "xorg-util-macros"
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
                "https://ftp.x.org/pub/individual/util/util-macros-",
                text,
            )
            self.assertNotIn("https://xorg.freedesktop.org/releases/", text)
            self.assertNotIn("https://www.x.org/releases/", text)

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
                "deb [signed-by=/data/data/com.codeci.ide/files/usr/etc/apt/keyrings/"
                "codec-archive-keyring-v1.gpg] https://pabi277.github.io/CodeC/dev stable main",
                apt_text,
            )
            self.assertNotIn("trusted=yes", apt_text)

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
            (tree / "packages" / "xorg-util-macros").mkdir(parents=True)
            (tree / "packages" / "xorg-util-macros" / "build.sh").write_text(
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

    # ------------------------------------------------------------------
    # Round 2 catalog (Part 4.5): the bash and git overrides.
    #
    # The apt recipe is mandatory for the script to reach the round 2
    # blocks (it exits 1 when absent); the other legacy fixtures are
    # optional and skipped when missing.
    # ------------------------------------------------------------------

    def _write_apt_fixture(self, tree: Path) -> Path:
        apt = tree / "packages" / "apt"
        apt.mkdir(parents=True)
        (apt / "build.sh").write_text(
            'TERMUX_PKG_DEPENDS="coreutils, dpkg, findutils, gpgv, grep, '
            'libandroid-glob, libbz2, libc++, libiconv, libgcrypt, '
            'libgnutls, liblz4, liblzma, sed, termux-keyring, '
            'termux-licenses, xxhash, zlib, zstd"\n'
            '\t\techo "# The main termux repository, with cloudflare cache"\n'
            '\t\techo "deb https://packages-cf.termux.dev/apt/termux-main/ stable main"\n'
            '\t\techo "# The main termux repository, without cloudflare cache"\n'
            '\t\techo "# deb https://packages.termux.dev/apt/termux-main/ stable main"\n'
        )
        return apt

    def test_bash_termux_tools_removed_for_repository_build(self) -> None:
        """bash (a round 2 dependency via libtool) must lose termux-tools
        in the repository build too, not only in the bootstrap build."""
        bash_depends = 'TERMUX_PKG_DEPENDS="libandroid-support, libiconv, readline (>= 8.3), termux-tools"\n'
        with tempfile.TemporaryDirectory() as tmp:
            tree = Path(tmp)
            self._write_apt_fixture(tree)
            bash = tree / "packages" / "bash"
            bash.mkdir(parents=True)
            (bash / "build.sh").write_text(bash_depends)

            subprocess.run([str(OVERRIDES), str(tree)], check=True, text=True)

            self.assertEqual((bash / "build.sh").read_text(),
                             'TERMUX_PKG_DEPENDS="libandroid-support, libiconv, readline (>= 8.3)"\n')
            self.assertNotIn("termux-tools", (bash / "build.sh").read_text())

    def test_git_subpackages_excluded_and_tcltk_disabled(self) -> None:
        """gitk/git-gui (tcl/tk/X11) and git-svn (subversion-perl) are
        excluded for CodeC arches; git builds without tcl/tk support."""
        with tempfile.TemporaryDirectory() as tmp:
            tree = Path(tmp)
            self._write_apt_fixture(tree)
            git_dir = tree / "packages" / "git"
            git_dir.mkdir(parents=True)
            (git_dir / "build.sh").write_text(
                'TERMUX_PKG_EXTRA_CONFIGURE_ARGS="\n'
                "ac_cv_fread_reads_directories=yes\n"
                "--with-curl\n"
                "--with-expat\n"
                "--with-shell=$TERMUX_PREFIX/bin/sh\n"
                "--with-tcltk=$TERMUX_PREFIX/bin/wish\n"
                '"\n'
            )
            (git_dir / "git-gitk.subpackage.sh").write_text(
                'TERMUX_SUBPKG_DESCRIPTION="Git repository browser"\n'
                'TERMUX_SUBPKG_DEPENDS="tk"\n'
            )
            (git_dir / "git-gui.subpackage.sh").write_text(
                'TERMUX_SUBPKG_DESCRIPTION="A graphical interface to Git"\n'
                'TERMUX_SUBPKG_DEPENDS="tk"\n'
            )
            (git_dir / "git-svn.subpackage.sh").write_text(
                'TERMUX_SUBPKG_DESCRIPTION="Convert between Git and Subversion repositories"\n'
                'TERMUX_SUBPKG_DEPENDS="subversion-perl"\n'
            )

            subprocess.run([str(OVERRIDES), str(tree)], check=True, text=True)

            build_text = (git_dir / "build.sh").read_text()
            self.assertIn("--with-tcltk=no", build_text)
            self.assertNotIn("--with-tcltk=$TERMUX_PREFIX/bin/wish", build_text)
            for sub in ("git-gitk", "git-gui", "git-svn"):
                lines = (git_dir / f"{sub}.subpackage.sh").read_text().splitlines()
                self.assertEqual(
                    lines[0],
                    'TERMUX_SUBPKG_EXCLUDED_ARCHES="aarch64 x86_64" '
                    "# CodeC: no tcl/tk/X11 or subversion in the userland",
                )

    def test_git_override_fails_loud_on_pinned_revision_drift(self) -> None:
        """A missing git subpackage file means the pinned recipe changed;
        the build must abort instead of silently skipping the review."""
        with tempfile.TemporaryDirectory() as tmp:
            tree = Path(tmp)
            self._write_apt_fixture(tree)
            git_dir = tree / "packages" / "git"
            git_dir.mkdir(parents=True)
            (git_dir / "build.sh").write_text("--with-tcltk=$TERMUX_PREFIX/bin/wish\n")
            (git_dir / "git-gitk.subpackage.sh").write_text('TERMUX_SUBPKG_DEPENDS="tk"\n')
            (git_dir / "git-gui.subpackage.sh").write_text('TERMUX_SUBPKG_DEPENDS="tk"\n')

            result = subprocess.run(
                [str(OVERRIDES), str(tree)], text=True, capture_output=True
            )
            self.assertNotEqual(result.returncode, 0)
            self.assertIn("pinned-revision drift", result.stderr)
            self.assertIn("git-svn", result.stderr)

    def test_symlink_override_patches_termux_step_massage(self) -> None:
        """termux_step_massage.sh and libbz2/build.sh must be patched to convert
        absolute symlinks in $TERMUX_PREFIX into relative symlinks."""
        with tempfile.TemporaryDirectory() as tmp:
            tree = Path(tmp)
            self._write_apt_fixture(tree)
            
            libbz2_dir = tree / "packages" / "libbz2"
            libbz2_dir.mkdir(parents=True)
            libbz2_build = libbz2_dir / "build.sh"
            libbz2_build.write_text("termux_step_make_install() {\n\tmake install\n}\n")

            scripts_build = tree / "scripts" / "build"
            scripts_build.mkdir(parents=True)
            massage = scripts_build / "termux_step_massage.sh"
            massage.write_text(
                "termux_step_massage() {\n"
                "\techo 'hello'\n"
                '\tif [ "$TERMUX_PACKAGE_FORMAT" = "debian" ]; then\n'
                "\t\ttermux_create_debian_subpackages\n"
                "\tfi\n"
                "}\n"
            )

            subprocess.run([str(OVERRIDES), str(tree)], check=True, text=True)

            text = massage.read_text()
            self.assertIn("CodeC override: convert absolute symlinks", text)
            self.assertIn("realpath -m --relative-to", text)
            self.assertIn("CodeC override: remove maintainer scripts for non-whitelisted packages", text)
            symlink_idx = text.find("CodeC override: convert absolute symlinks")
            subpkg_idx = text.find("termux_create_debian_subpackages")
            self.assertLess(symlink_idx, subpkg_idx)

            libbz2_text = libbz2_build.read_text()
            self.assertIn("CodeC: fix absolute symlinks in libbz2", libbz2_text)
            self.assertIn("realpath -m --relative-to", libbz2_text)

    def test_debscripts_override_patches_termux_step_create_debscripts(self) -> None:
        """termux_step_create_debscripts.sh is patched to disable maintainer scripts
        for non-whitelisted packages."""
        with tempfile.TemporaryDirectory() as tmp:
            tree = Path(tmp)
            self._write_apt_fixture(tree)

            scripts_build = tree / "scripts" / "build"
            scripts_build.mkdir(parents=True)
            debscripts = scripts_build / "termux_step_create_debscripts.sh"
            debscripts.write_text(
                "termux_step_create_debscripts() {\n"
                "\techo 'creating debscripts'\n"
                "}\n"
            )

            subprocess.run([str(OVERRIDES), str(tree)], check=True, text=True)

            text = debscripts.read_text()
            self.assertIn('case "${TERMUX_PKG_NAME:-}" in', text)
            self.assertIn('coreutils|less|nano|bat|util-linux)', text)

    def test_xcb_proto_python_subpackages_excluded(self) -> None:
        """python-xcbgen subpackage in xcb-proto must be excluded and xcb-proto
        debscripts disabled to prevent unapproved maintainer scripts in CodeC repository."""
        with tempfile.TemporaryDirectory() as tmp:
            tree = Path(tmp)
            self._write_apt_fixture(tree)

            xcb_proto_dir = tree / "packages" / "xcb-proto"
            xcb_proto_dir.mkdir(parents=True)
            (xcb_proto_dir / "build.sh").write_text('TERMUX_PKG_HOMEPAGE=https://xorg.freedesktop.org\n')
            subpkg = xcb_proto_dir / "python-xcbgen.subpackage.sh"
            subpkg.write_text('TERMUX_SUBPKG_DESCRIPTION="Python bindings for xcb-proto"\n')

            subprocess.run([str(OVERRIDES), str(tree)], check=True, text=True)

            lines = subpkg.read_text().splitlines()
            self.assertEqual(
                lines[0],
                'TERMUX_SUBPKG_EXCLUDED_ARCHES="aarch64 x86_64" '
                "# CodeC: no python/X11 bindings in userland",
            )
            build_text = (xcb_proto_dir / "build.sh").read_text()
            self.assertIn("termux_step_create_debscripts() { :; }", build_text)

    def test_rxvt_unicode_uses_debian_download_mirror(self) -> None:
        """The dist.schmorp.de timeout host is replaced with Debian CDN mirror."""
        with tempfile.TemporaryDirectory() as tmp:
            tree = Path(tmp)
            self._write_apt_fixture(tree)

            rxvt_dir = tree / "packages" / "rxvt-unicode"
            rxvt_dir.mkdir(parents=True)
            recipe = rxvt_dir / "build.sh"
            recipe.write_text(
                'TERMUX_PKG_SRCURL=https://dist.schmorp.de/rxvt-unicode/Attic/'
                'rxvt-unicode-${TERMUX_PKG_VERSION}.tar.bz2\n'
            )

            subprocess.run([str(OVERRIDES), str(tree)], check=True, text=True)

            text = recipe.read_text()
            self.assertIn(
                "https://deb.debian.org/debian/pool/main/r/rxvt-unicode/rxvt-unicode_",
                text,
            )
            self.assertNotIn("dist.schmorp.de", text)


if __name__ == "__main__":
    unittest.main()
