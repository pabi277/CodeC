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
- **`main` was at `b869ce6`** (PR #34 merged 2026-08-31 09:55Z — Phase 19 on
  top of the `8dd961a2` = PR #33 docs state). Post-merge `Build APK`
  `33380041937` green. **On 2026-08-31 the owner also merged the Phase 15/16
  + device-round branch (`arena/01a057e0-codec`, branch tip `4db8c72` or
  later) into `main` — verify the actual tip with `git log` before acting.**

- **Phases 15–17 (Spck clone) are IMPLEMENTED on that branch — CI green +
  three device rounds done.** They live under `docs/chat-phase15/`
  (README + PART_15/16/17 + `mockups/`):
  - **Phase 15 — Projects Hub & Unified Import:** implemented, device round 1
    (clone re-detect + Packages tab fixes, `83ba499`) AND round 2 (mockup-exact
    re-skin of hub, clone dialog, import sheet — `95cd554`) done.
  - **Phase 16 — Spck-style Editor Shell:** implemented, CI-green
    (`33388547817`), device round 1 done (`__pycache__` git fix, `435c5f4`),
    mockup-exact re-skin done (single top bar, gutter divider, ghost RUN,
    keycap-style keys row, drawer, status bar).
  - **Device round 3 (2026-08-31, owner: "remove the home button … terminal
    will be in the middle … open where the user left off … auto save … .out
    files … run button even for html"):**
    - Bottom bar is now **Projects · Editor · Terminal · Packages · Settings**
      — Home tab + `HomeScreen` deleted, Packages tab restored to the bar
      (undoing round 2's Home-screen button), **Terminal dead-center**.
    - **Launch-restore:** the last opened project file (or active tab) is
      persisted (`ui/projects/EditorLaunchState.kt`) and is the app's start
      destination; first launch / stale entry → Projects hub.
    - **Editor autosave:** 2 s debounced save after any edit (type/undo/redo/
      format) + immediate flush when leaving the editor (`EditorViewModel`
      `scheduleAutoSave`/`flushAutoSave`, `EditorScreen` dispose hook).
    - **Build outputs stay out of git:** `*.out/*.o/*.obj/*.exe/*.class`,
      `bin/`, `dist/`, `build/`, `target/`, `node_modules/`, venvs are
      repo-locally excluded via `.git/info/exclude` (`BuildArtifactIgnore`,
      user's `.gitignore` untouched) at git refresh / commit & push / project
      open / RUN — and already-tracked artifacts get `git rm --cached`
      (`GitManager.trackedFiles`/`rmCached`) so they stop traveling at push.
    - **RUN ▶ is the HTML preview:** an open `.html` file makes RUN ▶ save +
      open Web Preview; the separate "Preview" overflow item is deleted.
  - **Phase 17 — Source Control:** now **fully IMPLEMENTED on
    `arena/01a05878-codec` (2026-08-31)** — the mockup-exact SC sheet +
    in-tree M/A/D/? letters + tap-to-diff + **per-file +/− stage toggle**
    (`GitManager.stageFile`/`unstageFile`) from the re-skin, **plus** the
    Switch Branch dialog (checkout/stash/auto-restore + bonus New branch) and
    the merge-conflict UI (Conflicts group, purple `U`, Mark Resolved, commit
    blocked). Both "coming soon" toasts are gone. Record + decisions D1–D8:
    `docs/chat-phase15/PART_17_SOURCE_CONTROL.md` §6.1.
    **Device round 1 (2026-08-31) — two owner-reported bugs FIXED:** (a) a
    branch created in the app had no upstream, so every push died with
    `fatal: The current branch X has no upstream branch` → `pushHandlingUpstream()`
    now runs `git push --set-upstream <remote> HEAD` when the status branch
    line has no `...origin/x`, plain `git push` otherwise; (b) a failed push
    was indistinguishable from a successful one (a commit clears the change
    list) → the sheet now reports **"Committed locally ✓ — NOT pushed: …"**,
    keeps the failure sticky, shows an amber **"N commit(s) not pushed yet"**
    row with a **PUSH** retry (also for a never-published branch), the
    Projects card shows an amber **↑N** badge, and git ops re-read status
    after failures. CI green `33421815293` @ `1c01f84`. Record:
    `docs/chat-phase15/PART_17_SOURCE_CONTROL.md` §6.2. **Do not redo these.** **Open gate: the
    owner's §4 device recipe steps 5–8** — do not re-implement; only fix on
    device evidence.
  - **Vector-API compile saga (round 2/3 of the re-skin, resolved
    2026-08-31, `253201e`):** the resolved `ui-graphics` is far newer than the
    BOM number suggests — the old string-path `addPath` API is gone. Verified
    API (recorded in `ui/components/SpckIcons.kt` header):
    `addPath(pathData: List<PathNode>, …, fill: Brush?, …, stroke: Brush?,
    strokeLineWidth, strokeLineCap, strokeLineJoin, …)`; `PathNode` lives in
    `androidx.compose.ui.graphics.vector` (`MoveTo/LineTo/HorizontalTo/
    VerticalTo/CurveTo/ArcTo/Close`); `Color` is NOT a `Brush` (wrap in
    `SolidColor`); `DrawScope.drawLine` endpoint is `end` (not `stop`);
    bottom-bar inset is `navigationBarsPadding()`. Do NOT reintroduce the old
    string-path `fillColor`/`Stroke(width=…)` calls.
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
    Copy-All/Share/Reset menu). ~70 new host tests total. **DEVICE-ACCEPTED
    (2026-08-31, owner's final word: "All ok now") after 4 device rounds:**
    R1 letter gaps → `fitSizeToGrid`; R2 density/weight (`stty size` 32×60 vs
    Termux 39×71) → 12sp default + bundled JetBrains Mono Medium/Bold (SIL
    OFL, notice in `assets/licenses/`) + 0.9 row pitch; R3 lag/steppy
    scroll/IME misses → run-batched drawing, stable gesture keys, sub-row
    smooth scrolling, IME retry; R4 = owner PASS. Clean-room throughout
    (public specs only). PR #34 (from `arena/01a056aa-codec`, CI-green)
    **MERGED to `main` at `b869ce6` 2026-08-31 — Phase 19 CLOSED; do not
    revisit unless an identical symptom reappears with regression evidence.**
    Postmortems:
    `docs/chat-phase19/PART_19_2_RENDERING.md` §7.1–7.2, `PART_19_3_LIVE_OUTPUT.md` §9.

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

1. **Phase 17 — device gate.** The code is written and **CI-green**
   (Switch Branch + merge-conflict UI, `arena/01a05878-codec`, `Build APK`
   `33417811422` @ `3a2846f`, +33 host tests); what's left is the owner
   running `docs/chat-phase15/PART_17_SOURCE_CONTROL.md` §4 steps 5–8 on the
   device APK (artifact `CodeC-IDE` of that run) and reporting back. Fix only
   on device evidence; do not re-implement.
2. **Phase 18 — CodeCApi Device Capabilities** (the tail phase):
   `docs/chat-phase18/PART_18_CODEAPI.md`.
3. Phases 15–16 remain open to **owner device feedback** on the merged build
   (the bar/autosave/launch-restore/.out/RUN-html changes of device round 3
   have not had a dedicated device pass yet — the owner is reviewing).
   Re-verify any plan against the current code before writing anything,
   follow the CLEAN-ROOM LAW and RESEARCH-WHEN-NEEDED rule, and record design
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
2. Phases 3–14 + 19 are closed (PRs #15/#23/#25/#26/#27/#28/#29/#30/#31/#32/#34
   merged). Phases 15–16 are implemented + device-rounded and merged
   (2026-08-31, from `arena/01a057e0-codec`); Phase 17 is partial (SC sheet +
   stage toggle done; Switch Branch + conflicts pending); Phase 18 is spec'd.
   If the owner commands a phase, re-verify its plan against current code, then
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
