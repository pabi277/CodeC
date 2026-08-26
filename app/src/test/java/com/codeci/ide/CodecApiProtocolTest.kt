package com.codeci.ide

import com.codeci.ide.ui.terminal.CodecApiProtocol
import com.codeci.ide.ui.terminal.TerminalEmulator
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Host tests for the Phase 4.7 terminal bridge protocol: OSC 1337 parsing,
 * path confinement (the security boundary), and emulator dispatch.
 */
class CodecApiProtocolTest {

    private fun tempDir(): File =
        File(System.getProperty("java.io.tmpdir"), "codec-api-proto-${System.nanoTime()}").apply {
            deleteRecursively()
            mkdirs()
        }

    @Test
    fun `parse accepts every clipboard op`() {
        val cases = mapOf(
            "clipboard.get" to CodecApiProtocol.Op.CLIPBOARD_GET,
            "clipboard.set" to CodecApiProtocol.Op.CLIPBOARD_SET,
            "clipboard.clear" to CodecApiProtocol.Op.CLIPBOARD_CLEAR,
            "clipboard.status" to CodecApiProtocol.Op.CLIPBOARD_STATUS,
            "notify.send" to CodecApiProtocol.Op.NOTIFY_SEND,
            "notify.clear" to CodecApiProtocol.Op.NOTIFY_CLEAR,
            "notify.status" to CodecApiProtocol.Op.NOTIFY_STATUS
        )
        for ((wire, op) in cases) {
            val req = CodecApiProtocol.parse("CodeCApi:$wire:/prefix/tmp/codec-api/req.abc:/prefix/tmp/codec-api/res.def")
            requireNotNull(req)
            assertEquals(op, req.op)
            assertEquals("/prefix/tmp/codec-api/req.abc", req.requestFile)
            assertEquals("/prefix/tmp/codec-api/res.def", req.responseFile)
        }
    }

    @Test
    fun `parse rejects foreign or malformed payloads`() {
        // Not our namespace.
        assertNull(CodecApiProtocol.parse("CodeCRequestStorage"))
        assertNull(CodecApiProtocol.parse("OtherApi:clipboard.get:a:b"))
        // Wrong field count.
        assertNull(CodecApiProtocol.parse("CodeCApi:clipboard.get:/a:/b:/c"))
        assertNull(CodecApiProtocol.parse("CodeCApi:clipboard.get:/a"))
        // Unknown op.
        assertNull(CodecApiProtocol.parse("CodeCApi:clipboard.copy:/a:/b"))
        // Empty response path.
        assertNull(CodecApiProtocol.parse("CodeCApi:clipboard.get:/a:"))
        // Empty payload.
        assertNull(CodecApiProtocol.parse(""))
    }

    @Test
    fun `build round-trips through parse`() {
        val built = CodecApiProtocol.build(
            CodecApiProtocol.Op.CLIPBOARD_SET,
            "/prefix/tmp/codec-api/req.1",
            "/prefix/tmp/codec-api/res.2"
        )
        assertEquals("CodeCApi:clipboard.set:/prefix/tmp/codec-api/req.1:/prefix/tmp/codec-api/res.2", built)
        val parsed = CodecApiProtocol.parse(built)
        requireNotNull(parsed)
        assertEquals(CodecApiProtocol.Op.CLIPBOARD_SET, parsed.op)
        assertEquals("/prefix/tmp/codec-api/req.1", parsed.requestFile)
        assertEquals("/prefix/tmp/codec-api/res.2", parsed.responseFile)
    }

    @Test
    fun `parse accepts the phase 5_3 termux-api ops`() {
        val cases = mapOf(
            "toast.show" to CodecApiProtocol.Op.TOAST_SHOW,
            "share.text" to CodecApiProtocol.Op.SHARE_TEXT,
            "url.open" to CodecApiProtocol.Op.OPEN_URL,
            "vibrate" to CodecApiProtocol.Op.VIBRATE
        )
        for ((wire, op) in cases) {
            val req = CodecApiProtocol.parse("CodeCApi:$wire:/p/tmp/codec-api/req.a:/p/tmp/codec-api/res.b")
            requireNotNull(req)
            assertEquals(op, req.op)
        }
    }

    @Test
    fun `termux-api ops are flagged but notify ops are not`() {
        assertTrue(CodecApiProtocol.Op.TOAST_SHOW.isTermuxApiOperation)
        assertTrue(CodecApiProtocol.Op.SHARE_TEXT.isTermuxApiOperation)
        assertTrue(CodecApiProtocol.Op.OPEN_URL.isTermuxApiOperation)
        assertTrue(CodecApiProtocol.Op.VIBRATE.isTermuxApiOperation)
        assertFalse(CodecApiProtocol.Op.NOTIFY_SEND.isTermuxApiOperation)
        assertFalse(CodecApiProtocol.Op.CLIPBOARD_GET.isTermuxApiOperation)
    }

