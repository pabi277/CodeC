# CodeC Phase 8 — Projects & File Tree (Keystone Architecture)

**Status:** ✅ Implementation complete in PR #27 · **core workflows device-confirmed; final export/re-import round-trip confirmation pending**
**Cost:** `[client-only]` · **Depends on:** Phase 7 (Multi-Terminal)

**Keystone Role:** Phase 8 is the foundational keystone for Phase 9 (Editor),
Phase 11 (Output Panel & Run), Phase 12 (Multi-language), and Phase 13
(GitHub).

---

## 1. Context & Motivation

Before Phase 8, `FileManagerScreen.kt` was a flat file list in a single
directory. Projects could not have subdirectories, could not be imported and
exported as complete codebases, and the terminal/editor had no project model.

Phase 8 creates a real hierarchical Project Workspace model:

1. **Hierarchical File Tree:** Expandable/collapsible directories, breadcrumbs,
   nested file creation, rename, and delete.
2. **Project Model & Run Configuration (`.codec/project.json`):** Standard
   metadata defining project type, entry file, build command, run command, and
   clean command.
3. **SAF Import / Export:** Native Android Storage Access Framework (SAF)
   document and folder pickers for private imports and explicit ZIP exports.
4. **Terminal & Editor Integration:** Project files open with project-relative
   paths and breadcrumbs; the terminal lists project directories from the
   private projects parent and build/run commands enter the project root.
5. **Web Project Entry:** An HTML/HTM file can be selected as the default web
   run page and opened by the project Run action.

## 2. Architectural Design

### 2.1 Project Workspace Structure

Projects reside in app-private executable storage:

```text
filesDir/CodeC/projects/my_c_project/
├── .codec/
│   └── project.json
├── include/
│   └── utils.h
├── src/
│   ├── main.c
│   └── utils.c
├── Makefile
└── README.md
```

Compiled project outputs remain in this app-private location so executable
permissions are not lost on a `noexec` shared-storage mount.

### 2.2 Project Configuration Schema (`project.json`)

```json
{
  "version": 1,
  "name": "my_c_project",
  "type": "c",
  "entry": "src/main.c",
  "build": "cc -I include src/main.c src/utils.c -o bin/app",
  "run": "./bin/app",
  "clean": "rm -rf bin/app"
}
```

For a static web project, selecting an HTML file as the default run page stores
its safe relative path in `entry` and changes `type` to `web`.

### 2.3 Hierarchical Tree Data Model

`FileTreeRepository` builds a canonical-path-checked tree of `DirectoryNode` and
`FileLeaf` values. It sorts directories before files, tracks expanded relative
paths, and flattens only visible nodes for Compose rendering. Symlink escapes
are excluded.

### 2.4 SAF Import / Export Architecture

- **Import Folder (`ACTION_OPEN_DOCUMENT_TREE`):** Recursively reads the selected
  document tree and copies its files into a new private project.
- **Import ZIP (`ACTION_OPEN_DOCUMENT`):** Copies the selected SAF stream into
  temporary private storage, opens the archive through `ZipFile`, and extracts
  every central-directory file entry into a new private project. It is not
  restricted by filename extension.
- **Import File (`ACTION_OPEN_DOCUMENT`):** Copies ordinary files into the active
  private project; ZIP files selected through this path are expanded instead of
  stored as opaque archives.
- **Export ZIP (`ACTION_CREATE_DOCUMENT`):** Only after an explicit user action,
  writes the project tree to the chosen destination with paths relative to the
  project root.

ZIP imports preserve nested directories, spaces, Unicode, extensionless files,
binary content, and all normal file extensions. Traversal, absolute paths,
duplicate files, symlink representations, excessive entry counts, and oversized
entries/archives are rejected.

### 2.5 Terminal and Editor Integration

- Selecting a project moves the terminal to the private `CodeC/projects`
  directory, allowing `ls` to show the project as a folder.
- Build/run handoffs explicitly `cd` to the selected project root, so relative
  includes and run commands continue to work.
- Opening a project file navigates with a project name and sanitized relative
  path. The editor displays a breadcrumb such as
  `my_c_project > src > main.c`.
- HTML/HTM files provide Preview and Set as default run in their file menu.
  Web projects use the configured entry page when Run is pressed.

### 2.6 Projects UI

The Projects top bar uses a three-dot menu:

- Project list: Import Folder, Import ZIP, Refresh Projects.
- Open project: Refresh and collapse folders, Import File, Export ZIP, New File.
- HTML/HTM file row: Preview and Set as default run.

Refresh reloads the project tree and clears all expanded-directory state, giving
a clean collapsed structure.

## 3. Implementation Summary

1. Created `ProjectManager.kt`, `ProjectConfig.kt`, `ProjectPathUtils.kt`, and
   `FileTreeRepository.kt`.
2. Refactored `FileManagerScreen.kt` and `FileManagerViewModel.kt` for project
   lifecycle, hierarchical files, SAF operations, ZIP transfers, overflow
   actions, refresh/collapse, and web entry selection.
3. Added project-aware routes and editor breadcrumbs in `MainActivity.kt`,
   `EditorScreen.kt`, and `WebPreviewScreen.kt`.
4. Added terminal project synchronization and safe project-relative handoffs.
5. Added tests for tree operations, config round trips, path confinement, ZIP
   preservation/security, and terminal handoff construction.
6. Fixed ZIP reading for SAF archives whose streaming local entries exposed only
   a root directory by enumerating the ZIP central directory with `ZipFile`.

## 4. Acceptance and Verification

The implementation acceptance record is maintained in
[`PART_8_DESIGN_DECISIONS.md`](PART_8_DESIGN_DECISIONS.md).

Owner-confirmed on device:

- ZIP containing HTML, CSS, JS, C, and Python files imported successfully with
  all files intact.
- Project-folder terminal listing behavior works.
- Refresh and collapse-all folders works.
- HTML default-run selection and web preview Run behavior works.

Automated/source verification:

- APK assembly passed in CI run
  [33236115940](https://github.com/pabi277/CodeC/actions/runs/33236115940).
- `git diff --check` passed for the implementation commits.
- Project tree, path, config, ZIP, and terminal handoff tests are present.

Before merge, explicitly complete the export → import-as-a-different-project
round trip and record the device result. CI currently assembles the APK but does
not execute the unit-test task; the agent sandbox has no Java runtime for local
execution.

## 5. Non-Goals and Invariants

- **Not in Phase 8:** The split output panel remains Phase 11; full editor
  foundation remains Phase 9; Git remote sync remains Phase 13.
- Projects remain under app-private executable storage.
- SAF imports copy into private storage; exports happen only after an explicit
  user action.
- Every archive path is validated before extraction; no archive may escape its
  project directory.
