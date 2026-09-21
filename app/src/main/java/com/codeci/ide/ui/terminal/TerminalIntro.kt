package com.codeci.ide.ui.terminal

/**
 * Phase 51.3 — the terminal's first frame answers "is it ready?".
 *
 * The evidence (2026-09-21, `main` @ `0f1b650`): a fresh install's terminal
 * opens on a dark canvas whose only explanation is the setup bar's strip *above*
 * it (`TerminalScreen.kt:331-345`, Phase 44.1) and the status chip
 * (`:315-325`). A user who lands here first — the terminal is one of five
 * bottom-bar destinations — sees no words of its own, and has no way to tell
 * "still downloading" from "ready, just quiet".
 *
 * [TerminalIntroPolicy] answers that from **the same object Phase 44 already
 * computes** ([SetupFacts], produced by [SetupGatePolicy.factsFor] and shared
 * with the setup bar and Packages through the ViewModel): whether an install is
 * on screen is the stage's own [InstallProgress.inFlight] — the same predicate
 * the setup bar refuses dismissal on — and usability is [SetupFacts.usable], so
 * the terminal, the setup bar and Packages cannot drift apart — the plan's exit
 * condition for this part. (Deliberately not `barVisible`, which is also true
 * for a *settled* bar: the terminal must not call a failed setup "installing".)
 *
 * It is a *first frame*, never a permanent fixture: as soon as the shell has
 * produced a line ([TerminalIntroFacts.hasOutput]) the screen shows the shell
 * and this says [TerminalIntro.NONE].
 *
 * Pure Kotlin (the same file family as `SetupState.kt`); `TerminalChromeTest`
 * pins the mapping and that the screen quotes this policy rather than guessing.
 */
enum class TerminalIntro { NONE, INSTALLING, NEEDS_SETUP, READY }

data class TerminalIntroFacts(
    val setup: SetupFacts = SetupFacts(),
    /** The active session has produced at least one line of output. */
    val hasOutput: Boolean = false,
)

object TerminalIntroPolicy {

    fun introFor(facts: TerminalIntroFacts): TerminalIntro = when {
        // The shell is talking: the terminal speaks for itself.
        facts.hasOutput -> TerminalIntro.NONE
        // In flight means an install is on screen — the setup bar's own
        // definition of "do not interrupt me", read from the shared facts
        // instead of a second guess about this screen.
        facts.setup.progress.inFlight -> TerminalIntro.INSTALLING
        facts.setup.usable -> TerminalIntro.READY
        else -> TerminalIntro.NEEDS_SETUP
    }

    /** True when there is nothing for the terminal itself to say. */
    fun isSilent(intro: TerminalIntro): Boolean = intro == TerminalIntro.NONE
}
