package com.codeci.ide

import com.codeci.ide.ui.modules.InstallOutcome
import com.codeci.ide.ui.modules.InstallOutcomes
import com.codeci.ide.ui.modules.PkgResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Phase 71.1 — the Packages row learns how an install ENDED from `pkg` itself
 * (owner, 2026-09-29: "detect the real result"), instead of polling the disk
 * forever behind a disabled INSTALLING button.
 */
class PkgResultTest {

    @Test
    fun `parses the line pkg writes`() {
        val r = PkgResult.parse("1790647447 install 100 nano vim\n")!!
        assertEquals(1790647447L, r.epochSec)
        assertEquals("install", r.command)
        assertEquals(100, r.exit)
        assertEquals(listOf("nano", "vim"), r.targets)
        assertTrue(r.isInstall)
    }

    @Test
    fun `an update has no targets and is not an install`() {
        val r = PkgResult.parse("1790647447 update 0 ")!!
        assertEquals(emptyList<String>(), r.targets)
        assertFalse(r.isInstall)
    }

    @Test
    fun `garbage is no result`() {
        assertNull(PkgResult.parse(null))
        assertNull(PkgResult.parse(""))
        assertNull(PkgResult.parse("install 0"))
        assertNull(PkgResult.parse("soon install 0 nano"))
        assertNull(PkgResult.parse("1 install nope nano"))
    }

    @Test
    fun `read tolerates a missing file`() {
        val dir = java.nio.file.Files.createTempDirectory("codec-prefix").toFile()
        try {
            assertNull(PkgResult.read(dir))
            val f = PkgResult.file(dir)
            f.parentFile!!.mkdirs()
            f.writeText("5 i 1 git\n")
            assertEquals(1, PkgResult.read(dir)!!.exit)
            assertTrue(PkgResult.read(dir)!!.isInstall) // `pkg i` is `pkg install`
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun `the result file lives where the pkg script writes it`() {
        val script = com.codeci.ide.ui.terminal.ShellEnvironment.pkgScript()
        assertTrue(script.contains("\$STATE/last-result"))
        assertEquals("var/lib/codec-pkg/last-result", PkgResult.RELATIVE_PATH)
        assertTrue(script.contains("STATE=\"\$PREFIX/var/lib/codec-pkg\""))
    }

    // ---- what a card's command hands to pkg ----

    @Test
    fun `a plain pkg install names its packages`() {
        assertEquals(listOf("clang"), PkgResult.installTargets("pkg install -y clang"))
        assertEquals(listOf("git", "gh"), PkgResult.installTargets("pkg install -y git gh"))
    }

    @Test
    fun `chains and other tools are not spoken for by pkg`() {
        assertEquals(
            emptyList<String>(),
            PkgResult.installTargets("pkg install -y python-pip && pip install python-lsp-server"),
        )
        assertEquals(emptyList<String>(), PkgResult.installTargets("pkg install -y npm; npm i -g x"))
        assertEquals(emptyList<String>(), PkgResult.installTargets("cc --help"))
        assertEquals(emptyList<String>(), PkgResult.installTargets("pkg uninstall -y nano"))
        assertEquals(emptyList<String>(), PkgResult.installTargets("pkg install"))
    }

    @Test
    fun `every catalog card either has pkg targets or is a chain the row leaves to the disk`() {
        for (item in com.codeci.ide.ui.modules.PackageCatalog.ALL_PACKAGES) {
            val targets = PkgResult.installTargets(item.installCommand)
            if (item.installCommand.startsWith("pkg install") && !item.installCommand.contains("&&")) {
                assertTrue("${item.id} should name a package", targets.isNotEmpty())
            } else {
                assertTrue("${item.id} must not claim pkg targets", targets.isEmpty())
            }
        }
    }

    // ---- the verdict ----

    private val nano = listOf("nano")

    @Test
    fun `on disk always wins`() {
        assertEquals(
            InstallOutcome.INSTALLED,
            InstallOutcomes.decide(nano, 100, true, PkgResult(100, "install", 1, nano)),
        )
    }

    @Test
    fun `a non-zero exit for this package is a failure`() {
        assertEquals(
            InstallOutcome.FAILED,
            InstallOutcomes.decide(nano, 100, false, PkgResult(101, "install", 100, nano)),
        )
    }

    @Test
    fun `a clean exit that installed nothing ends without an install`() {
        assertEquals(
            InstallOutcome.ENDED_WITHOUT_INSTALL,
            InstallOutcomes.decide(nano, 100, false, PkgResult(100, "install", 0, nano)),
        )
    }

    @Test
    fun `a result from before this install is somebody else's`() {
        assertEquals(
            InstallOutcome.WAITING,
            InstallOutcomes.decide(nano, 100, false, PkgResult(99, "install", 1, nano)),
        )
    }

    @Test
    fun `a result for a different package is somebody else's`() {
        assertEquals(
            InstallOutcome.WAITING,
            InstallOutcomes.decide(nano, 100, false, PkgResult(101, "install", 1, listOf("vim"))),
        )
        assertEquals(
            InstallOutcome.WAITING,
            InstallOutcomes.decide(nano, 100, false, PkgResult(101, "update", 1, emptyList())),
        )
    }

    @Test
    fun `no file, or a command pkg cannot speak for, keeps waiting`() {
        assertEquals(InstallOutcome.WAITING, InstallOutcomes.decide(nano, 100, false, null))
        assertEquals(
            InstallOutcome.WAITING,
            InstallOutcomes.decide(emptyList(), 100, false, PkgResult(101, "install", 1, nano)),
        )
    }
}
