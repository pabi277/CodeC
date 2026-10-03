package com.codeci.ide

import com.codeci.ide.ui.ai.AiLink
import com.codeci.ide.ui.ai.AiLinkPolicy
import com.codeci.ide.ui.ai.AiLinkRefusal
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 88.2 / Level 11 — model-written links: `https` only, behind a confirm.
 * The allow and deny tables are the brief's (PART_88_2), case for case.
 */
class AiLinkPolicyTest {

    private fun inert(target: String, reason: AiLinkRefusal) =
        assertEquals("'$target'", AiLink.Inert(reason), AiLinkPolicy.classify(target))

    // ---- allowed ----------------------------------------------------------------

    @Test
    fun `a plain https link is openable and keeps every character`() {
        assertEquals(
            AiLink.Openable("https://developer.android.com/x?y=1#z", "developer.android.com"),
            AiLinkPolicy.classify("https://developer.android.com/x?y=1#z")
        )
    }

    @Test
    fun `only the scheme is lowercased and the host is shown lowercase`() {
        assertEquals(
            AiLink.Openable("https://Example.COM/a", "example.com"),
            AiLinkPolicy.classify("HTTPS://Example.COM/a")
        )
    }

    @Test
    fun `a port, punycode and an IP address are still https and still confirmed`() {
        assertEquals(AiLink.Openable("https://example.com:8443/p", "example.com"), AiLinkPolicy.classify("https://example.com:8443/p"))
        // Punycode is shown as punycode: decoding it would invite look-alike hosts.
        assertEquals(
            AiLink.Openable("https://xn--exmple-cua.example/", "xn--exmple-cua.example"),
            AiLinkPolicy.classify("https://xn--exmple-cua.example/")
        )
        assertEquals(AiLink.Openable("https://192.168.1.10/", "192.168.1.10"), AiLinkPolicy.classify("https://192.168.1.10/"))
    }

    @Test
    fun `surrounding ASCII whitespace is trimmed and nothing else changes`() {
        assertEquals(AiLink.Openable("https://a.example/x", "a.example"), AiLinkPolicy.classify("  https://a.example/x \n"))
    }

    // ---- inert --------------------------------------------------------------------

    @Test
    fun `plain http stays inert (owner decision)`() {
        inert("http://example.com", AiLinkRefusal.NOT_HTTPS)
        inert("HTTP://example.com", AiLinkRefusal.NOT_HTTPS)
    }

    @Test
    fun `script schemes stay inert in any case`() {
        inert("javascript:alert(1)", AiLinkRefusal.NOT_HTTPS)
        inert("JavaScript:alert(1)", AiLinkRefusal.NOT_HTTPS)
        inert(" JAVASCRIPT:x", AiLinkRefusal.NOT_HTTPS)
        inert("vbscript:x", AiLinkRefusal.NOT_HTTPS)
    }

    @Test
    fun `data, file, content and intent stay inert`() {
        inert("data:text/html;base64,PHNjcmlwdD5hbGVydCgxKTwvc2NyaXB0Pg==", AiLinkRefusal.NOT_HTTPS)
        inert("data:image/png;base64,iVBORw0KGgo=", AiLinkRefusal.NOT_HTTPS)
        inert("file:///sdcard/x", AiLinkRefusal.NOT_HTTPS)
        inert("content://x/y", AiLinkRefusal.NOT_HTTPS)
        inert("intent://x#Intent;end", AiLinkRefusal.NOT_HTTPS)
    }

    @Test
    fun `other apps' schemes stay inert`() {
        inert("mailto:a@b.example", AiLinkRefusal.NOT_HTTPS)
        inert("tel:123", AiLinkRefusal.NOT_HTTPS)
        inert("ftp://x.example", AiLinkRefusal.NOT_HTTPS)
    }

    @Test
    fun `scheme-relative, relative and anchor targets have no https scheme`() {
        inert("//evil.example/x", AiLinkRefusal.NOT_HTTPS)
        inert("relative/path.md", AiLinkRefusal.NOT_HTTPS)
        inert("#anchor", AiLinkRefusal.NOT_HTTPS)
        inert("", AiLinkRefusal.NOT_HTTPS)
    }

    @Test
    fun `https without a host stays inert`() {
        inert("https://", AiLinkRefusal.NO_HOST)
        inert("https:///path", AiLinkRefusal.NO_HOST)
        inert("https:example.com", AiLinkRefusal.NO_HOST)
    }

    @Test
    fun `user-info stays inert because it exists to deceive`() {
        inert("https://good.example@evil.example/", AiLinkRefusal.USERINFO)
        inert("https://user:pw@host.example/", AiLinkRefusal.USERINFO)
    }

    @Test
    fun `hidden characters stay inert`() {
        inert("https://exa\u200Bmple.com", AiLinkRefusal.HIDDEN_CHARACTERS)
        inert("https://example.com/\u202Egnp.exe", AiLinkRefusal.HIDDEN_CHARACTERS)
        inert("java\tscript:x", AiLinkRefusal.HIDDEN_CHARACTERS)
        inert("https://ex ample.com", AiLinkRefusal.HIDDEN_CHARACTERS)
        inert("https://a.example/\uFEFF", AiLinkRefusal.HIDDEN_CHARACTERS)
        inert("https://a.example/\u2066x", AiLinkRefusal.HIDDEN_CHARACTERS)
        inert("https://a.example/\u0085", AiLinkRefusal.HIDDEN_CHARACTERS)
    }

    @Test
    fun `a host that is not plain ASCII stays inert`() {
        inert("https://exämple.com", AiLinkRefusal.NON_ASCII_HOST)
        inert("https://exam_ple.com/", AiLinkRefusal.NON_ASCII_HOST)
    }

    @Test
    fun `a URL longer than the cap stays inert and the cap itself is allowed`() {
        val base = "https://a.example/"
        val atCap = base + "x".repeat(AiLinkPolicy.MAX_URL_CHARS - base.length)
        assertEquals(AiLinkPolicy.MAX_URL_CHARS, atCap.length)
        assertTrue(AiLinkPolicy.classify(atCap) is AiLink.Openable)
        inert(atCap + "x", AiLinkRefusal.TOO_LONG)
        assertEquals(2_048, AiLinkPolicy.MAX_URL_CHARS)
    }

    @Test
    fun `characters java net URI rejects make the link malformed`() {
        inert("https://a.example/<x>", AiLinkRefusal.MALFORMED)
        inert("https://a.example:port/", AiLinkRefusal.MALFORMED)
    }

    @Test
    fun `the url Open would pass is exactly the one the dialog shows`() {
        // One value object carries both: the dialog renders `url` and `host`, Open passes `url`.
        val link = AiLinkPolicy.classify("HTTPS://Docs.Example/Path?Q=1") as AiLink.Openable
        assertEquals("https://Docs.Example/Path?Q=1", link.url)
        assertEquals("docs.example", link.host)
    }

    @Test
    fun `the policy is pure java net and never android net Uri or the preview allowlist`() {
        val source = File(RepoFiles.mainSource("app/src/main/java/com/codeci/ide/ui/ai"), "AiLevel11Policies.kt").readText()
        val code = RepoFiles.codeOnly(source)
        assertFalse(source.contains("import android"))
        assertFalse(code.contains("android.net"))
        assertFalse(code.contains("safeUrl"))
        assertFalse(code.contains("MarkdownPreview"))
        assertFalse(code.contains("openUri("))
        assertTrue(source.contains("import java.net.URI"))
    }
}
