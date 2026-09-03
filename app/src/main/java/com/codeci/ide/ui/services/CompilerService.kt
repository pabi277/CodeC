package com.codeci.ide.ui.services

import android.content.Context
import android.os.Build
import com.codeci.ide.ui.modules.InstalledModulesStore
import com.codeci.ide.ui.modules.ModuleInstaller
import com.codeci.ide.ui.utils.AppLogger
import com.codeci.ide.ui.utils.DeviceDiagnostics
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext

data class CompilerSettings(
    val cStandard: String,
    val warnings: Boolean,
    val optimization: Int
)

enum class ErrorType {
    ERROR,
    WARNING
}

data class CompilerError(
    val line: Int,
    val column: Int,
    val message: String,
    val type: ErrorType
)

data class CompilationResult(
    val success: Boolean,
    val errors: List<CompilerError>,
    val output: String,
    val binaryPath: String? = null,
    /** Which compiler produced this result. */
    val engine: CompilerEngine = CompilerEngine.BUNDLED,
    /** Path of the compiled program inside Termux when [engine] is TERMUX. */
    val termuxProgramPath: String? = null,
    /** Short human note about the engine, e.g. why the fallback was used. */
    val engineNote: String? = null
)

enum class CompilerEngine {
    EMBEDDED,
    BUNDLED,
    TERMUX
}

data class ExecutionResult(
    val output: String,
    val exitCode: Int,
    val executionTime: Long,
    val timedOut: Boolean
)

sealed class ExecutionUpdate {
    data class OutputLine(val line: String) : ExecutionUpdate()
    data class Completed(val result: ExecutionResult) : ExecutionUpdate()
}

class CompilerService(private val context: Context) {

