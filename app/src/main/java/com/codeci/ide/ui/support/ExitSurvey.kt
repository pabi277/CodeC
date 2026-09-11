package com.codeci.ide.ui.support

/**
 * Phase 41 follow-up (owner, after device round 1: *"for the testing phase
 * it when user want to close the app it show a sweet request pop up for
 * rate, experience, bugs, problems etc and tap again to exit and a option
 * to give review"*) — the exit survey's pure decisions.
 *
 * The **no-nag law is amended, not abandoned**: the 41.2 spec said "no
 * first-run dialog, no rate-us prompt" — the OWNER has now explicitly
 * requested an exit prompt for the testing phase, so it ships WITH the two
 * honest guards the original law was protecting:
 *  1. it is OFF-able (the Feedback screen carries the "Ask for feedback on
 *     exit" switch, default ON — `feedback_exit_prompt_enabled`), and
 *  2. it still sends nothing by itself — the stars travel only inside the
 *     report the user reviews and sends from the Feedback screen. No
 *     rating is ever uploaded, stored, or counted anywhere.
 *
 * "Tap again to exit": the first back press at a root destination shows
 * this dialog; the dialog's own back handling (onDismissRequest) EXITS —
 * so the second back press closes the app, the classic pattern. NOT NOW
 * stays in the app; EXIT closes it; outside taps do nothing (an accidental
 * tap must not close the app).
 */
object ExitSurvey {

    /**
     * Where "GIVE A REVIEW" goes. CodeC ships from GitHub, not the Play
     * Store — the review surface is the repository page (star/watch), and
     * the issues page is one click from there.
     */
    const val REPO_URL = "https://github.com/pabi277/CodeC"

    /** The star row's range. */
    const val MAX_STARS = 5

    /** "★★★★☆" for 4 — the banner and the report line both render stars. */
    fun stars(rating: Int): String {
        val n = rating.coerceIn(0, MAX_STARS)
        return "★".repeat(n) + "☆".repeat(MAX_STARS - n)
    }

    /**
     * The report's info-line fragment ("Rating: 4/5"), or null when there
     * is no usable rating (null, 0, out of range) — the report never
     * carries a rating the user did not tap.
     */
    fun ratingLine(rating: Int?): String? =
        rating?.takeIf { it in 1..MAX_STARS }?.let { "Rating: $it/$MAX_STARS" }

    /** True when [rating] would show a star banner on the Feedback screen. */
    fun hasRating(rating: Int): Boolean = rating in 1..MAX_STARS
}
