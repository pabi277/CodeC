# CodeC Phase 52.4 — The proof: screenshot goldens and the final walkthrough

> **Status:** 📋 PLANNED · **Cost:** `[client-only]` · **Effort:** M ·
> **Owner row (verbatim):** *"boost it's ui 100× time"* — and the only honest
> answer to "did it work?" is evidence, not adjectives.
> Parent: [`README.md`](README.md) ·
> [`PHASE50_52_ROADMAP.md`](../PHASE50_52_ROADMAP.md).

## First move: evidence, not code

**The screenshot stack is already in the repo and is unused.**

```text
gradle/libs.versions.toml:30              roborazzi = "1.59.0"
gradle/libs.versions.toml:75-77,121       the plugin + the artifact
app/build.gradle.kts:8                    alias(libs.plugins.roborazzi)   ← applied to :app
app/build.gradle.kts:272-274              testImplementation(roborazzi …)
$ grep -rln "roborazzi\|Roborazzi" app/src/test
  (no matches)
```

Phase 53's README already recorded this and named the follow-up: *"Phase 53
should use it for the static parts of the new UI … instead of adding a rival
library"*, and it also recorded the catch — **CI runs `testDebugUnitTest` with
no `roborazzi.test.*` property, so a first screenshot test would *record*
rather than *compare***. 52.4 closes both: it writes the goldens **and** wires
verify mode, so a future UI regression fails the build instead of silently
re-recording a new picture.

