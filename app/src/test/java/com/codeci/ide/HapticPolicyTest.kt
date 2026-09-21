package com.codeci.ide

import com.codeci.ide.ui.components.HapticInput
import com.codeci.ide.ui.components.HapticMoment
import com.codeci.ide.ui.components.HapticPolicy
import com.codeci.ide.ui.components.HapticStrength
import com.codeci.ide.ui.components.RunHapticRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 51.4 — exactly eight moments, two strengths, and three guards that
 * silence everything: a missing moment, the Settings switch, and a device with
 * no vibrator.
 */
class HapticPolicyTest {

    @Test
    fun `there are exactly eight moments, and these are they`() {
        val expected = listOf(
            "RUN_STARTED",
            "PROGRAM_FINISHED",
            "PROGRAM_FAILED",
            "FILE_SAVED",
            "INSTALL_FINISHED",
            "TAB_CLOSED",
            "PROJECT_OPENED",
            "DRAG_STARTED",
        )
        assertEquals(8, HapticMoment.entries.size)
        assertEquals(expected, HapticMoment.entries.map { it.name })
        assertEquals(8, HapticPolicy.moments.size)
    }

    @Test
    fun `only a failure, a close and a finish are firm`() {
        assertEquals(
            setOf(
                HapticMoment.PROGRAM_FAILED,
                HapticMoment.INSTALL_FINISHED,
                HapticMoment.TAB_CLOSED,
            ),
            HapticPolicy.FIRM,
        )
    }

    @Test
    fun `every moment has a strength when enabled`() {
        for (moment in HapticMoment.entries) {
            assertEquals(
                "$moment must be felt",
                if (HapticPolicy.isFirm(moment)) HapticStrength.FIRM else HapticStrength.LIGHT,
                HapticPolicy.performFor(HapticInput(moment = moment)),
            )
        }
    }

    @Test
    fun `starting a run is light, failing is firm`() {
        assertEquals(
            HapticStrength.LIGHT,
            HapticPolicy.performFor(HapticInput(moment = HapticMoment.RUN_STARTED)),
        )
        assertEquals(
            HapticStrength.FIRM,
            HapticPolicy.performFor(HapticInput(moment = HapticMoment.PROGRAM_FAILED)),
        )
    }

    @Test
    fun `the switch silences all eight`() {
        for (moment in HapticMoment.entries) {
            assertNull(
                "$moment must be silent with haptics off",
                HapticPolicy.performFor(HapticInput(moment = moment, enabledInSettings = false)),
            )
        }
    }

    @Test
    fun `a device without a vibrator gets nothing`() {
        for (moment in HapticMoment.entries) {
            assertNull(
                "$moment must be silent without a vibrator",
                HapticPolicy.performFor(HapticInput(moment = moment, hasVibrator = false)),
            )
        }
    }

    @Test
    fun `no moment means no feedback`() {
        assertNull(HapticPolicy.performFor(HapticInput(moment = null)))
    }

    @Test
    fun `off beats firm`() {
        // A guard is checked before the mapping: "off" cannot be bypassed by a
        // moment that happens to be firm.
        assertNull(
            HapticPolicy.performFor(
                HapticInput(
                    moment = HapticMoment.PROGRAM_FAILED,
                    enabledInSettings = false,
                    hasVibrator = true,
                )
            )
        )
    }

    @Test
    fun `isFirm is total over the eight`() {
        for (moment in HapticMoment.entries) {
            assertEquals(moment in HapticPolicy.FIRM, HapticPolicy.isFirm(moment))
        }
        assertFalse(HapticPolicy.isFirm(null))
    }

    @Test
    fun `a run that starts is felt once`() {
        assertEquals(
            HapticMoment.RUN_STARTED,
            RunHapticRule.momentFor(wasRunning = false, running = true, exitCode = null),
        )
    }

    @Test
    fun `a run that succeeds and one that fails are two different moments`() {
        assertEquals(
            HapticMoment.PROGRAM_FINISHED,
            RunHapticRule.momentFor(wasRunning = true, running = false, exitCode = 0),
        )
        assertEquals(
            HapticMoment.PROGRAM_FAILED,
            RunHapticRule.momentFor(wasRunning = true, running = false, exitCode = 1),
        )
    }

    @Test
    fun `the first observation of a screen owes nothing`() {
        assertNull(RunHapticRule.momentFor(wasRunning = false, running = false, exitCode = null))
    }

    @Test
    fun `a re-render of the same state is silent`() {
        assertNull(RunHapticRule.momentFor(wasRunning = true, running = true, exitCode = null))
    }

    @Test
    fun `a job that ends without reporting an exit code stays quiet`() {
        assertNull(RunHapticRule.momentFor(wasRunning = true, running = false, exitCode = null))
    }
}
