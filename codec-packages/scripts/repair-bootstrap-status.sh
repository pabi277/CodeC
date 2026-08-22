#!/usr/bin/env bash
# Repair the seeded dpkg status DB inside a published CodeC Phase 3 bootstrap
# archive, without rebuilding the ~100-minute package pipeline.
#
# Background (docs/PHASE3_PKG_DEBUGGING.md §1): the userland-v2-dev bootstrap
# was built before the dpkg-perl recipe override dropped the bogus *runtime*
# dependency on clang (codec-packages/scripts/apply-recipe-overrides.sh).
# assemble-bootstrap.sh seeds each built package's control fields verbatim
# into var/lib/dpkg/status, so the published archive contains a stale
# Depends line inside the `Package: dpkg-perl` stanza. Device evidence
# (2026-08-22, grep on a freshly extracted prefix) shows its true shape:
#
#   Depends: perl, clang, make, dpkg (= 1.22.6-5)
#
# apt treats the userland as broken ("clang ... is not installable") on
# every install until `pkg` self-heals it. The recipe fix changes only
# dependency metadata (TERMUX_SUBPKG_DEPENDS), so the complete content
# delta of a full rebuild is exactly that one line in the status DB. This
# script applies the same one-line repair to the published artifact
# directly (same transformation as the app's self-heal: remove the
# ` clang,` element) and proves, with evidence, that nothing else changed.
#
# Exit codes:
#   0  archive was repaired (patched/ output ready)
#   3  status DB already clean — nothing to do, output untouched
#   4  unexpected evidence (stale line shape differs, multiple matches,
#      collateral changes detected) — refused to patch
#   1  any other failure (missing tools, bad archive, validator failed)
#
# Invariants honored: no `.` on PATH; this tool never installs or uses
# official com.termux prebuilt packages or repositories (no installer-flag
# bootstrap builds), and the archive keeps the CodeC prefix layout.
# NOTE for the CI guardrail scanner in "Validate CodeC overlay": do not
# put the literal forbidden build flag and its script name on one line in
# this file's comments — the scanner must not match its own rationale.
#
# Usage:
#   repair-bootstrap-status.sh ARCHIVE_OUTDIR BOOTSTRAP_TAR_GZ [--skip-validate]
#
# Writes ARCHIVE_OUTDIR/<archive-name> and <archive-name>.sha256 only when the
# repair was actually applied.

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" >/dev/null && pwd)"

usage() {
  echo "usage: $0 ARCHIVE_OUTDIR BOOTSTRAP_TAR_GZ [--skip-validate]" >&2
  exit 1
}

OUTDIR=""
ARCHIVE=""
SKIP_VALIDATE=0
while [[ $# -gt 0 ]]; do
  case "$1" in
    --skip-validate) SKIP_VALIDATE=1 ;;
    -*) usage ;;
    *)
      if [[ -z "$OUTDIR" ]]; then OUTDIR="$1";
      elif [[ -z "$ARCHIVE" ]]; then ARCHIVE="$1";
      else usage; fi
      ;;
  esac
  shift
done
[[ -n "$OUTDIR" && -n "$ARCHIVE" ]] || usage
[[ -f "$ARCHIVE" ]] || { echo "repair: archive not found: $ARCHIVE" >&2; exit 1; }

basename="$(basename "$ARCHIVE")"
case "$basename" in
  bootstrap-phase3-aarch64.tar.gz|bootstrap-phase3-x86_64.tar.gz) ;;
  *) echo "repair: unexpected archive name: $basename" >&2; exit 1 ;;
esac

for tool in tar gzip sha256sum awk diff find sort; do
  command -v "$tool" >/dev/null || { echo "repair: missing tool: $tool" >&2; exit 1; }
done

# Preserve the original numeric owners/modes when privileges allow (the
# archive is also byte-faithful evidence); fall back to plain tar in unrooted
# hosts. The app extractor reads only mode bits, so this is belt-and-braces.
SUDO=""
if command -v sudo >/dev/null && sudo -n true 2>/dev/null; then
  SUDO="sudo -n"
fi

WORKDIR="$(mktemp -d)"
trap 'rm -rf "$WORKDIR"' EXIT
STAGE="$WORKDIR/stage"
mkdir -p "$STAGE" "$OUTDIR"

STATUS_REL="./var/lib/dpkg/status"

echo "repair: extracting $basename"
$SUDO tar -xpzf "$ARCHIVE" -C "$STAGE"
[[ -f "$STAGE/$STATUS_REL" ]] || { echo "repair: no dpkg status DB in archive" >&2; exit 1; }

echo "repair: snapshot of every extracted file BEFORE patching"
BEFORE_LIST="$WORKDIR/before.sha"
( cd "$STAGE" && find . -type f -print0 | sort -z | xargs -0 sha256sum ) > "$BEFORE_LIST"
BEFORE_MEMBER_LIST="$WORKDIR/before.members"
tar -tzf "$ARCHIVE" | sort > "$BEFORE_MEMBER_LIST"

evidence_stanza() {
  awk 'BEGIN{RS=""; FS="\n"} /^Package: dpkg-perl\n/ { print "----- Package: dpkg-perl stanza -----"; print $0; print "--------------------------------------" }' \
    "$STAGE/$STATUS_REL"
}

echo "repair: evidence BEFORE patching:"
evidence_stanza

# The stale line's real shape, from device evidence (grep on the seeded
# status DB of the published bootstrap):
#
#   Package: dpkg-perl
#   ...
#   Depends: perl, clang, make, dpkg (= 1.22.6-5)
#
# The recipe appends a versioned cross-dependency, so the exact-line guard
# must NOT assume the line ends at "make". The repair therefore mirrors the
# app's on-device self-heal exactly (ShellEnvironment.pkgScript): inside the
# `Package: dpkg-perl` paragraph only, remove the single ` clang,` element
# from the Depends line, leaving `Depends: perl, make, dpkg (= 1.22.6-5)`.

