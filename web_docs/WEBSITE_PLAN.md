# WEBSITE_PLAN.md — master spec for the CodeC website

> **Status (2026-09-12, v2.2): PLANNED — nothing built yet, all phases
> fully spec'd, now synced with Phases 21–43.** v2 added the **learning wing** (owner's own Termux-Mastery
> as structural reference) and the **fully self-dependent** requirement
> (owner commands 2026-09-02). v2.1: every phase W1–W6 now has a full spec
> folder — `web_docs/web-phase1/` … `web_docs/web-phase6/` (35 docs: design,
> implementation steps, exit conditions per part). v2.2 syncs the spec with
> **Phases 34–43** (file icons, editor feel, terminal speed, LAN server, identity,
> outputs-temporary, GitHub truth, feedback, share-readiness, file-system strength)
> plus earlier **Phases 21–33** (Auto engine only, R8 weight, TextMate, snippets/Emmet, etc.).
> Implementation starts only when the owner commands it in chat —
> **"Start W1"** … **"Start W6"** (or "Build the website" = Start W1).
> Stack, pages and deployment are **decided** (see `DECISIONS.md`) —
> changing them needs the owner's explicit command.

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
  **from GitHub only** — **release channel `app-v*` tags** (universal APK `CodeC-IDE-<version>-universal.apk`, 6.6 MB signed non-debuggable, SHA256 lines in release notes), plus **Actions debug artifacts** `CodeC-IDE-debug` / `CodeC-IDE-release` for branch builds, and **in-app Settings → About → Check for updates** which looks only at `app-v*` releases, compares numerically, verifies `sha256:` lines, refuses downgrades with a named reason, and opens Releases page when no checksum. **Single universal APK** — per-ABI splits were measured (<2% saving because `assets/tcc/<abi>` rides every split) and **reverted** (Phase 42.2). One honest CTA: *Get the APK on GitHub*.
  Never imply a store listing or Play Store.
- **Package universe scale.** CodeC ships 25+ verified packages — the
  packages page and chapter 5 list them for real, no inflation.
- **No sponsors/funders section.**

## 2. Goals & non-goals

**Goals**

1. **Accurate at all times** — every claim traceable to a repo file
   (`README.md` first, then `docs/TROUBLESHOOTING.md`, `docs/JOURNEY.md`, `docs/BETA.md`, `docs/RELEASE_NOTES.md`). Every new fact from Phases 21–43 must be reflected: Auto engine, Settings trim, >_ icon, file icons, LAN server, outputs temporary, GitHub truth, feedback, backup rules, crash-loop guard, export-all.
2. **Fully self-dependent (owner requirement):** the site must stand alone —
   see §5. No external resources of any kind; the learning course must be
   completable without ever leaving the site.
3. Dead simple to build, host and maintain: static files, no build step, no
   backend — the owner can fix a typo in a browser on GitHub and it goes live.
4. Mobile-first — the audience is Android users, most arrive on a phone.
5. Deep enough to deflect support: real troubleshooting (product FAQ +
   chapter 17 + BETA.md known issues), every command in a chapter must work on a fresh CodeC
   install.
6. Cheap & boring to host: GitHub Pages on the same repo.

**Non-goals**

- No server-side rendering, no SPA framework, no build step, no CSS framework
  build, no CMS, no search index, no analytics, no i18n, no blog engine, no
  download mirror (the APK always downloads from GitHub).
- No marketing fluff: no fake feature claims, no benchmarks that aren't in
  the repo docs.
- The learning wing teaches **what CodeC actually ships today**; planned Phases beyond 43 are mentioned only as "coming", never taught.

## 3. Site structure & content

Single-page-per-topic, one shared header/footer. Final URLs
(`https://pabi277.github.io/CodeC/…` once deployed — decided in W6, §8).

### 3.0 Shared chrome

- **Header:** wordmark "CodeC" with the **> _ mark** (the original launcher icon from `docs/icon/codec-512.png` — Phase 38.1, flat adaptive + real monochrome layer + legacy PNGs, notification silhouette `ic_stat_codec`), nav: Home · Install · Start · Engines · Packages · **Learn** · FAQ · About · GitHub icon. "Learn" is a top-level nav item (the learning wing is a first-class citizen). Sticky header, collapses to a single toggle on narrow screens.
- **Footer:** "CodeC — free & open source C IDE for Android" + repo/README/Releases/Issues + "Site source: this repo, `website/`".
- **Learn pages add:** a chapter breadcrumb ("Chapter N of 17") and prev-chapter / next-chapter footer links on every chapter page.
- Every page: consistent meta title/description; one canonical link to the Releases page (never hardcoded artifact URLs — they rot).

