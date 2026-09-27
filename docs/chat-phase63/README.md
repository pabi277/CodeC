# Phase 63 — verify Git, add safe per-file Discard

Research/owner decision: 2026-09-27. **BUILT, CI GREEN** on the session branch. Device round pending; no merge authorised.

## Reference and scope
Re-read all seven real phone shots (contact sheet); 124055 is the Repository
empty-state/Initialize action, not a populated change list. No screenshot
specifies discard semantics. The owner answered the explicit question:
**Discard unstaged changes only (recommended)** — restore the selected file
from the index, preserve staged edits, protect new files/renames/conflicts.
No second Git engine, rail redesign, or history-reverting action. Reject store
art, `0*.png` drawings, SPCK Account/shop and the withdrawn full-bottom-bar
removal. CodeC's own implementation only.

## Before-edit verification (paths under app/src/main/java/com/codeci/ide/)
- `ui/screens/GitControlView.kt:95`: existing Source Control sheet;
  `:390` conflicts/Mark Resolved, `:454-462` file diff and stage toggle;
  `:978-1078` existing typed file row. No Discard yet.
- `ui/projects/GitManager.kt:137`: status; `:158` HEAD diff blob; `:182`
  stage-all hygiene; `:193/:204` per-file stage/unstage; `:241` commit;
  `:274/:303/:390` push variants; `:489` pull; `:875` branch/stash flow.
- `ui/viewmodels/GitControlViewModel.kt:322`: refresh/readiness; existing
  commit/push/pull/resolve actions call the engine, not a terminal-only facade.
- `ui/screens/EditorScreen.kt:2643` and `ui/screens/FileManagerScreen.kt:1199`:
  both existing entry points share GitControlSheet.
- `ui/viewmodels/EditorViewModel.kt:375-391`: pending autosave; `:2277-2291`
  project write choke point; `:2359` active reload; `:2406-2480` branch reload
  touches ALL tabs and is therefore NOT appropriate for per-file Discard.

Plan and safety decisions: [PART_63_1_DISCARD.md](PART_63_1_DISCARD.md).
Gate: pure policy + real local Git integration tests + editor lifecycle/wiring
checks, CI green, then device verification. Phase 61 device checks still pending.

## Implementation / local verdict

Implemented policy, fresh engine validation + index-only checkout, confirmation
and blocking progress in existing sheet, and weak editor-VM registration with
per-root/path write guard + targeted buffer/undo reconciliation. Both existing
entry points use the same coordinator; no screen-local callback that would miss
an editor behind the Projects screen. No second Git engine or new dependency.

Local: **134 passed / 0 failed**, existing engine/branch/status/error tests plus
19 new host cases and annotation pin; strings XML parses; diff whitespace check
passes. Android compilation, full tests and the two real EditorViewModel buffer tests
subsequently passed in CI (below). Device verification not run.

Additional boundary check: the engine verifies `git rev-parse --show-toplevel` matches the selected project. A repo with `core.worktree` pointing elsewhere is refused; a real-Git test asserts the external file remains untouched.

## CI and live implementation — 2026-09-27

- Initial code `c43ede6`: ✅ **36301773302**.
- Additional worktree-boundary guard `bd55e70`: ✅ **36301860116**.
- Both runs passed the full host unit/screenshot task and debug/release APK
  builds and artifact checks, with no failed steps. Latest build/artifacts:
  https://github.com/pabi277/CodeC/actions/runs/36301860116
- 134/0 local JVM regression checks; actual editor-buffer integration exercised
  in CI, not claimed to compile in the sandbox. No live remote push/pull was
  performed for this phase; existing command/error tests are not GitHub auth
  validation.

Current anchors (under `app/src/main/java/com/codeci/ide/ui/`):
- `projects/GitManager.kt:209-247`: exact-path validation, real worktree check,
  index/staged/unstaged preflight, index-only restore;
  `:1241-1243/:1279`: byte-preserving status path parsing;
  `:1328`: one isStaged property fixes `??` stage-toggle classification.
- `projects/GitDiscardEditors.kt:20-54`: weak registration, dirty-buffer refusal,
  per-root/path write guard and finally-release + targeted reconciliation.
- `viewmodels/EditorViewModel.kt:372/:1006`: registration/lifetime;
  `:2288`: project-write guard; `:2363-2406`: selected buffer/undo reload only.
- `viewmodels/GitControlViewModel.kt:781-811`: busy guard, non-cancellable bounded
  disk operation, reconciliation in finally, existing refresh/error reporting.
- `screens/GitControlView.kt:465`: eligible row action; `:675-704`: named-file
  confirmation and non-dismissable in-flight progress. Cancel does not invoke
  the engine.

[Device checks G1–G13](DEVICE_ROUND.md) **NOT RUN**. No extra rail entry point:
the existing Repository → Source control route is retained. The optional
rebuild suggested by the original spec is explicitly not this phase.

Phases 59–63 are now built on the session branch (62 was first by request).
Next is device acceptance/review, including Phase 61's pending checks; no new
feature phase is assumed. Stop at the merge gate: no PR or merge authorised.
