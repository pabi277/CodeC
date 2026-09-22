package com.codeci.ide

import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 50.1 — every icon button in the six core surfaces keeps a ≥ 48 dp
 * touch target (source scan).
 *
 * M3's `IconButton` reserves the 48 dp minimum by itself
 * (`minimumInteractiveComponentSize`); what this pins is that no call site
 * in the six files shrinks it back with an explicit small `size(` /
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

    private val sixFiles = listOf(
        "WelcomeScreen.kt",
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
    fun `no IconButton in the six files shrinks below 48dp`() {
        val smallBox = Regex("""\.(size|requiredSize|defaultMinSize)\(\s*(\d+)""")
        val failures = mutableListOf<String>()
        for (name in sixFiles) {
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
        // A pin that never matches is a pin that never fails: the six files
        // hold seventeen IconButtons today (4 editor + 5 hub + 2 packages +
        // 6 terminal; welcome and settings hold none). The editor's count went
        // 3 → 4 in Phase 57.1, and this comment is the record the pin demands:
        // the top row's ⋮ `IconButton` left the bar, the RUN action became the
        // shots' bare green ▶ `IconButton`, and the tab row's trailing cell
        // added the editor-menu `IconButton` (+1 net). A phase that changes this
        // number again must say why here, not just edit the digit.
        var total = 0
        for (name in sixFiles) {
            val code = RepoFiles.codeOnly(
                RepoFiles.mainSource(
                    "app/src/main/java/com/codeci/ide/ui/screens/$name",
                ).readText(),
            )
            total += Regex("""\bIconButton\s*\(""").findAll(code).count()
        }
        assertTrue(
            "expected 17 IconButtons across the six files, found $total",
            total == 17,
        )
    }
}
