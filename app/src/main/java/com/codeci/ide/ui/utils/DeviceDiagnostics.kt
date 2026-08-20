package com.codeci.ide.ui.utils

import android.os.Build
import java.io.File

/**
 * Best-effort device diagnostics used to explain *why* a downloaded compiler
 * cannot execute. Parsing helpers are pure so they are unit-testable.
 *
 * The two hardware/OS-level causes we can detect from an app:
 *  - CPU ABI mismatch (x86 emulator vs arm64 Clang) -> "Exec format error".
 *  - The app-data mount being `noexec` (some emulators / cloud phones /
 *    enterprise-managed devices) -> execve() fails with EACCES.
 *
 * The third cause — Android 10+'s W^X rule that denies execve() of writable
 * files in the app home for apps targeting API 29+ — is fixed by targeting
 * API 28 (see app/build.gradle.kts) and is not detectable at runtime, only
 * by its "Permission denied" signature.
 */
object DeviceDiagnostics {

    /** e.g. "arm64-v8a, armeabi-v7a" or "x86_64". */
    fun abiSummary(): String = Build.SUPPORTED_ABIS.joinToString(", ")

    fun isArm64(): Boolean =
        Build.SUPPORTED_ABIS.any { it.contains("arm64", ignoreCase = true) }

    fun isLikelyEmulator(): Boolean {
        val haystack = listOf(Build.FINGERPRINT, Build.PRODUCT, Build.MODEL, Build.HARDWARE)
            .joinToString(" ")
            .lowercase()
        return haystack.contains("generic") ||
            haystack.contains("emulator") ||
            haystack.contains("sdk_gphone") ||
            haystack.contains("goldfish") ||
            haystack.contains("ranchu") ||
            haystack.contains("vsoc") ||
            Build.HARDWARE.lowercase().contains("virtual")
    }

    fun osSummary(): String = "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})"

    data class MountInfo(
        val device: String,
        val mountPoint: String,
        val flags: List<String>
    ) {
        val isNoExec: Boolean get() = flags.contains("noexec")
    }

    /**
     * Splits one /proc/self/mounts line into fields, unescaping `\040`
     * (space), `\011` (tab), `\012` (newline) and `\134` (backslash).
     * Returns null for blank/odd lines.
     */
    fun splitMountLine(line: String): List<String>? {
        if (line.isBlank()) return null
        val fields = mutableListOf<String>()
        val current = StringBuilder()
        var i = 0
        while (i < line.length) {
            val c = line[i]
            if (c == '\\' && i + 3 < line.length && line[i + 1] == '0') {
                val escaped = when (line[i + 2]) {
                    '4' -> if (line[i + 3] == '0') ' ' else null
                    '1' -> when (line[i + 3]) {
                        '1' -> '\t'
                        '2' -> '\n'
                        '3' -> '\\'
                        else -> null
                    }
                    else -> null
                }
                if (escaped != null) {
                    current.append(escaped)
                    i += 4
                    continue
                }
            }
            if (c == ' ') {
                fields.add(current.toString())
                current.setLength(0)
                i += 1
                continue
            }
            current.append(c)
            i += 1
        }
        fields.add(current.toString())
        if (fields.size < 4) return null
        return fields
    }

    /**
     * Finds the mount whose mount point is the longest prefix of [path],
     * i.e. the filesystem that actually backs the path.
     */
    fun findMount(mountsText: String, path: String): MountInfo? {
        val normalized = runCatching { File(path).canonicalPath }.getOrDefault(path)
        var best: MountInfo? = null
        for (line in mountsText.lineSequence()) {
            val parts = splitMountLine(line) ?: continue
            val mountPoint = parts[1]
            val matches = normalized == mountPoint ||
                normalized.startsWith(mountPoint + File.separator)
            if (matches && (best == null || mountPoint.length > best.mountPoint.length)) {
                // /proc/mounts: device mountpoint fstype options dump pass
                best = MountInfo(parts[0], mountPoint, parts[3].split(","))
            }
        }
        return best
    }

    fun mountInfoFor(dir: File): MountInfo? {
        return try {
            val text = File("/proc/self/mounts").readText()
            findMount(text, dir.absolutePath)
        } catch (e: Exception) {
            null
        }
    }

    fun isNoExec(dir: File): Boolean = mountInfoFor(dir)?.isNoExec ?: false

    /**
     * One-line environment summary used in logs and error messages, e.g.
     * "Android 13 (API 33), ABI: arm64-v8a, app storage mount: /dev/... on
     * /data [rw,exec,...]".
     */
    fun summary(dataDir: File): String {
        val mount = mountInfoFor(dataDir)
        val mountText = if (mount != null) {
            "app storage mount: ${mount.device} on ${mount.mountPoint} " +
                "[${mount.flags.joinToString(",")}]"
        } else {
            "app storage mount: unknown"
        }
        return buildString {
            append(osSummary())
            append(", ABI: ").append(abiSummary())
            if (isLikelyEmulator()) append(", emulator-like device")
            append(", ").append(mountText)
        }
    }
}
