package com.codeci.ide

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 48 device round — the CodeC Keys blink fix's wiring pins: the sora
 * replay must TRY the incremental delta first and keep the atomic `setText`
 * exactly as the one fallback (its 2026-09-06 crash story is load-bearing).
 * Source-scanned, same role as `BackHandlerWiringTest` / `CaretCallSiteTest`.
 */
class ReplayPathWiringTest {

    private val host: String
        get() = RepoFiles.mainSource(
            "app/src/main/java/com/codeci/ide/ui/editor/sora/SoraEditorHost.kt"
        ).readText()

    @Test
    fun `the incremental attempt precedes the atomic fallback`() {
        val attempt = host.indexOf("IncrementalEdit.between(known, target.text)")
        val atomic = host.indexOf("ed.setText(target.text)")
        assertTrue("the planner must be consulted", attempt > 0)
        assertTrue("the atomic fallback must exist", atomic > 0)
        assertTrue(
            "the incremental attempt must come first",
            attempt < atomic
        )
    }

    @Test
    fun `the first replay stays atomic - the listener must follow the new Content`() {
        assertTrue(
            "no plan when sora holds nothing yet (known == null)",
            host.contains("val plan = if (known != null) IncrementalEdit.between(known, target.text) else null")
        )
    }

    @Test
    fun `exactly one setText call site - the atomic fallback`() {
        assertEquals(
            1,
            Regex("ed\\.setText\\(").findAll(host).count()
        )
    }

    @Test
    fun `the incremental application is one Content replace, crash-guarded`() {
        assertTrue(
            host.contains("ed.text.replace(plan.start, plan.end, plan.replacement)")
        )
        // A failed delta must recover through the atomic path, never crash:
        // the VM text is the source of truth.
        val attemptBlock = host.substring(
            host.indexOf("val appliedIncrementally"),
            host.indexOf("if (!appliedIncrementally)")
        )
        assertTrue(attemptBlock.contains("runCatching"))
        assertTrue(attemptBlock.contains(".getOrDefault(false)"))
    }

    @Test
    fun `the atomic fallback still re-attaches the content listener`() {
        val atomicBlock = host.substring(
            host.indexOf("if (!appliedIncrementally)"),
            host.indexOf("Opening a file must not replay")
        )
        assertTrue(atomicBlock.contains("ed.setText(target.text)"))
        assertTrue(atomicBlock.contains("ed.text.addContentListener(contentListener)"))
    }
}
