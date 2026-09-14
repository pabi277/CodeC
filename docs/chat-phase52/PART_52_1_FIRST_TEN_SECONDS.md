# CodeC Phase 52.1 — Cold start and the first screen

> **Status:** 📋 PLANNED · **Cost:** `[client-only]` · **Effort:** M ·
> **New dependency:** `androidx.core:core-splashscreen` (Apache-2.0) — the
> **only** new dependency in the whole 51-53 series.
> **Owner row (verbatim):** *"not attractive … boost it's ui 100×"*, clarified
> as *"the first 10 seconds — it must look gorgeous on open"*.
> Parent: [`README.md`](README.md) ·
> [`PHASE51_53_ROADMAP.md`](../PHASE51_53_ROADMAP.md).

## First move: evidence, not code

```text
$ cat app/src/main/res/values/themes.xml
  <resources>
      <style name="Theme.MyApplication" parent="android:Theme.DeviceDefault.NoActionBar" />
  </resources>

$ grep -rn "SplashScreen\|windowSplashScreen\|postSplashScreen" \
        app/src/main app/src/main/AndroidManifest.xml
  (no matches)
```

So: the launcher activity (`AndroidManifest.xml:64-74`) and the application
(`:50-56`) both use a one-line `DeviceDefault` theme. **There is no splash
screen of any kind** — not the Android 12+ platform splash (which needs a
`postSplashScreenTheme` + the compat library on `minSdk 24`), not an XML window
background, not a Compose first frame.

What the user therefore sees on a cold start, on a dark-mode phone, is:

1. a **black rectangle** (the system window background) while the process
   starts and `MainActivity.onCreate` runs — including `TerminalViewModel`'s
   construction at `MainActivity.kt:629`, which is where the userland check
   begins;
2. then, with no transition, the real UI.

That is the *first impression* the research says is judged in **50 ms**
(dossier §3.1) and it is not CodeC's. It is also the cheapest thing in the whole
series to fix: a themed window background is **one** style and **one** drawable,
and on a warm start it is nearly free.

Second finding — the screen that follows:

```text
ui/screens/WelcomeScreen.kt:52-100
   Text("CodeC", headlineLarge, Bold) · one sentence of copy · one small line
   → WelcomeStarters.starters.forEach { StarterTile(…, Card(shape = RoundedCornerShape(16.dp),
       colors = …surfaceVariant, elevation = cardElevation(defaultElevation = 0.dp))) }
```

The structure is right and is the owner's own Phase 33.1 decision (three
language tiles = Pydroid's pattern). What is missing is **identity**: flat
cards, zero elevation, no mark, no colour beyond the accent, and nothing that
says what happens after the tap.

## Design

### A — `LaunchReadiness`: the splash leaves when the app is ready, never later

```kotlin
data class LaunchFacts(
    val themeResolved: Boolean,     // 51.2 brand/dynamic decision made
    val startRouteKnown: Boolean,   // welcome vs. resume (Phase 53's decision)
    val crashOverlay: Boolean,      // ui/crash/CrashReportOverlay
    val safeMode: Boolean,          // ui/crash/SafeMode
)
object LaunchReadiness {
    fun keepSplash(f: LaunchFacts): Boolean =
        !(f.crashOverlay || f.safeMode) && !(f.themeResolved && f.startRouteKnown)
}
```

- **No minimum display time, ever.** A splash that waits to be admired is a
  splash that steals a second from every launch; that is the opposite of
  perceived speed (53.2 owns the measurement).
- **The crash overlay and safe mode always win** — a splash must never hide a
  crash report the owner is waiting for (`MainActivity.kt:338`).
- Wiring: `installSplashScreen()` + `setKeepOnScreenCondition { … }` reading
  the policy; the pure policy is testable without Android.

### B — the splash itself

A `Theme.Codec.Splash` style (`postSplashScreenTheme` → the existing
`Theme.MyApplication`) with:
- `windowSplashScreenBackground` = the **brand surface** from 51.2 (dark/light
  variants, no new colour literals — `CodecPalette` / the ramp supply it);
