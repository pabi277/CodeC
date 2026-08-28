# Phase 8 — Projects & File Tree Design Decisions

**Status:** Implementation started 2026-08-28 · device acceptance pending
**Cost:** `[client-only]` · **Depends on:** Phase 7 (merged/device-verified)

This record turns the Phase 8 handoff into concrete implementation decisions
before device verification. It does not claim the Phase 8 exit condition is met.

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
configured build and run commands from the project root. Phase 11 owns the
future split output panel.

## D4 — SAF privacy model

`ACTION_OPEN_DOCUMENT_TREE` and `ACTION_OPEN_DOCUMENT` are copy-in operations:
selected data is streamed into a newly created private project or the active
private project. `ACTION_CREATE_DOCUMENT` is used only after an explicit Export
ZIP action. The app never edits a SAF source tree in place and never auto-exports.

## D5 — ZIP safety

Exports contain paths relative to the project root and preserve empty
 directories. Imports reject traversal/absolute paths, duplicate files, symlink
 representations (which ZIP cannot safely carry here), more than 10,000 entries,
entries over 128 MiB, or an archive over 1.28 GiB. Failed imports are removed by
the ViewModel so a partial project is not presented as complete.

## D6 — Terminal synchronization

The existing activity-scoped `TerminalViewModel` remains the routing owner.
Opening a project or project file sends a quoted `cd` command to the active
session; if the shell is still bootstrapping, the existing command queue sends
it after startup. The public multi-terminal wire protocol is unchanged.

## Remaining verification

- Host tests for path confinement, tree ordering/mutation, config round-trip,
  run command construction, ZIP round-trip, and malicious ZIP rejection.
- Green APK CI build and test execution where available.
- Real-device recipe from `PART_8_PROJECTS.md`, including SAF folder/file import,
  nested editor breadcrumb, terminal `pwd`, run config, ZIP export/import, and
  no files outside `filesDir/CodeC/projects`.
