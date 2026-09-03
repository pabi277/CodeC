package com.codeci.ide

import androidx.compose.ui.text.AnnotatedString
import com.codeci.ide.ui.editor.DiagnosticSeverity
import com.codeci.ide.ui.editor.EditorDiagnostic
import com.codeci.ide.ui.theme.EditorThemeType
import com.codeci.ide.ui.theme.getEditorTheme
import com.codeci.ide.ui.utils.EditorDecorations
import com.codeci.ide.ui.utils.HighlightedCode
import com.codeci.ide.ui.utils.LanguageType
import com.codeci.ide.ui.utils.MultiLanguageSyntaxHighlighter
import com.codeci.ide.ui.utils.SyntaxVisualTransformation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 22.1 — the debounced off-thread highlight cache. The rule the editor
 * depends on: a cached snapshot may only be reused when the buffer text, the
 * theme and the language all still match; otherwise the transformation must
 * fall back to highlighting inline so text is never mis-colored.
 */
class EditorHighlightCacheTest {

    private val source = "int main() { return 0; }"

    @Test
    fun `snapshot matches its own inputs`() {
        val snapshot = HighlightedCode.of(source, EditorThemeType.DRACULA, LanguageType.C)
        assertTrue(snapshot.matches(source, EditorThemeType.DRACULA, LanguageType.C))
    }

    @Test
    fun `snapshot is stale after an edit`() {
        val snapshot = HighlightedCode.of(source, EditorThemeType.DRACULA, LanguageType.C)
        assertFalse(snapshot.matches(source + "\n", EditorThemeType.DRACULA, LanguageType.C))
    }

    @Test
    fun `snapshot is stale after a theme or language switch`() {
        val snapshot = HighlightedCode.of(source, EditorThemeType.DRACULA, LanguageType.C)
        val otherTheme = EditorThemeType.entries.first { it != EditorThemeType.DRACULA }
        assertFalse(snapshot.matches(source, otherTheme, LanguageType.C))
        assertFalse(snapshot.matches(source, EditorThemeType.DRACULA, LanguageType.PYTHON))
    }

    @Test
    fun `snapshot carries the same spans the inline highlighter produces`() {
        val snapshot = HighlightedCode.of(source, EditorThemeType.DRACULA, LanguageType.C)
        val inline = SyntaxVisualTransformation(EditorThemeType.DRACULA, EditorDecorations(), LanguageType.C)
            .filter(AnnotatedString(source))
        assertEquals(inline.text.spanStyles, snapshot.annotated.spanStyles)
    }

    @Test
    fun `a matching cache produces the same output as highlighting inline`() {
        val snapshot = HighlightedCode.of(source, EditorThemeType.DRACULA, LanguageType.C)
        val decorations = EditorDecorations(currentLineRange = 0..4)
        val cached = SyntaxVisualTransformation(
            EditorThemeType.DRACULA, decorations, LanguageType.C, snapshot
        ).filter(AnnotatedString(source))
        val inline = SyntaxVisualTransformation(
            EditorThemeType.DRACULA, decorations, LanguageType.C, null
        ).filter(AnnotatedString(source))
        assertEquals(inline.text.text, cached.text.text)
        assertEquals(inline.text.spanStyles, cached.text.spanStyles)
    }

    @Test
    fun `a stale cache is ignored and the buffer is highlighted correctly`() {
        val stale = HighlightedCode.of("int x;", EditorThemeType.DRACULA, LanguageType.C)
        val out = SyntaxVisualTransformation(
            EditorThemeType.DRACULA, EditorDecorations(), LanguageType.C, stale
        ).filter(AnnotatedString(source))
        assertEquals(source, out.text.text)
        assertNotEquals(stale.annotated.spanStyles, out.text.spanStyles)
    }

    @Test
    fun `offsets stay identity mapped with a cache in play`() {
        val snapshot = HighlightedCode.of(source, EditorThemeType.DRACULA, LanguageType.C)
        val out = SyntaxVisualTransformation(
            EditorThemeType.DRACULA, EditorDecorations(), LanguageType.C, snapshot
        ).filter(AnnotatedString(source))
        assertEquals(7, out.offsetMapping.originalToTransformed(7))
        assertEquals(7, out.offsetMapping.transformedToOriginal(7))
    }

    // ---- Phase 22.7: span windowing ---------------------------------------