### 3.1 Product wing (7 pages)

**`/` — Home.** Hero: "CodeC — a C programming IDE for your Android phone."
One paragraph: built-in offline compiler (TCC), real terminal, package hub, web preview with LAN server (source: `README.md` intro + Phase 37). Primary CTA *Get the APK on GitHub* → Releases (universal 6.6 MB, signed, SHA256); secondary *Read the README*. Feature callouts (6 cards, Termux landing grid pattern): Built-in compiler (offline, instant, no Termux needed; arm64-v8a + x86_64) · Real terminal ("Mini-Termux", Canvas grid + PTY, multi-session) + **Device as server (LAN)** opt-in 0.0.0.0 bind, two URLs, QR (ZXing), keep-alive foreground service · Package hub (25+ signed, 1-tap INSTALL/RUN, live badges) · Spck-grade editor with **official file icons** (Seti MIT), **typing feel** (ghost text TAB ▸, suggestion strip ƒ/λ/≠ chips + ⌄ more, 29 snippet packs MIT + Emmet, TextMate Dark+, CodeC Keys auto-close) · Web preview + LAN sharing · Always updatable & safe (in-app updater with SHA256 verification, export-all ZIP, backup include-list only, crash-loop guard safe mode on 3rd launch). **Learning banner:** "New to the command line or to C? Start the free course — Master CodeC from Zero to Advanced" → `/learn`. Footnote strip: "Free & open source · Built-in compiler · No Termux required · 6.6 MB universal".

**`/install` — Install guide.** Updated to Phase 42.1 release channel:

1. **Release (everyone):** GitHub Releases page → `app-v*` tag → download **`CodeC-IDE-<version>-universal.apk`** — the ONLY APK, 6.6 MB signed non-debuggable (R8 + shrinkResources, -74% vs 25.5 MB, Phase 42.2). Release notes carry `sha256:` lines + versionCode + changelog. Verify with `sha256sum`.
2. **Developer / branch builds:** push → Actions → Build APK → Artifacts `CodeC-IDE-debug` (debug key, updates only over debug) and `CodeC-IDE-release` (signed with upload key). A release is published only from `app-v*` tag — tag must equal versionName, versionCode must exceed shipped (publish step checks).
3. **In-app updater:** Settings → About → Check for updates — looks only at `app-v*` releases, compares numerically ("up to date" / named refusal when older), verifies `sha256:` line before installing, opens Releases page when no checksum. Never installs `userland-*` bootstrap as app, never phones home by itself. Also note **debug→release signing change = fresh install** — export projects first (Files → Export all).

"Allow install from unknown sources". Device support (arm64 best; x86_64 emulators via built-in TCC; 32-bit: built-in TCC **not bundled** — `tccBinary()` returns null on armeabi-v7a/x86, so C works through Clang module or Termux fallback; universal APK still runs, per-ABI splits reverted because `assets/tcc/` not filtered). **Optional Termux engine:** Phase 38.2 removed Settings card — mechanism untouched (TermuxCompiler fallback + RUN_COMMAND permission + <queries> stay). When a build actually needs fallback (Permission denied), Output Panel prints four setup steps (wording source TROUBLESHOOTING.md §27): install Termux 0.109+ from F-Droid/GitHub (Play version outdated), `echo "allow-external-apps=true" >> ~/.termux/termux.properties`, `termux-reload-settings`, `pkg update && pkg install clang`, grant "Run commands in Termux" permission. Links: Releases, BETA.md, TROUBLESHOOTING §27.

