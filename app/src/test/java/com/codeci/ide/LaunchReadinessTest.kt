package com.codeci.ide

import com.codeci.ide.ui.crash.LaunchFacts
import com.codeci.ide.ui.crash.LaunchGate
import com.codeci.ide.ui.crash.LaunchReadiness
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 51.1 — the splash leaves when the app is ready, and for no other
 * reason. No timer exists in the policy at all (the source pin lives in
 * [SplashThemeTest]): the answer is a function of facts, so a slow device
 * simply keeps its already-drawn splash a little longer instead of flashing
 * half-built UI.
 */
class LaunchReadinessTest {

    @Test
    fun `a cold start keeps the splash`() {
        assertTrue(LaunchReadiness.keepSplash(LaunchFacts()))
    }

    @Test
    fun `the theme decision alone is not enough`() {
        assertTrue(LaunchReadiness.keepSplash(LaunchFacts(themeResolved = true)))
    }

    @Test
    fun `the first route alone is not enough`() {
        assertTrue(LaunchReadiness.keepSplash(LaunchFacts(startRouteKnown = true)))
    }

    @Test
    fun `theme and route together release the splash`() {
        assertFalse(
            LaunchReadiness.keepSplash(
                LaunchFacts(themeResolved = true, startRouteKnown = true)
            )
        )
    }

    @Test
    fun `a crash overlay releases the splash at once, even before the theme`() {
        assertFalse(LaunchReadiness.keepSplash(LaunchFacts(crashOverlay = true)))
    }

    @Test
    fun `safe mode releases the splash at once`() {
        assertFalse(LaunchReadiness.keepSplash(LaunchFacts(safeMode = true)))
    }

    @Test
    fun `safe mode wins even when the theme and route are still unknown`() {
        val facts = LaunchFacts(
            themeResolved = false,
            startRouteKnown = false,
            crashOverlay = true,
            safeMode = true,
        )
        assertFalse(LaunchReadiness.keepSplash(facts))
    }

    @Test
    fun `ready is the exact complement of keepSplash`() {
        val flags = listOf(false, true)
        for (theme in flags) {
            for (route in flags) {
                for (crash in flags) {
                    for (safe in flags) {
                        val facts = LaunchFacts(theme, route, crash, safe)
                        assertEquals(
                            "ready must be !keepSplash for $facts",
                            !LaunchReadiness.keepSplash(facts),
                            LaunchReadiness.ready(facts),
                        )
                    }
                }
            }
        }
    }

    @Test
    fun `the policy is derived, never latched`() {
        // An overlay that appears and is dismissed must not leave the splash
        // "already released" if the route is still unknown: the same facts
        // always give the same answer.
        assertFalse(LaunchReadiness.keepSplash(LaunchFacts(crashOverlay = true)))
        assertTrue(LaunchReadiness.keepSplash(LaunchFacts(crashOverlay = false)))
    }

    @Test
    fun `the gate starts from the facts it was given`() {
        val safeGate = LaunchGate(LaunchFacts(safeMode = true))
        assertFalse(safeGate.keepSplash())
        val normalGate = LaunchGate()
        assertTrue(normalGate.keepSplash())
    }

    @Test
    fun `the gate only releases when both of its facts have landed`() {
        val gate = LaunchGate()
        gate.themeResolved()
        assertTrue("one fact is not two", gate.keepSplash())
        gate.startRouteKnown()
        assertFalse(gate.keepSplash())
    }

    @Test
    fun `the gate's overlay fact is written by the activity, not by the clock`() {
        val gate = LaunchGate()
        gate.themeResolved()
        gate.overlayShown(true)
        assertFalse(gate.keepSplash())
        // The overlay is dismissed: the still-unknown route keeps the splash
        // honest again (the policy is a function, not a latch).
        gate.overlayShown(false)
        assertTrue(gate.keepSplash())
    }
}
