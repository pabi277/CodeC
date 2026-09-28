package com.codeci.ide.ui.services

import java.net.URI
import java.util.Locale

/**
 * No Android state here: limits, filtering, viewport math, the network
 * readout and the page's viewport report are host-testable.
 *
 * Phase 72.1 (2026-09-28) — the owner's six real Web Preview shots turned this
 * from two chips into the strip SPCK actually draws:
 * **Console · Elements · Network · Resources · Settings**, a console that
 * *takes commands*, a Network table with Name/Method/Status/Type/Size/Time and
 * a page that is phone-sized on **every** page, not only `index.html`.
 * Every decision that can be made from plain facts stays in this file (and its
 * two siblings, [PreviewConsolePolicy] and [PreviewInspectorPolicy]); the
 * WebView only carries the scripts out and the bytes back.
 */
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

/**
 * One observed request.
 *
 * @param type `performance`'s `initiatorType` (`script`, `img`, …) when the
 *   page reported the same resource; null when only the WebView saw it.
 * @param size transfer size in bytes, when the page's own timing entry has it.
 * @param time the entry's duration in ms, when the page's timing entry has it.
 */
data class PreviewRequest(
    val method: String,
    val address: String,
    val mainFrame: Boolean,
    val type: String? = null,
    val size: Long? = null,
    val time: Long? = null,
)

/** One page resource from `performance.getEntriesByType("resource")`. */
data class PreviewResource(
    val address: String,
    val type: String,
    val size: Long,
    val time: Long,
)

/**
 * Phase 72.1 follow-up (owner, 2026-09-28: *"browser have a good view and code is
 * very smaller view"*). What the page believes its viewport is.
 *
 * @param dpr the page's own `devicePixelRatio`, which is the density its CSS
 *   pixels are drawn at.
 * @param meta the page's own `viewport` meta content, capped at [META_LIMIT]
 *   characters; null when the page declares none (a page with none is the one
 *   `fitToPhone` gives a phone-sized default).
 */
data class PreviewPageBox(
    val cssWidth: Int,
    val cssHeight: Int,
    val dpr: Double,
    val meta: String? = null,
)

/**
 * Phase 72.1 — the five tabs of the strip the shots show, in their order.
 * Elements/Resources/Settings are new; Console and Network already existed.
 */
enum class PreviewToolTab { CONSOLE, ELEMENTS, NETWORK, RESOURCES, SETTINGS }

enum class PreviewResolution(val widthDp: Int, val heightDp: Int) {
    DEVICE(0, 0), PHONE(360, 640), TABLET(768, 1024), DESKTOP(1280, 720)
}

/** What happened when the page was asked about its own viewport meta. */
enum class PreviewViewport {
    /** The page sets its own viewport — left exactly as authored. */
    AUTHORED,

    /** The page had none; a phone-sized default was appended (the owner's ask). */
    ADDED,

    /** The page could not be asked (no head element, script refused). */
    UNKNOWN,

    /** The Settings switch is off — the page loads exactly as it is. */
    DISABLED,
}

object PreviewToolsPolicy {
    const val CAPACITY = 200
    const val TEXT_LIMIT = 4096
    val zoomPresets = listOf(50, 75, 100, 125, 150, 200)

    /** The console's own separator: unit is inside a row, record between rows. */
    /** The page-box report's field separator: the meta content itself has spaces. */
    private const val FIELD = '|'

    /** A meta is a declaration, not a document: this is plenty of it. */
    const val META_LIMIT = 80

    private const val UNIT = '\u0001'

    fun <T> append(entries: List<T>, entry: T): List<T> =
        (entries + entry).takeLast(CAPACITY)

    fun visible(entries: List<PreviewConsoleEntry>, levels: Set<PreviewLevel>) =
        entries.filter { it.level in levels }

    fun console(level: String, message: String, line: Int) = PreviewConsoleEntry(
        PreviewLevel.from(level), message.take(TEXT_LIMIT), line.coerceAtLeast(0)
    )

    /**
     * The safe form of a URL for anything this screen retains or shows: scheme
     * and authority (without credentials) plus the path. Query, fragment and
     * payload URLs are dropped — the Phase 61 rule the panel still states.
     */
    fun redact(url: String): String? = runCatching {
        val uri = URI(url)
        if (uri.scheme?.lowercase(Locale.ROOT) !in setOf("http", "https") || uri.host == null) {
            return null
        }
        "${uri.scheme}://${uri.rawAuthority.substringAfterLast('@')}${uri.rawPath.orEmpty()}"
            .take(TEXT_LIMIT)
    }.getOrNull()

