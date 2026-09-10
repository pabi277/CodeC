package com.codeci.ide.ui.support

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.codeci.ide.ui.theme.dataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Phase 41.2 + follow-up — the feedback reply-to contacts and the exit
 * prompt switch, stored the way `GitCredentialsStore` stores credentials:
 * app-private DataStore, no UI assumptions. The validation decisions live
 * in [FeedbackContacts] (pure, host-tested); this class is plumbing only.
 *
 * **Defaults ship the OWNER's contacts** (owner decision, 2026-09-10,
 * after device round 1: *"Add number +91 62967 46606 · Email-
 * chakraborttypabi2772006@gmail.com"* — this was the exact Phase 42 open
 * question PART_41_2 recorded as `DEFAULT_WHATSAPP_NUMBER`'s one-line
 * decision point, now decided). A tester's fresh install therefore shows
 * the CHAT and EMAIL rows immediately; a STORED value always wins over the
 * default, so the owner can still point testing at another number without
 * a new APK, and clearing the fields reverts to these defaults.
 *
 * The attachment checkboxes remain never-persisted (fresh choice per
 * report, pinned by `FeedbackCheckboxNotPersistedTest`); the ONE feedback
 * boolean allowed in this store is the exit prompt switch — a UI
 * preference, not attachment consent.
 */
class FeedbackStore(private val context: Context) {

    companion object {
        val FEEDBACK_WHATSAPP_NUMBER = stringPreferencesKey("feedback_whatsapp_number")
        val FEEDBACK_CONTACT_EMAIL = stringPreferencesKey("feedback_contact_email")

        /**
         * Phase 41 follow-up — the exit survey prompt (owner-requested for
         * the testing phase; see [ExitSurvey]). Default ON; the Feedback
         * screen carries the switch, so it is off-able per device.
         */
        val FEEDBACK_EXIT_PROMPT_ENABLED = booleanPreferencesKey("feedback_exit_prompt_enabled")

        /** The owner's support number, in E.164 digits (+91 62967 46606). */
        const val DEFAULT_WHATSAPP_NUMBER = "916296746606"

        /** The owner's reply-to email. */
        const val DEFAULT_CONTACT_EMAIL = "chakraborttypabi2772006@gmail.com"
    }

    /** Normalised E.164 digits, or the owner's shipped default. */
    val whatsappNumberFlow: Flow<String> =
        context.dataStore.data.map { it[FEEDBACK_WHATSAPP_NUMBER] ?: DEFAULT_WHATSAPP_NUMBER }

    /** The contact email, or the owner's shipped default. */
    val contactEmailFlow: Flow<String> =
        context.dataStore.data.map { it[FEEDBACK_CONTACT_EMAIL] ?: DEFAULT_CONTACT_EMAIL }

    /** The exit survey prompt is shown on back-at-root (testing phase default). */
    val exitPromptEnabledFlow: Flow<Boolean> =
        context.dataStore.data.map { it[FEEDBACK_EXIT_PROMPT_ENABLED] ?: true }

    /**
     * @return true when something was stored; false when the input was
     *   neither blank nor a valid WhatsApp number, in which case nothing
     *   is stored and the caller shows the inline validation message.
     *   A blank input stores "" — an EXPLICIT off that hides the CHAT row
     *   even though a default ships (a stored value, empty included,
     *   always wins over [DEFAULT_WHATSAPP_NUMBER]); retyping the shipped
     *   number restores it.
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

    suspend fun setExitPromptEnabled(enabled: Boolean) {
        context.dataStore.edit { it[FEEDBACK_EXIT_PROMPT_ENABLED] = enabled }
    }
}
