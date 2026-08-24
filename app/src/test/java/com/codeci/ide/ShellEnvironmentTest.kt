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
        assertTrue(script.contains("com.codeci.ide.action.REQUEST_STORAGE_PERMISSION"))
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
}
