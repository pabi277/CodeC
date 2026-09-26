package com.codeci.ide

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 60 — the tab menu's new rows, wired end to end.
 *
 * The pure halves live in `TabSortPolicyTest` / `TabClosePolicyTest` and the
 * bar-hide rule in `NavBarPolicyTest`. This file pins the parts only the real
 * source can witness: that the menu really offers *Close unmodified*, *Hide
 * tabs* and the three sorts (and every sort really has a label); that *Close
 * unmodified* runs through the policy instead of a second, hand-rolled rule;
 * that the sorts re-order the open tabs without moving the active one; that
 * *Hide tabs* folds the tab row into the reveal strip while the editor's own ⋮
 * cell stays composed; and that the same flag parks the bottom bar, whose own
 * reveal handle writes it back.
 */
class TabMenuWiringTest {

    private fun source(relativePath: String) =
        RepoFiles.mainSource(relativePath).readText()

    private val bar = source(
        "app/src/main/java/com/codeci/ide/ui/components/EditorTabBar.kt"
    )

    private val editor = source(
        "app/src/main/java/com/codeci/ide/ui/screens/EditorScreen.kt"
    )

    private val main = source("app/src/main/java/com/codeci/ide/MainActivity.kt")

    private val viewModel = source(
        "app/src/main/java/com/codeci/ide/ui/viewmodels/EditorViewModel.kt"
    )

    /**
     * The body of `fun <name>` in [code], from its own opening `{`.
     *
     * The opening brace is the first one at Bracket depth zero: a parameter
     * default like `onCloseAll: () -> Unit = {}` would otherwise hand back a
     * two-character "body" (the first cut of this helper did exactly that).
     */
    private fun bodyOf(code: String, declaration: String): String {
        val start = code.indexOf("fun $declaration")
        assertTrue("$declaration is gone", start > 0)
        var bracket = 0
        var open = -1
        for (i in start until code.length) {
            when (code[i]) {
                '(', '[' -> bracket++
                ')', ']' -> bracket--
                '{' -> {
                    if (bracket == 0) {
                        open = i
                        break
                    }
                }
            }
        }
        assertTrue("$declaration has no body", open > start)
        var depth = 0
        for (i in open until code.length) {
            when (code[i]) {
                '{' -> depth++
                '}' -> {
                    depth--
                    if (depth == 0) return code.substring(open, i + 1)
                }
            }
        }
        error("unbalanced braces in $declaration")
    }

    @Test
    fun `the tab menu offers the spec's new rows`() {
        val menu = bodyOf(bar, "EditorTabBar(")
        assertTrue("Close unmodified is missing", menu.contains("R.string.tab_close_unmodified"))
        assertTrue("Hide tabs is missing", menu.contains("R.string.tab_hide"))
        assertTrue(
            "the sorts must come from the policy's own order, not a hand-typed list",
            menu.contains("TabSortPolicy.MENU_ORDER.forEach"),
        )
        assertTrue("the menu's sort rows are unwired", menu.contains("onSort(sort)"))
        assertTrue("Close unmodified is unwired", menu.contains("onCloseUnmodified()"))
        assertTrue("Hide tabs is unwired", menu.contains("onHideTabs()"))
    }

    @Test
    fun `every sort has its own label`() {
        val labels = bodyOf(bar, "of(sort:")
        val missing = Regex("""TabSort\.([A-Z_]+)""")
            .findAll(bar.substring(bar.indexOf("internal object TabSortLabels")))
            .map { it.groupValues[1] }
            .toSet()
        for (entry in listOf("NAME", "EXTENSION", "PATH")) {
            assertTrue("TabSort.$entry has no label", entry in missing)
            assertTrue("TabSort.$entry is not labelled in the menu", labels.contains("TabSort.$entry ->"))
        }
        assertEquals(
            "the label map must have exactly the three sorts",
            3,
            Regex("""TabSort\.[A-Z_]+ ->""").findAll(bar).count(),
        )
    }

