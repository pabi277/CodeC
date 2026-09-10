package com.codeci.ide

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Phase 38.1 — the launcher asset set, read from the REAL repo tree
 * (plain JVM; these are file-shape contracts, not rendering, so no
 * Robolectric needed). This is the JUnit twin of
 * `scripts/check_icon_assets.sh` (which CI also runs right after
 * checkout): together they pin
 *
 *  · every density folder has BOTH rasters, at the density-table sizes;
 *  · no template .webp leftovers (duplicate-resource build failure);
 *  · the adaptive XMLs wire background + foreground + a REAL monochrome
 *    layer whose drawable differs from the foreground (the exact
 *    mistake the Android Studio template shipped);
 *  · the notification silhouette and the in-app mark exist;
 *  · the manifest icon names did not churn (installer identity).
 */
class IconAssetSetTest {

    private val res: File get() = RepoFiles.mainSource("app/src/main/res")
    private val densities = listOf("mdpi", "hdpi", "xhdpi", "xxhdpi", "xxxhdpi")
    private val densitySize = mapOf(
        "mdpi" to 48, "hdpi" to 72, "xhdpi" to 96, "xxhdpi" to 144, "xxxhdpi" to 192
    )

    @Test
    fun `every density folder has both launcher rasters`() {
        for (d in densities) {
            assertTrue("missing ${d}/ic_launcher.png", File(res, "mipmap-$d/ic_launcher.png").isFile)
            assertTrue("missing ${d}/ic_launcher_round.png", File(res, "mipmap-$d/ic_launcher_round.png").isFile)
        }
    }

    @Test
    fun `no template webp rasters remain`() {
        val leftovers = densities.flatMap { d ->
            File(res, "mipmap-$d").listFiles().orEmpty().filter { it.extension == "webp" }
        }
        assertTrue(
            "stale webp rasters (duplicate-resource build failure): $leftovers",
            leftovers.isEmpty()
        )
    }

    @Test
    fun `raster sizes match the density table`() {
        for (d in densities) {
            val expected = densitySize.getValue(d)
            for (name in listOf("ic_launcher.png", "ic_launcher_round.png")) {
                val f = File(res, "mipmap-$d/$name")
                assertTrue("$f missing", f.isFile)
                val (w, h) = pngSize(f)
                assertEquals("$f width", expected, w)
                assertEquals("$f height", expected, h)
            }
        }
    }

    @Test
    fun `adaptive xmls declare background foreground and a distinct monochrome layer`() {
        for (name in listOf("ic_launcher.xml", "ic_launcher_round.xml")) {
            val xml = File(res, "mipmap-anydpi-v26/$name")
            assertTrue("$name missing", xml.isFile)
            val text = xml.readText()
            for (layer in listOf("background", "foreground", "monochrome")) {
                assertTrue("$name has no <$layer>", text.contains("<$layer"))
            }
            val fg = drawableOf(text, "foreground")
            val mono = drawableOf(text, "monochrome")
            assertNotNull("$name foreground drawable", fg)
            assertNotNull("$name monochrome drawable", mono)
            assertTrue(
                "$name monochrome ($mono) must not be the foreground drawable ($fg) — " +
                    "that is the template bug this phase fixes",
                fg != mono
            )
            // The referenced drawables exist.
            for (ref in listOf(fg!!, mono!!)) {
                assertTrue(
                    "@drawable/$ref does not exist",
                    File(res, "drawable/$ref.xml").isFile
                )
            }
        }
    }

    @Test
    fun `notification silhouette and in-app mark exist`() {
        assertTrue(File(res, "drawable/ic_stat_codec.xml").isFile)
        assertTrue(File(res, "drawable/app_mark.xml").isFile)
        // and the launcher foreground is no longer used as the notification
        // icon anywhere in production code.
        val offenders = RepoFiles.mainKotlinSources().filter {
            it.readText().contains("setSmallIcon(R.drawable.ic_launcher_foreground)")
        }
        assertTrue("ic_launcher_foreground still used as small icon: $offenders", offenders.isEmpty())
    }

    @Test
    fun `manifest icon names are unchanged`() {
        val manifest = RepoFiles.mainSource("app/src/main/AndroidManifest.xml").readText()
        assertTrue(manifest.contains("android:icon=\"@mipmap/ic_launcher\""))
        assertTrue(manifest.contains("android:roundIcon=\"@mipmap/ic_launcher_round\""))
    }

    @Test
    fun `store art and generator exist`() {
        val art = RepoFiles.mainSource("docs/icon/codec-512.png")
        assertTrue("docs/icon/codec-512.png missing", art.isFile)
        val (w, h) = pngSize(art)
        assertEquals(512, w)
        assertEquals(512, h)
        val master = RepoFiles.mainSource("docs/icon/codec-mark.svg")
        assertTrue("master svg missing", master.isFile)
        val script = RepoFiles.mainSource("scripts/render_icon.mjs")
        assertTrue("render script missing", script.isFile)
        val scriptText = script.readText()
        // The script must keep reading the master SVG (single source of truth)
        // and keep writing the committed raster set (density folders are
        // built as `mipmap-${density}`, so assert on the parts that exist).
        assertTrue("script must read the master svg", scriptText.contains("codec-mark.svg"))
        assertTrue("script must write to mipmap- folders", scriptText.contains("mipmap-"))
        for (d in densities) {
            assertTrue("script must cover the $d density", "\"$d\"" in scriptText || "'$d'" in scriptText)
        }
    }

    // ---- helpers ---------------------------------------------------------

    private fun drawableOf(adaptiveXml: String, layer: String): String? {
        val m = Regex("<$layer\\s+android:drawable=\"@drawable/(\\w+)\"\\s*/>").find(adaptiveXml)
        return m?.groupValues?.get(1)
    }

    /** PNG dimensions straight from the IHDR chunk (no image library needed). */
    private fun pngSize(f: File): Pair<Int, Int> {
        val bytes = f.readBytes()
        assertTrue("${f.name} is not a PNG", bytes.size > 24 && bytes[0] == 0x89.toByte() && bytes[1] == 0x50.toByte())
        val w = ((bytes[16].toInt() and 0xFF) shl 24) or ((bytes[17].toInt() and 0xFF) shl 16) or
            ((bytes[18].toInt() and 0xFF) shl 8) or (bytes[19].toInt() and 0xFF)
        val h = ((bytes[20].toInt() and 0xFF) shl 24) or ((bytes[21].toInt() and 0xFF) shl 16) or
            ((bytes[22].toInt() and 0xFF) shl 8) or (bytes[23].toInt() and 0xFF)
        return w to h
    }
}
