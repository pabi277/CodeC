package com.codeci.ide

import com.codeci.ide.ui.services.LanAddress
import com.codeci.ide.ui.services.ServerEndpoints
import com.codeci.ide.ui.services.ServerEvent
import com.codeci.ide.ui.services.ServerHost
import com.codeci.ide.ui.services.ServerKind
import com.codeci.ide.ui.services.ServerRegistry
import com.codeci.ide.ui.services.ServerRunner
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Phase 37.2 — the host that owns background servers, driven for real on the
 * host JVM: sockets bind, `/bin/sh` + `python3` children start, a second
 * observer re-attaches to the same process, and Stop releases the port.
 *
 * These are 37.2's exit conditions minus the phone: the notification text is
 * [ServerNotificationTest], "reachable from another device" is proven by the
 * real wildcard bind asserted below, and the human part (a second device
 * scanning the QR) stays in the owner's device round. Requires `python3` and
 * `/bin/sh` on the runner, like [ServerScaffoldE2ETest].
 */
class ServerHostTest {

    private lateinit var root: File
    private lateinit var scope: CoroutineScope
    private lateinit var host: ServerHost

    @Before
    fun setUp() {
        root = File.createTempFile("codec-host", "").apply { delete(); mkdirs() }
        File(root, "index.html").writeText("<h1>hello from the phone</h1>")
        File(root, "dev_server.py").writeText(DEV_SERVER_SOURCE)
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        host = ServerHost(scope, ServerRegistry { 0L })
    }

    @After
    fun tearDown() {
        host.stopAll()
        scope.cancel()
        root.deleteRecursively()
    }

    private fun fetch(url: String): String? = runCatching {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.connectTimeout = 1_000
        connection.readTimeout = 1_000
        try {
            if (connection.responseCode == 200) connection.inputStream.bufferedReader().readText() else null
        } finally {
            connection.disconnect()
        }
    }.getOrNull()

    /** Retries briefly: the bind line can precede the listener socket. */
    private fun awaitBody(url: String, contains: String? = null): Boolean {
        repeat(20) {
            val body = fetch(url)
            if (body != null && (contains == null || body.contains(contains))) return true
            Thread.sleep(50)
        }
        return false
    }

    /**
     * A stopped server must stop answering: retry, because killing a process
     * releases its socket a moment later than the kill call returns.
     */
    private fun assertClosed(port: Int, host: String = "127.0.0.1") {
        var last: String? = null
        repeat(20) {
            val body = fetch("http://$host:$port/")
            if (body == null) return
            last = body
            Thread.sleep(50)
        }
        assertTrue(
            "nothing may answer on :$port after the server was stopped, got: " +
                "${last?.take(120)} | listening: $debugListeners",
            false
        )
    }

    /** Which TCP ports are LISTENing in this container — for diagnosing a leaked server. */
    private val debugListeners: String
        get() = runCatching {
            File("/proc/net/tcp").readLines().drop(1).mapNotNull { line ->
                val parts = line.trim().split(' ')
                if (parts.getOrNull(3) == "0A") parts[1].substringAfter(':').toLongOrNull(16)?.toString() else null
            }.distinct().sortedBy { it.toIntOrNull() ?: 0 }.joinToString(",")
        }.getOrDefault("n/a")

    // ---- static preview (37.1 LAN bind + 37.2 lifecycle) ------------------

    @Test
    fun `a loopback preview serves the folder and registers itself`() {
        val entry = (host.serveStatic("static:site", "site", root, lan = false)
            as ServerHost.StaticOutcome.Serving).entry
        assertEquals(ServerKind.STATIC_PREVIEW, entry.kind)
        assertFalse(entry.lanShared)
        assertEquals(LanAddress.LOOPBACK_HOST, entry.bind)
        assertNull(entry.endpoints?.lanUrl)
        assertTrue("the preview must answer on loopback", awaitBody(entry.endpoints!!.loopbackUrl, "hello from the phone"))
        assertEquals(1, host.registry.liveCount())
    }

