# CodeC Phase 47.1 — A drawer you can always close, with the project list inside it

> **Status:** ✅ COMPLETE & MERGED via PR #78 (2026-09-13; device rounds 1-2 owner-approved "All working"; implemented 2026-09-12, `arena/01a097b5-codec`; CI =
> executor of record, device round pending) · **Cost:** `[client-only]` ·
> **Effort:** M ·
> **Owner rows:** *"the file ber can't close without opening any file"* ·
> *"I can switch project but can't directly open folder"* → owner's chosen shape
> (2026-09-12): **in-drawer project picker**.

## Symptom, in code

- **No close affordance.** `EditorProjectDrawer`'s header row is
  `clickable(onClick = onSwitchProject)` (`EditorProjectDrawer.kt:96-106`); the
  only other exits are a file row (`EditorScreen.kt:958-966`) or the scrim. The
  ☰ that opened the drawer is behind the scrim (`:1055-1064`), and edge-swipe is
  deliberately off whenever a file is open
  (`gesturesEnabled = activeTabPath == null && currentFileName.isEmpty()`,
  `:905-907` — a Phase 25.2 fix for gutter-scroll conflicts that must **not** be
  reverted).
- **The switcher lies.** `showContextPicker` (`EditorScreen.kt:1916-1973`) is
  titled `"Open folder"` and says *"The editor works inside one folder at a
  time"*, but lists **Single files + CodeC projects**. After Phase 46.1 there is
  no folder picker anywhere in the app, so the title is not just confusing, it
  is false.

## Design

### 1. An explicit ✕ (and the three close paths that must agree)

A 38 dp `IconButton(Icons.Default.Close)` at the end of the drawer header row,
mirroring the source-control glyph on the same row (same size/clip, so the
header stays balanced). Tapping it calls `drawerState.close()` and **nothing
else** — no dialog, no navigation, no autosave flush (the existing
`DisposableEffect { onDispose { viewModel.flushAutoSave() } }` at
`EditorScreen.kt:574-576` already covers leaving the screen).

All four close paths route through one callback so they cannot drift:

```kotlin
// DrawerPolicy (pure, host-tested)
enum class DrawerCloseReason { CLOSE_BUTTON, SCRIM, BACK, FILE_OPENED, PROJECT_SWITCHED }
object DrawerPolicy {
    fun shouldClose(reason: DrawerCloseReason, drawerOpen: Boolean): Boolean
    /** A project switch is the one close that also changes context. */
    fun closesAndSwitches(reason: DrawerCloseReason): Boolean
}
```

**Back is deliberately *not* implemented here.** Phase 49's `BackRouter` owns
every back decision in the app (precedence: unsaved → drawer → hub tree →
sheets → route pop → exit prompt), and 47.1 only guarantees the *visible*
affordances. If 47.1 ships before 49, it adds the one-line
`BackHandler(enabled = drawerState.isOpen) { scope.launch { drawerState.close() } }`
**inside** `EditorScreen`, and 49 later folds it into the router — recorded here
so the interim state is intentional, not an accident.

### 2. The project list, in the drawer

A collapsible **PROJECTS** section between the header and the tree toolbar
(`EditorProjectDrawer.kt:181-215` is where the toolbar starts, so the section
slots in above it):

```text
▸ PROJECTS                                   ← tap to expand/collapse
   ● myapp                                   ← current context, marked
     Single files
     playground
     school-c
   + New project…                            ← jumps to the Projects tab's create sheet
```

- **Content:** `"Single files"` first (the null context), then
  `ProjectManager.listProjects()` alphabetically — the exact list the retiring
  dialog builds (`EditorScreen.kt:1917-1919`), so nothing about which contexts
  exist changes.
- **Action:** tapping a row calls the **same** `viewModel.switchContext(context,
  name)` + `onProjectSelected(project)` pair the dialog uses today
  (`:1941-1948`), then closes the drawer. One code path, two entry points — the
  header's project name now *expands the list* instead of opening the dialog.
- **State:** the expanded flag is local (`remember`), reset when the drawer
  closes; the current context is marked with the same `●` the dialog used.
