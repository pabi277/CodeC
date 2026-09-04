package com.codeci.bench.core

import com.codeci.ide.ui.utils.LanguageType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VisibleWindowAndSpansTest {

    // ---- VisibleWindow -----------------------------------------------------

    @Test
    fun `window covers viewport plus overscan`() {
        val range = VisibleWindow.range(firstVisible = 100, visibleCount = 40, overscan = 8, lineCount = 5_000)
        assertEquals(92, range.first)
        assertEquals(148, range.last)
    }

    @Test
    fun `window clamps at both edges`() {
        assertEquals(0, VisibleWindow.range(0, 40, 8, 5_000).first)
        val top = VisibleWindow.range(0, 40, 8, 5_000)
        assertEquals(48, top.last)
        val bottom = VisibleWindow.range(4_990, 40, 8, 5_000)
        assertEquals(4_999, bottom.last)
    }

    @Test
    fun `window is never empty even with a zero viewport`() {
        val range = VisibleWindow.range(10, 0, 0, 20)
        assertTrue(!range.isEmpty())
    }

    // ---- LineSpanCache -----------------------------------------------------

    @Test
    fun `per line spans are cached and invalidated per line`() {
        val buffer = DocumentBuffer("int main() {\n  return 0;\n}\n")
        val cache = LineSpanCache(LanguageType.C, com.codeci.ide.ui.theme.EditorThemeType.DRACULA)
        val line0 = cache.get(buffer, 0)
        val line0Again = cache.get(buffer, 0)
        assertEquals(1, cache.size) // served from cache
        assertEquals(line0, line0Again)

        cache.invalidateLines(0, 1)
        assertEquals(0, cache.size)
        // Still returns a (freshly computed) annotated string.
        assertTrue(cache.get(buffer, 0).length >= buffer.lineAt(0).length)
    }

    @Test
    fun `cache is bounded by capacity`() {
        val buffer = DocumentBuffer((1..50).joinToString("\n") { "int v$it = $it;" })
        val cache = LineSpanCache(LanguageType.C, com.codeci.ide.ui.theme.EditorThemeType.DRACULA, capacity = 4)
        for (line in 0 until 50) cache.get(buffer, line)
        assertTrue(cache.size <= 4)
    }

    @Test
    fun `invalidate range is exclusive of the until line`() {
        val buffer = DocumentBuffer("a\nb\nc")
        val cache = LineSpanCache(LanguageType.C, com.codeci.ide.ui.theme.EditorThemeType.DRACULA)
        cache.get(buffer, 0)
        cache.get(buffer, 1)
        cache.get(buffer, 2)
        assertEquals(3, cache.size)
        cache.invalidateLines(0, 2) // invalidates lines 0 and 1 only
        assertEquals(1, cache.size)
    }
}
