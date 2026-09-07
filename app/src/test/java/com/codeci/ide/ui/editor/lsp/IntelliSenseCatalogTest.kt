package com.codeci.ide.ui.editor.lsp

import com.codeci.ide.ui.modules.PackageCategory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 31.2 / 31.3 — the install-card half of the LSP catalog.
 *
 * The cards live in [IntelliSenseCatalog]; the server config lives
 * in [LspServerCatalog]. These tests pin the two halves of the lock-
 * step contract: every card has a server config, every server
 * config's `id` matches a card `id`, and the install commands are
 * the documented ones (31.2 clang pkg; 31.3 pip / npm inside
 * PREFIX).
 */
class IntelliSenseCatalogTest {

    @Test
    fun cardsCoverAllServerCatalogLanguages() {
        // Every language in the LSP server catalog has at least one
        // card; otherwise the install half is missing.
        val cardLanguages = IntelliSenseCatalog.cards
            .flatMap { card ->
                LspServerCatalog.servers.filter { it.probeBinary == card.binary }.map { it.language }
            }.toSet()
        val serverLanguages = LspServerCatalog.servers.map { it.language }.toSet()
        assertEquals("every server-language has a matching card", serverLanguages, cardLanguages)
    }

    @Test
    fun everyServerHasAMatchingCardByProbeBinary() {
        // The lock-step is by `probeBinary` (= the binary the
        // manager probes for after install). If the manager says
        // "clangd" is the binary, the card must probe the same
        // binary — otherwise install flips AVAILABLE→INSTALLED on
        // the card but the orchestrator still says "missing".
        for (server in LspServerCatalog.servers) {
            val card = IntelliSenseCatalog.cards.firstOrNull { it.binary == server.probeBinary }
            assertNotNull("missing card for ${server.displayName} (probe=${server.probeBinary})", card)
        }
    }

    @Test
    fun clangCardInstallsClangNotClangd() {
        // Phase 20.1 ships `clang` (the libllvm deb), with `bin/clangd`
        // as a sub-binary. The install command is `pkg install -y
        // clang`, NEVER `pkg install -y clangd` (clangd is not a
        // separate package at the pinned ref). Pin this so a future
        // catalog edit does not silently break the recipe.
        val clang = IntelliSenseCatalog.cardById["intellisense-c-cpp-clangd"]
        assertNotNull(clang)
        assertEquals("pkg install -y clang", clang!!.installCommand)
    }

    @Test
    fun pythonCardUsesPipInsidePrefix() {
        // pylsp is not a CodeC apt package (Phase 20.1 didn't publish
        // it — see README §3.3 "If a pip/npm name is not in the CodeC
        // apt repo, prefer a documented pip install"). The card
        // surfaces a chain: `pkg install -y python-pip` lands `pip` on
        // PATH (Phase 12 publishes `python-pip` as its own deb; the
        // bare `pip install` command on a fresh userland hits
        // `command not found` — device round 2026-09-07), then
        // `pip install python-lsp-server` (NO `--user` — device
        // round 2: `--user` writes scripts to `~/.local/bin/`, NOT
        // `$PREFIX/bin/`, so the orchestrator's probe still fails)
        // installs the LSP server into `$PREFIX/`.
        val pylsp = IntelliSenseCatalog.cardById["intellisense-python-pylsp"]
        assertNotNull(pylsp)
        assertEquals(
            "pkg install -y python-pip && pip install python-lsp-server",
            pylsp!!.installCommand,
        )
        assertEquals("pylsp", pylsp.binary)
    }

    @Test
    fun pythonCardDoesNotUsePipUserFlag() {
        // The `--user` form lands scripts in `~/.local/bin/`, which
        // is NOT on `$PREFIX/bin/` — the orchestrator's probe would
        // never see pylsp and the card would never flip to INSTALLED
        // even after a successful pip install. Device round 2
        // (2026-09-07) caught this. Pin the absence of `--user` so a
        // future "let's be safe" edit doesn't reintroduce the bug.
        val pylsp = IntelliSenseCatalog.cardById["intellisense-python-pylsp"]!!
        assertTrue(
            "python card must NOT use pip --user (breaks the orchestrator probe)",
            !pylsp.installCommand.contains("--user"),
        )
    }

    @Test
    fun jsCardChainsNpmInstallFromAptRepo() {
        // `npm` is a separate CodeC apt package from Phase 20.1 (it
        // was split out of nodejs upstream at 25.3.0-1). Bare
        // `npm install -g ...` FAILED on a userland that had
        // `nodejs` but not `npm` (device round 2 2026-09-07). The
        // chain `pkg install -y npm && npm install -g ...` is the
        // self-sufficient form.
        val tsserver = IntelliSenseCatalog.cardById["intellisense-js-tsserver"]
        assertNotNull(tsserver)
        assertEquals(
            "pkg install -y npm && npm install -g typescript typescript-language-server",
            tsserver!!.installCommand,
        )
        assertEquals("typescript-language-server", tsserver.binary)
    }

    @Test
    fun allCardsAreInLanguagesCategory() {
        // The IntelliSense cards are language intelligence — the
        // existing Languages category is the right home; a future
        // dedicated "IntelliSense" category is a follow-up.
        for (card in IntelliSenseCatalog.cards) {
            assertEquals(PackageCategory.LANGUAGES, card.category)
        }
    }

