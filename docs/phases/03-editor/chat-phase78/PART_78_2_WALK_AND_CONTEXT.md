# Part 78.2 — the walk, the dirty-buffer contract, and the third source

**Status: ✅ IMPLEMENTED 2026-10-01 on `arena/01a0f5d2-codec`.**
Files: [`AiProjectReader.kt`](../../../../app/src/main/java/com/codeci/ide/ui/ai/AiProjectReader.kt) (230 lines, `java.io` only) ·
[`AiContext.kt`](../../../../app/src/main/java/com/codeci/ide/ui/ai/AiContext.kt) (365 lines, +207) ·
[`AiViewModel.kt`](../../../../app/src/main/java/com/codeci/ide/ui/ai/AiViewModel.kt) (+81).
Tests: [`AiProjectReaderTest.kt`](../../../../app/src/test/java/com/codeci/ide/AiProjectReaderTest.kt) — **14 cases**; `AiProjectFilesTest` also covers `fromProject`.

---

## 1. The split that keeps this host-testable

| Piece | Reads | Decides | Android? |
|---|---|---|---|
| `AiProjectReader` | the disk | nothing — it asks the policy | `java.io` only |
| `AiProjectFiles` | nothing | include/exclude, rank, pack | no |
| `AiContextBuilder.fromProject` | nothing | refuse or render | no |
| `AiViewModel.askProject` | the view model's own state | when to gather | yes (coroutines) |

`AiProjectReader` deliberately imports only `java.io.File`, so the whole walk
is exercised on the host JVM against a real temporary tree — no Robolectric.
`AiLevel2WiringTest` pins that: `java.nio`, `isSymbolicLink`, `readNBytes`,
`readAllBytes`, `walkTopDown` and `toPath()` are all banned in that file.

---

## 2. The walk

Breadth-first with an explicit `ArrayDeque` — no recursion, so a deep tree
cannot overflow the stack, and the entry cap is checked in one place.

For every child, in this order:

1. **entry cap** — at `AiProjectFiles.MAX_ENTRIES` (4 000) the walk stops and
   sets `hitEntryCap = true`, which the preview then says out loud;
