package com.codeci.ide

import com.codeci.ide.ui.services.PtyLineBuffer
import com.codeci.ide.ui.services.decodeExitStatus
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Host-JVM tests for the Phase 11 (D9) PTY line assembly and exit-code
 * decoding. The PTY session itself is Android-only (libcodec-pty.so) and is
 * validated on device.
 */
class PtyLineBufferTest {

    private class Sink {
        val complete = mutableListOf<String>()
        val partial = mutableListOf<String>()
    }

    @Test
    fun `complete lines are emitted with CR stripped`() {
        val sink = Sink()
        val buffer = PtyLineBuffer(
            onCompleteLine = { sink.complete += it },
            onPartialLine = { sink.partial += it }
        )
        buffer.append("Hello World!\r\n".toByteArray(Charsets.UTF_8), 14)
        assertEquals(listOf("Hello World!"), sink.complete)
        assertEquals(emptyList<String>(), sink.partial)
    }

    @Test
    fun `prompt without newline is emitted as a partial immediately`() {
        val sink = Sink()
        val buffer = PtyLineBuffer(
            onCompleteLine = { sink.complete += it },
            onPartialLine = { sink.partial += it }
        )
        buffer.append("Enter your full name: ".toByteArray(Charsets.UTF_8), 22)
        assertEquals(emptyList<String>(), sink.complete)
        assertEquals(listOf("Enter your full name: "), sink.partial)
    }

    @Test
    fun `partial prompt is completed by the next chunk`() {
        val sink = Sink()
        val buffer = PtyLineBuffer(
            onCompleteLine = { sink.complete += it },
            onPartialLine = { sink.partial += it }
        )
        buffer.append("Enter your ".toByteArray(Charsets.UTF_8), 11)
        buffer.append("full name: \r\n".toByteArray(Charsets.UTF_8), 13)
        assertEquals(listOf("Enter your full name: "), sink.complete)
        assertEquals(emptyList<String>(), sink.partial)
    }

    @Test
    fun `multiple lines in one chunk are split`() {
        val sink = Sink()
        val buffer = PtyLineBuffer(
            onCompleteLine = { sink.complete += it },
            onPartialLine = { sink.partial += it }
        )
        buffer.append("one\r\ntwo\r\nthree\r\n".toByteArray(Charsets.UTF_8), 17)
        assertEquals(listOf("one", "two", "three"), sink.complete)
        assertEquals(emptyList<String>(), sink.partial)
    }

    @Test
    fun `flush emits a leftover fragment as a final complete line`() {
        val sink = Sink()
        val buffer = PtyLineBuffer(
            onCompleteLine = { sink.complete += it },
            onPartialLine = { sink.partial += it }
        )
        buffer.append("done".toByteArray(Charsets.UTF_8), 4)
        assertEquals(listOf("done"), sink.partial)
        buffer.flush()
        assertEquals(listOf("done"), sink.complete)
    }

    @Test
    fun `blank lines are skipped`() {
        val sink = Sink()
        val buffer = PtyLineBuffer(
            onCompleteLine = { sink.complete += it },
            onPartialLine = { sink.partial += it }
        )
        buffer.append("\r\n\r\nhello\r\n".toByteArray(Charsets.UTF_8), 11)
        assertEquals(listOf("hello"), sink.complete)
    }

    @Test
    fun `exit status decoding follows shell conventions`() {
        assertEquals(0, decodeExitStatus(0))
        // Exit code 3: status = 3 shl 8
        assertEquals(3, decodeExitStatus(3 shl 8))
        // Exit code 255: status = 255 shl 8
        assertEquals(255, decodeExitStatus(255 shl 8))
        // Killed by SIGKILL (9): 128 + 9
        assertEquals(137, decodeExitStatus(9))
        // Invalid status
        assertEquals(1, decodeExitStatus(-1))
    }
}
