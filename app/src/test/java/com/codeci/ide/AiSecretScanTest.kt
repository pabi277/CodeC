package com.codeci.ide

import com.codeci.ide.ui.ai.AiSecretScan
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 94 — the content-level secret guard, host-tested.
 *
 * The name filter (`AiProjectFiles.isSecretLike`) refuses a *file*; this one
 * refuses a *value* inside any file the agent reads, and in the packed context.
 * Both are deliberately over-inclusive, and both must never hide anything
 * silently — that is what the count line is for.
 */
class AiSecretScanTest {

    @Test
    fun `known credential shapes are redacted wherever they appear`() {
        // Every value below is a deliberately SHORT fake: long enough for the
        // guard's own pattern, short enough that GitHub push protection does not
        // read it as a live token (it flagged a longer Slack-shaped fixture once,
        // so that one is assembled at runtime — the guard still sees one string).
        val slackFixture = "xox" + "b-123456789012-abcdefghijklmn"
        val text = """
            GEMINI=AIzaSyD4bC1xH9kLmN0pQrSt
            nvidia key nvapi-AbCdEfGhIjKlMnOpQrSt
            openai sk-proj-AbCdEfGhIjKlMnOp
            github ghp_AbCdEfGhIjKlMnOpQrSt
            gitlab glpat-AbCdEfGhIjKlMnOpQrS
            slack $slackFixture
            aws AKIAIOSFODNN7EXAMPLE
        """.trimIndent()
        val result = AiSecretScan.redact(text)
        assertFalse("no key survives", result.text.contains("AIzaSy"))
        assertFalse(result.text.contains("nvapi-"))
        assertFalse(result.text.contains("sk-proj-"))
        assertFalse(result.text.contains("ghp_"))
        assertFalse(result.text.contains("glpat-"))
        assertFalse(result.text.contains("xoxb-1234"))
        assertFalse(result.text.contains("AKIAIOSFODNN7EXAMPLE"))
        assertEquals("one redaction per value", 7, result.redactions)
        assertTrue("and the count is said out loud", result.text.contains(AiSecretScan.noteFor(7)))
        assertTrue(result.text.contains(AiSecretScan.MARKER))
    }

    @Test
    fun `an ordinary assignment with a credential-shaped name loses only its value`() {
        val result = AiSecretScan.redact("""api_key = "Ab3dEf9hIjK2lMn0"  # keep this secret""")
        assertTrue("the name survives", result.text.contains("api_key ="))
        assertTrue("the value does not", !result.text.contains("Ab3dEf9hIjK2lMn0"))
        assertTrue("the trailing comment survives", result.text.contains("keep this secret"))
        assertEquals(1, result.redactions)
    }

    @Test
    fun `placeholders and code references are never redacted`() {
        val text = """
            api_key = os.environ["GEMINI_API_KEY"]
            token = process.env.TOKEN
            secret: String
            apiKey = "YOUR_API_KEY"
            client_secret = "<your-secret>"
            password = "changeme"
            token = getAccessToken()
        """.trimIndent()
        val result = AiSecretScan.redact(text)
        assertEquals("nothing worth hiding in this file", 0, result.redactions)
        assertEquals("and the text is untouched", text, result.text)
    }

    @Test
    fun `a bare value is hidden when in doubt - a comment keeps the shape`() {
        // Documented over-inclusion: `password = userInput` *could* be a
        // reference, but a scanner that guesses "reference" leaks the one time
        // it is wrong. The marker keeps the code shape readable and the count
        // line says why the line looks odd.
        val result = AiSecretScan.redact("password = userInput\nsecretKey = TopSecretValue1\n")
        assertEquals(2, result.redactions)
        assertTrue(result.text.contains("password = ${AiSecretScan.MARKER}"))
        assertTrue("the name is never hidden", result.text.contains("secretKey ="))
        assertTrue(result.text.contains(AiSecretScan.noteFor(2)))
    }

    @Test
    fun `a pasted private key block becomes one marker line`() {
        val text = """
            before
            -----BEGIN RSA PRIVATE KEY-----
            MIIEowIBAAKCAQEA1234567890abcdefghijklmnopqrstuvwxyz
            ABCDEFGHIJKLMNOPQRSTUVWXYZ0987654321abcdefghijkl
            -----END RSA PRIVATE KEY-----
            after
        """.trimIndent()
        val result = AiSecretScan.redact(text)
        assertTrue(result.text.startsWith("before"))
        assertTrue("the line after the block survives", result.text.lines().any { it == "after" })
        assertTrue("the key material is gone", !result.text.contains("MIIEowIBAAKCAQEA"))
        assertTrue("and the block is named", result.text.contains(AiSecretScan.KEY_BLOCK_MARKER))
        assertFalse("no BEGIN/END left", result.text.contains("BEGIN RSA PRIVATE KEY"))
    }

    @Test
    fun `a key that was never closed hides the whole tail`() {
        val result = AiSecretScan.redact(
            "-----BEGIN PRIVATE KEY-----\nAAAA1111BBBB2222CCCC3333\nDDDD4444EEEE5555"
        )
        assertFalse("nothing of the tail survives", result.text.contains("DDDD4444EEEE5555"))
        assertTrue(result.text.contains(AiSecretScan.KEY_BLOCK_MARKER))
    }

    @Test
    fun `a JWT in a log line is redacted and the line is otherwise intact`() {
        val result = AiSecretScan.redact(
            "auth: eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIxMjM0NTY3ODkwIn0.dBjftJeZ4CVPmB92K27uhbUJU1p1r_wW1g"
        )
        assertFalse(result.text.contains("eyJhbGciOiJIUzI1NiJ9"))
        assertTrue(result.text.startsWith("auth: "))
    }

    @Test
    fun `clean text is returned byte for byte with no note`() {
        val text = "int main(void) {\n  printf(\"hello\");\n  return 0;\n}\n"
        val result = AiSecretScan.redact(text)
        assertTrue(result.clean)
        assertEquals(text, result.text)
    }

    @Test
    fun `the guard is pure - no android, no io, no clock`() {
        val raw = RepoFiles.mainSource("app/src/main/java/com/codeci/ide/ui/ai/AiSecretScan.kt").readText()
        for (banned in listOf("import android.", "import java.io", "import androidx.")) {
            assertFalse("AiSecretScan.kt must stay pure ($banned)", raw.contains(banned))
        }
    }
}
