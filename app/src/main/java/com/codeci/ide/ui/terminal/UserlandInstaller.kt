package com.codeci.ide.ui.terminal

import android.content.Context
import android.net.ConnectivityManager
import android.os.Build
import android.os.StatFs
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
    data class Installed(val releaseTag: String, val archive: String) : UserlandStatus()
    data class Failed(val message: String) : UserlandStatus()
}

/**
 * A published CodeC userland bootstrap release.
 *
 * [PHASE3] is the Phase 3 package-manager bootstrap (real CodeC-built apt/dpkg,
 * seeded dpkg status database, termux-exec LD_PRELOAD library). [PHASE2] is the
 * Phase 2 shell-only userland: it has no apt/dpkg and exists only as a safe
 * fallback while the Phase 3 release is absent or unreachable.
 */
data class UserlandManifest(
    val releaseTag: String,
    val assetPrefix: String,
    val baseUrl: String = DEFAULT_BASE_URL
) {
    fun archiveName(arch: String): String = "$assetPrefix-$arch.tar.gz"
    fun tarballUrl(arch: String): String = "$baseUrl/$releaseTag/$assetPrefix-$arch.tar.gz"
    fun shaUrl(arch: String): String = tarballUrl(arch) + ".sha256"

    companion object {
        const val DEFAULT_BASE_URL = "https://github.com/pabi277/CodeC/releases/download"

        /** Phase 3 package-manager bootstrap. */
        val PHASE3 = UserlandManifest("userland-v2-dev", "bootstrap-phase3")

        /** Phase 2 shell-only userland (safe fallback, no apt/dpkg). */
        val PHASE2 = UserlandManifest("userland-v1", "bootstrap")

        /** Preferred install order: Phase 3 first, Phase 2 as fallback. */
        val ORDER: List<UserlandManifest> = listOf(PHASE3, PHASE2)

        /** Device ABI -> bootstrap architecture; null when no bootstrap exists. */
        fun archNameForAbis(abis: Array<out String>): String? = when {
            abis.any { it == "arm64-v8a" } -> "aarch64"
            abis.any { it == "x86_64" } -> "x86_64"
            else -> null
        }

        fun archName(): String? = archNameForAbis(Build.SUPPORTED_ABIS ?: emptyArray())
    }
}

/** Thrown when a release (or one of its assets) does not exist (HTTP 404). */
class ReleaseNotPublished(message: String = "release not published") : Exception(message)

/**
 * Downloads, verifies, and installs a CodeC userland bootstrap into the app
 * private prefix (`/data/data/com.codeci.ide/files/usr`).
 *
 * Guarantees:
 * - Phase 3 bootstrap is selected when published; Phase 2 (userland-v1) is a
 *   safe automatic fallback; the app never touches official Termux assets.
 * - Downloads land in `.partial` files and are renamed only after the SHA-256
 *   sidecar matches, so an interrupted download is always re-downloadable.
 * - Extraction happens in a staging directory; the live prefix is replaced by
 *   an atomic rename on the same filesystem and rolled back on failure.
 * - Bash/BusyBox must actually start (ELF magic alone is not enough); a
 *   missing shared library (e.g. `libandroid-support.so`) is diagnosed and
 *   reported instead of letting the PTY die.
 * - An existing runnable userland is never destroyed: a failed staged install
 *   leaves the old prefix untouched, and an installed userland is only
 *   replaced when the target release differs (upgrade) or `force` is set.
 * - The Phase 1 `cc` launcher, TCC bundle, and a real ELF Bash are never
 *   overwritten with a shim (see [ShellBootstrap]).
 */
