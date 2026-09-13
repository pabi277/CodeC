# CodeC Phase 50 — The cross-device round

> **Status:** 🚧 **IMPLEMENTED** (2026-09-13, this branch; owner: *"Start phase 50"*)
> — the **instrument** ships: a 90-row runbook over 44–49's exit conditions, eleven
> `## Test log` tables (one per part file, Phase 50's exit 4), and a host test that
> pins the runbook to the app so a quoted sentence can never go stale again. What
> it deliberately **cannot** ship is the filled matrix: no sandbox has a handset, an
> IME or an OEM battery manager, so the results belong to the owner's devices
> (`rule.md` §5 — *device pass required*). · **Cost:** `[client-only]` ·
> **Effort:** S (docs + gates) + M (the bugs it finds) · **Owner rows it closes
> (verbatim):** *"A. If the code is very big it's last line go under the keyboard,
> when i use a suggestion it go down and hide behind the keyboard"* · *"B. My phone
> showing the option when try to close not now option but in most phone no option
> like not now or exit"* · *"C. The back botton all screen behavior please recheck
> and refine"* — and the implicit *"it works on mine"* behind all seven rows.

```text
  50.1  DEVICE_MATRIX.md — every fix from 44-49, checked on every device class,
        pinned to the app by DeviceMatrixTest so the runbook cannot rot
```

| Part | Title | Effort | Status |
|---|---|---|---|
| [50.1](DEVICE_MATRIX.md) | The matrix + the record format + the CI pin | S + M | 🚧 IMPLEMENTED (matrix pending devices) |

---

## Why this phase exists at all

The owner's report is a **single-device** report: *"My phone showing the option…
but in most phone no option."* Four of the six shipped phases above rest on a
behaviour that only exists on hardware — the caret above a real IME (48), the back
stack under two nav modes (49), a setup bar that survives OEM battery management
(44), coach-mark anchor geometry on a 5" screen (45). None of it is provable by a
JVM test, and the repository's law is that CI is the test executor of record
(`rule.md` §3, §8). Phase 50 is the bridge: a written procedure that turns handset
testing into **evidence the repo can keep**, plus the one automated check that a
runbook can actually carry — its own truthfulness.

**Phase 50's real bug was in the runbook, and finding it was the work.** The
planned `DEVICE_MATRIX.md` predated everything 44–49 shipped: round A quoted
`"Preparing Python (1/3) · 0 %"`, `"Ready ✓"`, `"Offline — setup paused · Retry"`
and `"Setup is running — open Terminal to watch it"` — **none of those strings has
ever existed in the app**; the setup bar really says
`Setting up CodeC's Linux tools — 42 % · C works right now`
(`SetupGatePolicy.barText`). Round B asked for a **SKIP on every tour card** six
rounds after the owner deleted the skip. Round C listed a four-row `+` sheet with
an "Import file" row that lives in the hub's ⋮ menu, not the sheet (three rows:
New Project / Clone Git Repository / Import ZIP). Round F row B4 still described
three unrelated coach marks with NEXT/SKIP buttons instead of the eleven-beat tour.
Row counts and grep claims were stale too (the docs still said the 44–49 host tests
*"do not exist yet"* — 271 `@Test` cases exist across 27 files, all green in CI).
That is Phase 44's device round 1 all over again — a round burned discovering the
instructions instead of the app — so the fix is not only "correct the rows" but
**"make it impossible to ship stale rows"**.

## What Phase 50 is

