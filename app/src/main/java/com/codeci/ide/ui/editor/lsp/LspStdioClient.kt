package com.codeci.ide.ui.editor.lsp

import com.codeci.ide.ui.utils.AppLogger
import java.io.InputStream
import java.io.OutputStream
import java.nio.charset.StandardCharsets
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

/**
 * Phase 31.5/31.6 — hand-rolled LSP stdio client.
 *
 * Why not sora's `editor-lsp` AAR? The AAR requires minSdk 26 and failed
 * `:app:processDebugMainManifest` against CodeC's AGP 9.1.1 (gate-flip
 * experiment, run `34182333111`). We own the wire instead. See
 * `docs/chat-phase31/PART_31_5_STDIO_WIRE.md`.
 *
 * **Concurrency.** Writes are serialized under [lock]. Reads live on a
 * dedicated daemon thread that frames messages with [LspWire] (byte
 * Content-Length, not chars) and parks them on [incoming]. A request
 * polls the queue with [requestTimeoutMs] so a hung server cannot
 * block the editor's completion thread past the budget (L3 / L5).
 */
class LspStdioClient(
    private val config: LspServerConfig,
    private val projectRoot: String = ".",
    private val requestTimeoutMs: Long = LspManager.DEFAULT_REQUEST_TIMEOUT_MS,
    private val processBuilderFactory: (List<String>, String) -> Process =
        { cmd, root -> defaultProcess(cmd, root) },
) : Provider {

    private val lock = Any()
    private val nextId = AtomicInteger(1)
    private val process = AtomicReference<Process?>(null)
    private val incoming = LinkedBlockingQueue<LspResponse>()
    private var output: OutputStream? = null
    private var currentFile: String? = null
    @Volatile private var readerAlive = false

    override fun start() {
        val initId: Int
        synchronized(lock) {
            check(process.get() == null) { "already started" }
            val p = processBuilderFactory(config.command, projectRoot)
            process.set(p)
            output = p.outputStream
            readerAlive = true
            Thread({ drainStderr(p) }, "lsp-stderr-${config.displayName}").apply {
                isDaemon = true
                start()
            }
            Thread({ readLoop(p.inputStream) }, "lsp-read-${config.displayName}").apply {
                isDaemon = true
                start()
            }
            initId = nextId.getAndIncrement()
            sendLocked(initializePayload(initId))
        }
        val init = pollResponse(initId, BOOT_TIMEOUT_MS)
            ?: throw TimeoutException(
                "initialize timed out after " + BOOT_TIMEOUT_MS + "ms"
            )
        if (init.error != null) {
            throw IllegalStateException("initialize returned error: " + init.error)
        }
        synchronized(lock) {
            sendNotificationLocked("initialized", "{}")
        }
    }

    override fun setBuffer(fileName: String, content: String) = synchronized(lock) {
        applyBufferLocked(fileName, content)
    }

    override fun request(context: LspRequestContext): List<LspItemMapping.LspShape> {
        val id: Int
        synchronized(lock) {
            if (output == null) return emptyList()
            applyBufferLocked(context.fileName, context.content)
            id = nextId.getAndIncrement()
            val payload = "{\"jsonrpc\":\"2.0\",\"id\":" + id +
                ",\"method\":\"textDocument/completion\"," +
                "\"params\":{\"textDocument\":{\"uri\":\"" +
                LspUris.fileUri(context.fileName) + "\"}," +
                "\"position\":{\"line\":" + context.line +
                ",\"character\":" + context.column + "}}}"
            sendLocked(payload)
        }
        val resp = pollResponse(id, requestTimeoutMs)
            ?: throw TimeoutException(
                config.displayName + " completion timed out after " + requestTimeoutMs + "ms"
            )
        if (resp.error != null) {
            AppLogger.w(TAG, config.displayName + " completion error: " + resp.error)
            return emptyList()
        }
        return LspResponseParser.parseCompletionList(resp.result)
    }

    override fun shutdown() = synchronized(lock) {
        readerAlive = false
        val p = process.getAndSet(null) ?: return
        try {
            if (output != null) {
                sendNotificationLocked("shutdown", "null")
                sendNotificationLocked("exit", "null")
            }
        } catch (_: Throwable) {
        }
        try { output?.close() } catch (_: Throwable) {}
        output = null
        currentFile = null
        incoming.clear()
        // minSdk 24: Process.waitFor(timeout) / isAlive / destroyForcibly
        // are API 26. Same pattern as ExecutionRunner (poll exitValue,
        // destroy(), reflective destroyForcibly). Lint 34209358314.
        destroyProcessApi24(p)
    }

    private fun processStillRunning(p: Process): Boolean = try {
        p.exitValue()
        false
    } catch (_: IllegalThreadStateException) {
        true
    }

    private fun destroyProcessApi24(p: Process) {
        try { p.destroy() } catch (_: Throwable) {}
        val deadline = System.nanoTime() + 2_000_000_000L
        while (processStillRunning(p) && System.nanoTime() < deadline) {
            try {
                Thread.sleep(50)
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
                break
            }
        }
        if (processStillRunning(p)) {
            try {
                Process::class.java.getMethod("destroyForcibly").invoke(p)
            } catch (_: Throwable) {
            }
        }
    }

    // ---------- internals ----------

    private fun applyBufferLocked(fileName: String, content: String) {
        val w = output ?: return
        if (fileName.isBlank()) return
        if (currentFile == fileName) {
            val version = nextId.getAndIncrement()
            val params = "{\"textDocument\":{\"uri\":\"" + LspUris.fileUri(fileName) +
                "\",\"version\":" + version + "}," +
                "\"contentChanges\":[{\"text\":" + jsonString(content) + "}]}"
            sendNotificationLocked("textDocument/didChange", params)
        } else {
            val old = currentFile
            currentFile = fileName
            if (old != null) {
                sendNotificationLocked(
                    "textDocument/didClose",
                    "{\"textDocument\":{\"uri\":\"" + LspUris.fileUri(old) + "\"}}",
                )
            }
            val openParams = "{\"textDocument\":{\"uri\":\"" + LspUris.fileUri(fileName) +
                "\",\"languageId\":\"" + config.languageId + "\",\"version\":1," +
                "\"text\":" + jsonString(content) + "}}"
            sendNotificationLocked("textDocument/didOpen", openParams)
        }
        w.flush()
    }

    private fun sendLocked(json: String) {
        val w = output ?: return
        w.write(LspWire.frame(json))
        w.flush()
    }

    private fun sendNotificationLocked(method: String, paramsJson: String) {
        sendLocked(
            "{\"jsonrpc\":\"2.0\",\"method\":\"" + method + "\",\"params\":" + paramsJson + "}"
        )
    }

    private fun pollResponse(id: Int, timeoutMs: Long): LspResponse? {
        val deadline = System.nanoTime() + timeoutMs * 1_000_000L
        while (true) {
            val remaining = (deadline - System.nanoTime()) / 1_000_000L
            if (remaining <= 0) return null
            val msg = try {
                incoming.poll(remaining, TimeUnit.MILLISECONDS)
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
                return null
            } ?: return null
            if (msg.id == id) return msg
            // Notification or a different id — keep waiting for ours.
        }
    }

    private fun readLoop(input: InputStream) {
        try {
            while (readerAlive) {
                val msg = LspWire.readMessage(input) ?: break
                incoming.offer(msg)
            }
        } catch (_: Throwable) {
        }
    }

    private fun drainStderr(p: Process) {
        try {
            p.errorStream.bufferedReader(StandardCharsets.UTF_8).use { err ->
                while (err.readLine() != null) {
                    // Discard — a future "LSP log" feature would tee this.
                }
            }
        } catch (_: Throwable) {
        }
    }

    private fun initializePayload(id: Int): String {
        val initParams = buildString {
            append("{")
            append("\"processId\":null")
            append(",\"rootUri\":\"" + LspUris.fileUri(projectRoot) + "\"")
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
        return "{\"jsonrpc\":\"2.0\",\"id\":" + id +
            ",\"method\":\"initialize\",\"params\":" + initParams + "}"
    }

    /**
     * JSON-encode a string with the minimum required escapes
     * (backslash + double-quote + control chars).
     */
    internal fun jsonString(s: String): String {
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
        private const val BOOT_TIMEOUT_MS = 5_000L

        /**
         * Default spawn — working directory = [projectRoot]. The production
         * factory ([StdioLspProviderFactory]) injects PREFIX/PATH/
         * LD_LIBRARY_PATH/LD_PRELOAD so shebang scripts under `$PREFIX/bin`
         * actually exec. Tests inject a fake Process.
         */
        @JvmStatic
        fun defaultProcess(command: List<String>, projectRoot: String): Process =
            ProcessBuilder(command).directory(java.io.File(projectRoot)).start()
    }
}
