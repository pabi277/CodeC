# prompt.md — paste this into the next chat

> Copy **everything between the two `---` lines below** as the first message of
> a new chat. Do not edit it. It forces the next agent to verify before acting,
> to trust the repo over its own assumptions, and to finish Phase 3 in the
> right order without redoing or breaking anything.

---

Read `docs/JOURNEY.md` and `docs/NEXT_STEPS.md` first, **before doing anything
else**, then report back what you found and the current git/PR/CI state before
making any change.

You are continuing **CodeC** (an Android C IDE) Phase 3 work on branch
`arena/01a028e2-codec` (PR #10). It is already CI-green and device-verified.

**SELF-DISTRUST PROTOCOL — follow strictly:**

1. **Trust nothing from memory or training data about this repo.** Verify every
   fact against the actual files, `git` state, and GitHub Actions before acting.
2. **Evidence before hypothesis.** When something fails, reproduce/observe it
   first (device output, CI log, file contents), *then* diagnose. Never commit a
   fix on a guess — a wrong guess earlier in this project cost hours.
3. **Do not redo or re-debug anything marked COMPLETE / ✅ in the docs.**
   `pkg update / search / install / uninstall / upgrade` and the alternatives
   postinst are already verified working on a real device. Do not "fix" them
   unless the identical symptom reappears AND you have evidence it is a
   regression, not a known-closed item.
4. **Never trigger an expensive action without explicit confirmation.** The
   "CodeC package repository" workflow takes ~100 minutes — first check whether
   a run already exists (`gh run list`), and ask before starting a new one.
   Same rule for releases and force-pushes.
5. **Honor the invariants (they are law):** no `.` on `PATH`; never
   `build-package.sh -I`; never overwrite `cc` or real ELF `bash` with a shim;
   keep the TCC link order with `-o` last; never use official `com.termux`
   packages or repositories. The full list is in `chat-phase1/SOLUTIONS.md`,
   `chat-phase2/SOLUTIONS.md`, and `PHASE3_PLAN.md`.
6. **One PR at a time, from the current state.**

**ORDER OF WORK:**

1. First verify and merge **PR #10** into `main` if it is not already merged
   (`gh pr list`, `gh pr view 10`, `gh pr checks 10`).
2. Then work `docs/NEXT_STEPS.md` **in order**: Part A (republish clean
   bootstrap) → Part B (bootstrap correctness) → Part C (clean-device
   acceptance) → Part D (M3 signing).
3. A part is complete only when its **"Exit condition"** is met, not merely
   when code is written.

**Before each change, state:** what you are changing, which Part and exit
condition it serves, and which invariant (if any) it could affect.
