# Phase 63 — phone checks (NOT RUN)

> **Owner update — 2026-09-27:** Top-level appearance looks good; further device
> testing for this delivery is declined, **not passed**. The owner explicitly
> authorised merging via [PR #86](https://github.com/pabi277/CodeC/pull/86),
> subject to final CI. Future detail-by-detail polishing phases will be defined
> in new chats. [Full owner feedback and handoff](../../../journal/OWNER_HANDOFF_20260927.md).

**Checklist retained for reference only; the owner is not being asked to run it for this merge.**

Build **bd55e70**, CI **36301860116**:
https://github.com/pabi277/CodeC/actions/runs/36301860116

Use a disposable local test project, not irreplaceable work. No GitHub push is
needed to check Discard. Keep terminal Git commands idle while confirming.

## Prepare a partial-stage example

In the CodeC terminal inside the disposable project (configure a local test
identity only if git commit asks for it):

```sh
git init
printf 'committed\n' > tracked.txt
printf 'keep other file\n' > other.txt
git add -- tracked.txt other.txt
git commit -m 'Discard test base'
printf 'staged\n' > tracked.txt
git add -- tracked.txt
printf 'unstaged\n' > tracked.txt
```

Do not run this setup in an existing valuable project: it intentionally changes
the two named files and creates a local commit. No remote or credential needed.

| ID | Check | Expected |
|---|---|---|
| G1 | Open Source control through editor Repository, then through Projects | Existing pane, branch, diff, staging, commit/push/pull controls remain reachable |
| G2 | Open tracked.txt, then its diff | Working file says unstaged; diff still opens; Discard action is present |
| G3 | Tap Discard, then Cancel or back | Path is named and staged-preservation warning is explicit; bytes/status unchanged |
| G4 | Tap Discard and confirm | Working file becomes staged, NOT committed; staged change stays; other.txt unchanged |
| G5 | Return to editor; wait past autosave, switch tabs and back | Restored text remains; discarded edit does not reappear; selected undo history is cleared |
| G6 | Repeat via Projects while the editor is parked on the back stack | Same targeted buffer refresh, no stale content on return |
| G7 | Try with a still-unsaved open buffer (if autosave has not run yet) | Refusal asks to save/review first; no silent buffer loss |
| G8 | Delete an existing tracked file without staging deletion, then Discard | File restored from index; unrelated edits remain |
| G9 | Add a new file, stage it, modify it; inspect untracked/staged-only/rename/conflict rows | No Discard for these unsupported cases; conflict Mark Resolved remains |
| G10 | Stage an untracked row, then unstage it | Untracked is treated as Stage, not Unstage; no broken toggle |
| G11 | Open two files, leave an edit in the other tab, discard selected clean-buffer file | Only selected tab content/history refreshed; other buffer retained |
| G12 | Refresh after failure/stale status (e.g. stage file before confirming) | Honest refusal/refreshed status, no optimistic clean result |
| G13 | Existing commit/push/pull in an explicitly disposable remote, if available | Existing flow and error/outcome reporting unchanged; optional live-network verification, not claimed by host tests |

Host CI exercises temporary real-Git repos, source wiring, and actual editor
buffer integration. It does NOT prove device UI taps, Android-packaged Git,
GitHub authentication or remote pushes. Do not share tokens in the test report.
Phase 61 preview checks also remain pending. No merge before explicit approval.
