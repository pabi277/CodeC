package com.codeci.ide.ui.ai

import java.io.File

/**
 * Phase 78 (AI Level 2) — walking one open project and reading the few files
 * [AiProjectFiles.shortlist] picked. `java.io` only: no Android import, so the
 * whole walk is host-testable against a temp directory.
 *
 * ## The contract this file exists to keep
 *
 * - **Inside the root, or not at all** (D5). Every path handed back is
 *   `child.relativeTo(root)`, and a child whose canonical path leaves the
 *   root's canonical path is skipped — so a symlink to `/sdcard` or to a
 *   sibling project contributes nothing (`02_WHOLE_PROJECT_CONTEXT.md`: *"Do
 *   not crawl outside the selected project root, follow symlinks outside it,
 *   or include other CodeC projects"*).
 * - **No symlinks at all.** [isSymlink] is the same minSdk-24-safe check the
 *   editor's search uses (`ProjectSearch.kt:181-182`: canonical ≠ absolute),
 *   not `Files.isSymbolicLink` (API 26+).
 * - **Bounded.** At most [AiProjectFiles.MAX_ENTRIES] directory entries are
 *   visited and at most [AiProjectFiles.READ_SHORTLIST] files are read, so a
 *   large project cannot freeze the sheet or build an unbounded request.
 * - **Read-only** (D1). This object opens files for reading and does nothing
 *   else; there is no write, no run and no index anywhere in it (D6).
 * - **The dirty buffer wins.** For the file the user is actually looking at,
 *   the editor's live text is used when it is dirty — never stale disk bytes
 *   presented as the visible version (`02_WHOLE_PROJECT_CONTEXT.md`, "Editor
 *   consistency"). The result carries [AiProjectFiles.Candidate.fromBuffer] so
 *   the preview can label it.
 */
object AiProjectReader {

    /** A file bigger than this is not opened at all — it cannot fit the budget anyway. */
    const val MAX_FILE_BYTES: Long = 512L * 1024L

    /**
     * What one walk produced. The counts are part of the answer: the preview
     * tells the user how much of the project was looked at and how much was
     * left out and why, so nothing is dropped silently.
     */
    data class Scan(
        val candidates: List<AiProjectFiles.Candidate>,
        /** Paths refused as credential-shaped. Counted, never sent. */
        val skippedSecret: Int,
        /** Paths that are not code/text this filter recognises. */
        val skippedNotText: Int,
        /** Directories not entered (build output, VCS, hidden). */
        val skippedDirectories: Int,
        val filesSeen: Int,
        /** True when the walk stopped at [AiProjectFiles.MAX_ENTRIES]. */
        val hitEntryCap: Boolean,
        /** Phase 79 — every existing text file path seen in the walk, so CREATE cannot collide. */
        val allTextPaths: List<String> = emptyList()
    ) {
        val totalLeftOut: Int get() = skippedSecret + skippedNotText
    }

