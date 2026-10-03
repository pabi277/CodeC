# CodeC Phase 88.4 — One truthful progress line

> **Status:** ✅ IMPLEMENTED (2026-10-03, on the owner's *"Complete level 11"*; CI-GREEN: Build APK `37132310296` on `88f0186` — see the [README record](README.md#implementation-record-2026-10-03)) · **Cost:** `[client-only]` · **Effort:** S
> **Owner decision (2026-10-03):** one truthful compact-progress line.
> **Level 11 spec:** *"Compact progress — one truthful line: what stage, how many reads, what is left."*
> **Parent brief:** [Phase 88 README](README.md)

## First move: evidence, not code

Read 2026-10-03 on `arena/01a101db-codec` @ `8062f0c`.

| Where | Builder | Input | "steps" means |
|---|---|---|---|
| activity card, `AiChatSheet.kt:610-617` | `AiCopy.agentUsageLine` (`AiCopy.kt:213-233`) | `AiAgentUsage` | **model turns** (*"T of 12 steps · R of 24 reads · … · U of 2 runs"*) |
| bottom bar while `STREAMING`, `AiChatSheet.kt:968-970` | `AiCopy.agentWorkingLine` (`AiCopy.kt:236-237`) | `agentSteps.count { it.kind == TOOL }` | **tool rows** (*"Working on it — N steps done…"*) |

After two turns that batch eight reads, the screen reads *"8 steps done"* beside *"2 of 12 steps"*.
That is two builders with two meanings for one word, and neither names the stage.

The state needed to name the stage already exists; no new field is required:

- Every agent turn sets `phase = STREAMING, answer = ""` (`AiViewModel.kt:1095`) and then appends its
  REQUEST row (`:1096`). An empty `answer` after a REQUEST means *waiting for the model*; text arriving
  means *the model is replying*.
- A run request sets `agentRun` while the loop pauses for the user's tap (`AiViewModel.kt:1236-1243`);
  `agentRunRunning` is set when the approved command runs (`:951`).
- `retryCountdown` is non-null while a rate-limit wait shows its countdown (`AiChatSheet.kt:967`).
- A stop sets `phase` DONE or FAILED, appends a STOPPED row and may set `budgetOffer`, all in one update
  (`AiViewModel.kt:1436-1450`).
- `AiAgentUsage` (`AiViewModel.kt:135-150`) carries the task's **own** caps (`turnCap`, `readCap`).
  The Phase 87.6 lesson applies: clamp against those, never against the constants.

## Design

```kotlin
// In AiLevel11Policies.kt — pure.

enum class AiProgressStage {
    WAITING_FOR_MODEL, MODEL_REPLYING, READING_FILES,
    WAITING_FOR_RUN_DECISION, RUNNING_COMMAND, WAITING_TO_RETRY,
    DONE, STOPPED, FAILED
}

data class AiProgressInput(
    val phase: AiPhase,
    val lastKind: AiAgentStepKind?,
    val answerEmpty: Boolean,
    val runPending: Boolean,
    val runRunning: Boolean,
    val retrying: Boolean
)

object AiProgressPolicy {
    enum class Placement { BOTTOM_BAR, CARD, NONE }

    fun stage(input: AiProgressInput): AiProgressStage
    fun placement(phase: AiPhase, hasAgentSteps: Boolean): Placement
    /** Stage words, then counters from AiAgentUsage; the retry countdown replaces the stage words. */
    fun line(stage: AiProgressStage, usage: AiAgentUsage?, countdown: String? = null): String
}
```

### Stage, first match wins

| # | Condition | Stage |
|---|---|---|
| 1 | `phase == FAILED` | `FAILED` |
| 2 | `phase == DONE` and the last row is STOPPED | `STOPPED` (the row's title already says why) |
| 3 | `phase == DONE` | `DONE` |
| 4 | `STREAMING` and `retrying` | `WAITING_TO_RETRY` |
| 5 | `STREAMING` and `runRunning` | `RUNNING_COMMAND` |
| 6 | `STREAMING` and `runPending` | `WAITING_FOR_RUN_DECISION` |
| 7 | `STREAMING` and the last row is TOOL or DENIED | `READING_FILES` |
| 8 | `STREAMING` and the answer is not empty | `MODEL_REPLYING` |
| 9 | `STREAMING` | `WAITING_FOR_MODEL` |

### Exactly one line on screen

| Phase | Agent rows? | Placement |
|---|---|---|
| `IDLE`, `PREVIEW` | any | `NONE`: nothing has been sent |
| `STREAMING` | yes | `BOTTOM_BAR`: always visible while work happens |
| `DONE`, `FAILED` | yes | `CARD`: the activity card's first line, a final summary |
| any | no (single-shot ask) | `NONE`: unchanged from today |

While streaming, the card shows **no** counter line, and at DONE the bar shows none. The duplicate
disappears with the second builder.

### The line's content

**Stage words**, then the counters: `step T of TC · R of RC reads`, plus `· N refused` and `· N reused`
only when non-zero, plus `· U of 2 runs` once a run has been requested or used.

- "steps" means **model turns** everywhere. The counters reuse `agentUsageLine`'s clamping, so `R`
  never exceeds `RC`, and both caps are the task's own (Phase 87.6).
- `WAITING_TO_RETRY` uses the existing `AiRateLimits.countdownLine` text as its stage words, so the
  countdown does not need a line of its own.
- With `usage == null` (before the first update) the line is the stage words alone.
- Wording lives in `AiCopy`. Tests pin the builder's structure and numbers, never the sentence
  (`RepoFiles.codeOnly` blanks string literals).

## The Android edge

`AiChatSheet.kt` changes in two places, and nothing else:
- `:610-617`: the card draws the progress line only when `placement == CARD`.
- `:966-972`: the bar draws it only when `placement == BOTTOM_BAR`. The separate countdown line at
  `:967` folds into it; **Stop** stays.

`AiCopy.agentWorkingLine` is deleted (no callers remain). `agentUsageLine` survives as the counter
builder that `line()` reuses.

## Exit condition

- [ ] One builder: no `agentWorkingLine` anywhere; both sites call `AiProgressPolicy.line(` and consult `AiProgressPolicy.placement(`.
- [ ] Every precedence row above is asserted; the stage is named in every non-`NONE` placement.
- [ ] Counters never exceed the task's caps, including after an accepted budget extension.
- [ ] `refused` / `reused` appear only when non-zero.
- [ ] The single-shot path shows no progress line, as today.

## Tests (plan)

`AiProgressPolicyTest`, about 12 cases: each precedence row (9); placement for IDLE/PREVIEW, STREAMING
and DONE (3, counted with the rows where they overlap); clamping with extended caps; omitted zero
counters; `usage == null`. Wiring pins in `AiLevel11WiringTest`: no `agentWorkingLine`; both call sites
use the policy. Copy wording, if pinned at all, goes to a CI-only copy test, as with Level 10's
`AiLevel10CopyTest`. Counts are a plan, not a result.

## Sources (record)

1. The Level 11 spec — *"one truthful line: what stage, how many reads, what is left"* —
   [`11_AGENT_PHONE_PRESENTATION.md`](../../../roadmaps/ai-integration/11_AGENT_PHONE_PRESENTATION.md).
2. SWE-agent — *"environment feedback should be informative but concise"* —
   https://arxiv.org/abs/2405.15793 (cited from the spec; not re-fetched).
3. Repository lines above, read 2026-10-03 on `8062f0c`.

## Deferred / rejected with reasons

- **A time estimate ("about 30 s left")** — rejected: nothing in the state supports it, and an untruthful
  estimate is the defect this part removes.
- **A progress bar** — rejected: turns are not uniform work, so a bar would imply a precision the agent
  does not have. "step 3 of 12" is the honest form.
- **Naming the final-synthesis turn separately** — deferred: the flag lives on the session, not in the
  UI state, and adding a state field is not needed for a truthful line.
