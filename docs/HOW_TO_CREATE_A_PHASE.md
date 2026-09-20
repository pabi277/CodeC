# How to create a new phase in CodeC

> **What this file is.** The phase ceremony, extracted from the repo itself
> (`rule.md`, `prompt.md`, `docs/PHASE34_37_ROADMAP.md`,
> `docs/PHASE38_43_ROADMAP.md`, `docs/PHASE44_50_ROADMAP.md` and the
> `docs/chat-phase1…50/` directories that were produced by them). Nothing here
> is invented: every step below is what those rounds actually did. Read this
> before numbering a new phase, and follow it in this order.
>
> **Audience:** the agent in a future chat. The owner does not have to run any
> of it — the owner says **"Start Phase N"** and the agent does the rest
> (`rule.md` §1/§3).

---

## 0. The one law above all

**No PR, no merge, no push to `main` without the owner's explicit command in
chat** (`rule.md` §3). The agent commits and pushes the session branch freely
(`arena/*` — never any other branch), gets CI green, reports, and stops. The
owner merges, or types *"merge it"* / *"create pr and merge"*.

Everything below happens **on the session branch only**.

---

## 1. What a phase IS (the shape the repo expects)

A phase is **one owner row** — one sentence the owner said about the app —
turned into:

| Artefact | Where | Purpose |
|---|---|---|
| A **research dossier** (for a series) | `docs/PHASE<from>_<to>_UX_RESEARCH.md` or `…_OSS_RESEARCH.md` | external sources + licences + the *rejected* options with reasons |
| A **roadmap** (for a series) | `docs/PHASE<from>_<to>_ROADMAP.md` | the row → phase table, numbering, execution order, live status |
| A **phase README** | `docs/chat-phaseNN/README.md` | evidence (file:line), risks, design, exit condition, device rows |
| One **part doc per part** | `docs/chat-phaseNN/PART_NN_x_<SLUG>.md` | the spec that becomes code |
| A **device round** (when the work is device-only) | `docs/chat-phaseNN/DEVICE_ROUND.md` | numbered rows the owner runs on a handset |

Numbering law (owner 2026-09-12, `PHASE44_50_ROADMAP.md`): *"Number the phases
like i go in a row."* New phases continue the sequence — **the next free number
after the last planned one**. Part numbers are `<phase>.<n>` (50.1, 50.2, …).

---

## 2. The order of work (do not reorder)

### Step 1 — verify state first (`rule.md` §4.1)
```bash
git status && git log --oneline -5 && gh pr list && gh run list --limit 5
```
Trust the repo, not memory. The Arena sandbox can reset HEAD while files stay
newer — realign with `git fetch origin <session-branch> && git reset --mixed
FETCH_HEAD`. **Never `reset --hard`.**

### Step 2 — reproduce / evidence BEFORE any hypothesis (`rule.md` §4.2)
Read the owning file. A plan doc's first section is always *evidence*: exact
`file:line` reads against the current `main`, plus the grep count that proves
the claim ("**one** animation call site in the whole app"). **No fix on a
guess.**

### Step 3 — research, and record it
- External claims must name their source and whether it was fetched this
  session. The house format is the dossier's own header: *"every claim about
  this app is a `file:line` read on `<date>` against `main` @ `<sha>`; every
  external claim names its source."*
- Open-source-first, in this order (`PHASE38_43_OSS_RESEARCH.md` §0): **is
  there a library that already does this? → a canonical data set we may vendor?
  → an app whose behaviour we should copy? → only then custom Kotlin.**
- Clean-room law (`rule.md` §6): replicate FEATURES, never paste code. Closed
  source = visible behaviour only. Copyleft (Termux/GPL) = read the public
  spec, re-implement. Depend only on MIT / Apache-2.0 / BSD / CC0.
- Record what you **rejected** and why. *"We researched it and chose the
  platform"* is a decision the next phase must not re-litigate.

### Step 4 — split the row into phases, then into parts
One part = one pure policy (or one screen, or one dependency decision). Keep
each part small enough to be **implemented in one push with green CI in one or
two rounds**. Phases 44 and 45 were two parts each; 51–53 are four.

### Step 5 — write the part docs in the house skeleton
Every `PART_NN_x_*.md` in `docs/chat-phase44…49/` follows this heading order:

```text
# CodeC Phase NN.x — <title>
> **Status:** 📋 PLANNED · **Cost:** `[client-only]` · **Effort:** S/M/L
> **Owner row (verbatim):** "…"
## First move: evidence, not code      # file:line reads, the grep that convicts
## Design                              # the pure policy (Kotlin signatures)
## The Android edge                    # the Compose/framework half, call sites
## Exit condition                      # what "done" means, testable
## Tests (plan)                        # named test files + case counts
## Sources (record)                    # numbered, with URL + fetched date
## Deferred / rejected with reasons
```

