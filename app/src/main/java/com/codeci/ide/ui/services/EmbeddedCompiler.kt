package com.codeci.ide.ui.services

import android.content.Context
import android.os.Build
import com.codeci.ide.ui.modules.ModuleInstaller
import com.codeci.ide.ui.utils.AppLogger
import java.io.File

/**
 * Built-in TCC (Tiny C Compiler) — the "Coding C"-style offline compiler.
 *
 * A static-musl TCC binary is shipped per ABI in jniLibs (extracted by the OS
 * into `nativeLibraryDir`, where exec is always allowed) and the compile-time
 * support files (musl headers, libc.a, crt objects, libtcc1.a) ship in
 * `assets/tcc/<abi>/` and are extracted to app storage on first use.
 *
 * TCC is built with `--crtprefix=. --libpaths=. --sysincludepaths=.`, so all
 * crt/library lookups are relative to the *working directory*; the app runs
 * tcc with the bundle directory as its working directory and passes
 * `-I include-tcc -I include -L .`. Compiled programs are fully static musl
 * ELF executables, so they run on any device without bionic dependencies.
 *
 * TCC covers ANSI C and most of C99 (plus a subset of C11). Advanced code can
 * use the Clang module engine instead.
 */
object EmbeddedCompiler {

    const val BUNDLE_VERSION = "3"
    const val TCC_LIB_NAME = "libtcc.so"
    private const val ASSET_ROOT = "tcc"

    /** Android ABI -> assets/tcc/ sub-directory. */
    val ABI_DIRS = listOf("arm64-v8a", "x86_64")

    /** The asset sub-directory for this device's ABI, or null if unsupported. */
    fun abiDir(): String? {
        for (abi in Build.SUPPORTED_ABIS) {
            if (ABI_DIRS.contains(abi)) return abi
        }
        return null
    }

    /** The extracted tcc binary (nativeLibraryDir), or null when not packaged. */
    fun tccBinary(context: Context): File? {
        if (abiDir() == null) return null
        val binary = File(context.applicationInfo.nativeLibraryDir, TCC_LIB_NAME)
        return binary.takeIf { it.exists() && it.isFile }
    }

    fun bundleDir(context: Context): File = File(context.filesDir, "CodeC/tcc")

    /** GNU/SysV ar magic. TCC rejects anything else as "unrecognized file type". */
    fun isUnixArchive(file: File): Boolean {
        if (!file.isFile) return false
        return try {
            file.inputStream().use { ins ->
                val magic = ByteArray(8)
                if (ins.read(magic) != 8) return false
                magic.contentEquals("!<arch>\n".toByteArray(Charsets.ISO_8859_1))
            }
        } catch (_: Exception) {
            false
        }
    }

    fun bundleArchivesValid(dir: File): Boolean =
        isUnixArchive(File(dir, "libtcc1.a")) && isUnixArchive(File(dir, "libc.a"))

    /**
     * True when the tcc binary is packaged for this device's ABI. The support
     * files are extracted lazily by [ensureExtracted] on first compile.
     */
    fun isAvailable(context: Context): Boolean = tccBinary(context) != null

    /**
     * Extracts the per-ABI support files (headers, libc.a, crt objects) from
     * assets into the bundle dir, then marks the binary executable. Returns
     * false if the ABI is unsupported or extraction fails.
     */
    fun ensureExtracted(context: Context): Boolean {
        val abi = abiDir() ?: return false
        val binary = tccBinary(context) ?: return false
        val dir = bundleDir(context)
        val marker = File(dir, ".bundle-v$BUNDLE_VERSION")
        if (marker.exists() && bundleArchivesValid(dir)) {
            binary.setExecutable(true, false)
            compileStdioObject(binary, dir)
            return true
        }
        return try {
            if (dir.exists()) dir.deleteRecursively()
            dir.mkdirs()
            extractAssetDir(context, "$ASSET_ROOT/$abi", dir)
            val required = listOf(
                "include/stdio.h", "include-tcc/stdarg.h",
                "libc.a", "crt1.o", "crti.o", "crtn.o", "libtcc1.a", "codec_stdio.c"
            )
            val missing = required.filter { !File(dir, it).exists() }
            if (missing.isNotEmpty()) {
                AppLogger.e("TCC", "Bundle incomplete, missing: $missing")
                return false
            }
            if (!bundleArchivesValid(dir)) {
                AppLogger.e("TCC", "Bundle archives are not Unix ar files")
                return false
            }
            compileStdioObject(binary, dir)
            marker.writeText(BUNDLE_VERSION)
            binary.setExecutable(true, false)
            if (!binary.canExecute()) {
                // Native library dir files are extracted executable by the OS,
                // but some ROMs strip the bit; chmod as a fallback.
                ModuleInstaller.chmodExecutable(binary)
            }
            AppLogger.i("TCC", "Extracted TCC bundle ($abi) to ${dir.absolutePath}")
            true
        } catch (e: Exception) {
            AppLogger.e("TCC", "Bundle extraction failed", e)
            false
        }
    }

