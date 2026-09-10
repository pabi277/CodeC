package com.codeci.ide

import com.codeci.ide.ui.crash.CrashLog
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 41.2 — the shared crash-record reader. `CrashLog.newestRecord` is
 * the ONE read of `filesDir/crash-log.txt` (CrashReportOverlay + the
 * feedback section both call it), so its semantics — newest record FROM ITS
 * HEADER, never a byte tail, absent/empty/blank files are "nothing to
 * show" — are pinned here. Plain `java.io` temp files, no JUnit rules: the
 * same tests run on the host pre-validation harness and in CI.
 */
class CrashLogTest {

    private fun tempDir(): File =
        java.nio.file.Files.createTempDirectory("codec-crashlog-").toFile()

    private fun cleanup(dir: File) {
        dir.deleteRecursively()
    }

    @Test
    fun `absent or empty file means no record`() {
        val dir = tempDir()
        try {
            assertNull(CrashLog.newestRecord(dir))
            val empty = File(dir, CrashLog.FILE_NAME)
            empty.writeText("")
            assertNull(CrashLog.newestRecord(dir))
        } finally {
            cleanup(dir)
        }
    }

    @Test
    fun `a blank-but-nonempty file is nothing to show either`() {
        val dir = tempDir()
        try {
            File(dir, CrashLog.FILE_NAME).writeText("   \n\n  ")
            assertNull(CrashLog.newestRecord(dir))
        } finally {
            cleanup(dir)
        }
    }

    @Test
    fun `records accumulate - the NEWEST one is read from its header`() {
        val dir = tempDir()
        try {
            File(dir, CrashLog.FILE_NAME).writeText(
                "==== 2026-09-09 10:00:00  thread=main ====\n" +
                    "java.lang.IllegalStateException: OLD crash\n" +
                    "    at Old.kt:1\n" +
                    "\n" +
                    "==== 2026-09-10 01:22:11  thread=main ====\n" +
                    "java.lang.RuntimeException: NEW crash\n" +
                    "    at New.kt:2\n"
            )
            val record = CrashLog.newestRecord(dir)
            assertTrue(record != null && record.startsWith("==== 2026-09-10 01:22:11"))
            assertTrue(record!!.contains("java.lang.RuntimeException: NEW crash"))
            assertTrue("the diagnosis line must not be cut off", record.contains("RuntimeException"))
            assertTrue("the OLD record must not leak into the read", !record.contains("OLD crash"))
        } finally {
            cleanup(dir)
        }
    }

    @Test
    fun `a single record is read whole (no leading header marker yet)`() {
        val dir = tempDir()
        try {
            val single = "==== 2026-09-10 01:22:11  thread=main ====\n" +
                "java.lang.RuntimeException: only\n"
            File(dir, CrashLog.FILE_NAME).writeText(single)
            assertEquals(single, CrashLog.newestRecord(dir))
        } finally {
            cleanup(dir)
        }
    }
}
