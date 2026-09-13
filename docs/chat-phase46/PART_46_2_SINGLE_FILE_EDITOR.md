# CodeC Phase 46.2 — One file is one file; the project is an explicit action

> **Status:** ✅ COMPLETE & MERGED via PR #78 (2026-09-13; device rounds 1-2 owner-approved "All working"; implemented 2026-09-12, `arena/01a097b5-codec`, owner:
> "Start phase 46 and 47"; CI = executor of record, device round pending) ·
> **Cost:** `[client-only]` · **Effort:** M ·
> **Owner row (verbatim):** *"file single click to open in a editor screen with
> real path and same file edit but not full project to editor. To open a full
> project in editor the 3 dot will have the option to open in editor"*

## Symptom

`FileManagerScreen.kt:520-523` — a single tap on a file in the hub's project tree
does `onProjectSelected(activeProject!!)` **and**
`onProjectFileSelected(activeProject!!.name, path)`, which lands on
`Screen.Editor.createRoute(path, projectName)` (`MainActivity.kt:990-995`).
That route loads the **whole project** into the editor: the drawer tree, git
metadata, launch-default markers, run-target detection and
`EditorLaunchState.save` (`EditorViewModel.kt:1273`). For a user who wants to
change one line in one file, that is a heavy door — and the app has no light
one, and no explicit heavy one (the card overflow, `FileManagerScreen.kt:1360-1407`,
has no "Open in editor").

## Design

### 1. `EditorOpenMode` — one enum, three behaviours

```kotlin
enum class EditorOpenMode { PROJECT, SINGLE_FILE, SCRATCH }
```

| Concern | `PROJECT` | `SINGLE_FILE` | `SCRATCH` |
|---|---|---|---|
| Buffer source / save target | project file | **the same project file** | single-files folder |
| Status bar path | relative path | **real path** (`~proj/<p>/<rel>`) | file name |
| Drawer tree, git badges, branch chip | yes | **no** | no |
| RUN ▶ | project/file run | **that file's own run** | that file's own run |
| `EditorLaunchState.save` | yes | **no** | no |
| Tabs | project tab list | **one tab** | one tab |
| Autosave / undo / find / completions | yes | **yes, identical** | yes |

Everything in the "identical" column matters: the owner asked for *"same file
edit"* — a single-file open is not a read-only viewer and not a degraded editor.
Syntax highlighting, ghost completions, the keys/strip, autosave and undo all
keep working because they key off the buffer and the file's language
(`LanguageType.fromFileName`, `EditorScreen.kt:373-376`), not off the project.

### 2. Route shape (no new destination)

`Screen.Editor`'s route today is `editor?projectName={projectName}&fileName={fileName}`
(`ui/navigation/Screen.kt`). Add **one** nullable flag, `single={single}`
(`"1"` / absent), defaulting to absent so every existing caller —
`EditorLaunchState` at `MainActivity.kt:706-717`, the "Open with CodeC" bridge at
`:724-733`, `TemplatesScreen`'s `onUseTemplate`, the editor's own
`onFileRenamed` — keeps producing `PROJECT` mode unchanged. That is the whole
compatibility story: **new behaviour is opt-in by the flag, not by a rewrite.**

`EditorViewModel.openFile(context, projectName, fileName)` (`:1229`) gains the
mode and dispatches to a new `openSingleProjectFile(...)` that reuses
`openProjectFile`'s read path (`ProjectPathUtils.resolveInside`, `LineEndings`,
`EditorTab`) and skips exactly four things: `_projectName` stays null for chrome
purposes (a separate `chromeProjectName` keeps the save path honest), no
`refreshFileEntries`, no `refreshGitMeta`, no `EditorLaunchState.save`.

> **Design note (the subtle part, and the reason for the test):** rather than
> null-ing `_projectName` (which `saveFile` uses to resolve the root), the mode
> is carried as its own state and every *chrome* consumer reads
> `EditorOpenMode.showsProjectChrome(mode)`. Null-ing the project would silently
> turn a save into a scratch save — the exact bug class Phase 22.5's "the active
> tab's truth is the live buffer" comment warns about.

### 3. The real path in the status bar

`EditorStatusBar` (already a component with a `languageLabel` / `lineEnding`
slot) gains a leading path segment in `SINGLE_FILE` mode, rendered with the
existing shortening vocabulary from `ui/support/FeedbackDraft.kt`
(`~proj/`, `~home/`, `~app/`) so a deep path fits a phone width and the same
alias means the same thing in a feedback report. Long-press copies the absolute
path (the drawer's *Copy path* action already does this —
`EditorScreen.kt:1000-1010` — so the behaviour and the toast string are reused).

