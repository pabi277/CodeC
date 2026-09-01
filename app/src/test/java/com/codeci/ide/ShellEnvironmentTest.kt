package com.codeci.ide

import com.codeci.ide.ui.terminal.ShellEnvironment
import com.codeci.ide.ui.terminal.TarGzExtractor
import com.codeci.ide.ui.viewmodels.TerminalViewModel
import java.io.File
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ShellEnvironmentTest {

    @Test
    fun `cc script runs TCC with static musl flags then chmods the ELF`() {
        val script = ShellEnvironment.ccScript()
        assertTrue(script.startsWith("#!/system/bin/sh"))
        assertTrue(script.contains("\"\$TCC_BIN\""))
        assertFalse(script.contains("exec \"\$TCC_BIN\""))
        assertTrue(script.contains("chmod 755"))
        assertTrue(script.contains("-static"))
        assertTrue(script.contains("-I include-tcc"))
        assertTrue(script.contains("-I include"))
        assertTrue(script.contains("-L ."))
        assertTrue(script.contains("-nostdlib"))
        assertTrue(script.contains("crt1.o"))
        assertTrue(script.contains("crti.o"))
        assertTrue(script.contains("crtn.o"))
        assertTrue(script.contains("libtcc1.a"))
        assertTrue(script.contains("libc.a"))
        assertFalse(script.contains("\\"))
        assertTrue(script.contains("cd \"\$TCC_BUNDLE\""))
        assertTrue(script.contains("CC_STD"))
        assertTrue(script.contains("CODEC_PROJECTS"))
        assertTrue(script.contains("outfile"))
        assertTrue(script.contains("crtn.o -o"))
        assertTrue(script.contains("continue"))
        assertTrue(script.contains("codec_stdio.o"))
    }

    @Test
    fun `pkg script is guarded to the CodeC repository`() {
        val script = ShellEnvironment.pkgScript()
        assertTrue(script.contains("apt-get"))
        assertTrue(script.contains("dpkg"))
        assertTrue(script.contains("pkg update"))
        assertTrue(script.contains("pkg install"))
        assertTrue(script.contains("pkg uninstall"))
        assertTrue(script.contains("https://pabi277.github.io/CodeC/dev"))
        assertTrue(script.contains("sourceparts=-"))
        assertTrue(script.contains("com.codeci.ide/files/usr"))
        assertTrue(script.contains("maintainer script"))
        assertTrue(script.contains("signed-by="))
        assertTrue(script.contains("codec-archive-keyring-v1.gpg"))
        assertTrue(script.contains("bin/gpgv"))
        assertTrue(script.contains("/InRelease"))
        assertFalse(script.contains("trusted=yes"))
        assertFalse(script.contains("Release.sha256"))
        assertFalse(script.contains("com.termux/files/usr"))
    }

    @Test
    fun `pkg repair reclaims a lock whose owner process is dead`() {
        val base = File(System.getProperty("java.io.tmpdir"), "codec-lock-${System.nanoTime()}")
        try {
            val prefix = File(base, "usr")
            val bin = File(prefix, "bin").apply { mkdirs() }
            listOf("apt-get", "dpkg", "gpgv").forEach { name ->
                File(bin, name).apply {
                    writeText("#!/bin/sh\nexit 0\n")
                    setExecutable(true)
                }
            }
            File(prefix, "etc/apt/keyrings").mkdirs()
            File(prefix, "etc/apt/keyrings/${ShellEnvironment.PACKAGE_REPOSITORY_KEYRING}")
                .writeText("public-test-key")
            val lock = File(prefix, "var/lib/codec-pkg/lock").apply { mkdirs() }
            // Linux/Android pid_max is far below this, so kill -0 must report
            // that the force-stopped lock owner no longer exists.
            File(lock, "pid").writeText("99999999\n")
            val pkg = File(bin, "pkg").apply {
                writeText(ShellEnvironment.pkgScript())
                setExecutable(true)
            }

            val process = ProcessBuilder("/bin/sh", pkg.absolutePath, "repair")
                .redirectErrorStream(true)
                .apply { environment()["PREFIX"] = prefix.absolutePath }
                .start()
            val completed = process.waitFor(10, TimeUnit.SECONDS)
            if (!completed) process.destroyForcibly()
            val output = process.inputStream.bufferedReader().readText()

            assertTrue("pkg repair timed out: $output", completed)
            assertEquals(output, 0, process.exitValue())
            assertTrue(output.contains("recovered stale package-operation lock (dead pid 99999999)"))
            assertTrue(output.contains("pkg: no interrupted transaction"))
            assertFalse(lock.exists())
        } finally {
            base.deleteRecursively()
        }
    }

    @Test
    fun `pkg locking keeps a live owner and atomically claims stale locks`() {
        val script = ShellEnvironment.pkgScript()
        assertTrue(script.contains("kill -0 \"\$owner_pid\""))
        assertTrue(script.contains("mkdir \"\$LOCK/reclaiming\""))
        assertTrue(script.contains("mv \"\$LOCK\" \"\$stale_lock\""))
        assertTrue(script.contains("another package operation is still running"))
    }

    @Test
    fun `pkg heal quarantines only unreadable alternatives admin files`() {
        // On-device evidence 2026-08-25: an interrupted transaction left the
        // pager admin file truncated; dpkg refused every later postinst with
        // "corrupt: unexpected end of file". The healer must move only the
        // unreadable file aside so the group can re-register.
        val base = File(System.getProperty("java.io.tmpdir"), "codec-heal-${System.nanoTime()}")
        try {
            val prefix = File(base, "usr")
            val bin = File(prefix, "bin").apply { mkdirs() }
            listOf("apt-get", "dpkg", "gpgv").forEach { name ->
                File(bin, name).apply {
                    writeText("#!/bin/sh\nexit 0\n")
                    setExecutable(true)
                }
            }
            File(prefix, "etc/apt/keyrings").mkdirs()
            File(prefix, "etc/apt/keyrings/${ShellEnvironment.PACKAGE_REPOSITORY_KEYRING}")
                .writeText("public-test-key")
            // update-alternatives stub: parses every group except the
            // truncated "editor" record, mirroring dpkg's behaviour.
            File(bin, "update-alternatives").apply {
                writeText("#!/bin/sh\n[ \"\$2\" = editor ] && exit 2\nexit 0\n")
                setExecutable(true)
            }
            val altDir = File(prefix, "var/lib/dpkg/alternatives").apply { mkdirs() }
            File(altDir, "pager").writeText("auto\nwell-formed\n")
            File(altDir, "editor").writeText("auto\n/truncated")
            val pkg = File(bin, "pkg").apply {
                writeText(ShellEnvironment.pkgScript())
                setExecutable(true)
            }

            val process = ProcessBuilder("/bin/sh", pkg.absolutePath, "heal")
                .redirectErrorStream(true)
                .apply { environment()["PREFIX"] = prefix.absolutePath }
                .start()
            val completed = process.waitFor(10, TimeUnit.SECONDS)
            if (!completed) process.destroyForcibly()
            val output = process.inputStream.bufferedReader().readText()

            assertTrue("pkg heal timed out: $output", completed)
            assertEquals(output, 0, process.exitValue())
            assertTrue(output.contains("quarantined unreadable alternatives admin file: editor"))
            assertFalse(File(altDir, "editor").exists())
            assertTrue(altDir.listFiles()!!.any { it.name.startsWith("editor.corrupt-") })
            assertTrue("well-formed pager admin file must be untouched", File(altDir, "pager").exists())
        } finally {
            base.deleteRecursively()
        }
    }

    @Test
    fun `pkg spec checks do not require python3 on the device`() {
        // Part B (2026-08-23): the fresh-device Phase 3 closure ships no
        // python3, so the maintainer-script byte checks must be pure shell.
        val script = ShellEnvironment.pkgScript()
        assertFalse(script.contains("python3 -c"))
        assertTrue(script.contains("codec_spec_body"))
        // Exact-byte semantics preserved via a quoted case pattern.
        assertTrue(script.contains("*\"\$2\"*) return 0"))
        // curl stays the preferred HTTPS metadata fetcher.
        assertTrue(script.contains("\"\$PREFIX/bin/curl\""))
    }

    @Test
    fun `bash shim execs system sh`() {
        val shim = ShellEnvironment.bashShim()
        assertTrue(shim.contains("exec /system/bin/sh"))
    }

    @Test
    fun `profile exports PREFIX HOME and PATH`() {
        val prefix = File("/data/data/com.codeci.ide/files/usr")
        val home = File("/data/data/com.codeci.ide/files/home")
        val projects = File("/data/user/0/com.codeci.ide/files/CodeC/projects")
        val profile = ShellEnvironment.profileScript(prefix, home, projects)
        assertTrue(profile.contains("export PREFIX='${prefix.absolutePath}'"))
        assertTrue(profile.contains("export HOME='${home.absolutePath}'"))
        assertTrue(profile.contains("export CODEC_PROJECTS='${projects.absolutePath}'"))
        assertTrue(profile.contains("export PATH=\"\$PREFIX/bin:/system/bin:/system/xbin:\$PATH\""))
        assertTrue(profile.contains("cd \"\$CODEC_PROJECTS\""))
        assertTrue(profile.contains("/system/bin/ls"))
        assertFalse(profile.contains("ls -la"))
        assertFalse(profile.contains("codec:\\w"))
        assertTrue(profile.contains("export PS1='codec $ '"))
    }

    @Test
    fun `buildEnv wires TCC and compiler settings`() {
        val files = File("/data/data/com.codeci.ide/files")
        val native = File("/data/app/lib")
        val tcc = File("/data/app/lib/libtcc.so")
        val bundle = File(files, "CodeC/tcc")
        val env = ShellEnvironment.buildEnv(
            filesDir = files,
            nativeLibDir = native,
            tccBinary = tcc,
            tccBundle = bundle,
            standard = "C11",
            warnings = true,
            optimization = 2
        )
        assertEquals(File(files, "usr").absolutePath, env["PREFIX"])
        assertEquals(File(files, "home").absolutePath, env["HOME"])
        assertEquals("c11", env["CC_STD"])
        assertEquals("-Wall -Wextra", env["CC_WARN"])
        assertEquals("-O2", env["CC_OPT"])
        assertEquals(tcc.absolutePath, env["TCC_BIN"])
        assertEquals(bundle.absolutePath, env["TCC_BUNDLE"])
        assertTrue(env["PATH"]!!.startsWith(File(files, "usr/bin").absolutePath))
        assertTrue(env["PATH"]!!.contains(native.absolutePath))
        assertEquals("xterm-256color", env["TERM"])
        assertEquals("codec $ ", env["PS1"])
        assertTrue(env.containsKey("CODEC_PROJECTS"))
    }

    @Test
    fun `optimization and standard helpers clamp and normalize`() {
        assertEquals("c17", ShellEnvironment.normalizeStandard("C17"))
        assertEquals("-O3", ShellEnvironment.optimizationFlag(9))
        assertEquals("-O0", ShellEnvironment.optimizationFlag(-2))
        assertEquals("", ShellEnvironment.warningFlags(false))
    }

    @Test
    fun `envToArray is KEY equals VALUE and sorted`() {
        val arr = ShellEnvironment.envToArray(mapOf("B" to "2", "A" to "1"))
        assertEquals(listOf("A=1", "B=2"), arr.toList())
    }

    @Test
    fun `resolveShell prefers prefix bash then system sh`() {
        val prefix = File("/tmp/codec-no-such-prefix")
        assertEquals(File("/system/bin/sh"), ShellEnvironment.resolveShell(prefix))
    }

    @Test
    fun `isElf detects ELF magic and not scripts`() {
        val dir = File(System.getProperty("java.io.tmpdir"), "codec-elf-${System.nanoTime()}")
        dir.mkdirs()
        val prefix = File(dir, "usr")
        val bin = File(prefix, "bin")
        bin.mkdirs()
        val elf = File(bin, "bash")
        elf.writeBytes(byteArrayOf(0x7f, 'E'.code.toByte(), 'L'.code.toByte(), 'F'.code.toByte(), 1, 2, 3))
        val shim = File(bin, "shim")
        shim.writeText("#!/system/bin/sh\n")
        assertTrue(ShellEnvironment.isElf(elf))
        assertFalse(ShellEnvironment.isElf(shim))
        assertFalse(ShellEnvironment.hasRealUserland(prefix))
        dir.deleteRecursively()
    }

    @Test
    fun `tar extractor refuses path escape and strips usr prefix`() {
        val dest = File(System.getProperty("java.io.tmpdir"), "codec-tar-${System.nanoTime()}")
        dest.mkdirs()
        val ok = TarGzExtractor.safeFile(dest, "usr/bin/bash")
        assertTrue(ok.absolutePath.startsWith(dest.canonicalPath))
        assertTrue(ok.absolutePath.endsWith("bin/bash"))
        try {
            TarGzExtractor.safeFile(dest, "../etc/passwd")
            throw AssertionError("expected escape to fail")
        } catch (_: SecurityException) {
        }
        try {
            TarGzExtractor.safeFile(dest, "/etc/passwd")
            throw AssertionError("expected absolute path to fail")
        } catch (_: SecurityException) {
        }
        dest.deleteRecursively()
    }

    @Test
    fun `ctrl mapping covers letters and specials`() {
        assertEquals('\u0003', TerminalViewModel.ctrl('c'))
        assertEquals('\u0003', TerminalViewModel.ctrl('C'))
        assertEquals('\u001b', TerminalViewModel.ctrl('['))
        assertEquals('\u007f', TerminalViewModel.ctrl('?'))
    }

    @Test
    fun `manifest maps device ABIs to bootstrap architectures`() {
        val manifest = com.codeci.ide.ui.terminal.UserlandManifest
        assertEquals("aarch64", manifest.archNameForAbis(arrayOf("arm64-v8a", "armeabi-v7a")))
        assertEquals("x86_64", manifest.archNameForAbis(arrayOf("x86_64")))
        assertNull(manifest.archNameForAbis(arrayOf("armeabi-v7a")))
        // Phase 3 is selected first; userland-v1 stays the safe fallback.
        assertEquals("userland-v2-dev", manifest.ORDER.first().releaseTag)
        assertEquals("bootstrap-phase3", manifest.ORDER.first().assetPrefix)
        assertEquals("userland-v1", manifest.ORDER.last().releaseTag)
        assertEquals(
            "https://github.com/pabi277/CodeC/releases/download/userland-v2-dev/bootstrap-phase3-aarch64.tar.gz",
            manifest.PHASE3.tarballUrl("aarch64")
        )
        assertEquals(
            "https://github.com/pabi277/CodeC/releases/download/userland-v2-dev/bootstrap-phase3-aarch64.tar.gz.sha256",
            manifest.PHASE3.shaUrl("aarch64")
        )
    }

    @Test
    fun `missing library diagnostics parse dynamic loader output`() {
        assertEquals(
            "libandroid-support.so",
            ShellEnvironment.missingLibraryFromOutput(
                "error: library \"libandroid-support.so\" not found"
            )
        )
        assertEquals(
            "libfoo.so",
            ShellEnvironment.missingLibraryFromOutput("bash: library 'libfoo.so' not found")
        )
        assertNull(ShellEnvironment.missingLibraryFromOutput("exit 1"))
        assertNull(ShellEnvironment.missingLibraryFromOutput(""))
    }

    @Test
    fun `buildEnv sets LD_PRELOAD only when termux-exec is present`() {
        val base = File(System.getProperty("java.io.tmpdir"), "codec-preload-${System.nanoTime()}")
        try {
            val files = File(base, "files")
            val prefix = File(files, "usr")
            val lib = File(prefix, "lib")
            val native = File(base, "native")
            val bundle = File(files, "CodeC/tcc")
            native.mkdirs()
            bundle.mkdirs()

            val without = ShellEnvironment.buildEnv(
                filesDir = files, nativeLibDir = native, tccBinary = null, tccBundle = bundle,
                standard = "c11", warnings = false, optimization = 0
            )
            assertNull(without["LD_PRELOAD"])

            lib.mkdirs()
            val primary = File(lib, "libtermux-exec-ld-preload.so")
            primary.writeBytes(byteArrayOf(1))
            val with = ShellEnvironment.buildEnv(
                filesDir = files, nativeLibDir = native, tccBinary = null, tccBundle = bundle,
                standard = "c11", warnings = false, optimization = 0
            )
            assertEquals(primary.absolutePath, with["LD_PRELOAD"])

            primary.delete()
            val compat = File(lib, "libtermux-exec.so")
            compat.writeBytes(byteArrayOf(1))
            val compatEnv = ShellEnvironment.buildEnv(
                filesDir = files, nativeLibDir = native, tccBinary = null, tccBundle = bundle,
                standard = "c11", warnings = false, optimization = 0
            )
            assertEquals(compat.absolutePath, compatEnv["LD_PRELOAD"])
        } finally {
            base.deleteRecursively()
        }
    }

    @Test
    fun `termuxExecPreload prefers the primary variant`() {
        val base = File(System.getProperty("java.io.tmpdir"), "codec-tre-${System.nanoTime()}")
        try {
            val prefix = File(base, "usr")
            val lib = File(prefix, "lib")
            lib.mkdirs()
            assertNull(ShellEnvironment.termuxExecPreload(prefix))
            val compat = File(lib, "libtermux-exec.so")
            compat.writeBytes(byteArrayOf(1))
            assertEquals(compat, ShellEnvironment.termuxExecPreload(prefix))
            val primary = File(lib, "libtermux-exec-ld-preload.so")
            primary.writeBytes(byteArrayOf(1))
            assertEquals(primary, ShellEnvironment.termuxExecPreload(prefix))
        } finally {
            base.deleteRecursively()
        }
    }

    @Test
    fun `profile exports LD_PRELOAD only when the library exists`() {
        val prefix = File("/data/data/com.codeci.ide/files/usr")
        val home = File("/data/data/com.codeci.ide/files/home")
        val projects = File("/data/data/com.codeci.ide/files/CodeC/projects")
        val profile = ShellEnvironment.profileScript(prefix, home, projects)
        assertTrue(profile.contains("export LD_PRELOAD"))
        assertTrue(profile.contains("libtermux-exec-ld-preload.so"))
        assertTrue(profile.contains("if [ -f \"\$_codec_preload\" ]"))
        assertTrue(profile.contains("unset _codec_preload"))
    }

    @Test
    fun `pkg script exports termux-exec preload defensively`() {
        val script = ShellEnvironment.pkgScript()
        assertTrue(script.contains("libtermux-exec-ld-preload.so"))
        assertTrue(script.contains("export LD_PRELOAD"))
        // The repository must stay CodeC-only.
        assertFalse(script.contains("packages.termux.dev"))
        assertFalse(script.contains("packages-cf.termux.dev"))
    }

    @Test
    fun `setupStorageScript creates posix script with standard shared directories and permission request`() {
        val script = ShellEnvironment.setupStorageScript()
        assertTrue(script.startsWith("#!/system/bin/sh"))
        assertTrue(script.contains("STORAGE_DIR=\"\$HOME/storage\""))
        assertTrue(script.contains("setup_link \"\$SHARED_ROOT\" \"shared\""))
        assertTrue(script.contains("setup_link \"\$SHARED_ROOT/Download\" \"downloads\""))
        assertTrue(script.contains("setup_link \"\$SHARED_ROOT/Documents\" \"documents\""))
        assertTrue(script.contains("setup_link \"\$SHARED_ROOT/DCIM\" \"dcim\""))
        assertTrue(script.contains("setup_link \"\$SHARED_ROOT/Pictures\" \"pictures\""))
        assertTrue(script.contains("setup_link \"\$SHARED_ROOT/Music\" \"music\""))
        assertTrue(script.contains("setup_link \"\$SHARED_ROOT/Movies\" \"movies\""))
        assertTrue(script.contains("external-"))
    }

    @Test
    fun `clipboardScript is a posix sh CLI speaking the CodeCApi osc protocol`() {
        val script = ShellEnvironment.clipboardScript()
        assertTrue(script.startsWith("#!/system/bin/sh"))
        assertTrue(script.contains("codec-clipboard get"))
        assertTrue(script.contains("codec-clipboard set [TEXT]"))
        assertTrue(script.contains("codec-clipboard clear"))
        assertTrue(script.contains("codec-clipboard status"))
        assertTrue(script.contains("API_DIR=\"\$PREFIX/tmp/codec-api\""))
        assertTrue(script.contains("mktemp \"\$API_DIR/req.XXXXXX\""))
        // The response file derives from the request temp file so it is
        // unique AND not pre-created (the CLI polls for the app's rename).
        assertTrue(script.contains("res=\"\${req}.out\""))
        assertTrue(script.contains("wire_op=\"clipboard.\$op\""))
        assertTrue(script.contains("CodeCApi:%s:%s:%s"))
        assertTrue(script.contains("sleep 0.05"))
        assertTrue(script.contains("trap 'rm -f"))
        // The request must go to the controlling terminal when stdout is
        // piped/redirected, with a stdout fallback when there is no tty.
        // The tty path must be an *attempted write* (a `[ -w /dev/tty ]`
        // test is unreliable: access(2) succeeds even with no controlling
        // terminal, and the real open() then fails with ENXIO).
        assertTrue(script.contains("} 2>/dev/null; then"))
        assertTrue(script.contains(">/dev/tty"))
        // Both the /dev/tty branch and the stdout fallback emit the OSC.
        assertEquals(2, script.windowed("CodeCApi:%s:%s:%s".length)
            .count { it == "CodeCApi:%s:%s:%s" })
        // The OSC must be emitted as real ESC/BEL bytes: a single backslash
        // before 033/007, never a doubled backslash (which printf prints
        // literally and the emulator would render as text).
        assertTrue(script.contains("\\033]1337;CodeCApi:%s:%s:%s\\007"))
        assertFalse(script.contains("\\\\033]1337;CodeCApi"))
    }

    @Test
    fun `clipboardScript performs a full osc request and prints the app response`() {
        val base = File(System.getProperty("java.io.tmpdir"), "codec-clip-${System.nanoTime()}")
        try {
            val prefix = File(base, "usr")
            val bin = File(prefix, "bin").apply { mkdirs() }
            val script = File(bin, "codec-clipboard")
            script.writeText(ShellEnvironment.clipboardScript())
            script.setExecutable(true)

            val proc = ProcessBuilder("sh", script.absolutePath, "get")
                .redirectErrorStream(true)
                .start()

            // Fake the app: scan the script's stdout byte-by-byte for the
            // OSC request (a large blocking read would not see it until the
            // script exits), then create the named response file.
            val output = StringBuilder()
            val responded = java.util.concurrent.atomic.AtomicBoolean(false)
            val marker = Regex("\u001b\\]1337;CodeCApi:([^:]+):([^:]+):([^\u0007]*)\u0007")
            val responder = Thread {
                try {
                    val input = proc.inputStream
                    while (true) {
                        val b = input.read()
                        if (b < 0) break
                        output.append(b.toChar()) // byte-preserving (Latin-1)
                        val match = marker.find(output)
                        if (match != null && !responded.get()) {
                            responded.set(true)
                            val resPath = match.groupValues[3]
                            if (resPath.isNotEmpty()) {
                                File(resPath).writeText("hello from clipboard")
                            }
                        }
                    }
                } catch (_: Exception) {
                }
            }
            responder.start()
            val exitCode = proc.waitFor()
            responder.join(5000)

            assertEquals(0, exitCode)
            assertTrue(output.toString().contains("\u001b]1337;CodeCApi:clipboard.get:"))
            assertTrue(output.toString().contains("\u0007"))
            assertTrue(output.toString().contains("hello from clipboard"))
            assertTrue(responded.get())
        } finally {
            base.deleteRecursively()
        }
    }

    @Test
    fun `notifyScript is a posix sh CLI speaking the CodeCApi osc protocol`() {
        val script = ShellEnvironment.notifyScript()
        assertTrue(script.startsWith("#!/system/bin/sh"))
        assertTrue(script.contains("codec-notify send TITLE [BODY]"))
        assertTrue(script.contains("codec-notify clear"))
        assertTrue(script.contains("codec-notify status"))
        assertTrue(script.contains("API_DIR=\"\$PREFIX/tmp/codec-api\""))
        assertTrue(script.contains("mktemp \"\$API_DIR/req.XXXXXX\""))
        assertTrue(script.contains("res=\"\${req}.out\""))
        assertTrue(script.contains("wire_op=\"notify.\$op\""))
        assertTrue(script.contains("CodeCApi:%s:%s:%s"))
        assertTrue(script.contains("sleep 0.05"))
        assertTrue(script.contains("trap 'rm -f"))
        // Permission flow: NEED_PERMISSION is not a final outcome — the CLI
        // prints a hint and keeps polling until the app writes OK/ERR.
        assertTrue(script.contains("NEED_PERMISSION:*"))
        // The hint must be printed ONCE (early feedback: 25+ per-poll
        // repeats flooded the terminal) and the permission wait must be
        // long enough for the user to answer the system dialog.
        assertTrue(script.contains("hint_shown=0"))
        assertTrue(script.contains("hint_shown=1"))
        assertTrue(script.contains("\$i\" -ge 600"))
        // Same /dev/tty-first, attempted-write discipline as clipboard.
        assertEquals(2, script.windowed("CodeCApi:%s:%s:%s".length)
            .count { it == "CodeCApi:%s:%s:%s" })
        assertTrue(script.contains("\\033]1337;CodeCApi:%s:%s:%s\\007"))
        assertFalse(script.contains("\\\\033]1337;CodeCApi"))
    }

    @Test
    fun `notifyScript waits through the permission dance and prints the final outcome`() {
        val base = File(System.getProperty("java.io.tmpdir"), "codec-notify-${System.nanoTime()}")
        try {
            val prefix = File(base, "usr")
            val bin = File(prefix, "bin").apply { mkdirs() }
            val script = File(bin, "codec-notify")
            script.writeText(ShellEnvironment.notifyScript())
            script.setExecutable(true)

            val proc = ProcessBuilder("sh", script.absolutePath, "send", "Build done", "3 files")
                .redirectErrorStream(true)
                .start()

            val output = StringBuilder()
            val responded = java.util.concurrent.atomic.AtomicBoolean(false)
            val marker = Regex("\\u001b\\]1337;CodeCApi:([^:]+):([^:]+):([^\\u0007]*)\\u0007")
            val responder = Thread {
                try {
                    val input = proc.inputStream
                    while (true) {
                        val b = input.read()
                        if (b < 0) break
                        output.append(b.toChar())
                        val match = marker.find(output)
                        if (match != null && !responded.get()) {
                            responded.set(true)
                            val resPath = match.groupValues[3]
                            if (resPath.isNotEmpty()) {
                                // 1) Permission dialog pending → marker; 2) user
                                // allows → the app atomically rewrites OK.
                                File(resPath).writeText(
                                    "NEED_PERMISSION:android.permission.POST_NOTIFICATIONS"
                                )
                                Thread.sleep(300)
                                File(resPath).writeText("OK")
                            }
                        }
                    }
                } catch (_: Exception) {
                }
            }
            responder.start()
            val exitCode = proc.waitFor()
            responder.join(5000)

            assertEquals(0, exitCode)
            assertTrue(output.toString().contains("\u001b]1337;CodeCApi:notify.send:"))
            val hint = "Android notification permission: allow it"
            assertEquals(1, Regex(Regex.escape(hint)).findAll(output.toString()).count())
            assertTrue(output.toString().trim().endsWith("OK"))
            assertTrue(responded.get())
        } finally {
            base.deleteRecursively()
        }
    }

    @Test
    fun `notifyScript exits 1 when permission is denied`() {
        val base = File(System.getProperty("java.io.tmpdir"), "codec-notify-denied-${System.nanoTime()}")
        try {
            val prefix = File(base, "usr")
            val bin = File(prefix, "bin").apply { mkdirs() }
            val script = File(bin, "codec-notify")
            script.writeText(ShellEnvironment.notifyScript())
            script.setExecutable(true)

            val proc = ProcessBuilder("sh", script.absolutePath, "send", "Hi")
                .redirectErrorStream(true)
                .start()

            val output = StringBuilder()
            val responded = java.util.concurrent.atomic.AtomicBoolean(false)
            val marker = Regex("\\u001b\\]1337;CodeCApi:([^:]+):([^:]+):([^\\u0007]*)\\u0007")
            val responder = Thread {
                try {
                    val input = proc.inputStream
                    while (true) {
                        val b = input.read()
                        if (b < 0) break
                        output.append(b.toChar())
                        val match = marker.find(output)
                        if (match != null && !responded.get()) {
                            responded.set(true)
                            val resPath = match.groupValues[3]
                            if (resPath.isNotEmpty()) {
                                Thread.sleep(100)
                                File(resPath).writeText(
                                    "ERR:notification permission denied"
                                )
                            }
                        }
                    }
                } catch (_: Exception) {
                }
            }
            responder.start()
            val exitCode = proc.waitFor()
            responder.join(5000)

            assertEquals(1, exitCode)
            assertTrue(output.toString().contains("notification permission denied"))
            assertTrue(output.toString().contains("ERR:") == false)
            assertTrue(responded.get())
        } finally {
            base.deleteRecursively()
        }
    }

    @Test
    fun `toast share openUrl and vibrate scripts are posix sh CLIs over the bridge`() {
        val toast = ShellEnvironment.toastScript()
        assertTrue(toast.startsWith("#!/system/bin/sh"))
        assertTrue(toast.contains("usage: codec-toast MESSAGE"))
        assertTrue(toast.contains("toast.show"))

        val share = ShellEnvironment.shareScript()
        assertTrue(share.startsWith("#!/system/bin/sh"))
        assertTrue(share.contains("usage: codec-share TEXT"))
        assertTrue(share.contains("share.text"))

        val openUrl = ShellEnvironment.openUrlScript()
        assertTrue(openUrl.startsWith("#!/system/bin/sh"))
        assertTrue(openUrl.contains("usage: codec-open-url URL"))
        assertTrue(openUrl.contains("url.open"))

        val vibrate = ShellEnvironment.vibrateScript()
        assertTrue(vibrate.startsWith("#!/system/bin/sh"))
        assertTrue(vibrate.contains("usage: codec-vibrate [MILLISECONDS]"))
        assertTrue(vibrate.contains("vibrate"))
        assertTrue(vibrate.contains("ms=\"\${1:-500}\""))
    }

    @Test
    fun `toastScript performs a full osc request and prints OK`() {
        val base = File(System.getProperty("java.io.tmpdir"), "codec-toast-${System.nanoTime()}")
        try {
            val prefix = File(base, "usr")
            val bin = File(prefix, "bin").apply { mkdirs() }
            val script = File(bin, "codec-toast")
            script.writeText(ShellEnvironment.toastScript())
            script.setExecutable(true)

            val proc = ProcessBuilder("sh", script.absolutePath, "hello", "from", "CodeC")
                .redirectErrorStream(true)
                .start()

            val output = StringBuilder()
            val responded = java.util.concurrent.atomic.AtomicBoolean(false)
            val marker = Regex("\u001b\\]1337;CodeCApi:([^:]+):([^:]+):([^\u0007]*)\u0007")
            val responder = Thread {
                try {
                    val input = proc.inputStream
                    while (true) {
                        val b = input.read()
                        if (b < 0) break
                        output.append(b.toChar())
                        val match = marker.find(output)
                        if (match != null && !responded.get()) {
                            responded.set(true)
                            val resPath = match.groupValues[3]
                            if (resPath.isNotEmpty()) {
                                File(resPath).writeText("OK")
                            }
                        }
                    }
                } catch (_: Exception) {
                }
            }
            responder.start()
            val exitCode = proc.waitFor()
            responder.join(5000)

            assertEquals(0, exitCode)
            assertTrue(output.toString().contains("\u001b]1337;CodeCApi:toast.show:"))
            assertTrue(output.toString().trim().endsWith("OK"))
            assertTrue(responded.get())
        } finally {
            base.deleteRecursively()
        }
    }

    @Test
    fun `setupStorageDirectory creates storage directory and symlinks to target folders`() {
        val base = File(System.getProperty("java.io.tmpdir"), "codec-storage-test-${System.nanoTime()}")
        try {
            val home = File(base, "home").apply { mkdirs() }
            val fakeShared = File(base, "emulated_0").apply { mkdirs() }
            val subDirs = listOf("Download", "Documents", "DCIM", "Pictures", "Music", "Movies")
            subDirs.forEach { File(fakeShared, it).mkdirs() }

            val fakeStorageRoot = File(base, "storage_root").apply { mkdirs() }
            val fakeSdCard = File(fakeStorageRoot, "1234-5678").apply { mkdirs() }

            val result = ShellEnvironment.setupStorageDirectory(
                homeDir = home,
                externalStorageDir = fakeShared,
                storageRoot = fakeStorageRoot
            )

            assertTrue(result.success)
            val storageDir = File(home, "storage")
            assertTrue(storageDir.isDirectory)

            val sharedLink = File(storageDir, "shared")
            assertTrue("shared link should exist", sharedLink.exists())

            val downloadsLink = File(storageDir, "downloads")
            assertTrue("downloads link should exist", downloadsLink.exists())

            val documentsLink = File(storageDir, "documents")
            assertTrue("documents link should exist", documentsLink.exists())

            val dcimLink = File(storageDir, "dcim")
            assertTrue("dcim link should exist", dcimLink.exists())

            val ext1Link = File(storageDir, "external-1")
            assertTrue("external-1 link should exist", ext1Link.exists())

            // Re-run setup to verify idempotency
            val rerunResult = ShellEnvironment.setupStorageDirectory(
                homeDir = home,
                externalStorageDir = fakeShared,
                storageRoot = fakeStorageRoot
            )
            assertTrue(rerunResult.success)
            assertTrue(sharedLink.exists())
            assertTrue(downloadsLink.exists())
        } finally {
            base.deleteRecursively()
        }
    }

    @Test
    fun `pkg script includes transaction confirmation and preflight summary`() {
        val script = ShellEnvironment.pkgScript()
        assertTrue(script.contains("display_cache_summary"))
        assertTrue(script.contains("confirm_transaction"))
        assertTrue(script.contains("format_kb"))
        assertTrue(script.contains("Do you want to continue? [Y/n]"))
        assertTrue(script.contains("Transaction Summary"))
        assertTrue(script.contains("Preflight:        PASSED"))
        assertTrue(script.contains("-y|--yes|--assume-yes"))
        assertTrue(script.contains("pkg install [-y]"))
        assertTrue(script.contains("pkg upgrade [-y]"))
        assertTrue(script.contains("pkg uninstall [-y]"))
    }

    @Test
    fun `pkg script executes transaction confirmation and honors yes flag and user abort`() {
        val base = File(System.getProperty("java.io.tmpdir"), "codec-pkg-confirm-${System.nanoTime()}")
        try {
            val prefix = File(base, "usr")
            val bin = File(prefix, "bin").apply { mkdirs() }
            val cache = File(prefix, "var/cache/apt/archives").apply { mkdirs() }
            val keyrings = File(prefix, "etc/apt/keyrings").apply { mkdirs() }
            File(keyrings, ShellEnvironment.PACKAGE_REPOSITORY_KEYRING).writeText("public-test-key")

            // Mock apt-get
            File(bin, "apt-get").apply {
                writeText("""
                    #!/bin/sh
                    case "${'$'}*" in
                      *--download-only*)
                        deb="${cache.absolutePath}/nano_9.2_aarch64.deb"
                        printf "fakedeb" > "${'$'}deb"
                        exit 0
                        ;;
                      *)
                        exit 0
                        ;;
                    esac
                """.trimIndent() + "\n")
                setExecutable(true)
            }

            // Mock dpkg-deb
            File(bin, "dpkg-deb").apply {
                writeText("""
                    #!/bin/sh
                    case "${'$'}*" in
                      *"-f "*Package*) echo "nano"; exit 0 ;;
                      *"-f "*Version*) echo "9.2"; exit 0 ;;
                      *"-f "*Architecture*) echo "aarch64"; exit 0 ;;
                      *"-f "*Installed-Size*) echo "840"; exit 0 ;;
                      *"--control "*) exit 0 ;;
                      *"--contents "*) echo "data/data/com.codeci.ide/files/usr/bin/nano"; exit 0 ;;
                      *) exit 0 ;;
                    esac
                """.trimIndent() + "\n")
                setExecutable(true)
            }

            // Mock dpkg
            File(bin, "dpkg").apply {
                writeText("""
                    #!/bin/sh
                    if [ "${'$'}1" = "--print-architecture" ]; then echo "aarch64"; exit 0; fi
                    exit 0
                """.trimIndent() + "\n")
                setExecutable(true)
            }

            // Mock gpgv
            File(bin, "gpgv").apply {
                writeText("""
                    #!/bin/sh
                    out=""
                    while [ "${'$'}#" -gt 0 ]; do
                      if [ "${'$'}1" = "--output" ]; then out="${'$'}2"; shift 2; else shift; fi
                    done
                    if [ -n "${'$'}out" ]; then
                      printf "Origin: CodeC\nSuite: stable\n" > "${'$'}out"
                    fi
                    exit 0
                """.trimIndent() + "\n")
                setExecutable(true)
            }

            // Mock curl
            File(bin, "curl").apply {
                writeText("""
                    #!/bin/sh
                    dest=""
                    while [ "${'$'}#" -gt 0 ]; do
                      if [ "${'$'}1" = "-o" ]; then dest="${'$'}2"; shift 2; else shift; fi
                    done
                    if [ -n "${'$'}dest" ]; then
                      printf "Origin: CodeC\nSuite: stable\n" > "${'$'}dest"
                    fi
                    exit 0
                """.trimIndent() + "\n")
                setExecutable(true)
            }

            val pkg = File(bin, "pkg").apply {
                writeText(ShellEnvironment.pkgScript())
                setExecutable(true)
            }

            // 1) Test with -y flag -> automatic acceptance
            val procYes = ProcessBuilder("/bin/sh", pkg.absolutePath, "install", "-y", "nano")
                .redirectErrorStream(true)
                .apply {
                    environment()["PREFIX"] = prefix.absolutePath
                    environment()["PATH"] = "${bin.absolutePath}:/bin:/usr/bin"
                }
                .start()
            val completedYes = procYes.waitFor(10, TimeUnit.SECONDS)
            if (!completedYes) procYes.destroyForcibly()
            val outYes = procYes.inputStream.bufferedReader().readText()
            assertTrue("pkg install -y timed out: $outYes", completedYes)
            assertEquals(outYes, 0, procYes.exitValue())
            assertTrue(outYes.contains("Transaction Summary"))
            assertTrue(outYes.contains("nano 9.2"))
            assertTrue(outYes.contains("pkg: installed nano"))

            // 2) Test with interactive 'n' abort
            val procAbort = ProcessBuilder("/bin/sh", pkg.absolutePath, "install", "nano")
                .redirectErrorStream(true)
                .apply {
                    environment()["PREFIX"] = prefix.absolutePath
                    environment()["PATH"] = "${bin.absolutePath}:/bin:/usr/bin"
                }
                .start()
            procAbort.outputStream.bufferedWriter().use {
                it.write("n\n")
                it.flush()
            }
            val completedAbort = procAbort.waitFor(10, TimeUnit.SECONDS)
            if (!completedAbort) procAbort.destroyForcibly()
            val outAbort = procAbort.inputStream.bufferedReader().readText()
            assertTrue("pkg install abort timed out: $outAbort", completedAbort)
            assertEquals(outAbort, 0, procAbort.exitValue())
            assertTrue(outAbort.contains("pkg: installation aborted by user."))
            assertFalse(outAbort.contains("pkg: installed nano"))
            assertFalse(File(prefix, "var/lib/codec-pkg/transaction.pending").exists())

            // 3) Test pkg status command output
            val procStatus = ProcessBuilder("/bin/sh", pkg.absolutePath, "status")
                .redirectErrorStream(true)
                .apply {
                    environment()["PREFIX"] = prefix.absolutePath
                    environment()["PATH"] = "${bin.absolutePath}:/bin:/usr/bin"
                }
                .start()
            val completedStatus = procStatus.waitFor(10, TimeUnit.SECONDS)
            if (!completedStatus) procStatus.destroyForcibly()
            val outStatus = procStatus.inputStream.bufferedReader().readText()
            assertTrue("pkg status timed out: $outStatus", completedStatus)
            assertEquals(outStatus, 0, procStatus.exitValue())
            assertTrue(outStatus.contains("CodeC Package Repository & Trust Status:"))
            assertTrue(outStatus.contains("328500868CE9B0F74B62CEFC1D7D52F6F8135015"))
            assertTrue(outStatus.contains("https://pabi277.github.io/CodeC/dev"))
            assertTrue(outStatus.contains("Installed & Active"))
        } finally {
            base.deleteRecursively()
        }
    }

    @Test
    fun `pkg install of an already-newest package succeeds (KI-1)`() {
        // KI-1 (2026-08-26): apt exits 0 with "already the newest version" and
        // downloads nothing, but the wrapper treated "0 packages downloaded"
        // as a fetch failure. It must now report success and exit 0.
        val base = File(System.getProperty("java.io.tmpdir"), "codec-ki1-${System.nanoTime()}")
        try {
            val prefix = File(base, "usr")
            val bin = File(prefix, "bin").apply { mkdirs() }
            val cache = File(prefix, "var/cache/apt/archives").apply { mkdirs() }
            val keyrings = File(prefix, "etc/apt/keyrings").apply { mkdirs() }
            File(keyrings, ShellEnvironment.PACKAGE_REPOSITORY_KEYRING).writeText("public-test-key")

            // Mock apt-get: --download-only install of an already-newest
            // package succeeds but writes no .deb (the KI-1 trigger).
            File(bin, "apt-get").apply {
                writeText("#!/bin/sh\nexit 0\n")
                setExecutable(true)
            }

            // Mock dpkg (existence is checked by require_backend).
            File(bin, "dpkg").apply {
                writeText("#!/bin/sh\nif [ \"\$1\" = \"--print-architecture\" ]; then echo \"aarch64\"; exit 0; fi\nexit 0\n")
                setExecutable(true)
            }

            // Mock gpgv: emit signed release metadata into the --output file.
            File(bin, "gpgv").apply {
                writeText("""
                    #!/bin/sh
                    out=""
                    while [ "${'$'}#" -gt 0 ]; do
                      if [ "${'$'}1" = "--output" ]; then out="${'$'}2"; shift 2; else shift; fi
                    done
                    if [ -n "${'$'}out" ]; then
                      printf "Origin: CodeC\nSuite: stable\n" > "${'$'}out"
                    fi
                    exit 0
                """.trimIndent() + "\n")
                setExecutable(true)
            }

            // Mock curl: fetch the signed InRelease into the -o destination.
            File(bin, "curl").apply {
                writeText("""
                    #!/bin/sh
                    dest=""
                    while [ "${'$'}#" -gt 0 ]; do
                      if [ "${'$'}1" = "-o" ]; then dest="${'$'}2"; shift 2; else shift; fi
                    done
                    if [ -n "${'$'}dest" ]; then
                      printf "Origin: CodeC\nSuite: stable\n" > "${'$'}dest"
                    fi
                    exit 0
                """.trimIndent() + "\n")
                setExecutable(true)
            }

            val pkg = File(bin, "pkg").apply {
                writeText(ShellEnvironment.pkgScript())
                setExecutable(true)
            }

            val proc = ProcessBuilder("/bin/sh", pkg.absolutePath, "install", "-y", "make")
                .redirectErrorStream(true)
                .apply {
                    environment()["PREFIX"] = prefix.absolutePath
                    environment()["PATH"] = "${bin.absolutePath}:/bin:/usr/bin"
                }
                .start()
            val completed = proc.waitFor(10, TimeUnit.SECONDS)
            if (!completed) proc.destroyForcibly()
            val output = proc.inputStream.bufferedReader().readText()

            assertTrue("pkg install timed out: $output", completed)
            assertEquals(output, 0, proc.exitValue())
            assertTrue(output.contains("already installed (already the newest version)"))
            assertFalse(output.contains("apt downloaded no packages"))
            assertFalse(File(prefix, "var/lib/codec-pkg/transaction.pending").exists())
        } finally {
            base.deleteRecursively()
        }
    }

    @Test
    fun `canonicalPrefix only rewrites the user 0 emulation alias`() {
        assertEquals(
            "/data/data/com.codeci.ide/files/usr",
            ShellEnvironment.canonicalPrefix("/data/user/0/com.codeci.ide/files/usr")
        )
        // Already-canonical, host-test temp dirs, and secondary users are
        // untouched (rewriting /data/user/10 to /data/data would be wrong).
        assertEquals(
            "/data/data/com.codeci.ide/files/usr",
            ShellEnvironment.canonicalPrefix("/data/data/com.codeci.ide/files/usr")
        )
        assertEquals("/tmp/codec/usr", ShellEnvironment.canonicalPrefix("/tmp/codec/usr"))
        assertEquals(
            "/data/user/10/com.codeci.ide/files/usr",
            ShellEnvironment.canonicalPrefix("/data/user/10/com.codeci.ide/files/usr")
        )
    }

    @Test
    fun `KI-2 exports the canonical prefix without changing the installer path`() {
        val files = File("/data/user/0/com.codeci.ide/files")

        // The installer's filesystem path must stay the raw Context.getFilesDir()
        // spelling: the bootstrap download/extract/swap logic operates on this
        // path, and rewriting it to /data/data/… was observed to make the
        // installer re-download the bootstrap. KI-2 is an *environment* fix.
        assertEquals("/data/user/0/com.codeci.ide/files/usr", ShellEnvironment.prefixDir(files).path)

        // The CodeCApi bridge confines against the raw prefix; the CLI scripts
        // emit paths derived from the exported $PREFIX. Both resolve to the
        // same inodes (canonicalFile collapses /data/data → /data/user/0), so
        // the confinement check still matches.
        val apiDir = ShellEnvironment.codecApiDir(ShellEnvironment.prefixDir(files))
        assertEquals("/data/user/0/com.codeci.ide/files/usr/tmp/codec-api", apiDir.path)

        // The exported process env must use the dpkg-recorded /data/data/…
        // spelling (the actual KI-2 fix).
        val env = ShellEnvironment.buildEnv(
            filesDir = files,
            nativeLibDir = File("/data/app/lib"),
            tccBinary = null,
            tccBundle = File(files, "CodeC/tcc"),
            standard = "c11",
            warnings = false,
            optimization = 0
        )
        assertEquals("/data/data/com.codeci.ide/files/usr", env["PREFIX"])
        assertTrue(env["PATH"]!!.startsWith("/data/data/com.codeci.ide/files/usr/bin"))
        assertEquals("/data/data/com.codeci.ide/files/usr/tmp", env["TMPDIR"])

        // The sourced profile must export the same canonical spelling.
        val profile = ShellEnvironment.profileScript(
            ShellEnvironment.prefixDir(files),
            File("/data/user/0/com.codeci.ide/files/home"),
            File("/data/user/0/com.codeci.ide/files/CodeC/projects")
        )
        assertTrue(profile.contains("export PREFIX='/data/data/com.codeci.ide/files/usr'"))
    }

    @Test
    fun `phase 18 codec scripts use the in-band CodeCApi bridge and no termux identity`() {
        val scripts = mapOf(
            ShellEnvironment.batteryScript() to "battery.status",
            ShellEnvironment.sensorScript() to "sensor.read",
            ShellEnvironment.ttsScript() to "tts.speak",
            ShellEnvironment.cameraScript() to "camera.capture",
            ShellEnvironment.intentScript() to "intent.send"
        )
        for ((script, wire) in scripts) {
            assertTrue(script.startsWith("#!/system/bin/sh"))
            assertTrue("script must emit $wire", script.contains("CodeCApi:$wire"))
            assertTrue(script.contains("tmp/codec-api"))
            assertTrue(script.contains("mktemp"))
            assertTrue(script.contains("/dev/tty"))
            assertFalse("no official Termux identity", script.contains("com.termux"))
        }
        // The camera CLI must understand both interim markers of the
        // permission/capture flow and poll longer than the simple commands.
        val camera = ShellEnvironment.cameraScript()
        assertTrue(camera.contains("NEED_PERMISSION:*"))
        assertTrue(camera.contains("CAPTURING:*"))
        assertTrue(camera.contains("1200"))
        // The intent CLI maps action + data into the two-line payload format.
        assertTrue(ShellEnvironment.intentScript().contains("printf '%s\\n%s'"))
    }

    @Test
    fun `getRepositoryTrustInfo inspects active keyring and signing metadata`() {
        val base = File(System.getProperty("java.io.tmpdir"), "codec-trust-${System.nanoTime()}")
        try {
            val prefix = File(base, "usr")
            val keyrings = File(prefix, "etc/apt/keyrings").apply { mkdirs() }
            val keyFile = File(keyrings, ShellEnvironment.PACKAGE_REPOSITORY_KEYRING)
            keyFile.writeText("sample-keyring-data")

            val info = ShellEnvironment.getRepositoryTrustInfo(base)
            assertEquals("https://pabi277.github.io/CodeC/dev", info.repositoryUrl)
            assertEquals("stable", info.suite)
            assertEquals("main", info.component)
            assertEquals("codec-archive-keyring-v1.gpg", info.keyringName)
            assertEquals("328500868CE9B0F74B62CEFC1D7D52F6F8135015", info.signingFingerprint)
            assertTrue(info.keyringInstalled)
            assertTrue(info.keyringSize > 0)
        } finally {
            base.deleteRecursively()
        }
    }
}
