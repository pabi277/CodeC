package com.codeci.ide.ui.terminal

import com.codeci.ide.ui.projects.ProjectConfig
import java.io.File

/**
 * Editor → terminal handoff. Builds the shell command that compiles the
 * current file with the embedded TCC (`cc`) and runs the result.
 *
 * Pure functions so the quoting rules are unit-tested.
 */
object TerminalHandoff {

    const val DEFAULT_OUTPUT = "a.out"

    /**
     * POSIX-safe single-argument quoting. Unquoted when the string is a
     * conservative identifier; otherwise wrapped in single quotes with
     * embedded quotes escaped the usual `'\''` way.
     */
    fun shellEscape(value: String): String {
        if (value.isEmpty()) return "''"
        val safe = value.all { ch ->
            ch.isLetterOrDigit() || ch in "._-/=:@+,%"
        }
        if (safe) return value
        return buildString(value.length + 8) {
            append('\'')
            for (ch in value) {
                if (ch == '\'') append("'\\''") else append(ch)
            }
            append('\'')
        }
    }

    /**
     * Phase 11 — split [compileAndRunCommand] into its build and run halves so
     * the Output Panel can report each phase separately. [build] compiles the
     * saved source with the `cc` frontend; [run] executes the result in the
     * source's directory.
     */
    fun compileParts(sourcePath: String, outputName: String = DEFAULT_OUTPUT): Pair<String, String> {
        val source = File(sourcePath)
        val build = "cc ${shellEscape(sourcePath)} -o ${shellEscape(outputName)}"
        val run = "./${shellEscape(outputName)}"
        return build to run
    }

    /**
     * `cd` to the source directory, compile with `cc`, run `./a.out`.
     * [sourcePath] should be an absolute path to a saved `.c` file.
     */
    fun compileAndRunCommand(sourcePath: String, outputName: String = DEFAULT_OUTPUT): String {
        val source = File(sourcePath)
        val dir = source.parent ?: "."
        val (build, run) = compileParts(sourcePath, outputName)
        return listOf(
            "cd ${shellEscape(dir)}",
            build,
            run
        ).joinToString(" && ")
    }

    /**
     * Phase 12 — split a script (interpreted) run into its build and run
     * halves. Scripts have no build step; [run] executes the saved file with
     * [interpreter] (python3 for CodeC's Phase 12 package).
     */
    fun interpretedParts(sourcePath: String, interpreter: String = "python3"): Pair<String?, String?> {
        val run = "$interpreter ${shellEscape(sourcePath)}"
        return null to run
    }

    /**
     * Phase 12 — `cd` to the source directory, then run the file with the
     * interpreter (python3). Scripts are not compiled; the RUN ▶ pipeline
     * treats them like a project with an empty build step.
     */
    fun interpretedRunCommand(sourcePath: String, interpreter: String = "python3"): String {
        val source = File(sourcePath)
        val dir = source.parent ?: "."
        return "cd ${shellEscape(dir)} && $interpreter ${shellEscape(sourcePath)}"
    }

    /**
     * Phase 9.1: compile and run one project file in place, from the project
     * folder — the tree's "Run in terminal" action. Build output goes under
     * `<dir>/bin` so the source tree stays clean; [relativePath] is inside
     * [projectDirectory]. Script files (.py) are run directly with python3 —
     * there is nothing to compile.
     */
    fun projectFileRunCommand(projectDirectory: File, relativePath: String): String {
        val dir = projectDirectory.absolutePath
        val rel = relativePath.replace('\\', '/').trim().trimStart('/')
        if (rel.isBlank()) return "cd ${shellEscape(dir)} && echo 'run: no file selected'"
        val leaf = rel.substringAfterLast('/')
        if (leaf.endsWith(".py", ignoreCase = true)) {
            return "cd ${shellEscape(dir)} && python3 ${shellEscape(rel)}"
        }
        val out = (leaf.substringBeforeLast('.', leaf).ifBlank { "main" } + ".out")
            .replace(Regex("[^A-Za-z0-9._-]"), "_")
        return "cd ${shellEscape(dir)} && mkdir -p bin && cc ${shellEscape(rel)} -o bin/$out && ./bin/$out"
    }

    /** Just drop the user into the file's directory. */
    fun openInDirectoryCommand(directory: String): String =
        "cd ${shellEscape(directory)}"

    /**
     * Phase 11 — split a project's run configuration into its build and run
     * halves (either may be empty, e.g. web or python projects with no build
     * step). Commands are intentionally returned verbatim: they are the
     * user's run configuration, while only app-private paths need quoting.
     */
    fun projectRunParts(projectDirectory: String, config: ProjectConfig): Pair<String?, String?> {
        val build = config.build.trim().takeIf { it.isNotEmpty() }
        val run = config.run.trim().takeIf { it.isNotEmpty() }
        return build to run
    }

    /**
     * Runs a project configuration from the project root. Build and run are
     * intentionally command strings: they are the user's run configuration,
     * while only the app-private path needs quoting.
     */
    fun projectRunCommand(projectDirectory: String, config: ProjectConfig): String {
        val commands = mutableListOf("cd ${shellEscape(projectDirectory)}")
        val (build, run) = projectRunParts(projectDirectory, config)
        build?.let(commands::add)
        run?.let(commands::add)
        return commands.joinToString(" && ")
    }
}
