# WEBSITE_PLAN.md — master spec for the CodeC website

> **Status (2026-09-02, v2): PLANNED — nothing built yet.** v2 adds the
> **learning wing** (owner's own Termux-Mastery as structural reference) and
> the **fully self-dependent** requirement (owner command 2026-09-02).
> Implementation starts only when the owner commands it in chat ("Build the
> website" / "start W1"), phase by phase (W1 → W6). Stack, pages and
> deployment are **decided** (see `DECISIONS.md`) — changing them needs the
> owner's explicit command.

---

## 1. What we are building

A fully self-dependent public website for **CodeC** — an Android C
programming IDE with a built-in compiler, an in-app terminal, and a signed
package repository. The site has **two wings under one roof**:

1. **Product wing** — take a visitor from "never heard of CodeC" to
   **APK downloaded and first `cc hello.c` compiled**: landing, install
   guide, getting started, engines, packages, FAQ, about.
2. **Learning wing** — a complete, book-like course:
   **"Master CodeC from Zero to Advanced"** — numbered chapters that take a
   complete beginner (no command line, no C) to real projects, exactly the
   shape of the owner's **Termux-Mastery** site
   (`pabitra27706-oss.github.io/Termux-Mastery`): "read it like a book",
   folder-by-folder chapters, hands-on exercises, a course structure table,
   free & open source.

### Reference models (structure only — clean-room)

- **termux.dev** (product wing): landing with one-sentence pitch, install
  CTAs, feature callout grid; docs-style subpages with stable URLs; plain,
  fast, content-first.
- **Termux-Mastery** (learning wing, **the owner's own project** — structure
  may be mirrored freely): course home (about / why learn / how to use /
  chapter table / roadmap / disclaimer), 15 numbered chapters from
  "Getting Started" to "Troubleshooting", each chapter self-contained with
  hands-on content.

**What does NOT transfer from Termux (product wing):**

- **Install buttons.** Termux offers F-Droid + GitHub. CodeC is distributed
  **from GitHub only** (Actions artifact on green `Build APK`, GitHub
  Releases, in-app updater). One honest CTA: *Get the APK on GitHub*.
  Never imply a store listing.
- **Package universe scale.** CodeC ships 25+ verified packages — the
  packages page and chapter 5 list them for real, no inflation.
- **No sponsors/funders section.**

## 2. Goals & non-goals

**Goals**

1. **Accurate at all times** — every claim traceable to a repo file
   (`README.md` first, then `docs/TROUBLESHOOTING.md`, `docs/JOURNEY.md`).
2. **Fully self-dependent (owner requirement):** the site must stand alone —
   see §5. No external resources of any kind; the learning course must be
   completable without ever leaving the site.
3. Dead simple to build, host and maintain: static files, no build step, no
   backend — the owner can fix a typo in a browser on GitHub and it goes live.
4. Mobile-first — the audience is Android users, most arrive on a phone.
5. Deep enough to deflect support: real troubleshooting (product FAQ +
   chapter 17), every command in a chapter must work on a fresh CodeC
   install.
6. Cheap & boring to host: GitHub Pages on the same repo.

**Non-goals**

- No server-side rendering, no SPA framework, no build step, no CSS framework
  build, no CMS, no search index, no analytics, no i18n, no blog engine, no
  download mirror (the APK always downloads from GitHub).
- No marketing fluff: no fake feature claims, no benchmarks that aren't in
  the repo docs.
- The learning wing teaches **what CodeC actually ships today**; the app's
  planned Phases 20–24 are mentioned only as "coming", never taught.

## 3. Site structure & content

Single-page-per-topic, one shared header/footer. Final URLs
(`https://pabi277.github.io/CodeC/…` once deployed — decided in W6, §8).

### 3.0 Shared chrome

- **Header:** wordmark "CodeC", nav: Home · Install · Start · Engines ·
  Packages · **Learn** · FAQ · About · GitHub icon. "Learn" is a top-level
  nav item (the learning wing is a first-class citizen). Sticky header,
  collapses to a single toggle on narrow screens.
- **Footer:** "CodeC — free & open source C IDE for Android" + repo/README/
  Releases/Issues + "Site source: this repo, `website/`".
- **Learn pages add:** a chapter breadcrumb ("Chapter N of 17") and
  prev-chapter / next-chapter footer links on every chapter page.
- Every page: consistent meta title/description; one canonical link to the
  Releases page (never hardcoded artifact URLs — they rot).

### 3.1 Product wing (7 pages)

**`/` — Home.** Hero: "CodeC — a C programming IDE for your Android phone."
One paragraph: built-in offline compiler, real terminal, package hub, web
preview (source: `README.md` intro). Primary CTA *Get the APK on GitHub* →
Releases; secondary *Read the README*. Feature callouts (6 cards, Termux
landing grid pattern): Built-in compiler · Real terminal ("Mini-Termux") ·
Package hub (25+ signed) · Spck-grade editor · Web preview · Always
updatable (in-app updater). **Learning banner:** "New to the command line or
to C? Start the free course — Master CodeC from Zero to Advanced" → `/learn`.
Footnote strip: "Free & open source · Built-in compiler · No Termux required".

**`/install` — Install guide.** Three APK paths in README order (Actions
artifact on green `Build APK`; GitHub Release; in-app Settings → Install APK
from GitHub). "Allow install from unknown sources". Device support (arm64
best; x86_64 emulators via TCC; 32-bit → Termux engine). Optional Termux
engine setup (`allow-external-apps` line, CHECK BRIDGE). Links: in-app
updater, Releases.

**`/start` — Getting started (first hour).** Shortest first compile
(hello.c → RUN), the terminal loop (`cc hello.c -o a.out` → `./a.out`,
explain the `./` rule), where input programs (`scanf`) run — Term tab, not
RUN. Packages tab 1-tap install & run. Web preview (RUN on HTML, live
reload). "Where things live" map: Projects · Editor · Terminal · Packages ·
Settings. Cross-link: "Ready for the full course? → Chapter 1".

**`/engines` — Compiler engines.** The 4-engine table straight from README
(Auto / Built-in TCC / Bundled Clang / Termux): what each does, when to
pick it. Why TCC first (offline, instant, W^X-safe), TCC coverage (ANSI C +
most C99) vs Clang (full C11/C17). arm64 note for the module; x86 emulator
note. CHECK BRIDGE explained.

**`/packages` — Package hub & repository.** What `pkg`/Packages tab is
(guarded CodeC-only frontend). The real package list (25+; from README:
`git`, `python`, `clang`, `nano`, `make`, `ripgrep`, `tmux`, …) as a table.
How the repo works: `https://pabi277.github.io/CodeC/dev`, signed metadata,
SHA-256-verified bootstrap `userland-v2-dev`, atomic installs. Honest scope
note: "a small, verified repository — not the full Termux package universe."

**`/faq` — FAQ & troubleshooting.** Distilled from README §Troubleshooting +
`docs/TROUBLESHOOTING.md` (website-length answers, each linking to the repo
doc for depth): compiler could not start · "Permission denied" (W^X/noexec) ·
"Exec format error" (CPU mismatch) · "Runtime libraries missing" · hangs
(30 s/10 s caps) · "Do I need Termux?" · hardware keyboards/extra-keys ·
projects & export · where to report bugs (include the Logs "Device:" line).

**`/about` — About CodeC.** What it is / who it's for (one section, no ego).
Feature tour in short form (editor, projects, honest git, web preview,
device APIs via CodeCApi: battery/sensor/TTS/camera/intent). The story:
phases 0–19+ built in public → link `docs/JOURNEY.md`. Engineering facts:
static musl TCC in the APK, signed package repo, CI-built APK, clean-room
approach. Links: repo, README, JOURNEY, Releases, Issues.

### 3.2 Learning wing — "Master CodeC from Zero to Advanced"

**`/learn` — Course home** (mirrors the Termux-Mastery home):

- *About this course* — a structured, text-based guide for complete
  beginners (no command line, no C required); read like a book, chapter by
  chapter; each chapter builds on the previous; hands-on in the CodeC app
  on your phone.
- *Why learn with CodeC?* — compiler + terminal + packages in one app,
  offline, no computer needed.
- *What you need* — an Android phone (arm64 best), the CodeC APK (→ `/install`),
  10–20 minutes per chapter.
- *Course structure* — the chapter table (below): number / topic / what you
  will be able to do.
- *Roadmap* — where the course goes (mirrors Termux-Mastery's roadmap link,
  but inline: "17 chapters, hands-on projects at the end").
- *Disclaimer* — educational and utility purposes only; no hacking content;
  use responsibly (mirrors Termux-Mastery's disclaimer).
- *License* — site content licensed with the owner's chosen license
  (default: same as the repo; owner confirms in O7).

**Chapter pages `/learn/ch-01` … `/learn/ch-17`.** Every chapter follows one
template (self-contained): **Learning goals → What you need (a fresh
CodeC install is enough, or named earlier chapters) → Step-by-step (every
command typed by the learner, in a copyable code block) → Try it yourself
(1–3 exercises) → Common mistakes → Prev / Next.** No chapter requires
leaving the site; repo links are optional "go deeper" footnotes.

Proposed chapter set (mirrors Termux-Mastery's 15-chapter arc, adapted to
CodeC reality; final set confirmed against the verified package list at W4
and by the owner via O6):

| # | Chapter | You will be able to… |
|---|---|---|
| 01 | Getting Started | install CodeC from GitHub, find every tab (Projects · Editor · Terminal · Packages · Settings), take a first look |
| 02 | Your First C Program | write `hello.c`, tap RUN, read output, then the terminal loop `cc hello.c -o a.out` → `./a.out` (and why `./`) |
| 03 | The CodeC Terminal | work in the Term tab: `ls`, `cd`, `pwd`, `mkdir`, `touch`, `cat`, `cp`, `mv`, `rm`, `clear`; one command per line; where programs run; input programs (`scanf`) must run here |
| 04 | Compiler Engines | choose Auto / Built-in TCC / Bundled Clang / Termux; run CHECK BRIDGE; understand arm64 vs x86 limits |
| 05 | Package Manager | 1-tap install & run from the Packages tab; `pkg` commands; update/upgrade/heal; read live status badges |
| 06 | Files & Projects | create/open projects, the `+` sheet (New / Clone / Import ZIP / Open Folder), single files, export, where projects live |
| 07 | The Editor | tabs & dirty state, undo/redo, find & replace (regex), Format, extra-keys row (ESC/TAB/CTRL/ALT), autosave, compiler-error squiggles |
| 08 | C Programming Basics | write real C: types, `printf`/`scanf`, operators, loops, functions, arrays, first pointers — every example typed and RUN in CodeC (standard C teaching content, verified runnable on TCC) |
| 09 | Shell Scripting | write and run bash scripts in CodeC, variables, loops, conditionals, a first automation |
| 10 | Python in CodeC | install `python`/`python3`, run scripts, REPL, a small utility |
| 11 | Git & GitHub | in-app GitHub account, clone a repo, Source Control pane, COMMIT & PUSH, branches, honest git (unpushed badge, conflict flow) |
| 12 | Networking & SSH | network tools **as they exist in the CodeC package repo** (list verified at W4: e.g. `curl`/`wget`/`openssh` if shipped); basic troubleshooting; nothing taught that isn't installable from the repo |
| 13 | Device APIs (CodeCApi) | the CodeC answer to Termux API: `codec-battery`, `codec-sensor`, `codec-tts`, `codec-camera`, `codec-intent` — with the `NEED_PERMISSION:` flow |
| 14 | Web Projects | an HTML/CSS/JS project in CodeC, RUN = preview, live reload on save, console output, `fetch`/modules over the loopback server |
| 15 | Custom Setup & Advanced Tools | themes, custom extra-key macros, per-project config; `make`, `clang`, `ripgrep`, `tmux`, `nano` as the repo ships them |
| 16 | Real World Projects | 3–5 hands-on projects: a CLI calculator in C; a personal web page with live preview; a Python utility; a git-backed project pushed to your GitHub; a small automation script |
| 17 | Troubleshooting | read every common error (mirrors `/faq` in depth), read Logs, the "Device:" line, how to report a useful bug on GitHub |

Chapter content rules:

1. **Only what CodeC ships.** Any chapter that mentions a package, engine or
   API must be cross-checked against `README.md` + the package repo at
   content-writing time (W4); "if it's not in the repo, it's not in the
   chapter".
2. **Every command must work on a fresh install** — commands are written
   for the CodeC terminal environment (`$PREFIX` layout, `./` rule, one
   command per line).
3. **Chapter 8 (C Basics)** is the only teaching content beyond the repo
   docs (standard C); every snippet must be known-runnable on built-in TCC
   (ANSI C + most C99) — no C11-only constructs in the course.
4. Code samples are inline in the page (self-dependent — §5); exercises are
   checkable by the learner (expected output shown).

## 4. Content rules (both wings)

1. **Source of truth order:** `README.md` → `docs/TROUBLESHOOTING.md` →
   `docs/JOURNEY.md` → `docs/chat-phaseN/` (detail). If a product fact isn't
   in the repo, it isn't on the site.
2. **Distill, don't dump** — website-length answers; link out for depth.
3. **No rotting links:** stable places only (Releases page, repo root,
   README anchors, docs files) — never artifact URLs or run IDs.
4. **Version-awareness:** "as of" notes where the README could change
   (package count, engine list); the W6 sweep re-reads the README first.
5. **Honest scope:** C IDE today + planned multi-language path (Phases
   20–24) labelled as roadmap, never taught.

## 5. Fully self-dependent (owner requirement — law)

"Self-dependent" means the site stands alone on its own files:

1. **Zero fetched external resources, ever.** No CDN links, no external
   fonts (system font stack only), no external JavaScript, no external CSS,
   no external images, no analytics, no third-party embeds. Everything the
   browser *loads* is a file in this repo's `website/` folder.
2. **Outbound hyperlinks are fine** (they are the user's choice, not a
   dependency): github.com (repo, README, Releases, Issues, JOURNEY), the
   package repo URL, F-Droid/GitHub **only** where the README tells the user
   to get Termux for the optional engine. Nothing else.
3. **Offline-complete:** every page must render fully (layout, styles, all
   content) with the network turned off after first load.
4. **The course is self-sufficient:** a learner completes all 17 chapters
   without ever needing to open the GitHub repo. Repo links exist only as
   optional "go deeper" footnotes.
5. **Verification (W6, recorded in `chat-web6/`):**
   - grep sweep: no `http(s)://` in any `src=`, `<link href=`, `<img src=`,
     `@import`, or `url(` — the only `http(s)` occurrences in the site may
     be in `<a href>` (outbound links).
   - offline check: open the site with networking disabled, all pages
     render.
   - link sweep: every internal URL and outbound link resolves (HTTP 200 /
     redirect).

## 6. Design direction

- **Mobile-first, dark by default** — the audience is on Android; the app
  wears a dark Spck-grade skin; the site should feel like it belongs to the
  app.
- **Terminal accents:** monospace for code/commands/labels; one accent
  family (green/amber, terminal-style) on near-black; WCAG AA contrast.
- **Content-first:** max-width text column; card grid for feature callouts;
  tables for engines/packages/chapter index; simple heading+anchor FAQ.
- **Learn wing layout (Termux-Mastery pattern):** course home with the
  chapter table (number/topic/what you'll do); each chapter page: goal box
  at top, "Chapter N of 17" breadcrumb, step sections, highlighted Try-it
  boxes, Common-mistakes box, Prev/Next footer. Reading a chapter should
  feel like reading a book page.
- **Fast & light:** no carousels, no autoplay video, no blocking webfonts
  (system font stack — required by §5); optional app screenshots only if
  the owner supplies/approves them (O1), stored locally in `website/` —
  never fabricated, never external.
- **No JavaScript by default.** Vanilla JS only if static HTML/CSS cannot do
  a job (mobile nav toggle, if not done via CSS), decided in W5/W6, kept in
  the repo (self-dependent).
- Breakpoints: phone (default) → tablet → desktop. Desktop is an
  enhancement.

## 7. Stack & repo layout (locked — D3/D7)

- **Plain static site:** one folder of `.html` files + one shared `.css`
  (+ favicon). No framework, no build step, no dependencies, no lockfile,
  no node_modules. (More pages than v1 planned — still the right weight:
  each chapter is one HTML file the owner can edit in a browser on GitHub.)
- **Host:** GitHub Pages, same repo.

```
website/                 ← the ENTIRE website (new top-level folder, created in W1)
  index.html             ← Home
  install.html  start.html  engines.html  packages.html
  faq.html  about.html
  learn.html             ← course home
  ch-01.html … ch-17.html
  style.css
  (favicon assets; optional approved screenshots)
```

- Nothing in `website/` is served by the app; nothing in `app/`,
  `codec-packages/`, `docs/`, `gradle*` changes as part of website work.
- The one exception at W6: the GitHub Pages workflow (`pages.yml`, clearly
  named) + a one-line site link in the root README.

## 8. Deployment (W6)

- **GitHub Pages**, same repo, serving `website/` (source = *main branch /
  `website` folder* proposed; `gh-pages` branch only if owner prefers —
  record the choice in DECISIONS.md).
- Site URL: `https://pabi277.github.io/CodeC/…` (custom domain only if O2
  says so).
- After deploy: README gets one line linking the site (the single allowed
  cross-workstream edit, part of W6's commit).
- Evidence for the record: green Pages build + every page URL opened once +
  §5 self-dependency checks pass.

## 9. Implementation phases (owner commands one at a time; product wing first, course second, deploy last)

| Phase | Scope | Exit condition |
|---|---|---|
| **W1** | Scaffold `website/`: shared chrome (header/footer incl. Learn item), stylesheet, self-dependent asset rule in force from day one; Home page (product hero + learning banner). | Home renders at 360 px & 1440 px; all footer links valid; zero external resources (first §5 sweep). |
| **W2** | `/install` + `/start` (full content per §3.1). | Both pages in the scaffold; content matches README facts. |
| **W3** | `/engines` + `/packages` + `/faq` + `/about` (full content per §3.1). | Tables match README; package list = README list in scope; every FAQ answer traceable to a repo doc. |
| **W4** | `/learn` course home + chapters 01–06 (per §3.2 template); **package list & engine facts re-verified against README** and recorded in `chat-web4/` (locks the final chapter set, closes O6). | Course home + 6 chapters render; chapter template consistent; verification notes recorded. |
| **W5** | Chapters 07–12 (Editor, C Basics, Scripting, Python, Git, Networking). | All snippets verified runnable on built-in TCC / as shipped; every chapter self-sufficient (no "open the repo" requirement). |
| **W6** | Chapters 13–17 (Device APIs, Web Projects, Custom/Advanced, Real Projects, Troubleshooting) + polish pass + GitHub Pages deploy + README link + full link sweep + §5 self-dependency verification. | 25 pages live (7 product + learn + 17 chapters); Pages build green; all links verified; offline check passed; report with URL. |

Each phase: one `web_docs/chat-webN/` record + living docs update + commit +
push + report + stop at the merge gate. Owner may re-scope or re-order —
record it in `DECISIONS.md` first.

## 10. Acceptance criteria (whole site)

1. **25 pages** (7 product + course home + 17 chapters), shared chrome, no
   broken internal/external links (W6 sweep, recorded).
2. **Every factual claim traceable** to a repo file; traceability notes in
   `chat-webN/` records.
3. **Self-dependent (W6, §5):** zero fetched external resources; offline
   render verified; course completable without the repo.
4. Renders acceptably at 360 px and 1440 px (visual check, W6).
5. **Course quality:** every chapter has goals / steps / try-it / common
   mistakes; every command works on a fresh CodeC install.
6. Deploys on GitHub Pages; site URL recorded in `web_docs/NEXT_STEPS.md`.
7. No app code, tests, or the APK CI workflow changed.
8. `web_prompt.md` + `web_docs/` living docs updated in the final commit.

## 11. Open questions (owner decides; do not guess)

1. **O1 — Screenshots:** wanted on Home/chapters? Owner supplies/approves
   images (never fabricated, always stored locally).
2. **O2 — Domain:** keep the GitHub Pages URL or point a custom domain?
   (Default: Pages URL.)
3. **O3 — Copy tone:** technical-direct (proposed) vs friendly-casual.
4. **O4 — Phase order:** W1→W6 as listed, or re-prioritized?
5. **O5 — In-app link to the site** (app workstream, separate command).
6. **O6 — Chapter set:** the 17 chapters in §3.2 as proposed, or add/drop/
   reorder? (Locked at W4 against the verified package list.)
7. **O7 — License for course content:** same as the repo (default) or MIT
   (like Termux-Mastery)?
