# CodeC Phase 46 — Projects, not folders

> **Status:** 📋 PLANNED (researched + specced, no code) · **Cost:**
> `[client-only]` · **Effort:** M · **Owner row (verbatim):** *"The project have
> a feature open a folder (phase 43, incomplete) i want to remove it completely
> and make the project section more optimization features like file single click
> to open in a editor screen with real path and same file edit but not full
> project to editor. To open a full project in editor the 3 dot will have the
> option to open in editor"*

```text
  46.1  "Open Folder" removed completely (+ the Phase 43 tombstone)
  46.2  One file = one file; the whole project = ⋮ → Open in editor
```

| Part | Title | Effort | Status |
|---|---|---|---|
| [46.1](PART_46_1_REMOVE_OPEN_FOLDER.md) | Delete the folder-import feature | S | 📋 PLANNED |
| [46.2](PART_46_2_SINGLE_FILE_EDITOR.md) | Single-file editor + "Open in editor" | M | 📋 PLANNED |

**Ordering rule: 46.1 before 46.2.** Both touch the hub's tap routing and the
`+` sheet; deleting first keeps 46.2's diff about the new model only.

---

## What exists today (evidence, read 2026-09-12 against `main` @ `f3a6e32`)

**The folder feature (all of it):**

| File:line | What |
|---|---|
| `FileManagerScreen.kt:180-188` | `folderImportLauncher` = `rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree())` → `viewModel.importFolder(...)` |
| `FileManagerScreen.kt:1077-1080` | the `+`-sheet's `onOpenFolder` branch |
| `FileManagerScreen.kt:1431` | `ProjectsHubAddSheet(onOpenFolder: …)` parameter |
| `FileManagerScreen.kt:1474-1481` | the "Open Folder" row (green tile, `SpckIcons.FolderLine`) |
| `FileManagerViewModel.kt:357-382` | `importFolder()`: new empty project → `ProjectTransfer.copyDocumentTree` → `catch (e: Exception)` |
| `ProjectTransfer.kt:20-29` | `copyDocumentTree()` |
| `ProjectTransfer.kt:313-345` | `copyDocumentChildren()` — **plain recursion**, no visited set, no depth/file/byte budget, no cancel, no progress |
| `strings.xml:348-349` | `hub_sheet_folder` / `hub_sheet_folder_subtitle` |

`takePersistableUriPermission` has **zero call sites** in `app/src/main`
(grep, 2026-09-12) — so an imported folder was never linked, only copied once.
And `grep -rln copyDocumentTree app/src/test/` returns **nothing**: no test ever
covered the walk.

**The hub's tap model today:**

- Project card tap (`FileManagerScreen.kt:1263`, `HubCardAction.OPEN`) →
  `selectProject` → the hub shows that project's **file tree** in place
  (`ProjectTree`, `:516-537`).
- **File tap** (`:520-523`) → `onProjectSelected` + `onProjectFileSelected` →
  `MainActivity.kt:990-995` → `Screen.Editor.createRoute(path, projectName)` →
  the **full project editor** (drawer tree, git meta, run targets, launch state
  all loaded).
- Card overflow menu (`:1360-1407`) lists OPEN / SOURCE CONTROL / SWITCH BRANCH
  / PULL / PUSH / COPY REMOTE URL / EXPORT / SHARE ZIP / RENAME / DELETE —
  **there is no "Open in editor"**; `HubCardAction` (`:1114`) has no such
  member.

So the owner's sentence is exactly right: a single tap on a file currently
carries the *whole project* into the editor, and there is no explicit way to do
the opposite.

## The new model (one sentence per rule)

1. **Tap a file → edit that file.** One buffer, the real path in the status bar,
   save works, autosave works — and **no project chrome**: no drawer tree, no
   git badges, no launch-default marker, no `EditorLaunchState` write (a peek
   must not become the app's launch point).
2. **Card ⋮ → Open in editor → the whole project.** The drawer, git, run targets
   and launch state, exactly as today.
3. **"Open Folder" is gone.** Import ZIP, Import file, Export and
   "Open with CodeC" all stay.

## Design

The pure core is one enum and one decision function — the same lever for both
rules:

```kotlin
// ui/editor/EditorOpenMode.kt   (pure, host-tested)
enum class EditorOpenMode { PROJECT, SINGLE_FILE, SCRATCH }

object EditorOpenMode {
    /** The only place "what does this open mean" is answered. */
    fun forOpen(route: OpenRequest): EditorOpenMode
    fun showsProjectChrome(mode: EditorOpenMode): Boolean   // false for SINGLE_FILE
    fun writesLaunchState(mode: EditorOpenMode): Boolean    // false for SINGLE_FILE
    fun showsGit(mode: EditorOpenMode): Boolean             // false for SINGLE_FILE
    /** The "real path" the owner asked to see. */
    fun statusBarPath(mode: EditorOpenMode, projectRoot: String?, relativePath: String,
                      homeAlias: String): String
}
```

`EditorViewModel` already has two of the three modes
(`openProjectFile`, `EditorViewModel.kt:1239`; `openScratchFile`, `:1276`).
`SINGLE_FILE` is the third: the project-file **read/save path** (same root,
same `ProjectPathUtils.resolveInside` guard, same `LineEndings` handling) with
the project chrome suppressed. The Android edge is a nullable route flag
(`single=1`), not a new screen — one destination, three modes.

`statusBarPath` answers the owner's *"with real path"*:
`~/CodeC/projects/<project>/<rel>` shortened to `~/proj/<project>/<rel>` on small
screens using the **existing** `~proj/` alias convention that
`ui/support/FeedbackDraft.kt` already uses for path shortening (verified: the
Phase 41 redactor shortens `~proj/`, `~home/`, `~app/`), so one vocabulary
covers the status bar and the feedback report.

## Risks to watch

- **A user who expects RUN ▶ to work in single-file mode.** It must: the run
  planner keys off the file's language (`LanguageRegistry` / `LanguageRunPlanner`,
  Phase 21), not off the project. The one exception to check on device is a
  *server* preset that needs a project config — in single-file mode the plain
  file run applies, and the Output Panel says so.
