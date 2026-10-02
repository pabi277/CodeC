# Phase 79 — AI Level 3: proposed edits, diff review, and task-level undo

> **Status: ✅ MERGED to `main` @ `df2c5d4` (PR #105, 2026-10-02; post-merge CI
> `36934250423` green, release APK `7,079,820 B`).** Branch CI `36898288949` green on `607ccb7`. Device-verified on the owner's phone (*"All device tests passed"* → *"Ok merge it"*).
>
> Parts:
> - [79.1 Structured edit proposals, validation, and local unified diff](PART_79_1_EDIT_PROPOSAL_AND_DIFF.md)
> - [79.2 Task-level preimage journal, atomic apply, and editor sync](PART_79_2_JOURNAL_AND_APPLIER.md)
> - [79.3 Diff review UI, per-file checkboxes, conflict prompts, and undo surface](PART_79_3_REVIEW_AND_UNDO_UI.md)
> - [Device round (`DEVICE_ROUND.md`)](DEVICE_ROUND.md)
>
> Baseline for every claim below: **`main` @ `bd1aa06`** (PR #104, Phase 78
> merge commit; post-merge CI `36884609111` green), read **2026-10-01** in the
> session sandbox. Every repository claim is a `file:line` read from that tree.
> Builds on [Phase 76](../chat-phase76/README.md) (Level 1),
> [Phase 77](../chat-phase77/README.md) (phone AI UI), and
> [Phase 78](../chat-phase78/README.md) (whole-project context).

---

## 1. Binding owner decisions (2026-10-01, answered before coding)

Level 3 (`docs/roadmaps/ai-integration/03_EDIT_REVIEW_AND_UNDO.md`) collides by
design with Level 0's initial read-only and zero-persistence rules
(`00_LEVEL0_DECISION_RECORD.md:27-28, 59-61`). Before any Phase 79 code was
written, the owner answered all four questions explicitly:

