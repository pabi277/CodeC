package com.codeci.ide.ui.services

import java.io.File
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

/**
 * Phase 9.1: tiny loopback HTTP server for the web preview.
 *
 * Loading a page over `file://` cannot `fetch()` sibling JSON, import ES
 * modules, or read anything beyond the CSS/JS referenced directly in the
 * HTML — the WebView blocks every same-origin file request. The preview
 * therefore serves the whole project folder over
 * `http://127.0.0.1:<ephemeral port>/`, which makes relative paths, fetch,
 * XHR and modules behave exactly like a `python -m http.server` dev server.
 *
 * The socket binds to the loopback interface by default (nothing on the LAN
 * can reach it), answers plain GET/HEAD requests without caching, and refuses
 * any path — encoded or not — that resolves outside the served folder.
 *
 * **Phase 37.1** adds the opt-in counterpart: `start(root, lanMode = true)`
 * binds `0.0.0.0`, so other devices on the same Wi-Fi can open the very same
 * URL the phone's WebView loads. Loopback stays the default everywhere; the
 * path-confined [resolveServedFile] guard is unchanged in both modes.
 */
class WebPreviewServer private constructor(
    private val root: File,
    private val socket: ServerSocket
) {
    @Volatile
    private var running = true

    private val stopped = java.util.concurrent.CountDownLatch(1)
    private val acceptThread: Thread

    /**
     * Captured at bind time: after [stop] a closed `ServerSocket` reports
     * `localPort == -1`, and a server row that outlived its socket (the
     * keep-alive case, Phase 37.2) must still be able to say which port it had.
     */
    val port: Int = socket.localPort

    /** `127.0.0.1` or `0.0.0.0` — what the socket was bound to (Phase 37.1). */
    val bindAddress: String = socket.inetAddress.hostAddress

    init {
        // A bounded accept, so [stop] can actually wait for this thread: without
        // the poll the loop sits in `accept()` and the fd (and therefore the
        // port) is only released whenever the blocked syscall happens to wake.
        runCatching { socket.soTimeout = ACCEPT_POLL_MILLIS }
        acceptThread = thread(isDaemon = true, name = "codec-preview-accept") {
            while (running) {
                val connection = try {
                    socket.accept()
                } catch (poll: java.net.SocketTimeoutException) {
                    continue
                } catch (closed: Exception) {
                    break
                }
                thread(isDaemon = true, name = "codec-preview-conn") {
                    runCatching { handle(connection) }
                    runCatching { connection.close() }
                }
            }
            // Close here too and only then open the gate: after [stopped] fires
            // the port is genuinely free, so "Stop, then re-run on the same
            // port" works on the first try (37.2 exit check 3).
            running = false
            runCatching { socket.close() }
            stopped.countDown()
        }
    }

    /** Stops the server and waits (bounded) until the listening port is released. */
    fun stop() {
        running = false
        runCatching { socket.close() }
        runCatching { acceptThread.join(STOP_JOIN_MILLIS) }
        runCatching { stopped.await(STOP_WAIT_MILLIS, TimeUnit.MILLISECONDS) }
    }

    /** True while the accept loop is alive (a stopped server answers nothing). */
    val isRunning: Boolean get() = running && !socket.isClosed

    private fun handle(connection: Socket) {
        connection.soTimeout = 5_000
        val reader = connection.getInputStream().bufferedReader(Charsets.ISO_8859_1)
        val requestLine = reader.readLine() ?: return
        // Headers are read and discarded: preview pages are static.
        while (true) {
            val line = reader.readLine() ?: break
            if (line.isEmpty()) break
        }
        val parts = requestLine.trim().split(' ')
        val method = parts.getOrNull(0)?.uppercase()
        val target = parts.getOrNull(1) ?: "/"
        val output = connection.getOutputStream()
        // Phase 37 (found by the LAN test): every response announces its
        // Content-Length, so an error written "header only" left the client
        // waiting for bytes that never came (HttpURLConnection: "Premature
        // EOF"). HEAD is the one case where a body-less answer is correct.
        if (method != "GET" && method != "HEAD") {
            respond(
                output, 405, "text/plain; charset=utf-8",
                "Method Not Allowed".toByteArray(), withBody = method != "HEAD"
            )
            return
        }
        val file = resolveServedFile(root, target)
        if (file == null) {
            respond(
                output, 404, "text/plain; charset=utf-8",
                "Not found: $target".toByteArray(), withBody = method != "HEAD"
            )
            return
        }
        val bytes = runCatching { file.readBytes() }.getOrNull()
        if (bytes == null) {
            respond(
                output, 500, "text/plain; charset=utf-8",
                "Read failed".toByteArray(), withBody = method != "HEAD"
            )
            return
        }
        respond(output, 200, contentTypeFor(file.name), bytes, withBody = method != "HEAD")
    }

    private fun respond(
        output: java.io.OutputStream,
        status: Int,
        contentType: String,
        body: ByteArray,
        withBody: Boolean
    ) {
        val reason = when (status) {
            200 -> "OK"
            404 -> "Not Found"
            405 -> "Method Not Allowed"
            else -> "Internal Server Error"
        }
        val header = buildString {
            append("HTTP/1.1 $status $reason\r\n")
            append("Content-Type: $contentType\r\n")
            append("Content-Length: ${body.size}\r\n")
            append("Cache-Control: no-store\r\n")
            append("Connection: close\r\n")
            append("\r\n")
        }.toByteArray(Charsets.ISO_8859_1)
        runCatching {
            output.write(header)
            if (withBody) output.write(body)
            output.flush()
        }
    }

    companion object {

        /** How often a blocked `accept()` wakes to notice that we stopped. */
        private const val ACCEPT_POLL_MILLIS = 200

        /** Bounds on [stop]: the accept loop never needs longer than one poll. */
        private const val STOP_JOIN_MILLIS = 1_500L
        private const val STOP_WAIT_MILLIS = 1_500L

        /** The two addresses CodeC ever binds a preview socket to. */
        const val LOOPBACK_HOST = "127.0.0.1"
        const val LAN_HOST = LanAddress.WILDCARD_HOST

        /**
         * Binds [host] on [port] (0 = ephemeral). Kept for callers that only
         * care about success: null when the folder is missing or the bind
         * failed for any reason.
         */
        fun start(rootDir: File, lanMode: Boolean = false, port: Int = 0): WebPreviewServer? =
            (startAt(rootDir, if (lanMode) LAN_HOST else LOOPBACK_HOST, port) as? PreviewStart.Ready)
                ?.server

        /**
         * Binds [host]:[port] and says *why* it could not (Phase 37.2 §4): a
         * port already taken is a message the UI can act on, not a silent
         * null. [port] 0 lets the OS pick an ephemeral one.
         */
        fun startAt(rootDir: File, host: String = LOOPBACK_HOST, port: Int = 0): PreviewStart {
            if (!rootDir.isDirectory) {
                return PreviewStart.Failed("Folder is not readable: ${rootDir.absolutePath}")
            }
            return try {
                // Bind in two steps on purpose: `ServerSocket(port, …, addr)`
                // creates the native fd *before* binding, so a failed bind
                // leaks it until the finalizer runs — and a LAN retry loop that
                // walks a port range would then be fighting its own leftovers.
                val socket = ServerSocket()
                socket.reuseAddress = true
                try {
                    socket.bind(java.net.InetSocketAddress(InetAddress.getByName(host), port))
                } catch (e: Exception) {
                    runCatching { socket.close() }
                    throw e
                }
                PreviewStart.Ready(WebPreviewServer(rootDir, socket))
            } catch (e: Exception) {
                if (isBindConflict(e)) {
                    PreviewStart.PortInUse(port, host)
                } else {
                    PreviewStart.Failed(e.message ?: "Could not start the preview server")
                }
            }
        }

        /** `BindException` / "address already in use", through the cause chain. */
        fun isBindConflict(error: Throwable): Boolean =
            generateSequence(error) { it.cause }.any { cause ->
                cause is java.net.BindException ||
                    cause.message?.lowercase()?.contains("already in use") == true
            }

        /** Percent-decodes a URL component; null on malformed escapes or control bytes. */
        fun decodePercent(raw: String): String? {
            if (!raw.contains('%')) return raw
            val out = StringBuilder(raw.length)
            var i = 0
            while (i < raw.length) {
                val c = raw[i]
                if (c == '%') {
                    if (i + 2 >= raw.length) return null
                    val hi = Character.digit(raw[i + 1], 16)
                    val lo = Character.digit(raw[i + 2], 16)
                    if (hi < 0 || lo < 0) return null
                    val byte = (hi shl 4) + lo
                    if (byte < 0x20) return null
                    out.append(byte.toInt().toChar())
                    i += 3
                } else {
                    if (c < ' ') return null
                    out.append(c)
                    i++
                }
            }
            return out.toString()
        }

        /**
         * Normalises a request target into relative path segments: strips the
         * query/fragment, percent-decodes, and collapses `.` / `..` — a `..`
         * that would escape the served root returns null instead.
         */
        fun cleanSegments(target: String): List<String>? {
            val path = target.substringBefore('?').substringBefore('#')
            val decoded = decodePercent(path) ?: return null
            if (decoded.isBlank()) return emptyList()
            val segments = decoded.replace('\\', '/').split('/')
            val out = ArrayList<String>()
            for (segment in segments) {
                when (segment) {
                    "", "." -> Unit
                    ".." -> if (out.isEmpty()) return null else out.removeAt(out.size - 1)
                    else -> {
                        if (segment.any { it < '\u0020' }) return null
                        out += segment
                    }
                }
            }
            return out
        }

        /**
         * Resolves a request target inside [root]: regular files only, a
         * directory needs its `index.html`, and anything escaping the root is
         * rejected. Returns null for "not found", never a listing.
         */
        fun resolveServedFile(root: File, target: String): File? {
            val segments = cleanSegments(target) ?: return null
            val canonicalRoot = runCatching { root.canonicalFile }.getOrNull() ?: return null
            if (!canonicalRoot.isDirectory) return null
            var current = canonicalRoot
            for (segment in segments) {
                val next = File(current, segment)
                val canonical = runCatching { next.canonicalFile }.getOrNull() ?: return null
                if (!isWithin(canonicalRoot, canonical)) return null
                current = canonical
            }
            if (current.isDirectory) {
                val index = File(current, "index.html")
                current = runCatching { index.canonicalFile }.getOrNull() ?: return null
                if (!isWithin(canonicalRoot, current) || !current.isFile) return null
            }
            return if (current.isFile) current else null
        }

        private fun isWithin(root: File, file: File): Boolean =
            file.path == root.path || file.path.startsWith(root.path + File.separator)

        /** Preview-friendly MIME map (everything an HTML/CSS/JS project references). */
        fun contentTypeFor(fileName: String): String {
            val ext = fileName.substringAfterLast('.', "").lowercase()
            return when (ext) {
                "html", "htm" -> "text/html; charset=utf-8"
                "css" -> "text/css; charset=utf-8"
                "js", "mjs", "cjs" -> "text/javascript; charset=utf-8"
                "json" -> "application/json; charset=utf-8"
                "svg" -> "image/svg+xml"
                "png" -> "image/png"
                "jpg", "jpeg" -> "image/jpeg"
                "gif" -> "image/gif"
                "webp" -> "image/webp"
                "ico" -> "image/x-icon"
                "bmp" -> "image/bmp"
                "woff" -> "font/woff"
                "woff2" -> "font/woff2"
                "ttf" -> "font/ttf"
                "otf" -> "font/otf"
                "txt", "md" -> "text/plain; charset=utf-8"
                "xml" -> "application/xml"
                "mp3" -> "audio/mpeg"
                "wav" -> "audio/wav"
                "ogg" -> "audio/ogg"
                "m4a" -> "audio/mp4"
                "mp4" -> "video/mp4"
                "webm" -> "video/webm"
                else -> "application/octet-stream"
            }
        }

        /** Percent-encodes a relative path for the preview URL (spaces etc.). */
        fun urlPathFor(relativePath: String): String =
            relativePath.replace('\\', '/').trimStart('/')
                .split('/')
                .joinToString("/") { segment ->
                    segment.toByteArray(Charsets.UTF_8).joinToString("") { byte ->
                        val c = byte.toInt() and 0xFF
                        if ((c in 'a'.code..'z'.code) || (c in 'A'.code..'Z'.code) ||
                            (c in '0'.code..'9'.code) || c == '.'.code || c == '-'.code ||
                            c == '_'.code || c == '~'.code
                        ) c.toChar().toString() else "%%%02X".format(c)
                    }
                }
    }

    /** How a preview-server start ended — a value, never an exception (37.2 §4). */
    sealed interface PreviewStart {
        data class Ready(val server: WebPreviewServer) : PreviewStart

        /** [port] is 0 when the OS was asked to pick one and the bind still failed. */
        data class PortInUse(val port: Int, val host: String) : PreviewStart

        data class Failed(val message: String) : PreviewStart
    }
}
