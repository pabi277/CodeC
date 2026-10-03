# CodeC Phase 85.2 — the `read_files` batch tool

> Status: 🚧 IMPLEMENTED · Cost: host pre-validation only · Effort: one enum entry, one validation path, one runner path, 12 tests.
> Owner row (verbatim): **Full Level 8 (all four items)**.

## First move: evidence, not code

`AiToolName` had four entries (`list_files`, `search_project`, `read_file`,
`request_run`); reading N files cost N round trips. The tool block format is
`key: value` lines with no repeated key, so a batch needs its paths in one value.

## Design

**Wire format.** `read_files` takes one `paths` value: comma-separated, each entry a
path with an optional `:start-end` (`paths: src/a.kt, src/b.kt:10-40, src/c.kt`).
`AiToolLimits.MAX_BATCH_READS = 8` bounds the batch.

**Validation (`AiToolPolicy.validateReadFiles`).** Checks only the *structure* — a
non-empty list within the cap, each entry parseable, each range `start ≥ 1`,
`end ≥ start`, per-file lines clamped to `MAX_READ_LINES`. It produces
`AiToolCall.reads: List<ReadSpec>`. It does **not** deny the whole batch for one bad
path: per-path security is a run-time, per-file concern.

**Run (`AiToolRunner.readFiles` / `readBatchBlock`).** Each path is validated and read
**on its own**, in the order asked: the secret filter, canonical containment +
symlink test, admitted-path check, existence and binary check are re-applied per file
(**S5**). A refused path yields a per-file `[refused: …]` block while its siblings
still deliver. Each block carries its own coverage token (85.1). `shouldStop` is
checked between files, so Stop ends a batch promptly. The batch shares the one
`MAX_RESULT_CHARS` cap, divided across the files (~120 chars/file reserved for the
header), with `clip()` as the final guard. This is parallel **read-only IO**, never
parallel agents (**S7**) — one brain still writes.

**Additive disclosure.** `AiToolProtocol.INSTRUCTIONS` gains one line naming
`read_files(paths)`; every existing tool is still listed, never replaced. The schema
growth is visible in the disclosed prompt.

## The Android edge

No concurrency primitive is added: the batch reads sequentially on the caller's
`Dispatchers.IO` context (the loop already runs the batch off the main thread), which
is bounded read-only IO. No `java.nio.file`; the same `readLineRange` reader serves
each file.

## Exit condition

A secret path in a mixed batch is refused while its siblings succeed; an escaping
symlink contributes nothing in a batch or alone; order is stable; every file states
its own status.

## Tests

`AiLevel8BatchTest` (12): secret refused beside siblings (the key value never
appears); escaping symlink contributes nothing; stable order; missing and
non-admitted per-file refusals; per-file range with `more lines follow`; prompt stop
reads nothing once stopped; empty/oversized batch denied; unknown argument and
malformed range denied; result within the cap; a block parses and validates into a
batch call; the instructions name `read_files` additively.

## Sources (record)

Level 8 spec item 3; `00_AGENTIC_MAP_AND_SECURITY_RULES.md` S5 (per-path security)
and S7 (parallelize only read-only IO).

## Implementation (2026-10-03)

`AiTools.kt` (`AiToolName.READ_FILES`, `ReadSpec`, `AiToolCall.reads`,
`MAX_BATCH_READS`, `validateReadFiles`, describe sites, INSTRUCTIONS);
`AiToolRunner.kt` (`readFiles`, `readBatchBlock`). Commit: *Phase 85 part 2*.

## Deferred / rejected with reasons

Per-file concurrency with a thread pool — rejected: sequential bounded IO on the
existing IO dispatcher is simpler, cancellation-safe, and still one round trip.
