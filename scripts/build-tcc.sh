#!/usr/bin/env bash
# Builds self-contained TCC (Tiny C Compiler) bundles for Android ABIs.
#
# TCC is statically linked against musl libc, so:
#  - the tcc binary runs on any Android device (no bionic/NDK dependency),
#  - programs compiled by tcc are fully static ELF executables that run
#    anywhere on the device.
#
# Each bundle contains everything tcc needs at compile time:
#   tcc          - the compiler binary (static musl ELF)
#   include-tcc/ - tcc's own headers (stdarg.h etc., searched first)
#   include/     - musl libc headers (kernel UAPI dirs trimmed; only musl's
#                  own headers are kept — see the grep check below)
#   libc.a, crt1.o, crti.o, crtn.o, libtcc1.a - static link support
#
# The bundle is relocatable: crt/lib paths are baked as "." and the app runs
# tcc with the bundle directory as its working directory
# (`-static -std=c11 -Wall -Wextra -O0..3 -I include-tcc -I include -L .`).
#
# Requires a musl cross toolchain per ABI. The script accepts either a local
# directory (--toolchain <dir>) or downloads from musl.cc when run somewhere
# with unrestricted network (e.g. a CI runner).
#
# tinycc is pinned to the same "mob" commit Termux ships (aarch64 fixes).

set -euo pipefail

TINYCC_COMMIT=6a24b762d3e1086dcffd002c68cb5ca3a33a5c6d
MUSL_CC_BASE=https://musl.cc
JOBS=${JOBS:-$(nproc)}

TOOLCHAIN_DIR=""
while [ $# -gt 0 ]; do
  case "$1" in
    --toolchain) TOOLCHAIN_DIR="$2"; shift 2 ;;
    *) echo "usage: $0 [--toolchain <dir>]" >&2; exit 2 ;;
  esac
done

rm -rf work dist
mkdir -p work dist
cd work

if [ ! -d tinycc ]; then
  echo "==> Cloning tinycc (mob @ ${TINYCC_COMMIT})"
  git clone --quiet https://github.com/C-Chads/tinycc.git tinycc
  git -C tinycc checkout --quiet "$TINYCC_COMMIT"
fi

# Locate a musl cross toolchain: either the local one or download from musl.cc.
# Sets TC_DIR and exports PATH in the calling shell (must not run in a $()).
find_toolchain() {
  local triplet="$1"
  if [ -n "$TOOLCHAIN_DIR" ]; then
    TC_DIR="$TOOLCHAIN_DIR"
  else
    TC_DIR="$PWD/toolchains/$triplet-cross"
    if [ ! -x "$TC_DIR/bin/$triplet-gcc" ]; then
      mkdir -p toolchains
      echo "==> Downloading musl cross toolchain $triplet"
      curl -sSL -o "$triplet.tgz" "$MUSL_CC_BASE/$triplet-cross.tgz"
      tar -xzf "$triplet.tgz" -C toolchains/
    fi
  fi
  export PATH="$TC_DIR/bin:$PATH"
}

# Find the sysroot (headers + libc.a + crt objects) of the toolchain.
find_sysroot() {
  local triplet="$1" tc="$2"
  local sysroot
  sysroot="$($triplet-gcc -print-sysroot 2>/dev/null || true)"
  # NB: bash gives && and || equal precedence, so group the fallback check.
  if [ -z "$sysroot" ] || { [ ! -f "$sysroot/include/stdio.h" ] && [ ! -f "$sysroot/usr/include/stdio.h" ]; }; then
    sysroot="$(dirname "$(dirname "$(find "$tc" -path '*/usr/include/stdio.h' -o -path '*/include/stdio.h' | grep -v c++ | head -1)")")"
  fi
  if [ -z "$sysroot" ] || [ ! -f "$sysroot/include/stdio.h" ] && [ ! -f "$sysroot/usr/include/stdio.h" ]; then
    echo "ERROR: could not locate the musl sysroot for $triplet" >&2
    exit 1
  fi
  # Resolve the layout (musl-cross: <sysroot>/include + <sysroot>/lib, or usr/ variants).
  if [ -d "$sysroot/usr/include" ]; then
    MUSL_INC="$sysroot/usr/include"; MUSL_LIB="$sysroot/usr/lib"
  else
    MUSL_INC="$sysroot/include"; MUSL_LIB="$sysroot/lib"
  fi
  echo "==> sysroot: $sysroot (inc=$MUSL_INC lib=$MUSL_LIB)"
  ls "$MUSL_INC/stdio.h" "$MUSL_LIB/libc.a" "$MUSL_LIB/crt1.o" >/dev/null
}

