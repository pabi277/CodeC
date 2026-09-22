package com.codeci.ide

import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 50.3 — one icon size per role, and every action names itself
 * (source scan over the core files).
 *
 * - An `Icon(` sized with a raw `N.dp` fails at or under 48 (display heroes
 *   above the scale stay raw, like every other display size — see
 *   `TokenAdoptionTest`);
 * - the first icon inside an `IconButton`'s content must carry a non-null
 *   `contentDescription` (text-only buttons, like the terminal's session
 *   number, have no icon and are skipped);
 * - decorative icons keep declaring `contentDescription = null` explicitly
 *   (the hub's git glyph, the terminal's warning
 *   triangle all duplicate adjacent text — null is correct there).
 */
class IconRoleTest {

    private val coreFiles = listOf(
        "EditorScreen.kt",
        "FileManagerScreen.kt",
        "ModulesScreen.kt",
        "TerminalScreen.kt",
        "SettingsScreen.kt",
    )

    private fun codeOf(name: String): String = RepoFiles.codeOnly(
        RepoFiles.mainSource(
            "app/src/main/java/com/codeci/ide/ui/screens/$name",
        ).readText(),
    )

    /** Source from the call's opening paren through its match. */
    private fun parenSlice(code: String, callAt: Int): String {
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

    /** The trailing `{ … }` lambda after a call header, or null. */
    private fun trailingLambda(code: String, headerEnd: Int): String? {
        var k = headerEnd
        while (k < code.length && code[k].isWhitespace()) k++
        if (k >= code.length || code[k] != '{') return null
        var depth = 0
        for (j in k until code.length) {
            when (code[j]) {
                '{' -> depth++
                '}' -> {
                    depth--
                    if (depth == 0) return code.substring(k, j + 1)
                }
            }
        }
        error("unbalanced braces after index $headerEnd")
    }

    @Test
    fun `icon sizes in the core files are tokens`() {
        val failures = mutableListOf<String>()
        for (name in coreFiles) {
            val code = codeOf(name)
            for (m in Regex("""\bIcon\s*\(""").findAll(code)) {
                val slice = parenSlice(code, m.range.first)
                for (size in Regex("""\.size\(\s*(\d+)""").findAll(slice)) {
                    if (size.groupValues[1].toInt() <= 48) {
                        val line = code.substring(0, m.range.first).count { it == '\n' } + 1
                        failures.add("$name:$line: Icon sized with raw ${size.value}…")
                    }
                }
            }
        }
        assertTrue(
            "raw icon sizes:\n" + failures.joinToString("\n"),
            failures.isEmpty(),
        )
    }

    @Test
    fun `icon-only actions name themselves`() {
        val failures = mutableListOf<String>()
        for (name in coreFiles) {
            val code = codeOf(name)
            for (m in Regex("""\bIconButton\s*\(""").findAll(code)) {
                val header = parenSlice(code, m.range.first)
                val lambda = trailingLambda(code, m.range.first + header.length) ?: continue
                val icon = Regex("""\bIcon\s*\(""").find(lambda) ?: continue // text button
                val slice = parenSlice(lambda, icon.range.first)
                if (slice.contains("contentDescription = null")) {
                    val line = code.substring(0, m.range.first).count { it == '\n' } + 1
                    failures.add("$name:$line: IconButton whose icon declares null")
                }
            }
        }
        assertTrue(
            "unnamed icon actions:\n" + failures.joinToString("\n"),
            failures.isEmpty(),
        )
    }

    @Test
    fun `decorative icons declare null explicitly`() {
        // The three known decorative glyphs beside text: if one gains a
        // description it is a copy change to record, not a drive-by.
        // The welcome's tile arrow left with the screen (58.1); the two that
        // remain still duplicate adjacent text and must say so explicitly.
        for (name in listOf("FileManagerScreen.kt", "TerminalScreen.kt")) {
            assertTrue(
                "$name lost its decorative contentDescription = null",
                codeOf(name).contains("contentDescription = null"),
            )
        }
    }
}
