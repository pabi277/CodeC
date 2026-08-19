package com.codeci.ide.ui.modules

import android.content.Context
import com.codeci.ide.ui.utils.AppLogger
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

class ManifestRepository(private val context: Context) {

    companion object {
        const val MANIFEST_URL =
            "https://raw.githubusercontent.com/pabi277/CodeC-Modules/main/manifest.json"
    }

    data class ManifestLoad(
        val modules: List<Module>,
        val fromCache: Boolean,
        val error: String? = null
    )

    fun cacheFile(modulesRoot: File): File = File(modulesRoot, "manifest.cache.json")

    suspend fun fetchManifest(modulesRoot: File): ManifestLoad = withContext(Dispatchers.IO) {
        val cache = cacheFile(modulesRoot)
        try {
            val connection = URL(MANIFEST_URL).openConnection() as HttpURLConnection
            connection.connectTimeout = 15_000
            connection.readTimeout = 20_000
            connection.instanceFollowRedirects = true
            connection.setRequestProperty("User-Agent", "CodeC-IDE")
            connection.connect()
            if (connection.responseCode !in 200..299) {
                throw IllegalStateException("HTTP ${connection.responseCode}")
            }
            val body = connection.inputStream.bufferedReader().use { it.readText() }
            connection.disconnect()
            cache.writeText(body)
            ManifestLoad(parseModules(body), fromCache = false)
        } catch (e: Exception) {
            AppLogger.e("Manifest", "Failed to fetch manifest", e)
            if (cache.exists()) {
                try {
                    ManifestLoad(
                        modules = parseModules(cache.readText()),
                        fromCache = true,
                        error = "Can't check for updates. Showing last downloaded catalog."
                    )
                } catch (parseError: Exception) {
                    ManifestLoad(emptyList(), fromCache = true, error = userFriendlyNetworkError(e))
                }
            } else {
                ManifestLoad(emptyList(), fromCache = false, error = userFriendlyNetworkError(e))
            }
        }
    }

    fun parseModules(json: String): List<Module> {
        val root = JSONObject(json)
        val array = root.optJSONArray("modules") ?: return emptyList()
        return (0 until array.length()).mapNotNull { index ->
            val obj = array.optJSONObject(index) ?: return@mapNotNull null
            val depsJson = obj.optJSONArray("dependencies")
            val deps = if (depsJson == null) emptyList() else {
                (0 until depsJson.length()).map { depsJson.optString(it) }.filter { it.isNotBlank() }
            }
            Module(
                id = obj.optString("id"),
                name = obj.optString("name"),
                version = obj.optString("version"),
                description = obj.optString("description"),
                size = obj.optLong("size_compressed", obj.optLong("size")),
                downloadUrl = obj.optString("download_url"),
                checksum = obj.optString("checksum"),
                installPath = obj.optString("install_path"),
                executable = obj.optString("executable"),
                dependencies = deps,
                required = obj.optBoolean("required", false)
            )
        }
    }

    private fun userFriendlyNetworkError(e: Exception): String {
        val message = e.message.orEmpty()
        return when {
            message.contains("Unable to resolve", ignoreCase = true) ||
                message.contains("UnknownHost", ignoreCase = true) ->
                "No internet connection. Connect and try again."
            message.startsWith("HTTP") ->
                "Couldn't reach the module catalog ($message). Try again later."
            else -> "Couldn't load modules. Check your connection and try again."
        }
    }
}
