package com.codeci.ide.ui.services

import java.net.URI
import java.util.Locale

/** No Android state here: limits, filtering and viewport math are host-testable. */
enum class PreviewLevel { LOG, INFO, WARN, ERROR;
    companion object {
        fun from(label: String): PreviewLevel = when (label.lowercase(Locale.ROOT)) {
            "info", "tip" -> INFO
            "warn", "warning" -> WARN
            "error" -> ERROR
            else -> LOG // Chromium DEBUG shares the Log filter, never disappears.
        }
    }
}

data class PreviewConsoleEntry(val level: PreviewLevel, val message: String, val line: Int)
data class PreviewRequest(val method: String, val address: String, val mainFrame: Boolean)
enum class PreviewToolTab { CONSOLE, NETWORK }
enum class PreviewResolution(val widthDp: Int, val heightDp: Int) {
    DEVICE(0, 0), PHONE(360, 640), TABLET(768, 1024), DESKTOP(1280, 720)
}

object PreviewToolsPolicy {
    const val CAPACITY = 200
    const val TEXT_LIMIT = 4096
    val zoomPresets = listOf(50, 75, 100, 125, 150, 200)

    fun <T> append(entries: List<T>, entry: T): List<T> =
        (entries + entry).takeLast(CAPACITY)

    fun visible(entries: List<PreviewConsoleEntry>, levels: Set<PreviewLevel>) =
        entries.filter { it.level in levels }

    fun console(level: String, message: String, line: Int) = PreviewConsoleEntry(
        PreviewLevel.from(level), message.take(TEXT_LIMIT), line.coerceAtLeast(0)
    )

    /** Never retain credentials, query tokens, fragments or non-network payload URLs. */
    fun request(method: String, url: String, mainFrame: Boolean): PreviewRequest? = runCatching {
        val uri = URI(url)
        if (uri.scheme?.lowercase(Locale.ROOT) !in setOf("http", "https") || uri.host == null) {
            return null
        }
        val safe = "${uri.scheme}://${uri.rawAuthority.substringAfterLast('@')}${uri.rawPath.orEmpty()}"
        PreviewRequest(method.take(16), safe.take(TEXT_LIMIT), mainFrame)
    }.getOrNull()

    fun panelHeight(requested: Float, available: Float): Float {
        val max = (available.coerceAtLeast(0f) * .65f)
        val min = minOf(160f, max)
        return (if (requested.isFinite()) requested else 240f).coerceIn(min, max)
    }

    fun fitScale(width: Float, height: Float, viewportWidth: Float, viewportHeight: Float): Float {
        if (viewportWidth <= 0 || viewportHeight <= 0) return 1f
        return minOf(1f, width.coerceAtLeast(0f) / viewportWidth,
            height.coerceAtLeast(0f) / viewportHeight)
    }
}
