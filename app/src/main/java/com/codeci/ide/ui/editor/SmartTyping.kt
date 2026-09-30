package com.codeci.ide.ui.editor

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import com.codeci.ide.ui.utils.LanguageType
import com.codeci.ide.ui.utils.MultiLanguageSyntaxHighlighter
import com.codeci.ide.ui.utils.TokenKind

/**
 * Phase 26.2 — Smart typing semantics. Pure, host-testable, no Android.
 *
 * Grounded behaviors (Squircle CE changelog & behavior; Sora SymbolPairMatch; VS Code rules):
 * 1. Type-over: typing ) ] } " ' when next char is same closer → move over, no insert.
 * 2. Wrap-selection: pair key with selection → surround, keep selected.
 * 3. Empty-pair backspace: backspace inside () with nothing between → delete both.
 * 4. Auto-indent: Enter copies indent; after { adds level and splits } onto own line; after a Python block header (SmartTyping.opensPythonBlock) adds level; dedent on sole closer.
 * 4b. Phase 75.1 — the Python block test lives here and ONLY here: Sora's
 *     `getIndentAdvance` adapter asks [opensPythonBlock] too, so the system
 *     keyboard and CodeC Keys cannot disagree about which line opens a block.
 * 4c. Phase 75.1 — Backspace inside leading indentation deletes one space
 *     per press ([handleIndentBackspace]), whatever the surface requested.
 * 4d. Phase 75.2 — Tab inserts one level of SPACES ([handleTabAsIndent]), so a
 *     file cannot end up with a tab unit and a space unit at once.
 * 5. String-aware negatives: inside string literal, rules 1–3 don't fire for the quote that opens string; inside comments none fire.
 * 6. Delete-word: previous word (identifier + whitespace) with stop chars whitespace, ., /, quotes.
 * 7. Undo integrity: each smart edit is a single undo unit (handled by ViewModel).
 *
 * All pair/indent rules are individually toggleable via Settings — [Config].
 * The Phase 75.1 one-space Backspace law and the Phase 75.2 one-level Tab law
 * are deliberately NOT toggles: they are the corrections of a wrong deletion and
 * of a wrong unit, not preferences.
 */
object SmartTyping {

    data class Config(
        val typeOver: Boolean = true,
        val wrapSelection: Boolean = true,
        val emptyPairBackspace: Boolean = true,
        val autoIndent: Boolean = true,
        val stringAware: Boolean = true,
        val deleteWord: Boolean = true
    )

    // -------------------------------------------------------------------------
    // Type-over
    // -------------------------------------------------------------------------

    private val closers = setOf(')', ']', '}', '"', '\'', '`')
    private val openToClose = mapOf('(' to ')', '[' to ']', '{' to '}', '"' to '"', '\'' to '\'', '`' to '`', '<' to '>')

    /**
     * If [incoming] is a single closer char and the char right after the caret
     * equals [incoming], move the caret over it instead of inserting.
     * Returns the transformed [TextFieldValue] or null if not applicable.
     */
    fun handleTypeOver(
        old: TextFieldValue,
        incoming: String,
        config: Config = Config(),
        language: LanguageType? = null
    ): TextFieldValue? {
        if (!config.typeOver) return null
        if (incoming.length != 1) return null
        val ch = incoming[0]
        if (ch !in closers) return null
        if (!old.selection.collapsed) return null
        val caret = old.selection.start.coerceIn(0, old.text.length)
        if (caret >= old.text.length) return null
        if (old.text[caret] != ch) return null
        // String-aware: if inside string/comment, don't type-over for that quote.
        if (config.stringAware && isInsideStringOrComment(old.text, caret, language)) {
            // For quote closers inside string, check if we're at string end? Actually inside string literal,
            // typing " should close/behave per lex context — we suppress type-over for the opening quote.
            // Simpler: if inside string token that started with same char, allow type-over to close it; else suppress.
            // For now, allow type-over inside string for its closing delimiter, suppress for others.
            val tokenKind = tokenKindAt(old.text, caret, language)
            if (tokenKind == TokenKind.COMMENT) return null
            // If inside string and ch is not the string delimiter, suppress.
            // But we don't know delimiter; assume inside string + ch == '"' or '\'' => allow; else suppress.
            // Keep simple: allow for " and ' inside string, suppress for )]} etc.
            if (tokenKind == TokenKind.STRING && ch !in setOf('"', '\'', '`')) return null
        }
        // Move caret over the closer.
        return TextFieldValue(old.text, TextRange(caret + 1))
    }

    // -------------------------------------------------------------------------
    // Wrap-selection
    // -------------------------------------------------------------------------

