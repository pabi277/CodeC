package com.codeci.ide.ui.services

import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel

/**
 * Phase 37.1 — the QR code for a LAN URL, encoded with ZXing `core`
 * (Apache-2.0, zero transitive dependencies — the open-source-first verdict
 * in `docs/PHASE34_37_OSS_RESEARCH.md` §4: never hand-roll a QR encoder).
 *
 * The result is deliberately **not** an `Android Bitmap`: [QrModules] is the
 * raw module grid, so the encoder runs (and is round-trip tested) on a plain
 * JVM while the Compose side owns the pixels. Encoding returns null rather
 * than throwing — a QR that cannot be drawn must never take the server row
 * down with it; the URL text stays usable on its own.
 */
data class QrModules(val size: Int, val dark: BooleanArray) {

    fun isDark(x: Int, y: Int): Boolean =
        x in 0 until size && y in 0 until size && dark[y * size + x]

    /** Number of dark modules — a valid QR code always has some. */
    fun darkCount(): Int = dark.count { it }
}

object QrCode {

    /** Pixels of the rendered square; the module scale is derived from it. */
    const val DEFAULT_PIXELS = 320

    /** Spec-recommended quiet zone, in modules. */
    const val QUIET_ZONE_MODULES = 4

    /** Encodes [text] as a square QR grid, or null when it cannot be encoded. */
    fun encode(text: String?, pixels: Int = DEFAULT_PIXELS): QrModules? {
        val contents = text?.trim().orEmpty()
        if (contents.isEmpty()) return null
        val side = pixels.coerceIn(MIN_PIXELS, MAX_PIXELS)
        val hints = mapOf(
            EncodeHintType.MARGIN to QUIET_ZONE_MODULES,
            EncodeHintType.CHARSET to "UTF-8",
            EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M
        )
        return try {
            val matrix = QRCodeWriter().encode(contents, BarcodeFormat.QR_CODE, side, side, hints)
            val width = matrix.width
            val height = matrix.height
            if (width <= 0 || height <= 0) return null
            val side2 = if (width == height) width else minOf(width, height)
            val cells = BooleanArray(side2 * side2)
            for (y in 0 until side2) {
                for (x in 0 until side2) {
                    cells[y * side2 + x] = matrix.get(x, y)
                }
            }
            QrModules(side2, cells)
        } catch (e: Exception) {
            null
        }
    }

    private const val MIN_PIXELS = 96
    private const val MAX_PIXELS = 1024

    /** The sentence under the QR code (kept here so the panel stays dumb). */
    fun hint(url: String?): String =
        if (url.isNullOrBlank()) {
            "No LAN address to show yet"
        } else {
            "Scan with any device on this Wi-Fi to open $url"
        }
}
