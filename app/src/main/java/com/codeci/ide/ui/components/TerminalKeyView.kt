package com.codeci.ide.ui.components

import android.content.Context
import android.text.InputType
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.BaseInputConnection
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.view.inputmethod.InputMethodManager

/**
 * Termux-style IME surface: a real Android View that reports
 * [onCheckIsTextEditor] and implements [onCreateInputConnection], matching
 * `TerminalView`. A 1.dp Compose `BasicTextField` is not a text-editor View,
 * so Gboard can sit idle for tens of seconds before it pops up.
 *
 * `TYPE_TEXT_VARIATION_VISIBLE_PASSWORD | NO_SUGGESTIONS` is the same trick
 * Termux uses: the IME commits one key at a time and skips composing /
 * autocorrect, which would glue `cc` and `./a.out` together.
 */
class TerminalKeyView(context: Context) : View(context) {

    @Volatile
    var onInput: (String) -> Unit = {}

    /** True while we still owe the user a showSoftInput (IME retry loop). */
    private var pendingIme = false

    init {
        isFocusable = true
        isFocusableInTouchMode = true
        isClickable = false
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
    }

    override fun onCheckIsTextEditor(): Boolean = true

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (sendKey(event)) return true
        return super.onKeyDown(keyCode, event)
    }

    override fun onCreateInputConnection(outAttrs: EditorInfo): InputConnection {
        outAttrs.inputType = InputType.TYPE_CLASS_TEXT or
            InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD or
            InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
        outAttrs.imeOptions = EditorInfo.IME_FLAG_NO_FULLSCREEN or
            EditorInfo.IME_FLAG_NO_EXTRACT_UI or
            EditorInfo.IME_ACTION_NONE
        outAttrs.initialSelStart = -1
        outAttrs.initialSelEnd = -1
        return object : BaseInputConnection(this, false) {
            override fun commitText(text: CharSequence?, newCursorPosition: Int): Boolean {
                val value = text?.toString().orEmpty()
                if (value.isNotEmpty()) onInput(value.replace("\n", "\r"))
                return true
            }

            override fun setComposingText(text: CharSequence?, newCursorPosition: Int): Boolean {
                // VISIBLE_PASSWORD should not compose. Swallow so a stray
                // composing update cannot double-commit with commitText.
                return true
            }

            override fun finishComposingText(): Boolean = true

            override fun sendKeyEvent(event: KeyEvent): Boolean {
                if (event.action == KeyEvent.ACTION_DOWN) sendKey(event)
                return true
            }

            override fun deleteSurroundingText(beforeLength: Int, afterLength: Int): Boolean {
                if (beforeLength > 0) {
                    onInput("\u007f".repeat(beforeLength.coerceAtMost(64)))
                }
                return true
            }

            override fun performEditorAction(actionCode: Int): Boolean {
                onInput("\r")
                return true
            }
        }
    }

    fun showIme() {
        requestFocus()
        val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
            ?: return
        // Phase 19.2 round 3 (owner: "keyboard sometimes does not pop up"):
        // the one-shot showSoftInput is silently dropped when the window has
        // not regained focus yet (screen switch, dialog/back). Retry once
        // shortly after, and again from onWindowFocusChanged, until the IME
        // reports this view active. restartInput() was removed — it rebound
        // the IME on EVERY tap for no benefit.
        pendingIme = true
        imm.showSoftInput(this, InputMethodManager.SHOW_IMPLICIT)
        postDelayed(::retryShowIme, IME_RETRY_MS)
    }

    private fun retryShowIme() {
        if (!pendingIme || !isAttachedToWindow) return
        val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
            ?: return
        if (!imm.isActive(this)) {
            if (hasWindowFocus()) {
                imm.showSoftInput(this, InputMethodManager.SHOW_IMPLICIT)
            }
            // Still not up (window unfocused): onWindowFocusChanged retries.
        } else {
            pendingIme = false
        }
    }

    override fun onWindowFocusChanged(hasWindowFocus: Boolean) {
        super.onWindowFocusChanged(hasWindowFocus)
        if (hasWindowFocus && pendingIme) retryShowIme()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        removeCallbacks(::retryShowIme)
    }

    companion object {
        private const val IME_RETRY_MS = 150L
    }

    private fun sendKey(event: KeyEvent): Boolean {
        when (event.keyCode) {
            KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER -> {
                onInput("\r")
                return true
            }
            KeyEvent.KEYCODE_DEL -> {
                onInput("\u007f")
                return true
            }
            KeyEvent.KEYCODE_TAB -> {
                onInput("\t")
                return true
            }
            KeyEvent.KEYCODE_ESCAPE -> {
                onInput("\u001b")
                return true
            }
            KeyEvent.KEYCODE_DPAD_UP -> {
                onInput("\u001b[A")
                return true
            }
            KeyEvent.KEYCODE_DPAD_DOWN -> {
                onInput("\u001b[B")
                return true
            }
            KeyEvent.KEYCODE_DPAD_RIGHT -> {
                onInput("\u001b[C")
                return true
            }
            KeyEvent.KEYCODE_DPAD_LEFT -> {
                onInput("\u001b[D")
                return true
            }
            else -> {
                val c = event.unicodeChar
                if (c != 0) {
                    onInput(c.toChar().toString())
                    return true
                }
            }
        }
        return false
    }
}
