package com.codeci.ide.ui.editor

/**
 * Phase 16 — Spck-style LF/CRLF line-ending handling, pure and host-tested.
 *
 * The editor buffer always works in LF (Compose text fields, the highlighter
 * and the caret math all assume it); the file's native ending is remembered
 * per tab and re-expanded on save. Detection is majority-rule so a mostly-LF
 * file with a stray CRLF is not rewritten wholesale, and mixed files stay
 * stable (open → save → same ending, no silent reflow of every line).
 */
object LineEndings {

    const val LF = "LF"
    const val CRLF = "CRLF"

    /**
     * The dominant ending of [text]: [CRLF] only when at least one `\r\n`
     * pair exists AND CRLF lines outnumber bare-LF lines. Empty/single-line
     * text is [LF].
     */
    fun detect(text: String): String {
        if (text.isEmpty()) return LF
        var crlf = 0
        var lfOnly = 0
        var i = 0
        while (i < text.length) {
            if (text[i] == '\n') {
                if (i > 0 && text[i - 1] == '\r') crlf++ else lfOnly++
            }
            i++
        }
        return if (crlf > 0 && crlf >= lfOnly) CRLF else LF
    }

    /** Buffer-safe view: every `\r\n` becomes `\n` (also lone `\r`). */
    fun normalizeToLf(text: String): String =
        if (text.indexOf('\r') < 0) text else text.replace("\r\n", "\n").replace('\r', '\n')

    /** On-disk view for [ending]: LF files save untouched. */
    fun toNative(text: String, ending: String): String =
        if (ending == CRLF) text.replace("\n", "\r\n") else text

    /**
     * Toggle for the status-bar/overflow segment — always flips LF ⇄ CRLF
     * (unknown values read as LF, so legacy tabs behave).
     */
    fun toggle(ending: String): String = if (ending == CRLF) LF else CRLF
}
