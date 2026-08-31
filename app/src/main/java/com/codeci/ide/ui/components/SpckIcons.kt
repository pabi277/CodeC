package com.codeci.ide.ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

/**
 * Phase 15/16 — clean-room vector glyphs for the Spck-style UI.
 *
 * The Material icon set has no git-branch, QR-scan, zip-file, clone, or
 * two-tone language-mark glyphs, so the design's exact shapes are drawn here
 * from scratch (public design mockups only — no copied assets). Single-color
 * icons use a white base color so callers can re-tint with `Icon(tint=...)`;
 * the two-tone marks (Python, HTML) keep fixed colors and must NOT be tinted.
 */
object SpckIcons {

    /** Git branch glyph (the "⌥" mark used on branch chips and cards). */
    val GitBranch: ImageVector = ImageVector.Builder(
        name = "spck.gitBranch",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).apply {
        addPath(
            pathData = "M6,8.4 L6,15.5",
            color = Color.White,
            strokeWidth = 2f,
            strokeCap = StrokeCap.Round
        )
        addPath(
            pathData = "M18,9.4 C18,13.8 13.2,15.5 6.2,15.5",
            color = Color.White,
            strokeWidth = 2f,
            strokeCap = StrokeCap.Round
        )
        addPath(
            pathData = "M3.7,6 a2.3,2.3 0 1,0 4.6,0 a2.3,2.3 0 1,0 -4.6,0 Z",
            fillColor = Color.White
        )
        addPath(
            pathData = "M3.7,18 a2.3,2.3 0 1,0 4.6,0 a2.3,2.3 0 1,0 -4.6,0 Z",
            fillColor = Color.White
        )
        addPath(
            pathData = "M15.7,7 a2.3,2.3 0 1,0 4.6,0 a2.3,2.3 0 1,0 -4.6,0 Z",
            fillColor = Color.White
        )
    }.build()

    /** QR scanner (clone dialog URL field, Spck's QR repo import). */
    val QrScan: ImageVector = ImageVector.Builder(
        name = "spck.qrScan",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).apply {
        addPath(pathData = "M3,3 H11 V5 H5 V11 H3 Z", fillColor = Color.White)
        addPath(pathData = "M5.6,5.6 H8.4 V8.4 H5.6 Z", fillColor = Color.White)
        addPath(pathData = "M13,3 H21 V11 H19 V5 H13 Z", fillColor = Color.White)
        addPath(pathData = "M15.6,5.6 H18.4 V8.4 H15.6 Z", fillColor = Color.White)
        addPath(pathData = "M3,13 V21 H11 V19 H5 V13 Z", fillColor = Color.White)
        addPath(pathData = "M5.6,15.6 H8.4 V18.4 H5.6 Z", fillColor = Color.White)
        addPath(pathData = "M13,13 H15.5 V15.5 H13 Z", fillColor = Color.White)
        addPath(pathData = "M17.5,13.5 H20.5 V16.5 H17.5 Z", fillColor = Color.White)
        addPath(pathData = "M13,18 H15.5 V20.5 H13 Z", fillColor = Color.White)
        addPath(pathData = "M17.5,18 H20.5 V20.5 H17.5 Z", fillColor = Color.White)
    }.build()

    /** Zip archive file (Import ZIP sheet row). */
    val ZipFile: ImageVector = ImageVector.Builder(
        name = "spck.zipFile",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).apply {
        addPath(
            pathData = "M6.2,2.5 H14 L18.8,7.3 V21.5 H6.2 Z",
            color = Color.White,
            strokeWidth = 1.8f,
            strokeJoin = StrokeJoin.Round
        )
        addPath(
            pathData = "M14,2.5 V7.3 H18.8",
            color = Color.White,
            strokeWidth = 1.8f,
            strokeJoin = StrokeJoin.Round
        )
        addPath(pathData = "M11.2,8.2 H12.8 V10.2 H11.2 Z", fillColor = Color.White)
        addPath(pathData = "M11.2,11.5 H12.8 V13.5 H11.2 Z", fillColor = Color.White)
        addPath(pathData = "M11.2,14.8 H12.8 V16.8 H11.2 Z", fillColor = Color.White)
        addPath(pathData = "M10.5,17.6 H13.5 V20.6 H10.5 Z", fillColor = Color.White)
    }.build()

