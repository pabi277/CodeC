package com.codeci.ide.ui.editor.lsp

import com.codeci.ide.ui.utils.LanguageType
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Phase 31.2 / 31.3 — the "should I nudge the user to install
 * IntelliSense?" rule.
 *
 * The status is a UI concern (the Packages card, the future editor
 * nudge), but the resolver is pure so CI carries confidence. The
 * tests here double as the 31.2/31.3 exit-condition pins: a card
 * that flips from AVAILABLE to INSTALLED on `pkg install` is
 * exactly what the device recipe wants the user to see.
 */
class LspStatusResolverTest {

    @Test
    fun installedWhenServerBinaryIsOnDisk() {
        // clangd on disk -> the C card is INSTALLED. The status
        // resolver says the editor can use the server (the
        // orchestrator handles the wire).
        val probe = BinaryProbe { it == "clangd" }
        assertEquals(LspStatus.Installed, LspStatusResolver.statusFor(LanguageType.C, probe))
        assertEquals(LspStatus.Installed, LspStatusResolver.statusFor(LanguageType.CPP, probe))
    }

    @Test
    fun availableCardWhenLanguageHasCardButServerMissing() {
        // clangd is NOT on disk, but the C card exists. The user
        // has a tap target to install. The editor's nudge is
        // honest: "install clangd from Packages".
        val probe = BinaryProbe { false }
        assertEquals(LspStatus.AvailableCard, LspStatusResolver.statusFor(LanguageType.C, probe))
        assertEquals(LspStatus.AvailableCard, LspStatusResolver.statusFor(LanguageType.CPP, probe))
    }

    @Test
    fun installedForPythonWhenPylspPresent() {
        val probe = BinaryProbe { it == "pylsp" }
        assertEquals(LspStatus.Installed, LspStatusResolver.statusFor(LanguageType.PYTHON, probe))
    }

    @Test
    fun availableCardForPythonWhenPylspMissing() {
        val probe = BinaryProbe { false }
        assertEquals(LspStatus.AvailableCard, LspStatusResolver.statusFor(LanguageType.PYTHON, probe))
    }

    @Test
    fun installedForTypeScriptAndJavaScriptWhenTsserverPresent() {
        val probe = BinaryProbe { it == "typescript-language-server" }
        assertEquals(LspStatus.Installed, LspStatusResolver.statusFor(LanguageType.TYPESCRIPT, probe))
        assertEquals(LspStatus.Installed, LspStatusResolver.statusFor(LanguageType.JAVASCRIPT, probe))
    }

    @Test
    fun noCardForLanguagesWithoutIntelliSense() {
        // 31.4 (device round 2026-09-07): shell, HTML, CSS, JSON,
        // YAML are now COVERED (bash-language-server,
        // vscode-langservers-extracted, yaml-language-server). What
        // remains NoCard: plain text / markdown / go / rust / php /
        // ruby / lua / xml. The 31.3 README says "defer until a
        // CodeC package exists" for the heavy ones (gopls ~50 MB,
        // rust-analyzer ~200 MB); PHP/Ruby/Lua have LSPs but
        // complex installs that are a Phase 32/33 conversation;
        // XML has no widely-deployed LSP; markdown / text have no
        // good LSP.
        val probe = BinaryProbe { false }
        for (lang in listOf(
            LanguageType.TEXT, LanguageType.MARKDOWN,
            LanguageType.GO, LanguageType.RUST, LanguageType.PHP,
            LanguageType.RUBY, LanguageType.LUA, LanguageType.XML,
        )) {
            assertEquals(
                "expected NoCard for $lang",
                LspStatus.NoCard,
                LspStatusResolver.statusFor(lang, probe)
            )
        }
    }