2. **symlink** — `isSymlink` is the minSdk-24-safe
   `file.canonicalPath != file.absolutePath`, copied from
   `ProjectSearch.kt:181-182`. **Not** `Files.isSymbolicLink` (API 26+;
   CodeC's minSdk is 24 — the same class of mistake as `readNBytes`);
3. **root containment** — the child's canonical path must start with the
   canonical root's, so a symlink or a `..` can never pull in another project
   or `/sdcard` (`02_WHOLE_PROJECT_CONTEXT.md`: *"Do not crawl outside the
   selected project root, follow symlinks outside it, or include other CodeC
   projects"*). This mirrors the containment check `ProjectManager.project()`
   already uses (`ProjectManager.kt:33-38`);
4. **directory** — pruned when `AiProjectFiles.isExcludedDirectory`, else queued;
5. **file** — counted, then classified as `SECRET` / `NOT_TEXT` / candidate.

Only the **shortlist** is read: `AiProjectFiles.shortlist` ranks paths by name
alone and keeps 12, plus the open file. A phone project can hold thousands of
files and reading all of them to send five is the wrong trade. Each read is
capped at `MAX_READ_CHARS` (24 000) characters and skipped entirely above
`MAX_FILE_BYTES` (512 KB, the same guard `ProjectSearch.MAX_FILE_BYTES` uses).
Binary is the NUL-in-the-first-8 KB check.

Every path handed back is `child.relativeTo(root)` with `/` separators. **No
absolute device path ever leaves** — the same rule `fromRunOutput` enforces
with `pathLabels` (`AiContext.kt:90-96`).

`scan()` never throws: an unreadable or missing root answers an empty `Scan`.

---

## 3. The dirty-buffer contract

`02_WHOLE_PROJECT_CONTEXT.md`, "Editor consistency": *"Before context
assembly, either use the current editor buffer for that file or require an
explicit save; never send stale disk contents while implying they are the
visible unsaved version."*

Implemented as the first option, and labelled:

- `EditorScreen` passes the live buffer (`openText = buffer.text`,
  `openDirty = viewModel.isDirty.value`) with the active tab's
  project-relative path;
- when the walked path is that file **and** it is dirty, the buffer text is
  used instead of the disk copy;
- the candidate carries `fromBuffer = true`, which becomes
  `AiSentFile.fromBuffer`, which renders `[unsaved edits]` **inside the sent
  text** and `, includes unsaved edits` in the preview line.

So a dirty file is never described from stale bytes, and the model is told the
text it is reading is not what is on disk. A dirty buffer for a *different*
file leaks nowhere — `AiProjectReaderTest` pins that.

---

## 4. The third source

```
enum class AiSource { SELECTION, RUN_OUTPUT, PROJECT }
```

`AiPrompt` gained one nullable field, `project: AiProjectSummary?`, plus two
small data classes (`AiSentFile`, `AiProjectSummary`). **No existing field
changed meaning**, so the two Level 1 sources are byte-for-byte unchanged.

`AiContextBuilder.fromProject` reads no file and walks no directory — it takes
an already-packed `Plan` and either refuses or renders. Two different refusals,
told differently:

- `NO_PROJECT_FILES` — the project has no readable code/text file;
- `PROJECT_TOO_LARGE` — candidates existed but nothing useful would fit.

**The D4 invariant survives untouched, and that is the design's whole point.**
The file list, the cut marks, the unsaved marks and the left-out counts are all
rendered *inside* `AiPromptText.userText`. `GeminiRequest.body(prompt)` builds
the request from `prompt.systemInstruction` and `prompt.userText`
(`GeminiRequest.kt:45-49`) — the same two strings `SentText` draws. So "what
you saw" and "what was sent" stay one value with **zero** changes to the
request path, the key path or the streaming path.

`AiSource.PROJECT` renders without a second code fence: `projectBody` already
fences each file, so `userText` appends it directly instead of wrapping it
again.

---

## 5. The view-model path

```
askProject(question, openPath, openText, openDirty)
  guard: not STREAMING, not already gathering
  guard: question length, project present
  gathering = true
  IO: ProjectManager(...).project(name)?.root      <- never a constructed path
  IO: AiProjectReader.scan(root, q, openPath, openText, openDirty)
  AiProjectFiles.plan(scan.candidates, q, openPath)
  AiContextBuilder.fromProject(...)
  gathering = false
  preview(result)          <- the SAME gate the other two sources use
```

`askProject` ends in `preview()`. It never touches `client`. `AiLevel2WiringTest`
pins all three: `askProject` contains `preview(result)`, does **not** contain
`client.stream(`, and the file still has **exactly two** `client.stream(` call
sites (`send()` and the content-free `testConnection()`). `cancelGather()` and
`clear()` cancel the walk; `onCleared()` cancels it too.

---

## 6. Tests

`AiProjectReaderTest` — 14 cases, all against real temporary trees: code files
found; credential files never offered and always counted; **the secret text
itself never appears in any candidate**; build/VCS pruning; file *and*
directory symlinks skipped (including one escaping the root); no absolute path
leaks; dirty buffer used and labelled; clean buffer falls back to disk; a dirty
buffer for another file does not leak; missing root answers empty; the 4 005-file
entry cap trips; an oversized file is never opened; an empty project scans to
nothing; the binary/line helpers on their own.

Pre-validated in-sandbox before CI: **30/30** walker assertions on the local
kotlinc/JRE harness, on a tree containing `.env`, `.env.production`,
`.npmrc`, `node_modules`, `.git`, a NUL file, a file symlink and a directory
symlink escaping the root.

## 7. Deferred / rejected with reasons

- **`Files.isSymbolicLink` / `java.nio.file`** — rejected: API 26+, minSdk is 24.
- **Reading every file to rank by body** — rejected: unbounded IO on a phone;
  the path-only shortlist bounds it at 12 reads.
- **Requiring a save instead of using the buffer** — rejected: it would add a
  modal step and still not tell the model the text is unsaved. The buffer is
  used and labelled instead.
- **A background index** — rejected (D6, and the Level 2 spec's *"Do not index
  in the background without an explicit, documented policy"*).
