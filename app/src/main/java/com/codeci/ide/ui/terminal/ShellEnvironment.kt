package com.codeci.ide.ui.terminal

import android.content.Context
import com.codeci.ide.ui.services.CompilerSettings
import com.codeci.ide.ui.services.EmbeddedCompiler
import com.codeci.ide.ui.utils.AppLogger
import com.codeci.ide.ui.utils.FileManager
import java.io.File

/**
 * Phase-1 Mini-Termux userland that lives under the app-private prefix
 * (`/data/data/com.codeci.ide/files/usr`).
 *
 * Writes the `cc` frontend (wired to the embedded TCC), a `pkg` placeholder
 * and a `bash` shim so the terminal can `exec bash` before Phase 2 ships a
 * real bootstrap. Script bodies are pure strings so they are unit-tested.
 */
object ShellEnvironment {
    const val BOOTSTRAP_VERSION = "3"
    const val PREFIX_NAME = "usr"
    const val HOME_NAME = "home"

    fun prefixDir(filesDir: File): File = File(filesDir, PREFIX_NAME)
    fun homeDir(filesDir: File): File = File(filesDir, HOME_NAME)

    fun binDir(prefix: File): File = File(prefix, "bin")
    fun etcDir(prefix: File): File = File(prefix, "etc")
    fun tmpDir(prefix: File): File = File(prefix, "tmp")

    fun ccScript(): String = """
        #!/system/bin/sh
        # CodeC cc — frontend for the embedded TCC compiler (Phase 1).
        if [ ! -x "${'$'}{TCC_BIN:-}" ]; then
          echo "cc: built-in TCC is not available on this device." >&2
          echo "cc: reinstall the app, or pick another Compiler Engine in Settings." >&2
          exit 127
        fi
        if [ ! -d "${'$'}{TCC_BUNDLE:-}" ]; then
          echo "cc: TCC support files missing. Open the editor and tap RUN once to extract them." >&2
          exit 127
        fi
        CWD=${'$'}(pwd)
        converted=""
        out_next=0
        for arg in "${'$'}@"; do
          if [ "${'$'}out_next" = 1 ]; then
            case "${'$'}arg" in
              /*) ;;
              *) arg="${'$'}CWD/${'$'}arg" ;;
            esac
            out_next=0
          else
            case "${'$'}arg" in
              -o) out_next=1 ;;
              -*) ;;
              *)
                case "${'$'}arg" in
                  /*) ;;
                  *)
                    if [ -e "${'$'}CWD/${'$'}arg" ]; then
                      arg="${'$'}CWD/${'$'}arg"
                    elif [ -n "${'$'}CODEC_PROJECTS" ] && [ -e "${'$'}CODEC_PROJECTS/${'$'}arg" ]; then
                      arg="${'$'}CODEC_PROJECTS/${'$'}arg"
                    else
                      arg="${'$'}CWD/${'$'}arg"
                    fi
                    if [ ! -e "${'$'}arg" ]; then
                      echo "cc: not found: ${'$'}arg" >&2
                      echo "cc: save the file in the Editor, then:" >&2
                      echo "    cd \"${'$'}CODEC_PROJECTS\"" >&2
                      echo "    ls" >&2
                      echo "    cc main.c -o a.out" >&2
                      echo "    ./a.out" >&2
                      exit 1
                    fi
                    ;;
                esac
                ;;
            esac
          fi
          converted="${'$'}converted ${'$'}arg"
        done
        cd "${'$'}TCC_BUNDLE" || exit 1
        extra="-static"
        [ -n "${'$'}CC_STD" ] && extra="${'$'}extra -std=${'$'}CC_STD"
        [ -n "${'$'}CC_WARN" ] && extra="${'$'}extra ${'$'}CC_WARN"
        [ -n "${'$'}CC_OPT" ] && extra="${'$'}extra ${'$'}CC_OPT"
        extra="${'$'}extra -I include-tcc -I include -L ."
        # Word-splitting is intentional: CodeC source names are sanitised.
        # shellcheck disable=SC2086
        exec "${'$'}TCC_BIN" ${'$'}extra ${'$'}converted
    """.trimIndent() + "\n"

