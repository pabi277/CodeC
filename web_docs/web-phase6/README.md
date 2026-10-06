# CodeC Website Phase W6 — Learning Wing III: Chapters 13–17 + Polish + Deploy

> **Mandatory phase-exit law (D31):** deliver a verified ZIP of the entire current
> website and a direct GitHub download link in the final report. See
> [REVIEW_DOWNLOAD_RULE](../REVIEW_DOWNLOAD_RULE.md). No PR/merge/deploy implied.

> **Current v2.3 — 2026-10-07.** **PLANNED / NOT STARTED** — awaits its own owner command after earlier phases.

Ch-13…17, THEN W6.8 ch-18 and W6.9 ch-19, THEN W6.6 polish and W6.7 deployment. Seven new HTML files. All 29 pages and 19 chapters; P1+P5 owner device transcripts. Preserve signed package Pages endpoints.

**9 parts in this phase.** The six phases contain **39 docs (6 READMEs + 33 PART docs)**.

Read [master plan v2.3](../WEBSITE_PLAN.md) and the binding v2.3 block at the top
of every PART doc. Current requirements override the retained prior design below:
29 pages, 19 chapters, AI nav, Privacy footer, release v1.3.18, contacts in-app
only, no deleted UI or cancelled proposals taught. W1→W6 strictly sequential.
Use [NEXT_STEPS](../NEXT_STEPS.md) for status, not old session-folder assumptions.

| Added part (before polish/deploy) | Spec |
|---|---|
| W6.8 ch-18 Ask about your code | [PART_6_8_CH18.md](PART_6_8_CH18.md) |
| W6.9 ch-19 Agent, tools and approvals | [PART_6_9_CH19.md](PART_6_9_CH19.md) |

---

## Prior phase brief — historical v2.2 context

The earlier status, counts and session pointers below are superseded above.

**Status:** 📋 **PLANNED** — not yet started. Awaiting owner's "Start W6".
· **Cost:** `[static]` — seven new files in `website/` + **one allowed
  exception**: the GitHub Pages workflow (`.github/workflows/pages.yml`) +
  one line in the root README (D7)
· **Depends on:** W4 + W5 (course continuity); W3 (About/FAQ links)
· **Blocks:** nothing — after W6 the site is **live**; the learning wing
  and the product wing are both complete

> Master spec: [`../WEBSITE_PLAN.md`](../WEBSITE_PLAN.md) §3.2, §5
> (self-dependent verification), §8 (deployment), §10 (acceptance).

---

## Why this exists

The last five chapters finish the course (device APIs, web projects,
customization & power tools, real projects, troubleshooting), then the
whole 25-page site gets its final polish, its GitHub Pages home, and its
self-dependent proof (grep sweep + offline render + full link sweep). This
phase closes the W0→W6 arc: after it, `web_docs/getting-started/NEXT_STEPS.md` records the
live URL.

---

## The seven parts

| Part | Title | Effort | Doc |
|---|---|---|---|
| **W6.1** | Chapter 13 — Device APIs (CodeCApi) | S | [PART_6_1_CH13.md](PART_6_1_CH13.md) |
| **W6.2** | Chapter 14 — Web Projects & Live Preview | S | [PART_6_2_CH14.md](PART_6_2_CH14.md) |
| **W6.3** | Chapter 15 — Custom Setup & Advanced Tools | S | [PART_6_3_CH15.md](PART_6_3_CH15.md) |
| **W6.4** | Chapter 16 — Real World Projects | **M** | [PART_6_4_CH16.md](PART_6_4_CH16.md) |
| **W6.5** | Chapter 17 — Troubleshooting | S | [PART_6_5_CH17.md](PART_6_5_CH17.md) |
| **W6.6** | Polish pass (all 25 pages) | S | [PART_6_6_POLISH.md](PART_6_6_POLISH.md) |
| **W6.7** | Deploy (GitHub Pages) + link sweep + self-dependent verification | S | [PART_6_7_DEPLOY.md](PART_6_7_DEPLOY.md) |

---

## ⚖️ Ground rules

- **Chapters 13–15 teach only shipped features.** Planned app phases
  (20–24) may appear **only** as a one-line "coming" note — never as
  content (plan §2).
- **W6.7 is the only part allowed to touch outside `website/`:** the Pages
  workflow file + the one-line README link (D7). Nothing else in the repo
  moves in this phase.
- **Verification before "live":** the deploy part does NOT open the site to
  the world until the §5 sweep + offline check + link sweep all pass and
  are recorded in `chat-web6/` (a green Pages build of an unverified site
  is a process failure, not an acceptable risk).
- Chrome byte-identical; no app code; no PR/merge without the owner's
  command (the deploy lands when the owner merges — same gate as every
  phase).

## Standing rules (unchanged)

- Session branch only; merge gate; verify state first.
- Living docs updated in the same commit; session record →
  `web_docs/chat-web6/` (includes the verification records).
