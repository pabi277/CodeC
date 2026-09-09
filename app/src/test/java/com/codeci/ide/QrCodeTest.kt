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
    fun `the grid is square and mostly light`() {
        val modules = QrCode.encode("http://192.168.1.20:8100/", 320)!!
        assertEquals(320, modules.size)
        assertEquals(modules.size * modules.size, modules.dark.size)
        assertTrue("a quiet zone is part of a scannable code", modules.darkCount() < modules.dark.size / 2)
        assertTrue("corners of the quiet zone stay white", !modules.isDark(0, 0) && !modules.isDark(319, 319))
        assertTrue("the top-left finder pattern is dark", modules.isDark(modules.size / 8, modules.size / 8))
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