    fun pkgScript(): String = """
        #!/system/bin/sh
        # CodeC pkg — placeholder until Phase 3 (apt + dpkg + our repo).
        echo "pkg: CodeC's package manager ships in Phase 3 of the Mini-Termux plan."
        echo "     For now the built-in compiler is available as:  cc file.c -o a.out"
        echo "     See docs/TERMINAL_PLAN.md"
        exit 1
    """.trimIndent() + "\n"

    fun bashShim(): String = """
        #!/system/bin/sh
        # Phase 1 stand-in: Android's /system/bin/sh until the bootstrap
        # ships a real bash (Phase 2). Keeps "exec bash" working today.
        exec /system/bin/sh "${'$'}@"
    """.trimIndent() + "\n"

    fun profileScript(prefix: File, home: File, projects: File): String = """
        # CodeC login profile (Phase 1)
        export PREFIX='${prefix.absolutePath}'
        export HOME='${home.absolutePath}'
        export CODEC_PROJECTS='${projects.absolutePath}'
        export TMPDIR="${'$'}PREFIX/tmp"
        export PATH="${'$'}PREFIX/bin:${'$'}PATH"
        export TERM="${'$'}{TERM:-xterm-256color}"
        export COLORTERM="${'$'}{COLORTERM:-truecolor}"
        export LANG="${'$'}{LANG:-C.UTF-8}"
        # Keep the prompt short so it fits a phone and does not wrap into
        # the command the user is typing.
        export PS1='codec ${'$'} '
        mkdir -p "${'$'}HOME" "${'$'}TMPDIR" "${'$'}CODEC_PROJECTS" 2>/dev/null
        pj() { cd "${'$'}CODEC_PROJECTS" || return; }
        if [ -z "${'$'}CODEC_MOTD_SHOWN" ]; then
          export CODEC_MOTD_SHOWN=1
          echo "CodeC terminal  (Phase 1)"
          echo "  type:  ls"
          echo "         cc main.c -o a.out"
          echo "         ./a.out"
          echo "  (save main.c in the Editor first)"
          echo
        fi
        cd "${'$'}CODEC_PROJECTS" 2>/dev/null || cd "${'$'}HOME" 2>/dev/null || true
    """.trimIndent() + "\n"

    fun normalizeStandard(standard: String): String =
        "c" + standard.lowercase().removePrefix("c")

    fun warningFlags(warnings: Boolean): String =
        if (warnings) "-Wall -Wextra" else ""

    fun optimizationFlag(level: Int): String = "-O${level.coerceIn(0, 3)}"

    fun buildEnv(
        filesDir: File,
        nativeLibDir: File,
        tccBinary: File?,
        tccBundle: File,
        standard: String,
        warnings: Boolean,
        optimization: Int,
        extraPath: List<File> = emptyList(),
        projectsDir: File? = null
    ): Map<String, String> {
        val prefix = prefixDir(filesDir)
        val home = homeDir(filesDir)
        val projects = projectsDir ?: File(filesDir, "CodeC/projects")
        val pathParts = buildList {
            add(binDir(prefix).absolutePath)
            add(nativeLibDir.absolutePath)
            extraPath.forEach { add(it.absolutePath) }
            add("/system/bin")
            add("/system/xbin")
            add("/vendor/bin")
        }
        return buildMap {
            put("PREFIX", prefix.absolutePath)
            put("HOME", home.absolutePath)
            put("TMPDIR", tmpDir(prefix).absolutePath)
            put("TERM", "xterm-256color")
            put("COLORTERM", "truecolor")
            put("LANG", "C.UTF-8")
            put("LC_ALL", "C.UTF-8")
            put("PATH", pathParts.joinToString(":"))
            put("CC_STD", normalizeStandard(standard))
            put("CC_WARN", warningFlags(warnings))
            put("CC_OPT", optimizationFlag(optimization))
            put("ENV", File(etcDir(prefix), "profile").absolutePath)
            put("PS1", "codec $ ")
            put("USER", "codec")
            put("CODEC_PROJECTS", projects.absolutePath)
            if (tccBinary != null) put("TCC_BIN", tccBinary.absolutePath)
            put("TCC_BUNDLE", tccBundle.absolutePath)
        }
    }

