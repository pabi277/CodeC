# web_prompt.md — paste this into the next chat (WEBSITE project)

> Copy **everything between the two `---` lines below** as the first message of
> a new chat. It forces the next agent to verify before acting, to trust the
> repo over its own assumptions, and to continue the **CodeC website** without
> redoing or breaking anything. The app project's handoff file is
> `prompt.md` — this one is for the website workstream only.

---

Read `web_docs/README.md`, `web_docs/WEBSITE_PLAN.md`, `web_docs/NEXT_STEPS.md`,
and **`rule.md`** first, before doing anything else, then report what you found
and the current git/PR/CI state before making any change.

You are continuing the **CodeC website** — a public website for the CodeC
Android C IDE with **two wings** (plan v2, 2026-09-02):
1. **Product wing** — modelled on the Termux site (`termux.dev`): landing
   page with install CTA, feature callouts, install guide, getting-started
   guide, compiler engines, packages, FAQ/troubleshooting, About.
2. **Learning wing** — modelled on the owner's own **Termux-Mastery** site
   (`pabitra27706-oss.github.io/Termux-Mastery`): a book-like course
   **"Master CodeC from Zero to Advanced"** — `/learn` course home + 17
   numbered chapters (`ch-01` … `ch-17`) with hands-on exercises.
The whole site must be **fully self-dependent** (see law below). Each chat
session gets its own `arena/*` session branch — verify with `git status`;
commit and push to the SESSION branch only, never `main` or any other
branch. `rule.md` is the operating manual (branching, merge gate,
invariants, docs policy) — follow it for the website work too.

**WHERE THE WEBSITE STANDS (2026-09-02, plan v2.1):**

- **WEBSITE PHASES W0 + W0.1 + W0.2 (planning) are COMPLETE. NO WEBSITE
  CODE EXISTS YET, BUT THE SITE IS FULLY SPEC'D END TO END.** The owner's
  strict rule for the planning session (2026-09-02) was: **no code of any
  kind** — this workstream started with documentation only, exactly like
  `docs/` + `prompt.md` started the app project. Plan v2 added the
  learning wing (owner command); v2.1 added **fully spec'd implementation
  phases** (owner command "Can you create phases") — see
  `web_docs/DECISIONS.md` D10–D13.
- **The phase specs (the website's `docs/chat-phase20…24` equivalent):**
  `web_docs/web-phase1/` … `web_docs/web-phase6/` — 35 docs (6 phase
  READMEs + 29 PART docs), each part with design, implementation steps and
  a numbered exit condition. Phase laws baked into the specs: **W4.2 is a
  verification gate that runs FIRST inside W4** (re-verifies README +
  `codec-packages/` build config into a committed Verified Facts Table,
  locks the 17-chapter set, closes O6); **ch-08 (W5) and P1+P5 (W6) carry
  device passes** (owner transcripts — same convention as app phases);
  **W6.7 verifies before the site goes live** (self-dependent sweep +
  offline render + full link sweep, recorded in `chat-web6/`).
- What exists now:
  - `web_docs/` — the website's history & planning folder (the website's
    equivalent of `docs/`): `README.md` (index), `WEBSITE_PLAN.md` (the
    master spec v2.1 — two wings (product + 17-chapter learning course),
    per-page content, design, stack, deployment, phases W1–W6),
    `web-phase1/` … `web-phase6/` (the fully spec'd phases — 35 docs),
    `DECISIONS.md` (decision log), `NEXT_STEPS.md` (head state),
    `WEB_JOURNEY.md` (narrative), `chat-web1/SUMMARY.md` (session record).
  - `web_prompt.md` — this file.
- **Nothing is built, nothing is deployed, there is no `website/` folder
  yet.** If you see website HTML/CSS/JS anywhere that this session did not
  create, verify where it came from before touching it.
- **PR #41 (2026-09-02) is MERGED** — the W0 planning docs landed on
  `main` as squash commit `9b3669e` (owner command "Please merge the pull
  request"). Verify with `git ls-remote origin main`. The website is fully
  spec'd on `main` and waiting for the owner's **"Start W1"** command.

**WHAT THE OWNER MUST SAY TO PROCEED:**

Implementation starts only when the owner commands a phase in chat —
**"Start W1"** … **"Start W6"** (or "Build the website" = Start W1). Each
phase is executed **strictly per its spec folder** `web_docs/web-phaseN/`
(phase README + PART docs, one phase at a time, in order W1 → W6), writing
the site (25 pages) into a new top-level `website/` folder: W1 scaffold +
Home → W2 Install + Getting Started → W3 Engines + Packages + FAQ + About →
W4 **verification gate first**, then course home + chapters 01–06 → W5
chapters 07–12 (ch-08 device pass) → W6 chapters 13–17 + polish + GitHub
Pages deploy + verification (P1+P5 device pass). Only from W1 onward is
writing HTML/CSS/JS allowed. Until a phase is commanded: planning edits
only, still no code.

**LAW (inherits the app project, no exceptions):**

- **No PR/merge without the owner's literal command in chat** (`rule.md`
  §3). Committing to and pushing the session branch is fine.
- **The website never touches app code.** `app/`, `codec-packages/`,
  `gradle*`, `scripts/`, `.github/workflows` (except adding the website's own
  Pages workflow when W5 starts) and all `docs/` content are out of bounds
  for website work. `docs/` is the app's history — never rewrite it.
- **`web_docs/` is the website's history.** Append, don't destructively
  rewrite. Update `web_prompt.md` (this file), `web_docs/NEXT_STEPS.md` (head
  state line) and `web_docs/WEB_JOURNEY.md` (timeline) as web gates close —
  the next chat trusts only what is written there and verified in git.
- **Clean-room:** website *content* is distilled from the public repo files
  (`README.md`, `docs/TROUBLESHOOTING.md`, `docs/JOURNEY.md`) and the Termux
  site's *public structure* (page model, layout ideas) — never paste
  Termux's site source (it is GPL-ish licensed; read the public spec,
  re-implement). No decompilation, no copying of closed-source material.