    @Test
    fun `close unmodified runs through the policy, and never saves`() {
        val body = bodyOf(viewModel, "closeUnmodifiedTabs(")
        assertTrue(
            "the decision must be TabClosePolicy's, not a second rule",
            body.contains("TabClosePolicy.unmodifiedTargets("),
        )
        assertTrue(
            "a clean tab needs no save, so closeTab takes saveFirst = false",
            body.contains("closeTab(appContext, path, saveFirst = false)") ||
                body.contains("closeTab(context, path, saveFirst = false)"),
        )
        // The active tab's stash is stale by design: answer from the live flag.
        assertTrue(
            "the active tab's dirtiness must come from the live flag",
            body.contains("if (tab.relativePath == active) !_isDirty.value"),
        )
    }

    @Test
    fun `the sorts re-order the open tabs and leave the active one alone`() {
        val body = bodyOf(viewModel, "sortTabs(")
        assertTrue("the order must be the policy's", body.contains("TabSortPolicy.sort(_openTabs.value, sort)"))
        assertTrue("the re-ordered list must be kept", body.contains("_openTabs.value = reordered"))
        assertFalse(
            "a sort must never change which tab is active",
            body.contains("_activeTabPath.value ="),
        )
    }

    @Test
    fun `hide tabs folds the row into its strip`() {
        assertTrue(
            "the reveal strip must be a composable of its own",
            bar.contains("fun TabRowRevealStrip("),
        )
        assertTrue(
            "the strip says the same word as the bar's handle",
            bodyOf(bar, "TabRowRevealStrip(").contains("R.string.tab_show"),
        )
        val at = editor.indexOf("if (tabsHidden) {")
        assertTrue("the row does not answer Hide tabs", at > 0)
        val stripCall = editor.indexOf("TabRowRevealStrip(", at)
        val barCall = editor.indexOf("EditorTabBar(", at)
        assertTrue("the strip is not in the row", stripCall in (at + 1) until barCall)
        assertTrue(
            "the strip must carry the row's weight so the ⋮ cell keeps its edge",
            editor.substring(at, barCall).contains("Modifier.weight(1f)"),
        )
        assertTrue(
            "the editor's own action cell must sit OUTSIDE the fold: hiding the " +
                "tabs may never hide undo/save/format with them",
            editor.indexOf("showMoreMenu = true") > barCall,
        )
    }

    @Test
    fun `the flag is published once and cleared on the way out`() {
        assertTrue(
            "the editor must publish the flag",
            editor.contains("LaunchedEffect(tabsHidden) { EditorChromeState.setTabsHidden(tabsHidden) }"),
        )
        assertTrue(
            "a stale flag would greet the next visit with a parked row",
            editor.contains("EditorChromeState.setTabsHidden(false)"),
        )
        assertTrue(
            "Hide tabs comes from the menu",
            editor.contains("onHideTabs = { tabsHidden = true }"),
        )
        assertTrue(
            "the strip's tap brings the row back",
            editor.contains("TabRowRevealStrip(\n                                onReveal = { tabsHidden = false }"),
        )
    }

    @Test
    fun `the same flag parks the bottom bar, and its handle brings both back`() {
        assertTrue(
            "the scaffold never reads the flag",
            main.contains("val editorTabsHidden by EditorChromeState.tabsHidden.collectAsState()"),
        )
        assertTrue(
            "the bar's policy is not told",
            main.contains("hiddenByUser = editorTabsHidden"),
        )
        assertTrue(
            "the reveal handle must write the flag back",
            main.contains("EditorChromeState.setTabsHidden(false)"),
        )
        assertTrue(
            "one word for both reveal affordances",
            main.contains("text = stringResource(R.string.tab_show)"),
        )
    }

    @Test
    fun `the close rows are passed the view model's own operations`() {
        assertTrue(
            editor.contains("onCloseUnmodified = { viewModel.closeUnmodifiedTabs(context) }")
        )
        assertTrue(editor.contains("onSort = { sort -> viewModel.sortTabs(sort) }"))
    }
}
