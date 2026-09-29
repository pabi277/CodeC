# Phase 73.7 — Git moves into the editor panel (Spck-ditto dialogs, no sheets)

**Status: 🚧 IMPLEMENTED (2026-09-29), CI PENDING; session branch
`arena/01a0eca9-codec`. Owner device pass owed, not merged (rule.md §3).
Not one of the numbered UI-polish discussion drafts (65.1/74.1 remain
untouched) — this is a rule.md §4 lifecycle item from the owner's own
follow-up on the 73.5/73.6 builds, superseding the 73.6 drawer slot it
replaces (see below).**

## The owner's follow-up (2026-09-29, 8 Spck screenshots attached)

After 73.5/73.6 the owner sent eight Spck screenshots (branch menu, Git
Credentials, Remotes list, New Remote, detach-HEAD checkout confirm,
Commit All, empty Remotes, Push) and asked for the ditto copy: git must
live **inside the editor's side panel** (the REPOSITORY tab) the way
Spck's does — no bottom sheet anywhere — with Spck's Commit All and Push
dialogs, Spck's centered Remotes/Log dialogs, a count badge on the rail's
branch icon, and the push menu's Provider row opening Git Credentials.
Publish (the permission-asked direct-create path) and the manual
paste-a-URL flow both stay. Four scope questions were asked before
coding; the owner locked all four: (1) **full-panel** (the whole git
surface in the drawer; the hub ⋮ opens the editor with the panel
selected); (2) **ditto-dialogs** (new Commit All + Push dialogs; the
inline commit box and COMMIT & PUSH button deleted); (3) **all-dialogs**
(Remotes and Log History become centered dialogs; Branch Switch already
was one); (4) **provider-row** (push menu's Provider → Git Credentials;
Publish stays the permission-asked path).

## What shipped

- **The panel, not the sheet** (`GitControlSheet` →
  `GitControlPanel`): the `ModalBottomSheet` wrapper and its
  `onDismiss` are deleted; the drawer hosts the scrolling Column
  directly through a new `repositoryContent` slot (like the Files
  slot's tree). The 73.6 drawer slot UI (`RepositoryPanelState`,
  `RepositorySlot`, the drawer engine, the sheet door) is deleted with
  it — git has one home now. The drawer's Source Control row and the
  hub ⋮ both select the Repository slot (the hub routes via a new
  optional `panel=repository` editor-route arg, applied once per
  session, PROJECT opens only).
- **Spck's Commit All dialog** (new `GitCommitDialog.kt`): credentials
  row, message, author name + email (a third editor on the shared
  `GitCredentialsStore` — prefill on open, save back when changed), the
  Stage All toggle defaulting ON, Cancel/Ok. Ok stages (when on) and
  commits via `commitOnly(..., stageAll)`; Stage-OFF with an empty
  index fails in git's own plain "nothing to commit" words.
- **Spck's Push dialog** (new `GitPushDialog.kt`): Remotes + Branches
  dropdowns (house read-only-field shape), credentials row, Cancel/Ok.
  `pushCapturing` gained `remoteName?` (blank keeps the first remote);
  the ViewModel's `push(remote?, branch?)` defaults to today's
  behaviour. No remote at all offers the Remotes screen, not a dead Ok.
- **Centered Remotes + Log dialogs** (`GitRemotesSheet` →
  `GitRemotesDialog` with search, per-row copy-URL/delete menu, and a
  New Remote dialog; `GitLogSheet` → `GitLogDialog`; `BranchSwitchSheet`
  → `BranchSwitchDialog`, content untouched). Branch/load/validation
  logic is unchanged — only the chrome moved.
- **Rail count badge** (`repositoryBadgeCount = gitChangeCount` on the
  Repository glyph only; zero draws none). 11 dead strings deleted
  (the commit box/button set + the drawer slot set); 13 added.

## The stray `projects/.git`

Unchanged from 73.6: still owned by one Terminal command from the
default prompt (`rm -rf .git`) — nothing in the app auto-deletes it.
The 73.6 drawer path that created it no longer exists.

## Tests

- New `GitPanelWiringTest.kt` (8 cases) replaces
  `GitDrawerInstallWiringTest.kt`: slot hosting + badge, footer/route
  selection, no-`ModalBottomSheet` anywhere in git, hub→editor routing,
  push/commit dialog contracts, Remotes/Log dialog conversion, 13 new
  strings present + 11 dead strings absent.
- `GitGuiParityWiringTest.kt`: dialog-state renames
  (`showBranchDialog`, `showRemotesDialog`, `logDialogMode`,
  `GitLogDialogMode`); Commit All/Push pins assert the dialog opens,
  not a direct ViewModel call.
- `GitSpckPanelWiringTest.kt`: Manage lost its sheet dismiss (now
  asserted absent); Commit All slice gained the `stageAll`-conditional
  pin. `GitInstallWiringTest.kt` survives untouched (verified: all 33
  anchors present, both negative pins absent).
- `BackRouterRootTest` / `ExitPromptPolicyTest` route pins gained
  `&panel={panel}`; `GitDiscardWiringTest`'s engine-delegation pin
  rides `commitOnly` now; `StageAllHygieneTest`'s message names
  `commitOnly`. `GitRefreshStateWiringTest` untouched.

## Evidence

No JVM/kotlinc in the sandbox — verified with the Kotlin
state-machine brace/paren scan (every touched file balanced) + a
Python mirror of every new/changed test assertion (all pass) + a
whole-suite stale-pin sweep (only the new test's own intentional
`assertFalse` pins match). Compile-risk review: every dialog call site
re-checked against the real dialog signatures (caught and fixed a
`onCommit`/`onPush`/loading-params mismatch before commit), every new
`R.string` cross-checked against `strings.xml`, and the full import
surface re-audited after the deletions. CI result to be stamped here
on green.
