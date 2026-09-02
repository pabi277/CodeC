# CodeC Website Phase W3 — Engines + Packages + FAQ + About

**Status:** 📋 **PLANNED** — not yet started. Awaiting owner's "Start W3"
(can run in parallel with W2 — both only depend on W1).
· **Cost:** `[static]` — four new files in `website/`; zero app code
· **Depends on:** W1 (chrome)
· **Blocks:** nothing directly; completes the **product wing** (7/7 pages)

> Master spec: [`../WEBSITE_PLAN.md`](../WEBSITE_PLAN.md) §3.1
> (`/engines`, `/packages`, `/faq`, `/about`), §4 content rules, §5
> self-dependent.

---

## Why this exists

Four pages that turn the README's dense sections into browsable, linkable
answers: *why four compiler engines?*, *what can I actually install?*,
*my compile failed — now what?*, *what is this project, really?* After W3
the product wing is done; W4–W6 build the learning wing on top.

---

## The four parts

| Part | Title | Effort | Doc |
|---|---|---|---|
| **W3.1** | Compiler engines (`/engines`) | S | [PART_3_1_ENGINES.md](PART_3_1_ENGINES.md) |
| **W3.2** | Package hub & repository (`/packages`) | S | [PART_3_2_PACKAGES.md](PART_3_2_PACKAGES.md) |
| **W3.3** | FAQ & troubleshooting (`/faq`) | S | [PART_3_3_FAQ.md](PART_3_3_FAQ.md) |
| **W3.4** | About CodeC (`/about`) | S | [PART_3_4_ABOUT.md](PART_3_4_ABOUT.md) |

---

## ⚖️ Ground rules

- **Tables are verbatim truth:** the engine table (W3.1) and the package
  table (W3.2) must match `README.md` exactly in scope — W3.2 additionally
  pulls the **authoritative package list from the repo's own build config**
  (`codec-packages/`) at implementation time and records it in
  `chat-web3/` (this table feeds W4.2's verification gate and the course).
- FAQ answers are website-length (2–5 sentences) + a "go deeper" link into
  `docs/` — never a copy-paste dump.
- No app code, no PR/merge without the owner's command. Chrome byte-identical
  to W1.1 (active-nav state only).

## Standing rules (unchanged)

- Session branch only; merge gate; verify state first.
- Self-dependent sweep after each part (plan §5.5).
- Living docs updated in the same commit; session record →
  `web_docs/chat-web3/`.