class UserlandInstaller(
    private val filesDir: File,
    private val cacheDir: File,
    private val onlineProvider: () -> Boolean = { true },
    private val archProvider: () -> String? = { UserlandManifest.archName() },
    private val freeSpaceProvider: (File) -> Long = { freeSpaceBytes(it) },
    private val launchChecker: (File, File, Boolean) -> ShellEnvironment.LaunchDiagnostic =
        ShellEnvironment::launchDiagnostic,
    private val candidates: List<UserlandManifest> = UserlandManifest.ORDER
) {
    constructor(
        context: Context,
        candidates: List<UserlandManifest> = UserlandManifest.ORDER
    ) : this(
        filesDir = context.filesDir,
        cacheDir = File(context.cacheDir, "userland"),
        onlineProvider = { isOnline(context) },
        candidates = candidates
    )

    fun hasRealUserland(prefix: File = ShellEnvironment.prefixDir(filesDir)): Boolean =
        ShellEnvironment.hasRealUserland(prefix)

    /**
     * Installs (or upgrades) the best published bootstrap for this device.
     * Offline with a runnable userland: keep it (offline startup stays intact).
     */
    fun installIfNeeded(
        force: Boolean = false,
        onProgress: (String) -> Unit = {}
    ): UserlandStatus {
        val prefix = ShellEnvironment.prefixDir(filesDir)
        try {
            prefix.mkdirs()
            val runnable = hasRunnableUserland(prefix)

            if (!force && runnable) {
                val current = installedRelease(prefix)
                if (!onlineProvider()) {
                    onProgress("userland: offline — using installed userland")
                    return UserlandStatus.AlreadyInstalled
                }
                val target = chooseRelease(archProvider())
                if (target == null) {
                    // Online but no release reachable; keep what works.
                    onProgress("userland: release check failed — keeping installed userland")
                    return UserlandStatus.AlreadyInstalled
                }
                if (current == target.releaseTag) {
                    onProgress("userland: already installed (${target.releaseTag})")
                    return UserlandStatus.AlreadyInstalled
                }
                onProgress("userland: upgrading ${current ?: "userland"} to ${target.releaseTag}…")
            }

            if (!onlineProvider()) {
                onProgress("userland: offline — using built-in cc (TCC)")
                return UserlandStatus.SkippedOffline
            }
            val arch = archProvider()
            if (arch == null) {
                onProgress("userland: no bootstrap for this ABI")
                return UserlandStatus.SkippedNoRelease
            }
            val target = chooseRelease(arch)
                ?: run {
                    onProgress("userland: no release yet")
                    return UserlandStatus.SkippedNoRelease
                }
            try {
                return installRelease(target, arch, onProgress)
            } catch (e: ReleaseNotPublished) {
                if (target.releaseTag != UserlandManifest.PHASE3.releaseTag) throw e
                // The sidecar existed but an asset is missing: inconsistent
                // release — fall back to the safe Phase 2 userland.
                throw FallbackToPhase2(e.message ?: "asset missing")
            } catch (e: FallbackToPhase2) {
                return fallbackToPhase2(e, arch, onProgress)
            } catch (e: Exception) {
                if (target.releaseTag != UserlandManifest.PHASE3.releaseTag) throw e
                // A broken Phase 3 bootstrap must never leave the device with
                // no userland: the Phase 2 shell-only userland is the fallback.
                return fallbackToPhase2(FallbackToPhase2(e.message ?: "install failed"), arch, onProgress)
            }
        } catch (e: ReleaseNotPublished) {
            onProgress("userland: no release yet")
            return UserlandStatus.SkippedNoRelease
        } catch (e: Exception) {
            AppLogger.e("UserlandInstaller", "install failed", e)
            val msg = e.message ?: e.javaClass.simpleName
            onProgress("userland: $msg — cc (TCC) still works offline")
            return UserlandStatus.Failed(msg)
        }
    }

    private fun fallbackToPhase2(
        cause: FallbackToPhase2,
        arch: String,
        onProgress: (String) -> Unit
    ): UserlandStatus {
        onProgress(
            "userland: Phase 3 bootstrap unusable (${cause.message}) — falling back to ${UserlandManifest.PHASE2.releaseTag}"
        )
        val fallback = chooseRelease(arch, only = UserlandManifest.PHASE2)
            ?: throw cause
        return installRelease(fallback, arch, onProgress)
    }

    /**
     * The release to install: the first candidate in [candidates] whose
     * SHA-256 sidecar is published and well-formed for [arch].
     */
    internal fun chooseRelease(arch: String?, only: UserlandManifest? = null): UserlandManifest? {
        if (arch == null) return null
        val ordered = if (only != null) listOf(only) else candidates
        for (candidate in ordered) {
            try {
                val sidecar = fetchSidecar(candidate.shaUrl(arch))
                if (isValidSha(sidecar)) return candidate
            } catch (e: ReleaseNotPublished) {
                // Try the next (older) release.
            } catch (e: Exception) {
                // Probe failure (network, …). For a targeted fallback rethrow;
                // otherwise prefer the next (older) published release.
                AppLogger.w("UserlandInstaller", "release probe failed for ${candidate.releaseTag}: ${e.message}")
                if (only != null) throw e
            }
        }
        return null
    }

    private fun installRelease(
        manifest: UserlandManifest,
        arch: String,
        onProgress: (String) -> Unit
    ): UserlandStatus {
        val prefix = ShellEnvironment.prefixDir(filesDir)
        cacheDir.mkdirs()
        val name = manifest.archiveName(arch)
        val finalTar = File(cacheDir, name)
        val partial = File(cacheDir, name + ".partial")

        onProgress("userland: fetching SHA-256…")
        val expected = fetchSidecar(manifest.shaUrl(arch))
        if (!isValidSha(expected)) {
            throw ReleaseNotPublished("invalid SHA-256 sidecar for ${manifest.releaseTag}")
        }

        // Preflight: the bootstrap expands several times its compressed size
        // and the staged copy lives next to the prefix.
        val preFree = freeSpaceProvider(filesDir)
        if (preFree in 0 until MIN_FREE_BYTES) {
            throw IllegalStateException(
                "insufficient disk space (have $preFree bytes, need at least $MIN_FREE_BYTES)"
            )
        }

        onProgress("userland: downloading $name…")
        try {
            downloadFile(manifest.tarballUrl(arch), partial) { pct, bytes ->
                onProgress("userland: download $pct% ($bytes bytes)")
            }
        } catch (e: ReleaseNotPublished) {
            partial.delete()
            throw e
        } catch (e: Exception) {
            partial.delete()
            throw e
        }
        onProgress("userland: verifying SHA-256…")
        val actual = sha256(partial)
        if (!actual.equals(expected, ignoreCase = true)) {
            partial.delete()
            throw IllegalStateException("SHA-256 mismatch (got $actual)")
        }
        if (finalTar.exists()) finalTar.delete()
        if (!partial.renameTo(finalTar)) {
            partial.delete()
            throw IllegalStateException("cannot move verified download into place")
        }

        // Enough room for the staged extraction (worst case ~3x the archive
        // plus margin, measured on this filesystem).
        val free = freeSpaceProvider(filesDir)
        val required = finalTar.length() * 3 + 16L * 1024 * 1024
        if (free in 0 until required) {
            throw IllegalStateException(
                "insufficient disk space to extract (have $free bytes, need ~$required bytes)"
            )
        }

        onProgress("userland: extracting into $prefix")
        val staged = File(filesDir, STAGING_DIR + "-" + System.currentTimeMillis())
        try {
            TarGzExtractor.extract(finalTar, staged)
            requireLaunchable(staged, "staged userland cannot start")
            swapPrefix(staged, prefix)
        } catch (e: Exception) {
            staged.deleteRecursively()
            throw e
        }

        // Post-install check on the live prefix (also catches devices where the
        // staged probe and the live layout differ).
        val live = launchDiagnosticOrNull(prefix)
        if (live != null) {
            onProgress("userland: installed, but no shell can start:$live — reinstall or check disk space")
        } else {
            onProgress("userland: ready")
        }
        writeMarkers(prefix, manifest, arch)
        chmodBin(ShellEnvironment.binDir(prefix))
        return UserlandStatus.Installed(manifest.releaseTag, name)
    }

    /** True when a native shell in [prefix] can actually start. */
    fun hasRunnableUserland(prefix: File): Boolean {
        val bin = ShellEnvironment.binDir(prefix)
        return launchChecker(File(bin, "bash"), prefix, true).ok ||
            launchChecker(File(bin, "busybox"), prefix, false).ok
    }

    /**
     * Throws when neither Bash nor BusyBox in [dir] can start. The message
     * includes the missing shared library when the dynamic loader reported
     * one (e.g. `missing library libandroid-support.so`).
     */
    private fun requireLaunchable(dir: File, what: String) {
        val bin = ShellEnvironment.binDir(dir)
        val bash = File(bin, "bash")
        if (!ShellEnvironment.isElf(bash)) {
            throw IllegalStateException("bootstrap archive has no ELF bash")
        }
        val bashDiag = launchChecker(bash, dir, true)
        if (bashDiag.ok) return
        val busyboxDiag = launchChecker(File(bin, "busybox"), dir, false)
        if (busyboxDiag.ok) return
        val missing = bashDiag.missingLibrary ?: busyboxDiag.missingLibrary
        throw IllegalStateException(what + (missing?.let { ": missing library $it" } ?: ""))
    }

    /** `null` when at least one shell starts; otherwise the diagnostic suffix. */
    private fun launchDiagnosticOrNull(prefix: File): String? {
        val bin = ShellEnvironment.binDir(prefix)
        val bashDiag = launchChecker(File(bin, "bash"), prefix, true)
        if (bashDiag.ok) return null
        val busyboxDiag = launchChecker(File(bin, "busybox"), prefix, false)
        if (busyboxDiag.ok) return null
        val missing = bashDiag.missingLibrary ?: busyboxDiag.missingLibrary
        return (missing?.let { " missing library $it" } ?: "")
    }

    /** Release tag recorded in [MARKER_RELEASE] (null when unknown). */
    fun installedRelease(prefix: File): String? {
        val marker = File(prefix, MARKER_RELEASE)
        if (marker.isFile) {
            val value = marker.readText().trim()
            if (value.isNotEmpty()) return value
        }
        // Marker written by the Phase 2 installer.
        if (File(prefix, LEGACY_MARKER).isFile) return UserlandManifest.PHASE2.releaseTag
        return null
    }

    private fun writeMarkers(prefix: File, manifest: UserlandManifest, arch: String) {
        writeTextAtomically(File(prefix, MARKER_RELEASE), manifest.releaseTag)
        writeTextAtomically(File(prefix, MARKER_ARCH), arch)
    }

    /**
     * Atomically replaces [prefix] with [staged] (same filesystem). The old
     * prefix is moved aside, restored if the rename fails, and deleted only
     * after the new prefix is in place.
     */
    internal fun swapPrefix(staged: File, prefix: File) {
        if (!prefix.exists()) {
            if (!staged.renameTo(prefix)) {
                throw IllegalStateException("cannot install userland (rename failed)")
            }
            return
        }
        val old = File(prefix.parentFile, prefix.name + ".old-" + System.currentTimeMillis())
        if (!prefix.renameTo(old)) {
            throw IllegalStateException("cannot move old userland aside")
        }
        if (!staged.renameTo(prefix)) {
            old.renameTo(prefix) // roll back: previous userland is untouched
            throw IllegalStateException("cannot replace userland; previous userland restored")
        }
        old.deleteRecursively()
    }

    private fun writeTextAtomically(file: File, text: String) {
        val tmp = File(file.parentFile, file.name + ".tmp")
        tmp.writeText(text)
        if (!tmp.renameTo(file) && !file.delete() && !tmp.renameTo(file)) {
            tmp.delete()
            throw IllegalStateException("cannot write ${file.name}")
        }
    }

    private fun chmodBin(bin: File) {
        if (!bin.isDirectory) return
        bin.listFiles()?.forEach { f ->
            if (f.isFile) f.setExecutable(true, false)
        }
    }

    private fun fetchSidecar(url: String): String {
        val conn = open(url)
        try {
            when (val code = conn.responseCode) {
                in 200..299 -> return conn.inputStream.bufferedReader().readText()
                    .trim().substringBefore(' ').lowercase()
                404 -> throw ReleaseNotPublished(url)
                else -> throw IllegalStateException("HTTP $code")
            }
        } finally {
            conn.disconnect()
        }
    }

    private fun isValidSha(value: String): Boolean =
        value.length == 64 && value.all { it in '0'..'9' || it in 'a'..'f' }

    private fun downloadFile(url: String, dest: File, progress: (Int, Long) -> Unit) {
        val conn = open(url)
        try {
            when (val code = conn.responseCode) {
                in 200..299 -> { /* fall through */ }
                404 -> throw ReleaseNotPublished(url)
                else -> throw IllegalStateException("HTTP $code")
            }
            val total = conn.contentLengthLong
            // Always start the .partial fresh: an interrupted download is
            // recovered by re-downloading, never by trusting stale bytes.
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
        private const val STAGING_DIR = ".userland-staging"
        private const val MARKER_RELEASE = ".userland-release"
        private const val MARKER_ARCH = ".userland-arch"
        private const val LEGACY_MARKER = ".userland-v-userland-v1"
        private const val MIN_FREE_BYTES = 48L * 1024 * 1024

        /** Thrown by [installRelease] callers when Phase 3 must fall back to Phase 2. */
        internal class FallbackToPhase2(message: String) : Exception(message)

        fun sha256(file: File): String {
            val digest = MessageDigest.getInstance("SHA-256")
            FileInputStream(file).use { input ->
                val buf = ByteArray(8192)
                var n: Int
                while (input.read(buf).also { n = it } != -1) digest.update(buf, 0, n)
            }
            return digest.digest().joinToString("") { "%02x".format(it) }
        }

        fun freeSpaceBytes(file: File): Long = try {
            StatFs(file.path).availableBytes
        } catch (_: Exception) {
            -1L // unknown — skip the disk-space preflight
        }

        fun isOnline(context: Context): Boolean {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
                ?: return false
            @Suppress("DEPRECATION")
            val info = cm.activeNetworkInfo
            return info != null && info.isConnected
        }
    }
}
