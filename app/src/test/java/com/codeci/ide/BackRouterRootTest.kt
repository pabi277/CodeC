package com.codeci.ide

import com.codeci.ide.ui.navigation.BackRouter
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 49.1/49.2 — `isRoot`, the pure "am I at a root tab" decision the
 * exit prompt is now made from (PART_49_2), with the exact route patterns
 * `Screen.kt` builds and `MainActivity` passes (`screens.map { it.route }`).
 *
 * The parameterised cases are the trap this function exists for: Navigation
 * reports a destination's PATTERN as its route, so `editor?projectName=…`
 * and `terminal?cmd=…` must both answer "root", and a substring startsWith
 * would be wrong (the same trap the 45.2 replay idiom worked around).
 */
class BackRouterRootTest {

    /**
     * Exactly what `MainActivity` passes since Phase 56: the **four bottom-bar
     * patterns plus the Projects route**. The bar lost the Projects option (owner:
     * *“only removing the project option is ok”*) but the SCREEN is still a room —
     * the router and the bar stopped sharing one list, because they were never
     * answering the same question.
     */
    private val tabPatterns = listOf(
        "editor?projectName={projectName}&fileName={fileName}&single={single}",
        "terminal?cmd={cmd}&nonce={nonce}",
        "modules",
        "settings",
        "file_manager?openSheet={openSheet}"
    )

    @Test
    fun `the four tab roots and the Projects room are roots - plain and parameterised`() {
        assertTrue(BackRouter.isRoot("file_manager", tabPatterns))
        assertTrue(BackRouter.isRoot("file_manager?openSheet=1", tabPatterns))
        assertTrue(BackRouter.isRoot("editor", tabPatterns))
        assertTrue(
            BackRouter.isRoot(
                "editor?projectName=demo_flask&fileName=app.py&single=1",
                tabPatterns
            )
        )
        assertTrue(BackRouter.isRoot("terminal?nonce=1726200000000", tabPatterns))
        assertTrue(BackRouter.isRoot("terminal?cmd=pwd&nonce=1", tabPatterns))
        assertTrue(BackRouter.isRoot("modules", tabPatterns))
        assertTrue(BackRouter.isRoot("settings", tabPatterns))
    }

    @Test
    fun `Projects stays a root even though it left the bar`() {
        // Phase 56 — the tab is gone, the room is not: `file_manager` and its
        // parameterised forms still answer "root", so a phone that starts there
        // gets the exit prompt instead of a silent exit.
        assertTrue(BackRouter.isRoot("file_manager", tabPatterns))
        assertTrue(BackRouter.isRoot("file_manager?openSheet=1", tabPatterns))
    }

    @Test
    fun `non-tab routes are not roots`() {
        assertFalse(
            BackRouter.isRoot(
                "preview?projectName=demo&fileName=index.html&url=",
                tabPatterns
            )
        )
        assertFalse(BackRouter.isRoot("templates", tabPatterns))
        assertFalse(BackRouter.isRoot("logs", tabPatterns))
        assertFalse(BackRouter.isRoot("feedback", tabPatterns))
        assertFalse(BackRouter.isRoot("feedback?rating=5&crash=1", tabPatterns))
    }

    @Test
    fun `no route or an unknown route is not a root`() {
        assertFalse(BackRouter.isRoot(null, tabPatterns))
        assertFalse(BackRouter.isRoot("", tabPatterns))
        assertFalse(BackRouter.isRoot("does_not_exist", tabPatterns))
    }

    @Test
    fun `query text can never forge a root`() {
        // ?settings as an ARGUMENT VALUE of another route must not flip the
        // decision — the comparison is on the base segment, equality, not
        // substring.
        assertFalse(
            BackRouter.isRoot("preview?url=https://x/settings", tabPatterns)
        )
        assertFalse(
            BackRouter.isRoot("unknown?route=modules", tabPatterns)
        )
    }
}
