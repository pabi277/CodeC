package com.codeci.ide.ui.editor.sora

import android.os.Bundle
import com.codeci.ide.ui.editor.CodeCompletionEngine
import com.codeci.ide.ui.utils.LanguageType
import com.codeci.ide.ui.utils.MultiLanguageSyntaxHighlighter
import com.codeci.ide.ui.utils.TokenKind
import io.github.rosemoe.sora.lang.Language
import io.github.rosemoe.sora.lang.analysis.AnalyzeManager
import io.github.rosemoe.sora.lang.analysis.SimpleAnalyzeManager
import io.github.rosemoe.sora.lang.completion.CompletionItemKind
import io.github.rosemoe.sora.lang.completion.CompletionPublisher
import io.github.rosemoe.sora.lang.completion.SimpleCompletionItem
import io.github.rosemoe.sora.lang.format.Formatter
import io.github.rosemoe.sora.lang.styling.MappedSpans
import io.github.rosemoe.sora.lang.styling.SpanFactory
import io.github.rosemoe.sora.lang.styling.Styles
import io.github.rosemoe.sora.lang.styling.TextStyle
import io.github.rosemoe.sora.text.CharPosition
import io.github.rosemoe.sora.text.Content
import io.github.rosemoe.sora.text.ContentReference
import io.github.rosemoe.sora.text.TextRange
import io.github.rosemoe.sora.widget.CodeEditor
import io.github.rosemoe.sora.widget.SymbolPairMatch
import io.github.rosemoe.sora.widget.schemes.EditorColorScheme

/**
 * Phase 25.2 — token kind → sora color-slot id. Pure; host-tested.
 *
 * Sora 0.24 has no dedicated STRING slot: strings and numbers both live in
 * [EditorColorScheme.LITERAL] in this adapter (the CodeC themes color them
 * identically via `EditorThemeColors.string`, and numbers share it — the
 * app's BasicTextField renderer made the same compromise visually).
 */
object TokenStyleIds {
    fun styleIdFor(kind: TokenKind): Int = when (kind) {
        TokenKind.KEYWORD -> EditorColorScheme.KEYWORD
        TokenKind.STRING -> EditorColorScheme.LITERAL
        TokenKind.NUMBER -> EditorColorScheme.LITERAL
        TokenKind.COMMENT -> EditorColorScheme.COMMENT
        TokenKind.FUNCTION -> EditorColorScheme.FUNCTION_NAME
        TokenKind.OPERATOR -> EditorColorScheme.OPERATOR
        TokenKind.DECORATOR -> EditorColorScheme.ANNOTATION
    }
}

/**
 * Phase 25.2 — CodeC's tokenizer inside sora's analysis pipeline.
 *
 * Extends [SimpleAnalyzeManager], which runs [analyze] on its OWN background
 * thread with latest-request-wins semantics (insert/delete just schedule a
 * full re-run). The analyzer therefore tokenizes the whole buffer with the
 * SAME regex rules the app always used (`MultiLanguageSyntaxHighlighter.tokenize`)
 * OFF the main thread — the budget-critical difference from Phase 22 is that
 * nothing ever tokenizes on the UI thread anymore and the WIDGET no longer
 * relayouts spans per keystroke (sora draws its own line-partitioned spans).
 *
 * This is the honest v1: full re-tokenize per settled edit (a 175 kB file is
 * a background regex sweep; the 517-line HTML is ~2 ms). Incremental
 * per-line lexing is a follow-up behind `AsyncIncrementalAnalyzeManager`.
 */
class CodeCAnalyzer(private val language: LanguageType) : SimpleAnalyzeManager<Int>() {

    override fun analyze(text: StringBuilder, delegate: Delegate<Int>): Styles {
        val content = text.toString()
        val spans = MultiLanguageSyntaxHighlighter.tokenize(content, language)
        val builder = MappedSpans.Builder(content.length.coerceAtLeast(128))
        val positions = LineColumnCursor(content)
        for (span in spans) {
            val (line, column) = positions.advance(span.start)
            builder.addIfNeeded(line, column, TextStyle.makeStyle(TokenStyleIds.styleIdFor(span.kind)))
            positions.advance(span.end) // keep the cursor at the span end
        }
        builder.addNormalIfNull()
        builder.determine(content.count { it == '\n' })
        return Styles(builder.build())
    }
}

/**
 * Offset → (line, column) cursor that only moves FORWARD over ordered spans —
 * one O(text) walk for the whole file. Pure; host-tested.
 */
class LineColumnCursor(private val text: String) {
    private var offset = 0
    private var line = 0
    private var column = 0

    fun advance(target: Int): Pair<Int, Int> {
        val t = target.coerceIn(offset, text.length)
        while (offset < t) {
            if (text[offset] == '\n') {
                line++
                column = 0
            } else {
                column++
            }
            offset++
        }
        return line to column
    }
}

