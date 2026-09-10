package com.codeci.ide.ui.support

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.codeci.ide.ui.theme.dataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Phase 41.2 — the owner's reply-to contacts, stored the way
 * `GitCredentialsStore` stores credentials: app-private DataStore, no UI
 * assumptions, never welded into the APK. The validation decisions live in
 * [FeedbackContacts] (pure, host-tested); this class is plumbing only.
 *
 * Default empty = the corresponding row is HIDDEN, not broken (the phase's
 * law): a fresh install has no WhatsApp CHAT row and no EMAIL button until
 * the owner fills the fields once in Settings → Feedback & Support.
 *
 * There are exactly TWO keys, and no boolean key will ever be added here —
 * the attachment checkboxes are a fresh choice per report and are never
 * persisted (pinned by `FeedbackCheckboxNotPersistedTest`).
 */
class FeedbackStore(private val context: Context) {

    companion object {
        val FEEDBACK_WHATSAPP_NUMBER = stringPreferencesKey("feedback_whatsapp_number")
        val FEEDBACK_CONTACT_EMAIL = stringPreferencesKey("feedback_contact_email")

        /**
         * What this APK ships with when nothing has been stored yet. Empty
         * BY DESIGN (PART_41_2: the number is not welded into the APK — the
         * owner fills it once in Settings, default empty → the CHAT row is
         * hidden, not broken). Phase 42 (share-readiness) is where the
         * owner decides whether a fresh tester install should carry the
         * number; if yes, it is THIS constant that changes — one line, and
         * the honest disclosure in the card still applies.
         */
        const val DEFAULT_WHATSAPP_NUMBER = ""
        const val DEFAULT_CONTACT_EMAIL = ""
    }

    /** Normalised E.164 digits, or the (empty) default — "" hides the row. */
    val whatsappNumberFlow: Flow<String> =
        context.dataStore.data.map { it[FEEDBACK_WHATSAPP_NUMBER] ?: DEFAULT_WHATSAPP_NUMBER }

    /** The contact email, or the (empty) default — "" hides the button. */
    val contactEmailFlow: Flow<String> =
        context.dataStore.data.map { it[FEEDBACK_CONTACT_EMAIL] ?: DEFAULT_CONTACT_EMAIL }

    /**
     * @return true when something was stored (a blank input CLEARS the
     *   number — that is a choice too); false when the input was neither
     *   blank nor a valid WhatsApp number, in which case nothing is stored
     *   and the caller shows the inline validation message.
     */
    suspend fun saveWhatsappNumber(raw: String): Boolean {
        val n = FeedbackContacts.numberForStorage(raw) ?: return false
        context.dataStore.edit { it[FEEDBACK_WHATSAPP_NUMBER] = n }
        return true
    }

    suspend fun saveContactEmail(raw: String): Boolean {
        val e = FeedbackContacts.emailForStorage(raw) ?: return false
        context.dataStore.edit { it[FEEDBACK_CONTACT_EMAIL] = e }
        return true
    }
}
