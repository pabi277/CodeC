# Part 80.2 — the agent loop: validated calls, results, caps, and Stop

This part is the loop itself: `ui/ai/AiAgentLoop.kt` (new, pure, all decisions
and limits), plus the wiring in `ui/ai/AiViewModel.kt` (streaming, IO, state).
Owner's cap choice: **`looser`** — ≤ 12 model turns, ≤ 24 tool calls, ≤ 2 runs,
≤ 8 000 chars per tool result, ~5 min wall clock, enforced by CodeC after every
proposed call.

## The pure decision surface (`AiAgentLoop.kt`)

```kotlin
object AiAgentLimits {
    const val MAX_TURNS = 12
    const val MAX_TOOL_CALLS = 24
    const val MAX_RUNS = 2
    const val MAX_WALL_CLOCK_MS = 300_000L
    const val MAX_REQUEST_CHARS = 24_000
    const val MAX_RESULT_CHARS = AiToolLimits.MAX_RESULT_CHARS   // 8 000
    const val KEEP_LAST_RESULTS = 4
    const val MAX_STEP_DETAIL_CHARS = 1_200
}

sealed class AiAgentDecision {
    data class ExecuteTools(val calls: List<AiToolCall>, val denied: List<AiToolVerdict.Denied>)
    data class AskRunApproval(val call: AiToolCall, val alsoQueued: List<AiToolCall>,
                              val denied: List<AiToolVerdict.Denied>)
    data class Finish(val answer: String)
    data class Stop(val reason: AiAgentStopReason)
}
```

- `AiAgentBudget` is immutable and is only ever *grown* by the loop
  (`withTurn`, `withToolCalls`, `withRun`); `blockModelTurn(nowMs)` answers the
  first ceiling reached. The model never sees or edits it.
- `AiAgentPolicy.decide(parsed, budget, nowMs, projectView)` is the whole
  authorisation step, in order:
  1. budget exhausted → `Stop` (turn / tool / run / wall-clock);
  2. a `request_run` inside the run budget → `AskRunApproval`, and **the calls
     beside it wait with it** (`alsoQueued`) instead of being dropped;
  3. a `request_run` beyond the run budget → denied, not paused;
  4. every call validated by `AiToolPolicy.validate`; refused ones become
     `Denied(reason)` entries the model reads as results, and calls past the
     remaining tool budget are answered with a refusal rather than executed;
  5. nothing allowed and no prose to stand on → `Finish`.
- `AiAgentStopReason` = `TURN_BUDGET`, `TOOL_BUDGET`, `RUN_BUDGET`, `WALL_CLOCK`,
  `USER_STOP`, `PROVIDER_FAILURE`; each has `stopSentence` (plain words, shown
  in the timeline).
- `AiAgentPrompt.pack(question, mapText, steps)` → the request text: **task +
  map on every turn**, then the newest ≤ 4 tool results under a 24 000-char cap,
  with a disclosure line counting what was dropped. `renderStep` renders one
  timeline row.
- `AiAgentStep(kind, title, detail, ok)` + `AiAgentStepKind` (TASK, ANSWER,
  TOOL, DENIED, RUN_DECISION, RUN_RESULT, STOPPED) are the timeline's data type
  — pure, so the sheet only draws them.

## The wiring (`AiViewModel.kt`)

State (all in memory, D6): `agentSteps`, `agentRun` (`AiAgentRunRequest`),
`agentRunRunning`, `agentUsage` (`AiAgentUsage(turns, toolCalls, runs)`) — all
inside `AiUiState` as plain additive fields, and `AiAgentRunRequest` /
`AiAgentUsage` declared **outside** `AiUiState` so the existing
`AiHelperWiringTest:55` slice (`data class AiUiState(` → first `)` line) still
parses.

Private `AgentSession` holds: question, source, project root, the map text, the
admitted paths, dirty buffers, the system instruction, the budget, the pending
run request, and the queue that waits behind a run decision. A project switch,
`clear()`, or process death throws it away.

Flow:

1. `agentAsk(question, …)` / `agentPropose(question, …)` — the two chips. Both
   walk the project (`AiProjectReader.scan`), build the map
   (`AiRepoMap.build`), build the first prompt (`AiContextBuilder.fromAgent`),
   capture the Level 3 baselines/`existingPaths`, create the session, and land on
   the **existing preview gate**. No network, nothing sent.
2. `send()` on an agent prompt: resets the budget, seeds the TASK step, clears
   answer/proposal/apply state, and calls `agentTurn`.
3. `agentTurn` is `private fun` and is the file's **third** `client.stream(`
   site; `AiLevel3WiringTest:102`, `AiLevel2WiringTest:90`,
   `AiHelperWiringTest:70` and `AiSurfaceWiringTest:88` pin exactly three, so no
   fourth can appear. The `if (_state.value.phase != AiPhase.PREVIEW` gate that
   makes Send necessary is unchanged.
4. `onAgentAnswer` → `AiToolProtocol.parse`; malformed text costs a tool call and
   returns the reason; otherwise `AiAgentPolicy.decide`.
5. `executeToolBatch` runs allowed calls on `Dispatchers.IO` in order through
   `AiToolRunner.execute` (each result one timeline row, truncated results say
   so), appends DENIED rows, charges the budget, then `resumeAgentOrStop`.
6. `resumeAgentOrStop` blocks on the budget → `stopAgent(reason)`; otherwise it
   drains the run queue or takes the next turn.
7. `stopAgent` is the only place a task ends badly: it clears `agentRun` /
   `agentRunRunning`, appends a STOPPED row with `stopSentence`, and sets the
   phase to DONE (or FAILED with the provider's message for
   `PROVIDER_FAILURE`). Every stop is recoverable: the sheet offers Send again.

## Caps, coverage, and rollback

- The counters are visible: `AiCopy.agentUsageLine(turns, calls, runs)` renders
  `"… of 12 steps"` etc. in the activity card, updated from `agentUsage`.
- `stop()` on the sheet calls `viewModel.stop()`; during an agent task that
  appends a `USER_STOP` row and cancels the in-flight job, and an **approved run
  the agent started can still be stopped from the Output panel** (the panel's
  stop is untouched).
- There is no new persistence: no store key, no file, no journal (`D6`), and the
  Level 2 deterministic path (`askProject`, `proposeEdits`) stays compiled and
  tested as the documented fallback.

## Tests

- `AiAgentLoopTest` (19): the owner's caps are the constants the app uses; a
  model turn is blocked at the turn cap; tools/runs report what is left; the
  wall clock stops the task whatever else is left; prose with no tool block ends
  the task; a valid call is executed; an unknown tool becomes a denial the model
  can read; a run request pauses the loop; **the calls beside a run request wait
  with it instead of being dropped**; a run request beyond the run budget is
  refused not paused; calls past the tool budget are refused; an exhausted tool
  budget stops the task before anything runs; the budget object is only ever
  grown; the task and the map are always in the request; only the newest results
  ride along and the dropped ones are counted; a tight request budget drops
  results rather than overrunning; a result block keeps tool name + refusal
  marker; every stop reason has a sentence the user can read; a denial never
  carries an argument map into the planner.
- `AiLevel4WiringTest` (14) covers the app-level claims: turns exist only behind
  Send, caps are the owner's numbers, tools fail closed, the run goes through
  `viewModel.runFile(context, target)`, only a digest returns, the timeline is
  memory-only and the store learns no new key, the sheet shows timeline +
  approval card and keeps Stop.
