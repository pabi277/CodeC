# Phase 69.4 — the Files tree remembers its shape, and the route opens once

**Date:** 2026-09-28. **Branch:** `arena/01a0e49f-codec` (tip `e7e420e`).
**Status:** implemented; **Android CI ✅ GREEN** (run 36386089320, 10m12s, both
APKs: debug 26,027,944 B / release 6,798,904 B).
**No PR opened, nothing merged** — that needs the owner's explicit word.

Read first, asked second, coded third. Both reports arrived in one message after
the 69.3 delivery; both were read on this checkout before anything was proposed,
and the owner's two answers were taken **before** any code was written.

## The two reports (owner, verbatim)

1. **The Files tree** — *"When i import a zip or repository and open in editor it
   will in collapse state and remember what open by the use when leaving and
   again open the editor and the project in the same position no all expend or
   collapse"*.
2. **Coming back from a run** — *"If i run a file but it is not in 1st of the
   editor and back from preview it again opens the 1st file on the editor not the
   file i opened"*.

Both are the same kind of failure: the editor forgets where the user was. Neither
is a look problem, and neither is fixed by a setting.

## What the investigation actually found

### 1. The tree: the shape lived in the session, and every switch erased it

- `EditorViewModel._collapsedDirs` (`EditorViewModel.kt:415`) is a `StateFlow`
  in the ViewModel and **nothing else** — no storage, no restore, no per-project
  key. Whatever the user opened lived exactly as long as that one session.
- Worse, three places reset it to `emptySet()` on leaving a project —
  `openProject` (`:1428`), the single-files mode flip (`:1522`) and
  `switchContext` (`:2015`). In this code the **empty set means "every folder
  expanded"** (`FileTreeCollapse.visible` hides what is in the set), so each of
  those resets is "throw the shape away and open everything".
- That is the report read literally: a ZIP or a clone is a wall of folders on a
  phone (everything expanded, `src/`, `node_modules/`, `.git/`-visible trees and
  all), no project starts closed, and coming back after leaving the editor starts
  from that wall again.
- There is no preference, no database and no config involved anywhere else in the
  drawer — the only existing "remember where I was" store is
  `EditorLaunchState` (the launch default, "open where I left off"), which is a
  tiny `SharedPreferences` file. 69.4 mirrors it rather than inventing a
  mechanism.

### 2. Coming back from the Preview: the route re-opened the file it was entered with

- `EditorScreen.kt` opened the route's file in
  `LaunchedEffect(projectName, fileName, singleFile)` (now `:320`). A
  `LaunchedEffect` re-runs whenever the composable is re-composed with a new key
  **and also when it re-enters the composition** — which is what happens when the
  Web Preview, the Terminal or Settings is popped and the editor is shown again.
- The route carries the file the editor was **entered** with
  (`editor?projectName=…&fileName=…`); **opening a file from the drawer does not
  navigate**, it only switches tab. So the route kept naming the first file while
  the user was on another tab, and every return from the Preview re-activated the
  route's file: that is the *"1st file on the editor"* of the report.
- It also reset that file's caret and re-pointed `EditorLaunchState` at it, so
  "open where I left off" was quietly re-aimed at the first tab on every return.

## Owner decisions (verbatim)

| # | Question | Owner's answer |
|---|---|---|
| Q1 | A project opened for the **first** time — what should the tree look like? | **A — Every folder closed, only the top level listed** — a never-opened project shows just the top level; nothing is expanded for the user. *(Option B, "open only the path of the file that opens", was offered and not chosen.)* |
| Q2 | How long should the shape be remembered? | **A — Remembered per project, and it survives closing the app (recommended)** — each project keeps its own open/closed list on the device; folders that no longer exist are dropped automatically. *(Session-only memory not chosen.)* |

## What changed

### 1. The tree's shape belongs to the project

- **`ui/editor/FileTreeMemory.kt`** (new) — one `SharedPreferences` file,
  `codec_file_tree`, one key per project (`"collapsed␟" + projectName`):
  `save` / `load` / `forget`. A project that has never been remembered reads back
  as `null`, which is deliberately **not** the same as an empty set (empty =
  "the user opened every folder"). Every read and write is `runCatching`ed, so a
  storage failure can never take the editor down.
- **`ui/editor/FileTreeCollapse.kt`** — the pure half, extended:
  `encode`/`decode` (sorted, so the same tree writes the same bytes),
  `initialTree(allDirs, remembered) = remembered ?: allDirs` (Q1 = A: first open
  closes every folder) and `prune(collapsed, existingDirs)` (folders that were
  renamed, moved or deleted drop out of what is remembered).
- **`EditorViewModel.kt`** — `refreshFileEntries` is the one place that knows
  both the project and its directories, so it now ends with
  `applyTreeStateFor(project, entries, …)` (`:1932`): first open per project
  loads and applies the remembered shape, a re-listing of the *same* project only
  prunes. Five `rememberTreeState()` calls (`:2196`, `:2202`, `:2207`, `:2219`,
  `:2335`) — the chevron, Collapse all, Expand all, the reveal of the active
  file, and a rename's path remap — are the only writers, i.e. **only what the
  user did is remembered**. The three project-switch resets are deliberately left
  alone: the tree being left keeps its own shape, and the new project's shape is
  loaded by the refresh that follows.
- **`FileManagerViewModel.kt:442`** — deleting a project from the hub calls
  `FileTreeMemory.forget`, so the store cannot grow by one entry per project the
  user ever opened.
