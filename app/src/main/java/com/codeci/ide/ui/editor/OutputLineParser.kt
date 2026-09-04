package com.codeci.ide.ui.editor

/**
 * Phase 11 — parses compiler diagnostics out of raw build output so the
 * Output Panel can render them as clickable error lines.
 *
 * Matches the Clang form `file:line:col: error: message` and the TCC form
 * `file:line: error: message` (the built-in TCC frontend prints no column).
 * The file part may be a bare name, a relative path, or an absolute path;
 * the caller resolves it against the active project folder.
 */
data class OutputDiagnostic(
    val file: String,
    val line: Int,
    /** 0 when the compiler did not print a column (TCC line-only form). */
    val column: Int,
    /** One of: error | fatal error | warning | note. */
    val kind: String,
    val message: String
) {
    val isError: Boolean get() = kind == "error" || kind == "fatal error"
}

object OutputLineParser {

    private val CLANG_LINE = Regex(
        """^([^:\r\n]+):(\d+):(\d+):\s*(error|fatal error|warning|note):\s*(.+)$"""
    )

    // TCC prints `file.c:3: error: ...` — line only, no column.
    private val TCC_LINE = Regex(
        """^([^:\r\n]+):(\d+):\s*(error|fatal error|warning|note):\s*(.+)$"""
    )

    /** Parses a single output line; null when it is not a diagnostic. */
    fun parseLine(raw: String): OutputDiagnostic? {
        val line = raw.trim()
        if (line.isEmpty()) return null
        CLANG_LINE.matchEntire(line)?.let { match ->
            val lineNumber = match.groupValues[2].toIntOrNull() ?: return null
            if (lineNumber < 1) return null
            return OutputDiagnostic(
                file = match.groupValues[1],
                line = lineNumber,
                column = match.groupValues[3].toIntOrNull() ?: 0,
                kind = match.groupValues[4],
                message = match.groupValues[5].trim()
            )
        }
        TCC_LINE.matchEntire(line)?.let { match ->
            val lineNumber = match.groupValues[2].toIntOrNull() ?: return null
            if (lineNumber < 1) return null
            return OutputDiagnostic(
                file = match.groupValues[1],
                line = lineNumber,
                column = 0,
                kind = match.groupValues[3],
                message = match.groupValues[4].trim()
            )
        }
        return null
    }

    /** Parses every diagnostic line in [text]; non-diagnostic lines are skipped. */
    fun parse(text: String): List<OutputDiagnostic> =
        text.lineSequence().mapNotNull { parseLine(it) }.toList()
}

/**
 * Phase 24.6 — a test-runner output line with a colour category. Pure so the
 * Output Panel renders pytest / go test results with the right colours and
 * the mapping is host-unit-testable.
 */
enum class TestLineKind { PASS, FAIL, ERROR, OK, PLAIN }

data class TestLine(val text: String, val kind: TestLineKind)

object TestOutputParser {

    /**
     * Maps a single test-runner line to a colour category. Go summaries get
     * FAIL first (so `ok 1 failed` is not mistaken for a pass); pytest's
     * `PASSED`/`FAILED` and the `FAIL`/`ERROR` prefixes follow; `--- FAIL`
     * separators stay FAIL (a summary line, not a passing one).
     */
    fun parseLine(raw: String): TestLine {
        val trimmed = raw.trim()
        val upper = trimmed.uppercase()
        val kind = when {
            // FAIL takes precedence: `ok 1 failed` is a Go FAIL, never a pass.
            upper.startsWith("FAIL") ||
                upper.startsWith("ERROR") ||
                upper.contains("FAILED") ||
                upper.contains(" FAIL") ||
                upper.contains("FAILURE") -> TestLineKind.FAIL
            upper.contains("ERROR") -> TestLineKind.ERROR
            upper.startsWith("PASSED") || upper.startsWith("OK") -> TestLineKind.PASS
            upper.startsWith("---") && upper.substringAfter("---").startsWith(" FAIL") ->
                TestLineKind.FAIL
            trimmed.startsWith("ok ") -> TestLineKind.PASS
            upper.contains("PASSED") ||
                upper.contains(" PASSED") ||
                upper.contains(" passed") -> TestLineKind.PASS
            upper.startsWith("---") || upper.startsWith("===") -> TestLineKind.OK
            else -> TestLineKind.PLAIN
        }
        return TestLine(raw, kind)
    }
}
