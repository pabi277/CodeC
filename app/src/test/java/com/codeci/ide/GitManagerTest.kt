package com.codeci.ide

import com.codeci.ide.ui.projects.GitCredentials
import com.codeci.ide.ui.projects.GitIdentity
import com.codeci.ide.ui.projects.GitManager
import java.io.File
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * Phase 13 — host-JVM tests for the Git engine: a fake `git` shell script
 * (CI runners have /bin/sh, same trick as ExecutionRunnerTest) records the
 * exact argv GitManager builds and replays canned porcelain/output, so
 * command construction, environment injection, redaction, and parsing are
 * exercised end to end through real processes.
 */
class GitManagerTest {

    private fun tempDir(): File = File.createTempFile("codec-git", "").apply {
        delete()
        mkdirs()
    }

    /** Writes the fake git binary: logs argv, then replays canned env-driven output. */
    private fun fakeGit(dir: File): File {
        val script = File(dir, "git")
        script.writeText(
            """
            #!/bin/sh
            {
              printf 'CMD'
              for a in "${'$'}@"; do printf ' [%s]' "${'$'}a"; done
              printf '\n'
            } >> "${'$'}FAKE_LOG"
            # Global flags come before the subcommand (e.g. --no-pager show).
            case "${'$'}1" in
              --no-pager) shift ;;
            esac
            case "${'$'}1" in
              --version)
                echo "git version 2.45-fake"
                exit 0
                ;;
              status)
                sleep "${'$'}{FAKE_STATUS_SLEEP:-0}"
                if [ -n "${'$'}FAKE_STATUS_OUT" ]; then printf '%b\n' "${'$'}FAKE_STATUS_OUT"; fi
                exit "${'$'}{FAKE_STATUS_EXIT:-0}"
                ;;
              show)
                if [ -n "${'$'}FAKE_SHOW_OUT" ]; then printf '%b\n' "${'$'}FAKE_SHOW_OUT"; fi
                exit "${'$'}{FAKE_SHOW_EXIT:-0}"
                ;;
              push)
                printf 'prompt=%s\n' "${'$'}GIT_TERMINAL_PROMPT"
                printf 'token=%s\n' "${'$'}CODEC_GIT_TOKEN"
                printf 'askpass=%s\n' "${'$'}GIT_ASKPASS"
                exit "${'$'}{FAKE_PUSH_EXIT:-0}"
                ;;
              clone)
                # Destination is the final argument (flags like --depth 1
                # --branch x shift the positional ones). POSIX sh only.
                last=""
                for a in "${'$'}@"; do last="${'$'}a"; done
                if [ -n "${'$'}last" ]; then mkdir -p "${'$'}last"; fi
                exit "${'$'}{FAKE_CLONE_EXIT:-0}"
                ;;
              ls-remote)
                if [ -n "${'$'}FAKE_LSREMOTE_OUT" ]; then printf '%b\n' "${'$'}FAKE_LSREMOTE_OUT"; fi
                if [ -n "${'$'}FAKE_LSREMOTE_ERR" ]; then printf '%b\n' "${'$'}FAKE_LSREMOTE_ERR" >&2; fi
                exit "${'$'}{FAKE_LSREMOTE_EXIT:-0}"
                ;;
            esac
            exit "${'$'}{FAKE_EXIT:-0}"
            """.trimIndent()
        )
        script.setExecutable(true)
        return script
    }

    private fun baseEnv(dir: File, extra: Map<String, String> = emptyMap()): Map<String, String> =
        mapOf(
            "PATH" to "/usr/bin:/bin",
            "FAKE_LOG" to File(dir, "calls.log").path
        ) + extra

    private fun manager(
        dir: File,
        env: Map<String, String>,
        auth: GitCredentials? = null,
        askpass: File? = null,
        localTimeout: Long = 15L,
        networkTimeout: Long = 15L
    ): GitManager = GitManager(
        gitBinary = fakeGit(dir),
        baseEnv = env,
        auth = auth,
        identity = GitIdentity("Owner", "owner@example.com"),
        askpassFile = askpass,
        localTimeoutSeconds = localTimeout,
        networkTimeoutSeconds = networkTimeout
    )

