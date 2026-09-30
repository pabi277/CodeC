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
 * **Law (Phase 66.1, owner 2026-09-27: *"Yes — stay deleted"*): the demo is
 * seeded at most once per install.** Between 2026-09-12 and Phase 64 it was
 * re-seeded whenever the directory was missing, because the Phase 45 guided
 * tour taught the project by name; Phase 64 removed that tour, and with it the
 * only reason a deleted demo had to come back. Re-seeding made *Delete* on the
 * hub card look broken (the card never left the list — `deleteProject` reloads
 * the list, and the reload re-seeded it) and made the hub's designed empty
 * state unreachable on a real device. The marker file in the projects root is
 * therefore the gate again, exactly as [GameArenaSample] is seeded once after the
 * user accepts first-run: a `demo_flask` the user deleted stays deleted. A user who wants
 * it back creates a *Flask Web Server* project from the `+` sheet — the same
 * scaffold, one tap.
 *
 * What never changed: an existing `demo_flask` is never overwritten or touched
 * (the user's edits are theirs), and a plain FILE of the same name blocks the
 * seed and is left alone.
 */
object DemoProjects {

    const val NAME = "demo_flask"
    const val TYPE = "python-flask"

    /**
     * The demo's entry file — the one `ProjectScaffold` writes first for [TYPE]
     * (`DemoProjectSeedTest` pins it against `ProjectScaffold.filesFor`).
     */
    const val ENTRY_FILE = "app.py"

    /**
     * Written beside the projects (not inside the demo) when the demo has been
     * seeded — or found — once. Hidden (leading dot), so `listProjects()` never
     * lists it as a project. Its presence means "never seed again".
     */
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
     * Seeds the demo project on the first call of an install, and returns the
     * directory when THIS call created it (null in every other case). Idempotent
     * and safe to call from every list refresh:
     *  - an existing `demo_flask` directory is never read, rewritten or touched
     *    (and, if the marker is somehow missing beside it, the marker is written
     *    so the once-per-install rule still holds after the user deletes it);
     *  - a plain file named `demo_flask` blocks the seed and is left alone;
     *  - a marker without a directory means the user deleted the demo: it stays
     *    deleted;
     *  - a seed that throws is rolled back, so a half-written demo can never be
     *    mistaken for a real project (and the next list retries, because the
     *    marker is written only after a complete seed).
     */
    fun ensure(projectsRoot: File): File? {
        val project = File(projectsRoot, NAME)
        val marker = File(projectsRoot, MARKER)
        if (project.isDirectory) {
            if (!marker.exists()) runCatching { marker.writeText(MARKER_TEXT) }
            return null
        }
        if (project.exists()) return null
        if (marker.exists()) return null
        if (!project.mkdirs()) return null
        return try {
            writeProject(project)
            marker.writeText(MARKER_TEXT)
            project
        } catch (e: Exception) {
            project.deleteRecursively()
            null
        }
    }

    private const val MARKER_TEXT = "seeded once; a deleted demo_flask stays deleted (Phase 66.1)"

    private fun writeProject(project: File) {
        val config = ProjectConfig.defaultFor(NAME, TYPE)
        val metadata = File(project, ".codec")
        if (!metadata.exists() && !metadata.mkdirs()) throw IllegalStateException("Could not create project metadata")
        File(metadata, "project.json").writeText(config.toJsonString())
        ProjectScaffold.writeFiles(config.type, project)
        File(project, "README.md").writeText(README)
    }
}
