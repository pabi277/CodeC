package com.codeci.ide.ui.editor

import com.codeci.ide.ui.components.EditorTabUi

/**
 * Phase 16 — the pure computations the editor shell renders, kept out of the
 * composables so CI can assert them: per-tab dirtiness drives the tab-bar
 * ● dot, and the status-bar errors badge counts severities and picks the
 * first error to jump to (tap-to-jump per the Phase 16 spec).
 */
object EditorShellUi {

    /** Tab strip row model: dirty = buffer drifted from what is on disk. */
    fun tabModel(relativePath: String, bufferText: String, savedText: String): EditorTabUi =
        EditorTabUi(
            path = relativePath,
            name = relativePath.substringAfterLast('/'),
            isDirty = bufferText != savedText
        )

    fun errorCount(diagnostics: List<EditorDiagnostic>): Int =
        diagnostics.count { it.severity == DiagnosticSeverity.ERROR }

    fun warningCount(diagnostics: List<EditorDiagnostic>): Int =
        diagnostics.count { it.severity == DiagnosticSeverity.WARNING }

    /** First error by document position — what the errors badge taps to. */
    fun firstError(diagnostics: List<EditorDiagnostic>): EditorDiagnostic? =
        diagnostics.filter { it.severity == DiagnosticSeverity.ERROR }
            .minWithOrNull(compareBy({ it.line }, { it.column }))
}
