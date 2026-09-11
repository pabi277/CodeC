package com.codeci.ide.ui.services

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import com.codeci.ide.BuildConfig
import com.codeci.ide.ui.terminal.UserlandInstaller
import com.codeci.ide.ui.utils.AppLogger
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The outcome of a manual update check (Settings → About → Check for updates).
 * Manual only — the app must not phone home on its own (the same law Phase 41
 * writes down for feedback), so this class is only ever driven by a user tap.
 */
sealed class UpdateCheck {
    /** Network or JSON failure; the Releases page is the useful fallback. */
    data object Failed : UpdateCheck()

    /** The API answered but no `app-v*` release exists yet (only bootstraps). */
    data object NoAppReleases : UpdateCheck()

    /** Installed version equals the newest app release. */
    data object UpToDate : UpdateCheck()

    /** The newest app release is OLDER than the installed build: refused. */
    data class OlderVersion(val reason: String, val candidate: String? = null) : UpdateCheck()

    /** A strictly newer release exists; [asset] was picked for this device. */
    data class UpdateAvailable(
        val release: AppRelease,
        val asset: UpdatePolicy.Asset,
        /** Published digest for [asset]; null means no auto-install, browser only. */
        val expectedSha256: String?
    ) : UpdateCheck()
}

sealed class DownloadResult {
    /** Downloaded and SHA-256 verified; safe to pass to [ApkUpdateManager.installApk]. */
    data class Ready(val file: File) : DownloadResult()

    /** Downloaded but the release published NO checksum: never auto-install. */
    data object BrowserOnly : DownloadResult()

    /** Any refusal/failure (reason is already spelled for the user). */
    data class Failed(val reason: String) : DownloadResult()
}

/**
 * Phase 42.1 — the update path made honest. Today the old manager asked GitHub
 * for `releases/latest` (which resolved to `userland-v1`, a boot­strap release
 * with no app APK) and would have installed the FIRST `.apk` asset with no
 * version compare, no size cap, no digest and no cleanup.
 *
 * Now: all network/JSON facts flow through [ReleaseFetch] (decode) and
 * [UpdatePolicy] (decide) — the same discipline `UserlandInstaller` applies
 * to the bootstrap download (`MIN_FREE_BYTES` excluded: an APK is ≤ 200 MB,
 * capped by [UpdatePolicy.MAX_APK_BYTES] and deleted on any refusal).
 *
 * Cleanup policy (recorded, spec §3): anything that did NOT launch the
 * installer is deleted immediately; a verified APK that DID launch the
 * installer is kept — the PackageInstaller may read through the FileProvider
 * grant after we return, so deleting it at once can break the install. It is
 * pruned by [pruneStaleDownloads] on the next check, and lives in cacheDir,
 * which the system may reclaim at will. At most one APK is ever left behind.
 */
class ApkUpdateManager(private val context: Context) {

    companion object {
        const val GITHUB_OWNER = "pabi277"
        const val GITHUB_REPO = "CodeC"

        /** The releases LIST (`/latest` is why the updater used to land on a bootstrap). */
        const val RELEASES_API =
            "https://api.github.com/repos/$GITHUB_OWNER/$GITHUB_REPO/releases?per_page=10"
        const val RELEASES_PAGE =
            "https://github.com/$GITHUB_OWNER/$GITHUB_REPO/releases"

        /** The version-compare input, isolated so tests can pin the parse rule. */
        fun installedVersion(): UpdatePolicy.Version? =
            UpdatePolicy.Version.parse(BuildConfig.VERSION_NAME)
    }

    /**
     * Fetch the releases list, decode it, and apply the pure policy:
     * app releases only (a `userland-*` tag can never be an app update,
     * whatever GitHub marks "Latest"), asset for THIS abi (universal
     * fallback), version compared on parsed X.Y.Z — never string equality.
     */
    suspend fun checkForUpdate(): UpdateCheck = withContext(Dispatchers.IO) {
        val body = httpGet(RELEASES_API) ?: return@withContext UpdateCheck.Failed
        val releases = ReleaseFetch.parseReleases(body) ?: return@withContext UpdateCheck.Failed
        val candidate = UpdatePolicy.newestAppRelease(releases)
            ?: return@withContext UpdateCheck.NoAppReleases
        val candidateVersion = UpdatePolicy.Version.parse(candidate.tag)
            ?: return@withContext UpdateCheck.Failed.also {
                AppLogger.e("Update", "unparseable app tag ${candidate.tag}")
            }
        when (val decision = UpdatePolicy.shouldOffer(installedVersion(), candidateVersion)) {
            is UpdatePolicy.Decision.Same -> UpdateCheck.UpToDate
            is UpdatePolicy.Decision.Older ->
                UpdateCheck.OlderVersion(decision.reason, candidateVersion.text)
            is UpdatePolicy.Decision.Unsafe ->
                UpdateCheck.OlderVersion(decision.reason, candidateVersion.text)
            is UpdatePolicy.Decision.Newer -> {
                val asset = UpdatePolicy.pickAsset(
                    candidate.assets,
                    Build.SUPPORTED_ABIS?.toList() ?: emptyList()
                ) ?: return@withContext UpdateCheck.NoAppReleases
                when (val pre = UpdatePolicy.preflight(asset)) {
                    is UpdatePolicy.Verification.Ok -> UpdateCheck.UpdateAvailable(
                        release = candidate,
                        asset = asset,
                        expectedSha256 = UpdatePolicy.digestsFromNotes(candidate.notesBody)[asset.name]
                    )
                    is UpdatePolicy.Verification.NeverInstall ->
                        UpdateCheck.OlderVersion(pre.reason)
                    is UpdatePolicy.Verification.RefuseBrowserOnly -> UpdateCheck.UpdateAvailable(
                        release = candidate,
                        asset = asset,
                        expectedSha256 = null
                    )
                }
            }
        }
    }

