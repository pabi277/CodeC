# Phase 90.4 — The device checklist

> **Status: ✅ IMPLEMENTED (2026-10-04) — the round now lives in the phase's own
> [`DEVICE_ROUND.md`](DEVICE_ROUND.md), owner-only and nothing pre-ticked.** Like Phase 89's
> [`DEVICE_ROUND.md`](../chat-phase89/DEVICE_ROUND.md): the agent never fills a result cell. `Phase90DeviceTest`
> pins the rows and the emptiness of every cell, so no later edit can quietly drop a row or tick one. The rows
> below are what the owner and only the owner can answer with a phone in hand, and the round adds the **N1–N3**
> checks for what must not have moved.

## Before the rows

- Build: the Phase 90 head (sha and Build APK run id to be filled at implementation time, from the run's own
  annotations).
- Use a **non-secret demo project**. The transcript is raw chat: never screenshot anything with a real key in it.
- Rows are resumable; a row that could not be attempted is **NOT EXERCISED** with a written reason.

## Part 1 — the conversation (C1–C8)

| # | Do | Expect | Result |
|---|---|---|---|
| C1 | Ask a question, read the answer, then ask a follow-up in the same sheet. | Both your messages and both answers stay on screen, in order, each answer labelled with who answered it. | ⏳ |
| C2 | Ask a follow-up that only makes sense with the earlier answer ("explain the second point more"). | The answer shows it has the earlier turn; the preview's collapsed "Earlier turns" section holds exactly the text that was sent. | ⏳ |
| C3 | Open the preview on a follow-up and expand the earlier turns. | Every character that leaves the phone is visible; the character count matches. | ⏳ |
| C4 | Send enough turns to pass 8 turns / 12 000 characters. | The oldest turns drop with a visible marker, your newest message is never the one dropped, and the preview shows the marker. | ⏳ |
| C5 | Switch provider (Gemini → NVIDIA) mid-conversation and ask a follow-up. | The follow-up goes to the provider you chose; earlier turns stay labelled with who actually answered them (S8). | ⏳ |
| C6 | Press **⧉ New chat** with turns on screen; then press **✕ clear** with turns on screen. | New chat asks first, then empties the conversation and the task. ✕ clears only the current task — the conversation stays. | ⏳ |
| C7 | Stop an answer mid-stream, then ask a follow-up. | The stopped answer stays in the conversation, marked stopped, and the follow-up still has it as context. | ⏳ |
| C8 | Ask for a proposal; before applying, ask what the change does. | The proposed edit card is unchanged, Apply/Undo unchanged, and the transcript saw prose — **no** re-emitted edit block. | ⏳ |

## Part 2 — the look (C9–C14, then the V1–V8 re-run)

| # | Do | Expect | Result |
|---|---|---|---|
| C9 | Ask for a code-heavy answer. | Code sits in a **visible block**: frame, header with language + Copy, monospace, scrolls sideways. | ⏳ |
| C10 | Tap Copy in a block; paste elsewhere. | Exactly the block's text. | ⏳ |
| C11 | Repeat C9 in the **dark theme**. | Frame, header and code all legible — the block is visible against the bubble, not merged into it. | ⏳ |
| C12 | Ask for an answer with a very long code line. | The line scrolls instead of wrapping or clipping; nothing is silently lost. | ⏳ |
| C13 | Ask a long multi-section answer and scroll. | Headings, lists, quotes and rules read as a hierarchy; scrolling stays smooth (Q2 said no lazy list needed — confirm the polish did not change that). | ⏳ |
| C14 | Paste a hostile answer containing `<script>`, `javascript:` and `data:` links again. | Still inert text; the link rules are untouched by the polish. | ⏳ |
| V1–V8 | Re-run the eight visual checks from [Phase 89's round](../chat-phase89/DEVICE_ROUND.md#part-4--the-eight-visual-checks-phase-88-handed-forward). | This is the re-run the owner asked for: most passed before, and the polish is meant to clear the "visible block / real AI" note. | ⏳ ×8 |

## Device result

**Status: ⏳ NOT RUN — owner-only.** Fill in only what you actually did:
`rows run: ___ / 14 + ___ / 8 visual re-checks` · `failed rows: ___` · `NOT EXERCISED: ___` · date `________`.
A failed row gets its own fix phase before this phase is called done (the same rule as Level 12).
