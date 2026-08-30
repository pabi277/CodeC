package com.codeci.ide.ui.projects

import java.io.File
import java.io.BufferedReader
import java.io.InputStreamReader

/**
 * Phase 13 — the visual Git engine. Executes the real `git` binary installed
 * under `$PREFIX/bin/git` (package `git` from the CodeC repository) with a
 * private per-command environment and returns parsed, secret-scrubbed
 * results.
 *
 * Deliberately Android-free (like `ExecutionRunner`) so the command
 * construction, porcelain parsing, and credential redaction are all
 * unit-testable on the host JVM; the caller resolves the binary, base
 * environment, and stored credentials (see [GitContext]).
 *
 * Security model (docs/chat-phase13/PART_13_GITHUB.md §5):
 *  - Commands are passed as an argv LIST to ProcessBuilder — no shell — so
 *    no URL/branch/message can ever inject extra shell words.
 *  - The GitHub token travels only in the child process environment
 *    (`CODEC_GIT_TOKEN`) read back by a `GIT_ASKPASS` helper script. It is
 *    never on a command line, never written into `.git/config`, and never
 *    exported into terminal sessions (`ShellEnvironment.buildEnv` is
 *    untouched).
 *  - Every output line and error message passes through [GitRedactor] before
 *    it reaches the UI or [com.codeci.ide.ui.utils.AppLogger].
 */
