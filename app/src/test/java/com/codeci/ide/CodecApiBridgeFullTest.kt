package com.codeci.ide

import com.codeci.ide.ui.terminal.BatterySnapshot
import com.codeci.ide.ui.terminal.CodecApiBridge
import com.codeci.ide.ui.terminal.CodecApiProtocol
import com.codeci.ide.ui.terminal.DeviceApiOps
import com.codeci.ide.ui.terminal.SensorReading
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Host tests for the Phase 18 bridge core (battery / sensor / TTS / camera /
 * intent). Everything here runs against the injected android-free
 * [DeviceApiOps] so the validation and JSON formatting are proven without a
 * device.
 */
class CodecApiBridgeFullTest {

    private fun tempDir(): File =
        File(System.getProperty("java.io.tmpdir"), "codec-api-full-${System.nanoTime()}").apply {
            deleteRecursively()
            mkdirs()
        }

    /** Builds a request whose payload file (when non-null) lives in [apiDir]. */
    private fun request(
        op: CodecApiProtocol.Op,
        apiDir: File,
        payload: String? = null,
        fileName: String = "req.p18"
    ): CodecApiProtocol.Request {
        val req = if (payload != null) {
            File(apiDir, fileName).apply { writeText(payload) }
        } else {
            File(apiDir, fileName)
        }
        return CodecApiProtocol.Request(
            op, req.absolutePath, File(apiDir, "res.p18").absolutePath
        )
    }

    private fun recordingOps(): Pair<DeviceApiOps, MutableList<String>> {
        val events = mutableListOf<String>()
        val ops = DeviceApiOps(
            batteryStatus = {
                BatterySnapshot(
                    percentage = 85,
                    status = "charging",
                    temperatureC = 31.5,
                    health = "good",
                    voltageMv = 4088,
                    plugged = "ac"
                )
            },
            sensorRead = { type ->
                when (type) {
                    "accelerometer" -> SensorReading(type, x = 0.12, y = 9.81, z = 0.05)
                    "gyroscope" -> SensorReading(type, x = 0.0, y = -0.5, z = 1.0)
                    "light" -> SensorReading(type, lux = 123.45)
                    else -> null
                }
            },
            ttsSpeak = { text ->
                events.add("tts:$text")
                "OK"
            },
            intentSend = { action, data ->
                events.add("intent:$action:$data")
                "OK"
            }
        )
        return ops to events
    }

    // ---- battery -----------------------------------------------------------

    @Test
    fun `battery status formats the snapshot as json`() {
        val (ops, _) = recordingOps()
        val response = CodecApiBridge.batteryResponse(ops)
        assertEquals(
            "{\"percentage\":85,\"status\":\"charging\",\"temperature\":31.5," +
                "\"health\":\"good\",\"voltage\":4088,\"plugged\":\"ac\"}",
            response
        )
    }

    @Test
    fun `battery status reports an unavailable service as an error`() {
        val response = CodecApiBridge.batteryResponse(DeviceApiOps())
        assertTrue(response.startsWith("ERR:"))
        assertTrue(response.contains("battery service unavailable"))
    }

    @Test
    fun `battery json tolerates unknown fields`() {
        val ops = DeviceApiOps(
            batteryStatus = {
                BatterySnapshot(
                    percentage = null,
                    status = "unknown",
                    temperatureC = null,
                    health = "unknown",
                    voltageMv = null,
                    plugged = "unknown"
                )
            }
        )
        val response = CodecApiBridge.batteryResponse(ops)
        assertEquals(
            "{\"percentage\":null,\"status\":\"unknown\",\"temperature\":null," +
                "\"health\":\"unknown\",\"voltage\":null,\"plugged\":\"unknown\"}",
            response
        )
    }

    // ---- sensor ------------------------------------------------------------

