package com.codeci.ide.ui.editor.lsp

import com.codeci.ide.ui.terminal.ShellEnvironment
import java.io.File

/**
 * Phase 31.5/31.6 — production [ProviderFactory].
 *
 * Resolves each catalog command against `$PREFIX/bin` and starts the
 * process with the same PATH / LD_LIBRARY_PATH / LD_PRELOAD the
 * terminal uses. Without that env, `clangd` is an ELF that still needs
 * `libLLVM`, and `pylsp` / `typescript-language-server` are shebang
 * scripts the kernel will not exec (ENOEXEC) unless termux-exec is
 * preloaded.
 */
class StdioLspProviderFactory(
    private val projectRoot: String,
    private val filesDir: File,
    private val requestTimeoutMs: Long = LspManager.DEFAULT_REQUEST_TIMEOUT_MS,
) : ProviderFactory {

    private val prefixDir: File = ShellEnvironment.prefixDir(filesDir)

    override fun create(config: LspServerConfig): Provider {
        val binDir = File(prefixDir, "bin")
        val head = config.command.first()
        val resolved = File(binDir, head)
        val command = if (resolved.isFile) {
            listOf(resolved.absolutePath) + config.command.drop(1)
        } else {
            config.command
        }
        return LspStdioClient(
            config.copy(command = command),
            projectRoot = projectRoot,
            requestTimeoutMs = requestTimeoutMs,
            processBuilderFactory = { cmd, root -> spawn(cmd, root) },
        )
    }

    private fun spawn(command: List<String>, root: String): Process {
        val work = File(root).apply { mkdirs() }
        return ProcessBuilder(command)
            .directory(work)
            .apply {
                val env = environment()
                env["PREFIX"] = prefixDir.absolutePath
                env["HOME"] = ShellEnvironment.homeDir(filesDir).absolutePath
                val tmp = File(prefixDir, "tmp")
                tmp.mkdirs()
                env["TMPDIR"] = tmp.absolutePath
                env["PATH"] = File(prefixDir, "bin").absolutePath + ":/system/bin:/system/xbin"
                env["LD_LIBRARY_PATH"] = File(prefixDir, "lib").absolutePath
                env["LANG"] = "C.UTF-8"
                env["LC_ALL"] = "C.UTF-8"
                ShellEnvironment.termuxExecPreload(prefixDir)?.let {
                    env["LD_PRELOAD"] = it.absolutePath
                }
            }
            .start()
    }
}
