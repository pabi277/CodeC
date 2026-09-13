package com.codeci.ide.ui.editor

/**
 * Phase 46.2 — the one enum that answers "what does this editor open mean?"
 *
 * The owner's row: *"file single click to open in a editor screen with real
 * path and same file edit but not full project to editor. To open a full
 * project in editor the 3 dot will have the option to open in editor."*
 *
 * One editor screen, three modes (PART_46_2 §1). Everything in the "identical"
 * column of the spec's table — buffer editing, save, autosave, undo, find,
 * completions, RUN ▶ — keys off the buffer and the file's language, not off
 * the mode; the mode only decides which PROJECT CHROME is present and whether
 * opening the file writes the app's launch state.
 */
enum class EditorOpenMode {
    /** The whole project: drawer tree, git, run targets, launch state. */
    PROJECT,

    /** Exactly one file of a project, at its real path — no project chrome. */
    SINGLE_FILE,

    /** The scratch context (single-files folder, no project at all). */
    SCRATCH
}

/**
 * The policy over [EditorOpenMode] — the only place "what does this open
 * mean" is answered. Pure Kotlin, host-tested ([EditorOpenModeTest]).
 *
 * (The specced shape put these functions inside an `object EditorOpenMode`
 * sharing the enum's name; Kotlin does not allow an enum and an object with
 * the same name in one package, so the object is `…Policy`. Recorded in
 * PART_46_2 as a naming deviation, not a behavioural one.)
 */
object EditorOpenModePolicy {

    /**
     * Mode resolution for every route shape. [single] is the editor route's
     * `single=1` flag; every caller that does not pass it keeps producing
     * [EditorOpenMode.PROJECT] — that is the whole compatibility story.
     */
    fun forOpen(projectName: String?, fileName: String?, single: Boolean): EditorOpenMode =
        when {
            projectName != null && fileName != null && single -> EditorOpenMode.SINGLE_FILE
            projectName != null && fileName != null -> EditorOpenMode.PROJECT
            else -> EditorOpenMode.SCRATCH
        }

    /** Drawer tree, branch chip, git badges: PROJECT only. */
    fun showsProjectChrome(mode: EditorOpenMode): Boolean = mode == EditorOpenMode.PROJECT

    /**
     * "Open where I left off" is written by PROJECT opens only — a single-file
     * peek must never become the app's launch point (PART_46_2 exit 7).
     */
    fun writesLaunchState(mode: EditorOpenMode): Boolean = mode == EditorOpenMode.PROJECT

    /** Git metadata is a project concern; SCRATCH and SINGLE_FILE show none. */
    fun showsGit(mode: EditorOpenMode): Boolean = mode == EditorOpenMode.PROJECT

    /**
     * Tabs: PROJECT keeps the project tab list; a peek and a scratch buffer
     * are exactly one tab (the spec's "one file = one file").
     */
    fun usesProjectTabList(mode: EditorOpenMode): Boolean = mode == EditorOpenMode.PROJECT

    /**
     * The "real path" the owner asked to see, rendered for the status bar with
     * the `~proj/` alias vocabulary [com.codeci.ide.ui.support.FeedbackDraft]
     * already uses for path shortening — so one vocabulary covers the status
     * bar and a feedback report.
     *
     * - [EditorOpenMode.PROJECT] → the relative path (today's status bar has no
     *   path segment; the SCREEN only renders this segment in SINGLE_FILE
     *   mode, so PROJECT/SCRATCH rendering is unchanged — the function still
     *   answers for all three so the rule has one home).
     * - [EditorOpenMode.SINGLE_FILE] → `~proj/<project>/<relative path>`, the
     *   real path with the private prefix shortened. `~proj/` is only claimed
     *   when the root actually IS the project root; a null/foreign root yields
     *   the bare relative path rather than a lie.
     * - [EditorOpenMode.SCRATCH] → the bare file name.
     */
    fun statusBarPath(mode: EditorOpenMode, projectRoot: String?, relativePath: String): String? {
        val rel = relativePath.trim('/')
        if (rel.isBlank()) return null
        return when (mode) {
            EditorOpenMode.PROJECT -> rel
            EditorOpenMode.SCRATCH -> rel.substringAfterLast('/')
            EditorOpenMode.SINGLE_FILE -> {
                val project = projectRoot?.trim('/')?.substringAfterLast('/')
                if (project.isNullOrEmpty()) rel else "~proj/$project/$rel"
            }
        }
    }
}
