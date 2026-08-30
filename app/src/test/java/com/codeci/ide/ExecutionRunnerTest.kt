package com.codeci.ide

import com.codeci.ide.ui.services.ExecutionRunner
import com.codeci.ide.ui.services.RunEvent
import com.codeci.ide.ui.services.RunPhase
import com.codeci.ide.ui.services.RunSpec
import java.io.File
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Host-JVM tests for the Phase 11 runner: /bin/sh exists on CI Linux runners,
 * so the real process pipeline is exercised end to end.
 */
class ExecutionRunnerTest {

    private val shell = File("/bin/sh")
    private val env = mapOf("PATH" to "/usr/bin:/bin")

    private fun runner(buildTimeout: Long = 5, runTimeout: Long = 5) =
        ExecutionRunner(shell, env, buildTimeoutSeconds = buildTimeout, runTimeoutSeconds = runTimeout)

    private fun tempDir(): File = File.createTempFile("codec-runner", "").apply {
        delete()
        mkdirs()
    }

    @Test
    fun `build then run streams output and both exit codes`() = runBlocking {
        val dir = tempDir()
        val events = runner().run(
            RunSpec(dir, "echo building", "echo running; exit 3")
        ).toList()

        assertTrue(events.any { it == RunEvent.PhaseChanged(RunPhase.BUILDING) })
        assertTrue(events.any { it == RunEvent.PhaseChanged(RunPhase.RUNNING) })
        assertTrue(
            events.any {
                it is RunEvent.Output && it.phase == RunPhase.BUILDING && it.line == "building"
            }
        )
        assertTrue(
            events.any {
                it is RunEvent.Output && it.phase == RunPhase.RUNNING && it.line == "running"
            }
        )
        assertTrue(events.any { it is RunEvent.BuildFinished && it.exitCode == 0 && !it.timedOut })
        val runFinished = events.filterIsInstance<RunEvent.RunFinished>().single()
        assertEquals(3, runFinished.exitCode)
        assertFalse(runFinished.timedOut)
        assertTrue(runFinished.durationMs >= 0)
    }

    @Test
    fun `failing build skips the run phase`() = runBlocking {
        val dir = tempDir()
        val events = runner().run(
            RunSpec(dir, "echo bad && exit 9", "echo should-not-run")
        ).toList()

        assertTrue(events.any { it is RunEvent.BuildFinished && it.exitCode == 9 })
        assertFalse(events.any { it == RunEvent.PhaseChanged(RunPhase.RUNNING) })
        assertFalse(events.any { it is RunEvent.RunFinished })
        assertFalse(events.any { it is RunEvent.Output && it.phase == RunPhase.RUNNING })
    }

    @Test
    fun `run without build skips straight to running`() = runBlocking {
        val dir = tempDir()
        val events = runner().run(
            RunSpec(dir, null, "echo only-run")
        ).toList()

        assertFalse(events.any { it is RunEvent.BuildFinished })
        assertTrue(events.any { it == RunEvent.PhaseChanged(RunPhase.RUNNING) })
        assertTrue(events.any { it is RunEvent.RunFinished && it.exitCode == 0 })
    }

    @Test
    fun `no commands fails with a message`() = runBlocking {
        val dir = tempDir()
        val events = runner().run(RunSpec(dir, "   ", null)).toList()
        assertTrue(events.any { it is RunEvent.Failed })
    }

    @Test
    fun `missing working directory fails`() = runBlocking {
        val events = runner().run(
            RunSpec(File("/nonexistent-codec-dir-xyz"), "echo hi", null)
        ).toList()
        assertTrue(events.any { it is RunEvent.Failed })
    }

    @Test
    fun `long running program times out and is reported`() = runBlocking {
        val dir = tempDir()
        val events = runner(runTimeout = 1).run(
            RunSpec(dir, null, "sleep 5")
        ).toList()

        val finished = events.filterIsInstance<RunEvent.RunFinished>().single()
        assertTrue(finished.timedOut)
        assertEquals(ExecutionRunner.TIMED_OUT_EXIT_CODE, finished.exitCode)
        assertTrue(finished.durationMs >= 1_000)
    }

    @Test
    fun `send input reaches the running program stdin`() = runBlocking {
        val dir = tempDir()
        val runner = runner()
        val events = mutableListOf<RunEvent>()
        val collectJob = launch {
            runner.run(
                RunSpec(dir, null, "read line && echo \"got: $line\"")
            ).collect { events += it }
        }
        // Wait until the child process exists and is blocked on its read.
        withTimeout(5_000) {
            while (!runner.hasLiveProcess()) delay(20)
        }
        // Let the child actually start reading before sending.
        delay(300)
        runner.sendInput("hello panel")
        collectJob.join()

        assertTrue(
            events.any {
                it is RunEvent.Output &&
                    it.phase == RunPhase.RUNNING &&
                    it.line == "got: hello panel"
            }
        )
        assertTrue(events.any { it is RunEvent.RunFinished && it.exitCode == 0 })
    }
}
