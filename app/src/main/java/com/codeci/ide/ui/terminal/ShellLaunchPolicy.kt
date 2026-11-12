package com.codeci.ide.ui.terminal

/**
 * Phase 104.1 — how the terminal's first process is launched. Pure and
 * Android-free, so it is provable on the host.
 *
 * [file] is the executable to `execve`; [argv] includes argv[0]. [login] is
 * true when argv[0] carries the login-shell `-` prefix.
 */
data class LaunchPlan(val file: String, val argv: List<String>, val login: Boolean)

object ShellLaunchPolicy {

    /** Android's own shell; the fallback when no shell is installed. */
    const val SYSTEM_SH = "/system/bin/sh"

    /**
     * [shellPath] is the resolved shell (bash, busybox, or the system shell).
     * [shellIsElf] says whether it is a real binary rather than a script shim.
     * [profilePath] is the prefix's `etc/profile` when it exists.
     *
     * A real bash is exec'd directly as a login shell (argv[0] `-bash`, `-l`):
     * login bash reads `~/.profile`, which the app writes to source
     * `etc/profile` and then `~/.bashrc`. Anything else keeps the previous
     * `sh -c ". profile; exec <shell> -i"` chain, byte for byte in behaviour.
     */
    fun plan(shellPath: String, shellIsElf: Boolean, profilePath: String?): LaunchPlan {
        val isBash = shellPath.substringAfterLast('/') == "bash"
        if (shellIsElf && isBash) {
            return LaunchPlan(file = shellPath, argv = listOf("-bash", "-l"), login = true)
        }
        val chain = buildString {
            if (profilePath != null) append(". ").append(quote(profilePath)).append("; ")
            append("exec ").append(quote(shellPath)).append(" -i")
        }
        return LaunchPlan(file = SYSTEM_SH, argv = listOf("sh", "-c", chain), login = false)
    }

    /** POSIX single-quote escaping, for the `sh -c` chain only. */
    fun quote(value: String): String = "'" + value.replace("'", "'\\''") + "'"
}