- **CI is the only test executor** for app changes; the website is a static
  site — its "tests" are: builds/deploys green on GitHub Pages and every link
  on the site resolves (verify during W5).

**FACTS THAT MUST NOT REGRESS (website):**

- **Stack is locked:** plain static HTML + CSS (+ minimal vanilla JS only if
  a page genuinely needs it), **no framework, no build step, no backend, no
  CMS, no database**. Served by **GitHub Pages** from the `website/` folder
  in this same repo (see `web_docs/DECISIONS.md` D3/D7). Reopening this
  requires the owner's explicit command.
- **SELF-DEPENDENT = LAW (owner command 2026-09-02, D11):** zero fetched
  external resources — **no CDN, no external fonts** (system font stack),
  **no external JS/CSS/images, no analytics, no third-party embeds**. Every
  file the browser loads must live in this repo's `website/`. Outbound
  hyperlinks are fine (github.com repo/README/Releases/Issues, the package
  repo URL, F-Droid/GitHub only where the README points to Termux). Every
  page must render **fully offline**. The learning course must be
  **completable without ever opening the repo** (repo links are optional
  "go deeper" footnotes). Verified in W6 per plan §5: grep sweep (no
  external `src=`/`<link href=`/`@import`/`url(`), offline render check,
  full link sweep — recorded in `web_docs/chat-web6/`.
- **Learning wing (D10):** "Master CodeC from Zero to Advanced" — `/learn`
  course home + 17 chapters `ch-01…ch-17`, fixed chapter template (goals →
  steps → try-it → common mistakes → prev/next). Chapters teach **only what
  CodeC ships today** (final set locked at W4 against the verified package
  list, plan §3.2); CodeC twists: ch-04 the 4 compiler engines, ch-13
  CodeCApi (CodeC's Termux-API answer: battery/sensor/TTS/camera/intent),
  ch-14 web projects + live preview. Structure mirrors the owner's own
  Termux-Mastery (same owner — clean-room safe; its content is NOT reused).
  Total site at completion: **25 pages** (7 product + course home + 17
  chapters).
- **Content source of truth is the repo itself** — `README.md` first, then
  `docs/TROUBLESHOOTING.md`, `docs/JOURNEY.md`. The site never states
  anything the repo files don't support (feature claims, package list,
  engine table, install steps). When the README changes, the site's affected
  section changes in the same effort — drift is a bug.
- **Install facts (as of 2026-09-02):** CodeC is distributed **from GitHub
  only** — Actions artifact `CodeC-IDE` on green `Build APK` runs, GitHub
  Releases, and the in-app **Settings → Install APK from GitHub**. There is
  **no Play Store / F-Droid listing** — the site must never imply one
  (Termux's dual F-Droid/GitHub buttons do NOT transfer; CodeC gets a
  single "Get the APK on GitHub" style call to action).
- **Repo facts the site will state:** built-in TCC compiler (offline,
  instant, no Termux needed; arm64-v8a + x86_64), 4 compiler engines
  (Auto / Built-in TCC / Bundled Clang / Termux), in-app VT/ANSI terminal
  ("Mini-Termux"), 25+ signed packages (`git`, `python`, `clang`, `nano`,
  `make`, `ripgrep`, `tmux`, …) from the signed repository
  `https://pabi277.github.io/CodeC/dev`, HTML web preview over loopback,
  Spck-style editor + Projects hub + honest git, device APIs (battery,
  sensor, TTS, camera, intents).

**ORDER OF WORK:**

1. Verify state (`git status`, `gh pr list`, `gh run list`) before acting.
2. If the owner has **not** commanded implementation: stay in W0 — answer
   questions, refine the plan in `web_docs/` only (no code), update the
   living web docs, commit + push, report, stop at the merge gate.
3. If the owner **has** commanded implementation: work the current phase
   (W1–W6) strictly per its spec folder `web_docs/web-phaseN/`, one phase
   at a time, record the phase in `web_docs/chat-webN/`, update
   `web_prompt.md` / `web_docs/NEXT_STEPS.md` / `web_docs/WEB_JOURNEY.md`
   in the same commit, push, report (including any **device pass required**
   items: W5 ch-08, W6 P1+P5), stop at the merge gate.
4. Keep this file and the `web_docs/` living docs updated as gates close.

**Before each change, state:** what you are changing, which existing feature
it serves, which invariant (if any) it could affect — and for website work,
which part of the repo's own docs the change relies on.