    /**
     * Compiles [codec_stdio.c] to [codec_stdio.o] in the bundle. That object
     * makes stdout unbuffered so textbook printf/scanf shows the prompt first.
     */
    fun compileStdioObject(tcc: File, dir: File): Boolean {
        val src = File(dir, "codec_stdio.c")
        val obj = File(dir, "codec_stdio.o")
        if (!src.isFile) return false
        if (obj.isFile && obj.length() > 0L && obj.lastModified() >= src.lastModified()) {
            return true
        }
        return try {
            val process = ProcessBuilder(
                tcc.absolutePath,
                "-c",
                "-I", "include-tcc",
                "-I", "include",
                "-o", obj.absolutePath,
                src.absolutePath
            ).directory(dir).redirectErrorStream(true).start()
            val ok = process.waitFor() == 0 && obj.isFile && obj.length() > 0L
            if (!ok) {
                AppLogger.e("TCC", "codec_stdio.o compile failed")
            }
            ok
        } catch (e: Exception) {
            AppLogger.e("TCC", "codec_stdio.o compile failed", e)
            false
        }
    }

    /** Recursively copies an assets directory tree to [target]. */
    private fun extractAssetDir(context: Context, assetPath: String, target: File) {
        val names = context.assets.list(assetPath) ?: return
        names.forEach { name ->
            val childAsset = "$assetPath/$name"
            val childTarget = File(target, name)
            val children = context.assets.list(childAsset)
            if (children != null && children.isNotEmpty()) {
                childTarget.mkdirs()
                extractAssetDir(context, childAsset, childTarget)
            } else {
                childTarget.parentFile?.mkdirs()
                context.assets.open(childAsset).use { input ->
                    childTarget.outputStream().use { output -> input.copyTo(output) }
                }
            }
        }
    }

    /**
     * Builds the tcc argument list. Run with the bundle directory as the
     * working directory: `-I include-tcc -I include -L .` resolve there, and
     * tcc's baked crt/lib paths are "." too. Pure function for testability.
     */
    fun buildCompileCommand(
        standard: String,
        warnings: Boolean,
        optimization: Int,
        sourceFile: File,
        outputFile: File
    ): List<String> {
        val std = standard.lowercase().removePrefix("c").let { "c$it" }
        val opt = optimization.coerceIn(0, 3)
        val args = mutableListOf("-nostdlib", "-static", "-std=$std", "-O$opt")
        if (warnings) {
            args += "-Wall"
            args += "-Wextra"
        }
        args += "-I"
        args += "include-tcc"
        args += "-I"
        args += "include"
        args += "-B"
        args += "."
        args += "-L"
        args += "."
        // Full musl link line. -nostdlib stops TCC appending libtcc1.a *after*
        // libc (its linker does not rescan archives, which left memmove
        // undefined). Order: crt startup, source, compiler runtime, libc, crt end.
        args += "crt1.o"
        args += "crti.o"
        args += "codec_stdio.o"
        args += sourceFile.absolutePath
        // TCC has no --start-group. libc (printf) needs libtcc1's *tf*
        // helpers; libtcc1 needs memmove from libc. Scan each archive twice.
        args += "libtcc1.a"
        args += "libc.a"
        args += "libtcc1.a"
        args += "libc.a"
        args += "crtn.o"
        args += "-o"
        args += outputFile.absolutePath
        return args
    }
}
