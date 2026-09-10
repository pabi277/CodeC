package com.codeci.ide

import com.codeci.ide.ui.support.FeedbackDraft
import com.codeci.ide.ui.support.FeedbackInput
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 41.1 — the report builder and the links that carry it, pinned to
 * the part doc's exit conditions 1–5. Everything here is pure: the same
 * input twice gives the same string, redaction happens before the budget,
 * the user's own text is never cut, and a number that is not strict E.164
 * never becomes a wa.me link (a wrong number opens a chat with the wrong
 * person — the documented #1 failure of this feature).
 */
class FeedbackDraftTest {

    private fun input(
        userText: String = "The RUN button does nothing after I edit main.c",
        includeLog: Boolean = false,
        logTail: List<String> = emptyList(),
        includeCrash: Boolean = false,
        crashRecord: String? = null,
        maxChars: Int = FeedbackDraft.WHATSAPP_BUDGET,
        secretToScrub: String? = null,
        project: String? = "hello-c"
    ) = FeedbackInput(
        appVersion = "1.3.16 (3439922)",
        androidRelease = "13",
        apiLevel = 33,
        device = "Redmi Note 9",
        abis = "arm64-v8a",
        project = project,
        screen = "Settings",
        userText = userText,
        includeLog = includeLog,
        logTail = logTail,
        includeCrash = includeCrash,
        crashRecord = crashRecord,
        maxChars = maxChars,
        secretToScrub = secretToScrub
    )

    // ------------------------------------------------------------------
    // Exit 1 — layout golden (byte-for-byte, fixed order, user text untouched)
    // ------------------------------------------------------------------

    @Test
    fun `build produces the fixed layout with the user text verbatim`() {
        val golden =
            "CodeC 1.3.16 (3439922) · Android 13 (API 33) · Redmi Note 9 · arm64-v8a\n" +
                "Project: hello-c · Screen: Settings\n" +
                "--- what I saw ---\n" +
                "The RUN button does nothing after I edit main.c"
        assertEquals(golden, FeedbackDraft.build(input()))
    }

    @Test
    fun `empty user text is still a valid report with a no-text line`() {
        val report = FeedbackDraft.build(input(userText = "   \n  "))
        assertTrue(report.contains("--- what I saw ---\n(no text)"))
        assertFalse(report.isBlank())
    }

    @Test
    fun `a missing project is stated, not silently dropped`() {
        val report = FeedbackDraft.build(input(project = null))
        assertTrue(report.contains("\nProject: (none)"))
        assertTrue("the screen line is independent of the project", report.contains("Project: (none) · Screen: Settings"))
    }

    @Test
    fun `build is pure - the same input twice gives the same string`() {
        val a = FeedbackDraft.build(input(includeLog = true, logTail = listOf("E/Compiler: x", "I/Term: y")))
        val b = FeedbackDraft.build(input(includeLog = true, logTail = listOf("E/Compiler: x", "I/Term: y")))
        assertEquals(a, b)
    }

    // ------------------------------------------------------------------
    // Section presence — the flags are the truth, not the data
    // ------------------------------------------------------------------

    @Test
    fun `an unticked log box leaves no log section even when a tail exists`() {
        val report = FeedbackDraft.build(
            input(includeLog = false, logTail = listOf("[09-10 01:22:11.401] ERROR/Compiler: boom"))
        )
        assertFalse(report.contains("--- log"))
        assertFalse(report.contains("boom"))
    }

    @Test
    fun `an unticked crash box leaves no crash section even when a crash exists`() {
        val crash = "==== 2026-09-10 01:22:11 thread=main ====\n" +
            "java.lang.RuntimeException: forced\n" +
            "    at com.codeci.ide.MainActivity.onCreate(MainActivity.kt:120)"
        val report = FeedbackDraft.build(input(includeCrash = false, crashRecord = crash))
        assertFalse(report.contains("--- crash"))
        assertFalse(report.contains("RuntimeException"))
    }

    @Test
    fun `a ticked log box renders the tail with a truthful line count`() {
        val report = FeedbackDraft.build(
            input(includeLog = true, logTail = listOf("E/A: 1", "I/B: 2"))
        )
        assertTrue(report.contains("--- log (last 2 lines, redacted) ---\nE/A: 1\nI/B: 2"))
    }

    @Test
    fun `a 200-line tail is capped by section at 120, not by accident`() {
        val tail = (1..200).map { "log line $it" }
        val report = FeedbackDraft.build(input(includeLog = true, logTail = tail))
        assertTrue(report.contains("--- log (last 120 lines, redacted) ---"))
        // The NEWEST 120 survive; the oldest 80 are dropped by the section cap.
        assertTrue(report.contains("log line 200"))
        assertTrue(report.contains("log line 81"))
        assertFalse(report.contains("log line 80"))
    }

    @Test
    fun `the crash section carries the header, the exception and at most 12 frames`() {
        val crash = buildString {
            append("==== 2026-09-10 01:22:11 thread=main ====\n")
            append("java.lang.RuntimeException: forced\n")
            for (i in 1..30) append("    at frame$i (File.kt:$i)\n")
        }
        val report = FeedbackDraft.build(input(includeCrash = true, crashRecord = crash))
        assertTrue(report.contains("--- crash ---"))
        assertTrue(report.contains("==== 2026-09-10 01:22:11 thread=main ===="))
        assertTrue(report.contains("java.lang.RuntimeException: forced"))
        assertTrue(report.contains("frame12"))
        assertFalse(report.contains("frame13"))
    }

    // ------------------------------------------------------------------
    // Exit 2 — redaction (secrets and private paths), before any budget
    // ------------------------------------------------------------------

    @Test
    fun `a planted ghp token and a CODEC_GIT_TOKEN line come out redacted`() {
        val tail = listOf(
            "[09-10 01:22:11.401] ERROR/Git: CODEC_GIT_TOKEN=ghp_secret123 rejected",
            "[09-10 01:22:11.402] INFO/Git: using token ghp_AbCdEf1234567890AbCdEf12345678901234"
        )
        val report = FeedbackDraft.build(input(includeLog = true, logTail = tail))
        assertFalse("the planted classic PAT must not survive", report.contains("ghp_secret123"))
        assertFalse("the full PAT must not survive", report.contains("ghp_AbCdEf"))
        assertTrue(report.contains("<redacted>"))
    }

    @Test
    fun `every known token shape is scrubbed`() {
        val redacted = FeedbackDraft.redact(
            listOf(
                "fine-grained github_pat_11AAAAaaa0BBBBbbb1CCCCccc2",
                "server ghs_AAAAaaaaBBBBbbbb11112222",
                "oauth gho_CCCCddddDDDDdddd33334444",
                "x-access-token:ghs_AAAAaaaaBBBBbbbb11112222",
                "Authorization: Bearer eyJhbGciOiJIUzI1NiJ9.sig",
                "client_secret=supersecretvalue123",
                "api_key: 998877665544",
                "curl https://user:pass@github.com/o/r.git"
            )
        )
        val joined = redacted.joinToString("\n")
        assertFalse(joined.contains("github_pat_11AAAA"))
        assertFalse(joined.contains("ghs_AAAA"))
        assertFalse(joined.contains("gho_CCCC"))
        assertFalse(joined.contains("x-access-token:ghs"))
        assertFalse(joined.contains("eyJhbGciOiJIUzI1NiJ9"))
        assertFalse(joined.contains("supersecretvalue123"))
        assertFalse(joined.contains("998877665544"))
        assertFalse(joined.contains("user:pass@"))
        // …and the GitRedactor URL rule replaced the credential pair:
        assertTrue(joined.contains("https://***@github.com/o/r.git"))
    }

    @Test
    fun `the stored literal secret is scrubbed even when it matches no shape`() {
        val redacted = FeedbackDraft.redact(
            listOf("git push failed with 0xA1B2C3D4E5"),
            secret = "0xA1B2C3D4E5"
        )
        assertEquals("git push failed with ***", redacted[0])
    }

    @Test
    fun `private paths are shortened in both data-dir spellings`() {
        val lines = listOf(
            "compile /data/user/0/com.codeci.ide/files/CodeC/projects/hello-c/main.c",
            "exec /data/data/com.codeci.ide/files/usr/bin/git failed",
            "read /data/user/0/com.codeci.ide/files/x.txt",
            "read /data/data/com.codeci.ide/files/CodeC/projects/demo/util.c"
        )
        val redacted = FeedbackDraft.redact(lines)
        assertEquals("compile ~proj/hello-c/main.c", redacted[0])
        assertEquals("exec ~home/bin/git failed", redacted[1])
        assertEquals("read ~app/x.txt", redacted[2])
        assertEquals("read ~proj/demo/util.c", redacted[3])
    }

    @Test
    fun `a custom files dir shortens through the factory`() {
        val paths = FeedbackDraft.RedactionPaths.forApp("/data/data/com.codeci.ide/files")
        val redacted = FeedbackDraft.redact(
            listOf("/data/data/com.codeci.ide/files/usr/lib/libc.so"),
            paths = paths
        )
        assertEquals("~home/lib/libc.so", redacted[0])
    }

    @Test
    fun `redaction runs before the budget so a trim can never keep a secret`() {
        // 200 lines so the section cap at 120 already applies, then a budget
        // that trims further; the secret sits in the NEWEST line, which is
        // the last thing a budget would ever cut.
        val tail = (1..199).map { "log line $it with some filler text to eat budget" } +
            listOf("CODEC_GIT_TOKEN=ghp_secret123")
        val report = FeedbackDraft.build(input(includeLog = true, logTail = tail, maxChars = 600))
        assertFalse("the secret must not survive any budget", report.contains("ghp_secret123"))
        assertTrue(report.contains(FeedbackDraft.LOG_TRIM_NOTICE))
    }

    // ------------------------------------------------------------------
    // Exit 5 — the budget: log first, then crash, never the user's text
    // ------------------------------------------------------------------

    @Test
    fun `over the budget the log shrinks, the notice appears and the text stays`() {
        val tail = (1..120).map { i -> "[09-10 01:22:11.40$i] ERROR/Compiler: filler line $i" }
        val report = FeedbackDraft.build(
            input(
                userText = "app freezes when I open the drawer after rotating the screen",
                includeLog = true,
                logTail = tail,
                maxChars = 1200
            )
        )
        assertTrue(report.length <= 1200)
        assertTrue(report.contains("app freezes when I open the drawer after rotating the screen"))
        assertTrue(report.contains(FeedbackDraft.LOG_TRIM_NOTICE))
        assertTrue("the newest lines are the ones that survive", report.contains("filler line 120"))
    }

    @Test
    fun `the log is trimmed before the crash`() {
        val crash = "==== 2026-09-10 01:22:11 thread=main ====\n" +
            "java.lang.RuntimeException: forced\n" +
            (1..12).joinToString("\n") { "    at frame$it (File.kt:$it)" }
        val tail = (1..120).map { "log line $it padded to a plausible log width for the budget test" }
        // The floor: same report with NO log section and the FULL crash.
        val floor = FeedbackDraft.build(
            input(includeLog = true, logTail = emptyList(), includeCrash = true, crashRecord = crash, maxChars = Int.MAX_VALUE)
        )
        // A budget the floor + the trim notice fits into, but any log line
        // (plus its header + notice) does not.
        val budget = floor.length + FeedbackDraft.LOG_TRIM_NOTICE.length + 3
        val report = FeedbackDraft.build(
            input(includeLog = true, logTail = tail, includeCrash = true, crashRecord = crash, maxChars = budget)
        )
        assertTrue(report.length <= budget)
        // The log was fully cut (only the notice remains), the crash kept all its lines.
        assertTrue(report.contains(FeedbackDraft.LOG_TRIM_NOTICE))
        assertFalse(report.contains("--- log (last"))
        assertTrue(report.contains("--- crash ---"))
        assertTrue(report.contains("frame12"))
        assertFalse(report.contains(FeedbackDraft.CRASH_TRIM_NOTICE))
    }

    @Test
    fun `the crash is trimmed from the bottom when the log alone cannot save it`() {
        val crash = "==== 2026-09-10 01:22:11 thread=main ====\n" +
            "java.lang.RuntimeException: forced\n" +
            (1..12).joinToString("\n") { "    at frame$it (File.kt:$it)" }
        // The floor: header + project + user text, no attachments at all.
        val floor = FeedbackDraft.build(
            input(userText = "x".repeat(100), includeCrash = false, maxChars = Int.MAX_VALUE)
        )
        // A budget with room for the crash section's top (header + exception
        // line + a frame or two + the trim notice) but not its bottom frames.
        val budget = floor.length + 170
        val report = FeedbackDraft.build(
            input(userText = "x".repeat(100), includeCrash = true, crashRecord = crash, maxChars = budget)
        )
        assertTrue(report.length <= budget)
        assertTrue(report.contains(FeedbackDraft.CRASH_TRIM_NOTICE))
        assertTrue("the diagnosis lines are never the ones cut", report.contains("java.lang.RuntimeException: forced"))
        assertFalse(report.contains("frame12"))
    }

    @Test
    fun `the user's text is never cut even when it alone busts the budget`() {
        val longText = "this is a very long description ".repeat(120) // ~3.9 k chars
        val report = FeedbackDraft.build(
            input(
                userText = longText,
                includeLog = true,
                logTail = listOf("log line"),
                maxChars = 1800
            )
        )
        assertTrue("over budget is correct when the human typed a lot", report.length > 1800)
        assertTrue(report.contains(longText.trim()))
    }

    @Test
    fun `no budget means no notices`() {
        val tail = (1..200).map { "log line $it" }
        val report = FeedbackDraft.build(
            input(includeLog = true, logTail = tail, maxChars = Int.MAX_VALUE)
        )
        assertFalse(report.contains(FeedbackDraft.LOG_TRIM_NOTICE))
        assertTrue(report.contains("log line 200"))
        assertTrue(report.contains("--- log (last 120 lines, redacted) ---"))
    }

    // ------------------------------------------------------------------
    // Exit 3 — normaliseNumber (the doc's table, verbatim, plus edges)
    // ------------------------------------------------------------------

    @Test
    fun `normaliseNumber - the exit-condition table`() {
        assertEquals("919876543210", FeedbackDraft.normaliseNumber("+91 98765 43210"))
        assertEquals("447700900123", FeedbackDraft.normaliseNumber("0044-7700-900123"))
        assertNull(FeedbackDraft.normaliseNumber("8-800-555-3535")) // domestic trunk form
        assertNull(FeedbackDraft.normaliseNumber("abc"))
        assertNull(FeedbackDraft.normaliseNumber("12345"))
    }

    @Test
    fun `normaliseNumber - separators, parens and a bare digit string`() {
        assertEquals("15551234567", FeedbackDraft.normaliseNumber("+1 (555) 123-4567"))
        assertEquals("919876543210", FeedbackDraft.normaliseNumber("919876543210"))
        assertEquals("447700900123", FeedbackDraft.normaliseNumber("00 44 7700 900123"))
        assertEquals("919876543210", FeedbackDraft.normaliseNumber("+91.98765.43210"))
    }

    @Test
    fun `normaliseNumber - reject lists`() {
        assertNull(FeedbackDraft.normaliseNumber(""))
        assertNull(FeedbackDraft.normaliseNumber("   "))
        assertNull("words after the number are a paste error", FeedbackDraft.normaliseNumber("+91 98765 43210 home"))
        assertNull("7 digits is under E.164's floor", FeedbackDraft.normaliseNumber("1234567"))
        assertNull("16 digits is over E.164's ceiling", FeedbackDraft.normaliseNumber("1234567890123456"))
        assertNull("a trunk 0 without a country code", FeedbackDraft.normaliseNumber("0919876543210"))
        assertNull("a plus in the middle is not a separator", FeedbackDraft.normaliseNumber("91+9876543210"))
        assertNull("a plus inside parens is the same paste error", FeedbackDraft.normaliseNumber("(+1) 555-123-4567"))
        assertNull(
            "Italian landlines keep a 0 after the country code — rejected on purpose (PART_41_1)",
            FeedbackDraft.normaliseNumber("+390612345678")
        )
    }

    @Test
    fun `normaliseNumber - real-world country codes`() {
        assertEquals("8801712345678", FeedbackDraft.normaliseNumber("+880 1712 345678")) // Bangladesh
        assertEquals("919876543210", FeedbackDraft.normaliseNumber("+91 98765 43210")) // India
        assertEquals("6281234567890", FeedbackDraft.normaliseNumber("+62 812-3456-7890")) // Indonesia
        assertEquals("27821234567", FeedbackDraft.normaliseNumber("+27 82 123 4567")) // South Africa
        assertEquals("971501234567", FeedbackDraft.normaliseNumber("+971 50 123 4567")) // UAE
    }

    // ------------------------------------------------------------------
    // Exit 4 — the links (encoding is the failure mode)
    // ------------------------------------------------------------------

    @Test
    fun `whatsappUrl - the doc's exact case`() {
        assertEquals(
            "https://wa.me/919876543210?text=hi%0Athere%20100%25",
            FeedbackDraft.whatsappUrl("919876543210", "hi\nthere 100%")
        )
    }

    @Test
    fun `whatsappUrl - normalises the number and refuses bad ones`() {
        assertEquals(
            "https://wa.me/919876543210?text=hi",
            FeedbackDraft.whatsappUrl("+91 98765 43210", "hi")
        )
        assertNull(FeedbackDraft.whatsappUrl("12345", "hi"))
        assertNull(FeedbackDraft.whatsappUrl("not-a-number", "hi"))
        assertNull(FeedbackDraft.whatsappUrl("", "hi"))
    }

    @Test
    fun `whatsappUrl - reserved characters, non-latin text and emoji survive encoding`() {
        assertEquals(
            "https://wa.me/919876543210?text=a%26b%23c%3Fd%3De%20f",
            FeedbackDraft.whatsappUrl("919876543210", "a&b#c?d=e f")
        )
        // Devanagari "नमस्ते CodeC"
        assertEquals(
            "https://wa.me/919876543210?text=%E0%A4%A8%E0%A4%AE%E0%A4%B8%E0%A5%8D%E0%A4%A4%E0%A5%87%20CodeC",
            FeedbackDraft.whatsappUrl("919876543210", "नमस्ते CodeC")
        )
        // Bengali "হ্যালো"
        assertEquals(
            "https://wa.me/919876543210?text=%E0%A6%B9%E0%A7%8D%E0%A6%AF%E0%A6%BE%E0%A6%B2%E0%A7%8B",
            FeedbackDraft.whatsappUrl("919876543210", "হ্যালো")
        )
        // Emoji (UTF-8 4-byte sequence)
        assertEquals(
            "https://wa.me/919876543210?text=%F0%9F%91%8D",
            FeedbackDraft.whatsappUrl("919876543210", "👍")
        )
    }

    @Test
    fun `mailto carries an encoded subject and body`() {
        assertEquals(
            "mailto:owner@example.com?subject=CodeC%20feedback&body=line1%0Aline2",
            FeedbackDraft.mailto("owner@example.com", "line1\nline2", subject = "CodeC feedback")
        )
    }

    @Test
    fun `gitHubIssueUrl builds a prefilled new-issue link with both parts encoded`() {
        assertEquals(
            "https://github.com/pabi277/CodeC/issues/new?title=Bug%3A%20crash%20%26%20more&body=body%20with%20%25%20and%20%0A",
            FeedbackDraft.gitHubIssueUrl("pabi277", "CodeC", "Bug: crash & more", "body with % and \n")
        )
    }
}
