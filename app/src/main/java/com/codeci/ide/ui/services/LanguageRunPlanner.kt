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
            return RunDecision.NeedsInstall(profile, profile.requiredPackage!!)
        }
        val plan = LanguageRegistry.planFor(profile, workDir, sourceRef, outputDir)
            ?: return RunDecision.WebPreview(profile)
        return RunDecision.Execute(profile, plan)
    }

    /** The `pkg install` command the install gate runs. */
    fun installCommand(packageName: String): String = "pkg install -y $packageName"
}