    @Test
    fun `accelerometer and gyroscope format x y z triples`() {
        val base = tempDir()
        try {
            val (ops, _) = recordingOps()
            val accel = CodecApiBridge.execute(
                request(CodecApiProtocol.Op.SENSOR_READ, base, "accelerometer"),
                base, { error("must not read clipboard") }, {}, deviceApi = ops
            )
            assertEquals("{\"type\":\"accelerometer\",\"x\":0.12,\"y\":9.81,\"z\":0.05}", accel)

            val gyro = CodecApiBridge.execute(
                request(CodecApiProtocol.Op.SENSOR_READ, base, "gyroscope", "req.gyro"),
                base, { error("must not read clipboard") }, {}, deviceApi = ops
            )
            assertEquals("{\"type\":\"gyroscope\",\"x\":0,\"y\":-0.5,\"z\":1}", gyro)
        } finally {
            base.deleteRecursively()
        }
    }

    @Test
    fun `light sensor formats lux`() {
        val base = tempDir()
        try {
            val (ops, _) = recordingOps()
            val response = CodecApiBridge.execute(
                request(CodecApiProtocol.Op.SENSOR_READ, base, "light"),
                base, { error("must not read clipboard") }, {}, deviceApi = ops
            )
            assertEquals("{\"type\":\"light\",\"lux\":123.45}", response)
        } finally {
            base.deleteRecursively()
        }
    }

    @Test
    fun `unknown sensor type is rejected before the adapter runs`() {
        val base = tempDir()
        try {
            var adapterCalls = 0
            val ops = DeviceApiOps(sensorRead = { adapterCalls++; null })
            val response = CodecApiBridge.sensorResponse(
                request(CodecApiProtocol.Op.SENSOR_READ, base, "magnetometer"),
                base, ops
            )
            assertTrue(response.startsWith("ERR:"))
            assertTrue(response.contains("unknown sensor type"))
            assertEquals(0, adapterCalls)
        } finally {
            base.deleteRecursively()
        }
    }

    @Test
    fun `unavailable sensor is an explicit error`() {
        val base = tempDir()
        try {
            val response = CodecApiBridge.sensorResponse(
                request(CodecApiProtocol.Op.SENSOR_READ, base, "gyroscope"),
                base, DeviceApiOps()
            )
            assertTrue(response.startsWith("ERR:"))
            assertTrue(response.contains("gyroscope sensor unavailable"))
        } finally {
            base.deleteRecursively()
        }
    }

    @Test
    fun `sensor read without a request file and oversized payload are errors`() {
        val base = tempDir()
        try {
            val noReq = CodecApiProtocol.Request(
                CodecApiProtocol.Op.SENSOR_READ,
                null, File(base, "res.1").absolutePath
            )
            assertTrue(CodecApiBridge.sensorResponse(noReq, base, DeviceApiOps())
                .startsWith("ERR:"))

            val big = File(base, "req.big").apply { writeBytes(ByteArray(300)) }
            val req = CodecApiProtocol.Request(
                CodecApiProtocol.Op.SENSOR_READ,
                big.absolutePath, File(base, "res.2").absolutePath
            )
            assertTrue(CodecApiBridge.sensorResponse(req, base, DeviceApiOps())
                .startsWith("ERR:"))
        } finally {
            base.deleteRecursively()
        }
    }

    // ---- TTS ---------------------------------------------------------------

    @Test
    fun `tts speaks the validated text and returns OK`() {
        val base = tempDir()
        try {
            val (ops, events) = recordingOps()
            val response = CodecApiBridge.execute(
                request(CodecApiProtocol.Op.TTS_SPEAK, base, "Hello from CodeC terminal"),
                base, { error("must not read clipboard") }, {}, deviceApi = ops
            )
            assertEquals("OK", response)
            assertEquals(listOf("tts:Hello from CodeC terminal"), events)
        } finally {
            base.deleteRecursively()
        }
    }

