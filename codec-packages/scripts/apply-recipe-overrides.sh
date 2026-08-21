#!/usr/bin/env bash
# Apply narrowly scoped CodeC build-environment overrides to official recipes.
# These are source transport fixes only: package versions, patches, and hashes
# remain those from the pinned official termux-packages revision.
set -euo pipefail

TREE="${1:?usage: apply-recipe-overrides.sh /path/to/termux-packages}"
ATTR_RECIPE="$TREE/packages/attr/build.sh"

if [[ ! -f "$ATTR_RECIPE" ]]; then
  echo "recipe-overrides: attr recipe not found; nothing to override"
  exit 0
fi

# The official attr recipe currently uses Savannah's HTTP endpoint. GitHub
# Actions intermittently receives HTTP 502 from that endpoint. Use Savannah's
# official HTTPS download mirror without changing the source archive or hash.
sed -i \
  's#http://download\.savannah\.gnu\.org/releases/attr/#https://download-mirror.savannah.gnu.org/releases/attr/#' \
  "$ATTR_RECIPE"

if grep -q 'http://download.savannah.gnu.org/releases/attr/' "$ATTR_RECIPE"; then
  echo "recipe-overrides: failed to update attr source URL" >&2
  exit 1
fi

echo "recipe-overrides: attr uses official HTTPS Savannah mirror"
