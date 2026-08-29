package com.codeci.ide

import com.codeci.ide.ui.services.WebPreviewServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/** Pure parts of the preview server: URL decoding, path safety, MIME types. */
class WebPreviewServerTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private fun rootWith(vararg paths: String): File {
        val root = tmp.newFolder("proj")
        paths.forEach { rel ->
            val file = File(root, rel)
            file.parentFile?.mkdirs()
            file.writeText("x:$rel")
        }
        return root
    }

    @Test
    fun `percent decoding accepts escapes and rejects malformed ones`() {
        assertEquals("a b", WebPreviewServer.decodePercent("a%20b"))
        assertEquals("..", WebPreviewServer.decodePercent("%2e%2e"))
        assertEquals("plain", WebPreviewServer.decodePercent("plain"))
        assertNull(WebPreviewServer.decodePercent("trailing%"))
        assertNull(WebPreviewServer.decodePercent("%zz"))
        assertNull(WebPreviewServer.decodePercent("%0a")) // control byte
    }

    @Test
    fun `segments collapse dot-dot without escaping the root`() {
        assertEquals(listOf("css", "app.css"), WebPreviewServer.cleanSegments("/css/app.css"))
        assertEquals(emptyList<String>(), WebPreviewServer.cleanSegments("/"))
        assertEquals(listOf("a", "c"), WebPreviewServer.cleanSegments("/a/./b/../c"))
        assertNull(WebPreviewServer.cleanSegments("/../secret"))
        assertNull(WebPreviewServer.cleanSegments("/%2e%2e/secret")) // encoded traversal
        assertNull(WebPreviewServer.cleanSegments("/..%2fsecret")) // encoded separator
        assertEquals(listOf("page"), WebPreviewServer.cleanSegments("/page?x=1#frag"))
    }

    @Test
    fun `resolves files and index html but never listings`() {
        val root = rootWith("index.html", "css/app.css", "data.json")
        assertEquals(File(root, "index.html").canonicalPath,
            WebPreviewServer.resolveServedFile(root, "/")?.canonicalPath)
        assertEquals(File(root, "css/app.css").canonicalPath,
            WebPreviewServer.resolveServedFile(root, "/css/app.css")?.canonicalPath)
        assertEquals(File(root, "data.json").canonicalPath,
            WebPreviewServer.resolveServedFile(root, "/data.json")?.canonicalPath)
        // encoded + padded forms of the same path
        assertEquals(File(root, "data.json").canonicalPath,
            WebPreviewServer.resolveServedFile(root, "/%64ata.json?cb=1")?.canonicalPath)
        // walking back to the folder itself still yields the index page
        assertEquals(File(root, "index.html").canonicalPath,
            WebPreviewServer.resolveServedFile(root, "index.html/../index.html/../")?.canonicalPath)
        // missing file and directory without an index (no listings) are refused
        assertNull(WebPreviewServer.resolveServedFile(root, "/nope"))
        assertNull(WebPreviewServer.resolveServedFile(root, "/css"))
    }

    @Test
    fun `traversal and absolute paths cannot leave the root`() {
        val secret = File(tmp.root, "secret.txt").apply { writeText("leak") }
        val root = rootWith("index.html")
        assertNull(WebPreviewServer.resolveServedFile(root, "/../secret.txt"))
        assertNull(WebPreviewServer.resolveServedFile(root, "/%2e%2e%2fsecret.txt"))
        assertNull(WebPreviewServer.resolveServedFile(root, "index.html/../../secret.txt"))
        // "absolute" paths are still treated as relative to the served root
        assertNull(WebPreviewServer.resolveServedFile(root, secret.absolutePath))
    }

    @Test
    fun `mime map covers what a preview needs`() {
        assertEquals("text/html; charset=utf-8", WebPreviewServer.contentTypeFor("index.html"))
        assertEquals("text/html; charset=utf-8", WebPreviewServer.contentTypeFor("Index.HTML"))
        assertEquals("text/css; charset=utf-8", WebPreviewServer.contentTypeFor("app.css"))
        assertEquals("text/javascript; charset=utf-8", WebPreviewServer.contentTypeFor("mod.mjs"))
        assertEquals("application/json; charset=utf-8", WebPreviewServer.contentTypeFor("data.json"))
        assertEquals("image/svg+xml", WebPreviewServer.contentTypeFor("logo.svg"))
        assertEquals("application/octet-stream", WebPreviewServer.contentTypeFor("noextension"))
    }

    @Test
    fun `url paths percent-encode unsafe characters per segment`() {
        assertEquals("main.c", WebPreviewServer.urlPathFor("main.c"))
        assertEquals("my%20file.c", WebPreviewServer.urlPathFor("my file.c"))
        assertEquals("css/a%20b/app.css", WebPreviewServer.urlPathFor("css/a b/app.css"))
        assertEquals("app.css", WebPreviewServer.urlPathFor("/app.css"))
    }
}