    @Test
    fun `tts rejects blank and oversized text without speaking`() {
        val base = tempDir()
        try {
            val (ops, events) = recordingOps()
            val blank = CodecApiBridge.execute(
                request(CodecApiProtocol.Op.TTS_SPEAK, base, "   "),
                base, { error("must not read clipboard") }, {}, deviceApi = ops
            )
            assertTrue(blank.startsWith("ERR:"))
            assertTrue(blank.contains("empty"))

            val big = File(base, "req.tts-big").apply { writeBytes(ByteArray(CodecApiProtocol.MAX_TTS_BYTES + 1)) }
            val requestBig = CodecApiProtocol.Request(
                CodecApiProtocol.Op.TTS_SPEAK, big.absolutePath, File(base, "res.tts-big").absolutePath
            )
            val oversized = CodecApiBridge.execute(
                requestBig, base, { error("must not read clipboard") }, {}, deviceApi = ops
            )
            assertTrue(oversized.startsWith("ERR:"))
            assertTrue(oversized.contains("too large"))
            assertTrue(events.isEmpty())
        } finally {
            base.deleteRecursively()
        }
    }

    @Test
    fun `tts engine failure propagates as an error`() {
        val base = tempDir()
        try {
            val ops = DeviceApiOps(
                ttsSpeak = { "${CodecApiProtocol.ERR_PREFIX}no TextToSpeech engine available" }
            )
            val response = CodecApiBridge.execute(
                request(CodecApiProtocol.Op.TTS_SPEAK, base, "hello"),
                base, { error("must not read clipboard") }, {}, deviceApi = ops
            )
            assertTrue(response.startsWith("ERR:"))
            assertTrue(response.contains("TextToSpeech"))
        } finally {
            base.deleteRecursively()
        }
    }

    // ---- intent ------------------------------------------------------------

    @Test
    fun `intent view and dial dispatch allowed schemes`() {
        val base = tempDir()
        try {
            val (ops, events) = recordingOps()
            val view = CodecApiBridge.execute(
                request(CodecApiProtocol.Op.INTENT_SEND, base, "view\ngeo:0,0?q=restaurants"),
                base, { error("must not read clipboard") }, {}, deviceApi = ops
            )
            assertEquals("OK", view)

            val dial = CodecApiBridge.execute(
                request(CodecApiProtocol.Op.INTENT_SEND, base, "dial\ntel:+919876543210", "req.dial"),
                base, { error("must not read clipboard") }, {}, deviceApi = ops
            )
            assertEquals("OK", dial)
            assertEquals(
                listOf(
                    "intent:view:geo:0,0?q=restaurants",
                    "intent:dial:tel:+919876543210"
                ),
                events
            )
        } finally {
            base.deleteRecursively()
        }
    }

    @Test
    fun `intent send passes the text payload`() {
        val base = tempDir()
        try {
            val (ops, events) = recordingOps()
            val response = CodecApiBridge.execute(
                request(CodecApiProtocol.Op.INTENT_SEND, base, "send\nHello from CodeC\nsecond line"),
                base, { error("must not read clipboard") }, {}, deviceApi = ops
            )
            assertEquals("OK", response)
            assertEquals(listOf("intent:send:Hello from CodeC\nsecond line"), events)
        } finally {
            base.deleteRecursively()
        }
    }

    @Test
    fun `intent rejects unknown actions, bad schemes and missing data`() {
        val base = tempDir()
        try {
            val (ops, events) = recordingOps()
            val unknown = CodecApiBridge.execute(
                request(CodecApiProtocol.Op.INTENT_SEND, base, "open\nhttps://example.com"),
                base, { error("must not read clipboard") }, {}, deviceApi = ops
            )
            assertTrue(unknown.startsWith("ERR:"))
            assertTrue(unknown.contains("unknown intent action"))

            val badScheme = CodecApiBridge.execute(
                request(CodecApiProtocol.Op.INTENT_SEND, base, "view\nfile:///etc/passwd", "req.bad"),
                base, { error("must not read clipboard") }, {}, deviceApi = ops
            )
            assertTrue(badScheme.startsWith("ERR:"))
            assertTrue(badScheme.contains("allows only"))

            val noData = CodecApiBridge.execute(
                request(CodecApiProtocol.Op.INTENT_SEND, base, "view\n", "req.nodata"),
                base, { error("must not read clipboard") }, {}, deviceApi = ops
            )
            assertTrue(noData.startsWith("ERR:"))
            assertTrue(noData.contains("missing intent data"))

            assertTrue(events.isEmpty())
        } finally {
            base.deleteRecursively()
        }
    }

