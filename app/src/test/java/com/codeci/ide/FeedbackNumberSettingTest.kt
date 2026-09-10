package com.codeci.ide

import com.codeci.ide.ui.support.FeedbackContacts
import com.codeci.ide.ui.support.FeedbackSectionState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 41.2 — the number/email setting at the store's decision layer.
 *
 * Planned as a Robolectric DataStore round-trip; implemented as pure tests
 * over `FeedbackContacts` (the storage decisions) plus a source-scan of
 * `FeedbackStore` (the plumbing), because the agent sandbox cannot run
 * Robolectric and Phase 40.4's law is to never push an unverifiable test
 * when a pure equivalent carries the same guarantee. What is pinned:
 *  - save stores ONLY normalised E.164 digits (or a clear);
 *  - an invalid input stores nothing (the UI shows the inline message);
 *  - empty stored value = the feature is hidden, not broken (exit 41.2.1);
 *  - the store declares exactly the two feedback keys, on the shared
 *    DataStore, with flows and normalising savers — the reader chain
 *    itself is enforced by `SettingsKeysHaveReadersTest` (this store is in
 *    its list since 41).
 */
class FeedbackNumberSettingTest {

    @Test
    fun `numberForStorage - a valid number round-trips to its E164 digits`() {
        assertEquals("919876543210", FeedbackContacts.numberForStorage("+91 98765 43210"))
        assertEquals("447700900123", FeedbackContacts.numberForStorage("0044-7700-900123"))
        assertEquals("919876543210", FeedbackContacts.numberForStorage(" 919876543210 "))
    }

    @Test
    fun `numberForStorage - blank clears, invalid is refused`() {
        assertEquals("", FeedbackContacts.numberForStorage(""))
        assertEquals("", FeedbackContacts.numberForStorage("   "))
        assertNull(FeedbackContacts.numberForStorage("12345"))
        assertNull(FeedbackContacts.numberForStorage("abc"))
        assertNull(FeedbackContacts.numberForStorage("8-800-555-3535"))
    }

    @Test
    fun `whatever is stored normalises to itself - the round trip is stable`() {
        for (raw in listOf("+91 98765 43210", "0044-7700-900123", "+1 (555) 123-4567", "+62 812-3456-7890")) {
            val stored = FeedbackContacts.numberForStorage(raw)
            assertNotNull(stored)
            assertEquals(stored, FeedbackContacts.numberForStorage(stored!!))
        }
    }

    @Test
    fun `emailForStorage - blank clears, a real address survives, junk is refused`() {
        assertEquals("", FeedbackContacts.emailForStorage(""))
        assertEquals("", FeedbackContacts.emailForStorage("   "))
        assertEquals("owner@example.com", FeedbackContacts.emailForStorage("  owner@example.com "))
        assertNull(FeedbackContacts.emailForStorage("not-an-address"))
        assertNull(FeedbackContacts.emailForStorage("@example.com"))
        assertNull(FeedbackContacts.emailForStorage("owner@"))
    }

    @Test
    fun `empty stored values mean the channels are hidden, not broken`() {
        val s = FeedbackSectionState(
            userText = "hi",
            whatsappNumberE164 = "",
            contactEmail = ""
        )
        assertTrue(!s.chatAvailable)
        assertTrue(!s.emailAvailable)
    }

    // ------------------------------------------------------------------
    // Source scan of the store file (the Robolectric round-trip's honest
    // replacement: the store's shape is pinned so CI compiles what was
    // reviewed, and the reader-chain test enforces the flows are used).
    // ------------------------------------------------------------------

    private val storeSource: String
        get() = RepoFiles.mainSource(
            "app/src/main/java/com/codeci/ide/ui/support/FeedbackStore.kt"
        ).readText()

    @Test
    fun `the store declares exactly the two feedback keys on the shared datastore`() {
        val keys = Regex("stringPreferencesKey\\(\"([^\"]+)\"\\)")
            .findAll(storeSource).map { it.groupValues[1] }.toList()
        assertEquals(listOf("feedback_whatsapp_number", "feedback_contact_email"), keys)
        assertTrue("must ride the same DataStore as the other settings", storeSource.contains("com.codeci.ide.ui.theme.dataStore"))
    }

    @Test
    fun `both keys have flows and normalising savers`() {
        assertTrue(storeSource.contains("whatsappNumberFlow"))
        assertTrue(storeSource.contains("contactEmailFlow"))
        assertTrue(storeSource.contains("saveWhatsappNumber"))
        assertTrue(storeSource.contains("saveContactEmail"))
        assertTrue("saves go through the pure normaliser", storeSource.contains("FeedbackContacts.numberForStorage(raw)"))
        assertTrue(storeSource.contains("FeedbackContacts.emailForStorage(raw)"))
    }
}