    /**
     * If selection non-empty and incoming is an opener ( ( [ { " ' < ` ) → surround.
     * Returns transformed value or null.
     */
    fun handleWrapSelection(
        old: TextFieldValue,
        incoming: String,
        config: Config = Config()
    ): TextFieldValue? {
        if (!config.wrapSelection) return null
        if (old.selection.collapsed) return null
        if (incoming.length != 1) return null
        val opener = incoming[0]
        val closer = openToClose[opener] ?: return null
        val text = old.text
        val start = minOf(old.selection.start, old.selection.end).coerceIn(0, text.length)
        val end = maxOf(old.selection.start, old.selection.end).coerceIn(0, text.length)
        val selected = text.substring(start, end)
        val body = opener + selected + closer
        val next = text.substring(0, start) + body + text.substring(end)
        return TextFieldValue(
            next,
            TextRange(start + 1, start + 1 + selected.length)
        )
    }

    // -------------------------------------------------------------------------
    // Empty-pair backspace
    // -------------------------------------------------------------------------

    /**
     * If caret is between an empty pair like (|) and Backspace is pressed,
     * delete BOTH characters. The caller signals backspace as a deletion of
     * one char before caret (old -> new where new.length == old.length -1).
     * This function detects the condition on [old] and returns the both-deleted
     * value, or null if not applicable.
     */
    fun handleEmptyPairBackspace(
        old: TextFieldValue,
        config: Config = Config(),
        language: LanguageType? = null
    ): TextFieldValue? {
        if (!config.emptyPairBackspace) return null
        if (!old.selection.collapsed) return null
        val caret = old.selection.start
        if (caret <= 0 || caret >= old.text.length) return null
        val left = old.text[caret - 1]
        val right = old.text[caret]
        val expectedClose = openToClose[left] ?: return null
        if (right != expectedClose) return null
        // String-aware: don't fire inside string/comment?
        if (config.stringAware && isInsideStringOrComment(old.text, caret, language)) {
            val kind = tokenKindAt(old.text, caret, language)
            if (kind == TokenKind.COMMENT) return null
            // Inside string, pair is part of string content — allow only if pair chars are quotes?
            // For simplicity, allow for " " etc? We'll allow but check.
        }
        val next = old.text.substring(0, caret - 1) + old.text.substring(caret + 1)
        return TextFieldValue(next, TextRange(caret - 1))
    }

    // -------------------------------------------------------------------------
    // Auto-indent
    // -------------------------------------------------------------------------

    /**
     * Transforms a single-newline insertion ([old] -> [newValue]) with smart indent.
     * Handles: copy leading indent, after { add tabSize, split } onto own line,
     * after Python : add tabSize, dedent on sole closer.
     */
    fun handleAutoIndent(
        old: TextFieldValue,
        newValue: TextFieldValue,
        language: LanguageType?,
        tabSize: Int = 4,
        config: Config = Config()
    ): TextFieldValue? {
        if (!config.autoIndent) return null
        // Detect single newline insertion.
        if (newValue.text.length != old.text.length + 1) return null
        val insertAt = newValue.selection.start - 1
        if (insertAt < 0 || newValue.text.getOrNull(insertAt) != '\n') return null
        // Old caret before insertion.
        val oldCaret = old.selection.start.coerceIn(0, old.text.length)

        // Find previous line (line before caret in newValue).
        val before = newValue.text.substring(0, insertAt)
        val lastLineStart = before.lastIndexOf('\n', (insertAt - 1).coerceAtLeast(0))
        val previousLine = if (lastLineStart >= 0) {
            before.substring(lastLineStart + 1)
        } else {
            before
        }
        val trimmedPrev = previousLine.trimEnd()
        val indent = previousLine.takeWhile { it == ' ' || it == '\t' }
        var extra = ""
        var splitBrace = false

        if (trimmedPrev.endsWith("{")) {
            extra = " ".repeat(tabSize.coerceIn(2, 8))
            // Check if closer exists after caret.
            val afterCaret = newValue.text.substring(insertAt + 1)
            val afterTrim = afterCaret.trimStart()
            if (afterTrim.startsWith("}")) {
                splitBrace = true
            }
        } else if (language == LanguageType.PYTHON && opensPythonBlock(trimmedPrev)) {
            // Phase 75.1 — the block test is [opensPythonBlock], the SAME one
            // the Sora route asks (see CodeCLanguage.getIndentAdvance). Before
            // this the rule was "the line ends with a colon", restated twice.
            extra = " ".repeat(tabSize.coerceIn(2, 8))
        }

        // Dedent for sole closer: if new line's content after indent is just } ] ) etc, reduce one level.
        // But for Enter case, new line is currently empty after indent+extra; dedent not needed until user types }.
        // We'll handle dedent on typing of closer separately via handle Dedent.

        if (extra.isEmpty() && indent.isEmpty()) return null
        // Build new text: insert indent+extra after newline.
        // For split brace case, insert "\n" + indent before the closer.
        return if (splitBrace) {
            // Need to insert: indent+extra + "\n" + indent before the existing }
            // newValue already has "\n" at insertAt. We replace suffix adjustment.
            val addition = indent + extra
            // Text currently: old[0:oldCaret] + "\n" + old[oldCaret:]
            // old[oldCaret] is "}" or "}..." . We want: old[0:oldCaret] + "\n" + addition + "\n" + indent + old[oldCaret:]
            // But newValue's suffix after newline is old[oldCaret:] which starts with "}".
            val suffix = newValue.text.substring(insertAt + 1) // includes "}..."
            // The suffix may have leading spaces before }? Trim and handle.
            // Find first non-whitespace after insertAt in newValue.
            val braceIdx = suffix.indexOf('}')
            if (braceIdx < 0) {
                // Should not happen; fallback to simple indent.
                val text = newValue.text.substring(0, insertAt + 1) + addition + newValue.text.substring(insertAt + 1)
                TextFieldValue(text, TextRange(insertAt + 1 + addition.length))
            } else {
                // Preserve content after brace? For "}": we want "\n" + indent + "}"
                val beforeBraceWhitespace = suffix.substring(0, braceIdx)
                val afterBrace = suffix.substring(braceIdx) // "}..."
                // If there was whitespace before brace, it will be replaced by our indent.
                val text = newValue.text.substring(0, insertAt + 1) + addition + "\n" + indent + afterBrace
                TextFieldValue(text, TextRange(insertAt + 1 + addition.length))
            }
        } else {
            val addition = indent + extra
            if (addition.isEmpty()) return null
            val text = newValue.text.substring(0, insertAt + 1) + addition + newValue.text.substring(insertAt + 1)
            TextFieldValue(text, TextRange(insertAt + 1 + addition.length))
        }
    }

