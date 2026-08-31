package com.codeci.ide

import com.codeci.ide.ui.projects.BranchTarget
import com.codeci.ide.ui.projects.BranchTargetKind
import com.codeci.ide.ui.projects.GitManager
import com.codeci.ide.ui.projects.GitStatusParser
import java.io.File
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * Phase 17 — argv proofs for the branch/stash engine: a fake `git` shell
 * script records the exact commands [GitManager] builds (CI runners have
 * /bin/sh), so the ordering of the Switch Branch flow — stash → checkout →
 * auto-restore — is verified without a real repository.
 *
 * Same harness style as `GitManagerTest` (Phase 13).
 */
class GitBranchManagerTest {

    private fun tempDir(): File = File.createTempFile("codec-branch", "").apply {
        delete()
        mkdirs()
    }

    /** Writes the fake git binary: logs argv, then replays canned env output. */
    private fun fakeGit(dir: File): File {
        val script = File(dir, "git")
        script.writeText(
            """
            #!/bin/sh
            {
              printf 'CMD'
              for a in "${'$'}@"; do printf ' [%s]' "${'$'}a"; done
              printf '\\n'
            } >> "${'$'}FAKE_LOG"
            case "${'$'}1" in
              --version)
                echo "git version 2.45-fake"
                exit 0
                ;;
              branch)
                if [ -n "${'$'}FAKE_BRANCH_OUT" ]; then printf '%b\\n' "${'$'}FAKE_BRANCH_OUT"; fi
                exit "${'$'}{FAKE_BRANCH_EXIT:-0}"
                ;;
              rev-parse)
                if [ -n "${'$'}FAKE_REVPARSE_OUT" ]; then printf '%b\\n' "${'$'}FAKE_REVPARSE_OUT"; fi
                exit "${'$'}{FAKE_REVPARSE_EXIT:-0}"
                ;;
              checkout)
                if [ -n "${'$'}FAKE_CHECKOUT_OUT" ]; then printf '%b\\n' "${'$'}FAKE_CHECKOUT_OUT"; fi
                exit "${'$'}{FAKE_CHECKOUT_EXIT:-0}"
                ;;
              stash)
                case "${'$'}2" in
                  list)
                    if [ -n "${'$'}FAKE_STASH_OUT" ]; then printf '%b\\n' "${'$'}FAKE_STASH_OUT"; fi
                    exit 0
                    ;;
                  push)
                    if [ -n "${'$'}FAKE_STASH_PUSH_OUT" ]; then printf '%b\\n' "${'$'}FAKE_STASH_PUSH_OUT"; fi
                    exit "${'$'}{FAKE_STASH_PUSH_EXIT:-0}"
                    ;;
                  pop)
                    exit "${'$'}{FAKE_STASH_POP_EXIT:-0}"
                    ;;
                esac
                exit 0
                ;;
              status)
                if [ -n "${'$'}FAKE_STATUS_OUT" ]; then printf '%b\\n' "${'$'}FAKE_STATUS_OUT"; fi
                exit "${'$'}{FAKE_STATUS_EXIT:-0}"
                ;;
            esac
            exit "${'$'}{FAKE_EXIT:-0}"
            """.trimIndent()
        )
        script.setExecutable(true)
        return script
    }

    private fun env(dir: File, extra: Map<String, String> = emptyMap()): Map<String, String> =
        mapOf(
            "PATH" to "/usr/bin:/bin",
            "FAKE_LOG" to File(dir, "calls.log").path
        ) + extra

    private fun manager(dir: File, env: Map<String, String>): GitManager = GitManager(
        gitBinary = fakeGit(dir),
        baseEnv = env,
        localTimeoutSeconds = 15L,
        networkTimeoutSeconds = 15L
    )

    private fun log(env: Map<String, String>): List<String> =
        File(env["FAKE_LOG"]!!).readLines()

    private fun repo(dir: File): File = File(dir, "repo").apply { mkdirs() }

    // --- branch listing ----------------------------------------------------

