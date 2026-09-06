package com.codeci.ide.ui.editor.snippets

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.codeci.ide.ui.editor.CompletionKind
import com.codeci.ide.ui.utils.LanguageType
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Phase 30.1 — the snippet library on the JVM, through the REAL vendored APK
 * assets (Robolectric), mirroring `TextMateSupportTest`.
 *
 * This is the host-testable stand-in for the device round: it proves every
 * mapped asset ships and parses, the packs resolve to completion items in the
 * ballpark the plan expects, the caches behave, a file name really reaches the
 * `${TM_*}` variables, and a missing pack degrades to "no snippets" instead of
 * throwing inside a keystroke.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SnippetLibraryTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Before
    fun setUp() {
        // Robolectric hands out a fresh Application (and AssetManager) per test
        // class; start from "no packs" so a previous test cannot leak in.
        SnippetLibrary.reset()
        SnippetLibrary.attach(context)
    }

    @After
    fun tearDown() {
        SnippetLibrary.reset()
    }

    // ---- the assets really ship -------------------------------------------

    @Test
    fun `every mapped pack asset exists and is strict json`() {
        val all = SnippetAssets.all
        assertTrue("the vendor script must have shipped packs", all.size >= 20)
        for (asset in all) {
            val json = context.assets.open(asset.path).bufferedReader().use { it.readText() }
            assertTrue("asset ${asset.path} must not be empty", json.isNotBlank())
            val node = SnippetJson.parse(json)
            assertNotNull("asset ${asset.path} must parse as a JSON object", node)
            assertTrue(
                "asset ${asset.path} must be an object of snippets",
                (node as? JsonValue.Obj)?.entries?.isNotEmpty() == true
            )
        }
    }

    @Test
    fun `every language pack path is reachable through the library`() {
        for (language in LanguageType.entries) {
            if (SnippetAssets.packsFor(language).isEmpty()) continue
            val items = SnippetLibrary.snippetsFor(language)
            assertTrue("$language must produce snippets", items.isNotEmpty())
        }
    }

    // ---- resolved items ----------------------------------------------------

    @Test
    fun `packs replace the hand written tables by a wide margin`() {
        // Pre-30 the built-in tables offered 7 (C), 9 (Python), 8 (HTML) and
        // 3 (CSS) snippets. The plan's exit condition is that the vendored
        // packs dwarf that, so these floors are deliberately generous: they
        // catch a truncated or mis-vendored asset without breaking whenever
        // upstream adds a snippet.
        assertTrue("C", SnippetLibrary.snippetsFor(LanguageType.C).size > 40)
        assertTrue("Python", SnippetLibrary.snippetsFor(LanguageType.PYTHON).size > 40)
        assertTrue("HTML", SnippetLibrary.snippetsFor(LanguageType.HTML).size > 40)
        assertTrue("CSS", SnippetLibrary.snippetsFor(LanguageType.CSS).size > 40)
        assertTrue(
            "JavaScript",
            SnippetLibrary.snippetsFor(LanguageType.JAVASCRIPT).size > 100
        )
    }

    @Test
    fun `items are snippets with distinct labels and no empty inserts`() {
        val items = SnippetLibrary.snippetsFor(LanguageType.C)
        assertTrue(items.all { it.kind == CompletionKind.SNIPPET })
        assertEquals(items.size, items.map { it.label }.distinct().size)
        assertTrue(items.all { it.label.isNotBlank() })
        assertTrue(items.all { it.insertText.isNotBlank() })
        assertTrue("details are chip-sized", items.all { (it.detail ?: "").length <= 64 })
        // The everyday C snippet is in there.
        assertTrue(items.any { it.label == "for" })
        // Labels are the PACK PREFIXES ("#inc", not "#include <stdio.h>").
        assertTrue(items.any { it.label == "#inc" })
        assertTrue(items.any { it.label == "main" })
    }

    @Test
    fun `languages that ship no pack stay empty`() {
        // Plan rule S4: JSON/TEXT get none (the engine returns nothing for them
        // anyway); XML/YAML have no upstream pack worth its weight.
        for (language in listOf(
            LanguageType.JSON, LanguageType.TEXT, LanguageType.XML, LanguageType.YAML
        )) {
            assertTrue(
                "$language must not produce pack snippets",
                SnippetLibrary.snippetsFor(language).isEmpty()
            )
        }
    }

    // ---- caching ------------------------------------------------------------

    @Test
    fun `repeated lookups return the cached list instance`() {
        val first = SnippetLibrary.snippetsFor(LanguageType.PYTHON)
        val second = SnippetLibrary.snippetsFor(LanguageType.PYTHON)
        assertSame(first, second)
        assertTrue(SnippetLibrary.loadedLanguages().contains(LanguageType.PYTHON))
    }

    @Test
    fun `the file name is part of the cache key`() {
        // `${TM_FILENAME_BASE}` resolution depends on the open file, so two
        // files of one language must not share a resolved list.
        val a = SnippetLibrary.snippetsFor(LanguageType.CPP, "proj/inc/alpha.h")
        val b = SnippetLibrary.snippetsFor(LanguageType.CPP, "proj/inc/beta.h")
        assertFalse(a === b)
        assertTrue(
            "alpha guard: ${a.firstOrNull { it.label == "#guard" }?.insertText}",
            a.first { it.label == "#guard" }.insertText.contains("ALPHA")
        )
        assertTrue(
            "beta guard: ${b.first { it.label == "#guard" }.insertText}",
            b.first { it.label == "#guard" }.insertText.contains("BETA")
        )
        assertFalse(
            "the beta list must not carry alpha's guard",
            b.first { it.label == "#guard" }.insertText.contains("ALPHA")
        )
    }

    @Test
    fun `tm variables reach the real pack bodies`() {
        // cpp.json `#guard`: INCLUDE_<dirname>_<basename>_<extension>_
        val items = SnippetLibrary.snippetsFor(LanguageType.CPP, "proj/inc/thing.h")
        val guard = items.firstOrNull { it.label == "#guard" }
        assertNotNull("cpp.json must ship #guard", guard)
        assertTrue(
            "guard body: ${guard!!.insertText}",
            guard.insertText.contains("#ifndef INCLUDE_INC_THING_H_")
        )
        assertTrue(guard.insertText.contains("#endif"))
        // A file at the project root simply loses the <dirname> segment.
        val root = SnippetLibrary.snippetsFor(LanguageType.CPP, "thing.h")
        assertTrue(
            root.first { it.label == "#guard" }.insertText.contains("#ifndef INCLUDE_THING_H_")
        )
    }

    @Test
    fun `warm up parses packs in the background without resolving them`() {
        SnippetLibrary.reset()
        SnippetLibrary.warmUp(context, listOf(LanguageType.C, LanguageType.HTML))
        assertEquals(
            setOf(LanguageType.C, LanguageType.HTML),
            SnippetLibrary.loadedLanguages()
        )
        // …and the first lookup afterwards is a cache hit that still resolves.
        assertTrue(SnippetLibrary.snippetsFor(LanguageType.C).isNotEmpty())
    }

    @Test
    fun `warm up skips languages that ship no pack`() {
        SnippetLibrary.reset()
        SnippetLibrary.warmUp(context, listOf(LanguageType.JSON, LanguageType.TEXT))
        assertTrue(SnippetLibrary.loadedLanguages().isEmpty())
    }

    // ---- degradation --------------------------------------------------------

    @Test
    fun `attach is idempotent for the same asset manager`() {
        val before = SnippetLibrary.snippetsFor(LanguageType.C)
        SnippetLibrary.attach(context)
        assertSame("a second attach must not drop the cache", before, SnippetLibrary.snippetsFor(LanguageType.C))
        assertTrue(SnippetLibrary.isInstalled())
    }

    @Test
    fun `a reader that fails every pack degrades to no snippets`() {
        SnippetLibrary.install { null }
        assertTrue(SnippetLibrary.isInstalled())
        assertTrue(SnippetLibrary.snippetsFor(LanguageType.C).isEmpty())
        // The total failure is NOT cached, so a reset + attach recovers. (A
        // bare `attach` is deliberately a no-op here: the AssetManager has not
        // changed, and re-installing on every call would defeat idempotence —
        // `install` is a test seam, never used on a device.)
        SnippetLibrary.reset()
        SnippetLibrary.attach(context)
        assertTrue(SnippetLibrary.snippetsFor(LanguageType.C).isNotEmpty())
    }

    @Test
    fun `a transient read failure is retried on the next keystroke`() {
        var broken = true
        SnippetLibrary.install { path -> if (broken) null else readAsset(path) }
        assertTrue(SnippetLibrary.snippetsFor(LanguageType.C).isEmpty())
        broken = false
        // No reset needed: an all-failed read is not put in the entries cache.
        assertTrue(SnippetLibrary.snippetsFor(LanguageType.C).isNotEmpty())
    }

    @Test
    fun `a malformed pack is skipped, the rest still load`() {
        SnippetLibrary.install { path ->
            if (path.endsWith("cdoc.json")) "{ this is not json" else null
        }
        // Everything unreadable → no items, no crash.
        assertTrue(SnippetLibrary.snippetsFor(LanguageType.C).isEmpty())
        SnippetLibrary.reset()
        SnippetLibrary.install { path ->
            if (path.endsWith("c/c.json")) "{ this is not json" else readAsset(path)
        }
        // The doc pack still contributes, so C is not left empty.
        assertTrue(SnippetLibrary.snippetsFor(LanguageType.C).isNotEmpty())
    }

    @Test
    fun `reset returns to the built-in-table world`() {
        assertTrue(SnippetLibrary.snippetsFor(LanguageType.C).isNotEmpty())
        SnippetLibrary.reset()
        assertFalse(SnippetLibrary.isInstalled())
        assertTrue(SnippetLibrary.snippetsFor(LanguageType.C).isEmpty())
        assertTrue(SnippetLibrary.loadedLanguages().isEmpty())
    }

    private fun readAsset(path: String): String? = try {
        context.assets.open(path).bufferedReader().use { it.readText() }
    } catch (e: java.io.IOException) {
        null
    }
}
