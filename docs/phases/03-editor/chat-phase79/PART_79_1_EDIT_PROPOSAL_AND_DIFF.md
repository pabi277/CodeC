# Part 79.1 — Structured edit proposals, validation, and local unified diff (`AiEditProposal`)

> **Status:** 🚧 IMPLEMENTED 2026-10-01 · **Cost:** `[client-only]` · **Effort:** M
> **Owner decision (2026-10-01):** Approve D1 amendment (diff-gated writes only; no auto-Git/run) + Full Level 3 scope (`MODIFY`, `CREATE`, `DELETE` with per-file selection in diff review).

---

## 1. First move: evidence, not code

Read on `main` @ `bd1aa06` (2026-10-01):

| Read | Fact |
|---|---|
| `docs/roadmaps/ai-integration/03_EDIT_REVIEW_AND_UNDO.md:29` | *"Never treat model-produced prose as a patch. Parse/validate structured edits, then compute the diff locally."* |
| `ui/projects/GitDiff.kt:10-28` | `DiffEngine.compute(oldText, newText): List<DiffLine>` already computes a pure-Kotlin line diff with `DiffOp.CONTEXT`, `DiffOp.ADD`, `DiffOp.REMOVE` and 1-based line numbers (`oldNumber`, `newNumber`), falling back to a block replace above `LCS_LIMIT = 1000`. |
| `ui/projects/ProjectPathUtils.kt:34-48` | `sanitizeRelativePath` rejects leading `/` or `~`, `.` and `..`, NUL, and control characters. |
| `ui/ai/AiProjectFiles.kt:92-146` | `isSecretLike(name)` refuses credential-shaped filenames; `isTextFile(name)` allows recognised code/text files; `isExcludedDirectory(name)` refuses `.git`, `node_modules`, `build`, and hidden folders (except `.github`/`.vscode`). |
| `ui/ai/AiProjectFiles.kt:364-391` | `sliceFor` sets `cut = true` when a file was truncated to fit `MAX_FILE_CHARS = 3_000`. Replacing an entire file when the model only saw its first `linesSent` lines would silently delete the unsent tail of the file. |

---

## 2. Design (`ui/ai/AiEditProposal.kt`)

Pure Kotlin (no `java.io`, no Android), host-tested on the JVM.

### 2.1 Baseline snapshot (`AiFileBaseline`)

Captured when `AiSource.PROPOSE_EDITS` gathers context:
- `path: String` (normalized project-relative path)
- `exists: Boolean`
- `content: String` (LF-normalized text at proposal time, empty when `!exists`)
- `cut: Boolean` (true if only a prefix of the file was sent in the prompt)
- `fromBuffer: Boolean` (true if captured from an unsaved editor buffer)

### 2.2 Structured block grammar (never prose as a patch)

`AiPromptText.EDIT_SYSTEM_INSTRUCTION` instructs the model to explain briefly and emit changes only inside explicit delimiters:

```text
<<<CODEC_EDIT path="src/main.c" op="modify">>>
<<<SEARCH>>>
int old_line = 1;
<<<REPLACE>>>
int new_line = 2;
<<<END_SEARCH>>>
<<<END_CODEC_EDIT>>>
```

Supported operations (`AiEditOp`):
- `MODIFY`: either one or more `<<<SEARCH>>>...<<<REPLACE>>>...<<<END_SEARCH>>>` blocks applied in order against the baseline text, OR full-file content (permitted **only** when `baseline.cut == false`, so a truncated file can never have its tail dropped).
- `CREATE`: full new file content for a path that does not yet exist in the project.
- `DELETE`: deletes an existing project text file (body inside the block must be blank).

### 2.3 Validation rules (`AiEditValidator`)

Every proposed block is validated before any diff is shown:
1. **Framing:** unclosed `<<<CODEC_EDIT`, missing `path=` or `op=`, unknown `op`, or unclosed `<<<SEARCH>>>` / missing `<<<REPLACE>>>` produces `AiProposalResult.Invalid` with a plain-English reason.
2. **Path safety:**
   - Rejects empty paths, leading `/` or `~`, drive letters (`C:`), `.` or `..` segments, NUL or control characters, and empty segments (`a//b`).
   - Rejects any path with a directory segment matching `AiProjectFiles.isExcludedDirectory(segment)` or `.codec` / `.git`.
   - Rejects any filename where `AiProjectFiles.isSecretLike(name)` is true or `!AiProjectFiles.isTextFile(name)`.
   - Rejects duplicate paths within the same proposal, proposals with `> MAX_EDIT_FILES` (5), or any resulting file exceeding `MAX_EDIT_FILE_CHARS` (24 000) or containing NUL (`\u0000`).
