# CodeC Phase 47.1 — A drawer you can always close, with the project list inside it

> **Status:** 📋 PLANNED · **Cost:** `[client-only]` · **Effort:** M ·
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
