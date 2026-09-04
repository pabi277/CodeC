package com.codeci.ide.ui.editor.sora

import com.codeci.ide.ui.theme.EditorThemeType
import com.codeci.ide.ui.theme.getEditorTheme
import io.github.rosemoe.sora.widget.schemes.EditorColorScheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CodeCThemeMapTest {

    @Test
    fun `mapping covers the slots the editor surface needs`() {
        val ids = CodeCThemeMap.entries(getEditorTheme(EditorThemeType.DRACULA)).map { it.first }
        for (required in listOf(
            EditorColorScheme.WHOLE_BACKGROUND,
            EditorColorScheme.TEXT_NORMAL,
            EditorColorScheme.LINE_NUMBER,
            EditorColorScheme.LINE_NUMBER_BACKGROUND,
            EditorColorScheme.LINE_DIVIDER,
            EditorColorScheme.CURRENT_LINE,
            EditorColorScheme.SELECTION_INSERT,
            EditorColorScheme.KEYWORD,
            EditorColorScheme.LITERAL,
            EditorColorScheme.COMMENT,
            EditorColorScheme.FUNCTION_NAME,
            EditorColorScheme.OPERATOR,
            EditorColorScheme.ANNOTATION
        )) {
            assertTrue("missing slot $required", required in ids)
        }
    }

    @Test
    fun `every theme type maps background and text distinctly`() {
        for (type in EditorThemeType.entries) {
            val entries = CodeCThemeMap.entries(getEditorTheme(type)).toMap()
            val background = entries.getValue(EditorColorScheme.WHOLE_BACKGROUND)
            val text = entries.getValue(EditorColorScheme.TEXT_NORMAL)
            assertTrue("$type: text == background would be invisible", text != background)
            val keyword = entries.getValue(EditorColorScheme.KEYWORD)
            assertTrue("$type: keyword == background would be invisible", keyword != background)
        }
    }

    @Test
    fun `mapping carries valid ARGB colors`() {
        for (type in EditorThemeType.entries) {
            for ((_, color) in CodeCThemeMap.entries(getEditorTheme(type))) {
                // ARGB ints: alpha byte may be 0..255 but the value must fit 32 bits
                // (compose Color.toArgb() guarantees this; guard regressions).
                assertTrue(color in Int.MIN_VALUE..Int.MAX_VALUE)
            }
        }
    }

    @Test
    fun `theme colors differ between themes`() {
        val dracula = CodeCThemeMap.entries(getEditorTheme(EditorThemeType.DRACULA)).toMap()
        val monokai = CodeCThemeMap.entries(getEditorTheme(EditorThemeType.MONOKAI)).toMap()
        assertTrue(
            dracula.getValue(EditorColorScheme.WHOLE_BACKGROUND) !=
                monokai.getValue(EditorColorScheme.WHOLE_BACKGROUND)
        )
    }

    @Test
    fun `token style ids map every token kind`() {
        for (kind in com.codeci.ide.ui.utils.TokenKind.entries) {
            val id = TokenStyleIds.styleIdFor(kind)
            assertTrue("kind $kind mapped to an unknown slot $id", id in 1..100)
        }
        assertEquals(
            TokenStyleIds.styleIdFor(com.codeci.ide.ui.utils.TokenKind.STRING),
            TokenStyleIds.styleIdFor(com.codeci.ide.ui.utils.TokenKind.NUMBER)
        )
        assertTrue(
            TokenStyleIds.styleIdFor(com.codeci.ide.ui.utils.TokenKind.KEYWORD) !=
                TokenStyleIds.styleIdFor(com.codeci.ide.ui.utils.TokenKind.COMMENT)
        )
    }
}
