package com.codeci.bench.core

/**
 * Phase 25.1 candidate C-compose2 — document model.
 *
 * Line-partitioned content ("rope-lite"): an array of line strings plus a
 * cumulative line-start index that is MAINTAINED on edit, so offset→line is a
 * binary search, never the O(file) linear scan Compose's own
 * `getLineForOffset` performs (the JetBrains compose-multiplatform#4021 trap
 * the research dossier documents). Edits touch only the lines after the edit
 * point's line (index shift), never a full-file copy.
 *
 * Pure Kotlin: the seeded random-edit differential test in
 * `DocumentBufferTest` plays 10k operations against a StringBuilder oracle.
 */
class DocumentBuffer(text: String) {

    private val lines = ArrayList<String>()
    private val lineStarts = ArrayList<Int>()

    val lineCount: Int get() = lines.size
    val length: Int get() = lineStarts.last() + lines.last().length

    init {
        var start = 0
        for (segment in text.split('\n')) {
            lines.add(segment)
            lineStarts.add(start)
            start += segment.length + 1 // +1 for the '\n'
        }
        if (lines.isEmpty()) {
            lines.add("")
            lineStarts.add(0)
        }
    }

    /** The text of [line], without its newline. */
    fun lineAt(line: Int): String = lines[checkLine(line)]

    /** Buffer offset of the first character of [line]. */
    fun lineStart(line: Int): Int = lineStarts[checkLine(line)]

    fun lineLength(line: Int): Int = lines[checkLine(line)].length

    /** offset → (line, column) by binary search over the start index. */
    fun locate(offset: Int): Pair<Int, Int> {
        val off = offset.coerceIn(0, length)
        var lo = 0
        var hi = lines.size - 1
        while (lo < hi) {
            val mid = (lo + hi + 1) / 2
            if (lineStarts[mid] <= off) lo = mid else hi = mid - 1
        }
        return lo to (off - lineStarts[lo])
    }

    fun toFullString(): String {
        val sb = StringBuilder(length)
        for (i in lines.indices) {
            if (i > 0) sb.append('\n')
            sb.append(lines[i])
        }
        return sb.toString()
    }

    /**
     * Inserts [text] at [offset]. Returns the first line whose content changed
     * and the first line index AFTER the edit that moved (i.e. callers
     * invalidate `[first, movedAfter)` for span caches).
     */
    fun insert(offset: Int, text: String): Pair<Int, Int> {
        require(text.none { it == '\r' }) { "normalized text only" }
        val off = offset.coerceIn(0, length)
        if (text.isEmpty()) return locate(off).first to locate(off).first
        val (line, col) = locate(off)
        val segments = text.split('\n')
        if (segments.size == 1) {
            val old = lines[line]
            lines[line] = old.substring(0, col) + text + old.substring(col)
            shiftStarts(line + 1, text.length)
            return line to line + 1
        }
        val old = lines[line]
        val tail = old.substring(col)
        lines[line] = old.substring(0, col) + segments.first()
        // Each new line starts right after the previous line's text + '\n'.
        var runningStart = lineStarts[line] + lines[line].length + 1
        for (i in 1 until segments.size) {
            lines.add(line + i, segments[i])
            lineStarts.add(line + i, runningStart)
            runningStart += segments[i].length + 1
        }
        // The old tail follows the last inserted segment.
        val lastLine = line + segments.size - 1
        lines[lastLine] = lines[lastLine] + tail
        shiftStarts(lastLine + 1, text.length)
        return line to lastLine + 1
    }

    /** Deletes [start] until [end]; returns (first changed line, first moved line after the edit). */
    fun delete(start: Int, end: Int): Pair<Int, Int> {
        val s = start.coerceIn(0, length)
        val e = end.coerceIn(s, length)
        if (s == e) return locate(s).let { it.first to it.first }
        val (startLine, startCol) = locate(s)
        val (endLine, endCol) = locate(e)
        if (startLine == endLine) {
            val old = lines[startLine]
            lines[startLine] = old.substring(0, startCol) + old.substring(endCol)
            shiftStarts(startLine + 1, -(e - s))
            return startLine to startLine + 1
        }
        val head = lines[startLine].substring(0, startCol)
        val tail = lines[endLine].substring(endCol)
        // Remove lines (startLine, endLine], then join head+tail into startLine.
        repeat(endLine - startLine) { idx ->
            lines.removeAt(startLine + 1)
            lineStarts.removeAt(startLine + 1)
        }
        lines[startLine] = head + tail
        shiftStarts(startLine + 1, -(e - s))
        return startLine to startLine + 1
    }

    /** Moves every line-start at/after [from] by [delta]. O(lines-after-edit). */
    private fun shiftStarts(from: Int, delta: Int) {
        if (delta == 0) return
        for (i in from until lineStarts.size) {
            lineStarts[i] = lineStarts[i] + delta
        }
    }

    private fun checkLine(line: Int): Int {
        require(line in lines.indices) { "line $line out of 0..${lines.size - 1}" }
        return line
    }
}