- **Save semantics.** A single-file buffer saves to the same absolute path as
  the project editor would. If the user then opens the project, the tab must
  not be stale: `EditorViewModel`'s tab list is keyed by `relativePath`
  (`:1247-1251`), and the mode switch must reload from disk rather than reuse a
  buffer that was opened in the other mode. This is the subtlest part of 46.2
  and gets its own test.
- **Deep links.** `EditorLaunchState` and the `Open with CodeC` bridge both
  build editor routes (`MainActivity.kt:706-717`, `:724-733`); they must keep
  producing `PROJECT` mode so nothing about launch behaviour changes.

## Exit condition

```text
46.1
1. The `+` sheet has three rows (New project / Clone / Import ZIP); no "Open
   Folder" anywhere in the app; no SAF tree picker remains.
2. `copyDocumentTree`, `copyDocumentChildren`, `importFolder` and
   `folderImportLauncher` are gone from the source tree (grep = 0 hits), CI
   green, no test deleted except none (there were none).
46.2
3. Hub → project → tap `main.c`: the editor opens with ONLY that file, the
   status bar shows its real path, editing + save + autosave work, ☰ shows no
   project tree, and no git badges appear.
4. Back from that editor returns to the hub's file tree (not the app exit).
5. Card ⋮ → Open in editor: the full project editor (drawer tree, git, RUN
   targets) opens, on the project's launch default file if set, else the newest
   source file, else the first source file.
6. Edit in single-file mode, then open the project: the file's new content is
   there (no stale buffer, no lost edit).
7. Relaunch after step 5: the app still opens where the PROJECT session left off
   — a single-file peek never became the launch state.
8. RUN ▶ in single-file mode runs that file (C offline; a language needing a
   package still offers to install it).
PASS = all eight.
```

## Tests (plan)

- `EditorOpenModeTest` (host): `forOpen` for every route shape; the
  chrome/git/launch-state booleans per mode; `statusBarPath` for a short path, a
  deep path (shortened with the `~proj/` alias), a scratch file, and a path with
  spaces/unicode.
- `ProjectEntryFileTest` (host): the "which file opens" order
  (launch default → newest source → first source) on synthetic trees, including
  an empty project (→ no file, project chrome only) and a project whose launch
  default was deleted (→ falls through, no crash).
- `SingleFileSaveTest` (Robolectric/VM): save from `SINGLE_FILE` writes the real
  path; switching to `PROJECT` mode for the same file reloads from disk; the
  dirty flag and undo stack do not leak across modes.
- Source-scan test: `app/src/main` contains no `OpenDocumentTree` and no
  `copyDocumentTree` (the regression pin that the feature cannot quietly come
  back — the shape of `SettingsAuditTest`).

## Sources

- CodeC 2026-09-12 — every file:line above.
- `docs/PHASE44_50_UX_RESEARCH.md` §4 (Spck/Acode/Pydroid/Squircle file-vs-project
  models; why deleting the folder feature is honest).
- [`../chat-phase43/README.md`](../chat-phase43/README.md) — the tombstone for
  the cancelled phase, including the "if anyone asks again" note.
- `ui/support/FeedbackDraft.kt` — the `~proj/`/`~home/`/`~app/` path-shortening
  vocabulary reused by `statusBarPath`.

## Deferred, recorded on purpose

- **Multi-file selection / "open all in tabs"** — not asked for; a phone IDE
  opens one file at a time.
- **Opening a file outside any project by absolute path** — that is the SAF story
  this phase deletes; do not reintroduce it through the back door.
- **A "recent files" list** — `recent_files_csv` was deleted in Phase 38.2 for
  having no reader; bringing it back needs a real reader and an owner ask.
- **Renaming the Projects tab** — "Projects" stays; the single-file editor is a
  mode of the editor, not a new destination.
