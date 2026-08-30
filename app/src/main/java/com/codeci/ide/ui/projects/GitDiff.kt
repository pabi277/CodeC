package com.codeci.ide.ui.projects

/**
 * Phase 13 — pure-Kotlin line diff for the Source Control pane's inline diff
 * viewer. The old side is the HEAD blob (`git show HEAD:<path>`), the new side
 * is the working-tree file; computing the diff here keeps the viewer free of
 * `git diff` output parsing and pagers, and makes it fully unit-testable on
 * the host JVM.
 */
enum class DiffOp { CONTEXT, ADD, REMOVE }

data class DiffLine(
    val op: DiffOp,
    val oldNumber: Int?,
    val newNumber: Int?,
    val text: String
)

object DiffEngine {

    /** LCS above this (trimmed) size falls back to a whole-block replace. */
    private const val LCS_LIMIT = 1000

    fun compute(oldText: String, newText: String): List<DiffLine> {
        val oldLines = splitLines(oldText)
        val newLines = splitLines(newText)
        return computeLines(oldLines, newLines)
    }

    private fun computeLines(oldLines: List<String>, newLines: List<String>): List<DiffLine> {
        // Trim the common prefix/suffix so the LCS table only covers the
        // changed middle — typical mobile edits touch a few lines of a file.
        var prefix = 0
        val maxPrefix = minOf(oldLines.size, newLines.size)
        while (prefix < maxPrefix && oldLines[prefix] == newLines[prefix]) prefix++
        var suffix = 0
        val maxSuffix = minOf(oldLines.size - prefix, newLines.size - prefix)
        while (suffix < maxSuffix &&
            oldLines[oldLines.size - 1 - suffix] == newLines[newLines.size - 1 - suffix]
        ) suffix++

        val oldMiddle = oldLines.subList(prefix, oldLines.size - suffix)
        val newMiddle = newLines.subList(prefix, newLines.size - suffix)

        val output = mutableListOf<DiffLine>()
        fun context(index: Int) {
            output += DiffLine(DiffOp.CONTEXT, index + 1, index + 1, oldLines[index])
        }
        for (i in 0 until prefix) context(i)

        val middle = if (oldMiddle.isEmpty() && newMiddle.isEmpty()) {
            emptyList()
        } else if (oldMiddle.size > LCS_LIMIT || newMiddle.size > LCS_LIMIT) {
            // Pathologically large middle section: emit a whole-block replace
            // instead of allocating a huge DP table.
            oldMiddle.mapTo(mutableListOf()) { DiffLine(DiffOp.REMOVE, null, null, it) } +
                newMiddle.map { DiffLine(DiffOp.ADD, null, null, it) }
        } else {
            lcsDiff(oldMiddle, newMiddle, prefix, suffix)
        }
        output += middle

        for (i in maxOf(prefix, oldLines.size - suffix) until oldLines.size) context(i)
        return output
    }

    /**
     * Backtracks an LCS table over the trimmed middle sections. [oldOffset]
     * / [newOffset] are the lengths of the common prefix, used to number the
     * context lines that follow the changed block.
     */
    private fun lcsDiff(
        oldMiddle: List<String>,
        newMiddle: List<String>,
        oldOffset: Int,
        newOffset: Int
    ): List<DiffLine> {
        val n = oldMiddle.size
        val m = newMiddle.size
        // table[i][j] = LCS length of oldMiddle[i..] and newMiddle[j..]
        val table = Array(n + 1) { IntArray(m + 1) }
        for (i in n - 1 downTo 0) {
            for (j in m - 1 downTo 0) {
                table[i][j] = if (oldMiddle[i] == newMiddle[j]) {
                    table[i + 1][j + 1] + 1
                } else {
                    maxOf(table[i + 1][j], table[i][j + 1])
                }
            }
        }

        val lines = mutableListOf<DiffLine>()
        var i = 0
        var j = 0
        while (i < n && j < m) {
            when {
                oldMiddle[i] == newMiddle[j] -> {
                    lines += DiffLine(
                        DiffOp.CONTEXT,
                        oldOffset + i + 1,
                        newOffset + j + 1,
                        oldMiddle[i]
                    )
                    i++
                    j++
                }
                table[i + 1][j] >= table[i][j + 1] -> {
                    lines += DiffLine(DiffOp.REMOVE, oldOffset + i + 1, null, oldMiddle[i])
                    i++
                }
                else -> {
                    lines += DiffLine(DiffOp.ADD, null, newOffset + j + 1, newMiddle[j])
                    j++
                }
            }
        }
        while (i < n) {
            lines += DiffLine(DiffOp.REMOVE, oldOffset + i + 1, null, oldMiddle[i])
            i++
        }
        while (j < m) {
            lines += DiffLine(DiffOp.ADD, null, newOffset + j + 1, newMiddle[j])
            j++
        }
        return lines
    }

    /** Splits into lines without their terminators; a trailing newline adds no extra line. */
    private fun splitLines(text: String): List<String> =
        if (text.isEmpty()) emptyList() else text.split('\n').let { lines ->
            if (lines.lastOrNull()?.isEmpty() == true) lines.dropLast(1) else lines
        }
}
