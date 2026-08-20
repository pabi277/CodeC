#!/usr/bin/env bash
# Clone pinned termux-packages, override PREFIX, build v1 set, pack tarball.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
# shellcheck disable=SC1091
source "$ROOT/properties.codec.sh"

ARCH="${1:-aarch64}"
WORK="${CODEC_WORK:-$ROOT/.work}"
SRC="$WORK/termux-packages"
DIST="$ROOT/dist"
mkdir -p "$WORK" "$DIST"

if [[ ! -d "$SRC/.git" ]]; then
  git clone --depth 1 --branch "$TERMUX_PACKAGES_REF" "$TERMUX_PACKAGES_REPO" "$SRC" \
    || git clone --depth 1 "$TERMUX_PACKAGES_REPO" "$SRC"
fi

"$ROOT/scripts/apply-prefix.sh" "$SRC"

PACKAGES=$(echo "$CODEC_BOOTSTRAP_PACKAGES" | tr '\n' ' ')

run_build() {
  local pkg="$1"
  echo "=== building $pkg ($ARCH) ==="
  if [[ -x "$SRC/build-package.sh" ]]; then
    (cd "$SRC" && ./build-package.sh -a "$ARCH" -I "$pkg")
  else
    echo "build-package.sh missing" >&2
    return 1
  fi
}

if [[ "${CODEC_USE_DOCKER:-1}" == "1" ]] && command -v docker >/dev/null 2>&1; then
  docker pull ghcr.io/termux/package-builder:latest || true
  docker run --rm \
    -v "$SRC:/home/builder/termux-packages" \
    -v "$ROOT:/home/builder/codec-packages:ro" \
    ghcr.io/termux/package-builder:latest \
    bash -lc "
      set -e
      cd /home/builder/termux-packages
      /home/builder/codec-packages/scripts/apply-prefix.sh /home/builder/termux-packages
      for p in $PACKAGES; do
        ./build-package.sh -a $ARCH -I \$p
      done
    "
else
  echo "CODEC_USE_DOCKER=0 or no docker — building on host"
  for p in $PACKAGES; do
    run_build "$p"
  done
fi

"$ROOT/scripts/assemble-bootstrap.sh" "$SRC" "$ARCH" "$DIST"
