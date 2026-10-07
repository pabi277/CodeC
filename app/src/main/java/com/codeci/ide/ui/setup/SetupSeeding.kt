package com.codeci.ide.ui.setup

import android.content.Context
import com.codeci.ide.ui.projects.FileTreeRepository
import com.codeci.ide.ui.projects.GameArenaSample
import com.codeci.ide.ui.projects.ProjectManager
import java.io.File

/**
 * Phase 97 — the one Android-touching half of the setup flow: it does exactly
 * what [SetupFlowPolicy.planFor] promised, and nothing else.
 *
 * The split is the house's own (`rule.md` §4.4): the decision lives in
 * `SetupFlowPolicy` (host-tested, Android-free), the disk work lives here and
 * takes a [Context], and the UI ([SetupFlowScreen]) never touches either.
 *
 * Laws:
 * - **Skip is the legacy path.** [SetupFlowPolicy.isLegacySkip] runs
 *   `GameArenaSample.ensure` — the call every build before Phase 97 made on the
 *   first run — so a skipping user gets the old behaviour exactly.
 * - **Nothing is written that the plan did not list.** [onFileWritten] is
 *   invoked once per file, in the plan's order, from the code that really
 *   writes it (never from a timer).
 * - **A failure returns null and leaves the caller in charge.** The flow shows
 *   the hub's own recovery rather than a half-built project; a project that
 *   could not be finished is deleted by the writer that started it.
 */
object SetupSeeding {

    /**
     * Creates (or reuses) the project [choice] asked for and returns its safe
     * name, or null when nothing could be created. [readAsset] resolves a
     * project-relative path such as `js/games/snake.js`; the caller hands it
     * Android's asset stream, while host tests can hand it anything.
     */
    fun apply(
        context: Context,
        choice: SetupChoice,
        readAsset: (String) -> String,
        onFileWritten: (String) -> Unit = {},
    ): String? {
        val manager = ProjectManager(context)
        val root = manager.projectsRoot()

        // Law — Skip setup is the app as it was before Phase 97.
        if (SetupFlowPolicy.isLegacySkip(choice)) {
            GameArenaSample.ensure(root, readAsset)
            return if (File(root, GameArenaSample.NAME).isDirectory) GameArenaSample.NAME else null
        }

        val name = SetupFlowPolicy.safeProjectName(choice) ?: return null

        // An existing project is reused rather than refused: the name beat has
        // already refused a *taken* name, so reaching this with a name in use
        // means the user went back and re-picked their own project.
        manager.project(name)?.let { return name }

        if (choice.start == SetupStart.ARCADE) {
            GameArenaSample.seedInto(root, name, readAsset, onFileWritten)
            return if (File(root, name).isDirectory) name else null
        }

        val info = manager.createProject(name, choice.start.projectType).getOrNull() ?: return null
        // The scaffold's own writes, reported in the order ProjectManager makes
        // them (config first, then the starter file it wrote).
        onFileWritten(".codec/project.json")
        onFileWritten(choice.start.entryFile)

        // A C template replaces the scaffold's hello-world with the template's
        // own bytes — the same call shape ProjectScaffold uses, so the write
        // stays inside the confined repository.
        val template = SetupFlowPolicy.templateFor(choice)
        if (template != null) {
            val target = File(info.root, choice.start.entryFile)
            if (target.exists()) target.delete()
            FileTreeRepository.createFile(
                info.root,
                "",
                choice.start.entryFile,
                SetupFlowPolicy.templateSource(template),
            ).getOrElse { return null }
        }
        return name
    }
}
