#!/usr/bin/env bash
# Generate and validate a CodeC-only static APT repository.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
INPUT="${1:?usage: generate-repository.sh <deb-directory> <repository-output> [arch ...]}"
OUTPUT="${2:?usage: generate-repository.sh <deb-directory> <repository-output> [arch ...]}"
shift 2
if [[ $# -eq 0 ]]; then
  ARCHES=(aarch64 x86_64)
else
  ARCHES=("$@")
fi

PYTHONPATH="$ROOT/scripts${PYTHONPATH:+:$PYTHONPATH}" \
  python3 "$ROOT/scripts/generate-repository.py" "$INPUT" "$OUTPUT" \
    --architectures "${ARCHES[@]}"
PYTHONPATH="$ROOT/scripts${PYTHONPATH:+:$PYTHONPATH}" \
  python3 "$ROOT/scripts/validate-repository.py" "$OUTPUT" \
    --architectures "${ARCHES[@]}"
