package com.codeci.ide.ui.projects

import com.codeci.ide.ui.services.LanguageRegistry

/**
 * Phase 46.2 — the "Open in editor" entry-file rule (PART_46_2 §4, decision
 * recorded in the spec): opening a whole project from the card's ⋮ lands on
 *
 *   1. the project's **launch default**, if set and still present, else
 *   2. the **most recently modified source file**, else
 *   3. the **first source file alphabetically**, else
 *   4. null — an empty project (or one with no source at all) opens with no
 *      file and the caller falls back to the hub's own tree view.
 *
 * Pure Kotlin, host-tested ([com.codeci.ide.ProjectEntryFileTest]). A
 * "source file" is a file the run planner knows ([LanguageRegistry] — C,
 * Python, JS, HTML, …), so binaries and assets are never chosen.
 */
object ProjectEntryFile {

    /** One candidate file: its project-relative path and its mtime. */
    data class Candidate(val relativePath: String, val lastModified: Long)

    /**
     * Pick the file "Open in editor" opens, or null when there is nothing to
     * open. [launchDefault] that no longer resolves (deleted since it was set)
     * falls through to the mtime rule — no crash, no stale pointer.
     */
    fun pick(candidates: List<Candidate>, launchDefault: String?): String? {
        val sources = candidates.filter { isSource(it.relativePath) }
        if (sources.isEmpty()) return null
        launchDefault
            ?.let { def -> sources.firstOrNull { it.relativePath == def } }
            ?.let { return it.relativePath }
        return sources
            .sortedWith(compareByDescending<Candidate> { it.lastModified }.thenBy { it.relativePath })
            .first()
            .relativePath
    }

    /** The same "can RUN act on it" bar the rest of the app uses. */
    private fun isSource(relativePath: String): Boolean =
        LanguageRegistry.forFile(relativePath) != null
}
