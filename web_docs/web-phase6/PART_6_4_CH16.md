# CodeC Website Phase W6.4 — Chapter 16: Real World Projects

> **Owner rounds1–3: required device checks PASSED.**
> [Evidence](../chat-web6/DEVICE_ROUND_1.md): Chapter8/P1/P5 including audible speech;
> Android16 / aarch64, CodeC “latest” (exact build unspecified). No retest needed.
> Review/license and explicit PR/merge/deploy authorization are separate.

> **Current batch status — 2026-10-07, D33: CONTENT IMPLEMENTED; REQUIRED DEVICE CHECKS PASSED.**
> Owner authorized W3–W6 content together, then one commit/watch; separate start
> commands/intermediate commits below are superseded. W4.2 verification ran FIRST.
> [Batch record](../chat-web6/BATCH_SUMMARY.md) · [checks](../chat-web6/CHECKS.md) ·
> [device gates](../chat-web6/DEVICE_TESTS.md) · [current full ZIP](../chat-web6/review-zips/CodeC-website-W6-review.zip).
> D31 download law retained. Device pass recorded above; no PR, merge or deployment implied.

> **Binding v2.3 amendment — 2026-10-07 (scope supplied 2026-10-06).**
> Apply [WEBSITE_PLAN.md](../WEBSITE_PLAN.md) and the current instructions below
> before the retained prior design. They supersede conflicting old facts and exits:
> **29 pages / 9 product / 19 chapters**; app-v1.3.18 **7 217 532 B, versionCode 22**;
> AI in header, Privacy every footer; contacts in-app only; README then AI/privacy
> guides; Phases 1–96 shipped except cancelled proposals, 97+ roadmap. Never pair
> current APK size with historical -74%. No picker, Open Folder, ProjectLink,
> first-hour tiles, mandatory tour or automatic userland setup in visitor teaching.
> Earlier session-folder references are historical: preserve existing records;
> W1 uses `chat-web4/W1_*`. No next phase, PR, merge or deploy without command.

**Current status:** CONTENT IMPLEMENTED; REQUIRED DEVICE CHECKS PASSED; owner-authorized combined batch (D33).

## Current v2.3 implementation requirements

Keep all five projects and brief/plan/build/result/stretch structure. P1 C
calculator TCC-safe; website project includes opt-in LAN; Git shows real push
outcome. P1+P5 OWNER DEVICE TRANSCRIPTS mandatory, not browser substitutes.
Chapter 16 of 19; prev ch-15, next ch-17.

## Current acceptance additions (binding)

1. Follow the current requirements above and the master plan, not superseded UI/copy.
2. Preserve each non-conflicting numbered exit below, including device gates.
3. Record source file/line/sha, responsive checks and self-dependent sweep; use 19-chapter crumbs where applicable.
4. No app/workflow changes or phase expansion; update web living docs and stop at merge gate.

---

## Prior design detail — retained v2.2 record

**Historical reference, not authority for superseded facts or exit counts.**

> **v2.2 update (2026-09-12):** synced with app Phases 21–43 — universal APK 6.6 MB signed SHA256, Auto engine only picker deleted Termux card deleted fallback automatic error-path Output Panel, >_ mark icon, official file icons Seti MIT, typing feel, terminal multi-session + LAN server opt-in 0.0.0.0 two URLs QR ZXing open-in-browser keep-alive, outputs temporary RunArtifacts+RepoHygiene ~60 patterns .codec/ user .gitignore wins, GitHub truth GitReadiness+PushOutcome+Publish POST /user/repos, feedback hardcoded +91 62967 46606 / email + FeedbackScreen + exit survey + crash-log header-first + OpenInBrowser no telemetry, backup include-list-only FullBackupContent law crash-loop guard safe mode 3rd launch export-all over both roots 11 privacy rows RELEASE_NOTES template {{SHA256_LINES}}, safe walk Throwable-safe TreeWalkPolicy ProjectLink persisted SAF grant noexec mirror, BETA B-1…B-8, TCC null on armeabi-v7a/x86.

