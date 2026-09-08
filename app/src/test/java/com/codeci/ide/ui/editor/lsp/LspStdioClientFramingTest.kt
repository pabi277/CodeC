package com.codeci.ide.ui.editor.lsp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import java.io.ByteArrayInputStream
import java.nio.charset.StandardCharsets

/**
 * Phase 31.5 — framing tests. Byte arrays only: a [java.io.PipedInputStream]
 * without a closed writer makes [readBytes] wait for EOF forever (CI runs
 * 34185097875 / 34188032592 / 34189756351 hung 3+ hours on that).
 */
class LspStdioClientFramingTest {

    @Test
    fun writerFramesContentLengthHeader() {
        val seen = LspWire.frame("hello").toString(StandardCharsets.UTF_8)
        assertEquals("Content-Length: 5\r\n\r\nhello", seen)
    }

    @Test
    fun readerParsesOneFramedMessage() {
        val body = "{\"jsonrpc\":\"2.0\",\"id\":1,\"result\":{\"value\":\"ok\"}}"
        val framed = LspWire.frame(body)
        val resp = LspWire.readMessage(ByteArrayInputStream(framed))!!
        assertEquals(1, resp.id)
        assertNotNull(resp.result)
    }

    @Test
    fun readerSkipsUnknownHeaders() {
        val body = "{\"jsonrpc\":\"2.0\",\"id\":2}"
        val framed = ("Content-Type: application/json\r\n" +
            "Content-Length: " + body.toByteArray(StandardCharsets.UTF_8).size + "\r\n" +
            "\r\n" +
            body).toByteArray(StandardCharsets.UTF_8)
        val resp = LspWire.readMessage(ByteArrayInputStream(framed))!!
        assertEquals(2, resp.id)
    }

    @Test
    fun responseParserRoundTripsACompletionList() {
        val body = "{\"jsonrpc\":\"2.0\",\"id\":7,\"result\":{\"isIncomplete\":false,\"items\":[" +
            "{\"label\":\"fread\",\"kind\":3,\"detail\":\"size_t fread(...)\",\"insertText\":\"fread\"}" +
            "]}}"
        val resp = LspResponseParser.parse(body)
        val items = LspResponseParser.parseCompletionList(resp.result)
        assertEquals(1, items.size)
        assertEquals("fread", items[0].label)
        assertEquals(3, items[0].kind)
    }
}
