#!/usr/bin/env bash
# Apply narrowly scoped CodeC build-environment overrides to official recipes.
# These are source transport fixes plus the repository-identity rewrite below:
# package versions, patches, and hashes remain those from the pinned official
# termux-packages revision.
set -euo pipefail

TREE="${1:?usage: apply-recipe-overrides.sh /path/to/termux-packages}"

# CodeC development channel (same values the Android `pkg` frontend uses).
CODEC_REPO_URL="${CODEC_PACKAGE_REPOSITORY_URL:-https://pabi277.github.io/CodeC/dev}"
CODEC_REPO_SUITE="${CODEC_PACKAGE_REPOSITORY_SUITE:-stable}"
CODEC_REPO_COMPONENT="${CODEC_PACKAGE_REPOSITORY_COMPONENT:-main}"
CODEC_REPO_KEYRING="${CODEC_PACKAGE_REPOSITORY_KEYRING:-/data/data/com.codeci.ide/files/usr/etc/apt/keyrings/codec-archive-keyring-v1.gpg}"

# Savannah's HTTP/primary HTTPS endpoint intermittently returns HTTP 502 from
# GitHub Actions. Its official download mirror serves the same source archives.
# Keep this list explicit: do not rewrite unrelated third-party source URLs.
for recipe in attr libacl; do
  path="$TREE/packages/$recipe/build.sh"
  if [[ ! -f "$path" ]]; then
    echo "recipe-overrides: $recipe recipe not found; skipping"
    continue
  fi
  sed -i \
    's#https\?://download\.savannah\.gnu\.org/releases/\(attr\|acl\)/#https://download-mirror.savannah.gnu.org/releases/\1/#' \
    "$path"
  if grep -qE 'https?://download\.savannah\.gnu\.org/releases/(attr|acl)/' "$path"; then
    echo "recipe-overrides: failed to update $recipe source URL" >&2
    exit 1
  fi
  echo "recipe-overrides: $recipe uses official HTTPS Savannah mirror"
done

# xorg.freedesktop.org and www.x.org/releases repeatedly timed out from GitHub Actions
# package builds. ftp.x.org serves all official, hash-verified X.Org source archives directly via HTTPS.
if [[ -d "$TREE/packages" ]]; then
  find "$TREE/packages" -name "build.sh" -exec sed -i \
    's#https\?://\(xorg\.freedesktop\.org\|www\.x\.org\)/\(releases\|archive\)/individual/#https://ftp.x.org/pub/individual/#' {} +
fi
UTIL_MACROS_RECIPE="$TREE/packages/xorg-util-macros/build.sh"
if [[ -f "$UTIL_MACROS_RECIPE" ]]; then
  if grep -qE 'https://(xorg\.freedesktop\.org|www\.x\.org)/releases/individual/util/' "$UTIL_MACROS_RECIPE"; then
    echo "recipe-overrides: failed to update util-macros source URL" >&2
    exit 1
  fi
  grep -qF 'https://ftp.x.org/pub/individual/util/' "$UTIL_MACROS_RECIPE"
  echo "recipe-overrides: util-macros uses official HTTPS ftp.x.org mirror"
else
  echo "recipe-overrides: util-macros recipe not found; skipping" >&2
fi

# rxvt-unicode SRCURL override REMOVED 2026-08-25 (post-Part 4.5/4.6 review):
# the recipe does not exist at the pinned termux-packages revision — it is
# absent from both packages/ and x11-packages/ at 1bbe66903526df2e8af51e704316bc68ede72603
# (verified by full-tree search), so the override could never fire and was
# dead code (its host test used a synthetic fixture and could not notice).
# Precedent kept as documentation: if a future pin reintroduces a recipe on a
# flaky host (dist.schmorp.de timed out in run 32781913358), the fix pattern
# is a narrow, fail-loud SRCURL override to an official hash-verified mirror,
# like attr/libacl and the xorg sweep above — plus a real-tree absence check.

# The official dpkg-perl subpackage lists clang (a build-time compiler) as a
# *runtime* dependency: TERMUX_SUBPKG_DEPENDS="perl, clang, make". CodeC
# userland never installs clang, and a runtime dependency on a compiler is
# wrong in any case: once dpkg-perl is installed the userland is recorded as
# broken for apt ("Depends: clang but it is not installable") and every
# `pkg install` is refused. Drop the bogus dependency; perl and make remain.
DPG_PERL_RECIPE="$TREE/packages/dpkg/dpkg-perl.subpackage.sh"
if [[ -f "$DPG_PERL_RECIPE" ]]; then
  sed -i 's/^TERMUX_SUBPKG_DEPENDS="perl, clang, make"$/TERMUX_SUBPKG_DEPENDS="perl, make"/' \
    "$DPG_PERL_RECIPE"
  if grep -q '^TERMUX_SUBPKG_DEPENDS=.*clang' "$DPG_PERL_RECIPE"; then
    echo "recipe-overrides: failed to remove clang from dpkg-perl dependencies" >&2
    exit 1
  fi
  echo "recipe-overrides: dpkg-perl runtime dependencies: $(grep '^TERMUX_SUBPKG_DEPENDS=' "$DPG_PERL_RECIPE")"
fi

# The official apt recipe writes the official Termux repository URLs into
# $PREFIX/etc/apt/sources.list (the apt conffile). CodeC must never point at
# the official Termux repository: a bare `apt-get update` on the device would
# otherwise resolve com.termux packages for a foreign prefix. Rewrite the
# generated sources.list to the CodeC-only development channel. The CodeC
# `pkg` frontend supplies its own sources list regardless; this keeps the
# ambient apt configuration CodeC-only as well.
APT_RECIPE="$TREE/packages/apt/build.sh"
if [[ -f "$APT_RECIPE" ]]; then
  python3 - "$APT_RECIPE" "$CODEC_REPO_URL" "$CODEC_REPO_SUITE" "$CODEC_REPO_COMPONENT" "$CODEC_REPO_KEYRING" <<'PY'
import pathlib
import re
import sys

path = pathlib.Path(sys.argv[1])
url, suite, component, keyring = sys.argv[2], sys.argv[3], sys.argv[4], sys.argv[5]
text = path.read_text()
pattern = re.compile(
    r'\t\techo "# The main termux repository, with cloudflare cache"\n'
    r'\t\techo "deb https://packages-cf\.termux\.dev/apt/termux-main/ stable main"\n'
    r'\t\techo "# The main termux repository, without cloudflare cache"\n'
    r'\t\techo "# deb https://packages\.termux\.dev/apt/termux-main/ stable main"\n'
)
replacement = (
    '\t\techo "# CodeC development package repository (CodeC packages only)."\n'
    '\t\techo "# CodeC never uses the official Termux repository."\n'
    f'\t\techo "deb [signed-by={keyring}] {url} {suite} {component}"\n'
)
new_text, count = pattern.subn(replacement, text)
if count != 1:
    print(
        f"recipe-overrides: failed to rewrite apt sources.list block (matches={count})",
        file=sys.stderr,
    )
    sys.exit(1)
