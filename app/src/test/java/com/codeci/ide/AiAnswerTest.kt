package com.codeci.ide

import com.codeci.ide.ui.ai.AiAnswerAccumulator
import com.codeci.ide.ui.ai.AiErrors
import com.codeci.ide.ui.ai.AiFailureKind
import com.codeci.ide.ui.ai.AiKeyBlob
import com.codeci.ide.ui.ai.AiOutcome
import com.codeci.ide.ui.ai.GeminiChunk
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 76 — how answers and failures are judged (pure), plus the encrypted
 * key's on-disk format. Error messages are fixed sentences: nothing Google
 * sends back (which could echo a URL or a key fragment) is ever shown.
 */
class AiAnswerTest {

    // ---- errors ---------------------------------------------------------

    @Test
    fun `http failures map to fixed plain messages`() {
        assertEquals(AiFailureKind.BAD_KEY, AiErrors.forHttp(400, "{\"reason\":\"API_KEY_INVALID\"}").kind)
        assertEquals(AiFailureKind.BAD_KEY, AiErrors.forHttp(401, null).kind)
        assertEquals(AiFailureKind.BAD_KEY, AiErrors.forHttp(403, "PERMISSION_DENIED").kind)
        assertEquals(AiFailureKind.MODEL_NOT_FOUND, AiErrors.forHttp(404, null).kind)
        assertEquals(AiFailureKind.RATE_LIMIT, AiErrors.forHttp(429, null).kind)
        assertEquals(AiFailureKind.NOT_AVAILABLE, AiErrors.forHttp(400, "FAILED_PRECONDITION").kind)
        assertEquals(AiFailureKind.NOT_AVAILABLE, AiErrors.forHttp(403, "User location is not supported").kind)
        assertEquals(AiFailureKind.BAD_REQUEST, AiErrors.forHttp(413, null).kind)
        assertEquals(AiFailureKind.SERVER, AiErrors.forHttp(503, null).kind)
    }

    @Test
    fun `no message ever echoes the server body`() {
        val body = "API key not valid. key=AIzaSECRET https://generativelanguage.googleapis.com/x"
        val f = AiErrors.forHttp(400, body)
        assertFalse(f.message.contains("AIza"))
        assertFalse(f.message.contains("http"))
        AiFailureKind.values().forEach { assertTrue(AiErrors.message(it).isNotBlank()) }
    }

    // ---- answers --------------------------------------------------------

    @Test
    fun `chunks accumulate into one answer`() {
        val acc = AiAnswerAccumulator()
        assertTrue(acc.accept(GeminiChunk(text = "Hello ")))
        assertTrue(acc.accept(GeminiChunk(text = "world", finishReason = "STOP")))
        assertEquals(AiOutcome.Answer("Hello world", cutShort = false), acc.outcome())
    }

    @Test
    fun `MAX_TOKENS keeps the text and says it was cut short`() {
        val acc = AiAnswerAccumulator()
        acc.accept(GeminiChunk(text = "partial", finishReason = "MAX_TOKENS"))
        assertEquals(AiOutcome.Answer("partial", cutShort = true), acc.outcome())
    }

    @Test
    fun `the reply cap stops the stream and marks the answer cut short`() {
        val acc = AiAnswerAccumulator(maxChars = 10)
        assertTrue(acc.accept(GeminiChunk(text = "12345")))
        assertTrue(acc.accept(GeminiChunk(text = "67890")))
        assertFalse(acc.accept(GeminiChunk(text = "X")))
        assertEquals(AiOutcome.Answer("1234567890", cutShort = true), acc.outcome())
    }

    @Test
    fun `a blocked or empty reply is a failure, never a blank answer`() {
        val blocked = AiAnswerAccumulator()
        blocked.accept(GeminiChunk(blockReason = "SAFETY"))
        assertEquals(AiFailureKind.BLOCKED, (blocked.outcome() as AiOutcome.Failed).failure.kind)

        val finishBlocked = AiAnswerAccumulator()
        finishBlocked.accept(GeminiChunk(finishReason = "RECITATION"))
        assertEquals(AiFailureKind.BLOCKED, (finishBlocked.outcome() as AiOutcome.Failed).failure.kind)

        val empty = AiAnswerAccumulator()
        empty.accept(GeminiChunk(text = "", finishReason = "STOP"))
        assertEquals(AiFailureKind.EMPTY, (empty.outcome() as AiOutcome.Failed).failure.kind)
    }

    @Test
    fun `an error event mid-stream ends the answer as a failure`() {
        val acc = AiAnswerAccumulator()
        acc.accept(GeminiChunk(text = "so far"))
        assertFalse(acc.accept(GeminiChunk(isError = true, errorCode = 429)))
        assertFalse(acc.accept(GeminiChunk(text = "ignored")))
        assertEquals(AiFailureKind.RATE_LIMIT, (acc.outcome() as AiOutcome.Failed).failure.kind)
    }

    // ---- key blob format ------------------------------------------------

    @Test
    fun `the key blob round-trips iv and ciphertext`() {
        val iv = ByteArray(12) { it.toByte() }
        val ct = byteArrayOf(9, 8, 7, 6, 5)
        val (iv2, ct2) = AiKeyBlob.decode(AiKeyBlob.encode(iv, ct))!!
        assertArrayEquals(iv, iv2)
        assertArrayEquals(ct, ct2)
    }

    @Test
    fun `anything this version did not write is rejected`() {
        assertNull(AiKeyBlob.decode(byteArrayOf()))
        assertNull(AiKeyBlob.decode(byteArrayOf(2, 1, 0, 0)))
        assertNull(AiKeyBlob.decode(byteArrayOf(1, 0, 5, 5)))
        assertNull(AiKeyBlob.decode(byteArrayOf(1, 12, 1, 2, 3)))
        // Plaintext is never mistaken for a blob.
        assertNull(AiKeyBlob.decode("AIzaSyPlainTextKey".toByteArray()))
    }
}