### 4. "Open in editor" on the project card

- `HubCardAction` (`FileManagerScreen.kt:1114`) gains `OPEN_IN_EDITOR`.
- The overflow menu (`:1360-1407`) gains the row **at the top**, above SOURCE
  CONTROL: an explicit "open the project" action belongs next to the card's
  primary tap.
- Which file opens — **decision recorded:** the project's **launch default** if
  set (`setLaunchDefault` already exists in the editor), else the **most recently
  modified source file**, else the **first source file alphabetically**, else
  (empty project) the project editor with no file. Rationale: that is what a
  person means by "open this project", and it reuses state the app already keeps.
  A pure `ProjectEntryFile.pick(entries, launchDefault)` function owns the rule
  so CI pins it.
- Routing: `onProjectFileSelected(project.name, entryFile)` — the existing
  PROJECT-mode callback, unchanged.

### 5. What happens to the hub's file tree

It stays exactly as it is (tap a directory to expand, tap a file to edit) — only
the *destination* of a file tap changes to single-file mode. `onRunFile` and
`onPreview` keep their current behaviour (both are per-file already).

## Exit condition

```text
1. Hub → project → tap `main.c` → editor with ONE file, real path in the status
   bar, no ☰ tree, no git badges, no branch chip.
2. Edit + Ctrl-free save + autosave all work; reopen the file from the hub and
   the edit is there.
3. Back returns to the hub's file tree (see Phase 49's BackRouter; without 49 it
   must at minimum not exit the app).
4. RUN ▶ runs that file: C compiles offline; a language needing a package still
   shows the "Install X?" prompt.
5. Card ⋮ → Open in editor → full project editor (tree + git + run targets) on
   the launch-default / newest-source file.
6. Edit in single-file mode, then open the project: the project shows the new
   content (no stale buffer); the reverse order also holds.
7. Relaunch: the app opens where the last PROJECT session left off; single-file
   peeks never wrote the launch state.
8. Long-press the status-bar path → the absolute path is on the clipboard.
PASS = all eight; 2 and 6 are the ones that lose work if they fail.
```

## Tests (plan)

- `EditorOpenModeTest` (host): mode resolution from every route shape; the
  chrome/git/launch-state/tabs booleans per mode.
- `StatusBarPathTest` (host): short, deep (shortened), scratch, spaces, unicode,
  and a path that is exactly at the shortening threshold.
- `ProjectEntryFileTest` (host): launch default wins; deleted launch default
  falls through; newest-source beats first-source; empty project yields none;
  binary/asset files are never chosen as the entry file.
- `SingleFileSaveTest` (Robolectric/VM): save writes the real project path (not
  the scratch folder — **the money assertion**); mode switch in both directions
  reloads from disk; the dirty flag and undo stack do not leak; a single-file
  open does not add to `EditorLaunchState`.
- `EditorRouteCompatTest`: every existing route-building call site still
  produces a PROJECT-mode route (source scan over `MainActivity.kt`,
  `TemplatesScreen.kt`, `IncomingImportBridge`).

## Sources (record)

- CodeC 2026-09-12: `FileManagerScreen.kt:520-523,1114,1263,1360-1407`,
  `MainActivity.kt:706-733,990-995`, `EditorViewModel.kt:1229-1298`
  (`openFile` / `openProjectFile` / `openScratchFile`), `EditorScreen.kt:373-376,
  1000-1010`, `ui/components/EditorStatusBar.kt`, `ui/navigation/Screen.kt`,
  `ui/support/FeedbackDraft.kt` (path-shortening vocabulary),
  `ProjectPathUtils.resolveInside`.
- `docs/PHASE44_50_UX_RESEARCH.md` §4.1-4.2 (the market's file-vs-project
  models; why "both, made explicit" is the answer).

## Deferred / rejected with reasons

- **A separate "quick edit" screen** — rejected: two editors means two places to
  keep autosave, undo, completions and theming correct. One editor, three modes.
- **Read-only single-file view** — the owner asked for *"same file edit"*.
- **Opening every file tap in PROJECT mode but with the drawer closed** — that
  still loads the tree, git and launch state; it is the same cost with less
  honesty about what mode you are in.