/**
 * Phase 25.2 — the sora `Language` for the CodeC editor window.
 *
 * - Analyzer: [CodeCAnalyzer] (CodeC regex rules → sora spans).
 * - Completions: `requireAutoComplete` feeds CodeC's existing engine
 *   results to sora's NATIVE panel at the caret (device round 3, owner
 *   request — replaces the app's bottom-anchored popup).
 * - Indent: [indentAdvanceFor] gives one level after a line that opens a
 *   block (`{`, or `:` for Python); sora preserves the current line's
 *   indentation itself.
 * - Symbol pairs: standard C-family pairs + quotes, so `(` auto-closes and
 *   typing `)` over `)` skips (the 25.1 device evidence).
 * - Formatter: no-op — CodeC formats through the VM (clang-format / built-in,
 *   Phase 24 E.1) by editing the buffer, not through sora.
 */
class CodeCLanguage(private val language: LanguageType) : Language {

    private val analyzer = CodeCAnalyzer(language)

    override fun getAnalyzeManager(): AnalyzeManager = analyzer

    override fun getInterruptionLevel(): Int = Language.INTERRUPTION_LEVEL_NONE

    override fun requireAutoComplete(
        content: ContentReference,
        position: CharPosition,
        publisher: CompletionPublisher,
        extraArguments: Bundle
    ) {
        // Phase 25.2 device-round 3 (owner: "sora is better than that") —
        // sora's OWN panel now serves completions (enabled by default in
        // CodeEditor; this is the only hook it needs). The engine and its
        // results are the same as before — only the RENDERER changed: a
        // native popup at the caret replaces the app's bottom-anchored one.
        // Runs on sora's completion thread (fast, windowed engine).
        val text = content.toString()
        val cursor = position.index.coerceIn(0, text.length)
        val prefixLength = CodeCompletionEngine.currentPrefix(text, cursor).length
        for (item in CodeCompletionEngine.completions(text, cursor, language)) {
            publisher.addItem(
                SimpleCompletionItem(item.label, item.detail, prefixLength, item.insertText)
                    .kind(
                        when (item.kind) {
                            com.codeci.ide.ui.editor.CompletionKind.SNIPPET ->
                                CompletionItemKind.Snippet
                            com.codeci.ide.ui.editor.CompletionKind.KEYWORD ->
                                CompletionItemKind.Keyword
                            com.codeci.ide.ui.editor.CompletionKind.IDENTIFIER ->
                                CompletionItemKind.Identifier
                        }
                    )
            )
        }
    }

    override fun getIndentAdvance(content: ContentReference, line: Int, column: Int): Int {
        if (line !in 0 until content.lineCount) return 0
        return indentAdvanceFor(content.getLine(line))
    }

    override fun useTab(): Boolean = false

    override fun getFormatter(): Formatter = NoOpFormatter

    override fun getSymbolPairs(): SymbolPairMatch = symbolPairsFor(language)

    override fun getNewlineHandlers(): Array<io.github.rosemoe.sora.lang.smartEnter.NewlineHandler> = arrayOf()

    override fun destroy() {
        analyzer.destroy()
    }

    companion object {

        /** Pure indent rule: one more level after a block-opener. Host-tested. */
        fun indentAdvanceFor(lineText: String, language: LanguageType = LanguageType.C): Int {
            val trimmed = lineText.trimEnd()
            if (trimmed.isEmpty()) return 0
            return when {
                trimmed.endsWith('{') -> 1
                language == LanguageType.PYTHON &&
                    trimmed.endsWith(':') &&
                    !trimmed.trimStart().startsWith("#") -> 1
                else -> 0
            }
        }

        /** Pure pair table: standard code pairs, none for prose formats. Host-tested. */
        fun symbolPairsFor(language: LanguageType): SymbolPairMatch {
            val match = SymbolPairMatch()
            if (language == LanguageType.TEXT || language == LanguageType.MARKDOWN) return match
            match.putPair('(', SymbolPairMatch.SymbolPair("(", ")"))
            match.putPair('[', SymbolPairMatch.SymbolPair("[", "]"))
            match.putPair('{', SymbolPairMatch.SymbolPair("{", "}"))
            match.putPair('"', SymbolPairMatch.SymbolPair("\"", "\""))
            match.putPair('\'', SymbolPairMatch.SymbolPair("'", "'"))
            return match
        }
    }
}

/** No-op formatter (CodeC formats via the VM pipeline; see class KDoc). */
private object NoOpFormatter : Formatter {
    override fun format(text: Content, cursorRange: TextRange) = Unit
    override fun formatRegion(text: Content, rangeToFormat: TextRange, cursorRange: TextRange) = Unit
    override fun setReceiver(receiver: io.github.rosemoe.sora.lang.format.Formatter.FormatResultReceiver?) = Unit
    override fun isRunning() = false
    override fun destroy() = Unit
}
