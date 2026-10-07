package com.codeci.ide.ui.projects

import java.io.File

/**
 * CodeC's first-run starter: a small offline game arena with a browsable,
 * genuinely multi-file web project. The game sources live as normal assets in
 * `app/src/main/assets/game-arena/`; the Files tab receives those same files
 * at the paths listed in [PROJECT_FILES].
 *
 * Existing projects are never rewritten. A hidden marker records that the
 * starter was seeded once, so a user who deletes it keeps it deleted.
 */
object GameArenaSample {

    const val NAME = "codec-arcade"
    const val TYPE = "web"
    const val ENTRY_FILE = "index.html"
    const val DISPLAY_NAME = "CodeC Arcade"
    const val ASSET_DIRECTORY = "game-arena"

    private const val MARKER = ".codec-arcade-seeded-v1"
    private const val MARKER_TEXT = "seeded once; a deleted CodeC Arcade stays deleted\n"

    /** Project paths are explicit so the seed can be verified before it is written. */
    val PROJECT_FILES: List<String> = listOf(
        "index.html",
        "styles/arena.css",
        "js/main.js",
        "js/storage.js",
        "js/games/snake.js",
        "js/games/blocks.js",
        "js/games/tic-tac-toe.js",
        "README.md",
    )

    /**
     * Seeds the bundled project once. [readAsset] receives a project-relative
     * path such as `js/games/snake.js`; callers can load from Android assets,
     * while host tests provide the repository's corresponding source files.
     */
    fun ensure(projectsRoot: File, readAsset: (String) -> String): File? {
        val project = File(projectsRoot, NAME)
        val marker = File(projectsRoot, MARKER)
        if (project.isDirectory) {
            if (!marker.exists()) runCatching { marker.writeText(MARKER_TEXT) }
            return null
        }
        if (project.exists() || marker.exists()) return null
        if (!project.mkdirs()) return null
        return try {
            writeProject(project, readAsset)
            marker.writeText(MARKER_TEXT)
            project
        } catch (_: Exception) {
            project.deleteRecursively()
            null
        }
    }

    /**
     * Phase 97 — seed the same arena into a project the user named in the setup
     * flow, rather than into the fixed sample.
     *
     * This is deliberately **not** [ensure]: the sample's "seeded once; a
     * deleted CodeC Arcade stays deleted" marker (Phase 33.2) guards the demo
     * that appears on its own, and must never block a project a person asked
     * for by name. There is no marker here — the setup flow's own gate is what
     * decides whether this runs at all.
     *
     * [onFileWritten] is called with each project-relative path as it lands, so
     * the flow's build screen lists the real files in the real order instead of
     * a progress animation over nothing.
     */
    fun seedInto(
        projectsRoot: File,
        projectName: String,
        readAsset: (String) -> String,
        onFileWritten: (String) -> Unit = {},
    ): File? {
        val project = File(projectsRoot, projectName)
        if (project.exists()) return null
        if (!project.mkdirs()) return null
        return try {
            writeProject(project, readAsset, projectName, onFileWritten)
            project
        } catch (_: Exception) {
            project.deleteRecursively()
            null
        }
    }

    private fun writeProject(
        project: File,
        readAsset: (String) -> String,
        projectName: String = NAME,
        onFileWritten: (String) -> Unit = {},
    ) {
        val config = ProjectConfig.defaultFor(projectName, TYPE)
        val metadata = File(project, ".codec")
        if (!metadata.exists() && !metadata.mkdirs()) {
            throw IllegalStateException("Could not create project metadata")
        }
        File(metadata, "project.json").writeText(config.toJsonString())
        onFileWritten(".codec/project.json")
        for (relativePath in PROJECT_FILES) {
            val target = File(project, relativePath)
            target.parentFile?.mkdirs()
            target.writeText(readAsset(relativePath))
            onFileWritten(relativePath)
        }
    }
}
