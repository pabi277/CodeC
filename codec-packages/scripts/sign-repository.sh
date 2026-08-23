#!/usr/bin/env bash
# Sign a generated CodeC APT Release file with one explicitly selected key.
#
# The private key is never read from the repository. The caller must import a
# dedicated signing subkey into GNUPGHOME and pass that subkey's full
# fingerprint. CI supplies both through owner-controlled GitHub configuration.
set -euo pipefail

REPOSITORY="${1:?usage: sign-repository.sh <repository> <signing-subkey-fingerprint>}"
FINGERPRINT="${2:?usage: sign-repository.sh <repository> <signing-subkey-fingerprint>}"
GPG_BIN="${GPG_BIN:-gpg}"

case "$FINGERPRINT" in
  *[!0-9A-Fa-f]*|'')
    echo "sign-repository: fingerprint must be hexadecimal" >&2
    exit 2
    ;;
esac
if [[ ${#FINGERPRINT} -ne 40 ]]; then
  echo "sign-repository: expected a full 40-character OpenPGP fingerprint" >&2
  exit 2
fi
FINGERPRINT="${FINGERPRINT^^}"

[[ -d "$REPOSITORY" ]] || {
  echo "sign-repository: repository does not exist: $REPOSITORY" >&2
  exit 2
}
MANIFEST="$REPOSITORY/repository.json"
[[ -f "$MANIFEST" ]] || {
  echo "sign-repository: missing repository manifest: $MANIFEST" >&2
  exit 2
}
SUITE="$(python3 - "$MANIFEST" <<'PY'
import json
import pathlib
import sys
value = json.loads(pathlib.Path(sys.argv[1]).read_text()).get("suite")
if not isinstance(value, str) or not value or "/" in value or value in {".", ".."}:
    raise SystemExit("invalid repository suite")
print(value)
PY
)"
RELEASE="$REPOSITORY/dists/$SUITE/Release"
[[ -s "$RELEASE" ]] || {
  echo "sign-repository: missing Release file: $RELEASE" >&2
  exit 2
}
command -v "$GPG_BIN" >/dev/null 2>&1 || {
  echo "sign-repository: gpg is required" >&2
  exit 2
}

# A trailing ! forces GnuPG to use this exact signing subkey rather than
# silently selecting another secret key. Remove old outputs first so failure
# can never leave a stale signature beside a new Release file.
rm -f "$RELEASE.gpg" "${RELEASE%/Release}/InRelease"
"$GPG_BIN" --batch --yes --local-user "$FINGERPRINT!" --digest-algo SHA256 \
  --output "$RELEASE.gpg" --detach-sign "$RELEASE"
"$GPG_BIN" --batch --yes --local-user "$FINGERPRINT!" --digest-algo SHA256 \
  --output "${RELEASE%/Release}/InRelease" --clearsign "$RELEASE"

[[ -s "$RELEASE.gpg" && -s "${RELEASE%/Release}/InRelease" ]] || {
  echo "sign-repository: GnuPG did not produce both signatures" >&2
  exit 1
}
echo "signed CodeC repository suite=$SUITE key=$FINGERPRINT"
