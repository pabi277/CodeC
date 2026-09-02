# CodeC Website Phase W2 — Install Guide + Getting Started

**Status:** 📋 **PLANNED** — not yet started. Awaiting owner's "Start W2".
· **Cost:** `[static]` — two new files in `website/`; zero app code
· **Depends on:** W1 (chrome)
· **Blocks:** W4 (course home + chapter 01 both point at `/install`)

> **Owner:** site must take a visitor "from never heard of CodeC to first
> compiled program" (2026-09-02).
> Master spec: [`../WEBSITE_PLAN.md`](../WEBSITE_PLAN.md) §3.1 (`/install`,
> `/start`), §5 self-dependent.

---

## Why this exists

These two pages are the conversion path: Home says *what* — `/install` says
*get it*, `/start` says *run your first program*. Together they must make a
fresh phone go from zero to `./a.out` printing, with the site alone.

---

## The two parts

| Part | Title | Effort | Doc |
|---|---|---|---|
| **W2.1** | Install guide (`/install`) | S | [PART_2_1_INSTALL.md](PART_2_1_INSTALL.md) |
| **W2.2** | Getting started — first hour (`/start`) | S | [PART_2_2_START.md](PART_2_2_START.md) |

---

## ⚖️ Ground rules

- **Install facts are law** (D9): GitHub-only distribution — Actions
  artifact `CodeC-IDE` on a green `Build APK` run, GitHub Releases, in-app
  Settings → Install APK from GitHub. **No store implication, ever.**
- Content source: `README.md` ("Install the APK from GitHub", "Run C on the
  phone", Troubleshooting) — every step traceable, recorded in `chat-web3/`.
- Chrome is the W1.1 copy-paste pattern, byte-identical (add active-nav
  state only).
- No app code, no PR/merge without the owner's command.

## Standing rules (unchanged)

- Session branch only; merge gate; verify state first.
- Self-dependent sweep after each part (plan §5.5).
- Living docs updated in the same commit; session record →
  `web_docs/chat-web3/`.