- **"Open in editor" on the file's own row menu** — the owner asked for the
  project card's ⋮; a per-file duplicate would make the two paths disagree.

## Implementation (2026-09-12)

**New pure code (host-tested):**
- `ui/editor/EditorOpenMode.kt` — the enum (`PROJECT / SINGLE_FILE / SCRATCH`)
  and the policy (`forOpen`, `showsProjectChrome`, `writesLaunchState`,
  `showsGit`, `usesProjectTabList`, `statusBarPath`). One decision home, no
  Android imports.
- `ui/projects/ProjectEntryFile.kt` — `pick(candidates, launchDefault)`:
  launch default (if still present) → newest source (mtime, ties broken
  alphabetically on the full relative path) → first source → null. "Source" =
  a `LanguageRegistry` profile, so binaries/assets are never chosen.

**The Android edge:**
- Route: `Screen.Editor` grew the nullable `single={single}` flag;
  `createRoute(…, single: Boolean = false)` emits `single=1`. Every existing
  caller is untouched and still builds PROJECT routes
  (`EditorRouteCompatTest` pins: `single: Boolean = false` default, exactly
  ONE `single = true` call site in the app — the hub's file tap — and no
  `EditorLaunchState.save` may mention the flag).
- `EditorViewModel`: new `_openMode`/`openMode` state; new
  `openSingleProjectFile` (read/save path shared with `openProjectFile`:
  `sanitizeRelativePath` → `resolveInside` → `canRead` → `LineEndings`, ONE
  tab) skipping `refreshFileEntries`, `refreshGitMeta`,
  `bootstrapRemainingTabs` and `EditorLaunchState.save`. `_projectName` STAYS
  SET in SINGLE_FILE mode (the design note: null-ing it would turn a save
  into a scratch save) — chrome consumers read the mode through
  `EditorOpenModePolicy`, not the project name. `openProjectFile` /
  `openScratchFile` / `switchContext`'s terminal branches set the mode.
- Mode flips on one back-stack entry (launchSingleTop editor→editor) flush
  the autosave and RELOAD FROM DISK in both directions; undo stacks are
  cleared at the boundary (no dirty-flag/history leak). A same-file PROJECT
  re-open re-affirms the launch point, so a peek of that file can never leave
  it stale.