    /** Clone repo mark: a branch node with a download arrow (sheet row). */
    val CloneRepo: ImageVector = ImageVector.Builder(
        name = "spck.cloneRepo",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).apply {
        addPath(
            pathData = "M8,7.2 L8,15.8",
            color = Color.White,
            strokeWidth = 2f,
            strokeCap = StrokeCap.Round
        )
        addPath(
            pathData = "M17.5,10.2 C17.5,14 12.8,14.8 8,14.8",
            color = Color.White,
            strokeWidth = 2f,
            strokeCap = StrokeCap.Round
        )
        addPath(pathData = "M5.8,5 a2.2,2.2 0 1,0 4.4,0 a2.2,2.2 0 1,0 -4.4,0 Z", fillColor = Color.White)
        addPath(pathData = "M15.3,8 a2.2,2.2 0 1,0 4.4,0 a2.2,2.2 0 1,0 -4.4,0 Z", fillColor = Color.White)
        addPath(
            pathData = "M8,15.8 L8,19.2",
            color = Color.White,
            strokeWidth = 2f,
            strokeCap = StrokeCap.Round
        )
        addPath(pathData = "M8,21 L5.6,17.6 H10.4 Z", fillColor = Color.White)
    }.build()

    /** Document with a plus (New Project sheet row). */
    val FilePlus: ImageVector = ImageVector.Builder(
        name = "spck.filePlus",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).apply {
        addPath(
            pathData = "M5,2.5 H13.5 L19,8 V21.5 H5 Z",
            color = Color.White,
            strokeWidth = 1.8f,
            strokeJoin = StrokeJoin.Round
        )
        addPath(
            pathData = "M13.5,2.5 V8 H19",
            color = Color.White,
            strokeWidth = 1.8f,
            strokeJoin = StrokeJoin.Round
        )
        addPath(
            pathData = "M10.5,15.2 H17.5",
            color = Color.White,
            strokeWidth = 2f,
            strokeCap = StrokeCap.Round
        )
        addPath(
            pathData = "M14,11.7 V18.7",
            color = Color.White,
            strokeWidth = 2f,
            strokeCap = StrokeCap.Round
        )
    }.build()

    /** Outline folder (Open Folder sheet row, drawer tree). */
    val FolderLine: ImageVector = ImageVector.Builder(
        name = "spck.folderLine",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).apply {
        addPath(
            pathData = "M3,7 C3,5.9 3.9,5 5,5 H9.6 L11.8,7.6 H19 C20.1,7.6 21,8.5 21,9.6 " +
                "V17.5 C21,18.6 20.1,19.5 19,19.5 H5 C3.9,19.5 3,18.6 3,17.5 Z",
            color = Color.White,
            strokeWidth = 1.8f,
            strokeJoin = StrokeJoin.Round
        )
    }.build()

    /**
     * Simplified two-snake Python mark (fixed colors; do not tint).
     * Top-left snake light blue, bottom-right snake yellow.
     */
    val PythonLogo: ImageVector = ImageVector.Builder(
        name = "spck.pythonLogo",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).apply {
        addPath(
            pathData = "M12,2.5 C8.5,2.5 7,3.7 7,5.9 V8.5 H12 V9.5 H5.5 " +
                "C3.3,9.5 2,10.9 2,13.6 C2,16.3 3.3,17.8 5.5,17.8 H7.7 V15.1 " +
                "C7.7,13 9.3,11.6 11.5,11.6 H16.5 C18.3,11.6 19.7,10.2 19.7,8.4 " +
                "V5.9 C19.7,3.7 18,2.5 14.5,2.5 Z",
            fillColor = Color(0xFF6FA8DC)
        )
        addPath(
            pathData = "M12,21.5 C15.5,21.5 17,20.3 17,18.1 V15.5 H12 V14.5 H18.5 " +
                "C20.7,14.5 22,13.1 22,10.4 C22,7.7 20.7,6.2 18.5,6.2 H16.3 V8.9 " +
                "C16.3,11 14.7,12.4 12.5,12.4 H7.5 C5.7,12.4 4.3,13.8 4.3,15.6 " +
                "V18.1 C4.3,20.3 6,21.5 9.5,21.5 Z",
            fillColor = Color(0xFFFFD43B)
        )
        addPath(pathData = "M10.2,4.6 a0.9,0.9 0 1,0 1.8,0 a0.9,0.9 0 1,0 -1.8,0 Z", fillColor = Color.White)
        addPath(pathData = "M12.8,18.5 a0.9,0.9 0 1,0 1.8,0 a0.9,0.9 0 1,0 -1.8,0 Z", fillColor = Color.White)
    }.build()

