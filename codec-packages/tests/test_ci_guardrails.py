#!/usr/bin/env python3
"""Tripwires replicating the static CI guardrails that other checks do not.

The "Validate CodeC overlay" step of the package-repository workflow fails
the ~104-minute build if ANY file under codec-packages/scripts contains the
literal forbidden build-flag pattern — including in comments (a Part A
repair script documented the rule with the exact literal string and failed
a dispatch after ~90 seconds; caught early, but preventable). Replicating
the scanner here keeps it honest before anything is pushed.
"""

from __future__ import annotations

import re
import unittest
from pathlib import Path

SCRIPTS_DIR = Path(__file__).resolve().parents[1] / "scripts"

# Exact ERE from .github/workflows/package-repository.yml "Validate CodeC
# overlay"; keep in sync with the workflow if it ever changes.
forbidden = re.compile(r"build-package\.sh.*-[ \t]*I|build-package\.sh.*--install")


class NoForbiddenBuildFlagTest(unittest.TestCase):
    def test_no_forbidden_build_flag_literals_in_scripts(self) -> None:
        offenders = []
        for path in sorted(SCRIPTS_DIR.rglob("*")):
            if not path.is_file() or path.suffix == ".md" or path.name == "LICENSE":
                continue
            try:
                text = path.read_text(errors="replace")
            except OSError:
                continue
            for lineno, line in enumerate(text.splitlines(), 1):
                if forbidden.search(line):
                    offenders.append(f"{path.relative_to(SCRIPTS_DIR)}:{lineno}: {line.strip()}")
        self.assertEqual(offenders, [], "\n".join(offenders))


if __name__ == "__main__":
    unittest.main()
