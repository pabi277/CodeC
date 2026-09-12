# CodeC Phase 46.2 — One file is one file; the project is an explicit action

> **Status:** 📋 PLANNED · **Cost:** `[client-only]` · **Effort:** M ·
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
