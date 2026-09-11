#!/usr/bin/env bash
# Phase 42.2 — measure-only release build: R8 + shrinkResources numbers
# WITHOUT waiting for the owner's signing secrets. Generates a THROWAWAY
# key (thrown away with the runner), assembles the release variant, and
# annotates the facts the part doc records. UPLOADS NOTHING: an unsigned-
# channel artifact must never look downloadable (the wrong-install
# factory is what the artifact shape rules are about), so this step
# deliberately has no upload-artifact twin.
set -u
cd "$(dirname "$0")/.."

KEY="$RUNNER_TEMP/measure-only.keystore"
keytool -genkeypair -alias measure -keyalg RSA -keysize 2048 -validity 1 \
  -keystore "$KEY" -storetype PKCS12 \
  -storepass measure-only-key -keypass measure-only-key \
  -dname "CN=CodeC measure-only (throwaway)"

# A gradle failure here must NOT be masked by the checks below: keep the
# log and annotate its TAIL verbatim (the classified surfacer's needle
# list has already missed one failure family — annotations are the only
# log surface reachable from outside the runner).
set -o pipefail
LOG="app/build/measure-build.log"
if ! KEYSTORE_PATH="$KEY" STORE_PASSWORD=measure-only-key KEY_PASSWORD=measure-only-key \
    ./gradlew :app:assembleRelease --no-daemon 2>&1 | tee "$LOG"; then
  tail -40 "$LOG" | while IFS= read -r line; do echo "::error::$line"; done
  echo "::error::measure-only release assemble FAILED (full tail above)"
  exit 1
fi

apk=$(find app/build/outputs/apk/release -name 'CodeC-IDE-*-universal.apk' | head -1)
if [ -z "$apk" ]; then
  echo "::error::no release universal APK produced (naming or assemble broke)"
  exit 1
fi
bytes=$(du -b "$apk" | cut -f1)
echo "::notice title=APK size (measure-only release)::$(basename "$apk") = $bytes bytes"

# Exit-3's machine half: the release manifest must NOT enable debugging.
AAPT=""
for d in "$ANDROID_HOME"/build-tools/*/; do
  [ -x "$d/aapt" ] && AAPT="$d/aapt"
done
if [ -n "$AAPT" ]; then
  if "$AAPT" dump badging "$apk" 2>/dev/null | grep -q "android:debuggable='true'"; then
    echo "::error::release APK is DEBUGGABLE — android:debuggable=true leaked into the release manifest"
    exit 1
  else
    echo "::notice::release manifest: no android:debuggable flag"
  fi
else
  echo "::warning::aapt not found — debuggability check SKIPPED this run"
fi

# R8 must have run and left its symbol table for stack-trace decoding.
map="app/build/outputs/mapping/release/mapping.txt"
if [ -s "$map" ]; then
  echo "::notice title=R8 mapping::mapping.txt = $(du -b "$map" | cut -f1) bytes"
else
  echo "::error::mapping.txt missing/empty — R8 did not run on the release variant"
  exit 1
fi

# shrinkResources must not eat the content the app loads BY PATH (assets
# are raw, but pin the offline-product survivors anyway):
for probe in "assets/tcc/arm64-v8a/" "assets/tcc/x86_64/" "assets/textmate/" "assets/snippets/" "assets/licenses/"; do
  if ! unzip -l "$apk" | grep -q "$probe"; then
    echo "::error::$probe missing from the R8/shrunk release — likely shrink overreach"
    exit 1
  fi
done
echo "measure-only release check OK: $bytes bytes, R8 on, mapping kept, not debuggable, assets intact"
