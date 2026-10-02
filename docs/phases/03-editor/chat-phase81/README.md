# Phase 81 — Continue: getting the rest of an answer that was cut off

> **Status: 🚧 IMPLEMENTED 2026-10-02 on `arena/01a0f9a5-codec`** (owner row:
> *"Add a continue open on Gemini so i can continue after cut off"*). Branch CI
> **`36946839410` ✅ GREEN** on `9f9ffac`. Device round ⏳ NOT RUN
> ([`DEVICE_ROUND.md`](DEVICE_ROUND.md)); **not merged** (`rule.md` §3).
>
> Part:
> - [81.1 Continue after cut off — the follow-up request and its bounds](PART_81_1_CONTINUE_AFTER_CUTOFF.md)

## Why this phase exists

A long answer stops in two different places, and both were dead ends:

| Where it stops | Evidence on this branch |
|---|---|
| The app's own character cap | `AiPolicy.kt:32` `MAX_REPLY_CHARS = 24_000`; `AiAnswer.kt:37` sets `cutShort` when the accumulator fills |
| The model's own output budget | `AiPolicy.kt:40` `MAX_OUTPUT_TOKENS = 8_192` asked per request; `AiAnswer.kt:43` sets `cutShort` on `finishReason = MAX_TOKENS` |

The user saw one line (`AiErrors.kt:72` — *"The answer was cut short because it
reached the length limit."*) and **Copy** — there was no way to ask for the
rest. Phase 81 adds the missing door.

## What shipped

| File | Change |
|---|---|
| `ui/ai/AiContinuation.kt` (new, pure) | `AiContinuationRequest(index, tail)`; `tailOf` (last 1 400 chars, snapped to a line break when close, `…`-marked when cut); `block(request)` (the model-facing resume sentence + the tail); `canContinue`, `remainingChars`, `requestBudget`, `limitNote`. Bounds: `MAX_CONTINUATIONS = 8`, `MAX_TOTAL_CHARS = 64_000`, `TAIL_CHARS = 1_400`, `MIN_REMAINING_CHARS = 600`. |
| `ui/ai/AiContext.kt` | `AiPrompt.continuation: AiContinuationRequest? = null`; `userText` = `AiPromptText.userText(this) + AiContinuation.block(continuation)`. One string, so the preview, `sentChars` and the sent body cannot drift (D4). |
| `ui/ai/GeminiClient.kt` | `stream(..., maxChars: Int = AiLimits.MAX_REPLY_CHARS, onText)` → `AiAnswerAccumulator(maxChars = maxChars)`. A continuation passes what is left of the total budget; the default keeps the three existing callers unchanged. |
| `ui/ai/AiViewModel.kt` | `continuations` state field (in memory, D6); `fun continueAnswer()` builds the continuation prompt and lands on **PREVIEW** — it never touches the network; `send()` keeps the earlier text (`answer = base + streamed`, `answer = base + outcome.text`) and passes `AiContinuation.requestBudget(base.length)`; `preview()` and `clear()` reset the counter. |
| `ui/ai/AiCopy.kt` | `CONTINUE`, `CONTINUE_HINT`, `continuePreviewNote(index)`, `CONTINUE_AGENT_NOTE`, `CONTINUE_PROPOSAL_NOTE`. |
| `ui/ai/AiChatSheet.kt` | A `Continue` button in the pinned DONE bar (only on a cut-off prose answer with room left), the one-line reason when it cannot be offered, the hint beside the cut note, and the continuation disclosure on the preview. |
| `ui/screens/EditorScreen.kt` | `onContinue = aiViewModel::continueAnswer`. |

## Laws and pins

- **D4 is unchanged:** Continue only builds the ordinary preview of the same
  request plus the tail; the user taps **Send** there, and the preview renders
  the same two strings that leave the phone. No new `client.stream` site — the
  file still has exactly **three** (`send`, the private `agentTurn`,
  `testConnection`).
- **D6 is unchanged:** the counter and the text live in memory only; nothing new
  is persisted, and no new store key exists.
- **D1/D5 untouched:** no writes, no commands, no filter changes.
- **Bounded by construction:** ≤ 8 continuations, ≤ 64 000 characters in one
  visible answer, ≤ 24 000 characters added per reply, and each continuation's
  request is the same context — nothing grows without a tap.

## Deliberate limits (v1)

- **An agent task does not offer Continue.** After Phase 80 merges, *Ask about
  the project* / *Propose edits* end in the agent loop; its caps already bound
  the turn count, so a cut final answer gets `CONTINUE_AGENT_NOTE` instead of a
  button ("ask a new question for the rest"). Making a continuation an extra
  agent turn is a future change (it needs the loop's budget, not this one's).
- **An edit proposal does not offer Continue.** The reply is reviewed as a diff;
  resuming mid-proposal would re-parse a fragment. The note points at the review
  card.
- **`MAX_OUTPUT_TOKENS` is not raised here.** Continuing adds text; it does not
  change how much the model may write per request. Raising the per-request
  ceiling is a separate, cost-relevant decision (see
  [`NVIDIA_API_RESEARCH_20261002.md`](../../../research/NVIDIA_API_RESEARCH_20261002.md) §5).

## Verification

- **Host harness (kotlinc/JRE, `rule.md` §9):** `AiContinuationTest` **12**
  (tail whole/marked/line-snapped/empty, budget math, tap and length limits,
  block wording, limit note) and `AiContinueWiringTest` **7** (button gating,
  preview-only, three stream sites, the single user string, base + budget,
  resets, purity) — **19 new cases**, and **260/260** across the 19
  host-compilable AI test classes.
- **CI (`rule.md` §5, the executor of record):** `Build APK` run
  **`36946839410` ✅ GREEN** on `arena/01a0f9a5-codec` @ `9f9ffac` (release APK
  `7,104,312 B`, debug `26,831,328 B`) — `:app:testDebugUnitTest` ran the new
  classes in the real Android/JUnit environment and the Compose changes
  compiled. The first push (`14c14f7`) failed CI on exactly one real error —
  the new button lives in `BottomBar`, which had not gained the `onContinue`
  parameter (`AiChatSheet.kt:776 Unresolved reference 'onContinue'`) — fixed in
  `9f9ffac`.

## Exit condition

CI green on the session branch, this brief committed with the code, and the
device round run by the owner. **No PR, no merge before the owner's command**
(`rule.md` §3).