**`/start` — Getting started (first hour).** Shortest first compile (hello.c → RUN), the terminal loop (`cc hello.c -o a.out` → `./a.out`, explain the `./` rule), where input programs (`scanf`) run — Term tab, not RUN. Packages tab 1-tap install & run. Web preview (RUN on HTML, live reload). **First-run tiles** (Phase 33.1): three starter tiles C/Python/HTML, DataStore flag `first_launch_complete`. "Where things live" map: Projects · Editor · Terminal · Packages · Settings. Mention **editor opens straight into file you left in** (first launch → Projects hub), autosave ~2s. Cross-link: "Ready for the full course? → Chapter 1".

**`/engines` — Compiler engines.** Updated to Phase 21 + 38.2 truth: **There is nothing to pick — every RUN uses Auto** (built-in TCC first offline instant; if unavailable, Clang module from Packages; if Android blocks downloaded compiler — W^X, noexec, CPU mismatch, broken toolchain — compiles via Termux Clang automatically). The four setup steps for that last fallback appear in Output Panel exactly when needed — TROUBLESHOOTING.md §27. The **Settings → Compiler Engine picker and COMPILER_BACKEND preference were deleted in Phase 21** (D22); **Termux Engine card deleted in Phase 38.2** (D17) — guidance moved to error path via `CompilerRemediation` → `finishFailedBuild` Output-Panel line. Table still explains conceptual engines for learning: Auto (default) / Built-in TCC (ANSI C + most C99, learning & everyday) / Bundled Clang (full C11/C17, arm64 only) / Termux (optional, needs setup). Why TCC first (offline, instant, W^X-safe, static musl, native lib dir allowed at any targetSdk), coverage honesty, arm64 note, x86 emulator note, CHECK BRIDGE removed (now error-path message). Link to /install and /faq.

**`/packages` — Package hub & repository.** What `pkg`/Packages tab is (guarded CodeC-only frontend). The real package list (25+; from README + codec-packages config: `git`, `python`, `clang`, `nano`, `make`, `ripgrep`, `tmux`, …) as a table with N from config (sha recorded in chat-web3, feeds W4.2). How the repo works: `https://pabi277.github.io/CodeC/dev`, signed metadata (`signed-by=`, never `trusted=yes`), SHA-256-verified bootstrap `userland-v2-dev`, atomic installs, gpgv verification. 1-tap UI: INSTALL/RUN buttons, live status badges (`INSTALLED ✓` / `AVAILABLE`), quick system actions (`pkg update`, `upgrade -y`, `codec-setup-storage`, `status`, `heal`, `repair`), interactive command runner. Extra-keys row (ESC/TAB/CTRL/ALT/arrows) + custom macros. Honest scope note.

**`/faq` — FAQ & troubleshooting.** Distilled from README §Troubleshooting + `docs/TROUBLESHOOTING.md` + `docs/BETA.md` (B-1…B-8) — website-length answers, each linking to repo doc for depth:

- compiler could not start · Permission denied (W^X/noexec, targetSdk 28 compatibility mode, reinstall once, Termux fallback) · Exec format error (CPU mismatch, TCC null on armeabi-v7a) · Runtime libraries missing · hangs (30s/10s caps, scanf in Term) · Do I need Termux? (No) · hardware keyboards/extra-keys + CodeC Keys · projects & export + **export-all ZIP** + backup rules (include-list-only, token not backed up, userland not backed up) · crash-loop guard (3rd launch safe mode with export + report hand-off) · huge folder slow (open subfolder) · 32-bit ARM built-in not available · debug vs release APK (debuggable flag, run-as risk) · signature change = fresh install · "Parse error / package appears invalid" (SHA256 verification since 42.1) · cursor wrong on very long lines (Phase 39 guard) · where to report bugs (include Logs "Device:" line, versionName with CI run number, feedback & support via WhatsApp hardcoded +91 62967 46606 / email chakraborttypabi2772006@gmail.com, GitHub issue).

