package com.codeci.ide

import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 50.1 — the core surfaces adopted the scale (source scan).
 *
 * "Six" until Phase 58.1 retired the first-run welcome (owner: *"first open is
 * the editor, on a snake sample"*); the five that remain are the ones a user
 * actually lives in, and they are the ones this scan holds to the ladder.
 *
 * The rules, exactly as implemented:
 * - every one of the five files imports [com.codeci.ide.ui.theme.CodecTokens];
 * - no raw `RoundedCornerShape(N.dp)` anywhere in the five (every radius is a
 *   token — the plan's "corners match" row);
 * - no raw `N.dp` at or under 48 in a padding/gap/size call (padding,
 *   PaddingValues, spacedBy, defaultMinSize, size, width, height,
 *   defaultElevation) — chrome at or under the touch floor is always a token;
 * - raw sizes ABOVE 48 are allowed (display art — hero icons, avatars — is
 *   not chrome and no design system scales it);
 * - borders and progress strokes (`border(`, `BorderStroke`, `strokeWidth`)
 *   keep raw values: a 1 dp hairline is a thickness, not a gap.
 */
class TokenAdoptionTest {

    private val coreFiles = listOf(
        "app/src/main/java/com/codeci/ide/ui/screens/EditorScreen.kt",
        "app/src/main/java/com/codeci/ide/ui/screens/FileManagerScreen.kt",
        "app/src/main/java/com/codeci/ide/ui/screens/ModulesScreen.kt",
        "app/src/main/java/com/codeci/ide/ui/screens/TerminalScreen.kt",
        "app/src/main/java/com/codeci/ide/ui/screens/SettingsScreen.kt",
    )

    private fun codeOf(path: String): String =
        RepoFiles.codeOnly(RepoFiles.mainSource(path).readText())

    @Test
    fun `every core file imports CodecTokens`() {
        for (path in coreFiles) {
            val raw = RepoFiles.mainSource(path).readText()
            assertTrue(
                "$path must import CodecTokens",
                raw.contains("import com.codeci.ide.ui.theme.CodecTokens"),
            )
        }
    }

    @Test
    fun `every core file reaches for the tokens`() {
        for (path in coreFiles) {
            val code = codeOf(path)
            val uses = Regex("""CodecTokens\.""").findAll(code).count()
            assertTrue(
                "$path imports CodecTokens but never uses it",
                uses >= 3,
            )
        }
    }

    @Test
    fun `no raw corner radius in the core files`() {
        // The percent/pill overload (`RoundedCornerShape(50)`) is a shape
        // choice, not a dp value: only the `.dp` form is raw radius.
        val rawRadius = Regex("""RoundedCornerShape\(\s*\d+\s*\.dp""")
        for (path in coreFiles) {
            val code = codeOf(path)
            assertTrue(
                "$path still shapes a corner with a raw N.dp: " +
                    rawRadius.find(code)?.value,
                !rawRadius.containsMatchIn(code),
            )
        }
    }

    @Test
    fun `no raw chrome literal at or under 48dp in the core files`() {
        val literal = Regex("""(\d+(?:\.\d+)?)\.dp\b""")
        val spacingCalls = listOf(
            "padding", "PaddingValues", "spacedBy", "defaultMinSize",
            "size", "width", "height", "defaultElevation", "RoundedCornerShape",
        )
        val thicknessCalls = listOf("border", "BorderStroke", "strokeWidth")
        val failures = mutableListOf<String>()
        for (path in coreFiles) {
            val code = codeOf(path)
            for (m in literal.findAll(code)) {
                val value = m.groupValues[1].toFloat()
                var before = code.substring(maxOf(0, m.range.first - 120), m.range.first)
                // A border's named `width =` argument is not a `.width(`
                // call: blank named args so thickness classifies honestly.
                before = before.replace(Regex("""(?<!\.)\b(width|height|size)\s*="""), " ")
                val lastSpacing = spacingCalls.maxOfOrNull { before.lastIndexOf(it) } ?: -1
                val lastThickness = thicknessCalls.maxOfOrNull { before.lastIndexOf(it) } ?: -1
                if (lastThickness > lastSpacing) continue // a hairline, not a gap
                if (lastSpacing < 0) {
                    // No recognised call nearby: either a computed expression
                    // (`(…).dp` has no digits, so it never matches) or a
                    // literal the pin does not understand — fail loud.
                    failures.add("$path: unclassified raw ${m.value}")
                    continue
                }
                if (value <= 48f) {
                    val line = code.substring(0, m.range.first).count { it == '\n' } + 1
                    failures.add("$path:$line: raw ${m.value} in chrome (must be a token)")
                } else {
                    val call = spacingCalls.firstOrNull { before.lastIndexOf(it) == lastSpacing }
                    if (call != "size" && call != "width" && call != "height") {
                        val line = code.substring(0, m.range.first).count { it == '\n' } + 1
                        failures.add("$path:$line: raw ${m.value} above the scale outside size/width/height")
                    }
                }
            }
        }
        assertTrue(
            "raw dp literals survive the token conversion:\n" + failures.joinToString("\n"),
            failures.isEmpty(),
        )
    }

    @Test
    fun `the hub tree indent keeps its formula on tokens`() {
        val code = codeOf(
            "app/src/main/java/com/codeci/ide/ui/screens/FileManagerScreen.kt",
        )
        assertTrue(
            "the depth-computed indent must stay a formula, in token steps",
            code.contains("node.depth * Space.XL"),
        )
    }

    @Test
    fun `the packages copy button rides the touch floor`() {
        val code = codeOf(
            "app/src/main/java/com/codeci/ide/ui/screens/ModulesScreen.kt",
        )
        assertTrue(
            "the 24dp copy-button fix must read MIN_TOUCH, not a new literal",
            code.contains("MIN_TOUCH"),
        )
    }
}
