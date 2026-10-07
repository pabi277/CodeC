# CodeC Website Phase W1 — Scaffold + Home Page

> **Mandatory phase-exit law (D31):** deliver a verified ZIP of the entire current
> website and a direct GitHub download link in the final report. See
> [REVIEW_DOWNLOAD_RULE](../REVIEW_DOWNLOAD_RULE.md). No PR/merge/deploy implied.

> **Current v2.3 — 2026-10-07.** **IMPLEMENTED** — evidence in [W1_SUMMARY](../chat-web4/W1_SUMMARY.md); not merged or deployed.

Scaffold + Home (1/29 pages). Shared 10-link header including AI and Privacy footer; six cards, 19-chapter course preview, release facts. Only index.html/style.css/favicon.svg.

**2 parts in this phase.** The six phases contain **39 docs (6 READMEs + 33 PART docs)**.

Read [master plan v2.3](../WEBSITE_PLAN.md) and the binding v2.3 block at the top
of every PART doc. Current requirements override the retained prior design below:
29 pages, 19 chapters, AI nav, Privacy footer, release v1.3.18, contacts in-app
only, no deleted UI or cancelled proposals taught. W1→W6 strictly sequential.
Use [NEXT_STEPS](../NEXT_STEPS.md) for status, not old session-folder assumptions.

---

## Prior phase brief — historical v2.2 context

The earlier status, counts and session pointers below are superseded above.

**Status:** 📋 **PLANNED** — not yet started. Awaiting owner's explicit
"Start W1" (or "Build the website") command.
· **Cost:** `[static]` — new files inside `website/` only; **zero app code,
zero CI**
· **Depends on:** nothing (W0/W0.1 planning complete)
· **Blocks:** W2–W6 — everything renders inside this phase's chrome

> **Owner:** "I want to create a website for this project like Termux have"
> + "learning page also" (Termux-Mastery model) + "Make it fully proper self
> dependent" (all 2026-09-02).
>
> Master spec: [`../WEBSITE_PLAN.md`](../WEBSITE_PLAN.md) (v2) — §3.0 shared
> chrome, §3.1 Home, §5 self-dependent, §6 design.

---

## Why this exists

Every later phase writes pages *into* this scaffold. Getting the shared
chrome (header/footer), the stylesheet, and the design tokens right once —
with the self-dependent rule in force from the first byte — keeps all 25
final pages consistent and keeps the owner able to edit every file in a
browser on GitHub.

---

## The two parts

| Part | Title | Effort | Doc |
|---|---|---|---|
| **W1.1** | Shared scaffold (chrome + stylesheet + design tokens) | M | [PART_1_1_SCAFFOLD.md](PART_1_1_SCAFFOLD.md) |
| **W1.2** | Home page (hero, CTA, feature cards, learn banner) | M | [PART_1_2_HOME.md](PART_1_2_HOME.md) |

---

## ⚖️ Ground rules

- **Self-dependent from the first byte** (plan §5, D11): no external
  `src=`/`<link href=`/`@import`/`url()` ever — system font stacks, local
  favicon, inline-SVG icons only. First §5 grep sweep runs in W1.1.
- **Dark theme only** (v1). Light theme is not planned; don't build hooks
  for it.
- No app code, no `docs/`, no APK CI workflow changes. No PR/merge without
  the owner's explicit command (`rule.md` §3).
- Internal links use **final URLs from day one** (`/install`, `/learn`, …)
  — they 404 until their phase builds the page; the resolution sweep is W6.
- The chrome must be a **single documented copy-paste pattern** (recorded in
  `chat-web2/`) so W2–W6 stay byte-consistent across all 25 pages.

## Standing rules (unchanged)

- Session branch only; merge gate; verify state before acting.
- Content source of truth: `README.md` first, then `docs/`.
- Living docs updated in the same commit; session record →
  `web_docs/chat-web2/`.
