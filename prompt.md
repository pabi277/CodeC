# prompt.md — paste this into the next chat

> Copy **everything between the two `---` lines below** as the first message of
> a new chat. Do not edit it. It forces the next agent to verify before acting,
> to trust the repo over its own assumptions, and to continue CodeC in the
> right order without redoing or breaking anything.

---

Read `docs/JOURNEY.md` and `docs/NEXT_STEPS.md` first, **before doing anything
else**, then report back what you found and the current git/PR/CI state before
making any change.

You are continuing **CodeC** (an Android C IDE). Each chat session gets its own
`arena/*` session branch — verify the actual branch with `git status` instead
of assuming one.

**WHERE THINGS STAND (2026-08-30):**

- **Phases 3–11 (incl. the Package Hub, Phase 10) are all COMPLETE and
  device-accepted. Do not redo, re-debug or "improve" any of them** unless the
  identical symptom reappears AND you have regression evidence:
  - Phase 3 (repo/bootstrap/signing) ✅ device-complete (PR #15). Parts A–D.
  - Phase 4 (storage, install UX, trust channel, settings parity, catalog,
    clipboard, notifications) ✅ device-verified 2026-08-26.
  - Phase 5 (KI fixes, web preview, capabilities, file share) ✅ merged PR #23.
  - Phase 6 (terminal UX) + Phase 10 (Package & Command Hub) ✅ merged PR #25.
  - Phase 7 (multi-terminal sessions) ✅ merged PR #26, device-verified.
  - Phase 8 (Projects & file tree, ZIP round trip, web run) ✅ merged PR #27
    at `348eb03` after the owner confirmed the export → re-import round trip.
  - **Phase 9 (Editor Foundation: tabs, undo/redo, find/replace, format,
    bracket matching, compiler squiggles, status bar) + device rounds 9.1
    (in-editor file drawer, Save-to-project, per-file Run-in-terminal, loopback
    HTTP preview server) and 9.2 (simpler toolbar, open-folder-from-editor
    sheet, single files as a first-class context)** ✅ CI green
    (`33239651690`, `33241237168`, `33243620762`), owner device rounds passed
    ("Good working" → "Yes working"), closed 2026-08-29 by the owner's
    finalization instruction → **PR #28 MERGED to `main` at `961e942`** — main
    has everything through Phase 9.2. Records: `docs/chat-phase9/` (device
    recipes = regression checklist).
  - **Phase 11 (Output Panel & Integrated Run) — ✅ COMPLETE & DEVICE-ACCEPTED
    2026-08-30, MERGED via PR #29 at `771f58f`** (owner: "Ok start phase 11";
    CI green through `33293358085`; all device rounds passed; owner's final
    word: "All of the check passed"; merged on the owner's command "Please
    merge the pull request"). Split-screen Output Panel + draggable
    splitter; RUN ▶ builds/executes via the real `cc` toolchain (project.json
    build/run, or `cc <file> -o a.out && ./a.out` for single files) with
    streaming, Stop, Copy/Clear, auto-scroll, clickable `file:line:col:` error
    lines → editor jump + Phase 9 squiggles, and an Open-in-Terminal escape
    hatch. **Interactive runs on a real PTY** (D9, `InteractiveRunSession`
    over PtyNative/PtySession: per-prompt output, one input per scanf, echo,
    no timeout — Stop kills; piped fallback); one-tap **Add missing ;** Apply
    fix (D6, incl. TCC no-column parsing + brace-line fallback); honest
    timeout wording (D7); panel input row + terminal escape (D8). New:
    `ExecutionRunner`, `OutputLineParser`, `OutputPanelView`,
    `InteractiveRunSession` (+`PtyLineBuffer`, `decodeExitStatus`);
    `TerminalHandoff.compileParts`/`projectRunParts`; ~30 new/updated host
    unit tests. Legacy in-editor `runCode`/`CompilerService` pipeline removed
    (D1 — editor RUN now matches the terminal's `cc`; the Settings "Compiler
    Engine" picker's editor effect is superseded, flagged as a follow-up).
    **PR #29 MERGED to `main` at `771f58f` (2026-08-30).**
  - **Phase 12 (Multi-Language Support, Python & Code Intelligence) ✅
    COMPLETE & DEVICE-ACCEPTED — PR #30 MERGED to `main` at `260d8b6`
    (2026-08-30). Main has everything through Phase 12.** Multi-language
    highlighter, autocomplete popup, python/python-pip in the repo (device:
    "Now python is solved"), python RUN path. See item 1 of NEXT UP and
    `docs/chat-phase12/`.
  - **Phase 13 (GitHub & Git Integration) 🔨 IMPLEMENTED & CI-GREEN on
    this session branch `arena/01a053b3-codec` (2026-08-30; `Build APK`
    `33326161083` green incl. all 37 new tests; the first CI round caught
    two real bugs, fixed `501b6f2`)** — GitManager engine, Source Control
    pane, Clone from GitHub, Settings GitHub card. Awaiting device rounds
    (`PART_13_GITHUB.md` §7) + the owner's word to open the PR. See item 2
    of NEXT UP and `docs/chat-phase13/`.
- **Unit tests:** `Build APK` CI runs `:app:testDebugUnitTest` **and**
  `:app:lintDebug` inside the assemble chain — a failing test or a lint ERROR
  fails the run (Phase 9 caught real API-compat bugs this way: `SpanStyle.drawStyle`
  does not exist in Compose BOM 2024.09.00; `ProcessBuilder.redirect*(File)`
  needs API 26 vs minSdk 24 → use daemon-thread stream pumps). The local
  sandbox has **no Java runtime**; CI is the only test executor. Run the
  editor suites locally-purposely via:
  `./gradlew :app:testDebugUnitTest --tests 'com.codeci.ide.EditorUndoManagerTest'`
  (…`FindReplaceTest`, `BracketMatcherTest`, `CodeFormatterTest`,
  `CompilerDiagnosticsTest`, `TerminalHandoffTest`, `WebPreviewServerTest`).
- **Sandbox quirk (hit twice):** the Arena sandbox can reset the checkout so
  HEAD sits on the base/main commit while the working files keep newer
  content. If `git log` disagrees with the files, realign WITHOUT touching
  the worktree: `git fetch origin <session-branch> && git reset --mixed
  FETCH_HEAD`, then `git diff --stat` to confirm what actually differs.
  Never `reset --hard`.
- **Device APK:** artifact `CodeC-IDE` of the latest green `Build APK` run
  (`gh run download <run-id> -n CodeC-IDE`). The sandbox cannot install or
  test on device — the owner runs recipes and pastes transcripts.

**NEXT UP (only on the owner's explicit instruction):**

1. **Phase 11 and Phase 12 are both COMPLETE, DEVICE-ACCEPTED, and MERGED**
   (PR #29 → `main` at `771f58f`; **PR #30 (Phase 12) → `main` at `260d8b6`,
   2026-08-30**). Nothing pending on either. Phase 12's record:
   implemented on `arena/01a05221-codec` (python + python-pip in
   `CODEC_REPOSITORY_PACKAGES` with tk/tkinter (X11) recipe override,
   multi-language highlighter, autocomplete popup, python run path, 27 host
   tests; `[repo-build]` published python 3.14.6-1 + python-pip 26.2.1;
   device recipe FULLY PASSED — owner: "Now python is solved" / "Worked
   properly" / "Both working"), then merged as PR #30 on the owner's
   explicit command.
2. **Phase 13 (GitHub & Git Integration) is IMPLEMENTED on
   `arena/01a053b3-codec`** (2026-08-30, on the owner's "Start phase 13"):
   `GitManager.kt` Android-free engine over `$PREFIX/bin/git` (argv-list
   ProcessBuilder, no shell; porcelain `status -b` parser; timeouts),
   `GIT_ASKPASS`-based token transport with `GitRedactor` scrubbing on every
   output path (token never in argv/.git-config/terminal env/logs; stored
   app-private in DataStore), `GitDiff.kt` Kotlin line diff, Source Control
   bottom sheet (`GitControlView.kt`/`GitControlViewModel`), Files ⋮ →
   Clone from GitHub, Settings GitHub Account card, 37 new host tests
   (`GitStatusParserTest`, `DiffEngineTest`, `GitManagerTest`). Plan +
   design decisions D1–D7 + device recipe: `docs/chat-phase13/`. Remaining:
   CI green + owner device rounds (`PART_13_GITHUB.md` §7) + the owner's
   word to open the PR.
3. Phases 14 (mixed-language servers + webview) and 15 (CodeCApi device
   capabilities): `docs/chat-phase14/`, `docs/chat-phase15/`.

**SELF-DISTRUST PROTOCOL — follow strictly:**

1. **Trust nothing from memory or training data about this repo.** Verify every
   fact against the actual files, `git` state, and GitHub Actions before acting.
2. **Evidence before hypothesis.** Reproduce/observe first (device output, CI
   log, file contents), *then* diagnose. Never commit a fix on a guess.
3. **Do not redo or re-debug anything marked COMPLETE / ✅** in the docs.
4. **Never trigger an expensive action** (CodeC package repo build ~60–100
   min, release, destructive device test, force-push) without explicit user
   confirmation; check `gh run list` first.
5. **Honor the invariants (they are law):** no `.` on `PATH`; never
   `build-package.sh -I`; never overwrite `cc` or real ELF `bash` with a shim;
   keep the TCC link order with `-o` last; never use official `com.termux`
   packages or repositories; never bundle the bootstrap in the APK; repository
   metadata must stay signed (`signed-by=`, no `trusted=yes`). Full list:
   `chat-phase1/SOLUTIONS.md`, `chat-phase2/SOLUTIONS.md`,
   `chat-phase3/PHASE3_PLAN.md`, `chat-phase3/REPOSITORY_SIGNING.md`.
6. **One PR at a time, from the current state.**
7. **NEVER create, open, or merge a PR, and never merge/push to `main`,
   without the owner explicitly commanding it in chat.** Coding, committing to
   the session branch (`arena/*`), and pushing that branch are fine. If the
   user's message does not literally say to open/merge a PR, don't — report
   state and wait.
8. **Know the sandbox limits:** the agent sandbox reaches `api.github.com`
   only — no CI-log/release/artifact downloads, no on-device testing. Gradle
   build/lint/test only happen on CI. For logs use the check-runs annotations
   API (`/repos/:owner/:code/actions/...` / `/check-runs/<id>/annotations`);
   the log-zip endpoint is blocked.

**ORDER OF WORK:**

1. Verify current state (`gh pr list`, `git status`, `gh run list`,
   `gh release list`) before acting.
2. Phases 3–12 are closed (PR #15/#23/#25/#26/#27/#28/#29/#30 merged; main
   at `260d8b6` is current). If the owner commands the next phase, pick
   Phase 14 from `docs/chat-phase14/` — or continue Phase 13 device rounds
   from `docs/chat-phase13/` — and re-verify the plan against current code
   before implementing. Never open/merge a PR without the owner's explicit
   word.
3. A part is complete only when its "Exit condition" is met and verified
   (device evidence from the owner for device gates), not merely when code is
   written.
4. Keep `docs/JOURNEY.md`, `docs/NEXT_STEPS.md` and this `prompt.md` updated
   as each gate closes — the next chat trusts only what is written there and
   verified in git/CI.

**Before each change, state:** what you are changing, which Part and exit
condition it serves, and which invariant (if any) it could affect.
