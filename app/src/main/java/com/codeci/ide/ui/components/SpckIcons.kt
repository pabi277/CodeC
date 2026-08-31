package com.codeci.ide.ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathNode
import androidx.compose.ui.unit.dp

/**
 * Phase 15/16 — clean-room vector glyphs for the Spck-style UI.
 *
 * The Material icon set has no git-branch, QR-scan, zip-file, clone, or
 * two-tone language-mark glyphs, so the design's exact shapes are drawn here
 * from scratch (public design mockups only — no copied assets). Single-color
 * icons use a white base color so callers can re-tint with `Icon(tint=...)`;
 * the two-tone marks (Python, HTML) keep fixed colors and must NOT be tinted.
 *
 * API note (verified against CI): the pinned ui-graphics builds vectors from
 * `List<PathNode>` — `ImageVector.Builder.addPath(pathData, pathFillType, name,
 * fill: Brush?, fillAlpha, stroke: Brush?, strokeAlpha, strokeLineWidth,
 * strokeLineCap, strokeLineJoin, ...)` — so colors are wrapped in
 * [SolidColor] and every path is spelled out as PathNode commands
 * (`PathNode.MoveTo/LineTo/HorizontalTo/VerticalTo/CurveTo/ArcTo/Close`; no
 * string path data, no `fillColor`/`strokeWidth`-style params, and `Color` is
 * not a `Brush`). `DrawScope.drawLine` takes `end` (not `stop`); the bottom
 * bar uses `navigationBarsPadding()`.
 */
object SpckIcons {

    /** A full circle as two half-arcs: [cx]/[cy] center, [r] radius. */
    private fun circle(cx: Float, cy: Float, r: Float): List<PathNode> = listOf(
        PathNode.MoveTo(cx - r, cy),
        PathNode.ArcTo(r, r, 0f, isMoreThanHalf = true, isPositiveArc = false, cx + r, cy),
        PathNode.ArcTo(r, r, 0f, isMoreThanHalf = true, isPositiveArc = false, cx - r, cy),
        PathNode.Close
    )

    /** An axis-aligned rectangle starting top-left. */
    private fun rect(x: Float, y: Float, w: Float, h: Float): List<PathNode> = listOf(
        PathNode.MoveTo(x, y),
        PathNode.HorizontalTo(x + w),
        PathNode.VerticalTo(y + h),
        PathNode.HorizontalTo(x),
        PathNode.Close
    )

    /** Outline-only path (white stroke; callers re-tint via Icon tint). */
    private fun ImageVector.Builder.strokePath(
        nodes: List<PathNode>,
        width: Float,
        cap: StrokeCap = StrokeCap.Butt,
        join: StrokeJoin = StrokeJoin.Miter
    ) {
        addPath(
            pathData = nodes,
            stroke = SolidColor(Color.White),
            strokeLineWidth = width,
            strokeLineCap = cap,
            strokeLineJoin = join
        )
    }

    /** Solid path in an explicit color (tint-proof marks). */
    private fun ImageVector.Builder.fillPath(nodes: List<PathNode>, color: Color) {
        addPath(pathData = nodes, fill = SolidColor(color))
    }

    private fun builder(name: String): ImageVector.Builder = ImageVector.Builder(
        name = name,
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    )

    /** Git branch glyph (the "⌥" mark used on branch chips and cards). */
    val GitBranch: ImageVector = builder("spck.gitBranch").apply {
        // trunk + curving branch line
        strokePath(
            nodes = listOf(PathNode.MoveTo(6f, 8.4f), PathNode.LineTo(6f, 15.5f)),
            width = 2f,
            cap = StrokeCap.Round
        )
        strokePath(
            nodes = listOf(
                PathNode.MoveTo(18f, 9.4f),
                PathNode.CurveTo(18f, 13.8f, 13.2f, 15.5f, 6.2f, 15.5f)
            ),
            width = 2f,
            cap = StrokeCap.Round
        )
        // three branch nodes
        fillPath(circle(6f, 6f, 2.3f), Color.White)
        fillPath(circle(6f, 18f, 2.3f), Color.White)
        fillPath(circle(18f, 7f, 2.3f), Color.White)
    }.build()

