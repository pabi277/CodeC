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
import io.github.rosemoe.sora.lang.styling.TextStyle
import io.github.rosemoe.sora.lang.styling.Styles
import io.github.rosemoe.sora.text.CharPosition
import io.github.rosemoe.sora.text.Content
import io.github.rosemoe.sora.text.ContentReference
import io.github.rosemoe.sora.text.TextRange
import io.github.rosemoe.sora.widget.SymbolPairMatch
import io.github.rosemoe.sora.widget.schemes.EditorColorScheme
import io.github.rosemoe.sora.langs.textmate.TextMateLanguage

/**
 * Phase 25.2 — token kind → sora color-slot id. Pure; host-tested.
 *
 * Used ONLY by the regex fallback analyzer below (29.3: the live editor
 * colours through TextMate scopes; these slot ids remain for the fallback
 * path, SmartTyping probes and tests).
 *
 * Sora 0.24 has no dedicated STRING slot: strings and numbers both live in
 * [EditorColorScheme.LITERAL] in this adapter.
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
 * Phase 25.2 — CodeC's regex tokenizer inside sora's analysis pipeline.
 *
 * **Phase 29.3: this is the FALLBACK, not the editor hot path.** The live
 * analyzer is sora's TextMate `AsyncIncrementalAnalyzeManager` (VS Code
 * grammars — see [TextMateSupport]); this class survives for files with no
 * TextMate grammar (TEXT) and as the safety net when a grammar fails to
 * load. `MultiLanguageSyntaxHighlighter.tokenize` is therefore no longer
 * called per keystroke for any colourable language.
 *
 * Extends [SimpleAnalyzeManager], which runs [analyze] on its OWN background
 * thread with latest-request-wins semantics.
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
 * Phase 25.2 → 29 — the sora `Language` for the CodeC editor window.
 *
 * - **Analyzer (29.1):** a sora [TextMateLanguage] built from the VS Code
 *   grammar for the file's language (see [TextMateSupport]). Only
 *   `getAnalyzeManager()` changed from 25.2 (T5): everything below is still
 *   CodeC's own, so completions, indent behaviour and symbol pairs behave
 *   exactly as in the device-accepted 25.2/26/27 rounds.
 * - **Completions:** `requireAutoComplete` feeds CodeC's existing engine
 *   results to sora's NATIVE panel at the caret. The TextMate language is
 *   created with `collectIdentifiers = false` — its own identifier
 *   completion never runs.
 * - **Indent:** [indentAdvanceFor] gives one level after a line that opens a
 *   block (`{`, or `:` for Python); sora preserves the current line's
 *   indentation itself.
 * - **Symbol pairs:** standard C-family pairs + quotes, so `(` auto-closes
 *   and typing `)` over `)` skips (the 25.1 device evidence).
 * - **Formatter:** no-op — CodeC formats through the VM (clang-format /
 *   built-in, Phase 24 E.1) by editing the buffer, not through sora.
 *
 * Lifecycle: sora's `CodeEditor.setEditorLanguage` destroys the analyzer
 * returned by [getAnalyzeManager] and then calls [destroy] on the OLD
 * language — so [destroy] must not touch that analyzer again (it destroys
 * the TextMate language object or the fallback, never both paths).
 */
class CodeCLanguage private constructor(
    private val language: LanguageType,
    private val textMate: TextMateLanguage?,
    private val fallbackAnalyzer: CodeCAnalyzer
) : Language {

    override fun getAnalyzeManager(): AnalyzeManager =
        textMate?.analyzeManager ?: fallbackAnalyzer

    override fun getInterruptionLevel(): Int = Language.INTERRUPTION_LEVEL_NONE

    override fun requireAutoComplete(
        content: ContentReference,
        position: CharPosition,
        publisher: CompletionPublisher,
        extraArguments: Bundle
    ) {
        // Phase 25.2 device-round 3 (owner: "sora is better than that") —
        // sora's OWN panel now serves completions. The engine and its
        // results are the same as before — only the RENDERER changed.
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
        // The editor destroys the analyzer returned by getAnalyzeManager()
        // (see class KDoc) — only tear down what it does NOT.
        if (textMate == null) fallbackAnalyzer.destroy() else textMate.destroy()
    }

    companion object {

        /**
         * Build the language for a file. The TextMate grammar set for
         * [language]/[fileName] must already be loaded
         * ([TextMateSupport.ensureLanguageLoaded] — the editor host does
         * this on a background coroutine before calling us). If the grammar
         * is unavailable the 25.2 regex analyzer takes over so a broken
         * asset degrades to 22.x colour, never to a crash.
         */
        fun create(language: LanguageType, fileName: String? = null): CodeCLanguage {
            val scope = TextMateGrammars.scopeFor(language, fileName)
            val textMate = scope?.let {
                runCatching { TextMateLanguage.create(it, /* collectIdentifiers = */ false) }
                    .getOrNull()
            }
            return CodeCLanguage(language, textMate, CodeCAnalyzer(language))
        }

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
            match.putPair('\"', SymbolPairMatch.SymbolPair("\"", "\""))
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
