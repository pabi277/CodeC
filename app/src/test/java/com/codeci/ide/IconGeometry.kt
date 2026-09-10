package com.codeci.ide

/**
 * Phase 38.1 — strict, honest geometry for the icon vector XMLs.
 *
 * This is TEST-ONLY code (no production behaviour), so it is strict on
 * purpose: it parses exactly the path grammar our icon files use —
 * absolute `M L H V Z` — and REFUSES everything else (relative commands,
 * arcs, curves) with an exception instead of guessing a box. A parser
 * that silently mis-reads is worse than no parser: it would hand the
 * safe-zone test a false green. `SafeZoneMathTest` pins the math so the
 * parser cannot drift.
 */
object IconGeometry {

    data class Box(
        val minX: Double,
        val minY: Double,
        val maxX: Double,
        val maxY: Double
    ) {
        val width: Double get() = maxX - minX
        val height: Double get() = maxY - minY
        val centerX: Double get() = (minX + maxX) / 2
        val centerY: Double get() = (minY + maxY) / 2

        /** True when the whole box lies inside [lo, hi] on BOTH axes. */
        fun inside(lo: Double, hi: Double): Boolean =
            minX >= lo && minY >= lo && maxX <= hi && maxY <= hi
    }

    data class VectorPath(
        val fillColor: String?,
        val pathData: String
    ) {
        val hasArc: Boolean get() = pathData.contains('A') || pathData.contains('a')
    }

    data class VectorDoc(
        val viewportWidth: Double,
        val viewportHeight: Double,
        val paths: List<VectorPath>
    )

    private val NUMBER = Regex("[-+]?(?:\\d+\\.?\\d*|\\.\\d+)")
    private val PATH_ATTRS = Regex("<path\\b([^>]*?)/>")
    private val FILL = Regex("android:fillColor=\"([^\"]+)\"")
    private val DATA = Regex("android:pathData=\"([^\"]+)\"")
    private val ALLOWED = "MLHVZ".toList()

    /** Every absolute anchor point of the path, in order. */
    fun points(pathData: String): List<Pair<Double, Double>> {
        val tokens = tokenize(pathData)
        val pts = mutableListOf<Pair<Double, Double>>()
        var i = 0
        while (i < tokens.size) {
            val cmd = tokens[i]
            if (cmd !is Tok.Cmd) throw IllegalArgumentException("pathData must start with a command")
            if (cmd.c !in ALLOWED) {
                throw IllegalArgumentException(
                    "unsupported command '${cmd.c}' (absolute M/L/H/V only — the icon files are ours, " +
                        "so anything else means the file changed under the test)"
                )
            }
            i++
            when (cmd.c) {
                'M', 'L' -> {
                    val x = nextNumber(tokens, i, cmd.c); val y = nextNumber(tokens, i + 1, cmd.c)
                    pts += x to y
                    i += 2
                }
                'H' -> {
                    val x = nextNumber(tokens, i, cmd.c)
                    pts += x to (pts.lastOrNull()?.second ?: throw IllegalArgumentException("H before any point"))
                    i += 1
                }
                'V' -> {
                    val y = nextNumber(tokens, i, cmd.c)
                    pts += (pts.lastOrNull()?.first ?: throw IllegalArgumentException("V before any point")) to y
                    i += 1
                }
                // Z closes; no coordinates
            }
        }
        if (pts.isEmpty()) throw IllegalArgumentException("pathData has no points")
        return pts
    }

    fun boundingBox(pathData: String): Box {
        val pts = points(pathData)
        val xs = pts.map { it.first }
        val ys = pts.map { it.second }
        return Box(xs.min(), ys.min(), xs.max(), ys.max())
    }

    /** Parse one of our `<vector>` XMLs (regex-level, our own file format). */
    fun parseVectorXml(xml: String): VectorDoc {
        val vw = Regex("android:viewportWidth=\"([^\"]+)\"").find(xml)?.groupValues?.get(1)?.toDoubleOrNull()
            ?: throw IllegalArgumentException("no viewportWidth")
        val vh = Regex("android:viewportHeight=\"([^\"]+)\"").find(xml)?.groupValues?.get(1)?.toDoubleOrNull()
            ?: throw IllegalArgumentException("no viewportHeight")
        val paths = PATH_ATTRS.findAll(xml).map { m ->
            val attrs = m.groupValues[1]
            VectorPath(
                fillColor = FILL.find(attrs)?.groupValues?.get(1),
                pathData = DATA.find(attrs)?.groupValues?.get(1)
                    ?: throw IllegalArgumentException("<path> without pathData")
            )
        }.toList()
        if (paths.isEmpty()) throw IllegalArgumentException("vector has no <path>")
        return VectorDoc(vw, vh, paths)
    }

    private fun nextNumber(tokens: List<Tok>, index: Int, cmd: Char): Double {
        val t = tokens.getOrNull(index)
        if (t !is Tok.Num) {
            throw IllegalArgumentException("command '$cmd' needs ${if (cmd == 'M' || cmd == 'L') 2 else 1} number(s)")
        }
        return t.v
    }

    private fun tokenize(pathData: String): List<Tok> {
        val tokens = mutableListOf<Tok>()
        var i = 0
        while (i < pathData.length) {
            val c = pathData[i]
            if (c.isWhitespace() || c == ',') { i++; continue }
            if (c.isLetter()) { tokens += Tok.Cmd(c); i++; continue }
            val m = NUMBER.matchAt(pathData, i)
                ?: throw IllegalArgumentException("bad token at $i in pathData")
            tokens += Tok.Num(m.value.toDouble())
            i = m.range.last + 1
        }
        return tokens
    }

    private sealed class Tok {
        data class Cmd(val c: Char) : Tok()
        data class Num(val v: Double) : Tok()
    }
}