    private fun loggedCommands(log: File): List<String> = log.readLines()

    // --- availability ---

    @Test
    fun `isAvailable is true for a working binary`() = runBlocking {
        withTimeout(20_000) {
            val dir = tempDir()
            assertTrue(manager(dir, baseEnv(dir)).isAvailable())
        }
    }

    @Test
    fun `isAvailable is false for a missing binary`() = runBlocking {
        withTimeout(20_000) {
            val dir = tempDir()
            val missing = GitManager(
                gitBinary = File(dir, "no-such-git"),
                baseEnv = baseEnv(dir)
            )
            assertFalse(missing.isAvailable())
        }
    }

    // --- status ---

    @Test
    fun `status builds the porcelain command and parses the result`() = runBlocking {
        withTimeout(20_000) {
            val dir = tempDir()
            val env = baseEnv(
                dir,
                extra = mapOf(
                    "FAKE_STATUS_OUT" to "## main...origin/main [ahead 1]\\n M main.c\\n?? notes.txt"
                )
            )
            val workDir = File(dir, "repo").apply { mkdirs() }
            val status = manager(dir, env).status(workDir)

            assertEquals("main", status.branch)
            assertEquals("origin/main", status.upstream)
            assertEquals(1, status.ahead)
            assertEquals(2, status.files.size)
            assertEquals("main.c", status.files[0].path)
            assertEquals("notes.txt", status.files[1].path)

            val last = loggedCommands(File(env["FAKE_LOG"]!!)).last()
            assertTrue(last.contains("[status]"))
            assertTrue(last.contains("[--porcelain=v1]"))
            assertTrue(last.contains("[-b]"))
        }
    }

    @Test
    fun `status failure throws with redacted message`() = runBlocking {
        withTimeout(20_000) {
            val dir = tempDir()
            val env = baseEnv(
                dir,
                extra = mapOf(
                    "FAKE_STATUS_OUT" to "fatal: bad url https://oauth2:ghs_secret@github.com/u/r.git",
                    "FAKE_STATUS_EXIT" to "128"
                )
            )
            val workDir = File(dir, "repo").apply { mkdirs() }
            try {
                manager(dir, env).status(workDir)
                fail("expected GitCommandException")
            } catch (e: GitManager.GitCommandException) {
                assertEquals(128, e.exitCode)
                assertFalse(e.message!!.contains("ghs_secret"))
                assertTrue(e.message!!.contains("https://***@"))
            }
        }
    }

    // --- commit ---

    @Test
    fun `commit passes identity via -c and the message via -m`() = runBlocking {
        withTimeout(20_000) {
            val dir = tempDir()
            val env = baseEnv(dir)
            val workDir = File(dir, "repo").apply { mkdirs() }
            manager(dir, env).commit(workDir, "docs: test mobile commit")

            val last = loggedCommands(File(env["FAKE_LOG"]!!)).last()
            assertTrue(last.contains("[user.name=Owner]"))
            assertTrue(last.contains("[user.email=owner@example.com]"))
            assertTrue(last.contains("[commit]"))
            assertTrue(last.contains("[-m]"))
            assertTrue(last.contains("[docs: test mobile commit]"))
        }
    }

    // --- push / environment injection / redaction ---

    @Test
    fun `push failure output is redacted and env injection is visible`() = runBlocking {
        withTimeout(20_000) {
            val dir = tempDir()
            val env = baseEnv(dir, extra = mapOf("FAKE_PUSH_EXIT" to "128"))
            val workDir = File(dir, "repo").apply { mkdirs() }
            val askpass = File(dir, "askpass.sh")
            val git = manager(
                dir = dir,
                env = env,
                auth = GitCredentials("ghs_secrettoken", "bob"),
                askpass = askpass
            )
            try {
                git.push(workDir)
                fail("expected GitCommandException")
            } catch (e: GitManager.GitCommandException) {
                val all = (listOf(e.message ?: "") + e.output).joinToString("\n")
                assertFalse(all.contains("ghs_secrettoken"))
                assertTrue(all.contains("token=***"))
                assertTrue(all.contains("prompt=0"))
                // The askpass file itself must never contain the token.
                assertTrue(askpass.isFile)
                assertTrue(askpass.canExecute())
                assertFalse(askpass.readText().contains("ghs_secrettoken"))
                // And git was told where it lives.
                assertTrue(all.contains("askpass=${askpass.absolutePath}"))
            }
        }
    }

