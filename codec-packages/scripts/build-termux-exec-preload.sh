#!/usr/bin/env bash
# Host-side wrapper: run termux-exec-standalone.sh inside the termux package
# builder container and collect the LD_PRELOAD library.
#
# Usage: build-termux-exec-preload.sh <termux-packages tree> <arch> <out dir>
set -euo pipefail

SRC="${1:?termux-packages tree}"
ARCH="${2:?arch}"
OUT_DIR="${3:?output dir}"

CONTAINER_NAME="${CODEC_CONTAINER_NAME:-codec-bootstrap-${ARCH}}"
WORK="$SRC/.codec"
mkdir -p "$WORK"

# The container only sees the termux-packages tree (mounted at
# /home/builder/termux-packages), so the in-container script must live there.
cp "$(dirname "$0")/termux-exec-standalone.sh" "$WORK/termux-exec-standalone.sh"
chmod +x "$WORK/termux-exec-standalone.sh"

(
  cd "$SRC"
  CONTAINER_NAME="$CONTAINER_NAME" ./scripts/run-docker.sh \
    /home/builder/termux-packages/.codec/termux-exec-standalone.sh "$ARCH"
)

STAGED="$SRC/.codec/tre-out-$ARCH/lib"
mkdir -p "$OUT_DIR/lib"
if [[ -s "$STAGED/libtermux-exec-ld-preload.so" ]]; then
  cp -a "$STAGED/libtermux-exec-ld-preload.so" "$OUT_DIR/lib/"
  ln -sfn libtermux-exec-ld-preload.so "$OUT_DIR/lib/libtermux-exec.so"
  echo "preload: staged $OUT_DIR/lib/libtermux-exec-ld-preload.so"
else
  echo "preload: standalone build did not produce a library" >&2
  exit 1
fi
