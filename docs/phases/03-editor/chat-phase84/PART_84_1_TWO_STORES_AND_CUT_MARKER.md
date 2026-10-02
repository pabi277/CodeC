# CodeC Phase 84.1 — two stores and the surviving cut marker

> **Status:** 🚧 IMPLEMENTED · **Cost:** `[client-only]` · **Effort:** M
> **Owner row (verbatim):** “Start level 7”
> **Fixes:** 1 (defect 1, **S1**) and 5 (defect 1, **S2**)

## First move: evidence, not code

At `main` @ `392a614`:

- `AiViewModel.kt:893` built the tool step with
  `detail = outcome.text.take(AiAgentLimits.MAX_STEP_DETAIL_CHARS)` — a 1 200-char
  clip.
- `AiAgentLoop.kt` `AiAgentPrompt.renderStep` then packed
  `step.detail.take(AiAgentLimits.MAX_RESULT_CHARS)` — `.take(8 000)` on a string
  already ≤ 1 200, so the 8 000 cap was dead code.
- `AiToolRunner.clip` puts `CUT_NOTE` at the **end** of a truncated result, so the
  1 200-char `take` cut it away: a fragment looked complete.

Arithmetic the owner’s screenshot confirmed: a ~75-char header leaves ~1 124 chars
of body; at ~50 chars per numbered line that is ~22 lines — exactly what a 400-line
`read_file` arrived as.

## Design

Two stores on `AiAgentStep` (`AiAgentLoop.kt`):

```kotlin
data class AiAgentStep(
    val kind: AiAgentStepKind,
    val title: String,
    val detail: String = "",        // short UI preview, clipped to MAX_STEP_DETAIL_CHARS
    val modelResult: String = "",   // full-fidelity text the NEXT request carries
    val ok: Boolean = true,
    ...
)
```

- `AiAgentPrompt.renderStep` reads **`modelResult`**, clipped only to
  `MAX_RESULT_CHARS` (8 000). A wiring pin fails if it reads `detail`.
- `AiAgentLimits.timelineDetail(full, cutMarker)` is the marker-preserving clip:
  a result ≤ 1 200 chars is returned unchanged; a longer one is cut to fit **and
  re-appends `cutMarker` only if `full` already contained it** — so the timeline
  never claims a cut that did not happen, and never hides one that did.

## The Android edge

`AiViewModel.executeToolBatch` sets both stores on the TOOL step:
`detail = AiAgentLimits.timelineDetail(outcome.text, AiToolRunner.CUT_NOTE)` and
`modelResult = outcome.text`. DENIED and RUN_RESULT steps set `modelResult` to
their reason/digest (short, so preview and payload coincide). No Compose change;
the timeline still renders `detail`.

## Exit condition

- A full tool result reaches the next request unclipped *(failed before)*.
- The cut marker survives every clip boundary *(failed before)*.
- `renderStep` provably reads `modelResult`, never `detail`.

## Tests

- `AiLevel7CorrectnessTest`: `renderStep packs the full model result…`,
  `a step with no model result packs an empty body…`, `the timeline clip keeps the
  runner cut marker…`, `…never invents a marker…`, `a short result is returned
  unchanged…` (5).
- `AiLevel7WiringTest`: `the request packer reads modelResult…`, `the view model
  stores the full result beside a clipped preview`, `the timeline clip helper
  preserves the cut marker` (3).
- **Baseline re-run:** `AiLevel6BaselineTest` case 3, which recorded the defect,
  now asserts the fix — the packed request contains line 100 and `CUT_NOTE`, and
  delivers > 100 lines instead of 15–30. (Line 300 is still beyond the runner’s
  own 8 000-char result cap; reaching an arbitrary tail is Level 8, not Level 7.)

## Sources (record)

- Anthropic, *Effective Context Engineering for AI Agents* — tool-result clearing
  is safe **with** a summary, lossy without — https://www.anthropic.com/engineering/effective-context-engineering-for-ai-agents (checked 2026-10-02).
- Repository: `AiAgentLoop.kt`, `AiViewModel.kt`, `AiToolRunner.kt` at `392a614`.

## Implementation (2026-10-03)

Shipped as described. Pre-validated 337/337 on the host harness. No new
dependency, permission, DataStore key or wire-contract change.

## Deferred / rejected with reasons

- **Raising `MAX_STEP_DETAIL_CHARS` or `MAX_RESULT_CHARS`** — rejected: the 1 200
  clip survived every cap change; the fix is the second store, not a bigger clip.
- **Reaching an arbitrary line tail** — deferred to Level 8 (honest reads); the
  8 000-char per-result cap is unchanged here.
