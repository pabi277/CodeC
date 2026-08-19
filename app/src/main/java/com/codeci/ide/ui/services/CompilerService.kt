package com.codeci.ide.ui.services

import android.content.Context
import com.codeci.ide.ui.modules.InstalledModulesStore
import com.codeci.ide.ui.modules.ModuleInstaller
import com.codeci.ide.ui.utils.AppLogger
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
    val binaryPath: String? = null
)

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
        private val DIAGNOSTIC_REGEX =
            Regex("""^(.+?):(\d+):(\d+):\s*(fatal error|error|warning|note):\s*(.+)$""")
    }

    private val store = InstalledModulesStore(context)

    fun resolveClang(): File? = ModuleInstaller.compilerBinary(store.getModulesRoot())

    fun getTempDir(): File {
        val dir = File(context.filesDir, "CodeC/temp")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    suspend fun compile(
        code: String,
        settings: CompilerSettings = CompilerSettings("c11", warnings = true, optimization = 0)
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

            val finished = process.waitFor(COMPILE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            if (!finished) {
                process.destroyForcibly()
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
            val errors = parseDiagnostics(stderr.ifBlank { stdout })
            val success = process.exitValue() == 0 && outputBinary.exists()
            if (success) {
                outputBinary.setExecutable(true, false)
            } else {
                sourceFile.delete()
                outputBinary.delete()
            }
            CompilationResult(
                success = success,
                errors = if (success) errors.filter { it.type == ErrorType.WARNING } else {
                    if (errors.none { it.type == ErrorType.ERROR }) {
                        errors + CompilerError(0, 0, combined.ifBlank { "Compilation failed." }, ErrorType.ERROR)
                    } else errors
                },
                output = combined,
                binaryPath = if (success) outputBinary.absolutePath else null
            )
        } catch (e: Exception) {
            AppLogger.e("CompilerService", "Compilation failed", e)
            sourceFile.delete()
            outputBinary.delete()
            CompilationResult(
                success = false,
                errors = listOf(
                    CompilerError(0, 0, e.message ?: "Couldn't start Clang", ErrorType.ERROR)
                ),
                output = e.message.orEmpty()
            )
        }
    }

    fun execute(binaryPath: String): Flow<ExecutionUpdate> = callbackFlow {
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
            process = startToolProcess(listOf(binary.absolutePath), binary.parentFile ?: getTempDir(), clang)

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

            val finished = localProcess.waitFor(EXECUTE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            val duration = System.currentTimeMillis() - start
            if (!finished) {
                localProcess.destroyForcibly()
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
            process?.destroyForcibly()
            readerThread?.interrupt()
        }
    }.flowOn(Dispatchers.IO)

    fun parseDiagnostics(raw: String): List<CompilerError> {
        if (raw.isBlank()) return emptyList()
        return raw.lineSequence().mapNotNull { line ->
            val match = DIAGNOSTIC_REGEX.find(line.trim()) ?: return@mapNotNull null
            val kind = match.groupValues[4].lowercase()
            val type = if (kind.contains("warning") || kind == "note") ErrorType.WARNING else ErrorType.ERROR
            CompilerError(
                line = match.groupValues[2].toIntOrNull() ?: 0,
                column = match.groupValues[3].toIntOrNull() ?: 0,
                message = match.groupValues[5].trim(),
                type = type
            )
        }.toList()
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
