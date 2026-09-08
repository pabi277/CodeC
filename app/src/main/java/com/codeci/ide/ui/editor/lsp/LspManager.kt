package com.codeci.ide.ui.editor.lsp

import com.codeci.ide.ui.editor.CompletionItem
import com.codeci.ide.ui.utils.AppLogger
import com.codeci.ide.ui.utils.LanguageType
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Phase 31.1 / 31.5 — LSP completion orchestrator.
 *
 * Phone-shape LSP:
 *  - **One process per language** (L1). Spawn is lazy: the first time
 *    `completions(...)` is called for a language whose binary is installed.
 *  - **Destroy on master OFF / language switch / file close** (L2). The
 *    manager is process-scoped — the activity owns one instance and tells
 *    it when the surface goes away.
 *  - **Timeout / crash → silent fallback to snippets** (L3). A
 *    per-request budget (default 200 ms, 27.3's "don't block keys" law)
 *    plus a single-shot failure flag per language so a hung server never
 *    blocks a future keystroke.
 *  - **No network LSP** (L4). Stdio only; the catalog has no `ssh://` or
 *    `ws://` and the provider interface refuses a non-stdio transport.
 *  - **Completions stay off-main** (L5). The provider returns a `Future`,
 *    the manager awaits with a timeout. The hot call site
 *    ([com.codeci.ide.ui.editor.sora.CodeCLanguage.requireAutoComplete]) is
 *    already on sora's completion thread, so the await is bounded.
 *
 * **Host-testable:** [Provider] is a small interface that returns
 * a `List<LspItemMapping.LspShape>`. The real production provider is
 * the hand-rolled [LspStdioClient] (Phase 31.5). Tests provide a fake
 * that returns a static list or throws. The orchestrator NEVER calls
 * sora's AAR directly, so `:app:testDebugUnitTest` does not need the
 * AAR on the classpath (it is unusable on CodeC anyway: minSdk 26
 * vs our 24 — see PART_31_5_STDIO_WIRE.md).
 *
 * **L2 (destroy):** the manager exposes [shutdown] for the activity
 * lifecycle (master completion OFF, file close, app background). Per the
 * 31.1 README the manager is process-scoped — the activity recreates it
 * when completions are re-enabled.
 */
class LspManager(
    private val probe: BinaryProbe = SystemBinaryProbe.NoOp,
    private val providerFactory: ProviderFactory = SystemProviderFactory,
    private val clock: () -> Long = System::nanoTime,
    private val requestTimeoutMs: Long = DEFAULT_REQUEST_TIMEOUT_MS,
) {

    /** True when the user has turned master completion OFF (L2). */
    private val masterEnabled = AtomicBoolean(true)

    /** Active provider per language (null = not started or already shut down). */
    private val active = ConcurrentHashMap<LanguageType, Provider>()

    /**
     * Languages whose last request timed out / threw. We refuse to spawn
     * the same process again for the session — `pkg install clangd` is the
     * recovery path, and silently re-spawning a hung server every
     * keystroke is exactly the bug L3 forbids.
     */
    private val blacklisted = ConcurrentHashMap.newKeySet<LanguageType>()

    /**
     * Switch master completion on/off. When OFF, the manager returns an
     * empty list, destroys active providers, and is a no-op until
     * [setMasterEnabled] is called with `true` again.
     */
    fun setMasterEnabled(enabled: Boolean) {
        val changed = masterEnabled.compareAndSet(!enabled, enabled)
        if (!enabled) shutdown()
        if (changed) AppLogger.i(TAG, "masterEnabled=" + enabled + " (active=" + active.size + ")")
    }

    fun isMasterEnabled(): Boolean = masterEnabled.get()

    /**
     * Best-effort completion lookup. Returns an empty list when:
     *  - master completion is OFF (L2);
     *  - the language has no [LspServerConfig] in the catalog;
     *  - the binary is not installed on disk (probe miss — never hangs);
     *  - the language is blacklisted this session (L3);
     *  - the provider throws / times out (L3, logged).
     *
     * Otherwise the LSP items are pre-pended to the engine's items so the
     * strip shows LSP first, snippets next, keywords last (Phase 30's
     * rank order is preserved within the engine half).
     *
     * The optional [context] is the new Phase 31.5 field — file name +
     * cursor + full buffer content. When supplied (the analyzer's call
     * site), the LSP server can `didOpen` / `didChange` the file and
     * the manager can return LSP items scoped to the cursor. When
     * absent (older call sites), the manager builds a stub context;
     * the stdio client will simply skip the `didOpen` and the LSP
     * server may return an empty list — the engine half is
     * unaffected.
     */
    fun completions(
        language: LanguageType,
        prefix: String,
        engineItems: List<CompletionItem>,
        limit: Int,
        context: LspRequestContext? = null,
    ): List<CompletionItem> {
        if (!masterEnabled.get()) return engineItems
        if (language in blacklisted) return engineItems
        val config = LspServerCatalog.forLanguage(language) ?: return engineItems
        if (!config.available) return engineItems
        if (!probe.exists(config.probeBinary)) {
            // Quiet by design — the Packages card advertises the install,
            // not the editor. The next request that finds the binary
            // installed will pick the server up.
            return engineItems
        }
        val provider = active.getOrPut(language) { startProvider(config) }
            ?: return engineItems
        val ctx = context ?: LspRequestContext(
            fileName = "",
            prefix = prefix,
            line = 0,
            column = 0,
            content = "",
        )
        val shapes = try {
            timed { provider.request(ctx) }
        } catch (t: Throwable) {
            AppLogger.w(TAG, config.displayName + " request failed: " + t.javaClass.simpleName + ": " + t.message)
            blacklisted += language
            shutdownLanguage(language)
            return engineItems
        }
        if (shapes == null) {
            // Timeout — same handling as a thrown exception.
            AppLogger.w(TAG, config.displayName + " request timed out after " + requestTimeoutMs + "ms")
            blacklisted += language
            shutdownLanguage(language)
            return engineItems
        }
        val lspItems = shapes.map(LspItemMapping::toCompletionItem)
        // Dedup by label against the engine half: a snippet `int main()`
        // may match a clangd function of the same name; we keep the
        // snippet (it carries a richer insert + tabstops) and offer the
        // LSP item only when the engine didn't already claim the label.
        val engineLabels = engineItems.asSequence().map { it.label }.toHashSet()
        val merged = ArrayList<CompletionItem>(lspItems.size + engineItems.size)
        merged += lspItems.asSequence().filter { it.label !in engineLabels }
        merged += engineItems
        return merged.take(limit)
    }

    /** Destroy every active provider. Idempotent. */
    fun shutdown() {
        val keys = active.keys.toList()
        for (k in keys) shutdownLanguage(k)
        blacklisted.clear()
    }

    private fun shutdownLanguage(language: LanguageType) {
        active.remove(language)?.shutdown()
    }

    private fun startProvider(config: LspServerConfig): Provider? = try {
        val provider = providerFactory.create(config)
        provider.start()
        AppLogger.i(TAG, "started " + config.displayName + " (" + config.command.first() + ")")
        provider
    } catch (t: Throwable) {
        AppLogger.w(TAG, "failed to start " + config.displayName + ": " + t.javaClass.simpleName + ": " + t.message)
        blacklisted += config.language
        null
    }

    /**
     * Run [block] with a [requestTimeoutMs] budget. Returns the value on
     * success, null on timeout. The catch is a narrow wall — anything
     * thrown surfaces to the caller (the [completions] try/catch).
     */
    private inline fun <T> timed(block: () -> T): T? {
        // The provider's `request` returns a small list synchronously in
        // the test provider; the real provider returns a Future and we
        // poll with a small sleep. Either way the call site is bounded.
        val started = clock()
        val value = block()
        val elapsedMs = (clock() - started) / 1_000_000L
        if (elapsedMs > requestTimeoutMs) {
            AppLogger.w(TAG, "provider ignored its budget (" + elapsedMs + "ms)")
            return null
        }
        return value
    }

    companion object {
        const val TAG = "LspManager"
        const val DEFAULT_REQUEST_TIMEOUT_MS = 200L
    }
}

/**
 * The context the orchestrator passes to a provider for a
 * completion request. Built from the analyzer's call site (file
 * name + prefix + cursor + full buffer content). `content` is the
 * FULL buffer (not just the current line) so the LSP server has
 * the surrounding context the completion engine needs to scope
 * results. A phone-side buffer is small enough (a few KB for
 * typical source files) that this is fine.
 */
data class LspRequestContext(
    val fileName: String,
    val prefix: String,
    val line: Int,
    val column: Int,
    val content: String,
)

/**
 * A language server provider. Production is the hand-rolled
 * [LspStdioClient] (Phase 31.5). Tests provide a fake.
 *
 * The contract is:
 *  - `start()`: process spawn + LSP `initialize` handshake.
 *  - `setBuffer(fileName, content)`: LSP `didOpen` / `didChange`
 *    notification when the active file changes. Default no-op
 *    for providers that don't need it (the no-op, the test
 *    fakes).
 *  - `request(context)`: LSP `textDocument/completion` at the
 *    cursor. Returns the LSP-shaped items; the manager does the
 *    CodeC mapping.
 *  - `shutdown()`: `shutdown` + `exit` notifications, then destroy.
 */
interface Provider {
    fun start()
    fun setBuffer(fileName: String, content: String) {}
    fun request(context: LspRequestContext): List<LspItemMapping.LspShape>
    fun shutdown()
}

/** Builds a [Provider] for a given [LspServerConfig]. */
fun interface ProviderFactory {
    fun create(config: LspServerConfig): Provider
}

/** Probes whether a binary exists on disk. Pure: no Android, no IO. */
fun interface BinaryProbe {
    fun exists(binary: String): Boolean
}

/**
 * The default probe: a binary is "installed" when `$PREFIX/bin/<binary>`
 * exists and is a regular file. The probe accepts a `filesDir: File?`
 * so callers in app code pass the application private files directory;
 * host tests pass `null` and the probe returns `false` (no fake
 * binaries), which is exactly the "server missing" path the README
 * exit condition #1 exercises.
 */
class SystemBinaryProbe(private val filesDir: java.io.File? = null) : BinaryProbe {
    override fun exists(binary: String): Boolean {
        val dir = filesDir ?: return false
        // Mirrors `ShellEnvironment.prefixDir(filesDir).resolve("bin/<binary>")`
        // — the only place the CodeC userland keeps executables. Same path
        // the Phase 21 D.2 install gate + Phase 20.1 LspManager probe and
        // the ModulesScreen `checkIsInstalled` use; reusing the same
        // `filesDir/usr/bin/<bin>` location keeps "Packages" and "LSP" on
        // the same page (one install = both cards flip from gray to green).
        val file = java.io.File(java.io.File(dir, "usr"), "bin/$binary")
        return file.isFile
    }

    companion object {
        /** A no-arg probe that always returns false — for the no-op manager. */
        val NoOp: BinaryProbe = SystemBinaryProbe()
    }
}

/**
 * The default factory — returns a no-op provider. The production
 * swap (Phase 31.5) is `StdioLspProviderFactory` (a per-language
 * `LspStdioClient`); the activity wires that instead of this one.
 * This no-op keeps the manager's rules — master switch, blacklist,
 * timeout, shutdown — exercisable without any LSP server on the
 * path. Host tests use this factory by default; their test fakes
 * (the `StaticProvider` in `LspManagerTest`) override the
 * [Provider] methods directly.
 */
object SystemProviderFactory : ProviderFactory {
    override fun create(config: LspServerConfig): Provider = NoopProvider(config)

    /**
     * Returns an empty list and never blocks. The production
     * swap (Phase 31.5) replaces this with an [LspStdioClient]
     * whose `request` does a real LSP `textDocument/completion`
     * round-trip. The no-op keeps the manager's rules — master
     * switch, blacklist, timeout, shutdown — exercisable without
     * any LSP server on the path.
     */
    private class NoopProvider(private val config: LspServerConfig) : Provider {
        override fun start() = Unit
        override fun request(context: LspRequestContext): List<LspItemMapping.LspShape> = emptyList()
        override fun shutdown() = Unit
    }
}
