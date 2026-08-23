package com.codeci.ide

import com.codeci.ide.ui.terminal.ShellEnvironment
import com.codeci.ide.ui.terminal.TarGzExtractor
import com.codeci.ide.ui.viewmodels.TerminalViewModel
import java.io.File
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
        assertFalse(script.contains("com.termux/files/usr"))
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
}
