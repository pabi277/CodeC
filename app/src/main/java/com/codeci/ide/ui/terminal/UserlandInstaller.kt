package com.codeci.ide.ui.terminal

import android.content.Context
import android.net.ConnectivityManager
import android.os.Build
import com.codeci.ide.ui.utils.AppLogger
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

sealed class UserlandStatus {
    data object AlreadyInstalled : UserlandStatus()
    data object SkippedOffline : UserlandStatus()
    data object SkippedNoRelease : UserlandStatus()
    data class Installed(val archive: String) : UserlandStatus()
    data class Failed(val message: String) : UserlandStatus()
}

object UserlandManifest {
    const val RELEASE_TAG = "userland-v1"
    const val REPO = "pabi277/CodeC"

    fun archName(): String? {
        val abis = Build.SUPPORTED_ABIS ?: emptyArray()
        return when {
            abis.any { it == "arm64-v8a" } -> "aarch64"
            abis.any { it == "x86_64" } -> "x86_64"
            else -> null
        }
    }

    fun tarballUrl(arch: String): String =
        "https://github.com/$REPO/releases/download/$RELEASE_TAG/bootstrap-$arch.tar.gz"

    fun shaUrl(arch: String): String = tarballUrl(arch) + ".sha256"
}

class UserlandInstaller(private val context: Context) {

    fun hasRealUserland(prefix: File = ShellEnvironment.prefixDir(context.filesDir)): Boolean =
        ShellEnvironment.hasRealUserland(prefix)

    fun isOnline(): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return false
        @Suppress("DEPRECATION")
        val info = cm.activeNetworkInfo
        return info != null && info.isConnected
    }

    /**
     * Download + SHA-256 verify + extract into `$PREFIX` when no real bash/busybox.
     * Offline: skip (Phase 1 TCC `cc` still works).
     */
    fun installIfNeeded(
        force: Boolean = false,
        onProgress: (String) -> Unit = {}
    ): UserlandStatus {
        val prefix = ShellEnvironment.prefixDir(context.filesDir)
        prefix.mkdirs()
        if (!force && hasRealUserland(prefix)) {
            onProgress("userland: already installed")
            return UserlandStatus.AlreadyInstalled
        }
        if (!isOnline()) {
            onProgress("userland: offline — using built-in cc (TCC)")
            return UserlandStatus.SkippedOffline
        }
        val arch = UserlandManifest.archName()
        if (arch == null) {
            onProgress("userland: no bootstrap for this ABI")
            return UserlandStatus.SkippedNoRelease
        }
        val cache = File(context.cacheDir, "userland")
        cache.mkdirs()
        val tar = File(cache, "bootstrap-$arch.tar.gz")
        try {
            onProgress("userland: fetching SHA-256…")
            val expected = downloadText(UserlandManifest.shaUrl(arch)).trim()
                .substringBefore(' ')
                .lowercase()
            if (expected.length != 64) {
                onProgress("userland: no release yet (tag ${UserlandManifest.RELEASE_TAG})")
                return UserlandStatus.SkippedNoRelease
            }
            onProgress("userland: downloading bootstrap-$arch.tar.gz…")
            downloadFile(UserlandManifest.tarballUrl(arch), tar) { pct, bytes ->
                onProgress("userland: download $pct% ($bytes bytes)")
            }
            onProgress("userland: verifying SHA-256…")
            val actual = sha256(tar)
            if (!actual.equals(expected, ignoreCase = true)) {
                tar.delete()
                return UserlandStatus.Failed("SHA-256 mismatch (got $actual)")
            }
            onProgress("userland: extracting into $prefix")
            TarGzExtractor.extract(tar, prefix)
            chmodBin(File(prefix, "bin"))
            File(prefix, ".userland-v${UserlandManifest.RELEASE_TAG}").writeText(arch)
            onProgress("userland: ready")
            return UserlandStatus.Installed(tar.name)
        } catch (e: Exception) {
            AppLogger.e("UserlandInstaller", "install failed", e)
            val msg = e.message ?: e.javaClass.simpleName
            onProgress("userland: $msg — cc (TCC) still works offline")
            return if (msg.contains("404") || msg.contains("File not found", true)) {
                UserlandStatus.SkippedNoRelease
            } else {
                UserlandStatus.Failed(msg)
            }
        }
    }

    private fun chmodBin(bin: File) {
        if (!bin.isDirectory) return
        bin.listFiles()?.forEach { f ->
            if (f.isFile) f.setExecutable(true, false)
        }
    }

    private fun downloadText(url: String): String {
        val conn = open(url)
        try {
            if (conn.responseCode !in 200..299) {
                throw IllegalStateException("HTTP ${conn.responseCode}")
            }
            return conn.inputStream.bufferedReader().readText()
        } finally {
            conn.disconnect()
        }
    }

    private fun downloadFile(url: String, dest: File, progress: (Int, Long) -> Unit) {
        val conn = open(url)
        try {
            if (conn.responseCode !in 200..299) {
                throw IllegalStateException("HTTP ${conn.responseCode}")
            }
            val total = conn.contentLengthLong
            FileOutputStream(dest).use { out ->
                val buf = ByteArray(16 * 1024)
                var got = 0L
                var n: Int
                val input = conn.inputStream
                while (input.read(buf).also { n = it } != -1) {
                    out.write(buf, 0, n)
                    got += n
                    val pct = if (total > 0) ((got * 100) / total).toInt() else 0
                    progress(pct, got)
                }
            }
        } finally {
            conn.disconnect()
        }
    }

    private fun open(url: String): HttpURLConnection {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.instanceFollowRedirects = true
        conn.connectTimeout = 20_000
        conn.readTimeout = 60_000
        conn.setRequestProperty("User-Agent", "CodeC-IDE")
        conn.connect()
        return conn
    }

    companion object {
        fun sha256(file: File): String {
            val digest = MessageDigest.getInstance("SHA-256")
            FileInputStream(file).use { input ->
                val buf = ByteArray(8192)
                var n: Int
                while (input.read(buf).also { n = it } != -1) digest.update(buf, 0, n)
            }
            return digest.digest().joinToString("") { "%02x".format(it) }
        }
    }
}