- **Empty case:** the dialog's existing copy survives verbatim (*"No projects
  yet — create one in the Projects tab, or keep working with single files
  here."*) — it is good copy and it is already written.
- **`+ New project…`** navigates to the Projects tab (`onOpenProjects` callback,
  new) and opens the existing `showHubSheet`. It does **not** create a project
  from the drawer: project creation has a wizard (type/template) and duplicating
  it here would create two truths.

### 3. Retiring the dialog

`showContextPicker` (`:342`, `:917-922`, `:1916-1973`) is deleted with the
`"Open folder"` title. `grep -rn '"Open folder"' app/src` must return 0 hits
after this part — that string is the owner's complaint in one line, and a
source-scan test pins it.

### 4. What the drawer must NOT become

- Not a second file manager: no rename/delete/move of *projects* here (the hub
  owns that).
- Not a folder browser: nothing in this list touches SAF (Phase 46.1's law).
- Not lazy-loaded from disk on every recomposition: `listProjects()` is read
  once when the section expands, exactly as the dialog read it once in
  `remember`.

## Exit condition

```text
1. ☰ opens the drawer; ✕ closes it and does nothing else (no dialog, no nav).
2. Scrim tap closes it. Back closes it (interim handler or 49's router).
3. Tapping a file still opens the file and closes the drawer.
4. PROJECTS expands to "Single files" + every project, current one marked.
5. Tapping a project switches context, closes the drawer, and the tree + git
   badges are that project's.
6. Tapping "Single files" gives the scratch context exactly as the old dialog did.
7. "+ New project…" lands on the Projects tab with the create sheet open.
8. No dialog anywhere is titled "Open folder" (grep = 0 + device check).
9. With a file open and the keyboard up, opening and closing the drawer does not
   lose the buffer or the caret.
PASS = all nine.
```

## Tests (plan)

- `DrawerPolicyTest`: `shouldClose` for every reason; `closesAndSwitches` true
  only for `PROJECT_SWITCHED`; a close when already closed is a no-op (no
  duplicate `close()` calls — the "double animation" bug class).
- `DrawerProjectListTest`: list order (Single files → alphabetical), the current
  marker, the empty-state copy string, and that a project deleted from disk is
  not listed (stale-list guard).
- Source-scan test: `app/src/main` contains no `"Open folder"` string and no
  `showContextPicker` identifier.
- Robolectric `DrawerSwitchContextTest`: a project tap calls `switchContext`
  exactly once with the right name, and `onProjectSelected` exactly once (the
  drift guard between the two entry points).

## Sources (record)

- CodeC 2026-09-12: `EditorProjectDrawer.kt:96-215`, `EditorScreen.kt:342,
  574-576,900-1010,905-907,917-922,958-966,1055-1064,1916-1973`,
  `EditorViewModel.switchContext`, `ProjectManager.listProjects`.
- Phase 25.2 device round 3 (the `gesturesEnabled` comment at `:902-904`) — the
  reason edge-swipe stays off with a file open, and therefore why a visible ✕ is
  mandatory rather than decorative.
- `docs/PHASE44_50_UX_RESEARCH.md` §6 (back precedence; 49 owns the policy).

## Deferred / rejected with reasons

- **Re-enabling edge-swipe with a file open** — that is the Phase 25.2 gutter
  bug returning; the ✕ is the fix, not the gesture.
- **A `DismissibleNavigationDrawer` / swipe-anywhere drawer** — same conflict
  class, plus it changes the visual language of the Spck-derived chrome.
- **Keeping the dialog *and* adding the list** — two switchers, two behaviours,
  and the false title survives.
- **Showing per-project git badges in the list** — cost (a `git status` per
  project on expand) for decoration; the hub already shows them.

## Implementation (2026-09-12)

**New pure code (host-tested):** `ui/editor/DrawerPolicy.kt` —
`DrawerCloseReason { CLOSE_BUTTON, SCRIM, BACK, FILE_OPENED, PROJECT_SWITCHED }`
+ `DrawerPolicy.shouldClose` (an open drawer closes for every reason; a closed
drawer no-ops — the double-animation class) + `closesAndSwitches` (only
`PROJECT_SWITCHED`), and `DrawerProjectList.build` (Single files first with
the null context, then projects alphabetically case-insensitive, the current
context marked; `EMPTY_PROJECTS_COPY` keeps the retired dialog's copy
verbatim).

**The edge:**
- `EditorProjectDrawer`: the header row gains a 38 dp ✕ (`onClose`) mirroring
  the source-control glyph; the header's tap now EXPANDS the PROJECTS section
  instead of opening the dialog (the GuideAnchor stays on the header, so tour
  beat 2 = one tap that expands — the anchor click is the same lambda); the
  section (between the header and the tree toolbar) renders the rows, the ●
  marker, the empty copy and `＋ New project…`; `showProjectTree = false`
  (46.2's SINGLE_FILE) hides the toolbar/tree/git rows and shows a one-line
  hint instead — PROJECTS + Guide remain.
- `EditorScreen`: ONE `closeDrawer(reason)` callback over `DrawerPolicy` for
  everything the app controls (✕ = CLOSE_BUTTON and nothing else; a file row
  = FILE_OPENED; a pick = PROJECT_SWITCHED). The scrim stays Material3's own
  affordance (same result; the enum keeps SCRIM so the matrix is complete).
  Back: the interim `BackHandler(enabled = drawerState.isOpen)` registered
  BEFORE the unsaved-changes handler, so 49's precedence (unsaved → drawer)
  already holds — 49 later folds it into the router, exactly as the spec
  records. The list is read ONCE per expansion in `remember` (the dialog's
  old shape), and the expansion flag resets when the drawer closes.
- The pick calls the SAME pair the dialog ran — `onProjectSelected(project)`
  + `viewModel.switchContext(context, contextName)` — then closes the drawer,
  EXCEPT while `tourWaitsInDrawer` (45.2 round 3): the tour's next beat lives
  in the drawer, so it stays open (the old close-then-reopen dance, replaced
  because there is nothing to reopen). `GuideWiringTest`'s two picker pins
  were updated in the same commit and now pin the picker GONE.
- `＋ New project…` → `onOpenProjects()` → the Projects tab with the hub's `+`
  sheet already up: `Screen.FileManager` grew the optional `openSheet` arg
  (`createRoute(openAddSheet)`), the bottom bar was taught to navigate the
  PLAIN route (never the literal pattern), and `FileManagerScreen` consumes
  the flag in a `LaunchedEffect`. Project creation keeps its wizard in the
  hub — the drawer never creates projects.
- `grep -rn '"Open folder"' app/src` → **0 hits** (comments included — they
  say "Open-folder" now); pinned by `DrawerWiringTest` along with: the ✕ and
  its CLOSE_BUTTON wiring, the BACK handler, the PROJECTS callbacks, the
  switchContext one-code-path pin, and the openSheet route shape.

**Deviations:**
1. The tour stay-open replaces close-then-reopen (above); the two affected
   `GuideWiringTest` pins were updated with their reasoning, not deleted.
2. The `+ New project…` hand-off needed a route argument — the spec's
   "onOpenProjects callback, new" made concrete as `openSheet=1` (an optional
   arg on the existing destination, so deep links/state survive).
3. The peek's drawer (46.2) reuses this section — one switcher everywhere,
   which is why 46.2 and 47.1 landed in the same push.

**Exit condition status:** 1-9 are the device round (owner); the automated
halves (the ✕/BACK/scrim wiring pins, the list order/marker/empty-copy pins,
the grep pins, the switchContext drift guard) run in CI.


## Device round 1 (2026-09-13) — the New-project hand-off was broken

**Owner report:** *"I could not create new project from the editor when i click
the option of new project it just close and open editor."*

**Root cause — the Phase 44 round-1 lesson repeating:** the `＋ New project…`
navigation used `restoreState = true`. That flag restores the WHOLE saved
sub-stack for the destination, not a fresh screen: the restored sub-stack's
top could be an **editor** entry (the owner saw the drawer close and the
editor "just open" again), or a plain `file_manager` entry whose ORIGINAL
arguments carry no `openSheet` — so no sheet either. A `rememberSaveable`
consumed-flag then kept it closed after the first arrival.

**Fix:** the row is an INSTRUCTION ("show me the Projects tab with the create
sheet up"), so it is `restoreState = false` — popUpTo(start){saveState} still
saves the editor for the tab's return, but the destination is a fresh
Projects instance whose `openSheet=1` opens the sheet. Pinned by
`DrawerWiringTest.the New-project hand-off is an instruction - it restores
nothing` (slices the wiring window: `createRoute(openAddSheet = true)` +
`restoreState = false`, and the broken flag gone).

**Device-round fix CI ✅ GREEN: run `34736668771` on tip `ce4044d` — `conclusion: success`, assemble + `testDebugUnitTest` + `lintDebug`, artifacts `CodeC-IDE-release` / `CodeC-IDE-debug` (the owner's re-round build).**


## Device round 2 (2026-09-13) — a project pick must never close the drawer

**Owner report (guide beat 2):** *"when i click the project to select
demo_flask it closes the pop up of the file selection option it should drop
down all the available projects."*

**Root cause:** the pick ran `closeDrawer(PROJECT_SWITCHED)` UNLESS a
seen-set guard (`CoachMarkPlan.nextBeatIsInDrawer` → `tourWaitsInDrawer`)
predicted that the guided tour was waiting on a beat inside the drawer
(45.2 round 3's close-then-stay compromise). A prediction is exactly as good
as its inputs: on the owner's phone it said "no tour waiting" mid-tour, the
drawer closed, and the tour went silent between its own beats — the exact
failure the guard existed to prevent.

**Fix — the unconditional law (supersedes the 45.2 round-3 guard):** a
project pick is not a close at all. It switches the context BEHIND the
drawer: the list stays dropped-down with the new project ●-marked, the tree
refreshes under it, and the tour's drawer beats are unreachable-to-break by
construction — for everybody, tour or no tour. Removed with it:
`tourWaitsInDrawer` (EditorScreen param + host arg),
`CoachMarkPlan.nextBeatIsInDrawer` (pure, its only consumer gone),
`DrawerPolicy.closesAndSwitches` (a pick no longer closes, so "the close
that also switches" no longer exists), and `DrawerPolicy.shouldClose` now
answers `drawerOpen && reason != PROJECT_SWITCHED`.

**Tests:** `DrawerPolicyTest` rewritten to the new matrix (a pick never
closes — open or closed; the other three reasons still close; closed-drawer
no-op kept) · `GuideWiringTest` pick pin rewritten (switchContext stays the
one code path; NO close call after the switch; the guard and its predictor
pinned gone) · the `nextBeatIsInDrawer` CoachMarkPlanTest case removed with
its function. `step.inDrawer` stays (nextStep/remaining still use it).

**Device-round-2 fix CI ✅ GREEN: run `34737610972` — `conclusion: success`, assemble + `testDebugUnitTest` + `lintDebug` (the owner's re-round build).**

## Test log (Phase 50 — the cross-device matrix)

> The 6 rows of the cross-device matrix that this part owns ([`../chat-phase50/DEVICE_MATRIX.md`](../chat-phase50/DEVICE_MATRIX.md)). `⏳` = not run. Paste the tester's result into `Result` (✅, or ❌ + the text the screen really showed, verbatim) and the device identity line — the first line of Settings → Feedback & Support → COPY REPORT — into `Evidence`, one line per device class that ran it. `DeviceMatrixTest` pins this table both ways: every row here must exist in the matrix with `47.1` in its `Part` column, and every matrix row owned by this part must be listed here. That is Phase 50's exit 4, enforced by CI instead of by memory.

| Row | Result | Evidence (device · OS · nav mode · what was seen) |
|---|---|---|
| G1 | ⏳ | |
| G2 | ⏳ | |
| G3 | ⏳ | |
| G4 | ⏳ | |
| G5 | ⏳ | |
| G6 | ⏳ | |

**Already on record:** ✅ **device-passed at round level** (PR #78,
2026-09-13), with two fixes that came *out of* the device rounds: the `＋ New
project…` hand-off (G5) and *"a pick never closes the drawer"* (G4). G3/G6 on a
wide layout are new.

