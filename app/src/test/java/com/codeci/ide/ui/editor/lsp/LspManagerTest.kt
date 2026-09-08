package com.codeci.ide.ui.editor.lsp

import com.codeci.ide.ui.editor.CompletionItem
import com.codeci.ide.ui.editor.CompletionKind
import com.codeci.ide.ui.utils.LanguageType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

/**
 * Phase 31.1 — the orchestrator's contract, end-to-end on a JVM. No
 * Android, no sora, no lsp4j. Each test injects a fake [Provider] and
 * [BinaryProbe] so the manager's rules (L1–L5) are exercised against a
 * known surface.
 *
 * The 31.1 exit condition is three lines:
 *  - "With server missing: snippets still appear (no crash)."  — [missingServerFallsBackToEngine]
 *  - "With server present: a member/local that snippets cannot know appears in chips."  — [lspItemsPrepended]
 *  - "Completion master OFF: no LSP process."  — [masterOffStopsProvider]
 */
class LspManagerTest {

    // ---------- helpers ----------

    private fun engineItem(label: String) =
        CompletionItem(label, label, CompletionKind.IDENTIFIER, "engine")

    private fun lspItem(label: String) =
        LspItemMapping.LspShape(label, 3, "lsp", label, null)

    private fun manager(
        probe: BinaryProbe = BinaryProbe { true /* server present */ },
        factory: ProviderFactory = ProviderFactory { StaticProvider(it, emptyList()) },
    ) = LspManager(probe = probe, providerFactory = factory)

    // ---------- exit-condition #1: server missing → engine still runs ----------

    @Test
    fun missingServerFallsBackToEngine() {
        // The probe says clangd is NOT on disk; the manager must return
        // the engine items unchanged. No provider is started.
        val started = AtomicInteger(0)
        val m = manager(
            probe = BinaryProbe { false },
            factory = ProviderFactory {
                started.incrementAndGet()
                StaticProvider(it, listOf(lspItem("should_not_appear")))
            },
        )
        val engine = listOf(engineItem("printf"), engineItem("malloc"))
        val out = m.completions(LanguageType.C, "", engine, limit = 10)
        assertEquals(engine, out)
        assertEquals("no provider should have been started", 0, started.get())
    }

    // ---------- exit-condition #2: server present → LSP items prepended ----------

    @Test
    fun lspItemsPrepended() {
        val lsp = listOf(
            lspItem("printf"),   // duplicates the engine; engine wins
            lspItem("__stdin"),  // clangd-only knowledge
            lspItem("__stdout"),
        )
        val m = manager(
            probe = BinaryProbe { true },
            factory = ProviderFactory { StaticProvider(it, lsp) },
        )
        val engine = listOf(engineItem("printf"), engineItem("malloc"))
        val out = m.completions(LanguageType.C, "p", engine, limit = 10)
        // The dedup keeps the engine's printf (snippet/symbols win) but
        // adds the two clangd-only names in front.
        assertEquals(
            listOf("__stdin", "__stdout", "printf", "malloc"),
            out.map { it.label }
        )
    }

    @Test
    fun lspItemKindIsIdentifierAndDetailIsRouted() {
        // The mapping's detail is what the strip paints on the right
        // edge; the kind is what the engine/ghost cares about.
        val m = manager(
            factory = ProviderFactory { StaticProvider(it, listOf(lspItem("fread"))) },
        )
        val out = m.completions(LanguageType.C, "", emptyList(), limit = 10)
        assertEquals(1, out.size)
        val item = out.single()
        assertEquals(CompletionKind.IDENTIFIER, item.kind)
        assertEquals("lsp", item.detail)
    }

    // ---------- exit-condition #3: master OFF → no provider start, engine only ----------

    @Test
    fun masterOffStopsProvider() {
        val started = AtomicInteger(0)
        val m = manager(
            factory = ProviderFactory {
                started.incrementAndGet()
                StaticProvider(it, listOf(lspItem("printf")))
            },
        )
        m.setMasterEnabled(false)
        val out = m.completions(LanguageType.C, "", listOf(engineItem("printf")), limit = 10)
        assertEquals(listOf("printf"), out.map { it.label })
        assertEquals(0, started.get())
        // And the master setting survives a follow-up call: no provider
        // is started even when a request comes in.
        m.completions(LanguageType.CPP, "", listOf(engineItem("main")), limit = 10)
        assertEquals(0, started.get())
    }

