# CodeC Phase 46.1 — "Open Folder" removed completely

> **Status:** 📋 PLANNED · **Cost:** `[client-only]` · **Effort:** S ·
> **Owner row (verbatim):** *"The project have a feature open a folder (phase 43,
> incomplete) i want to remove it completely"*

## What "completely" means, file by file

A deletion is only complete when nothing can reach the code and nothing in the
product still refers to it. Checklist, in the order it should be done (each
step leaves the build green):

| # | File:line (verified 2026-09-12) | Delete |
|---|---|---|
| 1 | `FileManagerScreen.kt:1474-1481` | the `HubSheetRow` for "Open Folder" |
| 2 | `FileManagerScreen.kt:1431` | the `onOpenFolder: () -> Unit` parameter of `ProjectsHubAddSheet` |
| 3 | `FileManagerScreen.kt:1077-1080` | the `onOpenFolder = { … }` argument in the sheet call |
| 4 | `FileManagerScreen.kt:180-188` | `folderImportLauncher` (and its now-unused `OpenDocumentTree` import) |
| 5 | `FileManagerViewModel.kt:357-382` | `importFolder()` |
| 6 | `ProjectTransfer.kt:20-29` | `copyDocumentTree()` |
| 7 | `ProjectTransfer.kt:313-345` | `copyDocumentChildren()` |
| 8 | `strings.xml:348-349` | `hub_sheet_folder`, `hub_sheet_folder_subtitle` |
| 9 | `docs/chat-phase43/PART_43_1_*.md`, `PART_43_2_*.md` | **already deleted** (2026-09-12); the tombstone README stays |
| 10 | `docs/PHASE38_43_ROADMAP.md` | Phase 43's row marked ❌ CANCELLED, pointing at the tombstone |
| 11 | new | a source-scan test pinning that `OpenDocumentTree` and `copyDocumentTree` never return |

**After step 7, verify with grep, not by eye:**

```text
grep -rn "OpenDocumentTree\|copyDocumentTree\|copyDocumentChildren\|importFolder\|onOpenFolder\|hub_sheet_folder" app/src
→ 0 hits
```

## What must NOT be deleted (the honest boundary)

| Stays | Where | Why |
|---|---|---|
| Import ZIP (into a new project) | `zipImportLauncher`, `importZip` (`FileManagerViewModel.kt:424+`) | different feature, budgeted and tested (`MAX_ZIP_ENTRIES`, `MAX_ZIP_ENTRY_BYTES`, `finally { temporaryZip.delete() }`) |
| Import file (into the open project) | `fileImportLauncher`, `importFile` | single-document copy, no recursion, not part of 43 |
| Export ZIP / Share as ZIP | `exportLauncher`, `ProjectTransfer.exportZipToCache` | the way a user gets code *out* |
| Export all projects | `backupExportLauncher` (Phase 42.3) | the uninstall-eats-your-work answer |
| "Open with CodeC" | `AndroidManifest.xml` `ACTION_VIEW`/`ACTION_SEND` filters + `IncomingImportBridge` (Phase 24.7) | a *shared* file is a single document, not a tree walk |
| `DocumentsContract` usage in general | `ProjectTransfer` (ZIP + single doc) | still used; only the *tree* walk goes |
| The three other `+`-sheet rows | `FileManagerScreen.kt:1450-1473` | New project / Clone / Import ZIP |

## The one thing to check before deleting

`rule.md` §4.2 (evidence before action) applies even to a deletion: confirm on a
device that the row the owner means is the `+`-sheet row and not something else
that says "folder" (the editor drawer's *New folder* action must obviously
survive). Two minutes on the phone, then delete.

## Exit condition

```text
1. grep = 0 hits for the eight identifiers above in app/src.
2. The `+` sheet shows three rows; nothing in the app offers a folder picker.
3. New project / Clone / Import ZIP / Import file / Export / Export all /
   "Open with CodeC" all still work (device spot-check, one tap each).
4. The editor drawer's "New folder" still works (it is a different feature with
   a similar name).
5. CI green: `:app:assembleDebug :app:testDebugUnitTest :app:lintDebug` — no test
   existed for the deleted walk, so nothing is deleted from app/src/test.
6. `FolderImportRemovedTest` (new) passes and would fail if any of the eight
   identifiers returned.
PASS = all six.
```

## Tests (plan)

- `FolderImportRemovedTest` (source scan, `RepoFiles.mainKotlinSources()` —
  the existing helper the audit tests use): asserts zero occurrences of
  `OpenDocumentTree`, `copyDocumentTree`, `copyDocumentChildren`,
  `importFolder`, `onOpenFolder`, `hub_sheet_folder` in `app/src/main`, and that
  `strings.xml` has neither key. This is the pin that keeps a future "let's add
  open-folder back" from landing silently — the same role `SettingsAuditTest`
  plays for Settings.
- Existing `ProjectTransferTest` / `ProjectTransferExportAllTest` keep passing
  untouched (they cover the ZIP paths, which stay).

## Sources (record)

- CodeC 2026-09-12 — every file:line above, plus the greps quoted.
- [`../chat-phase43/README.md`](../chat-phase43/README.md) — why the feature is
  cancelled rather than fixed (one-way copy, unbounded recursion, no persisted
  grant).
- `docs/PHASE44_50_UX_RESEARCH.md` §4.3.

## Deferred / rejected with reasons

- **"Fix it instead of deleting it" (old Phase 43.1)** — rejected by the owner,
  and defensible on the merits: the bounded walk would still deliver a one-way
  copy that a user cannot tell from a link. The crash class disappears with the
  caller.
- **Hiding the row behind a flag** — a hidden feature is still shipped code with
  a crash path; deletion is the honest option.
- **Keeping `copyDocumentTree` "for later"** — dead code with an unbounded
  recursion is a liability; git history is the archive.