path.write_text(new_text)
print(f"recipe-overrides: apt sources.list now points at {url}")
PY
  if grep -qE 'packages(-cf)?\.termux\.dev' "$APT_RECIPE"; then
    echo "recipe-overrides: official Termux repository URL remains in apt recipe" >&2
    exit 1
  fi
  grep -qF "deb [signed-by=$CODEC_REPO_KEYRING] $CODEC_REPO_URL $CODEC_REPO_SUITE $CODEC_REPO_COMPONENT" "$APT_RECIPE"
  if grep -qF '[trusted=yes]' "$APT_RECIPE"; then
    echo "recipe-overrides: apt sources.list still bypasses signature verification" >&2
    exit 1
  fi

  # The official apt recipe also lists termux-keyring — the GPG keyring of
  # the OFFICIAL Termux repositories, which installs those keys into
  # $PREFIX/etc/apt/trusted.gpg.d/ — as a runtime dependency. CodeC never
  # uses official Termux repositories, so seeding their signing keys is
  # contamination (fresh-device evidence 2026-08-23: the published Phase 3
  # bootstrap recorded `ii termux-keyring 3.13` in dpkg status). Remove
  # exactly that one dependency from the TERMUX_PKG_DEPENDS line; every
  # other runtime dependency stays byte-identical (termux-licenses must
  # stay: it provides $PREFIX/share/LICENSES/*, the target of packaged
  # license symlinks like nano's share/licenses/nano).
  if ! grep -q '^TERMUX_PKG_DEPENDS=' "$APT_RECIPE"; then
    echo "recipe-overrides: apt recipe has no TERMUX_PKG_DEPENDS line to audit" >&2
    exit 1
  fi
  if ! grep -qE '^TERMUX_PKG_DEPENDS=.*[, ]termux-keyring([, "]|$)' "$APT_RECIPE"; then
    echo "recipe-overrides: apt TERMUX_PKG_DEPENDS no longer lists termux-keyring in the expected position — re-review the pinned recipe" >&2
    exit 1
  fi
  sed -i '/^TERMUX_PKG_DEPENDS=/s/, termux-keyring//' "$APT_RECIPE"
  if grep -q 'termux-keyring' "$APT_RECIPE"; then
    echo "recipe-overrides: failed to remove termux-keyring from apt dependencies" >&2
    exit 1
  fi
  echo "recipe-overrides: apt runtime dependencies no longer include termux-keyring: $(grep '^TERMUX_PKG_DEPENDS=' "$APT_RECIPE")"
else
  echo "recipe-overrides: apt recipe not found; skipping sources.list rewrite" >&2
  exit 1
fi

# The official bash recipe lists termux-tools — which pulls in the
# termux-am/termux-core chain of Android activity-manager wrappers that
# CodeC intentionally never ships — as a runtime dependency. CodeC needs
# bash with its remaining dependencies (libandroid-support, libiconv,
# readline) only. The Phase 3 bootstrap build applied this removal in
# build-bootstrap.sh; the repository build (which builds bash as a
# dependency of the round 2 libtool package) must share it, so the
# override now lives here. Idempotent: build-bootstrap.sh's original
# block remains as a double-safety assertion and becomes a no-op.
BASH_RECIPE="$TREE/packages/bash/build.sh"
if [[ -f "$BASH_RECIPE" ]]; then
  if ! grep -q '^TERMUX_PKG_DEPENDS=' "$BASH_RECIPE"; then
    echo "recipe-overrides: bash recipe has no TERMUX_PKG_DEPENDS line to audit (pinned-revision drift)" >&2
    exit 1
  fi
  if grep '^TERMUX_PKG_DEPENDS=' "$BASH_RECIPE" | grep -q 'termux-tools'; then
    sed -i '/^TERMUX_PKG_DEPENDS=/s/, termux-tools//' "$BASH_RECIPE"
  fi
  if grep -q 'termux-tools' "$BASH_RECIPE"; then
    echo "recipe-overrides: failed to remove termux-tools from bash dependencies" >&2
    exit 1
  fi
  echo "recipe-overrides: bash runtime dependencies (termux-tools removed): $(grep '^TERMUX_PKG_DEPENDS=' "$BASH_RECIPE")"
else
  echo "recipe-overrides: bash recipe not found; skipping termux-tools removal" >&2
fi

# The official git recipe builds three GUI/subversion subpackages whose
# dependencies do not belong in the CodeC userland: gitk and git-gui pull
# in tcl/tk and the whole X11 stack, git-svn pulls in subversion-perl.
# Excluding them uses the upstream-native per-arch skip mechanism and keeps
# the main git package byte-identical to the pinned recipe. Fail loudly on
# pinned-revision drift (a file disappearing is not something to paper
# over) so a future bump is reviewed.
GIT_DIR="$TREE/packages/git"
if [[ -d "$GIT_DIR" ]]; then
  for sub in git-gitk git-gui git-svn; do
    subfile="$GIT_DIR/$sub.subpackage.sh"
    if [[ ! -f "$subfile" ]]; then
      echo "recipe-overrides: git subpackage file missing (pinned-revision drift): $subfile" >&2
      exit 1
    fi
    if ! grep -q '^TERMUX_SUBPKG_EXCLUDED_ARCHES=' "$subfile"; then
      sed -i '1i TERMUX_SUBPKG_EXCLUDED_ARCHES="aarch64 x86_64" # CodeC: no tcl/tk/X11 or subversion in the userland' "$subfile"
    fi
    if ! grep -q '^TERMUX_SUBPKG_EXCLUDED_ARCHES="aarch64 x86_64"' "$subfile"; then
      echo "recipe-overrides: failed to exclude git subpackage: $sub" >&2
      exit 1
    fi
    echo "recipe-overrides: git subpackage $sub excluded for CodeC arches"
  done
  # tcl/tk is not built for the CodeC userland, so wish does not exist at
  # configure time: build git without Tcl/Tk support instead of pointing at
  # a missing binary.
  if grep -qF -- '--with-tcltk=$TERMUX_PREFIX/bin/wish' "$GIT_DIR/build.sh"; then
    sed -i 's#^--with-tcltk=\$TERMUX_PREFIX/bin/wish$#--with-tcltk=no#' "$GIT_DIR/build.sh"
  fi
  if grep -qF -- '--with-tcltk=$TERMUX_PREFIX/bin/wish' "$GIT_DIR/build.sh"; then
    echo "recipe-overrides: failed to disable git tcl/tk support" >&2
    exit 1
  fi
  if ! grep -qF -- '--with-tcltk=no' "$GIT_DIR/build.sh"; then
    echo "recipe-overrides: git recipe no longer carries the expected --with-tcltk line (pinned-revision drift)" >&2
    exit 1
  fi
  echo "recipe-overrides: git builds without tcl/tk (--with-tcltk=no)"
else
  echo "recipe-overrides: git recipe not found; skipping tcl/tk overrides" >&2
fi

# Exclude python-xcbgen subpackage in xcb-proto: CodeC userland does not ship Python
# or X11 Python bindings. (The unapproved xcb-proto postinst scripts seen during
# Part 4.5 came from termux_step_create_python_debscripts, which is disabled
# globally below — no per-recipe debscripts stub is needed here.)
XCB_PROTO_DIR="$TREE/packages/xcb-proto"
if [[ -d "$XCB_PROTO_DIR" ]]; then
  for subfile in "$XCB_PROTO_DIR"/python*.subpackage.sh; do
    if [[ -f "$subfile" ]]; then
      subname="$(basename "$subfile" .subpackage.sh)"
      if ! grep -q '^TERMUX_SUBPKG_EXCLUDED_ARCHES=' "$subfile"; then
        sed -i '1i TERMUX_SUBPKG_EXCLUDED_ARCHES="aarch64 x86_64" # CodeC: no python/X11 bindings in userland' "$subfile"
      fi
      if ! grep -q '^TERMUX_SUBPKG_EXCLUDED_ARCHES="aarch64 x86_64"' "$subfile"; then
        echo "recipe-overrides: failed to exclude $subname subpackage" >&2
        exit 1
      fi
      echo "recipe-overrides: $subname subpackage excluded for CodeC arches"
    fi
  done
else
  echo "recipe-overrides: xcb-proto recipe not found; skipping python-xcbgen exclusion" >&2
fi

