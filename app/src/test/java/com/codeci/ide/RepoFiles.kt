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
}
