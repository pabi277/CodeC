package com.codeci.ide.ui.terminal

/**
 * Phase 19.2 device round 3 (owner: "terminal feels lagging"): the renderer
 * drew EVERY cell with its own measureText + drawText native call pair —
 * ~2600 per frame on a 70x37 grid. Since the snapped grid makes the font's
 * advance EQUAL the cell width, a span of PLAIN columns can be drawn with a
 * single drawText and lands on exactly the same per-column positions.
 *
 * A column is batchable when its character is ASCII printable (the bundled
 * monospace font covers it with the grid-snapped advance) and it carries no
 * combining cluster. Cluster columns, wide cells and non-ASCII (fallback
 * font, unsnapped advance) must still be drawn individually.
 */
object GlyphSpans {

    /** ASCII printable — covered by the grid font with advance == cellW. */
    fun isPlain(c: Char): Boolean = c in ' '..'~'

    /**
     * Length of the run of batchable columns starting at [from].
     * Stops at the first cluster column, non-plain character, or
     * [endExclusive]. Never negative; 0 means the column at [from] must be
     * drawn individually.
     */
    fun spanLength(
        text: String,
        clusters: Map<Int, String>?,
        from: Int,
        endExclusive: Int
    ): Int {
        var i = from.coerceIn(0, text.length)
        val end = endExclusive.coerceIn(0, text.length)
        var n = 0
        while (i < end) {
            if (clusters != null && clusters.containsKey(i)) break
            if (!isPlain(text[i])) break
            n++
            i++
        }
        return n
    }
}
