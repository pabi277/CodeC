# prompt.md — paste this into the next chat

> Copy **everything between the two `---` lines below** as the first message of
> a new chat. Do not edit it. It forces the next agent to verify before acting,
> to trust the repo over its own assumptions, and to continue CodeC in the
> right order without redoing or breaking anything.

---

Read `docs/JOURNEY.md` and `docs/NEXT_STEPS.md` first, **before doing anything
else**, then report back what you found and the current git/PR/CI state before
making any change.

You are continuing **CodeC** (an Android C / multi-language IDE with its own
Termux-style terminal + signed package repo). Each chat session gets its own
`arena/*` session branch — verify the actual branch with `git status` instead
of assuming one. Commit and push to the SESSION branch only; never push to
`main` or any other branch.

**WHERE THINGS STAND (2026-08-31):**

- **Phases 3–14 are all COMPLETE, DEVICE-ACCEPTED and MERGED to `main`. Do not
  redo, re-debug or "improve" any of them** unless the identical symptom
  reappears AND you have regression evidence. Merged PRs and the `main` tip:
  - Phase 3 (repo/bootstrap/signing) ✅ PR #15. Parts A–D device-verified.
  - Phase 4 (storage, install UX, trust, settings, catalog, clipboard,
    notifications) ✅ device-verified 2026-08-26.
  - Phase 5 (KI fixes, web preview, capabilities) ✅ PR #23.
  - Phase 6 (terminal UX) + Phase 10 (Package & Command Hub) ✅ PR #25.
  - Phase 7 (multi-terminal sessions) ✅ PR #26.
  - Phase 8 (projects, folder tree, ZIP round trip, web run) ✅ PR #27.
  - Phase 9 (editor foundation + 9.1/9.2) ✅ PR #28 at `961e942`.
  - Phase 11 (Output panel & integrated run) ✅ PR #29 at `771f58f`.
  - Phase 12 (multi-language, Python, code intelligence) ✅ PR #30 at
    `260d8b6` (published python 3.14.6-1 + python-pip 26.2.1).
  - Phase 13 (GitHub & Git integration) ✅ PR #31 at `006515a`.
  - **Phase 14 (Mixed-Language, Server WebViews & Long-Tail Ecosystem) ✅
    MERGED — PR #32 at `main` = `0b591e2` (2026-08-31).** Server Runner +
    port monitor, Web Preview live mode, Flask/FastAPI/C-microservice presets,
    bundled `demo_flask`, Auto (detect) projects (D10). `[client-only]` — no
    `[repo-build]`. Record: `docs/chat-phase14/PART_14_IMPLEMENTATION.md`.
- **`main` is at `0b591e2` and current through Phase 14.**

- **Phases 15–19 are PLANNED — design/spec + phone mockups ONLY, NO code yet.**
  They live under `docs/chat-phase15/` and `docs/chat-phase19/` and are tracked
  in **PR #33 (docs-only, OPEN on `arena/01a05668-codec`)** — awaiting the
  owner's word to merge. Two owner requests drove them:
  - **Phases 15–17 — Spck Editor clone** (make CodeC's project + editor UX
    mirror **Spck Editor / Git Client**): 15 = Projects Hub & Unified Import
    (card list, filter chips, search, one `+` sheet → New Project / Clone Git
    Repo / Import ZIP / Open Folder, per-project git actions); 16 = Spck-style
    Editor Shell (nav drawer file tree with git status, refined tabs, snippet/
    extra-keys keyboard row, readability controls, launch-default HTML preview,
    errors badge); 17 = In-editor Source Control & Branching (SC sheet, in-tree
    M/A/D/? status letters, tap-to-diff, Switch Branch + stash, Pull/Push,
    merge-conflict marking). Docs: `docs/chat-phase15/` (README +
    PART_15/16/17 + `mockups/`).
  - **Phase 18 — CodeCApi Device Capabilities** (WAS Phase 15) renumbered and
    moved to the end: `docs/chat-phase18/PART_18_CODEAPI.md`.
  - **Phase 19 — Terminal Parity** ✅ **IMPLEMENTED & CI-GREEN
    (2026-08-31, `arena/01a056aa-codec`, run `33371114549`)** — on the
    owner's "Ok start phase 19 … also find other things Termux better and
    fix it". FIVE parts: the three planned bugs (19.1 reflow, 19.2
    integer-cell rendering, 19.3 frame-paced live output) **plus** 19.4
    Unicode column widths (CJK/emoji double-width, Indic cluster combining
    — CI round 2 caught missing Brahmic vowel signs) and 19.5 protocol/
    interaction parity (DA1/DA2 responses, OSC 52 clipboard write, xterm
    mouse reporting with Termux-style tap/swipe mapping, Ctrl+arrows,
    Copy-All/Share/Reset menu). ~50 new host tests. **Remaining: the
    owner's device recipes** (`docs/chat-phase19/PART_19_*.md` §5) and the
    owner's word to open/merge a PR. Clean-room throughout (public specs
    only).

