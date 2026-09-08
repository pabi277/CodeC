package com.codeci.ide.ui.editor.lsp

import com.codeci.ide.ui.utils.AppLogger
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.nio.charset.StandardCharsets
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

/**
 * Phase 31.5 — a hand-rolled LSP stdio client.
 *
 * Why not sora's `editor-lsp` AAR? The AAR requires `minSdk 26`
 * (sora's BOM is AGP 9.3.1 + Kotlin 2.4.10, and lsp4j 1.0.0 uses
 * `java.nio.file.Files` APIs introduced in API 26). CodeC's
 * `minSdk = 24` to keep the test device (linux-android24) working.
 * A `tools:overrideLibrary` workaround is risky (the AAR itself
 * warns: "may lead to runtime failures") and the AGP bump is
 * out of scope for this chat. So: ~280 LOC of stdio JSON-RPC and
 * we own the wire. See `docs/chat-phase31/PART_31_5_STDIO_WIRE.md`
 * (the follow-up doc to PART_31_1 §3.1) for the design rationale.
 *
 * **What it does (today):**
 *  - `start()`: spawn the LSP `Process` from `LspServerConfig.command`,
 *    send `initialize`, read the response, send `initialized`.
 *  - `setBuffer(fileName, content)`: tell the server about the file —
 *    `didClose` the old file (if any), then `didOpen` the new one.
 *  - `request(context)`: send `textDocument/completion` at the cursor,
 *    read the response, map LSP `CompletionItem[]` →
 *    `List<LspItemMapping.LspShape>`.
 *  - `shutdown()`: send `shutdown` + `exit`, destroy the process.
 *
 * **Concurrency:** all stdio I/O is serialized under [lock]. A
 * single caller at a time (the orchestrator already serializes via
 * the `timed` wrapper, see [LspManager.completions]). The
 * `requestTimeoutMs` (default 200, L3) bounds the read; if no
 * response arrives in time, the process is blacklisted by the
 * manager.
 *
 * **LSP frame format (from the spec):**
 * ```
 * Content-Length: <bytes>\r\n
 * \r\n
 * <body>           // exactly <bytes> bytes of UTF-8 JSON
 * ```
 * Other headers (Content-Type, etc.) are ignored. We send
 * `Content-Length` only; most servers accept that.
 *
 * **Host-testable:** no Android, no sora, no lsp4j. The framing
 * tests in `LspStdioClientFramingTest` exercise the wire format
 * directly (no real `Process`); the parser tests in
 * `LspResponseParserTest` exercise the JSON extraction. A full
 * round-trip test against a fake LSP server is a 31.6 follow-up
 * — the real value comes from running against actual clangd /
 * pylsp / tsserver on a device.
 */
