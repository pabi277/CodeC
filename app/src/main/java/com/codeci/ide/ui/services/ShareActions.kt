package com.codeci.ide.ui.services

/**
 * Phase 37 follow-up (owner, straight after the device pass): *"one more option
 * need to add — on same device directly open in default browser not copy the
 * link"*.
 *
 * The share row already offered Copy (and the in-app preview), which is exactly
 * the friction the owner named: on the phone itself you want to *look at the
 * page*, not paste its address into another app. So each row gains a browser
 * button — and the decisions behind it live here, pure, because the two rows
 * are not the same thing:
 *
 *  - [ShareRow.ON_PHONE] always has a URL to open (the loopback address is on
 *    this device by definition, so Chrome can open it);
 *  - [ShareRow.OTHER_DEVICES] only has one when LAN sharing produced one —
 *    there is no "open the empty LAN row" state to launch.
 *
 * The `Intent` itself is not here: [OpenInBrowser] is the app's single launcher
 * and the Compose edge calls it. What this object adds is the promise that the
 * link is never *lost* when no browser can handle it, and that the button is
 * never shown for nothing (each row asks this object before it draws one).
 */
enum class ShareRow {
    /** The `http://127.0.0.1:<port>` row — the phone looking at itself. */
    ON_PHONE,

    /** The `http://<lan-ip>:<port>` row — what a peer (or this phone) opens. */
    OTHER_DEVICES
}

object ShareActions {

    /**
     * The URL the browser button opens for [row], or null when that row has
     * nothing openable — null means *no button*, never a disabled one.
     */
    fun browserUrl(endpoints: ServerEndpoints?, row: ShareRow): String? = when {
        endpoints == null -> null
        row == ShareRow.ON_PHONE -> endpoints.loopbackUrl.takeIf { isHttpUrl(it) }
        else -> endpoints.lanUrl?.takeIf { isHttpUrl(it) }
    }

    /**
     * A URL is only worth an `ACTION_VIEW` when it carries a scheme. A bare
     * `127.0.0.1:8100` would be handed to the browser as a *search term*, which
     * is worse than not tapping at all, so it is refused here and the row falls
     * back to Copy.
     */
    fun isHttpUrl(url: String?): Boolean {
        val text = url?.trim()?.lowercase() ?: return false
        return text.startsWith("http://") || text.startsWith("https://")
    }

    /**
     * What the row says when no browser on the device could take the URL. The
     * link is put on the clipboard instead of being announced and then lost, so
     * the tap is never a dead end.
     */
    fun fallbackMessage(url: String): String = "No browser on this device — link copied: $url"

    /** Content description / tooltip for the button (one wording, both rows). */
    fun label(row: ShareRow): String = when (row) {
        ShareRow.ON_PHONE -> "Open in the phone's browser"
        ShareRow.OTHER_DEVICES -> "Open this LAN address in the browser"
    }
}
