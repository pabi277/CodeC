package com.codeci.ide

import com.codeci.ide.ui.services.LanAddress
import com.codeci.ide.ui.services.ServerEndpoints
import com.codeci.ide.ui.services.ServerPortDetector
import com.codeci.ide.ui.services.WebPreviewServer
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * Phase 37 — the two halves of "the phone is a server" that need a real
 * socket: the detector's bind-line rules (loopback vs wildcard) and the
 * preview server's LAN mode (bind every interface, keep the traversal guard).
 */
class ServerLanTest {

    @get:Rule
    val tmp = TemporaryFolder()

    /**
     * The served folder, plus a `secret.txt` one level ABOVE it — the file a
     * traversal attempt is trying to reach. (One inside the root is fair game
     * for the server, so it must not be used for this.)
     *
     * Built under `tmp.root` directly rather than through `newFolder("site")`:
     * a test that binds, clashes and rebinds needs the same folder three times,
     * and `TemporaryFolder.newFolder` refuses a name that already exists (CI
     * caught that: `IOException: a folder with the path 'site' already exists`).
     * The rule still owns the whole tree, so cleanup is unchanged.
     */
    private val site: File by lazy {
        File(tmp.root, "secret.txt").writeText("do not serve me")
        File(tmp.root, "site").apply {
            mkdirs()
            File(this, "index.html").writeText("<h1>lan ok</h1>")
        }
    }

    private fun fetch(url: String): Pair<Int, String> {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.connectTimeout = 2_000
        connection.readTimeout = 2_000
        try {
            val code = connection.responseCode
            val body = (if (code < 400) connection.inputStream else connection.errorStream)
                ?.bufferedReader()?.readText().orEmpty()
            return code to body
        } finally {
            connection.disconnect()
        }
    }

    private fun awaitOk(url: String): Boolean {
        repeat(20) {
            runCatching { if (fetch(url).first == 200) return true }
            Thread.sleep(50)
        }
        return false
    }

    // ---- detector (37.1 §3: the LAN URL comes from the bind, not a guess) --

    @Test
    fun `a wildcard bind line is kept as a wildcard while the url stays loopback`() {
        val detected = ServerPortDetector.detect(" * Running on http://0.0.0.0:5000/")!!
        assertEquals("http://127.0.0.1:5000", detected.url)
        assertEquals(5000, detected.port)
        assertEquals("0.0.0.0", detected.bind)
        assertTrue(detected.isWildcard)

        val endpoints = ServerEndpoints.of(detected.port, detected.bind, "192.168.1.7", lanShared = true)
        assertEquals("http://192.168.1.7:5000", endpoints.lanUrl)
        assertEquals("http://127.0.0.1:5000", endpoints.loopbackUrl)
    }

    @Test
    fun `a loopback bind line never yields a lan url`() {
        val detected = ServerPortDetector.detect(" * Running on http://127.0.0.1:5000")!!
        assertEquals("127.0.0.1", detected.bind)
        assertTrue(!detected.isWildcard)
        val endpoints = ServerEndpoints.of(detected.port, detected.bind, "192.168.1.7", lanShared = true)
        assertNull(endpoints.lanUrl)
        assertNotNull(endpoints.notice())
    }

    @Test
    fun `uvicorn and http-server wildcard lines are recognised`() {
        assertEquals(
            "0.0.0.0",
            ServerPortDetector.detect("INFO:     Uvicorn running on http://0.0.0.0:8000 (Press CTRL+C to quit)")!!.bind
        )
        assertEquals(
            8000,
            ServerPortDetector.detect("Serving HTTP on 0.0.0.0 port 8000 (http://0.0.0.0:8000/) ...")!!.port
        )
        assertEquals(
            "0.0.0.0",
            ServerPortDetector.detect("CodeC server listening on http://0.0.0.0:8080")!!.bind
        )
    }

