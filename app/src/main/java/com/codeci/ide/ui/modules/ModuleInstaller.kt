package com.codeci.ide.ui.modules

import android.os.Build
import android.os.StatFs
import android.system.Os
import com.codeci.ide.ui.utils.AppLogger
import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import java.util.zip.ZipInputStream

object ModuleInstaller {

    private val SYMLINK_TEXT_REGEX = Regex("""[A-Za-z0-9_./+-]+""")

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
        flattenSingleRootDirectory(destination)
    }

    /**
     * Some module archives wrap the whole toolchain in a single top-level folder
     * (e.g. `clang-compiler/bin/...`) while the manifest's install_path already
     * names that folder. Without flattening the toolchain ends up doubled
     * (`modules/clang-compiler/clang-compiler/bin/...`) and the declared
     * `bin/...` executable paths don't resolve. Move the contents up.
     */
    fun flattenSingleRootDirectory(dir: File) {
        try {
            val children = dir.listFiles() ?: return
            val dirs = children.filter { it.isDirectory }
            if (dirs.size != 1) return
            val only = dirs.single()
            // A flat archive (bin/, lib/ at the root) must stay untouched.
            if (only.name == "bin" || only.name == "lib" || only.name == "lib64") return
            val looksLikeToolchainRoot = File(only, "bin").exists() ||
                File(only, "lib").exists() || File(only, "lib64").exists()
            if (!looksLikeToolchainRoot) return
            only.listFiles()?.forEach { child ->
                val target = File(dir, child.name)
                if (target.exists()) {
                    if (target.isDirectory) target.deleteRecursively() else target.delete()
                }
                if (!child.renameTo(target)) {
                    if (child.isDirectory) child.copyRecursively(target) else child.copyTo(target, overwrite = true)
                    child.deleteRecursively()
                }
            }
            only.delete()
            AppLogger.i("Installer", "Flattened single-root module layout in ${dir.absolutePath}")
        } catch (e: Exception) {
            AppLogger.e("Installer", "Flatten failed", e)
        }
    }

    /**
     * ZIP extraction cannot preserve symlinks: a symlink entry is stored as a tiny
     * text file whose content is the target path. Termux-built toolchains (clang,
     * llvm-ar, ld.lld, versioned .so files, ...) rely heavily on symlinks, so those
     * placeholders must be converted back into real symlinks or execution fails.
     */
    fun materializeFlattenedSymlinks(installDir: File) {
        if (!installDir.exists()) return
        installDir.walkTopDown().forEach { file ->
            if (!file.isFile) return@forEach
            val target = flattenedSymlinkTarget(file) ?: return@forEach
            replaceFileWithSymlink(file, target)
        }
    }

    /** Returns the target file if [file] looks like a flattened symlink (short path text). */
    fun flattenedSymlinkTarget(file: File): File? {
        if (!file.isFile) return null
        if (file.length() == 0L || file.length() > 4096) return null
        val bytes = runCatching { file.readBytes() }.getOrNull() ?: return null
        if (bytes.size < 4) return null
        val isElf = bytes[0] == 0x7f.toByte() && bytes[1] == 'E'.code.toByte() &&
            bytes[2] == 'L'.code.toByte() && bytes[3] == 'F'.code.toByte()
        val isScript = bytes[0] == '#'.code.toByte() && bytes[1] == '!'.code.toByte()
        if (isElf || isScript) return null
        val text = bytes.decodeToString().trim()
        if (text.isEmpty() || text.contains('\n') || text.length > 200) return null
        if (!SYMLINK_TEXT_REGEX.matches(text)) return null
        val target = File(file.parentFile, text).canonicalFile
        return target.takeIf { it.exists() && it.isFile }
    }

    private fun replaceFileWithSymlink(link: File, target: File) {
        val relative = try {
            target.relativeTo(link.parentFile!!).path
        } catch (e: Exception) {
            target.absolutePath
        }
        try {
            link.delete()
            Os.symlink(relative, link.absolutePath)
            AppLogger.i("Installer", "Restored symlink ${link.name} -> $relative")
            return
        } catch (e: Exception) {
            AppLogger.e("Installer", "Os.symlink failed for ${link.name}", e)
        }
        try {
            link.delete()
            val process = ProcessBuilder("ln", "-sfn", relative, link.absolutePath).start()
            val finished = waitForCompat(process, 5)
            if (!finished) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) process.destroyForcibly() else process.destroy()
            }
            if (finished && process.exitValue() == 0) {
                AppLogger.i("Installer", "Restored symlink (ln) ${link.name} -> $relative")
                return
            }
        } catch (e: Exception) {
            AppLogger.e("Installer", "ln fallback failed for ${link.name}", e)
        }
        // Last resort: copy the target over the placeholder so the binary still runs.
        try {
            link.delete()
            target.copyTo(link, overwrite = true)
            link.setReadable(true, false)
            link.setExecutable(true, false)
            AppLogger.i("Installer", "Copied ${target.name} over flattened symlink ${link.name}")
        } catch (e: Exception) {
            AppLogger.e("Installer", "Symlink restore failed for ${link.name}", e)
        }
    }

    /**
     * Marks every regular file under the module (especially bin/) executable and
     * verifies the result, falling back to `chmod 755` if the Java API silently
     * fails. Returns false if any file could not be made executable.
     */
    fun markBinariesExecutable(installDir: File): Boolean {
        val bin = File(installDir, "bin")
        val roots = listOf(bin, installDir)
        var allOk = true
        roots.filter { it.exists() }.forEach { root ->
            root.walkTopDown().forEach { file ->
                if (file.isFile) {
                    file.setReadable(true, false)
                    val ok = file.setExecutable(true, false) && file.canExecute()
                    if (!ok && !chmodExecutable(file)) {
                        allOk = false
                        AppLogger.e("Installer", "Could not make executable: ${file.absolutePath}")
                    }
                }
            }
        }
        return allOk
    }

    /** Ensures a single file is executable, using `chmod 755` as a fallback. */
    fun chmodExecutable(file: File): Boolean {
        return try {
            file.setExecutable(true, false)
            if (file.canExecute()) return true
            val process = ProcessBuilder("chmod", "755", file.absolutePath).start()
            val finished = waitForCompat(process, 5)
            if (!finished) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) process.destroyForcibly() else process.destroy()
            }
            finished && process.exitValue() == 0 && file.canExecute()
        } catch (e: Exception) {
            AppLogger.e("Installer", "chmod failed for ${file.absolutePath}", e)
            false
        }
    }

    /** Process.waitFor(timeout) is API 26+; poll manually on older devices. */
    private fun waitForCompat(process: Process, timeoutSeconds: Long): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            return process.waitFor(timeoutSeconds, TimeUnit.SECONDS)
        }
        val deadlineNanos = System.nanoTime() + TimeUnit.SECONDS.toNanos(timeoutSeconds)
        while (System.nanoTime() < deadlineNanos) {
            try {
                process.exitValue()
                return true
            } catch (_: IllegalThreadStateException) {
                Thread.sleep(50)
            }
        }
        return false
    }

    /**
     * Applies the symlink + executable-bit repairs to an already-installed
     * toolchain (used at compile time so old installs recover without re-download).
     */
    fun repairToolchain(modulesRoot: File) {
        if (!modulesRoot.exists()) return
        materializeFlattenedSymlinks(modulesRoot)
        markBinariesExecutable(modulesRoot)
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
        candidates += File(modulesRoot, "clang-compiler/bin/compiler-wrapper.sh")
        candidates += File(modulesRoot, "clang/bin/clang")
        candidates += File(modulesRoot, "clang-compiler/bin/clang")
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
