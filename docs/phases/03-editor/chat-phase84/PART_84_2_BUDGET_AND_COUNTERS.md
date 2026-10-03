# CodeC Phase 84.2 — the tool budget on every path and split counters

> **Status:** 🚧 IMPLEMENTED · **Cost:** `[client-only]` · **Effort:** M
> **Owner row (verbatim):** “Start level 7”
> **Fixes:** 2 (defect 2) and 3 (defect 3)

## First move: evidence, not code

At `main` @ `392a614`:

- `AiViewModel.kt:748` `resumeAgentOrStop` gated on `blockModelTurn` only. The
  tool cap (`blockTool`) was consulted **only** inside `AiAgentPolicy.decide`
  (`AiAgentLoop.kt:195`), so the malformed-parse path (`:828`), the denied-only
  path (`:881`) and the post-run resume could all walk past an exhausted tool
  budget.
- `withToolCalls(n)` added executed **and** refused attempts into one counter and
  never clamped, so `AiCopy.kt:192` rendered “49 of 24 reads” (24 executed + 25
  refused at `used = 0`).

## Design

`AiAgentBudget` (`AiAgentLoop.kt`) splits and clamps:

```kotlin
data class AiAgentBudget(
    val turnsUsed: Int = 0,
    val toolCallsUsed: Int = 0,      // EXECUTED only; gates MAX_TOOL_CALLS; clamped
    val toolCallsRefused: Int = 0,   // malformed/denied/over-budget; separate
    val toolCallsReused: Int = 0,    // 0 until Level 9's working set (S11)
    val runsUsed: Int = 0,
    val startedAtMs: Long = 0L
) {
    fun blockResume(nowMs) = blockModelTurn(nowMs) ?: blockTool(nowMs)
    fun withToolCalls(n) = copy(toolCallsUsed = (toolCallsUsed + n).coerceIn(0, MAX_TOOL_CALLS))
    fun withRefused(n)   = copy(toolCallsRefused = (toolCallsRefused + n).coerceAtLeast(0))
}
```

- **Fix 2:** `resumeAgentOrStop` now gates on `blockResume` (turn/wall-clock, then
  tool). An exhausted execution budget stops **every** resume path.
- **Fix 3:** executions gate the cap; refusals are a separate, displayed count.
  `AiCopy.agentUsageLine(turns, toolCalls, runs, refused = 0, reused = 0)` renders
  executions clamped to the cap and appends `· N refused` / `· N reused` only when
  non-zero, so one mixed value can never appear.

## The Android edge

`AiViewModel`: `executeToolBatch` uses `withToolCalls(1)` per execution and
`withRefused(denied.size)` for refusals; the malformed path uses `withRefused(1)`.
`usage(session)` carries `refused`/`reused`; `AiChatSheet` passes them to
`agentUsageLine`.

## Exit condition

- An exhausted tool budget blocks every resume path, including malformed and
  denied-only *(failed before)*.
- Counters never exceed their caps after a 49-block answer *(failed before)*.
- Denied / executed / reused are counted separately.

## Tests

- `AiLevel7CorrectnessTest`: `an exhausted tool budget blocks the resume path…`,
  `the turn budget is reported before the tool budget…`, `a fresh budget does not
  block…`, `the execution counter is clamped…`, `the usage line renders executions
  and refusals separately…`, `…coerces an over-cap execution count…` (6).
- `AiLevel7WiringTest`: `resumeAgentOrStop gates on the combined…`, `refusals are
  counted on a separate clamped counter`, `the usage line takes refused and reused
  separately` (3).
- **Baseline re-run:** `AiLevel6BaselineTest` case 5 now asserts executed = 24
  (clamped), refused = 25, the line “9 of 12 steps · 24 of 24 reads · 25 refused ·
  0 of 2 runs”, and `blockResume == TOOL_BUDGET`.

## Sources (record)

- Repository: `AiAgentLoop.kt`, `AiViewModel.kt`, `AiCopy.kt`, `AiChatSheet.kt`
  at `392a614`.

## Implementation (2026-10-03)

Shipped as described; 337/337 host pre-validation.

## Deferred / rejected with reasons

- **`reused` counting** — the field exists and is separate, but stays **0** until
  Level 9’s working set (S11) makes duplicate reads free. Recording it now keeps
  the three counts structurally separate from day one; no mechanism is faked.
- **Letting refusals gate the cap** — rejected: a malformed/denied loop is already
  bounded by the 12-turn cap, and counting refusals toward the read cap is what
  produced “49 of 24”.
