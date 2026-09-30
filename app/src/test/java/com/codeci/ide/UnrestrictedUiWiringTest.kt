package com.codeci.ide

import com.codeci.ide.ui.settings.SettingsCatalog
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Owner request, 2026-09-27: remove both installation UI locks and the guide system. */
class UnrestrictedUiWiringTest {
    private fun source(name: String) = RepoFiles.mainSource(
        "app/src/main/java/com/codeci/ide/$name"
    ).readText()

    @Test
    fun `neither installer can impose a chrome lock`() {
        val code = RepoFiles.mainKotlinSources().joinToString("\n") { RepoFiles.codeOnly(it.readText()) }
        listOf("SetupLockPolicy", "ChromeLockReason", "editorChromeLocked", "showChromeLock",
            "setInstallRunning", "onLockMessage").forEach {
            assertFalse("retired UI lock is still wired: $it", code.contains(it))
        }
        val main = source("MainActivity.kt").substringAfter("private fun FlatBottomBar(")
            .substringBefore("private fun EditorNavRevealHandle(")
        assertTrue(main.contains("val onTabTap: () -> Unit = { onNavigate(screen) }"))
        assertFalse(main.contains("Icons.Default.Lock"))
        assertFalse(main.contains("verdictFor"))
    }

    @Test
    fun `editor navigation and chooser do not depend on installation state`() {
        val editor = source("ui/screens/EditorScreen.kt")
        assertTrue(editor.contains("gesturesEnabled = activeTabPath == null && currentFileName.isEmpty(),"))
        assertTrue(editor.contains("onClick = onDrawerTap"))
        val drawerTap = editor.substringAfter("val onDrawerTap: () -> Unit = {").substringBefore("\n    }")
        assertTrue(drawerTap.contains("toggleDrawer()"))
        assertFalse(drawerTap.contains("if ("))
        val runTap = editor.substringAfter("val onRunTap: () -> Unit = {")
            .substringBefore("// Phase 51.2 slot")
        assertTrue(runTap.contains("runChooserEntryOrNull()"))
        assertTrue(runTap.contains("runOpenFile()"))
    }

    @Test
    fun `first launch waits for the intro acknowledgement and not retired guide preferences`() {
        val main = source("MainActivity.kt")
        assertTrue(main.contains("val routeKnown = firstLaunchComplete != null"))
        assertTrue(main.contains("FirstRunIntroScreen("))
        assertTrue(main.contains("!firstRunAccepted"))
        assertTrue(main.contains("OrbitSample.ensure("))
        assertTrue(main.contains("settingsManager.setFirstLaunchComplete(true)"))
        assertFalse(main.contains("guideCompleted"))
        assertFalse(main.contains("coachSeen"))
    }

    @Test
    fun `guide slides overlays entry points and typing tips are removed`() {
        val sources = RepoFiles.mainKotlinSources()
        assertFalse(sources.any { it.parentFile.name == "guide" })
        val code = sources.joinToString("\n") { RepoFiles.codeOnly(it.readText()) }
        listOf("GuideScreen", "GuideAnchor", "CoachMarkPlan", "GuideCoachMarks", "onOpenGuide",
            "guideBeat", "imeGuideDismissed", "GUIDE_COMPLETED", "COACH_MARKS_SEEN_CSV",
            "IME_GUIDE_DISMISSED", "resetGuideTips").forEach {
            assertFalse("retired guide API is still present: $it", code.contains(it))
        }
        assertFalse(SettingsCatalog.entries.any { it.label in listOf("Help & guide", "Reset tips") })
    }

    @Test
    fun `removing Guide leaves space not a dead navigation control`() {
        val panel = source("ui/components/EditorSidePanel.kt")
        assertTrue(panel.contains("SidePanelPlan.CARD_COLUMNS - row.size"))
        assertTrue(panel.contains("Spacer(Modifier.weight(1f))"))
        assertFalse(panel.contains("NavCell.GUIDE"))
    }

    @Test
    fun `installation safety progress retry and busy-job protection remain`() {
        val shell = source("ui/terminal/ShellEnvironment.kt")
        assertTrue(shell.contains("acquire_lock()"))
        assertTrue(shell.contains("reclaim_stale_lock()"))
        val setup = source("ui/terminal/SetupState.kt")
        assertTrue(setup.contains("object SetupGatePolicy"))
        assertTrue(setup.contains("class SetupTracker"))
        assertTrue(setup.contains("object SetupAnnouncer"))
        val vm = source("ui/viewmodels/EditorViewModel.kt")
        assertTrue(vm.contains("if (_outputState.value.busy) return"))
        assertTrue(vm.contains("SetupGatePolicy.can("))
        assertTrue(source("ui/screens/TerminalScreen.kt").contains("viewModel.installUserland()"))
    }
}
