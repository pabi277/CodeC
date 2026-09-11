#!/bin/bash
# Phase 42.1 — fills docs/RELEASE_NOTES.md (template guarded by
# check_release_notes.sh) into build/release-notes.md for the publish step:
#   scripts/build_release_notes.sh <app-vX.Y.Z> <versionCode> <apk-file...>
# The sha256: lines it emits are EXACTLY the format the in-app updater's
# UpdatePolicy.digestsFromNotes reads: "sha256: <64hex>  <asset-name>".
set -euo pipefail

TAG="${1:?usage: build_release_notes.sh <tag> <versionCode> <apk...>}"
VERSION_CODE="${2:?versionCode required}"
shift 2
[ "$#" -ge 1 ] || { echo "::error::no APK files given to build_release_notes.sh"; exit 1; }

VERSION="${TAG#app-v}"
DATE="$(date -u +%Y-%m-%d)"

SHA_LINES=""
for f in "$@"; do
  [ -f "$f" ] || { echo "::error::missing artifact $f"; exit 1; }
  sum="$(sha256sum "$f" | cut -d' ' -f1)"
  name="$(basename "$f")"
  line="sha256: $sum  $name"
  SHA_LINES="${SHA_LINES}${line}"$'\n'
  echo "checksum: $line"
done
SHA_LINES="${SHA_LINES%$'\n'}"

# Changelog: commits since the previous app-v* tag (excluding this one).
PREV="$(git tag --list 'app-v*' --sort=-v:refname | grep -v "^${TAG}\$" | head -1 || true)"
if [ -n "$PREV" ]; then
  CHANGELOG="$(git log --oneline --no-decorate "${PREV}..${TAG}" | sed 's/^/- /')"
  [ -n "$CHANGELOG" ] || CHANGELOG="- (no commits since $PREV)"
else
  CHANGELOG="- First app release on the app-v* channel (see docs/JOURNEY.md for the full story)."
fi

python3 - "$VERSION" "$VERSION_CODE" "$DATE" "$SHA_LINES" "$CHANGELOG" <<'PY'
import pathlib, sys

version, version_code, date, sha_lines, changelog = sys.argv[1:6]
template = pathlib.Path("docs/RELEASE_NOTES.md").read_text()
out = (template
       .replace("{{VERSION}}", version)
       .replace("{{VERSION_CODE}}", version_code)
       .replace("{{DATE}}", date)
       .replace("{{SHA256_LINES}}", sha_lines)
       .replace("{{CHANGELOG}}", changelog))
# The template's own HTML comment block is guidance for editors; a shipped
# notes page does not carry it.
start = out.find("<!--")
end = out.find("-->")
if 0 <= start < end:
    out = out[:start] + out[end + 3:]
while "\n\n\n" in out:
    out = out.replace("\n\n\n", "\n\n")
pathlib.Path("build").mkdir(exist_ok=True)
pathlib.Path("build/release-notes.md").write_text(out.strip() + "\n")
print("build/release-notes.md written")
PY
