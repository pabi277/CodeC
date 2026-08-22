# prompt.md — paste this into the next chat

> Copy **everything between the two `---` lines below** as the first message of
> a new chat. Do not edit it. It forces the next agent to verify before acting,
> to trust the repo over its own assumptions, and to finish Phase 3 in the
> right order without redoing or breaking anything.

---

Read `docs/JOURNEY.md` and `docs/NEXT_STEPS.md` first, **before doing anything
else**, then report back what you found and the current git/PR/CI state before
making any change.

You are continuing **CodeC** (an Android C IDE) Phase 3 work. PRs #10 and #11
are **merged to `main`** and device-verified. Each chat session gets its own
`arena/*` session branch — verify the actual branch with `git status` instead
of assuming one (previous sessions used `arena/01a028e2-codec`,
`arena/01a02962-codec`).

**WHERE THINGS STAND (2026-08-22):**

- **Part A (republish clean bootstrap): ✅ DONE** — the published
  `userland-v2-dev` assets were repaired in place (no rebuild) and verified on
  a clean device. See `docs/PART_A_ARTIFACT_REPAIR.md`. Do not redo this.
- **Part B (bootstrap correctness): code + 49 host tests merged** (PR #11).
  The ~104-minute "CodeC package repository" rebuild is the ONLY remaining
  step. It has failed 3 times: run `32581293757` = our own guardrail scanner
  bug (fixed + tripwire test); run `32582311088` = upstream
  `xorg.freedesktop.org` download flake (log-proven, not our code); run
  `32585409356` = **cause unknown — the log has never been read**, because the
  agent sandbox cannot download CI logs.
  **Do not redispatch blindly.** First read dispatch 3's log tail in Termux
  (`gh run view --job 97060936792 --log | tail -120`), then follow the exact
  decision table in `docs/NEXT_STEPS.md` → Part B → "Continue here" (diagnose
  → fix or mirror-override → redispatch from `main` → republish → device
  verification script).
- **Parts C (clean-device acceptance) and D (M3 signing): not started.**

**SELF-DISTRUST PROTOCOL — follow strictly:**

1. **Trust nothing from memory or training data about this repo.** Verify every
   fact against the actual files, `git` state, and GitHub Actions before acting.
2. **Evidence before hypothesis.** When something fails, reproduce/observe it
   first (device output, CI log, file contents), *then* diagnose. Never commit a
   fix on a guess — a wrong guess earlier in this project cost hours.
3. **Do not redo or re-debug anything marked COMPLETE / ✅ in the docs.**
   `pkg update / search / install / uninstall / upgrade`, the alternatives
   postinst, and the Part A bootstrap repair are already verified working on a
   real device. Do not "fix" them unless the identical symptom reappears AND
   you have evidence it is a regression, not a known-closed item.
4. **Never trigger an expensive action without explicit confirmation.** The
   "CodeC package repository" workflow takes ~100 minutes — first check whether
   a run already exists (`gh run list`), and ask before starting a new one.
   Same rule for releases and force-pushes.
5. **Honor the invariants (they are law):** no `.` on `PATH`; never
   `build-package.sh -I`; never overwrite `cc` or real ELF `bash` with a shim;
   keep the TCC link order with `-o` last; never use official `com.termux`
   packages or repositories; never bundle the bootstrap in the APK. The full
   list is in `chat-phase1/SOLUTIONS.md`, `chat-phase2/SOLUTIONS.md`, and
   `PHASE3_PLAN.md`.
6. **One PR at a time, from the current state.**
7. **Know the sandbox limits:** the agent sandbox reaches `api.github.com`
   only — it cannot download CI logs, release assets, or workflow artifacts,
   and its token cannot push `.github/workflows/**` or dispatch workflows.
   Those are user actions in Termux; give exact copy-paste commands.

**ORDER OF WORK:**

1. Verify merged state (`gh pr list --state merged --limit 5`,
   `git ls-remote origin main`, `gh run list`). Do not reopen completed parts.
2. Finish **Part B** exactly per `docs/NEXT_STEPS.md` → "Continue here"
   (log-first, then redispatch from `main`, republish, device verification).
3. Then **Part C** (clean-device acceptance), then **Part D** (M3 signing).
4. A part is complete only when its **"Exit condition"** is met, not merely
   when code is written.

**Before each change, state:** what you are changing, which Part and exit
condition it serves, and which invariant (if any) it could affect.
