package com.codeci.ide.ui.projects

import java.io.File

/**
 * Phase 15 — the Projects Hub presentation engine (Spck-style project list).
 *
 * Everything in this file is deliberately Android-free and side-effect-light
 * so CI's `:app:testDebugUnitTest` can verify the hub's real logic: type
 * mapping, filter chips, search, subtitle formatting, and the cheap git
 * metadata readers (`.git/HEAD`, `ls-remote --heads`, `.git/config`). The
 * Compose screen and the ViewModel only call into these; heavy work (tree
 * walks, git status) runs on `Dispatchers.IO` in the ViewModel — never here.
 *
 * Clean-room note: this mirrors Spck Editor's visible project-list UX
 * (cards, filters, one unified add sheet) implemented from scratch on
 * CodeC's own engines (Phases 8/13/14).
 */

/** One selected filter chip in the Projects Hub. */
enum class ProjectHubFilter { ALL, GIT, C, PYTHON, WEB }

/**
 * The language/family a project belongs to — derived from the declared
 * [ProjectConfig.type] or, for `auto` projects, the Phase 14 run detector.
 * Drives both the card icon and the filter-chip membership.
 */
enum class ProjectHubKind {
    C,            // plain C program
    C_SERVER,     // c-microservice preset (also "web" in the family sense)
    PY,           // python script
    PY_SERVER,    // flask / fastapi presets
    WEB_STATIC,   // static html site
    GENERIC       // unknown / mixed / empty
}

/** Icon content for a card's leading square; the UI maps these to colors. */
enum class HubIconToken { C_ORANGE, PY_BLUE, WEB_GREEN, SERVER_PURPLE, GENERIC_GRAY }

/**
 * One row of the hub: everything the card needs, precomputed off the main
 * thread. [lastModified] is the project's newest file (or the folder when the
 * folder is newer); [branch]/[hasChanges] are null when unknown (not a repo,
 * git unavailable, or the read failed — the card degrades gracefully).
 */
data class ProjectHubEntry(
    val name: String,
    val kind: ProjectHubKind,
    val isGit: Boolean,
    val branch: String?,
    val fileCount: Int,
    val lastModified: Long,
    val hasChanges: Boolean?
) {
    /** Icon token for the card's leading square. */
    val icon: HubIconToken
        get() = when (kind) {
            ProjectHubKind.C -> HubIconToken.C_ORANGE
            ProjectHubKind.C_SERVER -> HubIconToken.C_ORANGE
            ProjectHubKind.PY -> HubIconToken.PY_BLUE
            ProjectHubKind.PY_SERVER -> HubIconToken.SERVER_PURPLE
            ProjectHubKind.WEB_STATIC -> HubIconToken.WEB_GREEN
            ProjectHubKind.GENERIC -> HubIconToken.GENERIC_GRAY
        }

    /** Label drawn inside the leading square (the green web chip uses an icon instead). */
    val iconLabel: String
        get() = when (icon) {
            HubIconToken.C_ORANGE -> "C"
            HubIconToken.PY_BLUE -> "Py"
            HubIconToken.SERVER_PURPLE -> "Web"
            else -> ""
        }

    /** Which filter chips this project matches. */
    val filters: Set<ProjectHubFilter>
        get() = buildSet {
            add(ProjectHubFilter.ALL)
            if (isGit) add(ProjectHubFilter.GIT)
            when (kind) {
                ProjectHubKind.C, ProjectHubKind.C_SERVER -> add(ProjectHubFilter.C)
                ProjectHubKind.PY, ProjectHubKind.PY_SERVER -> add(ProjectHubFilter.PYTHON)
                // Server apps are "web" in the family sense, exactly like Spck
                // groups framework projects under its web filter.
                ProjectHubKind.WEB_STATIC, ProjectHubKind.PY_SERVER, ProjectHubKind.C_SERVER ->
                    add(ProjectHubFilter.WEB)
                ProjectHubKind.GENERIC -> Unit
            }
        }
}

/** Pure hub logic: mapping, filtering, search, and subtitle text. */
object ProjectsHub {

    /** Maps a declared config type (Phase 12/14 vocabulary) to a hub kind. */
    fun kindOfConfigType(type: String): ProjectHubKind = when (type.trim().lowercase()) {
        "c" -> ProjectHubKind.C
        "python" -> ProjectHubKind.PY
        "web", "static-web" -> ProjectHubKind.WEB_STATIC
        "python-flask", "python-fastapi" -> ProjectHubKind.PY_SERVER
        "c-microservice" -> ProjectHubKind.C_SERVER
        else -> ProjectHubKind.GENERIC
    }

