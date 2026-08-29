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
     * `cd` to the source directory, compile with `cc`, run `./a.out`.
     * [sourcePath] should be an absolute path to a saved `.c` file.
     */
    fun compileAndRunCommand(sourcePath: String, outputName: String = DEFAULT_OUTPUT): String {
        val source = File(sourcePath)
        val dir = source.parent ?: "."
        return listOf(
            "cd ${shellEscape(dir)}",
            "cc ${shellEscape(sourcePath)} -o ${shellEscape(outputName)}",
            "./${shellEscape(outputName)}"
        ).joinToString(" && ")
    }

    /** Just drop the user into the file's directory. */
    fun openInDirectoryCommand(directory: String): String =
        "cd ${shellEscape(directory)}"

    /**
     * Runs a project configuration from the project root. Build and run are
     * intentionally command strings: they are the user's run configuration,
     * while only the app-private path needs quoting.
     */
    fun projectRunCommand(projectDirectory: String, config: ProjectConfig): String {
        val commands = mutableListOf("cd ${shellEscape(projectDirectory)}")
        config.build.trim().takeIf { it.isNotEmpty() }?.let(commands::add)
        config.run.trim().takeIf { it.isNotEmpty() }?.let(commands::add)
        return commands.joinToString(" && ")
    }
}
