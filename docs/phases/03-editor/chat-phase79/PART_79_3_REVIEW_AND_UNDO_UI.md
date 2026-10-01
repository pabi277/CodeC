# Part 79.3 — The review UI, per-file checkboxes, conflict prompts, and undo surface

> **Status:** 🚧 IMPLEMENTED 2026-10-01 · **Cost:** `[client-only]` · **Effort:** M
> **Owner decision (2026-10-01):** Full Level 3 UI in `AiChatSheet` — *Propose edits* chip, local unified diff with per-file checkboxes, baseline conflict prompt, post-apply undo conflict prompt, and truthful undo boundary copy.

---

## 1. First move: evidence, not code

Read on `main` @ `bd1aa06` (2026-10-01):

| Read | Fact |
|---|---|
| `ui/ai/AiChatSheet.kt:340-360` | `BottomBar` in `AiPhase.IDLE` renders the horizontal chip row (*Explain selection*, *Explain last error*, *Ask about the project*). |
| `ui/ai/AiChatSheet.kt:238-276` | `Conversation` renders `IDLE`, `PREVIEW`, `STREAMING`, `DONE`, and `FAILED`. |
| `ui/ai/AiParts.kt:29-48` | `SentText` uses `CodecType.codeFamily` and theme roles (`MaterialTheme.colorScheme.outlineVariant`, no `Color(0x…)`). |
| `ui/screens/EditorScreen.kt:1146-1178` | `EditorScreen` wires `AiViewModel` and `EditorViewModel` together inside `aiSheet`. |
| `app/src/test/java/com/codeci/ide/AiSurfaceWiringTest.kt:55-68, 146-154` | Pins `AiHome.kt` as setup-only, bans `BackHandler` in `AiChatSheet.kt`, bans `Color(0x…)` and `FontFamily.Monospace` across all AI surface files. |

---

## 2. Design

### 2.1 The fourth source (`AiSource.PROPOSE_EDITS`)

- `enum class AiSource { SELECTION, RUN_OUTPUT, PROJECT, PROPOSE_EDITS }` in `AiContext.kt`.
- `AiPrompt.systemInstruction`:
  - Returns `AiPromptText.SYSTEM_INSTRUCTION` for `SELECTION`, `RUN_OUTPUT`, and `PROJECT` (byte-for-byte unchanged).
  - Returns `AiPromptText.EDIT_SYSTEM_INSTRUCTION` for `PROPOSE_EDITS`, which defines the `<<<CODEC_EDIT path="..." op="modify|create|delete">>>` format and states that edits are only proposals shown as a diff for the user's approval.
- `AiViewModel.proposeEdits(question, openPath, openText, openDirty)`:
  - Requires a non-blank `question` describing the requested change (if blank, sets `notice = AiCopy.EDIT_QUESTION_REQUIRED`).
  - Scans the project on `Dispatchers.IO` via `AiProjectReader.scan` + `AiProjectFiles.plan` (the same secret-safe, bounded Level 2 walker).
  - Captures `baselines: Map<String, AiFileBaseline>` and `existingPaths: Set<String>` for the project at proposal time.
  - Lands on `preview(result)` — **never** calls `client.stream(` directly (D4).

### 2.2 Parsing the proposal when the stream finishes

In `AiViewModel.send()`:
- When `outcome is AiOutcome.Answer` and `prompt.source == AiSource.PROPOSE_EDITS`:
  - Parses and validates `outcome.text` against the captured `baselines` and `existingPaths` using `AiEditProposalParser.parse(...)`.
  - Stores the resulting `AiProposalResult` (`Proposal`, `Invalid`, or `NoProposal`) in `AiUiState.proposalResult`.

### 2.3 The diff review card in `AiChatSheet` (`AiPhase.DONE`)