    /** Never retain credentials, query tokens, fragments or non-network payload URLs. */
    fun request(method: String, url: String, mainFrame: Boolean): PreviewRequest? {
        val safe = redact(url) ?: return null
        return PreviewRequest(method.take(16), safe, mainFrame)
    }

    /**
     * The Network tab's Type/Size/Time columns. WebView's own observer cannot
     * see them (`shouldInterceptRequest` fires before the response), but the
     * page's own Resource Timing entries can — so the two lists are joined by
     * the address they share, and a row keeps its Method from the observer and
     * gains Type/Size/Time from the page. Rows nobody reported stay blank.
     */
    fun merge(requests: List<PreviewRequest>, resources: List<PreviewResource>): List<PreviewRequest> {
        if (resources.isEmpty() || requests.isEmpty()) return requests
        val byAddress = resources.associateBy { it.address }
        return requests.map { request ->
            val resource = byAddress[request.address] ?: return@map request
            request.copy(type = resource.type, size = resource.size, time = resource.time)
        }
    }

    /** One page resource, redacted, or null when it is not a network URL. */
    fun resource(url: String, type: String, size: Long, time: Long): PreviewResource? {
        val safe = redact(url) ?: return null
        return PreviewResource(
            address = safe,
            type = type.take(16).ifEmpty { "other" },
            size = size.coerceAtLeast(0L),
            time = time.coerceAtLeast(0L),
        )
    }

    /** The script the native view runs to collect the page's resource entries. */
    fun resourcesScript(): String = script(
        "(function(){var rows=[];",
        "try{var e=performance.getEntriesByType('resource')||[];",
        "for(var i=0;i<e.length&&rows.length<$CAPACITY;i++){var r=e[i];",
        "rows.push([r.name||'',r.initiatorType||'',Math.round(r.transferSize||0),",
        "Math.round(r.duration||0)].join('\\u0001'));}}catch(x){}",
        "return rows.join('\\n');})()",
    )

    /** Parse [resourcesScript]'s answer back into rows. Never throws. */
    fun parseResources(raw: String?): List<PreviewResource> {
        val value = PreviewConsolePolicy.unquote(raw)
        if (value.isBlank()) return emptyList()
        return value.lineSequence()
            .mapNotNull { line ->
                val parts = line.split(UNIT)
                if (parts.size < 4) return@mapNotNull null
                val size = parts[2].toLongOrNull() ?: 0L
                val time = parts[3].toLongOrNull() ?: 0L
                resource(parts[0], parts[1], size, time)
            }
            .toList()
            .takeLast(CAPACITY)
    }

    /**
     * Phase 72.1 — *“only the index.html load good others are very small
     * screen”*. A page with no `viewport` meta is laid out at a desktop width
     * by the WebView; the shots show SPCK rendering **every** page phone-sized.
     * So the page is asked once per load, and a page that never declared a
     * viewport gets `width=device-width, initial-scale=1` appended.
     *
     * A page that *did* declare one is left byte-for-byte as authored — this
     * adds a missing default, it never overrules the page's own choice.
     */
    fun viewportScript(): String = script(
        "(function(){try{",
        "if(document.querySelector('meta[name=viewport]'))return 'authored';",
        "var h=document.head||document.getElementsByTagName('head')[0];",
        "if(!h)return 'none';",
        "var t=document.createElement('meta');",
        "t.setAttribute('name','viewport');",
        "t.setAttribute('content','width=device-width, initial-scale=1');",
        "h.appendChild(t);",
        "return 'added';}catch(e){return 'none';}})()",
    )

    /** One-line scripts: the WebView evaluates them as-is, so no stray newlines. */
    private fun script(vararg lines: String): String = lines.joinToString(" ")

    fun viewportOutcome(raw: String?): PreviewViewport =
        when (PreviewConsolePolicy.unquote(raw)) {
            "added" -> PreviewViewport.ADDED
            "authored" -> PreviewViewport.AUTHORED
            else -> PreviewViewport.UNKNOWN
        }

