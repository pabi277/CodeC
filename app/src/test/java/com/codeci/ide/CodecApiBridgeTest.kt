package com.codeci.ide

import com.codeci.ide.ui.terminal.ClipboardContent
import com.codeci.ide.ui.terminal.CodecApiBridge
import com.codeci.ide.ui.terminal.CodecApiProtocol
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Host tests for the Phase 4.7 bridge's pure core: capability dispatch,
 * confinement enforcement and the response conventions.
 */
class CodecApiBridgeTest {

    private fun tempDir(): File =
        File(System.getProperty("java.io.tmpdir"), "codec-api-bridge-${System.nanoTime()}").apply {
            deleteRecursively()
            mkdirs()
        }

    private fun request(
        op: CodecApiProtocol.Op,
        apiDir: File,
        withRequestFile: Boolean = false
    ): Pair<CodecApiProtocol.Request, File> {
        val req = if (withRequestFile) {
            File(apiDir, "req.${System.nanoTime()}").apply { writeText("hello clipboard") }
        } else {
            File(apiDir, "req.none")
        }
        val res = File(apiDir, "res.${System.nanoTime()}")
        return CodecApiProtocol.Request(op, if (withRequestFile) req.absolutePath else null, res.absolutePath) to res
    }

    @Test
    fun `get returns raw clipboard text`() {
        val base = tempDir()
        try {
            val (req, res) = request(CodecApiProtocol.Op.CLIPBOARD_GET, base)
            val response = CodecApiBridge.execute(
                req, base,
                readClipboard = { ClipboardContent.Text("line1\nline2") },
                writeClipboard = { error("must not write") }
            )
            assertEquals("line1\nline2", response)
        } finally {
            base.deleteRecursively()
        }
    }

    @Test
    fun `get returns empty body for an empty clipboard`() {
        val base = tempDir()
        try {
            val (req, _) = request(CodecApiProtocol.Op.CLIPBOARD_GET, base)
            val response = CodecApiBridge.execute(
                req, base,
                readClipboard = { ClipboardContent.Empty },
                writeClipboard = { error("must not write") }
            )
            assertEquals("", response)
        } finally {
            base.deleteRecursively()
        }
    }

    @Test
    fun `get reports non-text clipboard as an error`() {
        val base = tempDir()
        try {
            val (req, _) = request(CodecApiProtocol.Op.CLIPBOARD_GET, base)
            val response = CodecApiBridge.execute(
                req, base,
                readClipboard = { ClipboardContent.NonText },
                writeClipboard = { error("must not write") }
            )
            assertTrue(response.startsWith("ERR:"))
        } finally {
            base.deleteRecursively()
        }
    }

    @Test
    fun `set writes the request file content and answers OK`() {
        val base = tempDir()
        try {
            val (req, _) = request(CodecApiProtocol.Op.CLIPBOARD_SET, base, withRequestFile = true)
            var written: String? = null
            val response = CodecApiBridge.execute(
                req, base,
                readClipboard = { error("must not read") },
                writeClipboard = { written = it }
            )
            assertEquals("OK", response)
            assertEquals("hello clipboard", written)
        } finally {
            base.deleteRecursively()
        }
    }

    @Test
    fun `set without a request file is an error`() {
        val base = tempDir()
        try {
            val (req, _) = request(CodecApiProtocol.Op.CLIPBOARD_SET, base, withRequestFile = false)
            val response = CodecApiBridge.execute(
                req, base,
                readClipboard = { error("must not read") },
                writeClipboard = { error("must not write") }
            )
            assertTrue(response.startsWith("ERR:"))
            assertTrue(response.contains("missing request file"))
        } finally {
            base.deleteRecursively()
        }
    }

    @Test
    fun `set rejects oversized content`() {
        val base = tempDir()
        try {
            val req = File(base, "req.big")
            req.writeBytes(ByteArray(CodecApiProtocol.MAX_SET_BYTES + 1))
            val res = File(base, "res.big")
            val request = CodecApiProtocol.Request(CodecApiProtocol.Op.CLIPBOARD_SET, req.absolutePath, res.absolutePath)
            var wrote = false
            val response = CodecApiBridge.execute(
                request, base,
                readClipboard = { error("must not read") },
                writeClipboard = { wrote = true }
            )
            assertTrue(response.startsWith("ERR:"))
            assertTrue(response.contains("too large"))
            assertTrue(!wrote)
        } finally {
            base.deleteRecursively()
        }
    }

    @Test
    fun `clear empties the clipboard through writeClipboard`() {
        val base = tempDir()
        try {
            val (req, _) = request(CodecApiProtocol.Op.CLIPBOARD_CLEAR, base)
            var written: String? = "sentinel"
            val response = CodecApiBridge.execute(
                req, base,
                readClipboard = { error("must not read") },
                writeClipboard = { written = it }
            )
            assertEquals("OK", response)
            assertEquals("", written)
        } finally {
            base.deleteRecursively()
        }
    }

    @Test
    fun `status reports kind and length`() {
        val base = tempDir()
        try {
            val (reqText, _) = request(CodecApiProtocol.Op.CLIPBOARD_STATUS, base)
            val textResponse = CodecApiBridge.execute(
                reqText, base,
                readClipboard = { ClipboardContent.Text("abcd") },
                writeClipboard = { error("must not write") }
            )
            assertEquals("clipboard: text\nlength: 4", textResponse)

            val (reqEmpty, _) = request(CodecApiProtocol.Op.CLIPBOARD_STATUS, base)
            val emptyResponse = CodecApiBridge.execute(
                reqEmpty, base,
                readClipboard = { ClipboardContent.Empty },
                writeClipboard = { error("must not write") }
            )
            assertEquals("clipboard: empty\nlength: 0", emptyResponse)
        } finally {
            base.deleteRecursively()
        }
    }

    @Test
    fun `unconfined response path is rejected without touching clipboard`() {
        val base = tempDir()
        try {
            val apiDir = File(base, "codec-api").apply { mkdirs() }
            val evilRes = File(base, "evil-res")
            val request = CodecApiProtocol.Request(
                CodecApiProtocol.Op.CLIPBOARD_GET,
                null,
                evilRes.absolutePath
            )
            var wrote = false
            val response = CodecApiBridge.execute(
                request, apiDir,
                readClipboard = { error("must not read") },
                writeClipboard = { wrote = true }
            )
            assertTrue(response.startsWith("ERR:"))
            assertTrue(!wrote)
        } finally {
            base.deleteRecursively()
        }
    }

    @Test
    fun `unconfined request path is rejected without touching clipboard`() {
        val base = tempDir()
        try {
            val apiDir = File(base, "codec-api").apply { mkdirs() }
            val evilReq = File(base, "evil-req").apply { writeText("x") }
            val res = File(apiDir, "res.1")
            val request = CodecApiProtocol.Request(
                CodecApiProtocol.Op.CLIPBOARD_SET,
                evilReq.absolutePath,
                res.absolutePath
            )
            var wrote = false
            val response = CodecApiBridge.execute(
                request, apiDir,
                readClipboard = { error("must not read") },
                writeClipboard = { wrote = true }
            )
            assertTrue(response.startsWith("ERR:"))
            assertTrue(!wrote)
        } finally {
            base.deleteRecursively()
        }
    }
}
