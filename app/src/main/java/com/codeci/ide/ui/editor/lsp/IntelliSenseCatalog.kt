package com.codeci.ide.ui.editor.lsp

import com.codeci.ide.ui.modules.PackageCategory
import com.codeci.ide.ui.modules.PackageItem

/**
 * Phase 31.2 / 31.3 — the "IntelliSense as Packages" cards.
 *
 * The orchestrator's [LspServerCatalog] owns the *server* half: the
 * argv list the manager runs, the LSP `languageId`, the probe binary.
 * This file owns the *install* half: the package card the user taps in
 * the Packages hub. The two stay in lock-step by `id` (a card and a
 * server config share the same `id`, so a fix to one is obvious in the
 * other).
 *
 * **Pure Kotlin, Android-free, host-testable.** The cards re-use the
 * existing Phase 10 [PackageItem] data class so the ModulesScreen
 * surfaces them with no UI change beyond a new category.
 *
 * **Phase 31.2 — clangd** is the C/C++ card. The probe binary is
 * `clangd`, which ships inside the `clang` package (Phase 20.1
 * `apply-recipe-overrides.sh` strips `bin/cc` from the libllvm deb to
 * keep the TCC invariant — the manager's probe is `clangd`, not `cc`,
 * so the 21.4 invariant stands).
 *
 * **Phase 31.3 — pylsp + tsserver** rides pip/npm inside PREFIX after
 * `python` / `nodejs` exist; the install command is the documented
 * `pip install` / `npm i -g`. If the user does not have python/node
 * yet, the install command still works (pip/npm will explain the
 * missing `python3` / `node`), and the LSP manager's probe will keep
 * returning false until the server is on disk.
 */
object IntelliSenseCatalog {