    @Test
    fun `a bind to another address is still not a codec server line`() {
        // The LAN URL is built from the device address, never from whatever the
        // process printed, so an unrelated (or hostile) address cannot leak in.
        assertNull(ServerPortDetector.detect(" * Running on http://10.0.0.5:5000"))
        assertNull(ServerPortDetector.detect("listening on http://example.com:5000"))
    }

    @Test
    fun `port clash lines are detected and their port named`() {
        fun portOf(line: String) = ServerPortDetector.detectBindFailure(line)?.port
        assertEquals(0, portOf("OSError: [Errno 98] Address already in use"))
        assertEquals(
            8080,
            portOf(
                "ERROR:    [Errno 98] error while attempting to bind on address " +
                    "('0.0.0.0', 8080): address already in use"
            )
        )
        assertEquals(3000, portOf("Error: listen EADDRINUSE: address already in use :::3000"))
        assertEquals(0, portOf("bind: Address already in use"))
        assertEquals(0, portOf("java.net.BindException: Address already in use"))
        assertNull(ServerPortDetector.detectBindFailure("Serving HTTP on 0.0.0.0 port 8000"))
        assertNull(ServerPortDetector.detectBindFailure("a page about \"address already in use\" errors"))
    }

    // ---- preview server LAN mode ------------------------------------------

    @Test
    fun `loopback mode is unchanged and advertises no lan url`() {
        val server = WebPreviewServer.start(site)!!
        try {
            assertEquals(LanAddress.LOOPBACK_HOST, server.bindAddress)
            assertTrue(awaitOk("http://127.0.0.1:${server.port}/"))
        } finally {
            server.stop()
        }
    }

    @Test
    fun `lan mode binds every interface and still refuses traversal`() {
        val server = WebPreviewServer.start(site, lanMode = true)!!
        try {
            assertEquals(LanAddress.WILDCARD_HOST, server.bindAddress)
            // The phone itself keeps dialing 127.0.0.1 — the same page, the
            // same guard, so LAN mode cannot smuggle a path escape (exit 3).
            val (code, body) = fetch("http://127.0.0.1:${server.port}/index.html")
            assertEquals(200, code)
            assertTrue(body.contains("lan ok"))
            // Percent-encoded on purpose: a literal `/../` would be normalised
            // by the HTTP client before it ever reached the guard.
            assertEquals(404, fetch("http://127.0.0.1:${server.port}/%2e%2e/secret.txt").first)
            assertEquals(404, fetch("http://127.0.0.1:${server.port}/..%2fsecret.txt").first)
            assertEquals(404, fetch("http://127.0.0.1:${server.port}/img/../../secret.txt").first)
            assertEquals(404, fetch("http://127.0.0.1:${server.port}/secret.txt").first)
        } finally {
            server.stop()
        }
    }

    @Test
    fun `a taken port is reported as a taken port`() {
        val first = WebPreviewServer.startAt(site, LanAddress.LOOPBACK_HOST, 0)
        val server = (first as WebPreviewServer.PreviewStart.Ready).server
        val outcome = WebPreviewServer.startAt(site, LanAddress.LOOPBACK_HOST, server.port)
        assertTrue(
            "expected PortInUse on ${server.port}, got $outcome",
            outcome is WebPreviewServer.PreviewStart.PortInUse
        )
        assertEquals(server.port, (outcome as WebPreviewServer.PreviewStart.PortInUse).port)
        server.stop()
        val again = WebPreviewServer.startAt(site, LanAddress.LOOPBACK_HOST, server.port)
        assertTrue(
            "the port must be claimable after stop, got $again",
            again is WebPreviewServer.PreviewStart.Ready
        )
        (again as WebPreviewServer.PreviewStart.Ready).server.stop()
    }

    @Test
    fun `a missing folder is a message not a null pointer`() {
        val outcome = WebPreviewServer.startAt(File(tmp.root, "gone"))
        assertTrue(outcome is WebPreviewServer.PreviewStart.Failed)
        assertTrue((outcome as WebPreviewServer.PreviewStart.Failed).message.contains("not readable"))
    }
}
