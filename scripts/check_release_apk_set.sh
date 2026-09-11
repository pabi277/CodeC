#!/usr/bin/env bash
# Phase 42.2 — validates THE release artifact (universal-only since the
# 2026-09-11 revert, see PART_42_2 — splits measured 0.94-1.77 % under the
# assets/tcc trap, below the 15 % floor that would have kept them).
# Check the executable of record (the APK zip itself):
#   1. exactly one release APK, named per the update-channel grammar
#      (…-universal.apk — the updater's default);
#   2. it carries native libs for ALL FOUR natural ABIs (the universal
#      promise: anyone can install it);
#   3. the bundled offline compiler material survives packaging for the
#      two tcc ABIs (assets/tcc/<abi> is NOT filtered — recorded design).
set -u
cd "$(dirname "$0")/.."

ROOT="app/build/outputs/apk/release"
FAIL=0

mapfile -t APK < <(find "$ROOT" -name '*.apk' | sort)
if [ "${#APK[@]}" -ne 1 ]; then
  echo "::error::expected exactly 1 release APK (universal-only per the 42.2 revert), found ${#APK[@]}: ${APK[*]:-none}"
  exit 1
fi
apk="${APK[0]}"
base=$(basename "$apk")

if [[ "$base" != CodeC-IDE-*-universal.apk ]]; then
  echo "::error::$base does not match the update-channel grammar CodeC-IDE-<version>-universal.apk (UpdatePolicy)"
  FAIL=1
fi

for abi in arm64-v8a armeabi-v7a x86_64 x86; do
  count=$(unzip -l "$apk" | grep -c "lib/$abi/" || true)
  if [ "$count" -eq 0 ]; then
    echo "::error::$base carries NO native libs for $abi — the universal promise is broken"
    FAIL=1
  fi
done

for d in arm64-v8a x86_64; do
  count=$(unzip -l "$apk" | grep -c "assets/tcc/$d/" || true)
  if [ "$count" -eq 0 ]; then
    echo "::error::assets/tcc/$d missing from release (packaging/shrink overreach) — offline C compiler is gone"
    FAIL=1
  fi
done

if [ "$FAIL" -ne 0 ]; then
  echo "::error::release APK check FAILED — see annotations above"
  exit 1
fi
echo "release APK check passed: $base (universal, 4 ABI native sets, tcc assets intact)"
