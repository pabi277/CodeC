# Phase 90.2 — The conversation surface

> **Status: ✅ IMPLEMENTED (2026-10-04).** Touches `AiViewModel.kt`, `AiChatSheet.kt`, `AiCopy.kt`,
> `AiContext.kt`, `AiParts.kt`. Everything user-visible is new copy in `AiCopy.kt` (the house rule: no string
> literals in composables).

## What the sheet becomes

```
┌──────────────────────────────────────────────┐
│  AI · gemini-3.1-flash-lite        ⧉   ⌄     │   ⧉ New chat   ⌄ minimize
├──────────────────────────────────────────────┤
│                                    ┌───────┐ │
│                                    │ YOU   │ │   his message (YouBubble, as today)
│                                    └───────┘ │
│  ┌─────────────────────────────────────────┐ │
│  │ AI · gemini-3.1-flash-lite              │ │   the answer, Markdown, as today
│  │ …                                       │ │   (AiBubble — gains the turn's label)
│  └─────────────────────────────────────────┘ │
│                                    ┌───────┐ │
│                                    │ YOU   │ │   the follow-up
│                                    └───────┘ │
│  ┌─────────────────────────────────────────┐ │
│  │ AI · nvidia/nemotron-…      (stopped)   │ │   after a manual switch (S8)
│  └─────────────────────────────────────────┘ │
├──────────────────────────────────────────────┤
│  [ Ask a follow-up… ]              Send ▶    │   one input, the only road
└──────────────────────────────────────────────┘
```

- **The turns are the conversation.** `AiChatSheet.Conversation` (`AiChatSheet.kt:253`) draws the transcript top
  to bottom; the current task's live/streaming answer is the last bubble. The **pinned bottom bar** (Send / Stop /
  Copy / Continue / Apply) keeps its behaviour, so nothing scrolls out of reach.
- **One `YouBubble` per sent message, one `AiBubble` per completed (or stopped) answer**, each labelled with who
  answered it. `YouBubble` and `AiBubble` already exist (`AiChatSheet.kt:914`, `:931`); this phase puts more than
  one pair on screen and moves the label into the bubble header.
- **New chat** — an action in the header, `AiCopy.NEW_CHAT`. With turns present it opens a small confirm dialog
  (**"Start a new chat? This clears the conversation. It is not saved anywhere."** → *New chat* / *Keep it*); with
  an empty transcript it acts immediately. It calls `clear()` and empties the session.
- **Follow-up** — nothing new to press: the input bar is always "the next message". The preview changes:
  - header gains **`Follow-up · N earlier turns`** and the exact total characters;
  - a collapsed **"Earlier turns (N characters) — Show"** section renders each packed turn verbatim (the
    disclosure pattern V7 already passes);
  - the standing sentence stays true: only the two strings below leave the phone — the transcript is *inside* the
    user text.

## What does **not** change (deliberately)

| Thing | Why |
|---|---|
| Stop, Continue, proposal Apply/Undo, run approvals, budget offer, reviewer, backup offer | They are **per task**. The transcript is conversation context; each task still gets its own caps, its own approval pause and its own numbers |
| Agent tasks | A follow-up that starts an agent task is a **fresh task** carrying the transcript — its own 12/24/2 budget, its own timeline. Tool rows never enter the transcript (90.1 §2) |
| The three `client.stream(` sites, the two `openUri(` in `ui/ai/` | Send stays the only road; links stay `https`-only behind the dialog |
| The measurements card | Still one line per finished task, in memory (Phase 89). With a transcript it attaches to the turn that just finished |
| `clear()` semantics | Unchanged — the ✕ still clears the task; only New chat also empties the transcript |

## Deviations, as built

- **Idempotence by flag, not by content.** `AiUiState.taskCommitted` guards the commit at every point (a new
  preview, New chat, ✕ clear, an agent task starting), so a task joins the conversation exactly once and two
  identical questions in a row are never treated as a duplicate.
- **The commit point is the preview, not Send.** The transcript block of the request being previewed must include
  the task that just finished, so the commit happens while the new prompt is frozen — and a cancelled preview
  therefore commits the finished task too, which is correct: it had already finished.
- **An agent task carries the block inside its first packed turn.** The agent's runtime bytes come from
  `AiAgentPrompt.pack`, not `userText`, so the block is handed to `pack(transcript = …)` on the first turn and to
  `AiPrompt.userText` in the preview — one string, both places (**D4**).
- **`clear()` commits first.** ✕ means "clear this task, keep the conversation", so a settled task is committed
  before the view is emptied; **New chat** then empties the session itself.

## Wiring sketch (as built)

1. `AiUiState` gains `session: AiChatSession = AiChatSession()` (in-memory, D6) — beside `answer` at
   `AiViewModel.kt:34`.
2. `send()`: on approval (before `startMeasuring()`) the question is appended as a YOU turn; on
   `finishAgent`/the single-shot DONE path the answer is appended as an ASSISTANT turn with its status; `stop()`
   appends what arrived as `STOPPED`; `FAILED` appends nothing.
3. `AiPrompt` gains `session: AiChatSession = AiChatSession.EMPTY` (like `agentMemory`), and
   `userText` (`AiContext.kt:135`) prepends `session.pack(...)` for non-agent and agent requests alike — one
   string, so preview/sent bytes still cannot drift.
4. `AiChatSheet.Conversation` iterates `state.session.turns` then draws the live answer; `Header` gains the
   ⧉ action; the preview block gains the collapsed transcript section.
5. `AiCopy` gains: `NEW_CHAT`, `newChatConfirm()`, `followUpHeader(turns, chars)`,
   `earlierTurnsLabel(turns, chars)`, `turnLabel(provider, model, status)`, `stoppedSuffix`.

## Planned cases (`AiChatSessionWiringTest`, ~8 source pins)

`exactly three client.stream(` · `exactly two openUri( in ui/ai` · `no direct writes in ui/ai` ·
`the preview packs exactly userText` · `New chat is confirmed before it clears` ·
`turns are labelled with provider and model` · `no persistence calls touch session state` ·
`the session clears on a project switch`.

## Known interactions, decided here

- **A proposal is not a turn until it is answered**: the transcript records the assistant's prose and one factual
  line (`Proposed a reviewable edit to app.py`), never the block. This is 90.1 §5 and it is also what keeps the
  parser's input single.
- **Continue** stays what it is (a cut answer's tail, `AiContinuation`), and a continuation appends to the *same*
  assistant turn rather than creating a second one.
- **The reviewer** (`AiSource.REVIEW`) is displayed beside the answer as today and never enters the transcript.
- **Stop mid-follow-up** keeps the partial answer as a STOPPED turn, so the next message still has context.
