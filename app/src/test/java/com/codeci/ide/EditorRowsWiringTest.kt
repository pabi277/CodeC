package com.codeci.ide

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 57.2 — the two rows above the system keyboard, and the caret's handle.
 *
 * Everything here is a *wiring* pin: the pure decisions live in
 * `StripContext`/`KeysStayPolicy` (pinned by `StripContextTest`) and the layout
 * decisions live in `EditorScreen.kt`, so this file's job is the thing a
 * screenshot would otherwise be the only witness of — that the screen really
 * docks the touch row with the keyboard down (`docs/reference/spck-ui` 122157), really
 * lifts it above the IME again when the keyboard is up (124105), really yields
 * the status line to the IME, and that the caret's drop is **sora's own handle,
 * styled**, never a second caret stacked on the editor.
 *
 * Phase 69.1 added the row's REACH: the caps row's horizontal position is owned
 * by the screen, so it survives the keyboard and the strip's context swaps
 * (owner Q3 = A: remember the position, change nothing about the caps).
 *
 * Phase 69.2 added the row's LANGUAGE: the file's own quick keys lead it and a
 * language change returns it to its head (owner: *"What language i am using
 * don't matter it always give me same fixed quick keys"*).
 */
class EditorRowsWiringTest {

    private val editor = RepoFiles.mainSource(
        "app/src/main/java/com/codeci/ide/ui/screens/EditorScreen.kt"
    ).readText()

    private val host = RepoFiles.mainSource(
        "app/src/main/java/com/codeci/ide/ui/editor/sora/SoraEditorHost.kt"
    ).readText()

    private val palette = RepoFiles.mainSource(
        "app/src/main/java/com/codeci/ide/ui/theme/CodecPalette.kt"
    ).readText()

    @Test
    fun `the touch row is docked with the keyboard down and rides the keyboard when it is up`() {
        // 122157: with no keyboard the row sits at the bottom of the column
        // (above the app's own bottom bar). 124105: with the keyboard up the
        // same row is the last child of the imePadding()'d column.
        // Phase 70.1 — Q3: the row is gated on `stripVisible` now
        // (editor keys OR the run-keys row a waiting program needs), so a
        // program waiting for stdin no longer takes the strip away with it.
        assertTrue(
            "the docked touch row (keyboard down) is gone",
            editor.contains("if (stripVisible && !imeVisible) {"),
        )
        assertTrue(
            "the keyboard-anchored touch row (IME up) is gone",
            editor.contains("if (stripVisible && imeVisible) {"),
        )
        assertTrue(
            "the run-keys row must survive the stdin yield (Q3)",
            editor.contains("val runStripVisible = KeysStayPolicy.isRunStripVisible(") &&
                editor.contains("val stripVisible = keysVisible || runStripVisible"),
        )
    }

    @Test
    fun `the status line yields the row to the keyboard`() {
        assertTrue(
            "the status line must not be drawn under a raised keyboard",
            editor.contains("if (caretPlaced && !imeVisible && !codecKeysUp) {"),
        )
        assertTrue(
            "the editor surface must agree with the status line's own gate",
            editor.contains("statusVisible = caretPlaced && !imeVisible && !codecKeysUp"),
        )
    }

    @Test
    fun `the chip row is gated on a typing surface`() {
        assertTrue(
            "the screen must tell the strip resolver when a keyboard is up",
            editor.contains("typingSurfaceUp = imeVisible || codecKeysUp"),
        )
    }

    @Test
    fun `the coding row's horizontal position is owned above the row`() {
        // Phase 69.1 (owner Q3 = A). The row is composed at TWO call sites
        // (keyboard down / keyboard up) and in three branches of the one strip
        // (Keys | Suggestions | Run); a `rememberScrollState()` inside the row
        // therefore died on every keyboard toggle and every chip appearance,
        // snapping the caps a thumb reaches for back out of reach. One state,
        // owned by the screen, handed to the row.
        assertTrue(
            "the screen must own one scroll state for the coding row",
            editor.contains("val keysRowScroll = rememberScrollState()"),
        )
        assertEquals(
            "both strip call sites must pass the SAME row position",
            2,
            Regex("""keysRowScroll = keysRowScroll,""").findAll(editor).count(),
        )
        assertTrue(
            "the strip must hand the state to the row",
            editor.contains("scrollState = keysRowScroll,"),
        )

        val keysRow = RepoFiles.mainSource(
            "app/src/main/java/com/codeci/ide/ui/components/EditorKeysRow.kt"
        ).readText()
        assertTrue(
            "EditorKeysRow must take the caller's scroll state",
            keysRow.contains("scrollState: ScrollState = rememberScrollState()"),
        )
        assertTrue(
            "the caps row must scroll through the owned state",
            keysRow.contains("horizontalScroll(scrollState)"),
        )
        val capsRowBody = keysRow.substringBefore("fun RunKeysRow(")
        assertFalse(
            "the caps row must not own a private scroll state again",
            capsRowBody.contains("horizontalScroll(rememberScrollState())"),
        )
        // Deliberately NOT shared: the run keys and the suggestion chips are
        // different content, and 57.2's row geometry stays theirs.
        val runRowBody = keysRow.substringAfter("fun RunKeysRow(")
        assertTrue(
            "the run keys keep their own row position",
            runRowBody.contains("horizontalScroll(rememberScrollState())"),
        )
    }

    @Test
    fun `the row starts at its head when the file language changes`() {
        // Phase 69.2 — the row LEADS with the file language's caps now (owner:
        // "What language i am using don't matter it always give me same fixed
        // quick keys"), so a language change returns the row to offset 0: the
        // remembered 69.1 offset belonged to the previous file's caps. Within
        // one file the owner's Q3 = A ("remember the row's horizontal
        // position") still stands.
        assertTrue(
            "the row must open at its head for a new language",
            editor.contains("LaunchedEffect(language) { keysRowScroll.scrollTo(0) }"),
        )
        assertTrue(
            "the row's language is the file's own extension, detected in one place",
            editor.contains("LanguageType.fromFileName(activeTabPath ?: currentFileName)"),
        )
    }

    @Test
    fun `the caret's drop is sora's own handle, styled`() {
        assertTrue(
            "the insert handle must be the reference's drop, not sora's default side tab",
            host.contains("setSelectionHandleStyle(HandleStyleDrop("),
        )
        assertTrue(
            "a fresh scheme resets custom colours: the handle's colour must ride the theme switch",
            host.contains("editor.colorScheme.setColor(EditorColorScheme.SELECTION_HANDLE, CodecPalette.CARET_HANDLE)"),
        )
        assertTrue(
            "the handle's colour is a palette role, never a literal at the call site",
            palette.contains("const val CARET_HANDLE"),
        )
        assertFalse(
            "no second caret may be stacked on the editor (only sora's own handle)",
            RepoFiles.codeOnly(host).contains("CursorHandle"),
        )
    }
}
