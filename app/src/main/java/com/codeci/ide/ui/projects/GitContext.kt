package com.codeci.ide.ui.projects

import android.content.Context
import com.codeci.ide.ui.terminal.ShellBootstrap
import java.io.File

/**
 * Phase 13 — the context-aware half of the Git integration: resolves the
 * packaged `git` binary, the CodeC shell environment, the askpass helper
 * file, and the stored credentials, and hands them to the Android-free
 * [GitManager].
 *
 * Kept separate from [GitManager] so the engine itself stays on the host-JVM
 * unit-test path.
 */
class GitContext(private val context: Context) {

    private val bootstrap = ShellBootstrap(context)
    private val store = GitCredentialsStore(context)

    /** `$PREFIX/bin/git` — present only after `pkg install git`. */
    fun gitBinary(): File? {
        val candidate = File(bootstrap.prefixDir(), "bin/git")
        return if (candidate.isFile && candidate.canExecute()) candidate else null
    }

    fun askpassFile(): File = File(context.filesDir, "CodeC/git-askpass.sh")

    suspend fun storedCredentials(): GitCredentialsStore.Stored = store.stored()

    /**
     * A manager wired to the packaged git and the current stored credentials,
     * or null when git is not installed (the UI shows install guidance).
     *
     * `ShellBootstrap.prepare()` is idempotent (TerminalViewModel calls it on
     * every app start); re-running it here keeps the environment map fresh
     * even when git is used from a screen that never opened the terminal.
     */
    suspend fun manager(): GitManager? {
        val binary = gitBinary() ?: return null
        val shell = bootstrap.prepare()
        val stored = store.stored()
        return GitManager(
            gitBinary = binary,
            baseEnv = shell.env,
            auth = stored.credentials,
            identity = stored.identity,
            askpassFile = if (stored.hasToken) askpassFile() else null
        )
    }
}