    /**
     * Walks [root], shortlists by path, reads the shortlist.
     *
     * [openPath] is the active tab's project-relative path (or null); when
     * [openDirty] its live [openText] is used instead of the disk copy.
     */
    fun scan(
        root: File,
        question: String,
        openPath: String?,
        openText: String?,
        openDirty: Boolean
    ): Scan {
        val rootDir = canonicalFileSafe(root) ?: return emptyScan()
        if (!rootDir.isDirectory) return emptyScan()

        val paths = mutableListOf<String>()
        var secret = 0
        var notText = 0
        var dirsSkipped = 0
        var filesSeen = 0
        var entries = 0
        var capped = false

        // Breadth-first with an explicit stack: no recursion, so a deep tree
        // cannot overflow, and the entry cap is checked in one place.
        val pending = ArrayDeque<File>()
        pending.addLast(rootDir)
        while (pending.isNotEmpty()) {
            if (entries >= AiProjectFiles.MAX_ENTRIES) {
                capped = true
                break
            }
            val dir = pending.removeFirst()
            val children = runCatching { dir.listFiles() }.getOrNull() ?: continue
            for (child in children.sortedBy { it.name.lowercase() }) {
                if (entries >= AiProjectFiles.MAX_ENTRIES) {
                    capped = true
                    break
                }
                entries++
                if (isSymlink(child)) {
                    dirsSkipped++
                    continue
                }
                if (!insideRoot(child, rootDir)) {
                    dirsSkipped++
                    continue
                }
                if (child.isDirectory) {
                    if (AiProjectFiles.isExcludedDirectory(child.name)) {
                        dirsSkipped++
                        continue
                    }
                    pending.addLast(child)
                    continue
                }
                filesSeen++
                val relative = relativePath(rootDir, child)
                when {
                    AiProjectFiles.isSecretLike(child.name) -> secret++
                    !AiProjectFiles.isTextFile(child.name) -> notText++
                    else -> paths += relative
                }
            }
        }

        val wanted = AiProjectFiles.shortlist(
            paths = paths,
            question = question,
            openPath = openPath,
            limit = AiProjectFiles.READ_SHORTLIST
        )
        val openRelative = openPath?.trim()?.takeIf { it.isNotEmpty() }?.replace('\\', '/')
        val candidates = mutableListOf<AiProjectFiles.Candidate>()
        var extraSecret = 0
        var extraNotText = 0
        for (relative in wanted) {
            val name = relative.substringAfterLast('/')
            // The shortlist is built from the same filter, but re-check here:
            // one place decides, and it decides twice rather than never.
            if (AiProjectFiles.isSecretLike(name)) {
                extraSecret++
                continue
            }
            if (!AiProjectFiles.isTextFile(name)) {
                extraNotText++
                continue
            }
            val bufferText = if (openDirty) openText else null
            val isBufferFile = openRelative != null && AiProjectFiles.samePath(relative, openRelative)
            val text = if (isBufferFile && bufferText != null) {
                cap(bufferText)
            } else {
                readCapped(File(rootDir, relative)) ?: continue
            }
            if (text.isBlank()) continue
            if (AiProjectFiles.exclusionFor(name, looksBinary(text), text.length) != null) {
                notText++
                continue
            }
            candidates += AiProjectFiles.Candidate(
                relativePath = relative,
                text = text,
                lines = countLines(text),
                fromBuffer = isBufferFile && bufferText != null,
                readCut = text.length >= AiProjectFiles.MAX_READ_CHARS
            )
        }
        return Scan(
            candidates = candidates,
            skippedSecret = secret + extraSecret,
            skippedNotText = notText + extraNotText,
            skippedDirectories = dirsSkipped,
            filesSeen = filesSeen,
            hitEntryCap = capped,
            allTextPaths = paths.toList()
        )
    }

    private fun emptyScan() = Scan(emptyList(), 0, 0, 0, 0, false)

    /** Reads at most [AiProjectFiles.MAX_READ_CHARS] characters; null when unreadable or too big. */
    internal fun readCapped(file: File): String? = runCatching {
        if (!file.isFile) return@runCatching null
        if (file.length() > MAX_FILE_BYTES) return@runCatching null
        val out = StringBuilder()
        file.reader(Charsets.UTF_8).use { r ->
            val buf = CharArray(8192)
            while (out.length < AiProjectFiles.MAX_READ_CHARS) {
                val want = minOf(buf.size, AiProjectFiles.MAX_READ_CHARS - out.length)
                val read = r.read(buf, 0, want)
                if (read <= 0) break
                out.append(buf, 0, read)
            }
        }
        out.toString()
    }.getOrNull()

    private fun cap(text: String): String =
        if (text.length <= AiProjectFiles.MAX_READ_CHARS) text
        else text.substring(0, AiProjectFiles.MAX_READ_CHARS)

    /**
     * Phase 85 (Level 8) — the result of reading one line range. [total] is the
     * file's TRUE line count (the reader streams to EOF), so coverage is honest:
     * a request for lines 1 900-2 000 of a 2 000-line file reports `of 2000`,
     * not `of 304` the way the old 24 000-char prefix did.
     */
    internal data class LineRange(
        val ok: Boolean,
        /** Why the read was refused, when [ok] is false. */
        val reason: String?,
        /** The delivered lines, without line numbers. */
        val slice: List<String>,
        /** First delivered line, 1-based; 0 when nothing was delivered. */
        val start: Int,
        /** Last delivered line, 1-based; 0 when nothing was delivered. */
        val end: Int,
        /** The file's true total line count. */
        val total: Int,
        /** True when the head looked binary (NUL) — the caller refuses. */
        val binary: Boolean,
        /** True when accumulation stopped at the char budget before the requested end. */
        val charCapped: Boolean,
        /** True when [shouldStop] ended the read early. */
        val stopped: Boolean
    )

