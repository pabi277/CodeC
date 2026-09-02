# WEBSITE_PLAN.md — master spec for the CodeC website

> **Status (2026-09-02): PLANNED — nothing built yet.** Implementation starts
> only when the owner commands it in chat ("Build the website" / "start W1"),
> and proceeds phase by phase (W1 → W5) in this file. Stack, pages and
> deployment below are **decided** (see `DECISIONS.md`) — changing them needs
> the owner's explicit command.

---

## 1. What we are building

A public website for **CodeC** — an Android C programming IDE with a built-in
compiler, an in-app terminal, and a signed package repository. The site does
one job: take a visitor from "never heard of CodeC" to **APK downloaded and
first `cc hello.c` compiled**, with enough depth (FAQ, package list, engine
explanation) that support questions stop landing on GitHub issues.

### Reference model: the Termux site (termux.dev)

We mirror Termux's *public structure*, not its code:

- **Landing page first** — what it is in one sentence, install buttons
  immediately, then feature callouts (Termux: "Secure", "Feature packed",
  "Customizable", "Tinkerable"…).
- **Docs-style subpages** — install guide, getting started, FAQ — simple,
  linkable, stable URLs.
- **Plain, fast, dark-friendly** — content-first, no marketing noise, no
  tracking.

**What does NOT transfer from Termux:**

- **Install buttons.** Termux offers F-Droid + GitHub. CodeC is distributed
  **from GitHub only** (Actions artifact on green `Build APK`, GitHub
  Releases, in-app updater). One honest CTA: *Get the APK on GitHub* (+
  link to the Releases page). Never imply a store listing.
- **Package ecosystem scale.** CodeC ships 25+ packages, not hundreds — the
  Packages page lists them for real, no inflation.
- **No sponsors/funders section** (not relevant yet).

## 2. Goals & non-goals

**Goals**

1. Accurate at all times — every claim traceable to a repo file
   (`README.md` first, then `docs/TROUBLESHOOTING.md`, `docs/JOURNEY.md`).
2. Dead simple to build, host and maintain: static files, no build step, no
   backend — the owner can fix a typo in a browser on GitHub and it goes live.
3. Mobile-first — the audience is Android users, most will arrive on a phone.
4. Deep enough to deflect support: real troubleshooting answers copied in
   spirit from `docs/TROUBLESHOOTING.md`, with links back to the repo.
5. Cheap & boring to host: GitHub Pages on the same repo.

**Non-goals**

- No server-side rendering, no SPA framework, no CSS framework build, no CMS,
  no search index, no analytics, no i18n, no blog engine, no downloads
  mirror (the APK always downloads from GitHub).
- No marketing fluff: no "revolutionary" copy, no fake feature claims, no
  benchmarks that aren't in the repo docs.

## 3. Site structure & content (7 pages)

Single-page-per-topic, one shared header/footer. Proposed URLs
(`https://pabi277.github.io/CodeC/…` once deployed — final URL host decided
in W5, see §8):

### 3.1 `/` — Home

- **Hero:** "CodeC — a C programming IDE for your Android phone." One
  paragraph: built-in offline compiler, real terminal, package hub, web
  preview. Source: `README.md` intro.
- **Primary CTA:** *Get the APK on GitHub* → Releases page; secondary:
  *Read the README* → repo root.
- **Feature callouts** (6 cards, mirroring Termux's grid pattern; each
  ~2–3 sentences + link to the relevant subpage):
  1. *Built-in compiler* — TCC embedded in the APK; offline, instant, no
     Termux, no setup; arm64 + x86_64. (→ `/engines`)
  2. *Real terminal* — VT/ANSI "Mini-Termux" with PTY; `cc file.c`,
     `./a.out`. (→ `/start`)
  3. *Package hub* — 25+ signed packages, 1-tap install, live status
     badges. (→ `/packages`)
  4. *Spck-grade editor* — projects, tabs, git pane, honest git. (→ `/about`)
  5. *Web preview* — HTML/CSS/JS projects preview in-app with live reload.
     (→ `/start`)
  6. *Always updatable* — in-app "Install APK from GitHub" + self-update.
     (→ `/install`)
- **Footnote strip:** "Free & open source · Built-in compiler · No Termux
  required" + GitHub link.

### 3.2 `/install` — Install guide

- Where the APK comes from (three paths, in the same order as README):
  1. GitHub Actions artifact on the latest green `Build APK` run.
  2. A GitHub Release.
  3. In-app: Settings → Install APK from GitHub (after first install).
- "Allow install from unknown sources" note.
- Device support: arm64-v8a phones (best), x86_64 emulators (TCC covers
  them), what 32-bit gets (Termux engine fallback).
- Optional: setting up the **Termux engine** (when & how, the
  `allow-external-apps` line, the in-app **CHECK BRIDGE** button) — quoted in
  spirit from README §Run C.
