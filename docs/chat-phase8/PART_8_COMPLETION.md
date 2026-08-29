# Phase 8 — Completion and Acceptance Record

**Implementation:** ✅ Complete in PR #27
**Core device workflows:** ✅ Owner-confirmed on 2026-08-29
**Final merge gate:** ⚠️ Explicit export → re-import-as-a-new-project device confirmation still required

This record separates what is implemented and tested in the repository from
what has been explicitly confirmed on an Android device. Phase 8 must not be
marked fully closed on the basis of APK assembly alone.

## Delivered scope

- App-private project directories under `filesDir/CodeC/projects`.
- Project metadata in `.codec/project.json` with project type, entry, build,
  run, and clean configuration.
- Hierarchical folders and files with nested creation, rename, delete, and
  expand/collapse state.
- Project-aware editor routes and breadcrumbs for nested relative paths.
- SAF folder import and file import into private storage.
- ZIP import and export preserving the complete tree and all normal file types.
- ZIP central-directory enumeration through `ZipFile` for problematic SAF
  archives whose streaming local entries expose only a root directory.
- ZIP path traversal, duplicate, symlink, entry-count, and size protections.
- Project-aware terminal navigation and safe project-relative build/run
  handoffs.
- Projects three-dot actions, refresh/collapse-all, and HTML/HTM default web
  Run entry selection.

## Acceptance evidence

| Acceptance area | Evidence | Result |
|---|---|---|
| ZIP import of mixed file types | Owner reported successful import with HTML, CSS, JS, C, and Python files intact | ✅ Passed on device |
| Complete ZIP extraction | Owner reported all archive files intact after import; central-directory fix is in the current APK | ✅ Core behavior passed |
| Project tree and nested structure | `FileTreeRepository` implementation and tests; imported tree visible in the app | ✅ Implemented |
| Editor project paths and breadcrumbs | Project-aware navigation and breadcrumb implementation | ✅ Implemented |
| Terminal project listing | Owner confirmed project-folder listing behavior after the terminal-directory correction | ✅ Passed on device |
| Refresh and collapse-all folders | Owner reported the new refresh action works | ✅ Passed on device |
| HTML default run | Owner reported Set as default run and web Run/preview work | ✅ Passed on device |
| Export ZIP | SAF CreateDocument export implementation and ZIP output tests | ✅ Implemented; device transcript not recorded |
| Re-import ZIP as a separate project | New-project ZIP import implementation and preservation tests | ⚠️ Device confirmation required |
| APK assembly | Build APK run `33236115940` passed for PR #27 code | ✅ Passed |
| Unit-test execution | CI workflow runs `assembleDebug` only; local sandbox has no Java runtime | ⚠️ Not executed here |

## Final device check before merge

On the latest PR #27 APK:

1. Open a populated project.
2. Use Projects → three-dot menu → Export ZIP and save it to Downloads.
3. Return to the project list and use Import ZIP.
4. Give the imported copy a different project name.
5. Confirm the copy contains the same folders and files, including mixed file
   types and nested paths.
6. Open a copied nested file in the editor.
7. In Terminal, enter the copied project directory and verify project-relative
   paths.

Once this result is reported, replace the remaining `⚠️ Device confirmation
required` result above with `✅ Passed on device` and mark the Phase 8 acceptance
gate fully closed.
