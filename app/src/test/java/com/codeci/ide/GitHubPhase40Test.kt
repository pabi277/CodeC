package com.codeci.ide

import com.codeci.ide.ui.projects.GitBlocker
import com.codeci.ide.ui.projects.GitErrorKind
import com.codeci.ide.ui.projects.GitHubPublish
import com.codeci.ide.ui.projects.GitManager
import com.codeci.ide.ui.projects.GitOp
import com.codeci.ide.ui.projects.GitPushAttempt
import com.codeci.ide.ui.projects.GitPushParser
import com.codeci.ide.ui.projects.GitReadiness
import com.codeci.ide.ui.projects.PublishErrorKind
import com.codeci.ide.ui.projects.PublishResult
import com.codeci.ide.ui.projects.PushOutcome
import com.codeci.ide.ui.projects.RejectReason
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 40 — host tests for the pure GitHub logic: readiness (40.1), the push
 * outcome parser (40.2) and Publish (40.3). No Android, no process, no network.
 *
 * The push fixtures are git's real output formats, recorded in
 * `GitPushOutcome.kt`; a format surprise fails here instead of on a phone.
 */
class GitHubPhase40Test {

    // ---- 40.1 readiness ----------------------------------------------------

    private fun ready(
        gitInstalled: Boolean = true,
        hasToken: Boolean = true,
        isRepository: Boolean = true,
        remoteName: String? = "origin",
        online: Boolean? = null
    ) = GitReadiness.forProject(
        gitInstalled = gitInstalled,
        hasToken = hasToken,
        isRepository = isRepository,
        remoteName = remoteName,
        remoteUrl = remoteName?.let { "https://github.com/u/r.git" },
        online = online
    )

    @Test
    fun `missing git wins over every other blocker`() {
        val state = ready(gitInstalled = false, hasToken = false, isRepository = false, remoteName = null)
        for (op in listOf(GitOp.COMMIT, GitOp.PUSH, GitOp.PULL, GitOp.CLONE, GitOp.PUBLISH, GitOp.REFRESH)) {
            assertEquals("$op", GitBlocker.GIT_NOT_INSTALLED, state.blocker(op))
            assertEquals(GitReadiness.ACTION_INSTALL_GIT, state.actionId(op))
        }
    }

    @Test
    fun `commit needs only a repository, never a token or a remote`() {
        val offlineNoRemote = ready(hasToken = false, remoteName = null, online = false)
        assertTrue(offlineNoRemote.isReady(GitOp.COMMIT))
        assertNull(offlineNoRemote.message(GitOp.COMMIT))
    }

    @Test
    fun `push needs a token and a remote, in that order`() {
        assertEquals(GitBlocker.NO_TOKEN, ready(hasToken = false).blocker(GitOp.PUSH))
        assertEquals(GitBlocker.NO_REMOTE, ready(remoteName = null).blocker(GitOp.PUSH))
        assertEquals(GitReadiness.ACTION_PUBLISH_REPO, ready(remoteName = null).actionId(GitOp.PUSH))
        assertTrue(ready().isReady(GitOp.PUSH))
    }

    @Test
    fun `publish needs a token but never an existing remote or repository`() {
        val noRepoNoRemote = ready(isRepository = false, remoteName = null)
        assertTrue(noRepoNoRemote.isReady(GitOp.PUBLISH))
        assertEquals(GitBlocker.NO_TOKEN, ready(isRepository = false, remoteName = null, hasToken = false).blocker(GitOp.PUBLISH))
    }

    @Test
    fun `clone needs git and a network, not a repository or a remote`() {
        val state = ready(isRepository = false, remoteName = null, hasToken = false)
        assertTrue(state.isReady(GitOp.CLONE))
        assertEquals(GitBlocker.OFFLINE, state.copy(online = false).blocker(GitOp.CLONE))
    }

    @Test
    fun `an unknown network state never blocks an operation`() {
        val state = ready(online = null)
        assertNull(state.blocker(GitOp.PUSH))
        assertTrue(state.isReady(GitOp.PULL))
        assertEquals(GitBlocker.OFFLINE, state.copy(online = false).blocker(GitOp.PULL))
    }

    @Test
    fun `refresh reports the repository blocker for a non-repo folder`() {
        assertEquals(GitBlocker.NO_REPOSITORY, ready(isRepository = false).blocker(GitOp.REFRESH))
        assertEquals(GitReadiness.ACTION_INIT_REPO, ready(isRepository = false).actionId(GitOp.REFRESH))
    }

