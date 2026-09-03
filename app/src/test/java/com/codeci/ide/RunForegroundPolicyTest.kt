package com.codeci.ide

import com.codeci.ide.ui.services.RunForegroundPolicy
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Phase 24.2 — the notification threshold is a pure rule. */
class RunForegroundPolicyTest {

    @Test
    fun `hello world does not promote`() {
        assertFalse(RunForegroundPolicy.shouldPromote(0))
        assertFalse(RunForegroundPolicy.shouldPromote(4_999))
    }

    @Test
    fun `long run promotes at five seconds`() {
        assertTrue(RunForegroundPolicy.shouldPromote(RunForegroundPolicy.THRESHOLD_MS))
        assertTrue(RunForegroundPolicy.shouldPromote(60_000))
    }
}
