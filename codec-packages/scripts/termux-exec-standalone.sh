#!/usr/bin/env bash
# Build the termux-exec LD_PRELOAD library from pinned public sources.
#
# The official `termux-exec` recipe cannot be built in CodeC CI: its
# `TERMUX_PKG_BUILD_DEPENDS="termux-core-static"` is a prebuilt package that
# only exists on Termux's private build farm, and CodeC must not use official
# com.termux packages. Instead this script builds the two static libraries
# (termux-core, termux-exec) directly from their pinned source archives with
# the same toolchain the rest of the CodeC bootstrap uses, then builds only
# the `direct` LD_PRELOAD variant that CodeC exports.
#
# Runs INSIDE the termux package-builder container at the root of the
# (CodeC-patched) termux-packages tree. Output:
#   .codec/tre-out-<arch>/lib/libtermux-exec-ld-preload.so   (real file)
#   .codec/tre-out-<arch>/lib/libtermux-exec.so              (symlink)
set -euo pipefail

ARCH="${1:?usage: termux-exec-standalone.sh <arch>}"
TREE="$(pwd)"
if [[ "$(basename "$TREE")" != "termux-packages" ]]; then
  echo "tre-standalone: must run at the termux-packages tree root (got $TREE)" >&2
  exit 1
fi

# Pinned upstream sources (same versions/SHA-256 as the official pinned
# termux-packages recipes; never official .deb files).
CORE_VERSION="0.4.0"
CORE_SHA256="af6299f341292ca98d1748a06e342fe29fbc9eb485a7c1ba5c9f91ba72b4f44a"
EXEC_VERSION="2.5.0"
EXEC_SHA256="5c5eeb1565ad4379ce227ee3017f9fe88611c03ca91f00b8a3fadcf6f7396f51"

WORK="$TREE/.codec/tre-work-$ARCH"
OUT="$TREE/.codec/tre-out-$ARCH"
rm -rf "$WORK" "$OUT"
mkdir -p "$WORK" "$OUT"

log() { printf 'tre-standalone: %s\n' "$*"; }
fail() { log "FAILED: $*"; exit 1; }

# --- Build environment (mirrors the recipe build) ----------------------------
export TERMUX_ARCH="$ARCH"
export TERMUX_ON_DEVICE_BUILD=false
export TERMUX_PACKAGE_LIBRARY=bionic
export TERMUX_TOPDIR="$TREE"
export TERMUX_SCRIPTDIR="$TREE"
# shellcheck source=/dev/null
source "$TREE/scripts/properties.sh"
: "${TERMUX_PKG_API_LEVEL:=24}"
export TERMUX_PKG_API_LEVEL
export TERMUX_COMMON_CACHEDIR="${TERMUX_COMMON_CACHEDIR:-$TERMUX_TOPDIR/_cache}"
log "ARCH=$TERMUX_ARCH API_LEVEL=$TERMUX_PKG_API_LEVEL PREFIX=$TERMUX_PREFIX APP=$TERMUX_APP__PACKAGE_NAME"
log "NDK_VERSION=$TERMUX_NDK_VERSION CACHEDIR=$TERMUX_COMMON_CACHEDIR"

# Toolchain (same standalone NDK toolchain the recipes use; already warm in
# the container from the package builds that just ran).
# shellcheck source=/dev/null
source "$TREE/scripts/build/toolchain/termux_setup_toolchain_29.sh" 2>/dev/null \
  || source "$TREE/scripts/build/toolchain/termux_setup_toolchain_23c.sh"
# shellcheck source=/dev/null
source "$TREE/scripts/build/termux_step_setup_toolchain.sh"
termux_step_setup_toolchain
export PATH="$TERMUX_STANDALONE_TOOLCHAIN/bin:$PATH"
command -v "$CC" >/dev/null 2>&1 || fail "toolchain compiler '$CC' not found on PATH ($PATH)"
log "CC=$CC TOOLCHAIN=$TERMUX_STANDALONE_TOOLCHAIN"

# --- 1. termux-core static library -------------------------------------------
log "downloading termux-core v$CORE_VERSION"
curl -fsSL --retry 3 -o "$WORK/core.tar.gz" \
  "https://github.com/termux/termux-core-package/archive/refs/tags/v${CORE_VERSION}.tar.gz"
