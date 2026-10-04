# Phase 90 — The conversation surface

> **Status: ✅ IMPLEMENTED (2026-10-04) on `arena/01a102bd-codec` — the owner said *"Go"* the same day.** All
> three code parts are written and host-tested (**30 new cases**: `AiChatSessionTest` 14, `AiChatSessionWiringTest`
> 10, `Phase90DeviceTest` 6 — **30 passed / 0 failed** in the sandbox harness, kotlinc 2.2.10), **Build APK green on
> the head** (run 37208366826), and the phase's own
> [`DEVICE_ROUND.md`](DEVICE_ROUND.md) is waiting for the owner (C1–C14 + the V1–V8 re-run + N1–N3, **nothing
> pre-ticked**). **No PR, no merge, no `main` push** (`rule.md` §3); Level 12 keeps its own open items. The owner's own request, dictated straight
> after his Level 12 device round: ***"the ai feels not real i want the features like new chat or a follow up chat
> etc options"***, and, on the visuals: ***"i think it should be better like code with a visible block, etc other
> stuff like a real ai"***. **This is not Level 13 and not Level 14** — those stay unauthorized and untouched. This
> is a product-surface phase the owner asked for by name.
> **Authorization:** he said *"whatever i provide work on that"*, and then **"Go"** when this brief was shown —
> the implementation in this folder is that command. No PR, no merge and no `main` push: `rule.md` §3 stands.
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
| **90.1** | [The session transcript](PART_90_1_SESSION_TRANSCRIPT.md) | ✅ `AiChatSession.kt` — the pure, clock-free, bounded model: turns, caps, packing, stripping of protocol markers, the "only the newest answer is parsed" rule, and the D6 pin |
| **90.2** | [The conversation surface](PART_90_2_CONVERSATION_SURFACE.md) | ✅ The sheet draws the turns; **New chat**; the follow-up preview disclosure; per-turn provider/model labels; task controls (Stop, Apply/Undo, run approvals, Continue, budget offer) unchanged and still per-task |
| **90.3** | [The real-assistant look](PART_90_3_REAL_AI_LOOK.md) | ✅ Code blocks as visible blocks (frame + header strip + language + Copy + divider), a drawn cap for pathological fences, bubble labels — all still the hand-written Markdown model, no HTML, no dependency |
| **90.4** | [The device checklist](PART_90_4_DEVICE_CHECKLIST.md) | ✅ The phase's own [`DEVICE_ROUND.md`](DEVICE_ROUND.md): owner-only rows C1–C14, the V1–V8 re-run, and N1–N3 "what must not have moved" — **nothing pre-ticked** |

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

## Tests (written, and green)

| File | Kind | Cases |
|---|---|---|
| [`AiChatSessionTest`](../../../../app/src/test/java/com/codeci/ide/AiChatSessionTest.kt) | pure host | **14** — both sides of an exchange in order; the data-not-instructions opener; a stopped answer kept as stopped; blank question/answer add nothing; provider+model per turn; turn cap and character budget drop the oldest **with the marker**; the newest turn survives; a long answer is clipped to its newest end with the marker; edit blocks, tool blocks and an unclosed block are stripped; a hostile earlier answer stays plain text |
| [`AiChatSessionWiringTest`](../../../../app/src/test/java/com/codeci/ide/AiChatSessionWiringTest.kt) | source pins (reads the real tree) | **10** — three `client.stream(` sites and two `openUri(` in `ui/ai`; `AiChatSession.kt` has no Android/IO/Log/clock/store; the key store and the Level 9 task store never learn about chat; the prompt packs the block inside `userText`; an agent task carries it on its first packed turn; the commit is idempotent (`taskCommitted`); New chat clears and ✕ keeps; a project switch drops it; every drawn turn names its real recipient; the disclosure shows the exact block |
| [`Phase90DeviceTest`](../../../../app/src/test/java/com/codeci/ide/Phase90DeviceTest.kt) | doc pin | **6** — C1–C8 asked, C9–C14 asked, the V1–V8 re-run present, the N1–N3 no-move checks present, nothing pre-ticked, the six vetoable defaults stated |

## Exit conditions

- [x] 90.1–90.3 are written with their tests; the host harness is green (**30 passed / 0 failed**, kotlinc 2.2.10).
- [x] Every default in the table above is implemented — the three that changed shape while being built are recorded
      as deviations in [90.1](PART_90_1_SESSION_TRANSCRIPT.md) and [90.2](PART_90_2_CONVERSATION_SURFACE.md).
- [x] `DEVICE_ROUND.md` (this phase's own) carries C1–C14, the V1–V8 re-run and N1–N3, **nothing pre-ticked**.
- [x] No ceiling moves, no new dependency, permission or endpoint (`client.stream(` = 3, `openUri(` = 2 — pinned).
- [x] **Build APK green on this head** — run **37209301287** on `46f2cdb` (release 7 184 512 B / debug
      27 076 916 B / mapping 71 266 812 B, no `android:debuggable`), read from the run's own annotations; the same
      facts are in [`DEVICE_ROUND.md`](DEVICE_ROUND.md) so the phone round names the build it ran.
- [ ] **The device round** — owner-only ([`DEVICE_ROUND.md`](DEVICE_ROUND.md)); a failed row gets its own fix phase.

## Implementation record (2026-10-04)

The owner said ***"Go"*** and all three code parts landed in one commit on `arena/01a102bd-codec`:

- **New file** `app/src/main/java/com/codeci/ide/ui/ai/AiChatSession.kt` — 2 enums, 1 data class, 1 pure model.
- **Wiring:** `AiViewModel.kt` (`session` + `taskCommitted` in the state, `commitFinishedTask()`,
  `sessionForNewPrompt()`, `newChat()`, the commit inside `clear()`, the project-switch drop, the agent task's
  frozen `transcript`), `AiContext.kt` (`AiPrompt.session`, the two `userText` branches), `AiAgentLoop.kt`
  (`AiAgentPrompt.pack(transcript = …)`, defaulted), `AiChatSheet.kt` (the turns, the ＋ New chat action with its
  confirm, the follow-up disclosure, `AiBubble`'s label), `AiCopy.kt` (the new strings), `AiMarkdownView.kt` (the
  block frame, header strip, divider and drawn cap), `EditorScreen.kt` (`onNewChat`).
- **Tests:** the three files above — 30 cases, all host-runnable, **30/0 green** in the sandbox harness.
- **One existing pin moved, deliberately:** `AiLevel11WiringTest` censuses the surfaces that draw a body through
  the Markdown model (six, plus the one verbatim reviewer body). Each earlier turn is a seventh Markdown surface
  (`Answer(turn.text)`), so the census is **6 → 7** with the new surface named in the test; the verbatim exception
  is still exactly one. `AiProviderWiringTest`'s immutable `provider`/`model` pin is untouched — the preview
  freezes them first and the session rides along on a second `copy`, so the pinned literal stands as written.

**Deviations from the brief, all deliberate** (also noted in the parts): the block is a `render()` string the two
`userText` branches place themselves, rather than a `pack(question)` that would have duplicated the new message;
idempotence is a `taskCommitted` flag rather than content matching (two identical questions must both be kept); and
an agent task carries the block **inside** its first packed turn — with `AiPrompt.session` doing the same in the
preview — because the agent's runtime bytes are built by `AiAgentPrompt`, and the preview and the sent request must
stay one string (**D4**).
