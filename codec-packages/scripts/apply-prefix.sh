#!/usr/bin/env bash
# Patch termux-packages so binaries are compiled for CodeC, not Termux.
# Do not source properties.sh (it is huge, uses `exit`, and `set -u` will abort).
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
TREE="${1:?usage: apply-prefix.sh /path/to/termux-packages}"
PROPS="$TREE/scripts/properties.sh"
# shellcheck disable=SC1091
source "$ROOT/properties.codec.sh"

if [[ ! -f "$PROPS" ]]; then
  echo "apply-prefix: missing $PROPS" >&2
  exit 1
fi

python3 - "$PROPS" "$TERMUX_APP_PACKAGE" <<'PY'
import pathlib, re, sys
path = pathlib.Path(sys.argv[1])
pkg = sys.argv[2]
text = path.read_text()
# Current termux-packages: TERMUX_APP__PACKAGE_NAME="com.termux"
n = 0
text, c = re.subn(
    r'(TERMUX_APP__PACKAGE_NAME=)["\'][^"\']*["\']',
    rf'\1"{pkg}"',
    text,
    count=1,
)
n += c
text, c = re.subn(
    r'(TERMUX_APP_PACKAGE=)["\'][^"\']*["\']',
    rf'\1"{pkg}"',
    text,
    count=1,
)
n += c
if n == 0:
    sys.exit("apply-prefix: no TERMUX_APP__PACKAGE_NAME / TERMUX_APP_PACKAGE assignment found")
path.write_text(text)
print(f"apply-prefix: wrote {pkg} ({n} assignment(s)) in {path}")
PY

if ! grep -q "TERMUX_APP__PACKAGE_NAME=\"${TERMUX_APP_PACKAGE}\"" "$PROPS" \
  && ! grep -q "TERMUX_APP_PACKAGE=\"${TERMUX_APP_PACKAGE}\"" "$PROPS"; then
  echo "apply-prefix: package id not present after patch" >&2
  exit 1
fi
if grep -E 'TERMUX_APP__PACKAGE_NAME="com.termux"' "$PROPS" >/dev/null; then
  echo "apply-prefix: still com.termux — refusing to build" >&2
  exit 1
fi
echo "apply-prefix: ok (TERMUX_PREFIX will expand to /data/data/${TERMUX_APP_PACKAGE}/files/usr)"
