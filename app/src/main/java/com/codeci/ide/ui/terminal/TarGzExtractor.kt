package com.codeci.ide.ui.terminal

import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.zip.GZIPInputStream

/**
 * Minimal ustar/GNU tar.gz extractor. Bootstrap tarball root is PREFIX contents
 * (`bin/`, `lib/`, …). Never writes outside [destDir].
 */
object TarGzExtractor {

    fun extract(archive: File, destDir: File) {
        destDir.mkdirs()
        GZIPInputStream(FileInputStream(archive)).use { gz ->
            val header = ByteArray(512)
            var pendingLongName: String? = null
            while (true) {
                val n = readFully(gz, header)
                if (n == 0) break
                if (n < 512) throw IllegalStateException("truncated tar header")
                if (header.all { it.toInt() == 0 }) break
                val nameFromHeader = cString(header, 0, 100)
                val size = parseOctal(header, 124, 12)
                val type = header[156].toInt().toChar()
                val linkName = cString(header, 157, 100)
                val name = pendingLongName ?: nameFromHeader
                pendingLongName = null
                val data = ByteArray(size.toInt())
                readFully(gz, data)
                skipPadding(gz, size)
                when (type) {
                    'L' -> pendingLongName = cString(data, 0, data.size)
                    'x', 'g' -> { /* pax — ignore */ }
                    '5' -> {
                        val dir = safeFile(destDir, name)
                        dir.mkdirs()
                    }
                    '2' -> {
                        val target = safeFile(destDir, name)
                        target.parentFile?.mkdirs()
                        if (target.exists()) target.delete()
                        try {
                            java.nio.file.Files.createSymbolicLink(
                                target.toPath(),
                                java.nio.file.Paths.get(linkName)
                            )
                        } catch (_: Exception) {
                            // If symlink is blocked, skip; busybox applets still work.
                        }
                    }
                    '0', '\u0000' -> {
                        val out = safeFile(destDir, name)
                        out.parentFile?.mkdirs()
                        FileOutputStream(out).use { it.write(data) }
                        val mode = parseOctal(header, 100, 8).toInt()
                        if (mode and 0b001001001 != 0) {
                            out.setExecutable(true, false)
                        }
                    }
                }
            }
        }
    }

    internal fun safeFile(destDir: File, name: String): File {
        var rel = name.trimStart('/')
        if (rel.startsWith("./")) rel = rel.removePrefix("./")
        if (rel.startsWith("usr/")) rel = rel.removePrefix("usr/")
        val dest = destDir.canonicalFile
        val out = File(dest, rel).canonicalFile
        if (!out.path.startsWith(dest.path)) {
            throw SecurityException("tar path escapes prefix: $name")
        }
        return out
    }

    private fun readFully(input: java.io.InputStream, buf: ByteArray): Int {
        var off = 0
        while (off < buf.size) {
            val r = input.read(buf, off, buf.size - off)
            if (r < 0) return off
            off += r
        }
        return off
    }

    private fun skipPadding(input: java.io.InputStream, size: Long) {
        val pad = ((512 - (size % 512)) % 512).toInt()
        if (pad == 0) return
        val skip = ByteArray(pad)
        readFully(input, skip)
    }

    private fun cString(buf: ByteArray, off: Int, len: Int): String {
        var end = off
        val max = off + len
        while (end < max && buf[end] != 0.toByte()) end++
        return String(buf, off, end - off, Charsets.UTF_8)
    }

    private fun parseOctal(buf: ByteArray, off: Int, len: Int): Long {
        val s = cString(buf, off, len).trim()
        if (s.isEmpty()) return 0
        return s.toLong(8)
    }
}
