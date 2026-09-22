package com.codeci.ide

import com.codeci.ide.ui.services.PreviewChromeFacts
import com.codeci.ide.ui.services.PreviewChromePolicy
import com.codeci.ide.ui.services.PreviewLink
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 58.3 — which upper links the preview's ☰ offers, and the one decision
 * the part turns on: **the panel is not the default chrome any more.**
 *
 * The owner was asked the roadmap's question directly (the shots show the page's
 * own Content/Home/More buttons and no preview screen, so the phase says ask
 * rather than invent) and chose **CodeC's preview chrome**. These cases pin what
 * that means as a rule rather than as a screenshot nobody took: an item exists
 * only when its fact is true, the order is the menu's order, `file://` gets no
 * browser item (the scheme rule the panel already obeyed), and STOP SERVERS
 * keeps the panel's own `> 1` rule so a menu cannot grow a way to break the
 * screen it belongs to.
 */
class PreviewChromePolicyTest {

    /** A plain static preview of a page, server up, nothing shared. */
    private fun plain(
        hasAddress: Boolean = true,
        addressIsHttp: Boolean = true,
        hasPeerUrl: Boolean = false,
        ownsStaticServer: Boolean = true,
        serverCount: Int = 1,
    ) = PreviewChromeFacts(hasAddress, addressIsHttp, hasPeerUrl, ownsStaticServer, serverCount)

    @Test
    fun `a preview with nothing to offer draws no hamburger`() {
        val nothing = plain(
            hasAddress = false,
            addressIsHttp = false,
            ownsStaticServer = false,
            serverCount = 0,
        )
        assertEquals(emptyList<PreviewLink>(), PreviewChromePolicy.links(nothing))
        assertFalse(
            "an icon whose menu is empty is a dead control",
            PreviewChromePolicy.hasMenu(nothing),
        )
    }

    @Test
    fun `the LAN switch survives an address that has not resolved yet`() {
        // The static preview owns its socket from the first frame, so the switch
        // is real before the page loads — and the panel has always shown it that
        // way (it renders from the endpoints, not from the page).
        val starting = plain(hasAddress = false, addressIsHttp = false, serverCount = 0)
        assertEquals(listOf(PreviewLink.LAN_SHARING), PreviewChromePolicy.links(starting))
    }

    @Test
    fun `the page's own address is always the first item`() {
        assertEquals(
            listOf(
                PreviewLink.COPY_PAGE_ADDRESS,
                PreviewLink.OPEN_PHONE_BROWSER,
                PreviewLink.LAN_SHARING,
                PreviewLink.SERVER_OPTIONS,
            ),
            PreviewChromePolicy.links(plain()),
        )
    }

    @Test
    fun `a file address is copyable but never handed to a browser`() {
        val file = plain(addressIsHttp = false)
        val links = PreviewChromePolicy.links(file)
        assertTrue(links.contains(PreviewLink.COPY_PAGE_ADDRESS))
        assertFalse(
            "the panel's own scheme rule: no ACTION_VIEW for a file:// URL",
            links.contains(PreviewLink.OPEN_PHONE_BROWSER),
        )
    }

    @Test
    fun `the peer link appears exactly when a LAN address exists`() {
        val shared = plain(hasPeerUrl = true)
        val links = PreviewChromePolicy.links(shared)
        assertTrue("a shared server must offer its peer address", links.contains(PreviewLink.OPEN_PEER_LINK))
        assertTrue(
            "the peer's row sits after the phone's, as in the panel",
            links.indexOf(PreviewLink.OPEN_PEER_LINK) > links.indexOf(PreviewLink.OPEN_PHONE_BROWSER),
        )
        assertFalse(
            "no Wi-Fi address means no pretend link",
            PreviewChromePolicy.links(plain(hasPeerUrl = false)).contains(PreviewLink.OPEN_PEER_LINK),
        )
    }

    @Test
    fun `the LAN switch is only offered for a server this screen owns`() {
        assertTrue(PreviewChromePolicy.links(plain()).contains(PreviewLink.LAN_SHARING))
        val live = plain(ownsStaticServer = false, hasPeerUrl = true)
        assertFalse(
            "a live runner's socket is not this screen's to rebind",
            PreviewChromePolicy.links(live).contains(PreviewLink.LAN_SHARING),
        )
    }

    @Test
    fun `stopping servers keeps the panel's own more-than-one rule`() {
        assertTrue(PreviewChromePolicy.links(plain()).contains(PreviewLink.SERVER_OPTIONS))
        assertFalse(
            "one server is the one serving this page — the old chrome never offered to stop it",
            PreviewChromePolicy.links(plain(serverCount = 1)).contains(PreviewLink.STOP_SERVERS),
        )
        assertTrue(
            PreviewChromePolicy.links(plain(serverCount = 2)).contains(PreviewLink.STOP_SERVERS),
        )
        val none = plain(serverCount = 0)
        assertFalse(PreviewChromePolicy.links(none).contains(PreviewLink.SERVER_OPTIONS))
        assertFalse(PreviewChromePolicy.links(none).contains(PreviewLink.STOP_SERVERS))
    }

    @Test
    fun `the panel is the detail behind the hamburger, never the default`() {
        assertFalse(
            "58.3's whole point: the page gets this height back",
            PreviewChromePolicy.panelVisible(requested = false),
        )
        assertTrue(PreviewChromePolicy.panelVisible(requested = true))
        assertTrue(PreviewChromePolicy.pageHasFullHeight(requested = false))
        assertFalse(PreviewChromePolicy.pageHasFullHeight(requested = true))
    }
}
