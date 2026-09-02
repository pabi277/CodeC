# NEXT_STEPS.md — website head state

> **HEAD STATE (2026-09-02): W0 (planning) COMPLETE — zero website code
> exists.** The website is fully spec'd in `WEBSITE_PLAN.md` (7 pages, static
> HTML/CSS, GitHub Pages, phases W1–W5). Nothing is built, nothing is
> deployed, there is no `website/` folder.

## What the owner says next

| Owner says in chat | Agent does |
|---|---|
| **"Build the website"** / "start W1" | Begin W1 (scaffold + Home) strictly per `WEBSITE_PLAN.md` §9: create `website/`, shared chrome, stylesheet, Home page; record in `web_docs/chat-web2/`; update living docs; commit + push; report; stop at the merge gate. |
| "Change page X / the design / the stack" | Update `WEBSITE_PLAN.md` + `DECISIONS.md` in the same commit — still no code until implementation is commanded. |
| (answers O1–O5) | Record the answer in `DECISIONS.md` (close the open item), adjust the plan if needed. |
| "Create PR and merge" | Open/merge the PR for the current session branch per `rule.md` §3 — **only on this exact class of command.** |

## Phase queue (implementation — not started)

- [ ] **W1** — scaffold `website/` + Home page
- [ ] **W2** — Install + Getting Started
- [ ] **W3** — Engines + Packages
- [ ] **W4** — FAQ + About
- [ ] **W5** — polish + GitHub Pages deploy + README link + link sweep

## Verification before any implementation session

1. `git status` — on an `arena/*` session branch.
2. `web_docs/` and `web_prompt.md` exist and match this head state (they do
   as of 2026-09-02; if a newer session doc says otherwise, trust the newest
   dated entry in `WEB_JOURNEY.md`).
3. `README.md` re-read before writing any page — content rules §4 bind.

## History pointer

Full narrative: [`WEB_JOURNEY.md`](WEB_JOURNEY.md). Session records:
`chat-web1/` (W0 planning, 2026-09-02).
