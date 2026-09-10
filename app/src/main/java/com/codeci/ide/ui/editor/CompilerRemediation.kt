package com.codeci.ide.ui.editor

/**
 * Phase 38.2 — the ONE home of the Termux-fallback remedy text.
 *
 * When the "Termux Engine" card left Settings, the four setup steps it
 * carried did not die — they moved to the only place they are useful:
 * the Output Panel of a build that actually failed with the exec
 * "Permission denied" signature (Android refusing to run a downloaded
 * toolchain binary: W^X policy for apps targeting API 29+, noexec
 * mounts on some emulators/cloud phones, or a broken toolchain).
 *
 * Pure on purpose (CompilerDiagnostics-style): no Context, no
 * Resources, so the text cannot drift between surfaces — Settings now
 * carries only the one-sentence About-style promise ("CodeC can use a
 * compatible terminal app's compiler automatically"), and this object
 * owns the full steps.
 *
 * `docs/TROUBLESHOOTING.md` §27 quotes this wording; the doc is the
 * source of the text and [CompilerRemediationTest] pins the four steps
 * so neither can rot.
 */
object CompilerRemediation {

    /**
     * Exec-failure markers seen in the wild for this signature: the
     * shell's own wording (`sh: /path/cc: Permission denied`), the
     * wrapper script (`compiler-wrapper.sh[11]: …clang: Permission
     * denied`), and plain "cannot execute / cannot run" EACCES text.
     * A bare "permission denied" (a file read/write denial, a git
     * `permission denied (publickey)`) is NOT this failure and must not
     * produce the Termux remedy.
     */
    private val EXEC_MARKERS = listOf(
        "cannot execute", "cannot run", "exec format", "execve",
        "bin/", ".sh", "clang", "gcc", "tcc", "sh:", "cc:"
    )

    /**
     * True when [output] carries the exec-blocked signature (see class
     * doc). Everything CodeC controls writes English messages, so the
     * match is lowercase-English only — the same policy as every other
     * diagnostic heuristic in this app.
     *
     * A compiler diagnostic line (`cc: error: cannot open output file …
     * Permission denied`) is NOT this failure: the shell never prefixes
     * its exec errors with `error:`, so those lines are excluded.
     */
    fun isExecPermissionDenied(output: String?): Boolean {
        if (output.isNullOrBlank()) return false
        return output.lineSequence().any { line ->
            val lower = line.lowercase()
            lower.contains("permission denied") &&
                !lower.contains("error:") &&
                EXEC_MARKERS.any { lower.contains(it) }
        }
    }

    /** The lead + four-step remedy, or null when the output is not that failure. */
    fun textFor(output: String?): String? {
        if (!isExecPermissionDenied(output)) return null
        return LEAD + " " + STEPS
    }

    private const val LEAD =
        "Android is blocking execution of the downloaded compiler (Permission denied). " +
            "CodeC can borrow Termux's compiler instead \u2014 to enable it:"

    /** The four steps, one string so the Output Panel renders one line. */
    const val STEPS =
        "1) Install Termux 0.109+ from F-Droid or GitHub (termux.dev). " +
            "2) In Termux run: echo \"allow-external-apps=true\" >> ~/.termux/termux.properties && " +
            "termux-reload-settings. 3) Grant CodeC the \"Run commands in Termux environment\" " +
            "permission (Android Settings \u2192 Apps \u2192 CodeC IDE \u2192 Permissions \u2192 Additional " +
            "permissions). 4) In Termux run: pkg update && pkg install clang"
}
