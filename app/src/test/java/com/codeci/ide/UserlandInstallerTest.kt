package com.codeci.ide

import com.codeci.ide.ui.terminal.ShellEnvironment
import com.codeci.ide.ui.terminal.UserlandInstaller
import com.codeci.ide.ui.terminal.UserlandManifest
import com.codeci.ide.ui.terminal.UserlandStatus
import com.sun.net.httpserver.HttpServer
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.InetSocketAddress
import java.security.MessageDigest
import java.util.concurrent.CopyOnWriteArrayList
import java.util.zip.GZIPOutputStream
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * End-to-end installer tests against a local HTTP server: release selection,
 * Phase 2 fallback, interrupted-download recovery, checksum failure, wrong
 * architecture, path traversal, disk-space failure, offline startup, and
 * missing-shared-library diagnostics.
 */
class UserlandInstallerTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private lateinit var server: HttpServer
    private val requested = CopyOnWriteArrayList<String>()
    private val routes = java.util.concurrent.ConcurrentHashMap<String, Route>()
    private val specialHandlers =
        java.util.concurrent.ConcurrentHashMap<String, (com.sun.net.httpserver.HttpExchange) -> Unit>()

    // POSIX modes (Kotlin has no octal literals).
    private val MODE_EXEC = 493 // 0755 (octal)
    private val MODE_DATA = 420 // 0644 (octal)

    private data class Route(val code: Int, val body: ByteArray)

    @Before
    fun startServer() {
        server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/") { exchange ->
            val path = exchange.requestURI.path
            requested.add(path)
            specialHandlers[path]?.let { handler ->
                handler(exchange)
                return@createContext
            }
            val route = routes[path] ?: Route(404, ByteArray(0))
            if (route.code in 200..299 && route.body.isNotEmpty()) {
                exchange.sendResponseHeaders(route.code, route.body.size.toLong())
                exchange.responseBody.use { it.write(route.body) }
            } else {
                exchange.sendResponseHeaders(route.code, -1)
                exchange.close()
            }
        }
        server.start()
    }

    @After
    fun stopServer() {
        server.stop(0)
    }

    private val baseUrl: String
        get() = "http://127.0.0.1:${server.address.port}"

    private fun route(path: String, body: ByteArray, code: Int = 200) {
        routes[path] = Route(code, body)
    }

    private fun serveRelease(tag: String, assetPrefix: String, arch: String, tar: ByteArray, sha: String) {
        route("/$tag/$assetPrefix-$arch.tar.gz", tar)
        route("/$tag/$assetPrefix-$arch.tar.gz.sha256", "$sha  $assetPrefix-$arch.tar.gz\n".encodeToByteArray())
    }

    private fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    // --- Minimal ustar tar.gz writer (the extractor only needs headers). ---

    private fun tarEntry(name: String, data: ByteArray, mode: Int = MODE_EXEC, type: Char = '0'): ByteArray {
        val header = ByteArray(512)
        val nameBytes = name.toByteArray(Charsets.UTF_8)
        nameBytes.copyOf(minOf(nameBytes.size, 100)).copyInto(header, 0)

        fun octal(off: Int, len: Int, value: Long) {
            val text = value.toString(8).padStart(len - 1, '0')
            text.toByteArray().copyInto(header, off)
            header[off + len - 1] = 0
        }
        octal(100, 8, mode.toLong())
        octal(124, 12, data.size.toLong())
        octal(136, 12, 0L)
        for (i in 148..155) header[i] = ' '.code.toByte()
        header[156] = type.code.toByte()
        "ustar".toByteArray().copyInto(header, 257)
        header[262] = 0
        header[263] = '0'.code.toByte()
        header[264] = '0'.code.toByte()
        val sum = header.sumOf { it.toInt() and 0xff }
        String.format("%06o", sum).toByteArray().copyInto(header, 148)
        header[154] = 0
        header[155] = ' '.code.toByte()

        val out = ByteArrayOutputStream()
        out.write(header)
        out.write(data)
        val pad = (512 - data.size % 512) % 512
        if (pad > 0) out.write(ByteArray(pad))
        return out.toByteArray()
    }

    private fun tarGz(vararg entries: Pair<String, Pair<ByteArray, Int>>): ByteArray {
        val plain = ByteArrayOutputStream()
        for ((name, pair) in entries) {
            plain.write(tarEntry(name, pair.first, pair.second))
        }
        plain.write(ByteArray(1024)) // two zero blocks
        val gz = ByteArrayOutputStream()
        GZIPOutputStream(gz).use { it.write(plain.toByteArray()) }
        return gz.toByteArray()
    }

    private val elfBash = byteArrayOf(0x7f, 'E'.code.toByte(), 'L'.code.toByte(), 'F'.code.toByte()) + "codec-bash".encodeToByteArray()
    private val elfBusybox = byteArrayOf(0x7f, 'E'.code.toByte(), 'L'.code.toByte(), 'F'.code.toByte()) + "codec-busybox".encodeToByteArray()

    private fun userlandTar(extra: List<Pair<String, ByteArray>> = emptyList()): ByteArray {
        val entries = mutableListOf(
            "bin/bash" to Pair(elfBash, MODE_EXEC),
            "bin/busybox" to Pair(elfBusybox, MODE_EXEC),
            "lib/libandroid-support.so" to Pair("codec-lib".encodeToByteArray(), MODE_DATA),
            "var/lib/dpkg/status" to Pair("Package: apt\nStatus: install ok installed\n".encodeToByteArray(), MODE_DATA)
        )
        entries.addAll(extra.map { it.first to Pair(it.second, MODE_DATA) })
        return tarGz(*entries.toTypedArray())
    }

    private fun makeInstaller(
        files: File,
        launch: (File, File, Boolean) -> ShellEnvironment.LaunchDiagnostic =
            { file, _, _ -> ShellEnvironment.LaunchDiagnostic(file.isFile) },
        free: () -> Long = { 1L shl 30 },
        online: Boolean = true,
        arch: String? = "aarch64"
    ): UserlandInstaller = UserlandInstaller(
        filesDir = files,
        cacheDir = File(files, "cache"),
        onlineProvider = { online },
        archProvider = { arch },
        freeSpaceProvider = { free() },
        launchChecker = launch,
        candidates = listOf(
            UserlandManifest("userland-v2-dev", "bootstrap-phase3", baseUrl),
            UserlandManifest("userland-v1", "bootstrap", baseUrl)
        )
    )

    // --- Tests -------------------------------------------------------------

    @Test
    fun `archNameForAbis maps arm64 and x86_64 and rejects others`() {
        assertEquals("aarch64", UserlandManifest.archNameForAbis(arrayOf("arm64-v8a", "armeabi-v7a")))
        assertEquals("x86_64", UserlandManifest.archNameForAbis(arrayOf("x86_64", "x86")))
        assertEquals("aarch64", UserlandManifest.archNameForAbis(arrayOf("x86_64", "arm64-v8a")))
        assertNull(UserlandManifest.archNameForAbis(arrayOf("armeabi-v7a")))
        assertNull(UserlandManifest.archNameForAbis(emptyArray()))
    }

    @Test
    fun `selects phase 3 release when published`() {
        val files = tmp.newFolder("files")
        val tar = userlandTar()
        val p3 = UserlandManifest("userland-v2-dev", "bootstrap-phase3", baseUrl)
        serveRelease(p3.releaseTag, p3.assetPrefix, "aarch64", tar, sha256(tar))
        val installer = makeInstaller(files)

        val status = installer.installIfNeeded()

        assertTrue("expected Installed, got $status", status is UserlandStatus.Installed)
        assertEquals("userland-v2-dev", (status as UserlandStatus.Installed).releaseTag)
        assertTrue(requested.any { it == "/userland-v2-dev/bootstrap-phase3-aarch64.tar.gz" })
        assertTrue(File(files, "usr/bin/bash").exists())
        assertEquals("userland-v2-dev", File(files, "usr/.userland-release").readText())
        assertEquals("aarch64", File(files, "usr/.userland-arch").readText())
        val cache = File(files, "cache")
        assertTrue(cache.listFiles()?.any { it.name.endsWith(".partial") } == false)
        assertTrue(File(cache, "bootstrap-phase3-aarch64.tar.gz").exists())
    }

    @Test
    fun `falls back to phase 2 when phase 3 is missing`() {
        val files = tmp.newFolder("files")
        val tar = userlandTar()
        serveRelease("userland-v1", "bootstrap", "aarch64", tar, sha256(tar))
        val installer = makeInstaller(files)

        val status = installer.installIfNeeded()

        assertTrue("expected Installed, got $status", status is UserlandStatus.Installed)
        assertEquals("userland-v1", (status as UserlandStatus.Installed).releaseTag)
        assertTrue(requested.any { it == "/userland-v1/bootstrap-aarch64.tar.gz" })
        assertTrue(requested.none { it.endsWith("/bootstrap-phase3-aarch64.tar.gz") })
    }

    @Test
    fun `falls back to phase 2 when phase 3 checksum mismatch`() {
        val files = tmp.newFolder("files")
        val tar = userlandTar()
        // Published sidecar does not match the served bytes.
        val p3 = UserlandManifest("userland-v2-dev", "bootstrap-phase3", baseUrl)
        serveRelease(p3.releaseTag, p3.assetPrefix, "aarch64", tar, "0".repeat(64))
        serveRelease("userland-v1", "bootstrap", "aarch64", tar, sha256(tar))
        val progress = mutableListOf<String>()
        val installer = makeInstaller(files)

        val status = installer.installIfNeeded { progress.add(it) }

        assertTrue("expected Installed fallback, got $status", status is UserlandStatus.Installed)
        assertEquals("userland-v1", (status as UserlandStatus.Installed).releaseTag)
        assertTrue(progress.any { it.contains("falling back to userland-v1") })
    }

    @Test
    fun `checksum failure fails and cleans the partial file`() {
        val files = tmp.newFolder("files")
        val tar = userlandTar()
        serveRelease("userland-v1", "bootstrap", "aarch64", tar, "f".repeat(64))
        val installer = makeInstaller(files)

        val status = installer.installIfNeeded()

        assertTrue("expected Failed, got $status", status is UserlandStatus.Failed)
        assertTrue((status as UserlandStatus.Failed).message.contains("SHA-256 mismatch"))
        val cache = File(files, "cache")
        assertFalse(cache.listFiles()?.any { it.name.endsWith(".partial") } ?: true)
        assertFalse(File(files, "usr/bin/bash").exists())
    }

    @Test
    fun `interrupted download is recovered by a fresh partial download`() {
        val files = tmp.newFolder("files")
        val cache = File(files, "cache")
        cache.mkdirs()
        File(cache, "bootstrap-aarch64.tar.gz.partial").writeBytes(byteArrayOf(1, 2, 3, 4))
        val tar = userlandTar()
        serveRelease("userland-v1", "bootstrap", "aarch64", tar, sha256(tar))
        val installer = makeInstaller(files)

        val status = installer.installIfNeeded()

        assertTrue("expected Installed, got $status", status is UserlandStatus.Installed)
        assertFalse(File(cache, "bootstrap-aarch64.tar.gz.partial").exists())
        assertEquals(sha256(tar), UserlandInstaller.sha256(File(cache, "bootstrap-aarch64.tar.gz")))
    }

    @Test
    fun `interrupted download resumes via range instead of restarting`() {
        val files = tmp.newFolder("files")
        val cache = File(files, "cache")
        cache.mkdirs()
        val tar = userlandTar()
        val cut = tar.size / 2
        val head = tar.copyOfRange(0, cut)
        var fullRequests = 0
        var rangeRequests = 0
        specialHandlers["/userland-v1/bootstrap-aarch64.tar.gz"] = { ex ->
            val range = ex.requestHeaders.getFirst("Range")
            if (range == null) {
                fullRequests++
                // Declare the full size but close after half the bytes: the
                // client sees a truncated transfer and must resume, not
                // restart from zero.
                ex.sendResponseHeaders(200, tar.size.toLong())
                ex.responseBody.use { it.write(head) }
            } else {
                rangeRequests++
                val start = range.removePrefix("bytes=").substringBefore('-').trim().toLong()
                val rest = tar.copyOfRange(start.toInt(), tar.size)
                ex.responseHeaders.add("Content-Range", "bytes $start-${tar.size - 1}/${tar.size}")
                ex.sendResponseHeaders(206, rest.size.toLong())
                ex.responseBody.use { it.write(rest) }
            }
        }
        serveRelease("userland-v1", "bootstrap", "aarch64", tar, sha256(tar))
        val installer = makeInstaller(files)

        val status = installer.installIfNeeded()

        assertTrue("expected Installed, got $status", status is UserlandStatus.Installed)
        assertEquals("first attempt should be a full request", 1, fullRequests)
        assertEquals("retry should resume with a Range request", 1, rangeRequests)
        assertFalse(File(cache, "bootstrap-aarch64.tar.gz.partial").exists())
        assertEquals(sha256(tar), UserlandInstaller.sha256(File(cache, "bootstrap-aarch64.tar.gz")))
    }

    @Test
    fun `preflight disk space failure blocks before download`() {
        val files = tmp.newFolder("files")
        val tar = userlandTar()
        serveRelease("userland-v1", "bootstrap", "aarch64", tar, sha256(tar))
        val installer = makeInstaller(files, free = { 1L })

        val status = installer.installIfNeeded()

        assertTrue("expected Failed, got $status", status is UserlandStatus.Failed)
        assertTrue((status as UserlandStatus.Failed).message.contains("disk space"))
        assertTrue(requested.none { it.endsWith(".tar.gz") && !it.endsWith(".sha256") })
        assertFalse(File(files, "usr/bin/bash").exists())
    }

    @Test
    fun `extraction disk space failure leaves prefix untouched and no staging`() {
        val files = tmp.newFolder("files")
        val tar = userlandTar()
        serveRelease("userland-v1", "bootstrap", "aarch64", tar, sha256(tar))
        var calls = 0
        val installer = makeInstaller(files, free = {
            calls++
            if (calls == 1) 100L * 1024 * 1024 else 1L
        })

        val status = installer.installIfNeeded()

        assertTrue("expected Failed, got $status", status is UserlandStatus.Failed)
        assertTrue((status as UserlandStatus.Failed).message.contains("disk space"))
        assertFalse(File(files, "usr/bin/bash").exists())
        assertFalse(files.listFiles()?.any { it.name.startsWith(".userland-staging") } ?: true)
    }

    @Test
    fun `offline startup keeps an installed runnable userland`() {
        val files = tmp.newFolder("files")
        val prefix = File(files, "usr")
        File(prefix, "bin").mkdirs()
        File(prefix, "bin/bash").writeBytes(elfBash)
        File(prefix, ".userland-release").writeText("userland-v2-dev")
        val installer = makeInstaller(files, online = false)

        val status = installer.installIfNeeded()

        assertEquals(UserlandStatus.AlreadyInstalled, status)
        assertTrue(requested.isEmpty())
    }

    @Test
    fun `offline without userland skips install`() {
        val files = tmp.newFolder("files")
        val installer = makeInstaller(files, online = false)

        val status = installer.installIfNeeded()

        assertEquals(UserlandStatus.SkippedOffline, status)
        assertTrue(requested.isEmpty())
    }

    @Test
    fun `unsupported abi skips without requests`() {
        val files = tmp.newFolder("files")
        val installer = makeInstaller(files, arch = null)

        val status = installer.installIfNeeded()

        assertEquals(UserlandStatus.SkippedNoRelease, status)
        assertTrue(requested.isEmpty())
    }

    @Test
    fun `wrong architecture assets yield no release`() {
        val files = tmp.newFolder("files")
        // Only x86_64 assets are published; the device is aarch64.
        val tar = userlandTar()
        serveRelease("userland-v2-dev", "bootstrap-phase3", "x86_64", tar, sha256(tar))
        serveRelease("userland-v1", "bootstrap", "x86_64", tar, sha256(tar))
        val installer = makeInstaller(files, arch = "aarch64")

        val status = installer.installIfNeeded()

        assertEquals(UserlandStatus.SkippedNoRelease, status)
        assertTrue(requested.all { it.endsWith(".sha256") })
    }

    @Test
    fun `path traversal in archive is rejected and nothing escapes`() {
        val files = tmp.newFolder("files")
        val tar = tarGz(
            "bin/bash" to Pair(elfBash, MODE_EXEC),
            "../evil" to Pair("escape".encodeToByteArray(), MODE_DATA)
        )
        serveRelease("userland-v1", "bootstrap", "aarch64", tar, sha256(tar))
        val installer = makeInstaller(files)

        val status = installer.installIfNeeded()

        assertTrue("expected Failed, got $status", status is UserlandStatus.Failed)
        assertFalse(File(files, "evil").exists())
        assertFalse(files.listFiles()?.any { it.name.startsWith(".userland-staging") } ?: true)
        assertFalse(File(files, "usr/bin/bash").exists())
    }

    @Test
    fun `absolute paths in archive are rejected`() {
        val files = tmp.newFolder("files")
        val tar = tarGz(
            "bin/bash" to Pair(elfBash, MODE_EXEC),
            "/etc/evil" to Pair("escape".encodeToByteArray(), MODE_DATA)
        )
        serveRelease("userland-v1", "bootstrap", "aarch64", tar, sha256(tar))
        val installer = makeInstaller(files)

        val status = installer.installIfNeeded()

        assertTrue("expected Failed, got $status", status is UserlandStatus.Failed)
    }

    @Test
    fun `missing shared library is diagnosed and phase 2 fallback works`() {
        val files = tmp.newFolder("files")
        val tar = userlandTar()
        val p3 = UserlandManifest("userland-v2-dev", "bootstrap-phase3", baseUrl)
        serveRelease(p3.releaseTag, p3.assetPrefix, "aarch64", tar, sha256(tar))
        serveRelease("userland-v1", "bootstrap", "aarch64", tar, sha256(tar))
        var probes = 0
        val progress = mutableListOf<String>()
        // Probes 1-2: the (empty) existing prefix. Probes 3-4: the Phase 3
        // staged bash + busybox, both failing with a missing library.
        // Probe 5+: the Phase 2 staged install, healthy.
        val launch: (File, File, Boolean) -> ShellEnvironment.LaunchDiagnostic = { file, _, _ ->
            probes++
            if (probes in 3..4) ShellEnvironment.LaunchDiagnostic(false, "libandroid-support.so")
            else ShellEnvironment.LaunchDiagnostic(file.isFile)
        }
        val installer = makeInstaller(files, launch = launch)

        val status = installer.installIfNeeded { progress.add(it) }

        assertTrue("expected fallback Installed, got $status", status is UserlandStatus.Installed)
        assertEquals("userland-v1", (status as UserlandStatus.Installed).releaseTag)
        assertTrue(progress.any { it.contains("missing library libandroid-support.so") })
        assertTrue(progress.any { it.contains("falling back to userland-v1") })
    }

    @Test
    fun `installed phase 2 upgrades to phase 3 when published`() {
        val files = tmp.newFolder("files")
        val prefix = File(files, "usr")
        File(prefix, "bin").mkdirs()
        File(prefix, "bin/bash").writeBytes(elfBash)
        // Exact marker written by the released v1.3.14 installer.
        File(prefix, ".userland-vuserland-v1").writeText("aarch64")
        val tar = userlandTar()
        val p3 = UserlandManifest("userland-v2-dev", "bootstrap-phase3", baseUrl)
        serveRelease(p3.releaseTag, p3.assetPrefix, "aarch64", tar, sha256(tar))
        val progress = mutableListOf<String>()
        val installer = makeInstaller(files)

        val status = installer.installIfNeeded { progress.add(it) }

        assertTrue("expected upgrade Installed, got $status", status is UserlandStatus.Installed)
        assertEquals("userland-v2-dev", (status as UserlandStatus.Installed).releaseTag)
        assertEquals("userland-v2-dev", File(prefix, ".userland-release").readText())
        assertTrue(progress.any { it.contains("upgrading userland-v1 to userland-v2-dev") })
        assertFalse(files.listFiles()?.any { it.name.startsWith("usr.old-") } ?: true)
    }

    @Test
    fun `matching installed release is left alone`() {
        val files = tmp.newFolder("files")
        val prefix = File(files, "usr")
        File(prefix, "bin").mkdirs()
        File(prefix, "bin/bash").writeBytes(elfBash)
        File(prefix, ".userland-release").writeText("userland-v1")
        val tar = userlandTar()
        serveRelease("userland-v1", "bootstrap", "aarch64", tar, sha256(tar))
        val installer = makeInstaller(files)

        val status = installer.installIfNeeded()

        assertEquals(UserlandStatus.AlreadyInstalled, status)
        assertTrue(requested.none { it.endsWith(".tar.gz") && !it.endsWith(".sha256") })
    }

    @Test
    fun `force reinstalls even when already installed`() {
        val files = tmp.newFolder("files")
        val prefix = File(files, "usr")
        File(prefix, "bin").mkdirs()
        File(prefix, "bin/bash").writeBytes(elfBash)
        File(prefix, ".userland-release").writeText("userland-v1")
        val tar = userlandTar()
        serveRelease("userland-v1", "bootstrap", "aarch64", tar, sha256(tar))
        val installer = makeInstaller(files)

        val status = installer.installIfNeeded(force = true)

        assertTrue("expected Installed, got $status", status is UserlandStatus.Installed)
    }

    @Test
    fun `legacy phase 2 marker is recognized`() {
        val files = tmp.newFolder("files")
        val prefix = File(files, "usr")
        File(prefix, "bin").mkdirs()
        File(prefix, "bin/bash").writeBytes(elfBash)
        File(prefix, ".userland-vuserland-v1").writeText("aarch64")
        val tar = userlandTar()
        serveRelease("userland-v1", "bootstrap", "aarch64", tar, sha256(tar))
        val installer = makeInstaller(files)

        assertEquals("userland-v1", installer.installedRelease(prefix))
        val status = installer.installIfNeeded()
        assertEquals(UserlandStatus.AlreadyInstalled, status)
    }

    @Test
    fun `broken userland is reinstalled automatically`() {
        val files = tmp.newFolder("files")
        val prefix = File(files, "usr")
        File(prefix, "bin").mkdirs()
        // ELF magic, but the dynamic loader fails (simulated by the checker).
        File(prefix, "bin/bash").writeBytes(elfBash)
        File(prefix, ".userland-release").writeText("userland-v1")
        val tar = userlandTar()
        serveRelease("userland-v1", "bootstrap", "aarch64", tar, sha256(tar))
        var probes = 0
        val launch: (File, File, Boolean) -> ShellEnvironment.LaunchDiagnostic = { file, _, _ ->
            probes++
            // First probe: the existing broken bash; rest: healthy installs.
            if (probes == 1) ShellEnvironment.LaunchDiagnostic(false, "libandroid-support.so")
            else ShellEnvironment.LaunchDiagnostic(file.isFile)
        }
        val installer = makeInstaller(files, launch = launch)

        val status = installer.installIfNeeded()

        assertTrue("expected reinstall Installed, got $status", status is UserlandStatus.Installed)
        assertEquals("userland-v1", File(prefix, ".userland-release").readText())
    }

    @Test
    fun `no release published at all`() {
        val files = tmp.newFolder("files")
        val installer = makeInstaller(files)

        val status = installer.installIfNeeded()

        assertEquals(UserlandStatus.SkippedNoRelease, status)
    }

    @Test
    fun `sha256 helper matches java digest of known bytes`() {
        val file = tmp.newFile("payload")
        file.writeBytes("codec".encodeToByteArray())
        val expected = MessageDigest.getInstance("SHA-256")
            .digest("codec".encodeToByteArray())
            .joinToString("") { "%02x".format(it) }
        assertEquals(expected, UserlandInstaller.sha256(file))
    }
}
