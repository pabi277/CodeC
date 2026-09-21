# CodeC Phase 51 — The feel: the screens you touch every day

> **Status:** 🚧 **IMPLEMENTED (2026-09-21, `arena/01a0c4cb-codec`, owner:
> "Start phase 51"; CI ✅ GREEN `35630471779` tip `32c7c70` — rounds 1 and 2 red
> for-cause, see §CI below) — all four parts landed in source, 132 new host cases
> green locally and green in CI (assemble + `testDebugUnitTest` + lint);
> device round F1-F16 NOT run; not merged.**
> · **Cost:** `[client-only]` ·
> **Effort:** L · **Owner row (verbatim):** *"it's not attractive to user to use
> multiple time so i want to boost it's ui 100× time"* — the **surfaces** half of
> that sentence (the return half is Phase 52).
> Parent: [`PHASE50_52_ROADMAP.md`](../PHASE50_52_ROADMAP.md). Research dossier:
> [`PHASE50_52_UX_RESEARCH.md`](../PHASE50_52_UX_RESEARCH.md).
> **Depends on:** Phase 50 (tokens, brand, type, motion). Start 51 only after 50
> is merged, or the six surfaces get converted twice.

```text
  51.1  The first ten seconds: a splash that is CodeC, and a first screen with a face
  51.2  The editor surface: RUN ▶ as the hero, chrome with rhythm, a real empty state
  51.3  Hub, Packages, Terminal: cards with identity, skeletons, the install moment
  51.4  Micro-feedback: press states, haptics on the eight moments, confirmations
```

| Part | Title | Effort | Status |
|---|---|---|---|
| [51.1](PART_51_1_FIRST_TEN_SECONDS.md) | Cold start and the first screen | M | 🚧 IMPLEMENTED |
| [51.2](PART_51_2_EDITOR_SURFACE.md) | The editor, with RUN ▶ as the hero | L | 🚧 IMPLEMENTED |
| [51.3](PART_51_3_HUB_PACKAGES_TERMINAL.md) | Hub / Packages / Terminal surfaces | M | 🚧 IMPLEMENTED |
| [51.4](PART_51_4_MICRO_FEEDBACK.md) | Haptics, press states, confirmations | S/M | 🚧 IMPLEMENTED |

Device round: [`DEVICE_ROUND.md`](DEVICE_ROUND.md) (F1-F16, written, not run).

---

## The evidence: what the first session looks like today

Reads and greps on **`main` @ `62cfe7b`, 2026-09-13**.

| # | What the user meets | Evidence | Why it costs a return visit |
|---|---|---|---|
| 1 | A **black (or white) rectangle** for the first moment of every cold start | `app/src/main/res/values/themes.xml` is one line: `<style name="Theme.MyApplication" parent="android:Theme.DeviceDefault.NoActionBar" />`; `grep -rn "SplashScreen\|windowSplashScreen\|postSplashScreen"` → **0** | the first thing a new user sees is not CodeC |
| 2 | A first screen that is **functional and anonymous** | `WelcomeScreen.kt:52-100` — "CodeC" headline, one sentence, then three flat `Card`s (`RoundedCornerShape(16.dp)`, `defaultElevation = 0.dp`), no imagery, no colour beyond the accent | nothing to remember, nothing to feel |
| 3 | **The one action that matters looks like every other button** | `EditorScreen.kt` — RUN ▶ is a text/icon button among the chrome; the editor is 2,423 lines of chrome + a code view | Google's eye-tracking: a bigger, better-contained primary action is found **up to 4× faster** (dossier §3.2) |
| 4 | **Empty and loading states are one line of text** | `strings.xml:206-207, 331, 395, 442`; `FileManagerScreen.kt:1223` (`EmptyProjectsState`); the drawer's two empties (`EditorProjectDrawer.kt:255, 393`) | the moments a user is most unsure are the ones with the least design |
| 5 | **Nothing acknowledges a tap** | haptics exist only in the CodeC keyboard (`CodecKeyboard.kt:255,267`), the terminal bell (`TerminalEmulatorView.kt:126-136`) and the `codec-vibrate` API | a user's finger is never answered by the app's own chrome |
| 6 | Installs (the most anxious moment in the app) end with **terminal text** | Phase 44 owns the setup bar; Packages' rows are `ModulesScreen.kt` (783 lines) | the highest-emotion moment has the least reward |