# CodeC policy: maintainer scripts are forbidden for EVERY package. The only
# approved postinst/prerm are the update-alternatives pairs generated by
# termux_step_create_alternatives.sh (a separate, unpatched step) for the five
# reviewed packages with .alternatives files (coreutils, less, nano, bat,
# util-linux). At the pinned revision termux_step_create_debscripts is already
# an upstream no-op; stub it unconditionally anyway so a pinned-revision bump
# can never silently start generating maintainer scripts. The stub is appended
# at the END of the file: it wins by bash's last-definition rule while the
# rest of the file (e.g. termux_step_create_debscripts__copy_from_dir) stays
# sourced, so no helper ever goes missing and the result never depends on
# TERMUX_PKG_NAME being set at source time (it never is: build-package.sh
# sources step files before parsing any recipe).
DEBSCRIPTS_SCRIPT="$TREE/scripts/build/termux_step_create_debscripts.sh"
if [[ -f "$DEBSCRIPTS_SCRIPT" ]]; then
  if ! grep -q '^termux_step_create_debscripts()' "$DEBSCRIPTS_SCRIPT"; then
    echo "recipe-overrides: termux_step_create_debscripts.sh restructured (pinned-revision drift) — re-review the stub" >&2
    exit 1
  fi
  if ! grep -q 'CodeC: maintainer scripts forbidden for every package' "$DEBSCRIPTS_SCRIPT"; then
    cat <<'SH' >> "$DEBSCRIPTS_SCRIPT"

# CodeC: maintainer scripts forbidden for every package (the approved
# update-alternatives scripts are generated by termux_step_create_alternatives
# for reviewed packages only). Appended last so this definition wins.
termux_step_create_debscripts() { :; }
SH
  fi
  grep -q 'CodeC: maintainer scripts forbidden for every package' "$DEBSCRIPTS_SCRIPT"
  tail -n 1 "$DEBSCRIPTS_SCRIPT" | grep -qF 'termux_step_create_debscripts() { :; }'
  echo "recipe-overrides: termux_step_create_debscripts stubbed unconditionally (CodeC policy)"
else
  echo "recipe-overrides: termux_step_create_debscripts.sh not found; skipping debscripts stub" >&2
fi

# CodeC userland ships no Python interpreter at all (fresh-device evidence
# 2026-08-23: the Phase 3 closure contains no python3), so the auto-generated
# py3compile/py3clean postinst/prerm scripts could never succeed on device.
# Disable termux_step_create_python_debscripts for EVERY package — including
# the reviewed alternatives packages, none of which ships Python files at the
# pinned revision (this is also the exact behavior the published Part 4.5
# build had: its source-time guard always took the stub branch). Appended at
# the END of the file so the stub wins by bash's last-definition rule while
# the original implementation stays sourced; sourcing is therefore safe under
# set -u with TERMUX_PKG_NAME unset (build-package.sh sources step files
# before parsing any recipe).
PYTHON_DEBSCRIPTS_SCRIPT="$TREE/scripts/build/termux_step_create_python_debscripts.sh"
if [[ -f "$PYTHON_DEBSCRIPTS_SCRIPT" ]]; then
  if ! grep -q '^termux_step_create_python_debscripts()' "$PYTHON_DEBSCRIPTS_SCRIPT"; then
    echo "recipe-overrides: termux_step_create_python_debscripts.sh restructured (pinned-revision drift) — re-review the stub" >&2
    exit 1
  fi
  if ! grep -q 'CodeC: no Python interpreter in the userland' "$PYTHON_DEBSCRIPTS_SCRIPT"; then
    cat <<'SH' >> "$PYTHON_DEBSCRIPTS_SCRIPT"

# CodeC: no Python interpreter in the userland, so py3compile/py3clean
# maintainer scripts can never run on device; disabled for every package.
# Appended last so this definition wins.
termux_step_create_python_debscripts() { :; }
SH
  fi
  grep -q 'CodeC: no Python interpreter in the userland' "$PYTHON_DEBSCRIPTS_SCRIPT"
  tail -n 1 "$PYTHON_DEBSCRIPTS_SCRIPT" | grep -qF 'termux_step_create_python_debscripts() { :; }'
  echo "recipe-overrides: termux_step_create_python_debscripts stubbed unconditionally (no Python in userland)"
else
  echo "recipe-overrides: termux_step_create_python_debscripts.sh not found; skipping python debscripts stub" >&2
fi

# Direct override for libbz2: bzip2's upstream Makefile creates absolute symlinks
# in $TERMUX_PREFIX/bin during `make install` (e.g. bzcmp -> $TERMUX_PREFIX/bin/bzdiff).
# Clean them up in termux_step_post_make_install.
LIBBZ2_RECIPE="$TREE/packages/libbz2/build.sh"
if [[ -f "$LIBBZ2_RECIPE" ]]; then
  if ! grep -q "CodeC: fix absolute symlinks in libbz2" "$LIBBZ2_RECIPE"; then
    cat <<'SH' >> "$LIBBZ2_RECIPE"

