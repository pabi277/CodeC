# Level 3 — Proposed edits, review, and undo

**Status: ✅ Implemented & merged in [Phase 79](../../phases/03-editor/chat-phase79/README.md) (PR #105, `df2c5d4`, device-verified 2026-10-02).**

## User value

The agent can offer a fix or implement a small change across multiple project files, while the user remains able to inspect exactly what would change.

## Distinguish editor undo from agent-task rollback

Current main already has `ui/editor/EditorUndoManager.kt`: per-file/tab text history for ordinary editing. Do not present that as whole-agent-task undo. A project task may touch files that are not open in tabs, create/delete files, or survive process death. The future AI promise needs a task-level baseline/journal or isolated staging plus recovery semantics, and it must cooperate with (not silently reset) the existing tab history. See the [current-main source/data map](../../research/AI_INTEGRATION_RESEARCH_20260930.md#1-repository-recheck-facts-to-build-on).

## Change-set workflow

1. Agent returns a structured proposal tied to the current file versions.
2. CodeC validates every path stays inside the selected project and rejects invalid or stale edits.
3. Show a per-file unified diff, including new/deleted files and a summary.
4. User can accept or reject the complete change set (and, if safely supported, individual files).
5. Apply atomically as far as practical; report partial failures honestly.
6. Keep a task checkpoint so **Undo agent changes** restores the exact pre-apply file contents for that task.

This is the promised undo boundary: file edits made by that agent task. It does not reverse terminal effects, package installation, Git network actions, or changes the user made later. Those operations require separate approval and truthful messaging.

## Protect user edits

- Capture a baseline before applying; verify files have not changed since the proposal.
- If the user edits a target file while the agent is working, pause and ask to refresh/rebase/reject; do not overwrite.
- Keep editor dirty state, autosave, project tree refresh, and open tabs synchronized after acceptance and undo.
- Never treat model-produced prose as a patch. Parse/validate structured edits, then compute the diff locally.
- Do not stage/commit changes automatically. Git can be a later, separately approved feature.

## Rollback storage

Compare a per-task preimage journal or isolated staging copy against CodeC's project backup and storage rules. Store only the minimum needed, use app-private storage, define cleanup/retention, handle process death, and make undo failure visible. Avoid a global snapshot of all app data.

## Acceptance checks

- Wrong paths, path traversal, symlink escapes, malformed edits, and stale baselines are rejected.
- Rejecting a proposal leaves project files untouched.
- Accept then undo restores every changed file byte-for-byte for the captured task scope.
- User edits made after agent application are not silently destroyed by undo; offer a conflict decision.
- New/open files, dirty markers, preview/run state, and file tree refresh consistently.

## Research reference

Cline's checkpoint/undo flow and Aider's patch-oriented edits are useful references for reviewability. They are design references, not proof that either implementation fits CodeC's editor and filesystem lifecycle.
