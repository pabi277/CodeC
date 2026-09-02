#!/usr/bin/env bash
# Build a curated CodeC package closure from official Termux recipes.
# This deliberately never uses the dependency-download shortcut: all
# dependencies are rebuilt for com.codeci.ide and the CodeC prefix.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
# shellcheck disable=SC1091
source "$ROOT/properties.codec.sh"

ARCH="${1:-aarch64}"
# Optional second argument (Phase 20.1 CI split): a package GROUP name from
# properties.codec.sh (base / llvm / langs — CODEC_REPOSITORY_GROUP_* vars).
# Without it the full CODEC_REPOSITORY_PACKAGES union is built, byte-identical
# behavior to pre-split rounds (local runs, future single-leg debugging).
GROUP="${2:-}"

case "$ARCH" in
  aarch64|x86_64) ;;
  *) echo "build-package-repository: unsupported architecture: $ARCH" >&2; exit 2 ;;
esac

# Resolve the root list FIRST — with no filesystem side effects — so
# CODEC_REPO_DRY_RUN=1 can exercise group resolution in the host suite
# without touching the network, docker, git, or the worktree.
if [[ -n "$GROUP" ]]; then
  group_var="CODEC_REPOSITORY_GROUP_$(printf '%s' "$GROUP" | tr '[:lower:]' '[:upper:]')"
  group_list="${!group_var:-}"
  if [[ -z "$group_list" ]]; then
    echo "build-package-repository: unknown package group: $GROUP" >&2
    echo "known groups: $(compgen -v CODEC_REPOSITORY_GROUP_ | sed 's/^CODEC_REPOSITORY_GROUP_//' | tr 'A-Z' 'a-z' | tr '\n' ' ')" >&2
    exit 2
  fi
  PACKAGES=$(printf '%s\n' "$group_list" | awk 'NF { print $1 }')
else
  PACKAGES=$(printf '%s\n' "$CODEC_REPOSITORY_PACKAGES" | awk 'NF { print $1 }')
fi
if [[ -z "$PACKAGES" ]]; then
  echo "build-package-repository: no repository packages configured" >&2
  exit 2
fi

echo "CodeC repository roots ($ARCH${GROUP:+, group=$GROUP}):"
printf '  %s\n' $PACKAGES

# Testability hook: with CODEC_REPO_DRY_RUN=1 the script stops here, before
# any clone/build — the host suite exercises group resolution this way
# (rehearsals proved why: a flattened fixture once hid the real recipe
# shape from CI until the fail-loud guards tripped mid-dispatch).
if [[ "${CODEC_REPO_DRY_RUN:-0}" == "1" ]]; then
  echo "build-package-repository: dry run, stopping before clone/build"
  exit 0
fi

WORK="${CODEC_REPO_WORK:-$ROOT/.work/repository-$ARCH}"
SRC="$WORK/termux-packages"
DIST="$ROOT/dist/repository-$ARCH"
mkdir -p "$WORK" "$ROOT/dist"

if [[ ! -d "$SRC/.git" ]]; then
  git clone --filter=blob:none --no-checkout "$TERMUX_PACKAGES_REPO" "$SRC"
  git -C "$SRC" fetch --depth 1 origin "$TERMUX_PACKAGES_REF"
  git -C "$SRC" checkout --detach "$TERMUX_PACKAGES_REF"
fi

"$ROOT/scripts/apply-prefix.sh" "$SRC"
"$ROOT/scripts/apply-recipe-overrides.sh" "$SRC"

# Keep the build output limited to this architecture. The package builder will
# still place the complete source-built dependency closure in output/.
rm -rf "$SRC/output" "$SRC/debs"

build_one() {
  local package="$1"
  if [[ "${CODEC_USE_DOCKER:-1}" == "1" ]]; then
    if [[ ! -x "$SRC/scripts/run-docker.sh" ]]; then
      echo "build-package-repository: official run-docker.sh is missing" >&2
      exit 2
    fi
    (cd "$SRC" && \
      CONTAINER_NAME="${CODEC_CONTAINER_NAME:-codec-package-repository-${ARCH}}" \
        ./scripts/run-docker.sh ./build-package.sh -a "$ARCH" -f "$package")
  else
    echo "build-package-repository: CODEC_USE_DOCKER=0 (host build)" >&2
    (cd "$SRC" && ./build-package.sh -a "$ARCH" -f "$package")
  fi
}

for package in $PACKAGES; do
  build_one "$package"
done

DEB_DIR="$SRC/output"
[[ -d "$DEB_DIR" ]] || DEB_DIR="$SRC/debs"
[[ -d "$DEB_DIR" ]] || {
  echo "build-package-repository: no package output directory" >&2
  exit 1
}

"$ROOT/scripts/generate-repository.sh" "$DEB_DIR" "$DIST" "$ARCH"
echo "build-package-repository: wrote $DIST"
