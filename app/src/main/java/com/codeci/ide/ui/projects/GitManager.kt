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

    /** True when a GitHub token is configured and available to git children. */
    val hasCredentials: Boolean get() = auth != null

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
    /**
     * Phase 39.2 — the choke point. Every path that stages (COMMIT & PUSH,
     * future publish) goes through here, so ignore/untrack cannot be
     * bypassed by a caller's good intentions:
     *   1. [RepoHygiene.ensure] appends missing patterns to `.git/info/exclude`
     *   2. tracked violations are `git rm --cached`'d (file stays on disk)
     *   3. `git add -A`
     * A `git rm --cached` failure aborts before add — a half-staged commit
     * is worse than no commit. Returns the hygiene result so the sheet can
     * show "Removed N build outputs…".
     */
    fun stageAll(root: File): RepoHygiene.HygieneResult {
        val hygiene = RepoHygiene.prepareForStage(root, this, strict = true)
        exec(root, listOf("add", "-A"), localTimeoutSeconds, "git add failed")
        return hygiene
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

    /**
     * `git push` — needs an upstream (clone sets it) and, for private remotes,
     * a token.
     *
     * Phase 17 device fix (owner, 2026-08-31): a branch created in the app
     * (New branch…, `git checkout -b`, or the first push of a local repo) has
     * **no upstream**, so a plain `git push` dies with
     * `fatal: The current branch <name> has no upstream branch`. Pass
     * [setUpstream] to publish it instead:
     * `git push --set-upstream <remote> <branch>`, which pushes the branch to
     * the same-named branch on the remote and remembers the tracking link, so
     * later pushes are plain again. Use [pushHandlingUpstream] to decide
     * automatically.
     *
     * Phase 17 follow-up (owner, 2026-09-01): the ref is now the explicit
     * branch NAME ([branchName]) rather than `HEAD`, so a freshly created
     * branch always publishes under its own name (git's own suggested
     * `git push --set-upstream origin <branch>`). `HEAD` remains the fallback
     * when no branch name is known.
     */
    fun push(root: File, setUpstream: Boolean = false, branchName: String? = null) {
        if (!setUpstream) {
            exec(root, listOf("push"), networkTimeoutSeconds, "git push failed")
            return
        }
        // `origin` is what CodeC's clone creates; `git remote` covers repos
        // that were renamed or initialised by hand.
        val remote = firstRemote(root) ?: "origin"
        val ref = branchName?.trim()
            ?.takeIf { GitBranchOps.isSafeExistingBranch(it) }
            ?: "HEAD"
        exec(
            root,
            listOf("push", "--set-upstream", remote, ref),
            networkTimeoutSeconds,
            "git push failed"
        )
    }

    /** First configured remote (`git remote`), or null when the command fails. */
    fun firstRemote(root: File): String? {
        val result = runGit(root, listOf("remote"), localTimeoutSeconds)
        if (result.exitCode != 0) return null
        return result.stdout.map { it.trim() }.firstOrNull { it.isNotEmpty() }
    }

    /**
     * Pushes, setting the upstream first when the current branch has none.
     *
     * `git status --porcelain=v1 -b` prints `## main...origin/main` only once
     * a branch tracks something, so a missing upstream is detectable without
     * another process — and the extra `status` call is the one CodeC already
     * makes for the Source Control sheet.
     *
     * Always passes the branch **name** (not a bare `git push`) so a push
     * from `test-1` cannot be mistaken for (or silently land on) `main`.
     * When upstream is already set, this is still `git push <remote> <branch>`
     * — same effect as plain `git push`, with an explicit ref.
     */
    fun pushHandlingUpstream(root: File) {
        val status = runCatching { status(root) }.getOrNull()
        val branch = status?.branch
        val needsUpstream = status?.upstream == null
        if (needsUpstream) {
            push(root, setUpstream = true, branchName = branch)
        } else {
            // Explicit remote + branch so the argv (and the UI label) always
            // name the branch the user is on — never a silent default.
            val remote = firstRemote(root) ?: "origin"
            val ref = branch?.trim()
                ?.takeIf { GitBranchOps.isSafeExistingBranch(it) }
                ?: "HEAD"
            exec(
                root,
                listOf("push", remote, ref),
                networkTimeoutSeconds,
                "git push failed"
            )
        }
    }

    /**
     * True when [branch] already exists on the first remote as a head
     * (`refs/heads/<branch>` via `git ls-remote --heads`). Used to stop the
     * "not on the remote yet" banner after a successful publish when the
     * local upstream config is missing or stale — the remote is the truth.
     *
     * Best-effort: offline / no token / empty branch → false (banner may
     * still show; the next successful push clears it).
     */
    fun remoteHasBranch(root: File, branch: String): Boolean {
        val name = branch.trim()
        if (name.isEmpty() || !GitBranchOps.isSafeExistingBranch(name)) return false
        val remote = firstRemote(root) ?: "origin"
        // Cap the probe well below the full push timeout — a hung ls-remote
        // must not freeze the Source Control sheet for five minutes.
        val probeTimeout = networkTimeoutSeconds.coerceAtMost(30L)
        val result = runCatching {
            runGit(
                workingDir = root,
                args = listOf("ls-remote", "--heads", remote, name),
                timeoutSeconds = probeTimeout
            )
        }.getOrNull() ?: return false
        if (result.exitCode != 0) return false
        // Exact refs/heads/<name> only — never a suffix of another branch
        // (e.g. refs/heads/foo/test-1 must not match test-1).
        val needle = "refs/heads/$name"
        return result.stdout.any { line ->
            val ref = line.trim().substringAfter('\t', "")
                .ifEmpty { line.trim().substringAfter(' ', "") }
                .trim()
            ref == needle
        }
    }

    /**
     * `git branch --set-upstream-to=<remote>/<branch> <branch>` — wire local
     * tracking after we discover the remote already has the branch (e.g. a
     * previous publish succeeded but tracking never stuck). Best-effort; the
     * caller keeps going even if this fails.
     */
    fun setUpstream(root: File, branch: String) {
        val name = branch.trim()
        require(GitBranchOps.isSafeExistingBranch(name)) { "Invalid branch name" }
        val remote = firstRemote(root) ?: "origin"
        exec(
            root,
            listOf("branch", "--set-upstream-to=$remote/$name", name),
            localTimeoutSeconds,
            "git branch --set-upstream-to failed"
        )
    }

    /**
     * Enriches a local [GitStatus] with a remote probe (and, when the remote
     * already has the branch, repairs missing upstream tracking so the next
     * `git status` reports ahead/behind honestly).
     *
     * Only hits the network when the local heuristic says "unpublished".
     */
    fun resolvePublishState(root: File, local: GitStatus): GitStatus {
        val branch = local.branch ?: return local
        if (local.detached || local.noCommits || local.upstream != null) return local
        val onRemote = runCatching { remoteHasBranch(root, branch) }.getOrNull()
            ?: return local.copy(remoteBranchExists = null)
        if (onRemote) {
            // Repair tracking so the banner stays gone and ahead counts work.
            runCatching { setUpstream(root, branch) }
            val refreshed = runCatching { status(root) }.getOrNull()
            if (refreshed != null) {
                return refreshed.copy(remoteBranchExists = true)
            }
        }
        return local.copy(remoteBranchExists = onRemote)
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

    /**
     * `git fetch --prune <remote>` — refresh remote-tracking refs so the
     * Switch Branch sheet can offer branches that already exist on GitHub
     * (not only the one the clone landed on, and not only branches created
     * inside the app). Best-effort: callers treat failure as "list what we
     * already know".
     *
     * Uses `--prune` so deleted remote branches disappear from the list.
     * For shallow clones (`--depth 1`) a plain fetch still only updates the
     * tracked tip — use [fetchBranch] / [listRemoteHeadNames] for other heads.
     */
    fun fetch(root: File) {
        val remote = firstRemote(root) ?: "origin"
        exec(
            root,
            listOf("fetch", "--prune", remote),
            networkTimeoutSeconds,
            "git fetch failed"
        )
    }

    /**
     * `git fetch <remote> <branch>` — pull one remote head into
     * `refs/remotes/<remote>/<branch>` so [checkoutRemote] can track it.
     * Works for shallow clones that never received that branch (the usual
     * reason test-1 / test-2 were missing from the Switch Branch sheet while
     * GitHub still had them).
     *
     * Shallow clones get `--depth 1` on this one head so the tip is always
     * downloadable. After fetch we verify the remote-tracking ref exists;
     * some git builds only leave FETCH_HEAD — recover with update-ref.
     */
    fun fetchBranch(root: File, branch: String) {
        val name = branch.trim()
        require(GitBranchOps.isSafeExistingBranch(name)) { "Invalid branch name" }
        val remote = firstRemote(root) ?: "origin"
        val dest = "refs/remotes/$remote/$name"
        val refspec = "+refs/heads/$name:$dest"
        val args = mutableListOf("fetch")
        if (File(root, ".git/shallow").isFile) {
            args += "--depth"
            args += "1"
        }
        args += remote
        args += refspec
        exec(root, args, networkTimeoutSeconds, "git fetch failed")
        if (!remoteTrackingRefExists(root, remote, name)) {
            // Fallback: plain fetch into FETCH_HEAD, then point the tracking ref.
            exec(
                root,
                listOf("fetch", remote, name),
                networkTimeoutSeconds,
                "git fetch failed"
            )
            exec(
                root,
                listOf("update-ref", dest, "FETCH_HEAD"),
                localTimeoutSeconds,
                "git update-ref failed"
            )
        }
        if (!remoteTrackingRefExists(root, remote, name)) {
            throw GitCommandException(
                "git fetch failed: remote branch '$name' did not land on this device",
                1,
                emptyList()
            )
        }
    }

    /** True when `refs/remotes/<remote>/<branch>` resolves (loose or packed). */
    fun remoteTrackingRefExists(root: File, remote: String, branch: String): Boolean {
        val ref = "refs/remotes/${remote.trim()}/${branch.trim()}"
        val result = runCatching {
            runGit(
                workingDir = root,
                args = listOf("rev-parse", "--verify", ref),
                timeoutSeconds = localTimeoutSeconds
            )
        }.getOrNull() ?: return false
        return result.exitCode == 0 && result.stdout.any { it.trim().isNotEmpty() }
    }

    /**
     * Every branch name on the first remote (`git ls-remote --heads`), even
     * when a shallow clone never fetched them. Falls back to the remote URL
     * from `.git/config` when `git ls-remote <remote>` needs the full URL.
     * Empty list on failure (caller keeps the local list).
     */
    fun listRemoteHeadNames(root: File): List<String> {
        val remote = firstRemote(root) ?: "origin"
        // Prefer the configured remote name (auth/askpass already wired).
        val viaRemote = runCatching {
            runGit(
                workingDir = root,
                args = listOf("ls-remote", "--heads", remote),
                timeoutSeconds = networkTimeoutSeconds.coerceAtMost(60L)
            )
        }.getOrNull()
        if (viaRemote != null && viaRemote.exitCode == 0) {
            val names = ProjectsHub.branchNamesFromLsRemote(viaRemote.stdout)
            if (names.isNotEmpty()) return names.distinct()
        }
        // Fallback: read the remote URL from config and query it directly
        // (same path the clone dialog uses).
        val url = runCatching {
            val cfg = File(root, ".git/config")
            if (cfg.isFile) ProjectsHub.remoteUrlFromConfig(cfg.readText()) else null
        }.getOrNull()
        if (!url.isNullOrBlank() && isCloneableUrl(url)) {
            return runCatching { listRemoteBranches(url) }.getOrDefault(emptyList())
        }
        return emptyList()
    }

    /**
     * Local branches plus every head that exists on the remote (from
     * [listRemoteHeadNames]), so the Switch Branch sheet can offer "check out
     * test-1" even when the clone never fetched it. Remote rows already
     * covered by a local branch are dropped ([withoutLocallyTrackedRemotes]).
     *
     * [remoteDiscoveryError] is set when the network probe failed so the UI
     * can show a soft hint without hiding local branches.
     */
    fun listBranchesWithRemoteHeads(root: File): Pair<GitBranchList, String?> {
        val local = listBranches(root)
        // Always try a plain fetch first (updates tips we already track).
        val fetchErr = runCatching { fetch(root) }.exceptionOrNull()
        val afterFetch = runCatching { listBranches(root) }.getOrDefault(local)

        val remoteNames = runCatching { listRemoteHeadNames(root) }.getOrDefault(emptyList())
        if (remoteNames.isEmpty()) {
            val note = when {
                fetchErr != null ->
                    GitErrors.classify(
                        raw = fetchErr.message,
                        exitCode = (fetchErr as? GitCommandException)?.exitCode,
                        hasToken = hasCredentials
                    ).message
                else -> null
            }
            return afterFetch.withoutLocallyTrackedRemotes() to note
        }
        val remote = firstRemote(root) ?: "origin"
        val localNames = afterFetch.local.map { it.name }.toSet()
        val knownRemote = afterFetch.remote.map { it.localName }.toSet()
        val extras = remoteNames
            .filter { it !in localNames && it !in knownRemote }
            .filter { GitBranchOps.isSafeExistingBranch(it) }
            .sorted()
            .map { name ->
                GitBranch(
                    name = "$remote/$name",
                    isRemote = true,
                    isCurrent = false
                )
            }
        val merged = afterFetch.copy(branches = afterFetch.branches + extras)
        return merged.withoutLocallyTrackedRemotes() to null
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
     * Check out a remote-only branch as a local tracking branch.
     *
     * Device error was: `cannot set up tracking information; starting point
     * 'origin/test-1' is not a branch` — ls-remote listed the name, but
     * `checkout -b --track origin/test-1` needs a real remote-tracking ref.
     * On shallow clones the short name is not a branch.
     *
     * Path:
     *  1. [fetchBranch] so `refs/remotes/<remote>/<local>` really exists
     *  2. `git checkout -B <local> refs/remotes/<remote>/<local>` from the
     *     **full** ref (never the short `origin/name`)
     *  3. `git branch --set-upstream-to=<remote>/<local> <local>`
     *
     * When a local branch of the same name already exists, [switchBranch]
     * checks that one out instead of calling here.
     */
    fun checkoutRemote(root: File, remoteRef: String) {
        val safe = remoteRef.trim()
        require(GitBranchOps.isSafeExistingBranch(safe)) { "Invalid branch name" }
        val local = safe.substringAfter('/', "")
        require(local.isNotEmpty() && ProjectsHub.isValidBranchName(local)) { "Invalid branch name" }
        val remote = safe.substringBefore('/', firstRemote(root) ?: "origin")
            .ifEmpty { firstRemote(root) ?: "origin" }
        fetchBranch(root, local)
        val fullRef = "refs/remotes/$remote/$local"
        exec(
            root,
            listOf("checkout", "-B", local, fullRef),
            localTimeoutSeconds,
            "git checkout failed"
        )
        runCatching {
            exec(
                root,
                listOf("branch", "--set-upstream-to=$remote/$local", local),
                localTimeoutSeconds,
                "git branch --set-upstream-to failed"
            )
        }
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
     * 4. a NEW branch is published to the remote right after creation
     *    (best-effort — [SwitchBranchResult.published]/[publishError] report
     *    the honest outcome).
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

        // No-op when the user re-confirms the branch they are already on
        // (avoids a useless checkout that fails on a dirty tree).
        if (target.kind == BranchTargetKind.LOCAL &&
            fromBranch != null &&
            target.name.trim() == fromBranch
        ) {
            return SwitchBranchResult(branch = fromBranch)
        }
        if (target.kind == BranchTargetKind.REMOTE) {
            val local = target.name.trim().substringAfter('/', "")
            if (fromBranch != null && local == fromBranch) {
                return SwitchBranchResult(branch = fromBranch)
            }
        }

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

        // Phase 17 follow-up (owner, 2026-09-01: "create a new branch don't
        // add in github") — a NEW branch is published to the remote as soon as
        // it is created. `checkout -b` always leaves HEAD at a real commit, so
        // the push simply creates the same-named remote branch. Best-effort:
        // offline / no token keeps the branch local, the dialog says so
        // honestly, and the Source Control sheet offers the PUSH retry.
        var published = false
        var publishError: String? = null
        if (target.kind == BranchTargetKind.NEW) {
            runCatching { push(root, setUpstream = true, branchName = landed) }
                .onSuccess { published = true }
                .onFailure {
                    // Phase 17 follow-up — the same friendly, actionable
                    // wording as every other git failure (offline / no token /
                    // rejected), not raw git output.
                    publishError = GitErrors.classify(
                        raw = it.message,
                        exitCode = (it as? GitCommandException)?.exitCode,
                        hasToken = hasCredentials
                    ).display()
                }
        }

        var restored = false
        var pending = false
        if (stashed && target.kind == BranchTargetKind.NEW) {
            // Creating a branch carries the work you were doing onto it —
            // the stash was marked with the *parent* name, so the
            // "codecBranch == landed" lookup below would miss it and leave
            // edits parked forever under the old branch.
            runCatching { stashPop(root) }
                .onSuccess { restored = true }
                .onFailure { pending = true }
        } else if (stashChanges) {
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
            stashPending = pending,
            published = published,
            publishError = publishError
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
        var noCommits = false
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
                        noCommits = true
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
            files = files,
            noCommits = noCommits
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
    val files: List<GitFileChange> = emptyList(),
    /** True for `## No commits yet on <branch>` (an empty repository). */
    val noCommits: Boolean = false,
    /**
     * Phase 39 device follow-up — when non-null, overrides the local-only
     * "no upstream config" guess with a real `git ls-remote` answer:
     * `true` = branch exists on the remote (so the "not on remote yet"
     * banner must hide even if upstream tracking is missing);
     * `false` = confirmed missing on the remote.
     * `null` = not probed (fall back to the local heuristic).
     */
    val remoteBranchExists: Boolean? = null,
) {
    /**
     * Phase 17 follow-up (owner, 2026-09-01: "locally commit cannot be
     * pushed") — the branch exists, has commits, but tracks no remote branch:
     * its commits live only on this device and the first push must publish
     * the branch itself. A fresh repo with zero commits is NOT unpublished
     * (there is nothing to push), and a detached HEAD is not either.
     *
     * Phase 39 device follow-up: if we already know the remote has this
     * branch ([remoteBranchExists] = true), it is NOT unpublished — the
     * banner was lying after a successful create+push when only the local
     * upstream config was missing. If the remote probe says false, keep
     * the banner. If unprobed, keep the original local heuristic.
     */
    val unpublished: Boolean
        get() {
            if (branch == null || detached || noCommits) return false
            if (remoteBranchExists == true) return false
            if (remoteBranchExists == false) return upstream == null
            return upstream == null
        }
}

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