    @Test
    fun `every blocker has a sentence`() {
        val states = listOf(
            ready(gitInstalled = false),
            ready(hasToken = false),
            ready(isRepository = false),
            ready(remoteName = null),
            ready(online = false)
        )
        for (state in states) {
            val message = state.message(GitOp.PUSH)
            assertNotNull(message)
            assertTrue(message!!.isNotBlank())
        }
    }

    // ---- 40.2 push outcome -------------------------------------------------

    @Test
    fun `a new branch push is reported as a new branch`() {
        val outcome = GitPushParser.parse(
            stdout = emptyList(),
            stderr = listOf(
                "To https://github.com/u/r.git",
                " * [new branch]      feature/x -> feature/x",
                "branch 'feature/x' set up to track 'origin/feature/x'."
            ),
            exitCode = 0,
            branch = "feature/x",
            remoteUrl = null
        )
        assertTrue(outcome is PushOutcome.Pushed)
        val pushed = outcome as PushOutcome.Pushed
        assertTrue(pushed.newBranch)
        assertEquals("feature/x", pushed.branch)
        // The parser keeps git's own URL verbatim; shortening it is the UI's job.
        assertEquals("https://github.com/u/r.git", pushed.remoteUrl)
        assertTrue(outcome.ok)
    }

    @Test
    fun `an updated branch reports the refspec`() {
        val outcome = GitPushParser.parse(
            stdout = emptyList(),
            stderr = listOf("To https://github.com/u/r.git", "   a1b2c3d..e4f5a6b  main -> main"),
            exitCode = 0,
            branch = "main",
            remoteUrl = "https://github.com/u/r.git"
        ) as PushOutcome.Pushed
        assertFalse(outcome.newBranch)
        assertEquals("a1b2c3d", outcome.from)
        assertEquals("e4f5a6b", outcome.to)
        assertEquals("main", outcome.branch)
    }

    @Test
    fun `a forced update is still a push`() {
        val outcome = GitPushParser.parse(
            stdout = emptyList(),
            stderr = listOf(
                "To https://github.com/u/r.git",
                " + a1b2c3d...e4f5a6b main -> main (forced update)"
            ),
            exitCode = 0,
            branch = "main"
        ) as PushOutcome.Pushed
        assertEquals("a1b2c3d", outcome.from)
        assertEquals("e4f5a6b", outcome.to)
    }

    @Test
    fun `a branch name with a slash keeps its full name`() {
        val outcome = GitPushParser.parse(
            stdout = emptyList(),
            stderr = listOf(" * [new branch]      feature/login -> feature/login"),
            exitCode = 0,
            branch = "feature/login"
        ) as PushOutcome.Pushed
        assertEquals("feature/login", outcome.branch)
        assertEquals("feature/login", outcome.to)
    }

    @Test
    fun `everything up to date is not a push`() {
        val outcome = GitPushParser.parse(
            stdout = listOf("Everything up-to-date"),
            stderr = emptyList(),
            exitCode = 0,
            branch = "main"
        )
        assertEquals(PushOutcome.UpToDate, outcome)
        assertTrue(outcome.ok)
    }

    @Test
    fun `a non-fast-forward rejection names the reason and the remedy`() {
        val outcome = GitPushParser.parse(
            stdout = emptyList(),
            stderr = listOf(
                "To https://github.com/u/r.git",
                " ! [rejected]        main -> main (non-fast-forward)",
                "error: failed to push some refs to 'https://github.com/u/r.git'"
            ),
            exitCode = 1,
            branch = "main"
        ) as PushOutcome.Rejected
        assertEquals(RejectReason.NON_FAST_FORWARD, outcome.why)
        assertTrue(outcome.hint.contains("Pull"))
        assertFalse(outcome.ok)
    }

    @Test
    fun `GH006 becomes a protected-branch rejection`() {
        val outcome = GitPushParser.parse(
            stdout = emptyList(),
            stderr = listOf(
                "remote: error: GH006: Protected branch update failed for refs/heads/main.",
                " ! [remote rejected] main -> main (protected branch hook declined)"
            ),
            exitCode = 1,
            branch = "main"
        ) as PushOutcome.Rejected
        assertEquals(RejectReason.PROTECTED_BRANCH, outcome.why)
        assertTrue(outcome.hint.contains("new branch"))
    }

