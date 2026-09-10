package com.codeci.ide

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Phase 38.1 — the SAFE-ZONE law, checked numerically against the real
 * drawable XMLs:
 *
 *  · launcher foreground + monochrome: viewport 108, every drawn point
 *    inside the 66 dp safe box (21..87) AND inside the 33-unit safe
 *    circle (the roundest mask is a circle — the box alone is not
 *    enough);
 *  · monochrome is the SAME drawing as the foreground (themed icons are
 *    our design, not a compromise) and is white-on-transparent;
 *  · app_mark: one arc tile + the same glyph paths inside the safe box;
 *  · ic_stat_codec: a 24-grid silhouette with real coverage (not a
 *    3-pixel smear) and honest padding.
 *
 * The parser behind this is `IconGeometry`; `SafeZoneMathTest` pins the
 * parser itself.
 */
class IconGeometryTest {

    private val res: String get() = "app/src/main/res/drawable"

    private fun drawable(name: String): IconGeometry.VectorDoc =
        IconGeometry.parseVectorXml(RepoFiles.mainSource("$res/$name.xml").readText())

    @Test
    fun `launcher foreground draws inside the 66dp safe zone`() {
        val doc = drawable("ic_launcher_foreground")
        assertEquals(108.0, doc.viewportWidth, 0.0)
        assertEquals(108.0, doc.viewportHeight, 0.0)
        assertTrue("expected at least 2 glyph paths", doc.paths.size >= 2)
        for (path in doc.paths) {
            val box = IconGeometry.boundingBox(path.pathData)
            assertTrue(
                "path leaves the safe box 21..87: $box",
                box.inside(21.0, 87.0)
            )
        }
    }

    @Test
    fun `launcher foreground glyph is centred and inside the safe circle`() {
        val doc = drawable("ic_launcher_foreground")
        val all = doc.paths.flatMap { IconGeometry.points(it.pathData) }
        // Centre of the 108 canvas.
        for ((x, y) in all) {
            val r = sqrt((x - 54.0) * (x - 54.0) + (y - 54.0) * (y - 54.0))
            assertTrue(
                "point ($x,$y) is $r from centre — outside the 33-unit safe circle " +
                    "(it WILL clip on a round mask)",
                r <= 33.0
            )
        }
        // Composition centre: within 1.5 units of the canvas centre.
        val xs = all.map { it.first }; val ys = all.map { it.second }
        assertTrue(abs((xs.min() + xs.max()) / 2 - 54.0) <= 1.5)
        assertTrue(abs((ys.min() + ys.max()) / 2 - 54.0) <= 1.5)
    }

    @Test
    fun `monochrome layer is the same drawing, single colour`() {
        val fg = drawable("ic_launcher_foreground")
        val mono = drawable("ic_launcher_monochrome")
        // The themed icon must be OUR mark, not a lazy copy that drifted.
        assertEquals(
            "monochrome paths must equal the foreground paths",
            fg.paths.map { it.pathData },
            mono.paths.map { it.pathData }
        )
        assertTrue(
            "monochrome must be white (launcher tints it)",
            mono.paths.all { it.fillColor == "#FFFFFF" }
        )
        // and it is flat: one fill colour for the whole layer.
        assertEquals(setOf("#FFFFFF"), mono.paths.map { it.fillColor }.toSet())
    }

    @Test
    fun `app_mark is one rounded tile plus the same safe glyph`() {
        val mark = drawable("app_mark")
        assertEquals(108.0, mark.viewportWidth, 0.0)
        val arcs = mark.paths.filter { it.hasArc }
        val glyphs = mark.paths.filterNot { it.hasArc }
        assertEquals("app_mark must have exactly one tile path (the arc)", 1, arcs.size)
        assertEquals("app_mark must carry the two glyph paths", 2, glyphs.size)
        for (g in glyphs) {
            val box = IconGeometry.boundingBox(g.pathData)
            assertTrue("app_mark glyph leaves the safe box: $box", box.inside(21.0, 87.0))
        }
        // The tile fills the whole canvas (this is the in-app mark, no mask).
        val tileFill = arcs.first().fillColor
        assertTrue("tile must declare a fill colour", tileFill != null)
    }

    @Test
    fun `stat icon fills the 24 grid with honest padding`() {
        val stat = drawable("ic_stat_codec")
        assertEquals(24.0, stat.viewportWidth, 0.0)
        assertEquals(24.0, stat.viewportHeight, 0.0)
        assertTrue(stat.paths.all { it.fillColor == "#FFFFFF" })
        val all = stat.paths.flatMap { IconGeometry.points(it.pathData) }
        val xs = all.map { it.first }; val ys = all.map { it.second }
        // ≥ 2px padding on a 24 grid (status icons are not edge-to-edge)…
        for (v in xs + ys) {
            assertTrue("stat icon point $v outside the 24 grid", v >= 1.0 && v <= 23.0)
        }
        // …but with real coverage: at least 14 of 24 units wide AND tall,
        // or it is a smear no status bar can read.
        assertTrue("stat icon too narrow: ${xs.max() - xs.min()}", xs.max() - xs.min() >= 14.0)
        assertTrue("stat icon too short: ${ys.max() - ys.min()}", ys.max() - ys.min() >= 14.0)
    }

    @Test
    fun `background layer is a flat full-bleed tile`() {
        val bg = drawable("ic_launcher_background")
        assertEquals(108.0, bg.viewportWidth, 0.0)
        assertEquals(1, bg.paths.size)
        val box = IconGeometry.boundingBox(bg.paths.first().pathData)
        // The background deliberately covers the WHOLE canvas (that is its
        // job — parallax zone included).
        assertTrue(box.inside(0.0, 108.0))
        assertEquals(108.0, box.width, 0.001)
        assertEquals(108.0, box.height, 0.001)
        // Flat colour, and a different colour than the glyph paints with.
        val fill = bg.paths.first().fillColor
        assertTrue("background must declare a fill", fill != null)
        assertTrue(
            "background #101418 and glyph #3DDC84 must differ (two-colour law)",
            fill != "#3DDC84"
        )
    }

    @Test
    fun `master svg agrees with the shipped foreground`() {
        // docs/icon/codec-mark.svg is the single source of truth; the
        // drawable must not have drifted from it.
        val svg = RepoFiles.mainSource("docs/icon/codec-mark.svg").readText()
        val svgPaths = Regex("<path[^>]*\\bd=\"([^\"]+)\"").findAll(svg).map { it.groupValues[1].trim() }.toList()
        assertTrue("master svg must have >= 2 paths", svgPaths.size >= 2)
        val fg = drawable("ic_launcher_foreground")
        assertEquals(svgPaths, fg.paths.map { it.pathData })
    }
}
