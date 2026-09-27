# 63.1 — Discard unstaged changes

Plan before implementation:
- A labelled Discard action on eligible existing change rows, then a confirmation
  naming the path and explicitly saying unstaged edits are lost, staged changes
  retained, and no undo offered. Cancel/back does nothing.
- Eligible porcelain states only: x space/M, y M/D, no oldPath/conflict. New,
  staged-only, copied/renamed, type-changed, unmerged paths have no Discard.
- Engine never trusts UI status: validate exact safe relative path, reject .git,
  directories and symlink components; inspect index mode/stage plus fresh
  staged/unstaged diff status for exactly that literal path. Only regular
  tracked files (100644/100755, stage 0), not gitlinks or symlinks.
- Restore via `git --literal-pathspecs checkout-index --force -- <path>`;
  no reset, HEAD checkout, recursive delete, clean, network, or index mutation.
  New files stay safe. Spaces, Unicode, flags and wildcard-looking names are
  literal. No shell, no prefix match and no directory-wide discard.
- Main-thread editor coordination applies to BOTH sheet entry points: weak
  registration of live editor VMs, reject any unsaved buffer of the selected
  file, block writes to that file while Git runs, then reread only that file
  and reset only its undo history. Other tabs/undo buffers are not reloaded.
  Recheck dirty buffer before reload; do not overwrite new in-memory edits.
- Show non-dismissable progress during the bounded operation. Finally release
  the write guard and reconcile the selected file even if Git fails. Existing
  refresh/error reporting stays; no optimistic removal of the row.

Tests: eligible/refused matrix; path metacharacters; partially staged file keeps
index bytes, full staging status after restore; restore deleted worktree file;
refuse untracked/addition/rename/conflict/symlink/directory/traversal/.git;
other-file bytes preserved; stale status refused; coordinator dirty/lock/failure
cleanup; pins for confirm-only invocation and buffer/write integration. Existing
Git engine/status/branch tests and screen pins are CI regression gates.

## Implementation findings

Verification found two pre-existing defects that matter to this action:
- `change.x != ' '` misclassified `??` as staged. The row and VM now share
  `GitFileChange.isStaged`; untracked rows stage instead of attempting unstage.
- The porcelain parser trimmed leading filename spaces, split ordinary
  `a -> b` names as renames and left quoted UTF-8 octal bytes undecoded.
  Destructive actions must not normalize into another path. These are corrected
  and real-Git tests exercise status → exact path → discard. Paths containing
  the engine's `***` credential-redaction marker are refused conservatively.

The engine validates immediately before execution; there is no OS-wide lock
against a separate terminal process modifying the worktree/index between Git
commands. Do not run concurrent terminal Git mutations during confirmation.
The app's own open-editor writes are guarded until disk reconciliation finishes.
Unsaved buffers are refused rather than silently flushed or discarded.

## Local tests

134 passed / 0 failed (temporary Kotlin/JVM assertion harness): 19 new host
checks plus existing GitManager, branch manager/policy, status parser, errors
and annotation tests. The nine new real-Git cases use only isolated temporary
repositories, cover index preservation, deletion restore, exact unusual names,
new/renamed/staged-only refusals, path boundaries, merge stages/gitlinks and
an unborn repository. Local Unicode filesystem tests require C.UTF-8 (the
sandbox starts without a UTF-8 locale). Two actual EditorViewModel buffer tests
are written for Robolectric and passed in CI; no local Android build was claimed.

Additional boundary check: the engine verifies `git rev-parse --show-toplevel` matches the selected project. A repo with `core.worktree` pointing elsewhere is refused; a real-Git test asserts the external file remains untouched.


## CI verdict

`c43ede6` ✅ `36301773302`; boundary guard `bd55e70` ✅ `36301860116`.
Full host unit/screenshot task, Android compilation, debug/release builds and
artifact checks green. [Device checks](DEVICE_ROUND.md) pending; no PR/merge.
The previously approved meaning is preserved: restore FROM INDEX, never HEAD;
no new-file deletion and no staged changes removed.