class LspStdioClient(
    private val config: LspServerConfig,
    private val projectRoot: String = ".",
    private val requestTimeoutMs: Long = LspManager.DEFAULT_REQUEST_TIMEOUT_MS,
    private val processBuilderFactory: (List<String>, String) -> Process = { cmd, root -> defaultProcess(cmd, root) },
) : Provider {

    private val lock = Any()
    private val nextId = AtomicInteger(1)
    private val process = AtomicReference<Process?>(null)
    private var reader: BufferedReader? = null
    private var writer: BufferedWriter? = null
    private var currentFile: String? = null

    /**
     * Spawn the LSP process and complete the `initialize` handshake.
     * The contract is: when this returns, the server is ready to
     * accept `textDocument/didOpen` etc. notifications. The orchestrator
     * catches a throw here and blacklists the language (L3) — the
     * manager never sees this exception because `LspManager.startProvider`
     * wraps `factory.create(...).start()` in its own try/catch.
     */
    override fun start() = synchronized(lock) {
        check(process.get() == null) { "already started" }
        val p = processBuilderFactory(config.command, projectRoot)
        process.set(p)
        val r = BufferedReader(InputStreamReader(p.inputStream, StandardCharsets.UTF_8))
        val w = BufferedWriter(OutputStreamWriter(p.outputStream, StandardCharsets.UTF_8))
        reader = r
        writer = w
        // Drain stderr on a daemon thread — we don't read it for the
        // protocol, but a server that floods stderr can fill the
        // pipe and block the process otherwise.
        Thread({ drainStderr(p) }, "lsp-stderr-${config.displayName}").apply {
            isDaemon = true
            start()
        }
        // 1. initialize → response
        val initId = nextId.getAndIncrement()
        val initParams = buildString {
            append("{")
            append("\"processId\":null")
            append(",\"rootUri\":\"file://" + projectRoot + "\"")
            append(",\"capabilities\":{")
            append("\"textDocument\":{")
            append("\"completion\":{")
            append("\"completionItem\":{")
            append("\"snippetSupport\":false")
            append("}")
            append("}")
            append("}")
            append("}")
            append("}")
        }
        send(
            w,
            "{\"jsonrpc\":\"2.0\",\"id\":" + initId + ",\"method\":\"initialize\",\"params\":" + initParams + "}",
        )
        val init = readResponse(r, initId, BOOT_TIMEOUT_MS)
            ?: error("initialize timed out after " + BOOT_TIMEOUT_MS + "ms")
        if (init.error != null) error("initialize returned error: " + init.error)
        // 2. initialized (no response expected)
        sendNotification(w, "initialized", "{}")
    }

    override fun setBuffer(fileName: String, content: String) = synchronized(lock) {
        val w = writer ?: return
        if (currentFile == fileName) {
            // Same file — didChange. Most LSP servers are happy with a
            // full-content change; the alternative (incremental
            // textDocument/didChange with a `range`) is more code
            // for marginal wins on a phone.
            val version = nextId.getAndIncrement()
            val params = "{\"textDocument\":{\"uri\":\"" + fileUri(fileName) +
                "\",\"version\":" + version + "}," +
                "\"contentChanges\":[{\"text\":" + jsonString(content) + "}]}"
            sendNotification(w, "textDocument/didChange", params)
        } else {
            val old = currentFile
            currentFile = fileName
            if (old != null) {
                val closeParams = "{\"textDocument\":{\"uri\":\"" + fileUri(old) + "\"}}"
                sendNotification(w, "textDocument/didClose", closeParams)
            }
            val openParams = "{\"textDocument\":{\"uri\":\"" + fileUri(fileName) +
                "\",\"languageId\":\"" + config.languageId + "\",\"version\":1," +
                "\"text\":" + jsonString(content) + "}}"
            sendNotification(w, "textDocument/didOpen", openParams)
        }
    }

    /**
     * The `request` overload the orchestrator calls. The manager
     * passes an `LspRequestContext` (file + prefix + cursor + content);
     * this method does the `setBuffer` + `completion` round-trip and
     * returns the LSP-shaped items. Returns an empty list on timeout
     * or any read/write failure — the manager's L3 path will
     * blacklist the language on the next signal.
     */
    override fun request(context: LspRequestContext): List<LspItemMapping.LspShape> = synchronized(lock) {
        val w = writer ?: return emptyList()
        val r = reader ?: return emptyList()
        try {
            setBuffer(context.fileName, context.content)
        } catch (t: Throwable) {
            AppLogger.w(TAG, config.displayName + " setBuffer failed: " + t.javaClass.simpleName + ": " + t.message)
            return emptyList()
        }
        val id = nextId.getAndIncrement()
        val payload = "{\"jsonrpc\":\"2.0\",\"id\":" + id +
            ",\"method\":\"textDocument/completion\"," +
            "\"params\":{\"textDocument\":{\"uri\":\"" + fileUri(context.fileName) + "\"}," +
            "\"position\":{\"line\":" + context.line + ",\"character\":" + context.column + "}}}"
        try {
            send(w, payload)
        } catch (t: Throwable) {
            AppLogger.w(TAG, config.displayName + " send failed: " + t.javaClass.simpleName + ": " + t.message)
            return emptyList()
        }
        val resp = readResponse(r, id, requestTimeoutMs) ?: return emptyList()
        if (resp.error != null) {
            AppLogger.w(TAG, config.displayName + " completion error: " + resp.error)
            return emptyList()
        }
        LspResponseParser.parseCompletionList(resp.result)
    }

    override fun shutdown() = synchronized(lock) {
        val p = process.getAndSet(null) ?: return
        val w = writer
        try {
            if (w != null) {
                sendNotification(w, "shutdown", "null")
                sendNotification(w, "exit", "null")
                w.flush()
            }
        } catch (_: Throwable) {
            // Best effort. The process is going down either way.
        }
        try {
            p.waitFor(1, java.util.concurrent.TimeUnit.SECONDS)
        } catch (_: Throwable) {
        }
        if (p.isAlive) p.destroyForcibly()
        try {
            p.waitFor(2, java.util.concurrent.TimeUnit.SECONDS)
        } catch (_: Throwable) {
        }
        reader = null
        writer = null
        currentFile = null
    }

    // ---------- internals ----------

    private fun send(w: BufferedWriter, json: String) {
        w.write("Content-Length: " + json.toByteArray(StandardCharsets.UTF_8).size + "\r\n")
        w.write("\r\n")
        w.write(json)
        w.flush()
    }

    private fun sendNotification(w: BufferedWriter, method: String, paramsJson: String) {
        send(w, "{\"jsonrpc\":\"2.0\",\"method\":\"" + method + "\",\"params\":" + paramsJson + "}")
    }

    /**
     * Reads framed LSP messages until one with the matching `id` arrives,
     * or until the timeout elapses. Notifications (no `id`) are silently
     * dropped; diagnostics streaming is handled by the production
     * 31.6 follow-up (this client doesn't surface `PublishDiagnostics`
     * yet — see the file KDoc).
     */
    private fun readResponse(r: BufferedReader, id: Int, timeoutMs: Long): LspResponse? {
        val deadline = System.nanoTime() + timeoutMs * 1_000_000L
        while (System.nanoTime() < deadline) {
            val remaining = (deadline - System.nanoTime()) / 1_000_000L
            if (remaining <= 0) return null
            val msg = readOneMessage(r, remaining) ?: return null
            if (msg.id == id) return msg
            // A notification or a different request's response —
            // loop and keep reading until ours arrives or the
            // deadline elapses.
        }
        return null
    }

    /**
     * Reads one framed LSP message (Content-Length + body). The
     * timeout bounds the read of the headers + body, not the
     * whole request/response cycle. Returns null on EOF or timeout.
     */
    private fun readOneMessage(r: BufferedReader, timeoutMs: Long): LspResponse? {
        val deadline = System.nanoTime() + timeoutMs * 1_000_000L
        // 1. Read headers until blank line.
        var contentLength = -1
        while (true) {
            if (System.nanoTime() >= deadline) return null
            val line = r.readLine() ?: return null  // EOF
            if (line.isEmpty()) break
            if (line.startsWith("Content-Length:", ignoreCase = true)) {
                contentLength = line.substringAfter(":").trim().toIntOrNull() ?: -1
            }
            // Other headers (Content-Type, etc.) are ignored.
        }
        if (contentLength <= 0) return null
        // 2. Read `contentLength` bytes of body.
        val buf = CharArray(contentLength)
        var read = 0
        while (read < contentLength) {
            if (System.nanoTime() >= deadline) return null
            val n = r.read(buf, read, contentLength - read)
            if (n <= 0) return null  // EOF
            read += n
        }
        val body = String(buf, 0, read)
        return LspResponseParser.parse(body)
    }

    private fun drainStderr(p: Process) {
        try {
            BufferedReader(InputStreamReader(p.errorStream, StandardCharsets.UTF_8)).use { err ->
                while (err.readLine() != null) {
                    // Discard — LSP servers may log to stderr; we
                    // surface nothing here. A future "LSP log" feature
                    // would tee this into AppLogger.
                }
            }
        } catch (_: Throwable) {
            // Process is gone.
        }
    }

    private fun fileUri(fileName: String): String = "file://" + fileName

    /**
     * JSON-encode a string with the minimum required escapes
     * (backslash + double-quote + control chars). Good enough
     * for `textDocument/didOpen`'s `text` field and the
     * `contentChanges[0].text` field; not a full JSON encoder.
     */
    private fun jsonString(s: String): String {
        val sb = StringBuilder(s.length + 2)
        sb.append('"')
        for (c in s) {
            when (c) {
                '\\' -> sb.append("\\\\")
                '"' -> sb.append("\\\"")
                '\n' -> sb.append("\\n")
                '\r' -> sb.append("\\r")
                '\t' -> sb.append("\\t")
                '\b' -> sb.append("\\b")
                else -> if (c.code < 0x20) {
                    sb.append("\\u%04x".format(c.code))
                } else {
                    sb.append(c)
                }
            }
        }
        sb.append('"')
        return sb.toString()
    }

    companion object {
        private const val TAG = "LspStdioClient"
        // initialize can be slow (the server is reading its own
        // grammars / config). 5 s is generous; the L3 path catches
        // anything beyond.
        private const val BOOT_TIMEOUT_MS = 5_000L

        /**
         * The default [processBuilderFactory] — `ProcessBuilder` with
         * the project root as the working directory. Kept as a
         * function (not a constructor) so the host tests can inject
         * a fake that runs a Kotlin coroutine instead of a real
         * `Process`.
         */
        @JvmStatic
        fun defaultProcess(command: List<String>, projectRoot: String): Process =
            ProcessBuilder(command).directory(java.io.File(projectRoot)).start()
    }
}
