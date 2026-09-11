#!/usr/bin/env python3
"""Phase 42.1 — make app-release attachment idempotent.

A release can be reached TWICE by the publish pipeline (a tag pushed from
the CLI fires push:tags; a release created from the github.com UI fires
release[published] — and web-created tags do NOT fire push:tags, so the
browser flow lands here only). Re-uploading an asset name that already
exists is a 422, so before attaching we delete any same-named stale asset:
a re-run becomes a repair instead of a failure.

Usage: python3 scripts/clear_release_assets.py <tag> <apk-root>

<apk-root> is searched recursively, restricted to */release/ directories
(Phase 42.2 added per-ABI split dirs like outputs/apk/arm64-v8a/release/).
"""
import json
import os
import pathlib
import subprocess
import sys


def gh(*args: str) -> str:
    result = subprocess.run(
        ["gh", *args], capture_output=True, text=True, env=os.environ
    )
    if result.returncode != 0:
        raise RuntimeError(result.stderr.strip())
    return result.stdout


def main() -> None:
    if len(sys.argv) != 3:
        raise SystemExit("usage: clear_release_assets.py <tag> <apk-root>")
    tag, apk_root = sys.argv[1], pathlib.Path(sys.argv[2])
    try:
        rel = json.loads(gh("api", f"repos/{{owner}}/{{repo}}/releases/tags/{tag}"))
    except RuntimeError:
        print(f"no release at {tag} yet — the create step will make it")
        return
    wanted = {
        p.name
        for p in apk_root.rglob("*.apk")
        if p.parent.name == "release"
    }
    for asset in rel.get("assets", []):
        if asset.get("name") in wanted:
            gh("api", "-X", "DELETE", f"repos/{{owner}}/{{repo}}/releases/assets/{asset['id']}")
            print("replaced stale asset:", asset["name"])


if __name__ == "__main__":
    main()