1. **[DEVICE_MATRIX.md](DEVICE_MATRIX.md)** — 90 rows in ten rounds (A · 44.1,
   B · 44.2, C · 45.1, D · 45.2, E · the chrome lock, F · 46, G · 47, H · 48,
   I · 49, J · the hostile environment), four device classes, a fixed per-device
   record format, a **20-minute pass** for every device after the first, and the
   two destructive cases the roadmap asked for (kill mid-install **and** revoke
   file access — the plan's matrix had only the first). Every row names its
   `Part`, the class it must run on, and the **verbatim** on-screen text.
2. **The rule that makes it durable:** every row's result is pasted into the
   *owning* part file's `## Test log` — created for all eleven parts in this
   commit — so a fix and its proof live in the same file, the way every merged
   phase does today.
3. **The headless half, which is the runbook's own CI gate:**
   `app/src/test/java/com/codeci/ide/DeviceMatrixTest.kt`. It re-reads this file
   on every push and fails the build if a row is missing, duplicated,
   mis-parted, unrunnable-on-the-wrong-class, **or quotes a sentence the app does
   not contain** (resolved against `strings.xml` values and the production Kotlin
   literals, comments stripped, interpolations treated as holes). That is the
   failure mode this phase was created to remove, made structurally impossible.
   It is a plain JUnit4 host test — no Robolectric, no new dependency — so it
   runs wherever the app's unit tests run: on CI that is inside `Build APK`'s
   `Assemble debug APK` step, whose `gradle` is the bootstrap bridge that
   executes `:app:assembleDebug` + `:app:testDebugUnitTest` + `:app:lintDebug`
   ([`../chat-phase46/CI_TEST_SCOREBOARD.md`](../chat-phase46/CI_TEST_SCOREBOARD.md);
   the same bridge is recorded from run `34433912076` in `../JOURNEY.md` §52).
   The sandbox has no Gradle, so **CI is the first thing that compiles
   `DeviceMatrixTest.kt`** — the rules were mirrored in a throwaway Python parser
   over the same files to de-risk that (0 problems on all 90 rows), but the
   compile itself is CI's.

## The honest limits, stated up front

- **No emulator, no device, no Gradle in the sandbox.** Rounds A, B, E, H and I
  are physically unrunnable here. `n/a` is an acceptable answer for a device class
  nobody owns; `✅` is not.
- **A host test cannot excuse a device row.** The 271 cases across 44–49 check
  *pure policy*: that `barText` returns that sentence, not that a thumb saw it
  above a keyboard. `DeviceMatrixTest` checks the *document*, not the phone.
- **One device class is not enough for 49.2's cause C** (a gesture-nav home swipe
  sends no back event at all). The matrix requires both nav modes for the back and
  prompt rows, which is the whole point of D1 vs D2.
- **Battery-manager kills are not reproducible on demand** (Xiaomi / Oppo / Vivo /
  Samsung). J2 is the closest proxy.
- **The results table starts empty and stays empty until a human runs it.** The
  phase is 🚧 until ≥ 2 devices are pasted in, one of them 3-button-nav.

## Decisions taken here, with their reasons (no open question left implicit)

| Question | Decision | Why |
|---|---|---|
| Screenshot tests (Roborazzi 1.59.0 is declared, applied to `:app`, and **unused** — `grep -rln roborazzi app/src/test` = 0 hits)? | **Not built in Phase 50** | A golden recorded in a headless JVM window measures *Robolectric's* layout, not a phone's; the four surfaces it would cover (setup bar, tour overlay, single-file status bar, drawer PROJECTS) are already pinned by `SetupGatePolicyTest`, `TooltipPlacementTest`, `ViewportSnapshotTest` and `DrawerProjectListTest`. What they miss — a real IME resize, a real hole over a real control — is also what a recorded golden cannot see. **And CI has no `roborazzi.test.*` wiring**, so a first run would *record*, not *compare*: a green run would prove nothing while looking like it proved something. One small follow-up if the owner wants it; `rule.md` §6 needs no approval, the dependency is already declared. |
| A Robolectric back-stack test for 49? | **Not built** | 49 shipped the stronger thing: `BackRouterTest` (the table), `BackRouterRootTest` (`isRoot` over every real route shape, parameterised routes included) and `BackHandlerWiringTest` (every `BackHandler(` in the app is the root or router-driven). A UI test would re-derive that from a slower direction. |
| New device rows nobody asked for? | **None** | Round J exists because the roadmap demanded the *destructive* pair; every J row is a real limit named in a phase doc (revoke storage, OEM 10 idle minutes, low disk, upgrade-over-install, foldable resize, safe mode, largest font) — not an invention. |
| Marking the owner's earlier round-level passes as ✅? | **No** | 46/47 and 48/49 came back as *"All working"* / *"All device passed"* — one phone, model never supplied, no per-row text. Those reports are recorded **as reports** in each `## Test log` note; the rows themselves stay `⏳` until a device line exists to attach them to. A matrix is per-device or it is nothing. |
| `docs/PHONE_UX_ANALYSIS.md` §12's pending checks (exit 5 of the plan)? | **The citation was wrong, so it is closed, not deferred** | The file ends at §9 ("Bottom line") and holds no pending-check list — the plan read a section number that does not exist. What *is* owed is now owned elsewhere: 44's `DEVICE_ROUND.md` (R1-R8, D1-D12) and 45's (G1-G41) are the per-phase lists, and `DEVICE_MATRIX.md` is the cross-device selection of them plus the corrections. Recorded here so nobody re-opens a phantom checklist. |

## Exit condition

