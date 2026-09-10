package com.codeci.ide

import com.codeci.ide.ui.services.GcAction
import com.codeci.ide.ui.services.GcBudget
import com.codeci.ide.ui.services.KeepReason
import com.codeci.ide.ui.services.RunArtifacts
import com.codeci.ide.ui.services.RunDir
import com.codeci.ide.ui.services.TempGc
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * Phase 39.1 — age + capacity + keepNewest interplay; busy stamps never
 * deleted; walk confined to runs/; idempotent delete of a vanished dir.
 */
class TempGcTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private fun run(stamp: Long, bytes: Long, ageMs: Long, now: Long = 1_000_000L): RunDir {
        val path = File(tmp.root, "runs/$stamp")
        return RunDir(stamp, path, bytes, lastModifiedMillis = now - ageMs)
    }

    @Test
    fun `busy stamps are never deleted even when clearAllIdle`() {
        val now = 1_000_000L
        val runs = listOf(run(1, 10, 0, now), run(2, 10, 0, now), run(3, 10, 0, now))
        val actions = TempGc.plan(runs, now, busy = setOf(2L), clearAllIdle = true)
        val deleted = actions.filterIsInstance<GcAction.Delete>().map { it.path }
        val kept = actions.filterIsInstance<GcAction.Keep>()
        assertTrue(kept.any { it.path == runs[1].path && it.because == KeepReason.BUSY })
        assertFalse(deleted.any { it == runs[1].path })
        assertEquals(2, deleted.size)
    }

    @Test
    fun `keepNewest survives age-out`() {
        val now = 10_000_000L
        val budget = GcBudget(maxAgeMillis = 1_000L, maxBytes = Long.MAX_VALUE, keepNewest = 2)
        // stamps 1..5, all older than maxAge
        val runs = (1L..5L).map { run(it, 100, ageMs = 50_000L, now = now) }
        val actions = TempGc.plan(runs, now, busy = emptySet(), budget = budget)
        val keptStamps = actions.filterIsInstance<GcAction.Keep>().map {
            runs.first { r -> r.path == it.path }.stamp
        }.toSet()
        // newest 2 = 5 and 4
        assertEquals(setOf(4L, 5L), keptStamps)
        assertEquals(3, actions.filterIsInstance<GcAction.Delete>().size)
    }

    @Test
    fun `maxBytes evicts oldest first after keepNewest`() {
        val now = 1_000_000L
        val budget = GcBudget(maxAgeMillis = Long.MAX_VALUE, maxBytes = 250L, keepNewest = 1)
        // 5 runs × 100 bytes = 500; keepNewest=1 keeps stamp 5; need to drop to ≤250
        val runs = (1L..5L).map { run(it, 100, ageMs = 0, now = now) }
        val actions = TempGc.plan(runs, now, busy = emptySet(), budget = budget)
        val kept = actions.filterIsInstance<GcAction.Keep>().map {
            runs.first { r -> r.path == it.path }.stamp
        }.toSet()
        // stamp 5 is keepNewest; remaining budget 150 → at most one more 100-byte run
        assertTrue(5L in kept)
        val keptBytes = kept.sumOf { s -> runs.first { it.stamp == s }.bytes }
        assertTrue("keptBytes=$keptBytes kept=$kept", keptBytes <= budget.maxBytes + 100)
        // oldest should be preferred for deletion
        assertTrue(actions.filterIsInstance<GcAction.Delete>().any {
            it.path == runs.first { r -> r.stamp == 1L }.path
        })
    }

    @Test
    fun `empty inventory yields empty plan`() {
        assertTrue(TempGc.plan(emptyList(), 0L, emptySet()).isEmpty())
    }

    @Test
    fun `scan only returns stamp dirs under runs`() {
        val tempRoot = tmp.newFolder("temp")
        // plant a sibling that must NEVER be walked
        val important = File(tempRoot, "important").also { it.mkdirs(); File(it, "secret").writeText("x") }
        val runs = File(tempRoot, "runs").also { it.mkdirs() }
        File(runs, "10").mkdirs().also { File(File(runs, "10"), "program").writeText("bin") }
        File(runs, "not-a-stamp").mkdirs()
        File(runs, "11").writeText("i am a file not a dir") // kept out of inventory
        // symlink-style escape attempt: runs/../important is a different path
        val scanned = TempGc.scan(tempRoot)
        assertEquals(1, scanned.size)
        assertEquals(10L, scanned.single().stamp)
        assertTrue(important.exists())
        assertTrue(File(important, "secret").exists())
    }

    @Test
    fun `apply refuses deletes outside runs and is idempotent on vanished dirs`() {
        val tempRoot = tmp.newFolder("temp")
        File(tempRoot, "runs").mkdirs()
        val outside = tmp.newFolder("outside-secret")
        File(outside, "keep-me").writeText("safe")
        val actions = listOf(
            GcAction.Delete(outside),
            GcAction.Delete(File(tempRoot, "runs/999")), // vanished
        )
        val report = TempGc.apply(tempRoot, actions)
        // outside delete refused (failedDeletes++); vanished under runs/ is
        // idempotent success (deleted++ even though it was already gone).
        assertTrue(File(outside, "keep-me").exists())
        assertTrue(outside.exists())
        assertEquals(1, report.deleted)
        assertEquals(1, report.failedDeletes)
    }

    @Test
    fun `collect age-outs real dirs on disk`() {
        val tempRoot = tmp.newFolder("temp")
        val now = System.currentTimeMillis()
        // three stamp dirs; make 1 and 2 old by setting lastModified far past
        for (s in listOf(1L, 2L, 3L)) {
            val d = RunArtifacts.ensureRunDir(tempRoot, s)
            File(d, "program").writeText("x".repeat(100))
            if (s < 3L) d.setLastModified(now - 48L * 3600_000L)
        }
        val report = TempGc.sweep(
            tempRoot,
            now = now,
            busy = emptySet(),
            budget = GcBudget(maxAgeMillis = 24L * 3600_000L, maxBytes = Long.MAX_VALUE, keepNewest = 1),
        )
        // keepNewest=1 keeps stamp 3; 1 and 2 are old → deleted
        assertTrue(File(tempRoot, "runs/3").isDirectory)
        assertFalse(File(tempRoot, "runs/1").exists())
        assertFalse(File(tempRoot, "runs/2").exists())
        assertEquals(2, report.deleted)
    }

    @Test
    fun `clearIdle removes every non-busy run`() {
        val tempRoot = tmp.newFolder("temp")
        RunArtifacts.ensureRunDir(tempRoot, 1L).also { File(it, "a").writeText("1") }
        RunArtifacts.ensureRunDir(tempRoot, 2L).also { File(it, "b").writeText("2") }
        val report = TempGc.clearIdle(tempRoot, busy = setOf(2L))
        assertFalse(File(tempRoot, "runs/1").exists())
        assertTrue(File(tempRoot, "runs/2").isDirectory)
        assertEquals(1, report.deleted)
    }

    @Test
    fun `measure and formatMeasure are honest about empty and non-empty`() {
        val tempRoot = tmp.newFolder("temp")
        assertEquals("empty", TempGc.formatMeasure(TempGc.measure(tempRoot)))
        val d = RunArtifacts.ensureRunDir(tempRoot, 5L)
        File(d, "program").writeText("hello")
        val m = TempGc.measure(tempRoot)
        assertEquals(1, m.runCount)
        assertEquals(1, m.files)
        assertTrue(m.bytes >= 5)
        val text = TempGc.formatMeasure(m)
        assertTrue(text.contains("1 run"))
        assertTrue(TempGc.formatBytes(0) == "0 B")
        assertTrue(TempGc.formatBytes(2048) == "2 KB")
    }

    @Test
    fun `a file planted at a stamp path is not followed by scan`() {
        val tempRoot = tmp.newFolder("temp")
        File(tempRoot, "runs").mkdirs()
        File(tempRoot, "runs/7").writeText("not a directory")
        assertTrue(TempGc.scan(tempRoot).isEmpty())
    }
}
