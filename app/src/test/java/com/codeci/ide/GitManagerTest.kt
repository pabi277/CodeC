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
              init)
                # Phase 73.3 — `git init -b main` (git 2.28+) can fail on an
                # old git; GitManager then falls back to plain `init` +
                # `symbolic-ref`. FAKE_INIT_B_EXIT controls only the `-b`
                # attempt so both paths can be exercised independently.
                case "${'$'}2" in
                  -b)
                    exit "${'$'}{FAKE_INIT_B_EXIT:-0}"
                    ;;
                esac
                exit "${'$'}{FAKE_INIT_EXIT:-0}"
                ;;
              symbolic-ref)
                exit "${'$'}{FAKE_SYMBOLIC_REF_EXIT:-0}"
                ;;
              remote)
                case "${'$'}2" in
                  get-url)
                    if [ -n "${'$'}FAKE_REMOTE_URL_OUT" ]; then printf '%b\n' "${'$'}FAKE_REMOTE_URL_OUT"; fi
                    exit "${'$'}{FAKE_REMOTE_URL_EXIT:-0}"
                    ;;
                  add|remove)
                    exit "${'$'}{FAKE_REMOTE_EXIT:-0}"
                    ;;
                esac
                if [ -n "${'$'}FAKE_REMOTE_OUT" ]; then printf '%b\n' "${'$'}FAKE_REMOTE_OUT"; fi
                exit "${'$'}{FAKE_REMOTE_EXIT:-0}"
                ;;
              log)
                # Deliberately FAKE_GITLOG_* (not FAKE_LOG_*): FAKE_LOG is the
                # calls-log FILE PATH used by every case above, not git-log
                # output.
                if [ -n "${'$'}FAKE_GITLOG_OUT" ]; then printf '%b' "${'$'}FAKE_GITLOG_OUT"; fi
                exit "${'$'}{FAKE_GITLOG_EXIT:-0}"
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

    // --- per-file stage/unstage (Phase 15/16 mockup sheet) ---

    @Test
    fun `stage and unstage one file use the dash-dash protected argv`() = runBlocking {
        withTimeout(20_000) {
            val dir = tempDir()
            val env = baseEnv(dir)
            val workDir = File(dir, "repo").apply { mkdirs() }
            val git = manager(dir, env)
            git.stageFile(workDir, "src/app.py")
            git.unstageFile(workDir, "src/app.py")
            val log = loggedCommands(File(env["FAKE_LOG"]!!))
            assertEquals("CMD [add] [--] [src/app.py]", log[0])
            assertEquals("CMD [reset] [--] [src/app.py]", log[1])
        }
    }

    @Test
    fun `stageFile protects flag-looking paths from being parsed as options`() = runBlocking {
        withTimeout(20_000) {
            val dir = tempDir()
            val env = baseEnv(dir)
            val workDir = File(dir, "repo").apply { mkdirs() }
            manager(dir, env).stageFile(workDir, "-weird.c")
            val last = loggedCommands(File(env["FAKE_LOG"]!!)).last()
            assertEquals("CMD [add] [--] [-weird.c]", last)
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

    // --- Phase 73.3: init / checkout commit / revert all / remotes / log ---

    @Test
    fun `init uses git init -b main and does not fall back when it succeeds`() = runBlocking {
        withTimeout(20_000) {
            val dir = tempDir()
            val env = baseEnv(dir)
            val workDir = File(dir, "repo").apply { mkdirs() }
            manager(dir, env).init(workDir)
            val log = loggedCommands(File(env["FAKE_LOG"]!!))
            assertEquals(listOf("CMD [init] [-b] [main]"), log)
        }
    }

    @Test
    fun `init falls back to plain init plus symbolic-ref when -b is unsupported`() = runBlocking {
        withTimeout(20_000) {
            val dir = tempDir()
            val env = baseEnv(dir, extra = mapOf("FAKE_INIT_B_EXIT" to "1"))
            val workDir = File(dir, "repo").apply { mkdirs() }
            manager(dir, env).init(workDir)
            val log = loggedCommands(File(env["FAKE_LOG"]!!))
            assertEquals(
                listOf(
                    "CMD [init] [-b] [main]",
                    "CMD [init]",
                    "CMD [symbolic-ref] [HEAD] [refs/heads/main]"
                ),
                log
            )
        }
    }

    @Test
    fun `init refuses to run against a folder that is already a repository`() = runBlocking {
        withTimeout(20_000) {
            val dir = tempDir()
            val env = baseEnv(dir)
            val workDir = File(dir, "repo").apply { mkdirs() }
            File(workDir, ".git").mkdirs()
            try {
                manager(dir, env).init(workDir)
                fail("expected IllegalArgumentException")
            } catch (_: IllegalArgumentException) {
            }
            assertFalse(File(env["FAKE_LOG"]!!).exists())
        }
    }

    @Test
    fun `checkoutCommit checks out a full commit id`() = runBlocking {
        withTimeout(20_000) {
            val dir = tempDir()
            val env = baseEnv(dir)
            val workDir = File(dir, "repo").apply { mkdirs() }
            val sha = "a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2"
            manager(dir, env).checkoutCommit(workDir, sha)
            val last = loggedCommands(File(env["FAKE_LOG"]!!)).last()
            assertEquals("CMD [checkout] [$sha]", last)
        }
    }

    @Test
    fun `checkoutCommit rejects anything that is not a full 40-char hex id`() = runBlocking {
        withTimeout(20_000) {
            val dir = tempDir()
            val env = baseEnv(dir)
            val workDir = File(dir, "repo").apply { mkdirs() }
            val git = manager(dir, env)
            for (bad in listOf("abc1234", "-x", "a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1bz", "")) {
                try {
                    git.checkoutCommit(workDir, bad)
                    fail("expected invalid commit id: $bad")
                } catch (_: IllegalArgumentException) {
                }
            }
            assertFalse(File(env["FAKE_LOG"]!!).exists())
        }
    }

    @Test
    fun `revertAllChanges runs git reset --hard HEAD`() = runBlocking {
        withTimeout(20_000) {
            val dir = tempDir()
            val env = baseEnv(dir)
            val workDir = File(dir, "repo").apply { mkdirs() }
            manager(dir, env).revertAllChanges(workDir)
            val last = loggedCommands(File(env["FAKE_LOG"]!!)).last()
            assertEquals("CMD [reset] [--hard] [HEAD]", last)
        }
    }

    @Test
    fun `addRemote runs git remote add with the name and url`() = runBlocking {
        withTimeout(20_000) {
            val dir = tempDir()
            val env = baseEnv(dir)
            val workDir = File(dir, "repo").apply { mkdirs() }
            manager(dir, env).addRemote(workDir, "upstream", "https://github.com/u/r.git")
            val log = loggedCommands(File(env["FAKE_LOG"]!!))
            assertEquals(
                listOf(
                    "CMD [remote]",
                    "CMD [remote] [add] [upstream] [https://github.com/u/r.git]"
                ),
                log
            )
        }
    }

    @Test
    fun `removeRemote runs git remote remove for a remote that exists`() = runBlocking {
        withTimeout(20_000) {
            val dir = tempDir()
            val env = baseEnv(dir, extra = mapOf("FAKE_REMOTE_OUT" to "origin"))
            val workDir = File(dir, "repo").apply { mkdirs() }
            manager(dir, env).removeRemote(workDir, "origin")
            val log = loggedCommands(File(env["FAKE_LOG"]!!))
            assertEquals(listOf("CMD [remote]", "CMD [remote] [remove] [origin]"), log)
        }
    }

    @Test
    fun `removeRemote refuses a name that is not currently configured`() = runBlocking {
        withTimeout(20_000) {
            val dir = tempDir()
            val env = baseEnv(dir)
            val workDir = File(dir, "repo").apply { mkdirs() }
            try {
                manager(dir, env).removeRemote(workDir, "origin")
                fail("expected IllegalArgumentException")
            } catch (_: IllegalArgumentException) {
            }
            // hasRemote() still ran (git remote, empty list); remove never did.
            assertEquals(listOf("CMD [remote]"), loggedCommands(File(env["FAKE_LOG"]!!)))
        }
    }

    @Test
    fun `removeRemote rejects an invalid name before running git`() = runBlocking {
        withTimeout(20_000) {
            val dir = tempDir()
            val env = baseEnv(dir)
            val workDir = File(dir, "repo").apply { mkdirs() }
            try {
                manager(dir, env).removeRemote(workDir, "bad name!")
                fail("expected IllegalArgumentException")
            } catch (_: IllegalArgumentException) {
            }
            assertFalse(File(env["FAKE_LOG"]!!).exists())
        }
    }

    @Test
    fun `remotesDetailed lists every remote with its url`() = runBlocking {
        withTimeout(20_000) {
            val dir = tempDir()
            val env = baseEnv(
                dir,
                extra = mapOf(
                    "FAKE_REMOTE_OUT" to "origin\\nupstream",
                    "FAKE_REMOTE_URL_OUT" to "https://github.com/u/r.git"
                )
            )
            val workDir = File(dir, "repo").apply { mkdirs() }
            val remotes = manager(dir, env).remotesDetailed(workDir)
            assertEquals(2, remotes.size)
            assertEquals("origin", remotes[0].name)
            assertEquals("https://github.com/u/r.git", remotes[0].url)
            assertEquals("upstream", remotes[1].name)
            assertEquals("https://github.com/u/r.git", remotes[1].url)
        }
    }

    @Test
    fun `remotesDetailed shows a null url when the lookup fails`() = runBlocking {
        withTimeout(20_000) {
            val dir = tempDir()
            val env = baseEnv(
                dir,
                extra = mapOf("FAKE_REMOTE_OUT" to "origin", "FAKE_REMOTE_URL_EXIT" to "1")
            )
            val workDir = File(dir, "repo").apply { mkdirs() }
            val remotes = manager(dir, env).remotesDetailed(workDir)
            assertEquals(listOf(null), remotes.map { it.url })
        }
    }

    @Test
    fun `log parses newest-first commits and passes the limit through`() = runBlocking {
        withTimeout(20_000) {
            val dir = tempDir()
            val sep = "\u001F"
            val out =
                "1111111111111111111111111111111111111111${sep}1111111${sep}Owner${sep}" +
                    "2026-09-29T12:00:00+05:30${sep}Second commit\n" +
                    "2222222222222222222222222222222222222222${sep}2222222${sep}Owner${sep}" +
                    "2026-09-28T09:30:00+05:30${sep}First commit\n"
            val env = baseEnv(dir, extra = mapOf("FAKE_GITLOG_OUT" to out))
            val workDir = File(dir, "repo").apply { mkdirs() }
            val commits = manager(dir, env).log(workDir, limit = 10)

            assertEquals(2, commits.size)
            assertEquals("1111111111111111111111111111111111111111", commits[0].sha)
            assertEquals("1111111", commits[0].shortSha)
            assertEquals("Second commit", commits[0].subject)
            assertEquals("2222222", commits[1].shortSha)
            assertEquals("First commit", commits[1].subject)

            val last = loggedCommands(File(env["FAKE_LOG"]!!)).last()
            assertTrue(last.contains("[log]"))
            assertTrue(last.contains("[-n] [10]"))
        }
    }

    @Test
    fun `log returns an empty list instead of throwing when git fails`() = runBlocking {
        withTimeout(20_000) {
            val dir = tempDir()
            val env = baseEnv(dir, extra = mapOf("FAKE_GITLOG_EXIT" to "128"))
            val workDir = File(dir, "repo").apply { mkdirs() }
            assertTrue(manager(dir, env).log(workDir).isEmpty())
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
