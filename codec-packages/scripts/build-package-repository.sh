#!/usr/bin/env bash
# Build a curated CodeC package closure from official Termux recipes.
# This deliberately never uses the dependency-download shortcut: all
# dependencies are rebuilt for com.codeci.ide and the CodeC prefix.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
# shellcheck disable=SC1091
source "$ROOT/properties.codec.sh"

ARCH="${1:-aarch64}"
WORK="${CODEC_REPO_WORK:-$ROOT/.work/repository-$ARCH}"
SRC="$WORK/termux-packages"
DIST="$ROOT/dist/repository-$ARCH"
mkdir -p "$WORK" "$ROOT/dist"

case "$ARCH" in
  aarch64|x86_64) ;;
  *) echo "build-package-repository: unsupported architecture: $ARCH" >&2; exit 2 ;;
esac

if [[ ! -d "$SRC/.git" ]]; then
  git clone --filter=blob:none --no-checkout "$TERMUX_PACKAGES_REPO" "$SRC"
  git -C "$SRC" fetch --depth 1 origin "$TERMUX_PACKAGES_REF"
  git -C "$SRC" checkout --detach "$TERMUX_PACKAGES_REF"
fi

"$ROOT/scripts/apply-prefix.sh" "$SRC"
"$ROOT/scripts/apply-recipe-overrides.sh" "$SRC"

PACKAGES=$(printf '%s\n' "$CODEC_REPOSITORY_PACKAGES" | awk 'NF { print $1 }')
if [[ -z "$PACKAGES" ]]; then
  echo "build-package-repository: no repository packages configured" >&2
  exit 2
fi

echo "CodeC repository roots ($ARCH):"
printf '  %s\n' $PACKAGES

# Keep the build output limited to this architecture. The package builder will
# still place the complete source-built dependency closure in output/.
rm -rf "$SRC/output" "$SRC/debs"

build_one() {
  local package="$1"
  if [[ "${CODEC_USE_DOCKER:-1}" == "1" ]]; then
    if [[ ! -x "$SRC/scripts/run-docker.sh" ]]; then
      echo "build-package-repository: official run-docker.sh is missing" >&2
      exit 2
    fi
    (cd "$SRC" && ./scripts/run-docker.sh ./build-package.sh -a "$ARCH" -f "$package")
  else
    echo "build-package-repository: CODEC_USE_DOCKER=0 (host build)" >&2
    (cd "$SRC" && ./build-package.sh -a "$ARCH" -f "$package")
  fi
}

for package in $PACKAGES; do
  build_one "$package"
done

DEB_DIR="$SRC/output"
[[ -d "$DEB_DIR" ]] || DEB_DIR="$SRC/debs"
[[ -d "$DEB_DIR" ]] || {
  echo "build-package-repository: no package output directory" >&2
  exit 1
}

"$ROOT/scripts/generate-repository.sh" "$DEB_DIR" "$DIST" "$ARCH"
echo "build-package-repository: wrote $DIST"