    companion object {
        const val COMPILE_TIMEOUT_SECONDS = 30L
        const val EXECUTE_TIMEOUT_SECONDS = 10L

        // Phase 21 (owner, 2026-09-03): the Settings "Compiler Engine" picker
        // was removed — TCC is the default and users install clang from
        // Packages when they need it. These constants stay as the internal
        // fallback chain; [BACKEND_AUTO] is now the only value callers pass.
        const val BACKEND_AUTO = "auto"
        const val BACKEND_EMBEDDED = "embedded"
        const val BACKEND_BUNDLED = "bundled"
        const val BACKEND_TERMUX = "termux"
        private val DIAGNOSTIC_REGEX =
            Regex("""^(.+?):(\d+):(\d+):\s*(fatal error|error|warning|note):\s*(.+)$""")
        // TCC prints "file.c:3: error: ..." (line only, no column).
        private val TCC_DIAGNOSTIC_REGEX =
            Regex("""^(.+?):(\d+):\s*(fatal error|error|warning|note):\s*(.+)$""")

        const val DEVICE_EXEC_BLOCKED =
            "Android is blocking execution of the downloaded compiler. On Android 10+ the " +
                "system refuses to run downloaded binaries from app storage (W^X policy for " +
                "apps targeting API 29+, and some emulators/cloud phones mount app storage as " +
                "non-executable). Fixes: 1) Update CodeC to the latest APK — new builds use the " +
                "API 28 compatibility mode (the same one Termux uses); if it still fails, " +
                "uninstall and reinstall the app once. 2) Or install the full toolchain: " +
                "Packages → Clang / LLVM (or \"pkg install clang\" in the terminal). " +
                "3) Or use Termux (install it from F-Droid/GitHub, run " +
                "\"echo allow-external-apps=true >> ~/.termux/termux.properties && " +
                "termux-reload-settings\", then grant CodeC the 'Run commands in Termux " +
                "environment' permission). See the troubleshooting guide in the repo README."
        const val ARCH_MISMATCH =
            "The compiler can't run on this device's CPU (binary format not supported). CodeC " +
                "ships an ARM64 compiler, so x86 emulators and 32-bit devices can't run it " +
                "directly. On a real ARM64 phone, uninstall the module and download it again. " +
                "Alternatively install clang from Packages — the CodeC repository ships a " +
                "native build for your CPU."
        const val TOOLCHAIN_INCOMPLETE =
            "The compiler's runtime libraries are missing or corrupted. Open Modules, uninstall " +
                "the compiler and download it again, or install clang from Packages " +
                "(\"pkg install clang\")."
        const val TERMUX_CLANG_MISSING =
            "Termux's Clang is not installed. Open Termux and run: pkg update && pkg install clang"
        const val TERMUX_NOT_INSTALLED =
            "Termux is not installed. Install Termux 0.109+ from F-Droid or GitHub " +
                "(https://termux.dev), open it once, then run: pkg update && pkg install clang"
        const val TCC_UNAVAILABLE =
            "The built-in compiler could not start on this device (unsupported CPU ABI or " +
                "corrupted install). Reinstall the app, or pick another Compiler Engine in Settings."
        const val TERMUX_SETUP_REQUIRED =
            "CodeC tried to compile with Termux but Termux rejected the request. Open Termux and " +
                "run: echo \"allow-external-apps=true\" >> ~/.termux/termux.properties && " +
                "termux-reload-settings, then grant CodeC the 'Run commands in Termux " +
                "environment' permission (Android Settings → Apps → CodeC IDE → Permissions)."

        /**
         * Maps low-level toolchain launch failures to a user-facing explanation.
         * Returns null when the output is a normal compiler diagnostic.
         */
        fun detectEnvironmentError(raw: String?): String? {
            if (raw.isNullOrBlank()) return null
            val lower = raw.lowercase()
            return when {
                lower.contains("exec format error") || lower.contains("cannot execute binary file") ->
                    ARCH_MISMATCH
                lower.contains("cannot open shared object file") ||
                    (lower.contains(".so") && lower.contains("not found")) ->
                    TOOLCHAIN_INCOMPLETE
                lower.contains("permission denied") -> DEVICE_EXEC_BLOCKED
                else -> null
            }
        }

        fun parseDiagnostics(raw: String): List<CompilerError> {
            if (raw.isBlank()) return emptyList()
            return raw.lineSequence().mapNotNull { line ->
                val trimmed = line.trim()
                val match = DIAGNOSTIC_REGEX.find(trimmed) ?: TCC_DIAGNOSTIC_REGEX.find(trimmed)
                    ?: return@mapNotNull null
                // Clang: file:line:column: kind: message -> groups 1..5.
                // TCC:   file:line: kind: message       -> groups 1..4.
                val kindIndex = if (match.groupValues.size >= 6) 4 else 3
                val messageIndex = if (match.groupValues.size >= 6) 5 else 4
                val kind = match.groupValues[kindIndex].lowercase()
                val type = if (kind.contains("warning") || kind == "note") ErrorType.WARNING else ErrorType.ERROR
                CompilerError(
                    line = match.groupValues[2].toIntOrNull() ?: 0,
                    column = if (match.groupValues.size >= 6) {
                        match.groupValues[3].toIntOrNull() ?: 0
                    } else {
                        0
                    },
                    message = match.groupValues[messageIndex].trim(),
                    type = type
                )
            }.toList()
        }
    }

    private val store = InstalledModulesStore(context)

    fun resolveClang(): File? = ModuleInstaller.compilerBinary(store.getModulesRoot())

