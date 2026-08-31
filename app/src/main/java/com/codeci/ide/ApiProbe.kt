package com.codeci.ide

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.solid
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

/**
 * TEMPORARY compile probe (to be deleted), round 3. Round 2 established:
 * `fill`/`stroke`/`strokeLineWidth`/`strokeLineCap`/`strokeLineJoin` are all
 * real parameters of type Brush?/Float/StrokeCap/StrokeJoin; Color is NOT a
 * Brush (must convert); `PathNode` is NOT in androidx.compose.ui.graphics.
 * This round finds the PathNode package + string factory and verifies the
 * `Color.solid()` extension. If E1 and E2 disagree, the error reveals which
 * package is the real one.
 */
@Suppress("unused")
object ApiProbe {
    private fun builder(name: String) =
        ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f)

    /** E1: PathNode from androidx.compose.ui.graphics.vector. */
    val E1: ImageVector = builder("e1").apply {
        addPath(
            pathData = androidx.compose.ui.graphics.vector.PathNode.path("M0,0 H10"),
            fill = Color.White.solid()
        )
    }.build()

    /** E2: PathNode from androidx.compose.ui.graphics (fully qualified). */
    val E2: ImageVector = builder("e2").apply {
        addPath(
            pathData = androidx.compose.ui.graphics.PathNode.path("M0,0 H10"),
            stroke = Color.White.solid(),
            strokeLineWidth = 2f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round
        )
    }.build()
}