    @Test
    fun `push without auth does not expose askpass`() = runBlocking {
        withTimeout(20_000) {
            val dir = tempDir()
            val env = baseEnv(dir)
            val workDir = File(dir, "repo").apply { mkdirs() }
            manager(dir, env).push(workDir)
            val last = loggedCommands(File(env["FAKE_LOG"]!!)).last()
            // No crash and no credential plumbing without stored credentials.
            assertFalse(last.contains("askpass"))
        }
    }

    // --- clone ---

    @Test
    fun `clone validates url and destination and passes both through`() = runBlocking {
        withTimeout(20_000) {
            val dir = tempDir()
            val env = baseEnv(dir)
            val git = manager(dir, env)

            try {
                git.clone("git@github.com:u/r.git", File(dir, "a"))
                fail("expected non-https URL to be rejected")
            } catch (_: IllegalArgumentException) {
            }

            val existing = File(dir, "b").apply { mkdirs() }
            try {
                git.clone("https://github.com/u/r.git", existing)
                fail("expected existing destination to be rejected")
            } catch (_: IllegalArgumentException) {
            }

            val dest = File(dir, "ClonedRepo")
            git.clone("https://github.com/u/ClonedRepo.git", dest)
            assertTrue(dest.isDirectory)
            val last = loggedCommands(File(env["FAKE_LOG"]!!)).last()
            assertTrue(last.contains("[clone]"))
            assertTrue(last.contains("[https://github.com/u/ClonedRepo.git]"))
            assertTrue(last.contains("[${dest.absolutePath}]"))
        }
    }

    // --- clone flags (Phase 15) ---

    @Test
    fun `clone with defaults sends no extra flags`() = runBlocking {
        withTimeout(20_000) {
            val dir = tempDir()
            val env = baseEnv(dir)
            val dest = File(dir, "plain")
            manager(dir, env).clone("https://github.com/u/r.git", dest)
            val last = loggedCommands(File(env["FAKE_LOG"]!!)).last()
            assertEquals("CMD [clone] [https://github.com/u/r.git] [${dest.absolutePath}]", last)
        }
    }

    @Test
    fun `clone shallow and branch prepend --depth and --branch before the url`() = runBlocking {
        withTimeout(20_000) {
            val dir = tempDir()
            val env = baseEnv(dir)
            val dest = File(dir, "dev")
            manager(dir, env).clone(
                "https://github.com/u/r.git",
                dest,
                shallow = true,
                branch = "dev"
            )
            val last = loggedCommands(File(env["FAKE_LOG"]!!)).last()
            assertEquals(
                "CMD [clone] [--depth] [1] [--branch] [dev] [https://github.com/u/r.git] [${dest.absolutePath}]",
                last
            )
            assertTrue(dest.isDirectory)
        }
    }

    @Test
    fun `clone rejects an invalid branch name before running git`() = runBlocking {
        withTimeout(20_000) {
            val dir = tempDir()
            val env = baseEnv(dir)
            val git = manager(dir, env)
            for (bad in listOf("--upload-pack=evil", "a b", "x..y", "refs/heads/main", "")) {
                try {
                    git.clone("https://github.com/u/r.git", File(dir, "no-dest-$bad.length"), branch = bad)
                    fail("expected invalid branch: $bad")
                } catch (_: IllegalArgumentException) {
                }
            }
            // Nothing was ever executed: the log file was not created.
            assertFalse(File(env["FAKE_LOG"]!!).exists())
        }
    }

    // --- ls-remote (Phase 15) ---

