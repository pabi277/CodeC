#!/bin/bash
# Phase 42.1 — release-notes guard (bash + grep only, no tooling): the
# template at docs/RELEASE_NOTES.md must keep every section a stranger
# needs; a notes page that silently shrinks to "bug fixes" fails the build.
# Also pins the contract the in-app updater depends on: the template keeps
# its {{SHA256_LINES}} placeholder (publish fills it with "sha256:" lines —
# no checksum line, no auto-install) and its versionCode metadata line.
set -euo pipefail

NOTES="docs/RELEASE_NOTES.md"
fail() { echo "::error::$1"; exit 1; }

[ -f "$NOTES" ] || fail "$NOTES is missing (the publish step fills THIS file)"

for heading in \
  "## What's new" \
  "## Which APK should I install?" \
  "## Upgrading / downgrading" \
  "## Checksums" \
  "## What still bites (known issues)" \
  "## Feedback" \
  "## Privacy"; do
  grep -qF "$heading" "$NOTES" || fail "release notes lost the '$heading' section"
done

grep -q '{{SHA256_LINES}}' "$NOTES" || \
  fail "release notes lost the {{SHA256_LINES}} placeholder — the updater would have nothing to verify"
grep -q '^versionCode: {{VERSION_CODE}}$' "$NOTES" || \
  fail "release notes lost the 'versionCode: {{VERSION_CODE}}' metadata line (the publish guard parses it)"
grep -q '{{VERSION}}' "$NOTES" || fail "release notes lost the {{VERSION}} placeholder"
grep -q '{{CHANGELOG}}' "$NOTES" || fail "release notes lost the {{CHANGELOG}} placeholder"

echo "release notes template OK ($(wc -l < "$NOTES") lines)"
