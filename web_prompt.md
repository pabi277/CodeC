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
Android C IDE with **two wings** (plan v2.2, 2026-09-12):
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

**WHERE THE WEBSITE STANDS (2026-09-12, plan v2.2):**

- **WEBSITE PHASES W0 + W0.1 + W0.2 + W0.3 (planning + sync) are COMPLETE. NO WEBSITE
  CODE EXISTS YET, BUT THE SITE IS FULLY SPEC'D END TO END and SYNCED with app Phases 21–43.** The owner's
  strict rule for the planning session (2026-09-02) was: **no code of any
  kind** — this workstream started with documentation only, exactly like
  `docs/` + `prompt.md` started the app project. Plan v2 added the
  learning wing (owner command); v2.1 added **fully spec'd implementation
  phases** (owner command "Can you create phases"); v2.2 **synced with app Phases 21–43** (owner command "Check the new updates in the project from the docs folder then update the web-docs and web-prompmt") — see
  `web_docs/DECISIONS.md` D10–D22 and `WEB_JOURNEY.md` W0.3.
- **The phase specs (the website's `docs/chat-phase20…24` equivalent):**
  `web_docs/web-phase1/` … `web_docs/web-phase6/` — 35 docs (6 phase
  READMEs + 29 PART docs), each part with design, implementation steps and
  a numbered exit condition. Phase laws baked into the specs: **W4.2 is a
  verification gate that runs FIRST inside W4** (re-verifies README +
  `codec-packages/` build config + new facts (icon, backup rules, export-all, LAN, feedback, outputs temporary, GitHub truth, safe walk, universal APK size) into a committed Verified Facts Table,
  locks the 17-chapter set, closes O6); **ch-08 (W5) and P1+P5 (W6) carry
  device passes** (owner transcripts — same convention as app phases);
  **W6.7 verifies before the site goes live** (self-dependent sweep +
  offline render + full link sweep, recorded in `chat-web6/`).
- What exists now:
  - `web_docs/` — the website's history & planning folder (the website's
    equivalent of `docs/`): `README.md` (index), `WEBSITE_PLAN.md` (the
    master spec v2.2 — two wings (product + 17-chapter learning course) updated with Phases 21–43 facts: universal APK 6.6 MB, Auto engine only, >_ mark, file icons, LAN server, outputs temporary, GitHub truth, feedback hardcoded, backup include-list, crash-loop guard, export-all, safe walk planned),
    `web-phase1/` … `web-phase6/` (the fully spec'd phases — 35 docs, v2.2 refresh noted),
    `DECISIONS.md` (decision log D1–D22 + open O1–O8), `NEXT_STEPS.md` (head
    state v2.2), `WEB_JOURNEY.md` (narrative incl. W0.3 sync), `chat-web1/SUMMARY.md` (session record).
  - `web_prompt.md` — this file (v2.2).
- **Nothing is built, nothing is deployed, there is no `website/` folder
  yet.** If you see website HTML/CSS/JS anywhere that this session did not
  create, verify where it came from before touching it.
- **App head:** Phase 42 COMPLETE & MERGED to `main` via PR #71 (app-v1.3.17 universal 6.6 MB signed non-debuggable, SHA256 lines, updater version guard, backup include-list, crash-loop guard, export-all, BETA.md, RELEASE_NOTES template), Phase 43 PLANNED (safe folder walk + ProjectLink). Check `git log --oneline -10` and `docs/NEXT_STEPS.md` head line.

**WHAT THE OWNER MUST SAY TO PROCEED:**

Implementation starts only when the owner commands a phase in chat —
**"Start W1"** … **"Start W6"** (or "Build the website" = Start W1). Each
phase is executed **strictly per its spec folder** `web_docs/web-phaseN/`
(phase README + PART docs, one phase at a time, in order W1 → W6), writing
the site (25 pages) into a new top-level `website/` folder: W1 scaffold +
Home → W2 Install + Getting Started → W3 Engines + Packages + FAQ + About →
W4 **verification gate first (v2.2 facts)**, then course home + chapters 01–06 (ch-04 Auto only, ch-06 safe walk + ProjectLink + export-all) → W5
chapters 07–12 (ch-08 device pass, Editor with file icons + typing feel + ghost + strip + snippets 29 packs + Emmet + TextMate + 50 items, C Basics TCC-safe, Git with readiness + push truth + publish, Networking + LAN server QR + open in browser) → W6 chapters 13–17 (Device APIs 8 scripts, Web Projects + LAN, Custom/Advanced with export-all + backup + crash-loop + feedback, Real Projects with LAN share, Troubleshooting with BETA + crash-log + feedback) + polish + GitHub
Pages deploy + verification (P1+P5 device pass). Only from W1 onward is
writing HTML/CSS/JS allowed. Until a phase is commanded: planning edits
only, still no code.

**LAW (inherits the app project, no exceptions):**

- **No PR/merge without the owner's literal command in chat** (`rule.md`
  §3). Committing to and pushing the session branch is fine.
