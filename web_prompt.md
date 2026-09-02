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

**WHERE THE WEBSITE STANDS (2026-09-02, W1 COMPLETE):**

- **Planning (W0 + W0.1 + W0.2) is complete AND merged (PR #41 → `main`,
  `9b3669e`). W1 (scaffold + Home) is COMPLETE — the site now exists.**
- **What exists in `website/` (created 2026-09-02 by the W1 session):**
  - `style.css` — the one stylesheet: design tokens (near-black `#0B0F14`,
    surface `#11161D`, text `#E6EDF3`, muted `#8B949E`, accent green
    `#3FB950` + amber `#D29922`, system font stacks) + every component
    (chrome, containers 760/1040, cards, buttons, code blocks, tables,
    chapter-crumb/prev-next, goal/try-it/mistake boxes, learning banner,
    FAQ items). No JavaScript anywhere on the site; mobile nav = checkbox
    pattern.
  - `favicon.svg` — local hand-drawn `>_` prompt mark.
  - `index.html` — shared chrome + the Home page (hero → CTA row
    ["Get the APK on GitHub" → Releases, "Read the README"] → 6 feature
    cards → learning banner → footnote strip).
- **W2–W6 must copy the chrome byte-identical** — the documented copy-paste
  pattern + the full style reference + every Home card's README source
  line live in `web_docs/chat-web2/SUMMARY.md`. Internal links are
  **relative `*.html`** (works under the GitHub Pages subpath; root-
  relative `/install` would break — W1 note in `chat-web2/`).
- Still to build (they 404 until their phase): install, start, engines,
  packages, faq, about, learn + ch-01…ch-17 (19 pages). Nothing is
  deployed; no Pages workflow yet (that is W6).
- **PR #41 (W0 planning docs) was MERGED 2026-09-02** (squash `9b3669e`).
  The W1 session ran on a new branch; its own merge awaits the owner at
  the gate.

**WHAT THE OWNER MUST SAY TO PROCEED:**

Implementation runs one phase at a time, each started only by the owner's
command in chat — **"Start W2"** … **"Start W6"** ("Start W1" / "Build the
website" was the first trigger; W1 is done). Each phase is executed
**strictly per its spec folder** `web_docs/web-phaseN/` (phase README +
PART docs, one phase at a time, in order W2 → W6), building the 25-page
site inside the existing `website/` chrome: W2 Install + Getting Started →
W3 Engines + Packages + FAQ + About → W4 **verification gate first**, then
course home + chapters 01–06 → W5 chapters 07–12 (ch-08 device pass) → W6
chapters 13–17 + polish + GitHub Pages deploy + verification (P1+P5 device
pass). Only `website/` + the web living docs are written; every page
copies the W1 chrome byte-identical (pattern in `web_docs/chat-web2/`).

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
2. If the owner has **not** commanded a phase: answer questions, refine the
   plan/specs in `web_docs/` only, update the living web docs, commit +
   push, report, stop at the merge gate. (Website code exists from W1 on —
   but never touch it outside a commanded phase.)
3. If the owner **has** commanded a phase: work that phase (W2–W6) strictly
   per its spec folder `web_docs/web-phaseN/`, one phase at a time, record
   the phase in `web_docs/chat-webN+1/`, update `web_prompt.md` /
   `web_docs/NEXT_STEPS.md` / `web_docs/WEB_JOURNEY.md` in the same commit,
   push, report (including any **device pass required** items: W5 ch-08,
   W6 P1+P5), stop at the merge gate.
4. Keep this file and the `web_docs/` living docs updated as gates close.

**Before each change, state:** what you are changing, which existing feature
it serves, which invariant (if any) it could affect — and for website work,
which part of the repo's own docs the change relies on.
