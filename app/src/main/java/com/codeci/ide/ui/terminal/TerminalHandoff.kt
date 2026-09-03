package com.codeci.ide.ui.terminal

import com.codeci.ide.ui.projects.ProjectConfig
import com.codeci.ide.ui.services.LanguageRegistry
import java.io.File

/**
 * Editor → terminal handoff. Builds the shell command that compiles and runs
 * the current file.
 *
 * Phase 21.1: the per-language commands now come from [LanguageRegistry] —
 * `gcc`/`g++`/`python3`/… instead of the retired TCC `cc` shim. This object
 * keeps the quoting rules and the "join into one `cd … && …` line" shape the
 * terminal handoff needs; the registry owns *what* is run.
 *
 * Pure functions so the quoting rules are unit-tested.
 */
object TerminalHandoff {

    const val DEFAULT_OUTPUT = "a.out"

    /** Phase 21.1 — project-relative directory compiled binaries land in. */
    const val PROJECT_BUILD_DIR = "bin"

    /**
     * POSIX-safe single-argument quoting. Unquoted when the string is a
     * conservative identifier; otherwise wrapped in single quotes with
     * embedded quotes escaped the usual `'\''` way.
     */
    fun shellEscape(value: String): String = LanguageRegistry.shellEscape(value)

    /**
     * Phase 11 — split [compileAndRunCommand] into its build and run halves so
     * the Output Panel can report each phase separately. [build] compiles the
     * saved source with gcc/g++ (Phase 21); [run] executes the result in the
     * source's directory.
     */
    fun compileParts(sourcePath: String, outputName: String = DEFAULT_OUTPUT): Pair<String, String> {
        val compiler = if (sourcePath.substringAfterLast('.', "").lowercase() == "c") "gcc" else "g++"
        val build = "$compiler ${shellEscape(sourcePath)} -o ${shellEscape(outputName)} -lm"
        val run = "./${shellEscape(outputName)}"
        return build to run
    }

    /**
     * `cd` to the source directory, build (when the language needs it) and
     * run the file. Phase 21.1: dispatches through [LanguageRegistry] for
     * every language it knows, so a scratch `.py`/`.js`/`.rb` file opened in
     * the terminal runs with its own interpreter instead of being fed to a
     * C compiler. Falls back to the C/C++ compile line for anything else.
     */
    fun compileAndRunCommand(sourcePath: String, outputName: String = DEFAULT_OUTPUT): String {
        val source = File(sourcePath)
        val dir = source.parent ?: "."
        val profile = LanguageRegistry.forFile(sourcePath)
        if (profile != null && !profile.isWebPreview) {
            LanguageRegistry.planFor(profile, dir, sourcePath, null)?.let { return it.terminal }
        }
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
        val (build, run, fallback) = projectFileParts(projectDirectory, relativePath)
        val dir = projectDirectory.absolutePath
        val rel = relativePath.replace('\\', '/').trim().trimStart('/')
        if (rel.isBlank()) return fallback
        val steps = buildList {
            if (build != null) add(build)
            if (run != null) add(run)
        }
        return "cd ${shellEscape(dir)} && " + steps.joinToString(" && ")
    }

    /**
     * Phase 12 — split a project-file run into its build/run halves (or a
     * fallback terminal command when nothing is selected). Mirrors
     * [projectFileRunCommand] exactly. Phase 21.1: the per-language shape
     * comes from [LanguageRegistry] — interpreted files have no build step,
     * compiled files build into `<dir>/bin` so the source tree stays clean.
     * The Output Panel needs the halves separately; the terminal needs them
     * joined.
     */
    fun projectFileParts(projectDirectory: File, relativePath: String): Triple<String?, String?, String> {
        val dir = projectDirectory.absolutePath
        val rel = relativePath.replace('\\', '/').trim().trimStart('/')
        if (rel.isBlank()) {
            return Triple(null, null, "cd ${shellEscape(dir)} && echo 'run: no file selected'")
        }
        // Phase 21.1 — every language the registry knows (C, C++, Python,
        // Node, Go, Rust, PHP, Ruby, Lua, shell) runs through its profile;
        // compiled output still lands in `<project>/bin` so the tree stays clean.
        val profile = LanguageRegistry.forFile(rel)
        if (profile != null && !profile.isWebPreview) {
            val plan = LanguageRegistry.planFor(profile, dir, rel, PROJECT_BUILD_DIR)
            if (plan != null) return Triple(plan.build, plan.run, plan.terminal)
        }
        val leaf = rel.substringAfterLast('/')
        val out = (leaf.substringBeforeLast('.', leaf).ifBlank { "main" } + ".out")
            .replace(Regex("[^A-Za-z0-9._-]"), "_")
        val build = "mkdir -p bin && gcc ${shellEscape(rel)} -o bin/$out -lm"
        val run = "./bin/$out"
        return Triple(build, run, "cd ${shellEscape(dir)} && $build && $run")
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
