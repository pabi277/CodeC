# NEXT_STEPS.md — website head state

> **HEAD STATE (2026-09-02, W1 COMPLETE): the website EXISTS — scaffold +
> Home built, W2 is next.** W1 (2026-09-02, owner command "Build the
> website") created `website/` per `web-phase1/`: `style.css` (tokens + all
> components, system fonts only), `favicon.svg` (local mark), `index.html`
> (shared chrome + Home: hero → CTA → 6 cards → learn banner → footnote).
> Fully self-dependent from the first byte (first §5.5 sweep PASS, recorded
> in `chat-web2/`); no JavaScript; mobile nav = checkbox pattern; internal
> links are **relative `*.html`** (works under the Pages subpath — recorded
> in `chat-web2/`). The chrome copy-paste pattern lives in
> `web_docs/chat-web2/SUMMARY.md` — W2–W6 copy it byte-identical. Still
> zero: no app-code touches, no deploy, no Pages workflow (that's W6).
> 21 pages remain: install, start, engines, packages, faq, about, learn +
> ch-01…ch-17 (they 404 until built — expected).

## What the owner says next

| Owner says in chat | Agent does |
|---|---|
| **"Start W2"** | Execute W2 strictly per `web_docs/web-phase2/` (README + PART_2_1 + PART_2_2): `website/install.html` + `website/start.html` (full content per plan §3.1) inside the W1 chrome, record in `web_docs/chat-web3/`, update living docs, commit + push, report, stop at the merge gate. |
| **"Start W3"** … **"Start W6"** | Execute that phase strictly per its `web_docs/web-phaseN/` folder — one phase at a time, in order (W4.2 verification gate runs first inside W4); phase records in `chat-webN+1/`; device passes (W5 ch-08, W6 P1+P5) reported as **device pass required** with the owner's transcript. |
| "Change page X / the design / the stack" | Update `WEBSITE_PLAN.md` + the affected `web-phaseN/` part docs + `DECISIONS.md` in the same commit — still no code until implementation is commanded. |
| (answers O1–O7) | Record the answer in `DECISIONS.md` (close the open item), adjust the plan/part docs if needed. |
| "Create PR and merge" | Open/merge the PR for the current session branch per `rule.md` §3 — **only on this exact class of command.** |

## Phase queue (implementation)

- [x] **W1** — scaffold + Home · **COMPLETE 2026-09-02** · record: [`chat-web2/`](chat-web2/SUMMARY.md)
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
`chat-web1/` (W0 planning, 2026-09-02) · `chat-web2/` (W1 scaffold + Home,
2026-09-02 — includes the byte-identical chrome pattern + card source
lines).