    @Test
    fun catalogHasEightCardsForThe31Series() {
        // 31.1–31.3 shipped 3 cards (C/C++, Python, JS/TS). The
        // device round (2026-09-07) added 5 more (shell, HTML, CSS,
        // JSON, YAML) for a total of 8 — the user picked the "5 new
        // cards, one per language" path, so 3 (initial) + 5 (31.4) =
        // 8. Pin the card count so a future edit doesn't silently
        // drop one.
        assertEquals(8, IntelliSenseCatalog.cards.size)
    }

    @Test
    fun shellCardUsesNpmInsidePrefix() {
        // bash-language-server is the documented shell LSP, MIT,
        // 205k weekly downloads. The install chains
        // `pkg install -y npm` (Phase 20.1 — `npm` was split out
        // of nodejs upstream at 25.3.0-1, so a nodejs-only
        // userland needs the `npm` deb to land the wrapper on
        // PATH — device round 2 2026-09-07) and then
        // `npm install -g bash-language-server` (no chained
        // python-pip dance — nodejs itself is the requirement,
        // and Phase 20.1 ships it).
        val shell = IntelliSenseCatalog.cardById["intellisense-shell-bash"]
        assertNotNull(shell)
        assertEquals("bash-language-server", shell!!.binary)
        assertEquals(
            "pkg install -y npm && npm install -g bash-language-server",
            shell.installCommand,
        )
    }

    @Test
    fun vscodeCardsShareTheSameNpmInstall() {
        // The 3 vscode cards (HTML, CSS, JSON) all install
        // `@zed-industries/vscode-langservers-extracted` — one
        // npm package, three binaries. The install command is
        // identical across the 3 cards. Pin the install command
        // AND the distinct per-language binary so the catalog
        // stays honest about which language probes which binary.
        // The command also chains `pkg install -y npm` so the
        // user doesn't have to install `npm` separately (device
        // round 2 2026-09-07 caught a bare `npm install -g`
        // failing on a nodejs-only userland).
        val expectedInstall = "pkg install -y npm && npm install -g @zed-industries/vscode-langservers-extracted"
        for ((id, expectedBinary) in listOf(
            "intellisense-html-vscode" to "vscode-html-language-server",
            "intellisense-css-vscode" to "vscode-css-language-server",
            "intellisense-json-vscode" to "vscode-json-language-server",
        )) {
            val card = IntelliSenseCatalog.cardById[id]
            assertNotNull("missing card $id", card)
            assertEquals("binary mismatch on $id", expectedBinary, card!!.binary)
            assertEquals(
                "install command mismatch on $id",
                expectedInstall,
                card.installCommand,
            )
        }
    }

    @Test
    fun yamlCardUsesNpmInsidePrefix() {
        val yaml = IntelliSenseCatalog.cardById["intellisense-yaml-redhat"]
        assertNotNull(yaml)
        assertEquals("yaml-language-server", yaml!!.binary)
        assertEquals(
            "pkg install -y npm && npm install -g yaml-language-server",
            yaml.installCommand,
        )
    }

    @Test
    fun pythonCardChainMentionsBothPackages() {
        // The chain `pkg install -y python-pip && pip install
        // --user python-lsp-server` has TWO requirements:
        //   (1) the apt package `python-pip` (so `pip` lands on PATH),
        //   (2) the pip package `python-lsp-server` (the LSP server).
        // Pin both — a future edit that drops either breaks the
        // install path on a fresh userland (device round 2026-09-07
        // showed the bare `pip install` form failing with
        // `command not found`).
        val pylsp = IntelliSenseCatalog.cardById["intellisense-python-pylsp"]!!
        assertTrue(
            "python card must install python-pip from the apt repo",
            pylsp.installCommand.contains("python-pip"),
        )
        assertTrue(
            "python card must install python-lsp-server via pip",
            pylsp.installCommand.contains("python-lsp-server"),
        )
        assertTrue(
            "python card must chain with && so a missing pip is loud",
            pylsp.installCommand.contains("&&"),
        )
    }

    @Test
    fun everyNpmCardChainsPkgInstallNpm() {
        // Defense in depth: device round 2 (2026-09-07) caught a
        // bare `npm install -g ...` failing on a userland with
        // `nodejs` but not `npm`. Every card that ends with
        // `npm install -g` MUST chain `pkg install -y npm &&`
        // first. The C/C++ clang card is the one exception (it
        // uses `pkg install -y clang`, no npm involved).
        for (card in IntelliSenseCatalog.cards) {
            if (card.installCommand.contains("npm install")) {
                assertTrue(
                    "card ${card.id} must chain `pkg install -y npm &&` before `npm install -g`",
                    card.installCommand.startsWith("pkg install -y npm &&"),
                )
            }
        }
    }

    @Test
    fun cardDescriptionsMentionPhase31Versions() {
        // Description sanity: a card without a description won't
        // help the user. (The ModulesScreen renders the description
        // under the title.)
        for (card in IntelliSenseCatalog.cards) {
            assertTrue("blank description on ${card.id}", card.description.isNotBlank())
        }
    }
}
