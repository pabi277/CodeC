package com.codeci.ide.ui.services

import java.io.File

/**
 * Phase 33 (owner, 2026-09-09) — run a self-contained C file whose entry
 * function is NOT named `main`. The owner's practice-project model: every
 * `.c` file is a complete program, but its entry may be called `program01`,
 * `solve`, `run`, … rather than `main`.
 *
 * When such a file is compiled on its own the linker reports
 * `undefined symbol 'main'`. Instead of failing, we compile the file through
 * a tiny generated wrapper that `#include`s it and supplies `main()`. The
 * `#include` (rather than a separate forward-declared caller) means the
 * wrapper needs no knowledge of the entry's exact signature — the definition
 * arrives first, so the call is checked by the compiler.
 *
 * Pure Kotlin — host-testable.
 */
object CEntryWrapper {

    /** Skip sniffing files larger than this (binary/souce guards). */
    const val MAX_SNIFF_BYTES = 256L * 1024L

    /**
     * A C function DEFINITION at line start: `return-type name(args) {` with
     * the brace on the same or the next line. Declarations (`…;`) never match.
     * Control keywords (`if (…`, `for (…`, `switch (…`) are structurally
     * excluded because they are followed directly by `(` with no identifier
     * between the keyword and the parenthesis.
     */
    private val FUNCTION_DEF = Regex(
        """(?m)^[ \t]*(?:[A-Za-z_][A-Za-z0-9_ \t*]*?)[ \t*]+([A-Za-z_][A-Za-z0-9_]*)[ \t]*\([^;]*\)\s*\{"""
    )

    /** The names of every function the file defines, in source order. */
    fun functionNames(source: String): List<String> =
        FUNCTION_DEF.findAll(source).map { it.groupValues[1] }.toList()

    /** True when [source] defines a function named `main`. */
    fun hasMain(source: String): Boolean = functionNames(source).contains("main")

    /**
     * The entry to call when the file defines no `main` and EXACTLY one other
     * function. Null when a `main` exists (nothing to wrap) or when zero or
     * several other functions are defined (too ambiguous to guess) — the
     * caller falls back to the normal single-file build and its hint.
     */
    fun singleEntry(source: String): String? {
        val names = functionNames(source)
        if (names.contains("main")) return null
        return names.distinct().singleOrNull()
    }

    /**
     * The wrapper source: include [target] by absolute path, then supply
     * `main()` that calls [entry]. Null when the path cannot be quoted safely
     * inside a C `#include` string.
     */
    fun wrapperFor(target: File, entry: String): String? {
        val include = target.absolutePath.replace('\\', '/')
        if (include.contains('"') || include.contains('\n')) return null
        return "#include \"$include\"\nint main(void) { $entry(); return 0; }\n"
    }

    /** Write the wrapper under [cacheDir] (outside the project, so git stays clean). */
    fun write(cacheDir: File, target: File, entry: String): File? {
        val body = wrapperFor(target, entry) ?: return null
        return runCatching {
            val dir = File(cacheDir, "cc-entry").apply { mkdirs() }
            val safe = "${target.nameWithoutExtension}_$entry"
                .replace(Regex("[^A-Za-z0-9._-]"), "_")
            File(dir, "entry_$safe.c").apply { writeText(body) }
        }.getOrNull()
    }
}
