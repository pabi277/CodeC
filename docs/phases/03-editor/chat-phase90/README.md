# Phase 90 — The conversation surface

> **Status: 📋 BRIEFED (2026-10-04) — decision-complete, no code yet.** The owner's own request, dictated straight
> after his Level 12 device round: ***"the ai feels not real i want the features like new chat or a follow up chat
> etc options"***, and, on the visuals: ***"i think it should be better like code with a visible block, etc other
> stuff like a real ai"***. **This is not Level 13 and not Level 14** — those stay unauthorized and untouched. This
> is a product-surface phase the owner asked for by name.
> **Authorization:** he said *"whatever i provide work on that"*. Per `rule.md` §3 the agent writes the brief here
> and implements **on his explicit go** — no PR, no merge, no `main` push.
> **Baseline:** `arena/01a102bd-codec` @ `7c1c0e1` (Phase 89 / Level 12 implemented, CI-green `37202771868`,
> Level 12 **not accepted** — S2, T3-Gemini and T4-NVIDIA failed and their fix phases are unopened).
> **Read with:** [Phase 89 README](../chat-phase89/README.md) · the [owner's device report](../chat-phase89/DEVICE_ROUND_OWNER_REPORT_2026-10-04.md) ·
> [`rule.md`](../../../../rule.md) §3/§9 · [Level 12 spec](../../../roadmaps/ai-integration/12_AGENT_EVALUATION_AND_ACCEPTANCE.md).

## What he asked for, in his words

1. **New chat** — a way to start a fresh conversation instead of the sheet being one answer at a time.
2. **Follow-up chat** — asking the next question in the same conversation, with the earlier exchange as context.
3. **"Like a real AI"** — code in a visible block, and the surface reading like a real assistant chat.
4. *"other questions skip, whatever i provide work on that"* — so this brief makes decisions instead of asking;
   every default below is listed with its reason and can be vetoed in one line.

## What exists today (measured on `7c1c0e1`)

| Thing | Today | Where |
|---|---|---|
| One exchange at a time | `answer` is **replaced** by the next request; there is no transcript | `AiViewModel.kt:34` (`AiUiState`) |
| A "you" bubble and an "AI" bubble | They exist — but only ever one pair on screen | `AiChatSheet.kt:914` `YouBubble`, `:931` `AiBubble` |
| Clear | `clear()` wipes the task: answer, timeline, offers, review, numbers | `AiViewModel.kt:1832` |
| A new request | Builds a fresh prompt; nothing of the last one travels | `AiContext.kt:135` (`userText`), `:577` (`AiPromptText.userText`) |
| Continue | Exists, but only for an answer **cut short**, and only its last 1 400 chars | `AiContinuation.kt` |
| Code blocks | Already have a background, a language label and **Copy** — but he wants them to read as *a block* | `AiMarkdownView.kt:200-230` |
| Persistence | **None.** Answer, timeline, review, numbers are all in-memory (D6) | `AiViewModel.kt:34-110` |

## The design, in one screen

- A **transcript** of the conversation lives in the ViewModel: his messages and the assistant's answers, in order,
  **in memory only** (D6). It dies with the sheet's process, by design — and the app says so where it matters.
- **Sending again is the follow-up.** The next request carries a bounded transcript block inside the user text, so
  the preview still shows *exactly* what leaves the phone (D4) — the transcript is disclosed as a collapsed
  section with its exact character count.
- **New chat** empties the transcript and the task together, and asks for confirmation only when there is
  something to lose.
- **The transcript is data, never instructions** — same sentence as project text (S10), and its lines can never
  become a tool call or an edit proposal: only the **newest** answer is ever parsed (pinned).
- **Nothing about the caps moves** (S9), no new network road, no new dependency, no new permission.

## The four parts

| # | Part | What lands |
|---|---|---|
| **90.1** | [The session transcript](PART_90_1_SESSION_TRANSCRIPT.md) | `AiChatSession.kt` — the pure, clock-free, bounded model: turns, caps, packing, stripping of protocol markers, the "only the newest answer is parsed" rule, and the D6 pin |
| **90.2** | [The conversation surface](PART_90_2_CONVERSATION_SURFACE.md) | The sheet draws the turns; **New chat**; the follow-up preview disclosure; per-turn provider/model labels; task controls (Stop, Apply/Undo, run approvals, Continue, budget offer) unchanged and still per-task |
| **90.3** | [The real-assistant look](PART_90_3_REAL_AI_LOOK.md) | Code blocks as visible blocks (border + header + language + Copy), inline-code chips, list/quote/heading hierarchy, bubble polish, dark theme — all still the hand-written Markdown model, no HTML, no dependency |
| **90.4** | [The device checklist](PART_90_4_DEVICE_CHECKLIST.md) | Owner-only rows C1–C14 for the new surface, plus the V1–V8 re-run after the polish |

## Agent defaults — decided, not asked (veto any)

| # | Default | Why |
|---|---|---|
| 1 | **8 turns / 12 000 transcript characters**, oldest dropped first with a visible `[earlier turns left out]` marker; a single long answer keeps its newest 6 000 characters with a marker | Matches the existing context budget (`AiLimits.MAX_CONTEXT_CHARS = 12_000`, `AiPolicy.kt:23`) and Level 9/10 eviction habits; bounded, shown, never silent |
| 2 | The transcript carries **his messages and the assistant's prose only** — never tool rows, never reviewer text, never raw edit/tool blocks | Those are derived or machine markup; replaying them invites stale blocks (and re-injection). A proposal is recorded as one factual line |
| 3 | A **stopped** answer joins the transcript as what arrived, marked stopped; a **failed** request adds no assistant turn | What he saw is what the conversation was |
| 4 | **New chat** confirms only when there are turns; the existing **✕ clear** keeps its meaning (clear this task, keep the conversation) | Two different intents, two different losses — no silent data loss |
| 5 | Follow-ups are ordinary Sends: same preview, same Send button, no new network road, **three `client.stream(` sites stay three** | D4/S6 |
| 6 | Provider/model are labelled **per turn** (a transcript may span a manual switch) | S8: switching is manual and history must not rewrite the recipient |

## How the standing laws are kept

| Law | How |
|---|---|
| **D4** every request previewed | The preview shows the full user text including the transcript, collapsed with its exact count; nothing is sent without Send |
| **D6** never persist raw chat | The transcript lives in `AiUiState`; no file, store, log or backup; dies with the process; a test pins the absence |
| **S6** no writes/commands from `ui/ai/` | The transcript is text. The only write path stays `AiEditApplier` behind Apply |
| **S7** one brain writes | Unchanged |
| **S8** manual provider switch | Per-turn labels; switching is blocked while busy |
| **S9** ceilings never move | 12 / 24 / 3 / 8 / 24 000 / 8 000 and runs 2 untouched; the transcript cap is a new **prompt** bound, stated and previewed |
| **S10** injection | Transcript text carries the data-not-instructions sentence and is never parsed as tool/edit markup; only the newest answer is |
| **Only road** | Send; exactly three `client.stream(` sites; exactly two `openUri(` in `ui/ai/` |

## Planned tests (to be written with the code)

| File | Kind | Cases (planned) |
|---|---|---|
| `AiChatSessionTest` | pure host | ~14 — caps, drop-oldest marker, per-turn clip, the new message is never dropped, protocol markers stripped, only the newest answer is parsed, stopped/failed turn rules, clearing rules, D6 shape |
| `AiChatSessionWiringTest` | source pins | ~8 — three stream sites, two `openUri(`, zero writes in `ui/ai/`, the preview packs exactly `userText`, New chat confirmation, per-turn labels, no persistence calls |
| `AiMarkdownTest` / view pins | host + CI | code-block header/label/Copy pins where host-checkable; the drawn result is CI/device |
| `Phase90DeviceTest` | doc pin | the checklist exists with C1–C14 and nothing pre-ticked |

## Exit conditions

- [ ] 90.1–90.3 are written with their tests; the host harness is green; Build APK is green on the head.
- [ ] Every default in the table above is either implemented or explicitly overridden by the owner in chat.
- [ ] `DEVICE_ROUND.md` (this phase's own) carries C1–C14 and the V1–V8 re-run, **nothing pre-ticked**.
- [ ] No ceiling moves, no new dependency/permission/endpoint, no PR and no merge.

## Open items this phase does **not** close

- Phase 89 / Level 12 is still **not accepted**: **S2**, **T3-Gemini** and **T4-NVIDIA** failed and each needs its
  own fix phase (owner's rule) — **not opened**, awaiting his command. The read-window finding (F1) is the fourth
  candidate. This phase is his newer request and does not silently absorb them.
- The T7/T10/B2 rows of the Level 12 round remain NOT EXERCISED with recipes in the
  [owner report](../chat-phase89/DEVICE_ROUND_OWNER_REPORT_2026-10-04.md).
