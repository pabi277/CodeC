package com.codeci.ide.ui.projects

import com.codeci.ide.ui.services.LanguageRegistry
import com.codeci.ide.ui.utils.WebFileSupport
import java.io.File

/**
 * Phase 33 (first-hour UX) — the RUN ▶ chooser. The "default run file" is a
 * USER-set choice (the project's launch default, `ProjectConfig.launchDefault`),
 * not an automatic guess from the project type. When a default is set and it
 * is NOT the file currently open, RUN asks which one to run; with no default
 * set, RUN just runs the open file.
 *
 * Pure Kotlin — host-testable.
 */
object ProjectRunTarget {

    /**
     * True when [rel] is a file RUN can act on: the web preview (HTML) or the
     * run panel (a language profile). Directories, and files with no profile
     * (css, json, md, …), are not run targets.
     */
    fun isRunTarget(rel: String?): Boolean =
        rel != null && LanguageRegistry.forFile(rel) != null

    /**
     * True when [rel] is a source file the run panel can compile/execute — a
     * language with a run profile that is NOT the web preview. This is what
     * lets a C/Python file inside a `web` project be RUN instead of always
     * previewing `index.html` (Phase 33: "html project with c files").
     */
    fun isRunnableSource(rel: String?): Boolean =
        rel != null && !WebFileSupport.isHtml(rel) && LanguageRegistry.forFile(rel) != null

    /**
     * The raw resolution of a run-target path under [root]: the root-relative
     * [entry] when it names a real file there that differs from [openFile],
     * else null. (Traversal / absolute paths are refused by the confinement
     * rules in [ProjectPathUtils].)
     */
    fun chooserEntry(root: File, entry: String, openFile: String?): String? {
        val entryRel = ProjectPathUtils.sanitizeRelativePath(entry) ?: return null
        val openRel = openFile?.let { ProjectPathUtils.sanitizeRelativePath(it) }
        if (entryRel == openRel) return null
        val file = ProjectPathUtils.resolveInside(root, entryRel) ?: return null
        return if (file.isFile) entryRel else null
    }

    /**
     * The default file to offer in the RUN chooser. Returns null when there
     * is nothing to choose: no default set, the default IS the open file, the
     * default does not resolve to a real file under [root], or either file is
     * not a run target.
     */
    fun chooserDefault(root: File, default: String?, openFile: String?): String? {
        if (default == null || openFile == null) return null
        if (!isRunTarget(default)) return null
        if (!isRunTarget(openFile)) return null
        return chooserEntry(root, default, openFile)
    }
}
