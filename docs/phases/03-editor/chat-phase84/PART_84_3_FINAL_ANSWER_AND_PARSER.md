# CodeC Phase 84.3 — a readable final answer and a parser that keeps good calls

> **Status:** 🚧 IMPLEMENTED · **Cost:** `[client-only]` · **Effort:** M
> **Owner row (verbatim):** “Start level 7”
> **Fixes:** 4 (defect 4, **S12**) and 6 (defect 5)

## First move: evidence, not code

At `main` @ `392a614`:

- `stopAgent` (`AiViewModel.kt:1004`) set phase/error/notice/steps but **never
  `answer`**, so when the loop stopped mid-tool-use the last raw
  `<<<CODEC_TOOL …>>>` text stayed on screen as the answer.
- `AiToolProtocol.parse` (`AiTools.kt:101,105,113,117`) accumulated valid calls
  into a local list, then every malformed branch did an early
  `return AiToolParse.Malformed(prose, reason)` — discarding everything already
  parsed. Three good `read_file` blocks beside one broken block were all lost.

## Design

**Fix 4 — reserved masked synthesis turn (S12).**

- `AiAgentPrompt.finalSynthesis(...)` packs the ordinary context plus
  `FINAL_SYNTHESIS_INSTRUCTION`: *“Tool use is disabled for this final turn …
  answer in prose … Do not emit any `<<<CODEC_TOOL` block.”* Tools are **masked,
  not removed** (Manus) — the system instruction still defines them, so the
  provider’s KV-cache and schema stay intact.
- `stopAgent` routes an eligible budget stop (`TURN_BUDGET`, `TOOL_BUDGET`,
  `RUN_BUDGET`; provider reachable; not already synthesized) through
  `agentTurn(session, finalSynthesis = true, stopReason = …)` — the **same third
  `client.stream` site**, so no fourth appears. `session.synthesisDone` makes it
  run at most once.
- `finalizeStop` is terminal and guarantees `state.answer` is prose on **every**
  stop reason: the synthesis prose, else the partial answer run through
  `AiToolProtocol.proseOnly`, else the stop sentence.
- **Agent’s decision, recorded:** `USER_STOP` and `PROVIDER_FAILURE` (and the wall
  clock) finalize **locally without a new call** — “stop means stop / no delayed
  action” (S12) outranks a synthesis round-trip the user cancelled or the network
  cannot serve. The spec’s “every stop reason yields prose” still holds via the
  local prose guarantee.

**Fix 6 — the parser keeps the good calls.**

- `AiToolParse.Malformed` gains `calls: List<AiToolRequest> = emptyList()`; all
  four malformed branches pass `calls.toList()`.
- `onAgentAnswer` records the malformed refusal (`withRefused(1)`) and, when
  `parsed.calls` is non-empty, routes them through `handleParsedCalls` so the good
  reads still run instead of being thrown away.

## The Android edge

`agentTurn` gained `finalSynthesis`/`stopReason` parameters; on a synthesis answer
it strips blocks (`proseOnly`) and calls `finalizeStop`. `AiToolProtocol.proseOnly`
is pure. No Compose change.

## Exit condition

- Every `AiAgentStopReason` yields prose, never `<<<CODEC_TOOL` *(failed before)*.
- `parse` returns valid calls alongside a malformed block *(failed before)*.
- Exactly three `client.stream(` sites remain.

## Tests

- `AiLevel7CorrectnessTest`: `the final synthesis request masks tools…`,
  `proseOnly strips complete tool blocks…`, `proseOnly of a pure tool answer is
  blank…`, `parse returns the successfully parsed calls alongside the format
  error`, `a wholly malformed answer still carries no calls` (5).
- `AiLevel7WiringTest`: `a budget stop is routed through one reserved synthesis
  turn`, `the synthesis turn reuses the agent turn so no fourth stream site
  appears`, `every malformed branch carries the calls parsed so far`, plus the
  standing command-free / Android-free pins (4).

## Sources (record)

- Manus, *Context Engineering for AI Agents* — **mask, don’t remove**; constrain
  the action space without invalidating tool definitions — https://medium.com/@peakji/context-engineering-for-ai-agents-lessons-from-building-manus-71883f0a67f2 (checked 2026-10-02).
- SWE-agent, NeurIPS 2024 — malformed generations get an error response and a
  retry — https://arxiv.org/abs/2405.15793 (checked 2026-10-02).
- Repository: `AiViewModel.kt`, `AiAgentLoop.kt`, `AiTools.kt` at `392a614`.

## Implementation (2026-10-03)

Shipped as described; 337/337 host pre-validation; three stream sites pinned.

## Deferred / rejected with reasons

- **A fourth `client.stream` site for synthesis** — rejected: the handoff and
  `AiLevel4WiringTest` fix the count at three; the synthesis turn reuses `agentTurn`.
- **A synthesis call after `USER_STOP`** — rejected as a delayed action (S12);
  local prose is used instead. Recorded as the agent’s decision, not the owner’s.