    fun getTempDir(): File {
        val dir = File(context.filesDir, "CodeC/temp")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    /**
     * Compiles [code] with the engine selected by [backend]:
     *  - [BACKEND_EMBEDDED]: the built-in TCC compiler shipped in the APK.
     *  - [BACKEND_BUNDLED]: the Clang downloaded in Modules.
     *  - [BACKEND_TERMUX]:  Termux's Clang.
     *  - [BACKEND_AUTO] (default, and the only value the app passes since
     *    Phase 21 removed the Settings picker): built-in TCC first (works
     *    offline, no download, on any ABI we ship); when it is unavailable,
     *    the bundled Clang; when Android blocks that (W^X policy, noexec
     *    mounts, CPU mismatch or broken toolchain) and Termux is installed,
     *    Termux.
     */
    suspend fun compile(
        code: String,
        settings: CompilerSettings = CompilerSettings("c11", warnings = true, optimization = 0),
        backend: String = BACKEND_AUTO
    ): CompilationResult = withContext(Dispatchers.IO) {
        AppLogger.i("CompilerService", "Device: ${DeviceDiagnostics.summary(store.getModulesRoot())}")
        when (backend) {
            BACKEND_EMBEDDED -> return@withContext compileWithEmbedded(code, settings)
            BACKEND_BUNDLED -> return@withContext compileWithBundled(code, settings)
            BACKEND_TERMUX -> {
                if (!TermuxCompiler.isTermuxInstalled(context)) {
                    return@withContext CompilationResult(
                        success = false,
                        errors = listOf(CompilerError(0, 0, TERMUX_NOT_INSTALLED, ErrorType.ERROR)),
                        output = "Termux is not installed.",
                        engine = CompilerEngine.TERMUX
                    )
                }
                return@withContext compileWithTermux(code, settings)
            }
        }

        // AUTO: embedded -> bundled -> Termux.
        if (EmbeddedCompiler.isAvailable(context)) {
            val embedded = compileWithEmbedded(code, settings)
            if (embedded.success) return@withContext embedded
            // Only fall through when the built-in compiler itself failed to
            // run (never on ordinary compile errors).
            if (embedded.errors.any { it.message == TCC_UNAVAILABLE }) {
                // fall through to bundled below
            } else {
                return@withContext embedded
            }
        }

        val bundled = compileWithBundled(code, settings)
        if (bundled.success) return@withContext bundled

        val envFailure = isEnvironmentFailure(bundled)
        if (!envFailure) return@withContext bundled
        if (!TermuxCompiler.isTermuxInstalled(context)) return@withContext bundled

        val termux = compileWithTermux(code, settings)
        if (termux.success) return@withContext termux

        // Auto mode: both engines failed. Surface the bundled failure and the
        // Termux error so the user can fix the Termux setup (permission /
        // allow-external-apps / clang missing).
        bundled.copy(errors = bundled.errors + termux.errors)
    }

    /**
     * Compiles [code] with the built-in TCC (static musl) shipped in the APK.
     * Output is a fully static executable in the app temp dir.
     */
    private suspend fun compileWithEmbedded(
        code: String,
        settings: CompilerSettings
    ): CompilationResult = withContext(Dispatchers.IO) {
        if (!EmbeddedCompiler.ensureExtracted(context)) {
            return@withContext CompilationResult(
                success = false,
                errors = listOf(CompilerError(0, 0, TCC_UNAVAILABLE, ErrorType.ERROR)),
                output = "Built-in TCC is not available on this device (ABI: " +
                    DeviceDiagnostics.abiSummary() + ").",
                engine = CompilerEngine.EMBEDDED
            )
        }
        val tcc = EmbeddedCompiler.tccBinary(context)
        if (tcc == null) {
            return@withContext CompilationResult(
                success = false,
                errors = listOf(CompilerError(0, 0, TCC_UNAVAILABLE, ErrorType.ERROR)),
                output = "Built-in TCC binary missing.",
                engine = CompilerEngine.EMBEDDED
            )
        }
        val tempDir = getTempDir()
        val stamp = System.currentTimeMillis()
        val sourceFile = File(tempDir, "source_$stamp.c")
        val outputBinary = File(tempDir, "program_$stamp")
        try {
            sourceFile.writeText(code)
            val command = listOf(tcc.absolutePath) + EmbeddedCompiler.buildCompileCommand(
                settings.cStandard, settings.warnings, settings.optimization, sourceFile, outputBinary
            )
            AppLogger.i("CompilerService", "Compile (TCC): ${command.joinToString(" ")}")
            val process = ProcessBuilder(command)
                .directory(EmbeddedCompiler.bundleDir(context))
                .redirectErrorStream(false)
                .start()
            val stdoutReader = ThreadedReader(process.inputStream.bufferedReader())
            val stderrReader = ThreadedReader(process.errorStream.bufferedReader())
            stdoutReader.start()
            stderrReader.start()

            val finished = waitForProcess(process, COMPILE_TIMEOUT_SECONDS)
            if (!finished) {
                terminateProcess(process)
                stdoutReader.joinQuietly()
                stderrReader.joinQuietly()
                sourceFile.delete()
                return@withContext CompilationResult(
                    success = false,
                    errors = listOf(
                        CompilerError(0, 0, "Compilation timed out after ${COMPILE_TIMEOUT_SECONDS}s", ErrorType.ERROR)
                    ),
                    output = stdoutReader.text,
                    engine = CompilerEngine.EMBEDDED
                )
            }

            stdoutReader.joinQuietly()
            stderrReader.joinQuietly()
            val stderr = stderrReader.text
            val stdout = stdoutReader.text
            val combined = listOf(stdout, stderr).filter { it.isNotBlank() }.joinToString("\n")
            val success = process.exitValue() == 0 && outputBinary.exists()
            if (success) {
                outputBinary.setExecutable(true, false)
            } else {
                sourceFile.delete()
                outputBinary.delete()
            }
            val parsed = parseDiagnostics(stderr.ifBlank { stdout })
            val errors = when {
                success -> parsed.filter { it.type == ErrorType.WARNING }
                parsed.none { it.type == ErrorType.ERROR } ->
                    parsed + CompilerError(0, 0, combined.ifBlank { "Compilation failed." }, ErrorType.ERROR)
                else -> parsed
            }
            CompilationResult(
                success = success,
                errors = errors,
                output = combined,
                binaryPath = if (success) outputBinary.absolutePath else null,
                engine = CompilerEngine.EMBEDDED,
                engineNote = if (success) "Compiled with the built-in TCC compiler" else null
            )
        } catch (e: Exception) {
            AppLogger.e("CompilerService", "TCC compile failed", e)
            sourceFile.delete()
            outputBinary.delete()
            val message = detectEnvironmentError(e.message)
                ?: (e.message ?: "Couldn't start the built-in compiler")
            CompilationResult(
                success = false,
                errors = listOf(CompilerError(0, 0, message, ErrorType.ERROR)),
                output = e.message.orEmpty(),
                engine = CompilerEngine.EMBEDDED
            )
        }
    }

    private fun isEnvironmentFailure(result: CompilationResult): Boolean {
        return result.errors.any {
            it.message == DEVICE_EXEC_BLOCKED ||
                it.message == ARCH_MISMATCH ||
                it.message == TOOLCHAIN_INCOMPLETE ||
                it.message == "Compiler not installed"
        }
    }

    private suspend fun compileWithBundled(
        code: String,
        settings: CompilerSettings
    ): CompilationResult = withContext(Dispatchers.IO) {
        val clang = resolveClang()
        if (clang == null) {
            return@withContext CompilationResult(
                success = false,
                errors = listOf(
                    CompilerError(0, 0, "Compiler not installed", ErrorType.ERROR)
                ),
                output = "Compiler not installed. Open Modules and download Clang."
            )
        }

        clang.setExecutable(true, false)
        // The binary that must actually run: the compiler itself, or (when the
        // manifest declares a wrapper script) the clang it invokes next to it.
        val targetBinary = if (clang.name.endsWith(".sh")) {
            clang.parentFile?.let { File(it, "clang") } ?: clang
        } else clang
        var needsRepair = !targetBinary.exists() || !targetBinary.canExecute() ||
            ModuleInstaller.flattenedSymlinkTarget(targetBinary) != null
        if (needsRepair) {
            // Recover old installs without a re-download: restore flattened symlinks
            // and re-apply executable bits (with chmod fallback).
            ModuleInstaller.repairToolchain(store.getModulesRoot())
            clang.setExecutable(true, false)
            needsRepair = !targetBinary.canExecute() && !ModuleInstaller.chmodExecutable(targetBinary)
        }
        if (needsRepair) {
            return@withContext CompilationResult(
                success = false,
                errors = listOf(
                    CompilerError(0, 0, DEVICE_EXEC_BLOCKED, ErrorType.ERROR)
                ),
                output = "Compiler binary is not executable: ${targetBinary.absolutePath}"
            )
        }
        val tempDir = getTempDir()
        val stamp = System.currentTimeMillis()
        val sourceFile = File(tempDir, "source_$stamp.c")
        val outputBinary = File(tempDir, "program_$stamp")
        try {
            sourceFile.writeText(code)
            val command = buildCompileCommand(clang, settings, sourceFile, outputBinary)
            AppLogger.i("CompilerService", "Compile: ${command.joinToString(" ")}")

            val process = startToolProcess(command, tempDir, clang, mergeStreams = false)
            val stdoutReader = ThreadedReader(process.inputStream.bufferedReader())
            val stderrReader = ThreadedReader(process.errorStream.bufferedReader())
            stdoutReader.start()
            stderrReader.start()

            val finished = waitForProcess(process, COMPILE_TIMEOUT_SECONDS)
            if (!finished) {
                terminateProcess(process)
                stdoutReader.joinQuietly()
                stderrReader.joinQuietly()
                sourceFile.delete()
                return@withContext CompilationResult(
                    success = false,
                    errors = listOf(
                        CompilerError(0, 0, "Compilation timed out after ${COMPILE_TIMEOUT_SECONDS}s", ErrorType.ERROR)
                    ),
                    output = stdoutReader.text
                )
            }

            stdoutReader.joinQuietly()
            stderrReader.joinQuietly()
            val stderr = stderrReader.text
            val stdout = stdoutReader.text
            val combined = listOf(stdout, stderr).filter { it.isNotBlank() }.joinToString("\n")
            val success = process.exitValue() == 0 && outputBinary.exists()
            if (success) {
                outputBinary.setExecutable(true, false)
            } else {
                sourceFile.delete()
                outputBinary.delete()
            }
            val parsedErrors = parseDiagnostics(stderr.ifBlank { stdout })
            val environmentError = if (!success) detectEnvironmentError(combined) else null
            val errors = when {
                environmentError != null ->
                    listOf(CompilerError(0, 0, environmentError, ErrorType.ERROR))
                success -> parsedErrors.filter { it.type == ErrorType.WARNING }
                else -> {
                    if (parsedErrors.none { it.type == ErrorType.ERROR }) {
                        parsedErrors + CompilerError(
                            0, 0, combined.ifBlank { "Compilation failed." }, ErrorType.ERROR
                        )
                    } else parsedErrors
                }
            }
            CompilationResult(
                success = success,
                errors = errors,
                output = combined,
                binaryPath = if (success) outputBinary.absolutePath else null
            )
        } catch (e: Exception) {
            AppLogger.e("CompilerService", "Compilation failed", e)
            sourceFile.delete()
            outputBinary.delete()
            val message = detectEnvironmentError(e.message)
                ?: (e.message ?: "Couldn't start Clang")
            CompilationResult(
                success = false,
                errors = listOf(
                    CompilerError(0, 0, message, ErrorType.ERROR)
                ),
                output = e.message.orEmpty()
            )
        }
    }

    /**
     * Compiles [code] using Termux's Clang through the RUN_COMMAND intent.
     * The source is piped via stdin into Termux's home; the compiled program
     * is left at [TermuxCompiler.absoluteProgramPath] and later executed by
     * [execute].
     */
    private suspend fun compileWithTermux(
        code: String,
        settings: CompilerSettings
    ): CompilationResult = withContext(Dispatchers.IO) {
        // Embed the source in the script (base64) so no stdin support is
        // needed; fall back to stdin only for very large sources that would
        // blow the intent argument limit.
        val sourceBase64 = android.util.Base64.encodeToString(
            code.toByteArray(Charsets.UTF_8), android.util.Base64.NO_WRAP
        )
        val useStdin = sourceBase64.length > 100_000
        val script = TermuxCompiler.buildCompileScript(
            settings.cStandard,
            settings.warnings,
            settings.optimization,
            sourceBase64 = if (useStdin) null else sourceBase64
        )
        AppLogger.i("CompilerService", "Compile (Termux): $script")
        val result = TermuxCompiler.runCommand(
            context = context,
            arguments = listOf("-c", script),
            stdin = if (useStdin) code else null,
            label = "CodeC compile",
            timeoutSeconds = COMPILE_TIMEOUT_SECONDS
        )
        val combined = listOf(result.stdout, result.stderr)
            .filter { it.isNotBlank() }
            .joinToString("\n")

        val internalFailure = result.internalFailure
        if (internalFailure != null) {
            val message = when {
                result.timedOut ->
                    "Compilation timed out after ${COMPILE_TIMEOUT_SECONDS}s (Termux)."
                internalFailure.contains("allow-external-apps") ||
                    internalFailure.contains("denied the request") -> TERMUX_SETUP_REQUIRED
                else -> internalFailure
            }
            return@withContext CompilationResult(
                success = false,
                errors = listOf(CompilerError(0, 0, message, ErrorType.ERROR)),
                output = combined.ifBlank { message },
                engine = CompilerEngine.TERMUX,
                engineNote = message
            )
        }

        if (result.exitCode == 0) {
            return@withContext CompilationResult(
                success = true,
                errors = parseDiagnostics(result.stderr.ifBlank { result.stdout })
                    .filter { it.type == ErrorType.WARNING },
                output = combined,
                engine = CompilerEngine.TERMUX,
                termuxProgramPath = TermuxCompiler.absoluteProgramPath(),
                engineNote = "Compiled with Termux's Clang"
            )
        }

        val parsed = parseDiagnostics(result.stderr.ifBlank { result.stdout })
        val errors = if (parsed.none { it.type == ErrorType.ERROR }) {
            val message = when {
                combined.contains("command not found") -> TERMUX_CLANG_MISSING
                combined.isBlank() ->
                    "Compilation failed in Termux (exit code ${result.exitCode})."
                else -> combined
            }
            listOf(CompilerError(0, 0, message, ErrorType.ERROR))
        } else parsed
        CompilationResult(
            success = false,
            errors = errors,
            output = combined,
            engine = CompilerEngine.TERMUX
        )
    }

    fun execute(binaryPath: String): Flow<ExecutionUpdate> = execute(
        CompilationResult(
            success = true,
            errors = emptyList(),
            output = "",
            binaryPath = binaryPath,
            engine = CompilerEngine.BUNDLED
        )
    )

    /**
     * Runs a compiled program. For [CompilerEngine.TERMUX] results the
     * program lives inside Termux and is executed there via RUN_COMMAND;
     * output is returned in the result bundle, so it appears when the
     * program finishes.
     */
    fun execute(result: CompilationResult): Flow<ExecutionUpdate> = callbackFlow {
        if (result.engine == CompilerEngine.TERMUX) {
            val start = System.currentTimeMillis()
            val termuxResult = TermuxCompiler.runCommand(
                context = context,
                arguments = listOf("-c", TermuxCompiler.buildRunScript()),
                label = "CodeC run",
                timeoutSeconds = EXECUTE_TIMEOUT_SECONDS
            )
            val duration = System.currentTimeMillis() - start
            if (termuxResult.timedOut) {
                trySend(ExecutionUpdate.OutputLine("Program exceeded time limit (possible infinite loop)"))
                trySend(
                    ExecutionUpdate.Completed(
                        ExecutionResult(
                            output = "",
                            exitCode = 124,
                            executionTime = duration,
                            timedOut = true
                        )
                    )
                )
            } else {
                val internalFailure = termuxResult.internalFailure
                if (internalFailure != null) {
                    trySend(ExecutionUpdate.OutputLine(internalFailure))
                    trySend(
                        ExecutionUpdate.Completed(
                            ExecutionResult(
                                output = internalFailure + "\n",
                                exitCode = 1,
                                executionTime = duration,
                                timedOut = false
                            )
                        )
                    )
                } else {
                    termuxResult.stdout.trimEnd('\n').lineSequence()
                        .filter { it.isNotEmpty() }
                        .forEach { trySend(ExecutionUpdate.OutputLine(it)) }
                    trySend(
                        ExecutionUpdate.Completed(
                            ExecutionResult(
                                output = termuxResult.stdout,
                                exitCode = termuxResult.exitCode ?: 1,
                                executionTime = duration,
                                timedOut = false
                            )
                        )
                    )
                }
            }
            close()
            return@callbackFlow
        }

        val binaryPath = result.binaryPath
        if (binaryPath == null) {
            trySend(
                ExecutionUpdate.Completed(
                    ExecutionResult("No compiled program to run.", 1, 0, timedOut = false)
                )
            )
            close()
            return@callbackFlow
        }
        val binary = File(binaryPath)
        if (!binary.exists()) {
            trySend(
                ExecutionUpdate.Completed(
                    ExecutionResult("No compiled program to run.", 1, 0, timedOut = false)
                )
            )
            close()
            return@callbackFlow
        }

        val start = System.currentTimeMillis()
        val outputBuilder = StringBuilder()
        var process: Process? = null
        var readerThread: Thread? = null
        try {
            binary.setExecutable(true, false)
            val clang = resolveClang()
            process = startToolProcess(
                listOf(binary.absolutePath),
                binary.parentFile ?: getTempDir(),
                clang,
                mergeStreams = true
            )

            val localProcess = process
            readerThread = Thread {
                try {
                    BufferedReader(InputStreamReader(localProcess.inputStream)).use { reader ->
                        var line: String?
                        while (reader.readLine().also { line = it } != null) {
                            val text = line ?: continue
                            outputBuilder.append(text).append('\n')
                            trySend(ExecutionUpdate.OutputLine(text))
                        }
                    }
                } catch (_: Exception) {
                }
            }.also { it.start() }

            val finished = waitForProcess(localProcess, EXECUTE_TIMEOUT_SECONDS)
            val duration = System.currentTimeMillis() - start
            if (!finished) {
                terminateProcess(localProcess)
                readerThread.join(500)
                val message = "Program exceeded time limit (possible infinite loop)"
                trySend(ExecutionUpdate.OutputLine(message))
                trySend(
                    ExecutionUpdate.Completed(
                        ExecutionResult(
                            output = outputBuilder.toString() + message + "\n",
                            exitCode = 124,
                            executionTime = duration,
                            timedOut = true
                        )
                    )
                )
            } else {
                readerThread.join(1_000)
                trySend(
                    ExecutionUpdate.Completed(
                        ExecutionResult(
                            output = outputBuilder.toString(),
                            exitCode = localProcess.exitValue(),
                            executionTime = duration,
                            timedOut = false
                        )
                    )
                )
            }
        } catch (e: Exception) {
            AppLogger.e("CompilerService", "Execution failed", e)
            trySend(
                ExecutionUpdate.Completed(
                    ExecutionResult(
                        output = "Couldn't run program: ${e.message}",
                        exitCode = 1,
                        executionTime = System.currentTimeMillis() - start,
                        timedOut = false
                    )
                )
            )
        } finally {
            cleanupArtifacts(binary)
        }
        close()
        awaitClose {
            process?.let(::terminateProcess)
            readerThread?.interrupt()
        }
    }.flowOn(Dispatchers.IO)

    private fun waitForProcess(process: Process, timeoutSeconds: Long): Boolean {
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

    private fun terminateProcess(process: Process) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            process.destroyForcibly()
        } else {
            process.destroy()
        }
    }

