package com.codeci.ide.ui.services

/**
 * Phase 21.1/21.2 — the pure decision layer between "the user tapped RUN ▶"
 * and "a process is launched".
 *
 * Android-free on purpose: `EditorViewModel` supplies the paths, a probe
 * lambda that answers "is this tool installed?", and gets back one of the
 * [RunDecision] cases. Every rule here is host-unit-testable.
 */
sealed class RunDecision {

    /** HTML and friends: open Web Preview instead of spawning a process. */
    data class WebPreview(val profile: LanguageRunProfile) : RunDecision()

    /** The language's toolchain package is missing — show the install gate. */
    data class NeedsInstall(
        val profile: LanguageRunProfile,
        val packageName: String,
    ) : RunDecision()

    /** Ready to run: [plan] carries the build/run/terminal command strings. */
    data class Execute(
        val profile: LanguageRunProfile,
        val plan: LanguageRunPlan,
    ) : RunDecision()

    /** No profile claims this file extension. */
    data class Unsupported(val extension: String) : RunDecision()

    /**
     * The language is known but its package is not in the CodeC repository
     * yet (Go, Rust). Better an honest message than a `pkg install` that
     * cannot succeed — device round 1, 2026-09-03.
     */
    data class Unavailable(
        val profile: LanguageRunProfile,
        val packageName: String,
    ) : RunDecision()
}

object LanguageRunPlanner {

    /**
     * Map a package name to the binary that proves it is installed. Falls back
     * to the package name itself (correct for php/ruby/gcc/…).
     */
    fun probeBinaryFor(profile: LanguageRunProfile): String? {
        val pkg = profile.requiredPackage ?: return null
        return profile.probeBinary ?: pkg
    }

    /**
     * @param sourceRef  the source path as the shell should see it — project
     *                   relative inside a project, absolute for scratch files.
     * @param workDir    absolute directory the commands run in.
     * @param outputDir  directory (relative to [workDir]) for build output, or
     *                   null to write the binary next to the source.
     * @param toolInstalled probe: given a binary name, is it on the userland?
     */
    fun decide(
        sourceRef: String,
        workDir: String,
        outputDir: String? = null,
        toolInstalled: (String) -> Boolean = { true },
    ): RunDecision {
        val profile = LanguageRegistry.forFile(sourceRef)
            ?: return RunDecision.Unsupported(sourceRef.substringAfterLast('.', ""))
        if (profile.isWebPreview) return RunDecision.WebPreview(profile)
        val probe = probeBinaryFor(profile)
        if (probe != null && !toolInstalled(probe)) {
            val pkg = profile.requiredPackage!!
            return if (profile.inRepository) {
                RunDecision.NeedsInstall(profile, pkg)
            } else {
                RunDecision.Unavailable(profile, pkg)
            }
        }
        val plan = LanguageRegistry.planFor(profile, workDir, sourceRef, outputDir)
            ?: return RunDecision.WebPreview(profile)
        return RunDecision.Execute(profile, plan)
    }

    /**
     * Phase 21 device round 2 (2026-09-03) — the toolchain a raw command
     * string needs, or null when nothing known is missing.
     *
     * Server-type projects and custom `project.json` build/run strings never
     * pass through [decide]: they execute their configured command verbatim,
     * so a device without python3 got a bare `python3: command not found`
     * and exit 127 with no install gate at all. This inspects the FIRST word
     * of each command (the program actually being invoked) and maps it back
     * to the profile that provides it.
     *
     * Only the leading token is considered on purpose: `./bin/server`,
     * `cd x && …` and shell builtins must not be mistaken for toolchains.
     */
    fun toolchainForCommands(
        commands: List<String?>,
        toolInstalled: (String) -> Boolean,
    ): RunDecision.NeedsInstall? {
        commands.filterNotNull().forEach { raw ->
            raw.split("&&", ";", "|")
                .mapNotNull { segment -> segment.trim().split(Regex("\\s+")).firstOrNull() }
                .filter { it.isNotBlank() }
                .forEach { program ->
                    val profile = profileProviding(program) ?: return@forEach
                    if (!profile.inRepository) return@forEach
                    if (!toolInstalled(program)) {
                        return RunDecision.NeedsInstall(profile, profile.requiredPackage!!)
                    }
                }
        }
        return null
    }

    /** The profile whose package ships [program] as its probe binary. */
    fun profileProviding(program: String): LanguageRunProfile? =
        LanguageRegistry.profiles.firstOrNull {
            it.requiredPackage != null && it.probeBinary == program
        }

    /**
     * The command the install gate runs.
     *
     * Device round 1 (2026-09-03) hit "E: Unable to locate package" on a
     * device whose apt catalog predated the Phase 20.1 publish: the gate must
     * refresh the lists first, otherwise a brand-new install can never
     * succeed. `pkg update` is idempotent and cheap next to the download that
     * follows.
     */
    fun installCommand(packageName: String): String =
        "pkg update && pkg install -y $packageName"
}
