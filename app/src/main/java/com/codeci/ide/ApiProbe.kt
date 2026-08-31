package com.codeci.ide

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathNode
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

/**
 * TEMPORARY compile probe (to be deleted), round 2. Round 1 established:
 * - the first parameter is `pathData: List<PathNode>` (string overloads gone)
 * - `stroke` is a real parameter of type `Brush?`
 * - neither `color` nor `fillColor` is a parameter
 * This round pins down: the PathNode string factory, the fill parameter
 * name/acceptance, and the stroke width/cap/join parameter names.
 */
@Suppress("unused")
object ApiProbe {
    private fun builder(name: String) =
        ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f)

    private val nodes: List<PathNode> = PathNode.path("M0,0 H10")

    /** D1: fill named `fill`, solid Brush. */
    val D1: ImageVector = builder("d1").apply {
        addPath(pathData = nodes, fill = Brush.solid(Color.White))
    }.build()

    /** D2: fill named `fill`, plain Color (Color is-a Brush). */
    val D2: ImageVector = builder("d2").apply {
        addPath(pathData = nodes, fill = Color.White)
    }.build()

    /** D3: stroke + `strokeLineWidth`. */
    val D3: ImageVector = builder("d3").apply {
        addPath(pathData = nodes, stroke = Brush.solid(Color.White), strokeLineWidth = 2f)
    }.build()

    /** D4: stroke Color + `strokeLineCap`. */
    val D4: ImageVector = builder("d4").apply {
        addPath(
            pathData = nodes,
            stroke = Color.White,
            strokeLineWidth = 2f,
            strokeLineCap = StrokeCap.Round
        )
    }.build()

    /** D5: stroke + `strokeLineJoin`. */
    val D5: ImageVector = builder("d5").apply {
        addPath(
            pathData = nodes,
            stroke = Brush.solid(Color.White),
            strokeLineWidth = 2f,
            strokeLineJoin = StrokeJoin.Round
        )
    }.build()
}
