# Part 80.1 — the whole-project map and the bounded tool surface

Owner row this part answers: *"have full knowledge of my code and one model will
set what to sent what not to"*. The map is what makes the *whole* project known
without sending it; the tools are how the model pulls the parts it actually
needs. This part is entirely host-testable: three new files, of which two are
pure (`java.io`-free) and one uses `java.io` only.

## Files

| File | Purity | Tests |
|---|---|---|
| `app/src/main/java/com/codeci/ide/ui/ai/AiRepoMap.kt` | pure (no `java.io`, no `android.*`) | `AiRepoMapTest` 12 |
| `app/src/main/java/com/codeci/ide/ui/ai/AiTools.kt` | pure | `AiToolProtocolTest` 16 |
| `app/src/main/java/com/codeci/ide/ui/ai/AiToolRunner.kt` | `java.io` only (host-testable) | `AiToolRunnerTest` 13 |

## The map (`AiRepoMap`)

```kotlin
AiRepoMap.build(files: List<FileInfo>, budget: Int = MAX_MAP_CHARS): MapResult
data class FileInfo(val path: String, val lines: Int, val text: String?)
data class MapResult(val text: String, val filesListed: Int, val filesTotal: Int,
                     val symbols: Int, val elided: Boolean)
```

- Header: `PROJECT MAP — N code/text files; …` followed by per-directory groups,
  `path (N lines)`, and up to `MAX_SYMBOLS_PER_FILE = 8` definition names per
  file (only for files whose text the walk already read — candidates).
- Bounds: `MAX_MAP_CHARS = 6_000`, `MAX_SYMBOL_CHARS = 100`,
  `MAX_SYMBOL_SCAN_CHARS = 40_000` per file, `ELISION_RESERVE = 120`.
- **Elision is disclosed, never silent.** When the budget cannot list every file
  the map ends with a line naming how many were not listed and inviting the
  `list_files` tool: `+ N more files not listed (map budget); use list_files.`
  `MapResult.elided` is `filesListed < filesTotal`.
- The AI's own filters (`isSecretLike`, `isExcludedDirectory`) are re-applied
  inside `build`, so a map built from a hand-made `FileInfo` list still cannot
  name a file the AI filter would refuse.

Why a map at all: the old path sent five files blind. A map costs ~6 000 chars
once and holds in **every** request (it is what tells the model what exists), so
`AiAgentPrompt.pack` carries task + map on every turn, and only tool *results*
compete for the remaining budget.

## The wire format and the parser (`AiToolProtocol`)

```
<<<CODEC_TOOL name="read_file">>>
path: src/main.c
start: 1
end: 60
<<<END_CODEC_TOOL>>>
```

- `AiToolProtocol.parse(answer)` → `AiToolParse.Calls(prose, calls)` or
  `AiToolParse.Malformed(prose, reason)`. Prose outside blocks is kept (it is
  what the timeline shows and what remains if the task ends). A block that is
  never closed, a missing `<<<END_CODEC_TOOL>>>`, a duplicate argument key, or an
  argument line without a colon is **Malformed** and executes nothing.
- A malformed answer costs one tool call and the reason is returned to the model
  as a tool result, so it can fix its own format — a free retry loop would be a
  budget with no cap (`AiAgentPolicy`/`AiViewModel.executeToolBatch`).
- `describe(request)` / `describeCall(call)` render the one-line timeline title.
- `AiToolProtocol.INSTRUCTIONS` is the exact sentence that teaches the format;
  it is part of the sent prompt (and therefore rendered verbatim by the D4
  preview).

