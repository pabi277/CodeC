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

# xorg.freedesktop.org repeatedly timed out from both GitHub Actions package
# builds (run 32585409356) while fetching util-macros-1.20.2. X.Org's official
# download host serves the identical, hash-verified source archive. Limit this
# transport fallback to the one observed recipe and URL prefix.
UTIL_MACROS_RECIPE="$TREE/packages/util-macros/build.sh"
if [[ -f "$UTIL_MACROS_RECIPE" ]]; then
  sed -i \
    's#https://xorg\.freedesktop\.org/releases/individual/util/#https://www.x.org/releases/individual/util/#' \
    "$UTIL_MACROS_RECIPE"
  if grep -qF 'https://xorg.freedesktop.org/releases/individual/util/' "$UTIL_MACROS_RECIPE"; then
    echo "recipe-overrides: failed to update util-macros source URL" >&2
    exit 1
  fi
  grep -qF 'https://www.x.org/releases/individual/util/' "$UTIL_MACROS_RECIPE"
  echo "recipe-overrides: util-macros uses official X.Org download mirror"
else
  echo "recipe-overrides: util-macros recipe not found; skipping" >&2
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
