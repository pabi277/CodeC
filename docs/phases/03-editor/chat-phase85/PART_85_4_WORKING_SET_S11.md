# CodeC Phase 85.4 — the loop-local working set (S11)

> Status: 🚧 IMPLEMENTED · Cost: host pre-validation only · Effort: one value type, one budget method, one stop reason, thin wiring, 7 tests.
> Owner row (verbatim): **Full Level 8 (all four items)** — S11 is in the Level 8 acceptance list.

## First move: evidence, not code

`AiAgentBudget.toolCallsReused` existed but its doc read *“Always 0 until Level 9's
working set exists (S11)”*; `executeToolBatch` called `withToolCalls(1)` for **every**
call, so a model that re-read the same file paid for it again and the “reads” counter
climbed on duplicates. There was no backstop for a loop spinning on one unanswerable
request.

## Design

The agent loop is read-only (S6/S7: `ui/ai` never writes the project), so within one
task an **exact-duplicate** read — same path, same range — returns the same bytes.

**`AiAgentWorkingSet`** (loop-local, born with the task, gone when it ends) maps a read
identity (`path\u0000start-end`) to the result already delivered, and tracks the last
call signature with a repeat count. `AiViewModel.executeToolBatch` notes each call,
serves an exact-duplicate `read_file` from the set at **zero execution cost** — counted
with the new `withReused(1)`, never `withToolCalls(1)` — and records a fresh read. The
three counters (`toolCallsUsed` / `toolCallsRefused` / `toolCallsReused`) stay
structurally separate; only `toolCallsUsed` gates `MAX_TOOL_CALLS`, so reused reads
never consume the execution budget.

**No-progress stop.** `MAX_IDENTICAL_REPEATS` (3) consecutive identical calls set
`stalled()`; the batch then stops with the new `AiAgentStopReason.NO_PROGRESS` instead
of resuming. Because that reason is not `USER_STOP` / `WALL_CLOCK` / `PROVIDER_FAILURE`,
`stopAgent` still routes it through the reserved final-synthesis turn, so the user gets
prose, not a raw tool block.

## The Android edge

The working set is plain in-memory state on `AgentSession` (D6: nothing persisted, no
DataStore key). It dies with the task. No new network road — the three `client.stream`
sites are untouched.

## Exit condition

An unchanged repeat read costs zero execution budget and increments `toolCallsReused`;
N identical no-progress calls stop the loop.

## Tests

`AiLevel8ReuseTest` (7): an exact-duplicate read is served from the set; a different
range or path is a different entry; the reused counter is separate from executions and
refusals; reused reads never gate the tool cap (`blockTool` stays null); N identical
calls stall; a different call between repeats resets the count; `NO_PROGRESS` has its
own stop sentence.

## Sources (record)

Level 8 spec S11; `00_AGENTIC_MAP_AND_SECURITY_RULES.md` S11 (exact-duplicate reads
from the working set at zero execution cost; N identical no-progress calls stop).

## Implementation (2026-10-03)

`AiAgentLoop.kt` (`AiAgentWorkingSet`, `withReused`, `MAX_IDENTICAL_REPEATS`,
`AiAgentStopReason.NO_PROGRESS`, `stopSentence`); `AiViewModel.kt` (`AgentSession.
workingSet`, the reuse/record/stall wiring in `executeToolBatch`). Commit: *Phase 85
part 4*.

## Deferred / rejected with reasons

A persistent cross-task working set — deferred: that is a later level's concern and
would need its own storage decision (D6). This one is loop-local by design.
