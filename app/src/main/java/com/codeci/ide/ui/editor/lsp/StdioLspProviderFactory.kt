package com.codeci.ide.ui.editor.lsp

/**
 * Phase 31.5 — the production [ProviderFactory] that wires
 * [LspStdioClient] for every language in [LspServerCatalog].
 *
 * Replaces `SystemProviderFactory` (which is the no-op) at the
 * activity's call site. The factory is a one-liner per language;
 * the heavy lifting is in [LspStdioClient] itself.
 *
 * **Why a factory and not direct `LspManager` injection?** The
 * manager takes the factory as a constructor argument; this lets
 * the host tests keep using `SystemProviderFactory` (the no-op) and
 * also lets a future "fall back to no-op on phones that can't
 * `Process.exec`" path swap the factory without touching the
 * manager.
 *
 * **The project root** is a constructor argument so the activity
 * passes the user's project dir (where the LSP server expects
 * `compile_commands.json` / `compile_flags.txt` etc.). When the
 * activity doesn't have a project (a one-off buffer in the
 * terminal pane, say), it can pass `"."` and the LSP server will
 * fall back to the current working dir.
 */
class StdioLspProviderFactory(
    private val projectRoot: String,
) : ProviderFactory {
    override fun create(config: LspServerConfig): Provider =
        LspStdioClient(config, projectRoot = projectRoot)
}
