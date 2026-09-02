# CodeC Website Phase W4 — Learning Wing I: Course Home + Chapters 01–06

**Status:** 📋 **PLANNED** — not yet started. Awaiting owner's "Start W4".
· **Cost:** `[static]` — eight new files in `website/`; zero app code
· **Depends on:** W1 (chrome) + W2 (course home & ch-01 point at `/install`)
· **Blocks:** W5 (chapter nav continuity; W4.2 locks the final chapter set)

> **Owner:** "I want it to be learning page also see this website
> https://pabitra27706-oss.github.io/Termux-Mastery/" (2026-09-02) — a
> book-like course from zero to advanced, hands-on.
> Master spec: [`../WEBSITE_PLAN.md`](../WEBSITE_PLAN.md) §3.2, §5, §6
> (learn layout).

---

## Why this exists

The learning wing is half the owner's request and the reason the site
outlives the README: a beginner reads the site like a book, types every
command into CodeC, and finishes able to build real things. This phase
opens the course: the home page (structure + chapter table, Termux-Mastery
pattern) and the first six chapters (install → first program → terminal →
engines → packages → files/projects).

---

## The canonical chapter template (every chapter page, W4–W6)

```
<header chrome — active nav: Learn>
Chapter crumb:  "Chapter N of 17 — <title>"
H1: chapter title
.goal-box   : "After this chapter you can…" (2–4 bullets)
"what you need" line: a fresh CodeC install, or named earlier chapters
Step sections (H2 numbered): each step = prose + .code-block the learner
                             types into CodeC; expected output shown
.try-it     : 1–3 exercises, each with the expected result
.mistake    : common mistakes (bullets: symptom → fix)
prev / next : .prev-next links to chapters N−1 / N+1
<footer chrome>
```

Rules: inline code blocks only (self-dependent); every command works on a
fresh install; no chapter requires opening the repo (repo links are
optional "go deeper" footnotes only); "Chapter N of 17" uses the **locked**
count from W4.2.

---

## The eight parts

| Part | Title | Effort | Doc |
|---|---|---|---|
| **W4.1** | Course home (`/learn`) | S | [PART_4_1_LEARN_HOME.md](PART_4_1_LEARN_HOME.md) |
| **W4.2** | **Verification gate** — re-read README + package config; lock chapter set (closes O6) | S | [PART_4_2_VERIFY_GATE.md](PART_4_2_VERIFY_GATE.md) |
| **W4.3** | Chapter 01 — Getting Started | S | [PART_4_3_CH01.md](PART_4_3_CH01.md) |
| **W4.4** | Chapter 02 — Your First C Program | S | [PART_4_4_CH02.md](PART_4_4_CH02.md) |
| **W4.5** | Chapter 03 — The CodeC Terminal | S | [PART_4_5_CH03.md](PART_4_5_CH03.md) |
| **W4.6** | Chapter 04 — Compiler Engines | S | [PART_4_6_CH04.md](PART_4_6_CH04.md) |
| **W4.7** | Chapter 05 — Package Manager | S | [PART_4_7_CH05.md](PART_4_7_CH05.md) |
| **W4.8** | Chapter 06 — Files & Projects | S | [PART_4_8_CH06.md](PART_4_8_CH06.md) |

**Order inside the phase: W4.2 first** (the gate), then W4.1, then chapters
01–06. The gate decides what the course may claim; the course home then
publishes the locked chapter table.

---

## ⚖️ Ground rules

- **Only what CodeC ships.** W4.2's verified-facts table is the course's
  source of truth from this phase on; if a proposed chapter can't be
  taught from shipped features, it is cut or re-scoped (recorded in
  `DECISIONS.md`).
- Structure mirrors the owner's Termux-Mastery (same owner — clean-room
  safe); **content is written fresh for CodeC** — nothing copied from that
  site.
- License line on `/learn`: use the repo's license unless the owner answers
  O7 (record the assumption in `chat-web4/`).
- Chrome byte-identical to W1.1; self-dependent sweep per part (plan §5.5).
- No app code, no PR/merge without the owner's command.

## Standing rules (unchanged)

- Session branch only; merge gate; verify state first.
- Living docs updated in the same commit; session record →
  `web_docs/chat-web4/`.