    /** QR scanner (clone dialog URL field, Spck's QR repo import). */
    val QrScan: ImageVector = builder("spck.qrScan").apply {
        // three finder squares (hollow)
        fillPath(listOf(PathNode.MoveTo(3f, 3f), PathNode.HorizontalTo(11f), PathNode.VerticalTo(5f), PathNode.HorizontalTo(5f), PathNode.VerticalTo(11f), PathNode.HorizontalTo(3f), PathNode.Close), Color.White)
        fillPath(rect(5.6f, 5.6f, 2.8f, 2.8f), Color.White)
        fillPath(listOf(PathNode.MoveTo(13f, 3f), PathNode.HorizontalTo(21f), PathNode.VerticalTo(11f), PathNode.HorizontalTo(19f), PathNode.VerticalTo(5f), PathNode.HorizontalTo(13f), PathNode.Close), Color.White)
        fillPath(rect(15.6f, 5.6f, 2.8f, 2.8f), Color.White)
        fillPath(listOf(PathNode.MoveTo(3f, 13f), PathNode.VerticalTo(21f), PathNode.HorizontalTo(11f), PathNode.VerticalTo(19f), PathNode.HorizontalTo(5f), PathNode.VerticalTo(13f), PathNode.Close), Color.White)
        fillPath(rect(5.6f, 15.6f, 2.8f, 2.8f), Color.White)
        // data modules
        fillPath(rect(13f, 13f, 2.5f, 2.5f), Color.White)
        fillPath(rect(17.5f, 13.5f, 3f, 3f), Color.White)
        fillPath(rect(13f, 18f, 2.5f, 2.5f), Color.White)
        fillPath(rect(17.5f, 18f, 3f, 2.5f), Color.White)
    }.build()

    /** Zip archive file (Import ZIP sheet row). */
    val ZipFile: ImageVector = builder("spck.zipFile").apply {
        strokePath(
            nodes = listOf(
                PathNode.MoveTo(6.2f, 2.5f),
                PathNode.HorizontalTo(14f),
                PathNode.LineTo(18.8f, 7.3f),
                PathNode.VerticalTo(21.5f),
                PathNode.HorizontalTo(6.2f),
                PathNode.Close
            ),
            width = 1.8f,
            join = StrokeJoin.Round
        )
        strokePath(
            nodes = listOf(
                PathNode.MoveTo(14f, 2.5f),
                PathNode.VerticalTo(7.3f),
                PathNode.HorizontalTo(18.8f)
            ),
            width = 1.8f,
            join = StrokeJoin.Round
        )
        // zipper
        fillPath(rect(11.2f, 8.2f, 1.6f, 2f), Color.White)
        fillPath(rect(11.2f, 11.5f, 1.6f, 2f), Color.White)
        fillPath(rect(11.2f, 14.8f, 1.6f, 2f), Color.White)
        fillPath(rect(10.5f, 17.6f, 3f, 3f), Color.White)
    }.build()

    /** Clone repo mark: a branch node with a download arrow (sheet row). */
    val CloneRepo: ImageVector = builder("spck.cloneRepo").apply {
        strokePath(
            nodes = listOf(PathNode.MoveTo(8f, 7.2f), PathNode.LineTo(8f, 15.8f)),
            width = 2f,
            cap = StrokeCap.Round
        )
        strokePath(
            nodes = listOf(
                PathNode.MoveTo(17.5f, 10.2f),
                PathNode.CurveTo(17.5f, 14f, 12.8f, 14.8f, 8f, 14.8f)
            ),
            width = 2f,
            cap = StrokeCap.Round
        )
        fillPath(circle(8f, 5f, 2.2f), Color.White)
        fillPath(circle(17.5f, 8f, 2.2f), Color.White)
        // download stem + arrowhead
        strokePath(
            nodes = listOf(PathNode.MoveTo(8f, 15.8f), PathNode.LineTo(8f, 19.2f)),
            width = 2f,
            cap = StrokeCap.Round
        )
        fillPath(
            listOf(
                PathNode.MoveTo(8f, 21f),
                PathNode.LineTo(5.6f, 17.6f),
                PathNode.HorizontalTo(10.4f),
                PathNode.Close
            ),
            Color.White
        )
    }.build()

