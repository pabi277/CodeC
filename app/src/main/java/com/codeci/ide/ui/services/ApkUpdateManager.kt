package com.codeci.ide.ui.services

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import com.codeci.ide.BuildConfig
import com.codeci.ide.ui.utils.AppLogger
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

data class GithubRelease(
    val tag: String,
    val name: String,
    val apkUrl: String
)

class ApkUpdateManager(private val context: Context) {

    companion object {
        const val GITHUB_OWNER = "pabi277"
        const val GITHUB_REPO = "CodeC"
        const val RELEASES_API =
            "https://api.github.com/repos/$GITHUB_OWNER/$GITHUB_REPO/releases/latest"
        const val RELEASES_PAGE =
            "https://github.com/$GITHUB_OWNER/$GITHUB_REPO/releases"
    }

    suspend fun fetchLatestRelease(): GithubRelease? = withContext(Dispatchers.IO) {
        try {
            val connection = URL(RELEASES_API).openConnection() as HttpURLConnection
            connection.setRequestProperty("User-Agent", "CodeC-IDE")
            connection.setRequestProperty("Accept", "application/vnd.github+json")
            connection.connectTimeout = 15_000
            connection.readTimeout = 15_000
            if (connection.responseCode !in 200..299) {
                AppLogger.e("Update", "GitHub API ${connection.responseCode}")
                connection.disconnect()
                return@withContext null
            }
            val body = connection.inputStream.bufferedReader().use { it.readText() }
            connection.disconnect()
            val json = JSONObject(body)
            val assets = json.optJSONArray("assets") ?: return@withContext null
            var apkUrl = ""
            for (i in 0 until assets.length()) {
                val asset = assets.getJSONObject(i)
                val name = asset.optString("name")
                if (name.endsWith(".apk", ignoreCase = true)) {
                    apkUrl = asset.optString("browser_download_url")
                    break
                }
            }
            if (apkUrl.isBlank()) return@withContext null
            GithubRelease(
                tag = json.optString("tag_name"),
                name = json.optString("name", json.optString("tag_name")),
                apkUrl = apkUrl
            )
        } catch (e: Exception) {
            AppLogger.e("Update", "Failed to fetch release", e)
            null
        }
    }

    suspend fun downloadApk(url: String): File? = withContext(Dispatchers.IO) {
        try {
            val dir = File(context.cacheDir, "updates")
            if (!dir.exists()) dir.mkdirs()
            val file = File(dir, "CodeC-IDE.apk")
            val connection = URL(url).openConnection() as HttpURLConnection
            connection.instanceFollowRedirects = true
            connection.setRequestProperty("User-Agent", "CodeC-IDE")
            connection.connect()
            if (connection.responseCode !in 200..299) {
                connection.disconnect()
                return@withContext null
            }
            connection.inputStream.use { input ->
                file.outputStream().use { output -> input.copyTo(output) }
            }
            connection.disconnect()
            file
        } catch (e: Exception) {
            AppLogger.e("Update", "APK download failed", e)
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
}
