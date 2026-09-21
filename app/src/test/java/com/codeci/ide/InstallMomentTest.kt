package com.codeci.ide

import com.codeci.ide.ui.modules.InstallFacts
import com.codeci.ide.ui.modules.InstallFailure
import com.codeci.ide.ui.modules.InstallLabel
import com.codeci.ide.ui.modules.InstallMoment
import com.codeci.ide.ui.modules.PkgState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 51.3 — the install moment: what a package row says at each transition,
 * and when a finish is worth marking. The celebration is deliberately as strict
 * as Phase 44's `SetupLockPolicy`: a re-render is not news and an upgrade of a
 * working tool is not news.
 */
class InstallMomentTest {

    @Test
    fun `a package that is not installed offers INSTALL`() {
        assertEquals(
            InstallLabel.INSTALL,
            InstallMoment.labelFor(InstallFacts(state = PkgState.NOT_INSTALLED)),
        )
    }

    @Test
    fun `an install in flight says INSTALLING`() {
        assertEquals(
            InstallLabel.INSTALLING,
            InstallMoment.labelFor(InstallFacts(state = PkgState.INSTALLING, running = true)),
        )
    }

    @Test
    fun `a running command is enough to say INSTALLING`() {
        assertEquals(
            InstallLabel.INSTALLING,
            InstallMoment.labelFor(InstallFacts(state = PkgState.NOT_INSTALLED, running = true)),
        )
    }

    @Test
    fun `an installed package offers OPEN`() {
        assertEquals(
            InstallLabel.OPEN,
            InstallMoment.labelFor(InstallFacts(state = PkgState.INSTALLED)),
        )
    }

    @Test
    fun `an upgradable package offers UPDATE`() {
        assertEquals(
            InstallLabel.UPDATE,
            InstallMoment.labelFor(InstallFacts(state = PkgState.UPGRADABLE)),
        )
    }

    @Test
    fun `a failure beats every other word`() {
        for (state in listOf(PkgState.NOT_INSTALLED, PkgState.INSTALLING, PkgState.UPGRADABLE)) {
            assertEquals(
                "failed $state must offer RETRY",
                InstallLabel.RETRY,
                InstallMoment.labelFor(InstallFacts(state = state, failed = true)),
            )
        }
    }

    @Test
    fun `a failure never renames a working install`() {
        assertEquals(
            InstallLabel.OPEN,
            InstallMoment.labelFor(InstallFacts(state = PkgState.INSTALLED, failed = true)),
        )
        assertNull(InstallMoment.failureKind(InstallFacts(state = PkgState.INSTALLED, failed = true)))
    }

    @Test
    fun `the failure sentence tells the user where to look`() {
        assertEquals(
            InstallFailure.RETRY_IN_TERMINAL,
            InstallMoment.failureKind(InstallFacts(state = PkgState.NOT_INSTALLED, failed = true)),
        )
        assertNull(InstallMoment.failureKind(InstallFacts(state = PkgState.NOT_INSTALLED)))
    }

    @Test
    fun `a real finish is celebrated`() {
        assertTrue(InstallMoment.celebrateOnFinish(PkgState.NOT_INSTALLED, PkgState.INSTALLED))
        assertTrue(InstallMoment.celebrateOnFinish(PkgState.INSTALLING, PkgState.INSTALLED))
    }

    @Test
    fun `a re-render of an installed row is not news`() {
        assertFalse(InstallMoment.celebrateOnFinish(PkgState.INSTALLED, PkgState.INSTALLED))
    }

    @Test
    fun `upgrading a working package is not a finish`() {
        assertFalse(InstallMoment.celebrateOnFinish(PkgState.UPGRADABLE, PkgState.INSTALLED))
    }

    @Test
    fun `exactly one transition in the whole matrix celebrates`() {
        val states = PkgState.entries
        var celebrations = 0
        for (previous in states) {
            for (now in states) {
                if (InstallMoment.celebrateOnFinish(previous, now)) celebrations++
            }
        }
        // NOT_INSTALLED → INSTALLED and INSTALLING → INSTALLED: two.
        assertEquals(2, celebrations)
    }

    @Test
    fun `progress is clamped to the percentage it claims to be`() {
        assertEquals(0, InstallMoment.percentOrNull(InstallFacts(progress = -5)))
        assertEquals(100, InstallMoment.percentOrNull(InstallFacts(progress = 250)))
        assertEquals(42, InstallMoment.percentOrNull(InstallFacts(progress = 42)))
    }

    @Test
    fun `a package manager that reports nothing shows nothing`() {
        assertNull(InstallMoment.percentOrNull(InstallFacts(progress = null)))
    }
}
