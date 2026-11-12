package com.codeci.ide

import com.codeci.ide.ui.terminal.ShellLaunchPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Phase 104.1 — the pure launch decision (host-provable, no Android). */
class ShellLaunchPolicyTest {

    @Test
    fun `a real bash is exec'd directly as a login shell`() {
        val plan = ShellLaunchPolicy.plan("/p/bin/bash", shellIsElf = true, profilePath = "/p/etc/profile")
        assertEquals("/p/bin/bash", plan.file)
        assertEquals(listOf("-bash", "-l"), plan.argv)
        assertTrue(plan.login)
    }

    @Test
    fun `an ELF bash ignores the profile chain entirely`() {
        val plan = ShellLaunchPolicy.plan("/p/bin/bash", shellIsElf = true, profilePath = "/p/etc/profile")
        assertFalse(plan.argv.any { it.contains("profile") })
        assertFalse(plan.argv.contains("-c"))
    }

    @Test
    fun `busybox keeps the sh chain because it is not bash`() {
        val plan = ShellLaunchPolicy.plan("/p/bin/busybox", shellIsElf = true, profilePath = null)
        assertEquals(ShellLaunchPolicy.SYSTEM_SH, plan.file)
        assertEquals(listOf("sh", "-c", "exec '/p/bin/busybox' -i"), plan.argv)
        assertFalse(plan.login)
    }

    @Test
    fun `a bash script shim keeps the sh chain`() {
        val plan = ShellLaunchPolicy.plan("/p/bin/bash", shellIsElf = false, profilePath = "/p/etc/profile")
        assertEquals(ShellLaunchPolicy.SYSTEM_SH, plan.file)
        assertEquals("sh", plan.argv[0])
        assertEquals("-c", plan.argv[1])
        assertEquals(". '/p/etc/profile'; exec '/p/bin/bash' -i", plan.argv[2])
        assertFalse(plan.login)
    }

    @Test
    fun `the chain sources the profile only when it exists`() {
        val without = ShellLaunchPolicy.plan("/system/bin/sh", shellIsElf = true, profilePath = null)
        assertEquals("exec '/system/bin/sh' -i", without.argv[2])
    }

    @Test
    fun `single quotes in a path are escaped for the chain`() {
        val plan = ShellLaunchPolicy.plan("/p/it's/sh", shellIsElf = false, profilePath = null)
        assertEquals("exec '/p/it'\\''s/sh' -i", plan.argv[2])
    }
}
