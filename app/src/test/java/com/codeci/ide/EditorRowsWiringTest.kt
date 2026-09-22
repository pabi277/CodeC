package com.codeci.ide

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
 * docks the touch row with the keyboard down (`docs/spck-ui` 122157), really
 * lifts it above the IME again when the keyboard is up (124105), really yields
 * the status line to the IME, and that the caret's drop is **sora's own handle,
 * styled**, never a second caret stacked on the editor.
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
        assertTrue(
            "the docked touch row (keyboard down) is gone",
            editor.contains("if (keysVisible && !imeVisible) {"),
        )
        assertTrue(
            "the keyboard-anchored touch row (IME up) is gone",
            editor.contains("if (keysVisible && imeVisible) {"),
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
