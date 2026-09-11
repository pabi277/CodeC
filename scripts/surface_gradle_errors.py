#!/usr/bin/env python3
"""Re-emit the real error lines of a Gradle log as ::error annotations.

The repo's CI sandbox (and the owner pasting a failing log) can read
check-run *annotations*, not the raw log stream — so on failure the actual
error lines are surfaced as annotations (the same trick the gradle-bootstrap
:app shim uses, reusable for direct-wrapper steps like the Phase 42 release
build: see .github/workflows/build-apk.yml).

Usage: python3 scripts/surface_gradle_errors.py <log-file>
"""
import pathlib
import sys

NEEDLES = (
    "e: file", "error:", "FAILURE:", "* What went wrong:",
    "Execution failed for task", "There were failing tests", "FAILED",
)


def main() -> None:
    if len(sys.argv) != 2:
        raise SystemExit("usage: surface_gradle_errors.py <log-file>")
    log = pathlib.Path(sys.argv[1])
    if not log.is_file():
        print(f"::notice::no log file at {log} — nothing to surface")
        return
    lines = log.read_text(errors="replace").splitlines()
    seen: set[str] = set()
    count = 0
    for line in lines:
        if any(n in line for n in needles) and line not in seen:
            seen.add(line)
            count += 1
            if count > 40:
                break
            print("::error::" + line.replace("%", "%25")[:1800])


if __name__ == "__main__":
    main()