    /**
     * Download an [UpdateCheck.UpdateAvailable]'s asset into
     * `cacheDir/updates/`, stream-capped at [UpdatePolicy.MAX_APK_BYTES],
     * aborted on any truncation, then verified against the release's size and
     * the notes' `sha256:` line. Failed or refused downloads are deleted
     * before returning; a stray file from an earlier check is pruned first.
     */
    suspend fun downloadVerified(
        update: UpdateCheck.UpdateAvailable,
        onProgress: (bytesReceived: Long) -> Unit = {}
    ): DownloadResult = withContext(Dispatchers.IO) {
        val dir = File(context.cacheDir, "updates").apply { mkdirs() }
        val file = File(dir, update.asset.name.substringAfterLast('/').ifBlank { "CodeC-IDE.apk" })
        pruneStaleDownloads(dir, keep = file)
        try {
            val connection = URL(update.asset.downloadUrl).openConnection() as HttpURLConnection
            connection.instanceFollowRedirects = true
            connection.setRequestProperty("User-Agent", "CodeC-IDE")
            connection.connectTimeout = 15_000
            connection.readTimeout = 60_000
            try {
                if (connection.responseCode !in 200..299) {
                    return@withContext DownloadResult.Failed("HTTP ${connection.responseCode}")
                }
                val declared = connection.contentLengthLong
                if (declared > UpdatePolicy.MAX_APK_BYTES) {
                    return@withContext DownloadResult.Failed(
                        "download declares $declared bytes (cap ${UpdatePolicy.MAX_APK_BYTES})"
                    )
                }
                connection.inputStream.use { input ->
                    file.outputStream().use { output ->
                        val buf = ByteArray(16 * 1024)
                        var got = 0L
                        var n: Int
                        while (input.read(buf).also { n = it } != -1) {
                            got += n
                            // Abort BEFORE writing past the cap (spec: refuse early).
                            if (got > UpdatePolicy.MAX_APK_BYTES) {
                                throw DownloadCapExceeded()
                            }
                            output.write(buf, 0, n)
                            onProgress(got)
                        }
                        if (declared > 0 && got < declared) {
                            return@withContext DownloadResult.Failed(
                                "download truncated ($got of $declared bytes)"
                            )
                        }
                    }
                }
            } finally {
                connection.disconnect()
            }
        } catch (_: DownloadCapExceeded) {
            file.delete()
            return@withContext DownloadResult.Failed(
                "download exceeded the ${UpdatePolicy.MAX_APK_BYTES} byte cap"
            )
        } catch (e: Exception) {
            file.delete()
            AppLogger.e("Update", "APK download failed", e)
            return@withContext DownloadResult.Failed(e.message ?: "download failed")
        }

        when (
            val verdict = UpdatePolicy.verifyDownload(
                fileBytes = file.length(),
                actualSha256 = UserlandInstaller.sha256(file),
                asset = update.asset,
                expectedSha256 = update.expectedSha256
            )
        ) {
            is UpdatePolicy.Verification.Ok -> DownloadResult.Ready(file)
            is UpdatePolicy.Verification.RefuseBrowserOnly -> {
                file.delete()
                DownloadResult.BrowserOnly
            }
            is UpdatePolicy.Verification.NeverInstall -> {
                file.delete()
                DownloadResult.Failed(verdict.reason)
            }
        }
    }

    /** At most one downloaded APK is ever left in updates/ (policy above). */
    private fun pruneStaleDownloads(dir: File, keep: File) {
        dir.listFiles()?.forEach {
            if (it.absolutePath != keep.absolutePath) runCatching { it.delete() }
        }
    }

    private fun httpGet(url: String): String? {
        return try {
            val connection = URL(url).openConnection() as HttpURLConnection
            connection.setRequestProperty("User-Agent", "CodeC-IDE")
            connection.setRequestProperty("Accept", "application/vnd.github+json")
            connection.connectTimeout = 15_000
            connection.readTimeout = 15_000
            if (connection.responseCode !in 200..299) {
                AppLogger.e("Update", "GitHub API ${connection.responseCode}")
                connection.disconnect()
                return null
            }
            val body = connection.inputStream.bufferedReader().use { it.readText() }
            connection.disconnect()
            body
        } catch (e: Exception) {
            AppLogger.e("Update", "Failed to fetch releases", e)
            null
        }
    }

    fun canRequestPackageInstalls(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.packageManager.canRequestPackageInstalls()
        } else {
            true
        }
    }

    fun installPermissionIntent(): Intent {
        return Intent(
            Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
            Uri.parse("package:${context.packageName}")
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    fun installApk(file: File) {
        val uri = FileProvider.getUriForFile(
            context,
            "${BuildConfig.APPLICATION_ID}.fileprovider",
            file
        )
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    fun openReleasesPage() {
        context.startActivity(
            Intent(Intent.ACTION_VIEW, Uri.parse(RELEASES_PAGE)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }

    /** Thrown mid-stream to abort before the write crosses the cap. */
    private class DownloadCapExceeded : Exception()
}