- `EditorScreen`: the route effect dispatches the peek (and deliberately does
  NOT call `onProjectSelected` — the hub tap already set the terminal cwd; a
  peek must not flip the editor's project session). Chrome consumers gate on
  `projectChrome`: the RUN ▶ default-vs-open chooser, the web-default
  preview, the set/clear launch-default + run-config menu items, the drawer's
  tree/git (46.2 + 47.1's list give the peek's drawer its PROJECTS section
  and a one-line hint instead of the tree). The status bar gains the leading
  `~proj/<p>/<rel>` segment in SINGLE_FILE only (PROJECT/SCRATCH rendering is
  byte-identical to today); long-press copies the ABSOLUTE path (the drawer's
  Copy path behaviour and toast, reused).
- Hub: file tap → new `onProjectFilePeek` callback (the ⋮ keeps the PROJECT
  `onProjectFileSelected`); card ⋮ gains **Open in editor** at the top (under
  Open, above Source Control) → `FileManagerViewModel.entryFileForEditor`
  (all ViewModel IO) → `ProjectEntryFile.pick` → PROJECT route.

**Deviations (all recorded, none silent):**
1. The specced `object EditorOpenMode` cannot share the enum's name in one
   package — the object is `EditorOpenModePolicy`. Naming only.
2. The specced `StatusBarPathTest` merged into `EditorOpenModeTest` — the
   function lives in the same policy object; one file, all six specced cases
   (short, deep→`~proj/`, scratch, PROJECT-null, blank, spaces/unicode).
3. **Empty project + ⋮ Open in editor** falls back to the hub's own tree view
   (the plain OPEN action) rather than a fileless editor route: the editor
   with no `fileName` shows whatever scratch state the last session left —
   a destination that could look like a bug. `ProjectEntryFile.pick` still
   answers null (pinned), and the fallback is the hub's honest surface.
4. The peek keeps ☰: the drawer opens WITHOUT tree/git (hint text +
   47.1's PROJECTS list + Guide footer) — the owner's exit says "☰ shows no
   project tree", and 47.1's list is also the in-editor way back to the whole
   project. `gesturesEnabled` stays off with a file open (the 25.2 law).
5. The hub tap still calls `onProjectSelected(project)` (terminal cwd — RUN
   in terminal keeps working from a peek); the EDITOR's own session callback
   is the one skipped in single mode.

**Tests:** `EditorOpenModeTest` ×15 (route shapes, the four booleans per
mode, statusBarPath) · `ProjectEntryFileTest` ×9 (default wins / deleted
default falls through / non-source default falls through / newest-beats-first
/ tie → alphabetical / no sources → null / binaries never / html is source /
nested paths) · `SingleFileSaveTest` ×5 (Robolectric, real VM + real project:
**save-from-peek writes the real project path and not the scratch folder**,
peek never writes the launch state, PROJECT flip reloads from disk with the
edit intact and the launch point written, back-flip reloads from disk with
one tab and no git/launch chrome, traversal path refused) ·
`EditorRouteCompatTest` ×4 (source pins). `SettingsAuditTest` untouched.

**Exit condition status:** 1-3, 5-8 are the device round (owner); 4
(back) is covered structurally by the nav stack + the unsaved-changes
handler and verified on device in the same round. CI green = the eight
automated halves of those rows.


## Device round 1 (2026-09-13) — "files rules are not strong enough"

**Owner report:** *"I open demo_flask then the starter python it opens both in
the editor but 2 projects are different so don't open together."*

**Two causes, one law (the owner's: two projects never share an editor):**

1. **The navigation restored another project's session.** Both hub file
   navigations (⋮ → Open in editor, and the file tap) used
   `restoreState = true` — which restores a saved editor entry with its OLD
   arguments and its OLD ViewModel: another project's tabs. Tapping a file is
   an instruction to open THAT file, so both are now `restoreState = false`
   (the Templates hand-off had the same disease and got the same fix; the
   bottom-bar tab taps keep `restoreState = true` — "show me my editor where
   I left off" is a restore, not an instruction).
2. **The ViewModel could MIX two projects' tabs.** `openProjectFile` appends
   its tab to whatever list the surviving VM holds and bootstraps the new
   project's files alongside it — and its same-file/existing-tab early
   returns matched another project's identically-named file. New guard at the
   top of `openProjectFile`: when the tab list is non-empty and belongs to a
   different project, save everything (flush + saveAllTabs, while
   `_projectName` still names the old project), then clear tabs, undo stacks
   and git/launch state — exactly the rule `switchContext` applies — before
   opening. The peek path already replaced the list wholesale.

**Tests:** `SingleFileSaveTest` +3 (A's session → open B: B's tab list, no
marker of A; peek of one project → open another: no ride-along; peek replaces
the list wholesale and the flip back starts clean) · `EditorRouteCompatTest`
+1 (both hub file navigations pinned `restoreState = false`) ·
`DrawerWiringTest` +1 (the New-project hand-off, see PART_47_1).

**Device-round fix CI ✅ GREEN: run `34736668771` on tip `ce4044d` — `conclusion: success`, assemble + `testDebugUnitTest` + `lintDebug`, artifacts `CodeC-IDE-release` / `CodeC-IDE-debug` (the owner's re-round build).**

## Test log (Phase 50 — the cross-device matrix)

> The 5 rows of the cross-device matrix that this part owns ([`../chat-phase50/DEVICE_MATRIX.md`](../chat-phase50/DEVICE_MATRIX.md)). `⏳` = not run. Paste the tester's result into `Result` (✅, or ❌ + the text the screen really showed, verbatim) and the device identity line — the first line of Settings → Feedback & Support → COPY REPORT — into `Evidence`, one line per device class that ran it. `DeviceMatrixTest` pins this table both ways: every row here must exist in the matrix with `46.2` in its `Part` column, and every matrix row owned by this part must be listed here. That is Phase 50's exit 4, enforced by CI instead of by memory.

| Row | Result | Evidence (device · OS · nav mode · what was seen) |
|---|---|---|
| F3 | ⏳ | |
| F4 | ⏳ | |
| F5 | ⏳ | |
| F6 | ⏳ | |
| F7 | ⏳ | |

**Already on record:** ✅ **device-passed at round level** on the owner's
phone (PR #78, 2026-09-13), *including* the two round-1 fixes this part owns — the
single-tap file open and *"2 projects are different so don't open together"* (F7
is that law's row). F3-F6 on a tablet, and the long-press clipboard row F6, are
new.

