package com.codeci.ide.ui.editor

/** Run has no installation-lock state. The runner separately guards concurrent jobs. */
data class RunButtonState(
    val running: Boolean = false,
    val hasOpenFile: Boolean = true,
)

enum class RunButtonRole { HERO, QUIET }

object RunButtonStyle {
    fun roleFor(s: RunButtonState): RunButtonRole =
        if (s.hasOpenFile) RunButtonRole.HERO else RunButtonRole.QUIET

    /** Keep real job progress visible, including package installation. */
    fun showsRunning(s: RunButtonState): Boolean = s.running

    fun isEnabled(s: RunButtonState): Boolean = s.hasOpenFile

    fun isPrimary(s: RunButtonState): Boolean = roleFor(s) == RunButtonRole.HERO
}