    @Test
    fun availableCardUsesCardBinaryNotCardId() {
        // Pin the card-binary contract: the probe target is the
        // card's `binary` (e.g. "clangd", "pylsp"), NOT the card's
        // `id` (e.g. "intellisense-c-cpp-clangd"). A probe that says
        // "yes for clangd" must return Installed (the first branch),
        // and a probe that says "no for clangd" must return
        // AvailableCard (the second branch). An earlier draft
        // accidentally probed the card id, which would always
        // return false in production and silently mask the install
        // state.
        val clangdPresent = BinaryProbe { it == "clangd" }
        assertEquals(
            LspStatus.Installed,
            LspStatusResolver.statusFor(LanguageType.C, clangdPresent),
        )
        val clangdMissing = BinaryProbe { false }
        assertEquals(
            LspStatus.AvailableCard,
            LspStatusResolver.statusFor(LanguageType.C, clangdMissing),
        )
    }

    @Test
    fun availableCardProbesByLanguageSpecificBinary() {
        // Each language probes its own card binary. A probe that
        // says "yes for clangd" must report Installed for C but
        // AvailableCard for Python (the Python card probes pylsp,
        // not clangd).
        val clangdOnly = BinaryProbe { it == "clangd" }
        assertEquals(
            LspStatus.Installed,
            LspStatusResolver.statusFor(LanguageType.C, clangdOnly),
        )
        assertEquals(
            LspStatus.AvailableCard,
            LspStatusResolver.statusFor(LanguageType.PYTHON, clangdOnly),
        )
        assertEquals(
            LspStatus.AvailableCard,
            LspStatusResolver.statusFor(LanguageType.TYPESCRIPT, clangdOnly),
        )
    }

    @Test
    fun installedTakesPrecedenceOverAvailableCard() {
        // The card and the server probe are checked in order; the
        // installed path is the one the UI shows. This pins the
        // "I already installed it; do not nag" rule.
        val probe = BinaryProbe { it == "clangd" }
        assertEquals(LspStatus.Installed, LspStatusResolver.statusFor(LanguageType.C, probe))
        val sameProbeAgain = BinaryProbe { it == "clangd" }
        // Server config says C uses clangd; installed probe confirms
        // the file is on disk; status = Installed (not AvailableCard).
        assertEquals(
            LspStatus.Installed,
            LspStatusResolver.statusFor(LanguageType.C, sameProbeAgain)
        )
    }

    @Test
    fun installedForShellWhenBashLanguageServerPresent() {
        // 31.4 — bash-language-server on disk -> SHELL is INSTALLED.
        val probe = BinaryProbe { it == "bash-language-server" }
        assertEquals(LspStatus.Installed, LspStatusResolver.statusFor(LanguageType.SHELL, probe))
    }

    @Test
    fun availableCardForShellWhenBashLanguageServerMissing() {
        val probe = BinaryProbe { false }
        assertEquals(LspStatus.AvailableCard, LspStatusResolver.statusFor(LanguageType.SHELL, probe))
    }

    @Test
    fun installedForHtmlCssJsonYamlWhenVscodeAndYamlServersPresent() {
        // 31.4 — the 4 vscode-* servers + the redhat YAML server.
        // Pin each language's probe binary — they're distinct
        // binaries that all land in the user's $PREFIX/bin/ after
        // the npm installs.
        val allServers = BinaryProbe {
            it == "vscode-html-language-server" ||
                it == "vscode-css-language-server" ||
                it == "vscode-json-language-server" ||
                it == "yaml-language-server"
        }
        assertEquals(LspStatus.Installed, LspStatusResolver.statusFor(LanguageType.HTML, allServers))
        assertEquals(LspStatus.Installed, LspStatusResolver.statusFor(LanguageType.CSS, allServers))
        assertEquals(LspStatus.Installed, LspStatusResolver.statusFor(LanguageType.JSON, allServers))
        assertEquals(LspStatus.Installed, LspStatusResolver.statusFor(LanguageType.YAML, allServers))
    }

    @Test
    fun availableCardForHtmlCssJsonYamlWhenServersMissing() {
        val probe = BinaryProbe { false }
        for (lang in listOf(
            LanguageType.HTML, LanguageType.CSS, LanguageType.JSON, LanguageType.YAML,
        )) {
            assertEquals(
                "expected AvailableCard for $lang",
                LspStatus.AvailableCard,
                LspStatusResolver.statusFor(lang, probe)
            )
        }
    }
}