When `state.proposalResult` is present:
- **`AiProposalResult.Invalid(reason)`:** shows `ErrorLine(reason)` explaining why the proposed edit block was rejected (e.g. unsafe path, secret target, truncated file full-replace, unmatched search block) and leaves project files untouched.
- **`AiProposalResult.Proposal(proposal)`:**
  - Header: `Proposed file changes` + summary (`N of M files selected · +A -R lines`).
  - Per-file card for each `AiProposedFileEdit`:
    - `Row` with `Checkbox(checked = edit.selected, onCheckedChange = { onToggleEditFile(edit.path) })`, the relative path, the operation label (`Modify`, `Create`, `Delete`), and `(+A -R)`.
    - Monospaced (`CodecType.codeFamily`) scrollable unified diff box (`edit.unifiedDiff`).
  - Baseline conflict prompt (when `state.applyConflictPaths.isNotEmpty()`):
    - Explains which file(s) changed in the editor or on disk after the proposal was generated (`AiCopy.applyConflictMessage(paths)`).
    - Offers **Rebuild proposal** (`onProposeEdits(question)`) and **Reject changes** (`onRejectProposal`). Never overwrites.
  - Action row:
    - **Apply selected (N)** (`Button`, enabled when `proposal.selectedCount > 0 && !state.applying`)
    - **Reject changes** (`OutlinedButton`, calls `onRejectProposal`, leaving files untouched).

### 2.4 Task Undo card & post-apply conflict prompt

Whenever `state.undoSummary != null` (loaded on project open from `noBackupFilesDir/ai/undo/<project>/` and updated on apply/undo):
- Renders the **Last AI change** card in `AiChatSheet`:
  - Lists the files changed by the last task (`AiCopy.undoSummaryHeader(summary)`).
  - Shows `AiCopy.UNDO_SCOPE_NOTE`: *"Undo restores these files to their exact contents before this AI change. It does not undo terminal commands, package installs, Git actions, or edits you made afterward."*
  - **Undo AI changes** (`OutlinedButton`).
- If the user edited any affected file after the AI task was applied, tapping **Undo AI changes** sets `state.undoConflictPaths` and shows the conflict prompt:
  - *"You edited <paths> after this AI change was applied. Undoing will replace your newer edits in those files."*
  - **Restore pre-AI files anyway** (`Button` → `undoLastTask(force = true)`) and **Keep my edits** (`OutlinedButton` → `dismissUndoConflict()`).

---

## 3. Tests

- `AiLevel3WiringTest.kt` — **16 cases** (source pins executed through `RepoFiles.codeOnly`):
  1. `ui/ai` still contains zero direct file-write or process-execution calls (`writeText(`, `saveFile`, `runCode`, `ProcessBuilder`, `Runtime.getRuntime`, `updateCode(`);
  2. `AiEditProposal.kt` is pure (no `java.io`, no Android imports) and computes diffs via `DiffEngine.compute`;
  3. `AiEditProposal.kt` checks `AiProjectFiles.isSecretLike`, `AiProjectFiles.isTextFile`, and `AiProjectFiles.isExcludedDirectory`;
  4. `AiEditApplier.kt` uses `ProjectPathUtils.resolveInside`, the minSdk-24 `canonicalPath != absolutePath` symlink check, and `LineEndings.toNative`;
  5. `AiEditApplier.kt` never calls `git.stage`, `git.commit`, `GitManager`, or `ProcessBuilder` (Git boundary law);
  6. the undo journal lives under `noBackupFilesDir` in `ai/undo` and is cleaned up on `deleteProject` and `deleteKey`;
  7. `proposeEdits` in `AiViewModel.kt` lands on `preview(result)` and never calls `client.stream(`;
  8. `AiViewModel.kt` still has **exactly two** `client.stream(` call sites (`send()` and `testConnection()`);
  9. `AiChatSheet.kt` offers `AiCopy.PROPOSE_EDITS`, per-file checkboxes (`onToggleEditFile`), `AiCopy.APPLY_SELECTED`, `AiCopy.REJECT_CHANGES`, and `AiCopy.UNDO_CHANGES`;
  10. `AiChatSheet.kt` renders both conflict prompts (`applyConflictPaths` and `undoConflictPaths`) and the truthful undo scope note (`AiCopy.UNDO_SCOPE_NOTE`);
  11. `EditorScreen.kt` synchronizes open tabs, buffers, file tree, and Git badges through `viewModel.syncAfterAiFileChanges`;
  12. `EditorUndoManager.kt` is not used as the AI task journal;
  13. all AI files remain free of `Color(0x…)`, `FontFamily.Monospace`, and logging (`Log.`, `AppLogger`, `println(`, `printStackTrace`);
  14. `AiEditApplier.kt` uses only minSdk-24 APIs (no `java.nio`, `isSymbolicLink`, `readNBytes`, `Files.`);
  15. `GeminiRequest.body(prompt)` still sends `prompt.systemInstruction` and `prompt.userText` with `"store":false`;
  16. no new dependency, permission, DataStore key, or Settings-screen control was added.
