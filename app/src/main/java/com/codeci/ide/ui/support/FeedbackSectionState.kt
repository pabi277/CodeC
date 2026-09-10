package com.codeci.ide.ui.support

/**
 * Phase 41.2 — the Feedback & Support section's decisions as pure state, so
 * "which row exists / which is primary / what is pre-ticked" is testable
 * without Compose (the same reason GitReadiness exists for the git pane).
 *
 * The privacy law of this section lives here and is pinned by test:
 *  - both attachment choices are EPHEMERAL — a fresh state (what "leave the
 *    screen and come back" gives) has the log box OFF and the crash box
 *    equal to [crashRecordPresent]; nothing about attachments is ever
 *    persisted, so no report silently carries attachments the user ticked
 *    three weeks ago. Every report is a fresh choice.
 *  - [includeCrash] defaults to "a crash is present" (PART_41_2 exit 5:
 *    after a crash the row is prefilled in) — the crash record is short and
 *    is exactly what the owner needs; the LOG box stays off by default
 *    because the 120-line tail is the bigger privacy surface. The device
 *    round can flip this to off-if-too-much; that decision is recorded in
 *    the part doc, not re-litigated in the code.
 */
data class FeedbackSectionState(
    val userText: String = "",
    val includeLog: Boolean = false,
    val crashRecordPresent: Boolean = false,
    val includeCrash: Boolean = crashRecordPresent,
    /** The stored (already normalised) E.164 digits, or null when unset. */
    val whatsappNumberE164: String? = null,
    /** The stored contact email, or null when unset. */
    val contactEmail: String? = null
) {
    /**
     * A CHAT row exists only when a VALID number is stored — the row is
     * hidden (not broken) when unset or wrong, because a wa.me link to a
     * wrong number opens a chat with the wrong person (PART_41_1).
     */
    val chatAvailable: Boolean
        get() = whatsappNumberE164?.let { FeedbackDraft.normaliseNumber(it) != null } == true

    /** CHAT needs something to say — "non-empty to enable CHAT" (41.2 table). */
    val chatReady: Boolean
        get() = chatAvailable && userText.isNotBlank()

    val emailAvailable: Boolean
        get() = !contactEmail.isNullOrBlank()

    /**
     * With no usable CHAT (no number, or nothing typed), COPY is the
     * primary action — it works offline, with no account (exit 41.2.1).
     */
    val primaryAction: FeedbackAction
        get() = if (chatReady) FeedbackAction.CHAT else FeedbackAction.COPY
}

/** The section's first-class actions (the row order is the UI's business). */
enum class FeedbackAction { CHAT, COPY }
