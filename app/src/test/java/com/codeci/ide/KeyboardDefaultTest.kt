package com.codeci.ide

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 47.2 — the keyboard default (PART_47_2 §Tests). Host source-scan +
 * policy shape: the store's default for an ABSENT `codec_keys_enabled` key is
 * `false`; the `?: false` shape IS the semantics (a stored `true` still reads
 * `true`, a stored `false` reads `false` — DataStore's map does exactly that);
 * and no code writes the key at startup (the "never write the default" rule,
 * so an upgrader's stored choice can never be clobbered).
 */
class KeyboardDefaultTest {

    private val settingsManager: String
        get() = RepoFiles.mainSource(
            "app/src/main/java/com/codeci/ide/ui/settings/SettingsManager.kt"
        ).readText()

    private fun mainSources(): String =
        RepoFiles.mainKotlinSources().joinToString("\n") { it.readText() }

    @Test
    fun `the absent-key default is false - the system keyboard is the default`() {
        assertTrue(
            "codecKeysEnabledFlow must read CODEC_KEYS_ENABLED] ?: false",
            settingsManager.contains("it[CODEC_KEYS_ENABLED] ?: false")
        )
        assertFalse(
            "the old default must not survive anywhere in the flow",
            settingsManager.contains("it[CODEC_KEYS_ENABLED] ?: true")
        )
    }

    @Test
    fun `the stored-value-wins shape is intact - one map, one elvis, no migration`() {
        // Exactly one write path exists (the Settings switch's setter); the
        // flow is a plain map of the store. Together these ARE the
        // compatibility promise: absent → false, stored → its own value.
        val setterCalls = Regex("""setCodecKeysEnabled\(v: Boolean\)""").findAll(settingsManager).count()
        assertEquals(1, setterCalls)
    }

    @Test
    fun `no code writes the key at startup`() {
        // The ONLY call sites of setCodecKeysEnabled( are the Settings
        // switch's onCheckedChange — never an init, an Application, or an
        // Activity onCreate.
        val main = mainSources()
        val sites = Regex("""settingsManager\.setCodecKeysEnabled\(""").findAll(main).count()
        assertEquals(
            "setCodecKeysEnabled( must only be called from the Settings switch",
            1,
            sites
        )
        assertTrue(
            "the only call site is SettingsScreen's switch",
            RepoFiles.mainSource("app/src/main/java/com/codeci/ide/ui/screens/SettingsScreen.kt")
                .readText().contains("settingsManager.setCodecKeysEnabled(")
        )
    }

    @Test
    fun `no comment claims DEFAULT ON any more`() {
        // A stale "DEFAULT ON" comment next to a `?: false` is how the next
        // agent re-breaks the default (PART_47_2 §2).
        assertFalse(
            Regex("""DEFAULT ON""").containsMatchIn(settingsManager)
        )
    }

    @Test
    fun `the editor's first frame matches the store default`() {
        val editor = RepoFiles.mainSource(
            "app/src/main/java/com/codeci/ide/ui/screens/EditorScreen.kt"
        ).readText()
        assertTrue(
            "EditorScreen collects codecKeysEnabledFlow with initial = false",
            editor.contains("codecKeysEnabledFlow.collectAsState(initial = false)")
        )
    }
}
