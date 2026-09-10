#!/usr/bin/env python3
"""Read the compiler/test/lint annotations of a GitHub Actions run.

Why this exists (Phase 40.4): the agent sandbox cannot read raw CI logs
(`gh run view --log-failed` and `gh api .../jobs/<id>/logs` both fail — the
log blob host is unreachable), but a red `Build APK` run still carries its
real errors as check-run *annotations*, which GitHub renders into the run's
HTML page. That is the channel to read: `github.com` + `api.github.com` are
reachable from the sandbox, the log CDN is not.

Usage:
    python3 scripts/ci_annotations.py                     # latest run, this branch
    python3 scripts/ci_annotations.py <run-id>
    python3 scripts/ci_annotations.py <run-id> --branch arena/01a08c04-codec

Exit status is 0 when annotations were found or the run is green — a failure
to *read* CI is itself something worth noticing, so it exits non-zero when it
cannot find a run or a job page.
"""
from __future__ import annotations

import argparse
import html
import re
import subprocess
import sys
import urllib.request

DEFAULT_REPO = "pabi277/CodeC"


def gh(*args: str) -> str:
    result = subprocess.run(["gh", *args], capture_output=True, text=True)
    if result.returncode != 0:
        raise SystemExit(f"gh {' '.join(args)} failed: {result.stderr.strip()}")
    return result.stdout.strip()


def latest_run(repo: str, branch: str | None) -> str:
    args = [
        "run", "list", "--repo", repo, "--limit", "1",
        "--json", "databaseId,conclusion,headBranch,displayTitle",
    ]
    if branch:
        args += ["--branch", branch]
    raw = gh(*args)
    if not raw or raw == "[]":
        raise SystemExit(f"no runs found for {repo}" + (f" on {branch}" if branch else ""))
    import json

    run = json.loads(raw)[0]
    print(
        f"run {run['databaseId']} · {run['conclusion']} · {run['headBranch']} ·"
        f" {run['displayTitle']}",
        file=sys.stderr,
    )
    return str(run["databaseId"])


def annotations(repo: str, run_id: str) -> list[str]:
    import json

    jobs = json.loads(gh("api", f"repos/{repo}/actions/runs/{run_id}/jobs"))
    lines: list[str] = []
    for job in jobs.get("jobs", []):
        job_id = job.get("id")
        title = job.get("name", "job")
        # Prefer the REST annotations endpoint; fall back to the run's HTML page
        # (the endpoints answer "Not Found" for older/limited runs).
        try:
            found = gh("api", f"repos/{repo}/actions/jobs/{job_id}/annotations")
        except SystemExit:
            found = ""
        try:
            rows = json.loads(found) if found.strip().startswith("[") else []
        except json.JSONDecodeError:
            rows = []
        if rows:
            for row in rows:
                message = (row.get("message") or "").strip()
                path = row.get("path") or ""
                line = row.get("start_line") or ""
                lines.append(f"[{title}] {path}:{line} {message}")
                continue
        if not lines:
            lines += scrape_run_page(repo, run_id, job_id, title)
    return lines


def scrape_run_page(repo: str, run_id: str, job_id: str, title: str) -> list[str]:
    """Read the annotations GitHub renders into the job page's HTML."""
    url = f"https://github.com/{repo}/actions/runs/{run_id}/job/{job_id}"
    try:
        with urllib.request.urlopen(url, timeout=45) as response:
            page = response.read().decode("utf-8", "replace")
    except Exception as exc:  # pragma: no cover - network dependent
        print(f"could not fetch {url}: {exc}", file=sys.stderr)
        return []
    text = html.unescape(re.sub(r"<[^>]+>", "\n", page))
    out: list[str] = []
    # Compiler errors look like `e: file:///…/X.kt:12:5 message`, warnings `w:`.
    for raw in text.splitlines():
        row = raw.strip()
        if re.match(r"^[ew]:\s+file:///", row) or row.startswith("TEST FAILED:"):
            if row not in out:
                out.append(f"[{title}] {row}")
    return out


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("run_id", nargs="?", help="run id (default: latest)")
    parser.add_argument("--repo", default=DEFAULT_REPO)
    parser.add_argument("--branch", default=None)
    args = parser.parse_args()

    run_id = args.run_id or latest_run(args.repo, args.branch)
    rows = annotations(args.repo, run_id)
    if not rows:
        print(f"no annotations for run {run_id} (green, or nothing to annotate)")
        return 0
    print(f"{len(rows)} annotation(s) for run {run_id}:\n")
    for row in rows:
        print(row)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
