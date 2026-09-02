# WEB_JOURNEY.md — narrative timeline of the CodeC website

Numbered entries, newest last. Append-only — never rewrite an entry, add a
correction entry instead. This is the website's `docs/JOURNEY.md`.

---

## W0 — Planning (2026-09-02)

**1. Owner asks for a website (2026-09-02).** The owner wants a website for
CodeC "like Termux have" (the termux.dev model: landing + install +
getting-started + FAQ). With a **strict rule: no code of any kind** — the
project must start the same way the app did, with its history & planning
structure first: a `web_docs/` folder (the website's `docs/`) and
`web_prompt.md` (the website's `prompt.md`, the self-distrust handoff file).

**2. Research (2026-09-02).** The agent read `README.md` (features, engines,
packages, install paths, troubleshooting), `rule.md` (operating manual:
branching, merge gate, invariants, docs policy), `prompt.md` (handoff format
to mirror), the `docs/` structure (living docs + `chat-phaseN/` session
records), and the public structure of termux.dev (landing page with install
CTAs + feature callouts; docs-style subpages).

**3. Plan written (2026-09-02).** `web_docs/WEBSITE_PLAN.md` created: 7-page
structure (Home, Install, Start, Engines, Packages, FAQ, About), per-page
content outlines with repo sources, design direction (mobile-first, dark,
terminal accents), locked static stack (HTML/CSS, GitHub Pages, no
framework/build/backend), `website/` folder layout, deployment approach,
implementation phases W1–W5 with exit conditions, acceptance criteria, and
open owner questions O1–O5.

**4. Living docs + handoff created (2026-09-02).** `web_docs/README.md`
(index & file map), `web_docs/DECISIONS.md` (D1–D9 + open O1–O5),
`web_docs/NEXT_STEPS.md` (head state + owner command table),
`web_docs/WEB_JOURNEY.md` (this file), `web_docs/chat-web1/SUMMARY.md`
(session record), and root `web_prompt.md` (paste-this-into-the-next-chat
handoff).

**5. W0 closed (2026-09-02).** Planning complete. **No code written** —
owner's strict rule honoured: the only new files are markdown. The website
now waits for the owner's implementation command ("Build the website" /
"start W1").

## W0.1 — Learning wing + self-dependent requirement (2026-09-02)

**6. Owner expands the scope (2026-09-02).** Same session, same strict no-code
rule. Two new owner commands:
1. **"I want it to be learning page also"** — the site must include a
   learning course, with the owner's own Termux-Mastery site
   (`pabitra27706-oss.github.io/Termux-Mastery`) as the structural
   reference: "Master Termux from zero to advanced", 15 numbered chapters,
   read-like-a-book, hands-on projects, course structure table, disclaimer,
   MIT license.
2. **"Make it fully proper self dependent"** — the site must stand alone:
   no external dependencies of any kind.

**7. Reference study (2026-09-02).** The agent fetched and studied the
public structure of Termux-Mastery: course home (about / why learn / how to
use / 15-chapter table / getting started / contributions / roadmap /
disclaimer / license) + numbered chapter folders. Since it is the owner's
own project, mirroring its structure is clean-room-safe; its content will
not be reused (the chapters are written fresh for CodeC from CodeC's own
docs).

**8. Plan v2 written (2026-09-02).** `WEBSITE_PLAN.md` rewritten to v2:
- **Two wings:** product (7 pages, unchanged in intent) + learning wing —
  `/learn` course home + **17 proposed chapters** (`ch-01` Getting Started
  → `ch-17` Troubleshooting), each on a fixed self-contained template
  (goals → steps → try-it → common mistakes → prev/next). CodeC-specific
  twists: chapter 04 (4 compiler engines), chapter 13 (CodeCApi = the
  CodeC answer to Termux API), chapter 14 (web projects + live preview).
  Chapters teach only what CodeC ships today; final set locked at W4.
- **Self-dependent = law (plan §5):** zero fetched external resources (no
  CDN/fonts/JS/images from outside, system font stack), outbound links
  fine, offline-complete rendering, course completable without the repo;
  W6 verifies via grep sweep + offline render + link sweep.
- **Phases restructured W1→W6** (D12): product wing first (W1–W3), course
  second (W4–W6 content), deploy + verification last (W6).
- Decision log: **D10** (learning wing), **D11** (self-dependent), **D12**
  (phases); open items **O6** (chapter set) and **O7** (course license)
  added.
- `web_prompt.md` updated (facts + phase list), `NEXT_STEPS.md` head state
  v2 + new phase queue.

**9. W0.1 closed (2026-09-02).** Planning v2 complete. Still **zero code** —
the strict rule stands. Waiting for the owner's implementation command.

## W0.2 — All phases fully spec'd (2026-09-02)

**10. Owner asks for the phases (2026-09-02).** Same session, same strict
no-code rule: *"Can you create phases"* — the website's implementation
phases should exist as fully spec'd docs, the way the app project's
Phases 20–24 exist as `docs/chat-phase20/` … `docs/chat-phase24/` before a
single line of code.

**11. Reference format studied (2026-09-02).** The agent read
`docs/chat-phase24/` (README: status/cost/depends/blocks, parts table,
ground rules; PART docs: design, implementation steps, numbered exit
conditions) and `docs/chat-phase20/README.md` (cost tags like
`[client-only]`/`[repo-build]`, owner blockquote, "hard constraint" callouts)
and mirrored the format for the website.

**12. Six phase folders created (2026-09-02).** `web_docs/web-phase1/` …
`web_docs/web-phase6/` — **35 docs** (6 phase READMEs + 29 PART docs), each
part with design, implementation steps and a numbered exit condition:
- **W1** (scaffold + Home): the shared-chrome pattern is documented once
  and copied byte-identical by all later phases; first self-dependent
  sweep runs in W1.1.
- **W2** (install + start), **W3** (engines, packages, FAQ, about): product
  wing complete; W3.2 pulls the **authoritative package list from the
  `codec-packages/` build config** (sha recorded) instead of trusting
  memory.
- **W4** (verification gate → course home → ch 01–06): **W4.2 runs first**
  in the phase — it re-verifies the README + package config into a
  committed Verified Facts Table, then **locks the 17-chapter set (closes
  O6)**; the canonical chapter template (crumb / goal box / steps /
  try-it / mistakes / prev-next) is defined in the phase README and every
  chapter part builds on it.
- **W5** (ch 07–12): ch-08 C Basics carries the **TCC-safe law** (ANSI C
  only, no C11 constructs) + a snippet-review checklist + a **device pass**
  (owner transcript); ch-12 has variant A/B decided by W4.2's tool list.
- **W6** (ch 13–17 + polish + deploy): ch-15 may mention planned app phases
  only as one "coming" line; W6.7 **verifies before live** (self-dependent
  sweep + offline render + full link sweep, all recorded) and is the only
  part allowed to touch outside `website/` (the Pages workflow + the
  one-line README link, D7).

**13. Living docs updated (2026-09-02).** Plan → v2.1 (phase table now
links the spec folders), `DECISIONS.md` D13, `NEXT_STEPS.md` head state +
phase queue + new "Start W2…W6" command row, `web_docs/README.md` file map
(naming split: `chat-webN` = session records, `web-phaseN` = planned
phases), `web_prompt.md` (state + commands).

**14. W0.2 closed (2026-09-02).** The website is now **fully spec'd end to
end**: 25 pages, 6 phases, 35 spec docs, zero code — the strict rule held
through every step. The owner's only remaining move before anything is
built: **"Start W1"** (or "Build the website").

**15. PR #41 opened (2026-09-02).** The owner commanded: "Please open a
pull request for the changes on this branch." Agent opened
[PR #41](https://github.com/pabi277/CodeC/pull/41)
(`arena/01a06270-codec` → `main`, tip `626719a` at open) with the full W0
summary. **Not merged** — the merge gate applies; the owner merges (or
hands the command).

**16. PR #41 merged (2026-09-02).** The owner merged W0 planning to `main`
(squash → `9b3669e`); `Build APK` green on the merge run. `main` and the
planning branch tip became identical — the handoff's "if it's already
merged, continue" applies.

---

*Next entry: W2 (Install + Getting Started) — on the owner's "Start W2" command.*

## W1 — Scaffold + Home (2026-09-02)

**17. "Build the website" — W1 begins (2026-09-02).** New session, new
branch `arena/01a062f7-codec` (verified first: clean at `9b3669e` == remote
`main`, PR #41 merged, CI green). The owner's message was exactly the
implementation trigger from the handoff: "Build the website" = "Start W1".
The strict no-code era ended by the owner's own command; W0's documents
became the working spec.

**18. W1.1 — the scaffold (2026-09-02).** `website/` created with exactly
the PART_1_1 files: `style.css` (the only stylesheet — tokens: near-black
`#0B0F14`, surface `#11161D`, text `#E6EDF3`, muted `#8B949E`, accent green
`#3FB950` + amber `#D29922`, system font stacks; every component from the
spec defined: header/footer chrome, containers, cards, buttons, code
blocks, tables, chapter boxes, learning banner, FAQ items), `favicon.svg`
(hand-drawn `>_` prompt mark), and the chrome half of `index.html`. The
mobile nav collapses with **zero JavaScript** (checkbox pattern). The
byte-identical chrome pattern is documented in `chat-web2/SUMMARY.md` for
W2–W6 to copy.

**19. W1.2 — the Home page (2026-09-02).** Hero ("CodeC — a C programming
IDE for your Android phone."), the single honest CTA ("Get the APK on
GitHub" → Releases — D9; secondary "Read the README"), six feature cards
(built-in compiler, real terminal, package hub, Spck-grade editor, web
preview, always updatable — each claim's README source line recorded in
`chat-web2/`), the learning banner ("Master CodeC from Zero to Advanced" →
`learn.html`), and the footnote strip.

**20. Verification (2026-09-02).** First self-dependent grep sweep (plan
§5.5): zero external `src=` / `<link href=` / `@import` / `url(`; every
`http(s)` on the page lives in `<a href>` (one declared exception: the SVG
`xmlns` namespace identifier in the favicon — never fetched). Outbound
links curl-verified 200 (releases, repo, issues, JOURNEY). HTML parse
check clean. Served locally: `/`, `style.css`, `favicon.svg` all 200; the
not-yet-built pages 404 exactly as the spec expects until W2–W4 build
them. Visual check at 360/1440 px: no browser in the sandbox — exposed to
the owner as a live preview; CSS is mobile-first per spec breakpoints.

**21. W1 closed (2026-09-02).** Living docs updated in the same commit
(this file, `NEXT_STEPS.md`, `web_prompt.md`, `web_docs/README.md`).
Nothing outside `website/` + `web_docs/` + `web_prompt.md` was touched.
W1 note recorded in `chat-web2/`: internal links are relative `*.html`
(the only mechanism that resolves to the final URLs under the GitHub
Pages subpath). Stopped at the merge gate — the owner merges, or commands
the next phase ("Start W2").

---

## Early deploy — owner command (2026-09-02)

**22. "Deploy the website to Pages now" (2026-09-02).** Same session as
W1. The owner asked for a direct link; the honest answer was: the real
link (`https://pabi277.github.io/CodeC/`) needs a deploy, and the repo's
ONE Pages site is already the signed package repository (`/dev`, `/keys`
— `package-repository.yml` `publish-dev`). The owner then commanded the
deploy explicitly. Recorded as **D14**. Engineering before go-live:

- Research note appended to `web-phase6/README.md`: naive website deploys
  would wipe the package repo (and vice versa) — one Pages site per repo,
  every deploy replaces everything.
- **`.github/workflows/pages.yml`** created: the website deploy downloads
  the newest live `github-pages` artifact (the package repo), unions
  `dev/` + `keys/` under `website/`, sanity-checks
  `dev/CODEC-REPOSITORY` + `site/index.html`, then deploys. Fails loudly
  instead of publishing a site without `/dev`. Shared `pages` concurrency
  group with the package workflow.
- **`package-repository.yml` `publish-dev`** (app-side, covered by the
  owner's explicit command) mirrored: sparse-checkouts `website/` from
  `main` into the upload; same concurrency group — a package publish now
  re-publishes the site too.
- Verified locally: YAML parse (both files) + full simulation of the
  union steps against a fake artifact (SIMULATION-PASS). One YAML bug
  (colon in a plain step name) caught and fixed pre-push.
- **W6.7 is unchanged** — the full-site deploy still happens only after
  the §5 self-dependent sweep + offline render + link sweep.

The site goes live at `https://pabi277.github.io/CodeC/` (W1 content) on
merge / deploy run, `dev/` untouched beside it.

---
