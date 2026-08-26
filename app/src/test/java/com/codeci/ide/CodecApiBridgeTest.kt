package com.codeci.ide

import com.codeci.ide.ui.terminal.ClipboardContent
import com.codeci.ide.ui.terminal.CodecApiBridge
import com.codeci.ide.ui.terminal.CodecApiProtocol
import com.codeci.ide.ui.terminal.NotifyOps
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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

    private fun recordingNotify(): Pair<NotifyOps, MutableList<String>> {
        val sent = mutableListOf<String>()
        val cleared = intArrayOf(0)
        val ops = NotifyOps(
            send = { title, body -> sent.add("$title\n$body") },
            clear = { cleared[0]++ },
            status = { "notify: fake status" }
        )
        return ops to sent
    }

    @Test
    fun `notify send splits first line as title and rest as body`() {
        val base = tempDir()
        try {
            val reqFile = File(base, "req.notify")
            reqFile.writeText("Build finished\n3 files compiled")
            val res = File(base, "res.notify")
            val request = CodecApiProtocol.Request(
                CodecApiProtocol.Op.NOTIFY_SEND, reqFile.absolutePath, res.absolutePath
            )
            val (ops, sent) = recordingNotify()
            val response = CodecApiBridge.execute(
                request, base, { error("must not read clipboard") }, {}, ops
            )
            assertEquals("OK", response)
            assertEquals(listOf("Build finished\n3 files compiled"), sent.toList())
        } finally {
            base.deleteRecursively()
        }
    }

    @Test
    fun `notify send without a title is an error`() {
        val base = tempDir()
        try {
            val reqFile = File(base, "req.notify")
            reqFile.writeText("\nbody only")
            val res = File(base, "res.notify")
            val request = CodecApiProtocol.Request(
                CodecApiProtocol.Op.NOTIFY_SEND, reqFile.absolutePath, res.absolutePath
            )
            var sent = false
            val ops = NotifyOps({ _, _ -> sent = true }, {}, { "x" })
            val response = CodecApiBridge.execute(
                request, base, { error("must not read clipboard") }, {}, ops
            )
            assertTrue(response.startsWith("ERR:"))
            assertTrue(response.contains("title is empty"))
            assertFalse(sent)
        } finally {
            base.deleteRecursively()
        }
    }

    @Test
    fun `notify send without request file and oversized payload are errors`() {
        val base = tempDir()
        try {
            val (missingReq, _) = request(CodecApiProtocol.Op.NOTIFY_SEND, base)
            val response = CodecApiBridge.execute(
                missingReq, base, { error("must not read") }, {}, NotifyOps({ _, _ -> }, {}, { "x" })
            )
            assertTrue(response.startsWith("ERR:"))

            val big = File(base, "req.big-notify")
            big.writeBytes(ByteArray(CodecApiProtocol.MAX_NOTIFY_BYTES + 1))
            val res = File(base, "res.big-notify")
            val request = CodecApiProtocol.Request(
                CodecApiProtocol.Op.NOTIFY_SEND, big.absolutePath, res.absolutePath
            )
            var sent = false
            val response2 = CodecApiBridge.execute(
                request, base, { error("must not read") }, {}, NotifyOps({ _, _ -> sent = true }, {}, { "x" })
            )
            assertTrue(response2.startsWith("ERR:"))
            assertTrue(response2.contains("too large"))
            assertFalse(sent)
        } finally {
            base.deleteRecursively()
        }
    }

    @Test
    fun `notify clear and status route through the ops adapter`() {
        val base = tempDir()
        try {
            val (clearReq, _) = request(CodecApiProtocol.Op.NOTIFY_CLEAR, base)
            var cleared = 0
            val clearResponse = CodecApiBridge.execute(
                clearReq, base, { error("must not read") }, {},
                NotifyOps({ _, _ -> }, { cleared++ }, { "notify: fake status" })
            )
            assertEquals("OK", clearResponse)
            assertEquals(1, cleared)

            val (statusReq, _) = request(CodecApiProtocol.Op.NOTIFY_STATUS, base)
            val statusResponse = CodecApiBridge.execute(
                statusReq, base, { error("must not read") }, {},
                NotifyOps({ _, _ -> }, {}, { "notify: fake status" })
            )
            assertEquals("notify: fake status", statusResponse)
        } finally {
            base.deleteRecursively()
        }
    }

    @Test
    fun `notify ops without an adapter report an unavailable service`() {
        val base = tempDir()
        try {
            val (sendReq, _) = request(CodecApiProtocol.Op.NOTIFY_SEND, base, withRequestFile = true)
            val sendResponse = CodecApiBridge.execute(
                sendReq, base, { error("must not read") }, {}, null
            )
            assertTrue(sendResponse.startsWith("ERR:"))
            assertTrue(sendResponse.contains("notification service unavailable"))

            val (statusReq, _) = request(CodecApiProtocol.Op.NOTIFY_STATUS, base)
            val statusResponse = CodecApiBridge.execute(
                statusReq, base, { error("must not read") }, {}, null
            )
            assertTrue(statusResponse.startsWith("ERR:"))
        } finally {
            base.deleteRecursively()
        }
    }

    @Test
    fun `resume after granted runs the send, after denial it errors`() {
        val base = tempDir()
        try {
            val reqFile = File(base, "req.resume")
            reqFile.writeText("Granted afterwards")
            val res = File(base, "res.resume")
            val request = CodecApiProtocol.Request(
                CodecApiProtocol.Op.NOTIFY_SEND, reqFile.absolutePath, res.absolutePath
            )
            val (ops, sent) = recordingNotify()

            val granted = CodecApiBridge.resumeResponse(request, base, granted = true, notify = ops)
            assertEquals("OK", granted)
            assertEquals(listOf("Granted afterwards\n"), sent)

            val denied = CodecApiBridge.resumeResponse(request, base, granted = false, notify = ops)
            assertTrue(denied.startsWith("ERR:"))
            assertTrue(denied.contains("permission denied"))
            // Denial must never fall through to posting a notification.
            assertEquals(1, sent.size)
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
