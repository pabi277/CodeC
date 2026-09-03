package com.codeci.ide.ui.services

/**
 * Phase 21.1 — the generic language run model.
 *
 * Describes how a single language is compiled and executed. Android-free and
 * host-unit-testable: the registry only produces command *strings*; the
 * process launch stays in [ExecutionRunner] / `InteractiveRunSession`.
 *
 * Token substitution in the templates:
 *  - `$SRC` — the source path (absolute for scratch files, project-relative
 *    inside a project so build output stays inside the project).
 *  - `$OUT` — the output binary path (only meaningful when [buildTemplate]
 *    is non-null).
 */
data class LanguageRunProfile(
    val displayName: String,
    val extensions: List<String>,
    /** Package to auto-install if the tool is missing (null = always present). */
    val requiredPackage: String?,
    /** Binary that proves [requiredPackage] is installed (`$PREFIX/bin/<binary>`). */
    val probeBinary: String? = null,
    /**
     * False when [requiredPackage] is NOT published in the CodeC package
     * repository yet. RUN ▶ then explains that instead of firing a
     * `pkg install` that is guaranteed to fail (device round 1, 2026-09-03).
     */
    val inRepository: Boolean = true,
    /** Human-readable size hint shown in the install prompt ("~80 MB"). */
    val installSizeHint: String? = null,
    /** Build command template, null for interpreted languages. */
    val buildTemplate: String?,
    /** Run command template. Tokens: `$SRC`, `$OUT`. */
    val runTemplate: String,
    /** True when the program is likely interactive (PTY preferred). */
    val interactive: Boolean = false,
    /** Formatter command template, null if none. Token: `$SRC`. */
    val formatterTemplate: String? = null,
) {
    /** True when RUN ▶ must open the Web Preview instead of spawning a process. */
    val isWebPreview: Boolean get() = runTemplate == LanguageRegistry.WEB_PREVIEW
}

/**
 * The build/run/terminal triple a RUN ▶ tap needs. [terminal] is the joined
 * `cd … && build && run` form used by the "Open in Terminal" escape hatch.
 */
data class LanguageRunPlan(
    val build: String?,
    val run: String?,
    val terminal: String,
)

/**
 * Phase 21.1 — extension → [LanguageRunProfile] registry. Adding a language is
 * one entry in [profiles]; no new branch in `EditorViewModel.runActiveFile`.
 *
 * Pure Kotlin: **no Android imports may ever be added to this file.**
 */
object LanguageRegistry {

    /** Sentinel run template: intercepted by the editor, never executed. */
    const val WEB_PREVIEW = "__WEB_PREVIEW__"

    /**
     * POSIX-safe single-argument quoting. Unquoted when the string is a
     * conservative identifier; otherwise wrapped in single quotes with
     * embedded quotes escaped the usual `'\''` way.
     *
     * Lives here (not in `TerminalHandoff`) so the registry stays free of any
     * dependency on the terminal layer; `TerminalHandoff.shellEscape`
     * delegates to this function so both paths quote identically.
     */
    fun shellEscape(value: String): String {
        if (value.isEmpty()) return "''"
        val safe = value.all { ch -> ch.isLetterOrDigit() || ch in "._-/=:@+,%" }
        if (safe) return value
        return buildString(value.length + 8) {
            append('\'')
            for (ch in value) {
                if (ch == '\'') append("'\\''") else append(ch)
            }
            append('\'')
        }
    }