- **CLEAN-ROOM LAW (owner, 2026-08-31) — replicate FEATURES, never COPY code.**
  When a phase clones another app (Spck's UX in 15–17, Termux's terminal
  quality in 19), build the same screens/files/flows/behaviors as **original
  code** in CodeC's own Kotlin/Compose, reusing CodeC's existing engines.
  - Closed-source apps (Spck): match visible behavior only (mockups + public
    docs); never decompile or lift assets/code.
  - GPL/copyleft (Termux is GPLv3): read public specs and learn the TECHNIQUE
    (VT100/xterm/ECMA-48, reflow, render cadence) and re-implement; **never
    paste GPL source** — it would relicense CodeC and breaks the no-`com.termux`
    invariant. Same rule is recorded in `docs/TERMINAL_PLAN.md` §B.10.
- **RESEARCH WHEN NEEDED (owner, 2026-08-31).** The phase docs are a starting
  point, not the final word. Before/while implementing, do additional research
  if anything is unclear (re-check the app's public docs, Material 3 / Compose
  patterns, public terminal specs, coroutine patterns, and the CodeC engine you
  build on), record findings as a short "Research notes" block in the part, and
  resolve open questions with a linked source before marking it done. Recorded
  in `docs/TERMINAL_PLAN.md` §B.11.

- **Unit tests:** `Build APK` CI runs `:app:assembleDebug` +
  `:app:testDebugUnitTest` + `:app:lintDebug` via the gradle-bootstrap bridge —
  a failing test or a lint ERROR fails the run. **The local sandbox has NO Java
  runtime; CI is the only test executor.** Write host-unit-testable, Android-free
  logic (the pattern for `TerminalBuffer`, `AnsiParser`, `GitManager`,
  `ServerPortDetector`, etc.) so CI can verify it.
- **Sandbox quirk (hit before):** the Arena sandbox can reset the checkout so
  HEAD sits on the base/main commit while the working files keep newer content.
  If `git log` disagrees with the files, realign WITHOUT touching the worktree:
  `git fetch origin <session-branch> && git reset --mixed FETCH_HEAD`, then
  `git diff --stat`. **Never `reset --hard`.**
- **Device APK:** artifact `CodeC-IDE` of the latest green `Build APK` run
  (`gh run download <run-id> -n CodeC-IDE`). The sandbox cannot install or test
  on device — the owner runs the recipes and pastes transcripts.