**`/about` — About CodeC.** What it is / who it's for. Feature tour in short form (editor with official file icons, ghost text, suggestion strip, snippets+Emmet, TextMate Dark+, typing feel, projects with safe folder walk + ProjectLink persisted SAF grant + noexec mirror, honest git with readiness + push truth + publish, web preview + LAN server with QR + open in browser, device APIs via CodeCApi: battery/sensor/TTS/camera/intent, outputs temporary never in repo, backup rules, crash-loop guard, export-all, feedback & support). **The story:** phases 0–43 built in public (0–19 core, 20–24 multi-lang, 25–33 first-hour UX + IntelliSense, 34–37 UX/UI, 38–43 share-readiness) → link `docs/JOURNEY.md`. **Engineering facts:** original >_ mark (`docs/icon/codec-mark.svg`, safe-zone-verified, adaptive layers + real monochrome layer + 10 PNG rasters + codec-512.png generated by `scripts/render_icon.mjs` sharp 0.35.4 deterministic md5, template webps deleted, `ic_stat_codec` notification silhouette replacing `ic_launcher_foreground` + system `ic_dialog_info`, `app_mark` in About header), static musl TCC in APK arm64+x86_64, signed package repo, CI-built APK (Build APK runs assemble + testDebugUnitTest + lintDebug), clean-room approach, targetSdk 28 deliberate (keeps downloaded compilers executable, why GitHub not Play), R8 + shrinkResources (proguard-rules.pro law file, okhttp pair removed, 25.5 MB → 6.6 MB -74%), backup XMLs include-list-only (FullBackupContent lint law), crash-log.txt header-first with COPY ALL, FeedbackDraft wa.me with hardcoded DeveloperContact, no telemetry, 11 privacy rows, telemetry scan. Links: repo, README, JOURNEY, Releases, Issues, BETA.md, DATA_AND_PRIVACY.md, RELEASE_NOTES.md template.

### 3.2 Learning wing — "Master CodeC from Zero to Advanced"

**`/learn` — Course home** (mirrors the Termux-Mastery home):

- *About this course* — structured, text-based guide for complete beginners (no command line, no C required); read like a book, chapter by chapter; each chapter builds on previous; hands-on in CodeC app on phone.
- *Why learn with CodeC?* — compiler + terminal + packages in one app, offline, no computer needed, 6.6 MB universal, feedback reaches developer.
- *What you need* — Android phone (arm64 best, x86_64 emulator works, 32-bit needs Clang module), CodeC APK (→ `/install`), 10–20 minutes per chapter.
- *Course structure* — chapter table (below): number / topic / what you will be able to do (updated with 34–43 facts).
- *Roadmap* — where course goes ("17 chapters, hands-on projects at end").
- *Disclaimer* — educational and utility purposes only; no hacking content; use responsibly.
- *License* — site content licensed with owner's chosen license (default: same as repo; owner confirms in O7).

**Chapter pages `/learn/ch-01` … `/learn/ch-17`.** Every chapter follows one template (self-contained): **Learning goals → What you need (a fresh CodeC install is enough, or named earlier chapters) → Step-by-step (every command typed by learner, in copyable code block) → Try it yourself (1–3 exercises) → Common mistakes → Prev / Next.** No chapter requires leaving site; repo links optional "go deeper" footnotes.

