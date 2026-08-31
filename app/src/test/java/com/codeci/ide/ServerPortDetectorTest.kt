package com.codeci.ide

import com.codeci.ide.ui.services.ServerPortDetector
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Phase 14 — the bind-line patterns the port monitor accepts, plus the
 * no-false-positive cases (a URL inside rendered content must NOT match).
 */
class ServerPortDetectorTest {

    @Test
    fun `flask bind line is detected`() {
        val detected = ServerPortDetector.detect(
            " * Running on http://127.0.0.1:5000/ (Press CTRL+C to quit)"
        )!!
        assertEquals(5000, detected.port)
        assertEquals("http://127.0.0.1:5000", detected.url)
    }

    @Test
    fun `flask zero-bind is rewritten to loopback`() {
        val detected = ServerPortDetector.detect(" * Running on http://0.0.0.0:5000")!!
        assertEquals(5000, detected.port)
        assertEquals("http://127.0.0.1:5000", detected.url)
    }

    @Test
    fun `uvicorn bind line is detected`() {
        val detected = ServerPortDetector.detect(
            "INFO:     Uvicorn running on http://127.0.0.1:8000 (Press CTRL+C to quit)"
        )!!
        assertEquals(8000, detected.port)
        assertEquals("http://127.0.0.1:8000", detected.url)
    }

    @Test
    fun `python http server line is detected`() {
        val detected = ServerPortDetector.detect(
            "Serving HTTP on 127.0.0.1 port 8000 (http://127.0.0.1:8000/) ..."
        )!!
        assertEquals(8000, detected.port)
        assertEquals("http://127.0.0.1:8000", detected.url)
    }

    @Test
    fun `codec C microservice line is detected`() {
        val detected = ServerPortDetector.detect(
            "CodeC server listening on http://127.0.0.1:8080"
        )!!
        assertEquals(8080, detected.port)
        assertEquals("http://127.0.0.1:8080", detected.url)
    }

    @Test
    fun `generic listening on line is detected`() {
        val detected = ServerPortDetector.detect("Listening on http://127.0.0.1:9090")!!
        assertEquals(9090, detected.port)
        assertEquals("http://127.0.0.1:9090", detected.url)
    }

    @Test
    fun `random url inside rendered content does not match`() {
        assertNull(
            ServerPortDetector.detect(
                "<html><body>The API lives at http://127.0.0.1:5000/api and /api/hello</body></html>"
            )
        )
    }

    @Test
    fun `unrelated log line does not match`() {
        assertNull(ServerPortDetector.detect("INFO: 127.0.0.1 - - [01/Jan/2026] \"GET / HTTP/1.1\" 200"))
        assertNull(ServerPortDetector.detect("starting on port 5000"))
    }

    @Test
    fun `non-local host bind is ignored`() {
        assertNull(ServerPortDetector.detect(" * Running on http://10.0.0.5:5000"))
        assertNull(ServerPortDetector.detect("Uvicorn running on http://0.0.0.0:0"))
    }

    @Test
    fun `blank and malformed lines are ignored`() {
        assertNull(ServerPortDetector.detect(""))
        assertNull(ServerPortDetector.detect("* Running on http://127.0.0.1:abc"))
    }
}
