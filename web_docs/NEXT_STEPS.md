# NEXT_STEPS.md — website head state

> **HEAD STATE (2026-09-02, W2 COMPLETE — deploy returns to W6.7): 3 of 25
> pages exist.** W1 (scaffold + Home) and **W2 (2026-09-02, owner command
> "Start w2") are done**: `website/` now has `install.html` (3 GitHub-only
> APK paths, device-support table, optional Termux engine) and `start.html`
> (first hour: RUN ▶ → terminal loop + `./` rule → scanf-in-Term rule →
> 1-tap package install → web preview → bottom-bar map). Chrome
> byte-identical (diff-verified); §5.5 sweeps PASS; command blocks match
> README exactly; records + source lines in `web_docs/chat-web3/`. The
> early-deploy detour (D14) was reversed by the owner (D15) — **the site
> deploys at W6.7 only**; GitHub Pages still serves only the package repo
> (`/dev`, `/keys`). Remaining 18 pages: engines, packages, faq, about,
> learn + ch-01…ch-17 (404 until built, expected).

## What the owner says next

| Owner says in chat | Agent does |
|---|---|
| **"Start W3"** | Execute W3 strictly per `web_docs/web-phase3/` (README + 4 PART docs): `engines.html`, `packages.html` (package list from the `codec-packages/` build config, sha recorded), `faq.html`, `about.html`; record in `web_docs/chat-web4/`; update living docs; commit + push; report; stop at the merge gate. |
| **"Start W3"** … **"Start W6"** | Execute that phase strictly per its `web_docs/web-phaseN/` folder — one phase at a time, in order (W4.2 verification gate runs first inside W4); phase records in `chat-webN+1/`; device passes (W5 ch-08, W6 P1+P5) reported as **device pass required** with the owner's transcript. |
| "Change page X / the design / the stack" | Update `WEBSITE_PLAN.md` + the affected `web-phaseN/` part docs + `DECISIONS.md` in the same commit — still no code until implementation is commanded. |
| (answers O1–O7) | Record the answer in `DECISIONS.md` (close the open item), adjust the plan/part docs if needed. |
| "Create PR and merge" | Open/merge the PR for the current session branch per `rule.md` §3 — **only on this exact class of command.** |

## Phase queue (implementation)

- [x] **W1** — scaffold + Home · **COMPLETE 2026-09-02** · record: [`chat-web2/`](chat-web2/SUMMARY.md)
- [x] **W2** — Install + Getting Started · **COMPLETE 2026-09-02** · record: [`chat-web3/`](chat-web3/SUMMARY.md)
- [ ] **W3** — Engines + Packages + FAQ + About · spec: [`web-phase3/`](web-phase3/README.md)
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
`chat-web1/` (W0 planning, 2026-09-02) · `chat-web2/` (W1 scaffold + Home,
2026-09-02 — includes the byte-identical chrome pattern + card source
lines) · `chat-web3/` (W2 install + start, 2026-09-02).
