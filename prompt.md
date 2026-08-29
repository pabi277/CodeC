# prompt.md — paste this into the next chat

> Copy **everything between the two `---` lines below** as the first message of
> a new chat. Do not edit it. It forces the next agent to verify before acting,
> to trust the repo over its own assumptions, and to continue CodeC in the
> right order without redoing or breaking anything.

---

Read `docs/JOURNEY.md` and `docs/NEXT_STEPS.md` first, **before doing anything
else**, then report back what you found and the current git/PR/CI state before
making any change.

You are continuing **CodeC** (an Android C IDE). PRs #1–#17 are **merged to
`main`** (PR #14 was closed unmerged, superseded by PR #15). Each chat session
gets its own `arena/*` session branch — verify the actual branch with
`git status` instead of assuming one.

**WHERE THINGS STAND (2026-08-29):**

- **Phase 3 is device-acceptance complete.** Parts A, B, C, and D have all met
  their exit conditions and are verified on real Android hardware (PR #15).
  - **Do not redo, re-debug, or re-run any of the above.** They are closed.
- **Phase 4 is COMPLETE (Parts 4.1–4.8, device-verified 2026-08-26).**
  4.1 storage, 4.2 install UX, 4.3 trust/channel, 4.4 settings parity, 4.5
  catalog build, 4.6 publish + device gate, 4.7 clipboard over the reusable
  `CodeCApi` bridge, 4.8 notifications (`codec-notify`).
- **Phase 5 (5.1 KI fixes / 5.2 web preview / 5.3 capability batch) ✅ COMPLETE, merged PR #23 (2026-08-26).** Evidence in `docs/chat-phase5/`. See `docs/PHASE5_ROADMAP.md`.
- **Phase 6 (Terminal UX) & Package Hub (Phase 10) ✅ IMPLEMENTED (2026-08-28, merged PR #25).**
  - **Terminal UX:** Cutout / landscape safe area, configurable extra-keys + custom macros, wake-lock during active jobs, URL tap-to-open, VT BEL visual pulse + vibration, dynamic title, selection copy, smooth terminal rendering, and pinch-to-zoom.
  - **Package & Command Hub:** 1-tap package installation/execution, curated catalog, live `$PREFIX/bin` status detection, quick actions, custom command runner, and terminal line discipline.
  - CI Build: `33177852501` passed green.
- **Phase 7 (multi-terminal sessions) ✅ COMPLETE (2026-08-28, PR #26, device-verified).**
  `TerminalSessionManager` (N concurrent PTY sessions, monotonic numbering,
  adjacent-selection close, auto-recreate on last close, 8-session cap),
  dropdown switcher, per-session CodeCApi collectors, wake lock while any
  session is alive, and active-session routing. Device evidence is recorded in
  `docs/chat-phase7/PART_7_MULTI_TERMINAL.md`.
- **Phase 8 project implementation is complete in PR #27, but its final merge gate is still explicit.**
  The branch delivers private project storage, hierarchical file trees,
  breadcrumbs, project run configuration, SAF folder/file transfer,
  extension-agnostic ZIP import/export, central-directory ZIP recovery,
  project-aware terminal/editor integration, Projects overflow actions,
  refresh/collapse-all, and HTML/HTM Set as default run.
  The owner has confirmed on device: ZIP extraction with HTML, CSS, JS, C, and
  Python files; terminal project-folder listing; refresh/collapse; and HTML
  default-run preview. The export → import-as-a-different-project round trip
  has not yet been explicitly reported and must be confirmed before calling
  the Phase 8 acceptance gate fully closed. See `docs/chat-phase8/`.
- **Phase 9 (Editor Foundation) is the next planned client-only phase after the Phase 8 round-trip gate.** It covers multi-file tabs/navigation, undo/redo, find/replace, formatting, diagnostics, bracket matching, and line/column status. See `docs/chat-phase9/PART_9_EDITOR.md`.
- **PR #27 is MERGED to `main` at `348eb03` (2026-08-29).** The Phase 8
  acceptance gate is CLOSED: the owner confirmed the export → re-import-as-a-
  different-project round trip on device 2026-08-29. **Phase 9 (Editor
  Foundation) is implemented on `arena/01a04c1c-codec` with CI green
  (run `33239651690`)** — its remaining gate is the owner's on-device run of
  the `docs/chat-phase9/PART_9_EDITOR.md` §4 recipe. All client-only except
  Phase 12 (Python repo build).
- CI reality check (supersedes older notes): `Build APK` runs on every branch
  push and **does execute `:app:testDebugUnitTest` and `:app:lintDebug`**
  (a unit-test or lint-error regression fails the run — Phase 9 caught real
  bugs this way). The local sandbox has no Java runtime; CI is the test
  executor.

**SELF-DISTRUST PROTOCOL — follow strictly:**

1. **Trust nothing from memory or training data about this repo.** Verify every
   fact against the actual files, `git` state, and GitHub Actions before acting.
2. **Evidence before hypothesis.** When something fails, reproduce/observe it
   first (device output, CI log, file contents), *then* diagnose. Never commit a
   fix on a guess.
3. **Do not redo or re-debug anything marked COMPLETE / ✅ in the docs.** Parts
   A–D are closed with device evidence. Do not "fix" them unless the identical
   symptom reappears AND you have evidence it is a regression, not a
   known-closed item.
4. **Never trigger an expensive action (package rebuild, release, destructive
   device test, force-push) without explicit user confirmation.** The "CodeC
   package repository" workflow takes ~60–100 minutes — check `gh run list`
   for an existing/recent run first.
5. **Honor the invariants (they are law):** no `.` on `PATH`; never
   `build-package.sh -I`; never overwrite `cc` or real ELF `bash` with a shim;
   keep the TCC link order with `-o` last; never use official `com.termux`
   packages or repositories; never bundle the bootstrap in the APK; repository
   metadata must stay signed (`signed-by=`, no `trusted=yes`). The full list is
   in `chat-phase1/SOLUTIONS.md`, `chat-phase2/SOLUTIONS.md`, `chat-phase3/PHASE3_PLAN.md`,
   and `chat-phase3/REPOSITORY_SIGNING.md`.
6. **One PR at a time, from the current state.**
7. **NEVER create, open, or merge a PR, and never merge/push to `main`, without the owner explicitly commanding it in chat.** Coding, committing to the session branch (`arena/*`), and pushing that branch are fine; PR creation and any merge are NOT. If the user's message does not literally say to open/merge a PR, do not do it — end the turn by reporting state and waiting.
8. **Know the sandbox limits:** the agent sandbox reaches `api.github.com`
   only — it cannot download CI logs, release assets, or workflow artifacts
   directly, and cannot perform on-device testing. Device tests and any
   log-pull-dependent debugging need the user in Termux/on-device; give exact
   copy-paste commands and wait for the transcript.

**ORDER OF WORK:**

1. Verify current state (`gh pr list`, `git status`, `gh run list`,
   `gh release list`) before acting.
2. Do not redo completed Phases 3–7 or Package Hub/Phase 10 unless identical
   regression evidence appears.
3. Close the Phase 8 acceptance gate only after the owner confirms export,
   re-import as a different project, complete tree preservation, editor opening,
   and project-relative terminal behavior. Do not infer device acceptance from
   APK assembly or source tests.
4. After Phase 8 is fully accepted, Phase 9 is next: implement the planned
   editor foundation in `docs/chat-phase9/PART_9_EDITOR.md`.
5. No PR / no merge without an explicit owner command. Work on the session
   branch only; never push or merge to `main`.
6. A part is complete only when its "Exit condition" is met and verified, not
   merely when code is written.

**Before each change, state:** what you are changing, which Part and exit
condition it serves, and which invariant (if any) it could affect.