    /** Document with a plus (New Project sheet row). */
    val FilePlus: ImageVector = builder("spck.filePlus").apply {
        strokePath(
            nodes = listOf(
                PathNode.MoveTo(5f, 2.5f),
                PathNode.HorizontalTo(13.5f),
                PathNode.LineTo(19f, 8f),
                PathNode.VerticalTo(21.5f),
                PathNode.HorizontalTo(5f),
                PathNode.Close
            ),
            width = 1.8f,
            join = StrokeJoin.Round
        )
        strokePath(
            nodes = listOf(
                PathNode.MoveTo(13.5f, 2.5f),
                PathNode.VerticalTo(8f),
                PathNode.HorizontalTo(19f)
            ),
            width = 1.8f,
            join = StrokeJoin.Round
        )
        // plus sign
        strokePath(
            nodes = listOf(PathNode.MoveTo(10.5f, 15.2f), PathNode.HorizontalTo(17.5f)),
            width = 2f,
            cap = StrokeCap.Round
        )
        strokePath(
            nodes = listOf(PathNode.MoveTo(14f, 11.7f), PathNode.VerticalTo(18.7f)),
            width = 2f,
            cap = StrokeCap.Round
        )
    }.build()

    /** Outline folder (Open Folder sheet row, drawer tree). */
    val FolderLine: ImageVector = builder("spck.folderLine").apply {
        strokePath(
            nodes = listOf(
                PathNode.MoveTo(3f, 7f),
                PathNode.CurveTo(3f, 5.9f, 3.9f, 5f, 5f, 5f),
                PathNode.HorizontalTo(9.6f),
                PathNode.LineTo(11.8f, 7.6f),
                PathNode.HorizontalTo(19f),
                PathNode.CurveTo(20.1f, 7.6f, 21f, 8.5f, 21f, 9.6f),
                PathNode.VerticalTo(17.5f),
                PathNode.CurveTo(21f, 18.6f, 20.1f, 19.5f, 19f, 19.5f),
                PathNode.HorizontalTo(5f),
                PathNode.CurveTo(3.9f, 19.5f, 3f, 18.6f, 3f, 17.5f),
                PathNode.Close
            ),
            width = 1.8f,
            join = StrokeJoin.Round
        )
    }.build()