Statuses used in the repo: 📋 **PLANNED** → 🚧 **IMPLEMENTED** → ✅ **COMPLETE
& MERGED**; ❌ **CANCELLED** leaves a tombstone README (Phase 43) with the
reason and what replaces it.

### Step 6 — the code pattern (this is not optional)
**Pure, Android-free policy + thin wiring.**
- The decision lives in an object/function that takes a state data class and
  returns an action (`BackRouter.decide(BackState): BackAction`,
  `IncrementalEdit`, `SetupLockPolicy`). It compiles and tests on a host JVM.
- A **wiring pin test** asserts the app really uses it (`BackHandlerWiringTest`,
  `CaretCallSiteTest`) — otherwise the policy is decoration.
- Tests go in `app/src/test`; CI (`:app:testDebugUnitTest`) is the **only test
  executor of record** — Gradle/AGP/lint/Robolectric cannot run in the agent
  sandbox. Pre-validate locally with the jdk4py + kotlinc harness
  (`rule.md` §9) before pushing; it catches the stupid errors and keeps red CI
  runs down.

### Step 7 — docs in the SAME commit (`rule.md` §7)
- The owning part doc gets its `## Implementation (date)` section.
- Living docs stay current: `docs/JOURNEY.md` (a new numbered entry, §71 →),
  `docs/NEXT_STEPS.md` (a new **Head:** line at the top),
  `docs/TROUBLESHOOTING.md` (owner-facing symptom → fix), `rule.md` §9
  (the state snapshot), `prompt.md` when the handoff changes.
- Append; never rewrite history.

### Step 8 — push, watch CI, report
`Build APK` = assemble + unit tests + lint, all three (`rule.md` §5). Report:
**what changed, tip sha, run id, any "device pass required"**. A red run is
fixed for-cause — never papered over; the lesson goes into the part doc
(see `docs/chat-phase40/PART_40_4_CI_LOOP_DIAGNOSIS.md`).

### Step 9 — the device round, then the gate
Work that only a handset can prove is marked **"device pass required"**. The
owner runs `docs/chat-phaseNN/DEVICE_ROUND.md` and reports; each round's
report becomes a new dated section in the part doc, and the fix is pinned by a
test before the next round. **Never claim device acceptance without a
transcript.** Then stop at the merge gate (§0).

---

## 3. The tests that a phase must leave behind

| Kind | Example | Why |
|---|---|---|
| Pure policy test | `BackRouterTest`, `IncrementalEditTest` | the decision is provable without Android |
| Wiring pin | `BackHandlerWiringTest`, `CaretCallSiteTest` | proves the app calls the policy |
| Source scan | `SetupGateWiringTest`, `FolderImportRemovedTest` | greps the sources so a regression fails the build |
| Contrast/token gate | `AppContrastTest`, `ChromeContrastTest` | the Phase 40.5 colour law |
| Robolectric | `SingleFileSaveTest` | only where a real Android class is unavoidable |

Every test file and case count goes in the part doc's **Tests (plan)** section
and in the final report.

---

## 4. Things a new phase must NOT break (`rule.md` §6, standing laws)

- No `.` on `PATH`; never `build-package.sh -I`; never overwrite `cc` or the
  real ELF `bash`; `cc` is CodeC's own TCC frontend, never a clang symlink; TCC
  link order with `-o` last; never official `com.termux` packages; never bundle
  the bootstrap; repository metadata stays signed.
- **No new dependency, permission, DataStore key, Settings control or
  telemetry** unless the part doc's `## Deferred` section explicitly justifies
  it.
- **Phase 40.5 colour law:** the accent is a role, not a hex; no new
  `Color(0x…)` literal for text/icons; values that already pass are not
  restyled.
- **No-nag law (Phase 41/42):** no modal nag more than once; the guide's tour
  has **no skip** and Back must not end it (45.2 rounds 2-3).
- **Single-click law (45.2 round 7):** one tap on a guided control performs the
  whole thing the box teaches.

---

## 5. Quick checklist (paste into the report)

```text
[ ] state verified (git/gh)          [ ] evidence: file:line, not memory
[ ] research dossier updated         [ ] rejected options recorded
[ ] phase README + part docs written [ ] owner row quoted verbatim
[ ] pure policy + wiring pin         [ ] host tests pre-validated
[ ] CI green (run id)                [ ] living docs updated in the same commit
[ ] device pass required? Y/N        [ ] stopped at the merge gate
```