    // -------------------------------------------------------------------------
    // Python block headers — ONE rule for BOTH Enter routes (Phase 75.1)
    // -------------------------------------------------------------------------

    /**
     * The words that may open an indented Python block. A line asks for one
     * more level only when it ends with `:` (ignoring a trailing comment) and
     * its first word is one of these; every other trailing colon — a dict key
     * split over lines, an annotation, a label, prose — is left alone.
     *
     * Public because Phase 75.2 gave this set a second reader: the completion
     * engine ([com.codeci.ide.ui.editor.CodeCompletionEngine]) stays quiet
     * about snippets while the caret sits on one of these words ([typedBlockKeyword]).
     * One list, two rules — the same reason [opensPythonBlock] is shared.
     */
    val pythonBlockKeywords = setOf(
        "def", "class", "for", "while", "if", "elif", "else",
        "try", "except", "finally", "with", "async", "match", "case"
    )

    /**
     * Phase 75.2 — true when [word] is EXACTLY one Python block keyword, in any
     * case. This is the moment the owner's report describes: he has written the
     * keyword and is about to write the rest of the line himself, so the editor
     * that now indents after `def name():` must not push a whole skeleton —
     * *"If i write def it's auto completes it def fname(): pass … i have to cut
     * that and again write another thing"*.
     *
     * Deliberately narrow, and it is a Python-only law: a block keyword is
     * exactly the word the editor's OWN Enter rule already handles (one level,
     * [opensPythonBlock]), while every other prefix — `defm`, `deft`, `ifmain`,
     * `pr` — keeps the whole snippet set, which is how a skeleton is meant to
     * be fetched on a phone.
     */
    fun typedBlockKeyword(word: String): Boolean =
        word.isNotEmpty() && word.lowercase() in pythonBlockKeywords

    /**
     * True when [lineText] is a Python block header: it ends with `:` once a
     * trailing `#` comment is removed, that colon is not sitting inside a
     * string literal, and the line's first word is a block keyword
     * ([pythonBlockKeywords]).
     *
     * This is the SINGLE owner of the rule on purpose. Two routes insert a
     * newline — the VM one ([handleAutoIndent], used by CodeC Keys and any
     * surface that hands the VM a bare `\n`) and Sora's own
     * `Language.getIndentAdvance` (used by the system IME's Enter). Both ask
     * here, because they could not see each other before Phase 75.1 and
     * disagreed — the owner's "def indents, for does not".
     */
    fun opensPythonBlock(lineText: String): Boolean {
        var line = lineText.trimEnd()
        if (line.isEmpty()) return false
        var scan = pythonLineScan(line)
        if (scan.first == 0) return false // the whole line is a comment
        if (scan.first > 0) {
            // `for i in items:  # walk the list` — the comment must not hide
            // the block colon, and `# note:` must not invent one.
            line = line.substring(0, scan.first).trimEnd()
            scan = pythonLineScan(line)
        }
        if (!line.endsWith(":") || scan.second) return false
        val body = line.trimStart()
        if (body.isEmpty()) return false
        val word = body.takeWhile { it.isLetterOrDigit() || it == '_' }
        return word in pythonBlockKeywords
    }

