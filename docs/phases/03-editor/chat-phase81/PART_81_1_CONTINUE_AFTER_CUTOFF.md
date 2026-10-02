# Part 81.1 — Continue after cut off: the follow-up request and its bounds

Owner row (2026-10-02): *"Add a continue open on Gemini so i can continue after
cut off"*.

## 1. The problem in CodeC's own terms

One answer can stop in two places, and `AiAnswerAccumulator` marks both the same
way (`cutShort = true`):

- the app's cap — `AiLimits.MAX_REPLY_CHARS = 24_000` (`AiPolicy.kt:32`), hit
  inside `AiAnswerAccumulator.accept` (`AiAnswer.kt:37`);
- the model's own budget — `AiLimits.MAX_OUTPUT_TOKENS = 8_192` asked on every
  request (`AiPolicy.kt:40`), arriving as `finishReason = MAX_TOKENS`
  (`AiAnswer.kt:43`).

The sheet shows one sentence (`AiErrors.CUT_SHORT`) and the pinned bar offers
only **Copy** / **New question**. Phase 81 adds **Continue**.

## 2. The design: a follow-up that is still just a preview

The API is stateless (`store:false`, no conversation id — D6), so "continue"
can only mean: **send the same request again, with the last lines of the answer
and a resume sentence appended to the user message.**

```
…the original user text (selection / error / project context / question)…

[The answer above stopped before it was finished: it reached this app's length
limit. Continue it from exactly where it stopped. Do not repeat anything you
already wrote, do not apologise, and do not start the answer again.]
[The last lines of your answer so far:
…<the tail>]
```

That block is produced by `AiContinuation.block(request)` and appended by
`AiPrompt.userText` — **inside the one string the preview renders**, which is
also the string `GeminiRequest.body(prompt)` sends. So D4 stays literal: the
user reads the follow-up and taps Send; nothing else reaches the network.

Why it is a preview and not a one-tap send: D4 says *preview and confirm every
request*, and the continuation is a **new** request whose text (the tail) the
user has not seen before. A one-tap send would be the first request in CodeC
that leaves without its text being shown.

## 3. The pieces, exactly

| Concern | Where | Rule |
|---|---|---|
| Tail selection | `AiContinuation.tailOf(answer, maxChars = TAIL_CHARS)` | last 1 400 chars; if the cut lands mid-line, look up to `TAIL_LINE_SNAP = 200` chars forward for a line break; prefix `…` when truncated; short answers go whole |
| The block | `AiContinuation.block(AiContinuationRequest(index, tail))` | model-facing: resume / no repeat / no restart |
| Bounds | `MAX_CONTINUATIONS = 8`, `MAX_TOTAL_CHARS = 64_000`, `MIN_REMAINING_CHARS = 600` | `canContinue(continuations, answerChars)`; `limitNote(...)` names the reason when false |
| Per-reply budget | `AiContinuation.requestBudget(answerChars)` = `min(MAX_REPLY_CHARS, remaining)` | the accumulator never gets a zero/negative budget; one reply can add at most one cap |
| Counter | `AiUiState.continuations` | set from `prompt.continuation?.index` in `send()`, reset by `preview()`/`clear()`; in memory only (D6) |
| Keeping the text | `send()`: `base = if (continuation != null) answer else ""` | the stream renders `base + text`; `onText` from the client is the accumulating *new* text |
| Offer | `AiChatSheet` DONE bar | `state.cutShort && !prompt.agent && prompt.source != PROPOSE_EDITS && canContinue(...)` |
| Explanation | `AiChatSheet` DONE body | hint when offerable, `limitNote` / agent note / proposal note otherwise — no silent missing button |

## 4. What the button is not allowed to do

`AiContinueWiringTest` pins the shape mechanically:

- `continueAnswer()` contains `phase = AiPhase.PREVIEW` and **no**
  `client.stream(` and **no** `viewModelScope.launch` — it cannot send;
- `AiViewModel` still has exactly three `client.stream(` call sites;
- the sheet's DONE bar calls `onContinue` and never `onSend`;
- `AiContinuation.kt` is pure (no `java.io`, no `android.*`) and adds no store
  key.

## 5. Answer growth, budget arithmetic

| Step | Visible characters | What may be added |
|---|---|---|
| first answer | up to 24 000 (`MAX_REPLY_CHARS`) | — |
| continuation 1 | ≤ 48 000 | `min(24 000, 64 000 − base)` |
| continuation 2 | ≤ 64 000 (total cap) | `min(24 000, remaining)` |
| further | refused once `remaining < 600` | `limitNote` explains |

`MAX_CONTINUATIONS = 8` is a second, independent guard: it bounds the number of
taps (and therefore requests) even when each continuation adds only a little.

## 6. Tests

- `AiContinuationTest` (12) — pure: short answer carried whole; long answer
  keeps only the end, `…`-marked; tail snaps to a line break; blank answers
  never fake a tail; `requestBudget` respects both caps; `remainingChars` never
  goes negative; `canContinue` at the tap limit and at the length limit; the
  block says resume/no-repeat/no-restart and carries the tail exactly;
  `limitNote` is null while possible and names each reason otherwise.
- `AiContinueWiringTest` (7) — source pins: the button's gating and the three
  explanatory sentences; preview-only `continueAnswer()` and the three stream
  sites; the continuation inside `AiPrompt.userText` and the preview disclosure;
  `base + text` / `base + outcome.text` and the per-reply budget; counter reset
  paths; the editor/sheet wiring and the no-send bar; purity and no new key.

## 7. Deliberate v1 limits

- Agent tasks (Phase 80, once merged) and edit proposals do not offer Continue —
  they get a one-line explanation instead. A continuation of an agent task would
  have to be an extra loop turn inside `AiAgentLimits`, not a classic request.
- The per-request model budget (`MAX_OUTPUT_TOKENS`) is deliberately unchanged;
  that is a cost decision, not a UI one.
