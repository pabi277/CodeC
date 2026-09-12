package com.codeci.ide.ui.projects

import java.io.File

/**
 * The bundled sample project the app ships with (owner request, 2026-08-31):
 * a ready-to-run `demo_flask` Flask web server, already visible in the Files
 * tab, so it can be opened and RUN ▶ immediately.
 *
 * [ensure] is pure and host-testable. It seeds the project exactly like the
 * wizard would (`ProjectConfig.defaultFor` + `ProjectScaffold.writeFiles`),
 * plus a short README.
 *
 * **Change of law (2026-09-12, owner, Phase 45 device round): the demo is
 * ALWAYS present.** It used to be seeded once per install — a marker file in the
 * projects root meant deleting `demo_flask` never brought it back. The Phase 45
 * guided tour walks the user through this project by name (*"change the project
 * folder to demo_flask → select app.py → run"*), so a missing demo would make
 * the tour teach a tap that leads nowhere. [ensure] therefore re-seeds whenever
 * the directory is gone. What it still never does: overwrite or touch an
 * existing `demo_flask` (the user's edits are theirs), or replace a plain FILE
 * of the same name. The marker survives as a *record* of the first seed, not as
 * a gate.
 */
object DemoProjects {

    const val NAME = "demo_flask"
    const val TYPE = "python-flask"

    /**
     * The demo's entry file — the one the guided tour tells the user to open and
     * RUN ▶, and the one `ProjectScaffold` writes for [TYPE]. Named here so the
     * tour's copy, the drawer's coach-mark anchor and the scaffold cannot drift
     * apart (`DemoProjectSeedTest` pins it against `ProjectScaffold.filesFor`).
     */
    const val ENTRY_FILE = "app.py"

    private const val MARKER = ".demo-flask-seeded-v1"

    private val README = """
        # demo_flask — CodeC's bundled Flask demo

        A ready-to-run Flask web server. Open `app.py` and tap RUN:

        1. First time only: make sure Python is installed — in the terminal run
              pkg install -y python
           (Flask itself is optional: without it the app falls back to a stdlib
           server that serves the same page, so this demo always runs.)
        2. RUN ▶ starts the server on http://127.0.0.1:5000 and the Web Preview
           opens automatically (green ● live address bar).
        3. Edit index.html and Save — the preview reloads with your change.
        4. Tap Stop to stop the server.

        API (with Flask installed): http://127.0.0.1:5000/api/hello
    """.trimIndent() + "\n"

    /**
     * Makes sure the demo project exists, and returns the directory when THIS
     * call created it (null when it was already there, or the filesystem
     * refused). Idempotent and safe to call from every list refresh:
     *  - an existing `demo_flask` directory is never read, rewritten or touched;
     *  - a missing one is seeded again (the "always present" law above);
     *  - a plain file named `demo_flask` blocks the seed and is left alone;
     *  - a seed that throws is rolled back, so a half-written demo can never be
     *    mistaken for a real project (and the next list retries).
     */
    fun ensure(projectsRoot: File): File? {
        val project = File(projectsRoot, NAME)
        if (project.isDirectory) return null
        if (project.exists()) return null
        if (!project.mkdirs()) return null
        return try {
            writeProject(project)
            File(projectsRoot, MARKER).writeText("seeded 2026-08-31")
            project
        } catch (e: Exception) {
            project.deleteRecursively()
            null
        }
    }

    private fun writeProject(project: File) {
        val config = ProjectConfig.defaultFor(NAME, TYPE)
        val metadata = File(project, ".codec")
        if (!metadata.exists() && !metadata.mkdirs()) throw IllegalStateException("Could not create project metadata")
        File(metadata, "project.json").writeText(config.toJsonString())
        ProjectScaffold.writeFiles(config.type, project)
        File(project, "README.md").writeText(README)
    }
}