    @Test
    fun `listRemoteBranches builds the command and parses heads`() = runBlocking {
        withTimeout(20_000) {
            val dir = tempDir()
            val env = baseEnv(
                dir,
                extra = mapOf(
                    "FAKE_LSREMOTE_OUT" to
                        "aaaa\\trefs/heads/main\\nbbbb\\trefs/heads/dev\\ncccc\\trefs/tags/v1\\n"
                )
            )
            val branches = manager(dir, env).listRemoteBranches("https://github.com/u/r.git")
            assertEquals(listOf("main", "dev"), branches)
            val last = loggedCommands(File(env["FAKE_LOG"]!!)).last()
            assertTrue(last.contains("[ls-remote]"))
            assertTrue(last.contains("[--heads]"))
        }
    }

    @Test
    fun `listRemoteBranches surfaces a redacted failure`() = runBlocking {
        withTimeout(20_000) {
            val dir = tempDir()
            val env = baseEnv(
                dir,
                extra = mapOf(
                    "FAKE_LSREMOTE_ERR" to "fatal: could not read Username for 'https://github.com'",
                    "FAKE_LSREMOTE_EXIT" to "128"
                )
            )
            try {
                manager(dir, env).listRemoteBranches("https://github.com/u/private.git")
                fail("expected GitCommandException")
            } catch (e: GitManager.GitCommandException) {
                assertEquals(128, e.exitCode)
                assertTrue(e.message!!.contains("git ls-remote failed"))
            }
        }
    }

    @Test
    fun `listRemoteBranches rejects non-http urls`() = runBlocking {
        withTimeout(20_000) {
            val dir = tempDir()
            try {
                manager(dir, baseEnv(dir)).listRemoteBranches("git@github.com:u/r.git")
                fail("expected IllegalArgumentException")
            } catch (_: IllegalArgumentException) {
            }
        }
    }

    // --- head file content ---

    @Test
    fun `headFileContent returns content on success and null on miss`() = runBlocking {
        withTimeout(20_000) {
            val dir = tempDir()
            val env = baseEnv(
                dir,
                extra = mapOf("FAKE_SHOW_OUT" to "int main(void) {\\n    return 0;\\n}\\n")
            )
            val workDir = File(dir, "repo").apply { mkdirs() }
            val git = manager(dir, env)
            val content = git.headFileContent(workDir, "main.c")
            assertNotNull(content)
            assertTrue(content!!.contains("int main(void)"))

            val missingEnv = baseEnv(dir, extra = mapOf("FAKE_SHOW_EXIT" to "1"))
            assertNull(manager(dir, missingEnv).headFileContent(workDir, "new-file.c"))
        }
    }

    // --- timeout ---

    @Test
    fun `hung command times out instead of blocking forever`() = runBlocking {
        withTimeout(30_000) {
            val dir = tempDir()
            val env = baseEnv(dir, extra = mapOf("FAKE_STATUS_SLEEP" to "5"))
            val workDir = File(dir, "repo").apply { mkdirs() }
            try {
                manager(dir, env, localTimeout = 1).status(workDir)
                fail("expected timeout")
            } catch (e: GitManager.GitCommandException) {
                assertEquals(124, e.exitCode)
                assertTrue(e.message!!.contains("timed out"))
            }
        }
    }

    // --- askpass helper script ---

    @Test
    fun `askpass script answers username and password prompts`() {
        val dir = tempDir()
        val script = File(dir, "askpass")
        script.writeText(GitManager.askpassBody(shebang = "#!/bin/sh"))
        script.setExecutable(true)
        assertFalse(script.readText().contains("tok123"))

        fun ask(prompt: String, env: Map<String, String>): String =
            ProcessBuilder(script.absolutePath, prompt)
                .apply {
                    environment().clear()
                    environment().putAll(env)
                }
                .start()
                .inputStream
                .bufferedReader()
                .use { it.readText().trim() }

        val full = mapOf("CODEC_GIT_TOKEN" to "tok123", "CODEC_GIT_USERNAME" to "bob")
        assertEquals("tok123", ask("Password for 'https://bob@github.com':", full))
        assertEquals("bob", ask("Username for 'https://github.com':", full))
        // Without a stored username git falls back to the conventional oauth2.
        assertEquals("oauth2", ask("Username for 'https://github.com':", mapOf("CODEC_GIT_TOKEN" to "tok123")))
        assertEquals("tok123", ask("Password:", mapOf("CODEC_GIT_TOKEN" to "tok123")))
    }
}
