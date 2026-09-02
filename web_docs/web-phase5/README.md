# CodeC Website Phase W5 — Learning Wing II: Chapters 07–12

**Status:** 📋 **PLANNED** — not yet started. Awaiting owner's "Start W5".
· **Cost:** `[static]` — six new files in `website/`; zero app code
· **Depends on:** W4 (template, crumb count, verified-facts table)
· **Blocks:** W6 (chapters 13–17 continue the nav)

> Master spec: [`../WEBSITE_PLAN.md`](../WEBSITE_PLAN.md) §3.2 chapter
> outlines, §5 self-dependent. Canonical chapter template:
> [`../web-phase4/README.md`](../web-phase4/README.md) §"canonical chapter
> template".

---

## Why this exists

The middle of the course: from *using* the app to *working* in it — the
editor as a real tool (07), learning actual C (08), scripting (09), Python
(10), git with GitHub (11), and networking with what the repo ships (12).
Chapter 08 is the course's heart: every snippet must be TCC-safe and
device-verified before the phase closes.

---

## The six parts

| Part | Title | Effort | Doc |
|---|---|---|---|
| **W5.1** | Chapter 07 — The Editor | S | [PART_5_1_CH07.md](PART_5_1_CH07.md) |
| **W5.2** | Chapter 08 — C Programming Basics | **M** | [PART_5_2_CH08.md](PART_5_2_CH08.md) |
| **W5.3** | Chapter 09 — Shell Scripting | S | [PART_5_3_CH09.md](PART_5_3_CH09.md) |
| **W5.4** | Chapter 10 — Python in CodeC | S | [PART_5_4_CH10.md](PART_5_4_CH10.md) |
| **W5.5** | Chapter 11 — Git & GitHub | S | [PART_5_5_CH11.md](PART_5_5_CH11.md) |
| **W5.6** | Chapter 12 — Networking & SSH | S | [PART_5_6_CH12.md](PART_5_6_CH12.md) |

---

## ⚖️ Ground rules

- **TCC-safe teaching (ch-08 law):** standard C only — ANSI C + what
  built-in TCC demonstrably covers; **no C11-only constructs** in the
  course; every snippet reviewed before commit; the phase's exit includes a
  **device pass** (owner runs ch-08 programs on a real device — transcript
  in `chat-web5/`, same convention as app phases: never claim acceptance
  without one).
- **Only what the W4.2 verified facts allow** (ch-10 pip, ch-12 tools,
  ch-09 bash — all gated by the verified table; fallbacks written into the
  part docs).
- Chrome byte-identical; self-dependent sweep per part (plan §5.5).
- No app code, no PR/merge without the owner's command.

## Standing rules (unchanged)

- Session branch only; merge gate; verify state first.
- Living docs updated in the same commit; session record →
  `web_docs/chat-web5/`.
