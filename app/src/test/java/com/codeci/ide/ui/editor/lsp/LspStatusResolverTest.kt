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
        // Plain text / JSON / markdown / shell / HTML / CSS / go /
        // rust / php / ruby / lua / xml / yaml — these languages
        // have a run profile (most) but no LSP card. Status is
        // NoCard; the editor stays silent. The 31.3 README says
        // "defer until a CodeC package exists" for the ones that
        // do not (gopls, rust-analyzer).
        val probe = BinaryProbe { false }
        for (lang in listOf(
            LanguageType.TEXT, LanguageType.JSON, LanguageType.MARKDOWN,
            LanguageType.SHELL, LanguageType.HTML, LanguageType.CSS,
            LanguageType.GO, LanguageType.RUST, LanguageType.PHP,
            LanguageType.RUBY, LanguageType.LUA, LanguageType.XML,
            LanguageType.YAML,
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
}
