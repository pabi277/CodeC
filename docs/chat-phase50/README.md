# CodeC Phase 50 — The cross-device round

> **Status:** 📋 PLANNED · **Cost:** `[client-only]` · **Effort:** S (a day of
> testing) + M (the bugs it finds) · **Owner rows it closes (verbatim):** *"A.
> If the code is very big it's last line go under the keyboard…"*, *"B. My phone
> showing the option when try to close… but in most phone no option"*, and the
> implicit *"it works on mine"* behind all seven rows.

```text
  50.1  DEVICE_MATRIX.md — every fix from 44-49, checked on every device class
```

| Part | Title | Effort | Status |
|---|---|---|---|
| [50.1](DEVICE_MATRIX.md) | The matrix + the record format | S + M | 📋 PLANNED |

---

## Why this phase exists at all

The owner's report is a **single-device** report: *"My phone showing the option…
but in most phone no option."* Four of the seven phases above ship a behaviour
that is only meaningful on hardware — the caret above a keyboard (48), back
behaviour (49), the setup bar under OEM battery management (44), coach-mark
anchor geometry on a small screen (45). None of them can be proven by a JVM
test, and the repository's law is that CI is the test executor of record
(`rule.md` §3, §8). Phase 50 is the bridge: **a written procedure that turns
handset testing into evidence the repo can keep.**

`docs/PHONE_UX_ANALYSIS.md` §12 already states the same principle — *"I could
not run the app: no device, no emulator, no Gradle cache"* — and lists the
checks that remained pending. Phase 50 does them.

## What Phase 50 is

1. **[DEVICE_MATRIX.md](DEVICE_MATRIX.md)** — the device classes, the 40-odd
   checks, and a fixed record format (device, OS, nav mode, RAM, result,
   evidence).
2. **The rule that makes it durable:** each row's result is pasted back into the
   *owning* phase's part file (under a `## Test log` heading), not into a
   scratchpad — so a fix and its proof live in the same file, the way every
   merged phase does today.
3. **A CI-side half that *can* run headless** — so the matrix is not pure
   manual labour:
   - pure-policy unit tests already written in 44-49 (they run on every PR);
   - **screenshot tests via Paparazzi** for the *static* parts of the new UI: the
     setup bar in each verdict state, the coach-mark overlay geometry, the
     single-file status bar, the drawer's PROJECTS section. Paparazzi renders
     layouts on the JVM with no device and is the standard for exactly this. It
     is a **new test dependency**, so it is listed as a decision for the owner
     (below), not assumed.
   - **Robolectric** for the back stack and the exit-prompt states, if the
     existing test harness tolerates it (`ExitSurveyTest` and the settings tests
     already run on the JVM; adding Robolectric is an additive
     `testImplementation`).

## The honest limits, stated up front

- **No emulator in this environment.** Anything about IME insets, real keyboard
  behaviour, OEM battery management or predictive-back gestures **cannot be
  verified here** and must be run on the owner's handsets. The matrix exists so
  that this is a scheduled task with a record, not a hope.
- **Screenshot tests do not prove insets.** Paparazzi renders a fixed window;
  the caret-above-keyboard fix (48) is precisely about a window that changes
  size under a real IME. Paparazzi can pin the *layout* of the new chrome, and
  the matrix pins the *behaviour*.
- **One device class is not enough for cause C in 49.2** (gesture-nav home
  swipe). The matrix requires at least one 3-button-nav device *and* one
  gesture-nav device for the back rows.

## Decision for the owner (asked, not assumed)

| Option | Cost | Verdict |
|---|---|---|
| **Manual matrix only** (no new dependency) | 0 | ✅ default — start here |
| Add **Paparazzi** (`testImplementation`) for static layouts | one dependency, JVM-only, no runtime cost | recommended *if* the owner wants layout regressions caught in CI; it is the only way 44.1's setup-bar states and 45.2's overlay get an automated check |
| Add **Robolectric** for back-stack behaviour | one dependency, slower JVM tests | optional; the source-scan pins in 49.1 cover most of the risk without it |

No new dependency is added without the owner saying so — `rule.md` §6.

## Exit condition

```text
1. DEVICE_MATRIX.md filled in for at least: one small gesture-nav phone
   (≤6.1", low RAM), one large 3-button-nav phone, one tablet/foldable if
   available, and one Android 13+ device (so the FGS/notification behaviour in
   44 is checked where the platform is strictest).
2. Every row has a result and, for failures, an evidence note
   (log line / screenshot name / device).
3. Every failure has either a fix in the same phase's part file or a dated
   deferral with a reason.
4. Each part file from 44-49 carries a `## Test log` section with its own rows
   pasted in.
5. `docs/PHONE_UX_ANALYSIS.md`'s pending checks are marked done or superseded.
PASS = 1-5 with zero open failures that are not explicitly deferred.
```

## Sources

- `docs/PHONE_UX_ANALYSIS.md` §12 (the same limitation, stated earlier) and §8
  (the five phone-specific risks this matrix is designed to catch).
- `rule.md` §3 (CI is the test executor of record), §8 (verification law), §4.2
  (evidence before change).
- **Format precedent:** `docs/chat-phase40/DEVICE_TEST_PLAN.md` (204 lines, the
  full runbook shape — branch, functional round, green CI run IDs, "PASS looks
  like" rows with the exact on-screen text) and
  `docs/chat-phase41/DEVICE_TEST_PLAN.md` (68 lines, the leaner round format).
  `DEVICE_MATRIX.md` follows the same table columns so the three read as one
  series.
- `ui/utils/DeviceDiagnostics.kt:20-130` (`abiSummary`, `isArm64`,
  `isLikelyEmulator`, `osSummary`, `summary(dataDir)`) — the device-info line
  the matrix asks the tester to paste instead of guessing the model.
- Android platform: FGS + `POST_NOTIFICATIONS` behaviour on 13+
  (`MainActivity.kt:429-441`, `AndroidManifest.xml:16-17,113-118`) — the reason
  an Android 13+ device is mandatory in the matrix even though `targetSdk = 28`.