class GitManager(
    private val gitBinary: File,
    private val baseEnv: Map<String, String>,
    private val auth: GitCredentials? = null,
    private val identity: GitIdentity? = null,
    private val askpassFile: File? = null,
    private val localTimeoutSeconds: Long = 60L,
    private val networkTimeoutSeconds: Long = 300L
) {

    companion object {

        /** Shebang for the on-device askpass script: /system/bin/sh always exists. */
        const val ASKPASS_SHEBANG = "#!/system/bin/sh"

        /**
         * The askpass helper git executes for credential prompts. It holds NO
         * secret — it reads the token from the environment git itself passed
         * down, so nothing token-bearing is ever written to disk beyond the
         * app-private DataStore. Username prompts get the stored username
         * (GitHub accepts any non-empty username for a PAT; `oauth2` is the
         * conventional default).
         */
        fun askpassBody(shebang: String = ASKPASS_SHEBANG): String = buildString {
            append(shebang).append('\n')
            append("case \"$1\" in\n")
            append("  *[Uu]sername*) printf '%s\\n' \"\${CODEC_GIT_USERNAME:-oauth2}\" ;;\n")
            append("  *) printf '%s\\n' \"\$CODEC_GIT_TOKEN\" ;;\n")
            append("esac\n")
        }

        /**
         * Derives a project name from an HTTPS repository URL:
         * `https://github.com/u/CodeC.git/` → `CodeC`. Returns null for
         * anything that does not look like an HTTP(S) clone URL.
         */
        fun repoNameFromUrl(url: String): String? {
            val trimmed = url.trim()
            if (!(trimmed.startsWith("https://") || trimmed.startsWith("http://"))) return null
            val path = trimmed.removePrefix("https://").removePrefix("http://")
                .substringAfter('/', "")
            if (path.isEmpty()) return null
            val last = path.substringBefore('?').trimEnd('/').substringAfterLast('/')
            val name = last.removeSuffix(".git")
            return ProjectPathUtils.sanitizeProjectName(name)
        }

        /** True when [url] is a cloneable HTTP(S) URL (no local paths, no scp syntax). */
        fun isCloneableUrl(url: String): Boolean =
            repoNameFromUrl(url) != null
    }

    /** Thrown when git exits non-zero. [message] is already redacted. */
    class GitCommandException(
        message: String,
        val exitCode: Int,
        val output: List<String>
    ) : Exception(message)

    private val redactor = GitRedactor(auth?.token)

    /** Best-effort capability check: binary present, executable, and runs. */
    fun isAvailable(): Boolean {
        if (!gitBinary.isFile || !gitBinary.canExecute()) return false
        return try {
            runGit(workingDir = null, args = listOf("--version"), timeoutSeconds = 15L).exitCode == 0
        } catch (_: Exception) {
            false
        }
    }

    /** True when [root] is a git work tree (has a `.git` directory). */
    fun isRepository(root: File): Boolean = File(root, ".git").isDirectory

    /**
     * `git status --porcelain=v1 -b` — branch/upstream plus per-file XY codes.
     */
    fun status(root: File): GitStatus {
        val result = runGit(
            workingDir = root,
            args = listOf("status", "--porcelain=v1", "-b"),
            timeoutSeconds = localTimeoutSeconds
        )
        if (result.exitCode != 0) {
            throw GitCommandException(
                redactor.redact((result.stderr + result.stdout).joinToString("\n").trim())
                    .ifEmpty { "git status failed" },
                result.exitCode,
                redactor.redactAll(result.stdout + result.stderr)
            )
        }
        return GitStatusParser.parse(result.stdout)
    }

    /**
     * The HEAD version of [relativePath] for the diff viewer, or null when the
     * file has no HEAD revision yet (new file). Output is redacted.
     */
    fun headFileContent(root: File, relativePath: String): String? {
        val safe = ProjectPathUtils.sanitizeRelativePath(relativePath) ?: return null
        if (safe.isEmpty()) return null
        val result = runGit(
            workingDir = root,
            args = listOf("--no-pager", "show", "HEAD:$safe"),
            timeoutSeconds = localTimeoutSeconds
        )
        if (result.exitCode != 0) return null
        return redactor.redact(result.stdout.joinToString("\n"))
    }

    /** Stage everything (`git add -A`) — the pane commits the whole tree. */
    fun stageAll(root: File) {
        exec(root, listOf("add", "-A"), localTimeoutSeconds, "git add failed")
    }

    /** `git commit -m <message>` with the stored (or fallback) author identity. */
    fun commit(root: File, message: String) {
        val args = mutableListOf<String>()
        val id = identity ?: GitIdentity.FALLBACK
        args += "-c"
        args += "user.name=${id.name}"
        args += "-c"
        args += "user.email=${id.email}"
        args += "commit"
        args += "-m"
        args += message
        exec(root, args, localTimeoutSeconds, "git commit failed")
    }

    /** `git push` — needs an upstream (clone sets it) and, for private remotes, a token. */
    fun push(root: File) {
        exec(root, listOf("push"), networkTimeoutSeconds, "git push failed")
    }

    /** `git pull` — merge auto-edit disabled so no editor can ever block. */
    fun pull(root: File) {
        exec(root, listOf("pull"), networkTimeoutSeconds, "git pull failed", extraEnv = mapOf("GIT_MERGE_AUTOEDIT" to "no"))
    }

    /**
     * `git clone <url> <dest>` — [url] must be HTTP(S) ([isCloneableUrl]) and
     * [dest] must not exist (the caller picks a unique name inside the
     * projects root).
     */
    fun clone(url: String, dest: File) {
        require(isCloneableUrl(url)) { "Only http(s) repository URLs can be cloned" }
        require(!dest.exists()) { "Destination already exists: ${dest.name}" }
        dest.parentFile?.mkdirs()
        exec(
            workingDir = dest.parentFile ?: error("clone destination has no parent"),
            args = listOf("clone", url.trim(), dest.absolutePath),
            timeoutSeconds = networkTimeoutSeconds,
            failureMessage = "git clone failed"
        )
    }

    private fun exec(
        workingDir: File?,
        args: List<String>,
        timeoutSeconds: Long,
        failureMessage: String,
        extraEnv: Map<String, String> = emptyMap()
    ) {
        val result = runGit(workingDir, args, timeoutSeconds, extraEnv)
        if (result.exitCode != 0) {
            val detail = redactor.redact((result.stderr + result.stdout).joinToString("\n").trim())
            throw GitCommandException(
                listOf(failureMessage, detail).filter { it.isNotEmpty() }.joinToString(": "),
                result.exitCode,
                redactor.redactAll(result.stdout + result.stderr)
            )
        }
    }

    private data class GitResult(
        val exitCode: Int,
        val stdout: List<String>,
        val stderr: List<String>
    )

    /**
     * Runs `<gitBinary> [args]` with the private environment. Streams stdout
     * and stderr through daemon pump threads (API-24-safe: no
     * `redirect*(File)`), waits with a poll loop so a hung network command
     * cannot outlive [timeoutSeconds].
     */
    private fun runGit(
        workingDir: File?,
        args: List<String>,
        timeoutSeconds: Long,
        extraEnv: Map<String, String> = emptyMap()
    ): GitResult {
        val argv = listOf(gitBinary.absolutePath) + args
        val process = ProcessBuilder(argv)
            .apply {
                if (workingDir != null) directory(workingDir)
                environment().clear()
                environment().putAll(baseEnv)
                environment().putAll(extraEnv)
                auth?.let { credentials ->
                    environment()["CODEC_GIT_TOKEN"] = credentials.token
                    environment()["CODEC_GIT_USERNAME"] =
                        credentials.username?.takeIf { it.isNotBlank() } ?: "oauth2"
                    askpassFile?.let { file ->
                        writeAskpass(file)
                        environment()["GIT_ASKPASS"] = file.absolutePath
                    }
                }
                // Never let git block on a terminal prompt it cannot read.
                environment()["GIT_TERMINAL_PROMPT"] = "0"
            }
            .start()

        val stdout = mutableListOf<String>()
        val stderr = mutableListOf<String>()
        val outThread = pump(process.inputStream, stdout)
        val errThread = pump(process.errorStream, stderr)
        outThread.start()
        errThread.start()

        val deadline = System.nanoTime() + timeoutSeconds * 1_000_000_000L
        var exited = false
        while (System.nanoTime() < deadline) {
            exited = try {
                process.exitValue()
                true
            } catch (_: IllegalThreadStateException) {
                false
            }
            if (exited) break
            // Blocking poll — these calls always run on Dispatchers.IO, and
            // Process.waitFor(timeout) needs API 26 (minSdk is 24).
            Thread.sleep(50)
        }
        if (!exited) {
            destroy(process)
            outThread.join(500)
            errThread.join(500)
            throw GitCommandException(
                "git ${args.firstOrNull() ?: ""} timed out after ${timeoutSeconds}s",
                124,
                redactor.redactAll(stdout + stderr)
            )
        }
        outThread.join(1000)
        errThread.join(1000)
        return GitResult(process.exitValue(), stdout, stderr)
    }

    /**
     * Daemon pump thread (API-24-safe: no `redirect*(File)`), forwarding each
     * line of [stream] into [sink], redacted as it arrives.
     */
    private fun pump(stream: java.io.InputStream, sink: MutableList<String>): Thread = Thread {
        try {
            BufferedReader(InputStreamReader(stream)).use { reader ->
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    val text = line ?: continue
                    synchronized(sink) { sink += redactor.redact(text) }
                }
            }
        } catch (_: Exception) {
            // Stream closed — process exited or was killed.
        }
    }.also { it.isDaemon = true }

    private fun destroy(process: Process) {
        try {
            process.destroy()
        } catch (_: Exception) {
        }
        try {
            val method = Process::class.java.getMethod("destroyForcibly")
            method.invoke(process)
        } catch (_: Exception) {
        }
    }

    private fun writeAskpass(file: File) {
        val body = askpassBody()
        if (!file.isFile || file.readText() != body) {
            file.parentFile?.mkdirs()
            file.writeText(body)
        }
        file.setReadable(true, true)
        file.setWritable(true, true)
        file.setExecutable(true, true)
    }
}