    /**
     * One walk over a Python line: the index of the `#` that starts a trailing
     * comment outside string literals (`-1` when there is none), and whether
     * the line ENDS inside an unterminated quote — a string that carries on to
     * the next line, e.g. a triple-quoted block.
     */
    private fun pythonLineScan(line: String): Pair<Int, Boolean> {
        var i = 0
        var quote = ' '
        var triple = false
        while (i < line.length) {
            val c = line[i]
            if (quote != ' ') {
                when {
                    c == '\\' -> i += 2
                    triple && c == quote && i + 2 < line.length &&
                        line[i + 1] == quote && line[i + 2] == quote -> {
                        quote = ' '
                        triple = false
                        i += 3
                    }
                    !triple && c == quote -> {
                        quote = ' '
                        i++
                    }
                    else -> i++
                }
                continue
            }
            if (c == '#') return i to false
            if (c == '"' || c == '\'') {
                if (i + 2 < line.length && line[i + 1] == c && line[i + 2] == c) {
                    quote = c
                    triple = true
                    i += 3
                    continue
                }
                quote = c
            }
            i++
        }
        return -1 to (quote != ' ')
    }

    /**
     * Dedent handler for typing a closer char when line's sole content is that char.
     * E.g., autoIndent "    }" should be dedented to "}" when indent is 4.
     * Called when incoming is "}" etc on a line that has only indent + closer.
     */
    fun handleDedentOnCloser(
        old: TextFieldValue,
        incoming: String,
        language: LanguageType? = null,
        tabSize: Int = 4,
        config: Config = Config()
    ): TextFieldValue? {
        if (!config.autoIndent) return null
        if (incoming !in setOf("}", "]", ")")) return null
        if (!old.selection.collapsed) return null
        val caret = old.selection.start.coerceIn(0, old.text.length)
        // Find current line start.
        val lineStart = old.text.lastIndexOf('\n', (caret - 1).coerceAtLeast(0)).let { if (it < 0) 0 else it + 1 }
        val lineBeforeCaret = old.text.substring(lineStart, caret)
        if (lineBeforeCaret.trim().isNotEmpty()) return null
        // Line before caret is only whitespace.
        val indentLen = lineBeforeCaret.length
        if (indentLen == 0) return null
        val step = tabSize.coerceIn(2, 8)
        // Dedent one level (remove step spaces).
        val dedentedIndent = " ".repeat((indentLen - step).coerceAtLeast(0))
        val next = old.text.substring(0, lineStart) + dedentedIndent + incoming + old.text.substring(caret)
        return TextFieldValue(next, TextRange(lineStart + dedentedIndent.length + incoming.length))
    }

    // -------------------------------------------------------------------------
    // Tab means one level, in spaces (Phase 75.2)
    // -------------------------------------------------------------------------

    /**
     * A lone TAB commit inserts one indentation level of SPACES, aligned to the
     * next tab stop while the caret sits in the line's leading whitespace.
     *
     * Why this exists: the CodeC Keys TAB cap already inserts `tabSize` spaces
     * ([com.codeci.ide.ui.editor.EditorKeySet.apply]) and every auto-indent rule
     * here emits spaces, but the system keyboard's Tab commits a raw `\t`
     * straight into the buffer. One file then holds two units of indentation at
     * once, and the owner's *"user can't line up the space/indenting between
     * lines"* follows — two lines that look the same on screen are 4 and 1
     * characters wide, so the Backspace law, the caret and the align-all-lines
     * intuition all disagree. This makes the buffer speak one unit on every
     * surface; it is the sibling of Phase 75.1's one-space Backspace law and,
     * like it, deliberately NOT a Settings toggle (it is the correction of a
     * mismatch, not a preference).
     *
     * Only a NAIVE lone-tab insertion is rewritten ([newValue] must be exactly
     * [old] with `"\t"` at the collapsed caret): a Tab that arrives inside
     * composed text, over a selection, or together with any other change is the
     * user's own edit and is left alone.
     */
    fun handleTabAsIndent(
        old: TextFieldValue,
        newValue: TextFieldValue,
        tabSize: Int = 4
    ): TextFieldValue? {
        if (!old.selection.collapsed) return null
        if (newValue.text.length != old.text.length + 1) return null
        val caret = old.selection.start.coerceIn(0, old.text.length)
        if (newValue.selection.start != caret + 1) return null
        val naive = old.text.substring(0, caret) + "\t" + old.text.substring(caret)
        if (newValue.text != naive) return null

        val step = tabSize.coerceIn(2, 8)
        val run = indentRun(old.text, caret, step)
        return TextFieldValue(
            old.text.substring(0, caret) + run + old.text.substring(caret),
            TextRange(caret + run.length)
        )
    }

