package com.codeci.ide.ui.editor.sora

import com.codeci.ide.ui.services.LanguageRegistry
import com.codeci.ide.ui.utils.LanguageType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 29.1/29.2 — the PURE TextMate scope map (no Android, no registry
 * side effects). The companion integration test (TextMateSupportTest)
 * exercises the real assets through Robolectric.
 */
class TextMateGrammarsTest {

    // ---- 29.2 core promise: every run-profile extension has a grammar ----

    @Test
    fun `every language registry extension maps to a textmate scope`() {
        for (profile in LanguageRegistry.profiles) {
            for (ext in profile.extensions) {
                val type = LanguageType.fromFileName("probe.$ext")
                val scope = TextMateGrammars.scopeFor(type, "probe.$ext")
                assertNotNull(
                    "run-profile extension .$ext (${profile.displayName}) must colour " +
                        "(language=$type has no TextMate scope)",
                    scope
                )
            }
        }
    }

    @Test
    fun `every colourable language has a grammar set whose first scope is the root scope`() {
        for (type in LanguageType.entries) {
            val set = TextMateGrammars.grammarSetFor(type, "probe.${type.extensions.firstOrNull() ?: "txt"}")
            if (type == LanguageType.TEXT) {
                assertNull(set)
            } else {
                assertNotNull("language $type must have a grammar set", set)
                assertTrue(set!!.isNotEmpty())
                assertEquals(set.first().scope, TextMateGrammars.scopeFor(type))
            }
        }
    }

    // ---- the ts/tsx disambiguation --------------------------------------

    @Test
    fun `ts and tsx resolve to different grammars in one bucket`() {
        assertEquals("source.ts", TextMateGrammars.scopeFor(LanguageType.TYPESCRIPT, "src/app.ts"))
        assertEquals("source.tsx", TextMateGrammars.scopeFor(LanguageType.TYPESCRIPT, "src/Component.tsx"))
        // The leaf is what matters, not the whole path.
        assertEquals("source.tsx", TextMateGrammars.scopeFor(LanguageType.TYPESCRIPT, "C:\\x\\Component.TSX"))
        // No file name: the bucket default is .ts.
        assertEquals("source.ts", TextMateGrammars.scopeFor(LanguageType.TYPESCRIPT))
    }

    @Test
    fun `grammar sets load their embeds`() {
        // HTML embeds JS and CSS.
        val html = TextMateGrammars.grammarSetFor(LanguageType.HTML, "index.html")!!.map { it.scope }
        assertTrue(html.contains("source.js"))
        assertTrue(html.contains("source.css"))
        // PHP wraps a full HTML document.
        val php = TextMateGrammars.grammarSetFor(LanguageType.PHP, "index.php")!!.map { it.scope }
        assertTrue(php.first() == "text.html.php")
        assertTrue(php.contains("text.html.basic"))
        // YAML roots into its 1.2 sub-grammar + embedded rules.
        val yaml = TextMateGrammars.grammarSetFor(LanguageType.YAML, "ci.yaml")!!.map { it.scope }
        assertTrue(yaml.contains("source.yaml.1.2"))
    }

    // ---- registry hygiene --------------------------------------------------

    @Test
    fun `grammar assets have unique names scopes and paths`() {
        val all = listOf(
            TextMateGrammars.C, TextMateGrammars.CPP, TextMateGrammars.MAGIC_REGEXP,
            TextMateGrammars.PYTHON, TextMateGrammars.JAVASCRIPT, TextMateGrammars.TYPESCRIPT,
            TextMateGrammars.TSX, TextMateGrammars.HTML, TextMateGrammars.HTML_DERIVATIVE,
            TextMateGrammars.CSS, TextMateGrammars.JSON, TextMateGrammars.JSONC,
            TextMateGrammars.SHELL, TextMateGrammars.MARKDOWN, TextMateGrammars.GO,
            TextMateGrammars.RUST, TextMateGrammars.PHP, TextMateGrammars.PHP_HTML,
            TextMateGrammars.RUBY, TextMateGrammars.LUA, TextMateGrammars.XML,
            TextMateGrammars.YAML, TextMateGrammars.YAML_1_2, TextMateGrammars.YAML_EMBEDDED
        )
        assertEquals(all.size, all.map { it.name }.toSet().size)
        assertEquals(all.size, all.map { it.scope }.toSet().size)
        assertEquals(all.size, all.map { it.path }.toSet().size)
        // Every grammar asset is JSON (IGrammarSource sniffs the extension).
        assertTrue(all.all { it.path.endsWith(".json") })
    }

    @Test
    fun `text files have no grammar`() {
        assertNull(TextMateGrammars.scopeFor(LanguageType.TEXT, "notes.txt"))
        assertNull(TextMateGrammars.grammarSetFor(LanguageType.TEXT, "notes.txt"))
    }

    @Test
    fun `warm up covers the core set and defers the heavy embed chains`() {
        val warm = TextMateGrammars.warmUpLanguages
        // 29.1 T3 core set: C, C++, Python, JS, TS, HTML, CSS, JSON, Shell, Markdown.
        listOf(
            LanguageType.C, LanguageType.CPP, LanguageType.PYTHON, LanguageType.JAVASCRIPT,
            LanguageType.TYPESCRIPT, LanguageType.HTML, LanguageType.CSS, LanguageType.JSON,
            LanguageType.SHELL, LanguageType.MARKDOWN
        ).forEach { assertTrue("warm-up must cover $it", it in warm) }
        assertEquals(warm.size, warm.toSet().size)
        // PHP and Ruby load lazily (their embed chains are the heaviest).
        assertTrue(LanguageType.PHP !in warm)
        assertTrue(LanguageType.RUBY !in warm)
    }
}