/** Stored GitHub HTTPS credentials (app-private DataStore — see [GitCredentialsStore]). */
data class GitCredentials(val token: String, val username: String? = null)

/** Author identity for commits; falls back so `git commit` can never hard-fail. */
data class GitIdentity(val name: String, val email: String) {
    companion object {
        val FALLBACK = GitIdentity("CodeC", "codec@localhost")
    }
}

/**
 * Parses `git status --porcelain=v1 -b` output into [GitStatus].
 *
 * Format:
 * ```
 * ## main...origin/main [ahead 1, behind 2]
 * ## No commits yet on main
 * ## HEAD (no branch)
 * M  src/main.c
 * ?? notes.txt
 * R  old.txt -> new.txt
 * ```
 */
object GitStatusParser {

    fun parse(lines: List<String>): GitStatus {
        var branch: String? = null
        var detached = false
        var upstream: String? = null
        var ahead = 0
        var behind = 0
        val files = mutableListOf<GitFileChange>()

        for (raw in lines) {
            val line = raw.trimEnd('\r')
            when {
                line.startsWith("## ") -> {
                    val head = line.removePrefix("## ").trim()
                    if (head == "HEAD (no branch)") {
                        detached = true
                    } else if (head.startsWith("No commits yet on ")) {
                        branch = head.removePrefix("No commits yet on ").trim()
                    } else {
                        val tracking = head.split("...", limit = 2)
                        branch = tracking[0].trim().ifEmpty { null }
                        if (tracking.size == 2) {
                            var info = tracking[1]
                            val bracket = info.substringAfter('[', "").takeIf { it.isNotEmpty() }
                            if (bracket != null) {
                                info = info.substringBefore('[').trim()
                                for (part in bracket.removeSuffix("]").split(',')) {
                                    val item = part.trim()
                                    when {
                                        item.startsWith("ahead ") -> ahead = item.removePrefix("ahead ").trim().toIntOrNull() ?: 0
                                        item.startsWith("behind ") -> behind = item.removePrefix("behind ").trim().toIntOrNull() ?: 0
                                    }
                                }
                            }
                            upstream = info.ifEmpty { null }
                        }
                    }
                }
                line.length >= 4 && line[2] == ' ' && (line[0] != ' ' || line[1] != ' ') -> {
                    val x = line[0]
                    val y = line[1]
                    var rest = line.substring(3).trimStart()
                    var oldPath: String? = null
                    val renameSplit = splitRename(rest)
                    if (renameSplit != null) {
                        oldPath = unquote(renameSplit.first)
                        rest = unquote(renameSplit.second)
                    } else {
                        rest = unquote(rest)
                    }
                    files += GitFileChange(x = x, y = y, path = rest, oldPath = oldPath)
                }
            }
        }
        return GitStatus(
            branch = branch,
            detached = detached,
            upstream = upstream,
            ahead = ahead,
            behind = behind,
            files = files
        )
    }

