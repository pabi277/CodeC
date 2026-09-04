package com.codeci.bench.harness

import android.os.SystemClock
import android.view.KeyCharacterMap
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import com.codeci.bench.core.InputScripts
import com.codeci.bench.core.Script
import com.codeci.bench.core.ScriptEvent
import com.codeci.bench.core.TouchAction
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

/**
 * Phase 25.1 — lowers a pure [Script] onto the view tree.
 *
 * MUST run on the MAIN dispatcher (view dispatch is main-thread-only). Each
 * event fires at its script offset via `delay`, so the cadence matches the
 * script across candidates. Touch coordinates are resolved against the live
 * view bounds at dispatch time, so the scripts are resolution-independent.
 */
object ScriptRunner {

    private val keymap: KeyCharacterMap = KeyCharacterMap.load(KeyCharacterMap.VIRTUAL_KEYBOARD)

    /**
     * Runs [script] against [target]. Returns the number of characters that
     * actually landed in the content (typed chars — DIRECT-mode inserts and
     * KEY-mode commits both count), or -1 for scripts with no typing.
     */
    suspend fun run(script: Script, target: TypingTarget, mode: InputMode): Int {
        val view = target.view
        val before = target.length()
        val t0 = SystemClock.uptimeMillis()
        var typed = 0
        for (event in script.events) {
            val at = t0 + event.atMs
            while (true) {
                val now = SystemClock.uptimeMillis()
                if (now >= at) break
                delay(at - now)
            }
            when (event) {
                is ScriptEvent.TypeChar -> {
                    when (mode) {
                        InputMode.DIRECT -> {
                            target.insertAtCaret(event.c.toString())
                            typed++
                        }
                        InputMode.KEY_EVENTS -> {
                            if (dispatchChar(view, event.c)) typed++
                        }
                    }
                }
                is ScriptEvent.Touch -> dispatchTouch(view, event, t0)
                is ScriptEvent.Command -> {
                    when (event.name) {
                        InputScripts.CMD_SCROLL_TOP -> target.scrollToTop()
                    }
                }
            }
        }
        // Settle window so the scenario's trailing frames (fling glide, IME
        // echoes) stay inside the measurement window.
        delay(script.durationMs - (script.events.lastOrNull()?.atMs ?: 0L))
        val hasTyping = script.events.any { it is ScriptEvent.TypeChar }
        return if (hasTyping) target.length() - before else -1
    }

    /**
     * True when the character produced key events and all of them were
     * dispatched — shifted characters come back as SHIFT-down, key-down,
     * key-up, SHIFT-up sequences and every half must reach the view.
     */
    private fun dispatchChar(view: View, c: Char): Boolean {
        val events = keymap.getEvents(charArrayOf(c)) ?: return false
        var now = SystemClock.uptimeMillis()
        for (e in events) {
            val ev = KeyEvent(
                now, now, e.action, e.keyCode, 0, e.metaState,
                KeyCharacterMap.VIRTUAL_KEYBOARD, 0, 0
            )
            view.dispatchKeyEvent(ev)
            now += 8
        }
        return true
    }

    private fun dispatchTouch(view: View, event: ScriptEvent.Touch, t0: Long) {
        val loc = IntArray(2)
        view.getLocationInWindow(loc)
        val x = loc[0] + event.point.xFrac * view.width
        val y = loc[1] + event.point.yFrac * view.height
        val now = SystemClock.uptimeMillis()
        val action = when (event.action) {
            TouchAction.DOWN -> MotionEvent.ACTION_DOWN
            TouchAction.MOVE -> MotionEvent.ACTION_MOVE
            TouchAction.UP -> MotionEvent.ACTION_UP
        }
        val downTime = t0
        val motion = MotionEvent.obtain(downTime, now, action, x.roundToInt().toFloat(), y.roundToInt().toFloat(), 0)
        view.dispatchTouchEvent(motion)
        motion.recycle()
    }
}
