package com.codeci.ide

import com.codeci.ide.ui.editor.TabClosePolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 60 — "Close unmodified"'s two laws, pure.
 *
 * The dirty test itself lives in the ViewModel (the active tab's buffer stash
 * is deliberately stale between boundaries, so only the editor knows), which is
 * exactly why the part below it is pure: given the clean tabs, which of them may
 * go. The answer must never include the active tab — that is the editor's
 * "one buffer stays alive" invariant — and it must never touch a tab the caller
 * did not mark clean.
 */
class TabClosePolicyTest {

    @Test
    fun `with everything clean the active tab is the one that stays`() {
        val clean = listOf("a.kt", "b.kt", "c.kt")
        assertEquals(
            listOf("a.kt", "c.kt"),
            TabClosePolicy.unmodifiedTargets(clean, activePath = "b.kt"),
        )
    }

    @Test
    fun `a dirty active tab keeps only itself`() {
        // The active tab is dirty, so it is not in the clean list at all.
        val clean = listOf("a.kt", "c.kt")
        assertEquals(
            listOf("a.kt", "c.kt"),
            TabClosePolicy.unmodifiedTargets(clean, activePath = "b.kt"),
        )
    }

    @Test
    fun `a clean active tab beside dirty neighbours closes nothing`() {
        assertEquals(
            emptyList<String>(),
            TabClosePolicy.unmodifiedTargets(listOf("b.kt"), activePath = "b.kt"),
        )
    }

    @Test
    fun `only the tabs the caller marked clean are ever taken`() {
        val targets = TabClosePolicy.unmodifiedTargets(listOf("a.kt"), activePath = "a.kt")
        assertTrue("a dirty tab must never appear here: the list is the clean one", targets.isEmpty())
    }

    @Test
    fun `no active tab means every clean tab goes`() {
        assertEquals(
            listOf("a.kt", "b.kt"),
            TabClosePolicy.unmodifiedTargets(listOf("a.kt", "b.kt"), activePath = null),
        )
    }

    @Test
    fun `the order of the strip is preserved`() {
        val clean = listOf("z.kt", "a.kt", "m.kt", "b.kt")
        assertEquals(
            listOf("z.kt", "a.kt", "b.kt"),
            TabClosePolicy.unmodifiedTargets(clean, activePath = "m.kt"),
        )
    }
}
