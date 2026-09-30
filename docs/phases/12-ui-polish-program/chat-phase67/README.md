# Phase 67.1 — Files, drawer and project search

**Date:** 2026-09-27. **Branch:** `arena/01a0e2fc-codec`.
**Status:** merged to `main` via PR; Android CI green for code `3dd599e`. Handset verification recommended.
Delivered to `main` via PR on owner instruction.

## Owner decisions (option labels verbatim)

The owner first requested discussion only, then supplied three real Spck shots:
`Screenshot_20260927_190600_Spck Editor.jpg` (expanded tree),
`Screenshot_20260927_191736_Spck Editor.jpg` and
`Screenshot_20260927_191732_Spck Editor.jpg` (file and Files-toolbar menus).
These were viewed in chat; they are not generated CodeC screenshots or device
verification of this implementation. Earlier real references 122203 Files and
124052 Search were also reviewed. The newer owner shots control this part.

> I want the file system like this on the screenshot
>
> And genarate questions that you have doubts with options i will answers then start code

First answers:

1. **“Match the Files appearance closely”** — adapt to CodeC light/dark themes;
   other panels and bottom navigation stay unchanged.
2. **“Move switching to a menu”** — clearly labelled Switch project opens the
   in-panel project list; a pick keeps the list and drawer open.
3. **“Compact, with safe tap targets”** — minimum 48dp non-overlapping targets.
4. Toolbar: **“I will upload 2 new screenshot you will understand”**.
5. **“Include useful config files”** — without a new preference; exclude
   repository internals and generated/cache content.
6. **“File safety + search clarity”** — drawer-operation outcomes, deleted
   buffers, scope/loading/errors, stale results and result jumps.

After the two menu shots, final answers:

7. **“Existing actions, screenshot styling”** — icon-bearing grouped menus,
   separated Delete, existing applicable actions only; long-press remains.
8. **“Screenshot layout, bounded actions”** — Search, Locate active file,
   New file, New folder and overflow; overflow includes Switch project,
   Refresh, Expand/collapse and access to existing Git actions.

No Copy/Cut/Paste, multi-select, import, Add Library, Publish Project or
file-operation Undo/Redo. No inactive imitation of reference controls.

## Implementation

- `EditorProjectDrawer`: indented rows, subtle hierarchy lines (depth capped
  visually at six levels), thin theme-primary selected outline, separate green
  launch marker, file-type icons, independent 48dp row/menu targets, folder
  expanded and selection semantics. Project root collapses independently.
- Files toolbar and anchored row/root menus replace the old branch chip,
  four-column labelled toolbar and Git footer. Existing Git doors remain in
  overflow. Close files panel remains an explicit overflow action; Back/scrim
  policy is unchanged. Below 300dp panel width, Locate moves into overflow
  rather than shrinking touch targets. Menus expose full relative paths.
- Menu-based project list has a bounded scroll area; selecting a project does
  not dismiss it. No new project-selection destination or navigation change.
- `ProjectFilesPolicy` supplies drawer/search filtering: useful dotfiles,
  extensionless config files and `.github`/`.vscode` directories; excludes
  VCS internals, dependency/build/cache/virtual-environment directories.
  `FileTreeRepository` gains an optional traversal filter; default hub calls
  remain unfiltered, preserving Phase 66 behaviour.
- Drawer create/rename/delete now return success. Dialogs retain entered names
  and show failures locally rather than dismissing blindly. Create rejects
  occupied names and invalid path-like input; folder deletion explicitly
  warns about descendants and unsaved edits.
- Delete reconciles removed paths with this editor's descendant tabs, undo
  histories, active buffer, collapsed paths and launch default. Partial delete
  reconciles paths actually gone, preserving surviving files. A deleted last
  file leaves an unnamed empty buffer; save refuses it rather than recreating
  the deleted path. Existing normal tab-close behaviour is untouched.
