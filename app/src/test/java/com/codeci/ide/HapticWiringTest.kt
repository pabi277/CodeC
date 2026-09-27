package com.codeci.ide

import com.codeci.ide.ui.components.HapticMoment
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 51.4 — every one of the eight moments is wired, every haptic call goes
 * through the one adapter, the Settings switch is real (and separate from the
 * CodeC keyboard's own), and the animation-free zones stay haptic-free.
 */
class HapticWiringTest {

    private fun source(path: String): String = RepoFiles.mainSource(path).readText()

    private val editor = "app/src/main/java/com/codeci/ide/ui/screens/EditorScreen.kt"
    private val hub = "app/src/main/java/com/codeci/ide/ui/screens/FileManagerScreen.kt"
    private val modules = "app/src/main/java/com/codeci/ide/ui/screens/ModulesScreen.kt"
    private val adapter = "app/src/main/java/com/codeci/ide/ui/components/CodecHaptics.kt"

    @Test
    fun `the platform call lives in exactly two files, and one is the new adapter`() {
        val callers = RepoFiles.mainKotlinSources()
            .filter { RepoFiles.codeOnly(it.readText()).contains("performHapticFeedback") }
            .map { it.name }
            .sorted()
        // CodecKeyboard is Phase 28.2's key tick — the one pre-existing caller
        // that the app-chrome policy deliberately does not touch (it has its
        // own `codec_keys_haptics` setting).
        assertEquals(listOf("CodecHaptics.kt", "CodecKeyboard.kt"), callers)
    }

    @Test
    fun `the feedback handle is borrowed by the adapter and the keyboard only`() {
        val borrowers = RepoFiles.mainKotlinSources()
            .filter { RepoFiles.codeOnly(it.readText()).contains("LocalHapticFeedback") }
            .map { it.name }
            .sorted()
        assertEquals(listOf("CodecHaptics.kt", "CodecKeyboard.kt"), borrowers)
    }

    @Test
    fun `the adapter asks the policy and never decides for itself`() {
        val code = source(adapter)
        assertTrue(code.contains("HapticPolicy.performFor("))
        assertTrue(code.contains("HapticInput("))
        assertFalse("the adapter must not invent a moment", code.contains("HapticMoment."))
    }

    @Test
    fun `the switch is read by the adapter, defaulting on`() {
        val code = source(adapter)
        assertTrue(code.contains("settingsManager.hapticsFlow"))
        assertTrue(code.contains("initial = true"))
    }

    @Test
    fun `the settings store declares the haptics key and its writer`() {
        val store = source("app/src/main/java/com/codeci/ide/ui/settings/SettingsManager.kt")
        assertTrue(store.contains("booleanPreferencesKey(\"haptics\")"))
        assertTrue(store.contains("val hapticsFlow"))
        assertTrue(store.contains("suspend fun setHaptics("))
        // The keyboard's own setting is untouched by Phase 51 (Phase 47.2).
        assertTrue(store.contains("codec_keys_haptics"))
    }

    @Test
    fun `the settings screen exposes the one switch`() {
        val settings = source("app/src/main/java/com/codeci/ide/ui/screens/SettingsScreen.kt")
        assertTrue(settings.contains("R.string.haptics_title"))
        assertTrue(settings.contains("settingsManager.hapticsFlow"))
        assertTrue(settings.contains("settingsManager.setHaptics("))
    }

    @Test
    fun `the audit doc records the new control`() {
        val audit = RepoFiles.mainSource("docs/chat-phase38/SETTINGS_AUDIT.md").readText()
        assertTrue(audit.contains("| Haptics | switch | `haptics` |"))
    }

    @Test
    fun `all eight moments are wired somewhere real`() {
        val wired = listOf(
            HapticMoment.RUN_STARTED to editor,
            HapticMoment.PROGRAM_FINISHED to editor,
            HapticMoment.PROGRAM_FAILED to editor,
            HapticMoment.FILE_SAVED to editor,
            HapticMoment.TAB_CLOSED to editor,
            HapticMoment.INSTALL_FINISHED to modules,
            HapticMoment.PROJECT_OPENED to hub,
            HapticMoment.DRAG_STARTED to hub,
        )
        for ((moment, file) in wired) {
            assertTrue(
                "$moment is wired to no call site",
                source(file).contains("HapticMoment.$moment") ||
                    source(file).contains("RunHapticRule.momentFor("),
            )
        }
    }

    @Test
    fun `the run's moments come from the transition rule`() {
        val code = source(editor)
        assertTrue(code.contains("RunHapticRule.momentFor("))
        assertTrue(code.contains("rememberCodecHaptics()"))
    }

    @Test
    fun `the two animation-free zones stay haptic-free too`() {
        for (path in listOf(
            "app/src/main/java/com/codeci/ide/ui/editor/sora/SoraEditorHost.kt",
            "app/src/main/java/com/codeci/ide/ui/components/TerminalEmulatorView.kt",
        )) {
            assertFalse(
                "$path must not gain a haptic call",
                source(path).contains("HapticMoment") || source(path).contains("performHapticFeedback"),
            )
        }
        val guide = RepoFiles.mainSource("app/src/main/java/com/codeci/ide/ui/guide")
            .walkTopDown()
            .filter { it.extension == "kt" }
            .toList()
        for (file in guide) {
            assertFalse(
                "${file.name} must stay haptic-free (Phase 45's geometry is device-tested)",
                file.readText().contains("HapticMoment"),
            )
        }
    }

    @Test
    fun `the haptic policy itself is pure Kotlin`() {
        val policy = source("app/src/main/java/com/codeci/ide/ui/components/Haptics.kt")
        assertFalse(
            "the policy must stay host-testable (no Android, no Compose)",
            policy.contains("import android") || policy.contains("import androidx"),
        )
    }

    @Test
    fun `the press state is a shared component, not a per-screen one-off`() {
        val surface = source("app/src/main/java/com/codeci/ide/ui/components/PressableSurface.kt")
        assertTrue(surface.contains("fun PressableSurface("))
        assertTrue(surface.contains("collectIsPressedAsState"))
        assertTrue(surface.contains("CodecMotion.effectsSpring"))
        assertTrue(surface.contains("CodecTokens.space(CodecTokens.MIN_TOUCH)"))
        // The component exists to hold the rule for more than one surface: the
        // Packages section header and the hub's New-Project sheet rows are the
        // call sites, so a press there has an edge to happen inside.
        //
        // Phase 57.3 added the fourth: the editor's pill is a tappable surface,
        // and it renders through this component precisely so it does NOT become
        // the per-screen one-off this test exists to prevent. The list stays
        // exact on purpose — a new call site is a conscious edit, and this
        // comment is where the reason goes (nothing else here may be relaxed
        // into a `contains` check).
        val callSites = RepoFiles.mainKotlinSources()
            .filter { RepoFiles.codeOnly(it.readText()).contains("PressableSurface(") }
            .map { it.name }
            .sorted()
        assertEquals(
            listOf("FileManagerScreen.kt", "ModulesScreen.kt", "PillNotice.kt", "PressableSurface.kt"),
            callSites,
        )
    }
}