    /**
     * The box a page was laid out in, read from the page itself. One line, no
     * state: the console shows it after every load, next to the view's own size,
     * because a screenshot cannot tell "the page renders small" from "the
     * preview box is short" — and the owner's two reports looked identical.
     */
    fun pageBoxScript(): String = script(
        "(function(){try{var m=document.querySelector('meta[name=viewport]');",
        "return [Math.round(window.innerWidth),Math.round(window.innerHeight),",
        "(window.devicePixelRatio||1),",
        "(m?(m.getAttribute('content')||''):'')].join('|');}catch(e){return '';}})()",
    )

    /** Parse [pageBoxScript]'s answer. Never throws; null when the page stayed silent. */
    fun parsePageBox(raw: String?): PreviewPageBox? {
        val parts = PreviewConsolePolicy.unquote(raw).trim().split(FIELD)
        if (parts.size < 3) return null
        val width = parts[0].trim().toIntOrNull() ?: return null
        val height = parts[1].trim().toIntOrNull() ?: return null
        val dpr = parts[2].trim().toDoubleOrNull() ?: return null
        if (width <= 0 || height <= 0 || dpr <= 0.0) return null
        // Everything after the third field is the meta, even if the declaration
        // itself contained the separator: a meta is prose, not a wire format.
        val meta = if (parts.size > 3) {
            parts.drop(3)
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .joinToString(" ")
                .take(META_LIMIT)
                .takeIf { it.isNotEmpty() }
        } else null
        return PreviewPageBox(width, height, dpr, meta)
    }

    /**
     * The console line: what the page was laid out as, what it declared, the box
     * the view was given and the scale actually applied between the two.
     *
     * It exists because two reports in a row could not be told apart from a
     * screenshot alone (*"code is very smaller view"*, 2026-09-28). Reading it:
     * `scale` should equal `dpr` (one CSS pixel = one dp, the browser's
     * `initial-scale=1`); `CSS px` should equal the view's `dp`; and `meta none`
     * on a page that hard-codes a width explains a layout wider than the phone.
     */
    fun pageBoxLabel(
        box: PreviewPageBox?,
        viewWidthDp: Int,
        viewHeightDp: Int,
        scale: Double? = null,
    ): String {
        val view = "view $viewWidthDp\u00d7$viewHeightDp dp"
        val zoom = if (scale != null && scale > 0.0) " \u00b7 scale ${number(scale)}" else ""
        if (box == null) return "page box unanswered \u00b7 $view$zoom"
        val meta = box.meta ?: "none"
        return "page box ${box.cssWidth}\u00d7${box.cssHeight} CSS px \u00b7 meta $meta \u00b7 " +
            "$view$zoom \u00b7 dpr ${number(box.dpr)}"
    }

    private fun number(value: Double): String {
        val rounded = Math.round(value * 100.0) / 100.0
        return if (rounded == Math.floor(rounded)) rounded.toLong().toString()
        else rounded.toString()
    }

    /** “—” is the honest answer for a cell WebView or the page never reported. */
    fun statusLabel(status: Int?): String = status?.toString() ?: "—"

    fun sizeLabel(bytes: Long?): String {
        val value = bytes ?: return "—"
        if (value <= 0L) return "—"
        if (value < 1024L) return "$value B"
        val kb = value / 1024.0
        if (kb < 1024.0) return String.format(Locale.ROOT, "%.1f kB", kb)
        return String.format(Locale.ROOT, "%.1f MB", kb / 1024.0)
    }

    fun timeLabel(ms: Long?): String {
        val value = ms ?: return "—"
        if (value <= 0L) return "—"
        if (value < 1000L) return "$value ms"
        return String.format(Locale.ROOT, "%.1f s", value / 1000.0)
    }

    fun typeLabel(type: String?): String = type?.takeIf { it.isNotBlank() } ?: "—"

    /**
     * The panel keeps a strip and an input line now, so its floor is taller
     * than Phase 61's 160 dp; the 65 % cap is unchanged — the page is still
     * the screen's primary content.
     */
    fun panelHeight(requested: Float, available: Float): Float {
        val max = (available.coerceAtLeast(0f) * .65f)
        val min = minOf(220f, max)
        return (if (requested.isFinite()) requested else 240f).coerceIn(min, max)
    }

    fun fitScale(width: Float, height: Float, viewportWidth: Float, viewportHeight: Float): Float {
        if (viewportWidth <= 0 || viewportHeight <= 0) return 1f
        return minOf(1f, width.coerceAtLeast(0f) / viewportWidth,
            height.coerceAtLeast(0f) / viewportHeight)
    }
}