    /**
     * The cards. A "language intelligence" card has the same shape as
     * a compiler / interpreter card so the existing ModulesScreen
     * surfaces it without a UI change: the user taps INSTALL, the
     * `installCommand` runs in the live terminal, and the orchestrator
     * picks the server up on the next request once the probe binary
     * lands in `$PREFIX/bin/`.
     *
     * **IDs are distinct from the `clang` / `python` / `nodejs`
     * package cards** because the ModulesScreen keys the LazyColumn
     * by `id` (a duplicate would crash). The `intellisense-` prefix
     * makes the lock-step with [LspServerCatalog] obvious; a future
     * "deduplicate" pass could merge the `clang` compiler card and
     * the `intellisense-c-cpp-clangd` card into one with a richer
     * description.
     */
    val cards: List<PackageItem> = listOf(
        // 31.2 — C / C++. clangd is the LSP server VS Code's C/C++
        // extension uses; it ships with the existing `clang` package.
        // The install command is `pkg install -y clang` (NOT
        // `pkg install -y clangd` — clangd is a sub-binary of the
        // clang deb at the pinned Phase 20.1 ref).
        PackageItem(
            id = "intellisense-c-cpp-clangd",
            name = "IntelliSense: C / C++ (clangd)",
            binary = "clangd",
            category = PackageCategory.LANGUAGES,
            description = "Adds clangd — VS Code's LSP server for C/C++. " +
                "Member access, struct fields, #include paths, jump to " +
                "definition, hover docs. Snippets stay the fallback. " +
                "RUN still uses CodeC's built-in `cc` (TCC); clangd is " +
                "analysis only.",
            installCommand = "pkg install -y clang",
            runCommand = "clangd --version",
        ),
        // 31.3 — Python via python-lsp-server (pylsp). `python` is
        // already in the CodeC repository (Phase 12); `python-pip` is
        // a SEPARATE Phase 12 package (the python pip-separation
        // postinst was neutralized in the CodeC repo so pip is its
        // own deb — see `docs/chat-phase12/PART_12_PYTHON.md`). The
        // card surfaces the chained install: `pkg install -y
        // python-pip` lands `pip` on PATH (idempotent if already
        // present), then `pip install --user python-lsp-server`
        // installs the LSP server into `$PREFIX/`. The orchestrator
        // probes `$PREFIX/bin/pylsp` after install. **Device round
        // (2026-09-07, owner):** the bare `pip install ...` command
        // FAILED with `pip: command not found` because the user had
        // `python` (the interpreter) but not `python-pip` (the
        // wrapper). The chain makes the card self-sufficient.
        PackageItem(
            id = "intellisense-python-pylsp",
            name = "IntelliSense: Python (pylsp)",
            binary = "pylsp",
            category = PackageCategory.LANGUAGES,
            description = "Adds python-lsp-server — VS Code's Python LSP. " +
                "Jedi completions, import resolution, signature help, " +
                "hover docs, go-to-definition. Requires Python (pkg " +
                "install -y python) — install that first if not present. " +
                "This card also installs python-pip (the CodeC apt " +
                "package from Phase 12).",
            installCommand = "pkg install -y python-pip && pip install --user python-lsp-server",
            runCommand = "pylsp --version",
        ),
        // 31.3 — JavaScript / TypeScript via typescript-language-server.
        // nodejs is in the repo (Phase 20.1); tsserver is a documented
        // npm install inside PREFIX.
        PackageItem(
            id = "intellisense-js-tsserver",
            name = "IntelliSense: JavaScript / TypeScript (tsserver)",
            binary = "typescript-language-server",
            category = PackageCategory.LANGUAGES,
            description = "Adds typescript-language-server (tsserver). " +
                "Completion for document/console/Window, JSX/TSX types, " +
                "import resolution, signature help. Requires nodejs " +
                "(pkg install -y nodejs) — install that first if not " +
                "present.",
            installCommand = "npm install -g typescript typescript-language-server",
            runCommand = "typescript-language-server --version",
        ),
        // 31.4 (device round 2026-09-07, owner request) — Shell via
        // bash-language-server. MIT, npm, 205k weekly downloads,
        // requires node 20+ (Phase 20.1 ships node 26.4). Adds
        // command-name completion, man-page hover, shellcheck, and
        // explainshell. Requires nodejs (pkg install -y nodejs) —
        // install that first if not present.
        PackageItem(
            id = "intellisense-shell-bash",
            name = "IntelliSense: Shell (bash)",
            binary = "bash-language-server",
            category = PackageCategory.LANGUAGES,
            description = "Adds bash-language-server — command-name " +
                "completion, man-page hover, shellcheck, explainshell. " +
                "Requires nodejs (pkg install -y nodejs) — install " +
                "that first if not present.",
            installCommand = "npm install -g bash-language-server",
            runCommand = "bash-language-server --version",
        ),
        // 31.4 — HTML via vscode-html-language-server. The npm
        // package is `@zed-industries/vscode-langservers-extracted`
        // (Zed's maintained fork of the original Microsoft package —
        // 3 months since last publish vs the original's 2 years; the
        // binaries are identical). The same npm install also lands
        // the CSS and JSON servers; the three cards document that
        // — installing any one of them flips the install command for
        // the other two to a no-op (npm -g is idempotent).
        PackageItem(
            id = "intellisense-html-vscode",
            name = "IntelliSense: HTML (vscode)",
            binary = "vscode-html-language-server",
            category = PackageCategory.LANGUAGES,
            description = "Adds vscode-html-language-server — HTML " +
                "element completion, attribute hints, HTML5 tag docs. " +
                "Also installs the CSS and JSON vscode servers " +
                "(@zed-industries/vscode-langservers-extracted, one " +
                "npm package). Requires nodejs (pkg install -y nodejs).",
            installCommand = "npm install -g @zed-industries/vscode-langservers-extracted",
            runCommand = "vscode-html-language-server --version",
        ),
        // 31.4 — CSS via vscode-css-language-server. Same npm
        // package as the HTML card; the install command is the
        // same. The card's `binary` (vscode-css-language-server) is
        // the per-language probe; the orchestrator's check is
        // per-language. The user only has to install ONE of the
        // three vscode cards to flip all three to INSTALLED (npm
        // -g is idempotent — running it again is a no-op).
        PackageItem(
            id = "intellisense-css-vscode",
            name = "IntelliSense: CSS (vscode)",
            binary = "vscode-css-language-server",
            category = PackageCategory.LANGUAGES,
            description = "Adds vscode-css-language-server — CSS " +
                "property completion, browser-specific properties, " +
                "@media / @keyframes awareness. Same npm install as " +
                "the HTML card " +
                "(@zed-industries/vscode-langservers-extracted). " +
                "Requires nodejs (pkg install -y nodejs).",
            installCommand = "npm install -g @zed-industries/vscode-langservers-extracted",
            runCommand = "vscode-css-language-server --version",
        ),
        // 31.4 — JSON via vscode-json-language-server. Same npm
        // package; same install command. Schema-aware completion for
        // package.json / tsconfig.json / etc.
        PackageItem(
            id = "intellisense-json-vscode",
            name = "IntelliSense: JSON (vscode)",
            binary = "vscode-json-language-server",
            category = PackageCategory.LANGUAGES,
            description = "Adds vscode-json-language-server — " +
                "schema-aware completion for package.json, " +
                "tsconfig.json, and any file with a `$schema`. Same " +
                "npm install as the HTML card " +
                "(@zed-industries/vscode-langservers-extracted). " +
                "Requires nodejs (pkg install -y nodejs).",
            installCommand = "npm install -g @zed-industries/vscode-langservers-extracted",
            runCommand = "vscode-json-language-server --version",
        ),
        // 31.4 — YAML via redhat-developer/yaml-language-server.
        // MIT, npm, 2.4M weekly downloads, requires node 18+
        // (Phase 20.1 ships node 26). Schema-aware completion
        // for k8s manifests, GitHub Actions, docker-compose, etc.
        PackageItem(
            id = "intellisense-yaml-redhat",
            name = "IntelliSense: YAML (redhat)",
            binary = "yaml-language-server",
            category = PackageCategory.LANGUAGES,
            description = "Adds yaml-language-server — schema-aware " +
                "completion for Kubernetes manifests, GitHub Actions, " +
                "docker-compose, and any file with a `$schema`. " +
                "Requires nodejs (pkg install -y nodejs).",
            installCommand = "npm install -g yaml-language-server",
            runCommand = "yaml-language-server --version",
        ),
    )

    /**
     * Card ↔ server config lookup by `id`. The orchestrator and the
     * cards stay in lock-step; the comment on each entry above points
     * at the matching [LspServerCatalog.servers] row.
     */
    val cardById: Map<String, PackageItem> = cards.associateBy { it.id }
}
