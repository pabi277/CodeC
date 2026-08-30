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
            symlink_idx = text.find("CodeC override: convert absolute symlinks")
            subpkg_idx = text.find("termux_create_debian_subpackages")
            self.assertLess(symlink_idx, subpkg_idx)
            # Regression (post-4.5/4.6 review): the massage layer must NOT try
            # to purge maintainer scripts — DEBIAN/ does not exist during
            # massage, and the previous attempt used variable names that do
            # not exist at the pinned revision (TERMUX_PKG_MASSAGEDDIR /
            # SUBPKG_MASSAGEDDIR), making it unreachable dead code.
            self.assertNotIn("TERMUX_PKG_MASSAGEDDIR", text)
            self.assertNotIn("SUBPKG_MASSAGEDDIR", text)
            self.assertNotIn("rm -f DEBIAN/postinst", text)

            libbz2_text = libbz2_build.read_text()
            self.assertIn("CodeC: fix absolute symlinks in libbz2", libbz2_text)
            self.assertIn("realpath -m --relative-to", libbz2_text)

    def _assert_stub_wins_at_source_time(self, script: Path, func: str, helper: str | None) -> None:
        """Source the patched step file the way build-package.sh does — at top
        level, under set -u, with TERMUX_PKG_NAME still unset (step files are
        sourced before any recipe is parsed) — and assert that sourcing
        succeeds and that the effective definition of `func` is the no-op
        stub, while sibling helpers remain defined. This is the runtime check
        the original text-only assertions lacked: a source-time
        `case "${TERMUX_PKG_NAME:-}"` guard always falls into its `*)` branch,
        which is why it was replaced by an unconditional end-of-file stub."""
        probe = subprocess.run(
            [
                "bash", "-c",
                "set -euo pipefail; "
                "unset TERMUX_PKG_NAME; "
                f"source '{script}'; "
                f"declare -f {func}; "
                + (f"declare -f {helper}; " if helper else "")
                + f"{func}; echo FUNC_RAN_OK",
            ],
            text=True, capture_output=True,
        )
        self.assertEqual(probe.returncode, 0, probe.stderr)
        self.assertIn("FUNC_RAN_OK", probe.stdout)
        body = probe.stdout.split("FUNC_RAN_OK")[0]
        # The effective body is exactly the stub: no real implementation leaked.
        self.assertNotIn("creating debscripts", body)
        self.assertNotIn("creating python debscripts", body)
        self.assertRegex(body, r"\{\s*:;?\s*\}")
        if helper:
            self.assertIn(f"{helper} ()", body)

    def test_debscripts_override_patches_termux_step_create_debscripts(self) -> None:
        """Maintainer-script generation is stubbed unconditionally: the stub is
        appended last (so it wins by bash's last-definition rule), the rest of
        the file stays sourced, and nothing is evaluated at source time."""
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
                "termux_step_create_debscripts__copy_from_dir() {\n"
                "\techo 'helper'\n"
                "}\n"
            )
            py_debscripts = scripts_build / "termux_step_create_python_debscripts.sh"
            py_debscripts.write_text(
                "termux_step_create_python_debscripts() {\n"
                "\techo 'creating python debscripts'\n"
                "}\n"
            )

            subprocess.run([str(OVERRIDES), str(tree)], check=True, text=True)

            text = debscripts.read_text()
            self.assertNotIn('case "${TERMUX_PKG_NAME', text)
            self.assertIn("CodeC: maintainer scripts forbidden for every package", text)
            self.assertTrue(text.rstrip().endswith("termux_step_create_debscripts() { :; }"))

            py_text = py_debscripts.read_text()
            self.assertNotIn('case "${TERMUX_PKG_NAME', py_text)
            self.assertIn("CodeC: no Python interpreter in the userland", py_text)
            self.assertTrue(py_text.rstrip().endswith("termux_step_create_python_debscripts() { :; }"))

            self._assert_stub_wins_at_source_time(
                debscripts,
                "termux_step_create_debscripts",
                "termux_step_create_debscripts__copy_from_dir",
            )
            self._assert_stub_wins_at_source_time(
                py_debscripts,
                "termux_step_create_python_debscripts",
                None,
            )

    def test_debscripts_stub_fails_loud_on_pinned_revision_drift(self) -> None:
        """If upstream restructures the step files so the expected function
        definition disappears, the override must refuse to proceed."""
        with tempfile.TemporaryDirectory() as tmp:
            tree = Path(tmp)
            self._write_apt_fixture(tree)

            scripts_build = tree / "scripts" / "build"
            scripts_build.mkdir(parents=True)
            (scripts_build / "termux_step_create_debscripts.sh").write_text(
                "# restructured upstream: no function definitions at all\n"
            )
            (scripts_build / "termux_step_create_python_debscripts.sh").write_text(
                "# restructured upstream: no function definitions at all\n"
            )

            result = subprocess.run(
                [str(OVERRIDES), str(tree)], text=True, capture_output=True
            )
            self.assertNotEqual(result.returncode, 0)
            self.assertIn("pinned-revision drift", result.stderr)

    def test_xcb_proto_python_subpackages_excluded(self) -> None:
        """python-xcbgen subpackage in xcb-proto must be excluded for the CodeC
        arches. The unapproved xcb-proto postinst scripts observed during
        Part 4.5 came from termux_step_create_python_debscripts, which the
        global stubs above disable; appending a per-recipe
        termux_step_create_debscripts stub was dead code (the pinned upstream
        function is already a no-op) and must not come back."""
        with tempfile.TemporaryDirectory() as tmp:
            tree = Path(tmp)
            self._write_apt_fixture(tree)

            xcb_proto_dir = tree / "packages" / "xcb-proto"
            xcb_proto_dir.mkdir(parents=True)
            build_sh = xcb_proto_dir / "build.sh"
            build_sh.write_text('TERMUX_PKG_HOMEPAGE=https://xorg.freedesktop.org\n')
            subpkg = xcb_proto_dir / "python-xcbgen.subpackage.sh"
            subpkg.write_text('TERMUX_SUBPKG_DESCRIPTION="Python bindings for xcb-proto"\n')

            subprocess.run([str(OVERRIDES), str(tree)], check=True, text=True)

            lines = subpkg.read_text().splitlines()
            self.assertEqual(
                lines[0],
                'TERMUX_SUBPKG_EXCLUDED_ARCHES="aarch64 x86_64" '
                "# CodeC: no python/X11 bindings in userland",
            )
            build_text = build_sh.read_text()
            self.assertNotIn("termux_step_create_debscripts", build_text)


    # ------------------------------------------------------------------
    # Phase 12 (round 3): the python recipe — tk / python-tkinter.
    #
    # The python recipe is optional for the earlier blocks (the apt fixture
    # is still mandatory), so it is asserted in its own focused tests.
    # ------------------------------------------------------------------

    def _write_apt_fixture_only(self, tree: Path) -> Path:
        """Minimal apt fixture for the python tests (the script exits 1 when
        the apt recipe is absent before reaching the python block)."""
        return self._write_apt_fixture(tree)

    def test_python_tkinter_excluded_and_tk_build_dep_removed(self) -> None:
        """python-tkinter (tcl/tk/X11) is excluded for CodeC arches, tk
        is removed from python's build-dependencies, the recipe's
        _tkinter post-massage verification is overridden (it can never
        pass without tk), and the recipe's own termux_step_create_debscripts
        (postinst) is neutralized — keeping the Phase 12 build out of the
        X11 closure and free of maintainer scripts."""
        with tempfile.TemporaryDirectory() as tmp:
            tree = Path(tmp)
            self._write_apt_fixture_only(tree)
            py_dir = tree / "packages" / "python"
            py_dir.mkdir(parents=True)
            (py_dir / "build.sh").write_text(
                'TERMUX_PKG_VERSION="3.14.6"\n'
                'TERMUX_PKG_DEPENDS="gdbm, openssl, readline, zlib"\n'
                'TERMUX_PKG_BUILD_DEPENDS="tk"\n'
                "\n"
                "termux_step_post_massage() {\n"
                "\t# Verify that desired modules have been included:\n"
                "\tfor module in _bz2 _curses _lzma _multiprocessing "
                "_sqlite3 _ssl _tkinter zlib _zstd; do\n"
                '\t\tif [ ! -f "${TERMUX_PREFIX}/lib/python${_MAJOR_VERSION}/'
                'lib-dynload/${module}".*.so ]; then\n'
                '\t\t\ttermux_error_exit "Python module library $module not built"\n'
                "\t\tfi\n"
                "\tdone\n"
                "}\n"
                "\n"
                "termux_step_create_debscripts() {\n"
                "\tcat <<- POSTINST_EOF > ./postinst\n"
                "\t#!$TERMUX_PREFIX/bin/bash\n"
                "\t# pip-separation notice\n"
                "\texit 0\n"
                "\tPOSTINST_EOF\n"
                "\tchmod 0755 postinst\n"
                "}\n"
            )
            tkinter = py_dir / "python-tkinter.subpackage.sh"
            tkinter.write_text(
                'TERMUX_SUBPKG_DESCRIPTION="Tkinter support for Python 3"\n'
                'TERMUX_SUBPKG_DEPENDS="tcl, tk"\n'
            )

            subprocess.run([str(OVERRIDES), str(tree)], check=True, text=True)

            build_text = (py_dir / "build.sh").read_text()
            self.assertNotIn("TERMUX_PKG_BUILD_DEPENDS", build_text)
            self.assertIn("TERMUX_PKG_DEPENDS=", build_text)
            # The upstream _tkinter verification is overridden by a
            # last-defined termux_step_post_massage without _tkinter.
            self.assertIn("CodeC: python builds without tk", build_text)
            self.assertIn(
                "for module in _bz2 _curses _lzma _multiprocessing "
                "_sqlite3 _ssl zlib _zstd; do",
                build_text,
            )
            # The recipe's own debscripts (postinst) are neutralized by a
            # last-defined no-op.
            self.assertIn("CodeC: no maintainer scripts for python", build_text)
            self.assertTrue(
                build_text.rstrip().endswith(
                    "termux_step_create_debscripts() { :; }"
                )
            )
            lines = tkinter.read_text().splitlines()
            self.assertEqual(
                lines[0],
                'TERMUX_SUBPKG_EXCLUDED_ARCHES="aarch64 x86_64" '
                "# CodeC: no tcl/tk/X11 in the userland",
            )

    def test_python_pip_debscripts_neutralized(self) -> None:
        """python-pip defines its own termux_step_create_debscripts
        (postinst + prerm); the override must append a last-defined no-op
        so the published deb has no maintainer scripts (CI repo-build
        33308884424 aborted on python-pip_26.2.1_all.deb otherwise)."""
        with tempfile.TemporaryDirectory() as tmp:
            tree = Path(tmp)
            self._write_apt_fixture_only(tree)
            pip_dir = tree / "packages" / "python-pip"
            pip_dir.mkdir(parents=True)
            (pip_dir / "build.sh").write_text(
                'TERMUX_PKG_VERSION="26.2.1"\n'
                'TERMUX_PKG_DEPENDS="python (>= 3.11.1-1)"\n'
                "\n"
                "termux_step_create_debscripts() {\n"
                "\tcat <<- POSTINST_EOF > ./postinst\n"
                "\t#!$TERMUX_PREFIX/bin/bash\n"
                "\techo \"pip setup...\"\n"
                "\texit 0\n"
                "\tPOSTINST_EOF\n"
                "\tcat <<- PRERM_EOF > ./prerm\n"
                "\t#!$TERMUX_PREFIX/bin/bash\n"
                "\texit 0\n"
                "\tPRERM_EOF\n"
                "\tchmod 0755 postinst prerm\n"
                "}\n"
            )

            subprocess.run([str(OVERRIDES), str(tree)], check=True, text=True)

            build_text = (pip_dir / "build.sh").read_text()
            self.assertIn("CodeC: no maintainer scripts for python-pip", build_text)
            self.assertTrue(
                build_text.rstrip().endswith(
                    "termux_step_create_debscripts() { :; }"
                )
            )

    def test_python_override_fails_loud_on_pinned_revision_drift(self) -> None:
        """A python recipe whose build-depends line changed shape (or a
        missing tkinter subpackage file) must abort instead of silently
        skipping the X11-closure review."""
        with tempfile.TemporaryDirectory() as tmp:
            tree = Path(tmp)
            self._write_apt_fixture_only(tree)
            py_dir = tree / "packages" / "python"
            py_dir.mkdir(parents=True)
            (py_dir / "build.sh").write_text(
                'TERMUX_PKG_BUILD_DEPENDS="tk, tcl"\n'
            )
            (py_dir / "python-tkinter.subpackage.sh").write_text(
                'TERMUX_SUBPKG_DEPENDS="tcl, tk"\n'
            )

            result = subprocess.run(
                [str(OVERRIDES), str(tree)], text=True, capture_output=True
            )
            self.assertNotEqual(result.returncode, 0)
            self.assertIn("pinned-revision drift", result.stderr)

        with tempfile.TemporaryDirectory() as tmp:
            tree = Path(tmp)
            self._write_apt_fixture_only(tree)
            py_dir = tree / "packages" / "python"
            py_dir.mkdir(parents=True)
            (py_dir / "build.sh").write_text('TERMUX_PKG_BUILD_DEPENDS="tk"\n')

            result = subprocess.run(
                [str(OVERRIDES), str(tree)], text=True, capture_output=True
            )
            self.assertNotEqual(result.returncode, 0)
            self.assertIn("pinned-revision drift", result.stderr)
            self.assertIn("python-tkinter", result.stderr)

        # A python recipe whose termux_step_post_massage no longer verifies
        # _tkinter (or is missing entirely) must also fail loudly: the
        # tk-free build then silently drops the module the upstream check
        # was guarding, so a shape change is worth a re-review.
        for post_massage in ("", "termux_step_post_massage() { :; }\n"):
            with tempfile.TemporaryDirectory() as tmp:
                tree = Path(tmp)
                self._write_apt_fixture_only(tree)
                py_dir = tree / "packages" / "python"
                py_dir.mkdir(parents=True)
                (py_dir / "build.sh").write_text(
                    'TERMUX_PKG_BUILD_DEPENDS="tk"\n' + post_massage
                )
                (py_dir / "python-tkinter.subpackage.sh").write_text(
                    'TERMUX_SUBPKG_DEPENDS="tcl, tk"\n'
                )

                result = subprocess.run(
                    [str(OVERRIDES), str(tree)], text=True, capture_output=True
                )
                self.assertNotEqual(result.returncode, 0)
                self.assertIn("post_massage", result.stderr)

        # A python or python-pip recipe that no longer defines its own
        # termux_step_create_debscripts must fail loudly: a shape change
        # means the neutralization was written against a different recipe,
        # so it needs a re-review rather than a silent pass.
        for name in ("python", "python-pip"):
            with tempfile.TemporaryDirectory() as tmp:
                tree = Path(tmp)
                self._write_apt_fixture_only(tree)
                pkg_dir = tree / "packages" / name
                pkg_dir.mkdir(parents=True)
                # The python fixture must still pass the earlier tk and
                # post_massage overrides so the debscripts check is reached.
                post_massage = (
                    "termux_step_post_massage() {\n"
                    "\tfor module in _bz2 _curses _lzma _multiprocessing "
                    "_sqlite3 _ssl _tkinter zlib _zstd; do\n"
                    '\t\tif [ ! -f "${TERMUX_PREFIX}/lib/python${_MAJOR_VERSION}/'
                    'lib-dynload/${module}".*.so ]; then\n'
                    '\t\t\ttermux_error_exit "Python module library $module not built"\n'
                    "\t\tfi\n"
                    "\tdone\n"
                    "}\n"
                ) if name == "python" else ""
                (pkg_dir / "build.sh").write_text(
                    'TERMUX_PKG_BUILD_DEPENDS="tk"\n'
                    + post_massage
                )
                if name == "python":
                    (pkg_dir / "python-tkinter.subpackage.sh").write_text(
                        'TERMUX_SUBPKG_DEPENDS="tcl, tk"\n'
                    )

                result = subprocess.run(
                    [str(OVERRIDES), str(tree)], text=True, capture_output=True
                )
                self.assertNotEqual(result.returncode, 0)
                self.assertIn("termux_step_create_debscripts", result.stderr)


if __name__ == "__main__":
    unittest.main()
