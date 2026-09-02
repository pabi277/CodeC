# NEXT_STEPS.md — website head state

> **HEAD STATE (2026-09-02, v2.1): W0 (planning) COMPLETE — zero website
> code exists, ALL PHASES FULLY SPEC'D.** The website is spec'd in
> `WEBSITE_PLAN.md` v2.1: **two wings** — product site (7 pages) +
> **learning wing** ("Master CodeC from Zero to Advanced", 17 chapters,
> modelled on the owner's Termux-Mastery), **fully self-dependent** (zero
> external resources, offline-complete, course completable without the
> repo). Stack: static HTML/CSS, GitHub Pages. **Phases W1–W6 are fully
> spec'd** in `web-phase1/` … `web-phase6/` (35 docs with exit conditions
> per part; W4.2 verification gate runs first in W4; device passes due in
> W5 ch-08 and W6 P1+P5; W6.7 verifies before the site goes live).
> Nothing is built, nothing is deployed, there is no `website/` folder.

## What the owner says next

| Owner says in chat | Agent does |
|---|---|
| **"Build the website"** / **"Start W1"** | Begin W1 strictly per `web_docs/web-phase1/` (README + PART_1_1 + PART_1_2): create `website/`, shared chrome (incl. Learn nav item), stylesheet, Home page with learning banner; self-dependent rule (§5) in force from the first file; record in `web_docs/chat-web2/`; update living docs; commit + push; report; stop at the merge gate. |
| **"Start W2"** … **"Start W6"** | Execute that phase strictly per its `web_docs/web-phaseN/` folder — one phase at a time, in order (W4.2 verification gate runs first inside W4); phase records in `chat-webN+1/`; device passes (W5 ch-08, W6 P1+P5) reported as **device pass required** with the owner's transcript. |
| "Change page X / the design / the stack" | Update `WEBSITE_PLAN.md` + the affected `web-phaseN/` part docs + `DECISIONS.md` in the same commit — still no code until implementation is commanded. |
| (answers O1–O7) | Record the answer in `DECISIONS.md` (close the open item), adjust the plan/part docs if needed. |
| "Create PR and merge" | Open/merge the PR for the current session branch per `rule.md` §3 — **only on this exact class of command.** |

## Phase queue (implementation — not started)

- [ ] **W1** — scaffold + Home · spec: [`web-phase1/`](web-phase1/README.md)
- [ ] **W2** — Install + Getting Started · spec: [`web-phase2/`](web-phase2/README.md)
- [ ] **W3** — Engines + Packages + FAQ + About · spec: [`web-phase3/`](web-phase3/README.md)
- [ ] **W4** — verification gate → course home + ch 01–06 · spec: [`web-phase4/`](web-phase4/README.md)
- [ ] **W5** — ch 07–12 (Editor, C Basics, Scripting, Python, Git, Networking) · spec: [`web-phase5/`](web-phase5/README.md) · device pass: ch-08
- [ ] **W6** — ch 13–17 + polish + deploy + verification · spec: [`web-phase6/`](web-phase6/README.md) · device pass: P1+P5

## Verification before any implementation session

1. `git status` — on an `arena/*` session branch.
2. `web_docs/` and `web_prompt.md` exist and match this head state (they do
   as of 2026-09-02; if a newer session doc says otherwise, trust the newest
   dated entry in `WEB_JOURNEY.md`).
3. `README.md` re-read before writing any page — content rules §4 bind.

## History pointer

Full narrative: [`WEB_JOURNEY.md`](WEB_JOURNEY.md). Session records:
`chat-web1/` (W0 planning, 2026-09-02).