3. **Baseline consistency:**
   - `MODIFY` on a file not present in the captured baseline (or `!baseline.exists`) is rejected: the AI may not blindly overwrite a file it never read.
   - `MODIFY` with full-file replacement on a `baseline.cut == true` file is rejected (must use `<<<SEARCH>>>...<<<REPLACE>>>...<<<END_SEARCH>>>`).
   - `MODIFY` whose `<<<SEARCH>>>` block does not match the baseline text (or is empty) is rejected.
   - `MODIFY` that produces zero net change (`newText == baseline.content`) is rejected.
   - `CREATE` on a path that already exists on disk (`existingPaths` / `baseline.exists == true`) is rejected.
   - `DELETE` on a path that does not exist is rejected.

### 2.4 Local unified diff (`AiUnifiedDiff`)

Computed locally from `(oldText, newText)` via `DiffEngine.compute`:
- Renders standard unified diff headers (`--- a/<path>` / `--- /dev/null` and `+++ b/<path>` / `+++ /dev/null`) and `@@ -l,s +l,s @@` hunks with 2 lines of context around each change.
- Counts `addedLines` and `removedLines` per file and across the change set (`selectedAddedLines`, `selectedRemovedLines`).

### 2.5 Per-file selection & baseline staleness check

- `AiEditProposal` carries `files: List<AiProposedFileEdit>`, each with `selected: Boolean = true`.
- `toggleFile(path)` flips `selected` for that path; `selectAll(Boolean)` selects or deselects all.
- `checkStale(currentStates: Map<String, AiCurrentFileState>): List<String>` compares each **selected** file's baseline against the current editor/disk state (`exists` and LF-normalized `content`). Returns the list of paths that changed since the proposal was created (empty = safe to apply).

---

## 3. Exit condition

- Pure parser + validator + unified diff + staleness checker compile and pass host tests with zero Android dependencies.

## 4. Tests

- `AiEditProposalTest.kt` — **26 cases**:
  1. prose-only reply returns `NoProposal`;
  2. `MODIFY` with `<<<SEARCH>>>`/`<<<REPLACE>>>` applies cleanly and computes local unified diff;
  3. `MODIFY` with multiple search/replace blocks in one file;
  4. `MODIFY` with full-file replacement succeeds when `cut == false`;
  5. `MODIFY` with full-file replacement is **rejected** when `cut == true` (protects unsent tail);
  6. `MODIFY` with search/replace **succeeds** on a `cut == true` file;
  7. `CREATE` new file produces `+` diff lines and `--- /dev/null`;
  8. `DELETE` existing file produces `-` diff lines and `+++ /dev/null`;
  9. path traversal (`../secret.c`, `a/../../b.c`) is rejected;
  10. absolute and home paths (`/etc/passwd`, `~/main.c`) are rejected;
  11. secret-like target (`.env`, `.env.local`, `.npmrc`, `id_rsa`, `cert.pem`) is rejected;
  12. non-text target (`image.png`, `app.apk`, `local.properties`) is rejected;
  13. excluded directory target (`.git/config`, `.codec/project.json`, `node_modules/a.js`, `build/out.c`) is rejected;
  14. duplicate target paths in one proposal are rejected;
  15. unclosed `<<<CODEC_EDIT` block is rejected as malformed;
  16. unclosed `<<<SEARCH>>>` or missing `<<<REPLACE>>>` is rejected as malformed;
  17. unknown `op="rename"` or `op="exec"` is rejected;
  18. `MODIFY` on an unread/non-existent file is rejected;
  19. `CREATE` on an already-existing file is rejected;
  20. `DELETE` on a non-existent file is rejected;
  21. `MODIFY` with non-matching `<<<SEARCH>>>` block is rejected;
  22. `MODIFY` producing identical content (no-op) is rejected;
  23. more than `MAX_EDIT_FILES` (5) is rejected;
  24. per-file checkbox toggle updates `selectedFiles` and summary counts;
  25. baseline staleness check detects a file edited in the editor or on disk after proposal creation;
  26. baseline staleness check ignores unselected files that changed.
