# CodeC Phase 85.1 — the offset-capable honest reader

> Status: 🚧 IMPLEMENTED · Cost: host pre-validation only, no provider call · Effort: one reader method + one runner rewrite.
> Owner row (verbatim): “Start Level 8” → **bounded but honest**, **Full Level 8 (all four items)**.

## First move: evidence, not code

`AiProjectReader.readCapped(file)` returned the first `AiProjectFiles.MAX_READ_CHARS`
(24 000) characters and nothing else. `AiToolRunner.read` split *that prefix* into
lines and sliced it, so a request for lines 1 900–2 000 of a 2 000-line file
returned a prefix near the top and declared a false `of 304`. `AiLevel6BaselineTest`
pinned exactly this (`the prefix-only reader cannot reach a generated file tail range`).
The header also reported the *requested* range while `clip()` silently truncated the
body to 8 000 chars — a fragment could read as the whole file.

## Design

**Item 1 — `AiProjectReader.readLineRange(file, requestedStart, requestedEnd, maxChars, shouldStop)`.**
It streams with `file.bufferedReader(Charsets.UTF_8).use { readLine() }` — `java.io`
only, never `java.nio.file` (minSdk 24). It counts the **true** total to EOF while
accumulating only the requested slice under a char budget (reserving ~9 chars/line so
the formatted body fits the result cap). UTF-8 decoding matches `readCapped`;
`shouldStop` is checked between lines so Stop ends a big read promptly; the head
(first 8 192 chars) is checked for NUL so a binary file is refused — including a
*small* one, which the first draft missed because it only tested once the head filled.

**Item 2 — honest coverage in `AiToolRunner.read` / `formatRange`.** The header states
the lines **actually delivered** and the file's **true total**, with an explicit token:
`[complete]` only when the delivery reached the last line, otherwise
`[partial: more lines follow]`, `[partial: result cut at N chars]`,
`[partial: stopped by the user]`, or `[refused: …]`. A partial read still ends with
`CUT_NOTE`, so the Phase 84 timeline clip preserves the marker. The dirty-buffer path
slices the in-memory text under the same budget, so its header is honest too.

## The Android edge

`BufferedReader.readLine` is API 1; `Charsets.UTF_8` is API 19. No `Files.*`, no
`Path`, no API-26 symlink call — containment stays `canonicalPath != absolutePath`.
Memory is bounded: only the requested slice is held, the rest of the file is counted,
not stored.

## Exit condition

The last line of a >24 000-char file is reachable, and every read states its coverage.

## Tests

`AiLevel6BaselineTest.the offset reader reaches a generated file tail range and its
last line` (was the defect pin, now inverted) asserts `deliveredStart == 1900`,
`of 2000`, and that reading line 2 000 returns the end marker `[complete]` — for all
four fixtures. `AiToolRunnerTest.read_file refuses secrets binaries missing files and
escape paths` now also refuses a small NUL file. The Phase 84 packing case asserts the
honest delivered range (`lines 1-…`, `[partial: result cut`) instead of the old
over-claimed `1-300 of 300`.

## Sources (record)

Level 8 spec `08_FULL_CONTEXT_AND_HONEST_READS.md`; SWE-agent viewer ablation
(100-line 18.0% > full-file 12.7%) for the bounded-but-honest decision.

## Implementation (2026-10-03)

`AiProjectReader.kt` (+`LineRange`, +`readLineRange`); `AiToolRunner.kt` (`read`
rewritten, `formatRange` added). Commit: *Phase 85 part 1*.

## Deferred / rejected with reasons

Whole-file delivery — rejected: the owner chose bounded-but-honest on the ablation
evidence; caps stay, `KEEP_LAST_RESULTS` stays 4.
