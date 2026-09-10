package com.codeci.ide.ui.support

/**
 * Phase 41.2 — the reply-to contacts' STORAGE decisions as pure code, so
 * the validation the Settings field shows is host-testable (the DataStore
 * wrapper [FeedbackStore] stays dumb plumbing and only calls these).
 *
 * The law both helpers encode: an empty input is a CLEAR (a valid choice —
 * the corresponding channel is hidden, not broken), an invalid input is
 * refused (null — nothing is stored, the caller shows the inline message),
 * and everything else is stored in its normalised form.
 */
object FeedbackContacts {

    /**
     * What `FeedbackStore.saveWhatsappNumber` would store for [raw]:
     * `""` = cleared, the normalised E.164 digits = ok, null = not a
     * WhatsApp number.
     */
    fun numberForStorage(raw: String): String? =
        if (raw.isBlank()) "" else FeedbackDraft.normaliseNumber(raw)

    /**
     * What `FeedbackStore.saveContactEmail` would store for [raw]: `""` =
     * cleared, the trimmed address = ok, null = not an address. Something
     * on both sides of the `@` — deliberately not an RFC regex; the field
     * is owner-set, and the row is hidden when empty.
     */
    fun emailForStorage(raw: String): String? {
        val t = raw.trim()
        if (t.isEmpty()) return ""
        val at = t.indexOf('@')
        return if (at > 0 && at < t.length - 1) t else null
    }
}