    @Test
    fun masterOnThenOffShutsActiveProvider() {
        val shutdowns = AtomicInteger(0)
        val m = manager(
            factory = ProviderFactory {
                StaticProvider(it, emptyList(), onShutdown = { shutdowns.incrementAndGet() })
            },
        )
        // Warm the C provider up.
        m.completions(LanguageType.C, "", emptyList(), limit = 10)
        assertFalse("provider not started", m.isMasterEnabled().not())
        // Flip master OFF — the active provider is shut down.
        m.setMasterEnabled(false)
        assertEquals(1, shutdowns.get())
    }

    // ---------- L1: one provider per language, lazy on first request ----------

    @Test
    fun oneProviderPerLanguage() {
        val created = AtomicInteger(0)
        val m = manager(
            factory = ProviderFactory {
                created.incrementAndGet()
                StaticProvider(it, emptyList())
            },
        )
        // Three requests for C, one for Python: two providers, ever.
        m.completions(LanguageType.C, "", emptyList(), limit = 10)
        m.completions(LanguageType.C, "", emptyList(), limit = 10)
        m.completions(LanguageType.C, "", emptyList(), limit = 10)
        m.completions(LanguageType.PYTHON, "", emptyList(), limit = 10)
        assertEquals("C + Python = two providers", 2, created.get())
    }

    // ---------- L3: provider exception → blacklist + engine fallback ----------

    @Test
    fun providerExceptionBlacklistsAndFallsBack() {
        val calls = AtomicInteger(0)
        val m = manager(
            factory = ProviderFactory {
                object : Provider {
                    override fun start() = Unit
                    override fun request(context: LspRequestContext): List<LspItemMapping.LspShape> {
                        calls.incrementAndGet()
                        throw RuntimeException("server hung")
                    }
                    override fun shutdown() = Unit
                }
            },
        )
        // First call: provider throws, manager logs + blacklists +
        // returns the engine items.
        val out = m.completions(LanguageType.C, "", listOf(engineItem("printf")), limit = 10)
        assertEquals(listOf("printf"), out.map { it.label })
        assertEquals(1, calls.get())
        // Second call: blacklisted, no provider is touched.
        val out2 = m.completions(LanguageType.C, "", listOf(engineItem("malloc")), limit = 10)
        assertEquals(listOf("malloc"), out2.map { it.label })
        assertEquals("blacklisted language never re-spawns", 1, calls.get())
    }

    // ---------- L3: provider ignores its budget → timed() returns null → blacklist ----------

    @Test
    fun slowProviderBlacklists() {
        val m = LspManager(
            probe = BinaryProbe { true },
            providerFactory = ProviderFactory {
                object : Provider {
                    override fun start() = Unit
                    override fun request(context: LspRequestContext): List<LspItemMapping.LspShape> {
                        // Sleep well over the 200 ms budget the manager
                        // passes in by default. timed()'s own wall-clock
                        // check trips the L3 path.
                        Thread.sleep(500)
                        return emptyList()
                    }
                    override fun shutdown() = Unit
                }
            },
            // Keep the test fast: 50 ms budget.
            requestTimeoutMs = 50,
        )
        val out = m.completions(LanguageType.C, "", listOf(engineItem("x")), limit = 10)
        assertEquals(listOf("x"), out.map { it.label })
        // A second call sees the blacklist — no Thread.sleep second time.
        val out2 = m.completions(LanguageType.C, "", listOf(engineItem("y")), limit = 10)
        assertEquals(listOf("y"), out2.map { it.label })
    }

    // ---------- L4: no network — the catalog has no `ssh://` or `ws://` ----------

    @Test
    fun catalogHasNoNetworkTransports() {
        // Sanity: every command is a binary name (no scheme, no URL).
        for (config in LspServerCatalog.servers) {
            for (arg in config.command) {
                assertFalse(
                    "LSP command must be a local binary, got '$arg' in ${config.displayName}",
                    arg.contains("://")
                )
            }
        }
    }

