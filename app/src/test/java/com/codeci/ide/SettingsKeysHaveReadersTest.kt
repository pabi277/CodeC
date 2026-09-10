package com.codeci.ide

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Phase 38.2 — the audit's enforcement: every DataStore key in the three
 * preference stores must have a READER. Enumerating the stores (not one
 * file) matters: `GitCredentialsStore`'s four keys live on the same
 * `settings` DataStore as `SettingsManager`'s, declared from
 * `ui/theme/ThemeManager.kt`.
 *
 * The chain checked is honest about how this codebase reads values:
 * a key is consumed inside its store by a flow/setter ("block"), and
 * that block (or a block it calls, like `storedFlow` → `stored()`) must
 * be referenced from a production source OUTSIDE the store files.
 * Writers alone do not count — the Phase 38.2 finding was exactly a
 * write-only key (`recent_files_csv`).
 *
 * Known limitation, on purpose: block names are matched as whole words
 * in other files, so a future key whose only API happens to share a
 * name with an unrelated identifier could sneak a false green. The
 * floor on key count below guards the opposite failure (regex drift
 * extracting nothing).
 */
class SettingsKeysHaveReadersTest {

    private val storePaths = listOf(
        "app/src/main/java/com/codeci/ide/ui/settings/SettingsManager.kt",
        "app/src/main/java/com/codeci/ide/ui/theme/ThemeManager.kt",
        "app/src/main/java/com/codeci/ide/ui/projects/GitCredentialsStore.kt"
    )

    private val keyDecl = Regex("""\bval\s+(\w+)\s*=\s*\w+PreferencesKey\("([^"]+)"\)""")
    private val blockDecl = Regex("""^(\s*)(?:suspend\s+)?(?:val|fun)\s+(\w+)""", RegexOption.MULTILINE)

    private data class Block(val name: String, val text: String)

    @Test
    fun `every stored key has a reader outside its store`() {
        val stores: List<Pair<String, String>> = storePaths.map { path ->
            path to RepoFiles.mainSource(path).readText()
        }
        val externalSources = RepoFiles.mainKotlinSources()
            .filter { f -> storePaths.none { f.absolutePath.endsWith(it.substringAfter("app/")) } }
            .map { it.name to it.readText() }

        var keysChecked = 0
        val failures = mutableListOf<String>()

        for ((path, src) in stores) {
            // Split the file into member blocks: each `val`/`fun` declaration
            // owns the text up to the next declaration.
            val declarations = blockDecl.findAll(src).toList()
            val blocks = declarations.mapIndexed { i, m ->
                val end = declarations.getOrNull(i + 1)?.range?.start ?: src.length
                Block(m.groupValues[2], src.substring(m.range.start, end))
            }

            for (keyMatch in keyDecl.findAll(src)) {
                val keySymbol = keyMatch.groupValues[1]
                val keyLiteral = keyMatch.groupValues[2]
                keysChecked++

                // Blocks that USE the key (its own declaration excluded).
                val users = blocks.filter { it.name != keySymbol && Regex("\\b$keySymbol\\b").containsMatchIn(it.text) }
                if (users.isEmpty()) {
                    failures += "$path: key `$keySymbol` (\"$keyLiteral\") has no flow or setter — fully dead"
                    continue
                }
                // Read chain: the key's users, plus blocks those users call
                // (same file), up to 4 hops — e.g. GIT_TOKEN → storedFlow →
                // stored().
                val reachable = mutableSetOf<String>()
                var frontier = users.map { it.name }.toSet()
                repeat(4) {
                    reachable += frontier
                    val next = frontier.flatMap { name ->
                        val b = blocks.firstOrNull { it.name == name } ?: return@flatMap emptySet()
                        blocks.filter { other -> other.name != name && Regex("\\b${other.name}\\b").containsMatchIn(b.text) }
                            .map { it.name }
                    }.toSet() - reachable
                    frontier = next
                }
                reachable += users.map { it.name }

                val referencedOutside = reachable.any { name ->
                    externalSources.any { (_, text) -> Regex("\\b$name\\b").containsMatchIn(text) }
                }
                if (!referencedOutside) {
                    failures += "$path: key `$keySymbol` (\"$keyLiteral\") is never read outside its store " +
                        "(reach: ${reachable.sorted().joinToString(", ")})"
                }
            }
        }

        // Guard against regex drift: a parser that extracts zero keys would
        // pass vacuously. The three stores carry far more than this.
        assertTrue("extracted only $keysChecked keys — the store parser drifted", keysChecked >= 30)

        assertTrue(
            "Stored keys with no reader (the audit law: delete them):\n" + failures.joinToString("\n"),
            failures.isEmpty()
        )
    }

    @Test
    fun `the phase 38-2 dead keys stayed deleted`() {
        // Regression pins: these two were deleted by the audit. If either
        // literal reappears in a store, it must come with a reader — the
        // test above enforces that generically; this pin makes the history
        // loud instead of a diff note. Comments are stripped first: the
        // audit note in SettingsManager documents the deletion by name.
        fun codeOnly(path: String) =
            RepoFiles.mainSource(path).readLines().joinToString("\n") {
                it.substringBefore("//")
            }
        val allStores = storePaths.joinToString("\n") { codeOnly(it) }
        assertTrue("recent_files_csv came back", !"recent_files_csv".toRegex().containsMatchIn(allStores))
        assertTrue("smart_typing_delete_word came back", !"smart_typing_delete_word".toRegex().containsMatchIn(allStores))
        // And their call sites too.
        val sources = RepoFiles.mainKotlinSources()
        assertTrue(
            "addRecentFile/replaceRecentFile resurrected",
            sources.none { Regex("\\b(addRecentFile|replaceRecentFile)\\b").containsMatchIn(it.readText()) }
        )
    }
}
