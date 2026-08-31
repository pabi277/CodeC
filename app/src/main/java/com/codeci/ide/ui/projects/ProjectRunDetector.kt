package com.codeci.ide.ui.projects

import java.io.File

/**
 * What RUN ▶ should do for an `auto` project (owner request, 2026-08-31:
 * "no selection … just created and run any type"). The project carries no
 * type decision at creation time; instead the runnable type is inferred from
 * the project's files, with the actively open file taking precedence.
 */
sealed class AutoRunPlan {
    /** app.py / server.c / main.py(fastapi) — background server + Web Preview. */
    data class Server(val type: String) : AutoRunPlan()

    /** index.html — static Web Preview (same flow as a `web` project). */
    data class Web(val entry: String) : AutoRunPlan()

    /** Anything else — use the canonical preset config for [type] (c / python). */
    data class Project(val type: String) : AutoRunPlan()

    /** Nothing runnable found — explain to the user what to add. */
    data class None(val message: String) : AutoRunPlan()
}

/**
 * Pure, host-testable type detection for `auto` projects. Rules (first
 * match wins):
 *
 * 1. Active file: `app.py` → Flask server; `server.c` → C microservice;
 *    `main.py` → FastAPI server when its content imports fastapi/uvicorn,
 *    otherwise a plain Python script; any `.html` → static Web.
 * 2. If the active file gives no hint, scan the project root (same
 *    precedence): app.py, server.c, main.py, index.html, main.c, first .c,
 *    first .py.
 *
 * `Server`/`Web` are terminal (the ViewModel starts the server / preview);
 * `Project("c")`/`Project("python")` fall through to the normal active-file
 * run path with that preset as the project-level fallback; `None` reports a
 * user-facing hint.
 */
object ProjectRunDetector {

    private val PYTHON_SERVER_MARKERS = listOf("from fastapi import", "import fastapi", "import uvicorn")

    fun detect(projectRoot: File, activeRelativePath: String?): AutoRunPlan {
        val activeName = activeRelativePath
            ?.trim('/')
            ?.substringAfterLast('/')
            ?.lowercase()
        val active = activeRelativePath?.let { rel ->
            ProjectPathUtils.resolveInside(projectRoot, rel)?.takeIf { it.isFile }
        }
        val activeContent = active?.takeIf { it.length() <= MAX_SNIFF_BYTES }?.let {
            runCatching { it.readText() }.getOrNull()
        }

        // 1) The actively open file decides first — it is what the user is
        //    working on and matches RUN ▶'s Phase 12 semantics.
        when (activeName) {
            "app.py" -> return AutoRunPlan.Server("python-flask")
            "server.c" -> return AutoRunPlan.Server("c-microservice")
            "main.py" -> return if (isFastApi(activeContent)) {
                AutoRunPlan.Server("python-fastapi")
            } else {
                AutoRunPlan.Project("python")
            }
        }
        if (activeName?.endsWith(".html") == true) {
            return AutoRunPlan.Web(activeRelativePath ?: "index.html")
        }

        // 2) Project-level scan (root only — matching how the wizard tools
        //    treat the project as a flat workspace).
        return detectFromFiles(projectRoot)
    }

    private fun detectFromFiles(projectRoot: File): AutoRunPlan {
        if (!projectRoot.isDirectory) return AutoRunPlan.None(NO_FILES_MESSAGE)
        val files = projectRoot.listFiles()
            ?.filter { it.isFile }
            .orEmpty()
            .sortedBy { it.name.lowercase() }
        if (files.isEmpty()) return AutoRunPlan.None(NO_FILES_MESSAGE)

        fun firstFile(name: String): File? = files.firstOrNull { it.name.equals(name, ignoreCase = true) }
        fun firstByExtension(vararg ext: String): File? =
            files.firstOrNull { it.extension.lowercase() in ext }

        firstFile("app.py")?.let { return AutoRunPlan.Server("python-flask") }
        firstFile("server.c")?.let { return AutoRunPlan.Server("c-microservice") }
        firstFile("main.py")?.let {
            val content = runCatching { it.readText() }.getOrNull()
            return if (isFastApi(content)) {
                AutoRunPlan.Server("python-fastapi")
            } else {
                AutoRunPlan.Project("python")
            }
        }
        firstFile("index.html")?.let { return AutoRunPlan.Web("index.html") }
        firstFile("main.c")?.let { return AutoRunPlan.Project("c") }
        firstByExtension("c", "cpp", "cc", "cxx")?.let { return AutoRunPlan.Project("c") }
        firstByExtension("py")?.let { return AutoRunPlan.Project("python") }
        return AutoRunPlan.None(
            "Could not detect a runnable file. Add app.py (Flask), main.py " +
                "(Python / FastAPI), server.c (C microservice), main.c (C), " +
                "or index.html (web), then tap RUN again."
        )
    }

    private fun isFastApi(content: String?): Boolean =
        content?.let { source -> PYTHON_SERVER_MARKERS.any { source.contains(it) } } == true

    /** Only sniff file content for small source files (large binaries skipped). */
    private const val MAX_SNIFF_BYTES = 256L * 1024L

    private const val NO_FILES_MESSAGE =
        "This project has no files yet. Add app.py (Flask), main.py " +
            "(Python / FastAPI), server.c (C microservice), main.c (C), " +
            "or index.html (web), then tap RUN again."
}
