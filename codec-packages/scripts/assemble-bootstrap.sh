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

mapfile -t DEBS < <(
  find "$DEB_DIR" \( \
    -name "*_${ARCH}.deb" -o \
    -name "*_${ARCH}-*.deb" -o \
    -name "*_all.deb" \
  \) -type f | sort
)
if [[ ${#DEBS[@]} -eq 0 ]]; then
  mapfile -t DEBS < <(find "$SRC" -name '*.deb' | sort)
fi
if [[ ${#DEBS[@]} -eq 0 ]]; then
  echo "assemble: no .deb files found" >&2
  exit 1
fi

# Termux packages contain their complete Android prefix path:
# data/data/com.codeci.ide/files/usr/...
# Extract into a neutral root, then archive only the contents of $PREFIX.
EXTRACT_ROOT="$STAGE/root"
mkdir -p "$EXTRACT_ROOT"

for deb in "${DEBS[@]}"; do
  echo "extract $deb"
  dpkg-deb -x "$deb" "$EXTRACT_ROOT"
done

PREFIX_STAGE="$EXTRACT_ROOT/data/data/com.codeci.ide/files/usr"

if [[ ! -x "$PREFIX_STAGE/bin/bash" ]]; then
  echo "assemble: missing executable $PREFIX_STAGE/bin/bash" >&2
  exit 1
fi

if [[ ! -x "$PREFIX_STAGE/bin/busybox" ]]; then
  echo "assemble: missing executable $PREFIX_STAGE/bin/busybox" >&2
  exit 1
fi

# The app extracts this archive directly into its $PREFIX. Therefore the
# archive root must contain bin/, lib/, etc/, not data/data/.../files/usr/.
BOOTSTRAP_NAME="${CODEC_BOOTSTRAP_NAME:-bootstrap}"

# A Phase 3 bootstrap needs a real dpkg database for packages that were seeded
# before the first interactive apt transaction. Phase 2 keeps its original
# minimal archive behavior and does not claim apt/dpkg support.
if [[ "$BOOTSTRAP_NAME" == "bootstrap-phase3" ]]; then
  DPKG_STATE="$PREFIX_STAGE/var/lib/dpkg"
  mkdir -p "$DPKG_STATE/info"
  : > "$DPKG_STATE/status"
  printf '%s\n' "$ARCH" > "$DPKG_STATE/arch"
  for deb in "${DEBS[@]}"; do
    package_name="$(dpkg-deb -f "$deb" Package)"
    dpkg-deb -f "$deb" >> "$DPKG_STATE/status"
    printf 'Status: install ok installed\n\n' >> "$DPKG_STATE/status"
    dpkg-deb --contents "$deb" \
      | sed -E 's#.* (\./[^ ]+)( -> .*)?$#\1#; s#^\./#/#' \
      > "$DPKG_STATE/info/${package_name}.list"
  done
fi

OUT="$DIST/${BOOTSTRAP_NAME}-${ARCH}.tar.gz"
mkdir -p "$DIST"
tar -C "$PREFIX_STAGE" -czf "$OUT" .

# List once so grep -q cannot cause tar to receive SIGPIPE under pipefail.
ARCHIVE_CONTENTS="$STAGE/archive-contents.txt"
tar -tzf "$OUT" > "$ARCHIVE_CONTENTS"

# Refuse archives that would create a nested Android data path under $PREFIX.
if grep -qE '^\./?data/data/' "$ARCHIVE_CONTENTS"; then
  echo "assemble: invalid nested data/data archive layout" >&2
  exit 1
fi

if ! grep -qE '^\./?bin/bash$' "$ARCHIVE_CONTENTS"; then
  echo "assemble: archive does not contain bin/bash at its root" >&2
  exit 1
fi

if ! grep -qE '^\./?bin/busybox$' "$ARCHIVE_CONTENTS"; then
  echo "assemble: archive does not contain bin/busybox at its root" >&2
  exit 1
fi

sha256sum "$OUT" | awk '{print $1}' > "${OUT}.sha256"
echo "wrote $OUT"
cat "${OUT}.sha256"