    /**
     * Phase 85 (Level 8, item 1) — read the line range `[requestedStart, requestedEnd]`
     * by **streaming**, so any line of a file up to [MAX_FILE_BYTES] is reachable,
     * not only the lines inside the first [AiProjectFiles.MAX_READ_CHARS] characters.
     *
     * - **minSdk 24:** `java.io` `BufferedReader.readLine`, never `java.nio.file`.
     * - **UTF-8:** the reader decodes UTF-8, exactly as [readCapped] does.
     * - **Bounded memory:** only the requested slice is held (≤ [maxChars]); the
     *   rest of the file is counted, not stored, so [total] is honest.
     * - **Cancellable:** [shouldStop] is checked between lines, so Stop ends a big
     *   read promptly.
     * - **Binary:** the head (first 8 192 chars) is checked for NUL, matching
     *   [looksBinary]; a binary file is reported, never sliced.
     */
    internal fun readLineRange(
        file: File,
        requestedStart: Int,
        requestedEnd: Int,
        maxChars: Int,
        shouldStop: () -> Boolean = { false }
    ): LineRange = runCatching {
        if (!file.isFile) {
            return@runCatching LineRange(false, "not found", emptyList(), 0, 0, 0, false, false, false)
        }
        if (file.length() > MAX_FILE_BYTES) {
            return@runCatching LineRange(
                false, "larger than ${MAX_FILE_BYTES / 1024} KB", emptyList(), 0, 0, 0, false, false, false
            )
        }
        val slice = mutableListOf<String>()
        val head = StringBuilder()
        var total = 0
        var chars = 0
        var binary = false
        var stopped = false
        var charCapped = false
        file.bufferedReader(Charsets.UTF_8).use { r ->
            while (true) {
                if (shouldStop()) { stopped = true; break }
                val line = r.readLine() ?: break
                total++
                if (head.length < 8192) {
                    head.append(line).append('\n')
                    // NUL anywhere in the head window means "not text" — the same cheap
                    // check looksBinary makes, but caught while streaming so a SMALL
                    // binary file is refused too, not only one that runs past 8 192 chars.
                    if (line.indexOf('\u0000') >= 0) {
                        binary = true
                        break
                    }
                }
                if (total >= requestedStart && total <= requestedEnd) {
                    // Reserve ~9 chars per line for the "N: " prefix and newline the
                    // formatter adds, so the formatted body fits the result cap and
                    // the header never claims a line that was clipped away.
                    if (chars + line.length + 9 > maxChars) {
                        charCapped = true
                    } else {
                        slice += line
                        chars += line.length + 9
                    }
                }
            }
        }
        val start = if (slice.isEmpty()) 0 else requestedStart.coerceIn(1, maxOf(1, total))
        val end = if (slice.isEmpty()) 0 else start + slice.size - 1
        LineRange(true, null, slice, start, end, total, binary, charCapped, stopped)
    }.getOrDefault(
        LineRange(false, "could not be read", emptyList(), 0, 0, 0, false, false, false)
    )

    /** NUL in the head means "not text" — the same cheap check the search uses. */
    fun looksBinary(text: String): Boolean = text.take(8192).any { it == '\u0000' }

    fun countLines(text: String): Int {
        if (text.isEmpty()) return 0
        var lines = 1
        var at = 0
        while (true) {
            val nl = text.indexOf('\n', at)
            if (nl < 0) return lines
            lines++
            at = nl + 1
        }
    }

    /** The minSdk-24-safe symlink test (`ProjectSearch.kt:181-182`), copied not imported. */
    internal fun isSymlink(file: File): Boolean =
        runCatching { file.canonicalPath != file.absolutePath }.getOrDefault(false)

    /** Canonical containment: the child must stay under the canonical root. */
    internal fun insideRoot(child: File, rootDir: File): Boolean = runCatching {
        val childPath = child.canonicalFile.path
        val rootPath = rootDir.path
        childPath == rootPath || childPath.startsWith(rootPath + File.separator)
    }.getOrDefault(false)

    internal fun canonicalFileSafe(file: File): File? = runCatching { file.canonicalFile }.getOrNull()

    internal fun relativePath(rootDir: File, file: File): String =
        runCatching { file.relativeTo(rootDir).path.replace(File.separatorChar, '/') }
            .getOrDefault(file.name)
}
