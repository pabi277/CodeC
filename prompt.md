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

**WHERE THINGS STAND (2026-08-28):**

- **Phase 3 is device-acceptance complete.** Parts A, B, C, and D have all met
  their exit conditions and are verified on real Android hardware (PR #15).
  - **Do not redo, re-debug, or re-run any of the above.** They are closed.
- **Phase 4 is COMPLETE (Parts 4.1–4.8, device-verified 2026-08-26).**
  4.1 storage, 4.2 install UX, 4.3 trust/channel, 4.4 settings parity, 4.5
  catalog build, 4.6 publish + device gate, 4.7 clipboard over the reusable
  `CodeCApi` bridge, 4.8 notifications (`codec-notify`).
- **Phase 5 (5.1 KI fixes / 5.2 web preview / 5.3 capability batch) ✅ COMPLETE, merged PR #23 (2026-08-26).** Evidence in `docs/chat-phase5/`. See `docs/PHASE5_ROADMAP.md`.
- **Phase 6 (Terminal UX) & Package Hub (Phase 10) ✅ IMPLEMENTED (2026-08-28, branch `arena/01a0482c-codec`).**
  - **Terminal UX:** Cutout / landscape safe area, configurable extra-keys + custom macros, wake-lock during active jobs, URL tap-to-open, VT BEL visual pulse + vibration, dynamic title, selection copy + word boundary lookup, monospace cell-by-cell drawing (no cursor drift), and smooth 60fps pinch-to-zoom (PTY resize decoupled from continuous in-flight touch gestures).
  - **Package & Command Hub (`ModulesScreen`):** 1-tap package installation & execution (`pkg install -y <pkg>`), curated 25+ tool catalog (Compilers, Editors, Languages, CLI tools, Compression), live `$PREFIX/bin` installation status detection (`INSTALLED ✓` / `AVAILABLE`), 1-tap quick actions (`pkg update`, `pkg upgrade -y`, `codec-setup-storage`, `pkg status`, `pkg heal`, `pkg repair`), custom command runner, and line discipline (`\r`) terminal dispatch.
  - CI Build: `33177852501` passed green (2m53s).
- **Next Work:** Phase 7 (multi-terminal / session manager) or Phase 8 (projects / folder tree / run configuration). All client-only except Phase 12 (Python repo build). No PR/merge without explicit owner command.
- An optional x86_64 repeat of the Part D clean-device test was not run (no
  x86_64 device was available); this does not block calling Phase 3 complete
  on the tested aarch64 architecture (see `docs/chat-phase3/PHASE3_PLAN.md` §5 M3).

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
   `gh release list`). Do not reopen completed parts.
2. Phase 4 is complete; next work is Phase 5 (not started). Confirm with the
   user which candidate area from `docs/PHASE5_ROADMAP.md` they want, and
   decide/write down that part's open technical questions before coding
   (record it in `docs/chat-phase5/`).
3. **No PR / no merge without an explicit owner command** (see rule 7).
   Work on the session branch; push it if useful; report and wait.
4. A part is complete only when its **"Exit condition"** is met and verified,
   not merely when code is written.

**Before each change, state:** what you are changing, which Part and exit
condition it serves, and which invariant (if any) it could affect.