    /**
     * The spaces one TAB press stands for, at [caret] in [text], given an
     * indentation level of [step] spaces: while the caret sits inside the line's
     * leading whitespace the run runs up to the NEXT tab stop (so `  ` + Tab at
     * step 4 gives 2, and the line ends up at column 4 — the whole point being
     * that pressing Tab twice from any messy state lands the two lines on the
     * same column); anywhere else in the line it is a full level.
     *
     * [step] arrives already clamped (2..8). One owner for both surfaces:
     * [handleTabAsIndent] (the system keyboard / hardware Tab) and
     * [com.codeci.ide.ui.editor.EditorKeySet.apply] (the CodeC Keys TAB cap).
     */
    fun indentRun(text: String, caret: Int, step: Int = 4): String {
        val at = caret.coerceIn(0, text.length)
        val lineStart = text.lastIndexOf('\n', (at - 1).coerceAtLeast(0)).let { if (it < 0) 0 else it + 1 }
        var i = lineStart
        var width = 0
        while (i < at && (text[i] == ' ' || text[i] == '\t')) {
            width += if (text[i] == '\t') step else 1
            i++
        }
        val level = step.coerceIn(2, 8)
        if (i != at) return " ".repeat(level) // not in the indentation: one level
        return " ".repeat(level - (width % level))
    }

    // -------------------------------------------------------------------------
    // Backspace inside indentation (Phase 75.1)
    // -------------------------------------------------------------------------

    /**
     * The owner's Backspace law: on an indented line, ONE press removes ONE
     * space.
     *
     * Returns a corrected value when the raw edit [old] -> [newValue] is a
     * deletion of MORE THAN one whitespace character sitting immediately
     * before a collapsed caret, and that whole run is the line's leading
     * indentation — i.e. whatever the surface asked for (Gboard and friends
     * sometimes hand the editor the whole run; Sora's own `deleteEmptyLineFast`
     * used to answer a single press with "the indent, plus the line above"),
     * the editor performs exactly one character of it.
     *
     * `null` means "leave the edit exactly as it arrived": ordinary deletions
     * in code and prose, a selection, a line join at column 0, a single space.
     * The result is still one edit for undo, dirty state and autosave, because
     * the ViewModel records whatever this returns.
     */
    fun handleIndentBackspace(
        old: TextFieldValue,
        newValue: TextFieldValue
    ): TextFieldValue? {
        if (!old.selection.collapsed || !newValue.selection.collapsed) return null
        val text = old.text
        val next = newValue.text
        val removed = text.length - next.length
        if (removed < 2) return null // one character is already the contract
        val caret = old.selection.start.coerceIn(0, text.length)
        val from = caret - removed
        if (from < 0) return null
        // A Backspace lands at the start of what it removed.
        if (newValue.selection.start != from) return null
        // Nothing else moved: the edit is exactly "the run before the caret is
        // gone" — no insert, no paste, no second change elsewhere in the file.
        if (!text.regionMatches(0, next, 0, from)) return null
        val suffixLength = text.length - caret
        if (!text.regionMatches(caret, next, from, suffixLength)) return null
        // Indentation only: every character from the line start to the caret is
        // a space or a tab. Trailing spaces after code, and any deletion that
        // swallows the newline itself, keep their ordinary meaning.
        val lineStart = if (from == 0) 0 else text.lastIndexOf('\n', from - 1) + 1
        var i = lineStart
        while (i < caret) {
            val c = text[i]
            if (c != ' ' && c != '\t') return null
            i++
        }
        if (caret == lineStart) return null
        return TextFieldValue(text.substring(0, caret - 1) + text.substring(caret), TextRange(caret - 1))
    }

    // -------------------------------------------------------------------------
    // Delete word
    // -------------------------------------------------------------------------