    @Test
    fun `listBranches builds the branch command and parses local and remote`() = runBlocking {
        withTimeout(20_000) {
            val dir = tempDir()
            val e = env(
                dir,
                mapOf(
                    "FAKE_BRANCH_OUT" to
                        "  feature/x\\n* main\\n  remotes/origin/HEAD -> origin/main\\n  remotes/origin/develop"
                )
            )
            val list = manager(dir, e).listBranches(repo(dir))
            assertEquals("CMD [branch] [--all] [--no-color]", log(e).first())
            assertEquals(listOf("feature/x", "main"), list.local.map { it.name })
            assertEquals(listOf("origin/develop"), list.remote.map { it.name })
            assertEquals("main", list.current)
        }
    }

    @Test
    fun `currentBranch reads rev-parse and reports detached HEAD as null`() = runBlocking {
        withTimeout(20_000) {
            val dir = tempDir()
            val e = env(dir, mapOf("FAKE_REVPARSE_OUT" to "feature/x"))
            assertEquals("feature/x", manager(dir, e).currentBranch(repo(dir)))
            assertEquals("CMD [rev-parse] [--abbrev-ref] [HEAD]", log(e).last())

            val detached = tempDir()
            val e2 = env(detached, mapOf("FAKE_REVPARSE_OUT" to "HEAD"))
            assertNull(manager(detached, e2).currentBranch(repo(detached)))
        }
    }

    // --- single checkouts ---------------------------------------------------

    @Test
    fun `checkout a local branch uses a plain checkout`() = runBlocking {
        withTimeout(20_000) {
            val dir = tempDir()
            val e = env(dir)
            manager(dir, e).checkout(repo(dir), "main")
            assertEquals("CMD [checkout] [main]", log(e).last())
        }
    }

    @Test
    fun `checkoutNew creates the branch with -b`() = runBlocking {
        withTimeout(20_000) {
            val dir = tempDir()
            val e = env(dir)
            manager(dir, e).checkoutNew(repo(dir), "feature/login")
            assertEquals("CMD [checkout] [-b] [feature/login]", log(e).last())
        }
    }

    @Test
    fun `checkoutRemote creates a tracking branch instead of detaching HEAD`() = runBlocking {
        withTimeout(20_000) {
            val dir = tempDir()
            val e = env(dir)
            manager(dir, e).checkoutRemote(repo(dir), "origin/develop")
            assertEquals(
                "CMD [checkout] [-b] [develop] [--track] [origin/develop]",
                log(e).last()
            )
        }
    }

    @Test
    fun `stashPush includes untracked files and the marker message`() = runBlocking {
        withTimeout(20_000) {
            val dir = tempDir()
            val e = env(dir)
            val created = manager(dir, e).stashPush(repo(dir), "codec-switch: main")
            assertTrue(created)
            assertEquals(
                "CMD [stash] [push] [-u] [-m] [codec-switch: main]",
                log(e).last()
            )
        }
    }

    @Test
    fun `stashPush reports false when there was nothing to save`() = runBlocking {
        withTimeout(20_000) {
            val dir = tempDir()
            val e = env(dir, mapOf("FAKE_STASH_PUSH_OUT" to "No local changes to save"))
            assertFalse(manager(dir, e).stashPush(repo(dir), "codec-switch: main"))
        }
    }

    @Test
    fun `stashPop defaults to the newest entry`() = runBlocking {
        withTimeout(20_000) {
            val dir = tempDir()
            val e = env(dir)
            manager(dir, e).stashPop(repo(dir))
            assertEquals("CMD [stash] [pop] [stash@{0}]", log(e).last())
        }
    }

    // --- the switch flow -----------------------------------------------------

