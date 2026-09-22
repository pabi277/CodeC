package com.codeci.ide

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 57.3 — the pill is wired, and the messages it carries are not invented.
 *
 * The pure decisions live in `NoticePolicy` (pinned by `NoticePolicyTest`).
 * This file pins the three things a screenshot would otherwise be the only
 * witness of: the editor really renders the pill and dismisses it on the
 * policy's own timer, the tree's Refresh really goes through the *user-asked*
 * path, and the two failures that used to be silent really report.
 */
class PillNoticeWiringTest {

    private val editor = RepoFiles.mainSource(
        "app/src/main/java/com/codeci/ide/ui/screens/EditorScreen.kt"
    ).readText()

    private val viewModel = RepoFiles.mainSource(
        "app/src/main/java/com/codeci/ide/ui/viewmodels/EditorViewModel.kt"
    ).readText()

    // `codeOnly` because the pill's own kdoc argues *against* a dialog and a
    // toast — the pin must read the code, not the sentence rejecting them
    // (Phase 45 round 2: pin the button, never the word).
    private val pill = RepoFiles.codeOnly(
        RepoFiles.mainSource(
            "app/src/main/java/com/codeci/ide/ui/components/PillNotice.kt"
        ).readText()
    )

    private val strings = RepoFiles.mainSource(
        "app/src/main/res/values/strings.xml"
    ).readText()

    @Test
    fun `the editor renders the pill and lets it take itself away`() {
        assertTrue("the pill is part of the editor's chrome", editor.contains("PillNotice("))
        assertTrue("the message comes from the VM's one notice flow", editor.contains("val notice by viewModel.notice.collectAsState()"))
        assertTrue(
            "the pill's life is the policy's constant, not a number typed at the call site",
            editor.contains("NoticePolicy.AUTO_DISMISS_MS"),
        )
        assertTrue("the timer clears the message", editor.contains("viewModel.clearNotice()"))
        assertTrue(
            "a tap dismisses it too",
            editor.contains("PillNotice(kind = pill, onDismiss = { viewModel.clearNotice() })"),
        )
    }

    @Test
    fun `the pill is not a dialog and not a toast`() {
        assertFalse("the shots show a pill, never a question", pill.contains("AlertDialog"))
        assertFalse("and never an out-of-surface toast", pill.contains("Toast"))
        assertTrue(
            "it is one of the app's own pressable surfaces with one line",
            pill.contains("PressableSurface(") && pill.contains("Text("),
        )
    }

    @Test
    fun `the tree's own Refresh is the user-asked path`() {
        assertTrue(
            "the drawer's Refresh must confirm itself",
            editor.contains("onRefresh = { viewModel.refreshFilesFromUser(context) }"),
        )
        assertTrue(
            "which is the path that raises the notice",
            viewModel.contains("fun refreshFilesFromUser(context: Context)") &&
                viewModel.contains("NoticePolicy.refreshNotice(userAsked = true)"),
        )
    }

    @Test
    fun `a file that cannot open reports instead of returning in silence`() {
        assertTrue("the one failure handler exists", viewModel.contains("private fun failOpen(name: String?)"))
        assertTrue(
            "and it asks the policy, never spells a message itself",
            viewModel.contains("noticeFor(NoticePolicy.openFailureNotice(name))"),
        )
        val reported = Regex("""failOpen\(""").findAll(viewModel).count()
        assertTrue(
            "every open path must report (project open, single-file peek, scratch): found $reported",
            reported >= 8,
        )
        assertTrue(
            "the notice is one flow on the VM, cleared from one place",
            viewModel.contains("val notice: StateFlow<NoticeKind?> = _notice.asStateFlow()"),
        )
    }

    @Test
    fun `both messages exist, worded as the phase wrote them`() {
        assertTrue(strings.contains("<string name=\"notice_files_refreshed\">Refreshed Files</string>"))
        assertTrue(strings.contains("<string name=\"notice_file_open_failed\">Error opening file.</string>"))
        assertEquals(
            "the wording lives in the resource file, never inline in a composable",
            0,
            Regex("""\"Refreshed Files\"""").findAll(pill).count(),
        )
    }
}
