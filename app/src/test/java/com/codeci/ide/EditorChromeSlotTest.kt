package com.codeci.ide

import com.codeci.ide.ui.editor.EditorChrome
import com.codeci.ide.ui.editor.EditorChromeSlot
import com.codeci.ide.ui.theme.CodecTokens
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 51.2 — the editor chrome is declared, not ad hoc.
 *
 * The slots, their order and their token gaps are the pure [EditorChrome]
 * declaration; this test pins that the real screen carries the matching marker
 * per slot, in the declared order — so a future edit that moves a chrome piece
 * fails here instead of quietly changing the code view's height (the one
 * measurement Phase 48's caret policy keys on).
 *
 * Phase 57.1 — the reference (docs/spck-ui 122157/124105) re-shaped the top of
 * that chrome, and this file is where the change is *pinned*, not merely made:
 * the run action is a bare green ▶ (no label), the top row carries NO overflow
 * icon, the tab row is a row of its own below the bar, and the retired ⋮'s
 * list lives in the tab row's trailing cell — so the controls it holds (undo,
 * save, format, rename, line endings, the launch default) stay reachable. The
 * roadmap's exit also asks for one outside guarantee: the bottom bar is still
 * composed.
 */
class EditorChromeSlotTest {

    private val editor = RepoFiles.mainSource(
        "app/src/main/java/com/codeci/ide/ui/screens/EditorScreen.kt"
    ).readText()

    private val main = RepoFiles.mainSource(
        "app/src/main/java/com/codeci/ide/MainActivity.kt"
    ).readText()

    /** The editor's top row, from `actions = {` to the bar's own close. */
    private fun topRowActions(): String {
        val start = editor.indexOf("                actions = {")
        val end = editor.indexOf("                colors = TopAppBarDefaults.topAppBarColors(", start)
        assertTrue("the top row's actions block moved", start > 0 && end > start)
        return editor.substring(start, end)
    }

    /** The tab row's own block, below the app bar. */
    private fun tabRow(): String {
        val start = editor.indexOf("            // Phase 57.1 — the shots' second row")
        // The row's own block runs to the next declared slot. The first cut of
        // this helper anchored on a `// Phase 16 mockup-exact` comment that no
        // longer exists on this checkout — CI round 1 caught it ("the tab row's
        // block moved"), so the end anchor is now a marker the phase system
        // keeps alive rather than a comment someone can tidy away.
        val end = editor.indexOf("            // Phase 51.2 slot: find_bar", start)
        assertTrue("the tab row's block moved", start > 0 && end > start)
        return editor.substring(start, end)
    }

    @Test
    fun `the chrome declares eight named slots`() {
        assertEquals(8, EditorChromeSlot.entries.size)
        assertEquals(EditorChromeSlot.entries.toList(), EditorChrome.order)
    }

    @Test
    fun `every slot carries its marker in the screen`() {
        for (slot in EditorChromeSlot.entries) {
            assertTrue(
                "$slot has no marker in EditorScreen.kt",
                editor.contains("// " + EditorChrome.markerFor(slot)),
            )
        }
    }

    @Test
    fun `the markers appear in the order the chrome declares them`() {
        var cursor = 0
        for (slot in EditorChrome.order) {
            val marker = "// " + EditorChrome.markerFor(slot)
            val index = editor.indexOf(marker)
            assertTrue("missing marker $marker", index > 0)
            assertTrue(
                "the chrome's order changed: $slot (${slot.marker}) appears before the previous slot",
                index > cursor,
            )
            cursor = index
        }
    }

    @Test
    fun `the RUN action is its own slot`() {
        val runIndex = editor.indexOf("// " + EditorChrome.markerFor(EditorChromeSlot.RUN_ACTION))
        assertTrue(runIndex > 0)
        val region = editor.substring(runIndex, minOf(editor.length, runIndex + 2_000))
        assertTrue("the RUN slot must hold the RUN control", region.contains("onRunTap"))
    }

    @Test
    fun `every slot gap is a step of the one scale`() {
        val ladder = setOf(
            CodecTokens.Space.NONE,
            CodecTokens.Space.XXS,
            CodecTokens.Space.XS,
            CodecTokens.Space.S,
            CodecTokens.Space.M,
            CodecTokens.Space.L,
            CodecTokens.Space.XL,
            CodecTokens.Space.XXL,
            CodecTokens.Space.HUGE,
        )
        for (slot in EditorChromeSlot.entries) {
            assertTrue(
                "$slot has an off-scale gap ${EditorChrome.gapFor(slot)}",
                EditorChrome.gapFor(slot) in ladder,
            )
        }
    }

