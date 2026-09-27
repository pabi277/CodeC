# Phase 67.1 — Files, drawer and project search

**Date:** 2026-09-27. **Branch:** `arena/01a0e2fc-codec`.
**Status:** implemented; Android CI green for code `bd71cef`. No handset verification claimed.
No PR opened; no merge authorised.

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
