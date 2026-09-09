package com.codeci.ide.ui.editor

import java.io.File

/**
 * Phase 32.3 — resolves the file named by a compiler diagnostic for a
 * tap-to-line jump, and decides which file a tap actually lands on. A
 * diagnostic that names a compiler temp (`source_<stamp>.c`) or a file that
 * is not a real file inside the run's folder falls back to the ACTIVE file:
 * the run was launched from it, so the caret belongs in the user's file —
 * never on a nonexistent temp copy.
 *
 * Pure Kotlin (java.io only) — host-testable.
 */
object OutputDiagnosticTarget {

    /** True when the name is a compiler-generated temp source, not a user file. */
    fun isTempSource(name: String): Boolean {
        val base = name.substringAfterLast('/').substringAfterLast('\\')
        return base.startsWith("source_") ||
            name.startsWith("/tmp/") || name.startsWith("tmp/") ||
            name.contains("/tmp/") || name.contains("\\tmp\\")
    }

    /**
     * Resolves [raw] against [root] with the editor's confinement rule:
     * absolute paths must live under the root; relative paths are resolved
     * inside it. Returns the file, or null when it is not a real file under
     * the root.
     */
    fun resolve(root: File, raw: String): File? {
        val candidate = if (raw.startsWith('/')) File(raw) else File(root, raw)
        if (!candidate.isFile) return null
        val rootPath = root.absolutePath
        val candidatePath = candidate.absolutePath
        if (candidatePath != rootPath && !candidatePath.startsWith(rootPath + File.separator)) return null
        return candidate
    }

    /**
     * The path a tap must jump to. When [raw] names a real file under [root]
     * the result is that file's root-relative path; otherwise it is
     * [activeFile] (the user's file, as the caller already knows it).
     */
    fun targetOrActive(root: File, raw: String, activeFile: String): String {
        val resolved = resolve(root, raw) ?: return activeFile
        val relative = runCatching { resolved.toRelativeString(root) }.getOrNull()
        return if (relative != null && !relative.startsWith("..")) relative else activeFile
    }
}
