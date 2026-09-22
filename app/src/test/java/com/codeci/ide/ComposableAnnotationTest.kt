package com.codeci.ide

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 59 — a `@Composable` annotation must attach to a declaration.
 *
 * This pin exists because of a real cost, paid twice in one phase: editing a 2,000-line screen with
 * a script inserted a new `@Composable` immediately below the annotation that already belonged to
 * the next function. The result compiles nowhere but reads fine — Kotlin reports
 * *“This annotation is not repeatable”* and *“@Composable invocations can only happen from the
 * context of a @Composable function”* — and the sandbox cannot compile Compose at all, so only CI
 * saw it.
 *
 * The rule the pin checks is exactly the one the compiler enforces, ignoring blank lines and
 * comments: after every `@Composable`, the next significant line must be a declaration (or another
 * annotation — but then *that* one must reach a declaration, which the loop checks separately), and
 * two `@Composable`s must never end up stacked on one declaration.
 */
class ComposableAnnotationTest {

    /** The next line that is neither blank nor comment, or null at end of file. */
    private fun nextSignificant(lines: List<String>, from: Int): Int? {
        var j = from + 1
        while (j < lines.size) {
            val s = lines[j].trim()
            val comment = s.isEmpty() || s.startsWith("//") || s.startsWith("/*") ||
                s.startsWith("*") || s.endsWith("*/")
            if (!comment) return j
            j++
        }
        return null
    }

    @Test
    fun `every @Composable annotation reaches a declaration`() {
        val declaration = Regex("^(private |internal |public )?(fun|val|var|class|object)\\b")
        val problems = mutableListOf<String>()
        var annotations = 0
        for (file in RepoFiles.mainKotlinSources()) {
            val lines = file.readLines()
            lines.forEachIndexed { i, line ->
                if (line.trim() != "@Composable") return@forEachIndexed
                annotations++
                val j = nextSignificant(lines, i)
                if (j == null) {
                    problems += "${file.name}:${i + 1} @Composable at end of file"
                } else {
                    val next = lines[j].trim()
                    if (next.startsWith("@Composable")) {
                        problems += "${file.name}:${i + 1} stray @Composable (next annotation on ${j + 1})"
                    } else if (next.startsWith("@")) {
                        // A stack of annotations is legal; the loop visits each of them, and the
                        // last one before a declaration is the one that must be attached.
                    } else if (!declaration.containsMatchIn(next)) {
                        problems += "${file.name}:${i + 1} not attached to a declaration: '$next'"
                    }
                }
            }
        }
        assertTrue(
            "a @Composable that reaches no declaration does not compile in CI:\n" +
                problems.joinToString("\n"),
            problems.isEmpty()
        )
        assertTrue(
            "a scan that visits no annotations proves nothing (found $annotations)",
            annotations > 100
        )
    }
}