- Rename remaps collapsed paths and persists a moved launch-default path.
- Search carries query/options/project identity; old hits become unavailable
  immediately when identity changes. Debounced IO search checks cancellation
  between files and requests one extra hit to distinguish a real cap.
  The panel distinguishes idle, searching, no project, invalid regex, no hits,
  failure/incomplete reads and capped results. Scope explicitly says saved
  files, not unsaved buffers. Reopening Search retries a failure.
- Search result opening validates the originating project/path, opens and
  checks the selected file, then jumps to the recorded line **and column**.
  Failed opens leave the panel available. Options gain named accessibility
  semantics and 48dp targets.

## Validation

Initial CI [36324048608](https://github.com/pabi277/CodeC/actions/runs/36324048608)
compiled the Android sources and ran **2,215 tests: 2,211 passed / 4 failed**.
All four failures were the new `FilesPolishWiringTest`: its `codeOnly` helper
removed string literals which its assertions intentionally inspected. The
helper was corrected to read the real source (the existing DrawerWiringTest
style). The buffer-operation and search-policy tests passed. APK assembly
was gated behind the failed test task and was not reached.

Follow-up `bd71cef` adds two Compose layout tests as well as the test fix:
[CI 36324357068](https://github.com/pabi277/CodeC/actions/runs/36324357068).
**GREEN:** the complete host unit/screenshot test task, debug APK, release APK,
release APK-set checks and artifact uploads all passed. Debug APK: 25,988,068 B;
release APK: 6,783,772 B. The two new Compose layout tests passed, alongside the
buffer-operation, search-policy and corrected wiring tests. No release was
published. Subsequent documentation-only commits do not change the tested code.

Added:
- `ProjectFilesPolicyTest`: useful configs, exclusions, tree/search consistency,
  unchanged default hub traversal, exact cap probe, cancellation, descendant
  path boundaries.
- `DrawerFileActionsTest` (Robolectric): last-file delete/save safety, descendant
  tab reconciliation and sibling edits, create/rename failure preservation,
  result line/column and stale project/missing file rejection.
- `FilesPolishWiringTest`: source-level wiring for hierarchy, menus, targets,
  bounded switching, search states/identity, dialog-local errors.
- `FilesPanelLayoutTest` (Compose/Robolectric): narrow light panel at 1.4× font
  scale, reachable Locate/row actions and 48dp menu targets; dark panel switching
  projects while retaining the project list. These are not handset screenshots.
- Existing side-panel engine-call and empty-project wording assertions updated
  to match the agreed changes; DrawerPolicy and Back tests retained.

`git diff --check` passed. This sandbox initially had no JDK/Kotlin/Android SDK;
attempts to obtain host test tools failed at network access. No local Android
or JVM test execution is claimed. Android CI is the compilation/test gate.

**Not established:** handset rendering, narrow/large-font/IME interaction,
TalkBack behaviour, screenshot pixel equivalence, read/partial-delete failures
on a device, or coordination with another editor instance parked in a different
navigation entry. Regex matching still uses the existing engine; cancellation
between files is not a per-regex execution-time limit. These are not claimed
passes and do not constitute a mandatory owner device round.

## Boundaries

Phase 66.1 is already merged through PR #88. Its hub changes and Back-from-
Projects fix were not redone; `MainActivity` is untouched. Phase 64's no-guide,
no-installation-UI-lock and no-new-app-wide-indicator decisions remain intact.
No new app dependency, permission, preference or telemetry. SidePanelPlan rail,
reserved slot and bottom navigation remain unchanged. Stop after this part;
no other phase, PR or merge without the owner's instruction.

## Owner follow-up — Files-first, file finding, paths and file attachments

**Owner instruction (verbatim):**
> You did really great but some addition when i click 3 ber from editor it will open the file section not the project section, the search icon in the file section is for the file search, the new file create have a default file name remove it and new file also will have a feature like if someone try they can add location with file example css/subjects.css it will create a folder and the file will be inside the folder, single file download and share as file option add

This explicitly extends the agreed part; it does not authorise a different
phase, PR or merge. It supersedes the initial toolbar-search interpretation.

### Changes

- Opening the editor's hamburger selects **Files**, with the project switcher
  collapsed. The rail's default is now Files; rail order, navigation destinations
  and the close/Back law remain unchanged.
- Files' magnifier opens **Find file by name or path**, not content search.
  Filtering is case-insensitive, retains matching rows' ancestor folders, and
  searches below normally collapsed folders. Search-only expansion state does
  not overwrite the normal tree's expansion. Clear/close restores the full tree;
  switching project resets the filter. The separate Search rail still searches
  saved file contents with its existing options.
- New file starts blank; `css/subjects.css` is a placeholder, not a default
  value. `NewFilePath` creates missing parents then exclusively creates the
  file, with no overwrite/truncation. Toolbar paths are project-root-relative;
  “New file here” paths are relative to the selected folder. Absolute paths,
  traversal, empty segments, trailing separators, backslashes, escaping symlinks,
  occupied targets and blocking parent files are rejected with dialog feedback.
  The existing per-file Git restore write guard is preserved.
  Existing single-files/scratch mode remains flat: nested creation requires a
  project rather than silently flattening the typed path.
- File-row menus gain **Share as file** and **Download** (not folder transfer).
  The editor overflow has the same operations, including in single-file mode;
  its former text-only share is replaced by an actual attachment.
- The selected file's dirty buffer is saved before preparing an export, without
  switching tabs or substituting the active file. An unreadable/missing source,
  stale project, directory selection or save failure refuses the operation.
- Exports use unique cache snapshots with the original basename and exact bytes.
  Download uses Android's CreateDocument save picker: the user chooses Downloads
  or another document-provider destination. Pending snapshot identity survives
  activity recreation through saved instance state, not a new preference; a
  later project/tab change cannot retarget it. Cancellation removes the snapshot
  silently; provider failure reports a possible incomplete destination copy.
- Sharing uses the existing FileProvider with EXTRA_STREAM, MIME type and a
  temporary **read-only** URI grant/ClipData. It shares a cache copy rather than
  exposing a writable source file. Failed handoffs clean up; successful share
  snapshots remain readable and are eligible for cleanup on a later export
  after 24 hours. Missing pending cache snapshots request a retry.
- No dependency, manifest permission, persistent preference or global indicator
  added. Installation and Phase 66 navigation decisions remain untouched.

### Follow-up verification

Code `5fbf1c3`: [CI 36325808709](https://github.com/pabi277/CodeC/actions/runs/36325808709)
passed the full host unit/screenshot test task on its first run; APK assembly
was still running when this record was updated. A review follow-up preserves
`GitDiscardEditors.blocks` before exclusive nested creation, with a dedicated
buffer regression test. Intermediate code `1fa530d` passed the host test task in
[CI 36326072901](https://github.com/pabi277/CodeC/actions/runs/36326072901).
Review then caught an active-tab export edge case: the active tab's cached
buffer can lag live editor text after autosave. Code `3dd599e` prevents that
cache from being written back and adds a real-buffer regression test. **GREEN:** [CI 36326855236](https://github.com/pabi277/CodeC/actions/runs/36326855236)
for final code `3dd599e` completed successfully. The host unit and screenshot test
task, debug APK assembly (26,010,088 B), release APK assembly (6,790,476 B), release
manifest validation and artifact packaging all passed without errors. Subsequent
documentation-only commits do not change the tested code.
Do not treat the earlier `bd71cef` green run as evidence for these additions.

Added/extended tests cover exclusive nested creation, invalid paths and symlink
escape, binary-preserving snapshots and independent same-name exports,
filename/path matches with ancestors, real ViewModel nested creation and
selected-dirty-tab export and active export after autosave, Files-first/empty-name/attachment wiring, and Compose
file-filter plus Share/Download menu callbacks. External save-provider and
receiving-app behaviour, rotation while the picker is open, and handset IME
interaction still need real Android evidence; no such verification is claimed.