    @Test
    fun `switchBranch stashes a dirty tree first and parks the changes`() = runBlocking {
        withTimeout(20_000) {
            val dir = tempDir()
            val e = env(
                dir,
                mapOf("FAKE_STATUS_OUT" to "## main...origin/main\\n M src/app.py\\n?? notes.txt")
            )
            val result = manager(dir, e).switchBranch(
                repo(dir),
                BranchTarget("feature", BranchTargetKind.LOCAL)
            )
            val commands = log(e)
            assertEquals("CMD [status] [--porcelain=v1] [-b]", commands[0])
            assertEquals("CMD [stash] [push] [-u] [-m] [codec-switch: main]", commands[1])
            assertEquals("CMD [checkout] [feature]", commands[2])
            assertEquals("CMD [stash] [list]", commands[3])
            assertEquals(4, commands.size)
            assertEquals("feature", result.branch)
            assertTrue(result.stashed)
            assertFalse(result.restored)
            assertFalse(result.stashPending)
        }
    }

    @Test
    fun `switchBranch restores a CodeC stash belonging to the target branch`() = runBlocking {
        withTimeout(20_000) {
            val dir = tempDir()
            val e = env(
                dir,
                mapOf(
                    "FAKE_STATUS_OUT" to "## main...origin/main\\n M src/app.py",
                    "FAKE_STASH_OUT" to "stash@{0}: On feature: codec-switch: feature"
                )
            )
            val result = manager(dir, e).switchBranch(
                repo(dir),
                BranchTarget("feature", BranchTargetKind.LOCAL)
            )
            val commands = log(e)
            assertEquals("CMD [checkout] [feature]", commands[2])
            assertEquals("CMD [stash] [list]", commands[3])
            assertEquals("CMD [stash] [pop] [stash@{0}]", commands[4])
            assertTrue(result.stashed)
            assertTrue(result.restored)
            assertFalse(result.stashPending)
        }
    }

    @Test
    fun `switchBranch leaves a foreign stash alone`() = runBlocking {
        withTimeout(20_000) {
            val dir = tempDir()
            val e = env(
                dir,
                mapOf(
                    "FAKE_STATUS_OUT" to "## main...origin/main\\n M src/app.py",
                    "FAKE_STASH_OUT" to "stash@{0}: WIP on feature: 1a2b3c4 unrelated work"
                )
            )
            val result = manager(dir, e).switchBranch(
                repo(dir),
                BranchTarget("feature", BranchTargetKind.LOCAL)
            )
            assertTrue(log(e).none { it.contains("[pop]") })
            assertFalse(result.restored)
        }
    }

    @Test
    fun `switchBranch does not stash a clean tree`() = runBlocking {
        withTimeout(20_000) {
            val dir = tempDir()
            val e = env(dir, mapOf("FAKE_STATUS_OUT" to "## main...origin/main"))
            val result = manager(dir, e).switchBranch(
                repo(dir),
                BranchTarget("feature", BranchTargetKind.LOCAL)
            )
            val commands = log(e)
            assertEquals("CMD [checkout] [feature]", commands[1])
            assertEquals("CMD [stash] [list]", commands[2])
            assertFalse(result.stashed)
        }
    }

    @Test
    fun `switchBranch pops the stash back when the checkout fails`() = runBlocking {
        withTimeout(20_000) {
            val dir = tempDir()
            val e = env(
                dir,
                mapOf(
                    "FAKE_STATUS_OUT" to "## main...origin/main\\n M src/app.py",
                    "FAKE_CHECKOUT_EXIT" to "1",
                    "FAKE_CHECKOUT_OUT" to "error: pathspec 'feature' did not match"
                )
            )
            try {
                manager(dir, e).switchBranch(repo(dir), BranchTarget("feature", BranchTargetKind.LOCAL))
                fail("a failing checkout must surface an error")
            } catch (_: Exception) {
                // expected
            }
            val commands = log(e)
            assertEquals("CMD [stash] [push] [-u] [-m] [codec-switch: main]", commands[1])
            assertEquals("CMD [checkout] [feature]", commands[2])
            // The park is undone, so the user's work is never left in limbo.
            assertEquals("CMD [stash] [pop] [stash@{0}]", commands[3])
        }
    }

