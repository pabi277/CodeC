package com.codeci.ide

import com.codeci.ide.ui.projects.ProjectConfig
import com.codeci.ide.ui.projects.ProjectScaffold
import com.codeci.ide.ui.services.ServerEvent
import com.codeci.ide.ui.services.ServerRunner
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 14 — end-to-end host test of the server acceptance path.
 *
 * This writes the exact bytes a new project receives ([ProjectScaffold]),
 * then runs the exact `build`/`run` commands from
 * [ProjectConfig.defaultFor] through the real [ServerRunner] process
 * pipeline, and fetches the served page over loopback HTTP. It proves the
 * server-side half of the device recipe without a phone:
 *
 *   RUN ▶ -> output bind line -> ServerRunner.Ready -> HTTP 200 page
 *   edit index.html -> next GET shows the new page (no restart)
 *   Stop -> live process is gone
 *
 * The only acceptance step that cannot run on CI is the Compose WebView
 * itself (auto-open, ● live badge, Save -> reload); that stays on device.
 * Requires python3 and cc on the runner (GitHub ubuntu images provide both).
 */
class ServerScaffoldE2ETest {

    private val shell = File("/bin/sh")
    private val env = mapOf("PATH" to "/usr/bin:/bin")

    /** Writes [ProjectScaffold.filesFor] to a fresh temp dir (like ProjectManager). */
    private fun tempProject(type: String): File {
        val root = File.createTempFile("codec-e2e", "").apply {
            delete()
            mkdirs()
        }
        for (scaffold in ProjectScaffold.filesFor(type)) {
            val file = File(root, scaffold.relativePath)
            file.parentFile?.mkdirs()
            file.writeText(scaffold.content)
        }
        return root
    }

    /** Runs the preset's `build` command (if any) and asserts it succeeded. */
    private fun buildProject(dir: File, build: String) {
        if (build.isBlank()) return
        val process = ProcessBuilder(shell.absolutePath, "-c", build)
            .directory(dir)
            .redirectErrorStream(true)
            .apply {
                environment().clear()
                environment().putAll(env)
            }
            .start()
        val output = process.inputStream.bufferedReader().readText()
        val code = process.waitFor()
        assertEquals("preset build failed:\n$output", 0, code)
    }

    private fun httpGet(url: String): Pair<Int, String> {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.connectTimeout = 3_000
        connection.readTimeout = 3_000
        try {
            val code = connection.responseCode
            val body = (if (code < 400) connection.inputStream else connection.errorStream)
                ?.bufferedReader()?.use { it.readText() } ?: ""
            return code to body
        } finally {
            connection.disconnect()
        }
    }

    /**
     * Starts the preset server, waits for the bind line via [ServerRunner],
     * runs [verify] against the project dir + live events, then stops and
     * asserts cleanup. Fails fast with the captured output on any early exit.
     */
    private fun serverRoundTrip(
        type: String,
        port: Int,
        verify: (File, MutableList<ServerEvent>) -> Unit
    ) = runBlocking {
        val dir = tempProject(type)
        val config = ProjectConfig.defaultFor("demo", type)
        buildProject(dir, config.build)
        val runner = ServerRunner(shell, env, config.run, dir, readyTimeoutSeconds = 10)
        val events = mutableListOf<ServerEvent>()
        val job = launch { runner.start().collect { events += it } }
        try {
            withTimeout(20_000) {
                while (events.none { it is ServerEvent.Ready }) {
                    events.filterIsInstance<ServerEvent.Failed>().firstOrNull()?.let {
                        throw AssertionError(
                            "server failed: ${it.message}; output=" +
                                events.filterIsInstance<ServerEvent.Output>().map { it.line }
                        )
                    }
                    events.filterIsInstance<ServerEvent.Exited>().firstOrNull()?.let {
                        throw AssertionError(
                            "server exited early (${it.exitCode}); output=" +
                                events.filterIsInstance<ServerEvent.Output>().map { it.line }
                        )
                    }
                    delay(50)
                }
            }
            val ready = events.filterIsInstance<ServerEvent.Ready>().first()
            assertEquals("http://127.0.0.1:$port", ready.url)
            assertEquals(port, ready.port)
            verify(dir, events)
        } finally {
            runner.stop()
            withTimeout(5_000) { job.join() }
            assertFalse("server still alive after Stop", runner.hasLiveProcess())
            dir.deleteRecursively()
        }
    }

    @Test
    fun `flask scaffold starts via preset run, serves the live index and hot-reads edits`() {
        serverRoundTrip("python-flask", 5000) { dir, events ->
            val (code, body) = httpGet("http://127.0.0.1:5000/")
            assertEquals(200, code)
            assertTrue(body.contains("Welcome to CodeC Flask App!"))
            assertTrue(
                events.any {
                    it is ServerEvent.Output && it.line.contains("Running on http://127.0.0.1:5000")
                }
            )

            // Acceptance: edit the page file the server reads per request ->
            // the very next request reflects it, no restart, no rebuild.
            val index = File(dir, "index.html")
            assertTrue(index.isFile)
            index.writeText("<!doctype html><html><body><h1>Edited on the fly</h1></body></html>")
            val (code2, body2) = httpGet("http://127.0.0.1:5000/")
            assertEquals(200, code2)
            assertTrue(body2.contains("Edited on the fly"))
        }
    }

    @Test
    fun `fastapi scaffold starts via preset run and serves the live index`() {
        serverRoundTrip("python-fastapi", 8000) { dir, events ->
            val (code, body) = httpGet("http://127.0.0.1:8000/")
            assertEquals(200, code)
            assertTrue(body.contains("Welcome to CodeC FastAPI App!"))
            assertTrue(
                events.any {
                    it is ServerEvent.Output && it.line.contains("Uvicorn running on http://127.0.0.1:8000")
                }
            )

            val index = File(dir, "index.html")
            assertTrue(index.isFile)
            index.writeText("<!doctype html><html><body><h1>FastAPI edited</h1></body></html>")
            val (code2, body2) = httpGet("http://127.0.0.1:8000/")
            assertEquals(200, code2)
            assertTrue(body2.contains("FastAPI edited"))
        }
    }

    @Test
    fun `c microservice preset builds with cc, starts and serves 8080`() {
        serverRoundTrip("c-microservice", 8080) { _, events ->
            val (code, body) = httpGet("http://127.0.0.1:8080/")
            assertEquals(200, code)
            assertTrue(body.contains("Welcome to CodeC C Microservice!"))
            assertTrue(
                events.any {
                    it is ServerEvent.Output &&
                        it.line.contains("CodeC server listening on http://127.0.0.1:8080")
                }
            )
        }
    }
}
