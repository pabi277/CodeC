package com.codeci.ide

import com.codeci.ide.ui.terminal.SetupGatePolicy
import com.codeci.ide.ui.terminal.SetupPhase
import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * Phase 44.2 §3 — capability, not optimism.
 *
 * `UserlandInstaller.writeMarkers` writes `.userland-release` AFTER the swap,
 * so a marker-only test passes on a half tree; and the exec probe
 * (`ShellEnvironment.launchDiagnostic`) spawns a process, which is far too
 * heavy for a gate consulted on every frame. The truth used here is the actual
 * `bin/pkg` file: exists, non-empty, executable — plus a shell binary, plus a
 * ledger that is not stuck in `SWAPPING`.
 */
class UserlandUsableTest {

    @get:Rule
    val tmp = TemporaryFolder()

    /** `UserlandInstaller.MARKER_RELEASE` — written after the swap. */
    private val markerName = ".userland-release"

    private fun prefix(name: String): File =
        File(tmp.newFolder(name), "usr").also { File(it, "bin").mkdirs() }

    private fun executable(file: File, content: String = "#!/system/bin/sh\n") {
        file.writeText(content)
        file.setExecutable(true, false)
    }

    @Test
    fun `a marker with no pkg is not usable`() {
        val prefix = prefix("marker-only")
        File(prefix, markerName).writeText("userland-v2-dev")
        assertFalse(SetupGatePolicy.userlandUsable(prefix, SetupPhase.DONE))
        assertFalse(SetupGatePolicy.packageManagerPresent(prefix))
    }

    @Test
    fun `pkg with no shell beside it is not usable`() {
        val prefix = prefix("pkg-no-shell")
        executable(File(prefix, "bin/pkg"))
        assertTrue(SetupGatePolicy.packageManagerPresent(prefix))
        assertFalse("no bash and no busybox", SetupGatePolicy.shellPresent(prefix))
        assertFalse(SetupGatePolicy.userlandUsable(prefix, SetupPhase.DONE))
    }

    @Test
    fun `an empty pkg file is not usable`() {
        val prefix = prefix("empty-pkg")
        val pkg = File(prefix, "bin/pkg")
        pkg.writeText("")
        pkg.setExecutable(true, false)
        executable(File(prefix, "bin/bash"), "ELF")
        assertFalse(SetupGatePolicy.packageManagerPresent(prefix))
        assertFalse(SetupGatePolicy.userlandUsable(prefix, SetupPhase.DONE))
    }

    @Test
    fun `a pkg that cannot execute is not usable`() {
        val prefix = prefix("noexec-pkg")
        val pkg = File(prefix, "bin/pkg")
        pkg.writeText("#!/system/bin/sh\n")
        pkg.setExecutable(false, false)
        executable(File(prefix, "bin/bash"), "ELF")
        assertFalse(SetupGatePolicy.packageManagerPresent(prefix))
        assertFalse(SetupGatePolicy.userlandUsable(prefix, SetupPhase.DONE))
    }

    @Test
    fun `a working prefix beats a missing marker`() {
        val prefix = prefix("no-marker")
        executable(File(prefix, "bin/pkg"))
        executable(File(prefix, "bin/bash"), "ELF")
        assertFalse("the marker is genuinely absent", File(prefix, markerName).exists())
        assertTrue(SetupGatePolicy.userlandUsable(prefix, SetupPhase.DONE))
        assertTrue(SetupGatePolicy.userlandUsable(prefix, SetupPhase.IDLE))
    }

    @Test
    fun `busybox counts as the shell`() {
        val prefix = prefix("busybox")
        executable(File(prefix, "bin/pkg"))
        executable(File(prefix, "bin/busybox"), "ELF")
        assertTrue(SetupGatePolicy.shellPresent(prefix))
        assertTrue(SetupGatePolicy.userlandUsable(prefix, SetupPhase.DONE))
    }

    @Test
    fun `a perfect tree is not usable while a swap is pending`() {
        val prefix = prefix("swapping")
        executable(File(prefix, "bin/pkg"))
        executable(File(prefix, "bin/bash"), "ELF")
        File(prefix, markerName).writeText("userland-v2-dev")
        assertFalse(SetupGatePolicy.userlandUsable(prefix, SetupPhase.SWAPPING))
        assertTrue(SetupGatePolicy.userlandUsable(prefix, SetupPhase.DONE))
        // Downloading/extracting an UPGRADE leaves the current prefix usable:
        // the ledger only distrusts the swap window itself.
        assertTrue(SetupGatePolicy.userlandUsable(prefix, SetupPhase.DOWNLOADING))
        assertTrue(SetupGatePolicy.userlandUsable(prefix, SetupPhase.EXTRACTING))
    }

    @Test
    fun `no prefix at all is not usable and never throws`() {
        val missing = File(tmp.root, "nothing-here/usr")
        assertFalse(SetupGatePolicy.userlandUsable(missing, SetupPhase.IDLE))
        assertFalse(SetupGatePolicy.packageManagerPresent(missing))
        assertFalse(SetupGatePolicy.shellPresent(missing))
    }

    @Test
    fun `a directory called pkg is not a package manager`() {
        val prefix = prefix("pkg-dir")
        File(prefix, "bin/pkg").mkdirs()
        executable(File(prefix, "bin/bash"), "ELF")
        assertFalse(SetupGatePolicy.packageManagerPresent(prefix))
        assertFalse(SetupGatePolicy.userlandUsable(prefix, SetupPhase.DONE))
    }
}