    // ---------- L2: shutdown tears every provider down ----------

    @Test
    fun shutdownTearsEveryProviderDown() {
        val shutdowns = AtomicInteger(0)
        val m = manager(
            factory = ProviderFactory {
                StaticProvider(it, emptyList(), onShutdown = { shutdowns.incrementAndGet() })
            },
        )
        m.completions(LanguageType.C, "", emptyList(), limit = 10)
        m.completions(LanguageType.PYTHON, "", emptyList(), limit = 10)
        m.shutdown()
        assertEquals(2, shutdowns.get())
        // And a re-install is clean — the new request starts fresh
        // providers (a smoke for "manager is the unit of teardown").
        val after = AtomicInteger(0)
        val m2 = LspManager(
            probe = BinaryProbe { true },
            providerFactory = ProviderFactory {
                after.incrementAndGet()
                StaticProvider(it, emptyList())
            },
        )
        m2.completions(LanguageType.C, "", emptyList(), limit = 10)
        assertEquals(1, after.get())
    }

    // ---------- merged list respects the limit ----------

    @Test
    fun respectsLimit() {
        val lsp = (1..30).map { lspItem("lsp_$it") }
        val m = manager(
            factory = ProviderFactory { StaticProvider(it, lsp) },
        )
        val out = m.completions(LanguageType.C, "", emptyList(), limit = 8)
        assertEquals(8, out.size)
        // First slots are LSP (they pre-pend); the rest get cut.
        assertEquals("lsp_1", out.first().label)
    }

    // ---------- master OFF then ON: a new provider is started on the next request ----------

    @Test
    fun masterOffThenOnResumes() {
        val created = AtomicInteger(0)
        val m = manager(
            factory = ProviderFactory {
                created.incrementAndGet()
                StaticProvider(it, listOf(lspItem("a")))
            },
        )
        m.setMasterEnabled(false)
        m.completions(LanguageType.C, "", emptyList(), limit = 10)
        assertEquals(0, created.get())
        m.setMasterEnabled(true)
        m.completions(LanguageType.C, "", emptyList(), limit = 10)
        assertEquals(1, created.get())
    }

    // ---------- blacklisted language does NOT get a probe on every request ----------

    @Test
    fun blacklistedLanguageDoesNotProbeAgain() {
        val probeCalls = AtomicInteger(0)
        val m = LspManager(
            probe = BinaryProbe {
                probeCalls.incrementAndGet()
                true
            },
            providerFactory = ProviderFactory {
                object : Provider {
                    override fun start() = Unit
                    override fun request(context: LspRequestContext): List<LspItemMapping.LspShape> {
                        throw IllegalStateException("boom")
                    }
                    override fun shutdown() = Unit
                }
            },
        )
        m.completions(LanguageType.C, "", emptyList(), limit = 10)
        // First request: probe is called (1).
        assertEquals(1, probeCalls.get())
        m.completions(LanguageType.C, "", emptyList(), limit = 10)
        // Second request: blacklisted → no probe, no provider touch.
        assertEquals("blacklisted language is short-circuited before the probe", 1, probeCalls.get())
    }

    // ---------- provider shutdown is called when a language is blacklisted ----------

    @Test
    fun exceptionShutsDownProvider() {
        val shutdowns = AtomicInteger(0)
        val m = LspManager(
            probe = BinaryProbe { true },
            providerFactory = ProviderFactory {
                object : Provider {
                    override fun start() = Unit
                    override fun request(context: LspRequestContext): List<LspItemMapping.LspShape> {
                        throw RuntimeException("hung")
                    }
                    override fun shutdown() { shutdowns.incrementAndGet() }
                }
            },
        )
        m.completions(LanguageType.C, "", emptyList(), limit = 10)
        assertEquals("hung provider is shut down before blacklisting", 1, shutdowns.get())
    }

    // ---------- fakes ----------

    private class StaticProvider(
        private val config: LspServerConfig,
        private val items: List<LspItemMapping.LspShape>,
        private val onShutdown: () -> Unit = {},
    ) : Provider {
        override fun start() = Unit
        override fun request(context: LspRequestContext): List<LspItemMapping.LspShape> = items
        override fun shutdown() = onShutdown()
    }
}
