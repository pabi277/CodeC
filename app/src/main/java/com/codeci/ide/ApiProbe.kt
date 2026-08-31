package com.codeci.ide

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

/**
 * TEMPORARY compile probe (to be deleted): the CI log host is unreachable
 * from the agent sandbox, so the exact `ImageVector.Builder.addPath`
 * signature of the pinned ui-graphics is probed with one candidate per val.
 * A val that compiles without error is a valid API shape.
 */
@Suppress("unused")
object ApiProbe {
    private fun builder(name: String) =
        ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f)

    /** C1: string pathData + fillColor (AAPT2-generated code convention). */
    val C1: ImageVector = builder("c1").apply {
        addPath(pathData = "M0,0 H10", fillColor = Color.White)
    }.build()

    /** C2: string pathData + color. */
    val C2: ImageVector = builder("c2").apply {
        addPath(pathData = "M0,0 H10", color = Color.White)
    }.build()

    /** C3: drawscope Stroke + explicit Unspecified fill. */
    val C3: ImageVector = builder("c3").apply {
        addPath(
            pathData = "M0,0 H10",
            fillColor = Color.Unspecified,
            stroke = Stroke(width = 2f)
        )
    }.build()

    /** C4: drawscope Stroke, no fill argument. */
    val C4: ImageVector = builder("c4").apply {
        addPath(pathData = "M0,0 H10", stroke = Stroke(width = 2f))
    }.build()
}
