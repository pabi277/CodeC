package com.codeci.ide.ui.editor.snippets

import com.codeci.ide.ui.editor.CompletionKind
import com.codeci.ide.ui.utils.LanguageType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 30.1 — pack parsing and the entry → [com.codeci.ide.ui.editor.CompletionItem]
 * mapping, plus the pure asset map ([SnippetAssets]).
 *
 * The Robolectric `SnippetLibraryTest` runs the same pipeline against the REAL
 * vendored assets; this file pins the shapes with inline JSON so a parser
 * regression is caught without an APK.
 */
class SnippetPacksTest {

    private val packJson = """
    {
      "main() template": {
        "prefix": "main",
        "body": ["int main(int argc, char *argv[])", "{$0", "\treturn 0;", "}"],
        "description": "Standard main() snippet"
      },
      "For Loop": {
        "prefix": ["for", "fori"],
        "body": "for (int ${'$'}{1:i} = 0; $1 < ${'$'}{2:n}; $1++) {\n\t$0\n}"
      },
      "Choice": {
        "prefix": "sel",
        "body": "x: ${'$'}{1|a,b,c|};",
        "description": ["line one", "line two"]
      },
      "No prefix": { "body": "orphan" },
      "No body": { "prefix": "nobody" },
      "Blank body": { "prefix": "blank", "body": "   " }
    }
    """.trimIndent()

    @Test
    fun `entries keep prefix, body and description shapes`() {
        val entries = SnippetPacks.parse(packJson)
        // "No prefix" and "No body" are dropped here; a whitespace-only body
        // survives parsing and is dropped when items are built.
        assertEquals(4, entries.size)
        val main = entries.first { it.name == "main() template" }
        assertEquals(listOf("main"), main.prefixes)
        assertEquals("Standard main() snippet", main.description)
        // An array body joins with newlines; a scalar body stays as it is.
        assertTrue(main.body.startsWith("int main(int argc, char *argv[])\n{"))
        // An array prefix becomes one item per prefix (capped below).
        assertEquals(listOf("for", "fori"), entries.first { it.name == "For Loop" }.prefixes)
        // An array description joins into one line.
        assertEquals("line one line two", entries.first { it.name == "Choice" }.description)
    }

    @Test
    fun `entries without a usable prefix or body are dropped`() {
        val names = SnippetPacks.parse(packJson).map { it.name }
        assertFalse("No prefix" in names)
        assertFalse("No body" in names)
        // An empty or blank body yields no completion item (nothing to insert).
        assertTrue(itemsOf("""{"x":{"prefix":"x","body":""}}""").isEmpty())
        assertTrue(itemsOf("""{"x":{"prefix":"x","body":"   "}}""").isEmpty())
        // A pack that is not JSON at all degrades to no snippets.
        assertTrue(SnippetPacks.parse("{ not json at all").isEmpty())
        assertTrue(SnippetPacks.parse("").isEmpty())
        assertTrue(itemsOf("{ not json at all").isEmpty())
    }

    private fun itemsOf(json: String) = SnippetPacks.items(SnippetPacks.parse(json))

    @Test
    fun `items resolve the body and park the caret on the first stop`() {
        val items = SnippetPacks.items(SnippetPacks.parse(packJson))
        val main = items.first { it.label == "main" }
        assertEquals(CompletionKind.SNIPPET, main.kind)
        assertEquals("int main(int argc, char *argv[])\n{\n    return 0;\n}", main.insertText)
        assertEquals("Standard main() snippet", main.detail)
        // The body's only stop is $0, right after the opening brace.
        assertEquals(main.insertText.indexOf("{") + 1, main.caretOffset)
        val loop = items.first { it.label == "for" }
        assertEquals("for (int i = 0; i < n; i++) {\n    \n}", loop.insertText)
        // The LOWEST POSITIVE stop wins ($1, the loop counter's initial value);
        // $0 is only where VS Code would end up after tabbing through.
        assertEquals(loop.insertText.indexOf("i = 0"), loop.caretOffset)
        assertEquals(9, loop.caretOffset)
        // No description → the first body line is the detail.
        assertEquals("for (int i = 0; i < n; i++) {", loop.detail)
    }

