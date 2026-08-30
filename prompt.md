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

- **Phases 3–9.2 and the Package Hub (Phase 10) are all COMPLETE and
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
  - **Phase 11 (Output Panel & Integrated Run) — IMPLEMENTED 2026-08-30 on
    `arena/01a0508b-codec`** (owner: "Ok start phase 11"). Split-screen
    Output Panel + draggable splitter; RUN ▶ builds/executes via the real `cc`
    toolchain (project.json build/run, or `cc <file> -o a.out && ./a.out` for
    single files) with streaming, Stop, Copy/Clear, auto-scroll, clickable
    `file:line:col:` error lines → editor jump + Phase 9 squiggles, and an
    Open-in-Terminal escape hatch. New: `ExecutionRunner` (JVM-tested),
    `OutputLineParser`, `OutputPanelView`; `TerminalHandoff.compileParts`/
    `projectRunParts`; legacy in-editor `runCode`/`CompilerService` pipeline
    removed (D1 — editor RUN now matches the terminal's `cc`; the Settings
    "Compiler Engine" picker's editor effect is superseded, flagged as a
    follow-up). Device rounds in progress: single-file run ✅ + error display
    ✅ (owner transcripts); one-tap **Apply fix** ("Add missing ;" button +
    TCC no-column squiggle parsing + brace-line fallback) shipped on the
    owner's "write a code to apply" (commit `bc4efea`, CI `33290932427`).
    Remaining gates: tap-jump + Apply-fix device re-test (round 3: "1.
    Working"), the interactive-`scanf` timeout got honest wording + an
    always-visible Open-in-Terminal escape hatch (D7, commit `bfed3b4`), then
    the §4/§6.3 recipe close-out.
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

1. **Finish Phase 11** — CI run on `arena/01a0508b-codec` + the owner's device
   recipe (`docs/chat-phase11/PART_11_OUTPUT.md` §4/§6.3) must pass before the
   phase closes; then (on the owner's word) open the PR from this branch.
2. **Phase 12 — Python repo build** (`[repo-build]`, the ONE planned CI package
   build ~1–2h, plus multi-language highlight/autocomplete): `docs/chat-phase12/`.
3. Phases 13 (GitHub/Git UI), 14 (mixed-language servers + webview), 15
   (CodeCApi device capabilities): `docs/chat-phase13/..15/`.

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
2. If Phase 11 on `arena/01a0508b-codec` is not yet closed (CI or device gate
   pending): continue it — do not redo the implementation, do not start Phase
   12. If Phase 11 is closed and the owner commands a next phase, pick Phase
   12 from `docs/chat-phase12/` and re-verify its plan against current code
   before implementing (it includes the expensive ~1–2h repo build — explicit
   approval required before dispatching).
3. A part is complete only when its "Exit condition" is met and verified
   (device evidence from the owner for device gates), not merely when code is
   written.
4. Keep `docs/JOURNEY.md`, `docs/NEXT_STEPS.md` and this `prompt.md` updated
   as each gate closes — the next chat trusts only what is written there and
   verified in git/CI.

**Before each change, state:** what you are changing, which Part and exit
condition it serves, and which invariant (if any) it could affect.
