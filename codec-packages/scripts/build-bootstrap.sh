#!/usr/bin/env bash
# Clone pinned termux-packages, override PREFIX, build v1 set, pack tarball.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
# shellcheck disable=SC1091
source "$ROOT/properties.codec.sh"

ARCH="${1:-aarch64}"
BOOTSTRAP_NAME="${CODEC_BOOTSTRAP_NAME:-bootstrap}"
WORK="${CODEC_WORK:-$ROOT/.work}"
SRC="$WORK/termux-packages"
DIST="$ROOT/dist"
CONTAINER_NAME="${CODEC_CONTAINER_NAME:-codec-${BOOTSTRAP_NAME}-${ARCH}}"
mkdir -p "$WORK" "$DIST"

if [[ ! -d "$SRC/.git" ]]; then
  git clone --filter=blob:none --no-checkout "$TERMUX_PACKAGES_REPO" "$SRC"
  git -C "$SRC" fetch --depth 1 origin "$TERMUX_PACKAGES_REF"
  git -C "$SRC" checkout --detach "$TERMUX_PACKAGES_REF"
fi

"$ROOT/scripts/apply-prefix.sh" "$SRC"
"$ROOT/scripts/apply-recipe-overrides.sh" "$SRC"

# CodeC does not use Termux's Android activity-manager wrappers.
# Avoid the termux-tools -> termux-am Gradle/Android SDK dependency.
BASH_RECIPE="$SRC/packages/bash/build.sh"
sed -i 's/, termux-tools//' "$BASH_RECIPE"
if grep '^TERMUX_PKG_DEPENDS=' "$BASH_RECIPE" | grep -q 'termux-tools'; then
  echo "Failed to remove termux-tools from Bash dependencies" >&2
  exit 1
fi
echo "CodeC Bash dependencies:"
grep '^TERMUX_PKG_DEPENDS=' "$BASH_RECIPE"

BOOTSTRAP_PACKAGES="${CODEC_BOOTSTRAP_PACKAGES_OVERRIDE:-$CODEC_BOOTSTRAP_PACKAGES}"
PACKAGES=$(echo "$BOOTSTRAP_PACKAGES" | tr '\n' ' ')

if [[ "${CODEC_USE_DOCKER:-1}" == "1" ]] && command -v docker >/dev/null 2>&1; then
  if [[ -x "$SRC/scripts/run-docker.sh" ]]; then
    echo "=== Using termux-packages native scripts/run-docker.sh ==="
    (
      cd "$SRC"
      for p in $PACKAGES; do
        CONTAINER_NAME="$CONTAINER_NAME" \
          ./scripts/run-docker.sh ./build-package.sh -a "$ARCH" -f "$p"
      done
    )
  else
    echo "=== Fallback to direct docker run ==="
    docker pull ghcr.io/termux/package-builder:latest || true
    docker run --rm --privileged \
      -v "$SRC:/home/builder/termux-packages" \
      -v "$ROOT:/home/builder/codec-packages:ro" \
      ghcr.io/termux/package-builder:latest \
      bash -lc "
        set -e
        cd /home/builder/termux-packages
        /home/builder/codec-packages/scripts/apply-prefix.sh /home/builder/termux-packages
        /home/builder/codec-packages/scripts/apply-recipe-overrides.sh /home/builder/termux-packages
        for p in $PACKAGES; do
          ./build-package.sh -a $ARCH -f \$p
        done
      "
  fi
else
  echo "CODEC_USE_DOCKER=0 or no docker — building on host"
  for p in $PACKAGES; do
    (cd "$SRC" && ./build-package.sh -a "$ARCH" -f "$p")
  done
fi

"$ROOT/scripts/assemble-bootstrap.sh" "$SRC" "$ARCH" "$DIST"
