package com.codeci.ide

import com.codeci.ide.ui.services.ServerEndpoints
import com.codeci.ide.ui.services.ShareActions
import com.codeci.ide.ui.services.ShareRow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 37 follow-up (owner, after the device pass): *"on same device directly
 * open in default browser not copy the link"*.
 *
 * The button is a Compose detail; what belongs in a test is the decision behind
 * it — which row has something to open, and what happens on a device with no
 * browser. A row that offers a broken tap is worse than a row with no button,
 * so "no URL" means "no button" here, and the launcher is only ever handed a
 * URL that carries an http scheme.
 */
class ShareActionsTest {

    private fun endpoints(lanUrl: String?, lanShared: Boolean) = ServerEndpoints(
        port = 8100,
        loopbackUrl = "http://127.0.0.1:8100/",
        lanUrl = lanUrl,
        lanAddress = lanUrl?.substringAfter("://")?.substringBefore(":")?.substringBefore("/"),
        lanShared = lanShared,
        bind = if (lanUrl == null) "127.0.0.1" else "0.0.0.0"
    )

    @Test
    fun `the on-phone row always has something to open`() {
        val off = endpoints(lanUrl = null, lanShared = false)
        assertEquals("http://127.0.0.1:8100/", ShareActions.browserUrl(off, ShareRow.ON_PHONE))
        assertNull(ShareActions.browserUrl(off, ShareRow.OTHER_DEVICES))

        val on = endpoints(lanUrl = "http://192.168.1.20:8100/", lanShared = true)
        assertEquals("http://192.168.1.20:8100/", ShareActions.browserUrl(on, ShareRow.OTHER_DEVICES))
    }

    @Test
    fun `no server means no browser button on either row`() {
        assertNull(ShareActions.browserUrl(null, ShareRow.ON_PHONE))
        assertNull(ShareActions.browserUrl(null, ShareRow.OTHER_DEVICES))
    }

    @Test
    fun `a url without an http scheme is never handed to the browser`() {
        for (url in listOf("127.0.0.1:8100", "", "   ", "file:///sdcard/x", "javascript:alert(1)")) {
            assertFalse("must refuse: $url", ShareActions.isHttpUrl(url))
        }
        assertFalse(ShareActions.isHttpUrl(null))
        assertTrue(ShareActions.isHttpUrl("http://192.168.1.20:8100/"))
        assertTrue(ShareActions.isHttpUrl("  https://example.com  "))
    }

    @Test
    fun `a malformed url drops the button instead of offering a dead tap`() {
        val broken = ServerEndpoints(
            port = 8100,
            loopbackUrl = "127.0.0.1:8100",
            lanUrl = "192.168.1.20:8100",
            lanAddress = "192.168.1.20",
            lanShared = true,
            bind = "0.0.0.0"
        )
        assertNull(ShareActions.browserUrl(broken, ShareRow.ON_PHONE))
        assertNull(ShareActions.browserUrl(broken, ShareRow.OTHER_DEVICES))
    }

    @Test
    fun `when no browser can take it the link is copied, not lost`() {
        val message = ShareActions.fallbackMessage("http://192.168.1.20:8100/")
        assertTrue(message.contains("http://192.168.1.20:8100/"))
        assertTrue(message.lowercase().contains("copied"))
    }

    @Test
    fun `the two labels both name the browser and differ by row`() {
        val phone = ShareActions.label(ShareRow.ON_PHONE)
        val lan = ShareActions.label(ShareRow.OTHER_DEVICES)
        assertTrue(phone.contains("browser", ignoreCase = true))
        assertTrue(lan.contains("browser", ignoreCase = true))
        assertTrue(phone.contains("phone", ignoreCase = true))
        assertTrue(lan.contains("LAN", ignoreCase = true))
        assertFalse(phone == lan)
    }
}
