package com.codeci.ide.ui.modules

import android.os.StatFs
import com.codeci.ide.ui.utils.AppLogger
import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest
import java.util.zip.ZipInputStream

object ModuleInstaller {

    fun availableBytes(dir: File): Long {
        return try {
            val target = if (dir.exists()) dir else dir.parentFile ?: dir
            val stats = StatFs(target.absolutePath)
            stats.availableBytes
        } catch (e: Exception) {
            AppLogger.e("Installer", "Could not read free space", e)
            Long.MAX_VALUE
        }
    }

    fun checksumMatches(file: File, expected: String): Boolean {
        return try {
            val wanted = expected.substringAfter(":", expected).trim()
            if (wanted.isEmpty()) return true
            val digest = MessageDigest.getInstance("SHA-256")
            FileInputStream(file).use { input ->
                val buffer = ByteArray(8192)
                var read: Int
                while (input.read(buffer).also { read = it } != -1) {
                    digest.update(buffer, 0, read)
                }
            }
            val actual = digest.digest().joinToString("") { "%02x".format(it) }
            actual.equals(wanted, ignoreCase = true)
        } catch (e: Exception) {
            AppLogger.e("Installer", "Checksum failed", e)
            false
        }
    }

    fun extractZip(zipFile: File, destination: File) {
        if (destination.exists()) {
            destination.deleteRecursively()
        }
        destination.mkdirs()
        val destCanonical = destination.canonicalFile
        ZipInputStream(FileInputStream(zipFile)).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                val outFile = File(destination, entry.name).canonicalFile
                if (!outFile.path.startsWith(destCanonical.path + File.separator) &&
                    outFile.path != destCanonical.path
                ) {
                    throw SecurityException("Unsafe zip entry: ${entry.name}")
                }
                if (entry.isDirectory) {
                    outFile.mkdirs()
                } else {
                    outFile.parentFile?.mkdirs()
                    outFile.outputStream().use { output -> zip.copyTo(output) }
                }
                zip.closeEntry()
                entry = zip.nextEntry
            }
        }
    }

    fun markBinariesExecutable(installDir: File) {
        val bin = File(installDir, "bin")
        val roots = listOf(bin, installDir)
        roots.filter { it.exists() }.forEach { root ->
            root.walkTopDown().forEach { file ->
                if (file.isFile) {
                    file.setReadable(true, false)
                    file.setExecutable(true, false)
                }
            }
        }
    }

    fun compilerBinary(modulesRoot: File, module: Module? = null): File? {
        val candidates = mutableListOf<File>()
        if (module != null) {
            val installDir = File(modulesRoot, module.installPath)
            if (module.executable.isNotBlank()) {
                candidates += File(installDir, module.executable)
            }
            candidates += File(installDir, "bin/clang")
        }
        candidates += File(modulesRoot, "clang/bin/clang")
        candidates += File(modulesRoot, "clang-compiler/bin/clang")
        candidates += File(modulesRoot, "clang-compiler/bin/compiler-wrapper.sh")
        findNamed(modulesRoot, "compiler-wrapper.sh")?.let { candidates += it }
        findNamed(modulesRoot, "clang")?.let { candidates += it }
        return candidates.firstOrNull { it.exists() && it.isFile }
    }

    fun libraryPath(modulesRoot: File): String {
        val libs = modulesRoot.walkTopDown()
            .filter { it.isDirectory && (it.name == "lib" || it.name == "lib64") }
            .map { it.absolutePath }
            .toList()
        return libs.joinToString(":")
    }

    fun toolchainHome(modulesRoot: File, compiler: File): File {
        var dir = compiler.parentFile
        repeat(3) {
            if (dir == null) return modulesRoot
            if (File(dir, "lib").exists() || File(dir, "bin").exists()) return dir
            dir = dir.parentFile
        }
        return compiler.parentFile ?: modulesRoot
    }

    private fun findNamed(root: File, name: String): File? {
        if (!root.exists()) return null
        return root.walkTopDown().firstOrNull { it.isFile && it.name == name }
    }

    fun installedVersion(installDir: File): String? {
        val moduleJson = File(installDir, "module.json")
        if (!moduleJson.exists()) {
            val marker = File(installDir, ".installed")
            return if (marker.exists()) marker.readText().trim().ifBlank { null } else null
        }
        return try {
            org.json.JSONObject(moduleJson.readText()).optString("version").ifBlank { null }
        } catch (e: Exception) {
            AppLogger.e("Installer", "Could not read module.json", e)
            null
        }
    }
}
