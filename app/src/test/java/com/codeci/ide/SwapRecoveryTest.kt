package com.codeci.ide

import com.codeci.ide.ui.terminal.SetupLedger
import com.codeci.ide.ui.terminal.SetupPhase
import com.codeci.ide.ui.terminal.SetupRecovery
import com.codeci.ide.ui.terminal.SetupRecoveryGate
import com.codeci.ide.ui.terminal.SetupResume
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * Phase 44.2 — the money test (spec:
 * docs/chat-phase44/PART_44_2_ATOMIC_SETUP.md, "Tests").
 *
 * Real temp directories, one per kill point of
 * `UserlandInstaller.swapPrefix`'s two renames. The owner's bug is the middle
 * row: a kill between `usr → usr.old-<ts>` and `staging → usr` leaves **no
 * `usr` at all**, the next launch finds nothing runnable, answers
 * "offline — using built-in cc (TCC)" (which reads like success) and every
 * later `pkg install` fails with `pkg: not found`.
 */
class SwapRecoveryTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private class MemStore : SetupLedger.Store {
        var phase: String? = null
        var release: String? = null
        var startedAt: Long? = null
        var cleared = 0
        override fun readPhase() = phase
        override fun readRelease() = release
        override fun readStartedAt() = startedAt
        override fun write(phase: String, release: String, startedAt: Long) {
            this.phase = phase
            this.release = release
            this.startedAt = startedAt
        }
        override fun clear() {
            cleared++
            phase = null
            release = null
            startedAt = null
        }
    }

    /** A filesDir with a runnable-looking prefix whose marker says [tag]. */
    private fun fakePrefix(files: File, tag: String): File {
        val prefix = File(files, "usr")
        File(prefix, "bin").mkdirs()
        val pkg = File(prefix, "bin/pkg")
        pkg.writeText("#!/system/bin/sh\necho pkg $tag\n")
        pkg.setExecutable(true, false)
        val bash = File(prefix, "bin/bash")
        bash.writeText("ELF-bash-$tag")
        bash.setExecutable(true, false)
        File(prefix, ".userland-release").writeText(tag)
        return prefix
    }

    private fun oldPrefix(files: File, stamp: Long, tag: String): File {
        val dir = File(files, SetupRecovery.oldPrefixName("usr", stamp))
        File(dir, "bin").mkdirs()
        File(dir, "bin/pkg").writeText("#!/system/bin/sh\necho pkg $tag\n")
        File(dir, ".userland-release").writeText(tag)
        return dir
    }

    private fun staging(files: File, stamp: Long, tag: String = "staged"): File {
        val dir = File(files, SetupRecovery.stagingName(stamp))
        File(dir, "bin").mkdirs()
        File(dir, "bin/pkg").writeText("#!/system/bin/sh\necho pkg $tag\n")
        return dir
    }

    private fun ledgerOf(phase: SetupPhase?, store: MemStore = MemStore()): Pair<SetupLedger, MemStore> {
        store.phase = phase?.name
        store.startedAt = if (phase == null) null else 1L
        return SetupLedger(store) to store
    }

    // ---- the three kill points ---------------------------------------------

    @Test
    fun `kill before the first rename leaves an orphan staging tree and an untouched prefix`() {
        val files = tmp.newFolder("files-a")
        val prefix = fakePrefix(files, "userland-v2-dev")
        staging(files, 1_000L)
        val (ledger, store) = ledgerOf(SetupPhase.EXTRACTING)

        val report = SetupRecovery.recover(files, prefix, ledger)

        assertEquals(SetupResume.Reextract, report.resume)
        assertNull("nothing was restored", report.restored)
        assertTrue(report.swept.any { it.startsWith(".userland-staging-") })
        assertTrue("the live prefix survives", prefix.isDirectory)
        assertEquals("userland-v2-dev", File(prefix, ".userland-release").readText())
        assertTrue(File(prefix, "bin/pkg").isFile)
        assertEquals(0, store.cleared)
    }

    @Test
    fun `kill between the two renames puts the previous userland back`() {
        val files = tmp.newFolder("files-b")
        val prefix = File(files, "usr") // gone — this is the owner's bug
        assertFalse(prefix.exists())
        oldPrefix(files, 2_000L, "userland-v1")
        staging(files, 2_500L)
        val (ledger, store) = ledgerOf(SetupPhase.SWAPPING)

        val report = SetupRecovery.recover(files, prefix, ledger)

        assertEquals(SetupResume.RestoreOld("usr.old-2000"), report.resume)
        assertEquals("usr.old-2000", report.restored)
        assertTrue("usr exists again", prefix.isDirectory)
        assertEquals("userland-v1", File(prefix, ".userland-release").readText())
        assertTrue(File(prefix, "bin/pkg").isFile)
        assertTrue("the ledger is quiet again", store.cleared >= 1)
        assertEquals(SetupPhase.IDLE, ledger.read().phase)
        assertNotNull(report.message)
        assertTrue(report.message!!.contains("restored your Linux tools"))
        // The half-extracted staging tree is junk and goes with it.
        assertTrue(report.swept.any { it.startsWith(".userland-staging-") })
        assertFalse(File(files, "usr.old-2000").exists())
    }

    @Test
    fun `kill after the swap landed keeps the new prefix and sweeps the old`() {
        val files = tmp.newFolder("files-c")
        val prefix = fakePrefix(files, "userland-v2-dev")
        val before = File(prefix, ".userland-release").readText()
        oldPrefix(files, 1_500L, "userland-v1")
        val (ledger, store) = ledgerOf(SetupPhase.SWAPPING)

        val report = SetupRecovery.recover(files, prefix, ledger)

        assertEquals(SetupResume.SweepOnly(listOf("usr.old-1500")), report.resume)
        assertNull(report.restored)
        assertEquals(listOf("usr.old-1500"), report.swept)
        assertEquals("the valid prefix is never touched", before, File(prefix, ".userland-release").readText())
        assertTrue(File(prefix, "bin/pkg").isFile)
        assertTrue(store.cleared >= 1)
        assertEquals(SetupPhase.IDLE, ledger.read().phase)
    }

    @Test
    fun `the newest old prefix wins when several accumulated`() {
        val files = tmp.newFolder("files-d")
        val prefix = File(files, "usr")
        oldPrefix(files, 100L, "ancient")
        oldPrefix(files, 900L, "recent")
        oldPrefix(files, 500L, "middle")
        val (ledger, _) = ledgerOf(SetupPhase.SWAPPING)

        val report = SetupRecovery.recover(files, prefix, ledger)

        assertEquals("usr.old-900", report.restored)
        assertEquals("recent", File(prefix, ".userland-release").readText())
        // The other two are swept in the same pass — no accumulation.
        assertTrue(report.swept.containsAll(listOf("usr.old-100", "usr.old-500")))
        assertEquals(
            "no old prefix is left behind",
            0,
            files.listFiles()?.count { SetupRecovery.isOldPrefixName(it.name) }
        )
    }

    @Test
    fun `three interrupted swaps in a row never accumulate old directories`() {
        val files = tmp.newFolder("files-e")
        repeat(3) { round ->
            val prefix = File(files, "usr")
            if (round == 0) fakePrefix(files, "userland-v1")
            // Simulate: the swap moved the prefix aside, then the process died.
            val moved = File(files, SetupRecovery.oldPrefixName("usr", 1_000L + round))
            if (prefix.isDirectory) assertTrue(prefix.renameTo(moved)) else moved.mkdirs()
            File(moved, "bin").mkdirs()
            File(moved, "bin/pkg").writeText("pkg")
            File(moved, ".userland-release").writeText("userland-v1")
            val (ledger, _) = ledgerOf(SetupPhase.SWAPPING)
            val report = SetupRecovery.recover(files, prefix, ledger)
            assertEquals("round $round must restore the moved prefix", "usr.old-${1_000L + round}", report.restored)
            assertTrue("round $round: usr exists", prefix.isDirectory)
        }
        val leftovers = files.listFiles()?.filter {
            SetupRecovery.isOrphanName(it.name)
        }.orEmpty()
        assertTrue("no orphan accumulation, found $leftovers", leftovers.isEmpty())
    }

    // ---- the sweep law ------------------------------------------------------

    @Test
    fun `the sweep only takes the two orphan shapes and stays inside filesDir`() {
        val files = tmp.newFolder("files-f")
        val prefix = fakePrefix(files, "userland-v2-dev")
        val notAnOrphan = File(files, "usr.old-but-mine").also { it.mkdirs() }
        val notAShape = File(files, ".userland-staging-abc").also { it.mkdirs() }
        val aFile = File(files, ".userland-staging-999").also { it.writeText("not a directory") }
        val project = File(files, "CodeC/projects/MyApp/usr.old-123")
        project.mkdirs()
        val home = File(files, "home").also { it.mkdirs() }
        oldPrefix(files, 700L, "userland-v1")
        val (ledger, _) = ledgerOf(SetupPhase.IDLE)

        val report = SetupRecovery.recover(files, prefix, ledger)

        assertEquals(listOf("usr.old-700"), report.swept)
        assertTrue("a non-digit stamp is a user file", notAnOrphan.isDirectory)
        assertTrue(notAShape.isDirectory)
        assertTrue("a FILE with an orphan name is not a directory to delete", aFile.isFile)
        assertTrue("never walk into projects/", project.isDirectory)
        assertTrue(home.isDirectory)
        assertTrue(prefix.isDirectory)
    }

    @Test
    fun `a staging tree created by a live install is never swept`() {
        val files = tmp.newFolder("files-g")
        val prefix = fakePrefix(files, "userland-v2-dev")
        val fresh = staging(files, System.currentTimeMillis() + 60_000L)
        val stale = staging(files, 42L)
        val (ledger, _) = ledgerOf(SetupPhase.IDLE)

        val result = SetupRecovery.sweep(files, "usr", newerThan = System.currentTimeMillis())

        assertTrue("fresh staging survives", fresh.isDirectory)
        assertFalse("stale staging goes", stale.isDirectory)
        assertTrue(result.kept.contains(fresh.name))
        assertTrue(result.deleted.contains(stale.name))
        assertTrue(prefix.isDirectory)
    }

    @Test
    fun `restore refuses when a prefix already exists`() {
        val files = tmp.newFolder("files-h")
        val prefix = fakePrefix(files, "userland-v2-dev")
        oldPrefix(files, 11L, "userland-v1")
        assertNull(SetupRecovery.restoreOldPrefix(files, prefix, "usr.old-11"))
        assertEquals("userland-v2-dev", File(prefix, ".userland-release").readText())
        assertTrue(File(files, "usr.old-11").isDirectory)
    }

    @Test
    fun `restore refuses a name that is not an old prefix`() {
        val files = tmp.newFolder("files-i")
        val prefix = File(files, "usr")
        File(files, ".userland-staging-5").mkdirs()
        assertNull(SetupRecovery.restoreOldPrefix(files, prefix, ".userland-staging-5"))
        assertNull(SetupRecovery.restoreOldPrefix(files, prefix, "usr"))
        assertNull(SetupRecovery.restoreOldPrefix(files, prefix, "usr.old-x"))
    }

    @Test
    fun `a missing filesDir or a broken ledger never throws`() {
        val missing = File(tmp.root, "no-such-files-dir")
        val (ledger, store) = ledgerOf(SetupPhase.SWAPPING)
        store.phase = "garbage"
        val report = SetupRecovery.recover(missing, File(missing, "usr"), ledger)
        assertNotNull(report)
        assertTrue(SetupRecoveryGate.awaitFinished(50L))
    }

    // ---- pure name logic ----------------------------------------------------

    @Test
    fun `the orphan name grammar matches the installer's own names`() {
        assertTrue(SetupRecovery.isStagingName(".userland-staging-1712345678901"))
        assertTrue(SetupRecovery.isOldPrefixName("usr.old-1712345678901"))
        assertFalse(SetupRecovery.isOldPrefixName("usr"))
        assertFalse(SetupRecovery.isOldPrefixName("usr.old-"))
        assertFalse(SetupRecovery.isOldPrefixName("usr.old-mine"))
        assertFalse(SetupRecovery.isStagingName(".userland-staging"))
        assertFalse(SetupRecovery.isOrphanName("projects"))
        assertEquals(
            ".userland-staging-42",
            SetupRecovery.stagingName(42L)
        )
        assertEquals("usr.old-42", SetupRecovery.oldPrefixName("usr", 42L))
        assertEquals(
            listOf("usr.old-1", "usr.old-2"),
            SetupRecovery.orphansIn(listOf("usr.old-2", "usr", "usr.old-1", "home"))
        )
        assertEquals(
            "usr.old-9",
            SetupRecovery.newestOldPrefix(listOf("usr.old-3", "usr.old-9", ".userland-staging-99"))
        )
        assertNull(SetupRecovery.newestOldPrefix(listOf(".userland-staging-99")))
    }

    @Test
    fun `the scan lists orphan directories only, oldest first, never symlinks or the prefix`() {
        val files = tmp.newFolder("files-j")
        fakePrefix(files, "userland-v2-dev")
        staging(files, 500L)
        oldPrefix(files, 100L, "old")
        File(files, "home").mkdirs()

        val names = SetupRecovery.scan(files, "usr").map { it.name }
        assertEquals(listOf("usr.old-100", ".userland-staging-500"), names)
    }

    @Test
    fun `a symlink shaped like an orphan is never scanned`() {
        val files = tmp.newFolder("files-k")
        val target = tmp.newFolder("target-k")
        File(target, "payload.txt").writeText("keep me")
        val link = File(files, "usr.old-900")
        java.nio.file.Files.createSymbolicLink(link.toPath(), target.toPath())

        // minSdk 24 has no java.nio in production code, so the check is by
        // canonical NAME: a link's canonical file is its target.
        assertTrue(SetupRecovery.isSymlink(link))
        assertEquals(emptyList<String>(), SetupRecovery.scan(files, "usr").map { it.name })
        assertTrue("the link's target must be untouched", File(target, "payload.txt").isFile)
    }

    @Test
    fun `a same-named symlink target survives the sweep because delete() unlinks first`() {
        // The pathological case the canonical-name check cannot see: a link
        // whose TARGET carries the same orphan-shaped name. `File.delete()`
        // still unlinks the link and never walks into the target tree, which is
        // why `deleteOrphan` tries it before `deleteRecursively()`.
        val files = tmp.newFolder("files-l")
        val elsewhere = tmp.newFolder("elsewhere-l")
        val target = File(elsewhere, "usr.old-777")
        target.mkdirs()
        File(target, "bin").mkdirs()
        File(target, "bin/pkg").writeText("#!/bin/sh")
        val link = File(files, "usr.old-777")
        java.nio.file.Files.createSymbolicLink(link.toPath(), target.toPath())

        val report = SetupRecovery.sweep(files, "usr")

        assertEquals(listOf("usr.old-777"), report.deleted)
        assertFalse("the link itself is gone", link.exists())
        assertTrue("the target tree must survive", File(target, "bin/pkg").isFile)
    }

    // ---- the gate that keeps the installer and the repair apart -------------

    @Test
    fun `the recovery gate blocks until the repair is finished`() {
        SetupRecoveryGate.reset()
        assertFalse(SetupRecoveryGate.awaitFinished(60L))
        SetupRecoveryGate.finished()
        assertTrue(SetupRecoveryGate.isFinished)
        assertTrue(SetupRecoveryGate.awaitFinished(60L))
    }

    @Test
    fun `recover always finishes the gate, even when it repairs nothing`() {
        SetupRecoveryGate.reset()
        val files = tmp.newFolder("files-k")
        val prefix = fakePrefix(files, "userland-v2-dev")
        val (ledger, _) = ledgerOf(SetupPhase.IDLE)
        SetupRecovery.recover(files, prefix, ledger)
        assertTrue(SetupRecoveryGate.awaitFinished(50L))
    }
}