    /**
     * Preferred shell: `$PREFIX/bin/bash` (shim or Phase-2 real bash), then
     * `/system/bin/sh`.
     */
    fun resolveShell(prefix: File): File {
        val candidates = listOf(
            File(binDir(prefix), "bash"),
            File("/system/bin/bash"),
            File("/system/bin/sh")
        )
        return candidates.firstOrNull { it.exists() && it.canExecute() }
            ?: File("/system/bin/sh")
    }

    fun envToArray(env: Map<String, String>): Array<String> =
        env.entries
            .sortedBy { it.key }
            .map { "${it.key}=${it.value}" }
            .toTypedArray()
}

data class PreparedShell(
    val prefix: File,
    val home: File,
    val shell: File,
    val cwd: File,
    val env: Map<String, String>
)

class ShellBootstrap(private val context: Context) {

    fun prefixDir(): File = ShellEnvironment.prefixDir(context.filesDir)
    fun homeDir(): File = ShellEnvironment.homeDir(context.filesDir)

    /**
     * Creates `$PREFIX/{bin,etc,tmp}` and `$HOME`, extracts TCC if needed,
     * and (re)writes the Phase-1 scripts when the bootstrap version changes.
     */
    fun prepare(settings: CompilerSettings = CompilerSettings("c11", warnings = true, optimization = 0)): PreparedShell {
        val prefix = prefixDir()
        val home = homeDir()
        val bin = ShellEnvironment.binDir(prefix)
        val etc = ShellEnvironment.etcDir(prefix)
        val tmp = ShellEnvironment.tmpDir(prefix)
        bin.mkdirs()
        etc.mkdirs()
        tmp.mkdirs()
        home.mkdirs()

        val extracted = EmbeddedCompiler.ensureExtracted(context)
        if (!extracted) {
            AppLogger.w("ShellBootstrap", "TCC bundle not extracted; cc will report unavailable")
        }

        val projects = try {
            FileManager(context).getProjectDir()
        } catch (_: Exception) {
            File(context.filesDir, "CodeC/projects")
        }
        if (!projects.exists()) projects.mkdirs()

        val marker = File(prefix, ".bootstrap-v${ShellEnvironment.BOOTSTRAP_VERSION}")
        if (!marker.exists()) {
            writeExecutable(File(bin, "cc"), ShellEnvironment.ccScript())
            writeExecutable(File(bin, "pkg"), ShellEnvironment.pkgScript())
            writeExecutable(File(bin, "bash"), ShellEnvironment.bashShim())
            File(etc, "profile").writeText(
                ShellEnvironment.profileScript(prefix, home, projects)
            )
            File(home, ".profile").writeText(". ${TerminalHandoff.shellEscape(File(etc, "profile").absolutePath)}\n")
            marker.writeText(ShellEnvironment.BOOTSTRAP_VERSION)
            AppLogger.i("ShellBootstrap", "Wrote Phase-1 userland to ${prefix.absolutePath}")
        }

        val tcc = EmbeddedCompiler.tccBinary(context)
        val bundle = EmbeddedCompiler.bundleDir(context)
        val nativeLib = File(context.applicationInfo.nativeLibraryDir)

        val env = ShellEnvironment.buildEnv(
            filesDir = context.filesDir,
            nativeLibDir = nativeLib,
            tccBinary = tcc,
            tccBundle = bundle,
            standard = settings.cStandard,
            warnings = settings.warnings,
            optimization = settings.optimization,
            projectsDir = projects
        )
        return PreparedShell(
            prefix = prefix,
            home = home,
            shell = ShellEnvironment.resolveShell(prefix),
            cwd = projects,
            env = env
        )
    }

    private fun writeExecutable(file: File, body: String) {
        file.parentFile?.mkdirs()
        file.writeText(body)
        file.setExecutable(true, false)
    }
}
