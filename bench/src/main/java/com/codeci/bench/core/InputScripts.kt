package com.codeci.bench.core

/**
 * Phase 25.1 — the scripted input corpus, defined PURELY (no Android types)
 * so the identical scenario plays against all three candidates and the
 * builders are host-tested in CI.
 *
 * `harness.ScriptRunner` lowers [ScriptEvent]s onto the view tree:
 *  - [ScriptEvent.TypeChar] becomes a synthesized KeyEvent pair (or a direct
 *    programmatic insert, per the run's input mode);
 *  - the touch events become MotionEvents dispatched on the decor view;
 *  - every event fires at `scriptStart + atMs` wall-clock offset.
 */

/** A touch sample. Coordinates are fractions of the target view (0..1) so scripts are resolution-independent. */
data class NormPoint(val xFrac: Float, val yFrac: Float)

sealed interface ScriptEvent {
    data class TypeChar(val c: Char, val atMs: Long) : ScriptEvent
    data class Touch(val action: TouchAction, val point: NormPoint, val atMs: Long) : ScriptEvent
    /** Marker fired between reps (e.g. "scroll back to the top now"). */
    data class Command(val atMs: Long, val name: String) : ScriptEvent
}

enum class TouchAction { DOWN, MOVE, UP }

data class Script(val name: String, val durationMs: Long, val events: List<ScriptEvent>)

object InputScripts {

    /** Cadence of the typing burst: a fast-but-human 40 ms per keystroke. */
    const val BURST_INTERVAL_MS = 40L

    /** Interval of the completion-churn keystrokes: slower than the highlight debounce. */
    const val CHURN_INTERVAL_MS = 220L

    /** Long-press duration before the caret drag engages. */
    const val LONG_PRESS_MS = 600L

    /**
     * A 60-keystroke burst of real code-like text (letters, digits, spaces,
     * punctuation — no newlines) at [BURST_INTERVAL_MS] cadence.
     */
    fun burst60(): Script {
        val text = "int val = 0; for (int i = 0; i < 10; i++) { sum += va"
        require(text.length == 60) { "burst must be exactly 60 keystrokes, is ${text.length}" }
        var t = 100L
        val events = text.map { c -> ScriptEvent.TypeChar(c, t.also { t += BURST_INTERVAL_MS }) }
        return Script("burst60", t + 400L, events)
    }

    /**
     * Completion churn: type an identifier prefix slowly enough that each
     * keystroke lands AFTER the previous one's debounce fired, so the
     * completion pipeline wakes once per keystroke (the Phase 27 driver).
     */
    fun completionChurn(): Script {
        val text = "return variable_"
        var t = 100L
        val events = text.map { c -> ScriptEvent.TypeChar(c, t.also { t += CHURN_INTERVAL_MS }) }
        return Script("completion_churn", t + 400L, events)
    }

    /**
     * Fling through ~500 lines: accelerating drag upward (content moves down
     * the finger path), lift, then settle. The fling's glide frames are part
     * of the measurement window (the runner keeps capturing for the settle
     * time after UP).
     */
    fun fling(): Script {
        val events = mutableListOf<ScriptEvent>()
        var t = 100L
        events += ScriptEvent.Touch(TouchAction.DOWN, NormPoint(0.5f, 0.75f), t)
        // 12 accelerating moves over ~150 ms, thumb covers ~55% of the screen.
        var moved = 0
        for (i in 1..12) {
            t += when {
                i <= 4 -> 10L
                i <= 8 -> 14L
                else -> 20L
            }
            moved += when {
                i <= 4 -> 3
                i <= 8 -> 5
                else -> 7
            }
            val y = (0.75f - moved / 100.0f).coerceAtLeast(0.10f)
            events += ScriptEvent.Touch(TouchAction.MOVE, NormPoint(0.5f, y), t)
        }
        t += 20L
        events += ScriptEvent.Touch(TouchAction.UP, NormPoint(0.5f, 0.10f), t)
        return Script("fling500", t - 100L + 1600L, events)
    }

    /**
     * Caret/selection drag: long-press at the text, then a slow drag down
     * through the screen, ending with ~1.5 s of hold-and-wiggle at the bottom
     * edge — editors that auto-scroll during selection will traverse the
     * 500-line region; ones that don't will record zero traversal, which is
     * exactly the finding the decision table wants.
     */
    fun caretDrag(): Script {
        val events = mutableListOf<ScriptEvent>()
        var t = 100L
        events += ScriptEvent.Touch(TouchAction.DOWN, NormPoint(0.4f, 0.35f), t)
        t += LONG_PRESS_MS
        // Slow, precise drag down the screen (~1.2 s).
        var y = 0.35f
        while (y < 0.85f) {
            t += 30L
            y += 0.01f
            events += ScriptEvent.Touch(TouchAction.MOVE, NormPoint(0.4f, y.coerceAtMost(0.85f)), t)
        }
        // Wiggle at the bottom edge — the auto-scroll driver for 1.5 s.
        val wiggleEnd = t + 1500L
        var n = 0
        while (t < wiggleEnd) {
            t += 60L
            n++
            val x = if (n % 2 == 0) 0.41f else 0.39f
            events += ScriptEvent.Touch(TouchAction.MOVE, NormPoint(x, 0.85f), t)
        }
        events += ScriptEvent.Touch(TouchAction.UP, NormPoint(0.40f, 0.85f), t)
        return Script("caret_drag", t - 100L + 400L, events)
    }

    /** Names of the commands the runner understands. */
    const val CMD_SCROLL_TOP = "scroll_top"
}
