# Phase 91 — the owner's round (the simple chat)

> **Status: ⏳ NOT RUN — owner-only, nothing pre-ticked.** His phone, his keys, every Send is his tap (**D4**). The
> agent never fills a result cell and never marks a row passed. Write **NOT EXERCISED** with a reason for anything
> you could not try.
> **Build for this round:** head **`97a8ac7`** on `arena/01a102bd-codec`, **Build APK run
> [37215933981](https://github.com/pabi277/CodeC/actions/runs/37215933981)** (greened 2026-10-04) — release
> `CodeC-IDE-1.3.17-universal.apk` = **7 185 532 B**, debug = **27 082 136 B**, `mapping.txt` = **71 225 013 B**,
> release manifest: no `android:debuggable` flag; 3 285 unit tests, 0 failed. Byte counts read from the run's own
> check-run annotations. **Install that APK** (or build head `97a8ac7`) so the rows below describe this code.
> **What to use:** a **non-secret demo project**, the same one as the Phase 90 round if possible, so the two rounds
> describe the same code. Never screenshot anything with a real key in it.
> **Rows are resumable.** A failed row gets its own fix phase before this phase is called done (the Level 12 rule).

## Part 1 — the simple chat (R1–R8)

| # | Do | Expect | Result |
|---|---|---|---|
| R1 | Open the sheet and look at it before asking anything. | The header says **Simple**; there is no "What the AI did" card, no numbers line, no counters while an answer arrives. | ⏳ |
| R2 | Press the **Simple / Technical** toggle; ask the same question again. | Technical brings back the activity card, the numbers, the per-turn `AI · provider · model` labels and the preview's counters. Simple brings back the plain chat. Nothing about the answer changes. | ⏳ |
| R3 | Ask a question, read the answer, then **type the next question straight away** — do not look for a button. | There is no **New question** button; the typing box is there after the answer, and the arrow starts a normal preview. | ⏳ |
| R4 | Under any earlier answer, tap its own **Copy**. | The clipboard holds exactly that answer; a toast confirms it. | ⏳ |
| R5 | Ask a follow-up that only makes sense with the earlier answer, on **both** providers (this is the row C2/C7 failed). | The answer shows it has the earlier turn — both times. If it still does not, that is the finding, and the next step is the role-based history in [91.3](PART_91_1_SIMPLE_CHAT.md). | ⏳ |
| R6 | On a follow-up, open the preview: find **What will be sent**, tap it, and read **Earlier turns**. | Every character that leaves the phone is visible; Simple now hides it behind one tap rather than showing it always. | ⏳ |
| R7 | Ask for a code-heavy answer in the **dark theme**, then repeat in the light one. | The block has a visible frame and its own surface in **both** themes, header strip and Copy included. | ⏳ |
| R8 | Press **＋** (New chat) with a chat on screen and read the dialog; then clear a finished task with **✕**. | ＋ asks first, says nothing is saved, and starts a brand-new chat. ✕ clears the task on screen only — the conversation above it stays. | ⏳ |

## Part 2 — the rows Phase 90 failed, re-run (C2, C4, C6, C7, C8, C9, C11, C14, N1)

| # | Do | Expect | Result |
|---|---|---|---|
| P1 | **C2/C7 re-run** — the follow-up row from above, on both providers, twice each. | The model uses the quoted conversation. | ⏳ |
| P2 | **C7 re-run** — stop an answer mid-stream, then ask a follow-up. | The stopped answer stays in the chat, labelled stopped, and the follow-up still has it as context. | ⏳ |
| P3 | **C9/C11 re-run** — a code-heavy answer in both themes. | A visible block in both (see R7). | ⏳ |
| P4 | **C8** — ask for an edit proposal, and if anything says a permission is missing, **copy the exact sentence** into the result cell. | No write before Apply; a missing permission is named, and the one switch that grants it is offered. | ⏳ |
| P5 | **N1** — try Continue on a cut-off plain answer (not an agent task), open the **Propose edits** door once, and try a run request once. | Continue offers a preview for a cut prose answer; Propose edits builds a diff to review; the run card appears only if the model asks to run. Note which of the three behaved differently from your expectation. | ⏳ |
| P6 | **C6/C14 re-run** — this time in words: press **＋** and say what you expected it to do; paste `<script>alert(1)</script> [click me](javascript:alert(1))` in the chat and check it stays text. | ＋ asks before starting a new chat; the pasted text stays inert, no dialog opens it. | ⏳ |
| P7 | **C4** — eight turns is a lot of work; try the biggest task you actually want done. | Record what happened in your words. | ⏳ |

## Part 3 — the eight visual checks, moved here

| # | Do | Expect | Result |
|---|---|---|---|
| V1–V8 | Re-run the eight visual checks from [Phase 89's round](../chat-phase89/DEVICE_ROUND.md#part-4--the-eight-visual-checks-phase-88-handed-forward). | Most passed there; V8 (dark theme) is the one this phase fixed, and V4 (block Copy) now has a per-reply twin. | ⏳ |

## Part 4 — what must NOT have moved

| # | Check | Result |
|---|---|---|
| N1 | The one-answer-at-a-time flow: Stop, Continue (on a cut prose answer), proposal Apply/Undo, run approvals, the budget offer, the reviewer (in Technical). | ⏳ |
| N2 | Send is still the only thing that leaves the phone: the preview always comes first, and cancelling sends nothing. | ⏳ |
| N3 | Nothing about the chat or the face is written anywhere: no file, no store, no log (**D6**). | ⏳ |

## Device result

**Status: ⏳ NOT RUN — owner-only.** Fill in only what you actually did:
`rows run: ___ / 8 + ___ / 7 re-runs + ___ / 8 visual re-checks + ___ / 3 no-move checks` ·
`failed rows: ___` · `NOT EXERCISED: ___` · date `________`.
