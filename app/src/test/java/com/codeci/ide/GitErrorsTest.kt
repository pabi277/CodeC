package com.codeci.ide

import com.codeci.ide.ui.projects.GitErrorKind
import com.codeci.ide.ui.projects.GitErrors
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 17 follow-up — the friendly git error classifier is pure (no
 * Android, no filesystem), so CI proves every message the UI will show.
 * The `raw` strings here mimic the ALREADY-REDACTED text [GitErrors] receives
 * from [com.codeci.ide.ui.projects.GitManager].
 */
class GitErrorsTest {

    // ------------------------------------------------------------------
    // Timeouts
    // ------------------------------------------------------------------

    @Test
    fun `classify flags our 124 timeout exit code`() {
        val err = GitErrors.classify("git push failed: nothing", exitCode = 124, hasToken = true)
        assertEquals(GitErrorKind.TIMEOUT, err.kind)
        assertTrue(err.message.contains("took too long", ignoreCase = true))
    }

    @Test
    fun `classify flags git's own timed-out wording`() {
        val err = GitErrors.classify("git clone timed out after 60s", exitCode = 1, hasToken = false)
        assertEquals(GitErrorKind.TIMEOUT, err.kind)
    }

    // ------------------------------------------------------------------
    // Token / auth
    // ------------------------------------------------------------------

    @Test
    fun `missing username without a stored token means no token`() {
        val err = GitErrors.classify(
            "fatal: could not read Username for 'https://github.com': No such device or address",
            exitCode = 128,
            hasToken = false
        )
        assertEquals(GitErrorKind.NO_TOKEN, err.kind)
        assertEquals(GitErrors.TOKEN_HELP_URL, err.helpUrl)
        assertTrue(err.message.contains("Settings → GitHub Account"))
    }

    @Test
    fun `missing username with a stored token means invalid token`() {
        val err = GitErrors.classify(
            "fatal: could not read Username for 'https://github.com'",
            exitCode = 128,
            hasToken = true
        )
        assertEquals(GitErrorKind.AUTH_FAILED, err.kind)
        assertEquals(GitErrors.TOKEN_HELP_URL, err.helpUrl)
    }

    @Test
    fun `authentication failed with a token is invalid token`() {
        val err = GitErrors.classify(
            "remote: Support for password authentication was removed",
            exitCode = 128,
            hasToken = false
        )
        // This path lacks "authentication failed" — it should not claim a token
        // problem; it falls through to the generic fallback.
        assertEquals(GitErrorKind.GENERIC, err.kind)
    }

    @Test
    fun `http 401 means the stored token is rejected`() {
        val err = GitErrors.classify(
            "git push failed: the requested url returned error: 401",
            exitCode = 128,
            hasToken = true
        )
        assertEquals(GitErrorKind.AUTH_FAILED, err.kind)
        assertEquals(GitErrors.TOKEN_HELP_URL, err.helpUrl)
    }

    // ------------------------------------------------------------------
    // Permissions
    // ------------------------------------------------------------------

    @Test
    fun `http 403 means the token lacks write access`() {
        val err = GitErrors.classify(
            "remote: Permission to pabi277/repo.git denied to user.",
            exitCode = 128,
            hasToken = true
        )
        assertEquals(GitErrorKind.TOKEN_PERMISSION, err.kind)
        assertEquals(GitErrors.TOKEN_HELP_URL, err.helpUrl)
    }

    @Test
    fun `remote permission wording is permission kind`() {
        val err = GitErrors.classify(
            "remote: permission to pabi277/repo.git denied to oauth2",
            exitCode = 1,
            hasToken = true
        )
        assertEquals(GitErrorKind.TOKEN_PERMISSION, err.kind)
    }

    // ------------------------------------------------------------------
    // Offline
    // ------------------------------------------------------------------

    @Test
    fun `could not resolve host means offline`() {
        val err = GitErrors.classify(
            "fatal: unable to access 'https://github.com/x': Could not resolve host: github.com",
            exitCode = 128,
            hasToken = false
        )
        assertEquals(GitErrorKind.OFFLINE, err.kind)
        assertNull(err.helpUrl)
        assertTrue(err.message.contains("offline", ignoreCase = true))
    }

    @Test
    fun `failed to connect means offline`() {
        val err = GitErrors.classify(
            "fatal: unable to access 'https://github.com/x': Failed to connect to github.com port 443",
            exitCode = 128,
            hasToken = false
        )
        assertEquals(GitErrorKind.OFFLINE, err.kind)
    }

