package com.codeci.ide.ui.stats

/** The values already persisted by [StatsManager], with no new storage. */
data class StreakFacts(
    val streak: Int,
    val runs: Int,
    val files: Int,
    /** True when the caller knows the previous day was not consecutive. */
    val streakBroken: Boolean = false,
)

typealias StreakLineText = String

/**
 * Quiet, read-only progress copy. It never says that a user failed or lost
 * anything; a line is simply absent until there is something useful to show.
 */
object StreakLine {
    fun forAbout(facts: StreakFacts): StreakLineText? {
        if (facts.streak <= 0 && facts.runs <= 0 && facts.files <= 0) return null
        return listOf(
            quantity(facts.streak, "day", "days") + " in a row",
            quantity(facts.runs, "run", "runs"),
            quantity(facts.files, "file", "files") + " created",
        ).joinToString(" · ")
    }

    /** Only a continuing streak of at least two days earns a hub sentence. */
    fun forHub(facts: StreakFacts): StreakLineText? {
        if (facts.streak < 2 || facts.streakBroken) return null
        return "Day ${facts.streak} with CodeC."
    }

    private fun quantity(value: Int, singular: String, plural: String): String =
        "${value.coerceAtLeast(0)} ${if (value == 1) singular else plural}"
}