    val profiles: List<LanguageRunProfile> = listOf(
        LanguageRunProfile(
            displayName = "C",
            extensions = listOf("c"),
            // Phase 21 (owner, 2026-09-03): TCC stays the DEFAULT C compiler —
            // it ships in the APK, works offline and needs no download, so a
            // .c file must never be gated behind a ~90 MB install. `cc` is
            // CodeC's own TCC frontend (the cc invariant, Phase 20.1 D5/D15).
            // Users who need C11/C17 install clang from Packages and can set
            // a project.json build line that calls gcc/clang explicitly.
            requiredPackage = null,
            probeBinary = null,
            buildTemplate = "cc \$SRC -o \$OUT",
            runTemplate = "./\$OUT",
            interactive = true,
            formatterTemplate = "clang-format -i \$SRC",
        ),
        LanguageRunProfile(
            displayName = "C++",
            extensions = listOf("cpp", "cc", "cxx"),
            // TCC is a C compiler only — C++ genuinely requires the LLVM
            // toolchain, so this profile keeps its install gate.
            requiredPackage = "clang",
            probeBinary = "g++",
            installSizeHint = "~90 MB",
            buildTemplate = "g++ \$SRC -o \$OUT -lm",
            runTemplate = "./\$OUT",
            interactive = true,
            formatterTemplate = "clang-format -i \$SRC",
        ),
        LanguageRunProfile(
            displayName = "Python",
            extensions = listOf("py", "pyw"),
            requiredPackage = "python",
            probeBinary = "python3",
            buildTemplate = null,
            runTemplate = "python3 \$SRC",
            interactive = true,
            formatterTemplate = "black \$SRC",
        ),
        LanguageRunProfile(
            displayName = "JavaScript",
            extensions = listOf("js", "mjs", "cjs"),
            requiredPackage = "nodejs",
            probeBinary = "node",
            buildTemplate = null,
            runTemplate = "node \$SRC",
            interactive = true,
        ),
        LanguageRunProfile(
            displayName = "TypeScript",
            extensions = listOf("ts"),
            requiredPackage = "nodejs",
            probeBinary = "node",
            buildTemplate = null,
            runTemplate = "npx ts-node \$SRC",
            interactive = true,
        ),
        LanguageRunProfile(
            displayName = "Go",
            extensions = listOf("go"),
            // Not published by Phase 20.1 — the gate says so honestly instead
            // of running a `pkg install` that cannot succeed.
            requiredPackage = "golang",
            probeBinary = "go",
            inRepository = false,
            installSizeHint = "~80 MB",
            buildTemplate = null,
            runTemplate = "go run \$SRC",
            interactive = true,
            formatterTemplate = "gofmt -w \$SRC",
        ),
        LanguageRunProfile(
            displayName = "Rust",
            extensions = listOf("rs"),
            requiredPackage = "rust",
            probeBinary = "rustc",
            inRepository = false,
            installSizeHint = "~200 MB",
            buildTemplate = "rustc \$SRC -o \$OUT",
            runTemplate = "./\$OUT",
            interactive = true,
            formatterTemplate = "rustfmt \$SRC",
        ),
        LanguageRunProfile(
            displayName = "PHP",
            extensions = listOf("php"),
            requiredPackage = "php",
            probeBinary = "php",
            buildTemplate = null,
            runTemplate = "php \$SRC",
            interactive = true,
        ),
        LanguageRunProfile(
            displayName = "Ruby",
            extensions = listOf("rb"),
            requiredPackage = "ruby",
            probeBinary = "ruby",
            buildTemplate = null,
            runTemplate = "ruby \$SRC",
            interactive = true,
        ),
        LanguageRunProfile(
            displayName = "Lua",
            extensions = listOf("lua"),
            requiredPackage = "lua54",
            probeBinary = "lua",
            buildTemplate = null,
            runTemplate = "lua \$SRC",
            interactive = true,
        ),
        LanguageRunProfile(
            displayName = "Shell",
            extensions = listOf("sh", "bash"),
            requiredPackage = null,
            buildTemplate = null,
            runTemplate = "bash \$SRC",
            interactive = true,
        ),
        LanguageRunProfile(
            displayName = "HTML",
            extensions = listOf("html", "htm"),
            requiredPackage = null,
            buildTemplate = null,
            runTemplate = WEB_PREVIEW,
        ),
    )

    fun forExtension(ext: String): LanguageRunProfile? {
        val cleaned = ext.trim().removePrefix(".").lowercase()
        if (cleaned.isEmpty()) return null
        return profiles.firstOrNull { cleaned in it.extensions }
    }

    fun forFile(path: String): LanguageRunProfile? {
        val leaf = path.substringAfterLast('/').substringAfterLast('\\')
        val ext = leaf.substringAfterLast('.', "")
        if (ext.isEmpty() || ext == leaf) return null
        return forExtension(ext)
    }

