package com.codeci.ide

import com.codeci.ide.ui.services.RunArtifacts
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
 * Phase 39.1 — placement per language; userSuppliedOutput honoured
 * verbatim; isCodeCArtifact true only for CodeC-invented names; a path
 * that would escape the temp root is refused.
 */
class RunArtifactsTest {

    @get:Rule
    val tmp = TemporaryFolder()

    /** Fresh unique folder per call — TemporaryFolder forbids reusing names. */
    private fun fresh(): File = tmp.newFolder()

    @Test
    fun `C single-file plan lands under runs stamp and is owned by CodeC`() {
        val plan = RunArtifacts.plan("c", fresh(), fresh(), stamp = 42L, userSuppliedOutput = null)
        assertTrue(plan.ownedByCodeC)
        assertEquals(42L, plan.stamp)
        assertNotNull(plan.sourceCopy)
        assertTrue(plan.sourceCopy!!.path.replace('\\', '/').endsWith("runs/42/source.c"))
        assertTrue(plan.binary.path.replace('\\', '/').endsWith("runs/42/program"))
        assertTrue(plan.cwd.path.replace('\\', '/').endsWith("runs/42"))
    }

    @Test
    fun `userSuppliedOutput is never redirected`() {
        val project = fresh()
        val plan = RunArtifacts.plan(
            "c", project, fresh(), stamp = 7L, userSuppliedOutput = "bin/menu"
        )
        assertFalse(plan.ownedByCodeC)
        assertEquals(File(project, "bin/menu").absolutePath, plan.binary.absolutePath)
        assertEquals(project.absolutePath, plan.cwd.absolutePath)
        assertNull(plan.sourceCopy)
    }

    @Test
    fun `absolute userSuppliedOutput stays absolute`() {
        val abs = File(tmp.root, "elsewhere/out").absolutePath
        val plan = RunArtifacts.plan("c", fresh(), fresh(), 1L, abs)
        assertFalse(plan.ownedByCodeC)
        assertEquals(abs, plan.binary.absolutePath)
    }

    @Test
    fun `python plan gets a pycache dir under the run stamp`() {
        val plan = RunArtifacts.plan("python", fresh(), fresh(), 9L, null)
        assertTrue(plan.ownedByCodeC)
        assertNotNull(plan.pycacheDir)
        assertTrue(plan.pycacheDir!!.path.replace('\\', '/').endsWith("runs/9/pycache"))
    }

    @Test
    fun `server plan gets a server log under the run stamp`() {
        val plan = RunArtifacts.plan("server", fresh(), fresh(), 3L, null)
        assertNotNull(plan.serverLog)
        assertTrue(plan.serverLog!!.name == "server.log")
    }

    @Test
    fun `isCodeCArtifact recognises legacy and new names only`() {
        assertTrue(RunArtifacts.isCodeCArtifact("source_1710000000.c"))
        assertTrue(RunArtifacts.isCodeCArtifact("program_1710000000"))
        assertTrue(RunArtifacts.isCodeCArtifact("runs/42/program"))
        assertTrue(RunArtifacts.isCodeCArtifact("runs/42/source.c"))
        assertTrue(RunArtifacts.isCodeCArtifact("codec-import-123.zip"))
        assertTrue(RunArtifacts.isCodeCArtifact(".codec-tmp/x"))
        assertTrue(RunArtifacts.isCodeCArtifact("codec-scratch.tmp"))
        // User's own names — never.
        assertFalse(RunArtifacts.isCodeCArtifact("bin/menu"))
        assertFalse(RunArtifacts.isCodeCArtifact("a.out"))
        assertFalse(RunArtifacts.isCodeCArtifact("main.c"))
        assertFalse(RunArtifacts.isCodeCArtifact("src/index.ts"))
        assertFalse(RunArtifacts.isCodeCArtifact("build.gradle.kts"))
    }

    @Test
    fun `confineToTemp refuses a path that escapes the temp root`() {
        val tempRoot = fresh()
        val outside = File(tmp.root, "important-${System.nanoTime()}")
        outside.mkdirs()
        val safe = RunArtifacts.confineToTemp(tempRoot, outside, stamp = 5L)
        assertTrue(safe.path.replace('\\', '/').contains("runs/5"))
        assertTrue(safe.canonicalPath.startsWith(tempRoot.canonicalPath))
    }

    @Test
    fun `confineToTemp keeps a path already inside`() {
        val tempRoot = fresh()
        val inside = File(tempRoot, "runs/5/program")
        inside.parentFile.mkdirs()
        val safe = RunArtifacts.confineToTemp(tempRoot, inside, stamp = 5L)
        assertEquals(inside.canonicalPath, safe.canonicalPath)
    }

    @Test
    fun `ensureRunDir creates the stamp directory`() {
        val dir = RunArtifacts.ensureRunDir(fresh(), 99L)
        assertTrue(dir.isDirectory)
        assertEquals("99", dir.name)
    }
}