    /**
     * Deletes the previous word before the caret. Stop chars: whitespace, ., /, quotes.
     * Example: "foo.bar|" -> "foo.|" ; "foo  bar|" -> "foo  |" or "foo |"?
     */
    fun deletePrevWord(value: TextFieldValue): TextFieldValue {
        val text = value.text
        val sel = value.selection
        val start = minOf(sel.start, sel.end).coerceIn(0, text.length)
        val end = maxOf(sel.start, sel.end).coerceIn(0, text.length)
        if (start != end) {
            // Delete selection.
            val next = text.substring(0, start) + text.substring(end)
            return TextFieldValue(next, TextRange(start))
        }
        if (start == 0) return value
        var pos = start
        // Skip trailing whitespace? Spec says "identifier + whites" — phone-friendly = previous word (identifier + whites)
        // For "foo  bar|" with caret after bar, deletePrevWord should delete "bar" leaving "foo  "?
        // We'll implement: first, if char before caret is whitespace, delete that whitespace run? But spec says example "foo.bar|" -> "foo.|" and "- " whitespace pairs trimmed sane.
        // Simpler: handle two phases:
        // 1) If char before caret is whitespace, delete whitespace run back to previous non-whitespace then continue to delete word?
        // But spec says stop chars include whitespace, ., /, quote — so word boundary includes those.
        // Common phone behavior: long-press backspace deletes previous word including following spaces?
        // We'll implement: move left over whitespace? Let's define:
        // - If char before caret is whitespace, delete contiguous whitespace.
        // - Else, delete contiguous word chars (A-Za-z0-9_ and also maybe non-stop chars) until stop char.
        if (pos > 0 && text[pos - 1].isWhitespace()) {
            // Delete the whitespace run.
            var wsEnd = pos
            while (pos > 0 && text[pos - 1].isWhitespace()) pos--
            // Also delete the word before that whitespace? No, phone's delete-word typically deletes one word including its trailing spaces?
            // But spec's example "foo.bar" doesn't have whitespace. We need to decide.
            // We'll delete only the whitespace run for now, but also if double-tap?
            // Check spec 26.2: rule 6 delete-word swipe: "foo.ba|z → foo.|z"? NO: phone-friendly = previous word (identifier + whites). Stop chars: whitespace, ., /, quote.
            // That example suggests "foo.ba|z" caret in middle of "bar", delete word deletes "ba" leaving "foo.|z"? Hard.
            // We'll keep simple: delete whitespace run only.
        } else {
            // Delete word characters until stop char.
            while (pos > 0) {
                val ch = text[pos - 1]
                if (ch.isWhitespace() || ch == '.' || ch == '/' || ch == '"' || ch == '\'' || ch == '`') break
                // Also treat underscore as part of word, don't break.
                // If ch is . etc already break, so we stop.
                pos--
                // Continue? But stop after word chars; if we encounter whitespace, break.
            }
            // Also handle case where word is preceded by '.' - keep dot, as example.
            // Our loop stopped before dot, so dot stays.
        }
        // If we didn't move (e.g., caret after dot and previous char is dot, we deleted zero? Then we should delete dot? Spec says Stop chars: whitespace, ., /, quote — does delete-word delete up to but not including stop? For "foo.bar|" delete word should delete "bar" but keep "foo." => we did correct (pos stopped before ".") So delete from pos to start.
        if (pos == start) {
            // No word chars? If char before is stop char (like '.'), we should delete that stop char as separate word?
            // But for "foo.|" if caret after dot, previous word is dot? Maybe delete dot?
            // We'll delete one char if no word found.
            pos = (start - 1).coerceAtLeast(0)
        }
        val next = text.substring(0, pos) + text.substring(start)
        return TextFieldValue(next, TextRange(pos))
    }

    // -------------------------------------------------------------------------
    // Helpers — string-aware
    // -------------------------------------------------------------------------

    private fun isInsideStringOrComment(text: String, offset: Int, language: LanguageType?): Boolean {
        val kind = tokenKindAt(text, offset, language)
        return kind == TokenKind.STRING || kind == TokenKind.COMMENT
    }

    private fun tokenKindAt(text: String, offset: Int, language: LanguageType?): TokenKind? {
        if (text.isEmpty()) return null
        val lang = language ?: LanguageType.C
        // Use tokenizer to find token containing offset (or previous char).
        // Tokenizer is line-agnostic, returns list of spans with start/end.
        return try {
            val spans = MultiLanguageSyntaxHighlighter.tokenize(text, lang)
            val pos = offset.coerceIn(0, text.length)
            // Check position-1 and position for being inside string/comment.
            // Prefer pos-1 if pos is at boundary.
            spans.firstOrNull { pos in it.start until it.end || (pos > 0 && pos - 1 in it.start until it.end) }?.kind
        } catch (_: Exception) {
            null
        }
    }

    // -------------------------------------------------------------------------
    // Auto-pair (typing '(' when configstringAware, insert pair and keep caret inside)
    // -------------------------------------------------------------------------