    @Test
    fun `windowing collapses the span count on a long file`() {
        // The real cause of the long-file lag: BasicTextField's layout cost
        // scales with the SPAN count, not the character count
        // (compose-multiplatform#4023 / CMP-4023, closed WONTFIX). A 500-line
        // C file produced thousands of spans, all handed to the field on every
        // layout pass.
        val longFile = (1..500).joinToString("\n") {
            "int compute_$it(int a, int b) { return (a * $it) + b; } // note"
        }
        val full = MultiLanguageSyntaxHighlighter.highlight(
            longFile, getEditorTheme(EditorThemeType.DRACULA), LanguageType.C
        )
        val windowed = HighlightedCode.of(longFile, EditorThemeType.DRACULA, LanguageType.C, caret = 0)
        assertTrue(
            "windowed=${windowed.annotated.spanStyles.size} full=${full.spanStyles.size}",
            windowed.annotated.spanStyles.size < full.spanStyles.size
        )
    }

    @Test
    fun `the text itself is never truncated by windowing`() {
        val longFile = (1..500).joinToString("\n") { "int value_$it = $it;" }
        val windowed = HighlightedCode.of(longFile, EditorThemeType.DRACULA, LanguageType.C, caret = 0)
        assertEquals(longFile, windowed.annotated.text)
    }

    @Test
    fun `colours inside the window match a full-file highlight exactly`() {
        // Windowing must be invisible where the user is looking: scanning
        // still starts at offset 0 so multi-line constructs keep their
        // context, and only span EMISSION is bounded.
        val source = "/* block */\nint main() { return 0; }\n" + "// filler\n".repeat(200)
        val full = MultiLanguageSyntaxHighlighter.highlight(
            source, getEditorTheme(EditorThemeType.DRACULA), LanguageType.C
        )
        val windowed = MultiLanguageSyntaxHighlighter.highlight(
            source, getEditorTheme(EditorThemeType.DRACULA), LanguageType.C, from = 0, to = 40
        )
        // Compare token spans only: both outputs also carry one base-colour
        // span covering the whole text, which is not a token.
        val tokensIn = { a: AnnotatedString ->
            a.spanStyles.filter { it.end - it.start < source.length && it.start < 40 }
        }
        assertEquals(tokensIn(full), tokensIn(windowed))
        assertTrue(tokensIn(windowed).isNotEmpty())
    }

    @Test
    fun `a snapshot is stale once the caret leaves its window`() {
        val text = "x".repeat(HighlightedCode.WINDOW * 3)
        val snapshot = HighlightedCode.of(text, EditorThemeType.DRACULA, LanguageType.C, caret = 0)
        // Still valid where it was built...
        assertTrue(snapshot.matches(text, EditorThemeType.DRACULA, LanguageType.C, 0, 10))
        // ...but not for a window far past its end.
        assertFalse(
            snapshot.matches(
                text, EditorThemeType.DRACULA, LanguageType.C,
                text.length - 10, text.length
            )
        )
    }

    @Test
    fun `the window follows the caret`() {
        val text = "y".repeat(HighlightedCode.WINDOW * 3)
        val atEnd = HighlightedCode.of(text, EditorThemeType.DRACULA, LanguageType.C, caret = text.length)
        assertTrue(atEnd.from > 0)
        assertEquals(text.length, atEnd.to)
    }

    @Test
    fun `repeated filter calls on the same text reuse one result`() {
        // Phase 22.4 — filter() runs on every LAYOUT pass, and the soft
        // keyboard's open/close animation relayouts every frame. The memo is
        // what stops each of those frames rebuilding the whole decorated
        // string; identity equality proves no rebuild happened.
        val snapshot = HighlightedCode.of(source, EditorThemeType.DRACULA, LanguageType.C)
        val transformation = SyntaxVisualTransformation(
            EditorThemeType.DRACULA,
            EditorDecorations(currentLineRange = 0..4),
            LanguageType.C,
            snapshot
        )
        val first = transformation.filter(AnnotatedString(source))
        val second = transformation.filter(AnnotatedString(source))
        assertSame(first, second)
    }

    @Test
    fun `the memo never serves a result for different text`() {
        val transformation = SyntaxVisualTransformation(
            EditorThemeType.DRACULA, EditorDecorations(), LanguageType.C, null
        )
        val first = transformation.filter(AnnotatedString(source))
        val edited = transformation.filter(AnnotatedString("$source // tail"))
        assertEquals(source, first.text.text)
        assertEquals("$source // tail", edited.text.text)
    }

    @Test
    fun `empty decorations are detected so the decoration pass can be skipped`() {
        assertTrue(EditorDecorations().isEmpty())
        assertFalse(EditorDecorations(currentLineRange = 0..3).isEmpty())
        assertFalse(EditorDecorations(findMatches = listOf(0..2)).isEmpty())
        assertFalse(EditorDecorations(activeFindMatch = 0..2).isEmpty())
        assertFalse(EditorDecorations(bracketRanges = listOf(0..1)).isEmpty())
        assertFalse(
            EditorDecorations(
                diagnostics = listOf(EditorDiagnostic(1, 1, "boom", DiagnosticSeverity.ERROR))
            ).isEmpty()
        )
    }
}
