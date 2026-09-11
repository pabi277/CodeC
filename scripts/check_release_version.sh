#!/bin/bash
# Phase 42.1 — the publish-run version guard (bash + python3 + gh, CI only).
# Usage: scripts/check_release_version.sh app-v<X.Y.Z>
#
# Two laws, both cheaper than the fallout of shipping a bad release:
#  1. the tag's <X.Y.Z> must equal app/build.gradle.kts's versionName
#     (numeric part) — a tag naming a different version than the binary is
#     how "which build is this" stops being answerable;
#  2. versionCode must be STRICTLY GREATER than every versionCode recorded
#     in an existing app-v* release's notes (the "we shipped a release
#     nobody could update over" mistake is a versioning mistake): Android
#     refuses versionCode-downgrades, so that release would be dead weight.
#
# The previous versionCodes are read from the `versionCode: N` metadata
# line the publish step embeds in each release's notes (see
# docs/RELEASE_NOTES.md) via the releases API. No prior app-v* release →
# any positive versionCode passes.
set -euo pipefail

TAG="${1:?usage: check_release_version.sh app-v<X.Y.Z>}"
GRADLE_FILE="app/build.gradle.kts"

case "$TAG" in
  app-v*.*) ;;
  *) echo "::error::tag '$TAG' is not an app release tag (expected app-v<X.Y.Z>)"; exit 1 ;;
esac

TAG_VERSION="${TAG#app-v}"
[[ "$TAG_VERSION" =~ ^[0-9]+(\.[0-9]+){0,2}$ ]] || {
  echo "::error::tag '$TAG': version part '$TAG_VERSION' is not <X.Y.Z>"; exit 1; }

VERSION_NAME="$(python3 - "$GRADLE_FILE" <<'PY'
import pathlib, re, sys
text = pathlib.Path(sys.argv[1]).read_text()
m = re.search(r'versionName\s*=\s*"([0-9]+(?:\.[0-9]+){0,2})"', text)
if not m:
    print("MISSING")
else:
    print(m.group(1))
PY
)"
[ "$VERSION_NAME" = "MISSING" ] && { echo "::error::cannot read versionName from $GRADLE_FILE"; exit 1; }
[ "$VERSION_NAME" = "$TAG_VERSION" ] || {
  echo "::error::tag '$TAG' names $TAG_VERSION but $GRADLE_FILE has versionName $VERSION_NAME — align them before tagging"; exit 1; }

MY_CODE="$(python3 - "$GRADLE_FILE" <<'PY'
import pathlib, re, sys
text = pathlib.Path(sys.argv[1]).read_text()
m = re.search(r'versionCode\s*=\s*(\d+)', text)
if not m:
    print("0")
else:
    print(m.group(1))
PY
)"
[ "$MY_CODE" != "0" ] || { echo "::error::cannot read versionCode from $GRADLE_FILE"; exit 1; }

MAX_CODE="$(python3 - <<'PY'
import json, os, re, subprocess
out = subprocess.run(
    ["gh", "api", "repos/{owner}/{repo}/releases?per_page=100"],
    capture_output=True, text=True, env={**os.environ}
)
if out.returncode != 0:
    # Unreadable release list must not silently pass — but a brand-new
    # repo with zero releases answers 200/[], so an error means API trouble.
    print("ERROR")
else:
    best = 0
    try:
        releases = json.loads(out.stdout)
    except json.JSONDecodeError:
        print("ERROR")
        raise SystemExit
    for r in releases:
        tag = r.get("tag_name") or ""
        if not tag.startswith("app-v"):
            continue
        m = re.search(r"^versionCode:\s*(\d+)\s*$", r.get("body") or "", re.M)
        if m:
            best = max(best, int(m.group(1)))
    print(best)
PY
)"
[ "$MAX_CODE" = "ERROR" ] && { echo "::error::could not read the releases list to check versionCode ancestry; refusing to publish blind"; exit 1; }

if [ "$MAX_CODE" -ge "$MY_CODE" ]; then
  echo "::error::versionCode $MY_CODE is not greater than the already-released $MAX_CODE — bump it in $GRADLE_FILE or this release cannot update any previous one"
  exit 1
fi

echo "version guard OK: tag $TAG == versionName $TAG_VERSION; versionCode $MY_CODE > previous max $MAX_CODE"
echo "version=$TAG_VERSION" >> "${GITHUB_OUTPUT:-/dev/null}"
echo "version_code=$MY_CODE" >> "${GITHUB_OUTPUT:-/dev/null}"
