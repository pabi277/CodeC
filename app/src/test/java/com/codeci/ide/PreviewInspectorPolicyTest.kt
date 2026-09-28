package com.codeci.ide

import com.codeci.ide.ui.services.PreviewConsolePolicy
import com.codeci.ide.ui.services.PreviewDomAttribute
import com.codeci.ide.ui.services.PreviewDomNode
import com.codeci.ide.ui.services.PreviewInspectorPolicy
import org.junit.Assert.*
import org.junit.Test

/**
 * Phase 72.1 — the Elements tab's wire format, both directions.
 *
 * The scripts are asserted by their bounds and their handles (the numbered
 * `__codecDom` array), and the parsers against hand-built answers, including
 * the malformed rows a page can always produce.
 */
class PreviewInspectorPolicyTest {

    private fun node(
        index: Int = 0,
        depth: Int = 0,
        tag: String = "div",
        id: String = "",
        classes: String = "",
        text: String = "",
    ) = PreviewDomNode(index, depth, tag, id, classes, text)

    @Test fun `the tree script numbers every node and bounds the walk`() {
        val script = PreviewInspectorPolicy.treeScript()
        assertTrue(script.contains("window.__codecDom=all"))
        assertTrue(script.contains("all.length>=300"))
        assertTrue(script.contains("d>12"))
        assertTrue(script.contains("document.documentElement"))
        val narrow = PreviewInspectorPolicy.treeScript(maxNodes = 40, maxDepth = 4)
        assertTrue(narrow.contains("all.length>=40"))
        assertTrue(narrow.contains("d>4"))
    }

    @Test fun `tree rows parse and malformed rows are skipped`() {
        val first = listOf("0", "0", "html", "", "", "").joinToString("\u0001")
        val second = listOf("1", "1", "body", "main", "a b", "hello").joinToString("\u0001")
        val third = listOf("2", "2", "div", "", "", "").joinToString("\u0001")
        val raw = PreviewConsolePolicy.quote(
            listOf(first, second, "not a row", third).joinToString("\n")
        )
        val tree = PreviewInspectorPolicy.parseTree(raw)
        assertEquals(3, tree.size)
        assertEquals("html", tree[0].tag)
        assertEquals(0, tree[0].depth)
        assertEquals("main", tree[1].id)
        assertEquals("a b", tree[1].classes)
        assertEquals("hello", tree[1].text)
        assertEquals(2, tree[2].depth)
        assertTrue(PreviewInspectorPolicy.parseTree(null).isEmpty())
    }

    @Test fun `a selector prefers the id, then the tag with its classes`() {
        assertEquals("#main", PreviewInspectorPolicy.selector(node(id = "main", classes = "hero")))
        assertEquals("div.a.b", PreviewInspectorPolicy.selector(node(classes = "a  b")))
        assertEquals("span", PreviewInspectorPolicy.selector(node(tag = "span")))
    }

    @Test fun `details parse the tag, the box, the text and every attribute`() {
        val head = listOf("a", "40×16", "Home").joinToString("\u0002")
        val attribute = listOf("href", "/index.html").joinToString("\u0001")
        val raw = PreviewConsolePolicy.quote(head + "\n" + attribute)
        val details = PreviewInspectorPolicy.parseDetails(raw)!!
        assertEquals("a", details.tag)
        assertEquals("40×16", details.box)
        assertEquals("Home", details.text)
        assertEquals(PreviewDomAttribute("href", "/index.html"), details.attributes.single())
        assertNull(PreviewInspectorPolicy.parseDetails(null))
        assertNull(PreviewInspectorPolicy.parseDetails(PreviewConsolePolicy.quote("")))
    }

    @Test fun `highlight and html scripts address one numbered node only`() {
        assertTrue(PreviewInspectorPolicy.highlightScript(7).contains("__codecDom||[])[7]"))
        assertTrue(PreviewInspectorPolicy.highlightScript(7).contains("__codecHighlighted"))
        // Negative and absent indexes clear the outline instead of guessing.
        assertTrue(PreviewInspectorPolicy.highlightScript(-1).contains("if(-1<0)return 'off'"))
        assertTrue(PreviewInspectorPolicy.highlightScript(null).contains("if(null<0)"))
        assertTrue(PreviewInspectorPolicy.htmlScript(3).contains("__codecDom||[])[3]"))
        assertTrue(PreviewInspectorPolicy.htmlScript(3).contains("outerHTML"))
        assertTrue(PreviewInspectorPolicy.htmlScript(3).contains("4000"))
    }
}
