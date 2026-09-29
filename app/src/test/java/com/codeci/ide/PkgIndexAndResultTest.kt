package com.codeci.ide

import com.codeci.ide.ui.modules.PkgResult
import com.codeci.ide.ui.terminal.ShellEnvironment
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Phase 71.1 — two behaviours of the generated `pkg` script, run for real under
 * `/bin/sh` against mock `apt-get`/`gpgv`/`curl` (the same harness shape as
 * `ShellEnvironmentTest`):
 *
 * 1. **No more mandatory `pkg update`.** A fresh userland has no package index,
 *    so the first `pkg install` used to fail until the user typed `pkg update` —
 *    a step nobody had been told about (owner, 2026-09-29). Install / upgrade /
 *    search now refresh the index themselves, once, when there is none.
 * 2. **The result is reported.** Every install/upgrade/uninstall/update leaves
 *    one line for the Packages row to read (`PkgResult`), on success and on
 *    failure, without changing what the user sees.
 */
class PkgIndexAndResultTest {

    private class Fixture(val base: File) {
        val prefix = File(base, "usr")
        val bin = File(prefix, "bin")
        val state = File(prefix, "var/lib/codec-pkg")
        val lists = File(prefix, "var/lib/apt/lists")
        val calls = File(base, "apt-calls.log")

        init {
            bin.mkdirs()
            File(prefix, "var/cache/apt/archives").mkdirs()
            File(prefix, "etc/apt/keyrings").mkdirs()
            File(prefix, "etc/apt/keyrings/${ShellEnvironment.PACKAGE_REPOSITORY_KEYRING}").writeText("test-key")
            exe("apt-get", """
                #!/bin/sh
                echo "${'$'}*" >> "${calls.absolutePath}"
                case "${'$'}*" in
                  *" update"|update)
                    [ -n "${'$'}{FAIL_UPDATE:-}" ] && exit 100
                    mkdir -p "${lists.absolutePath}"
                    echo "Package: nano" > "${lists.absolutePath}/host_dists_stable_main_binary-aarch64_Packages"
                    exit 0 ;;
                  *--download-only*)
                    [ -n "${'$'}{FAIL_DOWNLOAD:-}" ] && { echo "E: Unable to locate package" >&2; exit 100; }
                    printf fakedeb > "${prefix.absolutePath}/var/cache/apt/archives/nano_9.2_aarch64.deb"
                    exit 0 ;;
                  *) exit 0 ;;
                esac
            """)
            exe("apt-cache", "#!/bin/sh\necho \"nano - Small, friendly text editor\"\nexit 0")
            exe("dpkg-deb", """
                #!/bin/sh
                case "${'$'}*" in
                  *"-f "*Package*) echo nano ;;
                  *"-f "*Version*) echo 9.2 ;;
                  *"-f "*Architecture*) echo aarch64 ;;
                  *"-f "*Installed-Size*) echo 840 ;;
                  *"--contents "*) echo data/data/com.codeci.ide/files/usr/bin/nano ;;
                esac
                exit 0
            """)
            exe("dpkg", "#!/bin/sh\n[ \"${'$'}1\" = \"--print-architecture\" ] && echo aarch64\nexit 0")
            exe("gpgv", """
                #!/bin/sh
                out=""
                while [ "${'$'}#" -gt 0 ]; do
                  if [ "${'$'}1" = "--output" ]; then out="${'$'}2"; shift 2; else shift; fi
                done
                [ -n "${'$'}out" ] && printf "Origin: CodeC\nSuite: stable\n" > "${'$'}out"
                exit 0
            """)
            exe("curl", """
                #!/bin/sh
                dest=""
                while [ "${'$'}#" -gt 0 ]; do
                  if [ "${'$'}1" = "-o" ]; then dest="${'$'}2"; shift 2; else shift; fi
                done
                [ -n "${'$'}dest" ] && printf "Origin: CodeC\nSuite: stable\n" > "${'$'}dest"
                exit 0
            """)
            exe("pkg", ShellEnvironment.pkgScript())
        }

        private fun exe(name: String, body: String) {
            File(bin, name).apply {
                writeText(body.trimIndent() + "\n")
                setExecutable(true)
            }
        }

        class Run(val exit: Int, val output: String)

        fun pkg(vararg args: String, env: Map<String, String> = emptyMap()): Run {
            val proc = ProcessBuilder(listOf("/bin/sh", File(bin, "pkg").absolutePath) + args)
                .redirectErrorStream(true)
                .apply {
                    environment()["PREFIX"] = prefix.absolutePath
                    environment()["PATH"] = "${bin.absolutePath}:/bin:/usr/bin"
                    environment().putAll(env)
                }
                .start()
            proc.outputStream.close()
            val done = proc.waitFor(20, TimeUnit.SECONDS)
            if (!done) proc.destroyForcibly()
            val out = proc.inputStream.bufferedReader().readText()
            assertTrue("pkg ${args.toList()} timed out: $out", done)
            return Run(proc.exitValue(), out)
        }

        fun aptCalls(): List<String> = if (calls.isFile) calls.readLines() else emptyList()
        fun updates(): Int = aptCalls().count { it.endsWith(" update") || it == "update" }
        fun result(): PkgResult? = PkgResult.read(prefix)
        fun pending(): Boolean = File(state, "transaction.pending").exists()
        fun lockHeld(): Boolean = File(state, "lock").exists()
    }

