package com.codeci.ide

import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 50.1 — every icon button in the core surfaces keeps a ≥ 48 dp
 * touch target (source scan).
 *
 * M3's `IconButton` reserves the 48 dp minimum by itself
 * (`minimumInteractiveComponentSize`); what this pins is that no call site
 * in the core files shrinks it back with an explicit small `size(` /
 * `requiredSize(` / `defaultMinSize(` in the button's own header. (The one
 * that did — the Packages command-snippet copy button at 24 dp — now reads
 * `CodecTokens.MIN_TOUCH`; `TokenAdoptionTest` pins the token.)
 *
 * Out of scope on purpose: menu-row and text-field glyphs (the terminal
 * session Edit/Close icons, the clone-dialog QR glyph) are 20–22 dp *glyphs*
 * inside taller rows — growing the glyph would not grow the row's target,
 * and the row is the target. The output panel's 36 dp header buttons belong
 * to 51.2's editor-surface pass.
 */
class TouchTargetTest {

    private val coreFiles = listOf(
        "EditorScreen.kt",
        "FileManagerScreen.kt",
        "ModulesScreen.kt",
        "TerminalScreen.kt",
        "SettingsScreen.kt",
    )

    /** The call header: from the opening paren to its match. */
    private fun callHeader(code: String, callAt: Int): String {
        var depth = 0
        for (j in code.indexOf('(', callAt) until code.length) {
            when (code[j]) {
                '(' -> depth++
                ')' -> {
                    depth--
                    if (depth == 0) return code.substring(callAt, j + 1)
                }
            }
        }
        error("unbalanced parens after index $callAt")
    }

    @Test
    fun `no IconButton in the core files shrinks below 48dp`() {
        val smallBox = Regex("""\.(size|requiredSize|defaultMinSize)\(\s*(\d+)""")
        val failures = mutableListOf<String>()
        for (name in coreFiles) {
            val code = RepoFiles.codeOnly(
                RepoFiles.mainSource(
                    "app/src/main/java/com/codeci/ide/ui/screens/$name",
                ).readText(),
            )
            for (m in Regex("""\bIconButton\s*\(""").findAll(code)) {
                val header = callHeader(code, m.range.first)
                for (box in smallBox.findAll(header)) {
                    if (box.groupValues[2].toInt() < 48) {
                        val line = code.substring(0, m.range.first).count { it == '\n' } + 1
                        failures.add("$name:$line: IconButton ${box.value}… (< 48dp)")
                    }
                }
            }
        }
        assertTrue(
            "IconButtons with small explicit targets:\n" + failures.joinToString("\n"),
            failures.isEmpty(),
        )
    }

    @Test
    fun `the scan really visits buttons`() {
        // A pin that never matches is a pin that never fails: the core files
        // held twenty IconButtons before Phase 71.1 (6 editor + 5 hub + 2 packages +
        // 6 terminal + 1 settings — the first-run welcome held none, and Phase
        // 58.1 retired that screen). Settings' first one is Phase 62's ✕: the
        // search field offers it only when there is something to clear, and it
        // is an IconButton like every other one here, so the 48 dp rule holds.
        // The editor's count went
        // 3 → 4 in Phase 57.1, and 4 → 5 in Phase 68.1 and 5 → 6 with sort door for compact Spck parity:
        // the Test ▷ Row became an IconButton to save horizontal space, the top
        // bar became a 48dp Row (hamburger + search + run + optional test), and
        // the tab row's trailing cell kept the editor-menu IconButton. Phase 71.1 took the total 20 → 21 (packages 2 → 3): the 📌 on a package card is an IconButton like every other, so the 48 dp rule holds. A phase
        // that changes this number again must say why here, not just edit the digit.
        var total = 0
        for (name in coreFiles) {
            val code = RepoFiles.codeOnly(
                RepoFiles.mainSource(
                    "app/src/main/java/com/codeci/ide/ui/screens/$name",
                ).readText(),
            )
            total += Regex("""\bIconButton\s*\(""").findAll(code).count()
        }
        assertTrue(
            "expected 21 IconButtons across the core files, found $total",
            total == 21,
        )
    }
}
