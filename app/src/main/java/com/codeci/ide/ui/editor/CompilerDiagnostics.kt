package com.codeci.ide.ui.editor

import com.codeci.ide.ui.services.CompilerError
import com.codeci.ide.ui.services.ErrorType

enum class DiagnosticSeverity { ERROR, WARNING }

/**
 * Phase 9 — one editor-visible compiler problem anchored to a 1-based line and
 * column of the current buffer.
 */
data class EditorDiagnostic(
    val line: Int,
    val column: Int,
    val message: String,
    val severity: DiagnosticSeverity
)

/**
 * Pure parser for GCC/Clang/TCC-style diagnostics:
 * `path/file.c:LINE:COL: error|warning|fatal error: message`.
 *
 * Only diagnostics that name the open file (matched by basename) are kept;
 * the structured [CompilerError] list from the embedded compiler is folded in
 * for lines the text parser did not cover.
 */
object CompilerDiagnostics {

    private val PATTERN =
        """(.+?):(\d+):(\d+):\s*(error|warning|fatal error|note|line)\s*(\d+)?:?\s*:?\s*(.*)""".toRegex()

    /** Parse free-form compiler/terminal output, filtered to [targetFileName]. */
    fun parse(output: String, targetFileName: String?): List<EditorDiagnostic> {
        val byLine = LinkedHashMap<Int, EditorDiagnostic>()
        output.lineSequence().forEach { rawLine ->
            val line = rawLine.trim()
            if (line.isEmpty()) return@forEach
            val match = PATTERN.matchEntire(line) ?: return@forEach
            val kind = match.groupValues[4]
            if (kind == "note" || kind == "line") return@forEach
            val file = match.groupValues[1]
            if (targetFileName != null && !sameFile(file, targetFileName)) return@forEach
            val number = match.groupValues[2].toIntOrNull() ?: return@forEach
            if (number < 1) return@forEach
            val column = match.groupValues[3].toIntOrNull()?.coerceAtLeast(1) ?: 1
            val message = match.groupValues[6].ifBlank { match.groupValues[5] }
            val severity =
                if (kind == "warning") DiagnosticSeverity.WARNING else DiagnosticSeverity.ERROR
            val existing = byLine[number]
            if (existing == null || (severity == DiagnosticSeverity.ERROR &&
                    existing.severity == DiagnosticSeverity.WARNING)
            ) {
                byLine[number] = EditorDiagnostic(number, column, message, severity)
            }
        }
        return byLine.values.sortedBy { it.line }
    }

    /** Map the embedded compiler's structured errors (file-relative by design). */
    fun fromCompilerErrors(errors: List<CompilerError>): List<EditorDiagnostic> =
        errors.filter { it.line > 0 }.map { error ->
            EditorDiagnostic(
                line = error.line,
                column = error.column.coerceAtLeast(1),
                message = error.message,
                severity = if (error.type == ErrorType.WARNING) {
                    DiagnosticSeverity.WARNING
                } else {
                    DiagnosticSeverity.ERROR
                }
            )
        }

    /** Structured errors first; text-parsed diagnostics fill the remaining lines. */
    fun combine(
        errors: List<CompilerError>,
        output: String,
        targetFileName: String?
    ): List<EditorDiagnostic> {
        val structured = fromCompilerErrors(errors)
        val structuredLines = structured.mapTo(mutableSetOf()) { it.line }
        val parsed = parse(output, targetFileName).filterNot { it.line in structuredLines }
        return (structured + parsed).sortedBy { it.line }
    }

    private fun sameFile(raw: String, target: String): Boolean {
        val a = raw.substringAfterLast('/').substringAfterLast('\\')
        val b = target.substringAfterLast('/')
        return a == b
    }

    /**
     * Heuristic quick fix. Currently recognizes "expected ';'" style errors
     * (clang: `expected ';' before '}' token`, tcc: ` ';' expected'`), and the
     * embedded-compiler message shape `expected ';'`.
     */
    fun semicolonFixLabel(diag: EditorDiagnostic): String? {
        val lower = diag.message.lowercase()
        val wantsSemicolon = (lower.contains("expected") || lower.contains("need") ||
            lower.contains("want")) && lower.contains("';'")
        return if (wantsSemicolon) "Add missing ';'" else null
    }

    /**
     * Applies the semicolon fix to [lineText]: returns the corrected line text,
     * or null when the fix is not applicable (already ends with a terminator).
     */
    fun applySemicolonFix(lineText: String): String? {
        val trimmed = lineText.trimEnd()
        if (trimmed.isEmpty()) return null
        if (trimmed.endsWith(';') || trimmed.endsWith('{') || trimmed.endsWith('}') ||
            trimmed.endsWith(':') || trimmed.endsWith(',') || trimmed.endsWith("\\")
        ) {
            return null
        }
        return "$trimmed;"
    }
}