# CodeC: fix absolute symlinks in libbz2
termux_step_post_make_install() {
	for f in "$TERMUX_PREFIX"/bin/*; do
		if [ -L "$f" ]; then
			local target
			target=$(readlink "$f")
			if [[ "$target" == "$TERMUX_PREFIX"* ]]; then
				local rel_target
				rel_target=$(realpath -m --relative-to="$TERMUX_PREFIX/bin" "$target")
				ln -sf "$rel_target" "$f"
			fi
		fi
	done
}
SH
    echo "recipe-overrides: patched libbz2 to fix absolute symlinks"
  fi
fi

# Convert absolute symlinks in $TERMUX_PREFIX to relative symlinks in
# termux_step_massage.sh BEFORE subpackages are created
# (termux_create_debian_subpackages), so subpackage file sets inherit the
# already-relative links (libbz2's bzcmp/bzless precedent). NOTE: an earlier
# revision of this override also injected a "purge maintainer scripts" block
# here; it was removed 2026-08-25 because it could never run — DEBIAN/ is only
# created later by termux_step_create_debian_package, and its guards used
# variable names (TERMUX_PKG_MASSAGEDDIR/SUBPKG_MASSAGEDDIR) that do not exist
# at the pinned revision. Maintainer-script control is enforced instead by the
# unconditional debscripts/python-debscripts stubs above, by the fact that
# termux_step_create_alternatives only fires for declared .alternatives files,
# and by the build-side and client-side validators.
MASSAGE_SCRIPT="$TREE/scripts/build/termux_step_massage.sh"
if [[ -f "$MASSAGE_SCRIPT" ]]; then
  python3 - "$MASSAGE_SCRIPT" <<'PY'
import pathlib
import sys

path = pathlib.Path(sys.argv[1])
text = path.read_text()

target_marker = 'if [ "$TERMUX_PACKAGE_FORMAT" = "debian" ]; then'
symlink_fix_block = """
	# CodeC override: convert absolute symlinks in $TERMUX_PREFIX to relative symlinks before subpackages
	while IFS= read -r -d '' file; do
		local _link_target
		_link_target=$(readlink "$file")
		if [[ "$_link_target" == "$TERMUX_PREFIX"* ]]; then
			local _rel_file="${file#./}"
			local _rel_dir
			_rel_dir=$(dirname "$_rel_file")
			local _abs_dir="$TERMUX_PREFIX/$_rel_dir"
			local _rel_target
			_rel_target=$(realpath -m --relative-to="$_abs_dir" "$_link_target")
			rm -f "$file"
			ln -s "$_rel_target" "$file"
			echo "INFO: Converted absolute symlink $file -> $_link_target to relative $_rel_target"
		fi
	done < <(find . -type l -print0)
"""

if "CodeC override: convert absolute symlinks" not in text:
    idx = text.find(target_marker)
    if idx == -1:
        print("recipe-overrides: cannot locate debian target marker in termux_step_massage.sh", file=sys.stderr)
        sys.exit(1)
    new_text = text[:idx] + symlink_fix_block + "\n\t" + text[idx:]
    path.write_text(new_text)
    print("recipe-overrides: patched termux_step_massage.sh to convert absolute symlinks to relative symlinks before subpackage creation")
else:
    print("recipe-overrides: termux_step_massage.sh already patched")
PY
  grep -q "CodeC override: convert absolute symlinks" "$MASSAGE_SCRIPT"
else
  echo "recipe-overrides: termux_step_massage.sh not found; skipping absolute symlink fix" >&2
fi

# The official python recipe (packages/python at the pinned revision, 3.14.6)
# declares tk as a build dependency and ships a python-tkinter subpackage.
# tk pulls the whole X11 stack (fontconfig, libx11, libxft, libxss, tcl)
# solely to build Tkinter, which the CodeC userland does not use (no X11).
# Phase 12: drop it the same way the git round-2 override drops gitk/git-gui —
# exclude the subpackage for CodeC arches with the upstream-native per-arch
# skip mechanism, and remove the build dependency so the closure stays lean.
# Fail loudly on pinned-revision drift (a shape change is worth a re-review).
PYTHON_DIR="$TREE/packages/python"
if [[ -d "$PYTHON_DIR" ]]; then
  TKINTER_SUBPKG="$PYTHON_DIR/python-tkinter.subpackage.sh"
  if [[ ! -f "$TKINTER_SUBPKG" ]]; then
    echo "recipe-overrides: python-tkinter subpackage file missing (pinned-revision drift): $TKINTER_SUBPKG" >&2
    exit 1
  fi
  if ! grep -q '^TERMUX_SUBPKG_EXCLUDED_ARCHES=' "$TKINTER_SUBPKG"; then
    sed -i '1i TERMUX_SUBPKG_EXCLUDED_ARCHES="aarch64 x86_64" # CodeC: no tcl/tk/X11 in the userland' "$TKINTER_SUBPKG"
  fi
  if ! grep -q '^TERMUX_SUBPKG_EXCLUDED_ARCHES="aarch64 x86_64"' "$TKINTER_SUBPKG"; then
    echo "recipe-overrides: failed to exclude python-tkinter subpackage" >&2
    exit 1
  fi
  # Verbatim from the pinned upstream revision (termux-packages @
  # 1bbe66903526df2e8af51e704316bc68ede72603, packages/python/build.sh).
  if ! grep -q '^TERMUX_PKG_BUILD_DEPENDS="tk"$' "$PYTHON_DIR/build.sh"; then
    echo "recipe-overrides: python recipe build-depends no longer has the expected tk line (pinned-revision drift)" >&2
    exit 1
  fi
  sed -i '/^TERMUX_PKG_BUILD_DEPENDS="tk"$/d' "$PYTHON_DIR/build.sh"
  if grep -q '^TERMUX_PKG_BUILD_DEPENDS=' "$PYTHON_DIR/build.sh"; then
    echo "recipe-overrides: failed to remove tk build dependency from python recipe" >&2
    exit 1
  fi

  # The pinned recipe's termux_step_post_massage() hard-verifies that
  # _tkinter was built (among other modules):
  #
  #   for module in _bz2 _curses _lzma _multiprocessing _sqlite3 _ssl _tkinter
  #                 zlib _zstd; do
  #       if [ ! -f ".../lib-dynload/${module}".*.so ]; then
  #           termux_error_exit "Python module library $module not built"
  #       fi
  #   done
  #
  # Once tk is removed from build-depends (above) _tkinter can never exist,
  # so that check aborts every python build. Tkinter is intentionally not
  # part of the CodeC userland (no X11; python-tkinter excluded above), so
  # append an overriding termux_step_post_massage (bash last-definition-wins:
  # the recipe is sourced as one file, and this definition follows the
  # upstream one) that validates the same module list minus _tkinter. Fail
  # loudly if the upstream function's shape drifts.
  PYTHON_BUILD="$PYTHON_DIR/build.sh"
  if grep -q '^termux_step_post_massage()' "$PYTHON_BUILD"; then
    if ! grep -q 'CodeC: python builds without tk' "$PYTHON_BUILD"; then
      if ! grep -q 'for module in .*_tkinter' "$PYTHON_BUILD"; then
        echo "recipe-overrides: python recipe module-verification list no longer contains _tkinter (pinned-revision drift) — re-review the post_massage override" >&2
        exit 1
      fi
      cat <<'SH' >> "$PYTHON_BUILD"

# CodeC: python builds without tk (tk removed from build-depends above;
# python-tkinter subpackage excluded for CodeC arches), so _tkinter is
# intentionally not built. Override the upstream post-massage module
# verification to validate the same module list minus _tkinter. Appended
# last so this definition wins (bash last-definition-wins).
termux_step_post_massage() {
	# Verify that desired modules have been included (no _tkinter — CodeC):
	for module in _bz2 _curses _lzma _multiprocessing _sqlite3 _ssl zlib _zstd; do
		if [ ! -f "${TERMUX_PREFIX}/lib/python${_MAJOR_VERSION}/lib-dynload/${module}".*.so ]; then
			termux_error_exit "Python module library $module not built"
		fi
	done
}
SH
    fi
    if ! grep -q 'CodeC: python builds without tk' "$PYTHON_BUILD"; then
      echo "recipe-overrides: failed to append python post_massage override" >&2
      exit 1
    fi
    if grep -q 'for module in .*_tkinter' <(tail -n 12 "$PYTHON_BUILD"); then
      echo "recipe-overrides: appended python post_massage still requires _tkinter" >&2
      exit 1
    fi
  else
    echo "recipe-overrides: python recipe has no termux_step_post_massage (pinned-revision drift) — re-review" >&2
    exit 1
  fi

  # The python recipe defines its own termux_step_create_debscripts()
  # (a pip-separation notice postinst). CodeC policy forbids maintainer
  # scripts for EVERY package (the five approved update-alternatives
  # packages are the only exception), and the shared debscripts stub
  # cannot help here: the recipe is sourced AFTER the step scripts, so a
  # per-recipe definition overrides the stub and the deb ships with
  # DEBIAN/postinst — which generate-repository rejects ("maintainer
  # scripts are not allowed", CI repo-build 33308884424). Append a
  # last-defined no-op so python's deb has no maintainer scripts. Fail
  # loudly if the per-recipe definition disappears (shape change worth a
  # re-review: dropping the no-op would silently reintroduce postinst).
  if ! grep -q '^termux_step_create_debscripts()' "$PYTHON_BUILD"; then
    echo "recipe-overrides: python recipe no longer defines its own termux_step_create_debscripts (pinned-revision drift) — re-review" >&2
    exit 1
  fi
  if ! grep -q 'CodeC: no maintainer scripts for python' "$PYTHON_BUILD"; then
    cat <<'SH' >> "$PYTHON_BUILD"

# CodeC: no maintainer scripts for python — maintainer scripts are
# forbidden for every package. The shared termux_step_create_debscripts
# stub is overridden by this recipe's own definition (recipes are sourced
# after the step scripts), so neutralize it here — appended last so this
# definition wins (bash last-definition-wins).
termux_step_create_debscripts() { :; }
SH
  fi
  if ! grep -q 'CodeC: no maintainer scripts for python' "$PYTHON_BUILD"; then
    echo "recipe-overrides: failed to append python debscripts override" >&2
    exit 1
  fi
  if tail -n 8 "$PYTHON_BUILD" | grep -q 'POSTINST_EOF\|cat <<-'; then
    echo "recipe-overrides: python recipe still emits a postinst after the override" >&2
    exit 1
  fi
  echo "recipe-overrides: python ships without maintainer scripts"

  echo "recipe-overrides: python builds without tk (tkinter excluded for CodeC arches)"
else
  echo "recipe-overrides: python recipe not found; skipping tk removal" >&2
fi

# The python-pip recipe defines its own termux_step_create_debscripts()
# (postinst: pip disable-version-check config; prerm: pip.conf cleanup).
# Same CodeC policy and the same reason the shared stub cannot help (the
# recipe is sourced after the step scripts, so its definition overrides
# the stub) — the deb ships with DEBIAN/postinst + DEBIAN/prerm and
# generate-repository aborts (CI repo-build 33308884424). Append a
# last-defined no-op. Fail loudly on pinned-revision drift.
PIP_DIR="$TREE/packages/python-pip"
if [[ -d "$PIP_DIR" ]]; then
  PIP_BUILD="$PIP_DIR/build.sh"
  if [[ ! -f "$PIP_BUILD" ]]; then
    echo "recipe-overrides: python-pip recipe build.sh missing (pinned-revision drift)" >&2
    exit 1
  fi
  if ! grep -q '^termux_step_create_debscripts()' "$PIP_BUILD"; then
    echo "recipe-overrides: python-pip recipe no longer defines its own termux_step_create_debscripts (pinned-revision drift) — re-review" >&2
    exit 1
  fi
  if ! grep -q 'CodeC: no maintainer scripts for python-pip' "$PIP_BUILD"; then
    cat <<'SH' >> "$PIP_BUILD"

# CodeC: no maintainer scripts for python-pip — maintainer scripts are
# forbidden for every package. This recipe's own
# termux_step_create_debscripts (postinst/prerm) overrides the shared
# stub because recipes are sourced after the step scripts; neutralize it —
# appended last so this definition wins (bash last-definition-wins).
termux_step_create_debscripts() { :; }
SH
  fi
  if ! grep -q 'CodeC: no maintainer scripts for python-pip' "$PIP_BUILD"; then
    echo "recipe-overrides: failed to append python-pip debscripts override" >&2
    exit 1
  fi
  if tail -n 8 "$PIP_BUILD" | grep -q 'POSTINST_EOF\|PRERM_EOF\|cat <<-'; then
    echo "recipe-overrides: python-pip recipe still emits maintainer scripts after the override" >&2
    exit 1
  fi
  echo "recipe-overrides: python-pip ships without maintainer scripts"
else
  echo "recipe-overrides: python-pip recipe not found; skipping debscripts neutralization" >&2
fi

# ============================ Phase 20.1 overrides ============================

# CodeC invariant: never overwrite cc. $PREFIX/bin/cc is the app's own TCC
# frontend written by ShellEnvironment; a package must never shadow it
# (docs/TERMINAL_PLAN.md §B/§J). At the pinned revision there is no standalone
# packages/gcc recipe — upstream removed it — and clang is a subpackage of
# packages/libllvm. The clang subpackage's include list ships compatibility
# symlinks bin/cc, bin/gcc, bin/g++, bin/c++, bin/cpp -> clang-<major>
# (created in libllvm's termux_step_post_make_install). Keep gcc/g++/c++/cpp
# (nothing in the CodeC userland owns them) but strip exactly the standalone
# `bin/cc` line so `pkg install clang` can never clobber the app frontend.
# Phase 21.4 revisits cc wiring when TCC is retired. Fail loudly if the line
# disappears — a reworded include glob would need a fresh invariant review.
CLANG_SUBPKG="$TREE/packages/libllvm/clang.subpackage.sh"
if [[ -f "$CLANG_SUBPKG" ]]; then
  if ! grep -qx 'bin/cc' "$CLANG_SUBPKG"; then
    echo "recipe-overrides: clang subpackage include list no longer has a standalone bin/cc line (pinned-revision drift) — re-review the cc invariant" >&2
    exit 1
  fi
  sed -i '/^bin\/cc$/d' "$CLANG_SUBPKG"
  if grep -qx 'bin/cc' "$CLANG_SUBPKG"; then
    echo "recipe-overrides: failed to strip bin/cc from the clang subpackage" >&2
    exit 1
  fi
  echo "recipe-overrides: clang subpackage never ships bin/cc (cc stays the app frontend; gcc/g++ kept)"
else
  echo "recipe-overrides: libllvm clang subpackage not found; skipping bin/cc strip" >&2
fi

# libcompiler-rt maintainer scripts neutralized (codeciide/package-repository
# validator forbids maintainer scripts outside the reviewed alternatives
# allowlist). The subpackage defines its own
# termux_step_create_subpkg_debscripts() writing a dpkg trigger + postinst +
# prerm whose ONLY purpose is interop with Termux's ndk-multilib package
# (symlinking cross-compiler runtimes from $PREFIX/opt/ndk-multilib into
# lib/clang/<major>/lib/linux). CodeC never ships ndk-multilib (on-device
# native arch only), so the scripts are pure dead code here — but the deb
# still carries them and the validator rejects it (run 33585242675 llvm
# legs). The shared debscripts stub does not help: a recipe-level definition
# shadows it, so same as python/python-pip append a last-defined-wins no-op
# to the SUBPACKAGE file itself. Fail loudly on pinned-revision drift.
CRT_SUBPKG="$TREE/packages/libllvm/libcompiler-rt.subpackage.sh"
if [[ -f "$CRT_SUBPKG" ]]; then
  if ! grep -q '^termux_step_create_subpkg_debscripts()' "$CRT_SUBPKG"; then
    echo "recipe-overrides: libcompiler-rt subpackage no longer defines its own termux_step_create_subpkg_debscripts (pinned-revision drift) — re-review" >&2
    exit 1
  fi
  if ! grep -q 'CodeC: no maintainer scripts for libcompiler-rt' "$CRT_SUBPKG"; then
    cat >> "$CRT_SUBPKG" <<'EOF'

# CodeC: no maintainer scripts for libcompiler-rt — the upstream
# postinst/prerm/triggers only serve Termux's ndk-multilib cross-compiler
# symlinks, which CodeC never ships. Last definition wins.
termux_step_create_subpkg_debscripts() { :; }
EOF
  fi
  if ! grep -q 'CodeC: no maintainer scripts for libcompiler-rt' "$CRT_SUBPKG"; then
    echo "recipe-overrides: failed to append libcompiler-rt debscripts override" >&2
    exit 1
  fi
  echo "recipe-overrides: libcompiler-rt ships without maintainer scripts (ndk-multilib interop is dead code in CodeC)"
else
  echo "recipe-overrides: libllvm libcompiler-rt subpackage not found; skipping debscripts neutralization" >&2
fi

# ---- libllvm build-time trim (D10, PERMANENT) ----
# Round-4 build 33506104710 (2026-09-01) was killed at the 360-minute
# per-job ceiling after 6h01m inside the monolithic build step: upstream
# compiles every LLVM backend (-DLLVM_TARGETS_TO_BUILD=all plus experimental
# ARC;CSKY;M68k;VE) and the full project set including lldb/mlir/polly, and
# GitHub-hosted runners cannot raise that ceiling. CodeC devices are
# aarch64/x86_64 only and the Phase 21 language profiles need clang
# (+clang-format/tools-extra), lld, llvm, compiler-rt and openmp — never
# lldb, mlir, or polly — so the recipe ALWAYS builds just the two device
# backends, drops lldb/mlir/polly (their subpackages are excluded for CodeC
# arches), drops the now-dangling target-coupled include lines that would
# otherwise fail subpackage creation (wasm-ld needs the WebAssembly target;
# amdgpu-arch/nvptx-arch their GPU backends; offload-arch follows
# libomptarget, configured OFF upstream), and shrinks the host tblgen build
# to the tools the target tree still uses. Removal direction is always safe:
# a present-but-unclaimed file just lands in the main libllvm deb.
if [[ -f "$TREE/packages/libllvm/build.sh" ]]; then
  LIBLLVM_BUILD="$TREE/packages/libllvm/build.sh"
  # 1. two device backends only
  if ! grep -qF -- '-DLLVM_TARGETS_TO_BUILD=all' "$LIBLLVM_BUILD"; then
    echo "recipe-overrides: libllvm targets line drifted (expected -DLLVM_TARGETS_TO_BUILD=all) — re-review the trim" >&2
    exit 1
  fi
  sed -i 's#-DLLVM_TARGETS_TO_BUILD=all#-DLLVM_TARGETS_TO_BUILD=AArch64;X86#' "$LIBLLVM_BUILD"
  if grep -qF -- '-DLLVM_TARGETS_TO_BUILD=all' "$LIBLLVM_BUILD"; then
    echo "recipe-overrides: failed to trim LLVM targets" >&2
    exit 1
  fi
  # 2. no experimental backends
  if ! grep -qF -- '-DLLVM_EXPERIMENTAL_TARGETS_TO_BUILD=ARC;CSKY;M68k;VE' "$LIBLLVM_BUILD"; then
    echo "recipe-overrides: libllvm experimental-targets line drifted — re-review the trim" >&2
    exit 1
  fi
  grep -vF -- '-DLLVM_EXPERIMENTAL_TARGETS_TO_BUILD=ARC;CSKY;M68k;VE' "$LIBLLVM_BUILD" > "$LIBLLVM_BUILD.codec-tmp"
  mv "$LIBLLVM_BUILD.codec-tmp" "$LIBLLVM_BUILD"
  # 3-4. drop lldb/mlir/polly from the target project set, and trim the
  #      HOST build in kind (its own -DLLVM_ENABLE_PROJECTS='clang;lldb;mlir'
  #      line and the multi-line ninja tblgen list). The upstream ninja
  #      region is TWO physical lines (backslash continuation + tab-tab
  #      indent) and the cmake string differs from the target one — two
  #      fixture-hidden mismatches proven by dispatch 33544558167 aborting
  #      at ~3.5 min; one python block now edits all three byte-exactly.
  python3 - "$LIBLLVM_BUILD" <<'PY'
import pathlib
import sys

path = pathlib.Path(sys.argv[1])
text = path.read_text()

# All old-byte strings verbatim from the pinned upstream revision
# (termux-packages @ 1bbe66903526df2e8af51e704316bc68ede72603,
# packages/libllvm/build.sh): termux_step_pre_configure's project list, and
# termux_step_host_build's cmake + ninja lines (tab indentation as upstream).
edits = [
    (
        "target project set",
        'local llvm_projects="clang;clang-tools-extra;compiler-rt;lld;lldb;mlir;openmp;polly"',
        'local llvm_projects="clang;clang-tools-extra;compiler-rt;lld;openmp"',
    ),
    (
        "host cmake project set",
        "-DLLVM_ENABLE_PROJECTS='clang;clang-tools-extra;lldb;mlir'",
        "-DLLVM_ENABLE_PROJECTS='clang;clang-tools-extra'",
    ),
    (
        "host ninja tblgen list",
        '\tninja -j "$TERMUX_PKG_MAKE_PROCESSES" clang-tblgen clang-tidy-confusable-chars-gen \\\n'
        '\t\tlldb-tblgen llvm-tblgen mlir-tblgen mlir-linalg-ods-yaml-gen\n',
        '\tninja -j "$TERMUX_PKG_MAKE_PROCESSES" clang-tblgen clang-tidy-confusable-chars-gen llvm-tblgen\n',
    ),
]
for label, old, new in edits:
    if old not in text:
        print(
            f"recipe-overrides: libllvm {label} drifted — re-review the trim",
            file=sys.stderr,
        )
        sys.exit(1)
    text = text.replace(old, new, 1)
path.write_text(text)
print("recipe-overrides: libllvm project sets and host tblgen list trimmed")
PY
  if grep -qF 'lld;lldb;mlir;openmp;polly' "$LIBLLVM_BUILD" || grep -qF "clang;clang-tools-extra;lldb;mlir" "$LIBLLVM_BUILD"; then
    echo "recipe-overrides: failed to trim the libllvm project sets" >&2
    exit 1
  fi
  if grep -q 'mlir-linalg-ods-yaml-gen' "$LIBLLVM_BUILD"; then
    echo "recipe-overrides: failed to trim the libllvm host tblgen list" >&2
    exit 1
  fi
  # 5. exclude the lldb/mlir/libpolly subpackages for CodeC arches
  for sub in lldb mlir libpolly; do
    subfile="$TREE/packages/libllvm/$sub.subpackage.sh"
    if [[ ! -f "$subfile" ]]; then
      echo "recipe-overrides: libllvm subpackage file missing (pinned-revision drift): $subfile" >&2
      exit 1
    fi
    if ! grep -q '^TERMUX_SUBPKG_EXCLUDED_ARCHES=' "$subfile"; then
      sed -i '1i TERMUX_SUBPKG_EXCLUDED_ARCHES="aarch64 x86_64" # CodeC: lldb/mlir/polly are not built (build-time trim)' "$subfile"
    fi
    if ! grep -q '^TERMUX_SUBPKG_EXCLUDED_ARCHES="aarch64 x86_64"' "$subfile"; then
      echo "recipe-overrides: failed to exclude libllvm subpackage: $sub" >&2
      exit 1
    fi
  done
  # 6. remove target-coupled include lines that no longer exist after the
  #    backend trim (kept lines for missing files fail subpackage creation)
  for pair in "clang.subpackage.sh bin/amdgpu-arch" "clang.subpackage.sh bin/nvptx-arch" "clang.subpackage.sh bin/offload-arch" "lld.subpackage.sh bin/wasm-ld"; do
    subfile="$TREE/packages/libllvm/${pair%% *}"
    line="${pair#* }"
    if [[ ! -f "$subfile" ]]; then
      echo "recipe-overrides: libllvm subpackage file missing (pinned-revision drift): $subfile" >&2
      exit 1
    fi
    if ! grep -qxF "$line" "$subfile"; then
      echo "recipe-overrides: $subfile no longer lists '$line' (pinned-revision drift) — re-review the trim" >&2
      exit 1
    fi
    grep -vxF "$line" "$subfile" > "$subfile.codec-tmp"
    mv "$subfile.codec-tmp" "$subfile"
  done
  echo "recipe-overrides: libllvm trimmed to AArch64/X86 backends without lldb/mlir/polly (D10: 360-min ceiling recovery)"
else
  echo "recipe-overrides: libllvm recipe not found; skipping the build-time trim" >&2
fi

# The nodejs recipe (26.4.0-1 at the pinned revision) defines its own
# termux_step_create_debscripts() that emits a preinst notice (npm split out
# of nodejs at 25.3.0-1). CodeC policy forbids maintainer scripts for EVERY
# package (the five approved update-alternatives packages are the only
# exception), and the shared debscripts stub cannot help here: the recipe is
# sourced AFTER the step scripts, so a per-recipe definition overrides the
# stub and the deb ships with DEBIAN/preinst — which generate-repository
# rejects ("maintainer scripts are not allowed"). Same pattern as the
# python/python-pip neutralization: append a last-defined no-op. Fail loudly
# on pinned-revision drift.
NODEJS_BUILD="$TREE/packages/nodejs/build.sh"
if [[ -f "$NODEJS_BUILD" ]]; then
  if ! grep -q '^termux_step_create_debscripts()' "$NODEJS_BUILD"; then
    echo "recipe-overrides: nodejs recipe no longer defines its own termux_step_create_debscripts (pinned-revision drift) — re-review" >&2
    exit 1
  fi
  if ! grep -q 'CodeC: no maintainer scripts for nodejs' "$NODEJS_BUILD"; then
    cat <<'SH' >> "$NODEJS_BUILD"

# CodeC: no maintainer scripts for nodejs — maintainer scripts are forbidden
# for every package. This recipe's own termux_step_create_debscripts (preinst
# npm-split notice) overrides the shared stub because recipes are sourced
# after the step scripts; neutralize it — appended last so this definition
# wins (bash last-definition-wins).
termux_step_create_debscripts() { :; }
SH
  fi
  if ! grep -q 'CodeC: no maintainer scripts for nodejs' "$NODEJS_BUILD"; then
    echo "recipe-overrides: failed to append nodejs debscripts override" >&2
    exit 1
  fi
  if tail -n 8 "$NODEJS_BUILD" | grep -q 'PREINST_EOF\|cat <<-'; then
    echo "recipe-overrides: nodejs recipe still emits a preinst after the override" >&2
    exit 1
  fi
  echo "recipe-overrides: nodejs ships without maintainer scripts"
else
  echo "recipe-overrides: nodejs recipe not found; skipping debscripts neutralization" >&2
fi

# The npm recipe (standalone since nodejs 25.3.0-1; 11.19.0 at the pinned
# revision) defines its own termux_step_create_debscripts() that emits a
# postinst notice (foreground-scripts config hint). Same CodeC policy and the
# same last-definition-wins no-op as nodejs/python-pip above. Fail loudly on
# pinned-revision drift.
NPM_BUILD="$TREE/packages/npm/build.sh"
if [[ -f "$NPM_BUILD" ]]; then
  if ! grep -q '^termux_step_create_debscripts()' "$NPM_BUILD"; then
    echo "recipe-overrides: npm recipe no longer defines its own termux_step_create_debscripts (pinned-revision drift) — re-review" >&2
    exit 1
  fi
  if ! grep -q 'CodeC: no maintainer scripts for npm' "$NPM_BUILD"; then
    cat <<'SH' >> "$NPM_BUILD"

# CodeC: no maintainer scripts for npm — maintainer scripts are forbidden for
# every package. This recipe's own termux_step_create_debscripts (postinst
# foreground-scripts notice) overrides the shared stub because recipes are
# sourced after the step scripts; neutralize it — appended last so this
# definition wins (bash last-definition-wins).
termux_step_create_debscripts() { :; }
SH
  fi
  if ! grep -q 'CodeC: no maintainer scripts for npm' "$NPM_BUILD"; then
    echo "recipe-overrides: failed to append npm debscripts override" >&2
    exit 1
  fi
  if tail -n 8 "$NPM_BUILD" | grep -q 'POSTINST_EOF\|cat <<-'; then
    echo "recipe-overrides: npm recipe still emits a postinst after the override" >&2
    exit 1
  fi
  echo "recipe-overrides: npm ships without maintainer scripts"
else
  echo "recipe-overrides: npm recipe not found; skipping debscripts neutralization" >&2
fi

# The php recipe (8.5.1 at the pinned revision) builds every extension a
# desktop distro wants — and prices them into the source-build closure:
#   --with-ldap{,-sasl}   -> openldap
#   --with-pgsql/--with-pdo-pgsql (+ TERMUX_PKG_BUILD_DEPENDS="postgresql")
#                         -> postgresql
#   --with-apxs2          -> apache2 (apxs is referenced in the MAIN configure;
#                         post_make_install also patchelf's extension .so's
#                         against libexec/apache2)
#   --enable-gd --with-external-gd -> libgd (+ libpng/freetype/jpeg/webp/avif)
# A phone IDE runs `php` and `php -S` — none of those extensions. Trim the
# flags (the "shared" extensions then never build), drop the postgresql build
# dependency, DELETE the apache/ldap/pgsql/gd subpackage files (see below:
# exclusion can't keep their TERMUX_SUBPKG_DEPENDS out of the arch-neutral
# buildorder closure — the libgd file alone dragged libheif/gdk-pixbuf/
# libx264 into the langs legs of run 33598824226), and replace
# termux_step_post_make_install with a trimmed twin that skips the php-apache
# assembly and writes conf.d entries only for the extensions that still ship
# (sodium). php-fpm/php-sodium stay.
# Every removal verifies an exact upstream line first and fails loudly on
# pinned-revision drift.
PHP_DIR="$TREE/packages/php"
if [[ -d "$PHP_DIR" ]]; then
  PHP_BUILD="$PHP_DIR/build.sh"
  if [[ ! -f "$PHP_BUILD" ]]; then
    echo "recipe-overrides: php recipe build.sh missing (pinned-revision drift)" >&2
    exit 1
  fi
  # Exact lines from the pinned revision (termux-packages @
  # 1bbe66903526df2e8af51e704316bc68ede72603, packages/php/build.sh
  # TERMUX_PKG_EXTRA_CONFIGURE_ARGS). The gd php_cv_* cache lines stay: with
  # --enable-gd removed they are inert, and keeping them shrinks the diff.
  # Removal is a whole-line fixed-string filter (grep -vxF): no regex
  # escaping games — the flags contain '/', '$' and '.'.
  for flag in \
    '--with-ldap=shared,$TERMUX_PREFIX' \
    '--with-ldap-sasl' \
    '--with-pgsql=shared,$TERMUX_PREFIX' \
    '--with-pdo-pgsql=shared,$TERMUX_PREFIX' \
    '--with-apxs2=$TERMUX_PKG_TMPDIR/apxs-wrapper.sh' \
    '--enable-gd=shared,$TERMUX_PREFIX' \
    '--with-external-gd' \
  ; do
    if ! grep -qxF -- "$flag" "$PHP_BUILD"; then
      echo "recipe-overrides: php recipe no longer has the expected configure flag '$flag' (pinned-revision drift) — re-review the php trim" >&2
      exit 1
    fi
    grep -vxF -- "$flag" "$PHP_BUILD" > "$PHP_BUILD.codec-tmp"
    mv "$PHP_BUILD.codec-tmp" "$PHP_BUILD"
    if grep -qxF -- "$flag" "$PHP_BUILD"; then
      echo "recipe-overrides: failed to remove php configure flag '$flag'" >&2
      exit 1
    fi
  done
  echo "recipe-overrides: php trimmed apache/ldap/pgsql/gd configure flags"
  if ! grep -q '^TERMUX_PKG_BUILD_DEPENDS="postgresql"$' "$PHP_BUILD"; then
    echo "recipe-overrides: php recipe build-depends no longer has the expected postgresql line (pinned-revision drift) — re-review" >&2
    exit 1
  fi
  sed -i '/^TERMUX_PKG_BUILD_DEPENDS="postgresql"$/d' "$PHP_BUILD"
  if grep -q '^TERMUX_PKG_BUILD_DEPENDS=' "$PHP_BUILD"; then
    echo "recipe-overrides: failed to remove the postgresql build dependency from php" >&2
    exit 1
  fi
  echo "recipe-overrides: php builds without the postgresql build dependency"
  if ! grep -q '^termux_step_post_make_install()' "$PHP_BUILD"; then
    echo "recipe-overrides: php recipe no longer defines termux_step_post_make_install (pinned-revision drift) — re-review" >&2
    exit 1
  fi
  if ! grep -q 'CodeC: php trimmed post_make_install' "$PHP_BUILD"; then
    cat <<'SH' >> "$PHP_BUILD"

# CodeC: php trimmed post_make_install — the trimmed build has no shared
# ldap/pgsql/gd extensions and no apache, so the upstream function (which
# assembles apache-specific extension copies and relinks them) can never
# pass. This twin keeps the remaining behavior: php-fpm config, the
# php.ini templates, conf.d entries only for extensions that still ship
# (sodium), and the phpize SED fix. Appended last so this definition wins
# (bash last-definition-wins).
termux_step_post_make_install() {
	mkdir -p $TERMUX_PREFIX/etc/php-fpm.d
	cp sapi/fpm/php-fpm.conf $TERMUX_PREFIX/etc/
	cp sapi/fpm/www.conf $TERMUX_PREFIX/etc/php-fpm.d/

	docdir=$TERMUX_PREFIX/share/doc/php
	mkdir -p $docdir
	for suffix in development production; do
		cp $TERMUX_PKG_SRCDIR/php.ini-$suffix $docdir/
	done

	# CodeC: conf.d entries only for extensions still built (sodium).
	local extdir="$TERMUX_PREFIX/etc/$TERMUX_PKG_NAME/conf.d"
	mkdir -p "$extdir"
	for ext in sodium; do
		echo "extension=$ext" > "$extdir/$ext.ini"
	done

	sed -i 's/SED=.*/SED=sed/' $TERMUX_PREFIX/bin/phpize
}
SH
  fi
  if ! grep -q 'CodeC: php trimmed post_make_install' "$PHP_BUILD"; then
    echo "recipe-overrides: failed to append php trimmed post_make_install" >&2
    exit 1
  fi
  # Only the winning (appended) definition may be scanned — the upstream
  # function above it legitimately still carries the apache assembly — and
  # comment lines are stripped so descriptive text can never false-trigger.
  if sed -n '/CodeC: php trimmed post_make_install/,$p' "$PHP_BUILD" | grep -v '^\s*#' | grep -qE 'patchelf --set-rpath|lib/php-apache'; then
    echo "recipe-overrides: php trimmed post_make_install still contains the apache assembly block" >&2
    exit 1
  fi
  if ! sed -n '/CodeC: php trimmed post_make_install/,$p' "$PHP_BUILD" | grep -v '^\s*#' | grep -q 'for ext in sodium; do'; then
    echo "recipe-overrides: php trimmed post_make_install lost the sodium-only conf.d loop" >&2
    exit 1
  fi
  # The excluded subpackages must be DELETED, not merely arch-skipped:
  # TERMUX_SUBPKG_EXCLUDED_ARCHES only suppresses deb *creation* — the
  # arch-neutral buildorder resolver still scans every *.subpackage.sh
  # TERMUX_SUBPKG_DEPENDS when computing the build closure, so exclusion
  # alone silently builds the entire phantom closure anyway (run
  # 33598824226: php-gd's TERMUX_SUBPKG_DEPENDS="libgd" dragged in
  # libgd → libheif → gdk-pixbuf + libde265 + libx264 — gdk-pixbuf's
  # upstream postinst failed the repo validator and libx264's videolan
  # source URL is dead upstream). Deleting the files removes them from both
  # packaging AND dependency resolution. Fail loudly on drift, as before —
  # a missing file means the pinned revision restructured php packaging.
  for sub in php-apache php-apache-ldap php-apache-pgsql php-apache-sodium php-ldap php-pgsql php-gd; do
    subfile="$PHP_DIR/$sub.subpackage.sh"
    if [[ ! -f "$subfile" ]]; then
      echo "recipe-overrides: php subpackage file missing (pinned-revision drift): $subfile" >&2
      exit 1
    fi
    rm "$subfile"
    if [[ -e "$subfile" ]]; then
      echo "recipe-overrides: failed to delete php subpackage: $sub" >&2
      exit 1
    fi
    echo "recipe-overrides: php subpackage $sub deleted (no apache/openldap/postgresql/libgd closures in the userland — exclusion alone cannot keep them out of the build closure)"
  done
