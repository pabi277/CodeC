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
        // The cards table is the source of truth for "is there a
        // Packages hub install row for this language?" — if the card
        // exists, the user can install it; otherwise we stay silent.
        val cardId = languageToIntelliSenseCardId(language)
        if (cardId != null && probe.exists(cardId).not()) {
            // The card exists, the binary does not (yet). The card's
            // `binary` is the install probe — if the user has already
            // installed the package via a path that does NOT produce
            // a $PREFIX/bin/<binary> file, the next request will
            // flip to Installed via the server probe. Either way,
            // the editor offers the Install IntelliSense hint.
            return LspStatus.AvailableCard
        }
        return LspStatus.NoCard
    }

    /**
     * Map a language to the IntelliSense card id, when one exists.
     * Languages that have a [LspServerConfig] also have a card; the
     * mapping is the LSP catalog's `id` field.
     */
    private fun languageToIntelliSenseCardId(language: LanguageType): String? =
        LspServerCatalog.forLanguage(language)?.language?.let { lang ->
            // The 31.1 catalog uses `language = C, CPP` for one card
            // (`clang`); the 31.3 catalog uses `language = PYTHON`
            // (`python-lsp-server`) and `language = JAVASCRIPT /
            // TYPESCRIPT` (`typescript-language-server`). Match by the
            // card id directly, not by language equality, so future
            // cards (e.g. one language, multiple servers) are easy.
            IntelliSenseCatalog.cards.firstOrNull { card ->
                LspServerCatalog.servers.any { it.language == lang && it.probeBinary == card.binary }
            }?.id
        }
}