Why this ordering inside the phase: **51.1 owns the seconds before the first
action, 51.2 owns the action itself, 51.3 makes the rest of the app feel like
the same product, 51.4 answers the finger.** That is the order the user meets
them.

---

## Design, in one page

### 51.1 — The first ten seconds

- **A splash that is CodeC.** One new dependency:
  `androidx.core:core-splashscreen` (Apache-2.0, the platform's own
  backported splash API) — a themed window background using the **existing**
  `ic_launcher_foreground` + the brand surface from 50.2, so the first frame is
  the app's mark on the app's colour instead of the system's black. The splash
  is dismissed by `SplashScreen.setKeepOnScreenCondition` keyed on a **pure**
  `LaunchReadiness` policy (never a hard delay).
- **A first screen with a face.** `WelcomeScreen` keeps its Phase 33.1
  structure (three language tiles — that is Pydroid's pattern and it works) and
  gains: the mark at a real size, the three tiles with **language identity**
  (the existing `StarterIconView` colours: C orange / Python blue / web green)
  on cards that use 51's radius/elevation, one line of "what happens next"
  under each, and a visible "C works offline — no download" reassurance, which
  is the single fact that stops a nervous first-run exit (Phase 44's whole
  problem).

### 51.2 — The editor, with RUN ▶ as the hero

- **RUN ▶ becomes the primary contained action** on the editor: larger, filled
  with the brand container role, above the chrome's visual weight — the one
  change with a measured 4× effect in Google's study.
- **Chrome rhythm:** tab bar, find bar, suggestion strip, status bar and the RUN
  row each get a declared slot in the column (50.1 tokens), so the code view's
  height stops being an accident. **Nothing** in the sora host changes.
- **A real empty state** for "no file open" (today: the tab bar collapses and
  the user is looking at an empty frame): one sentence, one action, the mark.
- **Save feedback** — Phase 46.2's single-file mode writes silently; give it a
  one-word confirmation (51.4's mechanism).

### 51.3 — Hub, Packages, Terminal

- **Project cards get identity** — the existing `ProjectIconView` (initial +
  colour) plus the file-type icon set from Phase 34, arranged with the token
  scale, so the hub reads as a shelf of *your* projects rather than a list.
- **Loading states**: the hub's project load and the file tree get a skeleton
  (`CodecMotion` shimmer), never a blank.
- **The install moment**: a package row's RUNNING → INSTALLED transition gets a
  visible, haptic-marked completion (not a terminal line); failure keeps Phase
  44's one-sentence honesty.

### 51.4 — Micro-feedback

Eight named moments get a haptic and a press state — and nothing else does,
because haptics everywhere is noise:

1. RUN ▶ started · 2. program finished (success) · 3. program failed ·
4. file saved · 5. install finished · 6. tab closed · 7. project opened ·
8. long-press / drag start.

All of it behind a **pure** `HapticPolicy.momentFor(action, settingsEnabled,
deviceSupportsVibrator)`, and behind a Settings switch (haptics on/off) that
defaults to **on** for these eight and never touches the CodeC keyboard's own
setting (`KeysStayPolicy` stays untouched, Phase 47.2).

---

## What this phase must NOT do

- **No new flow, no new screen, no changed navigation.** Phases 46/47/49 own
  that; this phase repaints.
- **No change to the sora editor host, the caret policy, the run pipeline, or
  the chrome lock.** 51.2 is chrome-only, and the editor keeps every Phase
  33/35/44/45/46/47/48 behaviour.
- **No modal nag, ever** (Phases 41/42/45). Confirmations are snackbars and
  states, never dialogs.
- **No second splash delay, no artificial minimum display time** — the splash
  leaves as soon as the app is ready (`LaunchReadiness`).
- **No change to the guide/tour** (Phase 45) — its geometry is device-tested.

## Exit condition

Cold start shows CodeC's own splash (not a black window); the welcome screen and
the editor chrome use 51's tokens throughout; RUN ▶ is measurably the largest,
most contained control on the editor (source pin + device row F6); every list
has a designed empty **and** loading state; the eight moments give a haptic and
the switch turns them off; the **one** new dependency is justified and recorded
with its APK delta; and `DEVICE_ROUND.md` F1-F16 has been run by the owner.

## Tests (plan)

| File | Cases | Pins |
|---|---|---|
| `LaunchReadinessTest` | ~9 | splash leaves on readiness, never on a timer; safe mode / crash overlay still win |
| `WelcomeLayoutTest` (source scan) | ~6 | tiles use tokens; each tile names its language; the offline-C line is present |
| `EditorChromeLayoutTest` (source scan) | ~10 | RUN ▶ is the largest contained control; chrome slots exist; **no** change inside `SoraEditorHost` |
| `EmptyStateTest` (source scan) | ~8 | every list surface has an empty + loading branch |
| `HapticPolicyTest` | ~12 | exactly the eight moments; off switch wins; keyboard setting untouched |
| `HapticWiringTest` (source scan) | ~8 | all haptic calls go through the policy; none in the sora host |

≈53 cases (the plan's file names were split where the decision turned out to have
three owners — see the implementation section below; **132 landed**). The haptic
*feel* is a device row, not a test; the policy is a test, not a feeling.

## Sources (record)

Dossier §2.4 (splash evidence), §2.5, §3.2 (the 4× finding and the containment
mechanism), §3.3 (users naming "little details and haptics"), §3.6 (D1 is
decided in the first session; 3 minutes to first value), §4 (splashscreen
library licence, Lottie rejected).

## Deferred / rejected with reasons

- **Lottie / animated empty-state illustrations** — bytes for decoration; the
  research's win is containment + motion, which 50.4 and 51.2 already deliver.
- **Reordering the bottom bar / a new home tab** — a navigation change; Phase 49
  and 46 own navigation.
- **Redesigning the terminal emulator's rendering** — Phase 36 tuned it for
  speed; only its *chrome* is in scope.
- **A "recent projects" carousel on the welcome screen** — that is 52.1's
  continuity job, and putting it here would duplicate the resume decision.

---

## Implementation (2026-09-21)

**What actually landed**, part by part, with the corrections the code forced.

### 51.1 — the cold start and the first screen

| Piece | Where | Note |
|---|---|---|
| `Theme.Codec.Splash` (parent `Theme.SplashScreen`) | `res/values/themes.xml` | `windowSplashScreenBackground` = `@color/codec_splash_background` (`#FF101418`, the launcher art's own value), `windowSplashScreenAnimatedIcon` = the **existing** `ic_launcher_foreground`, `postSplashScreenTheme` = `Theme.MyApplication` |
| the launcher activity wears it | `AndroidManifest.xml:71` | the `<application>` keeps `Theme.MyApplication` (only the launcher activity splashes) |
| the one new dependency | `gradle/libs.versions.toml` `coreSplashscreen = "1.0.1"` + `app/build.gradle.kts:219` | Apache-2.0, no new licence file (AndroidX is covered by the existing notices) |
| `LaunchFacts` / `LaunchReadiness` / `LaunchGate` | `ui/crash/LaunchReadiness.kt` (new, pure) | `keepSplash` = `!(crashOverlay \|\| safeMode) && !(themeResolved && startRouteKnown)` — the answer is derived from facts, so a slow device keeps its already-drawn splash instead of flashing half-built UI |
| the activity wiring | `MainActivity.kt:247-254, 434, 800` | `installSplashScreen().setKeepOnScreenCondition { launchGate.keepSplash() }` **before** `super.onCreate`; `SafeMode.active` seeds the gate; `overlayShown(startupPlan != NORMAL)` after the ledger; `themeResolved()` from a `SideEffect` in the themed composition; `startRouteKnown()` the moment `routeKnown` is true |
| the welcome repaint | `ui/screens/WelcomeScreen.kt` | the 96 dp `app_mark` (display art, 50.1's own exception), the tagline, a `secondaryContainer` **offline-C badge**, and the tiles on `surfaceContainerHigh` + `Elevation.CARD` with the arrow |
| `nextStep` on each starter | `ui/projects/WelcomeStarters.kt` | one line naming what the tap does **and the entry file it opens** |

**Correction found while wiring (recorded because it would have shipped a bug):**
`routeKnown = firstLaunchComplete != null` alone is **not** enough. The welcome
gate and the guide gate both `return` for a frame while their flags are still
reading, so a splash released on the launch flag alone hands the window to an
empty composition. It now requires **both** families —
`firstLaunchComplete != null && (guideCompleted != null || guideRequested)` —
which is exactly why the fact is named `startRouteKnown` and not
`firstLaunchRead`.

### 51.2 — the editor (chrome only)

- **`RunButtonStyle`** (`ui/editor/RunButtonStyle.kt`, new, pure): `roleFor` is
  lock-first (Phase 44's chrome lock wins over a running job, over a missing
  file, over everything), `showsRunning` is `running && !locked`,
  `isEnabled = locked || hasOpenFile` (a locked tap still runs
  `showChromeLock`, so it is *enabled*), `toneFor` = `PRIMARY_CONTAINER` vs
  `SURFACE_VARIANT`, and `visualFor` is the one call the screen makes.
- **RUN ▶** (`EditorScreen.kt`, the `run_action` slot) is now a contained
  `Button` on `primaryContainer` / `onPrimaryContainer`, `MIN_TOUCH` tall, with
  the `NAV` glyph and its label kept — `RUN`, or `RUNNING` while the job it
  started runs (new string `run_running`). The tour's anchor and its one-tap
  `onGuideRunTap` are untouched; `onRunTap`'s Phase 33 chooser logic is
  untouched; the old bare `.clip(...).clickable(onClick = onRunTap)` row is gone.
- **`EditorChrome`** (`ui/editor/EditorChrome.kt`, new, pure): eight declared
  slots in the screen's own order, `markerFor` → `// Phase 51.2 slot: <name>`,
  and `gapFor` as `CodecTokens.Space` steps. It is a **declaration, not a
  re-flow**: no chrome height changes and nothing moves, because Phase 48's
  caret work was device-proven on the current height. What it buys is that a
  later edit which moves a chrome piece fails `EditorChromeSlotTest` instead of
  quietly changing the code view's height.
- **`EditorEmptyState`** (`ui/editor/EditorEmptyState.kt`, new, pure) + the chip
  in the editor column: with no tab and no project open, one sentence
  (*"Nothing open — this is a scratch file."*) and **exactly one** action —
  *Open my last file* (through the one resume source, `EditorLaunchState`, so
  52.1 keeps owning resume) or *Browse projects*. It renders as chrome, never a
  dialog.

**Correction:** the plan's premise (*"with no file open … the user is looking at
an empty frame"*) is **half right**. `EditorTabBar` really does return early
(`EditorTabBar.kt:61`) and the top bar falls back to a bare file name — but the
code view is never blank: `EditorViewModel.INITIAL_CODE` is a Hello-World
`main.c` and `closeTab` deliberately keeps one buffer alive
(`tabs.size <= 1`). "No tabs" therefore means **scratch mode**, and the honest
deliverable is the thing that was genuinely missing — the chrome never *said* so
and offered no way back into a real file.

### 51.3 — hub, packages, terminal

- **`HubListPolicy`** (`ui/projects/HubListPolicy.kt`, new, pure): three
  branches — `LIST` when there are entries, `EMPTY` once a read has finished,
  `LOADING` otherwise; `rowCount` answers for the skeleton and the list from one
  function (floor `SKELETON_ROWS = 3`, and the last known count is honoured so
  the transition cannot move the scroll position — the Phase 36 bug class);
  `isPlaceholder` marks the non-interactive branch.
- **`FileManagerViewModel`**: `hubListFacts` (`HubListFacts`) + a
  `lastKnownHubRowCount()`, written by the existing `loadProjects`. **Not
  `isBusy`** — that is also true for clone/delete/export and would draw a
  skeleton over perfectly loaded projects.
- **`Skeleton.kt`** (`SkeletonBox` on `CodecMotion.shimmer`, `SkeletonHubCard`)
  + the hub's three-branch `Crossfade` (`FileManagerScreen` :1257 branch,
  :1273 skeleton rows) + the designed empty state (the `app_mark`, the three
  starter tiles, the create door).
- **`InstallMoment`** (`ui/modules/InstallMoment.kt`, new, pure):
  `labelFor` (RETRY beats everything except a working install → OPEN;
  INSTALLING on state *or* a running command; else UPDATE / INSTALL),
  `percentOrNull` coerced to 0-100, `failureKind` → `RETRY_IN_TERMINAL`, and
  **`celebrateOnFinish` = only `NOT_INSTALLED`/`INSTALLING` → `INSTALLED`**.
- **`ModulesScreen`**: the row's state is observable now (`installedNow` /
  `installRequested` / `celebrated`) with a 1.5 s poll that runs **only while the
  user's own install is in flight** and never as a background poll for a
  screenful of rows; a genuine finish fires the `INSTALL_FINISHED` haptic and
  the one-line toast; the badge crossfades on
  `motion.floatOrSnap(CodecMotion.crossfadeSpec)`; the primary button wears
  `installLabelText` and is disabled while in flight. The row still never
  guesses a failure — Phase 44's law stands, the terminal reports.
- **`TerminalIntroPolicy`** (`ui/terminal/TerminalIntro.kt`, new, pure) + the
  strip above the emulator in `TerminalScreen`: `hasOutput` → silent;
  `InstallProgress.inFlight` → INSTALLING; `SetupFacts.usable` → READY; else
  NEEDS_SETUP. The emulator view itself is untouched.

**Corrections:** (1) the planned `reading` flag was redundant — `loadedOnce`
alone answers the branch, and the pair (`entryCount`, `loadedOnce`) is what the
tests pin; (2) `SetupGatePolicy.barVisible` is **not** "an install is running"
(it is also true for a *settled* READY-without-pkg / FAILED / UNSUPPORTED bar),
so the terminal quotes `InstallProgress.inFlight`, the same predicate the setup
bar refuses dismissal on — otherwise a failed setup would be announced as
"installing"; (3) v1 of `celebrateOnFinish` (`previous != INSTALLED`) would have
celebrated an **upgrade** of a package the user already had working; the
predicate is now the genuine nothing→something transition, and a matrix test
counts exactly two true cells out of sixteen.

### 51.4 — micro-feedback

- **`Haptics.kt`** (`ui/components/Haptics.kt`, new, **pure Kotlin** — no
  Compose, no Android, so it runs on the host JVM): `HapticMoment` (the eight),
  `HapticStrength` (two), `HapticInput`, `HapticPolicy.performFor` with three
  guards checked *before* the mapping (no moment / switch off / no vibrator),
  `FIRM = PROGRAM_FAILED, INSTALL_FINISHED, TAB_CLOSED`, and `RunHapticRule` (a
  run's haptic fires on the transition the user caused; the first observation
  owes nothing).
- **`CodecHaptics.kt`** (new) is the **only** adapter: `LIGHT` →
  `TextHandleMove`, `FIRM` → `LongPress`, wrapped in `runCatching`;
  `rememberCodecHaptics()` reads the DataStore switch itself (`initial = true`)
  and `HapticsSupport.hasVibrator` (API-31 `VibratorManager`, API-24
  `Vibrator.hasVibrator()`; no `VibrationEffect` is built anywhere in the app).
- **The switch**: DataStore key `haptics` (default **on**) with
  `hapticsFlow`/`setHaptics`, rendered in Settings → Appearance above the theme
  preview, and audit row 65 in the same commit. The keyboard's own
  `codec_keys_haptics` is untouched — `HapticWiringTest` pins both keys.
- **All eight moments wired**: `RUN_STARTED` / `PROGRAM_FINISHED` /
  `PROGRAM_FAILED` (one `LaunchedEffect(busy, exitCode)` + `RunHapticRule`),
  `FILE_SAVED`, `TAB_CLOSED` (both close paths), `INSTALL_FINISHED`,
  `PROJECT_OPENED`, `DRAG_STARTED` (the tree's long press).
- **`PressableSurface`** (new): token radius + container role + the `MIN_TOUCH`
  floor + a 0.98 press scale on `CodecMotion.effectsSpring` (`indication = null`
  — the scale *is* the feedback). Call sites: the Packages section header
  (where the containment flag keeps a plain label plain) and the hub's
  New-Project sheet rows.
- **Snackbars, never dialogs**: the save path's one-word `Saved`, the install
  finish's one line. No new dialog exists in this phase.

**Correction:** the plan's `HapticPolicy.performFor` returned the *platform*
`HapticFeedbackType`, which would have dragged Android into the policy and cost
the host test. The split is now policy (pure → `HapticStrength`) / adapter
(`CodecHaptics`) — and that split is what `HapticWiringTest` pins, including
that nothing in `app/src/main/java` performs haptic feedback outside
`CodecHaptics.kt` and the pre-existing keyboard.

### The tests that landed (132 cases, 14 classes)

| File | Cases | Pins |
|---|---|---|
| `LaunchReadinessTest` | 12 | splash leaves on readiness, never on a timer; safe mode / crash overlay win; the gate is a function, not a latch |
| `SplashThemeTest` | 8 | the theme, the manifest, the colour, the icon, `postSplashScreenTheme`, the one dependency, and the no-clock pin |
| `WelcomeLayoutTest` | 8 | the mark at display size, the three tiles, `nextStep` naming the entry file, the elevated card roles, the offline-C badge |
| `RunButtonStyleTest` | 9 | the lock wins; a run owns the word; nothing-open is quiet, never hidden; the visual is total |
| `EditorChromeSlotTest` | 9 | eight slots, their order **read from the real file**, the marker per slot, off-scale gaps impossible, RUN contained |
| `EditorEmptyStateTest` | 8 | exactly one action, and it reuses `EditorLaunchState`; the chip is chrome, never a dialog |
| `HubListPolicyTest` | 9 | loading ≠ empty; the branch matrix; the placeholder is not interactive |
| `SkeletonStabilityTest` | 4 | the skeleton and the list answer their count from one function |
| `HubSurfaceTest` | 9 | the three branches render; the skeleton template; the empty state's design; every hub card action still exists |
| `InstallMomentTest` | 14 | the label per state; exactly two celebrating transitions; clamped progress; the failure points at the terminal |
| `TerminalIntroTest` | 9 | the four states from the shared facts; output always wins |
| `TerminalChromeTest` | 7 | the shared facts, the copy, the tokens, and the two do-not-touch files |
| `HapticPolicyTest` | 14 | the eight (by name), the two strengths, three guards, the run rule |
| `HapticWiringTest` | 12 | one platform call site, the adapter's shape, both Settings keys, all eight wired, the animation-free zones stay free |

Plus the Phase 50 pins re-run green against the new tree: `TokenAdoptionTest`,
`TouchTargetTest` (still exactly 16 `IconButton`s — RUN is a `Button` and the
chip a `TextButton`), `MotionWiringTest` (spec constructors live only in
`CodecMotion.kt` — the Crossfade spec is now passed in, not constructed),
`SettingsAuditTest` (the audit table's row count, corrected in the same commit:
its prose said "46 rows" while its own table held 64 — the test reads the
**table**, so the prose had drifted; it now says 65 with row 65 added).

### CI

**✅ Round 3 (`35630471779`, tip `32c7c70`) — GREEN.** Full `Build APK`: assemble
+ `:app:testDebugUnitTest` + `:app:lintDebug`. **APK delta** (the whole phase
plus its one new dependency, against the merged Phase 50 build `0f1b650` on the
same `versionName` 1.3.17):
**debug 25,809,492 B vs 25,735,608 B = +73,884 B (+0.29 %)** ·
**release (measure-only) 6,714,740 B vs 6,687,674 B = +27,066 B (+0.40 %)**.
Both well above Phase 42.2's ~0.1 % noise floor, which is what a new dependency
plus four parts of chrome should look like — and it is the number the roadmap
asked this phase to record.

**Round 2 (`35630097400`, tip `1abceca`) — red for-cause, fixed:**
`:app:compileDebugKotlin` reported `EditorScreen.kt:1710:42 Unresolved reference
'PaddingValues'` — the new contained RUN ▶ passes `contentPadding =
PaddingValues(...)` and the editor had never imported the layout type (its old
run row had no padding value of its own). One import; no behaviour change.

**Round 1 (`35629624183`, tip `b42f106`) — red for-cause, fixed:**
`:app:mergeDebugResources` rejected `strings.xml`'s `terminal_intro_installing`.
AAPT2 refuses a **raw apostrophe** in a string value and reports it as
`Invalid unicode escape sequence in string` — a message that names the wrong
thing entirely. The string now escapes it (`CodeC\'s`), the way every other
apostrophe in this file already does (14 strings were silently fine; this was
the only raw one), and the whole file was re-scanned for raw apostrophes before
the next push. Recorded in `rule.md` §9's lesson list so the next session greps
for it instead of re-reading AAPT2's message.

### What is NOT done

- **The device round**: `DEVICE_ROUND.md` F1-F16 (plus regression F17-F20) is
  written and **not run** — no device transcript exists, so nothing here claims
  device acceptance.
- **The APK delta is now recorded** (round 3 above): +73,884 B / +0.29 % debug,
  +27,066 B / +0.40 % release, for the whole phase including
  `androidx.core:core-splashscreen` 1.0.1. No local build is possible in the
  sandbox (Maven/Gradle hosts are unreachable), so CI is the only measurement.
- **Nothing is merged.** The branch stops at the merge gate (`rule.md` §3).