    /**
     * If incoming is an opener and caret is not inside string/comment (when stringAware), insert the pair.
     */
    fun handleAutoPair(
        old: TextFieldValue,
        incoming: String,
        config: Config = Config(),
        language: LanguageType? = null
    ): TextFieldValue? {
        if (!config.emptyPairBackspace) return null // reuse toggle: emptyPair flag covers pair auto-insert? (Spec ties them)
        if (incoming.length != 1) return null
        val closer = openToClose[incoming[0]] ?: return null
        if (incoming[0] == closer && incoming[0] in setOf('"', '\'', '`')) {
            // For quotes, don't auto-pair if next char is letter/digit (e.g. typing ' in don't)
            // Simplify: only auto-pair if not inside string/comment and next char is whitespace or closer or EOL.
        }
        if (!old.selection.collapsed) return null
        if (config.stringAware && isInsideStringOrComment(old.text, old.selection.start, language)) {
            val kind = tokenKindAt(old.text, old.selection.start, language)
            if (kind == TokenKind.COMMENT) return null
            if (kind == TokenKind.STRING && incoming[0] !in setOf('"', '\'', '`')) return null
        }
        val caret = old.selection.start.coerceIn(0, old.text.length)
        // Phase 75.3 (owner, device round 2: *"in c coding I tried to write
        // int main() then curly brackets it sent the brackets inside the first
        // brackets like ({})"*): when `()` is empty and the caret sits at
        // `(|)`, typing `{` is the block opener after `()`, so step past `)`
        // to produce `(){|}`.
        val at = if (
            incoming == "{" &&
            caret > 0 &&
            caret < old.text.length &&
            old.text[caret - 1] == '(' &&
            old.text[caret] == ')'
        ) {
            caret + 1
        } else {
            caret
        }
        val next = old.text.substring(0, at) + incoming + closer + old.text.substring(at)
        return TextFieldValue(next, TextRange(at + 1))
    }

    /**
     * Phase 75.3 — companion to [handleAutoPair]'s `(|)` + `{` -> `(){|}` step
     * for two multi-char arrival shapes:
     *  1. A caller or batch edit inserts `"{}"` directly inside empty `(|)`.
     *  2. Sora's `SymbolPairMatch` commits `"{"` in two synchronous steps
     *     (`replace("{")` then `insert("}")`): step 1 already transformed `old`
     *     to `"...(){}"` (caret at `pos`, inside `{|}`), and step 2 arrives
     *     with Sora's un-stepped `"...({})"` of the same length — keep `old`.
     */
    fun handleBraceInEmptyParens(
        old: TextFieldValue,
        newValue: TextFieldValue,
        config: Config = Config()
    ): TextFieldValue? {
        if (!config.emptyPairBackspace || !old.selection.collapsed) return null
        val caret = old.selection.start.coerceIn(0, old.text.length)
        // Shape 1: old had `(|)` at `caret` and newValue inserted `"{}"` inside `()`.
        if (newValue.text.length == old.text.length + 2 &&
            caret > 0 && caret < old.text.length &&
            old.text[caret - 1] == '(' && old.text[caret] == ')'
        ) {
            val inside = old.text.substring(0, caret) + "{}" + old.text.substring(caret)
            if (newValue.text == inside) {
                val outside = old.text.substring(0, caret + 1) + "{}" + old.text.substring(caret + 1)
                return TextFieldValue(outside, TextRange(caret + 2))
            }
        }
        // Shape 2: step 1 already stepped `old` to `(){|}` (caret at `(` + 3),
        // and Sora's step 2 arrives with `({})` at the same span.
        if (newValue.text.length == old.text.length &&
            caret >= 3 && caret < old.text.length &&
            old.text.substring(caret - 3, caret + 1) == "(){}"
        ) {
            val soraSecondHalf =
                old.text.substring(0, caret - 3) + "({})" + old.text.substring(caret + 1)
            if (newValue.text == soraSecondHalf) {
                return old
            }
        }
        return null
    }

    // -------------------------------------------------------------------------
    // Dispatcher — single entry for ViewModel
    // -------------------------------------------------------------------------