    private fun withFixture(block: (Fixture) -> Unit) {
        val base = File(System.getProperty("java.io.tmpdir"), "codec-pkg-71-${System.nanoTime()}")
        try {
            block(Fixture(base))
        } finally {
            base.deleteRecursively()
        }
    }

    // ---- 1. the index ----

    @Test
    fun `the first install after setup refreshes the package list itself`() = withFixture { f ->
        val run = f.pkg("install", "-y", "nano")

        assertEquals(run.output, 0, run.exit)
        assertEquals("exactly one refresh", 1, f.updates())
        assertTrue(run.output, run.output.contains("no package list yet"))
        assertTrue(run.output, run.output.contains("package list ready"))
        assertTrue(run.output, run.output.contains("pkg: installed nano"))
        // and the refresh came BEFORE the download
        val calls = f.aptCalls()
        assertTrue(calls.indexOfFirst { it.endsWith(" update") } < calls.indexOfFirst { it.contains("--download-only") })
    }

    @Test
    fun `a later install does not refresh again`() = withFixture { f ->
        f.pkg("install", "-y", "nano")
        val second = f.pkg("install", "-y", "nano")

        assertEquals(second.output, 0, second.exit)
        assertEquals(1, f.updates())
        assertFalse(second.output, second.output.contains("no package list yet"))
    }

    @Test
    fun `a failed refresh stops the install cleanly - nothing downloaded, no pending transaction`() = withFixture { f ->
        val run = f.pkg("install", "-y", "nano", env = mapOf("FAIL_UPDATE" to "1"))

        assertTrue(run.output, run.exit != 0)
        assertFalse(f.aptCalls().any { it.contains("--download-only") })
        assertFalse(f.pending())
        assertFalse("the lock must be released", f.lockHeld())
    }

    @Test
    fun `search and upgrade also get their list`() = withFixture { f ->
        val search = f.pkg("search", "nano")
        assertEquals(search.output, 0, search.exit)
        assertEquals(1, f.updates())
        assertTrue(search.output, search.output.contains("nano - Small"))

        f.lists.deleteRecursively()
        val upgrade = f.pkg("upgrade", "-y")
        assertEquals(upgrade.output, 0, upgrade.exit)
        assertEquals(2, f.updates())
    }

    @Test
    fun `an explicit pkg update still works and does not double up`() = withFixture { f ->
        val run = f.pkg("update")
        assertEquals(run.output, 0, run.exit)
        assertEquals(1, f.updates())
        assertFalse(run.output, run.output.contains("no package list yet"))
    }

    @Test
    fun `install, upgrade and search all go through ensure_index`() {
        val script = ShellEnvironment.pkgScript()
        assertEquals("install + upgrade + search", 3, Regex("ensure_index \\|\\|").findAll(script).count())
        assertTrue(script.contains("index_cached()"))
        assertTrue(script.contains("var/lib/apt/lists/*Packages*"))
    }

    // ---- 2. the result ----

    @Test
    fun `a successful install leaves its result`() = withFixture { f ->
        val before = System.currentTimeMillis() / 1000
        f.pkg("install", "-y", "nano")

        val r = f.result()!!
        assertEquals("install", r.command)
        assertEquals(0, r.exit)
        assertEquals(listOf("nano"), r.targets)
        assertTrue(r.epochSec >= before - 1)
    }

    @Test
    fun `a failed download leaves a non-zero result naming the package`() = withFixture { f ->
        val run = f.pkg("install", "-y", "nano", env = mapOf("FAIL_DOWNLOAD" to "1"))

        assertEquals(100, run.exit)
        val r = f.result()!!
        assertEquals(100, r.exit)
        assertEquals(listOf("nano"), r.targets)
        assertTrue(r.isInstall)
    }

    @Test
    fun `an early error is reported too`() = withFixture { f ->
        File(f.bin, "gpgv").delete() // require_backend refuses before anything runs
        val run = f.pkg("install", "-y", "nano")

        assertEquals(1, run.exit)
        assertEquals(1, f.result()!!.exit)
    }

    @Test
    fun `the exit status of pkg itself is unchanged`() = withFixture { f ->
        assertEquals(0, f.pkg("install", "-y", "nano").exit)
        assertEquals(1, f.pkg("frobnicate").exit)
        assertEquals(1, f.pkg("uninstall", "-y", "bash").exit) // refuses a base package
    }

    @Test
    fun `help leaves no result and prints nothing extra`() = withFixture { f ->
        val help = f.pkg("help")
        assertEquals(0, help.exit)
        assertFalse(File(f.state, "last-result").exists())
        assertFalse(help.output.contains("last-result"))
    }

    @Test
    fun `a finished operation releases its lock and pending marker`() = withFixture { f ->
        f.pkg("install", "-y", "nano")
        assertFalse(f.lockHeld())
        assertFalse(f.pending())
        f.pkg("install", "-y", "nano", env = mapOf("FAIL_DOWNLOAD" to "1"))
        assertFalse(f.lockHeld())
        assertFalse(f.pending())
    }
}