else
  echo "recipe-overrides: php recipe not found; skipping the php trim" >&2
fi

# The lua54 recipe (5.4.8-10 at the pinned revision) registers bin/lua and
# bin/luac through the update-alternatives postinst generated from
# lua54.alternatives. CodeC's repository validator approves maintainer
# scripts only for the five reviewed alternatives packages (coreutils, less,
# nano, bat, util-linux); lua54 is not among them, so the generated deb would
# be rejected. A phone IDE never needs a second Lua version, so instead of
# reviewing/extending the allowlist: remove the .alternatives file (the
# alternatives step then emits nothing) and ship plain RELATIVE symlinks in
# termux_step_post_massage — bin/lua -> lua5.4, bin/luac -> luac5.4, and the
# matching renamed man pages (the recipe renames them pre-massage; massage
# compresses them, so the .gz targets exist when post_massage runs). Relative
# targets keep the massage symlink-relativizer a no-op. Fail loudly if the
# alternatives file vanishes or changes shape (a re-review is then due).
LUA54_DIR="$TREE/packages/lua54"
if [[ -d "$LUA54_DIR" ]]; then
  LUA54_ALT="$LUA54_DIR/lua54.alternatives"
  LUA54_BUILD="$LUA54_DIR/build.sh"
  if [[ ! -f "$LUA54_ALT" ]]; then
    echo "recipe-overrides: lua54.alternatives missing (pinned-revision drift) — re-review: bin/lua may now be provided differently" >&2
    exit 1
  fi
  if ! grep -q '^Alternative: bin/lua5.4$' "$LUA54_ALT" || ! grep -q '^Alternative: bin/luac5.4$' "$LUA54_ALT"; then
    echo "recipe-overrides: lua54.alternatives no longer registers lua5.4/luac5.4 (pinned-revision drift) — re-review the symlink override" >&2
    exit 1
  fi
  if [[ ! -f "$LUA54_BUILD" ]]; then
    echo "recipe-overrides: lua54 recipe build.sh missing (pinned-revision drift)" >&2
    exit 1
  fi
  rm -f "$LUA54_ALT"
  if [[ -e "$LUA54_ALT" ]]; then
    echo "recipe-overrides: failed to remove lua54.alternatives" >&2
    exit 1
  fi
  if ! grep -q 'CodeC: lua/luac plain symlinks' "$LUA54_BUILD"; then
    cat <<'SH' >> "$LUA54_BUILD"

