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

**WHERE THINGS STAND (2026-08-26):**

- **Phase 3 is device-acceptance complete.** Parts A, B, C, and D have all met
  their exit conditions and are verified on real Android hardware; the
  Part C+D evidence and docs landed in `main` via **PR #15** (merged
  2026-08-24). Part D's final clean-device gate (full uninstall/reinstall
  against the published, key-seeded `userland-v2-dev` archive) passed on
  2026-08-24. See `docs/chat-phase3/PHASE3_DEVICE_ACCEPTANCE.md` §8 and
  `docs/JOURNEY.md` §5f for full evidence.
  - **Do not redo, re-debug, or re-run any of the above.** They are closed.
- **Phase 4 is COMPLETE (Parts 4.1–4.8, device-verified 2026-08-26).**
  4.1 storage, 4.2 install UX, 4.3 trust/channel, 4.4 settings parity, 4.5
  catalog build, 4.6 publish + device gate, 4.7 clipboard over the reusable
  `CodeCApi` bridge, 4.8 notifications + the `POST_NOTIFICATIONS` runtime
  path (`codec-notify`; owner-confirmed tap opens CodeC). Per-part records
  are in `docs/chat-phase4/`; the (now completed) Phase 4 roadmap is
  `docs/chat-phase4/PHASE4_ROADMAP.md`. **Do not redo any of it.**
- **Next work is Phase 5 — NOT STARTED**, planned in the new
  `docs/PHASE5_ROADMAP.md` (planning-only skeleton; candidate areas:
  further Termux:API-style capabilities — share sheet / open URL / vibrate /
  toast / sensors / camera / intents — the two known client fixes KI-1 and
  KI-2 from the 4.5/4.6 post-review, and the deferred GUI/catalog/root
  areas). Confirm with the user which candidate part they want before
  coding; the exact parts are not yet defined.
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
2. Phase 4 is complete; next work is Phase 5 (not started). Confirm with the
   user which candidate area from `docs/PHASE5_ROADMAP.md` they want, and
   decide/write down that part's open technical questions before coding
   (record it in `docs/chat-phase5/`).
3. A part is complete only when its **"Exit condition"** is met and verified,
   not merely when code is written.

**Before each change, state:** what you are changing, which Part and exit
condition it serves, and which invariant (if any) it could affect.
