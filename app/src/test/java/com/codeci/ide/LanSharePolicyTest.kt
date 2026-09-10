package com.codeci.ide

import com.codeci.ide.ui.services.LanSharePolicy
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 37.1 — LAN sharing is opt-in. This test pins the security posture the
 * spec asks for: off by default, and a flip reports whether anything changed so
 * callers only restart a server when the switch actually moved.
 *
 * It also keeps the app-wide switch honest for the two surfaces that read it
 * (the Output Panel share row and the Web Preview address bar) — if a future
 * change defaults it to on, this fails.
 */
class LanSharePolicyTest {

    @Test
    fun `the app default is off`() {
        assertFalse(LanSharePolicy().isEnabled())
        assertFalse("the shared switch must stay off by default", LanSharePolicy.shared.isEnabled())
    }

    @Test
    fun `set reports a real change only`() {
        val policy = LanSharePolicy()
        assertTrue(policy.set(true))
        assertTrue(policy.isEnabled())
        assertFalse(policy.set(true))
        assertTrue(policy.set(false))
        assertFalse(policy.isEnabled())
        assertFalse(policy.set(false))
    }

    @Test
    fun `toggle flips and returns the new state`() {
        val policy = LanSharePolicy()
        assertTrue(policy.toggle())
        assertFalse(policy.toggle())
    }
}