    /**
     * Simplified two-snake Python mark (fixed colors; do not tint).
     * Top-left snake light blue, bottom-right snake yellow.
     */
    val PythonLogo: ImageVector = builder("spck.pythonLogo").apply {
        fillPath(
            listOf(
                PathNode.MoveTo(12f, 2.5f),
                PathNode.CurveTo(8.5f, 2.5f, 7f, 3.7f, 7f, 5.9f),
                PathNode.VerticalTo(8.5f),
                PathNode.HorizontalTo(12f),
                PathNode.VerticalTo(9.5f),
                PathNode.HorizontalTo(5.5f),
                PathNode.CurveTo(3.3f, 9.5f, 2f, 10.9f, 2f, 13.6f),
                PathNode.CurveTo(2f, 16.3f, 3.3f, 17.8f, 5.5f, 17.8f),
                PathNode.HorizontalTo(7.7f),
                PathNode.VerticalTo(15.1f),
                PathNode.CurveTo(7.7f, 13f, 9.3f, 11.6f, 11.5f, 11.6f),
                PathNode.HorizontalTo(16.5f),
                PathNode.CurveTo(18.3f, 11.6f, 19.7f, 10.2f, 19.7f, 8.4f),
                PathNode.VerticalTo(5.9f),
                PathNode.CurveTo(19.7f, 3.7f, 18f, 2.5f, 14.5f, 2.5f),
                PathNode.Close
            ),
            Color(0xFF6FA8DC)
        )
        fillPath(
            listOf(
                PathNode.MoveTo(12f, 21.5f),
                PathNode.CurveTo(15.5f, 21.5f, 17f, 20.3f, 17f, 18.1f),
                PathNode.VerticalTo(15.5f),
                PathNode.HorizontalTo(12f),
                PathNode.VerticalTo(14.5f),
                PathNode.HorizontalTo(18.5f),
                PathNode.CurveTo(20.7f, 14.5f, 22f, 13.1f, 22f, 10.4f),
                PathNode.CurveTo(22f, 7.7f, 20.7f, 6.2f, 18.5f, 6.2f),
                PathNode.HorizontalTo(16.3f),
                PathNode.VerticalTo(8.9f),
                PathNode.CurveTo(16.3f, 11f, 14.7f, 12.4f, 12.5f, 12.4f),
                PathNode.HorizontalTo(7.5f),
                PathNode.CurveTo(5.7f, 12.4f, 4.3f, 13.8f, 4.3f, 15.6f),
                PathNode.VerticalTo(18.1f),
                PathNode.CurveTo(4.3f, 20.3f, 6f, 21.5f, 9.5f, 21.5f),
                PathNode.Close
            ),
            Color(0xFFFFD43B)
        )
        // eyes
        fillPath(circle(11.1f, 4.6f, 0.9f), Color.White)
        fillPath(circle(13.7f, 18.5f, 0.9f), Color.White)
    }.build()

    /** HTML shield mark (fixed orange + white; do not tint). */
    val HtmlShield: ImageVector = builder("spck.htmlShield").apply {
        fillPath(
            listOf(
                PathNode.MoveTo(4.8f, 2.5f),
                PathNode.HorizontalTo(19.2f),
                PathNode.LineTo(18.1f, 19.3f),
                PathNode.LineTo(12f, 22f),
                PathNode.LineTo(5.9f, 19.3f),
                PathNode.Close
            ),
            Color(0xFFE44D26)
        )
        fillPath(rect(8.2f, 6.2f, 7.6f, 2.2f), Color.White)
        fillPath(rect(8.7f, 10.2f, 6.6f, 2.2f), Color.White)
        fillPath(rect(9.4f, 14.2f, 3.5f, 2.2f), Color.White)
    }.build()

    /** Open book outline (README / markdown files). */
    val BookLine: ImageVector = builder("spck.bookLine").apply {
        // left page
        strokePath(
            nodes = listOf(
                PathNode.MoveTo(12f, 5.4f),
                PathNode.CurveTo(10.2f, 3.9f, 7.8f, 3.5f, 4.8f, 4.1f),
                PathNode.VerticalTo(19.3f),
                PathNode.CurveTo(7.8f, 18.7f, 10.2f, 19.1f, 12f, 20.6f)
            ),
            width = 1.6f,
            cap = StrokeCap.Round
        )
        // right page
        strokePath(
            nodes = listOf(
                PathNode.MoveTo(12f, 5.4f),
                PathNode.CurveTo(13.8f, 3.9f, 16.2f, 3.5f, 19.2f, 4.1f),
                PathNode.VerticalTo(19.3f),
                PathNode.CurveTo(16.2f, 18.7f, 13.8f, 19.1f, 12f, 20.6f)
            ),
            width = 1.6f,
            cap = StrokeCap.Round
        )
        // spine
        strokePath(
            nodes = listOf(PathNode.MoveTo(12f, 5.4f), PathNode.VerticalTo(20.6f)),
            width = 1.6f,
            cap = StrokeCap.Round
        )
    }.build()

