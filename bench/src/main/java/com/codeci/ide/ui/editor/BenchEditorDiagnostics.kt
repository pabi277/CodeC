package com.codeci.ide.ui.editor

/**
 * Phase 25.1 bench spike — trimmed copy of the two types
 * `MultiLanguageSyntaxHighlighter` references from
 * `app/.../ui/editor/CompilerDiagnostics.kt`. The bench never parses
 * compiler output, so only the decoration types travel with the copy;
 * the parser itself stays app-only. Verbatim type definitions.
 */
enum class DiagnosticSeverity { ERROR, WARNING }

data class EditorDiagnostic(
    val line: Int,
    val column: Int,
    val message: String,
    val severity: DiagnosticSeverity
)