| # | Question | Owner decision (2026-10-01) |
|---|---|---|
| **D1 amendment** | May the AI write project files, and what stays off? | **Yes — diff-gated project writes only.** AI may modify, create, or delete text files inside the open CodeC project **only after** the user reviews and approves the locally computed diff. Run, terminal, package installation, and automatic Git staging/commits remain strictly prohibited. |
| **D6 amendment** | Where does the task-undo preimage journal live, how big is it, when is it deleted, and what happens on process death? | **1-task disk journal in `noBackupFilesDir/ai/undo/`.** Outside `files/CodeC/projects/` (never backed up by `backup_rules.xml`, never pollutes the user's project tree or Git status). Capped at **≤ 5 files** and **≤ 256 KB** total preimage bytes for the last applied AI task of that project. **Survives process death** so *Undo AI changes* works after an app restart. Deleted on undo, when replaced by a newer applied AI task, when the project is deleted, or when the API key is deleted. |
| **Level 3 scope** | Which file operations and review controls are included? | **Full Level 3 scope:** `MODIFY`, `CREATE`, and `DELETE` of project text files, **per-file checkboxes** in the diff review so the user can apply all or a subset of proposed files, baseline conflict detection before apply and before undo, and a **Propose edits** chip in the chat sheet. |
| **Phase 78 bookkeeping** | How to handle `ebdd2b4` (the unmerged post-PR-#104 bookkeeping commit on `arena/01a0f5d2-codec`)? | **Fold the Phase 78 PR #104 merge bookkeeping (`bd1aa06`, post-merge CI `36884609111`, partial device round note) into Phase 79's docs** and start Phase 79. |

---

## 2. Evidence on `main` @ `bd1aa06` (2026-10-01)

### 2.1 Why `EditorUndoManager` is not agent-task rollback

| Read | Fact |
|---|---|
| `ui/editor/EditorUndoManager.kt:21-26` | `EditorUndoManager(maxHistory = 100, coalesceWindowMs = 600L)` holds an in-memory `ArrayDeque<TextFieldValue>` per open tab. |
| `ui/editor/EditorUndoManager.kt:33-46, 49-56, 67-71` | `recordChange`, `undo`, `redo`, `reset`, `undoDepth` operate on a single tab's `TextFieldValue` buffer. |
| `ui/viewmodels/EditorViewModel.kt:469, 1599, 1669, 1840, 2340, 2751` | `undoManagers` is a `HashMap<String, EditorUndoManager>` keyed by `relativePath`. It is cleared on project switch (`:1599`), mode switch (`:1669`), tab eviction (`:1770`), tab close (`:1840`), file deletion (`:2340`), and Git discard (`:2751`), and vanishes on process death. |

**Consequence.** An AI change set can modify files that are not open in tabs,
create a new file, delete an existing file, or be undone after process death.
Presenting `EditorUndoManager` as task undo would violate the Level 3 spec
(`03_EDIT_REVIEW_AND_UNDO.md:9-14`). Task undo requires its own preimage journal.

### 2.2 Existing path-containment and symlink guards to reuse

| Read | Fact |
|---|---|
| `ui/projects/ProjectPathUtils.kt:17-18` | `sanitizeProjectName(name)` rejects blank, `.`, `..`, `/`, `\`, NUL, and control chars. |
| `ui/projects/ProjectPathUtils.kt:42-48` | `sanitizeRelativePath(path)` normalizes `\` to `/`, rejects leading `/` or `~`, and validates every path segment with `sanitizeArchiveSegment`. |
| `ui/projects/ProjectPathUtils.kt:65-78` | `resolveInside(root, relativePath): File?` resolves against `root.canonicalFile` and verifies `candidate.path == canonicalRoot.path || candidate.path.startsWith(canonicalRoot.path + File.separator)`. |
| `ui/editor/ProjectSearch.kt:181-182, 190-201` | `isSymlink(file)` is `file.canonicalPath != file.absolutePath` (minSdk 24 safe, no `java.nio.file.Files.isSymbolicLink`), and `ProjectPathGuard.childOf` enforces canonical containment. |
| `ui/ai/AiProjectFiles.kt:71-89, 121-130` | `isSecretLike(name)` refuses `.env`, `.env.*`, `.npmrc`, keys, and keystores; `isExcludedDirectory(name)` refuses `.git`, `build`, `node_modules`, and hidden directories (including `.codec`). |

### 2.3 Existing editor save, tab reload, and diff seams

| Read | Fact |
|---|---|
| `ui/editor/LineEndings.kt:21-41` | `LineEndings.detect(text)` detects `LF` vs `CRLF`; `normalizeToLf(text)` normalizes for editor buffers; `toNative(text, ending)` restores native line endings on write. |
| `ui/viewmodels/EditorViewModel.kt:2639-2655` | `writeProjectFile` checks `GitDiscardEditors.blocks(info.root, safe)`, resolves with `ProjectPathUtils.resolveInside`, creates parent dirs, and writes `LineEndings.toNative(text, lineEnding)`. |
| `ui/viewmodels/EditorViewModel.kt:2313-2360` | `deleteFileEntry` cancels `autoSaveJob`, filters deleted tabs out of `_openTabs`, removes `undoManagers` keys, activates the next tab (or clears the buffer when the last tab was deleted), and calls `refreshFileEntries`. |
| `ui/viewmodels/EditorViewModel.kt:2737-2765` | `reloadDiscardedFile` reloads an open tab from disk with `LineEndings.detect` + `normalizeToLf`, updates `_codeText` and `_isDirty = false` when active, and refreshes `refreshFileEntries` + `refreshGitMeta`. |
| `ui/projects/GitDiff.kt:10-28` | `DiffEngine.compute(oldText, newText): List<DiffLine>` computes a pure-Kotlin LCS line diff (`DiffOp.CONTEXT`, `ADD`, `REMOVE` with 1-based `oldNumber` and `newNumber`). |

### 2.4 Existing wiring pins that shape the design

| Read | Fact |
|---|---|
| `app/src/test/java/com/codeci/ide/AiHelperWiringTest.kt:35-40` | Bans `writeText(`, `saveFile`, `runCode`, `ProcessBuilder`, `Runtime.getRuntime`, and `updateCode(` across every `.kt` file in `ui/ai`. |
| `app/src/test/java/com/codeci/ide/AiHelperWiringTest.kt:75-80` | Bans `rememberSaveable`, `SavedStateHandle`, `writeText`, and `Properties` in `AiViewModel.kt`. |
| `app/src/test/java/com/codeci/ide/AiLevel2WiringTest.kt:85-90` | Slices `AiViewModel.kt` with `substringAfter("fun askProject(").substringBefore("fun cancelGather(")` and pins `Regex("client\\.stream\\(").findAll(vm).count() == 2`. |
| `app/src/test/java/com/codeci/ide/AiPolicyTest.kt:124` | Pins `AiCopy.READ_ONLY.contains("never changes files")` and `AiContextBuilderTest.kt:79` pins `AiPromptText.SYSTEM_INSTRUCTION.contains("cannot see or change any files")`. |

**Architectural decision for `AiHelperWiringTest`:**
- All pure proposal parsing, path/target validation, baseline conflict checks, and unified diff formatting live in `ui/ai/AiEditProposal.kt` (100% pure Kotlin, zero `java.io`, zero file writes).
- All disk preimage journaling, atomic file application, and rollback live in `ui/projects/AiEditApplier.kt` (alongside `ProjectManager`, `ProjectPathUtils`, `FileTreeRepository`, and `GitDiff`), invoked by `AiViewModel` and synchronized with `EditorViewModel`.
- `ui/ai` therefore continues to have **zero** direct `writeText(` / `saveFile` / `runCode` / `ProcessBuilder` / `Runtime.getRuntime` / `updateCode(` calls, and `AiHelperWiringTest` is extended with a comment recording the Phase 79 D1/D6 amendment and pinning that `AiEditApplier` is the single diff-gated write seam.

---

## 3. Parts overview

| Part | Doc | Files | Tests |
|---|---|---|---|
| **79.1** | [PART_79_1_EDIT_PROPOSAL_AND_DIFF.md](PART_79_1_EDIT_PROPOSAL_AND_DIFF.md) | `ui/ai/AiEditProposal.kt` (pure parser, path/secret/op validator, search-replace + full-file applier, unified diff formatter, per-file selection, baseline staleness checker) | `AiEditProposalTest` (**26 cases**) |
| **79.2** | [PART_79_2_JOURNAL_AND_APPLIER.md](PART_79_2_JOURNAL_AND_APPLIER.md) | `ui/projects/AiEditApplier.kt` (`java.io` preimage journal in `noBackupFilesDir/ai/undo/`, containment + symlink guard, atomic apply with mid-batch rollback, post-apply conflict check + byte-for-byte undo) + `EditorViewModel.syncAfterAiFileChanges` + cleanup hooks in `ProjectManager.deleteProject` & `AiKeyStore.deleteKey` | `AiEditApplierTest` (**16 cases**) |
| **79.3** | [PART_79_3_REVIEW_AND_UNDO_UI.md](PART_79_3_REVIEW_AND_UNDO_UI.md) | `AiSource.PROPOSE_EDITS` in `AiContext.kt`, `AiCopy.kt`, `AiViewModel.kt`, `AiChatSheet.kt`, `EditorScreen.kt` | `AiLevel3WiringTest` (**16 cases**) |

---

## 4. Exit condition (from `03_EDIT_REVIEW_AND_UNDO.md`)

1. Wrong paths, path traversal (`..`), absolute paths, symlink escapes, secret-like files (`.env`, `.npmrc`, keys), excluded directories (`.git`, `.codec`, `node_modules`), malformed edit blocks, and full-file overwrites of truncated (`cut`) files are **rejected**.
2. Model prose is never treated as a patch; only structured `<<<CODEC_EDIT ...>>>` blocks are parsed and validated, and unified diffs are computed locally via `DiffEngine`.
3. Rejecting a proposal leaves project files untouched.
4. Before applying, baseline hashes/contents are verified against current disk and open editor buffers; if the user edited a target file since the proposal was built, apply pauses with a conflict prompt instead of overwriting.
5. Applying selected files writes a preimage journal in `noBackupFilesDir/ai/undo/<project>/` first, applies changes, and synchronizes open tabs, dirty markers, autosave, the file tree, and Git status badges — without ever staging or committing to Git.
6. **Undo AI changes** restores every changed, created, or deleted file byte-for-byte for that task, survives process death, and checks for post-apply user edits before restoring so newer user work is never silently destroyed.
7. `Build APK` CI green; `DEVICE_ROUND.md` written (carrying forward the unrun Phase 78 rows A, C4/C5, D–F, G1–G5 alongside Phase 79's Level 3 rows); stopped at the §3 merge gate.
