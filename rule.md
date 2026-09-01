# CodeC — Future-Update Rules (owner direction 2026-09-01)

> **Owner's direction:** *"Update all md files … make a rule.md for future
> updates where i will not do anything with phase maybe and merge with main."*
> This file is the operating manual for all CodeC work **after Phase 18**.
> The phase ceremony (per-phase recipes, owner-led device rounds, phase-by-phase
> commands) is retired unless the owner asks for it again. The owner's remaining
> role is: say what you want, then **merge to `main`** (or hand the agent the
> merge command).

---

## 1. What "future update" means

Any new work after Phase 18: a bug fix, an owner-reported issue, a small
feature, a docs correction. It follows the lifecycle in §4 and lands on `main`
through the gate in §3. There is no "start phase N" ceremony anymore; the owner
just states the problem or the change they want.

## 2. Branching & push discipline (law)

1. Work **only** on the current session branch (`arena/*`). Never push to
   `main` or create/push any other branch.
2. Commit as you go, with a message naming the fix/feature. Push the session
   branch freely. Never `push --force`.
3. One piece of work at a time, from the current branch state. No parallel
   branches, no second PR while one is open.
4. If the checkout disagrees with remote (the Arena sandbox can reset HEAD to
   the base commit while files stay newer), realign **without touching the
   worktree**: `git fetch origin <session-branch> &&
   git reset --mixed FETCH_HEAD`, then `git diff --stat`. **Never `reset --hard`.**

## 3. The merge gate (READ CAREFULLY)

The standing owner rule (2026-08-26) still binds: **the agent never opens,
creates, or merges a PR — and never pushes to `main` — without the owner's
explicit command in chat.**

On 2026-09-01 the owner said they will not run phases anymore and will
**merge with main**. Until the owner changes this rule in chat, that means:

- The agent does everything up to **CI green + docs + pushed session branch +
  a short report**, then stops.
- **The owner merges** — either by typing the merge command in chat (then the
  agent opens the PR and merges it) or by merging the PR themselves.
- If the owner ever wants the agent to open **and merge** PRs automatically
  when CI is green, the owner must say so with a phrase like
  *"auto-merge when CI is green"* — that phrase, once typed, updates section
  §3 of this file (and `prompt.md`), and only then does the agent act without
  per-change commands.

## 4. Update lifecycle (mandatory order)

1. **Verify state first** — `git status`, `git log`, `gh pr list`,
   `gh run list`, remote `main` tip. Trust the repo, not memory.
2. **Reproduce / evidence before hypothesis.** No fix on a guess: get the
   exact symptom (device output, file contents, CI annotation).
3. **Research when needed**; record "Research notes" with sources in the
   relevant part doc.
4. **Implement Android-free, host-testable** where possible (the codebase
   pattern: pure engines + injected adapters). Add/update tests.
5. **Update docs** — see §7.
6. **Commit + push** the session branch.
7. **Watch CI** (`Build APK`: assemble + unit tests + lint). CI is the **only**
   test executor — the agent sandbox has no JVM/device. Fix only for-cause
   failures; never paper over a red run.
8. **Report** state (run id, tip sha, what changed) and stop at the merge gate
   (§3).

## 5. CI & device policy

- `Build APK` runs `:app:assembleDebug`, `:app:testDebugUnitTest`,
  `:app:lintDebug`. A failing test or lint ERROR fails the run.
- The agent sandbox cannot reach CI logs/artifacts/releases (only
  `api.github.com`); on-device testing is impossible. Device transcripts come
  from the owner.
- The owner is **not** running per-phase recipes anymore. Work that genuinely
  needs a device pass must be marked **"device pass required"** in the report;
  the owner decides when (or whether) to run it. Never claim device acceptance
  without a transcript.
- Never trigger expensive actions without explicit confirmation: CodeC package
  repo build (~60–100 min), release/publication, destructive device tests,
  force-push. Check `gh run list` first — never double-dispatch an existing
  run.

## 6. Invariants that are law (do not break, ever)

- **No `.` on `PATH`.**
- **Never `build-package.sh -I`** (installs official `com.termux` debs).
- **Never overwrite `cc` or the real ELF `bash` with a shim.**
- **TCC link order with `-o` last.**
- **Never use official `com.termux` packages or repositories.**
- **Never bundle the bootstrap in the APK.**
- **Repository metadata stays signed** (`signed-by=`, no `trusted=yes`).
- **Clean-room law (2026-08-31):** replicate FEATURES, never copy code —
  closed-source apps: visible behavior only (never decompile); GPL/copyleft
  (Termux): read public specs, re-implement, never paste GPL source.
- **Do not redo/re-debug anything marked COMPLETE/✅** unless the identical
  symptom reappears with regression evidence.

Full background: `prompt.md` (self-distrust protocol), `docs/TERMINAL_PLAN.md`
§B/§J, `docs/chat-phase1/SOLUTIONS.md`, `docs/chat-phase3/REPOSITORY_SIGNING.md`.

## 7. Docs policy

Every update updates the docs **in the same commit**:

- Per-fix record: the owning part doc (e.g. `docs/chat-phase9/…` for editor
  fixes) — a short follow-up section with symptom → root cause → fix → CI run.
- **Living docs** stay current: `prompt.md` (handoff), `docs/JOURNEY.md`
  (narrative + numbered items), `docs/NEXT_STEPS.md` (head state line),
  `docs/TROUBLESHOOTING.md` (owner-facing symptoms).
- Never rewrite history destructively; append follow-ups and update only the
  state summaries.
- Reference `rule.md` from `prompt.md` so the next chat follows this manual.

## 8. Definition of done (for any future update)

1. Symptom/requirement understood and evidenced.
2. Code is host-testable; tests added/updated and pass on CI.
3. No invariant violated; clean-room.
4. Docs updated; commit + push on the session branch.
5. `Build APK` CI green (latest run id recorded).
6. Report says: what changed, tip sha, run id, any **device pass required**.
7. Stop — the owner merges to `main` (or commands the merge).

## 9. State snapshot (2026-09-01)

- **`main` = `f868e10`** (Phase 17 via PR #37 on `a0e7dc3` = PR #36
  Phases 15/16, on `b869ce6` = PR #34 Phase 19).
- **Phases 3–17 & 19: merged. Phase 18: COMPLETE & DEVICE-ACCEPTED — merged
  to `main` via PR #38 (2026-09-01, owner's command "Create pr and marge");
  verify the tip with `git log`/GitHub.** The PR carried Phase 18
  (feature `012deea`, lint fix `4460306`, docs `6c67202`), the Web Preview
  fix (`d49ac47`), and this future-update manual + living-docs refresh
  (`ffca133`).
- **No remaining spec'd implementation.** Open owner items: Phase 17 optional
  conflict recipe (needs a real conflict), Phases 15/16 device-round-3
  dedicated pass, amber ↑N badge for never-published branches, Phase 14 §5
  device round.
