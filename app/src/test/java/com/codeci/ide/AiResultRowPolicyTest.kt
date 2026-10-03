package com.codeci.ide

import com.codeci.ide.ui.ai.AiActivityDisplay
import com.codeci.ide.ui.ai.AiAgentLimits
import com.codeci.ide.ui.ai.AiAgentPrompt
import com.codeci.ide.ui.ai.AiAgentStep
import com.codeci.ide.ui.ai.AiAgentStepKind
import com.codeci.ide.ui.ai.AiResultRowPolicy
import com.codeci.ide.ui.ai.AiToolLimits
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 88.5 / Level 11 — an activity row's tap view is exactly what was packed
 * for the model (S1): the same expression `AiAgentPrompt.renderStep` uses.
 */
class AiResultRowPolicyTest {

    private fun step(kind: AiAgentStepKind, result: String, detail: String = "", ok: Boolean = true) =
        AiAgentStep(kind, "title of $kind", detail = detail, modelResult = result, ok = ok)

    @Test
    fun `the kinds that carry a result have one and the others do not`() {
        for (kind in listOf(AiAgentStepKind.TOOL, AiAgentStepKind.ANSWER, AiAgentStepKind.DENIED, AiAgentStepKind.RUN_RESULT)) {
            assertTrue("$kind", AiResultRowPolicy.hasFullResult(step(kind, "payload")))
        }
        for (kind in listOf(AiAgentStepKind.REQUEST, AiAgentStepKind.TASK, AiAgentStepKind.RUN_DECISION, AiAgentStepKind.STOPPED)) {
            assertFalse("$kind", AiResultRowPolicy.hasFullResult(step(kind, "")))
        }
    }

    @Test
    fun `for every kind with a result, the packed request contains exactly the row's full text`() {
        val kinds = listOf(AiAgentStepKind.TOOL, AiAgentStepKind.ANSWER, AiAgentStepKind.DENIED, AiAgentStepKind.RUN_RESULT)
        for (kind in kinds) {
            val s = step(kind, "FILE src/a.kt lines 1-3 of 3 [complete]\nline one\nline two", detail = "short preview", ok = kind != AiAgentStepKind.DENIED)
            val packed = AiAgentPrompt.renderStep(s)
            assertTrue("$kind", packed.contains(AiResultRowPolicy.fullResult(s)))
            // The preview is a UI string only; the row's full text is never the preview.
            assertFalse("$kind", AiResultRowPolicy.fullResult(s).contains("short preview"))
        }
    }

    @Test
    fun `a result over the cap is cut exactly where renderStep cuts it`() {
        val big = "x".repeat(AiAgentLimits.MAX_RESULT_CHARS) + "TAIL-BEYOND-THE-CAP"
        val s = step(AiAgentStepKind.TOOL, big)
        val full = AiResultRowPolicy.fullResult(s)
        assertEquals(AiAgentLimits.MAX_RESULT_CHARS, full.length)
        assertEquals(8_000, AiToolLimits.MAX_RESULT_CHARS)
        assertFalse(full.contains("TAIL"))
        val packed = AiAgentPrompt.renderStep(s)
        assertTrue(packed.contains(full))
        assertFalse(packed.contains("TAIL"))
    }

    @Test
    fun `rows start open only when the user chose expanded`() {
        assertTrue(AiResultRowPolicy.startsOpen(AiActivityDisplay.EXPANDED))
        assertFalse(AiResultRowPolicy.startsOpen(AiActivityDisplay.COLLAPSED))
    }

    @Test
    fun `the teaser keeps a TOOL row's honest FILE header on one line`() {
        val header = "FILE app/src/Main.kt lines 1-60 of 240 [partial]"
        val s = step(AiAgentStepKind.TOOL, "$header\nfun main() {}", detail = "$header\nfun main() {}")
        assertEquals(header, AiResultRowPolicy.teaser(s))
        assertFalse(AiResultRowPolicy.teaser(s).contains('\n'))
    }

    @Test
    fun `without a preview the teaser falls back to the result's first line`() {
        val s = step(AiAgentStepKind.RUN_RESULT, "\nExit code 0\nall good")
        assertEquals("Exit code 0", AiResultRowPolicy.teaser(s))
    }

    @Test
    fun `reading a row never changes the step it reads`() {
        val s = step(AiAgentStepKind.ANSWER, "answer text", detail = "answer text")
        val before = s.copy()
        AiResultRowPolicy.fullResult(s)
        AiResultRowPolicy.teaser(s)
        AiResultRowPolicy.hasFullResult(s)
        assertEquals(before, s)
        assertEquals(AiAgentPrompt.renderStep(before), AiAgentPrompt.renderStep(s))
    }
}