    /**
     * Phase 24.6 — is this file a test file CodeC can run through the Test ▷
     * button? Recognises pytest-style Python (`test_*.py`, `*_test.py`) and
     * Go test files (`*_test.go`). Pure filename logic, host-testable.
     */
    fun isTestFile(path: String): Boolean {
        val leaf = path.substringAfterLast('/').substringAfterLast('\\')
        val base = leaf.substringBeforeLast('.', leaf)
        val ext = leaf.substringAfterLast('.', "").lowercase()
        return when (ext) {
            "py" -> base.startsWith("test_") || base.endsWith("_test")
            "go" -> base.endsWith("_test")
            else -> false
        }
    }

    /**
     * Phase 24.6 — the run profile for a test file, or null when the file is
     * not a recognised test file. The returned profile keeps the parent
     * language's package/probe (so the existing install gate still applies)
     * but replaces the run template with the test runner; it is never
     * interactive (tests stream their whole result instead of prompting).
     */
    fun testProfileForFile(path: String): LanguageRunProfile? {
        if (!isTestFile(path)) return null
        val profile = forFile(path) ?: return null
        return when (profile.extensions.firstOrNull()?.lowercase()) {
            "py" -> profile.copy(
                displayName = "Python tests",
                runTemplate = "python3 -m pytest \$SRC -v",
                buildTemplate = null,
                interactive = false,
            )
            "go" -> profile.copy(
                displayName = "Go tests",
                runTemplate = "go test ./...",
                buildTemplate = null,
                interactive = false,
            )
            else -> null
        }
    }

    /**
     * Expand `$SRC` and `$OUT` in a template. Both tokens are read from the
     * template only — a replacement value that itself contains `$SRC`/`$OUT`
     * is never re-expanded (single pass, left to right).
     */
    fun expandTemplate(template: String, src: String, out: String): String {
        val result = StringBuilder(template.length + src.length + out.length)
        var i = 0
        while (i < template.length) {
            when {
                template.startsWith("\$SRC", i) -> { result.append(src); i += 4 }
                template.startsWith("\$OUT", i) -> { result.append(out); i += 4 }
                else -> { result.append(template[i]); i++ }
            }
        }
        return result.toString()
    }

    /**
     * The output binary name for a source leaf: `main.c` → `main.out`,
     * sanitized so it is always a safe single path segment.
     */
    fun outputNameFor(sourceLeaf: String): String {
        val base = sourceLeaf.substringAfterLast('/').substringBeforeLast('.', sourceLeaf)
            .ifBlank { "main" }
        return (base + ".out").replace(Regex("[^A-Za-z0-9._-]"), "_")
    }

    /**
     * Phase 21.1 — build the whole run plan for one file.
     *
     * @param profile   the resolved language profile.
     * @param workDir   absolute directory the commands run in.
     * @param sourceRef the source path as the commands should see it
     *                  (project-relative inside a project, absolute otherwise).
     * @param outputDir directory (relative to [workDir]) for compiled binaries,
     *                  or null to place the binary next to the source.
     */
    fun planFor(
        profile: LanguageRunProfile,
        workDir: String,
        sourceRef: String,
        outputDir: String? = null,
    ): LanguageRunPlan? {
        if (profile.isWebPreview) return null
        val src = shellEscape(sourceRef)
        val outName = outputNameFor(sourceRef)
        val outRef = if (outputDir.isNullOrBlank()) outName else "$outputDir/$outName"
        val out = shellEscape(outRef)
        val build = profile.buildTemplate?.let { template ->
            val compiled = expandTemplate(template, src, out)
            if (!outputDir.isNullOrBlank()) {
                "mkdir -p ${shellEscape(outputDir)} && $compiled"
            } else {
                compiled
            }
        }
        val run = expandTemplate(profile.runTemplate, src, out)
        val steps = buildList {
            add("cd ${shellEscape(workDir)}")
            build?.let { add(it) }
            add(run)
        }
        return LanguageRunPlan(build, run, steps.joinToString(" && "))
    }

    /** Formatter command for a file, or null when the language has none. */
    fun formatterCommand(profile: LanguageRunProfile, sourceRef: String): String? =
        profile.formatterTemplate?.let {
            expandTemplate(it, shellEscape(sourceRef), "")
        }
}
