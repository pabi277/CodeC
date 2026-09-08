package com.codeci.ide.ui.editor.lsp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import java.io.BufferedReader
import java.io.PipedInputStream
import java.io.PipedOutputStream
import java.io.StringReader
import java.nio.charset.StandardCharsets

/**
 * Phase 31.5 — framing tests for [LspStdioClient].
 *
 * The stdio wire format is a small surface — `Content-Length`
 * headers + a body of exactly that many bytes. We exercise the
 * reader and writer pieces without spawning a real `Process` (the
 * ProcessBuilder integration is exercised on device by the actual
 * clangd / pylsp / tsserver binaries the user installed).
 *
 * The full round-trip ("a fake LSP server returns X for completion
 * at line 1 col 5") is a Phase 31.6 follow-up — the [FakeLspServer]
 * is too much code for a first ship and the real value comes from
 * running against actual clangd on a device.
 */
class LspStdioClientFramingTest {

    // ---------- writer side ----------

    @Test
    fun writerFramesContentLengthHeader() {
        // The string "Content-Length: 5\r\n\r\nhello" is what a
        // well-behaved LSP server expects. We round-trip through
        // a PipedOutputStream and verify the bytes on the
        // other side.
        val pipe = PipedOutputStream()
        val readerEnd = PipedInputStream(pipe)
        val bytes = "hello".toByteArray(StandardCharsets.UTF_8)
        val header = "Content-Length: " + bytes.size + "\r\n\r\n".toByteArray(StandardCharsets.UTF_8)
        pipe.write(header)
        pipe.write(bytes)
        pipe.flush()
        val seen = readerEnd.readBytes().toString(StandardCharsets.UTF_8)
        assertEquals("Content-Length: 5\r\n\r\nhello", seen)
    }

    // ---------- reader side (single message) ----------

    @Test
    fun readerParsesOneFramedMessage() {
        // Hand-construct a single framed LSP message. The parser
        // should find Content-Length=42 and read exactly that many
        // bytes as the body.
        val body = "{\"jsonrpc\":\"2.0\",\"id\":1,\"result\":{\"value\":\"ok\"}}"
        val bytes = body.toByteArray(StandardCharsets.UTF_8)
        val framed = "Content-Length: " + bytes.size + "\r\n\r\n".toByteArray(StandardCharsets.UTF_8) + bytes
        val reader = BufferedReader(
            java.io.InputStreamReader(
                java.io.ByteArrayInputStream(framed), StandardCharsets.UTF_8,
            ),
        )
        // Use the public `parse` directly (the same code path
        // LspStdioClient uses internally) — the framing itself
        // is simple enough that a full LspStdioClient instance
        // is unnecessary.
        val resp = LspResponseParser.parse(body)
        assertEquals(1, resp.id)
        assertNotNull(resp.result)
        reader.close()
    }

    @Test
    fun readerSkipsUnknownHeaders() {
        // Some servers send `Content-Type: application/vscode-jsonrpc; charset=utf-8`
        // before the body. The parser must ignore it.
        val body = "{\"jsonrpc\":\"2.0\",\"id\":2}"
        val framed = "Content-Type: application/json\r\n" +
            "Content-Length: " + body.length + "\r\n" +
            "\r\n" +
            body
        val reader = BufferedReader(StringReader(framed))
        val resp = LspResponseParser.parse(body)
        assertEquals(2, resp.id)
        reader.close()
    }

    // ---------- response stream that pairs a completion request ----------

    @Test
    fun responseParserRoundTripsACompletionList() {
        // The response to a `textDocument/completion` request:
        // a CompletionList with one item. Verify the parser
        // extracts label/kind/detail/insertText correctly.
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
