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

REPO_ROOT = Path(__file__).resolve().parents[2]
SCRIPTS_DIR = REPO_ROOT / "codec-packages" / "scripts"

# Exact ERE from .github/workflows/package-repository.yml "Validate CodeC
# overlay"; keep in sync with the workflow if it ever changes.
forbidden = re.compile(r"build-package\.sh.*-[ \t]*I|build-package\.sh.*--install")


class PackageGroupSplitTest(unittest.TestCase):
    """The round-4 360-minute ceiling failures (dispatches 33506104710 and
    33547475854) forced the build job to fan out into group legs. The split
    introduces two seams that must never drift apart:
      1. CODEC_REPOSITORY_GROUPS (base/llvm/langs) must partition
         CODEC_REPOSITORY_PACKAGES exactly — any missed or duplicated root
         silently drops or double-builds a package;
      2. the workflow matrix group names, the per-leg artifact names, the
         publish-dev pattern download, and the publish-bootstrap-release
         download must all agree (a renamed artifact with a stale reference
         fails only at publish/bootstrap-release time — hours later).
    """

    @classmethod
    def _load_group_lists(cls) -> dict[str, list[str]]:
        text = (REPO_ROOT / "codec-packages" / "properties.codec.sh").read_text()
        lists: dict[str, list[str]] = {}
        for match in re.finditer(
            r'^(CODEC_REPOSITORY_PACKAGES|CODEC_REPOSITORY_GROUP_\w+)="([\s\S]*?)"',
            text,
            re.M,
        ):
            lists[match.group(1)] = match.group(2).split()
        return lists

    def test_groups_partition_the_full_package_list(self) -> None:
        lists = self._load_group_lists()
        full = set(lists["CODEC_REPOSITORY_PACKAGES"])
        groups = {
            key.removeprefix("CODEC_REPOSITORY_GROUP_").lower(): words
            for key, words in lists.items()
            if key.startswith("CODEC_REPOSITORY_GROUP_")
        }
        self.assertEqual(
            {"base", "llvm", "langs"}, set(groups), "expected exact group names"
        )
        union = [pkg for words in groups.values() for pkg in words]
        self.assertEqual(sorted(union), sorted(full), "group union != full list")
        self.assertEqual(
            len(union), len(set(union)), "a package appears in two groups"
        )
        # The long pole gets its own leg — the whole point of the split.
        self.assertEqual(groups["llvm"], ["libllvm"])

    def test_workflow_split_references_are_consistent(self) -> None:
        wf = (REPO_ROOT / ".github" / "workflows" / "package-repository.yml").read_text()
        # Matrix carries exactly the group names (single fan-out point).
        self.assertRegex(wf, r"group:\s*\[base, llvm, langs\]")
        # Per-leg artifact names are group-suffixed.
        self.assertIn("codec-repository-${{ matrix.arch }}-${{ matrix.group }}", wf)
        # The time-heavy bootstrap steps run only in the base leg.
        self.assertRegex(
            wf,
            r"name: Build Phase 3 package-manager bootstrap\n\s+if: \$\{\{ matrix\.group == 'base' \}\}",
        )
        self.assertRegex(
            wf,
            r"name: Validate Phase 3 bootstrap archive\n\s+if: \$\{\{ matrix\.group == 'base' \}\}",
        )
        # publish-dev pattern-merges every leg's artifact per arch.
        self.assertIn("pattern: codec-repository-aarch64-*", wf)
        self.assertIn("pattern: codec-repository-x86_64-*", wf)
        self.assertIn("merge-multiple: true", wf)
        # The old exact-name artifacts must be gone from this workflow.
        self.assertNotIn("name: codec-repository-aarch64\n", wf)
        self.assertNotIn("name: codec-repository-x86_64\n", wf)

    def test_bootstrap_release_consumes_base_leg_artifacts(self) -> None:
        wf = (REPO_ROOT / ".github" / "workflows" / "publish-bootstrap-release.yml").read_text()
        self.assertIn("name: codec-repository-aarch64-base\n", wf)
        self.assertIn("name: codec-repository-x86_64-base\n", wf)
        self.assertNotIn("name: codec-repository-aarch64\n", wf)
        self.assertNotIn("name: codec-repository-x86_64\n", wf)

    def test_build_script_resolves_each_group_exactly(self) -> None:
        """Dry-run the real script per group: the resolved root list must be
        exactly the group variable's content (resolution lives in the script,
        so test the script, not a copy of the mapping)."""
        import subprocess

        lists = self._load_group_lists()
        script = REPO_ROOT / "codec-packages" / "scripts" / "build-package-repository.sh"
        for group in ("base", "llvm", "langs"):
            proc = subprocess.run(
                ["bash", str(script), "aarch64", group],
                env={"CODEC_REPO_DRY_RUN": "1", "PATH": "/usr/bin:/bin"},
                capture_output=True,
                text=True,
            )
            self.assertEqual(proc.returncode, 0, proc.stderr)
            resolved = [
                line.strip()
                for line in proc.stdout.splitlines()
                if line.startswith("  ")
            ]
            expected = lists[f"CODEC_REPOSITORY_GROUP_{group.upper()}"]
            self.assertEqual(expected, resolved, f"group {group} resolution drifted")

    def test_build_script_rejects_unknown_group(self) -> None:
        import subprocess

        script = REPO_ROOT / "codec-packages" / "scripts" / "build-package-repository.sh"
        proc = subprocess.run(
            ["bash", str(script), "aarch64", "nonsense"],
            env={"CODEC_REPO_DRY_RUN": "1", "PATH": "/usr/bin:/bin"},
            capture_output=True,
            text=True,
        )
        self.assertNotEqual(proc.returncode, 0)
        self.assertIn("unknown package group", proc.stderr)


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
