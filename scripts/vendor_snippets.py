#!/usr/bin/env python3
"""Phase 30.1 — vendor rafamadriz/friendly-snippets (MIT) into the APK assets.

CodeC's completion snippets used to be hand-written Kotlin tables in
`CodeCompletionEngine` (1-9 entries per language). Phase 30.1 replaces them
with the community snippet packs from

    https://github.com/rafamadriz/friendly-snippets   (MIT, see LICENSE)

copied VERBATIM (no edits — same policy as the Phase 29 TextMate grammars)
into `app/src/main/assets/snippets/`, and attributed in
`app/src/main/assets/licenses/FRIENDLY_SNIPPETS_MIT.txt`.

The copy is PINNED to one upstream commit so re-running this script always
reproduces the same bytes:

    python3 scripts/vendor_snippets.py            # download + copy + verify
    python3 scripts/vendor_snippets.py --check    # verify the tree only

Only the packs for languages CodeC actually runs/colours are vendored
(`PACKS` below); the upstream tree is ~1.9 MB across 60+ languages, the
selected subset is ~280 KB raw (~50 KB inside the compressed APK).

Requires: python3 + curl (no other tooling). Network access to
codeload.github.com.
"""

from __future__ import annotations

import argparse
import json
import pathlib
import shutil
import subprocess
import sys
import tarfile
import tempfile

REPO = "rafamadriz/friendly-snippets"
# Pinned upstream commit ("main" as of 2026-09-06). Bump deliberately.
PIN = "6cd7280adead7f586db6fccbd15d2cac7e2188b9"
PIN_DATE = "2026-01-23"

ROOT = pathlib.Path(__file__).resolve().parent.parent
DEST = ROOT / "app" / "src" / "main" / "assets" / "snippets"
LICENSE_DIR = ROOT / "app" / "src" / "main" / "assets" / "licenses"

# LanguageType bucket -> upstream snippet files (relative to snippets/).
# Mirrors `SnippetAssets.packsFor` in
# app/src/main/java/com/codeci/ide/ui/editor/snippets/SnippetAssets.kt —
# keep the two lists in sync (SnippetLibraryTest pins every path).
PACKS: dict[str, list[str]] = {
    "C": ["c/c.json", "c/cdoc.json"],
    "CPP": ["cpp/cpp.json", "cpp/cppdoc.json"],
    "PYTHON": [
        "python/python.json",
        "python/comprehension.json",
        "python/debug.json",
        "python/pydoc.json",
        "python/unittest.json",
    ],
    "JAVASCRIPT": [
        "javascript/javascript.json",
        "javascript/jsdoc.json",
        "javascript/react.json",
    ],
    "TYPESCRIPT": [
        "javascript/typescript.json",
        "javascript/tsdoc.json",
        "javascript/react-ts.json",
    ],
    "SHELL": ["shell/shell.json", "shell/shelldoc.json"],
    "HTML": ["html.json"],
    "CSS": ["css.json"],
    "MARKDOWN": ["markdown.json"],
    "GO": ["go.json"],
    "RUST": ["rust/rust.json", "rust/rustdoc.json"],
    "PHP": ["php/php.json", "php/phpdoc.json"],
    "RUBY": ["ruby/ruby.json", "ruby/rdoc.json"],
    "LUA": ["lua/lua.json", "lua/luadoc.json"],
    # XML / YAML / JSON / TEXT intentionally get NO pack (plan rule S4:
    # "JSON/TEXT still get few/no snippets unless the pack has them").
}


def all_paths() -> list[str]:
    seen: list[str] = []
    for files in PACKS.values():
        for f in files:
            if f not in seen:
                seen.append(f)
    return sorted(seen)


def download(pin: str) -> pathlib.Path:
    """Fetch the upstream tarball for [pin]; returns the extracted snippets/ dir."""
    url = f"https://codeload.github.com/{REPO}/tar.gz/{pin}"
    tmp = pathlib.Path(tempfile.mkdtemp(prefix="friendly-snippets-"))
    tarball = tmp / "src.tar.gz"
    print(f"  downloading {url}")
    subprocess.run(
        ["curl", "-fsSL", "-o", str(tarball), url],
        check=True,
    )
    with tarfile.open(tarball, "r:gz") as tf:
        tf.extractall(tmp / "tree")
    root = tmp / "tree" / f"friendly-snippets-{pin}"
    if not root.is_dir():
        candidates = [p for p in (tmp / "tree").iterdir() if p.is_dir()]
        if len(candidates) != 1:
            raise SystemExit(f"unexpected tarball layout: {candidates}")
        root = candidates[0]
    snippets = root / "snippets"
    if not snippets.is_dir():
        raise SystemExit(f"no snippets/ directory in {root}")
    return snippets