    private fun startToolProcess(
        command: List<String>,
        workDir: File,
        clang: File?,
        mergeStreams: Boolean
    ): Process {
        val builder = ProcessBuilder(command)
            .directory(workDir)
            .redirectErrorStream(mergeStreams)
        val env = builder.environment()
        val root = store.getModulesRoot()
        val home = if (clang != null) ModuleInstaller.toolchainHome(root, clang) else root
        val libs = ModuleInstaller.libraryPath(root)
        if (libs.isNotBlank()) {
            val existing = env["LD_LIBRARY_PATH"].orEmpty()
            env["LD_LIBRARY_PATH"] = if (existing.isBlank()) libs else "$libs:$existing"
        }
        env["PREFIX"] = home.absolutePath
        env["HOME"] = context.filesDir.absolutePath
        env["TMPDIR"] = getTempDir().absolutePath
        env["PATH"] = listOfNotNull(File(home, "bin").takeIf { it.exists() }?.absolutePath, env["PATH"])
            .joinToString(":")
        return builder.start()
    }

    private fun buildCompileCommand(
        clang: File,
        settings: CompilerSettings,
        sourceFile: File,
        outputBinary: File
    ): List<String> {
        val standard = settings.cStandard.lowercase().removePrefix("c").let { "c$it" }
        val optimization = settings.optimization.coerceIn(0, 3)
        val command = mutableListOf<String>()
        if (clang.name.endsWith(".sh")) {
            command += listOf("sh", clang.absolutePath)
        } else {
            command += clang.absolutePath
        }
        command += "-std=$standard"
        if (settings.warnings) {
            command += "-Wall"
        }
        command += "-O$optimization"
        command += sourceFile.absolutePath
        command += "-o"
        command += outputBinary.absolutePath
        return command
    }

    private fun cleanupArtifacts(binary: File) {
        try {
            val stamp = binary.name.removePrefix("program_")
            binary.delete()
            File(binary.parentFile, "source_$stamp.c").delete()
        } catch (e: Exception) {
            AppLogger.e("CompilerService", "Cleanup failed", e)
        }
    }

    private class ThreadedReader(private val reader: BufferedReader) : Thread() {
        private val builder = StringBuilder()
        val text: String get() = builder.toString()

        override fun run() {
            try {
                reader.useLines { lines ->
                    lines.forEach { line ->
                        if (builder.isNotEmpty()) builder.append('\n')
                        builder.append(line)
                    }
                }
            } catch (_: Exception) {
            }
        }

        fun joinQuietly() {
            try {
                join(2_000)
            } catch (_: InterruptedException) {
            }
        }
    }
}
