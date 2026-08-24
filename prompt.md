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

**WHERE THINGS STAND (2026-08-24):**

- **Phase 3 is device-acceptance complete.** Parts A, B, C, and D have all met
  their exit conditions and are verified on real Android hardware; the
  Part C+D evidence and docs landed in `main` via **PR #15** (merged
  2026-08-24). Part D's final clean-device gate (full uninstall/reinstall
  against the published, key-seeded `userland-v2-dev` archive) passed on
  2026-08-24. See `docs/chat-phase3/PHASE3_DEVICE_ACCEPTANCE.md` §8 and
  `docs/JOURNEY.md` §5f for full evidence.
  - **Do not redo, re-debug, or re-run any of the above.** They are closed.
- **Phase 4 Parts 4.1–4.4 are done** and merged: #16 (4.1 shared-storage
  access), #17 (4.2 install-confirmation UX + 4.3 trust/channel indicator UX,
  including 4.4 settings/theme parity). Device-verification records are in
  `docs/chat-phase4/`.
- **Next work is Phase 4 Parts 4.5–4.7**, planned in
  `docs/PHASE4_ROADMAP.md`. **Part 4.5 (expanded package catalog, round 2) is
  IN PROGRESS**: the technical decisions are recorded in
  `docs/chat-phase4/PART_4_5_CATALOG_EXPANSION.md` (15 new repository roots,
  repository-only scope, fail-loud git/bash recipe overrides, reviewed
  `bat`/`util-linux` maintainer-script entries) and code + host tests are
  staged on the session branch. Its remaining exit condition is the
  `workflow_dispatch` CI build from `main` — an expensive action that needs
  explicit user confirmation before dispatching. Parts 4.6 (publish + device
  gate) and 4.7 (Android-integration slice) are not started.
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
7. **Know the sandbox limits:** the agent sandbox reaches `api.github.com`
   only — it cannot download CI logs, release assets, or workflow artifacts
   directly, and cannot perform on-device testing. Device tests and any
   log-pull-dependent debugging need the user in Termux/on-device; give exact
   copy-paste commands and wait for the transcript.

**ORDER OF WORK:**

1. Verify current state (`gh pr list`, `git status`, `gh run list`,
   `gh release list`). Do not reopen completed parts.
2. Continue the current part from its record in `docs/chat-phase4/` (as of
   2026-08-24: Part 4.5 — see `PART_4_5_CATALOG_EXPANSION.md` "Continue
   here"). Confirm with the user which numbered part of
   `docs/PHASE4_ROADMAP.md` they want, and decide/write down that part's open
   technical questions before coding.
3. A part is complete only when its **"Exit condition"** is met and verified,
   not merely when code is written.

**Before each change, state:** what you are changing, which Part and exit
condition it serves, and which invariant (if any) it could affect.