    /**
     * Main dispatcher: given [old] and raw [newValue] (the buffer after IME/sora's default handling),
     * returns the smart-corrected value, or [newValue] if no rule applied.
     * The ViewModel calls this before recording undo.
     */
    fun transform(
        old: TextFieldValue,
        newValue: TextFieldValue,
        language: LanguageType?,
        tabSize: Int = 4,
        config: Config = Config(),
        suppressAutoPair: Boolean = false,
        indentBackspaceGuard: Boolean = true
    ): TextFieldValue {
        // Quick path: selection-only change (no text change) — nothing to smart-handle.
        if (old.text == newValue.text) return newValue

        // Phase 75.1 — Backspace inside leading indentation removes exactly one
        // space, whatever multi-space deletion the surface asked for. Checked
        // first: this is the editor's answer to a Backspace press, and no rule
        // below has a claim on a deletion. The only caller that opts out is the
        // explicit word-delete cap (that key means "a word", not "one press of
        // ⌫").
        if (indentBackspaceGuard) {
            handleIndentBackspace(old, newValue)?.let { return it }
        }

        // Phase 75.3 — `{` or `{}` inside empty `(|)` steps past `)` to `(){|}`.
        if (!suppressAutoPair) {
            handleBraceInEmptyParens(old, newValue, config)?.let { return it }
        }

        // Detect single char insertion.
        if (newValue.text.length == old.text.length + 1 && old.selection.collapsed) {
            val caretOld = old.selection.start.coerceIn(0, old.text.length)
            val caretNew = newValue.selection.start.coerceIn(0, newValue.text.length)
            // Incoming char is at caretNew-1
            val incoming = newValue.text.getOrNull(caretNew - 1)?.toString() ?: ""
            // Phase 75.2 — a lone Tab is one level of spaces, on every surface.
            // No rule below claims '\t' (it is neither a closer nor an opener),
            // so this is checked first and simply falls through when the edit is
            // not the naive single-character insert.
            if (incoming == "\t") {
                handleTabAsIndent(old, newValue, tabSize)?.let { return it }
            }
            // Try type-over first (for closers).
            handleTypeOver(old, incoming, config, language)?.let { return it }
            // Try dedent on closer (typing } on indented line)
            handleDedentOnCloser(old, incoming, language, tabSize, config)?.let { return it }
            // Try auto-pair for openers (insert matching closer and keep caret inside)
            // Only when newValue is the naive single-char insert; replace with pair.
            // Detect naive: newValue == old[0:caretOld] + incoming + old[caretOld:]
            // Skipped where the surface has its OWN pair key: the editor key
            // strip's swipe-up single '(' must stay single (it has a `()` cap).
            // Phase 30 device round (2026-09-07) — CodeC Keys is NOT such a
            // surface: its SYM layer is one key per char and its commits go
            // through the VM, so sora's SymbolPairMatch never sees them. With
            // pairing suppressed there the IME-free keyboard closed nothing —
            // the owner's "want auto brackets close" report.
            if (!suppressAutoPair) {
                val naive = old.text.substring(0, caretOld) + incoming + old.text.substring(caretOld)
                if (newValue.text == naive) {
                    handleAutoPair(old, incoming, config, language)?.let { return it }
                }
            }
        }

        // Wrap-selection: detect selection replaced by single opener.
        if (!old.selection.collapsed) {
            val oldStart = minOf(old.selection.start, old.selection.end)
            val oldEnd = maxOf(old.selection.start, old.selection.end)
            val selectedLen = oldEnd - oldStart
            // If newValue replaced selection with single char opener (plus maybe selection kept? but default would be replacement)
            // Heuristic: new length = old length - selectedLen + 1, and new text contains old selection? No default would drop selection.
            // So we can detect incoming as the char that replaced selection at oldStart.
            if (newValue.text.length == old.text.length - selectedLen + 1) {
                val incoming = newValue.text.getOrNull(oldStart)?.toString() ?: ""
                handleWrapSelection(old, incoming, config)?.let { return it }
            }
            if (newValue.text.length == old.text.length - selectedLen + 2) {
                // Pair insertion via strip already handled as Pair, but if incoming was Pair via IME? Not.
            }
        }

        // Auto-indent for newline.
        if (newValue.text.length == old.text.length + 1) {
            handleAutoIndent(old, newValue, language, tabSize, config)?.let { return it }
        }

        // Empty-pair backspace handling is for deletions, not insertions.
        // Detect backspace deletion (length -1 or -2, selection collapsed). More permissive: if old was (|) and
        // newValue deleted at least one side, return the both-deleted smart value. This fixes hardware/Gboard
        // deletions via sora where naive string comparison was brittle (caret vs index mismatch).
        if ((newValue.text.length == old.text.length - 1 || newValue.text.length == old.text.length - 2)
            && old.selection.collapsed && newValue.selection.collapsed) {
            handleEmptyPairBackspace(old, config, language)?.let { smart ->
                // Naive single-char deletion at caret-1
                val caret = old.selection.start
                if (caret > 0) {
                    val naiveSingle = if (caret <= old.text.length) old.text.substring(0, caret - 1) + old.text.substring(caret) else ""
                    val naiveBoth = if (caret < old.text.length) old.text.substring(0, caret - 1) + old.text.substring(caret + 1) else ""
                    // If sora already deleted both, newValue == smart.text — keep smart (already correct)
                    // If sora deleted one, newValue == naiveSingle — upgrade to smart
                    if (newValue.text == naiveSingle || newValue.text == naiveBoth || newValue.text == smart.text) {
                        return smart
                    }
                    // Fallback: if old had empty pair and new length is reduced, still apply smart (device-observed case
                    // where Gboard's deleteSurroundingText produces same length but caret differs)
                    val left = if (caret > 0) old.text.getOrNull(caret - 1) else null
                    val right = if (caret < old.text.length) old.text.getOrNull(caret) else null
                    if (left != null && right != null && openToClose[left] == right) {
                        // Old was empty pair; if new doesn't contain that pair at that location, treat as pair delete
                        if (!newValue.text.contains("" + left + right) || newValue.text.length < old.text.length) {
                            return smart
                        }
                    }
                }
            }
        }

        return newValue
    }
}
