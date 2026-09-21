package com.codeci.ide.ui.crash

/**
 * Phase 51.1 — when the splash screen is allowed to leave.
 *
 * The evidence this replaces (2026-09-21, `main` @ `0f1b650`): `res/values/themes.xml`
 * was one line — `<style name="Theme.MyApplication" parent="android:Theme.DeviceDefault.NoActionBar" />`
 * — and `grep -rn "SplashScreen\|windowSplashScreen\|postSplashScreen"` found
 * **zero** hits, so the first thing a user met on every cold start was the
 * system's black window while `MainActivity.onCreate` built the world
 * (`TerminalViewModel` at `MainActivity.kt:629`, the startup ledger, the TempGc
 * sweep and the setup-recovery thread).
 *
 * The rule, and the only rule: **the splash leaves when the app is ready, never
 * later.** There is deliberately no minimum display time — a logo that waits to
 * be admired steals a second from every launch, which is the opposite of the
 * perceived-speed work 52.2 measures.
 *
 * Two facts can keep it up (the theme decision and the first route), and two
 * facts beat all of them: a crash overlay the owner is waiting for and a
 * [SafeMode] start must never be hidden behind a logo (Phase 42.3's overlay is
 * the door out of a crash loop — a splash on top of it is a locked door).
 *
 * `LaunchReadiness` is pure Kotlin; [LaunchGate] is the thin mutable holder the
 * activity writes and `SplashScreen.setKeepOnScreenCondition` reads. Both run on
 * a host JVM, which is where `LaunchReadinessTest` pins them.
 */
data class LaunchFacts(
    /** The 50.2 brand/dynamic-colour decision has been made for this start. */
    val themeResolved: Boolean = false,
    /** The first route is known (welcome / guide / resume / hub). */
    val startRouteKnown: Boolean = false,
    /** The crash-report overlay is on screen and must not be covered. */
    val crashOverlay: Boolean = false,
    /** This start is the reduced safe-mode start (Phase 42.3). */
    val safeMode: Boolean = false,
)

object LaunchReadiness {

    /**
     * True while the splash must stay on screen. Never a timer: the answer is
     * derived from facts, so the same facts always give the same answer and a
     * slow device simply keeps its (already drawn) splash a little longer
     * instead of flashing a half-built UI.
     */
    fun keepSplash(f: LaunchFacts): Boolean =
        !(f.crashOverlay || f.safeMode) && !(f.themeResolved && f.startRouteKnown)

    /**
     * True when the splash may leave because the app is genuinely ready — the
     * exact complement of [keepSplash] for a start that is not being covered.
     * Kept as its own function because call sites read better than `!keep`.
     */
    fun ready(f: LaunchFacts): Boolean = !keepSplash(f)
}

/**
 * The one mutable cell between the activity and the splash condition. Written
 * from the composition (theme decided, route decided, overlay shown) and read
 * from the platform's polling condition, hence `@Volatile` and no locks.
 */
class LaunchGate(initial: LaunchFacts = LaunchFacts()) {

    @Volatile
    var facts: LaunchFacts = initial
        private set

    /** The theme decision is made (the first composition has resolved it). */
    fun themeResolved() {
        facts = facts.copy(themeResolved = true)
    }

    /** The first route is known (the launch-state read has finished). */
    fun startRouteKnown() {
        facts = facts.copy(startRouteKnown = true)
    }

    /** The crash overlay is on screen (Phase 42.3) — it wins over the splash. */
    fun overlayShown(shown: Boolean) {
        facts = facts.copy(crashOverlay = shown)
    }

    fun keepSplash(): Boolean = LaunchReadiness.keepSplash(facts)
}
