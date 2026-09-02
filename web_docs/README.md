# web_docs/ — the CodeC website project (history & planning)

This folder is the **website's** equivalent of `docs/` (the app's history &
planning folder). Everything about the CodeC *website* — plans, decisions,
session records, narrative — lives here. The app project keeps using `docs/`
untouched.

**Entry point for a new chat:** [`../web_prompt.md`](../web_prompt.md) —
paste it as the first message. It is the website workstream's self-distrust
handoff file (the website's `prompt.md`).

## File map

| File | What it is | Update when… |
|---|---|---|
| [`README.md`](README.md) | This index — what the folder is, how it works | the folder's structure changes |
| [`WEBSITE_PLAN.md`](WEBSITE_PLAN.md) | **The master spec (v2)**: two wings — product site (7 pages) + learning wing ("Master CodeC from Zero to Advanced", `/learn` + 17 chapters) — per-page content, self-dependent rules, design, stack, repo layout, deployment, phases W1–W6, acceptance criteria | the owner changes scope/design/stack |
| [`DECISIONS.md`](DECISIONS.md) | Decision log (numbered D1, D2, …) with date, owner/agent attribution, and rationale | any decision is made or reversed |
| [`NEXT_STEPS.md`](NEXT_STEPS.md) | Head state line + what happens next (what the owner says to move) | every session that closes a gate |
| [`WEB_JOURNEY.md`](WEB_JOURNEY.md) | Narrative timeline of all website work (numbered entries, like `docs/JOURNEY.md`) | every session that closed something |
| `chat-webN/` | One folder per chat session on the website: what was asked, what was decided, what was done, evidence, next step (like `docs/chat-phaseN/`) | every session, before it ends |
| [`web-phase1/` … `web-phase6/`](web-phase1/README.md) | **The fully spec'd implementation phases W1–W6** (2026-09-02): each folder = phase README (status, cost, depends/blocks, parts table, ground rules) + one `PART_*` doc per page/chapter (design, implementation steps, exit condition) — the website's `docs/chat-phase20…24` equivalent. Owner starts one by saying **"Start W1"** … **"Start W6"**. | a phase's scope changes (update the part docs; record in DECISIONS.md) |

`chat-web1/` is the planning session (2026-09-02) that created this folder
— **including the phase specs** (W0.2). Naming split: `chat-webN` = session
records (what happened), `web-phaseN` = planned phases (what will be built).

## How this mirrors the app project

| App project | Website project |
|---|---|
| `prompt.md` (self-distrust handoff) | `../web_prompt.md` |
| `docs/` (history + planning) | `web_docs/` (this folder) |
| `rule.md` (operating manual, shared) | **same `rule.md`** — the website inherits its branching, merge gate, invariants and docs policy |
| `docs/chat-phaseN/` per session | `web_docs/chat-webN/` per session |
| Implementation: Android app code | Implementation: static site in `website/` (does not exist yet) |

## Ground rules (inherited, no exceptions)

1. **No PR/merge without the owner's literal command in chat** (`rule.md` §3).
2. **Website work never touches app code or `docs/`** — separate workstream,
   same repo.
3. **Append, don't destructively rewrite history** — session folders are
   permanent; living docs (`NEXT_STEPS.md`, `WEB_JOURNEY.md`, `WEBSITE_PLAN.md`)
   get their state summaries updated.
4. **Clean-room:** content is distilled from the repo's own public files; the
   Termux site is a structural reference only — never copy its source.
5. **The website writes no code until the owner commands implementation**
   ("Build the website" / "start W1"). Until then this folder is edited only.
