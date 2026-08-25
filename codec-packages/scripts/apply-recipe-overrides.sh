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

# dist.schmorp.de repeatedly timed out from GitHub Actions package builds (run 32781913358)
# while fetching rxvt-unicode-9.31.tar.bz2. Debian's official CDN mirror deb.debian.org
# serves the identical, hash-verified source archive directly via HTTPS without timeouts.
RXVT_UNICODE_RECIPE="$TREE/packages/rxvt-unicode/build.sh"
if [[ -f "$RXVT_UNICODE_RECIPE" ]]; then
  sed -i \
    's#https\?://dist\.schmorp\.de/rxvt-unicode/\(Attic/\)\?rxvt-unicode-\(.*\)\.tar\.bz2#https://deb.debian.org/debian/pool/main/r/rxvt-unicode/rxvt-unicode_\2.orig.tar.bz2#' \
    "$RXVT_UNICODE_RECIPE"
  if grep -qE 'dist\.schmorp\.de' "$RXVT_UNICODE_RECIPE"; then
    echo "recipe-overrides: failed to update rxvt-unicode source URL" >&2
    exit 1
  fi
  grep -qF 'https://deb.debian.org/debian/pool/main/r/rxvt-unicode/' "$RXVT_UNICODE_RECIPE"
  echo "recipe-overrides: rxvt-unicode uses official Debian HTTPS mirror"
else
  echo "recipe-overrides: rxvt-unicode recipe not found; skipping" >&2
fi

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

# Exclude python-xcbgen subpackage in xcb-proto and disable maintainer scripts on xcb-proto:
# CodeC userland does not ship Python or X11 Python bindings.
# The official python-xcbgen subpackage and xcb-proto default debscripts create
# unapproved postinst/prerm maintainer scripts (python byte-compilation hooks)
# which are forbidden by CodeC repository policy.
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
  XCB_PROTO_BUILD="$XCB_PROTO_DIR/build.sh"
  if [[ -f "$XCB_PROTO_BUILD" ]]; then
    if ! grep -q "termux_step_create_debscripts" "$XCB_PROTO_BUILD"; then
      echo "termux_step_create_debscripts() { :; }" >> "$XCB_PROTO_BUILD"
    fi
    echo "recipe-overrides: disabled maintainer scripts for xcb-proto"
  fi
else
  echo "recipe-overrides: xcb-proto recipe not found; skipping python-xcbgen exclusion" >&2
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

# Convert absolute symlinks in $TERMUX_PREFIX to relative symlinks in termux_step_massage.sh
# BEFORE subpackages are created (termux_create_debian_subpackages).
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
    print("recipe-overrides: patched termux_step_massage.sh to convert absolute symlinks to relative before subpackage creation")
else:
    print("recipe-overrides: termux_step_massage.sh already patched for symlinks")
PY
  grep -q "CodeC override: convert absolute symlinks" "$MASSAGE_SCRIPT"
else
  echo "recipe-overrides: termux_step_massage.sh not found; skipping absolute symlink fix" >&2
fi