printf '%s  %s\n' "$CORE_SHA256" "$WORK/core.tar.gz" | sha256sum -c - \
  || fail "termux-core source SHA-256 mismatch"
tar -xzf "$WORK/core.tar.gz" -C "$WORK"
CORE_SRC="$WORK/termux-core-package-$CORE_VERSION"
[[ -d "$CORE_SRC" ]] || fail "termux-core source tree missing"

log "building libtermux-core_nos_c_tre.a"
cd "$CORE_SRC"
# The target also builds test binaries that need extra toolchain bits; the
# static library itself is produced before that stage, so accept the target
# failing after the .a exists.
make build-libtermux-core_nos_c_tre || true
CORE_LIB="$CORE_SRC/build/output/usr/lib/libtermux-core_nos_c_tre.a"
[[ -s "$CORE_LIB" ]] || fail "libtermux-core_nos_c_tre.a was not produced"
log "core static library: $CORE_LIB"

# The termux-exec Makefile expects the core headers at
# $TERMUX__PREFIX/include/termux-core and finds the static library via
# -L$TERMUX__PREFIX/lib (the toolchain LDFLAGS). Both point at the CodeC
# prefix, which does not exist inside the container: create it (builder has
# passwordless sudo in the image).
sudo mkdir -p "$TERMUX_PREFIX/include/termux-core" "$TERMUX_PREFIX/lib"
cp -r "$CORE_SRC/lib/termux-core_nos_c/tre/include/." "$TERMUX_PREFIX/include/termux-core/"
cp -a "$CORE_LIB" "$TERMUX_PREFIX/lib/"
log "installed core headers + static lib under $TERMUX_PREFIX"

# --- 2. termux-exec direct LD_PRELOAD variant ---------------------------------
log "downloading termux-exec v$EXEC_VERSION"
curl -fsSL --retry 3 -o "$WORK/exec.tar.gz" \
  "https://github.com/termux/termux-exec-package/archive/refs/tags/v${EXEC_VERSION}.tar.gz"
printf '%s  %s\n' "$EXEC_SHA256" "$WORK/exec.tar.gz" | sha256sum -c - \
  || fail "termux-exec source SHA-256 mismatch"
tar -xzf "$WORK/exec.tar.gz" -C "$WORK"
EXEC_SRC="$WORK/termux-exec-package-$EXEC_VERSION"
[[ -d "$EXEC_SRC" ]] || fail "termux-exec source tree missing"

cd "$EXEC_SRC"
log "building libtermux-exec-ld-preload.so (direct variant)"
# Bake the same CodeC identity constants the official recipe build would.
make \
  TERMUX_EXEC_PKG__ARCH="$TERMUX_ARCH" \
  TERMUX__NAME="${TERMUX__NAME}" \
  TERMUX__LNAME="${TERMUX__LNAME}" \
  TERMUX_APP__NAME="${TERMUX_APP__NAME}" \
  TERMUX_APP__PACKAGE_NAME="${TERMUX_APP__PACKAGE_NAME}" \
  TERMUX_APP__DATA_DIR="${TERMUX_APP__DATA_DIR}" \
  TERMUX__ROOTFS="${TERMUX__ROOTFS}" \
  TERMUX__PREFIX="${TERMUX__PREFIX}" \
  build-libtermux-exec_nos_c_tre \
  build-libtermux-exec-direct-ld-preload

PRELOAD="$EXEC_SRC/build/output/usr/lib/libtermux-exec-ld-preload.so"
[[ -s "$PRELOAD" ]] || fail "libtermux-exec-ld-preload.so was not produced"

# --- 3. Collect artifacts ------------------------------------------------------
mkdir -p "$OUT/lib"
cp -a "$PRELOAD" "$OUT/lib/libtermux-exec-ld-preload.so"
rm -f "$OUT/lib/libtermux-exec.so"
ln -s libtermux-exec-ld-preload.so "$OUT/lib/libtermux-exec.so"
log "output:"
find "$OUT" -exec ls -la {} +
log "OK: termux-exec LD_PRELOAD library built for $ARCH"