    /**
     * Kind for an `auto` project from the Phase 14 detector plan (D1: hub and
     * RUN ▶ must never disagree about a project's family).
     */
    fun kindOfAutoPlan(plan: AutoRunPlan): ProjectHubKind = when (plan) {
        is AutoRunPlan.Server -> when (plan.type.trim().lowercase()) {
            "c-microservice" -> ProjectHubKind.C_SERVER
            "python-flask", "python-fastapi" -> ProjectHubKind.PY_SERVER
            else -> ProjectHubKind.PY_SERVER
        }
        is AutoRunPlan.Web -> ProjectHubKind.WEB_STATIC
        is AutoRunPlan.Project -> kindOfConfigType(plan.type)
        is AutoRunPlan.None -> ProjectHubKind.GENERIC
    }

    /** The kind the hub shows: declared config, or the detector for `auto`. */
    fun kindFor(config: ProjectConfig, autoPlan: AutoRunPlan?): ProjectHubKind {
        val declared = kindOfConfigType(config.type)
        return if (config.type.trim().lowercase() == "auto" && autoPlan != null) {
            kindOfAutoPlan(autoPlan)
        } else {
            declared
        }
    }

    /** Chip filter + case-insensitive name substring search over [entries]. */
    fun filterEntries(
        entries: List<ProjectHubEntry>,
        filter: ProjectHubFilter,
        query: String?
    ): List<ProjectHubEntry> {
        var result = entries
        if (filter != ProjectHubFilter.ALL) {
            result = result.filter { it.filters.contains(filter) }
        }
        val needle = query?.trim()?.lowercase()
        if (!needle.isNullOrEmpty()) {
            result = result.filter { it.name.lowercase().contains(needle) }
        }
        // D2 — most-recently-touched first (new/renamed/imported projects land
        // at the top, as the device recipe expects); names break ties so the
        // order is deterministic for a CI diff.
        return result.sortedWith(
            compareByDescending<ProjectHubEntry> { it.lastModified }
                .thenBy { it.name.lowercase() }
        )
    }

    /**
     * Subtitle segments in card order: `[branch] · N file(s) · <age>`.
     * The branch segment is present only for git projects with a known
     * branch; a detached repo shows the "HEAD" label the parser supplies.
     */
    fun subtitleSegments(entry: ProjectHubEntry, nowMillis: Long): List<String> =
        buildList {
            if (entry.isGit) entry.branch?.takeIf { it.isNotBlank() }?.let { add(it) }
            add(
                if (entry.fileCount == 1) "1 file" else "${entry.fileCount} files"
            )
            if (entry.lastModified > 0L) add(relativeAge(entry.lastModified, nowMillis))
        }

    /** "3 files" / "1 file". */
    fun formatFileCount(count: Int): String =
        if (count == 1) "1 file" else "$count files"

    /**
     * Human relative age, clock injected for testability. Buckets:
     * just now · N min ago · N hours ago · yesterday · N days ago · N weeks
     * ago · N months ago (30-day months) · N years ago (365-day years).
     * A future timestamp (clock skew) clamps to "just now".
     */
    fun relativeAge(timestampMillis: Long, nowMillis: Long): String {
        if (timestampMillis <= 0L) return ""
        val delta = nowMillis - timestampMillis
        if (delta < MINUTE_MILLIS) return "just now"
        val minutes = delta / MINUTE_MILLIS
        if (minutes < 60L) return "$minutes min ago"
        val hours = minutes / 60L
        if (hours < 24L) return if (hours == 1L) "1 hour ago" else "$hours hours ago"
        val days = hours / 24L
        if (days == 1L) return "yesterday"
        if (days < 7L) return "$days days ago"
        val weeks = days / 7L
        if (weeks < 5L) return if (weeks == 1L) "1 week ago" else "$weeks weeks ago"
        val months = days / 30L
        if (months < 12L) return if (months == 1L) "1 month ago" else "$months months ago"
        val years = days / 365L
        return if (years <= 1L) "1 year ago" else "$years years ago"
    }

    // ---- cheap git metadata readers (pure parsers; IO lives in the ViewModel) ----

    /**
     * Parses `.git/HEAD`. `ref: refs/heads/main` → `main`, nested branch
     * names keep their slashes (`feature/x`), a raw sha means detached →
     * `HEAD`. Anything unreadable → null (the card just omits the chip).
     */
    fun branchFromHeadFile(headContents: String?): String? {
        val trimmed = headContents?.trim() ?: return null
        if (trimmed.isEmpty()) return null
        if (trimmed.startsWith("ref:")) {
            val ref = trimmed.removePrefix("ref:").trim()
            if (ref.isEmpty()) return null
            val branch = ref.removePrefix("refs/heads/").ifEmpty { ref }
            return branch
        }
        // A raw object id: detached HEAD.
        return if (trimmed.matches(Regex("[0-9a-fA-F]{7,40}"))) "HEAD" else null
    }

    /**
     * Parses `git ls-remote --heads` output (`<sha>\trefs/heads/<name>`) into
     * branch names, preserving the remote's order and dropping refs that are
     * not heads.
     */
    fun branchNamesFromLsRemote(lines: List<String>): List<String> =
        lines.mapNotNull { raw ->
            val line = raw.trim()
            if (line.isEmpty()) return@mapNotNull null
            val parts = line.split('\t', limit = 2)
            if (parts.size != 2) return@mapNotNull null
            val ref = parts[1].trim()
            if (!ref.startsWith("refs/heads/")) return@mapNotNull null
            ref.removePrefix("refs/heads/").ifEmpty { null }
        }

