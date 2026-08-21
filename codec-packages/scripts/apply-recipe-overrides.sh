#!/usr/bin/env bash
# Apply narrowly scoped CodeC build-environment overrides to official recipes.
# These are source transport fixes only: package versions, patches, and hashes
# remain those from the pinned official termux-packages revision.
set -euo pipefail

TREE="${1:?usage: apply-recipe-overrides.sh /path/to/termux-packages}"

# Savannah's HTTP/primary HTTPS endpoint intermittently returns HTTP 502 from
# GitHub Actions. Its official download mirror serves the same source archives.
# Keep this list explicit: do not rewrite unrelated third-party source URLs.
for recipe in attr libacl; do
  path="$TREE/packages/$recipe/build.sh"
  if [[ ! -f "$path" ]]; then
    echo "recipe-overrides: $recipe recipe not found; skipping"
    continue
  fi
  sed -i \
    's#https\?://download\.savannah\.gnu\.org/releases/\(attr\|acl\)/#https://download-mirror.savannah.gnu.org/releases/\1/#' \
    "$path"
  if grep -qE 'https?://download\.savannah\.gnu\.org/releases/(attr|acl)/' "$path"; then
    echo "recipe-overrides: failed to update $recipe source URL" >&2
    exit 1
  fi
  echo "recipe-overrides: $recipe uses official HTTPS Savannah mirror"
done
