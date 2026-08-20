#!/usr/bin/env bash
# Clone pinned termux-packages, override app id, build v1 set via Termux's
# own run-docker.sh (fuse + builder user). Do NOT pass -I: that installs
# official Termux .debs with com.termux baked in.
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
  echo "cloning $TERMUX_PACKAGES_REPO @ $TERMUX_PACKAGES_REF"
  git clone --depth 1 --branch "$TERMUX_PACKAGES_REF" "$TERMUX_PACKAGES_REPO" "$SRC"
fi

"$ROOT/scripts/apply-prefix.sh" "$SRC"

PACKAGES=$(echo "$CODEC_BOOTSTRAP_PACKAGES" | tr '\n' ' ')

if [[ ! -x "$SRC/scripts/run-docker.sh" ]]; then
  echo "build-bootstrap: missing $SRC/scripts/run-docker.sh" >&2
  exit 1
fi

# GHA has no TTY; run-docker.sh already handles that.
export CI="${CI:-true}"

cd "$SRC"
for p in $PACKAGES; do
  [[ -z "$p" ]] && continue
  echo "=== building $p ($ARCH) ==="
  # -f force this package; do not -I (wrong prefix debs).
  ./scripts/run-docker.sh ./build-package.sh -f -a "$ARCH" "$p"
done

"$ROOT/scripts/assemble-bootstrap.sh" "$SRC" "$ARCH" "$DIST"
