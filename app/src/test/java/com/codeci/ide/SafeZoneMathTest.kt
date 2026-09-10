package com.codeci.ide

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 38.1 — pins for the `IconGeometry` helper itself, so the parser
 * cannot drift into a false green (a bbox test is only as good as the
 * bbox math). Strictness is the feature: the icon files are ours, so
 * anything the parser cannot read exactly is an error, never a guess.
 */
class SafeZoneMathTest {

    // ---- boundingBox -----------------------------------------------------

    @Test
    fun `bounding box of the caret path is exact`() {
        val box = IconGeometry.boundingBox("M39,36 L57,54 L39,72 L34,67 L47,54 L34,41 Z")
        assertEquals(34.0, box.minX, 1e-9)
        assertEquals(36.0, box.minY, 1e-9)
        assertEquals(57.0, box.maxX, 1e-9)
        assertEquals(72.0, box.maxY, 1e-9)
        assertEquals(23.0, box.width, 1e-9)
        assertEquals(36.0, box.height, 1e-9)
    }

    @Test
    fun `bounding box of the underscore path is exact`() {
        val box = IconGeometry.boundingBox("M52,64 L74,64 L74,72 L52,72 Z")
        assertEquals(52.0, box.minX, 1e-9)
        assertEquals(64.0, box.minY, 1e-9)
        assertEquals(74.0, box.maxX, 1e-9)
        assertEquals(72.0, box.maxY, 1e-9)
    }

    @Test
    fun `H and V absolute commands extend the box from the current point`() {
        val box = IconGeometry.boundingBox("M10,10 H20 V30 Z")
        assertEquals(10.0, box.minX, 1e-9)
        assertEquals(10.0, box.minY, 1e-9)
        assertEquals(20.0, box.maxX, 1e-9)
        assertEquals(30.0, box.maxY, 1e-9)
    }

    @Test
    fun `numbers survive decimals negatives and mixed separators`() {
        val box = IconGeometry.boundingBox("M-1.5,-2.75L3.25,0.5 Z")
        assertEquals(-1.5, box.minX, 1e-9)
        assertEquals(-2.75, box.minY, 1e-9)
        assertEquals(3.25, box.maxX, 1e-9)
        assertEquals(0.5, box.maxY, 1e-9)
    }

    @Test
    fun `inside is per-axis and inclusive`() {
        val inside = IconGeometry.Box(21.0, 30.0, 87.0, 80.0)
        assertTrue(inside.inside(21.0, 87.0))
        val clipped = IconGeometry.Box(0.0, 0.0, 108.0, 108.0)
        assertFalse(clipped.inside(21.0, 87.0))
        val edge = IconGeometry.Box(21.0, 21.0, 87.0, 87.0)
        assertTrue(edge.inside(21.0, 87.0))
        val wide = IconGeometry.Box(21.0, 21.0, 90.0, 60.0)
        assertFalse(wide.inside(21.0, 87.0))
    }

    // ---- strictness (the reason this helper can be trusted) -------------

    @Test(expected = IllegalArgumentException::class)
    fun `relative commands are refused`() {
        IconGeometry.boundingBox("M39,36 h21 v8 Z")
    }

    @Test(expected = IllegalArgumentException::class)
    fun `lowercase move is refused`() {
        IconGeometry.boundingBox("m39,36 L57,54 Z")
    }

    @Test(expected = IllegalArgumentException::class)
    fun `arcs are refused`() {
        IconGeometry.boundingBox("M24,0 A24,24 0 0 1 48,24 Z")
    }

    @Test(expected = IllegalArgumentException::class)
    fun `curves are refused`() {
        IconGeometry.boundingBox("M0,0 C1,1 2,2 3,3 Z")
    }

    @Test(expected = IllegalArgumentException::class)
    fun `odd coordinate counts are refused`() {
        IconGeometry.boundingBox("M39 L57,54 Z")
    }

    @Test(expected = IllegalArgumentException::class)
    fun `empty path data is refused`() {
        IconGeometry.boundingBox("   ")
    }

    @Test(expected = IllegalArgumentException::class)
    fun `garbage tokens are refused`() {
        IconGeometry.boundingBox("M39,36 Lxx,54 Z")
    }

    // ---- vector XML parsing ----------------------------------------------

    @Test
    fun `parseVectorXml reads viewport fills and paths`() {
        val doc = IconGeometry.parseVectorXml(
            """<vector xmlns:android="http://schemas.android.com/apk/res/android"
                android:viewportWidth="108" android:viewportHeight="108">
                <path android:fillColor="#3DDC84" android:pathData="M39,36 L57,54 Z"/>
                <path android:fillColor="#3DDC84" android:pathData="M52,64 L74,72 Z"/>
            </vector>"""
        )
        assertEquals(108.0, doc.viewportWidth, 0.0)
        assertEquals(108.0, doc.viewportHeight, 0.0)
        assertEquals(2, doc.paths.size)
        assertEquals("#3DDC84", doc.paths[0].fillColor)
        assertTrue(doc.paths.none { it.hasArc })
    }

    @Test
    fun `arc detection finds the tile`() {
        assertTrue(IconGeometry.parseVectorXml(
            """<vector android:viewportWidth="108" android:viewportHeight="108">
                 <path android:fillColor="#101418" android:pathData="M24,0 A24,24 0 0 1 48,24 Z"/>
               </vector>"""
        ).paths.single().hasArc)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `vector without viewport is refused`() {
        IconGeometry.parseVectorXml("<vector><path android:pathData=\"M0,0 Z\"/></vector>")
    }
}
