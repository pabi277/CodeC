#!/usr/bin/env bash
# Extract built .debs into a prefix tree and tar.gz it (root = PREFIX contents).
set -euo pipefail

SRC="${1:?termux-packages tree}"
ARCH="${2:?arch}"
DIST="${3:?dist dir}"
STAGE="$(mktemp -d)"
trap 'rm -rf "$STAGE"' EXIT

DEB_DIR="$SRC/output"
if [[ ! -d "$DEB_DIR" ]]; then
  DEB_DIR="$SRC/debs"
fi
if [[ ! -d "$DEB_DIR" ]]; then
  echo "assemble: no output/debs dir under $SRC" >&2
  find "$SRC" -name '*.deb' | head
  exit 1
fi

mapfile -t DEBS < <(find "$DEB_DIR" -name "*_${ARCH}.deb" -o -name "*_${ARCH}-*.deb" | sort)
if [[ ${#DEBS[@]} -eq 0 ]]; then
  mapfile -t DEBS < <(find "$SRC" -name '*.deb' | sort)
fi
if [[ ${#DEBS[@]} -eq 0 ]]; then
  echo "assemble: no .deb files found" >&2
  exit 1
fi

PREFIX_STAGE="$STAGE/usr"
mkdir -p "$PREFIX_STAGE"
for deb in "${DEBS[@]}"; do
  echo "extract $deb"
  dpkg-deb -x "$deb" "$PREFIX_STAGE"
done

# Tarball root is the contents of usr/ so the app extracts into $PREFIX.
OUT="$DIST/bootstrap-${ARCH}.tar.gz"
mkdir -p "$DIST"
tar -C "$PREFIX_STAGE" -czf "$OUT" .
sha256sum "$OUT" | awk '{print $1}' > "${OUT}.sha256"
echo "wrote $OUT"
cat "${OUT}.sha256"
