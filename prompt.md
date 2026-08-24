# prompt.md — paste this into the next chat

> Copy **everything between the two `---` lines below** as the first message of
> a new chat. Do not edit it. It forces the next agent to verify before acting,
> to trust the repo over its own assumptions, and to continue CodeC in the
> right order without redoing or breaking anything.

---

Read `docs/JOURNEY.md` and `docs/NEXT_STEPS.md` first, **before doing anything
else**, then report back what you found and the current git/PR/CI state before
making any change.

You are continuing **CodeC** (an Android C IDE). PRs #10–#13 are **merged to
`main`**. PR #14 (`arena/01a02db3-codec` → `main`) is open and contains Part
C, Part D (M3 repository signing), and their device-acceptance evidence on top
of the docs updates its title describes. Each chat session gets its own
`arena/*` session branch — verify the actual branch with `git status` instead
of assuming one.

**WHERE THINGS STAND (2026-08-24):**

- **Phase 3 is device-acceptance complete.** Parts A, B, C, and D have all met
  their exit conditions and are verified on real Android hardware:
  - **Part A** (republish clean bootstrap): ✅ done, in-place repair, device-verified.
  - **Part B** (bootstrap correctness — curl fetcher, no `termux-keyring`): ✅
    done, merged, rebuilt, republished, device-verified.
  - **Part C** (clean-device acceptance, M2 gate): ✅ done — every checklist
    item passed on real arm64 devices, including lock-recovery and legacy-marker
    fixes found along the way.
  - **Part D** (M3 repository signing): ✅ done — signing implementation,
    signed Pages publication, signed-client device acceptance, key-seeded
    bootstrap rebuild/republish, **and the final rebuilt-bootstrap
    clean-device gate** (full uninstall/reinstall against the published
    `userland-v2-dev` archive) all passed on 2026-08-24. See
    `docs/PHASE3_DEVICE_ACCEPTANCE.md` §8 and `docs/JOURNEY.md` §5f for full
    evidence.
  - **Do not redo, re-debug, or re-run any of the above.** They are closed.
- **PR #14 is open, CI-green, and mergeable.** It now bundles Parts C+D on top
  of its original docs scope. It has not yet been merged — check
  `gh pr view 14` for current state before assuming.
- **Next work is Phase 4 polish**, per `docs/NEXT_STEPS.md` Parts E (storage
  access / `termux-setup-storage`-equivalent) and F (install-confirmation UX,
  signing-status UX, theme/settings parity). Nothing in Phase 4 has started.
- An optional x86_64 repeat of the Part D clean-device test was not run (no
  x86_64 device was available); this does not block calling Phase 3 complete
  on the tested aarch64 architecture (see `docs/PHASE3_PLAN.md` §5 M3).

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
   in `chat-phase1/SOLUTIONS.md`, `chat-phase2/SOLUTIONS.md`, `PHASE3_PLAN.md`,
   and `REPOSITORY_SIGNING.md`.
6. **One PR at a time, from the current state.**
7. **Know the sandbox limits:** the agent sandbox reaches `api.github.com`
   only — it cannot download CI logs, release assets, or workflow artifacts
   directly, and cannot perform on-device testing. Device tests and any
   log-pull-dependent debugging need the user in Termux/on-device; give exact
   copy-paste commands and wait for the transcript.

**ORDER OF WORK:**

1. Verify current state (`gh pr list`, `gh pr view 14`, `git status`,
   `gh run list`, `gh release list`). Do not reopen completed parts.
2. If PR #14 has not been merged yet and the user wants to proceed, merging it
   is the natural next step (it is CI-green and mergeable as of 2026-08-24).
3. Otherwise, move to **Phase 4** (`docs/NEXT_STEPS.md` Parts E and F) —
   confirm which part the user wants before starting either.
4. A part is complete only when its **"Exit condition"** is met and verified,
   not merely when code is written.

**Before each change, state:** what you are changing, which Part and exit
condition it serves, and which invariant (if any) it could affect.