    @Test
    fun `one item per prefix and every label is distinct`() {
        val items = SnippetPacks.items(SnippetPacks.parse(packJson))
        assertTrue(items.any { it.label == "for" })
        assertTrue(items.any { it.label == "fori" })
        assertEquals(items.size, items.map { it.label }.distinct().size)
        // The choice resolves to its first alternative.
        assertEquals("x: a;", items.first { it.label == "sel" }.insertText)
        // A whitespace-only body produces nothing.
        assertFalse(items.any { it.label == "blank" })
    }

    @Test
    fun `the first entry claiming a prefix wins (duplicate collapse)`() {
        val json = """
        {
          "first": {"prefix": "dup", "body": "FIRST"},
          "second": {"prefix": "dup", "body": "SECOND"}
        }
        """.trimIndent()
        val items = SnippetPacks.items(SnippetPacks.parse(json))
        assertEquals(1, items.size)
        assertEquals("FIRST", items[0].insertText)
    }

    @Test
    fun `long prefixes are capped so one entry cannot flood the strip`() {
        val json = """{"many":{"prefix":["a","b","c","d","e"],"body":"x"}}"""
        val items = SnippetPacks.items(SnippetPacks.parse(json))
        assertEquals(SnippetPacks.MAX_PREFIXES_PER_ENTRY, items.size)
    }

    @Test
    fun `long details are cut to one line for chips and panel rows`() {
        val long = "x".repeat(SnippetPacks.DETAIL_MAX + 40)
        val items = SnippetPacks.items(
            SnippetPacks.parse("""{"n":{"prefix":"n","body":"$long","description":"$long"}}""")
        )
        assertTrue(items[0].detail!!.length <= SnippetPacks.DETAIL_MAX)
        assertTrue(items[0].detail!!.endsWith("…"))
        assertFalse(items[0].detail!!.contains("\n"))
    }

    @Test
    fun `the file name reaches TM_ variables`() {
        val items = SnippetPacks.items(
            SnippetPacks.parse("""{"n":{"prefix":"n","body":"f=${'$'}{TM_FILENAME_BASE}"}}"""),
            "src/util.py"
        )
        assertEquals("f=util", items[0].insertText)
    }

    // ---- the pure asset map ----------------------------------------------

    @Test
    fun `every language either ships packs or is deliberately empty`() {
        val withPacks = LanguageType.entries.filter { SnippetAssets.packsFor(it).isNotEmpty() }
        val without = LanguageType.entries.filter { SnippetAssets.packsFor(it).isEmpty() }
        // Plan rule S4: JSON/TEXT get none; XML/YAML have no upstream pack.
        assertEquals(
            setOf(LanguageType.JSON, LanguageType.XML, LanguageType.YAML, LanguageType.TEXT),
            without.toSet()
        )
        assertTrue("the run-profile languages all have packs", withPacks.size >= 14)
        for (language in withPacks) {
            assertTrue("$language has at least one pack", SnippetAssets.packsFor(language).isNotEmpty())
        }
    }

    @Test
    fun `pack paths live under the snippets asset dir and are unique`() {
        val paths = SnippetAssets.all.map { it.path }
        assertEquals(paths.size, paths.distinct().size)
        assertTrue(paths.all { it.startsWith(SnippetAssets.DIR + "/") })
        assertTrue(paths.all { it.endsWith(".json") })
    }

    @Test
    fun `the main pack of a language is listed first (rank tie-breaker)`() {
        assertEquals("snippets/c/c.json", SnippetAssets.packsFor(LanguageType.C).first().path)
        assertEquals(
            "snippets/python/python.json",
            SnippetAssets.packsFor(LanguageType.PYTHON).first().path
        )
        assertEquals(
            "snippets/javascript/javascript.json",
            SnippetAssets.packsFor(LanguageType.JAVASCRIPT).first().path
        )
        assertEquals(
            "snippets/javascript/typescript.json",
            SnippetAssets.packsFor(LanguageType.TYPESCRIPT).first().path
        )
    }

    @Test
    fun `warm-up only lists languages that ship packs`() {
        assertTrue(
            SnippetAssets.warmUpLanguages.all { SnippetAssets.packsFor(it).isNotEmpty() }
        )
        assertTrue(SnippetAssets.warmUpLanguages.contains(LanguageType.C))
        assertTrue(SnippetAssets.warmUpLanguages.contains(LanguageType.HTML))
    }
}
