package com.codeci.ide.ui.editor.lsp

import com.codeci.ide.ui.utils.LanguageType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 31 server catalog — the table the [LspManager] uses to
 * spawn stdio LSP servers per language.
 *
 * The catalog is a `val` so the host tests exercise the same
 * table the app ships. The contract is:
 *   - every server has a non-blank probe binary (the manager
 *     probes for it before spawn — fast, no 60 s hang);
 *   - every server has a non-empty argv list;
 *   - every server has an LSP-spec `languageId`;
 *   - the probe binary is unique per server row (no two servers
 *     accidentally share a binary — a future "one language,
 *     multiple servers" entry would break that contract);
 *   - the catalog covers exactly the languages the IntelliSense
 *     cards advertise (lock-step with [IntelliSenseCatalog]).
 */
class LspServerCatalogTest {

    @Test
    fun catalogHasExpectedLanguages() {
        // The 10-language set is the 31.4 device-round scope: 5 from
        // 31.1–31.3 (C, C++, Python, JS, TS) + 5 from 31.4 (shell,
        // HTML, CSS, JSON, YAML). Future cards (gopls, rust-analyzer,
        // PHP, Ruby, Lua) go here when their servers are added.
        val expected = setOf(
            LanguageType.C, LanguageType.CPP, LanguageType.PYTHON,
            LanguageType.JAVASCRIPT, LanguageType.TYPESCRIPT,
            LanguageType.SHELL, LanguageType.HTML, LanguageType.CSS,
            LanguageType.JSON, LanguageType.YAML,
        )
        val actual = LspServerCatalog.servers.map { it.language }.toSet()
        assertEquals(expected, actual)
    }

    @Test
    fun everyServerHasAUniqueProbeBinary() {
        // No two servers share a probe binary. (Two servers CAN
        // share an argv first word — e.g. both C and CPP use
        // `clangd` — but the per-language row is what the manager
        // consults. The probe is per-row.)
        val probes = LspServerCatalog.servers.map { it.probeBinary }
        assertEquals(
            "probe binaries must be unique per server row",
            probes.size,
            probes.toSet().size,
        )
    }

    @Test
    fun languageIdsMatchLspSpec() {
        // The `languageId` is what sora's editor-lsp sends to the
        // server in the LSP `initialize` handshake — it must match
        // the LSP-spec string the server expects, or the server
        // refuses to scope its index to the file.
        val byLanguage = LspServerCatalog.servers.associate { it.language to it.languageId }
        assertEquals("c", byLanguage[LanguageType.C])
        assertEquals("cpp", byLanguage[LanguageType.CPP])
        assertEquals("python", byLanguage[LanguageType.PYTHON])
        assertEquals("javascript", byLanguage[LanguageType.JAVASCRIPT])
        assertEquals("typescript", byLanguage[LanguageType.TYPESCRIPT])
        // 31.4 additions:
        assertEquals("shellscript", byLanguage[LanguageType.SHELL])
        assertEquals("html", byLanguage[LanguageType.HTML])
        assertEquals("css", byLanguage[LanguageType.CSS])
        assertEquals("json", byLanguage[LanguageType.JSON])
        assertEquals("yaml", byLanguage[LanguageType.YAML])
    }

    @Test
    fun forLanguageReturnsNullForUnsupported() {
        // TEXT, MARKDOWN, GO, RUST, PHP, RUBY, LUA, XML — no
        // server config. The manager returns the no-op provider
        // and the editor's completion strip falls back to the
        // Phase 30 snippet world.
        for (lang in listOf(
            LanguageType.TEXT, LanguageType.MARKDOWN,
            LanguageType.GO, LanguageType.RUST, LanguageType.PHP,
            LanguageType.RUBY, LanguageType.LUA, LanguageType.XML,
        )) {
            assertEquals(
                "expected null for $lang",
                null,
                LspServerCatalog.forLanguage(lang),
            )
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
            assertNotNull(server.command)
            assertTrue(
                "empty command for ${server.language}",
                server.command.isNotEmpty(),
            )
        }
    }
}
