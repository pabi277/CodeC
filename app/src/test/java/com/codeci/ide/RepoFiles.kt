package com.codeci.ide

import java.io.File

/**
 * Phase 38 — shared locator for host tests that read the REAL repo tree
 * (source-scanning tests, in the same spirit as the Phase 30/31 tests
 * that read real assets — but plain JVM, no Robolectric: these tests
 * assert on files, not on the APK).
 *
 * Under `:app:testDebugUnitTest` the working directory is the module
 * dir (`app/`); under a manual run it may be the repo root. Walk up
 * until both markers match so both work.
 */
object RepoFiles {

    fun root(): File {
        var dir = File(System.getProperty("user.dir") ?: ".").absoluteFile
        repeat(6) {
            if (File(dir, "settings.gradle.kts").isFile &&
                File(dir, "app/src/main/res").isDirectory
            ) {
                return dir
            }
            dir = dir.parentFile ?: return@repeat
        }
        error("CodeC repo root not found from ${System.getProperty("user.dir")}")
    }

    /** A file under the repo root, e.g. `mainSource("app/src/main/AndroidManifest.xml")`. */
    fun mainSource(relativePath: String): File = File(root(), relativePath)

    /** Every Kotlin source under `app/src/main/java` (production only — no tests). */
    fun mainKotlinSources(): List<File> =
        File(root(), "app/src/main/java")
            .walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .sorted()
            .toList()

    /**
     * Phase 50 — source-scan hygiene: blanks comments, string literals and
     * char literals (newlines kept, so line numbers survive). A pin that
     * greps for `8.dp` must not trip on a sentence promising there is none —
     * the Phase 45 round-2 lesson ("pin the button, never the word").
     */
    fun codeOnly(source: String): String {
        val out = StringBuilder(source.length)
        var i = 0
        var state = 0 // 0 code, 1 line comment, 2 block comment, 3 string, 4 char, 5 raw string
        while (i < source.length) {
            val c = source[i]
            val next = if (i + 1 < source.length) source[i + 1] else '\u0000'
            val next2 = if (i + 2 < source.length) source[i + 2] else '\u0000'
            if (state == 0) {
                when {
                    c == '/' && next == '/' -> { out.append("  "); i += 2; state = 1 }
                    c == '/' && next == '*' -> { out.append("  "); i += 2; state = 2 }
                    c == '"' && next == '"' && next2 == '"' -> { out.append("   "); i += 3; state = 5 }
                    c == '"' -> { out.append(' '); i += 1; state = 3 }
                    c == '\'' -> { out.append(' '); i += 1; state = 4 }
                    else -> { out.append(c); i += 1 }
                }
            } else if (state == 1) {
                if (c == '\n') { out.append(c); state = 0 } else out.append(' ')
                i += 1
            } else if (state == 2) {
                if (c == '*' && next == '/') { out.append("  "); i += 2; state = 0 }
                else { out.append(if (c == '\n') c else ' '); i += 1 }
            } else if (state == 3) {
                when {
                    c == '\\' && i + 1 < source.length -> { out.append("  "); i += 2 }
                    c == '"' -> { out.append(' '); i += 1; state = 0 }
                    else -> { out.append(if (c == '\n') c else ' '); i += 1 }
                }
            } else if (state == 4) {
                when {
                    c == '\\' && i + 1 < source.length -> { out.append("  "); i += 2 }
                    c == '\'' -> { out.append(' '); i += 1; state = 0 }
                    else -> { out.append(' '); i += 1 }
                }
            } else {
                if (c == '"' && next == '"' && next2 == '"') {
                    out.append("   "); i += 3; state = 0
                } else {
                    out.append(if (c == '\n') c else ' '); i += 1
                }
            }
        }
        return out.toString()
    }
}