    /** Splits `old -> new` outside of quotes; null when [rest] is not a rename. */
    private fun splitRename(rest: String): Pair<String, String>? {
        val marker = " -> "
        var index = rest.indexOf(marker)
        while (index != -1) {
            val left = rest.substring(0, index)
            val right = rest.substring(index + marker.length)
            if (left.count { it == '"' } % 2 == 0) return left to right
            index = rest.indexOf(marker, index + 1)
        }
        return null
    }

    /** Unquotes a C-style porcelain path (`"with \"quotes\" and \\slashes\\"`). */
    fun unquote(path: String): String {
        if (path.length < 2 || !path.startsWith("\"") || !path.endsWith("\"")) return path
        val body = path.substring(1, path.length - 1)
        return buildString(body.length) {
            var i = 0
            while (i < body.length) {
                val ch = body[i]
                if (ch == '\\' && i + 1 < body.length) {
                    when (val next = body[i + 1]) {
                        'n' -> append('\n')
                        't' -> append('\t')
                        '\\' -> append('\\')
                        '"' -> append('"')
                        else -> {
                            append('\\')
                            append(next)
                        }
                    }
                    i += 2
                } else {
                    append(ch)
                    i++
                }
            }
        }
    }
}

/** One changed path from [GitStatusParser]. */
data class GitFileChange(
    val x: Char,
    val y: Char,
    val path: String,
    val oldPath: String? = null
) {
    val state: GitFileState = when {
        x == '?' && y == '?' -> GitFileState.UNTRACKED
        x == 'U' || y == 'U' -> GitFileState.UNMERGED
        x == 'A' || y == 'A' -> GitFileState.ADDED
        x == 'D' || y == 'D' -> GitFileState.DELETED
        x == 'R' || y == 'R' -> GitFileState.RENAMED
        else -> GitFileState.MODIFIED
    }

    /** Single-letter badge shown in the pane (porcelain letter; `?` for untracked). */
    val badge: String = when (state) {
        GitFileState.UNTRACKED -> "?"
        GitFileState.UNMERGED -> "U"
        else -> (if (x != ' ') x else y).toString()
    }
}

enum class GitFileState { MODIFIED, ADDED, DELETED, UNTRACKED, RENAMED, UNMERGED }

data class GitStatus(
    val branch: String?,
    val detached: Boolean = false,
    val upstream: String? = null,
    val ahead: Int = 0,
    val behind: Int = 0,
    val files: List<GitFileChange> = emptyList()
)

/**
 * Scrubs secrets from git output before it can reach the UI or the log
 * buffer: the configured token literal, plus any `user:password@` URL
 * credentials git may echo.
 */
class GitRedactor(private val secret: String?) {

    fun redact(text: String): String {
        var result = text
        val token = secret
        if (!token.isNullOrBlank()) {
            result = result.replace(token, "***")
        }
        return redactUrls(result)
    }

    fun redactAll(lines: List<String>): List<String> = lines.map(::redact)

    companion object {
        /** URL-credential scrubbing works even without a known token. */
        fun redactUrls(text: String): String =
            text.replace(urlCredentialsRegex, "$1***@")

        private val urlCredentialsRegex = Regex(
            pattern = "(https?://)([^/\\s:@]+):([^/\\s@]+)@",
            options = setOf(RegexOption.IGNORE_CASE)
        )
    }
}
