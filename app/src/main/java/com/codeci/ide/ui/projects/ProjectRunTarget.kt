package com.codeci.ide.ui.projects

import com.codeci.ide.ui.services.LanguageRegistry
import com.codeci.ide.ui.utils.WebFileSupport
import java.io.File

/**
 * Phase 33 (first-hour UX) — the RUN ▶ chooser. When a project has a
 * main/index file ([ProjectConfig.entry]: main.c, index.html, main.py, …)
 * that is NOT the file currently open, RUN asks which one to run instead of
 * silently running the open file.
 *
 * Pure Kotlin — host-testable.
 */
object ProjectRunTarget {

    /**
     * The project's main/index file to offer in the RUN chooser, as a
     * root-relative path. Returns null when there is nothing to choose: the
     * entry does not resolve to a real file under [root], or it IS the
     * [openFile].
     */
    fun chooserEntry(root: File, entry: String, openFile: String?): String? {
        val entryRel = ProjectPathUtils.sanitizeRelativePath(entry) ?: return null
        val openRel = openFile?.let { ProjectPathUtils.sanitizeRelativePath(it) }
        if (entryRel == openRel) return null
        val file = ProjectPathUtils.resolveInside(root, entryRel) ?: return null
        return if (file.isFile) entryRel else null
    }

    /**
     * The full "should RUN ask" decision. [openRunnable] is true when the
     * open file is itself a run/preview target (HTML, or a source file the
     * language registry can run); asking otherwise would offer a choice whose
     * "current file" arm does nothing useful.
     */
    fun shouldAsk(root: File, entry: String, openFile: String?, openRunnable: Boolean): Boolean {
        if (!openRunnable) return false
        return chooserEntry(root, entry, openFile) != null
    }

    /**
     * True when [rel] is a source file the run panel can compile/execute —
     * a language with a run profile that is NOT the web preview. This is what
     * lets a C/Python file inside a `web` project be RUN instead of always
     * previewing `index.html` (Phase 33: "html project with c files").
     */
    fun isRunnableSource(rel: String?): Boolean {
        if (rel == null) return false
        return !WebFileSupport.isHtml(rel) && LanguageRegistry.forFile(rel) != null
    }
}