# CodeC: lua/luac plain symlinks — the upstream .alternatives postinst is
# removed (maintainer scripts are approved only for the five reviewed
# packages), so provide the same links as plain relative symlinks in the
# package payload instead.
termux_step_post_massage() {
	ln -sf lua5.4 "$TERMUX_PREFIX/bin/lua"
	ln -sf luac5.4 "$TERMUX_PREFIX/bin/luac"
	if [ -f "$TERMUX_PREFIX/share/man/man1/lua5.4.1.gz" ]; then
		ln -sf lua5.4.1.gz "$TERMUX_PREFIX/share/man/man1/lua.1.gz"
	fi
	if [ -f "$TERMUX_PREFIX/share/man/man1/luac5.4.1.gz" ]; then
		ln -sf luac5.4.1.gz "$TERMUX_PREFIX/share/man/man1/luac.1.gz"
	fi
}
SH
  fi
  if ! grep -q 'CodeC: lua/luac plain symlinks' "$LUA54_BUILD"; then
    echo "recipe-overrides: failed to append lua54 symlink override" >&2
    exit 1
  fi
  echo "recipe-overrides: lua54 ships bin/lua and bin/luac as plain symlinks (no .alternatives postinst)"
else
  echo "recipe-overrides: lua54 recipe not found; skipping alternatives removal" >&2
fi
