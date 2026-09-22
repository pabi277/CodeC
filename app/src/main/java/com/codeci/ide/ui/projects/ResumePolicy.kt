package com.codeci.ide.ui.projects

/**
 * The small, explicit decision behind the returning-user experience.
 *
 * This type is deliberately Android-free. The activity supplies facts from
 * [EditorLaunchState], the first-run gates and the crash overlay; this object
 * only decides whether jumping is obvious, whether the hub should offer a
 * choice, or whether an existing higher-priority route owns the start.
 */
data class ResumeFacts(
    val lastProject: String? = null,
    val lastFile: String? = null,
    val stillExists: Boolean = false,
    val tabCount: Int = 0,
    /** Null means this is an older install with no timestamp to trust. */
    val minutesSinceLastOpen: Long? = null,
    val crashedLastTime: Boolean = false,
    val welcomePending: Boolean = false,
    val setupNeedsWatching: Boolean = false,
    val safeMode: Boolean = false,
)

enum class ResumeOffer {
    /** The user was away only briefly, so today's direct resume remains kindest. */
    CONTINUE_IN_PLACE,
    /** Show a hub card with an explicit Continue and decline action. */
    OFFER_CARD,
    /** There is no valid file to resume; use the normal Projects hub. */
    HUB,
    /** A higher-priority first-run, setup or safety route owns the start. */
    NONE,
}

object ResumePolicy {
    /** A short interruption should not turn into an unnecessary question. */
    const val RESUME_WINDOW_MIN = 5L

    fun offerFor(facts: ResumeFacts): ResumeOffer = when {
        facts.safeMode || facts.welcomePending -> ResumeOffer.NONE
        facts.setupNeedsWatching -> ResumeOffer.NONE
        !facts.stillExists -> ResumeOffer.HUB
        facts.crashedLastTime -> ResumeOffer.OFFER_CARD
        facts.minutesSinceLastOpen == null -> ResumeOffer.OFFER_CARD
        facts.minutesSinceLastOpen <= RESUME_WINDOW_MIN -> ResumeOffer.CONTINUE_IN_PLACE
        else -> ResumeOffer.OFFER_CARD
    }

    /** The same spelling used by the editor status bar for a real project file. */
    fun displayPath(project: String?, file: String?): String? {
        val projectPart = project?.trim()?.trim('/')?.takeIf { it.isNotEmpty() } ?: return null
        val filePart = file?.trim()?.trim('/')?.takeIf { it.isNotEmpty() } ?: return null
        return "~proj/$projectPart/$filePart"
    }
}
