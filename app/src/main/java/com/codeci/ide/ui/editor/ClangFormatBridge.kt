package com.codeci.ide.ui.editor

import android.content.Context
import android.os.Build
import com.codeci.ide.ui.terminal.ShellEnvironment
import java.io.File
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Phase 9 — optional `clang-format` bridge.
 *
 * When the user has installed the `clang-format` package into the CodeC
 * userland, the editor runs it over the buffer via stdin/stdout (pumped in
 * daemon threads so neither pipe can deadlock; file redirection needs API 26
 * while CodeC's minSdk is 24); otherwise the caller falls back to
 * [CodeFormatter]'s built-in indenter.
 *
 * This only *executes* a user-installed ELF under `$PREFIX/bin` — exactly what
 * `cc` and the package manager already do under the targetSdk-28 exec model.
 * It never writes to `$PREFIX`, never touches `cc`/`bash`, and never adds
 * anything to `PATH` (absolute path is passed directly to [ProcessBuilder]).
 */
object ClangFormatBridge {

    private const val TIMEOUT_SECONDS = 10L
    const val STYLE = "-style=Google"

    /** The installed `clang-format` binary, or null when not available. */
    fun binary(context: Context): File? = runCatching {
        File(ShellEnvironment.binDir(ShellEnvironment.prefixDir(context.filesDir)), "clang-format")
            .takeIf { it.isFile && it.canExecute() }
    }.getOrNull()

    /** True when a userland `clang-format` can be used for the Format action. */
    fun isAvailable(context: Context): Boolean = binary(context) != null

    /** Formatted text, or null when unavailable/failed/timed out. */
    suspend fun format(context: Context, source: String, style: String = STYLE): String? {
        val bin = binary(context) ?: return null
        return withContext(Dispatchers.IO) {
            runCatching { runOnce(bin, context.cacheDir, source, style) }.getOrNull()
        }
    }

    private fun runOnce(bin: File, cacheDir: File, source: String, style: String): String? {
        val dir = cacheDir.resolve("codec-format").apply { mkdirs() }
        val process = runCatching {
            ProcessBuilder(bin.absolutePath, style)
                .directory(dir)
                .start()
        }.getOrNull() ?: return null
        val stdout = java.io.ByteArrayOutputStream()
        val outThread = Thread { runCatching { process.inputStream.use { it.copyTo(stdout) } } }
        val errThread = Thread { runCatching { process.errorStream.use { it.readBytes() } } }
        val inThread = Thread { runCatching { process.outputStream.use { it.write(source.toByteArray()) } } }
        outThread.isDaemon = true
        errThread.isDaemon = true
        inThread.isDaemon = true
        try {
            outThread.start(); errThread.start(); inThread.start()
            val finished = waitFor(process, TIMEOUT_SECONDS)
            if (!finished) {
                terminate(process)
                return null
            }
            inThread.join(2_000)
            outThread.join(2_000)
            errThread.join(2_000)
            if (process.exitValue() != 0) return null
            return String(stdout.toByteArray())
        } catch (_: Exception) {
            runCatching { terminate(process) }
            return null
        }
    }

    private fun waitFor(process: Process, timeoutSeconds: Long): Boolean {
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

    private fun terminate(process: Process) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            process.destroyForcibly()
        } else {
            process.destroy()
        }
    }
}
