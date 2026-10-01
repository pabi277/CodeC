# Part 79.2 — Task-level preimage journal, atomic apply, and editor synchronization (`AiEditApplier`)

> **Status:** 🚧 IMPLEMENTED 2026-10-01 · **Cost:** `[client-only]` · **Effort:** M
> **Owner decision (2026-10-01):** D6 amendment — 1-task disk journal in `noBackupFilesDir/ai/undo/` (outside backups & project tree), capped at ≤ 5 files / ≤ 256 KB, surviving process death, deleted on undo, next task, project delete, or key delete.

---

## 1. First move: evidence, not code

Read on `main` @ `bd1aa06` (2026-10-01):

| Read | Fact |
|---|---|
| `res/xml/backup_rules.xml:40` & `data_extraction_rules.xml:24-28` | Only `files/CodeC/projects` is backed up. `noBackupFilesDir` is never backed up and sits outside the project tree so Git never sees it as an untracked file. |
| `ui/ai/AiKeyStore.kt:33-35, 146-153` | `AiKeyStore` already uses `File(context.applicationContext.noBackupFilesDir, "ai")`. `deleteKey()` removes the key blob and terms acceptance. |
| `ui/projects/ProjectManager.kt:64-67` | `deleteProject(name)` deletes `info.root` recursively. |
| `ui/projects/ProjectPathUtils.kt:65-78` | `resolveInside(root, relativePath)` verifies canonical path containment under `root.canonicalFile`. |
| `ui/viewmodels/EditorViewModel.kt:2639-2655` | `writeProjectFile` checks `GitDiscardEditors.blocks(info.root, safe)` and writes `LineEndings.toNative(text, lineEnding)`. |
| `ui/viewmodels/EditorViewModel.kt:2335-2360, 2737-2765` | `deleteFileEntry` and `reloadDiscardedFile` show the exact sequence needed to keep `_openTabs`, `_activeTabPath`, `_codeText`, `_activeLineEnding`, `_isDirty`, `undoManagers`, `refreshFileEntries`, and `refreshGitMeta` consistent after disk changes. |
| `app/src/test/java/com/codeci/ide/AiHelperWiringTest.kt:35-40` | Every file under `ui/ai` is forbidden from calling `writeText(`, `saveFile`, `runCode`, `ProcessBuilder`, `Runtime.getRuntime`, or `updateCode(`. |

---

## 2. Design (`ui/projects/AiEditApplier.kt`)

Lives in `com.codeci.ide.ui.projects` (`java.io` only, no Android framework classes, so 100% host-testable on a plain JVM against real temporary directories).

### 2.1 Containment and ancestor symlink check

Before reading or writing any target path:
1. Validates `ProjectPathUtils.sanitizeRelativePath(relativePath)` and `ProjectPathUtils.resolveInside(projectRoot, relativePath)`.
2. Rejects any path where `AiProjectFiles.isSecretLike(name)`, `!AiProjectFiles.isTextFile(name)`, or any directory segment is in `AiProjectFiles.isExcludedDirectory(seg)` or `.codec` / `.git`.
3. Walks every ancestor segment from `projectRoot` down to the target file and rejects if any existing segment has `canonicalPath != absolutePath` (the minSdk-24-safe symlink check).

### 2.2 The 1-task preimage journal (`noBackupFilesDir/ai/undo/<project>/task.journal`)

- **Location:** `File(noBackupFilesDir, "ai/undo/<sanitizedProjectName>/task.journal")`.
- **Caps:** `MAX_JOURNAL_FILES = 5`, `MAX_JOURNAL_BYTES = 256 * 1024` (256 KB total preimage bytes). If the preimages exceed `MAX_JOURNAL_BYTES`, apply refuses before writing any project file.
- **Format:** pure, deterministic line/hex-encoded records (no `org.json`, so JVM unit tests need no Robolectric):
  - Header line: version, projectName, timestampMs, entryCount
  - Per entry: `relativePath`, `op` (`MODIFY`, `CREATE`, `DELETE`), `existedBefore: Boolean`, `preimageBytes` (exact raw bytes on disk before apply), `appliedBytes` (exact raw bytes written by apply, empty for `DELETE`).
- **Write-ahead:** written atomically via `task.journal.tmp` → `renameTo(task.journal)` **before** any project file is modified.

### 2.3 Atomic-as-practical apply & automatic rollback on failure

`AiEditApplier.apply(...)`:
1. Verifies non-empty selected edits, path safety, symlink absence, and `GitDiscardEditors.blocks(root, path) == false`.
2. Verifies every selected edit's baseline matches the current live state (`liveBuffers[path]` when dirty, else disk text normalized to LF, and `file.exists() == baseline.exists`). If any file drifted, returns `AiApplyOutcome.BaselineConflict(stalePaths)` without touching disk.
3. Reads exact pre-apply `ByteArray` for each target file, checks `MAX_JOURNAL_BYTES`, and writes the write-ahead journal.
4. Applies each edit in order:
   - `MODIFY`: preserves the existing file's native line ending (`LineEndings.detect(oldRawText)`) via `LineEndings.toNative(edit.newContent, ending)`, written via a sibling `.codec_ai_tmp` or direct write.
   - `CREATE`: creates parent directories inside `projectRoot` if needed and writes `edit.newContent` (LF).
   - `DELETE`: deletes the target file (`file.delete()`).