- Link to the in-app updater and the Releases page.

### 3.3 `/start` — Getting started (first hour)

- The shortest possible first compile: open editor → write `hello.c` → tap
  **RUN** → see output. Then the terminal loop:
  `cc hello.c -o a.out` → `./a.out` (explain the `./` requirement).
- Where input programs (`scanf`) must run — **Term** tab, not RUN.
- The Packages tab: 1-tap install & run, quick system actions, custom
  commands.
- Web preview: open an HTML file, tap RUN, edit, see live reload.
- Small "where things live" map: bottom bar tabs (Projects · Editor ·
  Terminal · Packages · Settings).

### 3.4 `/engines` — Compiler engines

- The 4-engine table straight from README (Auto / Built-in TCC / Bundled
  Clang / Termux): what each does, when to pick it.
- Why built-in TCC first (offline, instant, W^X-safe), what TCC covers
  (ANSI C + most C99) and when to switch to Clang (full C11/C17).
- arm64 note for the bundled Clang module; x86 emulator note.
- "CHECK BRIDGE" button explained.

### 3.5 `/packages` — Package hub & repository

- What `pkg`/Packages tab is (guarded CodeC-only frontend).
- The real package list (25+; from README: `git`, `python`, `clang`, `nano`,
  `make`, `ripgrep`, `tmux`, …) — presented as a table: name / what it gives
  you / install state.
- How the repository works: `https://pabi277.github.io/CodeC/dev`, signed
  metadata, SHA-256-verified bootstrap `userland-v2-dev`, atomic installs.
- Honest scope note: "a small, verified repository — not the full Termux
  package universe."

### 3.6 `/faq` — FAQ & troubleshooting

Content distilled from `README.md` §Troubleshooting + `docs/TROUBLESHOOTING.md`
(same questions, website-length answers, each linking to the repo doc for
depth):

