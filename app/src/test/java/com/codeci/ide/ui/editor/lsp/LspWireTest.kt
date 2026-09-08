package com.codeci.ide.ui.editor.lsp

import com.codeci.ide.ui.utils.LanguageType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.io.OutputStream
import java.io.PipedInputStream
import java.io.PipedOutputStream
import java.nio.charset.StandardCharsets

class LspWireTest {

    @Test
    fun frameUsesByteLengthNotCharLength() {
        val json = "{\"x\":\"é\"}" // é is 2 UTF-8 bytes
        val framed = LspWire.frame(json)
        val text = framed.toString(StandardCharsets.UTF_8)
        val bodyBytes = json.toByteArray(StandardCharsets.UTF_8)
        assertTrue(text.startsWith("Content-Length: " + bodyBytes.size))
        assertTrue(text.endsWith(json))
    }

    @Test
    fun readMessageRoundTripsAndIgnoresExtraHeaders() {
        val body = "{\"jsonrpc\":\"2.0\",\"id\":9,\"result\":{\"ok\":true}}"
        val framed = ("Content-Type: application/vscode-jsonrpc; charset=utf-8\r\n" +
            "Content-Length: " + body.toByteArray(StandardCharsets.UTF_8).size + "\r\n\r\n" +
            body).toByteArray(StandardCharsets.UTF_8)
        val resp = LspWire.readMessage(ByteArrayInputStream(framed))!!
        assertEquals(9, resp.id)
        assertTrue(resp.result!!.contains("ok"))
    }

    @Test
    fun fileUriIsAbsoluteAndEncodesSpaces() {
        assertEquals("file:///data/user/0/app/main.c", LspUris.fileUri("/data/user/0/app/main.c"))
        assertEquals("file:///untitled", LspUris.fileUri(""))
        assertEquals("file:///tmp/my%20file.c", LspUris.fileUri("/tmp/my file.c"))
        assertEquals("file:///proj/main.c", LspUris.fileUri("proj/main.c"))
    }

    @Test
    fun requestContextAtIsZeroBased() {
        val text = "int main() {\n    pr"
        val caret = text.length
        val ctx = LspRequestContext.at("/tmp/main.c", text, caret, "pr")
        assertEquals(1, ctx.line)
        assertEquals(6, ctx.column)
        assertEquals("pr", ctx.prefix)
        assertEquals("/tmp/main.c", ctx.fileName)
    }

    @Test(timeout = 10_000)
    fun clientRoundTripsInitializeAndCompletionAgainstFakeProcess() {
        val fake = ScriptedLspProcess()
        val config = LspServerCatalog.forLanguage(LanguageType.C)!!
        val client = LspStdioClient(
            config,
            projectRoot = "/tmp/proj",
            requestTimeoutMs = 2_000,
            processBuilderFactory = { _, _ -> fake },
        )
        client.start()
        val items = client.request(
            LspRequestContext.at("/tmp/proj/main.c", "int main() {\n    fr", "int main() {\n    fr".length, "fr")
        )
        client.shutdown()
        assertEquals(listOf("fread", "free"), items.map { it.label })
    }
}

/**
 * A Process that speaks LSP over pipes. Answers `initialize` with an
 * empty result and `textDocument/completion` with two items. Other
 * messages are notifications and are ignored.
 */
private class ScriptedLspProcess : Process() {
    private val stdin = PipedInputStream(64 * 1024)
    private val stdinWrite = PipedOutputStream(stdin)
    private val stdoutRead = PipedInputStream(64 * 1024)
    private val stdout = PipedOutputStream(stdoutRead)
    private val stderr = ByteArrayInputStream(ByteArray(0))
    @Volatile private var alive = true
    private val worker = Thread({
        try {
            while (alive) {
                val msg = LspWire.readMessage(stdin) ?: break
                when (msg.method) {
                    "initialize" -> reply(msg.id!!, "{\"capabilities\":{}}")
                    "textDocument/completion" -> reply(
                        msg.id!!,
                        "{\"isIncomplete\":false,\"items\":[" +
                            "{\"label\":\"fread\",\"kind\":3}," +
                            "{\"label\":\"free\",\"kind\":3}" +
                            "]}",
                    )
                }
            }
        } catch (_: Throwable) {
        }
    }, "scripted-lsp").apply {
        isDaemon = true
        start()
    }

    private fun reply(id: Int, resultJson: String) {
        val json = "{\"jsonrpc\":\"2.0\",\"id\":$id,\"result\":$resultJson}"
        synchronized(stdout) {
            stdout.write(LspWire.frame(json))
            stdout.flush()
        }
    }

    override fun getOutputStream(): OutputStream = stdinWrite
    override fun getInputStream(): InputStream = stdoutRead
    override fun getErrorStream(): InputStream = stderr
    override fun waitFor(): Int {
        destroy()
        worker.join(1_000)
        return 0
    }
    override fun waitFor(timeout: Long, unit: java.util.concurrent.TimeUnit): Boolean {
        worker.join(unit.toMillis(timeout))
        return !alive
    }
    override fun exitValue(): Int {
        if (alive) throw IllegalThreadStateException()
        return 0
    }
    override fun destroy() {
        alive = false
        runCatching { stdinWrite.close() }
        runCatching { stdout.close() }
        runCatching { stdin.close() }
        runCatching { stdoutRead.close() }
        worker.interrupt()
    }
    override fun destroyForcibly(): Process {
        destroy()
        return this
    }
}