    /** HTML shield mark (fixed orange + white; do not tint). */
    val HtmlShield: ImageVector = ImageVector.Builder(
        name = "spck.htmlShield",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).apply {
        addPath(
            pathData = "M4.8,2.5 H19.2 L18.1,19.3 L12,22 L5.9,19.3 Z",
            fillColor = Color(0xFFE44D26)
        )
        addPath(pathData = "M8.2,6.2 H15.8 V8.4 H8.2 Z", fillColor = Color.White)
        addPath(pathData = "M8.7,10.2 H15.3 V12.4 H8.7 Z", fillColor = Color.White)
        addPath(pathData = "M9.4,14.2 H12.9 V16.4 H9.4 Z", fillColor = Color.White)
    }.build()

    /** Open book outline (README / markdown files). */
    val BookLine: ImageVector = ImageVector.Builder(
        name = "spck.bookLine",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).apply {
        addPath(
            pathData = "M12,5.4 C10.2,3.9 7.8,3.5 4.8,4.1 V19.3 C7.8,18.7 10.2,19.1 12,20.6",
            color = Color.White,
            strokeWidth = 1.6f,
            strokeCap = StrokeCap.Round
        )
        addPath(
            pathData = "M12,5.4 C13.8,3.9 16.2,3.5 19.2,4.1 V19.3 C16.2,18.7 13.8,19.1 12,20.6",
            color = Color.White,
            strokeWidth = 1.6f,
            strokeCap = StrokeCap.Round
        )
        addPath(
            pathData = "M12,5.4 V20.6",
            color = Color.White,
            strokeWidth = 1.6f,
            strokeCap = StrokeCap.Round
        )
    }.build()

    /** Generic document outline (other files in the tree). */
    val FileLine: ImageVector = ImageVector.Builder(
        name = "spck.fileLine",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).apply {
        addPath(
            pathData = "M6,2.5 H13.8 L18.6,7.3 V21.5 H6 Z",
            color = Color.White,
            strokeWidth = 1.8f,
            strokeJoin = StrokeJoin.Round
        )
        addPath(
            pathData = "M13.8,2.5 V7.3 H18.6",
            color = Color.White,
            strokeWidth = 1.8f,
            strokeJoin = StrokeJoin.Round
        )
    }.build()

    /** "Collapse all" tree glyph: stacked lines with a center arrow. */
    val CollapseAll: ImageVector = ImageVector.Builder(
        name = "spck.collapseAll",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).apply {
        addPath(pathData = "M4,5.5 H20", color = Color.White, strokeWidth = 1.8f, strokeCap = StrokeCap.Round)
        addPath(pathData = "M4,18.5 H20", color = Color.White, strokeWidth = 1.8f, strokeCap = StrokeCap.Round)
        addPath(pathData = "M4,9.8 H13.5", color = Color.White, strokeWidth = 1.8f, strokeCap = StrokeCap.Round)
        addPath(pathData = "M4,14.2 H13.5", color = Color.White, strokeWidth = 1.8f, strokeCap = StrokeCap.Round)
        addPath(pathData = "M15.5,9.8 L20,12 L15.5,14.2 Z", fillColor = Color.White)
    }.build()

    /** Stage/unstage toggle glyph: the mockup's horizontal "+−" (plus, minus). */
    val PlusMinus: ImageVector = ImageVector.Builder(
        name = "spck.plusMinus",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).apply {
        // plus (left)
        addPath(pathData = "M6.5,12 H12.5", color = Color.White, strokeWidth = 1.8f, strokeCap = StrokeCap.Round)
        addPath(pathData = "M9.5,9 V15", color = Color.White, strokeWidth = 1.8f, strokeCap = StrokeCap.Round)
        // minus (right)
        addPath(pathData = "M14.5,12 H20", color = Color.White, strokeWidth = 1.8f, strokeCap = StrokeCap.Round)
    }.build()

    /** Globe (static web projects) — kept here for a single import site. */
    val Globe: ImageVector = ImageVector.Builder(
        name = "spck.globe",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).apply {
        addPath(
            pathData = "M12,2.5 a9.5,9.5 0 1,0 0.001,0 Z M12,4.5 a7.5,7.5 0 1,1 -0.001,0 Z",
            fillColor = Color.White,
            fillRule = androidx.compose.ui.graphics.FillRule.EvenOdd
        )
        addPath(
            pathData = "M4.5,12 H19.5",
            color = Color.White,
            strokeWidth = 1.6f
        )
        addPath(
            pathData = "M12,4.5 C9,6.8 9,17.2 12,19.5 C15,17.2 15,6.8 12,4.5 Z",
            color = Color.White,
            strokeWidth = 1.6f
        )
    }.build()
}
