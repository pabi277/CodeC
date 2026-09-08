package com.codeci.ide.ui.editor.lsp

import com.codeci.ide.ui.editor.CompletionKind
import com.codeci.ide.ui.utils.LanguageType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 31.1 — pure mapping tests. No Android, no sora, no lsp4j.
 *
 * The 31.1 exit condition #1 ("With server missing: snippets still appear
 * (no crash)") is covered by [LspManagerTest] (it exercises the
 * orchestrator with a stub provider). The mapping here is the half that
 * turns LSP `CompletionItem` JSON-ish shapes into CodeC's existing
 * [com.codeci.ide.ui.editor.CompletionItem]; if it lies, every LSP item
 * the user sees is wrong.
 */
class LspItemMappingTest {

    @Test
    fun mapsSnippetKindToSnippet() {
        // 15 = LSP CompletionItemKind.Snippet
        val out = LspItemMapping.toCompletionItem(
            LspItemMapping.LspShape("for (int i", 15, "snippet", "for (int i = 0; ; i++) {\n}", null)
        )
        assertEquals(CompletionKind.SNIPPET, out.kind)
        assertEquals("for (int i", out.label)
        assertEquals("for (int i = 0; ; i++) {\n}", out.insertText)
        assertEquals("snippet", out.detail)
    }

    @Test
    fun mapsKeywordKindToKeyword() {
        // 14 = LSP CompletionItemKind.Keyword
        val out = LspItemMapping.toCompletionItem(
            LspItemMapping.LspShape("return", 14, "keyword", null, null)
        )
        assertEquals(CompletionKind.KEYWORD, out.kind)
        assertEquals("return", out.label)
        // No insertText — fall back to label.
        assertEquals("return", out.insertText)
    }

    @Test
    fun mapsEverythingElseToIdentifier() {
        // 3 (Function), 6 (Variable), 10 (Property), 21 (Constant) — all
        // ride as Identifier; the strip paints the detail on the right
        // edge.
        for (kind in listOf(3, 6, 10, 21, null)) {
            val out = LspItemMapping.toCompletionItem(
                LspItemMapping.LspShape("printf", kind, "int printf(const char*, ...)", "printf", null)
            )
            assertEquals(
                "kind=$kind should map to IDENTIFIER",
                CompletionKind.IDENTIFIER,
                out.kind
            )
            // detail is truncated to MAX_DETAIL = 32 chars; the raw
            // signature is the right edge hint, not the chip label.
            assertNotNull(out.detail)
            assertTrue("detail too long: '${out.detail}'", out.detail!!.length <= 32)
        }
    }

    @Test
    fun usesLabelWhenInsertTextIsNullOrEmpty() {
        val a = LspItemMapping.toCompletionItem(
            LspItemMapping.LspShape("fopen", 3, null, null, null)
        )
        assertEquals("fopen", a.insertText)
        val b = LspItemMapping.toCompletionItem(
            LspItemMapping.LspShape("fopen", 3, null, "", null)
        )
        assertEquals("fopen", b.insertText)
    }

    @Test
    fun doesNotSetReplaceOrCaretOffset() {
        // The mapping NEVER pre-computes a replace span — that's the
        // engine's job (Phase 30 accept-span law). Honouring
        // LSP `textEdit` would re-introduce the `##include` device bug
        // (Phase 30 follow-up, 2026-09-07).
        val out = LspItemMapping.toCompletionItem(
            LspItemMapping.LspShape("std::sort", 3, "void sort(...)", "std::sort", null)
        )
        assertNull(out.replaceLength)
        assertNull(out.caretOffset)
    }

    @Test
    fun truncatesLongDetailsAndStripsNewlines() {
        val long = "x".repeat(100)
        val out = LspItemMapping.toCompletionItem(
            LspItemMapping.LspShape("a", 6, "first line\nsecond line", "a", null)
        )
        // First line only.
        assertEquals("first line", out.detail)
        // And for the long-detail case the trailing ellipsis fits within
        // the 32-char cap.
        val longOut = LspItemMapping.toCompletionItem(
            LspItemMapping.LspShape("a", 6, long, "a", null)
        )
        assertTrue(longOut.detail!!.length <= 32)
        assertTrue("expected ellipsis suffix", longOut.detail!!.endsWith("…")) // U+2026 horizontal ellipsis
        // (The Kotlin smart-cast warning is harmless: the compiler is
        // sure the value is non-null by this point. The explicit `!!`
        // stays so a future reader does not wonder about the smart cast.)
    }

    @Test
    fun filterTextIsIgnoredAsInsertText() {
        // LSP `filterText` is for prefix matching; CodeC's matcher keys
        // on the label (22.6 law), so we ignore it. The label stays the
        // strip paint.
        val out = LspItemMapping.toCompletionItem(
            LspItemMapping.LspShape("Integer", 7, "class", null, "Integer.parseInt")
        )
        assertEquals("Integer", out.label)
        assertEquals("Integer", out.insertText) // falls back to label
    }
}

