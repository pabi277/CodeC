#!/usr/bin/env bash
# Apply CodeC TERMUX_PREFIX / package-id onto a cloned termux-packages tree.
set -eo pipefail

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

text = re.sub(r'TERMUX_APP_PACKAGE=["\'][^"\']*["\']', f'TERMUX_APP_PACKAGE="{pkg}"', text)
text = re.sub(r'TERMUX_APP__PACKAGE_NAME=["\'][^"\']*["\']', f'TERMUX_APP__PACKAGE_NAME="{pkg}"', text)
text = text.replace("com.termux", pkg)
text = text.replace("/data/data/com.termux/files/usr", f"/data/data/{pkg}/files/usr")

path.write_text(text)
print(f"apply-prefix: patched {path} for package {pkg}")
PY

sed -i "1s|^|TERMUX_PREFIX=\"/data/data/${TERMUX_APP_PACKAGE}/files/usr\"\nTERMUX_APP_PACKAGE=\"${TERMUX_APP_PACKAGE}\"\nTERMUX_APP__PACKAGE_NAME=\"${TERMUX_APP_PACKAGE}\"\n|" "$PROPS"

EVAL_PREFIX=$(bash -c "source '$PROPS' >/dev/null 2>&1 || true; echo \"\$TERMUX_PREFIX\"")
echo "apply-prefix: TERMUX_PREFIX=${EVAL_PREFIX}"

if [[ "$EVAL_PREFIX" != "/data/data/com.codeci.ide/files/usr" ]]; then
  echo "apply-prefix: PREFIX mismatch — refusing to build Termux debs" >&2
  exit 1
fi