    @Test
    fun `notify ops are flagged as permission operations`() {
        assertTrue(CodecApiProtocol.Op.NOTIFY_SEND.isNotifyOperation)
        assertTrue(CodecApiProtocol.Op.NOTIFY_CLEAR.isNotifyOperation)
        assertTrue(CodecApiProtocol.Op.NOTIFY_STATUS.isNotifyOperation)
        assertFalse(CodecApiProtocol.Op.CLIPBOARD_GET.isNotifyOperation)
    }

    @Test
    fun `permission notice markers are protocol constants`() {
        assertEquals(
            "NEED_PERMISSION:android.permission.POST_NOTIFICATIONS",
            CodecApiProtocol.permissionNotice(
                "android.permission.POST_NOTIFICATIONS"
            )
        )
        assertTrue(
            CodecApiProtocol.permissionNotice("x")
                .startsWith(CodecApiProtocol.NEED_PERMISSION_PREFIX)
        )
    }

    @Test
    fun `confinement accepts direct children of the api dir only`() {
        val apiDir = tempDir()
        try {
            val child = File(apiDir, "res.1")
            assertTrue(CodecApiProtocol.isConfinedDirectChild(child.absolutePath, apiDir))

            // Sibling path is rejected.
            val outside = File(apiDir.parentFile, "res-evil.1")
            assertFalse(CodecApiProtocol.isConfinedDirectChild(outside.absolutePath, apiDir))

            // Traversal component is rejected.
            assertFalse(
                CodecApiProtocol.isConfinedDirectChild(
                    File(apiDir, "../res-evil.2").absolutePath,
                    apiDir
                )
            )

            // Nested paths are not direct children.
            val nested = File(File(apiDir, "sub"), "res.3")
            assertFalse(CodecApiProtocol.isConfinedDirectChild(nested.absolutePath, apiDir))

            // The api dir itself is not a child.
            assertFalse(CodecApiProtocol.isConfinedDirectChild(apiDir.absolutePath, apiDir))

            // Nonexistent children still validate by path (the app creates them).
            val future = File(apiDir, "res.not-yet")
            assertTrue(CodecApiProtocol.isConfinedDirectChild(future.absolutePath, apiDir))
        } finally {
            apiDir.deleteRecursively()
        }
    }

    @Test
    fun `canonicalUserPrefix maps the user 0 alias and leaves others untouched`() {
        assertEquals(
            "/data/data/com.codeci.ide/files/usr",
            CodecApiProtocol.canonicalUserPrefix("/data/user/0/com.codeci.ide/files/usr")
        )
        // Already-canonical, temp dirs, and secondary users are unchanged.
        assertEquals(
            "/data/data/com.codeci.ide/files/usr",
            CodecApiProtocol.canonicalUserPrefix("/data/data/com.codeci.ide/files/usr")
        )
        assertEquals("/tmp/codec/usr", CodecApiProtocol.canonicalUserPrefix("/tmp/codec/usr"))
        assertEquals(
            "/data/user/10/com.codeci.ide/files/usr",
            CodecApiProtocol.canonicalUserPrefix("/data/user/10/com.codeci.ide/files/usr")
        )
    }

    @Test
    fun `confinement resolves symlink escapes`() {
        val base = tempDir()
        try {
            val apiDir = File(base, "codec-api").apply { mkdirs() }
            val outside = File(base, "secret.txt").apply { writeText("outside") }
            val link = File(apiDir, "res-link")
            try {
                if (!java.nio.file.Files.createSymbolicLink(
                        link.toPath(),
                        outside.toPath()
                    ).toFile().exists()
                ) {
                    return // filesystem without symlink support: nothing to assert
                }
                assertFalse(
                    CodecApiProtocol.isConfinedDirectChild(link.absolutePath, apiDir)
                )
            } catch (_: UnsupportedOperationException) {
                // symlinks unsupported here — skip
            } catch (_: java.nio.file.FileSystemException) {
                // symlinks unsupported here — skip
            }
        } finally {
            base.deleteRecursively()
        }
    }

    @Test
    fun `emulator dispatches CodeCApi osc to the callback`() {
        var payload: String? = null
        val emu = TerminalEmulator(40, 10)
        emu.onCodecApiRequest = { payload = it }
        emu.feed("\u001b]1337;CodeCApi:clipboard.get:/a:/b\u0007")
        assertEquals("CodeCApi:clipboard.get:/a:/b", payload)
    }

    @Test
    fun `emulator keeps legacy storage control intact`() {
        var storageRequested = false
        var apiPayload: String? = null
        val emu = TerminalEmulator(40, 10)
        emu.onStoragePermissionRequested = { storageRequested = true }
        emu.onCodecApiRequest = { apiPayload = it }
        emu.feed("\u001b]1337;CodeCRequestStorage\u0007")
        assertTrue(storageRequested)
        assertNull(apiPayload) // the legacy control is not a CodeCApi request
    }

    @Test
    fun `emulator ignores unknown osc 1337 values`() {
        var storageRequested = false
        var apiPayload: String? = null
        val emu = TerminalEmulator(40, 10)
        emu.onStoragePermissionRequested = { storageRequested = true }
        emu.onCodecApiRequest = { apiPayload = it }
        emu.feed("\u001b]1337;UnknownControl\u0007")
        assertFalse(storageRequested)
        assertNull(apiPayload)
    }
}
