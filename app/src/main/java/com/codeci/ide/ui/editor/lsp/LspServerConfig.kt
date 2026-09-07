package com.codeci.ide.ui.editor.lsp

import com.codeci.ide.ui.utils.LanguageType

/**
 * Phase 31.1 — per-language LSP server configuration.
 *
 * Tells the [LspManager] HOW to launch a stdio language server for a file's
 * language. The actual `Process` launch + LSP handshake live in the manager;
 * this struct only knows:
 *  - the binary the userland must have installed (so the manager can probe
 *    `PREFIX/bin/<probe>` before spawn — fast, no 60 s hang on a missing
 *    server);
 *  - the command line to run (LSP stdio is the contract);
 *  - the LSP `languageId` sora's editor-lsp expects for the file.
 *
 * **Pure Kotlin, Android-free, host-testable.** No sora, no lsp4j, no
 * Process. The struct fields are stable across LSP versions; the manager
 * owns the wire.
 *
 * **C / C++ invariant (31.2):** `clangd` is analysis only. The Phase 21
 * RUN pipeline still uses CodeC's own `cc` (TCC) — never clangd, never a
 * clang symlink. See `LanguageRunProfile` for the run side.
 *
 * **31.3 deferred:** Go (gopls) and Rust (rust-analyzer) are NOT in the
 * CodeC repository yet (Phase 20.1 didn't publish them — D7's dispatch
 * couldn't, only the bootstrap/llvm/php/ruby/lua legs landed). The cards
 * stay disabled until `LanguageRunProfile.inRepository` flips true, so a
 * tap-to-install never advertises a server we cannot deliver.
 */
data class LspServerConfig(
    val language: LanguageType,
    val displayName: String,
    /** Bin name under `PREFIX/bin` the manager probes before spawn. */
    val probeBinary: String,
    /** Argv list — the manager passes these to [ProcessBuilder]. */
    val command: List<String>,
    /** LSP `languageId` for the open file (sora's editor-lsp contract). */
    val languageId: String,
    /** True when the package is in the CodeC repository today. */
    val available: Boolean = true,
) {
    init {
        require(command.isNotEmpty()) { "LSP command must not be empty for $language" }
        require(probeBinary.isNotBlank()) { "LSP probe binary must not be blank for $language" }
        require(languageId.isNotBlank()) { "LSP languageId must not be blank for $language" }
    }
}

/**
 * The Phase 31.1 server catalog. Three languages in 31.1–31.3 (C/C++,
 * Python, JS/TS) — go/rust/HTML/CSS/JSON/shell keep their snippet-only
 * world until a server exists in the CodeC repository.
 *
 * The catalog is a `val` (no Android, no IO) so the manager can be
 * constructed in a unit test with the exact same table the app ships.
 */
object LspServerCatalog {
    val servers: List<LspServerConfig> = listOf(
        // 31.2 — C / C++. clangd ships with the existing `clang` package
        // (Phase 20.1 D5: the package's `bin/cc` is stripped to keep
        // CodeC's own TCC frontend, but `bin/clangd` is intact and is the
        // LSP server binary). `languageId` is the LSP-spec string the
        // VS Code clangd extension uses; editor-lsp's `LspEditor` takes it
        // verbatim.
        LspServerConfig(
            language = LanguageType.C,
            displayName = "C / C++ (clangd)",
            probeBinary = "clangd",
            // clangd's LSP stdio launcher: `--background-index` is a phone
            // win (the first keystroke after a launch doesn't have to
            // block on a full project index), `--clang-tidy` is off (clangd
            // is the LSP server, clang-tidy is a separate binary), and
            // `--query-driver` lets clangd reuse the project compile
            // commands if the user later adds a `compile_flags.txt`.
            command = listOf("clangd", "--background-index"),
            languageId = "c",
        ),
        LspServerConfig(
            language = LanguageType.CPP,
            displayName = "C / C++ (clangd)",
            probeBinary = "clangd",
            command = listOf("clangd", "--background-index"),
            languageId = "cpp",
        ),
        // 31.3 — Python via python-lsp-server (pylsp). Phase 12 puts
        // `python` in the CodeC repository; `python-lsp-server` rides
        // pip inside PREFIX (the catalog defers the *install path* to a
        // future card so this phase ships the integration, not the
        // packaging). `languageId` follows the LSP-spec convention.
        LspServerConfig(
            language = LanguageType.PYTHON,
            displayName = "Python (pylsp)",
            probeBinary = "pylsp",
            command = listOf("pylsp"),
            languageId = "python",
        ),
        // 31.3 — JS / TS via typescript-language-server. Phase 20.1
        // publishes nodejs; the `typescript` and `typescript-language-server`
        // packages install inside PREFIX via npm. `languageId` is the
        // LSP-spec string editor-lsp expects per file extension.
        LspServerConfig(
            language = LanguageType.JAVASCRIPT,
            displayName = "JavaScript / TypeScript (tsserver)",
            probeBinary = "typescript-language-server",
            command = listOf("typescript-language-server", "--stdio"),
            languageId = "javascript",
        ),
        LspServerConfig(
            language = LanguageType.TYPESCRIPT,
            displayName = "JavaScript / TypeScript (tsserver)",
            probeBinary = "typescript-language-server",
            command = listOf("typescript-language-server", "--stdio"),
            languageId = "typescript",
        ),
    )

    /** Lookup by language. Returns null when no server is configured. */
    fun forLanguage(language: LanguageType): LspServerConfig? =
        servers.firstOrNull { it.language == language }
}
