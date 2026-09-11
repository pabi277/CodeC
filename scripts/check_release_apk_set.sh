#!/usr/bin/env bash
# Phase 42.2 — validates the release artifact SET right after
# :app:assembleRelease. Repo/grep tests check the CONFIG; this checks the
# executables of record (the APK zips themselves):
#   1. exactly one universal APK, named per the update-channel grammar;
#   2. exactly one APK per abiFilter ABI;
#   3. each per-ABI split contains native libs for its own ABI only;
#   4. assets/tcc coverage: splits carry ALL tcc dirs (the known trap —
#      splits do not filter assets) — recorded, not failed, until the
#      owner picks a mechanism (PART_42_2 decision card).
set -u
cd "$(dirname "$0")/.."

ROOT="app/build/outputs/apk"
FAIL=0

mapfile -t RELEASE_APKS < <(find "$ROOT" -path '*/release/*.apk' | sort)
if [ "${#RELEASE_APKS[@]}" -eq 0 ]; then
  echo "::error::no release APKs found under $ROOT"
  exit 1
fi

GRADLE=$(grep -o 'abiFilters += listOf([^)]*)' app/build.gradle.kts)
EXPECTED=$(echo "$GRADLE" | grep -o '"[^"]*"' | tr -d '"')

UNIVERSAL_COUNT=0
for apk in "${RELEASE_APKS[@]}"; do
  base=$(basename "$apk")
  case "$base" in
    CodeC-IDE-*-universal.apk) UNIVERSAL_COUNT=$((UNIVERSAL_COUNT+1)) ;;
  esac
  if [[ "$base" != CodeC-IDE-*.apk ]]; then
    echo "::error::$base does not follow the update-channel name grammar (UpdatePolicy)"
    FAIL=1
  fi
done
if [ "$UNIVERSAL_COUNT" -ne 1 ]; then
  echo "::error::expected exactly 1 universal release APK, found $UNIVERSAL_COUNT"
  FAIL=1
fi

for abi in $EXPECTED; do
  hits=$(printf '%s\n' "${RELEASE_APKS[@]}" | grep -c -- "-$abi\.apk$" || true)
  if [ "$hits" -ne 1 ]; then
    echo "::error::expected exactly 1 split APK for $abi (-$abi.apk), found $hits"
    FAIL=1
    continue
  fi
  apk=$(printf '%s\n' "${RELEASE_APKS[@]}" | grep -- "-$abi\.apk$" | head -1)
  bad=$(unzip -l "$apk" | awk '{print $4}' | grep '^lib/' | cut -d/ -f2 | sort -u | grep -vx "$abi" || true)
  if [ -n "$bad" ]; then
    echo "::error::$(basename "$apk") contains native libs for: $bad (splits must carry one ABI)"
    FAIL=1
  fi
  tccs=$(unzip -l "$apk" | awk '{print $4}' | grep '^assets/tcc/' | cut -d/ -f3 | sort -u | tr '\n' ' ')
  echo "::notice::$(basename "$apk") assets/tcc coverage: ${tccs:-none}" \
    "(known trap until the owner picks an exclude mechanism — PART_42_2)"
done

if [ "$FAIL" -ne 0 ]; then
  echo "::error::release APK set check FAILED — see annotations above"
  exit 1
fi
echo "release APK set check passed: ${#RELEASE_APKS[@]} artifacts (1 universal + per-ABI splits)"
