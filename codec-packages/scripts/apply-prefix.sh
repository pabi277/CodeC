#!/usr/bin/env bash
# Apply CodeC TERMUX_PREFIX / package-id onto a cloned termux-packages tree.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
TREE="${1:?usage: apply-prefix.sh /path/to/termux-packages}"
PROPS="$TREE/scripts/properties.sh"
OVERLAY="$ROOT/properties.codec.sh"

# shellcheck disable=SC1090
source "$OVERLAY"

if [[ ! -f "$PROPS" ]]; then
  echo "apply-prefix: missing $PROPS" >&2
  exit 1
fi

python3 - "$PROPS" "$TERMUX_APP_PACKAGE" <<'PY'
import pathlib, re, sys
path = pathlib.Path(sys.argv[1])
pkg = sys.argv[2]
text = path.read_text()
text = re.sub(
    r'TERMUX_APP_PACKAGE="[^"]*"',
    f'TERMUX_APP_PACKAGE="{pkg}"',
    text,
    count=1,
)
path.write_text(text)
print(f"apply-prefix: TERMUX_APP_PACKAGE={pkg} in {path}")
PY

# Confirm expansion.
# shellcheck disable=SC1090
source "$PROPS"
echo "apply-prefix: TERMUX_PREFIX=${TERMUX_PREFIX}"
if [[ "$TERMUX_PREFIX" != "/data/data/com.codeci.ide/files/usr" ]]; then
  echo "apply-prefix: PREFIX mismatch — refusing to build Termux debs" >&2
  exit 1
fi
