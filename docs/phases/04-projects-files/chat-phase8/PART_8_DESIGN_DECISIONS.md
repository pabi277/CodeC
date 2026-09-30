# Phase 8 — Projects & File Tree Design Decisions

**Status:** ✅ Implementation complete in PR #27 · **final full device acceptance gate pending explicit export/re-import confirmation**
**Cost:** `[client-only]` · **Depends on:** Phase 7 (merged/device-verified)

This record is the source of truth for the Phase 8 implementation and its
verification state. The code is complete and the owner has confirmed the core
ZIP, terminal, project-tree, refresh, and web-entry workflows on device. The
remaining acceptance item is recorded explicitly in the completion checklist
rather than inferred from a successful APK assembly.

## D1 — Project storage and compatibility

Projects are direct child directories of the existing app-private
`filesDir/CodeC/projects` root. This is the executable location already used by
CodeC; no project content is moved to emulated storage. Existing flat source/web
files are copied, never deleted, into a `default` project on first project-list
load. The old low-level `FileManager` API remains available during migration.

## D2 — Path confinement

All project paths crossing the UI/API boundary are relative POSIX-style paths.
Traversal, absolute paths, empty segments, invalid names, and NUL characters are
rejected. Canonical-path checks reject symlink escapes; tree traversal and ZIP
export skip symlinked entries. Project deletion cannot target the projects root.

## D3 — Metadata and run configuration

Each project has `.codec/project.json`, version `1`, with `name`, `type`,
`entry`, `build`, `run`, and `clean`. New C projects get a small starter
`main.c`; imported projects preserve an existing config when present, otherwise
a default config is generated. Project Run/Run-in-Terminal dispatches the
configured build and run commands from the project root. Selecting an HTML/HTM
file as **Set as default run** changes the project type to `web` and stores its
safe relative path as `entry`; the web Run action opens that page in the
built-in preview. Phase 11 still owns the future split output panel.

## D4 — SAF privacy model

`ACTION_OPEN_DOCUMENT_TREE` and `ACTION_OPEN_DOCUMENT` are copy-in operations:
selected data is streamed into a newly created private project or the active
private project. `ACTION_CREATE_DOCUMENT` is used only after an explicit Export
ZIP action. The app never edits a SAF source tree in place and never auto-exports.

## D5 — ZIP safety and complete enumeration

Exports contain paths relative to the project root and preserve empty
directories. Imports reject traversal/absolute paths, duplicate files, symlink
representations (which ZIP cannot safely carry here), more than 10,000 entries,
entries over 128 MiB, or an archive over 1.28 GiB. SAF ZIP streams are staged in
temporary private storage and read with `ZipFile`, which enumerates the ZIP
central directory and handles archives whose local stream exposes only a root
directory. Extraction remains extension-agnostic and preserves nested paths,
spaces, Unicode, extensionless files, and binary content. Failed imports are
removed by the ViewModel so a partial project is not presented as complete.

## D6 — Terminal synchronization

The activity-scoped `TerminalViewModel` remains the routing owner. Selecting a
project validates the direct child project path and moves the active terminal to
the app-private `CodeC/projects` directory, so `ls` shows project directories.
Project build/run commands explicitly `cd` to the project root before using
project-relative paths. The public multi-terminal wire protocol is unchanged.

## D7 — Projects UI actions

The Projects top bar uses a three-dot overflow menu for import and project
actions. At the project-list level it provides Import Folder, Import ZIP, and
Refresh Projects. Inside a project it provides Refresh and collapse folders,
Import File, Export ZIP, and New File. Each HTML/HTM file's own overflow menu
provides Preview and Set as default run.

## Verification record

| Area | Evidence | State |
|---|---|---|
| Project tree, nested paths, metadata, and path guards | Source implementation plus `FileTreeRepositoryTest`, `ProjectPathUtilsTest`, and `ProjectConfigTest` | ✅ Implemented and covered |
| Extension-agnostic ZIP import | Owner device report: archive containing HTML, CSS, JS, C, and Python files imported with all files intact | ✅ Owner-confirmed |
| ZIP central-directory robustness | CI APK build for `fc19bd9` passed after the device error; implementation is present in PR #27 | ✅ Implemented / CI assembled |
| Terminal project listing | Owner confirmed project files/folders work and requested projects-folder `ls` behavior | ✅ Owner-confirmed |
| Refresh and collapse-all | Owner confirmed the new refresh option works | ✅ Owner-confirmed |
| HTML default run | Owner confirmed the new web default-run workflow works | ✅ Owner-confirmed |
| APK build | Build APK workflow `33236115940` passed for `71978e6` | ✅ Passed |
| Full export → import as a separate project | Export/import implementation and ZIP round-trip tests are present; no explicit device transcript for this final round trip is recorded yet | ⚠️ Owner confirmation required before merge |
| Unit-test execution | The workflow assembles the APK only; local sandbox has no Java runtime | ⚠️ Not executed in this environment |

## Merge gate

Phase 8 implementation is complete and PR #27 is ready for review. Before
merging, run the remaining device round-trip check once:

1. Export the active project through Projects → three-dot menu → Export ZIP.
2. Import that ZIP through the Projects → three-dot menu as a different project name.
3. Confirm the second project contains the same nested files and opens them in
   the editor.
4. Confirm the terminal can enter the copied project and use project-relative
   paths.

After that owner confirmation is recorded, this checklist can be changed from
`⚠️ Owner confirmation required` to `✅ Device acceptance complete`.
