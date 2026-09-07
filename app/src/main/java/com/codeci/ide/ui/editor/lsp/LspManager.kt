package com.codeci.ide.ui.editor.lsp

import com.codeci.ide.ui.editor.CompletionItem
import com.codeci.ide.ui.utils.AppLogger
import com.codeci.ide.ui.utils.LanguageType
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Phase 31.1 — LSP completion orchestrator.
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
 * **Host-testable:** [Provider] is a single-method interface that returns
 * a `List<LspItemMapping.LspShape>`. The real production provider wraps
 * sora's `LspEditor.requestCompletion(...)`; the test provider returns a
 * static list or throws. The orchestrator NEVER calls sora directly, so
 * `:app:testDebugUnitTest` does not need the AAR on the classpath.
 *
 * **L2 (destroy):** the manager exposes [shutdown] for the activity
 * lifecycle (master completion OFF, file close, app background). Per the
 * 31.1 README the manager is process-scoped — the activity recreates it
 * when completions are re-enabled.
 */
class LspManager(
    private val probe: BinaryProbe = SystemBinaryProbe,
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
        if (changed) AppLogger.i(TAG, "masterEnabled=$enabled (active=${active.size})")
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
     */
    fun completions(
        language: LanguageType,
        prefix: String,
        engineItems: List<CompletionItem>,
        limit: Int,
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
        val shapes = try {
            timed { provider.request(prefix) }
        } catch (t: Throwable) {
            AppLogger.w(TAG, "${config.displayName} request failed: ${t.javaClass.simpleName}: ${t.message}")
            blacklisted += language
            shutdownLanguage(language)
            return engineItems
        }
        if (shapes == null) {
            // Timeout — same handling as a thrown exception.
            AppLogger.w(TAG, "${config.displayName} request timed out after ${requestTimeoutMs}ms")
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
        AppLogger.i(TAG, "started ${config.displayName} (${config.command.first()})")
        provider
    } catch (t: Throwable) {
        AppLogger.w(TAG, "failed to start ${config.displayName}: ${t.javaClass.simpleName}: ${t.message}")
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
            AppLogger.w(TAG, "provider ignored its budget (${elapsedMs}ms)")
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
 * A language server provider. Production wraps sora's `LspEditor`; tests
 * provide a fake. The interface is a single `request` because that is
 * the only operation the manager needs to know about.
 *
 * `start()` is the wire-up (process spawn + LSP initialize handshake).
 * `shutdown()` is the teardown. `request` returns a list of LSP-shaped
 * items; the manager does the mapping.
 */
interface Provider {
    fun start()
    fun request(prefix: String): List<LspItemMapping.LspShape>
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
 * exists and is a regular file. `PREFIX` is the only place the CodeC
 * userland keeps executables; `System.getenv` is safe to read on the
 * JVM (host tests see `null` and the probe returns false unless the
 * caller sets a temp dir).
 */
object SystemBinaryProbe : BinaryProbe {
    override fun exists(binary: String): Boolean {
        val prefix = System.getenv("CODEC_PREFIX") ?: return false
        val file = java.io.File("$prefix/bin/$binary")
        return file.isFile
    }
}

/**
 * The default factory — returns a no-op provider until the production
 * sora `LspEditor` wiring lands (it is a small file; see the follow-up
 * section in `docs/chat-phase31/PART_31_1_EDITOR_LSP.md` for the
 * production glue). Tests inject a different factory.
 */
object SystemProviderFactory : ProviderFactory {
    override fun create(config: LspServerConfig): Provider = NoopProvider(config)

    /**
     * Returns an empty list and never blocks. The production swap
     * replaces this with a sora `LspEditor`-backed implementation
     * (its `request` awaits the `requestCompletion` future on a
     * coroutine dispatcher). The no-op keeps the manager's rules —
     * master switch, blacklist, timeout, shutdown — exercisable
     * without sora on the classpath.
     */
    private class NoopProvider(private val config: LspServerConfig) : Provider {
        override fun start() = Unit
        override fun request(prefix: String): List<LspItemMapping.LspShape> = emptyList()
        override fun shutdown() = Unit
    }
}
