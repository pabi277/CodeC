package com.codeci.ide.ui.services

import java.io.File
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
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
 * The socket binds to the loopback interface only (nothing on the LAN can
 * reach it), answers plain GET/HEAD requests without caching, and refuses
 * any path — encoded or not — that resolves outside the served folder.
 */
class WebPreviewServer private constructor(
    private val root: File,
    private val socket: ServerSocket
) {
    @Volatile
    private var running = true

    val port: Int get() = socket.localPort

    init {
        thread(isDaemon = true, name = "codec-preview-accept") {
            while (running) {
                val connection = runCatching { socket.accept() }.getOrNull() ?: break
                thread(isDaemon = true, name = "codec-preview-conn") {
                    runCatching { handle(connection) }
                    runCatching { connection.close() }
                }
            }
        }
    }

    fun stop() {
        running = false
        runCatching { socket.close() }
    }

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
        if (method != "GET" && method != "HEAD") {
            respond(output, 405, "text/plain; charset=utf-8", "Method Not Allowed".toByteArray(), withBody = false)
            return
        }
        val file = resolveServedFile(root, target)
        if (file == null) {
            respond(output, 404, "text/plain; charset=utf-8", "Not found: $target".toByteArray(), withBody = false)
            return
        }
        val bytes = runCatching { file.readBytes() }.getOrNull()
        if (bytes == null) {
            respond(output, 500, "text/plain; charset=utf-8", "Read failed".toByteArray(), withBody = false)
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

        /** Binds to 127.0.0.1 on an ephemeral port; null when [rootDir] is missing or bind fails. */
        fun start(rootDir: File): WebPreviewServer? {
            if (!rootDir.isDirectory) return null
            return runCatching {
                val socket = ServerSocket(0, 16, InetAddress.getByName("127.0.0.1"))
                WebPreviewServer(rootDir, socket)
            }.getOrNull()
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
}
