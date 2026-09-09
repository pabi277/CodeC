package com.codeci.ide

import com.codeci.ide.ui.services.ServerLaunch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 37.2 — the `exec` policy behind "Stop releases the port".
 *
 * Measured on the host JVM: `sh -c "python3 app.py"` left the python process
 * answering after the shell was destroyed (an orphan holding the port, with
 * CodeC no longer able to stop it). Prefixing a *single simple command* with
 * `exec` makes the server the process the runner kills; every command with
 * shell structure must keep the old shape, because rewriting those is how a
 * working run becomes a broken one.
 */
class ServerLaunchTest {

    @Test
    fun `the preset server commands are all exec-able`() {
        // ProjectConfig.defaultFor(...).run for the three server types.
        for (command in listOf("python3 app.py", "python3 main.py", "./bin/server")) {
            assertTrue(command, ServerLaunch.isSingleSimpleCommand(command))
            assertEquals("exec $command", ServerLaunch.shellArgument(command))
        }
    }

    @Test
    fun `shell structure is left exactly as the user wrote it`() {
        for (
            command in listOf(
                "cd site && python3 -m http.server 8000",
                "python3 app.py; echo done",
                "flask run | tee log.txt",
                "python3 app.py 2>&1",
                "PORT=8080 python3 app.py",
                "exit 1",
                "true",
                ""
            )
        ) {
            assertFalse("must not rewrite: $command", ServerLaunch.isSingleSimpleCommand(command))
            assertEquals(command, ServerLaunch.shellArgument(command))
        }
    }

    @Test
    fun `an already exec'd command is not doubled`() {
        assertFalse(ServerLaunch.isSingleSimpleCommand("exec python3 app.py"))
        assertEquals("exec python3 app.py", ServerLaunch.shellArgument("exec python3 app.py"))
    }

    @Test
    fun `surrounding whitespace is tolerated`() {
        assertEquals("exec gunicorn app:app", ServerLaunch.shellArgument("  gunicorn app:app  "))
    }
}
