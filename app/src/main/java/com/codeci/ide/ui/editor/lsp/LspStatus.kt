package com.codeci.ide.ui.editor.lsp

import com.codeci.ide.ui.utils.LanguageType

/**
 * Phase 31.2 / 31.3 — the editor's view of "should I nudge the user
 * to install IntelliSense for this file's language?"
 *
 * Three states, one rule: never block typing.
 *
 *  - [Installed] — the LSP server binary is on disk. The orchestrator
 *    will pick it up on the next request. The card in the Packages
 *    hub flips from INSTALL to INSTALLED. **No prompt.**
 *  - [AvailableCard] — the language has an IntelliSense card but the
 *    binary is NOT on disk. The editor MAY show a one-line suggestion
 *    ("IntelliSense: install clangd from Packages") at the bottom of
 *    the file, OFF the caret; the suggestion is a tap target, never
 *    a modal. **Never blocks typing.**
 *  - [NoCard] — the language has no card. **Silent.** Snippets are
 *    the only completion source and that is correct.
 *
 * The function is pure; the manager consults it on every request but
 * does not own it (the orchestrator's job is request/response; the
 * status is a separate concern the UI consumes).
 */
enum class LspStatus {
    Installed,
    AvailableCard,
    NoCard,
}

/**
 * Pure mapping from a language + on-disk probe to the right status.
 * Host-tested: every [LspServerCatalog.servers] row exercises the
 * Installed path; every [IntelliSenseCatalog.cards] row exercises
 * the AvailableCard path; the rest falls through to NoCard.
 */
object LspStatusResolver {
    fun statusFor(language: LanguageType, probe: BinaryProbe): LspStatus {
        val server = LspServerCatalog.forLanguage(language)
        if (server != null && probe.exists(server.probeBinary)) {
            return LspStatus.Installed
        }
        // The card's `binary` field is the install probe (the same
        // `$PREFIX/bin/<binary>` path the orchestrator checks). If
        // the user has already installed the package via a path
        // that does NOT produce a $PREFIX/bin/<binary> file, the
        // next request will still see the server probe; either way
        // the editor offers the Install IntelliSense hint.
        val cardBinary = languageToIntelliSenseCardBinary(language)
        if (cardBinary != null && !probe.exists(cardBinary)) {
            return LspStatus.AvailableCard
        }
        return LspStatus.NoCard
    }

    /**
     * Map a language to the IntelliSense card's `binary` field, when
     * a card exists for that language. The card's `binary` is the
     * probe target (e.g. `clangd`, `pylsp`,
     * `typescript-language-server`) — the same string the
     * orchestrator checks via [LspServerConfig.probeBinary].
     */
    private fun languageToIntelliSenseCardBinary(language: LanguageType): String? =
        LspServerCatalog.forLanguage(language)?.language?.let { lang ->
            // The 31.1 catalog uses `language = C, CPP` for one card
            // (clangd); the 31.3 catalog uses `language = PYTHON`
            // (pylsp) and `language = JAVASCRIPT / TYPESCRIPT`
            // (typescript-language-server). Match by the card's
            // `binary` (== the server's `probeBinary`), not by
            // language equality, so future cards (e.g. one language,
            // multiple servers) are easy.
            IntelliSenseCatalog.cards.firstOrNull { card ->
                LspServerCatalog.servers.any { it.language == lang && it.probeBinary == card.binary }
            }?.binary
        }
}
