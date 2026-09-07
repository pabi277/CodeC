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
 * The Phase 31.1 server catalog.
 *
 * **31.1–31.3 (C/C++, Python, JS/TS):** in 31.1's first commit. The
 * cards in [IntelliSenseCatalog] drive the install path; the manager
 * uses these configs to launch stdio LSP.
 *
 * **Device round (2026-09-07, owner request):** added shell (bash),
 * HTML, CSS, JSON, and YAML. All four are documented npm packages
 * with active maintainers and 100k+ weekly downloads, install inside
 * PREFIX after `nodejs` exists (Phase 20.1), and are the canonical
 * phone-friendly LSP servers. Each language has its own server
 * config so the manager launches the right binary; the
 * [IntelliSenseCatalog] may use one combined install card for
 * several vscode-* servers (`@zed-industries/vscode-langservers-extracted`
 * ships HTML+CSS+JSON in one npm package), but the probe binaries
 * stay per-language.
 *
 * **Still deferred:** Go (gopls, ~50 MB; needs `golang` in repo),
 * Rust (rust-analyzer, ~200 MB; needs `rust` in repo), PHP (phpactor /
 * intelephense; complex installs), Ruby (solargraph; Ruby gem), Lua
 * (sumneko/lua-language-server, 50+ MB), XML (no widely-deployed
 * LSP — Phase 30 packs carry the common DTD/namespace completions).
 * The cards stay disabled until a server is in the CodeC repository
 * OR a documented npm/pip install path is verified for the device.
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
        // 31.4 (device round 2026-09-07, owner request) — Shell via
        // bash-language-server. MIT, 205k weekly downloads, requires
        // node 20+ (Phase 20.1 ships node 26.4). Adds command-name
        // completion, man-page hover, shellcheck integration, and
        // explainshell — completion sources the snippet packs cannot
        // provide (no pack knows the names of all 4 000+ commands in
        // the userland).
        LspServerConfig(
            language = LanguageType.SHELL,
            displayName = "Shell (bash-language-server)",
            probeBinary = "bash-language-server",
            command = listOf("bash-language-server", "start"),
            languageId = "shellscript",
        ),
        // 31.4 — HTML via vscode-html-language-server. MIT, ships
        // with the `@zed-industries/vscode-langservers-extracted`
        // package. Adds HTML element completion, attribute hints,
        // and HTML5 tag/attribute documentation.
        LspServerConfig(
            language = LanguageType.HTML,
            displayName = "HTML (vscode-html-language-server)",
            probeBinary = "vscode-html-language-server",
            command = listOf("vscode-html-language-server", "--stdio"),
            languageId = "html",
        ),
        // 31.4 — CSS via vscode-css-language-server. Same npm
        // package as HTML. Adds CSS property completion, browser-
        // specific properties, and `@media` / `@keyframes` awareness
        // that the Phase 30 Emmet/CSS packs cannot provide.
        LspServerConfig(
            language = LanguageType.CSS,
            displayName = "CSS (vscode-css-language-server)",
            probeBinary = "vscode-css-language-server",
            command = listOf("vscode-css-language-server", "--stdio"),
            languageId = "css",
        ),
        // 31.4 — JSON via vscode-json-language-server. Same npm
        // package. Adds schema-aware completion (e.g. `package.json`
        // knows the npm script names; `tsconfig.json` knows the
        // compiler options), which the snippet packs cannot provide.
        LspServerConfig(
            language = LanguageType.JSON,
            displayName = "JSON (vscode-json-language-server)",
            probeBinary = "vscode-json-language-server",
            command = listOf("vscode-json-language-server", "--stdio"),
            languageId = "json",
        ),
        // 31.4 — YAML via redhat-developer/yaml-language-server.
        // MIT, 2.4M weekly downloads, requires node 18+ (Phase 20.1
        // ships node 26). Adds YAML schema-aware completion (k8s,
        // GitHub Actions, docker-compose, etc. when the file
        // declares a `$schema`).
        LspServerConfig(
            language = LanguageType.YAML,
            displayName = "YAML (yaml-language-server)",
            probeBinary = "yaml-language-server",
            command = listOf("yaml-language-server", "--stdio"),
            languageId = "yaml",
        ),
    )

    /** Lookup by language. Returns null when no server is configured. */
    fun forLanguage(language: LanguageType): LspServerConfig? =
        servers.firstOrNull { it.language == language }
}