**Status:** 📋 **PLANNED** · **Cost:** `[static]` · **Effort:** M
· **Depends on:** W4 + W5 (all skills taught so far)
· **Target file:** `website/ch-16.html`

---

## 1. Content

- **Goal box:** stop doing exercises — build five things you'd keep: a C
  tool, a web page, a Python utility, a git-backed project, an automation.
- **Need:** Chapters 01–15 (each project names the chapters it uses).
- **Format per project:** *The brief → the plan (bullets) → the build
  (code blocks, verbatim-typeable) → the result (what you should see) → a
  stretch goal.* No project may require anything unshipped (scope law).

### The five projects

1. **P1 — A CLI calculator in C** (ch 02, 08, 15) — menu-driven: + − × ÷,
   two doubles, a `while` loop, a `compute` function, `make`-able. Result:
   `./calc` runs a 3-operand session. *Stretch:* add a history of the last
   5 results in an array.
2. **P2 — Your personal page** (ch 06, 14) — a real `about` page in a
   project: `index.html` + `style.css` + `app.js` + one fetch of
   `data.json` (your name, one bio line, a dark theme with CSS custom
   properties). Result: RUN → live-reloaded page; export the project ZIP.
   *Stretch:* a second page linked from the first (relative links over the
   loopback).
3. **P3 — A log summarizer in Python** (ch 10, 06) — `summarize.py`:
   reads a text file from argv, prints lines/words/chars + the top 3
   most-used words (a dict count, `sorted(..., key=...)` — basic 3.8+
   only). Result: run it on two real files from your projects. *Stretch:*
   `--json` flag printing a JSON object.
4. **P4 — A git-backed project** (ch 11) — your own GitHub repo from the
   phone: create the repo on GitHub's web UI (two lines + link), Clone
   from GitHub, add P1's `calc` (or P3's script) as the first commit,
   push, make a branch, change one thing, merge — the whole honest-git
   loop with the ↑N badge moving live. *Stretch:* a README.md in the repo
   (edit in the app, commit, push).
5. **P5 — A morning automation script** (ch 09, 05, 13) — `morning.sh`:
   `pkg upgrade -y` (quiet line), battery level (codec-battery, one line),
   a TTS line ("Good morning, battery at N percent"), all with the
   chapter-09 PASS/FAIL structure. Result: one command — `bash morning.sh`
   — the phone greets you. *Stretch:* log each run's line to
   `morning.log` (append).

### Closing block

"What you can do now" — the five verbs (compile, script, automate,
ship, preview) in one tight paragraph + the links: the repo's Issues for
ideas, `/faq` when something fights back, chapter 17 next.

- **Mistakes (cross-project):** mixing up which chapters a project needs
  (each names its prerequisites up top); forgetting Term for `scanf`/REPL
  programs (the rule from chapters 02/03/10, restated once here);
  committing build output (the repo-local ignore handles it — chapter 06);
  a project that only works on *your* phone's state (P4 uses your own repo
  — that's the point, but the build blocks stay generic).

## 2. Implementation steps

1. Build `ch-16.html` (crumb "Chapter 16 of 17") — the second-longest
   chapter; keep each project's build to ≤ 3 code blocks.
2. **Snippet review pass** (like ch-08): C blocks ANSI-C-safe; Python
   blocks 3.8+ basic; bash blocks userland-valid — checklist recorded in
   `chat-web6/SNIPPET_REVIEW.md`.
3. Self-dependent sweep.

## 3. Exit condition

```text
1. Template complete; prev → ch-15, next → ch-17.
2. 5/5 projects present with brief/plan/build/result/stretch; every
   block passes the snippet review (recorded).
3. **DEVICE PASS REQUIRED (owner, with W5's ch-08 pass if not yet done):**
   run P1 and P5 (the two that chain the most app features) on a real
   device; transcript in chat-web6/.
4. 360/1440 clean; sweep PASS.
```