    /** Generic document outline (other files in the tree). */
    val FileLine: ImageVector = builder("spck.fileLine").apply {
        strokePath(
            nodes = listOf(
                PathNode.MoveTo(6f, 2.5f),
                PathNode.HorizontalTo(13.8f),
                PathNode.LineTo(18.6f, 7.3f),
                PathNode.VerticalTo(21.5f),
                PathNode.HorizontalTo(6f),
                PathNode.Close
            ),
            width = 1.8f,
            join = StrokeJoin.Round
        )
        strokePath(
            nodes = listOf(
                PathNode.MoveTo(13.8f, 2.5f),
                PathNode.VerticalTo(7.3f),
                PathNode.HorizontalTo(18.6f)
            ),
            width = 1.8f,
            join = StrokeJoin.Round
        )
    }.build()

    /** "Collapse all" tree glyph: stacked lines with a center arrow. */
    val CollapseAll: ImageVector = builder("spck.collapseAll").apply {
        strokePath(
            nodes = listOf(PathNode.MoveTo(4f, 5.5f), PathNode.HorizontalTo(20f)),
            width = 1.8f,
            cap = StrokeCap.Round
        )
        strokePath(
            nodes = listOf(PathNode.MoveTo(4f, 18.5f), PathNode.HorizontalTo(20f)),
            width = 1.8f,
            cap = StrokeCap.Round
        )
        strokePath(
            nodes = listOf(PathNode.MoveTo(4f, 9.8f), PathNode.HorizontalTo(13.5f)),
            width = 1.8f,
            cap = StrokeCap.Round
        )
        strokePath(
            nodes = listOf(PathNode.MoveTo(4f, 14.2f), PathNode.HorizontalTo(13.5f)),
            width = 1.8f,
            cap = StrokeCap.Round
        )
        fillPath(
            listOf(
                PathNode.MoveTo(15.5f, 9.8f),
                PathNode.LineTo(20f, 12f),
                PathNode.LineTo(15.5f, 14.2f),
                PathNode.Close
            ),
            Color.White
        )
    }.build()

    /** Stage/unstage toggle glyph: the mockup's horizontal "+−" (plus, minus). */
    val PlusMinus: ImageVector = builder("spck.plusMinus").apply {
        // plus (left)
        strokePath(
            nodes = listOf(PathNode.MoveTo(6.5f, 12f), PathNode.HorizontalTo(12.5f)),
            width = 1.8f,
            cap = StrokeCap.Round
        )
        strokePath(
            nodes = listOf(PathNode.MoveTo(9.5f, 9f), PathNode.VerticalTo(15f)),
            width = 1.8f,
            cap = StrokeCap.Round
        )
        // minus (right)
        strokePath(
            nodes = listOf(PathNode.MoveTo(14.5f, 12f), PathNode.HorizontalTo(20f)),
            width = 1.8f,
            cap = StrokeCap.Round
        )
    }.build()

    /**
     * Globe (static web projects). The ring is drawn as a white outer circle
     * with an inner circle in the hub's WEB green so the "donut" reads
     * correctly without an even-odd fill rule; the box behind it is the
     * `HubIconToken.WEB_GREEN` color (FileManagerScreen hub tokens).
     */
    val Globe: ImageVector = builder("spck.globe").apply {
        fillPath(circle(12f, 12f, 9.5f), Color.White)
        fillPath(circle(12f, 12f, 7.5f), Color(0xFF4CAF50))
        // equator
        strokePath(
            nodes = listOf(PathNode.MoveTo(4.5f, 12f), PathNode.HorizontalTo(19.5f)),
            width = 1.6f
        )
        // meridian
        strokePath(
            nodes = listOf(
                PathNode.MoveTo(12f, 4.5f),
                PathNode.CurveTo(9f, 6.8f, 9f, 17.2f, 12f, 19.5f),
                PathNode.CurveTo(15f, 17.2f, 15f, 6.8f, 12f, 4.5f),
                PathNode.Close
            ),
            width = 1.6f
        )
    }.build()
}
