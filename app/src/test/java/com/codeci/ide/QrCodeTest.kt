package com.codeci.ide

import com.codeci.ide.ui.services.QrCode
import com.codeci.ide.ui.services.QrModules
import com.google.zxing.BinaryBitmap
import com.google.zxing.MultiFormatReader
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 37.1 — the QR code for the LAN URL. The interesting assertion is the
 * round trip: the module grid ZXing produced is fed back through ZXing's
 * reader, which proves a second device's camera app will actually get the URL
 * out of it — without a phone, a browser, or a screenshot.
 */
class QrCodeTest {

    private fun QrModules.toArgbPixels(): IntArray =
        IntArray(size * size) { index -> if (dark[index]) 0xFF000000.toInt() else 0xFFFFFFFF.toInt() }

    private fun readBack(modules: QrModules): String {
        val source = RGBLuminanceSource(modules.size, modules.size, modules.toArgbPixels())
        val bitmap = BinaryBitmap(HybridBinarizer(source))
        return MultiFormatReader().decode(bitmap).text
    }

    @Test
    fun `a lan url round-trips through the qr grid`() {
        val url = "http://192.168.1.20:8100/"
        val modules = QrCode.encode(url, 320)!!
        assertEquals(url, readBack(modules))
    }

    @Test
    fun `a url with a deep path still scans`() {
        val url = "http://10.0.0.9:5000/pages/about.html?from=codec"
        val modules = QrCode.encode(url, 256)!!
        assertEquals(url, readBack(modules))
    }

    @Test
    fun `the grid carries a quiet zone and a real finder eye`() {
        val modules = QrCode.encode("http://192.168.1.20:8100/", 320)!!
        val size = modules.size
        assertEquals(320, size)
        assertEquals(size * size, modules.dark.size)
        assertTrue(
            "a quiet zone is part of a scannable code",
            modules.darkCount() < modules.dark.size / 2
        )
        assertTrue(
            "every outer row and column stays white",
            (0 until size).all {
                !modules.isDark(it, 0) && !modules.isDark(0, it) &&
                    !modules.isDark(it, size - 1) && !modules.isDark(size - 1, it)
            }
        )

        // The eye is FOUND, not guessed. zxing scales the code to an integer
        // number of pixels per module and centres it, so assuming a fixed offset
        // is wrong (CI's first run failed exactly that way): the first dark pixel
        // in row-major order is the top-left corner of the 7x7 finder pattern,
        // and the run of dark pixels along that row is 7 modules wide.
        var firstX = -1
        var firstY = -1
        finding@ for (y in 0 until size) {
            for (x in 0 until size) {
                if (modules.isDark(x, y)) {
                    firstX = x
                    firstY = y
                    break@finding
                }
            }
        }
        assertTrue("a QR code always has dark modules", firstX > 0 && firstY > 0)
        var run7 = 0
        while (modules.isDark(firstX + run7, firstY)) run7++
        val scale = run7 / 7
        assertTrue("module scale must be at least one pixel (was run=$run7)", scale >= 1)
        for (i in 0..6) {
            assertTrue("finder top row", modules.isDark(firstX + i * scale, firstY))
            assertTrue("finder bottom row", modules.isDark(firstX + i * scale, firstY + 6 * scale))
            assertTrue("finder left column", modules.isDark(firstX, firstY + i * scale))
            assertTrue("finder right column", modules.isDark(firstX + 6 * scale, firstY + i * scale))
        }
        assertTrue("the eye ring is white", !modules.isDark(firstX + scale, firstY + scale))
        assertTrue("the eye pupil is dark", modules.isDark(firstX + 2 * scale, firstY + 2 * scale))
    }

    @Test
    fun `nothing to encode is a null, never an exception`() {
        assertNull(QrCode.encode(null))
        assertNull(QrCode.encode(""))
        assertNull(QrCode.encode("   "))
    }

    @Test
    fun `the hint names the url for the panel`() {
        assertTrue(QrCode.hint("http://a:1").contains("http://a:1"))
        assertTrue(QrCode.hint(null).contains("No LAN address"))
    }
}
