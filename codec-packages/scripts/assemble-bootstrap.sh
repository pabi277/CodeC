#!/usr/bin/env bash
# Extract built .debs into a prefix tree and tar.gz it (root = PREFIX contents).
set -euo pipefail

SRC="${1:?termux-packages tree}"
ARCH="${2:?arch}"
DIST="${3:?dist dir}"
STAGE="$(mktemp -d)"
trap 'rm -rf "$STAGE"' EXIT
SCRIPT_DIR="$(cd "$(dirname "$0")" >/dev/null && pwd)"

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

BOOTSTRAP_NAME="${CODEC_BOOTSTRAP_NAME:-bootstrap}"

# Part B (docs/NEXT_STEPS.md): for the Phase 3 package-manager bootstrap,
# extract and seed ONLY the transitive Depends closure of the explicit seed
# set. The first Phase 3 bootstrap extracted every built .deb — including
# build-only tools (doxygen, swig, tcl, tor, …) — bloating the archive
# (~174 MB) and recording build tools in the dpkg status DB. The Phase 2
# archive behavior stays byte-stable (v1 acceptance must not regress).
FILTERED_DEBS=("${DEBS[@]}")
SEEDED_PACKAGE_NAMES=()
if [[ "$BOOTSTRAP_NAME" == "bootstrap-phase3" ]]; then
  SEED_PACKAGES="${CODEC_BOOTSTRAP_SEED_PACKAGES:-busybox bash apt dpkg coreutils less curl}"
  echo "assemble: Phase 3 seed set: $SEED_PACKAGES"
  CLOSURE_FILE="$STAGE/closure.txt"
  if ! python3 "$SCRIPT_DIR/plan-bootstrap.py" closure "$SEED_PACKAGES" \
      "${DEBS[@]}" > "$CLOSURE_FILE"; then
    echo "assemble: closure computation failed (see plan-bootstrap output above)" >&2
    exit 1
  fi
  FILTERED_DEBS=()
  SEEDED_PACKAGE_NAMES=()
  while IFS=$'\t' read -r package_name deb_path; do
    [[ -n "$package_name" && -n "$deb_path" ]] || continue
    SEEDED_PACKAGE_NAMES+=("$package_name")
    FILTERED_DEBS+=("$deb_path")
  done < "$CLOSURE_FILE"
  if [[ ${#FILTERED_DEBS[@]} -eq 0 ]]; then
    echo "assemble: closure is empty — refusing to build an empty bootstrap" >&2
    exit 1
  fi
  echo "assemble: closure seeds ${#FILTERED_DEBS[@]} of ${#DEBS[@]} built package(s): ${SEEDED_PACKAGE_NAMES[*]}"
fi

# Termux packages contain their complete Android prefix path:
# data/data/com.codeci.ide/files/usr/...
# Extract into a neutral root, then archive only the contents of $PREFIX.
EXTRACT_ROOT="$STAGE/root"
mkdir -p "$EXTRACT_ROOT"

for deb in "${FILTERED_DEBS[@]}"; do
  echo "extract $deb"
  dpkg-deb -x "$deb" "$EXTRACT_ROOT"
done

PREFIX_STAGE="$EXTRACT_ROOT/data/data/com.codeci.ide/files/usr"
PREFIX_ABS="/data/data/com.codeci.ide/files/usr"

if [[ ! -x "$PREFIX_STAGE/bin/bash" ]]; then
  echo "assemble: missing executable $PREFIX_STAGE/bin/bash" >&2
  exit 1
fi

if [[ ! -x "$PREFIX_STAGE/bin/busybox" ]]; then
  echo "assemble: missing executable $PREFIX_STAGE/bin/busybox" >&2
  exit 1
fi

# A Phase 3 bootstrap needs a real dpkg database for packages that were seeded
# before the first interactive apt transaction. Phase 2 keeps its original
# minimal archive behavior and does not claim apt/dpkg support.
if [[ "$BOOTSTRAP_NAME" == "bootstrap-phase3" ]]; then
  # Part D trust anchor. This is the public key only; the offline primary and
  # CI signing subkey never enter package/build artifacts.
  CODEC_KEYRING_SOURCE="$SCRIPT_DIR/../keys/codec-archive-keyring-v1.gpg"
  if [[ ! -s "$CODEC_KEYRING_SOURCE" ]]; then
    echo "assemble: CodeC repository public keyring is missing or empty" >&2
    exit 1
  fi
  install -D -m 0644 "$CODEC_KEYRING_SOURCE" \
    "$PREFIX_STAGE/etc/apt/keyrings/codec-archive-keyring-v1.gpg"

  DPKG_STATE="$PREFIX_STAGE/var/lib/dpkg"
  mkdir -p "$DPKG_STATE/info"
  : > "$DPKG_STATE/status"
  printf '%s\n' "$ARCH" > "$DPKG_STATE/arch"
  for deb in "${FILTERED_DEBS[@]}"; do
    package_name="$(dpkg-deb -f "$deb" Package)"
    dpkg-deb -f "$deb" >> "$DPKG_STATE/status"
    printf 'Status: install ok installed\n\n' >> "$DPKG_STATE/status"
    dpkg-deb --contents "$deb" \
      | sed -E 's#.* (\./[^ ]+)( -> .*)?$#\1#; s#^\./#/#' \
      > "$DPKG_STATE/info/${package_name}.list"
    # md5sums in the pinned-upstream generate-bootstraps.sh format
    # (package-root-relative "data/data/..." paths, regular files only).
    # Without it every seeded package failed `dpkg --audit` (Part B defect 3).
    md5_scratch="$STAGE/md5-$package_name"
    mkdir -p "$md5_scratch"
    dpkg-deb --fsys-tarfile "$deb" | tar -x -C "$md5_scratch"
    ( cd "$md5_scratch" && find data -type f -print0 | xargs -0 -r md5sum ) \
      > "$DPKG_STATE/info/${package_name}.md5sums"
  done

  # Part B defect 2: seeded packages never ran their postinst, so the
  # alternatives they declare (busybox vi/pager/nc, coreutils pager, less
  # pager) never existed on a fresh device (`pager: command not found`).
  # Wire the link chains and the dpkg alternatives admin database directly,
  # from the same .alternatives files termux_step_create_alternatives reads.
  python3 "$SCRIPT_DIR/plan-bootstrap.py" alternatives \
    --tree "$SRC" --stage "$PREFIX_STAGE" --prefix "$PREFIX_ABS" \
    "${SEEDED_PACKAGE_NAMES[@]}"
fi

# Optional extra files merged into the prefix root (e.g. the standalone
# built termux-exec LD_PRELOAD library, which lands under lib/).
if [[ -n "${CODEC_EXTRA_PREFIX_FILES:-}" && -d "${CODEC_EXTRA_PREFIX_FILES:-}" ]]; then
  cp -a "$CODEC_EXTRA_PREFIX_FILES/." "$PREFIX_STAGE/"
  echo "assemble: merged extra prefix files from $CODEC_EXTRA_PREFIX_FILES"
fi

# Some packages (e.g. termux-keyring) ship symlinks whose targets are the
# absolute device prefix path baked into the .deb; the alternatives wiring
# above also creates absolute in-prefix targets. Relativize every target that
# falls inside the extracted prefix so the archive carries no absolute
# symlink targets and stays correct no matter where the prefix lives.
while IFS= read -r link; do
  target="$(readlink "$link")"
  case "$target" in
    "$PREFIX_ABS"/*)
      inner="${target#"$PREFIX_ABS"/}"
      rel_dir="$(dirname "${link#"$PREFIX_STAGE"/}")"
      if [[ "$rel_dir" == "." ]]; then
        depth=0
      else
        IFS=/ read -r -a _segs <<< "$rel_dir"
        depth=${#_segs[@]}
      fi
      climb=""
      _i=0
      while [ "$_i" -lt "$depth" ]; do
        climb="../$climb"
        _i=$((_i + 1))
      done
      ln -sfn "${climb}${inner}" "$link"
      echo "assemble: relativized symlink $(basename "$link") -> ${climb}${inner}"
      ;;
  esac
done < <(find "$PREFIX_STAGE" -type l)

# The app extracts this archive directly into its $PREFIX. Therefore the
# archive root must contain bin/, lib/, etc/, not data/data/.../files/usr/.
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

# Standard two-field sidecar (hash + basename): the validator verifies the
# sidecar names the archive, and the publish workflow / app read token 1.
( cd "$(dirname "$OUT")" && sha256sum "$(basename "$OUT")" > "$(basename "$OUT").sha256" )
echo "wrote $OUT"
cat "${OUT}.sha256"