- **The website never touches app code.** `app/`, `codec-packages/`,
  `gradle*`, `scripts/`, `.github/workflows` (except adding the website's own
  Pages workflow when W6 starts) and all `docs/` content are out of bounds
  for website work. `docs/` is the app's history — never rewrite it.
- **`web_docs/` is the website's history.** Append, don't destructively
  rewrite. Update `web_prompt.md` (this file), `web_docs/NEXT_STEPS.md` (head
  state line) and `web_docs/WEB_JOURNEY.md` (timeline) as web gates close —
  the next chat trusts only what is written there and verified in git.
- **Clean-room:** website *content* is distilled from the public repo files
  (`README.md`, `docs/TROUBLESHOOTING.md`, `docs/BETA.md`, `docs/RELEASE_NOTES.md`, `docs/JOURNEY.md`) and the Termux
  site's *public structure* (page model, layout ideas) — never paste
  Termux's site source (it is GPL-ish licensed; read the public spec,
  re-implement). No decompilation, no copying of closed-source material.
- **CI is the only test executor** for app changes; the website is a static
  site — its "tests" are: builds/deploys green on GitHub Pages and every link
  on the site resolves (verify during W6).

**FACTS THAT MUST NOT REGRESS (website, v2.2 synced 2026-09-12):**

- **Stack is locked:** plain static HTML + CSS (+ minimal vanilla JS only if
  a page genuinely needs it), **no framework, no build step, no backend, no
  CMS, no database**. Served by **GitHub Pages** from the `website/` folder
  in this same repo (see `web_docs/DECISIONS.md` D3/D7). Reopening this
  requires the owner's explicit command.
- **SELF-DEPENDENT = LAW (owner command 2026-09-02, D11):** zero fetched
  external resources — **no CDN, no external fonts** (system font stack),
  **no external JS/CSS/images, no analytics, no third-party embeds**. Every
  file the browser loads must live in this repo's `website/`. Outbound
  hyperlinks are fine (github.com repo/README/Releases/Issues/JOURNEY/BETA/DATA_AND_PRIVACY, the package
  repo URL, F-Droid/GitHub only where the README points to Termux). Every
  page must render **fully offline**. The learning course must be
  **completable without ever opening the repo** (repo links are optional
  "go deeper" footnotes). Verified in W6 per plan §5: grep sweep (no
  external `src=`/`<link href=`/`@import`/`url(`), offline render check,
  full link sweep — recorded in `web_docs/chat-web6/`).
- **Learning wing (D10):** "Master CodeC from Zero to Advanced" — `/learn`
  course home + 17 chapters `ch-01…ch-17`, fixed chapter template (goals →
  steps → try-it → common mistakes → prev/next). Chapters teach **only what
  CodeC ships today** (final set locked at W4 against the verified package
  list + v2.2 facts, plan §3.2); CodeC twists: ch-04 Auto engine only (picker deleted Phase 21, Termux card deleted Phase 38.2, fallback automatic, 4 steps appear in Output Panel only when needed), ch-07 editor with official file icons (Seti MIT) + typing feel + ghost text TAB ▸ + suggestion strip ƒ/λ/≠ chips + ⌄ more + 29 snippet packs MIT + Emmet clean-room rank 0 + TextMate Dark+ + 50 items + CodeC Keys auto-close, ch-08 TCC-safe law (ANSI C + most C99), ch-11 Git with readiness + push truth + publish via POST /user/repos, ch-12 Networking + LAN server opt-in 0.0.0.0 two URLs + QR ZXing Apache-2.0 + open in browser + keep-alive foreground service, ch-13
  CodeCApi (8 scripts: battery/sensor/TTS/camera/intent + clipboard/notify/toast/share/open-url/vibrate), ch-14 web projects + live preview + LAN share, ch-15 custom setup + export-all ZIP + backup include-list-only + crash-loop guard safe mode 3rd launch + feedback + About fingerprint, ch-16 real projects with LAN share, ch-17 troubleshooting with BETA B-1…B-8 + crash-log.txt header-first + feedback hardcoded. Structure mirrors the owner's own
  Termux-Mastery (same owner — clean-room safe; its content is NOT reused).
  Total site at completion: **25 pages** (7 product + course home + 17
  chapters).
- **Content source of truth is the repo itself** — `README.md` first, then
  `docs/TROUBLESHOOTING.md`, `docs/BETA.md`, `docs/RELEASE_NOTES.md`, `docs/JOURNEY.md`. The site never states
  anything the repo files don't support (feature claims, package list,
  engine table, install steps, icon, backup rules, export-all, LAN server, feedback). When the README changes, the site's affected
  section changes in the same effort — drift is a bug.
