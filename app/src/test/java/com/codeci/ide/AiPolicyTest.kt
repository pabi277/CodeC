package com.codeci.ide

import com.codeci.ide.ui.ai.AiAvailability
import com.codeci.ide.ui.ai.AiCopy
import com.codeci.ide.ui.ai.AiGate
import com.codeci.ide.ui.ai.AiKeySetup
import com.codeci.ide.ui.ai.AiLimits
import com.codeci.ide.ui.ai.AiModel
import com.codeci.ide.ui.editor.EditorOpenMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 76 (AI Level 1) — the owner's Level 0 decisions as executable rules:
 * D5 (projects only), O1 (key setup gate), O3 (pre-filled Flash model), and
 * the disclosures the panel must show word for word.
 */
class AiPolicyTest {

    // ---- D5: open CodeC project only ------------------------------------

    @Test
    fun `only a PROJECT open with a name and a key is ready`() {
        assertEquals(AiAvailability.READY, AiGate.availability(EditorOpenMode.PROJECT, "demo", hasKey = true))
        assertEquals(AiAvailability.NEEDS_KEY, AiGate.availability(EditorOpenMode.PROJECT, "demo", hasKey = false))
    }

    @Test
    fun `single-file and scratch opens never reach the helper, even with a key`() {
        // SINGLE_FILE keeps a project name internally (its save/run root) —
        // the name alone must never unlock the helper.
        assertEquals(AiAvailability.NEEDS_PROJECT, AiGate.availability(EditorOpenMode.SINGLE_FILE, "demo", true))
        assertEquals(AiAvailability.NEEDS_PROJECT, AiGate.availability(EditorOpenMode.SCRATCH, null, true))
        assertEquals(AiAvailability.NEEDS_PROJECT, AiGate.availability(EditorOpenMode.PROJECT, null, true))
        assertEquals(AiAvailability.NEEDS_PROJECT, AiGate.availability(EditorOpenMode.PROJECT, " ", true))
    }

    @Test
    fun `a run is worth explaining only when something failed`() {
        assertFalse(AiGate.runFailed(false, 0, 0, false))
        assertFalse(AiGate.runFailed(false, null, null, false))
        assertTrue(AiGate.runFailed(true, null, null, false))
        assertTrue(AiGate.runFailed(false, 1, null, false))
        assertTrue(AiGate.runFailed(false, 0, 139, false))
        assertTrue(AiGate.runFailed(false, 0, 0, true))
    }

    // ---- O1: key setup gate --------------------------------------------

    @Test
    fun `save needs a sane key AND the 18+ terms confirmation`() {
        val key = "AIza" + "x".repeat(35)
        assertTrue(AiKeySetup.canSave(key, confirmedAdultAndTerms = true))
        assertFalse(AiKeySetup.canSave(key, confirmedAdultAndTerms = false))
        assertFalse(AiKeySetup.canSave("", true))
    }

    @Test
    fun `key shape checks are shape only - no prefix is required`() {
        assertTrue(AiKeySetup.looksLikeKey("  " + "k".repeat(30) + "\n"))
        assertTrue(AiKeySetup.looksLikeKey("NotAnAIzaPrefix_but-long-enough"))
        assertFalse(AiKeySetup.looksLikeKey("short"))
        assertFalse(AiKeySetup.looksLikeKey("has inner space " + "x".repeat(20)))
        assertFalse(AiKeySetup.looksLikeKey("x".repeat(AiKeySetup.MAX_KEY_LENGTH + 1)))
        assertFalse(AiKeySetup.looksLikeKey("é".repeat(30)))
        assertEquals("abc", AiKeySetup.normalize("  abc \n"))
    }

    @Test
    fun `an acceptance counts only for the current terms version`() {
        assertTrue(AiKeySetup.acceptanceValid(AiKeySetup.TERMS_VERSION))
        assertFalse(AiKeySetup.acceptanceValid(null))
        assertFalse(AiKeySetup.acceptanceValid(AiKeySetup.TERMS_VERSION - 1))
    }

    // ---- O3: pre-filled Flash model, editable, URL-safe ----------------

    @Test
    fun `the default model is the device-proven Flash id and is valid`() {
        // Phase 76 device round 1: the id that actually answered on the owner's phone.
        assertEquals("gemini-3-flash-preview", AiModel.DEFAULT)
        assertTrue(AiModel.isValid(AiModel.DEFAULT))
        assertTrue(AiModel.isValid("gemini-2.5-pro"))
        assertTrue(AiModel.isValid(" gemini-3-flash-preview "))
    }

    @Test
    fun `a model id can never smuggle URL syntax`() {
        for (bad in listOf("", "a", "Gemini", "models/x", "x?key=1", "x:y", "x%2f", "x y", "-x", "x-", "../x")) {
            assertFalse("\"$bad\" must be refused", AiModel.isValid(bad))
        }
    }

    @Test
    fun `limits keep a request small and an answer bounded`() {
        assertEquals(12_000, AiLimits.MAX_CONTEXT_CHARS)
        assertEquals(60, AiLimits.MAX_OUTPUT_LINES)
        assertTrue(AiLimits.MAX_QUESTION_CHARS < AiLimits.MAX_CONTEXT_CHARS)
        assertTrue(AiLimits.CONNECT_TIMEOUT_MS in 1..AiLimits.READ_TIMEOUT_MS)
    }

    // ---- the disclosures, word for word --------------------------------

    @Test
    fun `the setup confirmation is the owner's exact sentence`() {
        assertEquals("I am 18 or older and I accept Google's Gemini API terms for my key.", AiCopy.CONFIRM)
        assertTrue(AiCopy.SETUP_INTRO.contains("your own Gemini API key"))
        assertTrue(AiCopy.TERMS_URL.startsWith("https://ai.google.dev/"))
        assertTrue(AiCopy.GET_KEY_URL.startsWith("https://aistudio.google.com/"))
        assertTrue(AiCopy.REGION_NOTE.contains("EEA") && AiCopy.REGION_NOTE.contains("UK"))
    }

    @Test
    fun `the free-tier note says data may be used and read by humans`() {
        assertTrue(AiCopy.FREE_TIER_NOTE.contains("free tier"))
        assertTrue(AiCopy.FREE_TIER_NOTE.contains("improve"))
        assertTrue(AiCopy.FREE_TIER_NOTE.contains("human reviewers"))
    }

    @Test
    fun `the panel promises read-only and says answers can be wrong`() {
        assertTrue(AiCopy.READ_ONLY.contains("never changes files"))
        assertTrue(AiCopy.WRONG_NOTE.contains("can be wrong"))
        assertTrue(AiCopy.TEST_NOTE.contains("No code is sent"))
        assertTrue(AiCopy.previewHeader("m", 42).contains("42 characters"))
    }
}