- **The default never overwrites a shape the user already chose** (CI round 2,
  for cause). Creating a nested file reveals its new parent folders
  (`expandAncestors`) *before* the drawer has ever listed that project, so the
  listing that followed applied "everything closed" over the reveal and shut the
  folder a file had just been created in. `treeStateTouched` is set by every
  user-driven change — it counts even when there is nothing to write to yet — so
  a first open closes every folder **only** when the tree is untouched. The three
  project-switch resets share `resetTreeShapeForNewContext`, which clears the
  flag: the project being entered gets its own first-open default instead of
  inheriting "everything expanded" from the one being left.

### 2. A route is opened once per editor session

- **`ui/editor/EditorRouteOpen.kt`** (new) — pure, two lines of policy:
  `key(projectName, fileName, singleFile)` (NUL-separated, so a scratch route is
  not a project route and the peek flag is part of the route) and
  `shouldOpen(routeKey, openedRouteKey) = openedRouteKey != routeKey`.
- **`EditorScreen.kt:319`** — the effect now keys on that one
  `editorRouteKey` and asks `shouldOpen` before it opens anything, then records
  the route through `viewModel.markRouteOpened`.
- **`EditorViewModel.kt:429`** — the session marker `openedRouteKey` lives in the
  ViewModel, which is what makes it the right scope: it survives a rotation (a
  `remember` in the screen would not) and it dies with the tabs (a saved-state
  flag would not), so a new back-stack entry, a rename, a deep link or a cold
  start still opens the route's file exactly as before.

## Tests

`FileTreeCollapseTest` +4 (round trip through storage, never-stored ≠
all-expanded, first open closes everything, pruning) · `EditorRouteOpenTest`
(new, 4: a fresh session opens, the same route does not open twice, a different
route still does, nulls kept apart) · `FileTreeStateWiringTest` (new, 5: the
shape is stored per project, exactly the five user-driven changes write it and
the project-switch resets do not, the first-open shape comes from the pure
policy, **the default never overwrites what the user already did**, a deleted
project forgets) · `EditorRouteWiringTest` (new, 3: both opens sit behind the
guard, one guard in one place, the marker lives in the ViewModel with one
writer). **Sixteen new host cases.**

## Validation

| What | Where | Result |
|---|---|---|
| Source tests (host JVM) | CI `:app:testDebugUnitTest` on `e7e420e` | ✅ run 36386089320 — 2306 tests, 0 failed |
| Android tests (instrumented) | — | none for this part; nothing claimed |
| Device evidence | — | **not claimed** — no round run, none asked for |

What only a handset can confirm (recorded, not requested): that the tree really
comes back in the same position after leaving the editor **and** after closing
the app, and that coming back from the Web Preview keeps the file the user
opened rather than the file the editor was entered with. Both are the owner's
device to make.

### CI

- **Round 1 — red, this part's own omission**, run
  [36383822168](https://github.com/pabi277/CodeC/actions/runs/36383822168) on
  `3bc55d8`: four `Unresolved reference 'FileTreeMemory'` in
  `EditorViewModel` — the store lives in `ui.editor` and the viewmodels package
  imports its siblings explicitly. Main code, one import line, no behaviour
  change (`1c61f42`).
- **Round 2 — red for cause**, run
  [36384352636](https://github.com/pabi277/CodeC/actions/runs/36384352636) on
  `1c61f42`: 2305 tests, 2 failed. (a) `DrawerFileActionsTest > nested create
  opens exact path and expands its ancestors` — a real conflict, described
  above: the first-open default was closing the folder a file had just been
  created in. (b) `FileTreeCollapseTest > folders that no longer exist drop out
  of what is remembered` — **my expectation was wrong**, not the code: `prune`
  keeps the folders that still exist, so the answer is `{src, src/img}`. Fixed
  in `dbbe884` (the `treeStateTouched` rule + the corrected expectation).
- **Round 3 — red, one stale pin**, run
  [36385643910](https://github.com/pabi277/CodeC/actions/runs/36385643910) on
  `dbbe884`: 2306 tests, 1 failed — `FileTreeStateWiringTest > a project listed
  for the first time starts with every folder closed` still looked for
  `initialTree(dirs, remembered)` after round 2 had moved the call to
  `initialTree(dirs, null)`. **The Robolectric case round 2 was about passed**,
  so the behaviour fix stood; only the pin moved (`e7e420e`).
- **Round 4 — tip green**, run
  [36386089320](https://github.com/pabi277/CodeC/actions/runs/36386089320) on
  `e7e420e`, 10m12s: the full suite plus this part's 16 new cases, both APKs
  built (debug 26,027,944 B / release 6,798,904 B).

This sandbox has no JDK (`which java javac kotlinc` → empty, `JAVA_HOME` unset),
so **CI is the executor of record**, as it was for 68.1, 69.1, 69.2 and 69.3.
Every literal the new wiring pins assert was re-read against the live sources and
reproduced by hand before the push (that simulation is how the one wrong
assertion was caught: `rememberTreeState()` counted 6 because the plain substring
also matched the declaration). That is a pin check, not a compile — the compile
is CI's. Round 2 is what a hand simulation cannot see: `DrawerFileActionsTest`
(a Robolectric test that predates this part) caught a real conflict between the
first-open default and a reveal that lands before the drawer's first listing.

## Boundaries

No new dependency, permission, screen, row, button or setting. One small
`SharedPreferences` file (`codec_file_tree`), deliberately mirroring the existing
`EditorLaunchState` store; no telemetry, no analytics, no new preference the user
can see. The drawer's look, its rows, its toolbar and its search are untouched;
the collapse *filter* itself (`FileTreeCollapse.visible`) is unchanged; the
drawer-close law (47.1), back precedence (49.1), the launch default and the
project switch's own resets are untouched. The editor's other re-composition
effects, the tab bar, the bottom bar and the Preview screen are untouched.

**Stop point:** this part only. No PR, no merge, no `main` push without the
owner's explicit instruction.
