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
 * and a `bash` shim so the terminal can `exec bash` before a real bootstrap
 * lands. After Phase 2 extract, [resolveShell] prefers ELF bash/busybox.
 * Script bodies are pure strings so they are unit-tested.
 */
object ShellEnvironment {
    const val BOOTSTRAP_VERSION = "13"
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
        outfile=""
        out_next=0
        for arg in "${'$'}@"; do
          if [ "${'$'}out_next" = 1 ]; then
            case "${'$'}arg" in
              /*) ;;
              *) arg="${'$'}CWD/${'$'}arg" ;;
            esac
            outfile="${'$'}arg"
            out_next=0
            continue
          fi
          case "${'$'}arg" in
            -o) out_next=1; continue ;;
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
                    echo "cc: save the file in the Editor, then run: cc main.c -o a.out" >&2
                    exit 1
                  fi
                  ;;
              esac
              ;;
          esac
          converted="${'$'}converted ${'$'}arg"
        done
        if [ -z "${'$'}outfile" ]; then
          outfile="${'$'}CWD/a.out"
        fi
        cd "${'$'}TCC_BUNDLE" || exit 1
        extra="-nostdlib -static"
        [ -n "${'$'}CC_STD" ] && extra="${'$'}extra -std=${'$'}CC_STD"
        [ -n "${'$'}CC_WARN" ] && extra="${'$'}extra ${'$'}CC_WARN"
        [ -n "${'$'}CC_OPT" ] && extra="${'$'}extra ${'$'}CC_OPT"
        extra="${'$'}extra -I include-tcc -I include -B . -L ."
        [ ! -f codec_stdio.o ] && [ -f codec_stdio.c ] && "${'$'}TCC_BIN" -c -I include-tcc -I include -o codec_stdio.o codec_stdio.c
        # Same order as EmbeddedCompiler.buildCompileCommand: archives then -o last.
        # Do not exec: we must chmod +x the ELF afterwards or ./a.out is denied.
        # shellcheck disable=SC2086
        "${'$'}TCC_BIN" ${'$'}extra crt1.o crti.o codec_stdio.o ${'$'}converted libtcc1.a libc.a libtcc1.a libc.a crtn.o -o "${'$'}outfile"
        status=${'$'}?
        if [ "${'$'}status" -eq 0 ] && [ -n "${'$'}outfile" ] && [ -f "${'$'}outfile" ]; then
          chmod 755 "${'$'}outfile" 2>/dev/null
        fi
        exit ${'$'}status
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
        # Prefer the path the app exported (current editor folder).
        if [ -z "${'$'}CODEC_PROJECTS" ]; then
          export CODEC_PROJECTS='${projects.absolutePath}'
        fi
        export TMPDIR="${'$'}PREFIX/tmp"
        export PATH="${'$'}PREFIX/bin:/system/bin:/system/xbin:${'$'}PATH"
        export TERM="${'$'}{TERM:-xterm-256color}"
        export COLORTERM="${'$'}{COLORTERM:-truecolor}"
        export LANG="${'$'}{LANG:-C.UTF-8}"
        export PS1='codec ${'$'} '
        mkdir -p "${'$'}HOME" "${'$'}TMPDIR" "${'$'}CODEC_PROJECTS" 2>/dev/null
        pj() { cd "${'$'}CODEC_PROJECTS" || return; }
        cd "${'$'}CODEC_PROJECTS" 2>/dev/null || cd "${'$'}HOME" 2>/dev/null || true
        if [ -z "${'$'}CODEC_MOTD_SHOWN" ]; then
          export CODEC_MOTD_SHOWN=1
          echo "CodeC terminal"
          /system/bin/ls
          echo
          if ! /system/bin/ls *.c >/dev/null 2>&1; then
            echo "(no .c files here — Editor: Save, then: ls)"
            echo
          fi
        fi
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

    fun isElf(file: File): Boolean {
        if (!file.isFile) return false
        return try {
            file.inputStream().use { input ->
                val mag = ByteArray(4)
                if (input.read(mag) != 4) return false
                mag[0] == 0x7f.toByte() && mag[1] == 'E'.code.toByte() &&
                    mag[2] == 'L'.code.toByte() && mag[3] == 'F'.code.toByte()
            }
        } catch (_: Exception) {
            false
        }
    }

    /** True when bootstrap extract dropped a native bash or busybox. */
    fun hasRealUserland(prefix: File): Boolean {
        val bin = binDir(prefix)
        return isElf(File(bin, "bash")) || isElf(File(bin, "busybox"))
    }

    /**
     * Preferred shell: real ELF `$PREFIX/bin/bash`, then busybox ash,
     * then the Phase-1 shim, then `/system/bin/sh`.
     */
    fun resolveShell(prefix: File): File {
        val bash = File(binDir(prefix), "bash")
        val busybox = File(binDir(prefix), "busybox")
        if (isElf(bash) && bash.canExecute()) return bash
        if (isElf(busybox) && busybox.canExecute()) return busybox
        val candidates = listOf(
            bash,
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
        writeExecutable(File(bin, "cc"), ShellEnvironment.ccScript())
        writeExecutable(File(bin, "pkg"), ShellEnvironment.pkgScript())
        val bash = File(bin, "bash")
        if (!ShellEnvironment.isElf(bash)) {
            writeExecutable(bash, ShellEnvironment.bashShim())
        }
        File(etc, "profile").writeText(
            ShellEnvironment.profileScript(prefix, home, projects)
        )
        File(home, ".profile").writeText(". ${TerminalHandoff.shellEscape(File(etc, "profile").absolutePath)}\n")
        marker.writeText(ShellEnvironment.BOOTSTRAP_VERSION)

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
