package com.codeci.ide

import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 47.2 — the single-lever invariant (PART_47_2 §Tests): sora's
 * `setSoftKeyboardEnabled(` appears only in EditorScreen.kt, at the two known
 * sites — the follow-the-CodeC-Keys lever and the dispose restore. No future
 * feature may switch the system IME from somewhere new.
 */
class ImeLeverTest {

    @Test
    fun `setSoftKeyboardEnabled appears only in EditorScreen at the two known sites`() {
        val hits = RepoFiles.mainKotlinSources()
            .filter { it.readText().contains("setSoftKeyboardEnabled(") }
            .map { it.path.substringAfterLast('/') }
        assertTrue(
            "the IME lever must live in exactly one file (got: $hits)",
            hits == listOf("EditorScreen.kt")
        )
        val editor = RepoFiles.mainSource(
            "app/src/main/java/com/codeci/ide/ui/screens/EditorScreen.kt"
        ).readText()
        val count = Regex("""setSoftKeyboardEnabled\(""").findAll(editor).count()
        assertTrue(
            "exactly two levers: the codecKeysUp effect and the dispose restore (got $count)",
            count == 2
        )
        assertTrue(
            "the lever must keep following CodeC Keys (not the new default)",
            editor.contains("soraEditor.setSoftKeyboardEnabled(!codecKeysUp)")
        )
        assertTrue(
            "leaving the editor must restore the system IME for other apps",
            editor.contains("onDispose { soraEditor.setSoftKeyboardEnabled(true) }")
        )
    }
}
