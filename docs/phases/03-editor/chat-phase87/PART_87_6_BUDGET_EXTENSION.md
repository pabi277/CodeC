# CodeC Phase 87.6 — Budget extension at the cap

> **Status:** 📋 PLANNED · **Cost:** `[client-only]` · **Effort:** M
> **Owner row (verbatim):** *"more options to use in the app for better optimization."*
> **Parent brief:** [Phase 87 README](README.md)

## First move: evidence, not code

Read 2026-10-03 against `main` @ `6838ea6`.

- The caps live in `AiAgentLoop.kt:29` `MAX_TURNS = 12` and `:32` `MAX_TOOL_CALLS = 24`.
- **S12** already requires that every stop produces a readable answer: Phase 84 added a reserved
  final-synthesis turn with tools **masked, not removed**, and `finalizeStop` guarantees prose on
  every `AiAgentStopReason`.
- Phase 82 established the retry precedent: *one visible, Stop-cancelable retry of the identical
  approved request*, sending the same strings and budget rather than an undisclosed new one.
- The owner's Level 4 caps are fixed: ≤ 12 turns, ≤ 24 tool calls, **≤ 2 runs**, ~5 min wall
  clock, enforced by CodeC after each proposed tool call.

The constraint that shapes this part: an extension is a **read-only** continuation. It may raise
the turn/tool budget for the current task. It may **never** raise the run count or grant an extra
approval — the run count is an owner cap on side effects, and S9 says no option raises a
permission.

## Design

```kotlin
enum class AiBudgetOffer { OFFER, NO_OFFER }

object AiBudgetExtensionPolicy {
    /** Extra turns one accepted extension may grant. Bounded, not user-tunable. */
    const val EXTRA_TURNS = 4
    const val EXTRA_TOOL_CALLS = 8
    const val MAX_EXTENSIONS_PER_TASK = 1

    /** An extension is offered only at a budget stop, never mid-turn. */
    fun offerAt(stopReason: AiAgentStopReason, used: Int, extensionsUsed: Int): Boolean

    /** The caps after an accepted extension — runs are untouched. */
    fun extend(turns: Int, toolCalls: Int, runs: Int): AiAgentCaps
}
```

`extend` returns the **same** `runs` it was given. That single line is the S9 guarantee for this
control, and a test asserts it directly rather than trusting the call sites.

The offer is a visible, dismissible card on the budget stop, in the same shape as Phase 82's
retry. Accepting sends the **same** disclosed request with the extended budget; declining leaves
the finalized answer from S12's synthesis turn on screen. Nothing is sent automatically.

**`NO_OFFER`** simply suppresses the card; the stop behaviour is unchanged. The default is
`OFFER`, matching today's behaviour of surfacing the situation.

### Why an extension is not a permission change

An extension spends more of the *same* budget the user already approved, on read-only turns, and
it is disclosed before it is spent. A run still needs its own tap. That is the line S9 draws, and
it is why this control can exist at all.

## The Android edge

One card in the chat timeline and one row in `AiHome`. The extended caps must appear in the
disclosed request so the user sees the new budget before Send, per D4 and Phase 82's
frozen-strings rule.

## Exit condition

- At a turn or tool-cap stop, the offer appears; accepting grants ≤ 4 extra turns and ≤ 8 extra
  tool calls, once per task.
- The run count is unchanged by an extension, provably.
- No extra approval is granted: an Apply and a Run still each need their own tap.
- Declining leaves the S12 finalized answer in place; nothing is sent.
- With `NO_OFFER`, no card appears and the stop behaves as it does at `6838ea6`.
- The extended budget is visible in the disclosed request before Send.

## Tests (plan)

- `AiBudgetExtensionPolicyTest` — **8**: `offerAt` for each `AiAgentStopReason` (offer on budget
  stops only); the caps after one extension; `MAX_EXTENSIONS_PER_TASK` enforced; **`extend`
  returns the same run count**; a second extension is refused.
- `AiAgentLoopTest` — **2 new**: an accepted extension continues from the same disclosed text;
  a declined extension still produces prose on the stop reason.
- `AiLevel10WiringTest` — **1 new**: source pin that no option path assigns to the run cap.

## Sources (record)

1. Repository: `AiAgentLoop.kt:29,32`, the S12 final-synthesis turn added in Phase 84, and
   Phase 82's identical-request retry — read 2026-10-03 against `main` @ `6838ea6`.
2. **S9** and **S12** in
   [`00_AGENTIC_MAP_AND_SECURITY_RULES.md`](../../../roadmaps/ai-integration/00_AGENTIC_MAP_AND_SECURITY_RULES.md).

## Deferred / rejected with reasons

- **A user-tunable extension size** — rejected; the size is a constant, so no option can push the
  loop past a bounded ceiling.
- **Automatic extension** — rejected; it would spend budget the user did not approve and would
  not be disclosed before being spent.
- **Extending the run count or the wall clock** — rejected; runs are side effects and their cap
  is an owner cap.