    @Test
    fun `switchBranch reports a conflicting restore without losing it`() = runBlocking {
        withTimeout(20_000) {
            val dir = tempDir()
            val e = env(
                dir,
                mapOf(
                    "FAKE_STATUS_OUT" to "## main...origin/main\\n M src/app.py",
                    "FAKE_STASH_OUT" to "stash@{0}: On feature: codec-switch: feature",
                    "FAKE_STASH_POP_EXIT" to "1"
                )
            )
            val result = manager(dir, e).switchBranch(
                repo(dir),
                BranchTarget("feature", BranchTargetKind.LOCAL)
            )
            assertEquals("feature", result.branch)
            assertFalse(result.restored)
            // git keeps the entry when a pop conflicts, so nothing is lost —
            // the UI says the changes are still saved.
            assertTrue(result.stashPending)
        }
    }

    @Test
    fun `switchBranch with stashChanges off never touches the stash`() = runBlocking {
        withTimeout(20_000) {
            val dir = tempDir()
            val e = env(dir, mapOf("FAKE_STATUS_OUT" to "## main...origin/main\\n M src/app.py"))
            manager(dir, e).switchBranch(
                repo(dir),
                BranchTarget("feature", BranchTargetKind.LOCAL),
                stashChanges = false
            )
            val commands = log(e)
            assertEquals("CMD [status] [--porcelain=v1] [-b]", commands[0])
            assertEquals("CMD [checkout] [feature]", commands[1])
            assertEquals(2, commands.size)
        }
    }

    @Test
    fun `switchBranch validates the target before letting git see it`() = runBlocking {
        withTimeout(20_000) {
            val dir = tempDir()
            val e = env(dir, mapOf("FAKE_STATUS_OUT" to "## main...origin/main"))
            try {
                manager(dir, e).switchBranch(repo(dir), BranchTarget("--upload-pack=evil", BranchTargetKind.LOCAL))
                fail("an option-shaped branch name must be rejected")
            } catch (e2: IllegalArgumentException) {
                // expected — rejected before exec
            }
            assertTrue(log(e).none { it.contains("[checkout]") })
        }
    }

    @Test
    fun `switchBranch creates a new branch with -b`() = runBlocking {
        withTimeout(20_000) {
            val dir = tempDir()
            val e = env(dir, mapOf("FAKE_STATUS_OUT" to "## main...origin/main"))
            val result = manager(dir, e).switchBranch(
                repo(dir),
                BranchTarget("feature/login", BranchTargetKind.NEW)
            )
            assertEquals("CMD [checkout] [-b] [feature/login]", log(e)[1])
            assertEquals("feature/login", result.branch)
        }
    }

    @Test
    fun `switchBranch reuses an existing local branch for a remote selection`() = runBlocking {
        withTimeout(20_000) {
            val dir = tempDir()
            val e = env(
                dir,
                mapOf(
                    "FAKE_STATUS_OUT" to "## main...origin/main",
                    "FAKE_BRANCH_OUT" to "* main\\n  develop\\n  remotes/origin/develop"
                )
            )
            manager(dir, e).switchBranch(
                repo(dir),
                BranchTarget("origin/develop", BranchTargetKind.REMOTE)
            )
            // A local `develop` already exists: check it out instead of
            // failing with "a branch named 'develop' already exists".
            val commands = log(e)
            assertEquals("CMD [branch] [--all] [--no-color]", commands[1])
            assertEquals("CMD [checkout] [develop]", commands[2])
        }
    }

    // --- conflicts -----------------------------------------------------------

    @Test
    fun `status marks conflicted files so the sheet can group and block them`() {
        val status = GitStatusParser.parse(
            listOf(
                "## main...origin/main",
                "UU src/app.py",
                "AA src/new.py",
                "DD src/gone.py",
                "AM src/staged.c",
                " M src/touched.c"
            )
        )
        val conflicts = status.files.filter { it.isConflict }
        assertEquals(listOf("src/app.py", "src/new.py", "src/gone.py"), conflicts.map { it.path })
        assertTrue(conflicts.all { it.badge == "U" })
        assertFalse(status.files.first { it.path == "src/staged.c" }.isConflict)
        assertFalse(status.files.first { it.path == "src/touched.c" }.isConflict)
    }
}