def copy_tree(snippets: pathlib.Path) -> None:
    if DEST.exists():
        shutil.rmtree(DEST)
    total = 0
    for rel in all_paths():
        src = snippets / rel
        if not src.is_file():
            raise SystemExit(f"missing upstream file: {rel}")
        dst = DEST / rel
        dst.parent.mkdir(parents=True, exist_ok=True)
        shutil.copyfile(src, dst)
        total += dst.stat().st_size
    print(f"  vendored {len(all_paths())} packs -> {DEST.relative_to(ROOT)} ({total:,} bytes raw)")


def write_license(snippets: pathlib.Path) -> None:
    upstream = snippets.parent / "LICENSE"
    text = upstream.read_text(encoding="utf-8") if upstream.is_file() else ""
    header = (
        "CodeC — vendored snippet packs (Phase 30.1)\n"
        "=============================================\n\n"
        f"assets/snippets/**.json are unmodified copies of files from\n"
        f"{REPO} (MIT), pinned to upstream commit\n"
        f"{PIN} ({PIN_DATE}).\n\n"
        "Re-vendor with:  python3 scripts/vendor_snippets.py\n\n"
        "Only the packs for languages CodeC runs/colours are shipped; the\n"
        "upstream licence follows in full.\n\n"
        "-------------------------------------------------------------\n\n"
    )
    LICENSE_DIR.mkdir(parents=True, exist_ok=True)
    (LICENSE_DIR / "FRIENDLY_SNIPPETS_MIT.txt").write_text(header + text, encoding="utf-8")
    print(f"  wrote {LICENSE_DIR.relative_to(ROOT)}/FRIENDLY_SNIPPETS_MIT.txt")


def verify() -> int:
    """Every mapped pack exists, is strict JSON, and has ≥1 usable snippet."""
    problems = 0
    total_entries = 0
    total_bytes = 0
    for rel in all_paths():
        path = DEST / rel
        if not path.is_file():
            print(f"  MISSING {rel}")
            problems += 1
            continue
        raw = path.read_text(encoding="utf-8")
        total_bytes += path.stat().st_size
        try:
            data = json.loads(raw)
        except json.JSONDecodeError as exc:
            print(f"  BAD JSON {rel}: {exc}")
            problems += 1
            continue
        if not isinstance(data, dict) or not data:
            print(f"  EMPTY {rel}")
            problems += 1
            continue
        usable = 0
        for name, entry in data.items():
            if not isinstance(entry, dict):
                continue
            prefix = entry.get("prefix")
            body = entry.get("body")
            if body is None:
                continue
            prefixes = prefix if isinstance(prefix, list) else [prefix]
            if any(isinstance(p, str) and p.strip() for p in prefixes):
                usable += 1
        total_entries += usable
        if usable == 0:
            print(f"  NO USABLE SNIPPETS {rel}")
            problems += 1
    lic = LICENSE_DIR / "FRIENDLY_SNIPPETS_MIT.txt"
    if not lic.is_file():
        print("  MISSING assets/licenses/FRIENDLY_SNIPPETS_MIT.txt")
        problems += 1
    print(
        f"  {len(all_paths())} packs, {total_entries} usable snippets, "
        f"{total_bytes:,} bytes raw ({problems} problem(s))"
    )
    return problems


def main() -> int:
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument("--check", action="store_true", help="verify the vendored tree only")
    ap.add_argument("--pin", default=PIN, help="upstream commit sha to vendor")
    args = ap.parse_args()

    if args.check:
        return 1 if verify() else 0

    snippets = download(args.pin)
    copy_tree(snippets)
    write_license(snippets)
    print("verifying…")
    return 1 if verify() else 0


if __name__ == "__main__":
    sys.exit(main())