- `windowSplashScreenAnimatedIcon` = the existing
  `drawable/ic_launcher_foreground` (Phase 38's adaptive icon work — **no new
  asset**);
- the platform's own icon animation on Android 12+, and a plain static mark on
  Android 7-11 via `core-splashscreen`'s backport.

### C — the first screen gets a face

`WelcomeScreen` keeps its three tiles and gains:

| Change | Reason |
|---|---|
| The mark (`drawable/app_mark.xml`, already in `res/`) at a real size above the wordmark | the app has a logo and never shows it |
| Tiles on **elevated, token-shaped** cards (51.1) with the language's own colour carried by the existing `StarterIconView` | identity + hierarchy; today every tile is the same flat `surfaceVariant` |
| One line of *"what happens next"* under each tile (e.g. *"creates a project and opens main.py"*) | uncertainty is the #1 first-run exit |
| The offline-C line promoted to a visible reassurance badge | Phase 44's entire problem is a user who closes the app during a download; telling them C needs nothing prevents the panic |
| Nothing else. No illustration, no carousel, no second button | the three tiles are the owner's Phase 33.1 decision and they work |

Copy changes go through `strings.xml` in the same commit so
`SettingsAuditTest`-style string pins and lint stay green.

## The Android edge

- `core-splashscreen` requires a `compileSdk` ≥ 31 (CodeC compiles against 36)
  and works down to API 21 — **no `minSdk` change** (it is 24).
- The style must be applied to the **launcher activity**
  (`AndroidManifest.xml:68`), not only the application, or nothing shows.
- `targetSdk = 28` is untouched; the compat library exists precisely for this.
- **Do not** touch `MainActivity.onCreate`'s ordering: `TerminalViewModel` is
  built at `:629` for the whole activity (Phase 44's reason), and the splash
  must not delay it — the splash only covers the window, not the work.

## Exit condition

`Theme.Codec.Splash` exists and is the launcher activity's theme;
`LaunchReadiness.keepSplash` is pinned case by case (including crash-overlay and
safe-mode wins); a cold start on a dark-mode phone shows **the CodeC mark on the
brand colour** — verified by the owner on a device (row F1) — and the splash is
gone as soon as the first route is composed (row F2: no perceptible wait, and
the owner's own stopwatch in 53.2's R4 records the number).

## Tests (plan)

- `LaunchReadinessTest` (~9): keeps the splash until theme + route are known;
  releases it in a crash; releases it in safe mode; never depends on time.
- `SplashThemeTest` (~5, source scan): the manifest's launcher activity names
  the splash theme; the splash style defines the animated icon and the
  background; `postSplashScreenTheme` points at the existing app theme; **no**
  new colour literal outside `CodecPalette`.
- `WelcomeScreenTest` (~6, source scan + copy pins): three tiles, each naming
  its language; each tile has a "what happens next" line; the offline-C
  reassurance is present; tiles use `CodecTokens`.

≈20 cases, host-JVM.

## Sources (record)

- `PHASE51_53_UX_RESEARCH.md` §2.4 (the evidence above), §3.1 (50 ms), §3.6
  (first-session value), §4 (splashscreen licence).
- `docs/JOURNEY.md` Phase 33.1 (the three-tile welcome is the owner's
  decision), Phase 38 (the adaptive icon), Phase 44 (why the offline-C line
  matters).
- `developer.android.com/develop/ui/views/launch/splash-screen` (the compat
  API) — verify the exact attribute names against the pinned library version
  before writing the style; the part doc records the version used.

## Deferred / rejected with reasons

- **An animated logo (Lottie / AVD)** — bytes and a dependency for one second
  of screen time; the platform splash already animates the icon on 12+.
- **A branded loading screen with progress** — Phase 44's setup bar already owns
  progress-telling, and two progress surfaces would compete.
- **A minimum splash duration** — rejected on principle (see the design).
- **Re-ordering or re-wording the three starters** — Phase 33.1's decision.
