package com.codeci.ide.ui.editor.lsp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 31.5 — unit tests for the LSP response parser. No Android,
 * no real LSP server. We hand-craft the JSON-shaped strings the
 * parser would see on the wire and verify it extracts the
 * [LspItemMapping.LspShape] fields correctly.
 */
class LspResponseParserTest {

    @Test
    fun parsesTopLevelFields() {
        val r = LspResponseParser.parse(
            "{\"jsonrpc\":\"2.0\",\"id\":42,\"result\":{\"items\":[]}}"
        )
        assertEquals(42, r.id)
        assertNull(r.error)
        assertNotNull(r.result)
    }

    @Test
    fun parsesErrorField() {
        val r = LspResponseParser.parse(
            "{\"jsonrpc\":\"2.0\",\"id\":7,\"error\":{\"code\":-32601,\"message\":\"Method not found\"}}"
        )
        assertEquals(7, r.id)
        assertEquals("Method not found", r.error)
        assertNull(r.result)
    }

    @Test
    fun parsesCompletionItemArray() {
        // clangd's typical first-request response: a CompletionList
        // with an `isIncomplete` flag and a non-empty `items` array.
        // Each item carries `label`, `kind`, `detail`, `insertText`.
        val body = "{\"jsonrpc\":\"2.0\",\"id\":3,\"result\":{\"isIncomplete\":false,\"items\":[" +
            "{\"label\":\"printf\",\"kind\":3,\"detail\":\"int printf(const char *fmt, ...)\",\"insertText\":\"printf\"}," +
            "{\"label\":\"__stdin\",\"kind\":6,\"detail\":\"FILE * __stdin\",\"insertText\":\"__stdin\"}" +
            "]}}"
        val resp = LspResponseParser.parse(body)
        assertEquals(3, resp.id)
        val items = LspResponseParser.parseCompletionList(resp.result)
        assertEquals(2, items.size)
        val printf = items[0]
        assertEquals("printf", printf.label)
        assertEquals(3, printf.kind)
        assertEquals("int printf(const char *fmt, ...)", printf.detail)
        assertEquals("printf", printf.insertText)
        val stdin = items[1]
        assertEquals("__stdin", stdin.label)
        assertEquals(6, stdin.kind)
    }

    @Test
    fun parsesBareCompletionArray() {
        // LSP servers can also return a bare array (no
        // `isIncomplete` wrapper). The parser should still find
        // the items.
        val body = "{\"jsonrpc\":\"2.0\",\"id\":4,\"result\":[" +
            "{\"label\":\"a\",\"kind\":1,\"insertText\":\"a\"}," +
            "{\"label\":\"b\",\"kind\":1,\"insertText\":\"b\"}" +
            "]}"
        val resp = LspResponseParser.parse(body)
        val items = LspResponseParser.parseCompletionList(resp.result)
        assertEquals(2, items.size)
        assertEquals("a", items[0].label)
        assertEquals("b", items[1].label)
    }

    @Test
    fun emptyResultReturnsEmptyList() {
        assertTrue(LspResponseParser.parseCompletionList(null).isEmpty())
        assertTrue(LspResponseParser.parseCompletionList("").isEmpty())
        assertTrue(LspResponseParser.parseCompletionList("null").isEmpty())
    }

    @Test
    fun nestedBracesInStringAreNotCounted() {
        // The item's `detail` may contain `{` and `}` inside a
        // JSON string; the parser must not be fooled by them.
        val body = "{\"jsonrpc\":\"2.0\",\"id\":5,\"result\":{\"items\":[" +
            "{\"label\":\"x\",\"kind\":1,\"detail\":\"struct { int a; } foo\"}" +
            "]}}"
        val resp = LspResponseParser.parse(body)
        val items = LspResponseParser.parseCompletionList(resp.result)
        assertEquals(1, items.size)
        assertEquals("x", items[0].label)
        assertEquals("struct { int a; } foo", items[0].detail)
    }

    @Test
    fun quotedBracesInStringDoNotCloseObject() {
        // Stress test: an item with `label = "}"` and a string
        // containing `"\""` and `"\\"` must not break the brace
        // matcher. The parser stores the raw value text (no
        // escape decoding — see LspResponseParser file KDoc).
        val body = "{\"jsonrpc\":\"2.0\",\"id\":6,\"result\":{\"items\":[" +
            "{\"label\":\"\\\\}\",\"kind\":1,\"insertText\":\"x\"}" +
            "]}}"
        val resp = LspResponseParser.parse(body)
        val items = LspResponseParser.parseCompletionList(resp.result)
        assertEquals(1, items.size)
        assertEquals("\\\\}", items[0].label)
    }
}
