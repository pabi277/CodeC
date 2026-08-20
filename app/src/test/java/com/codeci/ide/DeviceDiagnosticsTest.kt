package com.codeci.ide

import com.codeci.ide.ui.utils.DeviceDiagnostics
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceDiagnosticsTest {

    private val mounts = """
        /dev/block/sda1 / ext4 rw,seclabel,relatime 0 0
        /dev/block/sda2 /data ext4 rw,seclabel,nosuid,nodev,noexec,relatime 0 0
        /dev/block/sda3 /data/user/0/com.codeci.ide/files ext4 rw,nosuid,nodev,relatime 0 0
        tmpfs /storage/emulated/0 sdcardfs rw,nosuid,nodev,noexec,relatime 0 0
    """.trimIndent()

    @Test
    fun `findMount picks the longest matching prefix`() {
        val mount = DeviceDiagnostics.findMount(
            mounts,
            "/data/user/0/com.codeci.ide/files/CodeC/modules/clang/bin"
        )
        assertEquals("/data/user/0/com.codeci.ide/files", mount?.mountPoint)
        assertFalse(mount!!.isNoExec)
        assertTrue(mount.flags.contains("rw"))
    }

    @Test
    fun `findMount falls back to the parent mount when no exact prefix exists`() {
        val mount = DeviceDiagnostics.findMount(mounts, "/data/user/0/other.app/files")
        assertEquals("/data", mount?.mountPoint)
        // The /data mount is noexec in this fixture -> that's a blocked device.
        assertTrue(mount!!.isNoExec)
    }

    @Test
    fun `findMount matches the exact mount point itself`() {
        val mount = DeviceDiagnostics.findMount(mounts, "/data")
        assertEquals("/data", mount?.mountPoint)
    }

    @Test
    fun `findMount returns null when nothing matches`() {
        assertNull(DeviceDiagnostics.findMount(mounts, "/system/bin/sh"))
    }

    @Test
    fun `splitMountLine handles escaped spaces and tabs`() {
        val line = "/dev/block/sda1 /storage/emulated/0/My\\040Files ext4 rw,nosuid 0 0"
        val fields = DeviceDiagnostics.splitMountLine(line)
        assertEquals("/dev/block/sda1", fields?.get(0))
        assertEquals("/storage/emulated/0/My Files", fields?.get(1))
        assertEquals("ext4", fields?.get(2))
        assertEquals("rw,nosuid", fields?.get(3))
    }

    @Test
    fun `splitMountLine returns null for blank lines`() {
        assertNull(DeviceDiagnostics.splitMountLine(""))
        assertNull(DeviceDiagnostics.splitMountLine("   "))
    }

    @Test
    fun `splitMountLine returns null for malformed short lines`() {
        assertNull(DeviceDiagnostics.splitMountLine("/dev/sda1 /data"))
    }
}
