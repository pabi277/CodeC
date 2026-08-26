package com.codeci.ide

import com.codeci.ide.ui.utils.WebFileSupport
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WebFileSupportTest {

    @Test
    fun `isHtml recognizes html and htm only`() {
        assertTrue(WebFileSupport.isHtml("index.html"))
        assertTrue(WebFileSupport.isHtml("PAGE.HTML"))
        assertTrue(WebFileSupport.isHtml("page.htm"))
        assertFalse(WebFileSupport.isHtml("style.css"))
        assertFalse(WebFileSupport.isHtml("app.js"))
        assertFalse(WebFileSupport.isHtml("main.c"))
    }

    @Test
    fun `isWeb recognizes all four web extensions`() {
        listOf("a.html", "a.htm", "a.css", "a.js").forEach {
            assertTrue("expected web: $it", WebFileSupport.isWeb(it))
        }
        listOf("a.c", "a.txt", "a", "a.html.bak").forEach {
            assertFalse("expected not web: $it", WebFileSupport.isWeb(it))
        }
    }

    @Test
    fun `normalizeFileName keeps source and web extensions and defaults to c`() {
        // C behavior unchanged: a bare name still becomes a .c file.
        assertEquals("foo.c", WebFileSupport.normalizeFileName("foo"))
        assertEquals("main.c", WebFileSupport.normalizeFileName("main.c"))
        // Web names keep their extension.
        assertEquals("index.html", WebFileSupport.normalizeFileName("index.html"))
        assertEquals("style.css", WebFileSupport.normalizeFileName("style.css"))
        assertEquals("script.js", WebFileSupport.normalizeFileName("script.js"))
        assertEquals("page.htm", WebFileSupport.normalizeFileName("page.htm"))
    }

    @Test
    fun `starterContent returns the right template per file type`() {
        assertTrue(WebFileSupport.starterContent("main.c").contains("int main()"))
        assertTrue(WebFileSupport.starterContent("index.html").contains("<!doctype html>"))
        assertTrue(WebFileSupport.starterContent("style.css").contains("background"))
        assertTrue(WebFileSupport.starterContent("script.js").contains("console.log"))
        // A bare name (no recognized extension) defaults to the C template.
        assertTrue(WebFileSupport.starterContent("whatever").contains("int main()"))
    }
}