Proposed chapter set (mirrors Termux-Mastery's 15-chapter arc, adapted to CodeC reality; final set confirmed against verified package list at W4 and by owner via O6, now enriched with Phases 34–43):

| # | Chapter | You will be able to… | Updated notes (Phases 34–43) |
|---|---|---|---|
| 01 | Getting Started | install CodeC from GitHub (universal APK + SHA256 + updater), find every tab (Projects · Editor · Terminal · Packages · Settings), first-run tiles, take first look, know backup/export-all | Includes release channel, debug vs release, export-all, backup rules |
| 02 | Your First C Program | write `hello.c`, tap RUN, read output, then terminal loop `cc hello.c -o a.out` → `./a.out` (and why `./`) | RUN = Auto engine, TCC default, no picker |
| 03 | The CodeC Terminal | work in Term tab: `ls`, `cd`, `pwd`, `mkdir`, `touch`, `cat`, `cp`, `mv`, `rm`, `clear`; one command per line; where programs run; input programs (`scanf`) must run here; multi-terminal sessions, session switcher, terminal speed & feel (Phase 36) | Includes TerminalSessionManager, session switcher dropdown + rename/close |
| 04 | Compiler Engines | understand Auto fallback chain; know why TCC first; know device limits; see error-path guidance when Termux needed | **Updated:** no picker (deleted Phase 21), Termux card deleted Phase 38.2, CHECK BRIDGE now error-path Output Panel line (CompilerRemediation), 4 setup steps appear only when needed; table = conceptual, not selectable |
| 05 | Package Manager | 1-tap install & run from Packages tab; `pkg` commands; update/upgrade/heal; read live status badges | Includes quick system actions, extra-keys macros |
| 06 | Files & Projects | create/open projects, the `+` sheet (New / Clone / Import ZIP / Open Folder), single files, export, where projects live, **safe folder walk** (bounded, Throwable-safe, budgets, cancel/progress), **open folder as project** (ProjectLink + persisted SAF grant, noexec mirror sync in on open/save), export-all ZIP | Includes Phase 43.1/43.2, two project roots (filesDir + externalFilesDir), ZIP caps (10k entries, 128 MB) |
| 07 | The Editor | tabs & dirty state, undo/redo, find & replace (regex), Format, extra-keys row, autosave, compiler-error squiggles, **official file icons** (Seti), **typing feel** (keyboard stays open, smooth typing, non-blinky caret while typing, no cursor on open until tap), **ghost text** (TAB ▸, →▸ word, caret-row pill, HW Tab & Ctrl+→), **suggestion strip** (ƒ/λ/≠ chips, pinned ⌨ and ⌄ more, tap-accept, long-press tooltip, swipe-down dismiss-per-identifier), **snippets** (29 friendly-snippets MIT packs, 84 C / 76 Python / 126 HTML / 156 CSS / 367 JS / 140 TS / 62 MD / 16 shell), **Emmet** (clean-room, rank 0, `ul>li*3`, `!`, `m10`), **MAX_ITEMS 50** (8 chips + ⌄ more), **TextMate** (VS Code Dark+, 15 langs, lazy per language), **CodeC Keys** auto-close `()[]{}""''`, `{`+Enter indented | Enriched with Phases 28–35 |
| 08 | C Programming Basics | write real C: types, `printf`/`scanf`, operators, loops, functions, arrays, first pointers — every example typed and RUN in CodeC (standard C teaching content, verified runnable on TCC) | **TCC-safe law:** ANSI C only, no C11 constructs, snippet-review checklist, device pass (owner transcript) |
| 09 | Shell Scripting | write and run bash scripts in CodeC, variables, loops, conditionals, first automation | |
| 10 | Python in CodeC | install `python`/`python3`, run scripts, REPL, small utility | |
| 11 | Git & GitHub | in-app GitHub account, clone repo, Source Control pane, COMMIT & PUSH, branches, honest git (unpushed badge amber ↑N, conflict flow, **GitReadiness** pre-flight, **inline clone error** inside dialog not behind, **PushOutcome/PushParser** result card "Committed locally ✓ — NOT pushed", **Publish to GitHub** via POST /user/repos private by default ls-remote-verified, Switch Branch stash/auto-restore + New branch) | Updated with Phase 40 |
| 12 | Networking & SSH | network tools **as they exist in CodeC package repo** (list verified at W4: e.g. `curl`/`wget`/`openssh` if shipped); **device as server LAN** (opt-in 0.0.0.0 bind, ServerEndpoints.of, two-URL share panel + ZXing QR Apache-2.0, ServerRegistry/ServerHost port truth, keep-alive on existing RunForegroundService, open-in-browser via OpenInBrowser, copy fallback), basic troubleshooting | Updated with Phase 37 |
| 13 | Device APIs (CodeCApi) | CodeC answer to Termux API: `codec-battery`, `codec-sensor`, `codec-tts`, `codec-camera`, `codec-intent` — with `NEED_PERMISSION:` flow, plus `codec-clipboard`, `codec-notify`, `codec-toast`, `codec-share`, `codec-open-url`, `codec-vibrate` (Phase 5.3) | Includes Phase 18 + 4.7/4.8/5.3 |
| 14 | Web Projects | HTML/CSS/JS project in CodeC, RUN = preview, live reload on save, console output, `fetch`/modules over loopback server, **LAN share** from ch-12 | |
| 15 | Custom Setup & Advanced Tools | themes (VS Code Dark+ default, Monokai, Dracula, GitHub Dark), custom extra-key macros, per-project `.codec.json` config; `make`, `clang`, `ripgrep`, `tmux`, `nano` as repo ships; **export-all ZIP** backup, **backup rules** (include-list-only, projects only, token/userland not backed up), **crash-loop guard** safe mode on 3rd failed launch with export + report hand-off, **first-run permissions explainer**, **About fingerprint** (versionName + CI run number), **feedback & support** (hardcoded developer, WhatsApp chat, exit survey) | Updated with Phases 42.3 + 41 |
| 16 | Real World Projects | 3–5 hands-on projects: CLI calculator in C; personal web page with live preview + LAN share + QR; Python utility; git-backed project pushed to GitHub (publish flow); small automation script | Includes LAN sharing project |
| 17 | Troubleshooting | read every common error (mirrors `/faq` + BETA.md B-1…B-8 in depth), read Logs, "Device:" line, crash-log.txt header-first COPY ALL, feedback channel (WhatsApp hardcoded, email, copy report, GitHub issue), how to report useful bug | Includes Phase 41/42 facts |

Chapter content rules:

1. **Only what CodeC ships.** Any chapter that mentions a package, engine or API must be cross-checked against `README.md` + package repo at content-writing time (W4); "if it's not in repo, not in chapter".
2. **Every command must work on fresh install** — commands written for CodeC terminal environment (`$PREFIX` layout, `./` rule, one command per line).
3. **Chapter 8 (C Basics)** is only teaching content beyond repo docs (standard C); every snippet must be known-runnable on built-in TCC (ANSI C + most C99) — no C11-only constructs in course.
4. Code samples inline in page (self-dependent — §5); exercises checkable by learner (expected output shown).
5. **Outputs are temporary, never in your repo** (Phase 39): `RunArtifacts` routes every language's build output to `filesDir/CodeC/temp/runs/<stamp>/` + `TempGc` (age/capacity/newest-N) prunes on start and after Stop; `RepoHygiene`'s ~60 patterns incl `.codec/` enforced inside `stageAll`, user's own `.gitignore` always wins — chapter 06 must teach this.

## 4. Content rules (both wings)

1. **Source of truth order:** `README.md` → `docs/TROUBLESHOOTING.md` → `docs/BETA.md` → `docs/RELEASE_NOTES.md` → `docs/JOURNEY.md` → `docs/chat-phaseN/` (detail). If product fact isn't in repo, not on site.
2. **Distill, don't dump** — website-length answers; link out for depth.
3. **No rotting links:** stable places only (Releases page, repo root, README anchors, docs files) — never artifact URLs or run IDs.
4. **Version-awareness:** "as of" notes where README could change (package count, engine list — now Auto only, universal APK size, versionCode); W6 sweep re-reads README first.
5. **Honest scope:** C IDE today + multi-language (Python, JS, HTML) shipped; planned phases beyond 43 labelled as roadmap, never taught.

## 5. Fully self-dependent (owner requirement — law)

"Self-dependent" means the site stands alone on its own files:

1. **Zero fetched external resources, ever.** No CDN links, no external fonts (system font stack only), no external JavaScript, no external CSS, no external images, no analytics, no third-party embeds. Everything browser *loads* is a file in this repo's `website/` folder.
2. **Outbound hyperlinks are fine** (they are user's choice, not dependency): github.com (repo, README, Releases, Issues, JOURNEY, BETA, DATA_AND_PRIVACY), package repo URL, F-Droid/GitHub **only** where README tells user to get Termux for optional engine (two links, allowed). Nothing else.
3. **Offline-complete:** every page must render fully (layout, styles, all content) with network turned off after first load.
4. **The course is self-sufficient:** learner completes all 17 chapters without ever needing to open GitHub repo. Repo links exist only as optional "go deeper" footnotes.
5. **Verification (W6, recorded in `chat-web6/`):**
   - grep sweep: no `http(s)://` in any `src=`, `<link href=`, `<img src=`, `@import`, or `url(` — only `http(s)` occurrences in site may be in `<a href>` (outbound links).
   - offline check: open site with networking disabled, all pages render.
   - link sweep: every internal URL and outbound link resolves (HTTP 200 / redirect).

## 6. Design direction

- **Mobile-first, dark by default** — audience is on Android; app wears dark Spck-grade skin with VS Code Dark+ editor theme; site should feel like it belongs to app. Icon >_ mark used as favicon and header.
- **Terminal accents:** monospace for code/commands/labels; one accent family (green/amber, terminal-style) on near-black; WCAG AA contrast.
- **Content-first:** max-width text column; card grid for feature callouts; tables for engines/packages/chapter index; simple heading+anchor FAQ.
- **Learn wing layout (Termux-Mastery pattern):** course home with chapter table (number/topic/what you'll do); each chapter page: goal box at top, "Chapter N of 17" breadcrumb, step sections, highlighted Try-it boxes, Common-mistakes box, Prev/Next footer. Reading chapter should feel like reading book page.
- **Fast & light:** no carousels, no autoplay video, no blocking webfonts (system font stack — required by §5); optional app screenshots only if owner supplies/approves them (O1), stored locally in `website/` — never fabricated, never external.
- **No JavaScript by default.** Vanilla JS only if static HTML/CSS cannot do a job (mobile nav toggle, if not done via CSS), decided in W5/W6, kept in repo (self-dependent).
- Breakpoints: phone (default) → tablet → desktop. Desktop is enhancement.

## 7. Stack & repo layout (locked — D3/D7)

- **Plain static site:** one folder of `.html` files + one shared `.css` (+ favicon). No framework, no build step, no dependencies, no lockfile, no node_modules. (More pages than v1 planned — still right weight: each chapter is one HTML file owner can edit in browser on GitHub.)
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
  (docs/icon/codec-512.png used as source for favicon if needed — original >_ mark)
```

- Nothing in `website/` is served by the app; nothing in `app/`, `codec-packages/`, `docs/`, `gradle*` changes as part of website work.
- The one exception at W6: the GitHub Pages workflow (`pages.yml`, clearly named) + a one-line site link in the root README.

## 8. Deployment (W6)

- **GitHub Pages**, same repo, serving `website/` (source = *main branch / `website` folder* proposed; `gh-pages` branch only if owner prefers — record choice in DECISIONS.md).
- Site URL: `https://pabi277.github.io/CodeC/…` (custom domain only if O2 says so).
- After deploy: README gets one line linking site (single allowed cross-workstream edit, part of W6's commit).
- Evidence for record: green Pages build + every page URL opened once + §5 self-dependency checks pass.

## 9. Implementation phases (owner commands one at a time; product wing first, course second, deploy last)

**Fully spec'd (2026-09-02):** each phase has its own folder of implementation docs — `web_docs/web-phase1/` … `web_docs/web-phase6/` (phase README + one PART doc per page/chapter, with design, implementation steps and exit conditions). The owner starts one by saying **"Start W1"** … **"Start W6"** (or "Build the website" = Start W1). **v2.2 updates:** W2.1 install and W3.1 engines specs now reflect Phase 42.1 release channel and Phase 21/38.2 Auto-only engine; W3.3 FAQ and W3.4 About reflect Phases 38–43; W4.6 ch-04, W4.8 ch-06, W5.6 ch-12, W6.1 ch-13, W6.3 ch-15, W6.5 ch-17 updated with new facts. Verification gate W4.2 must re-verify against new README + package config + new facts (icon, backup rules, export-all, LAN server, feedback, etc.).

| Phase | Scope | Spec | Exit condition |
|---|---|---|---|
| **W1** | Scaffold `website/`: shared chrome (header/footer incl. Learn item, >_ mark), stylesheet, self-dependent rule in force from day one; Home page (hero + learning banner, 6 cards updated with file icons, LAN, safe features). | [web-phase1/](web-phase1/README.md) | Home renders at 360 px & 1440 px; all footer links valid; zero external resources (first §5 sweep). |
| **W2** | `/install` + `/start` (full content per §3.1 updated: universal APK 6.6 MB, SHA256, updater with version guard + checksum, debug vs release, export-all note, Termux fallback automatic). | [web-phase2/](web-phase2/README.md) | Both pages in scaffold; content matches README facts (v2.2). |
| **W3** | `/engines` + `/packages` + `/faq` + `/about` (full content per §3.1 updated: Auto only, picker deleted, Termux card deleted, FAQ includes BETA B-1…B-8 + backup + crash-loop + export-all + debug/release, About includes >_ icon + Settings trim + feedback + LAN + file icons + outputs temporary + GitHub truth). | [web-phase3/](web-phase3/README.md) | Tables match README; package list = repo build-config list (recorded with sha); every FAQ answer traceable to repo doc (TROUBLESHOOTING + BETA). |
| **W4** | **Verification gate first** (re-verify README + package config + new facts: icon, backup rules, export-all, LAN, feedback, outputs temporary, GitHub truth, safe folder walk planned, universal APK size) → lock 17-chapter set, close O6 → `/learn` course home + chapters 01–06 (ch-04 Auto only, ch-06 safe walk + ProjectLink + export-all). | [web-phase4/](web-phase4/README.md) | Verified-facts table committed (v2.2); O6 closed; course home + 6 chapters render on canonical template. |
| **W5** | Chapters 07–12 (Editor with file icons + typing feel + ghost + strip + snippets + Emmet + TextMate, C Basics TCC-safe, Scripting, Python, Git with readiness + push truth + publish, Networking + LAN server). | [web-phase5/](web-phase5/README.md) | All snippets TCC-safe / as-shipped (review recorded); ch-08 **device pass** (owner transcript); every chapter self-sufficient. |
| **W6** | Chapters 13–17 (Device APIs with 8 scripts, Web Projects with LAN, Custom/Advanced with export-all + backup + crash-loop + feedback, Real Projects with LAN share, Troubleshooting with BETA + crash-log + feedback) + polish + GitHub Pages deploy + README link + §5 verification. | [web-phase6/](web-phase6/README.md) | 25 pages live; Pages build green; pre-flight sweeps (self-dependent + offline + link) green **before** live; report with URL. |

Each phase: one `web_docs/chat-webN/` record + living docs update + commit + push + report + stop at merge gate. Owner may re-scope or re-order — record it in `DECISIONS.md` first.

## 10. Acceptance criteria (whole site)

1. **25 pages** (7 product + course home + 17 chapters), shared chrome with >_ mark, no broken internal/external links (W6 sweep, recorded).
2. **Every factual claim traceable** to a repo file (README, TROUBLESHOOTING, BETA, RELEASE_NOTES, JOURNEY, chat-phase docs); traceability notes in `chat-webN/` records.
3. **Self-dependent (W6, §5):** zero fetched external resources; offline render verified; course completable without repo.
4. Renders acceptably at 360 px and 1440 px (visual check, W6).
5. **Course quality:** every chapter has goals / steps / try-it / common mistakes; every command works on fresh CodeC install (universal APK 6.6 MB, offline TCC).
6. Deploys on GitHub Pages; site URL recorded in `web_docs/NEXT_STEPS.md`.
7. No app code, tests, or APK CI workflow changed (except Pages workflow in W6).
8. `web_prompt.md` + `web_docs/` living docs updated in final commit.
9. **New facts from Phases 21–43 reflected:** Auto engine only (no picker), Settings trim (13→11, Termux card deleted), >_ icon, file icons, typing feel, LAN server with QR + open-in-browser, outputs temporary (RunArtifacts + RepoHygiene), GitHub truth (readiness + push outcome + publish), feedback hardcoded +91 62967 46606 / email + exit survey + crash-log, universal APK 6.6 MB -74% + SHA256 + updater version guard, backup include-list-only + crash-loop guard + export-all, safe folder walk planned + ProjectLink.

## 11. Open questions (owner decides; do not guess)

1. **O1 — Screenshots:** wanted on Home/chapters? Owner supplies/approves images (never fabricated, always stored locally).
2. **O2 — Domain:** keep GitHub Pages URL or point custom domain? (Default: Pages URL.)
3. **O3 — Copy tone:** technical-direct (proposed) vs friendly-casual.
4. **O4 — Phase order:** W1→W6 as listed, or re-prioritized?
5. **O5 — In-app link to the site** (app workstream, separate command).
6. **O6 — Chapter set:** the 17 chapters in §3.2 as proposed, or add/drop/reorder? (Locked at W4 against verified package list; v2.2 enriches chapters with 34–43 facts but count stays 17 unless owner re-scopes.)
7. **O7 — License for course content:** same as repo (default) or MIT (like Termux-Mastery)?
8. **O8 — Feedback contact display on website?** Phase 41 hardcodes developer WhatsApp +91 62967 46606 and email chakraborttypabi2772006@gmail.com as the only contact — should About/FAQ show it, or keep it in-app only? (Default: in-app only, site links to GitHub Issues.)