    @Test
    fun `shallow update not allowed stays a shallow problem`() {
        val outcome = GitPushParser.parse(
            stdout = emptyList(),
            stderr = listOf(" ! [rejected] main -> main (shallow update not allowed)"),
            exitCode = 1,
            branch = "main"
        ) as PushOutcome.Rejected
        assertEquals(RejectReason.SHALLOW_UPDATE, outcome.why)
    }

    @Test
    fun `a branch with no upstream is a missing remote, not a failed push`() {
        val outcome = GitPushParser.parse(
            stdout = emptyList(),
            stderr = listOf("fatal: The current branch feature has no upstream branch"),
            exitCode = 128,
            branch = "feature"
        )
        assertTrue(outcome is PushOutcome.NoRemote)
        assertTrue((outcome as PushOutcome.NoRemote).message.contains("Publish"))
    }

    @Test
    fun `a project without a remote says so instead of guessing`() {
        val outcome = GitPushParser.parse(
            stdout = emptyList(),
            stderr = listOf("fatal: 'origin' does not exist"),
            exitCode = 128,
            branch = "main"
        )
        assertTrue(outcome is PushOutcome.NoRemote)
    }

    @Test
    fun `a missing token is an auth failure with the token help link`() {
        val outcome = GitPushParser.parse(
            stdout = emptyList(),
            stderr = listOf(
                "fatal: could not read Username for 'https://github.com': terminal prompts disabled"
            ),
            exitCode = 128,
            branch = "main"
        ) as PushOutcome.Auth
        assertEquals(GitErrorKind.AUTH_FAILED, outcome.kind)
        assertEquals(com.codeci.ide.ui.projects.GitErrors.TOKEN_HELP_URL, outcome.helpUrl)
    }

    @Test
    fun `a write without permission is a token-permission failure`() {
        val outcome = GitPushParser.parse(
            stdout = emptyList(),
            stderr = listOf("remote: Permission to u/r.git denied to someone.", "fatal: unable to access ..."),
            exitCode = 128,
            branch = "main"
        ) as PushOutcome.Auth
        assertEquals(GitErrorKind.TOKEN_PERMISSION, outcome.kind)
    }

    @Test
    fun `unknown text never becomes a guess`() {
        val outcome = GitPushParser.parse(
            stdout = emptyList(),
            stderr = listOf("fatal: something nobody has seen before"),
            exitCode = 1,
            branch = "main"
        ) as PushOutcome.Failed
        assertEquals("fatal: something nobody has seen before", outcome.detail)
        assertFalse(outcome.ok)
    }

    @Test
    fun `an exit zero without a refspec line still counts as pushed`() {
        val outcome = GitPushParser.parse(
            stdout = emptyList(),
            stderr = emptyList(),
            exitCode = 0,
            branch = "test-1"
        ) as PushOutcome.Pushed
        assertEquals("test-1", outcome.branch)
        assertTrue(outcome.ok)
    }

    @Test
    fun `a successful push attempt exposes its exit code verdict`() {
        assertTrue(GitPushAttempt(exitCode = 0, stdout = emptyList(), stderr = emptyList()).succeeded)
        assertFalse(GitPushAttempt(exitCode = 1).succeeded)
        assertEquals(2, GitPushAttempt(exitCode = 1, stdout = listOf("a"), stderr = listOf("b")).output.size)
    }

    // ---- 40.3 publish ------------------------------------------------------

    @Test
    fun `a project name is made GitHub-safe`() {
        assertEquals("My-App", GitHubPublish.nameFor("My App"))
        assertEquals("school-notes", GitHubPublish.nameFor("school/notes"))
        assertEquals("repo", GitHubPublish.nameFor(".repo."))
        assertEquals("codec-project", GitHubPublish.nameFor("///"))
        assertEquals("codec-project", GitHubPublish.nameFor("   "))
        // Non-ASCII letters are NOT allowed by GitHub, so they are mapped away.
        assertEquals("caf-note", GitHubPublish.nameFor("café note"))
        assertTrue(GitHubPublish.nameFor("a".repeat(400)).length <= 100)
    }

    @Test
    fun `a colliding name gets a numbered alternative`() {
        assertEquals("notes-2", GitHubPublish.alternativeName("notes", 2))
        assertEquals("notes-3", GitHubPublish.alternativeName("notes", 3))
        assertTrue(GitHubPublish.alternativeName("a".repeat(120), 2).length <= 100)
    }