    /**
     * Extracts `[remote "origin"] url` from a raw `.git/config` file text
     * (no git call — "Copy remote URL" must work offline). Falls back to the
     * first remote section when no `origin` exists. Returns null when absent.
     */
    fun remoteUrlFromConfig(configContents: String?): String? {
        val lines = configContents?.lines() ?: return null
        var sectionIsRemote = false
        var sectionIsOrigin = false
        var firstRemoteUrl: String? = null
        for (raw in lines) {
            val line = raw.trim()
            if (line.startsWith("[")) {
                val header = line.substringAfter('[').substringBeforeLast(']').trim()
                sectionIsRemote = header.startsWith("remote")
                sectionIsOrigin = sectionIsRemote &&
                    (header.substringAfter("remote", "").trim().trim('"', '\'') == "origin")
                continue
            }
            if (!sectionIsRemote || !line.contains('=')) continue
            val key = line.substringBefore('=').trim()
            val value = line.substringAfter('=').trim()
            if (key.equals("url", ignoreCase = true) && value.isNotEmpty()) {
                if (sectionIsOrigin) return value
                if (firstRemoteUrl == null) firstRemoteUrl = value
            }
        }
        return firstRemoteUrl
    }

    // ---- clone dialog helpers ----

    /**
     * De-duplicated project name inside the projects root:
     * `repo` → `repo_2` → `repo_3` … (same scheme as the Phase 8/13 imports).
     */
    fun uniqueProjectName(base: String, existing: Set<String>): String {
        val sanitized = ProjectPathUtils.sanitizeProjectName(base) ?: "project"
        if (!existing.contains(sanitized)) return sanitized
        var suffix = 2
        while (existing.contains("${sanitized}_$suffix")) suffix++
        return "${sanitized}_$suffix"
    }

    /**
     * Conservative branch-name gate before a name reaches `git` argv
     * (`--branch <name>`). Rejects empty names, flag-injection (`-` lead),
     * whitespace/control chars, path escapes (`..`), and the refspec
     * characters git itself reserves. Valid names may contain `/` for nested
     * branches. (D4 — argv is already shell-free via ProcessBuilder; this
     * only stops nonsensical/dangerous argv values.)
     */
    fun isValidBranchName(name: String): Boolean {
        val value = name.trim()
        if (value.isEmpty() || value.length > 200) return false
        if (value.startsWith("-")) return false
        if (value.startsWith(".")) return false
        if (value.startsWith("/")) return false
        if (value.endsWith("/")) return false
        if (value.contains("..")) return false
        if (value.contains("//")) return false
        if (value.endsWith(".lock")) return false
        if (value.any { it.isWhitespace() || it.isISOControl() }) return false
        // `~ ^ : ? * \ [` are git's own refname exclusions; `~`/`^` in
        // particular must never reach argv because they are revision syntax,
        // not branch names. Unicode names are rare for clones and rejected
        // for a conservative character set (D4).
        val allowed = Regex("^[A-Za-z0-9._/+-]+$")
        if (!allowed.matches(value)) return false
        // A name starting like `refs/` would double-prefix inside git.
        return !value.startsWith("refs/")
    }

    /**
     * True when a raw `git status --porcelain=v1 -b` transcript describes
     * uncommitted work. Only entry lines count — branch lines (`## …`) do
     * not, so a clean ahead/behind repo shows no badge.
     */
    fun hasChangesFromPorcelain(lines: List<String>): Boolean =
        GitStatusParser.parse(lines).files.isNotEmpty()

    private const val MINUTE_MILLIS = 60_000L
}

/**
 * Disk scan for one project folder: file count and newest-modified time.
 * Hidden infrastructure folders (`.git`, `.codec`) never count toward the
 * user-visible size, and a depth cap keeps huge trees cheap.
 */
object ProjectHubStats {

    private const val MAX_DEPTH = 6
    private const val MAX_FILES = 10_000

    data class ScanResult(val fileCount: Int, val lastModified: Long)

    fun scan(root: File): ScanResult {
        if (!root.isDirectory) return ScanResult(0, 0L)
        var count = 0
        var newest = root.lastModified()
        fun walk(dir: File, depth: Int) {
            val children = dir.listFiles() ?: return
            for (child in children) {
                if (child.isDirectory) {
                    if (depth < MAX_DEPTH && !SKIP_DIRS.contains(child.name)) {
                        walk(child, depth + 1)
                    }
                } else {
                    count++
                    val stamp = child.lastModified()
                    if (stamp > newest) newest = stamp
                    if (count >= MAX_FILES) return
                }
            }
        }
        walk(root, 0)
        return ScanResult(count, newest)
    }

    private val SKIP_DIRS = setOf(".git", ".codec")
}