Two honest limits, stated up front (Phase 53's own words, still true):

- **Goldens do not prove insets, IME behaviour or OEM quirks.** Roborazzi
  renders a fixed window. They prove **layout and colour**, which is exactly
  what 50-52 changed.
- **Goldens do not prove feel.** Motion, haptics and smoothness are the device
  round's job, and no screenshot can stand in for them.

## Design

### A — Twelve goldens (the states this series changed)

| # | State | Theme |
|---|---|---|
| 1 | Welcome / first run | dark + light |
| 2 | Hub — empty | dark |
| 3 | Hub — with projects, resume card showing | dark + light |
| 4 | Editor chrome — RUN ▶ idle, file open, keyboard down | dark + light |
| 5 | Editor — empty (no file open) | dark |
| 6 | Editor — output panel expanded | dark |
| 7 | Packages — the four row states (not installed / installing / installed / failed) | dark |
| 8 | Terminal chrome — first run, tools not ready | dark |
| 9 | Terminal chrome — ready | dark |
| 10 | Settings → Appearance (brand / wallpaper / haptics rows) | dark + light |
| 11 | About — with the streak line (52.3) | dark |
| 12 | The setup bar in each verdict state (Phase 44, re-pinned) | dark |

They live in one test file (`ChromeScreenshotTest`, Robolectric + Roborazzi)
and are committed as PNGs under `app/src/test/snapshots/` — **the repo's
convention is that generated artefacts are reviewed, not ignored**, and these
are small.

### B — Verify mode in CI (the part that makes them worth having)

- A `roborazzi.test.verify = true` property for the CI test task (and
  `record` only when a developer asks for it locally).
- **The honest risk, recorded:** a golden is device-independent but **not**
  font/SDK-independent. Roborazzi renders with a fixed SDK; a Gradle/SDK bump
  can legitimately change every image. The rule is therefore: *a golden change
  is reviewed like a code change* — the PR must show the diff, and a mass
  re-record without an explanation is a red flag, not a cleanup.
- The goldens run in `:app:testDebugUnitTest`, so they cost CI time but **zero**
  new dependencies.

### C — The final walkthrough (the human half)

One scripted journey the owner runs end to end on a **fresh install**, because
"100× more attractive" is a judgement about a whole experience, not a screen:

| Beat | What should happen | Whose work |
|---|---|---|
| 1 | Tap the icon → the mark on the brand colour, no black flash | 51.1 |
| 2 | Three language tiles, each with its colour and one line of "what happens next" | 51.1 |
| 3 | Pick Python → a project opens, the tour walks first-to-last, one tap per beat | 45, 52 |
| 4 | The setup bar says what is happening; the locked tabs say why | 44 |
| 5 | RUN ▶ is the most obvious thing on the screen | 51.2 |
| 6 | The program runs; the output panel animates; one haptic | 51.2, 51.4 |
| 7 | Close the app; reopen the next day → the resume card, not a silent jump | 52.1 |
| 8 | Settings → About → the streak line | 52.3 |

Every beat that fails becomes a row in the owning part doc's device-round
section, with a test before the fix — the 44-49 discipline.

### D — Where this leaves Phase 53 (❌ cancelled by the owner)

Phase 53 (the cross-device matrix) is **🚧 IMPLEMENTED on `arena/01a099d8-codec`**
(2026-09-13): `app/src/test/java/com/codeci/ide/DeviceMatrixTest.kt` (627 lines)
pins the ten rounds A-J for phases **44.1-49.2**, CI ✅ GREEN `34753000709` tip
`69a70ec`. **It is now ❌ CANCELLED** (owner, 2026-09-14: *"Remove the full device
cross check phase"*) — it was Phase 50, moved to 53 so the numbers stayed the
execution order, and then removed from the plan entirely. Its files are history
only. **So 52.4 is not "the CI half of the matrix" — it is the whole visual
gate:** the twelve goldens in verify mode are what catches a layout or colour
regression across the UI series, and the per-phase rounds (L/F/R) are the only
handset evidence that is still planned. 52.4 therefore adopts its format verbatim — the `## Test log (Phase NN
— …)` heading, the five-column table (`# | Part | Run on | What to do | PASS
looks like`), one part doc per row — and the three `DEVICE_ROUND.md` files in
this series already say so. Extending `DeviceMatrixTest` itself to cover 50-52
is deliberately **not** in this phase: it carries a fixed `PART_FILES` map over
44.1-49.2, and these rows do not exist until their phases are built.

⚠️ **Merge-order note:** that test hard-codes `MATRIX_PATH =
"docs/chat-phase50/DEVICE_MATRIX.md"` and demands a `## Test log (Phase 50 —
the cross-device matrix)` section in eleven part docs; this branch moved the
matrix to `docs/chat-phase53/` and the phase is cancelled. **The test must be
DELETED (or fully re-scoped) before both branches land on `main`**, or `Build
APK` goes red on a doc rename.

## The Android edge

- Roborazzi needs Robolectric (4.16.1, already a test dependency and already
  used by **8** test files, including `EditorLaunchMeasureReproTest`, which
  drives a real `NavHost` through `createComposeRule` — the precedent for
  rendering real screens).
- Screenshots must be **deterministic**: fixed size, fixed density, fixed
  theme, `System UI` off, and **no** animation left running (50.4's
  `MotionPolicy.INSTANT` path must be used in tests, or the goldens race).
- The `bench` APK and `:app` stay separate; nothing here ships in the release
  APK (recorded as a 0-byte change, like every phase reports).

## Exit condition

≥12 goldens exist, are committed, and run in **verify** mode on CI (proven by
deliberately breaking one layout in a scratch commit and watching the run go
red — the evidence goes in this part doc, then the scratch commit is dropped);
the final walkthrough's eight beats are run by the owner on a fresh install; and
the results are pasted into the owning part docs.

## Tests (plan)

- `ChromeScreenshotTest` (12+ cases): one per state above, each asserting
  `captureRoboImage()` against the committed golden.
- `GoldenDeterminismTest` (~5): the test harness sets a fixed size, density,
  theme and `MotionPolicy.INSTANT`; no test leaves an animation running; the
  snapshot directory is the one CI uploads.
- `VerifyModeWiringTest` (~3, source scan): the CI test task passes
  `roborazzi.test.verify=true`; the record property is not set in CI; the
  workflow does not silently re-record.

## Sources (record)

- `docs/chat-phase53/README.md` — Roborazzi declared/applied/unused, the
  verify-mode gap, and the two honest limits (quoted above).
- `gradle/libs.versions.toml`, `app/build.gradle.kts:8,272-274`,
  `grep -rln roborazzi app/src/test` (2026-09-13, `main` @ `62cfe7b`).
- `EditorLaunchMeasureReproTest` — the precedent for driving real Compose
  screens under Robolectric.
- `rule.md` §5 — CI is the executor of record; the sandbox cannot see logs or
  artifacts.

## Deferred / rejected with reasons

- **Screenshot tests for the editor's code area** — the sora view is an
  `AndroidView` with its own rendering; a golden of it would be a golden of
  nothing. Chrome only (51.2's law again).
- **A rival screenshot library (Paparazzi, Shot)** — the repo already declares
  Roborazzi; adding a second is exactly what Phase 53 told us not to do.
- **Device-farm / Firebase Test Lab** — needs an account, a billing decision
  and network access the repo does not have; the owner's handsets are the
  device lab, as they have been for all 50 phases.
- **Every screen** as a golden — 12 states that this series actually changed;
  more goldens means more churn, not more safety.