1. "The built-in compiler could not start"
2. "Permission denied" when compiling (Android 10+ W^X / noexec)
3. "Exec format error" when compiling (CPU mismatch)
4. "Runtime libraries missing" when compiling
5. Install or compile hangs (30 s / 10 s caps)
6. Do I need Termux? (No — Auto engine; Termux is an option)
7. Can I use my own compiler/keyboard? (extra-keys row, hardware keyboard)
8. Where do projects live? / Can I export? (SAF export, ZIP share)
9. Where do I report bugs? (GitHub Issues — with the "include the Device line
   from Logs" hint)

### 3.7 `/about` — About CodeC

- What it is / who it's for (one section, no ego).
- The feature tour in short form (editor, projects, honest git, web preview,
  device APIs: battery/sensor/TTS/camera/intent via CodeCApi).
- The story: phases 0–19+ built in public, linked to `docs/JOURNEY.md`.
- Engineering facts: static musl TCC toolchain in the APK, signed package
  repo, CI-built APK, clean-room approach (Feature parity, never copied
  code).
- Links: repo, README, JOURNEY, Releases, Issues.

### Shared chrome

- **Header:** wordmark "CodeC", nav: Home · Install · Start · Engines ·
  Packages · FAQ · About · GitHub icon. Sticky, collapses to a single toggle
  on narrow screens.
- **Footer:** "CodeC — free & open source C IDE for Android" + repo/README/
  Releases/Issues links + "Site source: this repo, `website/`".
- Every page: consistent meta title/description for search; one canonical
  link to the Releases page (never hardcoded artifact URLs, which rot).

## 4. Content rules

1. **Source of truth order:** `README.md` → `docs/TROUBLESHOOTING.md` →
   `docs/JOURNEY.md` → `docs/chat-phaseN/` (for detail). If a fact isn't in
   the repo, it isn't on the site.
2. **Distill, don't dump** — website-length answers; link out for depth.
3. **No rotting links:** link to stable places (releases page, repo root,
   README anchors), never to ephemeral artifact URLs or run IDs.
4. **Version-awareness:** state "as of" facts (package count, engine list)
   where the README could change; the W5 check re-reads README before deploy.
5. **Honest scope:** CodeC is a C IDE with a growing multi-language path
   (planned Phases 20–24); say what exists today, label the roadmap as
   roadmap.

## 5. Design direction

- **Mobile-first, dark by default** — the audience is on Android; the app
  itself wears a dark Spck-grade skin, the site should feel like it belongs
  to the app.
- **Terminal accents:** a monospace font for code/commands and small labels;
  a single accent color family (green/amber, terminal-style) on a near-black
  background; generous contrast (WCAG AA).
- **Content-first layout:** max-width text column, card grid for feature
  callouts (like Termux's landing), tables for engines/packages, simple
  accordion or heading+anchor FAQ.
- **Fast & light:** no image carousel, no autoplay video, no webfonts that
  block render (system font stack or one preloaded family); a small static
  screenshot or two of the app is allowed later (W5, only if the owner
  supplies/approves assets — no screenshots fabricated).
- **No JavaScript by default.** Vanilla JS only for things static CSS can't
  do (mobile nav toggle, FAQ accordion if not done with `<details>`),
  and only in W5 if truly needed.
- Responsive breakpoints: phone (default) → tablet → desktop. Desktop is an
  enhancement, not the target.

## 6. Stack (locked — D3)

- **Plain static site:** one folder of `.html` files + one `.css` file
  (or a small number) + favicon. No framework, no build step, no
  dependencies, no lockfile, no node_modules.
- **Host:** GitHub Pages, same repo (deployment details §8).
- Rationale: the owner can read, review and fix every byte in a browser on
  GitHub; "tests" are trivially "does Pages deploy and do links resolve";
  zero supply-chain surface.
- Alternatives considered & rejected (D3): Docusaurus/MkDocs/Hugo — correct
  tools for large doc sets, wrong weight for 7 pages owned by one person.

## 7. Repo layout (when implementation starts)

```
website/            ← the ENTIRE website lives here (new top-level folder)
  index.html        ← Home
  install.html      ← /install
  start.html        ← /start
  engines.html      ← /engines
  packages.html     ← /packages
  faq.html          ← /faq
  about.html        ← /about
  style.css         ← one shared stylesheet
  (favicon assets)  ← minimal, static
```

- Nothing in `website/` is served by the app; nothing in `app/`,
  `codec-packages/`, `docs/`, `gradle*` changes as part of website work.
- The one exception at W5: a GitHub Pages workflow (or Pages source config)
  that publishes `website/` — added under `.github/workflows/`, named
  clearly (e.g. `pages.yml`), touching nothing else.

## 8. Deployment (W5)

- **GitHub Pages**, same repo, serving the `website/` folder.
- Decision point in W5 (record in DECISIONS.md then): Pages source =
  *main branch / `website` folder* (simplest, what we propose) vs a
  `gh-pages` branch (only if the owner prefers).
- Site URL: `https://pabi277.github.io/CodeC/…` (project Pages URL with a
  folder). After deploy, the repo README gets a one-line link to the site —
  that single README edit is part of W5's commit (the one allowed
  cross-workstream touch, and it's a link, not a content rewrite).
- The site's own "Site source" footer link points at `website/` in the repo.
- Deploy evidence for the record: green Pages build + every page URL opened
  once (W5 exit condition).

## 9. Implementation phases (owner commands one at a time)

| Phase | Scope | Exit condition |
|---|---|---|
| **W1** | Scaffold `website/`: shared chrome (header/footer), stylesheet, Home page content & feature cards. | Home renders correctly on a phone-width and desktop-width viewport; all footer links valid. |
| **W2** | `/install` + `/start` pages, full content per §3.2/§3.3. | Both pages live in the scaffold; content matches README facts. |
| **W3** | `/engines` + `/packages` pages, full content per §3.4/§3.5. | Tables match README; package list = README list verbatim in scope. |
| **W4** | `/faq` + `/about` pages, full content per §3.6/§3.7. | Every FAQ answer traceable to a repo doc section (links included). |
| **W5** | Polish pass (consistency, contrast, meta tags), deploy via GitHub Pages, README link, full link sweep. | Pages build green; every page URL + every external link verified once; report with URL. |

Each phase: one `web_docs/chat-webN/` record + living docs update + commit +
push + report + stop at the merge gate. The owner may also order phases out
of sequence or re-scope a page — that goes in `DECISIONS.md` first.

## 10. Acceptance criteria (whole site)

1. Seven pages, shared chrome, no broken internal/external links (swept in
   W5, sweep recorded in `chat-web5`).
2. Every factual claim traceable to a repo file (traceability notes live in
   `chat-webN/` records).
3. Renders acceptably at 360 px width and 1440 px width (visual check).
4. Deploys on GitHub Pages; site URL recorded in `web_docs/NEXT_STEPS.md`
   head state.
5. No app code, tests, or CI (the APK workflow) changed.
6. `web_prompt.md` + `web_docs/` living docs updated in the final commit.

## 11. Open questions (owner decides; do not guess)

1. **Screenshots:** does the owner want app screenshots on Home? (If yes,
   the owner supplies/approves the images — the agent does not fabricate
   marketing imagery.)
2. **Domain:** keep the GitHub Pages URL or point a custom domain at it?
   (Default: keep Pages URL.)
3. **Copy tone:** technical-direct (proposed) vs friendly-casual?
4. **Phase order:** W1→W5 as listed, or any re-prioritization (e.g. FAQ
   first)?
5. **Where the in-app link points:** should the app itself (Settings or
   README of the app UI) ever link to the site? (App change — separate
   command, separate workstream, only if the owner asks.)
