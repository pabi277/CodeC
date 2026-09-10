package com.codeci.ide.ui.services

import java.io.File

/**
 * Phase 39.1 — one policy for every path a RUN produces.
 *
 * **Rule (one sentence):** an artifact CodeC invented goes under
 * `CodeC/temp/runs/<stamp>/`; an artifact the user asked for by name
 * (`cc … -o bin/menu`, a project.json `-o` path) stays where they asked
 * for it. [ownedByCodeC] is the boolean both GC (39.1) and the git
 * exclude table (39.2) read — neither re-derives "looks like a build
 * output" from name patterns.
 *
 * Pure Kotlin: **no Android imports.** The Android edge
 * ([CompilerService], Settings → Storage) supplies the temp root and
 * stamps; this object only places paths and classifies them.
 */
object RunArtifacts {

    /** Subdirectory of `CodeC/temp` that holds one dir per run stamp. */
    const val RUNS_DIR = "runs"

    data class ArtifactPlan(
        /** Copy of the source CodeC wrote for the compiler, or null when none. */
        val sourceCopy: File?,
        /** Binary / primary output path. */
        val binary: File,
        /** Working directory for the compile/run process. */
        val cwd: File,
        /** True ⇒ under CodeC's temp root ⇒ GC-eligible and git-excluded. */
        val ownedByCodeC: Boolean,
        /** Stamp that names the run dir (`runs/<stamp>/`). */
        val stamp: Long,
        /** Optional pycache root for PYTHONPYCACHEPREFIX. */
        val pycacheDir: File? = null,
        /** Optional server/session log path. */
        val serverLog: File? = null,
    )

    /**
     * Where a run of language [lang] in [project] puts each artifact kind.
     *
     * - [userSuppliedOutput] non-null/blank → binary lands at that path
     *   (relative to [project] when not absolute) and [ownedByCodeC] is
     *   false. CodeC never silently relocates a path the user typed.
     * - otherwise → everything under `tempRoot/runs/<stamp>/`.
     */
    fun plan(
        lang: String,
        project: File,
        tempRoot: File,
        stamp: Long,
        userSuppliedOutput: String? = null,
    ): ArtifactPlan {
        val safeStamp = stamp.coerceAtLeast(0L)
        val runDir = runDir(tempRoot, safeStamp)
        val userOut = userSuppliedOutput?.trim()?.takeIf { it.isNotEmpty() }
        if (userOut != null) {
            val binary = resolveUserOutput(project, userOut)
            return ArtifactPlan(
                sourceCopy = null,
                binary = binary,
                cwd = project,
                ownedByCodeC = false,
                stamp = safeStamp,
                pycacheDir = if (isPython(lang)) File(runDir, "pycache") else null,
                serverLog = if (isServer(lang)) File(runDir, "server.log") else null,
            )
        }
        val sourceCopy = when {
            isCFamily(lang) -> File(runDir, "source.c")
            else -> null
        }
        val binary = when {
            isCFamily(lang) -> File(runDir, "program")
            isPython(lang) -> File(runDir, "stdout.log")
            else -> File(runDir, "output")
        }
        return ArtifactPlan(
            sourceCopy = sourceCopy,
            binary = binary,
            cwd = runDir,
            ownedByCodeC = true,
            stamp = safeStamp,
            pycacheDir = if (isPython(lang)) File(runDir, "pycache") else null,
            serverLog = if (isServer(lang)) File(runDir, "server.log") else null,
        )
    }

    /** `tempRoot/runs/<stamp>/` — the only dir a run owns. */
    fun runDir(tempRoot: File, stamp: Long): File = File(File(tempRoot, RUNS_DIR), stamp.toString())

    /**
     * True when [relativePath] (project- or temp-relative) is something
     * CodeC itself produced. Used by 39.2's pre-filter and by 43.2's push
     * set. Deliberately narrow: the user's `bin/menu` is NOT a CodeC
     * artifact (it is theirs; 39.2's pattern table excludes it from git).
     */
    fun isCodeCArtifact(relativePath: String): Boolean {
        val n = normalize(relativePath)
        if (n.isEmpty()) return false
        // Legacy flat names (pre-39.1) still count so GC/untrack catch them.
        if (n.matches(Regex("""source_\d+\.c"""))) return true
        if (n.matches(Regex("""program_\d+"""))) return true
        if (n.startsWith("source_") && n.endsWith(".c")) return true
        if (n.startsWith("program_")) return true
        // New layout: anything under runs/<stamp>/…
        if (n.startsWith("$RUNS_DIR/") || n.contains("/$RUNS_DIR/")) return true
        // Scratch CodeC writes into a project.
        if (n.startsWith("codec-import-") && n.endsWith(".zip")) return true
        if (n.startsWith(".codec-tmp/") || n == ".codec-tmp") return true
        if (n.matches(Regex("""codec-.*\.tmp"""))) return true
        return false
    }

    /**
     * Refuse a path that would escape [tempRoot]. Returns the safe default
     * (a fresh runs/<stamp>/ under tempRoot) when the candidate is outside —
     * the same discipline as [com.codeci.ide.ui.projects.ProjectPathUtils.resolveInside].
     */
    fun confineToTemp(tempRoot: File, candidate: File, stamp: Long): File {
        val root = tempRoot.canonicalFile
        val target = runCatching { candidate.canonicalFile }.getOrNull() ?: return runDir(root, stamp)
        val rootPath = root.path.let { if (it.endsWith(File.separator)) it else it + File.separator }
        return if (target.path == root.path || target.path.startsWith(rootPath)) {
            target
        } else {
            runDir(root, stamp)
        }
    }

    /** Ensure the run directory exists; returns it. */
    fun ensureRunDir(tempRoot: File, stamp: Long): File {
        val dir = runDir(tempRoot, stamp)
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    private fun resolveUserOutput(project: File, userOut: String): File {
        val asFile = File(userOut)
        return if (asFile.isAbsolute) asFile else File(project, userOut)
    }

    private fun normalize(path: String): String =
        path.replace('\\', '/').removePrefix("./").trimStart('/')

    private fun isCFamily(lang: String): Boolean {
        val l = lang.lowercase()
        return l == "c" || l == "cpp" || l == "c++" || l == "cxx" || l.startsWith("c/")
    }

    private fun isPython(lang: String): Boolean = lang.lowercase().startsWith("py")

    private fun isServer(lang: String): Boolean {
        val l = lang.lowercase()
        return l.contains("server") || l == "node" || l == "http"
    }
}
