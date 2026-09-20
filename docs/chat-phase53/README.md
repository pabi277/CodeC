# CodeC Phase 53 — The cross-device round

> **Status:** ❌ **CANCELLED BY THE OWNER (2026-09-14)** — *"Remove the full
> device cross check phase"*. It was **Phase 50**; it was renumbered to **53**
> and deferred the same morning, and the owner then cancelled it outright. **It
> is not planned, not owed and not scheduled** — nothing in this directory is a
> to-do. The files are kept as history by the owner's choice.
>
> **What replaces it (this is the live answer to "how do we prove it works on a
> phone?"):**
>
> 1. **Per-phase rounds on the owner's own phones** — L1-L12 (50, the look),
>    F1-F16 (51, the feel), R1-R12 (52, the return), one short round when that
>    phase ships, on the handsets he already owns. Same five-column record
>    (`# | Part | Run on | What to do | PASS looks like`), same rule: results are
>    pasted into the **owning part doc** (`## Test log (Phase NN — …)`), never a
>    scratchpad.
> 2. **CI as the executor of record** — `Build APK` = assemble + unit tests +
>    lint (`rule.md` §5), plus the pure policies and wiring pins each phase
>    leaves behind.
> 3. **Roborazzi screenshot goldens in verify mode** (52.4) — the layout/colour
>    regressions a multi-device matrix would have caught by eye.
> 4. **The still-owed 44-49 rows stay owned by their own phases** (44 R1-R8 /
>    D1-D12, 45 G1-G41, the 46/47 re-rounds, 48's eight, 49's ten + eight).
>    They were never part of 53 and are unaffected by this cancellation.
>
> **What is deliberately not claimed:** no device class is proven by CI. IME
> insets, real haptics, OEM battery management and perceived smoothness remain
> handset-only, and the per-phase rounds above are now the only place they get
> proven.
>
> **Cost:** `[client-only]` · **Effort:** S (a day of testing) + M (the bugs it
> finds) · **Owner rows it closes (verbatim):** *"A. If the code is very big
> it's last line go under the keyboard…"*, *"B. My phone showing the option
> when try to close… but in most phone no option"*, and the implicit *"it
> works on mine"* behind all seven rows.
>
> ⚠️ **Before anything merges: a live test depends on the old location of this
> file.** `arena/01a099d8-codec` (tip `69a70ec`, CI ✅ GREEN `34753000709`)
> ships `app/src/test/java/com/codeci/ide/DeviceMatrixTest.kt` — 627 lines
> that *machine-pin* a matrix, hard-coding `MATRIX_PATH =
> "docs/chat-phase50/DEVICE_MATRIX.md"`, requiring `docs/chat-phase50/README.md`
> to link it, and requiring a `## Test log (Phase 50 — the cross-device
> matrix)` section in each of eleven part docs (44.1-49.2). **Since this phase
> is cancelled, that test must be DELETED (or fully repointed and re-scoped)
> before that branch lands on `main` — otherwise `Build APK` goes red on a doc
> path that no longer exists.** (That branch also carries a newer, longer
> version of this matrix: 378 lines, ten rounds A-J.)

```text
  53.1  DEVICE_MATRIX.md — every fix from 44-49, checked on every device class
```

| Part | Title | Effort | Status |
|---|---|---|---|
| [53.1](DEVICE_MATRIX.md) | The matrix + the record format | S + M | 📋 PLANNED |

---

## Why this phase exists at all

The owner's report is a **single-device** report: *"My phone showing the option…
but in most phone no option."* Four of the seven phases above ship a behaviour
that is only meaningful on hardware — the caret above a keyboard (48), back
behaviour (49), the setup bar under OEM battery management (44), coach-mark
anchor geometry on a small screen (45). None of them can be proven by a JVM
test, and the repository's law is that CI is the test executor of record
(`rule.md` §3, §8). Phase 53 is the bridge: **a written procedure that turns
handset testing into evidence the repo can keep.**

`docs/PHONE_UX_ANALYSIS.md` §12 already states the same principle — *"I could
not run the app: no device, no emulator, no Gradle cache"* — and lists the
checks that remained pending. Phase 53 does them.

## What Phase 53 is

1. **[DEVICE_MATRIX.md](DEVICE_MATRIX.md)** — the device classes, the **49
   checks** (A1-A8, B1-B7, C1-C6, D1-D8, E1-E8, F1-F12), and a fixed record
   format (device, OS, nav mode, RAM, result, evidence).
2. **The rule that makes it durable:** each row's result is pasted back into the
   *owning* phase's part file (under a `## Test log` heading), not into a
   scratchpad — so a fix and its proof live in the same file, the way every
   merged phase does today.
3. **A CI-side half that *can* run headless** — so the matrix is not pure
   manual labour. **The toolchain is already in the repo; nothing new is
   needed** (verified 2026-09-12):
   - **The pure-policy tests that 44-49 each write** — they are *planned*, not
     written yet (this is a docs-only plan), but every one of them is a plain
     JVM test, and CI already runs `:app:testDebugUnitTest`
     (`gradle-bootstrap/build.gradle.kts:15`), so they execute on every push the
     moment their phase lands.
   - **Screenshot tests — the stack is declared and applied but unused.**
     Roborazzi **1.59.0** is in the version catalog
     (`gradle/libs.versions.toml:30,75-77,121`), wired as `testImplementation`
     (`app/build.gradle.kts:272-274`) and the Gradle plugin is **applied to
     `:app`** (`app/build.gradle.kts:8`, declared at root
     `build.gradle.kts:5` with `apply false`) — yet `grep -rln roborazzi
     app/src/test` returns **nothing**. Phase 53 should use it for the *static*
     parts of the new UI (the setup bar in each verdict state, the coach-mark
     overlay geometry, the single-file status bar, the drawer's PROJECTS
     section) instead of adding a rival library.
   - **Robolectric 4.16.1 is already a test dependency**
     (`gradle/libs.versions.toml:29`, `app/build.gradle.kts:271`) and **8 test
     files already use `RobolectricTestRunner`** — including
     `EditorLaunchMeasureReproTest`, which drives a real `NavHost` +
     `rememberNavController` through `createComposeRule`. That is the precedent
     for 49's back-stack test, so no harness question is open there.

## The honest limits, stated up front

- **No emulator in this environment.** Anything about IME insets, real keyboard
  behaviour, OEM battery management or predictive-back gestures **cannot be
  verified here** and must be run on the owner's handsets. The matrix exists so
  that this is a scheduled task with a record, not a hope.
- **Screenshot tests do not prove insets.** Roborazzi renders a fixed window;
  the caret-above-keyboard fix (48) is precisely about a window that changes
  size under a real IME. A screenshot can pin the *layout* of the new chrome,
  and the matrix pins the *behaviour*.
- **Roborazzi's verify mode is not yet wired.** CI runs `testDebugUnitTest`
  with no `roborazzi.test.*` property, so a first screenshot test would
  *record* rather than *compare* unless the phase that adds it also sets the
  verify flag (or adds a `verifyRoborazziDebug` step). Recorded here so the
  first author does not believe a green run means a golden was checked.
- **One device class is not enough for cause C in 49.2** (gesture-nav home
  swipe). The matrix requires at least one 3-button-nav device *and* one
  gesture-nav device for the back rows.

## Decision for the owner (asked, not assumed)

**There is nothing to decide about dependencies** — an earlier draft of this
section asked whether to add Paparazzi and Robolectric. That question was
wrong: both capabilities are already in the repo (Roborazzi 1.59.0 applied to
`:app`, Robolectric 4.16.1 with 8 existing users). The only real choice left is
*effort*, and it is the owner's:

| Option | Cost | Verdict |
|---|---|---|
| **Manual matrix only** | 0 | ✅ default — start here; every row is a human check |
| **+ Roborazzi screenshots** for the static chrome (44.1's setup bar, 45.2's overlay, 46.2's status bar, 47.1's drawer) | one golden set per state; the verify-mode wiring noted above | recommended — it is the only automated check those four surfaces will ever get, and the dependency is already paid for |
| **+ a Robolectric back-stack test** (49) | one test class | optional; 49.1's source-scan pin covers most of the risk, but `EditorLaunchMeasureReproTest` proves the harness can do it |

Adding a *new* library would still need the owner's say-so (`rule.md` §6). Using
the ones already declared does not.

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
