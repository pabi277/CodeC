package com.codeci.ide

import com.codeci.ide.ui.projects.ProjectConfig
import com.codeci.ide.ui.terminal.TerminalHandoff
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TerminalHandoffTest {

    @Test
    fun `safe identifiers stay unquoted`() {
        assertEquals("main.c", TerminalHandoff.shellEscape("main.c"))
        assertEquals("/data/foo/bar.c", TerminalHandoff.shellEscape("/data/foo/bar.c"))
    }

    @Test
    fun `spaces and quotes are wrapped`() {
        assertEquals("'hello world.c'", TerminalHandoff.shellEscape("hello world.c"))
        assertEquals("'it'\\''s.c'", TerminalHandoff.shellEscape("it's.c"))
        assertEquals("''", TerminalHandoff.shellEscape(""))
    }

    @Test
    fun `compile and run uses cc then a out`() {
        val cmd = TerminalHandoff.compileAndRunCommand("/data/data/com.codeci.ide/files/CodeC/projects/main.c")
        assertEquals(
            "cd /data/data/com.codeci.ide/files/CodeC/projects && " +
                "cc /data/data/com.codeci.ide/files/CodeC/projects/main.c -o a.out && " +
                "./a.out",
            cmd
        )
    }

    @Test
    fun `open in directory just cds`() {
        val cmd = TerminalHandoff.openInDirectoryCommand("/tmp/work dir")
        assertEquals("cd '/tmp/work dir'", cmd)
    }

    @Test
    fun `project run uses config from the project root`() {
        val config = ProjectConfig(
            name = "calculator",
            entry = "src/main.c",
            build = "cc -I include src/main.c src/calc.c -o bin/app",
            run = "./bin/app"
        )
        assertEquals(
            "cd /data/data/com.codeci.ide/files/CodeC/projects/calculator && " +
                "cc -I include src/main.c src/calc.c -o bin/app && ./bin/app",
            TerminalHandoff.projectRunCommand(
                "/data/data/com.codeci.ide/files/CodeC/projects/calculator",
                config
            )
        )
    }

    @Test
    fun `custom output name is escaped`() {
        val cmd = TerminalHandoff.compileAndRunCommand("/tmp/x.c", "my prog")
        assertTrue(cmd.contains("-o 'my prog'"))
        assertTrue(cmd.endsWith("./'my prog'"))
    }

    @Test
    fun `project file run compiles in place from the project folder`() {
        val dir = java.io.File(
            "/data/user/0/com.codeci.ide/files/CodeC/projects/portfolio-system3"
        )
        assertEquals(
            "cd /data/user/0/com.codeci.ide/files/CodeC/projects/portfolio-system3 && " +
                "mkdir -p bin && cc main.c -o bin/main.out && ./bin/main.out",
            TerminalHandoff.projectFileRunCommand(dir, "main.c")
        )
    }

    @Test
    fun `project file run handles nested and awkward names`() {
        val dir = java.io.File("/data/CodeC/projects/my proj")
        val cmd = TerminalHandoff.projectFileRunCommand(dir, "src/my file.c")
        assertTrue(cmd.startsWith("cd '/data/CodeC/projects/my proj'"))
        assertTrue(" cc 'src/my file.c' -o bin/my_file.out && ./bin/my_file.out" in cmd)
    }

    @Test
    fun `project file run with empty selection is a safe echo`() {
        val cmd = TerminalHandoff.projectFileRunCommand(java.io.File("/tmp/p"), "  ")
        assertEquals("cd /tmp/p && echo 'run: no file selected'", cmd)
    }

    @Test
    fun `compile parts split build and run for the output panel`() {
        val (build, run) = TerminalHandoff.compileParts(
            "/data/data/com.codeci.ide/files/CodeC/projects/main.c"
        )
        assertEquals(
            "cc /data/data/com.codeci.ide/files/CodeC/projects/main.c -o a.out",
            build
        )
        assertEquals("./a.out", run)
    }

    @Test
    fun `compile parts honor a custom output name`() {
        val (build, run) = TerminalHandoff.compileParts("/tmp/x.c", "my prog")
        assertEquals("cc /tmp/x.c -o 'my prog'", build)
        assertEquals("./'my prog'", run)
    }

    @Test
    fun `project run parts return the config commands`() {
        val config = ProjectConfig(
            name = "calculator",
            entry = "src/main.c",
            build = "cc -I include src/main.c src/calc.c -o bin/app",
            run = "./bin/app"
        )
        val (build, run) = TerminalHandoff.projectRunParts("/data/p/calculator", config)
        assertEquals("cc -I include src/main.c src/calc.c -o bin/app", build)
        assertEquals("./bin/app", run)
    }

    @Test
    fun `project run parts allow an empty build or run`() {
        val web = ProjectConfig(name = "site", type = "web", build = "", run = "")
        val (build, run) = TerminalHandoff.projectRunParts("/data/p/site", web)
        assertEquals(null, build)
        assertEquals(null, run)
    }

    @Test
    fun `interpreted parts have no build step and run python3`() {
        val (build, run) = TerminalHandoff.interpretedParts(
            "/data/data/com.codeci.ide/files/CodeC/projects/script.py"
        )
        assertEquals(null, build)
        assertEquals(
            "python3 /data/data/com.codeci.ide/files/CodeC/projects/script.py",
            run
        )
    }

    @Test
    fun `interpreted run command cds then runs python3`() {
        assertEquals(
            "cd /data/scripts && python3 /data/scripts/main.py",
            TerminalHandoff.interpretedRunCommand("/data/scripts/main.py")
        )
    }

    @Test
    fun `project file run command uses python3 for py files`() {
        val dir = java.io.File("/tmp/p")
        assertEquals(
            "cd /tmp/p && python3 main.py",
            TerminalHandoff.projectFileRunCommand(dir, "main.py")
        )
        assertEquals(
            "cd /tmp/p && python3 'a b.py'",
            TerminalHandoff.projectFileRunCommand(dir, "a b.py")
        )
        assertEquals(
            "cd /tmp/p && python3 src/run.py",
            TerminalHandoff.projectFileRunCommand(dir, "src/run.py")
        )
    }

    @Test
    fun `project file parts split build and run for the output panel`() {
        val dir = java.io.File("/data/CodeC/projects/p1")
        val (build, run, terminal) = TerminalHandoff.projectFileParts(dir, "src/tool.c")
        assertEquals("mkdir -p bin && cc src/tool.c -o bin/tool.out", build)
        assertEquals("./bin/tool.out", run)
        assertEquals(
            "cd /data/CodeC/projects/p1 && mkdir -p bin && cc src/tool.c -o bin/tool.out && ./bin/tool.out",
            terminal
        )
    }

    @Test
    fun `project file parts for py has no build step`() {
        val dir = java.io.File("/tmp/p")
        val (build, run, terminal) = TerminalHandoff.projectFileParts(dir, "main.py")
        assertEquals(null, build)
        assertEquals("python3 main.py", run)
        assertEquals("cd /tmp/p && python3 main.py", terminal)
    }

    @Test
    fun `project file parts for blank selection is a safe echo`() {
        val (build, run, terminal) = TerminalHandoff.projectFileParts(java.io.File("/tmp/p"), "  ")
        assertEquals(null, build)
        assertEquals(null, run)
        assertEquals("cd /tmp/p && echo 'run: no file selected'", terminal)
    }
}