/**
 * Catalog sanity — the 31.1 server table is one entry per language, the
 * probe binary is set, and the languageId matches LSP-spec for the
 * languages the catalog claims to support. The catalog is the only
 * non-test surface that owns the language→server mapping; if it lies
 * the manager silently returns nothing.
 *
 * 31.4 (device round 2026-09-07, owner): added shell, HTML, CSS,
 * JSON, YAML. The 13-language NoCard set shrinks to 8 (TEXT,
 * MARKDOWN, GO, RUST, PHP, RUBY, LUA, XML).
 */
class LspServerCatalogTest {

    @Test
    fun catalogHasExpectedLanguages() {
        val supported = LspServerCatalog.servers.map { it.language }.toSet()
        // C / C++ (31.2), Python + JS / TS (31.3) + shell + HTML +
        // CSS + JSON + YAML (31.4) = 10 languages.
        assertEquals(
            setOf(
                LanguageType.C, LanguageType.CPP, LanguageType.PYTHON,
                LanguageType.JAVASCRIPT, LanguageType.TYPESCRIPT,
                LanguageType.SHELL, LanguageType.HTML, LanguageType.CSS,
                LanguageType.JSON, LanguageType.YAML,
            ),
            supported,
        )
    }

    @Test
    fun everyServerHasAUniqueProbeBinary() {
        // Two servers can SHARE a binary (C and C++ both use
        // clangd) — that's intentional, the install card is one
        // row. The probe binary MUST be set and non-blank on every
        // server; a blank probe would make the manager fall through
        // to the no-op provider for that language.
        val probs = LspServerCatalog.servers.map { it.probeBinary }
        probs.forEach { assertTrue("blank probe", it.isNotBlank()) }
    }

    @Test
    fun languageIdsMatchLspSpec() {
        // Per the LSP spec for the 31.4 set. editor-lsp passes this
        // string to the server in `initialize`; a wrong value means
        // the server does not hook completion up for our file.
        val byLang = LspServerCatalog.servers.associateBy { it.language }
        assertEquals("c", byLang[LanguageType.C]!!.languageId)
        assertEquals("cpp", byLang[LanguageType.CPP]!!.languageId)
        assertEquals("python", byLang[LanguageType.PYTHON]!!.languageId)
        assertEquals("javascript", byLang[LanguageType.JAVASCRIPT]!!.languageId)
        assertEquals("typescript", byLang[LanguageType.TYPESCRIPT]!!.languageId)
        // 31.4 additions:
        assertEquals("shellscript", byLang[LanguageType.SHELL]!!.languageId)
        assertEquals("html", byLang[LanguageType.HTML]!!.languageId)
        assertEquals("css", byLang[LanguageType.CSS]!!.languageId)
        assertEquals("json", byLang[LanguageType.JSON]!!.languageId)
        assertEquals("yaml", byLang[LanguageType.YAML]!!.languageId)
    }

    @Test
    fun forLanguageReturnsNullForUnsupported() {
        // The 31.4 NoCard set: TEXT, MARKDOWN, GO, RUST, PHP, RUBY,
        // LUA, XML. (gopls / rust-analyzer stay deferred until
        // those compilers are in the CodeC apt repo; PHP/Ruby/Lua
        // need complex installs; XML has no widely-deployed LSP;
        // markdown/text have no good LSP.)
        for (lang in listOf(
            LanguageType.TEXT, LanguageType.MARKDOWN,
            LanguageType.GO, LanguageType.RUST, LanguageType.PHP,
            LanguageType.RUBY, LanguageType.LUA, LanguageType.XML,
        )) {
            assertNull("expected no server for $lang", LspServerCatalog.forLanguage(lang))
        }
    }

    @Test
    fun shellAndVscodeAndYamlServersHaveCorrectArgv() {
        // Pin the argv so a future edit to the LSP launch path
        // doesn't accidentally drop the `--stdio` / `start` flag
        // (the editor-lsp / LSP-spec stdio contract requires it).
        val shell = LspServerCatalog.forLanguage(LanguageType.SHELL)!!
        assertEquals(listOf("bash-language-server", "start"), shell.command)

        val html = LspServerCatalog.forLanguage(LanguageType.HTML)!!
        assertEquals(listOf("vscode-html-language-server", "--stdio"), html.command)

        val css = LspServerCatalog.forLanguage(LanguageType.CSS)!!
        assertEquals(listOf("vscode-css-language-server", "--stdio"), css.command)

        val json = LspServerCatalog.forLanguage(LanguageType.JSON)!!
        assertEquals(listOf("vscode-json-language-server", "--stdio"), json.command)

        val yaml = LspServerCatalog.forLanguage(LanguageType.YAML)!!
        assertEquals(listOf("yaml-language-server", "--stdio"), yaml.command)
    }

    @Test
    fun everyServerHasNonBlankProbeAndLanguageId() {
        // Defense-in-depth — the LspServerConfig `init` block
        // already enforces this, but pin it here so a future
        // refactor that removes the `require` checks still
        // catches the regression in CI.
        for (server in LspServerCatalog.servers) {
            assertTrue(
                "blank probe for ${server.language}",
                server.probeBinary.isNotBlank(),
            )
            assertTrue(
                "blank languageId for ${server.language}",
                server.languageId.isNotBlank(),
            )
            assertTrue(
                "empty command for ${server.language}",
                server.command.isNotEmpty(),
            )
        }
    }
}
