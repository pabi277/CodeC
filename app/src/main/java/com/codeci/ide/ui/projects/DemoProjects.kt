package com.codeci.ide.ui.projects

import java.io.File

/**
 * The bundled sample project the app ships with (owner request, 2026-08-31):
 * a ready-to-run `demo_flask` Flask web server, already visible in the Files
 * tab, so it can be opened and RUN ▶ immediately.
 *
 * [ensure] is pure and host-testable. It seeds the project exactly like the
 * wizard would (`ProjectConfig.defaultFor` + `ProjectScaffold.writeFiles`),
 * plus a short README. It runs once per app install: a marker file in the
 * projects root records the seed, so deleting the demo project does NOT make
 * it reappear, and a user-created project with the same name is never
 * overwritten.
 */
object DemoProjects {

    const val NAME = "demo_flask"
    const val TYPE = "python-flask"
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
     * One-time seed of the demo project. Returns the project directory when
     * this call created it, or null when already seeded / already present.
     * Never overwrites existing content; a failed seed leaves no marker so it
     * can be retried on the next list.
     */
    fun ensure(projectsRoot: File): File? {
        if (File(projectsRoot, MARKER).exists()) return null
        val project = File(projectsRoot, NAME)
        val created = if (project.isDirectory) {
            null
        } else {
            if (project.exists() || !project.mkdirs()) return null
            writeProject(project)
            project
        }
        File(projectsRoot, MARKER).writeText("seeded 2026-08-31")
        return created
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
