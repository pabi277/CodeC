# Phase 92.1 — *"Not all test run"* (the owner's first round, 2026-10-05)

> **Status: ✅ IMPLEMENTED (2026-10-05) on `arena/01a102bd-codec`.** His screenshot from the device: the card open,
> *Check 3 of 5*, checks 1 and 2 green, checks 3–5 *not run*, the hint *"Send this check in the bar below when you
> are ready"* — and **no control on the card that could move the run on**. His words: **"Not all test run."**
>
> **Authorization:** the same message. **No PR, no merge, no `main` push** (`rule.md` §3).

## What the screenshot proved

The card read `Check 3 of 5 · Follow-up uses it — not run` and offered only **Copy report** and **Stop check**.
Everything else was correct and working: check 1 (`all-files access granted (API 36)`), check 2
(`answered (2 chars, 3329 sent)`), the redaction note, the two controls that existed.

Both the button the owner needed and the sentence explaining why he could not see it were missing, and the reason
has two halves:

| # | Cause | Why it happened |
|---|---|---|
| 1 | **The way on was invisible.** `Next check` was drawn only when the current step could already be judged (`live != null`). While a step was *waiting for its Send*, the row had no way forward at all, and the only escape was closing the card. | Phase 92.0 wrote the button for the happy path — *send each check, then move on* — and never wrote the path for *I could not or did not send this one*. |
| 2 | **A refused door said nothing.** The preview is built by the ordinary doors (`agentAsk`/`agentPropose`), and those doors refuse while an answer is still streaming, while a project walk is running or while an apply is in flight. The old `selfCheckNext()` returned in silence on that refusal. | A refusal that is not spoken reads exactly like a button that does nothing — the owner's C6/C14 complaint from Phase 90 all over again. |

## The fix

1. **The button is never hidden.** One button is drawn for every step that is left. Its label is the truth:
   **Next check** when this step has an answer to record, **Skip check** when it is still waiting for its Send.
   Nothing on the card can be a dead end any more.
2. **Skip check is a real verdict, not a shrug.** `AiSelfCheck.skipped(step)` records the step as
   `PENDING — "skipped, not run"`, which the report counts in its **not run** column, never in `passed`. A check
   nobody ran must never read as a check that worked. The card's finished state says *finished — Copy report*,
   and the run is never called "all checks run" after a skip.
3. **The run carries itself.** The moment a verdict lands, the card builds the next question by itself (after
   `AUTO_ADVANCE_MS = 900`, so the line can be read). The only tap left in the whole run is the **Send** of a
   preview — which is exactly what **D4** requires and all the owner asked for.
4. **A refusal is said out loud.** `selfCheckBusy()` names the three states that make a door refuse
   (`STREAMING`, `gathering`, `applying`); both **Next check** and **Skip check** then put
   *"The previous answer is still coming in — tap again in a moment."* in the notice line instead of doing
   nothing.

## What did **not** change

- **D4** — every check is still an ordinary preview and the Send is still the owner's tap. The auto-advance calls
  `selfCheckNext()`, which only *previews*: `AiSelfCheckWiringTest` still finds no `send()` in the block, still
  exactly **three** `client.stream(` sites.
- **D6** — the run, the verdicts and the report are still memory-only; the report still carries counts, labels and
  booleans, never a prompt or an answer.
- **The honesty rule** — a step is still judged only when *its own question* is the settled task on screen; a
  skipped step is never judged at all. Nothing was loosened to make the card look finished.

## Lesson for the record

The first Phase 92 round shipped a control surface whose **every control was conditional on success**: the button
appeared only after a step had worked. A self-testing feature must be able to report **"I could not run this"** —
otherwise its silence is the bug the owner reports instead of the bug it should have found. That is now a law of
this phase: *every step of the check has a way out, and a way out is recorded, not hidden.*

## Tests (this increment)

| File | New cases | What they pin |
|---|---|---|
| `AiSelfCheckTest` | 1 | `skipped` is `PENDING`, its line says *skipped*, and a run of skips reports `0 passed … not run` with no `PASS` mark anywhere |
| `AiSelfCheckWiringTest` | 4 | the button is drawn for **every** remaining step and its label matches what it will do · a skipped check is recorded through `AiSelfCheck.skipped` and the card never claims "all checks run" · the card advances itself (`LaunchedEffect(run.stepIndex, live?.detail)` + `AUTO_ADVANCE_MS`) and never advances a finished run · `selfCheckBusy()` names the three refusing states and both buttons say so instead of going silent |

**Host harness: 80 passed / 0 failed** (Phase 90/91's 41 + this phase's 39).

## Build facts

**Build APK green on `e06d3a5`** — run [`37231109942`](https://github.com/pabi277/CodeC/actions/runs/37231109942):
release `CodeC-IDE-1.3.17-universal.apk` = **7 192 032 B**, debug = **27 108 868 B**,
`mapping.txt` = 71 635 235 B, release manifest with **no `android:debuggable` flag**. The first head of this
increment (`57bd267`) failed the debug Kotlin compile because the new constant was inserted between the card's
`@Composable` and its function — the annotation landed on the property. Fixed in `e06d3a5`, with the shape now
pinned in `AiSelfCheckWiringTest` (the card stays `@Composable private fun SelfCheckCard(`, and no `@Composable`
may land on a `val`/`var`/`const val` in the sheet): a single-file syntax check cannot see that class of mistake,
and the host harness has no Compose compiler, so CI is what found it.

## Still open with the owner

- **Run it again.** Tap **Self-check**, then the four Sends; the card now walks itself and *Copy report* is the
  only other thing to press. The pasted report is what closes this phase.
- The two Phase 91 questions are unchanged: the exact C8 sentence he saw, and which `N1` item he wants changed.