Native Gemini function-calling was considered and **rejected** for this phase
(reasons in `AiTools.kt`'s KDoc): the protocol is small, the app already renders
the exact request text, and CodeC's own parser is the thing that can be tested
on the host. Native tools can be revisited later without changing the policy
layer.

## The policy (`AiToolPolicy.validate`) — the only door

```kotlin
validate(request: AiToolRequest, view: AiToolProjectView): AiToolVerdict
// Allowed(call: AiToolCall) | Denied(request, reason: String)
```

- **Unknown tool fails closed:** `request.name == null` (the wire name was not
  one of the four) → `Denied(request, "unknown tool \"…\"; the tools are …")`.
- **Exhaustive argument surface:** any key outside the call's allowlist →
  `Denied(request, "read_file does not take …")`. There is no “ignore unknown
  keys” path.
- `read_file`: `path` must pass `AiEditProposalParser.validateTargetPath` (no
  `..`, no absolute path, no credential-shaped name) **and** be present in
  `view.existingPaths` (the walk's admitted set); `start`/`end` must be whole
  numbers, `1 ≤ start ≤ end`, and `end - start + 1 ≤ MAX_READ_LINES = 400`.
- `search_project`: query 2–120 chars, `max` clamped to `MAX_SEARCH_HITS = 40`.
- `list_files`: `path` optional; when given it must pass `safeDirectoryPath`
  (relative, no `..`, no credential directory).
- `request_run`: `target` must be an admitted project path (or empty); the call
  is only ever *allowed*, never executed here.
- `AiToolProjectView(existingPaths, runsRemaining)` is built from the same walk
  the map came from, so “exists” means “the AI filter admitted it”.

`AiToolLimits` is the single place the numbers live: `MAX_READ_LINES = 400`,
`MAX_RESULT_CHARS = 8_000`, `MAX_LIST_ENTRIES = 200`, `MAX_SEARCH_HITS = 40`,
`MAX_SEARCH_FILES = 300`, `MAX_QUERY_CHARS = 120`, `MIN_QUERY_CHARS = 2`.

## The executor (`AiToolRunner`)

```kotlin
execute(call: AiToolCall, root: File, paths: List<String>,
        dirtyBuffers: Map<String, String>, shouldStop: () -> Boolean): Outcome
data class Outcome(val ok: Boolean, val text: String, val truncated: Boolean)
```

- `java.io` only (`File`, `BufferedReader`) — no `java.nio.file` (minSdk 24), no
  `ProcessBuilder`, no shell, no writes, no network.
- `list_files`: admitted paths only, grouped, ≤ 200 entries, with line counts.
- `search_project`: case-insensitive line search over admitted paths, ≤ 40 hits
  across ≤ 300 files, longest line cut at `MAX_LINE_CHARS = 220`.
- `read_file`: **the live dirty buffer wins over disk** (`dirtyBuffers`), the
  same contract Phase 78 gave the walk, so an unsaved edit in an open tab is what
  the model reads. Range-clipped, each line ≤ 220 chars, `CUT_NOTE` appended when
  anything was cut, `STOP_NOTE` when `shouldStop()` ends it early.
- `request_run` → `Outcome(false, "request_run is not executed here …")`. The
  runner **cannot** run anything.
- Every result is truncated to `MAX_RESULT_CHARS = 8 000` (the caller charges the
  call and returns the truncation note).

## Tests

- `AiRepoMapTest` (12): definitions for python / C / Kotlin / JS-Go-Rust-shell
  and *none* for config-markup-prose, definitions capped per file and never
  repeated, definitions ride along when the budget allows, every admitted file
  listed when it fits, directories grouped in a deterministic order, a tight
  budget elides files **and says so**, a secret-shaped or excluded path handed
  in by mistake is dropped, an empty project maps to nothing rather than a
  header alone.
- `AiToolProtocolTest` (16): one/many blocks parsed in order with their
  arguments, malformed cases (unclosed block, no colon, repeated argument, no
  name), `describe` names tool + target, unknown tool denied with the real list,
  an unexpected argument denied rather than ignored, `read_file` refuses
  traversal / absolute / credential / unknown paths and bad bounds and builds
  the default range from `start`, `search_project` query + cap, `list_files`
  directory + extension, `request_run` allowed only inside the run budget and
  only for real files, and the directory helper refuses what the file helper
  refuses. One case asserts `AiToolLimits.MAX_RESULT_CHARS ==
  AiAgentLimits.MAX_RESULT_CHARS`, so the two caps cannot drift.
- `AiToolRunnerTest` (13): on a real temp tree — `list_files` names only the
  admitted files, filters by directory/extension, and caps its answer; `search`
  finds file + line, is case-insensitive, is honest when nothing matches,
  honours `max` with a cut marker, never reads a secret, and stops when the task
  is stopped; `read_file` returns a numbered range with a truthful header,
  prefers the unsaved editor buffer and says so, refuses secrets / binaries /
  missing files / escape paths and a **symlink that leaves the project**
  (`canonicalPath != absolutePath`, the minSdk-24-safe form), and clips very
  large files with a visible marker; `request_run` never reaches a runner as an
  action.
