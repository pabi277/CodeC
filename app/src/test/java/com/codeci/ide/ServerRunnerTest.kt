package com.codeci.ide

import com.codeci.ide.ui.services.ServerEvent
import com.codeci.ide.ui.services.ServerRunner
import java.io.File
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 14 — host-JVM tests for the background server runner. /bin/sh exists
 * on the CI runners, so the real process pipeline (ready detection, live
 * process, exit, failure, cleanup) is exercised end to end.
 */
class ServerRunnerTest {

    private val shell = File("/bin/sh")
    private val env = mapOf("PATH" to "/usr/bin:/bin")

    private fun tempDir(): File = File.createTempFile("codec-server", "").apply {
        delete()
        mkdirs()
    }

    private fun runner(
        command: String,
        dir: File,
        readyTimeoutSeconds: Long = 3
    ) = ServerRunner(shell, env, command, dir, readyTimeoutSeconds)

    @Test
    fun `ready line yields Ready with the loopback url and keeps streaming`() = runBlocking {
        val dir = tempDir()
        val runner = runner("echo ' * Running on http://127.0.0.1:5000'; echo up; sleep 30", dir)
        val events = mutableListOf<ServerEvent>()
        val job = launch { runner.start().collect { events += it } }
        withTimeout(5_000) {
            while (events.none { it is ServerEvent.Ready }) delay(20)
        }
        val ready = events.filterIsInstance<ServerEvent.Ready>().first()
        assertEquals("http://127.0.0.1:5000", ready.url)
        assertEquals(5000, ready.port)
        assertTrue(events.any { it is ServerEvent.Output && it.line == "up" })
        assertTrue(runner.hasLiveProcess())
        runner.stop()
        withTimeout(3_000) { job.join() }
        assertFalse(runner.hasLiveProcess())
    }

    @Test
    fun `server exit is reported with its exit code`() = runBlocking {
        val dir = tempDir()
        // Brief stay-alive so the reader thread drains the bind line before
        // the process exits (otherwise Ready/Output can race the Exited event).
        val events = runner(
            "echo 'Uvicorn running on http://127.0.0.1:8000'; sleep 1; exit 7",
            dir
        ).start().toList()
        assertTrue(events.any { it is ServerEvent.Ready })
        val exited = events.filterIsInstance<ServerEvent.Exited>().single()
        assertEquals(7, exited.exitCode)
        assertTrue(events.any { it is ServerEvent.Output && it.line.contains("Uvicorn running on") })
    }

    @Test
    fun `missing command fails without starting a process`() = runBlocking {
        val dir = tempDir()
        val events = runner("   ", dir).start().toList()
        assertTrue(events.any { it is ServerEvent.Failed })
        assertFalse(events.any { it is ServerEvent.Ready })
    }

    @Test
    fun `shell failure is reported as a failed run`() = runBlocking {
        val dir = tempDir()
        val events = runner("definitely-not-a-command-xyz-42", dir).start().toList()
        // The reader sees `sh: ...: not found`; the process exits code 127.
        assertTrue(events.any { it is ServerEvent.Exited && it.exitCode != 0 })
    }

    @Test
    fun `no bind line emits a timeout warning and stays alive`() = runBlocking {
        val dir = tempDir()
        val runner = runner("echo started; sleep 30", dir, readyTimeoutSeconds = 1)
        val events = mutableListOf<ServerEvent>()
        val job = launch {
            runner.start().collect { events += it }
        }
        withTimeout(5_000) {
            while (events.none { it is ServerEvent.ReadyTimeout }) delay(50)
        }
        assertTrue(events.any { it is ServerEvent.ReadyTimeout })
        assertTrue(runner.hasLiveProcess())
        runner.stop()
        job.join()
    }

    @Test
    fun `stop kills the live process`() = runBlocking {
        val dir = tempDir()
        val runner = runner("sleep 30", dir)
        val job = launch { runner.start().collect { } }
        withTimeout(5_000) {
            while (!runner.hasLiveProcess()) delay(20)
        }
        runner.stop()
        withTimeout(3_000) { job.join() }
        assertFalse(runner.hasLiveProcess())
    }

    @Test
    fun `missing working directory fails`() = runBlocking {
        val events = runner("echo hi", File("/nonexistent-codec-server-dir-xyz")).start().toList()
        assertEquals(1, events.filterIsInstance<ServerEvent.Failed>().size)
        assertNotNull(events.filterIsInstance<ServerEvent.Failed>().first().message)
    }
}
