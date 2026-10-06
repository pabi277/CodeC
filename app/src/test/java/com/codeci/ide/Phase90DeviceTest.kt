package com.codeci.ide

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 90 (the conversation surface) — the owner's device round, pinned as a document.
 *
 * A checklist that quietly loses a row is a checklist that quietly passes. These
 * cases read the real file so a later edit cannot drop the conversation rows, the
 * look rows, the re-run the owner asked for, or the no-move checks — and cannot
 * pre-tick anything: every cell belongs to the owner.
 */
class Phase90DeviceTest {

    private val round = RepoFiles.mainSource("docs/phases/03-editor/chat-phase90/DEVICE_ROUND.md").readText()
    private val readme = RepoFiles.mainSource("docs/phases/03-editor/chat-phase90/README.md").readText()

    @Test
    fun `the conversation rows are all asked`() {
        for (row in listOf("C1", "C2", "C3", "C4", "C5", "C6", "C7", "C8")) {
            assertTrue("missing row $row", Regex("\\| *$row *\\|").containsMatchIn(round))
        }
        assertTrue("New chat is a row", round.contains("New chat"))
        assertTrue("the drop marker is a row", round.contains("[earlier turns left out]"))
    }

    @Test
    fun `the look rows are all asked`() {
        for (row in listOf("C9", "C10", "C11", "C12", "C13", "C14")) {
            assertTrue("missing row $row", Regex("\\| *$row *\\|").containsMatchIn(round))
        }
        assertTrue("the visible code block is the owner's ask", round.contains("visible block"))
        assertTrue("dark theme is checked", round.contains("dark theme"))
    }

    @Test
    fun `the visual re-run the owner asked for is in the round`() {
        assertTrue("V1-V8 are re-run here", round.contains("V1–V8"))
        assertTrue("and the ask is quoted", round.contains("real ai"))
    }

    @Test
    fun `the no-move checks are in the round`() {
        assertTrue("Stop and Continue and Apply still work", round.contains("Stop, Continue"))
        assertTrue("Send is still the only road", round.contains("Send is still the only thing"))
        assertTrue("nothing is saved", round.contains("nothing is saved"))
    }

    @Test
    fun `the owner ran the round, and every result says so`() {
        assertTrue("owner-only", round.contains("owner-only"))
        assertTrue("the owner ran it", round.contains("RUN by the owner"))
        assertTrue("owner-reported", round.contains("owner-reported"))
        // Phase 91: the results are his, so a tick is allowed only when it is
        // attributed to him (Phase 89's convention). No tick stands alone.
        assertFalse(
            "no unattributed pass tick anywhere",
            Regex("✅(?! owner-reported)").containsMatchIn(round)
        )
        assertTrue("C1 is his pass", Regex("\\| *C1 *\\|[^\\n]*\\| *✅ owner-reported *\\|").containsMatchIn(round))
        assertTrue("C2 is his fail", Regex("\\| *C2 *\\|[^\\n]*❌ owner-reported").containsMatchIn(round))
        assertTrue("C11 names the dark theme", round.contains("failed: C2, C4 (not possible), C7, C8 (no permission), C9, C11 (dark theme), N1"))
        assertTrue("C6 and C14 are not understood, not guessed", round.contains("not understood: C6, C14"))
        assertTrue("the V re-run moved to Phase 91", round.contains("Phase 91"))
    }

    @Test
    fun `his change requests are written where they can be answered`() {
        assertTrue("the technical toggle", round.contains("toggle option to hide all technical part"))
        assertTrue("the copy ask", round.contains("set the copy part after every reply default"))
        assertTrue("the button that goes", round.contains("remove the new question"))
        assertTrue("the permission ask", round.contains("grand permission as user request"))
        assertTrue("the follow-up ask", round.contains("C2/C7"))
    }

    @Test
    fun `the brief lists the six vetoable defaults`() {
        assertTrue("the transcript caps are stated", readme.contains("8 turns / 12 000"))
        assertTrue("the marker rule is stated", readme.contains("left out"))
        assertTrue("the S8 label rule is stated", readme.contains("Per-turn") || readme.contains("per turn"))
        assertTrue("the gates are stated", readme.contains("D4") && readme.contains("D6") && readme.contains("S9"))
    }
}
