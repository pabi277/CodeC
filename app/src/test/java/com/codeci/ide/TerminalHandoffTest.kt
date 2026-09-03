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
    fun `compile and run uses gcc then the out binary`() {
        // Phase 21.1: the retired TCC `cc` shim is gone — the registry's gcc
        // profile drives the compile line and names <leaf>.out.
        val cmd = TerminalHandoff.compileAndRunCommand("/data/data/com.codeci.ide/files/CodeC/projects/main.c")
        assertEquals(
            "cd /data/data/com.codeci.ide/files/CodeC/projects && " +
                "gcc /data/data/com.codeci.ide/files/CodeC/projects/main.c -o main.out -lm && " +
                "./main.out",
            cmd
        )
    }

    @Test
    fun `compile and run dispatches interpreted languages to their interpreter`() {
        assertEquals(
            "cd /tmp/s && python3 /tmp/s/app.py",
            TerminalHandoff.compileAndRunCommand("/tmp/s/app.py")
        )
        assertEquals(
            "cd /tmp/s && node /tmp/s/app.js",
            TerminalHandoff.compileAndRunCommand("/tmp/s/app.js")
        )
        assertEquals(
            "cd /tmp/s && g++ /tmp/s/app.cpp -o app.out -lm && ./app.out",
            TerminalHandoff.compileAndRunCommand("/tmp/s/app.cpp")
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
        // The registry-driven path names the binary after the source; the
        // custom-name override still applies to the legacy compileParts form.
        val (build, run) = TerminalHandoff.compileParts("/tmp/x.c", "my prog")
        assertTrue(build.contains("-o 'my prog'"))
        assertEquals("./'my prog'", run)
    }

    @Test
    fun `project file run compiles in place from the project folder`() {
        val dir = java.io.File(
            "/data/user/0/com.codeci.ide/files/CodeC/projects/portfolio-system3"
        )
        assertEquals(
            "cd /data/user/0/com.codeci.ide/files/CodeC/projects/portfolio-system3 && " +
                "mkdir -p bin && gcc main.c -o bin/main.out -lm && ./bin/main.out",
            TerminalHandoff.projectFileRunCommand(dir, "main.c")
        )
    }

    @Test
    fun `project file run handles nested and awkward names`() {
        val dir = java.io.File("/data/CodeC/projects/my proj")
        val cmd = TerminalHandoff.projectFileRunCommand(dir, "src/my file.c")
        assertTrue(cmd.startsWith("cd '/data/CodeC/projects/my proj'"))
        assertTrue(" gcc 'src/my file.c' -o bin/my_file.out -lm && ./bin/my_file.out" in cmd)
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
            "gcc /data/data/com.codeci.ide/files/CodeC/projects/main.c -o a.out -lm",
            build
        )
        assertEquals("./a.out", run)
    }

    @Test
    fun `compile parts honor a custom output name`() {
        val (build, run) = TerminalHandoff.compileParts("/tmp/x.c", "my prog")
        assertEquals("gcc /tmp/x.c -o 'my prog' -lm", build)
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
        assertEquals("mkdir -p bin && gcc src/tool.c -o bin/tool.out -lm", build)
        assertEquals("./bin/tool.out", run)
        assertEquals(
            "cd /data/CodeC/projects/p1 && mkdir -p bin && gcc src/tool.c -o bin/tool.out -lm && " +
                "./bin/tool.out",
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
    fun `project file parts route every registry language`() {
        val dir = java.io.File("/tmp/p")
        assertEquals("ruby s.rb", TerminalHandoff.projectFileParts(dir, "s.rb").second)
        assertEquals("lua s.lua", TerminalHandoff.projectFileParts(dir, "s.lua").second)
        assertEquals("php s.php", TerminalHandoff.projectFileParts(dir, "s.php").second)
        assertEquals("bash s.sh", TerminalHandoff.projectFileParts(dir, "s.sh").second)
    }

    @Test
    fun `project file parts for blank selection is a safe echo`() {
        val (build, run, terminal) = TerminalHandoff.projectFileParts(java.io.File("/tmp/p"), "  ")
        assertEquals(null, build)
        assertEquals(null, run)
        assertEquals("cd /tmp/p && echo 'run: no file selected'", terminal)
    }
}