# Guards: exactly one clang reference in the entire status DB, and it must
# sit on the dpkg-perl Depends line as a separate ` clang,` element.
# Evidence mismatch => refuse; already clean => nothing to do.
clang_count="$(grep -c "clang" "$STAGE/$STATUS_REL" || true)"

if [[ "$clang_count" == "0" ]]; then
  echo "repair: status DB is already clean (no clang reference) — nothing to do"
  exit 3
fi
if [[ "$clang_count" != "1" ]]; then
  echo "repair: unexpected evidence: clang matches=$clang_count (expected exactly 1)" >&2
  grep -n "clang" "$STAGE/$STATUS_REL" >&2 || true
  echo "repair: refusing to patch by guesswork" >&2
  exit 4
fi
if ! awk 'BEGIN{RS=""}
          /^Package: dpkg-perl\n/ && /\nDepends: [^\n]* clang,/ { found=1 }
          END{ exit !found }' "$STAGE/$STATUS_REL"; then
  echo "repair: unexpected evidence: the single clang reference is not a ' clang,' element on the dpkg-perl Depends line:" >&2
  grep -n "clang" "$STAGE/$STATUS_REL" >&2 || true
  echo "repair: refusing to patch by guesswork" >&2
  exit 4
fi

cp "$STAGE/$STATUS_REL" "$WORKDIR/status.before"

# Patch only the dpkg-perl paragraph: remove exactly one ` clang,` element.
# The whole-file clang count is already proven to be 1 and located on that
# Depends line, so sub() can only fire there. awk paragraph mode rewrites
# the file with canonical single-blank-line separators, byte-identical to
# the assembler's format (dpkg-deb -f + 'Status: install ok installed\n\n').
awk '
  BEGIN { RS=""; ORS="\n\n"; total=0 }
  /^Package: dpkg-perl\n/ {
    total += sub(/ clang,/, "")
  }
  { print }
  END { if (total != 1) exit 20 }
' "$WORKDIR/status.before" > "$STAGE/$STATUS_REL.tmp" || {
  echo "repair: awk substitution did not fire exactly once — refusing" >&2
  exit 4
}
# Preserve the original file's metadata (mode in particular).
cat "$STAGE/$STATUS_REL.tmp" > "$STAGE/$STATUS_REL"
rm -f "$STAGE/$STATUS_REL.tmp"

echo "repair: evidence AFTER patching:"
evidence_stanza

echo "repair: unified diff of the status DB (must be exactly one line):"
diff -u "$WORKDIR/status.before" "$STAGE/$STATUS_REL" || true

changed="$(diff "$WORKDIR/status.before" "$STAGE/$STATUS_REL" | grep -c "^[<>]" || true)"
if [[ "$changed" != "2" ]]; then
  echo "repair: status diff touched $changed lines, expected exactly 2 (1 old + 1 new)" >&2
  exit 4
fi
if grep -q "clang" "$STAGE/$STATUS_REL"; then
  echo "repair: clang reference still present after patch — refusing" >&2
  exit 4
fi

echo "repair: verifying every other extracted file is byte-identical"
AFTER_LIST="$WORKDIR/after.sha"
( cd "$STAGE" && find . -type f -print0 | sort -z | xargs -0 sha256sum ) > "$AFTER_LIST"
collateral="$(diff "$BEFORE_LIST" "$AFTER_LIST" | grep -c "^[<>]" || true)"
# pipefail-safe: diff exits 1 on any difference, so the `|| true` must guard
# the END of the whole pipeline, not a middle stage.
collateral_paths="$({ diff "$BEFORE_LIST" "$AFTER_LIST" | grep "^[<>]" | awk '{print $3}' | sort -u; } || true)"
if [[ "$collateral" != "2" || "$collateral_paths" != "$STATUS_REL" ]]; then
  echo "repair: unexpected collateral changes beyond $STATUS_REL:" >&2
  diff "$BEFORE_LIST" "$AFTER_LIST" | { grep "^[<>]" || true; } >&2
  exit 4
fi

OUT_ARCHIVE="$OUTDIR/$basename"
echo "repair: repacking with the assembler's invocation (tar -C stage -czf out .)"
$SUDO tar -cpzf "$OUT_ARCHIVE" -C "$STAGE" .
if [[ -n "$SUDO" ]]; then
  sudo -n chown "$(id -u):$(id -g)" "$OUT_ARCHIVE"
fi

tar -tzf "$OUT_ARCHIVE" | sort > "$WORKDIR/after.members"
if ! diff -q "$BEFORE_MEMBER_LIST" "$WORKDIR/after.members" >/dev/null; then
  echo "repair: archive member list changed — refusing" >&2
  diff "$BEFORE_MEMBER_LIST" "$WORKDIR/after.members" | head -20 >&2
  rm -f "$OUT_ARCHIVE"
  exit 4
fi

# Standard two-field sidecar (hash + basename), identical to the assembler and
# consumed by the app, the validator, and the publish workflow.
( cd "$OUTDIR" && sha256sum "$(basename "$OUT_ARCHIVE")" > "$(basename "$OUT_ARCHIVE").sha256" )

if [[ "$SKIP_VALIDATE" == "0" ]]; then
  echo "repair: running the repository's own release gate (validate-bootstrap.py)"
  python3 "$SCRIPT_DIR/validate-bootstrap.py" "$OUT_ARCHIVE"
fi

NEW_SHA="$(awk '{ print $1; exit }' "$OUT_ARCHIVE.sha256")"
echo "repair: OK  $OUT_ARCHIVE"
echo "repair: new sha256: $NEW_SHA"