**NEXT UP (only on the owner's explicit instruction):**

1. **Merge PR #33** (docs for Phases 15–19) if the owner says so — it is
   docs-only and CI-green. Do not merge without the literal command.
2. **Phase 19 device round 3, then PR on the owner's word.** Round 1 fixed
   19.2 letter gaps (`fitSizeToGrid`, PART_19_2 §7.1). Round 2 (owner
   screenshots + `stty size` 32×60 vs Termux 39×71) fixed density & weight:
   default 12sp, bundled JetBrains Mono Medium/Bold (SIL OFL, notice in
   assets/licenses), 0.9 row-pitch factor (PART_19_2 §7.2). Round 3 =
   objective check: `stty size` ≈ 70×37 in CodeC, plus the §5 recipes on
   the new APK (`gh run download <latest-green> -n CodeC-IDE`); then the
   owner says the word to open/merge the PR.
3. **Then the next planned phase.** The owner picks:
   - **Phases 15 → 16 → 17 (Spck clone)** — run in order; reuse Phase 8/9/11/
     13/14 engines; UI/UX parity + gap fill, not a rewrite.
   - **Phase 18 (CodeCApi tail)** — last.
   Re-verify each plan against the current code before writing anything, follow
   the CLEAN-ROOM LAW and RESEARCH-WHEN-NEEDED rule, and record design
   decisions (D1, D2, …) in the part doc as you go.

**SELF-DISTRUST PROTOCOL — follow strictly:**

1. **Trust nothing from memory or training data about this repo.** Verify every
   fact against the actual files, `git` state, and GitHub Actions before acting.
2. **Evidence before hypothesis.** Reproduce/observe first (device output, CI
   log, file contents), *then* diagnose. Never commit a fix on a guess.
3. **Do not redo or re-debug anything marked COMPLETE / ✅** in the docs.
4. **Never trigger an expensive action** (CodeC package repo build ~60–100 min,
   release, destructive device test, force-push) without explicit user
   confirmation; check `gh run list` first.
5. **Honor the invariants (they are law):** no `.` on `PATH`; never
   `build-package.sh -I`; never overwrite `cc` or real ELF `bash` with a shim;
   keep the TCC link order with `-o` last; never use official `com.termux`
   packages or repositories; never bundle the bootstrap in the APK; repository
   metadata must stay signed (`signed-by=`, no `trusted=yes`); **clean-room —
   replicate features, never copy others' code.** Full list:
   `chat-phase1/SOLUTIONS.md`, `chat-phase2/SOLUTIONS.md`,
   `chat-phase3/PHASE3_PLAN.md`, `chat-phase3/REPOSITORY_SIGNING.md`,
   `docs/TERMINAL_PLAN.md` §B and §J.
6. **One PR at a time, from the current state.**
7. **NEVER create, open, or merge a PR, and never merge/push to `main`,
   without the owner explicitly commanding it in chat.** Coding, committing to
   the session branch (`arena/*`), and pushing that branch are fine. If the
   user's message does not literally say to open/merge a PR, don't — report
   state and wait.
8. **Know the sandbox limits:** the agent sandbox reaches `api.github.com`
   only — no CI-log/release/artifact downloads, no on-device testing. Gradle
   build/lint/test only happen on CI. For logs use the check-runs annotations
   API; the log-zip endpoint is blocked.

**ORDER OF WORK:**

1. Verify current state (`gh pr list`, `git status`, `gh run list`,
   `gh release list`) before acting.
2. Phases 3–14 are closed (PRs #15/#23/#25/#26/#27/#28/#29/#30/#31/#32 merged;
   `main` at `0b591e2`). Phases 15–19 are spec'd (docs only, PR #33 open). If
   the owner commands a phase, re-verify its plan against current code, then
   implement it host-testably, commit + push the session branch, and let CI
   run. Never open/merge a PR without the owner's explicit word.
3. A part is complete only when its "Exit condition" is met and verified
   (device evidence from the owner for device gates), not merely when code is
   written.
4. Keep `docs/JOURNEY.md`, `docs/NEXT_STEPS.md`, `docs/TERMINAL_PLAN.md` and
   this `prompt.md` updated as each gate closes — the next chat trusts only what
   is written there and verified in git/CI.

**Before each change, state:** what you are changing, which Phase/Part and exit
condition it serves, and which invariant (if any) it could affect.