- **Install facts (as of 2026-09-12, v2.2):** CodeC is distributed **from GitHub
  only** — **release channel `app-v*` tags** → `CodeC-IDE-<version>-universal.apk` **ONLY APK** 6.6 MB signed non-debuggable (R8 + shrinkResources -74% from 25.5 MB, per-ABI splits measured <2% and reverted because `assets/tcc/<abi>` not filtered, okhttp removed, mapping.txt CI-only), release notes with `sha256:` lines + versionCode + date + changelog asserted by `scripts/check_release_notes.sh`, **Actions debug artifacts** `CodeC-IDE-debug` / `CodeC-IDE-release` for branch builds (debug key vs upload key), **in-app Settings → About → Check for updates** looks only at `app-v*` releases, compares versions numerically ("up to date" / named refusal when older), verifies `sha256:` line before installing, opens Releases page when no checksum, never installs `userland-*` bootstrap as app, never phones home. **Debug→release signing change = fresh install** — export projects first (Files → Export all). **No Play Store / F-Droid listing** — site must never imply one. Single CTA "Get the APK on GitHub" → Releases page (never artifact URL).
- **Repo facts the site will state (v2.2):** original **>_ mark** `docs/icon/codec-512.png` (Phase 38.1: adaptive layers + real monochrome layer + 10 PNG rasters + notification silhouette `ic_stat_codec` replacing `ic_launcher_foreground` + system `ic_dialog_info`, `app_mark` in About header, sharp 0.35.4 deterministic md5, template webps deleted), **built-in TCC compiler** (offline, instant, no Termux needed; arm64-v8a + x86_64, **null on armeabi-v7a/x86** so C works via Clang module or Termux fallback), **Auto engine only** (picker deleted Phase 21, Termux card deleted Phase 38.2 but mechanism stays: TermuxCompiler fallback + RUN_COMMAND permission + <queries>, guidance moved to Output Panel error path via CompilerRemediation, 4 setup steps appear only when build fails with Permission denied, wording source TROUBLESHOOTING.md §27), **in-app VT/ANSI terminal** ("Mini-Termux", Canvas grid + PTY via JNI openpty, multi-session TerminalSessionManager 8-cap anyAlive wake lock, session switcher dropdown + rename/close, progressive apt streaming, background command survival), **device as server LAN** (opt-in 0.0.0.0 bind pool 8100–8199 never <1024, ServerEndpoints.of, two-URL share panel + ZXing QR Apache-2.0, ServerRegistry/ServerHost port truth, keep-alive on existing RunForegroundService Serving <project> on <ip>:<port> + Stop, open in browser via OpenInBrowser/ShareActions copy fallback), **25+ signed packages** (`git`, `python`, `clang`, `nano`, `make`, `ripgrep`, `tmux`, …) from signed repository `https://pabi277.github.io/CodeC/dev` (signed-by= never trusted=yes, gpgv verification, bootstrap userland-v2-dev SHA-256 verified staged atomic), **HTML web preview over loopback + LAN**, **Spck-style editor + Projects hub + honest git** (official file icons Seti MIT, typing feel keyboard stay open smooth non-blinky caret no cursor on open, ghost text TAB ▸, suggestion strip ƒ/λ/≠ chips ⌨ ⌄ more tap-accept long-press tooltip swipe-down dismiss, 29 friendly-snippets MIT packs ~54KB deflated 84C/76Python/126HTML/156CSS/367JS/140TS/62MD/16shell + Emmet clean-room rank 0 + MAX_ITEMS 50 MAX_CHIPS 8 + TextMate Dark+ 15 langs lazy off UI thread + CodeC Keys auto-close, Projects card list type mark ⌥ branch · N files · age change badge amber ↑N when commits never reached remote filter chips + search + ONE + sheet New Project/Clone Git Repo/Import ZIP/Open Folder, file tree with in-tree git status letters, tabs dirty dot close, snippet/extra-keys row above status bar, Source Control sheet per-file stage toggle, opens straight into file you left in first launch → Projects hub autosave ~2s after stop typing, Switch Branch branch list local+remote + New branch… Spck promise dirty work stashed and restored, honest git conflicts grouped purple Mark Resolved block commit branch with no upstream published on first push --set-upstream failed push never looks successful "Committed locally ✓ — NOT pushed: …" PUSH retry, GitReadiness pre-flight is git installed · token stored · is repo · has remote · branch has upstream · anything to push computed pure data rendered before user taps Install Git routed to Modules installer, clone error shows inside dialog not behind, PushOutcome/PushParser result card what pushed to which remote/branch Everything up-to-date * [new branch] or still local because X, Publish to GitHub via POST /user/repos private by default ls-remote-verified adopt-existing, outputs temporary never in repo RunArtifacts filesDir/CodeC/temp/runs/<stamp>/ + TempGc age/capacity/newest-N prunes on start and after Stop RepoHygiene ~60 patterns incl .codec/ enforced inside stageAll git rm --cached already tracked what will be committed list before commit user's .gitignore always wins, safe folder walk planned Throwable-safe TreeWalkPolicy budgets progress+cancel per-provider failure as message Throwable at boundary grant persisted takePersistableUriPermission never called today, ProjectLink projectName treeUri + ProjectLinkPolicy.decide take/refuse/re-pick when grant gone noexec physics emulated storage mounted noexec so linked project runs from internal mirror sync in on open/save push-set on save-back excludes CodeC own outputs 39.1 must land first, ZIP import guarded MAX_ZIP_ENTRIES 10k MAX_ZIP_ENTRY_BYTES 128 MB total-bytes cap path-escape check), **export-all ZIP** over both project roots (filesDir + externalFilesDir candidate list) byte-identical round-trip, **backup include-list-only** (FullBackupContent lint law exclude-under-include hard error, projects only token/userland not backed up), **crash-loop guard** safe mode on 3rd failed launch with export + report hand-off, **feedback & support** (hardcoded DeveloperContact WHATSAPP_E164 916296746606 display +91 62967 46606 EMAIL chakraborttypabi2772006@gmail.com single source every channel reads, FeedbackScreen own screen Settings one OPEN row audit control 46, exit survey back-at-root Enjoying CodeC? 💚 star row / SHARE EXPERIENCE / GIVE A REVIEW / NOT NOW / EXIT tap back again to exit outside taps disabled rating rides report info line · Rating: 4/5 NOTHING uploaded by itself GIVE A REVIEW opens public repo whole prompt off-able feedback_exit_prompt_enabled default ON, crash-log.txt header-first frame-capped COPY ALL yields complete record dialog title = exception line versionName carries CI run number, OpenInBrowser.openOrCopy shared open-or-copy policy, no telemetry three honest disclosure lines above checkboxes ephemeral checkboxes privacy law, COPY REPORT / EMAIL / GITHUB ISSUE fallbacks repo public 2026-09-10 so issue link unconditional), **targetSdk 28 deliberate** (keeps downloaded compilers executable why GitHub not Play, Play demands AAB API-36 targeting from 2026-08-31 privacy policy 12 opted-in closed-testers 14 days), **R8 + shrinkResources** (proguard-rules.pro law file every keep needs named proven failure first jdt.annotation dontwarn run 34577896124 okhttp pair removed, 25 553 564 → 6 630 554 B -74% non-debuggable every run mapping.txt 54.9 MB CI-only), **RELEASE_NOTES template** {{VERSION}} {{VERSION_CODE}} {{DATE}} {{SHA256_LINES}} {{CHANGELOG}} asserted by check_release_notes.sh, **BETA.md** known issues B-1 huge folder slow open subfolder B-2 32-bit ARM older tablets universal APK includes native libs B-3 long-running builds die screen off keep notification visible B-4 git push asks key again after update credentials store wiped backup restore partial reconnect PAT never uploaded B-5 Parse error package appears invalid download interrupted updater verifies SHA-256 before install since 42.1 B-6 cursor/highlight wrong very long lines >10kB single line editor line-length guard 39.x readable substitute split line file on disk untouched display throttle never edit B-7 crash-loop after update app closes splash twice persisted settings mismatched build #1 cause 3rd launch loop sentence Try starting without my settings boots without settings projects untouched counter resets clean start B-8 32-bit ARM built-in C compiler not available bundled offline TCC toolchain ships only arm64-v8a/x86_64 Phase 33.3 typed null EmbeddedCompiler.tccBinary() returns null on other ABIs app never pretends otherwise install C toolchain module or use Termux.

**ORDER OF WORK:**

1. Verify state (`git status`, `gh pr list`, `gh run list`) before acting.
2. If the owner has **not** commanded implementation: stay in W0 — answer
   questions, refine the plan in `web_docs/` only (no code), update the
   living web docs, commit + push, report, stop at the merge gate.
3. If the owner **has** commanded implementation: work the current phase
   (W1–W6) strictly per its spec folder `web_docs/web-phaseN/` (v2.2 refreshed), one phase
   at a time, record the phase in `web_docs/chat-webN/`, update
   `web_prompt.md` / `web_docs/NEXT_STEPS.md` / `web_docs/WEB_JOURNEY.md`
   in the same commit, push, report (including any **device pass required**
   items: W5 ch-08, W6 P1+P5), stop at the merge gate.
4. Keep this file and the `web_docs/` living docs updated as gates close.

**Before each change, state:** what you are changing, which existing feature
it serves, which invariant (if any) it could affect — and for website work,
which part of the repo's own docs the change relies on.
