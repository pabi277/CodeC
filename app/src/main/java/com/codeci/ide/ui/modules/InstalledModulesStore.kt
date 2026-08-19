package com.codeci.ide.ui.modules

import android.content.Context
import com.codeci.ide.ui.utils.AppLogger
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import org.json.JSONArray
import org.json.JSONObject

data class InstalledModule(
    val id: String,
    val version: String,
    val installedAt: String,
    val path: String,
    val status: String
)

class InstalledModulesStore(private val context: Context) {

    /**
     * App-private directory so extracted Clang binaries are executable.
     * Shared storage is often mounted noexec on modern Android.
     */
    fun getModulesRoot(): File {
        val dir = File(context.filesDir, "CodeC/modules")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    fun getTempDir(): File {
        val dir = File(getModulesRoot(), "temp")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    fun isCompilerInstalled(): Boolean {
        return ModuleInstaller.compilerBinary(getModulesRoot()) != null
    }

    fun installDir(module: Module): File = File(getModulesRoot(), module.installPath)

    private fun installedFile(): File = File(getModulesRoot(), "installed.json")

    fun readInstalled(): List<InstalledModule> {
        return try {
            val file = installedFile()
            if (!file.exists()) return emptyList()
            val root = JSONObject(file.readText())
            val array = root.optJSONArray("modules") ?: JSONArray()
            (0 until array.length()).mapNotNull { index ->
                val obj = array.optJSONObject(index) ?: return@mapNotNull null
                InstalledModule(
                    id = obj.optString("id"),
                    version = obj.optString("version"),
                    installedAt = obj.optString("installedAt"),
                    path = obj.optString("path"),
                    status = obj.optString("status", "active")
                )
            }
        } catch (e: Exception) {
            AppLogger.e("Modules", "Failed to read installed.json", e)
            emptyList()
        }
    }

    fun markInstalled(module: Module, path: String) {
        try {
            val current = readInstalled().filterNot { it.id == module.id }.toMutableList()
            val today = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
            current.add(
                InstalledModule(
                    id = module.id,
                    version = module.version,
                    installedAt = today,
                    path = path,
                    status = "active"
                )
            )
            writeAll(current)
            File(path, ".installed").writeText(module.version)
        } catch (e: Exception) {
            AppLogger.e("Modules", "Failed to write installed.json", e)
        }
    }

    fun removeInstalled(moduleId: String) {
        writeAll(readInstalled().filterNot { it.id == moduleId })
    }

    private fun writeAll(modules: List<InstalledModule>) {
        val array = JSONArray()
        modules.forEach { module ->
            array.put(
                JSONObject()
                    .put("id", module.id)
                    .put("version", module.version)
                    .put("installedAt", module.installedAt)
                    .put("path", module.path)
                    .put("status", module.status)
            )
        }
        installedFile().writeText(JSONObject().put("modules", array).toString(2))
    }
}
