# CodeC Website Phase W6 — Learning Wing III: Chapters 13–17 + Polish + Deploy

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
phase closes the W0→W6 arc: after it, `web_docs/NEXT_STEPS.md` records the
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

---

## 🔎 Research note (2026-09-02, W1 session) — Pages is ALREADY the package repo

Verified while answering the owner's "can you create a link / direct link"
question: **this repo's GitHub Pages site already exists** — it is the
**signed CodeC package repository**. `.github/workflows/package-repository.yml`
(`publish-dev` job) uploads `packages/` (`dev/` + `keys/`) via
`actions/upload-pages-artifact` + `actions/deploy-pages` to the
`github-pages` environment. That is what serves
`https://pabi277.github.io/CodeC/dev` (the app's apt source — an app
invariant) and `/CodeC/keys` (the keyring).

**Consequence for W6.7 (hard constraint):** a Pages deploy of `website/`
**replaces the whole site** (one Pages site per repo; actions-deploy
swaps the entire artifact). A naive website deploy would **wipe `/dev` and
`/keys`**, breaking every installed app's package source — and the next
package-repo publish would in turn wipe the website. The W6.7 design must
make them **coexist**, e.g.:

- the deploy step fetches the currently-published Pages content (or the
  latest `codec-repository-*` artifacts / re-runs `generate+sign`) and
  uploads **`website/` + `dev/` + `keys/` together** as one artifact; and
- `package-repository.yml`'s publish job gets the mirror change (include
  the site) — note: touching that workflow is app-side and therefore
  **requires the owner's explicit command** (beyond D7's pages.yml + README
  line), so the choice must be recorded in `DECISIONS.md` at W6 start.

Also verified 2026-09-02: the sandbox cannot reach
`pabi277.github.io` (TLS blocked), so W6.7's live-link checks happen from
CI or the owner's browser, not the sandbox.
