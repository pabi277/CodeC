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
- **The owner merges.** Two ways, both fine — no terminal needed for either:
  - **Tell the agent in chat** — e.g. *"create pr and merge"* / *"merge it"*.
    The agent then opens the PR from the `arena/*` branch and merges it.
  - **Do it yourself in the browser** (about 30 seconds, no `git`/`gh`):
    1. Open the repo on github.com → **Pull requests** → click the PR for
       this change (it is always opened from the current `arena/*` branch).
    2. Confirm every check is green (✓ **Build APK**), then click
       **Merge pull request** → **Confirm merge**.
    3. (Optional) click **Delete branch** on the merged-PR page.
- If the owner ever wants the agent to open **and merge** PRs automatically
  when CI is green, the owner must say so with a phrase like
  *"auto-merge when CI is green"* — that phrase, once typed, updates section
  §3 of this file (and `prompt.md`), and only then does the agent act without
  per-change commands.

## 4. Update lifecycle (mandatory order)

1. **Verify state first** — `git status`, `git log`, `gh pr list`,
   `gh run list`, remote `main` tip. Trust the repo, not memory. (The owner's
   browser equivalents are the repo's **Code / Pull requests / Actions**
   tabs — see §10.)
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

> **Owner's browser view (no terminal).** The owner can follow everything at
> github.com: **Actions** (the `Build APK` check + its log), **Pull requests**
> (the PR), **Code** (the branch and its tip sha). The agent still reports the
> run id and tip sha, but the owner never has to type a `git`/`gh` command —
> §10 is the click-path ↔ command cheat sheet.

## 5. CI & device policy

- **`Build APK` = assemble + unit tests + lint.** Net effect: the run fails on
  any compile error, failing unit test, or lint ERROR. (Under the hood the
  workflow file lists only `:app:assembleDebug`; the `gradle-bootstrap` shim
  re-points `:app` for the legacy Gradle 9.0.0 invocation and delegates to the
  checked-in `./gradlew` (Gradle 9.3.1) for
  `:app:assembleDebug :app:testDebugUnitTest :app:lintDebug`, converting test
  and lint failures into readable GitHub annotations. **Known simplification
  candidate:** call `./gradlew` from the workflow directly and delete the
  shim — left as-is for now because the shim is what emits the readable
  `::error` annotations; see §10.)
- **Owner reads CI in the browser:** repo → **Actions** → the `Build APK`
  run → its log; a green ✓ means all three gates passed. (The agent sandbox
  cannot reach CI logs/artifacts/releases — only `api.github.com` — so the
  owner's browser is the log viewer. On-device testing is likewise impossible
  here; device transcripts come from the owner.)
- The owner is **not** running per-phase recipes anymore. Work that genuinely
  needs a device pass must be marked **"device pass required"** in the report;
  the owner decides when (or whether) to run it. Never claim device acceptance
  without a transcript.
- Never trigger expensive actions without explicit confirmation: CodeC package
  repo build (~60–100 min), release/publication, destructive device tests,
  force-push. Check for an existing run first (github.com **Actions** tab or
  `gh run list`) — never double-dispatch an existing run.

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

- **`main` = `dc68eee`** — Phase 18 via **PR #38** (merged 2026-09-01,
  owner's command "Create pr and marge"). Chain: `dc68eee` ← `f868e10`
  (PR #37 Phase 17) ← `a0e7dc3` (PR #36 Phases 15/16) ← `b869ce6` (PR #34
  Phase 19). Verify with `git ls-remote origin main` / the GitHub API — the
  local clone is shallow, so `git log` alone is not proof of history.
- **Phases 3–17 & 19: merged. Phase 18: COMPLETE & DEVICE-ACCEPTED — merged
  to `main` via PR #38 (2026-09-01).** The PR carried Phase 18
  (feature `012deea`, lint fix `4460306`, docs `6c67202`), the Web Preview
  fix (`d49ac47`), and this future-update manual + living-docs refresh
  (`ffca133`).
- **No remaining spec'd implementation.** Open owner items: Phase 17 optional
  conflict recipe (needs a real conflict), Phases 15/16 device-round-3
  dedicated pass, amber ↑N badge for never-published branches, Phase 14 §5
  device round.

---

## 10. GitHub without a terminal (cheat sheet)

Everything the owner may want to do has a github.com click path. The `gh`
commands the agent uses are just the terminal spelling of the same buttons.

| You want to… | On github.com (click) | Terminal equivalent (agent) |
|---|---|---|
| See the latest commit on `main` | **Code** tab → branch dropdown → `main` → the commit sha | `git ls-remote origin main` |
| See open / past PRs | **Pull requests** tab | `gh pr list` |
| See CI / build status | **Actions** tab → `Build APK` run → green ✓ / red ✗ | `gh run list` / `gh run view` |
| Read a failed CI log | **Actions** → the run → the job → expand the failing step | (sandbox cannot — the owner's browser is the viewer) |
| Re-run a failed build | **Actions** → the run → **Re-run jobs** | `gh run rerun <id>` |
| Merge a change to `main` | **Pull requests** → the PR → **Merge pull request** → **Confirm merge** | `gh pr merge <n> --merge` |
| Open the PR for this session | **Pull requests** → **New pull request** → base `main` ← `arena/*` | `gh pr create` |
| Run the package-repository workflow | **Actions** → **CodeC package repository** → **Run workflow** (set `publish`, `source_run_id`) | `gh workflow run "CodeC package repository"` |
| Publish the bootstrap release | **Actions** → **Publish CodeC bootstrap release** → **Run workflow** (`source_run_id`, `release_tag`) | `gh workflow run "Publish CodeC bootstrap release"` |
| Download the APK | **Actions** → latest green `Build APK` → **Artifacts** → `CodeC-IDE` | (sandbox cannot) |

Notes:

- The **"Run workflow"** button is the GUI for everything `gh workflow run`
  does — the owner never needs `gh` installed.
- The package-repository build is expensive (~60–100 min): only press
  **Run workflow** when you mean it, and check **Actions** first so you don't
  start a second one.
- The app already has a visual GitHub UI (Phase 13): Settings → **GitHub
  Account**, Files → ⋮ → **Clone from GitHub**, the **Source Control** pane,
  and **COMMIT & PUSH**. On-device, prefer those buttons over the terminal for
  everyday git work.

**Known simplification candidate (not done yet):** `build-apk.yml` still
provisions Gradle 9.0.0 and routes through the `gradle-bootstrap` shim
(AGP 9.1.1 needs Gradle 9.3.1). Moving the workflow to the checked-in
`./gradlew` and listing `:app:assembleDebug :app:testDebugUnitTest
:app:lintDebug` explicitly would delete the shim — but only once the readable
`::error` annotations the shim produces today are preserved.
