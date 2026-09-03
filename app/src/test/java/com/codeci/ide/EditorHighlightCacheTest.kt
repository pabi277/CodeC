package com.codeci.ide

import androidx.compose.ui.text.AnnotatedString
import com.codeci.ide.ui.editor.DiagnosticSeverity
import com.codeci.ide.ui.editor.EditorDiagnostic
import com.codeci.ide.ui.theme.EditorThemeType
import com.codeci.ide.ui.utils.EditorDecorations
import com.codeci.ide.ui.utils.HighlightedCode
import com.codeci.ide.ui.utils.LanguageType
import com.codeci.ide.ui.utils.SyntaxVisualTransformation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
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
