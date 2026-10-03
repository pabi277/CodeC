# CodeC Phase 85.3 — restorable eviction

> Status: 🚧 IMPLEMENTED · Cost: host pre-validation only · Effort: one packer change + one pointer helper, 4 tests.
> Owner row (verbatim): **Full Level 8 (all four items)**.

## First move: evidence, not code

`AiAgentPrompt.pack` kept the newest `KEEP_LAST_RESULTS` (4) tool results and reduced
everything older to a bare count: `(N earlier tool results were dropped to fit)`. The
model knew *how many* results it could not see but not *which*, so it could not
re-read them — it re-issued blind guesses instead.

## Design

`pack` now collects the dropped **steps**, not a count, and emits one re-read pointer
each. `evictionPointer(step)` parses the honest read header
(`FILE <path> — lines a-b of N [coverage]`) into `path — lines a-b — status`; a
non-read result falls back to the call it came from (`step.title`), which is enough to
re-issue it. The lead-in phrase `(N earlier tool results were dropped to fit; re-read
any on demand:)` is kept, so the existing pin still holds and the count survives. The
list is capped at `AiAgentLimits.MAX_EVICTION_POINTER_CHARS` (1 200) with a
`(+N more; re-issue the call to re-read)` remainder, so a long task cannot crowd out
the results that did fit. `droppedResults` is still returned (now `droppedSteps.size`).

Because the header lives at the **front** of a result, the pointer's status token also
survives the 1 200-char timeline clip — the same property Phase 84 relied on for
`CUT_NOTE`.

## The Android edge

Pure string building over the in-memory timeline; no IO, no new state. The cap keeps
the eviction block bounded regardless of how many calls a task made.

## Exit condition

Every evicted result is re-acquirable by **path and range**; the model can name what
it lost and ask for exactly that again.

## Tests

`AiLevel8ContextTest` (4): a dropped read is named by path, range and status; every
evicted read stays re-acquirable by path and range; a partial or refused read keeps
its true status in the pointer; the pointer list is capped and reports the remainder.
`AiAgentLoopTest`'s packing case was updated: a dropped path is now a pointer (present),
not invisible.

## Sources (record)

Level 8 spec item 4; Phase 84 fix 5 (the marker survives the clip).

## Implementation (2026-10-03)

`AiAgentLoop.kt` (`MAX_EVICTION_POINTER_CHARS`, `pack` collects dropped steps,
`evictionPointer`). Commit: *Phase 85 part 3*.

## Deferred / rejected with reasons

Re-inlining the full dropped results — rejected: that is what the cap exists to
prevent; a pointer is the honest, bounded middle ground.
