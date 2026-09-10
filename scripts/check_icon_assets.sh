#!/usr/bin/env bash
#
# Phase 38.1 — guard the launcher icon asset set. Runs in CI right after
# checkout (bash only, no tooling) and doubles as a local check:
#
#   1. every mipmap-{mdpi..xxxhdpi} folder has BOTH ic_launcher.png and
#      ic_launcher_round.png (the half-deleted state is a crash on some
#      device, not a build failure — lint only covers part of it);
#   2. no .webp launcher rasters remain (a stale ic_launcher.webp next to
#      a new ic_launcher.png IS a duplicate-resource build failure);
#   3. the raster sizes match the density table (48/72/96/144/192);
#   4. the adaptive XMLs in mipmap-anydpi-v26 wire background+foreground
#      +monochrome.
#
# The geometry/safe-zone rules live in IconAssetSetTest/IconGeometryTest
# (they run with the unit tests); this script is the fast repo-shape
# check that does not need a JVM.
set -euo pipefail

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
RES="$HERE/../app/src/main/res"
fail=0

declare -A SIZE=( [mdpi]=48 [hdpi]=72 [xhdpi]=96 [xxhdpi]=144 [xxxhdpi]=192 )

for density in mdpi hdpi xhdpi xxhdpi xxxhdpi; do
  dir="$RES/mipmap-$density"
  for name in ic_launcher ic_launcher_round; do
    f="$dir/$name.png"
    if [[ ! -f "$f" ]]; then
      echo "MISSING: $f"
      fail=1
      continue
    fi
    # PNG dimensions live in the IHDR header (bytes 16..23, big-endian).
    actual="$(python3 -c "
import struct, sys
with open('$f', 'rb') as fh:
    fh.seek(16)
    w, h = struct.unpack('>II', fh.read(8))
    print(f'{w}x{h}')
")"
    expected="${SIZE[$density]}x${SIZE[$density]}"
    if [[ "$actual" != "$expected" ]]; then
      echo "WRONG SIZE: $f is $actual, expected $expected"
      fail=1
    fi
  done
  leftovers="$(find "$dir" -name '*.webp' -print -quit)"
  if [[ -n "$leftovers" ]]; then
    echo "STALE WEBP: $leftovers (delete it — duplicate resource)"
    fail=1
  fi
done

for xml in "$RES"/mipmap-anydpi-v26/ic_launcher.xml "$RES"/mipmap-anydpi-v26/ic_launcher_round.xml; do
  if [[ ! -f "$xml" ]]; then
    echo "MISSING: $xml"
    fail=1
    continue
  fi
  for layer in background foreground monochrome; do
    if ! grep -q "android:drawable" "$xml" || ! grep -q "$layer" "$xml"; then
      echo "BAD ADAPTIVE XML: $xml has no <$layer> layer"
      fail=1
    fi
  done
done

if [[ "$fail" -ne 0 ]]; then
  echo "icon asset check: FAILED"
  exit 1
fi
echo "icon asset check: OK (10 rasters + 2 adaptive XMLs)"