# --- one target per ABI -----------------------------------------------------
build_abi() {
  local triplet="$1"   # e.g. aarch64-linux-musl
  local cpu="$2"       # tcc --cpu value
  local cross_t="$3"   # make CROSS_TARGET value (libtcc1.a naming)
  local abi="$4"       # Android ABI folder name
  echo ""
  echo "==================================================================="
  echo "== Building TCC for $abi ($triplet, cpu=$cpu)"
  echo "==================================================================="

  find_toolchain "$triplet"
  local tc="$TC_DIR"
  find_sysroot "$triplet" "$tc"

  local srcdir="$PWD/tinycc"
  cd "$srcdir"

  # 1) Host-runnable cross tcc (generates $cpu code, runs on this machine).
  #    Used only to build the target's libtcc1.a. Built via the Makefile's
  #    native cross target so c2str.exe stays host-runnable. The native
  #    configure must stay in place for step 2 (lib/Makefile includes
  #    config.mak), so distclean runs after step 2.
  echo "==> Building host-runnable cross tcc ($cpu)"
  make distclean >/dev/null 2>&1 || true
  ./configure --cc=gcc --prefix=/tmp/tcc.host >/dev/null
  make -j"$JOBS" "$cross_t-tcc" >/dev/null
  cp "$cross_t-tcc" "/tmp/xcc-$abi"

  # 2) libtcc1.a for the target, compiled by the cross tcc.
  cp "/tmp/xcc-$abi" "$cross_t-tcc"
  touch "$cross_t-tcc"
  echo "==> Building libtcc1.a for $abi"
  make -C lib CROSS_TARGET="$cross_t" XTCC="$PWD/$cross_t-tcc" \
    "XFLAGS=-B$PWD -I$PWD/include -I$MUSL_INC" >/dev/null
  cp "$cross_t-libtcc1.a" "/tmp/libtcc1-$abi.a"
  make distclean >/dev/null 2>&1 || true

  # 3) Final tcc binary: cross configure, static musl link.
  #    tccdefs_.h is generated with a native c2str.exe because the cross
  #    compiler cannot run on this machine.
  echo "==> Building final tcc binary ($abi)"
  make distclean >/dev/null 2>&1 || true
  ./configure --cc=gcc --cross-prefix="$triplet-" --cpu="$cpu" \
    --config-musl --disable-rpath \
    --crtprefix=. --libpaths=. --sysincludepaths=. \
    --prefix=/tmp/tcc.final >/dev/null
  gcc -DC2STR conftest.c -o c2str.exe
  ./c2str.exe include/tccdefs.h tccdefs_.h
  make -j"$JOBS" tcc LDFLAGS=-static >/dev/null
  cd "$OLDPWD"

  # 4) Assemble the relocatable bundle.
  local out="$PWD/dist/tcc-$abi"
  mkdir -p "$out"
  cp "$srcdir/tcc" "$out/tcc"
  cp -r "$srcdir/include" "$out/include-tcc"
  cp -r "$MUSL_INC" "$out/include"
  # Trim kernel UAPI headers: only musl's own headers are needed. (musl's
  # bits/kd.h, bits/soundcard.h, bits/vt.h reference them, which is fine —
  # those headers are never used by typical C programs.)
  ( cd "$out/include" && rm -rf c++ linux asm asm-generic drm mtd rdma scsi sound video xen misc )
  cp "$MUSL_LIB/libc.a" "$MUSL_LIB/crt1.o" "$MUSL_LIB/crti.o" "$MUSL_LIB/crtn.o" "$out/"
  cp "/tmp/libtcc1-$abi.a" "$out/libtcc1.a"
  cp "$srcdir/COPYING" "$out/COPYING.tcc"
  chmod +x "$out/tcc"

  # 5) Sanity checks.
  echo "==> Bundle contents:"
  du -sh "$out"
  python3 - "$out/tcc" "$cpu" <<'PY'
import struct, sys
path, cpu = sys.argv[1], sys.argv[2]
d = open(path, 'rb').read(64)
e_machine = struct.unpack('<H', d[18:20])[0]
expected = {"aarch64": 183, "arm64": 183, "arm": 40, "x86_64": 62}[cpu]
assert e_machine == expected, f"unexpected e_machine {e_machine} (wanted {expected})"
e_phoff = struct.unpack('<Q', d[32:40])[0]
e_phentsize = struct.unpack('<H', d[54:56])[0]
e_phnum = struct.unpack('<H', d[56:58])[0]
with open(path, 'rb') as f:
    f.seek(e_phoff); phs = f.read(e_phentsize * e_phnum)
dyn = any(struct.unpack('<I', phs[i*e_phentsize:(i+1)*e_phentsize][:4])[0] == 2 for i in range(e_phnum))
assert not dyn, "tcc is not statically linked"
print(f"    tcc e_machine={e_machine} static OK ({cpu})")
PY
}

build_abi aarch64-linux-musl aarch64 arm64 arm64-v8a
build_abi x86_64-linux-musl x86_64 x86_64 x86_64

# --- smoke test the x86_64 bundle on the host -------------------------------
echo ""
echo "==> Smoke test (x86_64 bundle): compile + run hello world"
cd dist/tcc-x86_64
cat > smoke.c <<'EOF'
#include <stdio.h>
#include <math.h>
int main(void) {
    printf("hello from tcc %d %.0f\n", 2026, sqrt(16.0));
    return 0;
}
EOF
./tcc -static -std=c11 -Wall -Wextra -O0 -I include-tcc -I include -L . smoke.c -o smoke
out="$(./smoke)"
echo "    program output: $out"
[ "$out" = "hello from tcc 2026 4" ]
python3 - <<'PY'
import struct
d = open('smoke', 'rb').read(64)
e_phoff = struct.unpack('<Q', d[32:40])[0]
e_phentsize = struct.unpack('<H', d[54:56])[0]
e_phnum = struct.unpack('<H', d[56:58])[0]
with open('smoke', 'rb') as f:
    f.seek(e_phoff); phs = f.read(e_phentsize * e_phnum)
dyn = any(struct.unpack('<I', phs[i*e_phentsize:(i+1)*e_phentsize][:4])[0] == 2 for i in range(e_phnum))
assert not dyn, "compiled program is not static"
print("    compiled program is a static EXEC — OK")
PY
rm -f smoke.c smoke

echo ""
echo "==> Bundles ready:"
ls -la ../dist/tcc-*/
