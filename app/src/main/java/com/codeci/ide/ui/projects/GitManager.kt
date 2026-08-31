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

    /**
     * Phase 15/16 — stage one file: `git add -- <path>`. The `--` guard keeps
     * a path that looks like a flag (e.g. `-weird.c`) from ever being parsed
     * as one; the path is a single argv element (no shell, no injection).
     */
    fun stageFile(root: File, path: String) {
        exec(root, listOf("add", "--", path), localTimeoutSeconds, "git add failed")
    }

    /**
     * Phase 15/16 — unstage one file: `git reset -- <path>` (mixed reset of a
     * single path — works on every supported git version, unlike the newer
     * `git restore` subcommand). Unstages staged changes, additions and
     * deletions; an untracked file that was staged simply becomes untracked
     * again.
     */
    fun unstageFile(root: File, path: String) {
        exec(root, listOf("reset", "--", path), localTimeoutSeconds, "git reset failed")
    }

    /**
     * `git ls-files` — every tracked path (one per line), or null when the
     * command fails. Used by [BuildArtifactIgnore] to find build outputs an
     * earlier push already committed.
     */
    fun trackedFiles(root: File): List<String>? {
        val result = runGit(
            workingDir = root,
            args = listOf("ls-files"),
            timeoutSeconds = localTimeoutSeconds
        )
        if (result.exitCode != 0) return null
        return result.stdout.map { it.trim() }.filter { it.isNotEmpty() }
    }

    /**
     * `git rm -f --cached --quiet -- <paths>` — untrack [paths] from the
     * index while leaving them on disk. Once untracked (and ignored via
     * `.git/info/exclude`), a previously committed build output stops
     * traveling to the remote on the next push.
     */
    fun rmCached(root: File, paths: List<String>) {
        val safe = paths.filter { it.isNotEmpty() }
        if (safe.isEmpty()) return
        exec(
            root,
            listOf("rm", "-f", "--cached", "--quiet", "--") + safe,
            localTimeoutSeconds,
            "git rm --cached failed"
        )
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
     *
     * Phase 15 — the Projects Hub clone dialog adds two optional arguments
     * while the historical caller keeps identical behavior (D5):
     *  - [shallow] `--depth 1` (Spck-style: fetch only the latest tree —
     *    mobile bandwidth first),
     *  - [branch] `--branch <name>` after `ProjectsHub.isValidBranchName`.
     */
    fun clone(url: String, dest: File, shallow: Boolean = false, branch: String? = null) {
        require(isCloneableUrl(url)) { "Only http(s) repository URLs can be cloned" }
        require(!dest.exists()) { "Destination already exists: ${dest.name}" }
        if (branch != null) {
            require(ProjectsHub.isValidBranchName(branch)) { "Invalid branch name" }
        }
        dest.parentFile?.mkdirs()
        val args = mutableListOf("clone")
        if (shallow) {
            args += "--depth"
            args += "1"
        }
        branch?.trim()?.takeIf { it.isNotEmpty() }?.let {
            args += "--branch"
            args += it
        }
        args += url.trim()
        args += dest.absolutePath
        exec(
            workingDir = dest.parentFile ?: error("clone destination has no parent"),
            args = args,
            timeoutSeconds = networkTimeoutSeconds,
            failureMessage = "git clone failed"
        )
    }

    /**
     * Phase 15 — branch list for the clone dialog's Advanced dropdown:
     * `git ls-remote --heads <url>`, parsed by
     * [ProjectsHub.branchNamesFromLsRemote]. Output already passes through
     * [GitRedactor]; a failure (offline, private repo without a token)
     * throws [GitCommandException] and the dialog falls back to free text.
     */
    fun listRemoteBranches(url: String): List<String> {
        require(isCloneableUrl(url)) { "Only http(s) repository URLs can be queried" }
        val result = runGit(
            workingDir = null,
            args = listOf("ls-remote", "--heads", url.trim()),
            timeoutSeconds = networkTimeoutSeconds
        )
        if (result.exitCode != 0) {
            val detail = redactor.redact((result.stderr + result.stdout).joinToString("\n").trim())
            throw GitCommandException(
                listOf("git ls-remote failed", detail).filter { it.isNotEmpty() }.joinToString(": "),
                result.exitCode,
                redactor.redactAll(result.stdout + result.stderr)
            )
        }
        return ProjectsHub.branchNamesFromLsRemote(result.stdout)
    }

    // ---------------------------------------------------------------------
    // Phase 17 — branches, stash and merge conflicts
    // ---------------------------------------------------------------------

    /**
     * `git branch --all --no-color` → local + remote branches with the current
     * one flagged (parsed by [GitBranchParser]). `--no-color` keeps a user
     * `color.branch=always` config from polluting the output with escapes.
     */
    fun listBranches(root: File): GitBranchList {
        val result = runGit(
            workingDir = root,
            args = listOf("branch", "--all", "--no-color"),
            timeoutSeconds = localTimeoutSeconds
        )
        if (result.exitCode != 0) {
            throw GitCommandException(
                redactOrFallback(result, "git branch failed"),
                result.exitCode,
                redactor.redactAll(result.stdout + result.stderr)
            )
        }
        return GitBranchParser.parse(result.stdout)
    }

    /**
     * `git rev-parse --abbrev-ref HEAD` — the checked-out branch, or null when
     * HEAD is detached (git prints the literal `HEAD`) or the command fails.
     */
    fun currentBranch(root: File): String? {
        val result = runGit(
            workingDir = root,
            args = listOf("rev-parse", "--abbrev-ref", "HEAD"),
            timeoutSeconds = localTimeoutSeconds
        )
        if (result.exitCode != 0) return null
        val value = result.stdout.firstOrNull { it.isNotBlank() }?.trim().orEmpty()
        return value.takeIf { it.isNotEmpty() && it != "HEAD" }
    }

    /** `git checkout <branch>` for a branch that already exists locally. */
    fun checkout(root: File, branch: String) {
        val safe = branch.trim()
        require(GitBranchOps.isSafeExistingBranch(safe)) { "Invalid branch name" }
        exec(root, listOf("checkout", safe), localTimeoutSeconds, "git checkout failed")
    }

    /**
     * `git checkout -b <name>` — creates the branch at the current HEAD and
     * switches to it (Spck cannot create branches; CodeC can, offered as a
     * bonus row in the Switch Branch dialog).
     */
    fun checkoutNew(root: File, name: String) {
        val safe = name.trim()
        require(ProjectsHub.isValidBranchName(safe)) { "Invalid branch name" }
        exec(root, listOf("checkout", "-b", safe), localTimeoutSeconds, "git checkout failed")
    }

    /**
     * `git checkout -b <local> --track <remote>` for a remote-only branch.
     *
     * Checking the remote-tracking ref out directly would detach HEAD, so a
     * local tracking branch is created from it instead; when a local branch of
     * the same name already exists, the caller (see [switchBranch]) checks
     * that one out instead of failing here.
     */
    fun checkoutRemote(root: File, remoteRef: String) {
        val safe = remoteRef.trim()
        require(GitBranchOps.isSafeExistingBranch(safe)) { "Invalid branch name" }
        val local = safe.substringAfter('/', "")
        require(local.isNotEmpty() && ProjectsHub.isValidBranchName(local)) { "Invalid branch name" }
        exec(
            root,
            listOf("checkout", "-b", local, "--track", safe),
            localTimeoutSeconds,
            "git checkout failed"
        )
    }

    /**
     * `git stash push [-u] -m <message>` — parks the working tree so a branch
     * switch cannot lose it. `-u` (untracked included) is the default because
     * Spck's promise covers new files too. Returns false when git reports
     * there was nothing to save.
     */
    fun stashPush(root: File, message: String, includeUntracked: Boolean = true): Boolean {
        val args = mutableListOf("stash", "push")
        if (includeUntracked) args += "-u"
        args += "-m"
        args += message
        val result = runGit(root, args, localTimeoutSeconds)
        if (result.exitCode != 0) {
            throw GitCommandException(
                redactOrFallback(result, "git stash failed"),
                result.exitCode,
                redactor.redactAll(result.stdout + result.stderr)
            )
        }
        val nothing = (result.stdout + result.stderr).any { it.contains("No local changes") }
        return !nothing
    }

    /**
     * `git stash pop [stash@{N}]` — restores (and, on success, drops) the top
     * stash entry by default. A conflicting pop leaves the entry on the stack,
     * so a failed pop never destroys the user's work; the caller reports it.
     */
    fun stashPop(root: File, ref: String = "stash@{0}") {
        val safe = ref.trim()
        require(safe.isNotEmpty() && !safe.startsWith("-")) { "Invalid stash reference" }
        exec(root, listOf("stash", "pop", safe), localTimeoutSeconds, "git stash pop failed")
    }

    /** `git stash list` — newest first; empty when the command fails. */
    fun stashList(root: File): List<GitStashEntry> {
        val result = runGit(root, listOf("stash", "list"), localTimeoutSeconds)
        if (result.exitCode != 0) return emptyList()
        return GitStashParser.parse(result.stdout)
    }

    /**
     * Phase 17 — the whole Switch Branch flow, in the order the user expects:
     *
     * 1. if the tree is dirty and [stashChanges] is on, stash it (marked with
     *    the branch it came from, [GitBranchOps.StashMarker]);
     * 2. check out the target (local → `checkout`, remote → `checkout -b
     *    --track`, new → `checkout -b`); if that fails **after** we stashed,
     *    pop the stash straight back so nothing is left in limbo;
     * 3. if a CodeC stash entry exists for the branch we just landed on, pop
     *    it (auto-restore). A conflicting pop keeps the entry on the stack and
     *    is reported through [SwitchBranchResult.stashPending].
     *
     * Every step is argv-only and runs through the Phase 13 private env, so no
     * token can leak and no shell is involved.
     */
    fun switchBranch(
        root: File,
        target: BranchTarget,
        stashChanges: Boolean = true
    ): SwitchBranchResult {
        val before = runCatching { status(root) }.getOrNull()
        val fromBranch = before?.branch
        val dirty = before?.files?.isNotEmpty() == true

        var stashed = false
        if (stashChanges && dirty) {
            stashed = stashPush(
                root = root,
                message = GitBranchOps.StashMarker.message(fromBranch ?: "HEAD"),
                includeUntracked = true
            )
        }

        val landed = try {
            checkoutTarget(root, target)
        } catch (e: Exception) {
            if (stashed) runCatching { stashPop(root) }
            throw e
        }

        var restored = false
        var pending = false
        if (stashChanges) {
            val mine = runCatching { stashList(root) }.getOrDefault(emptyList())
                .firstOrNull { it.codecBranch == landed }
            if (mine != null) {
                runCatching { stashPop(root, mine.ref) }
                    .onSuccess { restored = true }
                    .onFailure { pending = true }
            }
        }
        return SwitchBranchResult(
            branch = landed,
            stashed = stashed,
            restored = restored,
            stashPending = pending
        )
    }

    /** Resolves a [BranchTarget] to the argv checkout that matches it. */
    private fun checkoutTarget(root: File, target: BranchTarget): String {
        val name = target.name.trim()
        return when (target.kind) {
            BranchTargetKind.NEW -> {
                checkoutNew(root, name)
                name
            }
            BranchTargetKind.REMOTE -> {
                val local = name.substringAfter('/', "")
                val alreadyLocal = runCatching { listBranches(root) }.getOrNull()
                    ?.local?.any { it.name == local } == true
                if (alreadyLocal) {
                    checkout(root, local)
                    local
                } else {
                    checkoutRemote(root, name)
                    local.ifEmpty { name }
                }
            }
            BranchTargetKind.LOCAL -> {
                checkout(root, name)
                name
            }
        }
    }

    private fun redactOrFallback(result: GitResult, fallback: String): String =
        redactor.redact((result.stderr + result.stdout).joinToString("\n").trim())
            .ifEmpty { fallback }

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
    /**
     * Phase 17 — true for an unmerged (merge-conflicted) path. Git marks
     * conflicts with the seven XY pairs `DD AU UD UA DU AA UU`
     * ([GitBranchOps.isConflict] records the sources); `AA`/`DD` carry no `U`
     * at all, and `AD` (staged addition removed from the work tree) is NOT a
     * conflict, so the pair set is tested exactly rather than by column.
     */
    val isConflict: Boolean = GitBranchOps.isConflict(x, y)

    val state: GitFileState = when {
        isConflict -> GitFileState.UNMERGED
        x == '?' && y == '?' -> GitFileState.UNTRACKED
        x == 'U' || y == 'U' -> GitFileState.UNMERGED
        x == 'A' || y == 'A' -> GitFileState.ADDED
        x == 'D' || y == 'D' -> GitFileState.DELETED
        x == 'R' || y == 'R' -> GitFileState.RENAMED
        else -> GitFileState.MODIFIED
    }

    /** Single-letter badge shown in the pane (porcelain letter; `?` for untracked). */
    val badge: String = when {
        isConflict -> "U"
        state == GitFileState.UNTRACKED -> "?"
        state == GitFileState.UNMERGED -> "U"
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
