# CodeC Phase 52.2 — Jank budget and measured first paint

> **Status:** 📋 PLANNED · **Cost:** `[client-only]` (the bench APK is separate
> and ships nothing into `:app`) · **Effort:** M ·
> **Owner row (verbatim):** *"it's not attractive to user to use multiple
> time"* — because an app that stutters is an app that feels cheap, whatever it
> looks like.
> Parent: [`README.md`](README.md) ·
> [`PHASE50_52_ROADMAP.md`](../PHASE50_52_ROADMAP.md).

## First move: evidence, not code

**The instrument already exists.** Phases 25.1/28.1 built a separate bench APK
(`com.codeci.bench`) and it is still in the repo:

```text
bench/src/main/java/com/codeci/bench/core/FrameStats.kt
bench/src/main/java/com/codeci/bench/harness/FrameCapture.kt
bench/src/main/java/com/codeci/bench/candidate/SoraCandidate.kt
bench/src/main/java/com/codeci/bench/candidate/NowCandidate.kt
bench/src/main/java/com/codeci/bench/core/InputScripts.kt
```

…and `:bench` is included **only** in the legacy Gradle path
(`settings.gradle.kts`), built by CI as `./gradlew :bench:assembleRelease
:bench:testDebugUnitTest`, and uploads a `CodeC-Bench` artifact. So frame
timing is measurable **today**, on the owner's phone, with **zero** new
dependency and **zero** bytes in `:app`.

**What is *not* measured is the thing the user feels first:** time from tapping
the icon to a usable screen. Today there is no splash (51.1's finding) and no
timestamp, so nobody can say whether a cold start is 0.8 s or 3 s. The fix is
one log line, not a framework:

- `MainActivity` already writes structured logs the owner can read
  (Settings → Developer Options → **View App Logs**; Phase 49.2 added
  `AppLogger.i("Back", …)` the same way). Add
  **`AppLogger.i("Launch", "firstFrameMs=%d route=%s", …)`**, computed from the
  process start to the first composed frame — the owner reads it on the device
  with no new tooling.

**The law for this part: measure first, tune second.** Every previous
performance change in this repo that was guessed instead of measured cost a
device round (Phase 36's three follow-up regressions, Phase 48's blink). 52.2
publishes a table; it does not "optimise".

## Design

### A — The measurement (this is the deliverable)

| # | Metric | Where | How |
|---|---|---|---|
| M1 | Cold-start first paint | `:app` | the `Launch` log line above; the owner's stopwatch (row R4) as the cross-check |
| M2 | Warm-start first paint | `:app` | same line, app already in memory |
| M3 | Scroll jank on a 5,000-line file | `bench` APK | `FrameStats` during a scripted fling (`InputScripts`) |
| M4 | Typing jank (30 s of typing, CodeC Keys **and** system keyboard) | `bench` APK | same |
| M5 | Tab-switch jank | `bench` APK | same |
| M6 | Terminal output burst (a command printing 5,000 lines) | `bench` APK | same |

Each is recorded **before** (on the merged phase-51 build) and **after**, in a
table in this part doc, with the device, OS and build id next to it. No
device detail, no number — the repo's own law (Phase 48: *"no per-row details
supplied, none invented"*).

### B — The budget (a number the code can be held to)

```kotlin
object FrameBudget {                       // pure; the policy, not the metric
    const val TARGET_MS = 16L              // 60 Hz
    const val JANK_MS = 32L                // ≥2 missed frames
    fun verdictFor(frameMs: Long): FrameVerdict = when {
        frameMs <  TARGET_MS -> FrameVerdict.SMOOTH
        frameMs <  JANK_MS   -> FrameVerdict.SLOW
        else                 -> FrameVerdict.JANK
    }
    fun jankPercent(frames: List<Long>): Int      // % of frames at or over JANK_MS
}
object SpeedVerdict {
    fun firstPaintVerdict(ms: Long): PaintVerdict  // INSTANT < 500 · OK < 1500 · SLOW
}
```

The budget is a **statement**, not an aspiration: whatever the numbers are,
they get published. If a number is bad, it becomes a *new* phase row with its
own evidence — not a silent tweak inside 52.2.

### C — The only two code changes this part is allowed to make *after* measuring

1. **Skeletons instead of blanks** in the three remaining loading surfaces
   (file tree, git screens, package list) — 51.3 already did the hub; a blank
   frame is read as "slow" even when it is not (dossier §3.6: perceived speed).
2. **A `derivedStateOf` / `remember` audit of the six core surfaces** — only
   where the measurement (M3-M6) names a specific screen, and only with a
   before/after table. No speculative refactor.

Anything else (text engine, terminal rendering, I/O) is explicitly deferred:
Phase 36 tuned the terminal, 48.1 fixed the editor's worst path, and both are
device-tested.

## The Android edge

- `bench` is a **separate APK** (`com.codeci.bench`); nothing it contains ships
  in `:app` (`rule.md`'s Phase 28.1 note). Adding a candidate file there is
  free.
- The first-paint timestamp must not become a new permission, a new Settings
  row or a new dependency: it is one `AppLogger.i` line, read from
  Developer Options like Phase 49.2's back log.
- Do **not** add `StrictMode`, `Choreographer` or a frame callback to `:app`
  for continuous monitoring — that is instrumentation in a shipping build, and
  the bench APK exists precisely so `:app` stays clean.

## Exit condition

M1-M6 are recorded in this part doc, before and after, with the device and
build id; `FrameBudget` and `SpeedVerdict` are pinned by tests; the three
remaining loading surfaces have a skeleton branch (source pins); and
`DEVICE_ROUND.md` R4-R8 has been run by the owner (including the stopwatch
cross-check of M1).

## Tests (plan)

- `FrameBudgetTest` (~9): the thresholds; the verdict boundaries; `jankPercent`
  with an empty list (0, not a crash) and with all-smooth input (0).
- `SpeedVerdictTest` (~6): instant / ok / slow boundaries; a negative or absurd
  value is not reported as instant.
- `SkeletonCoverageTest` (~7, source scan): the file tree, the git screens and
  the package list each have a loading branch; each skeleton declares the same
  item count as its loaded list (scroll-position safety — 51.3's rule).
- `LaunchLogTest` (~4, source scan): the `Launch` log line exists, is written
  once per launch, and carries the route; no new permission or dependency was
  added for it.

≈26 cases, host-JVM.

## Sources (record)

- `bench/` source listing above (2026-09-13, `main` @ `62cfe7b`).
- `PHASE50_52_UX_RESEARCH.md` §2.6 (the instruments exist), §3.6.
- Phase 49.2 (`AppLogger.i("Back", …)` + Developer Options → View App Logs is
  the precedent for an in-build diagnostic the owner can read).
- Phases 36 and 48.1: the cautionary tales (tuning without measurement cost a
  device round each).

## Deferred / rejected with reasons

- **Continuous frame monitoring in `:app`** — instrumentation in a shipping
  build; the bench APK is the right place.
- **Rewriting the text engine / terminal renderer for speed** — 48.1 and 36
  already own those, and both are device-tested; a rewrite needs its own row
  with its own evidence.
- **Baseline Profiles / `baselineProfile` gradle plugin** — a build-graph change
  to a `targetSdk 28` compatibility build, for a win this part has not
  measured; revisit if M1 is bad on real devices.
- **Startup trimming (lazy `TerminalViewModel`)** — Phase 44 deliberately
  builds it at `MainActivity.kt:629` for the whole activity; changing that
  changes setup visibility, which is a Phase 44 decision, not a speed tweak.
