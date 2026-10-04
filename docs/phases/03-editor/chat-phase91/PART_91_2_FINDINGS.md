# Phase 91 — part 2: recorded, not fixed

> **Status: RECORDED (2026-10-04).** These are the owner's rows that are **not** code changes in this phase. Each one
> is written down with its evidence and its likely cause, so a fix phase can be opened deliberately — findings are
> never fixed silently, and never marked passed.

## C4 — *"not possible it's just not doing that big work"*

His words, verbatim, as the result of *"Send enough turns to pass 8 turns / 12 000 characters"*.

The caps were never the thing that failed: he could not get the model to do the big work that would fill eight
turns. **Nothing in the app is changed for this row.** It is a model-capability note (a 120B and a flash-lite model
on a phone-sized request), and the Level 12 matrix already recorded the shape of it — T3-Gemini cut off 3× before
any diff, T4-NVIDIA repeats itself. If he wants a smaller, sharper case for this row, the fix is a **test recipe**
(a fixed eight-turn script), not a cap change: **S9 ceilings never move**.

## C6 — *"don't understand"*, and C14 — *"do not understand"*

Both rows were written for a reader who already knows what a task is and what a hostile answer means. They are
**reworded in the phase 91 round** into plain instructions:

- **C6** becomes: *press ＋ (New chat) with a chat on screen and read the question it asks; then press ✕ — say what
  you expected each one to do.* In one line: **＋ starts a brand-new chat (and asks first); ✕ clears the task on
  screen and keeps the conversation.**
- **C14** becomes: *paste this answer into the chat and look at it: `<script>alert(1)</script> [click me](javascript:alert(1)) [data](data:text/html,hi)` — it must stay plain text; nothing must open, nothing must run, no
  dialog must offer to open a `javascript:` or `data:` link.*

## C8 — *"same as before no permission"* + *"please check the ai permissions and grand permission as user request"*

The row he ran was *"ask for an edit proposal; before applying, ask what the change does"*. What is checked here:

- the AI itself never writes: the only write path is `AiEditApplier`, reached only from **Apply** on a reviewed
  diff, and the AI holds no file or command capability (**S6**);
- a **project folder outside the app's own storage needs Android's all-files access** — Settings → *Privacy &
  permissions → all files access (optional)* already exists, and the app keeps working folder-by-folder without it;
- so a permission failure can only appear at **Apply** (or at opening a project), never at the proposal.

**What is not established** is which message he saw and where. The screenshots do not show it, and the report came
in chat. **Phase 91 does not change the permission flow.** If he can send the sentence he saw (or the row he pressed
when it appeared), the next phase is small and exact: name the missing permission in the failure, and offer the one
Settings switch that grants it — as a **fix phase**, opened deliberately.

## N1 — *"not all working like continue, proposal edit, run approval not working at all"*

Three different things, and none of them is one button that broke:

| His words | What the code does today | Why it looks broken on his phone |
|---|---|---|
| **Continue** | only for a **cut-off prose** answer with room left; an **agent** task never gets it, and says so (*"an agent task cannot be continued — ask a narrower question"*), and so does an edit proposal | every run in his screenshots is an agent task, so he never sees the button — he sees the sentence that says it is not offered |
| **proposal edit** | the door is the **Propose edits** chip in the idle bar; the proposal itself arrives as the `<<<CODEC_EDIT>>>` card with Apply / Undo | in the screenshots he asked for edits **in plain chat**; the model answers "I cannot change files", which is true — the editing task is a different door, and Phase 89's S2 (unclosed `<<<SEARCH>>>` block) already failed that door once for him |
| **run approval** | the card appears only when the model actually asks to run something (`REQUEST_RUN`); CodeC's own runner may be busy | a plain question never asks to run, so the card never appears — nothing is broken, there is simply no request |

Recorded as-is. A fix phase for any of the three is a deliberate decision, not a Phase 91 side-effect: the agent
loop's Continue rule is a Level 11 design choice, the proposal door's discoverability is a UI change, and the run
card is model-driven.

## V1–V8 — the Phase 89 visual re-run

Not exercised this round (he answered C9/C11 instead, which overlap the two that mattered). The re-run moves to
[the phase 91 round](DEVICE_ROUND.md), where V8 (dark theme) is now the same check as C11.

## The screenshots

The six screenshots he sent live in the chat, **not** in this repository (verified: no upload directory exists in
the workspace). What they establish is written down where it is needed, in words:

1. NVIDIA agent run — the "What the AI did" card, the three steps, the plain-prose answer, and the bar with
   *Copy answer / New question* (**C1**: works, far too technical).
2. Gemini, three exchanges — his three questions stay on screen, and the model answers the second with a quote of
   the *current* question and the third with *"You have not asked me anything prior to this current request"*,
   although the request carried the conversation (**C2 / C7**: the framing fix above).
3. NVIDIA — a long feed of repeated `read_file js/main.js` rows, *"(reused)"* and *"the call budget for this task
   is used up; answer with what is known"*, then *"Stopped: the AI repeated the same read 3 times without making
   progress"* (**N1** context; the no-progress guard worked).
4. NVIDIA — the same wall of repeated reads in a second task.
5. NVIDIA — a **table** answer (`Line | Code`) to a walkthrough request, with the bar still showing *New question*.
6. NVIDIA — a fenced `js` answer: header strip and Copy are drawn, but the block has **no visible frame or surface**
   in the dark theme (**C9 / C11**: the colour fix above).