```text
1. DEVICE_MATRIX.md run and pasted back for at least: one small gesture-nav phone
   (D1), one 3-button-nav phone (D2), and — if hardware exists — one Android 13+
   device (D3, so 44's notification/FGC rows are checked where the platform is
   strictest) and one tablet/foldable (D4).
2. Every row of the 20-minute pass has a result on every device; the full pass has
   one device; every ❌ carries the verbatim text or a log line (I15 exists to make
   that one paste).
3. Every ❌ has either a fix in its owning part file or a dated deferral with a
   reason.
4. Each of the eleven part files 44.1…49.2 carries a `## Test log` with its own
   rows filled in.  ✅ sections created, row-for-row pinned by DeviceMatrixTest
5. `DeviceMatrixTest` green in CI — i.e. the runbook still matches the app the
   tester installed (branch `main`, one build holding all of 44-49).
PASS = 1-5 with zero open failures that are not explicitly deferred. Until then
Phase 50 is 🚧 IMPLEMENTED, never ✅ COMPLETE: this phase's deliverable is a
filled matrix, not a promise.
```

**Row census** (measured from the file, and the floors `DeviceMatrixTest` enforces):
90 rows / 10 rounds; per part 44.1 ×20, 44.2 ×8, 45.1 ×7, 45.2 ×12, 46.1 ×3,
46.2 ×5, 47.1 ×6, 47.2 ×4, 48.1 ×9, 49.1 ×8, 49.2 ×8; 33 rows quote a
corpus-verified sentence (floor 30); every device class is named by ≥ 3 rows.

## Sources

- The five owner rows and the ordering, in
  [`../PHASE44_50_ROADMAP.md`](../PHASE44_50_ROADMAP.md) (§"The owner's five rows →
  phases" row —, and §"Phase 50 — Cross-device test round").
- The per-phase rounds this file selects from and corrects:
  [`../chat-phase44/DEVICE_ROUND.md`](../chat-phase44/DEVICE_ROUND.md),
  [`../chat-phase45/DEVICE_ROUND.md`](../chat-phase45/DEVICE_ROUND.md), and the
  exit conditions inside all eleven part docs (46–49 have no separate round file;
  their rows came from their READMEs and the two merged device rounds, PR #78 and
  PR #79).
- **Format precedent:** `../chat-phase40/DEVICE_TEST_PLAN.md` (the full runbook
  shape — branch, functional round, green CI run ids, "PASS looks like" with the
  exact on-screen text) and `../chat-phase41/DEVICE_TEST_PLAN.md` (the leaner
  round). `DEVICE_MATRIX.md` keeps their columns so the three read as one series.
- `docs/BETA.md` (the standard a beta round is held to: a record per device, not a
  verdict), `rule.md` §3 (CI as the test executor of record), §4.2 (evidence before
  change), §5 (device pass required; never claim device acceptance without a
  transcript), §8 (verification law).
- `ui/utils/DeviceDiagnostics.kt` (`abiSummary`, `osSummary`, `summary(dataDir)`)
  behind the identity line §0 asks for, via Settings → Feedback & Support →
  **COPY REPORT** (`ui/support/FeedbackDraft.kt`).
- The string corpus the runbook was checked against, line by line on 2026-09-13:
  `ui/terminal/SetupState.kt` (`SetupGatePolicy.barText` / `refusal` /
  `inFlightNote` / `dontCloseText` / `notificationText` / `barDismissAllowed` /
  `startOnTerminal`, `SetupLockPolicy.sweetMessage` / `option` /
  `editorChromeLocked`), `ui/terminal/SetupLedger.kt` (the boot-repair notice),
  `ui/terminal/TerminalUx.kt` (the status chip), `ui/components/SetupBar.kt`,
  `ui/guide/GuidePlan.kt` + `ui/guide/CoachMarks.kt` + `ui/guide/CoachMarkPlan.kt`
  (five slides, eleven beats, the finish card), `ui/screens/FileManagerScreen.kt`
  (the `+` sheet, the hub ⋮, `hubBackAction`),
  `ui/components/EditorProjectDrawer.kt` + `ui/editor/DrawerPolicy.kt` (✕,
  PROJECTS, the pick, the single-file hint), `ui/projects/EditorOpenModePolicy.kt`
  (the `~proj/…` status-bar path), `ui/screens/SettingsScreen.kt` (CodeC Keys copy,
  `Help & guide`, `Reset tips`, `Tell us before you go`),
  `ui/support/ExitFeedbackDialog.kt`, `ui/crash/CrashReportOverlay.kt`,
  `MainActivity.kt` (the root handler, its `Back` log line) and
  `app/src/main/res/values/strings.xml`.