    @Test
    fun `intent without a request file is an error`() {
        val base = tempDir()
        try {
            val noReq = CodecApiProtocol.Request(
                CodecApiProtocol.Op.INTENT_SEND,
                null, File(base, "res.1").absolutePath
            )
            assertTrue(CodecApiBridge.intentResponse(noReq, base, DeviceApiOps())
                .startsWith("ERR:"))
        } finally {
            base.deleteRecursively()
        }
    }

    // ---- camera ------------------------------------------------------------

    @Test
    fun `camera validates the output file name`() {
        val base = tempDir()
        try {
            val ok = CodecApiBridge.cameraResponse(
                request(CodecApiProtocol.Op.CAMERA_CAPTURE, base, "photo.jpg"), base
            )
            assertEquals("CAPTURING:photo.jpg", ok)

            val png = CodecApiBridge.cameraResponse(
                request(CodecApiProtocol.Op.CAMERA_CAPTURE, base, "my.photo-01.png", "req.png"), base
            )
            assertEquals("CAPTURING:my.photo-01.png", png)
        } finally {
            base.deleteRecursively()
        }
    }

    @Test
    fun `camera rejects unsafe or unsupported file names`() {
        val base = tempDir()
        try {
            for (bad in listOf("", "../evil.jpg", "a/b.jpg", "notes.txt", ".hidden.jpg", "..")) {
                val response = CodecApiBridge.cameraResponse(
                    request(CodecApiProtocol.Op.CAMERA_CAPTURE, base, bad, "req.${bad.hashCode()}"),
                    base
                )
                assertTrue("expected ERR for '$bad'", response.startsWith("ERR:"))
                assertNull(CodecApiBridge.cameraTargetName(
                    CodecApiProtocol.Request(
                        CodecApiProtocol.Op.CAMERA_CAPTURE,
                        File(base, "req.${bad.hashCode()}").absolutePath,
                        File(base, "res.${bad.hashCode()}").absolutePath
                    ),
                    base
                ))
            }
        } finally {
            base.deleteRecursively()
        }
    }

    @Test
    fun `camera without a request file errors and target name is null`() {
        val base = tempDir()
        try {
            val noReq = CodecApiProtocol.Request(
                CodecApiProtocol.Op.CAMERA_CAPTURE,
                null, File(base, "res.1").absolutePath
            )
            assertTrue(CodecApiBridge.cameraResponse(noReq, base).startsWith("ERR:"))
            assertNull(CodecApiBridge.cameraTargetName(noReq, base))
        } finally {
            base.deleteRecursively()
        }
    }

    @Test
    fun `camera resume writes the interim marker and the final outcomes`() {
        val base = tempDir()
        try {
            val request = request(CodecApiProtocol.Op.CAMERA_CAPTURE, base, "shot.jpg")
            assertTrue(
                CodecApiBridge.resumeResponse(request, base, granted = true, notify = null)
                    .startsWith("CAPTURING:")
            )
            val denied = CodecApiBridge.resumeResponse(request, base, granted = false, notify = null)
            assertTrue(denied.startsWith("ERR:"))
            assertTrue(denied.contains("camera permission denied"))
        } finally {
            base.deleteRecursively()
        }
    }

    // ---- float formatting --------------------------------------------------

    @Test
    fun `formatDouble trims trailing zeros and never emits exponent forms`() {
        assertEquals("31.5", CodecApiBridge.formatDouble(31.5))
        assertEquals("0.12", CodecApiBridge.formatDouble(0.12))
        assertEquals("0.05", CodecApiBridge.formatDouble(0.05))
        assertEquals("0", CodecApiBridge.formatDouble(0.0))
        // Negative values just below zero round to "-0.000"; the formatter
        // must never emit "-0".
        assertEquals("0", CodecApiBridge.formatDouble(-0.0004))
        assertEquals("null", CodecApiBridge.formatDouble(null))
    }
}
