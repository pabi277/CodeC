# Phase 91 — the simple chat (the owner's Phase 90 round)

> **Status: ✅ IMPLEMENTED (2026-10-04) on `arena/01a102bd-codec`.** Simple is the default face of the sheet; every
> reply carries its own **Copy**; the **New question** button is gone and the composer is on screen after an answer;
> the quoted conversation is closed with an instruction so both providers read it as conversation; and a code block
> is visible in the dark theme. Its own [`DEVICE_ROUND.md`](DEVICE_ROUND.md) is owner-only and **nothing is
> pre-ticked**.
>
> **Authorization:** the owner ran the [Phase 90 round](../chat-phase90/DEVICE_ROUND.md) on his phone and answered
> row by row in chat on **2026-10-04** — *"C1 work but it's way to technical, make it a toggle option to hide all
> technical part and make it only question answer, set the copy part after every reply default, and remove the new
> question it also default no need to click the new question part"*, *"C2 fail"*, *"C7 follow up question totally
> can't handle gemini and nvidia"*, *"C8 … no permission please check the ai permissions"*, *"C9 no visible block"*,
> *"C11 in light mode visible but dark mode not visible"*, *"N1 … continue, proposal edit, run approval not working
> at all"*. This phase is that message, turned into work. **No PR, no merge, no `main` push** (`rule.md` §3).
>
> **Not a roadmap level.** Levels 13–14 stay unauthorized, and Level 12 stays **not accepted**.

## What he reported (and where each row is answered)

| His row | His words (2026-10-04) | Answered by |
|---|---|---|
| C1 | works — but *"way to technical"* | [91.1](PART_91_1_SIMPLE_CHAT.md) **Simple** face, **default**; 91.2 Copy on every reply; 91.2 the New-question button removed |
| C2 | ❌ fail — screenshot: both messages on screen, and the model answers as if it had no history | [91.3](PART_91_1_SIMPLE_CHAT.md) the conversation is re-framed and closed with an instruction |
| C3 | ✅ pass (the **Earlier turns** disclosure matched the block exactly) | kept as it is — the failure was the model's reading, not the disclosure |
| C4 | ⏳ *"not possible it's just not doing that big work"* | recorded in [91.5](PART_91_2_FINDINGS.md) — the caps were never reached on his phone; a model-capability note, no code change |
| C5 | ✅ pass (provider switch mid-conversation) | unchanged |
| C6 | ❓ *"don't understand"* | the row is reworded in the phase 91 round; the ＋ action is explained in [91.5](PART_91_2_FINDINGS.md) |
| C7 | ❌ fail *"follow up question totally can't handle gemini and nvidia, some progress but not good"* | [91.3](PART_91_1_SIMPLE_CHAT.md) |
| C8 | ❌ *"same as before no permission"* | [91.5](PART_91_2_FINDINGS.md) — the AI permission surface, re-checked; fix phase if the report holds |
| C9 | ❌ *"no visible block"* | [91.4](PART_91_1_SIMPLE_CHAT.md) — frame, header strip and Copy were already drawn; the block's own surface was the bubble's colour, so it read as plain text |
| C10 | ✅ pass | unchanged |
| C11 | ❌ *"in light mode visible but dark mode not visible"* | [91.4](PART_91_1_SIMPLE_CHAT.md) |
| C12, C13 | ✅ pass | unchanged |
| C14 | ❓ *"do not understand"* | the row is reworded in the phase 91 round |
| N1 | ❌ *"continue, proposal edit, run approval not working at all"* | [91.5](PART_91_2_FINDINGS.md) — three separate causes, each written down; none of the three is a Phase 91 code change |
| N2, N3 | ✅ ok | unchanged |

## The parts

| Part | File | What it is |
|---|---|---|
| **91.1** | [The simple face](PART_91_1_SIMPLE_CHAT.md) | **Simple / Technical** in the header, **Simple default**: the machinery (activity card, numbers, reviewer, counters, per-turn model labels, the preview's technical rows) is hidden — but never a control, and never the exact text that leaves the phone (**D4** stays one tap away) |
| **91.2** | [Copy, and the button that went](PART_91_1_SIMPLE_CHAT.md) | Every assistant turn carries its own **Copy**; **New question** is gone from the bar and the **composer is on screen after an answer** — type and send, nothing to press first |
| **91.3** | [A conversation, read as one](PART_91_1_SIMPLE_CHAT.md) | The quoted block now opens by saying what it is and **closes with an instruction**: *"End of the quoted conversation — continue it by answering the request that follows."* The last line of a request is the one a model weighs most (C2, C7) |
| **91.4** | [The block in the dark theme](PART_91_1_SIMPLE_CHAT.md) | The code block's own surface was `surfaceVariant` — the bubble's colour — so it vanished in the dark theme; now `surface` with an `outline` frame (C9, C11) |
| **91.5** | [Recorded, not fixed](PART_91_2_FINDINGS.md) | C4 (model capability), C8 (permissions), N1 (Continue / proposal / run approval), C6 + C14 rewording, and the V1–V8 re-run that moved here |

## Laws that did not move

- **D4** — the preview still comes before every Send, and the exact strings are still shown in full: in Simple they
  sit behind one **What will be sent** tap instead of always open.
- **D6** — nothing about the chat or the face is persisted. The face is display state in `AiUiState`, in memory.
- **S1** — the face never changes a request. The packed bytes are identical in Simple and Technical; only the
  drawing changes. Pinned by `Phase91SimpleChatTest`.
- **S6 / S8 / S9 / S10** — Copy is still the only action an answer offers; the recipient is still frozen per
  request (Simple names it once in the header and in the preview, Technical keeps the per-turn label); no ceiling
  moved; the quoted block is still data, not instructions.
- **Three `client.stream(` sites, two `openUri(` in `ui/ai`, no new dependency, no new permission.**

## Exit conditions

- [x] The four changes are written with their tests; the host harness is green.
- [x] `DEVICE_ROUND.md` (this phase's own) carries the re-run rows, **nothing pre-ticked**.
- [ ] **Build APK green on the head** — recorded with the run's own annotations when it lands.
- [ ] **The owner's round** — R1–R8 plus the failed rows of Phase 90.
