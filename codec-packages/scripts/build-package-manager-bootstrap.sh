#!/usr/bin/env bash
# Build a Phase 3 bootstrap that seeds CodeC-built apt and dpkg.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
# shellcheck disable=SC1091
source "$ROOT/properties.codec.sh"

ARCH="${1:-aarch64}"
CODEC_BOOTSTRAP_NAME=bootstrap-phase3 \
CODEC_BOOTSTRAP_PACKAGES_OVERRIDE="$CODEC_PACKAGE_MANAGER_BOOTSTRAP_PACKAGES" \
CODEC_BOOTSTRAP_SEED_PACKAGES="$CODEC_BOOTSTRAP_SEED_PACKAGES" \
  "$ROOT/scripts/build-bootstrap.sh" "$ARCH"
