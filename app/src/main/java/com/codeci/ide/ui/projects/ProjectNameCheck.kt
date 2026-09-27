package com.codeci.ide.ui.projects

/**
 * Phase 66.1 — the verdict a project-name field shows *before* the user taps
 * Create / Rename / Import.
 *
 * Why this exists: the New Project, Rename Project and Import ZIP dialogs used
 * to learn that a name was invalid or already taken only from
 * `ProjectManager` — after the tap — and the message went to the snackbar
 * **behind the dialog**, which stayed open. That is the same class of bug the
 * owner reported for the clone dialog in Phase 40.1 (*"it shows error in the
 * background i can't see it"*): Create looked like it did nothing.
 *
 * The rules here are the ones `ProjectManager.createProject` and
 * `FileManagerViewModel.renameProject` really enforce — the same sanitizer
 * ([ProjectPathUtils.sanitizeProjectName]) and the same "a project with that
 * name already exists" — so the field can say it first. Pure Kotlin, no
 * Android, host-tested by `ProjectNameCheckTest`.
 */
enum class ProjectNameProblem {
    /** Nothing typed yet (or only spaces): no error to show, nothing to submit. */
    EMPTY,
    /** The sanitizer refuses it (`/`, `\`, control characters, `.` or `..`). */
    INVALID,
    /** A project already has this name (exact, trimmed match). */
    TAKEN,
}

/**
 * [safeName] is the trimmed name the operation would really use, or null when
 * there is none ([ProjectNameProblem.EMPTY] / [ProjectNameProblem.INVALID]).
 * [problem] is null when the name can be submitted as it is.
 */
data class ProjectNameVerdict(
    val safeName: String?,
    val problem: ProjectNameProblem?,
) {
    val ok: Boolean get() = problem == null && safeName != null

    /** True when the field should draw its error state (a typed, wrong name). */
    val showsError: Boolean
        get() = problem == ProjectNameProblem.INVALID || problem == ProjectNameProblem.TAKEN
}

object ProjectNameCheck {

    /**
     * The verdict for [raw] against the names in [existing].
     *
     * [current] is the project's own name in a rename: renaming a project to
     * the name it already has is a no-op, not a collision, so it is never
     * [ProjectNameProblem.TAKEN].
     *
     * [allowTaken] is for flows that de-duplicate on their own — the ZIP import
     * appends `_2`, `_3`, … (`ProjectsHub.uniqueProjectName`) rather than
     * refusing — so a taken name is reported as ok there and the dialog can say
     * what the import will be called instead of blocking it.
     */
    fun check(
        raw: String,
        existing: Collection<String>,
        current: String? = null,
        allowTaken: Boolean = false,
    ): ProjectNameVerdict {
        if (raw.isBlank()) return ProjectNameVerdict(safeName = null, problem = ProjectNameProblem.EMPTY)
        val safe = ProjectPathUtils.sanitizeProjectName(raw)
            ?: return ProjectNameVerdict(safeName = null, problem = ProjectNameProblem.INVALID)
        if (current != null && safe == current.trim()) return ProjectNameVerdict(safe, problem = null)
        val taken = existing.any { it.trim() == safe }
        if (taken && !allowTaken) return ProjectNameVerdict(safe, problem = ProjectNameProblem.TAKEN)
        return ProjectNameVerdict(safe, problem = null)
    }

    /**
     * The name a de-duplicating import will really use for [raw] — the same
     * `_2`, `_3` scheme the ZIP and clone paths apply — or null when [raw] is
     * not a usable name at all. Lets the Import ZIP dialog say *"will be imported
     * as my_project_2"* instead of surprising the user afterwards.
     */
    fun importedNameFor(raw: String, existing: Collection<String>): String? {
        val safe = ProjectPathUtils.sanitizeProjectName(raw) ?: return null
        return ProjectsHub.uniqueProjectName(safe, existing.map { it.trim() }.toSet())
    }
}