    // ------------------------------------------------------------------
    // Push rejection / upstream
    // ------------------------------------------------------------------

    @Test
    fun `non fast forward means pull first`() {
        val err = GitErrors.classify(
            "git push failed: ! [rejected] main -> main (non-fast-forward)",
            exitCode = 1,
            hasToken = true
        )
        assertEquals(GitErrorKind.REJECTED, err.kind)
        assertTrue(err.message.contains("PULL", ignoreCase = true))
    }

    @Test
    fun `no upstream branch is its own kind`() {
        val err = GitErrors.classify(
            "fatal: The current branch main has no upstream branch.",
            exitCode = 128,
            hasToken = false
        )
        assertEquals(GitErrorKind.NO_UPSTREAM, err.kind)
    }

    // ------------------------------------------------------------------
    // Branch exists / conflicts / not a repository
    // ------------------------------------------------------------------

    @Test
    fun `already exists means branch name collision`() {
        val err = GitErrors.classify(
            "fatal: a branch named 'feature' already exists",
            exitCode = 128,
            hasToken = false
        )
        assertEquals(GitErrorKind.BRANCH_EXISTS, err.kind)
    }

    @Test
    fun `unmerged paths mean merge conflict`() {
        val err = GitErrors.classify(
            "error: Pulling is not possible because you have unmerged files.",
            exitCode = 1,
            hasToken = false
        )
        assertEquals(GitErrorKind.CONFLICT, err.kind)
    }

    @Test
    fun `not a git repository is its own kind`() {
        val err = GitErrors.classify(
            "fatal: not a git repository (or any of the parent directories): .git",
            exitCode = 128,
            hasToken = false
        )
        assertEquals(GitErrorKind.NOT_A_REPOSITORY, err.kind)
    }

    // ------------------------------------------------------------------
    // Repository not found
    // ------------------------------------------------------------------

    @Test
    fun `repository not found suggests a token`() {
        val err = GitErrors.classify(
            "fatal: repository 'https://github.com/pabi277/private.git/' not found",
            exitCode = 128,
            hasToken = false
        )
        assertEquals(GitErrorKind.TOKEN_PERMISSION, err.kind)
        assertEquals(GitErrors.TOKEN_HELP_URL, err.helpUrl)
    }

    // ------------------------------------------------------------------
    // Generic fallback
    // ------------------------------------------------------------------

    @Test
    fun `unknown text falls back to a short generic message`() {
        val err = GitErrors.classify(
            "git push failed: something deeply unexpected happened here",
            exitCode = 42,
            hasToken = true
        )
        assertEquals(GitErrorKind.GENERIC, err.kind)
        assertNull(err.helpUrl)
        assertTrue(err.message.contains("deeply unexpected"))
    }

    @Test
    fun `null raw text still yields a generic message`() {
        val err = GitErrors.classify(null, exitCode = null, hasToken = false)
        assertEquals(GitErrorKind.GENERIC, err.kind)
        assertTrue(err.message.isNotBlank())
    }

    @Test
    fun `classify is case insensitive`() {
        val err = GitErrors.classify(
            "FATAL: NOT A GIT REPOSITORY",
            exitCode = 128,
            hasToken = false
        )
        assertEquals(GitErrorKind.NOT_A_REPOSITORY, err.kind)
    }

    // ------------------------------------------------------------------
    // Helpers + display()
    // ------------------------------------------------------------------

    @Test
    fun `notInstalled points at the git install step`() {
        val err = GitErrors.notInstalled()
        assertEquals(GitErrorKind.NOT_INSTALLED, err.kind)
        assertTrue(err.message.contains("not installed", ignoreCase = true))
    }

    @Test
    fun `tokenMissing carries the create-token url`() {
        val err = GitErrors.tokenMissing()
        assertEquals(GitErrorKind.NO_TOKEN, err.kind)
        assertEquals(GitErrors.TOKEN_HELP_URL, err.helpUrl)
    }

    @Test
    fun `display appends the help url on its own line`() {
        val err = GitErrors.tokenMissing()
        val text = err.display()
        assertTrue(text.startsWith(err.message))
        assertTrue(text.contains("\n${err.helpUrl}"))
    }

    @Test
    fun `display omits the url when absent`() {
        val err = GitErrors.notInstalled()
        assertEquals(err.message, err.display())
    }
}