    @Test
    fun `a lan preview binds every interface and only advertises a usable address`() {
        host.publishLanAddress(LanAddress.LOOPBACK_HOST) // deliberately unusable
        val first = (host.serveStatic("static:site", "site", root, lan = true)
            as ServerHost.StaticOutcome.Serving).entry
        assertEquals(LanAddress.WILDCARD_HOST, first.bind)
        assertTrue(first.lanShared)
        assertNull("127.0.0.1 is never a peer address", first.endpoints?.lanUrl)
        assertNotNull(first.endpoints?.notice())
        assertTrue(awaitBody("http://127.0.0.1:${first.port}/", "hello from the phone"))

        host.stop("static:site")
        host.publishLanAddress("192.168.1.20")
        val second = (host.serveStatic("static:site", "site", root, lan = true)
            as ServerHost.StaticOutcome.Serving).entry
        assertEquals("http://192.168.1.20:${second.port}", second.endpoints?.lanUrl)
        assertNull(second.endpoints?.notice())
        assertEquals(
            "Serving site on 192.168.1.20:${second.port}",
            com.codeci.ide.ui.services.ServerNotification.title("site", second.endpoints)
        )
    }

    @Test
    fun `re-entering the preview re-attaches instead of binding twice`() {
        val port = (host.serveStatic("static:site", "site", root, lan = true)
            as ServerHost.StaticOutcome.Serving).entry.port
        val again = host.serveStatic("static:site", "site", root, lan = true)
        assertEquals(port, (again as ServerHost.StaticOutcome.Serving).entry.port)
        assertEquals(1, host.registry.liveCount())
        assertTrue(awaitBody("http://127.0.0.1:$port/", "hello from the phone"))
    }

    @Test
    fun `flipping lan rebinds the folder and frees the old socket`() {
        val privatePort = (host.serveStatic("static:site", "site", root, lan = false)
            as ServerHost.StaticOutcome.Serving).entry.port
        val entry = (host.serveStatic("static:site", "site", root, lan = true)
            as ServerHost.StaticOutcome.Serving).entry
        assertEquals(1, host.registry.liveCount())
        assertEquals(LanAddress.WILDCARD_HOST, entry.bind)
        assertClosed(privatePort)
        assertTrue(awaitBody("http://127.0.0.1:${entry.port}/", "hello from the phone"))

        assertTrue(host.stop(entry.id))
        assertEquals(0, host.registry.liveCount())
        assertClosed(entry.port)
    }

    @Test
    fun `stopping releases the port so a re-run works immediately`() {
        val port = (host.serveStatic("static:site", "site", root, lan = true)
            as ServerHost.StaticOutcome.Serving).entry.port
        assertTrue(host.stop("static:site"))
        val rerun = host.serveStatic("static:site", "site", root, lan = true)
        assertEquals(
            "the same preferred port must be claimable again",
            port,
            (rerun as ServerHost.StaticOutcome.Serving).entry.port
        )
        assertFalse(host.stop("static:missing"))
    }

    @Test
    fun `an unreadable folder is refused with a message`() {
        val outcome = host.serveStatic("static:none", "none", File(root, "nope"), lan = false)
        assertTrue(outcome is ServerHost.StaticOutcome.Refused)
        assertTrue((outcome as ServerHost.StaticOutcome.Refused).message.contains("not readable"))
        assertEquals(0, host.registry.liveCount())
    }

    // ---- process servers (37.2 keep-alive) --------------------------------

    private fun runner(command: String) = ServerRunner(
        shell = File("/bin/sh"),
        environment = mapOf("PATH" to "/usr/bin:/bin"),
        command = command,
        workDir = root,
        readyTimeoutSeconds = 8,
        preferredPort = TEST_SERVER_PORT
    )

    @Test
    fun `a server outlives its first observer and the next one sees the tail`() {
        val id = ServerHost.processId("demo")
        val first = mutableListOf<ServerEvent>()
        val firstJob: Job = scope.launch {
            host.attachProcess(id, "demo", runner(DEV_SERVER_COMMAND), lan = true).collect { first += it }
        }
        runBlocking { withTimeout(20_000) { while (first.none { it is ServerEvent.Ready }) delay(25) } }
        assertEquals(TEST_SERVER_PORT, host.registry.findById(id)?.port)
        assertTrue(awaitBody("http://127.0.0.1:$TEST_SERVER_PORT/", "ok"))

        // The editor leaves: its collection dies, the process must not.
        firstJob.cancel()
        runBlocking { delay(200) }
        assertTrue(
            "a LAN server must keep answering after its observer is gone",
            awaitBody("http://127.0.0.1:$TEST_SERVER_PORT/2", "ok")
        )
        assertTrue("the process must still be live", host.isServing(id))

        // A fresh observer re-attaches to the same session and gets the replay.
        val replayed = mutableListOf<ServerEvent>()
        val secondJob = scope.launch { host.events(id)!!.collect { replayed += it } }
        runBlocking { withTimeout(10_000) { while (replayed.none { it is ServerEvent.Ready }) delay(25) } }
        secondJob.cancel()

        assertTrue(host.stop(id))
        assertClosed(TEST_SERVER_PORT)
        assertEquals(0, host.registry.liveCount())
    }