    @Test
    fun `the RUN control is the bare green triangle of the shots`() {
        val runIndex = editor.indexOf("// " + EditorChrome.markerFor(EditorChromeSlot.RUN_ACTION))
        val region = editor.substring(runIndex, minOf(editor.length, runIndex + 4_000))
        assertTrue("RUN is one glyph button", region.contains("IconButton("))
        assertTrue("the glyph is the triangle the shots draw", region.contains("Icons.Default.PlayArrow"))
        assertTrue("the colour decision comes from RunButtonStyle", region.contains("RunButtonStyle.roleFor(runButtonState)"))
        assertTrue(
            "the green belongs to the hero role alone (a locked or quiet control falls back)",
            region.contains("runRole == RunButtonRole.HERO") && region.contains("RunGreen"),
        )
        assertTrue(
            "the running state still answers 'did my tap work?'",
            region.contains("RunButtonStyle.showsRunning(runButtonState)"),
        )
        assertTrue(
            "RUN sits on the touch floor",
            region.contains("defaultMinSize(minHeight = CodecTokens.space(CodecTokens.MIN_TOUCH))"),
        )
    }

    @Test
    fun `the top row paints no RUN label`() {
        val actions = topRowActions()
        assertFalse(
            "the shots' top row has no RUN word: the label must not be painted as text",
            actions.contains("R.string.run_running") ||
                Regex("""Text\(\s*text = if \(RunButtonStyle\.showsRunning""").containsMatchIn(actions),
        )
        assertTrue(
            "the word survives for TalkBack (the pixels drop it, the accessibility tree keeps it)",
            actions.contains("stringResource(R.string.run)"),
        )
        assertTrue(
            "the tour's anchor and its one-tap click must survive the restyle",
            actions.contains("GuideAnchors.EDITOR_RUN") && actions.contains("onClick = onGuideRunTap"),
        )
    }

    @Test
    fun `the top row has no overflow icon`() {
        val actions = topRowActions()
        assertFalse("the top row's overflow is retired by the shots", actions.contains("MoreVert"))
        assertFalse("the top row's overflow is retired by the shots", actions.contains("showMoreMenu = true"))
        assertTrue(
            "the search control is the top row's one trailing icon",
            actions.contains("Icons.Default.Search"),
        )
    }

    @Test
    fun `the tab row is its own row and owns the editor menu`() {
        val row = tabRow()
        assertTrue("the tab strip must be in the tab row", row.contains("EditorTabBar("))
        assertTrue(
            "the retired overflow's list lives in the tab row's trailing cell",
            row.contains("showMoreMenu = true") && row.contains("DropdownMenu("),
        )
        assertTrue(
            "the cell wears the clean-room glyph drawn from the shot",
            row.contains("SpckIcons.EditorMenu"),
        )
        assertTrue(
            "the tab row sits BELOW the bar: the top row's name can never be taken by a tab",
            editor.indexOf("                title = {") < editor.indexOf("Phase 57.1 — the shots' second row"),
        )
    }

    @Test
    fun `the bottom bar is still composed`() {
        assertTrue(
            "Phase 56's law: the bar keeps its four tabs — only Projects left it",
            main.contains("!hideNav -> FlatBottomBar("),
        )
    }

    @Test
    fun `every minimum height in the file comes from the one scale`() {
        val minimums = Regex("""defaultMinSize\(([^\n]*)\)""")
            .findAll(editor)
            .map { it.groupValues[1].trim() }
            .toList()
        assertTrue("RUN's own minimum must exist", minimums.isNotEmpty())
        for (value in minimums) {
            assertTrue(
                "an off-token minimum height appeared: $value",
                value.contains("CodecTokens.space(CodecTokens.MIN_TOUCH)"),
            )
        }
    }

    @Test
    fun `the RUN slot itself sits on the touch floor`() {
        val start = editor.indexOf("// " + EditorChrome.markerFor(EditorChromeSlot.RUN_ACTION))
        val end = editor.indexOf("// " + EditorChrome.markerFor(EditorChromeSlot.FIND_BAR))
        val region = editor.substring(start, end)
        assertEquals(
            "the hero must declare the touch floor exactly once",
            1,
            Regex("""defaultMinSize\(\s*minHeight\s*=\s*CodecTokens\.space\(CodecTokens\.MIN_TOUCH\)\)""")
                .findAll(region).count(),
        )
    }
}