    @Test
    fun `the request body is valid JSON and private by default is explicit`() {
        val body = GitHubPublish.body("My-App", "a \"quoted\" description", isPrivate = true)
        assertTrue(body.contains("\"name\":\"My-App\""))
        assertTrue(body.contains("\"private\":true"))
        assertTrue(body.contains("\\\"quoted\\\""))
        assertTrue(body.startsWith("{") && body.endsWith("}"))
        assertTrue(GitHubPublish.body("x", null, isPrivate = false).contains("\"private\":false"))
        assertTrue(GitHubPublish.body("x", null, isPrivate = false).contains("\"description\":null"))
    }

    @Test
    fun `a created repository becomes an https remote`() {
        val result = GitHubPublish.parseCreateResponse(
            code = 201,
            body = """{"html_url":"https://github.com/u/r","ssh_url":"git@github.com:u/r.git",""" +
                """"default_branch":"main","private":true}""",
            acceptedPermissions = null
        ) as PublishResult.Published
        assertEquals("https://github.com/u/r.git", result.remoteUrl)
        assertEquals("https://github.com/u/r", result.htmlUrl)
        assertEquals("main", result.defaultBranch)
        assertTrue(result.isPrivate)
    }

    @Test
    fun `a missing token is a token error with a fix`() {
        val error = GitHubPublish.parseCreateResponse(401, """{"message":"Bad credentials"}""")
            as PublishResult.ApiError
        assertEquals(PublishErrorKind.TOKEN_MISSING, error.kind)
        assertNotNull(error.helpUrl)
    }

    @Test
    fun `a fine-grained token that cannot create repos offers the browser path`() {
        val error = GitHubPublish.parseCreateResponse(
            code = 403,
            body = """{"message":"Resource not accessible by personal access token"}""",
            acceptedPermissions = "administration=write"
        ) as PublishResult.ApiError
        assertEquals(PublishErrorKind.PERMISSION_MISSING, error.kind)
        assertEquals("administration=write", error.needsPermission)
        assertEquals(GitHubPublish.NEW_REPO_URL, error.helpUrl)
    }

    @Test
    fun `a rate-limited token is told to wait`() {
        val error = GitHubPublish.parseCreateResponse(
            403,
            """{"message":"API rate limit exceeded for user ID 1"}"""
        ) as PublishResult.ApiError
        assertEquals(PublishErrorKind.RATE_LIMITED, error.kind)
    }

    @Test
    fun `a taken name is a conversation, not a crash`() {
        val error = GitHubPublish.parseCreateResponse(
            422,
            """{"message":"Repository creation failed.","errors":[{"message":"name already exists on this account"}]}"""
        ) as PublishResult.ApiError
        assertEquals(PublishErrorKind.NAME_TAKEN, error.kind)
    }

    @Test
    fun `a server error is reported as temporary`() {
        val error = GitHubPublish.parseCreateResponse(500, "oops") as PublishResult.ApiError
        assertEquals(PublishErrorKind.SERVER, error.kind)
        assertTrue(error.message.contains("500"))
    }

    @Test
    fun `a success without a url is a bad response, never a fake success`() {
        val error = GitHubPublish.parseCreateResponse(201, "{}") as PublishResult.ApiError
        assertEquals(PublishErrorKind.BAD_RESPONSE, error.kind)
    }

    @Test
    fun `the https remote url always ends in dot git`() {
        assertEquals("https://github.com/u/r.git", GitHubPublish.remoteUrlFor("https://github.com/u/r"))
        assertEquals("https://github.com/u/r.git", GitHubPublish.remoteUrlFor("https://github.com/u/r/"))
        assertEquals("https://github.com/u/r.git", GitHubPublish.remoteUrlFor("https://github.com/u/r.git"))
    }

    @Test
    fun `json string extraction does not eat the first character`() {
        assertEquals("main", GitHubPublish.jsonString("""{"default_branch":"main"}""", "default_branch"))
        assertEquals("hello world", GitHubPublish.jsonString("""{"name": "hello world"}""", "name"))
        assertNull(GitHubPublish.jsonString("""{"other":"x"}""", "name"))
        assertEquals("a\"b", GitHubPublish.jsonString("""{"name":"a\"b"}""", "name"))
    }

    @Test
    fun `only https and scp-style remotes are handed to git`() {
        assertTrue(GitManager.isSafeRemoteUrl("https://github.com/u/r.git"))
        assertTrue(GitManager.isSafeRemoteUrl("git@github.com:u/r.git"))
        assertFalse(GitManager.isSafeRemoteUrl("/tmp/local/path"))
        assertFalse(GitManager.isSafeRemoteUrl("https://github.com/u/r.git; rm -rf /"))
        assertFalse(GitManager.isSafeRemoteUrl(""))
    }
}