    @Test
    fun `stopall tears down every server and clears the registry`() {
        val site = (host.serveStatic("static:a", "alpha", root, lan = true)
            as ServerHost.StaticOutcome.Serving).entry
        val other = (host.serveStatic("static:b", "beta", root, lan = true)
            as ServerHost.StaticOutcome.Serving).entry
        assertEquals(2, host.registry.liveCount())
        assertEquals(2, host.stopAll())
        assertEquals(0, host.registry.liveCount())
        assertClosed(site.port)
        assertClosed(other.port)
    }

    @Test
    fun `a bind clash is reported and the dead server frees its row`() {
        val id = ServerHost.processId("clash")
        val events = mutableListOf<ServerEvent>()
        val flow = host.attachProcess(
            id,
            "clash",
            runner("echo 'OSError: [Errno 98] Address already in use'; exit 1"),
            lan = true
        )
        val job = scope.launch { flow.collect { events += it } }
        runBlocking { withTimeout(20_000) { while (events.none { it is ServerEvent.Exited }) delay(25) } }
        job.cancel()
        assertTrue("expected a BindFailed in $events", events.any { it is ServerEvent.BindFailed })
        val clash = events.filterIsInstance<ServerEvent.BindFailed>().first()
        assertEquals(
            "Port $TEST_SERVER_PORT is in use — stop the other server or change the port.",
            clash.message
        )
        assertNull(
            "a finished run must not keep a live registry row",
            host.registry.findById(id)?.takeIf { it.alive }
        )
    }

    @Test
    fun `refreshlan repoints every live entry at once`() {
        host.publishLanAddress("10.0.0.5")
        // Both start loopback-bound, so a LAN flip cannot invent a peer URL for
        // them — the entry says why instead (that is the honest half of 37.1).
        host.serveStatic("static:a", "alpha", root, lan = false)
        host.serveStatic("static:b", "beta", root, lan = false)
        val updated = host.refreshLan(true)
        assertEquals(2, updated.size)
        assertTrue(updated.all { it.lanShared })
        assertTrue(updated.all { it.endpoints?.lanUrl == null })
        assertTrue(updated.all { it.endpoints?.notice() == ServerEndpoints.NOT_WILDCARD })

        // A socket that IS bound to the wildcard gets its URL back on refresh,
        // and loses it again when sharing is switched off.
        host.stop("static:a")
        host.stop("static:b")
        val shared = (host.serveStatic("static:c", "gamma", root, lan = true)
            as ServerHost.StaticOutcome.Serving).entry
        assertEquals("http://10.0.0.5:${shared.port}", shared.endpoints?.lanUrl)
        assertTrue(host.refreshLan(false).all { it.endpoints?.lanUrl == null })
        assertEquals("http://10.0.0.5:${shared.port}", host.refreshLan(true).single().endpoints?.lanUrl)
    }

    @Test
    fun `publishing an unusable address leaves every entry unshared`() {
        host.publishLanAddress("169.254.1.1")
        assertEquals(null, host.lanAddress())
        val entry = (host.serveStatic("static:a", "alpha", root, lan = true)
            as ServerHost.StaticOutcome.Serving).entry
        assertNull(entry.endpoints?.lanUrl)
        assertNotNull(entry.endpoints?.notice())
    }

    companion object {
        private const val TEST_SERVER_PORT = 5099

        /**
         * A 15-line stand-in dev server: print a bind line, answer three
         * requests, then stay alive. It binds a real socket, so "the port is
         * taken" and "Stop released the port" are facts about the OS, not
         * about a mock. `python3` is guaranteed on the CI image (see
         * [ServerScaffoldE2ETest]).
         */
        private val DEV_SERVER_SOURCE =
            """
            import socket, time
            s = socket.socket()
            s.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
            s.bind(("127.0.0.1", $TEST_SERVER_PORT))
            s.listen(4)
            print(" * Running on http://127.0.0.1:$TEST_SERVER_PORT/ (CodeC test)", flush=True)
            served = 0
            while served < 3:
                conn, _ = s.accept()
                conn.sendall(b"HTTP/1.1 200 OK\r\nContent-Length: 2\r\n\r\nok")
                conn.close()
                served += 1
            time.sleep(30)
            """.trimIndent() + "\n"

        private const val DEV_SERVER_COMMAND = "python3 dev_server.py"
    }
}
