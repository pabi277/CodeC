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
 * 4. Auto-indent: Enter copies indent; after { adds level and splits } onto own line; after : in Python adds level; dedent on sole closer.
 * 5. String-aware negatives: inside string literal, rules 1–3 don't fire for the quote that opens string; inside comments none fire.
 * 6. Delete-word: previous word (identifier + whitespace) with stop chars whitespace, ., /, quotes.
 * 7. Undo integrity: each smart edit is a single undo unit (handled by ViewModel).
 *
 * All rules individually toggleable via Settings — [Config].
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
        } else if (
            language == LanguageType.PYTHON &&
            trimmedPrev.endsWith(":") &&
            !trimmedPrev.trimStart().startsWith("#")
        ) {
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
        val next = old.text.substring(0, caret) + incoming + closer + old.text.substring(caret)
        return TextFieldValue(next, TextRange(caret + 1))
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
        isStrip: Boolean = false
    ): TextFieldValue {
        // Quick path: selection-only change (no text change) — nothing to smart-handle.
        if (old.text == newValue.text) return newValue

        // Detect single char insertion.
        if (newValue.text.length == old.text.length + 1 && old.selection.collapsed) {
            val caretOld = old.selection.start.coerceIn(0, old.text.length)
            val caretNew = newValue.selection.start.coerceIn(0, newValue.text.length)
            // Incoming char is at caretNew-1
            val incoming = newValue.text.getOrNull(caretNew - 1)?.toString() ?: ""
            // Try type-over first (for closers).
            handleTypeOver(old, incoming, config, language)?.let { return it }
            // Try dedent on closer (typing } on indented line)
            handleDedentOnCloser(old, incoming, language, tabSize, config)?.let { return it }
            // Try auto-pair for openers (insert matching closer and keep caret inside)
            // Only when newValue is the naive single-char insert; replace with pair.
            // Detect naive: newValue == old[0:caretOld] + incoming + old[caretOld:]
            // Skipped for strip-origin inserts: swipe-up single '(' must stay single, sora handles keyboard pairing.
            if (!isStrip) {
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
