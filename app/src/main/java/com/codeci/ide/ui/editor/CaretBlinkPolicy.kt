package com.codeci.ide.ui.editor

/**
 * Phase 35.3 — caret timing policy kept independent of sora/Android.
 *
 * A caret is solid while input is active and remains solid for a short
 * settle window after the last edit. Once that window has elapsed, sora's
 * normal blink is allowed to resume.
 */
object CaretBlinkPolicy {

    const val REARM_AFTER_MS = 500L

    enum class Mode { SOLID, BLINK }

    fun mode(lastEditDeltaMs: Long, isTyping: Boolean): Mode =
        if (isTyping || lastEditDeltaMs in 0 until REARM_AFTER_MS) Mode.SOLID else Mode.BLINK
}