5. If any step throws or fails on disk, immediately restores all previously touched files in that batch from their captured `preimageBytes` (and deletes any newly created files), deletes the journal, and returns `AiApplyOutcome.Failed(reason, rolledBack = true)`.

### 2.4 Task undo & post-apply user-edit conflict check

`AiEditApplier.undo(projectRoot, undoRoot, projectName, dirtyBuffers, force)`:
1. Loads the saved `AiTaskJournal` for `projectName` (or returns `AiUndoOutcome.NothingToUndo`).
2. Re-verifies every journaled path stays inside `projectRoot` with no symlinks.
3. Unless `force == true`, checks whether the user edited any of the task's files *after* the AI task applied them:
   - If `dirtyBuffers[path]` is true in the editor, or
   - For `MODIFY` / `CREATE`: the file on disk no longer exists or `!file.readBytes().contentEquals(entry.appliedBytes)`, or
   - For `DELETE`: the file was recreated on disk (`file.exists()`).
   If any file was modified after apply, returns `AiUndoOutcome.UserEditedConflict(conflictPaths)` without touching disk so the UI can ask the user to confirm.
4. Restores every entry in reverse order:
   - `existedBefore == true` (`MODIFY` or `DELETE`): recreates parent dirs if needed and writes `entry.preimageBytes` **byte-for-byte**.
   - `existedBefore == false` (`CREATE`): deletes the file and prunes empty parent directories created under `projectRoot`.
5. Deletes `task.journal` and returns `AiUndoOutcome.Restored(paths)`.

### 2.5 Cleanup hooks (D6 retention)

- **On undo:** `task.journal` is deleted.
- **On next task apply:** the project's `task.journal` is replaced.
- **On project delete:** `ProjectManager.deleteProject(name)` calls `AiEditApplier.clearProjectJournal(appContext.noBackupFilesDir, safe)`.
- **On API key delete:** `AiKeyStore.deleteKey()` calls `AiEditApplier.clearAllJournals( noBackupFilesDir )`.

### 2.6 `EditorViewModel.syncAfterAiFileChanges`

Synchronizes the editor on the main thread after apply or undo:
- Cancels `autoSaveJob` and stashes the active tab buffer.
- For each changed/created/restored file:
  - If open in `_openTabs`: reads the file from disk, detects `LineEndings`, normalizes to LF, pushes the previous buffer onto that tab's `EditorUndoManager` (`recordChange`), updates the tab (`buffer`, `savedText = normalized`, `lineEnding`), and if it is the active tab updates `_codeText`, `_activeLineEnding`, `_isDirty = false`, and `syncUndoFlags`.
  - If no tab is open in the editor at all and a file was created/restored, opens the first existing changed file.
- For each deleted file:
  - Removes its tab from `_openTabs` and its key from `undoManagers`; if the active tab was deleted, activates the next remaining tab (or clears the buffer when no tabs remain, matching `deleteFileEntry`).
- Calls `refreshFileEntries(appContext)` and `refreshGitMeta(appContext)` — never staging or committing to Git.

---

## 3. Tests

- `AiEditApplierTest.kt` — **16 cases** against real temporary project & `noBackup` directories:
  1. `MODIFY`, `CREATE`, and `DELETE` apply cleanly and write the journal in `noBackup/ai/undo/<project>/`;
  2. `undo` restores modified files byte-for-byte (including CRLF line endings), recreates deleted files, and removes created files;
  3. journal survives across fresh applier calls (simulating process death) and is deleted once undone;
  4. nothing is written inside `projectRoot` for the journal (no `.codec` or untracked file pollution);
  5. baseline drift on disk before apply returns `BaselineConflict` and leaves all files untouched;
  6. unsaved dirty editor buffer drift before apply returns `BaselineConflict` and leaves all files untouched;
  7. post-apply disk edit by the user triggers `UserEditedConflict` on undo when `force = false`;
  8. post-apply dirty editor buffer triggers `UserEditedConflict` on undo when `force = false`;
  9. `undo(..., force = true)` after user confirmation restores the pre-AI bytes and clears the journal;
  10. path traversal (`../escape.c`) and symlink escapes (file symlink and directory symlink) are refused;
  11. secret-like file (`.env`) and `.git/` / `.codec/` targets are refused by the applier even if passed directly;
  12. subset selection applies only `selected = true` files and journals only those files;
  13. a second applied task replaces the first task's journal for that project;
  14. `clearProjectJournal` removes only that project's journal; `clearAllJournals` removes all;
  15. preimage exceeding `MAX_JOURNAL_BYTES` (256 KB) is refused before any project file is touched;
  16. mid-batch write failure rolls back already-applied files in the batch from their preimages.
