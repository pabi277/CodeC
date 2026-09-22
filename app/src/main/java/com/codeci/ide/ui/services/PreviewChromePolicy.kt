package com.codeci.ide.ui.services

/**
 * Phase 58.3 — the preview screen's **upper links**, behind one hamburger.
 *
 * The owner's row for this part: *“The HTML page view's upper links want a
 * hamburger treatment.”* The roadmap named two possible readings and asked the
 * research pass to choose one, or to ask. The shots (`docs/spck-ui`, `124105`)
 * show Content / Home / More as buttons **inside the page's own `index.html`**
 * and never show CodeC's preview screen, so on 2026-09-22 the owner was asked —
 * with the two candidates named — and chose **CodeC's preview chrome**.
 *
 * Research, on this checkout: [com.codeci.ide.ui.screens.WebPreviewScreen] stacks
 * three things above the page — a `TopAppBar` (Back + Refresh), an **address
 * row**, and the whole [com.codeci.ide.ui.components.ServerSharePanel] (the
 * phone address, the LAN address, copy/browser buttons, the QR toggle, the LAN
 * switch, the server list and “Stop all”). On a phone that is well over a
 * hundred dp of chrome before the page starts, for information a user needs
 * *occasionally*, not continuously — exactly the shape a hamburger exists to fix.
 *
 * The decision this file makes pure: **which links the ☰ offers, from facts.**
 * The screen keeps rendering them with the actions it already had (`OpenInBrowser`,
 * `ShareActions`, `LanSharePolicy`, the `ServerHost` registry) — no new
 * behaviour, no link removed, only a new home. The address *readout* stays on
 * screen: a browser that hid the address would be the one thing worse than the
 * bar it replaced.
 *
 * Deliberately a `List` (order is the menu's order) and deliberately empty when
 * there is nothing to offer: an icon whose menu is empty is a dead control, and
 * this app does not draw those.
 */
enum class PreviewLink {
    /** The page's own address onto the clipboard (always available once loaded). */
    COPY_PAGE_ADDRESS,

    /** `http(s)` addresses only: the phone's browser can take a `file://` URL. */
    OPEN_PHONE_BROWSER,

    /** The peer-facing LAN address, when the socket really answers on one. */
    OPEN_PEER_LINK,

    /** The LAN sharing switch — only for a server this screen owns. */
    LAN_SHARING,

    /** The full panel (QR, every server, the notices) — the detail behind the ☰. */
    SERVER_OPTIONS,

    /** Stop the servers the registry knows about (more than one, as ever). */
    STOP_SERVERS,
}

/**
 * Everything the menu's contents depend on, as plain booleans.
 *
 * @param hasAddress the address row is showing something (a URL was resolved).
 * @param addressIsHttp the address is openable by a browser (`ShareActions.isHttpUrl`).
 * @param hasPeerUrl a LAN address exists (`ServerEndpoints.lanUrl != null`).
 * @param ownsStaticServer this screen owns the static server, so it may rebind it
 *   (`showSwitch = !isLive`, unchanged from the panel's own rule).
 * @param serverCount how many servers the registry knows about right now.
 */
data class PreviewChromeFacts(
    val hasAddress: Boolean,
    val addressIsHttp: Boolean,
    val hasPeerUrl: Boolean,
    val ownsStaticServer: Boolean,
    val serverCount: Int,
)

object PreviewChromePolicy {

    /**
     * The ☰'s items, in the order a user wants them: the page, then the peer,
     * then what this screen can do to its own server.
     */
    fun links(facts: PreviewChromeFacts): List<PreviewLink> = buildList {
        if (facts.hasAddress) add(PreviewLink.COPY_PAGE_ADDRESS)
        if (facts.addressIsHttp) add(PreviewLink.OPEN_PHONE_BROWSER)
        if (facts.hasPeerUrl) add(PreviewLink.OPEN_PEER_LINK)
        if (facts.ownsStaticServer) add(PreviewLink.LAN_SHARING)
        if (facts.serverCount > 0) add(PreviewLink.SERVER_OPTIONS)
        // > 1, exactly as the panel's own STOP ALL row has always ruled
        // (`if (servers.size > 1)`): stopping the one server that is serving
        // this very page is not an action the old chrome ever offered, and a
        // menu must not grow a way to break the screen it belongs to.
        if (facts.serverCount > 1) add(PreviewLink.STOP_SERVERS)
    }

    /** The ☰ is drawn only when it has something behind it. */
    fun hasMenu(facts: PreviewChromeFacts): Boolean = links(facts).isNotEmpty()

    /**
     * Is the whole [com.codeci.ide.ui.components.ServerSharePanel] on screen?
     *
     * Phase 58.3 — **no, not by default.** The panel was the default chrome of
     * the preview; it is now the detail behind the ☰ (one of its own items).
     * This is the decision the part turns on, so it is a named function with a
     * test rather than a `remember { false }` at the call site.
     */
    fun panelVisible(requested: Boolean): Boolean = requested

    /**
     * The reverse of [panelVisible] — the page's own space. True while the panel
     * is put away, which is what gives the page the height back.
     */
    fun pageHasFullHeight(requested: Boolean): Boolean = !panelVisible(requested)
}
