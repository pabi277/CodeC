package com.codeci.ide.ui.ai

import java.io.File

/**
 * Phase 80 (AI Level 4) — **the disk half of the tool surface**: `list_files`,
 * `search_project` and `read_file`, executed against one open project.
 *
 * ## The contract
 *
 * - **Read-only, always** (D1). This object opens files for reading; there is
 *   no write, no delete, no run and no index anywhere in it, and
 *   `AiHelperWiringTest`/`AiLevel4WiringTest` scan this file for the forbidden
 *   calls. `request_run` never reaches here: it is an approval request the UI
 *   handles through the existing RUN pipeline.
 * - **Only the paths the walk admitted.** The caller passes the same
 *   [AiProjectReader.Scan.allTextPaths] the map was built from, so a
 *   credential-shaped file, an excluded directory or a symlink can never be
 *   opened by a tool call — the filter ran once, and the tool list is its
 *   output. [read_file] additionally re-checks the name, because one check is
 *   a mistake and two is a coincidence (the Phase 78 rule).
 * - **Bounded.** At most [AiToolLimits.MAX_SEARCH_FILES] files are opened per
 *   search, at most [AiToolLimits.MAX_READ_LINES] lines per read, and every
 *   result is clipped to [AiToolLimits.MAX_RESULT_CHARS] characters with a
 *   visible marker — the model is told when it is seeing a fragment, so it can
 *   ask for the next range.
 * - **Stoppable.** [execute] takes a `shouldStop` probe, checked between files,
 *   so the Stop button ends a big search promptly instead of after the walk.
 * - **The dirty buffer wins**, exactly as in Level 2: a file the user is
 *   editing is read from the editor's live text when it is dirty, and the
 *   result says `[unsaved edits]` so nobody is told the disk changed.
 * - **`java.io` only** — no Android import, so `AiToolRunnerTest` runs on the
 *   host JVM against a real temp tree (symlink, `.env`, caps and all).
 */
object AiToolRunner {

    /** One line of a search hit or a file listing is clipped here. */
    const val MAX_LINE_CHARS = 220

    /** The marker that tells the model (and the timeline) a result was cut. */
    const val CUT_NOTE = "… [result cut; ask for a smaller range or a narrower search]"

    const val STOP_NOTE = "[stopped by the user]"

    data class Outcome(
        val ok: Boolean,
        val text: String,
        val truncated: Boolean = false
    )

    /**
     * Runs one validated [call].
     *
     * [paths] is the walk's admitted code/text list ([AiProjectReader.Scan.allTextPaths]);
     * [dirtyBuffers] maps project-relative paths to the editor's live text for
     * open, unsaved tabs.
     */
    fun execute(
        call: AiToolCall,
        root: File,
        paths: List<String>,
        dirtyBuffers: Map<String, String> = emptyMap(),
        shouldStop: () -> Boolean = { false }
    ): Outcome {
        val rootDir = AiProjectReader.canonicalFileSafe(root)
            ?: return Outcome(false, "The project folder could not be read.")
        if (!rootDir.isDirectory) return Outcome(false, "The project folder was not found.")
        return when (call.name) {
            AiToolName.LIST_FILES -> listFiles(call, paths)
            AiToolName.SEARCH_PROJECT -> search(call, rootDir, paths, shouldStop)
            AiToolName.READ_FILE -> read(call, rootDir, dirtyBuffers, shouldStop)
            // request_run is an approval request; it must never execute anything.
            AiToolName.REQUEST_RUN -> Outcome(false, "request_run is approved by the user, not executed as a tool.")
        }
    }

    // ---- list_files -------------------------------------------------------

    private fun listFiles(call: AiToolCall, paths: List<String>): Outcome {
        val prefix = call.path?.let { "$it/" }
        var matching = paths
            .filter { prefix == null || it.startsWith(prefix) }
            .filter { call.ext == null || it.substringAfterLast('.', "").equals(call.ext, ignoreCase = true) }
            .sorted()
        if (matching.isEmpty()) {
            return Outcome(true, "No code or text file matches" +
                (if (call.path != null) " under ${call.path}/" else "") +
                (if (call.ext != null) " with extension .${call.ext}" else "") + ".")
        }
        val total = matching.size
        val truncated = total > AiToolLimits.MAX_LIST_ENTRIES
        if (truncated) matching = matching.take(AiToolLimits.MAX_LIST_ENTRIES)
        val header = "FILES" + (if (call.path != null) " in ${call.path}/" else "") +
            (if (call.ext != null) " (*.${call.ext})" else "") +
            " — ${matching.size} of $total listed"
        val body = buildString {
            append(header)
            for (p in matching) append('\n').append(p)
            if (truncated) append('\n').append(CUT_NOTE)
        }
        return Outcome(true, clip(body), truncated)
    }

    // ---- search_project ---------------------------------------------------

    private fun search(
        call: AiToolCall,
        rootDir: File,
        paths: List<String>,
        shouldStop: () -> Boolean
    ): Outcome {
        val needle = call.query.orEmpty()
        if (needle.isEmpty()) return Outcome(false, "search_project needs a query.")
        val maxHits = call.max ?: AiToolLimits.MAX_SEARCH_HITS
        val hits = mutableListOf<String>()
        var filesScanned = 0
        var filesSkipped = 0
        var capped = false
        var stopped = false
        for (relative in paths.sorted()) {
            if (shouldStop()) {
                stopped = true
                break
            }
            if (filesScanned >= AiToolLimits.MAX_SEARCH_FILES) {
                capped = true
                break
            }
            if (AiProjectFiles.isSecretLike(relative.substringAfterLast('/'))) {
                filesSkipped++
                continue
            }
            val file = File(rootDir, relative)
            if (!safeChild(file, rootDir)) {
                filesSkipped++
                continue
            }
            val text = AiProjectReader.readCapped(file)
            if (text == null) {
                filesSkipped++
                continue
            }
            if (AiProjectReader.looksBinary(text)) {
                filesSkipped++
                continue
            }
            filesScanned++
            var lineNo = 0
            for (line in text.lineSequence()) {
                lineNo++
                if (!line.contains(needle, ignoreCase = true)) continue
                hits += "$relative:$lineNo: ${line.trim().take(MAX_LINE_CHARS)}"
                if (hits.size >= maxHits) {
                    capped = true
                    break
                }
            }
            if (capped) break
        }
        if (hits.isEmpty()) {
            return Outcome(
                true,
                "SEARCH \"$needle\" — no match in $filesScanned files scanned" +
                    (if (filesSkipped > 0) " ($filesSkipped skipped)" else "") +
                    (if (stopped) "." + STOP_NOTE else "."),
                truncated = stopped
            )
        }
        val header = "SEARCH \"$needle\" — ${hits.size} match" +
            (if (hits.size == 1) "" else "es") +
            " in $filesScanned files scanned" +
            (if (filesSkipped > 0) " ($filesSkipped skipped)" else "")
        val body = buildString {
            append(header)
            for (h in hits) append('\n').append(h)
            if (capped) append('\n').append(CUT_NOTE)
            if (stopped) append('\n').append(STOP_NOTE)
        }
        return Outcome(true, clip(body), capped || stopped)
    }

    // ---- read_file --------------------------------------------------------

    private fun read(
        call: AiToolCall,
        rootDir: File,
        dirtyBuffers: Map<String, String>,
        shouldStop: () -> Boolean = { false }
    ): Outcome {
        val path = call.path ?: return Outcome(false, "read_file needs a path.")
        if (AiProjectFiles.isSecretLike(path.substringAfterLast('/'))) {
            return Outcome(false, "$path is credential-shaped and is never read.")
        }
        val requestedStart = (call.start ?: 1).coerceAtLeast(1)
        val requestedEnd = (call.end ?: Int.MAX_VALUE).coerceAtLeast(requestedStart)
        val fromBuffer = dirtyBuffers.entries.firstOrNull { AiProjectFiles.samePath(it.key, path) }?.value

        // Phase 85 (Level 8): the dirty buffer is already whole in memory; the disk
        // path streams the requested range so ANY line is reachable, not only the
        // lines inside the first 24 000 characters.
        val slice: List<String>
        val start: Int
        val end: Int
        val total: Int
        var charCapped = false
        var stopped = false
        if (fromBuffer != null) {
            val text = AiEditProposalParser.normalizeLf(fromBuffer)
            val lines = text.split('\n')
            total = when {
                text.isEmpty() -> 0
                text.endsWith("\n") -> lines.size - 1
                else -> lines.size
            }
            if (total == 0) return Outcome(true, "FILE $path is empty. [complete]")
            start = requestedStart.coerceIn(1, total)
            end = requestedEnd.coerceIn(start, total)
            slice = lines.subList(start - 1, end)
        } else {
            val file = File(rootDir, path)
            if (!safeChild(file, rootDir)) return Outcome(false, "$path is outside the project.")
            val range = AiProjectReader.readLineRange(
                file, requestedStart, requestedEnd, AiToolLimits.MAX_RESULT_CHARS, shouldStop
            )
            if (!range.ok) return Outcome(false, "$path ${range.reason}.")
            if (range.binary) return Outcome(false, "$path does not look like text.")
            if (range.total == 0) return Outcome(true, "FILE $path is empty. [complete]")
            if (range.slice.isEmpty()) {
                return Outcome(
                    false,
                    "$path has ${range.total} line" + (if (range.total == 1) "" else "s") +
                        "; the requested start $requestedStart is past the end. [refused: out of range]"
                )
            }
            slice = range.slice
            start = range.start
            end = range.end
            total = range.total
            charCapped = range.charCapped
            stopped = range.stopped
        }

        return Outcome(true, formatRange(path, slice, start, end, total, fromBuffer != null, charCapped, stopped),
            truncated = end < total || charCapped || stopped)
    }

    /**
     * Phase 85 (Level 8, S2) — one honest read block. The header states the lines
     * ACTUALLY delivered and the file's true total, and an explicit coverage token
     * distinguishes a whole-file read from a fragment:
     * `[complete]` only when the delivery reached the last line, otherwise
     * `[partial: <reason>]`. A fragment can never be mistaken for the whole file.
     */
    private fun formatRange(
        path: String,
        slice: List<String>,
        start: Int,
        end: Int,
        total: Int,
        fromBuffer: Boolean,
        charCapped: Boolean,
        stopped: Boolean
    ): String {
        val coverage = when {
            stopped -> "[partial: stopped by the user]"
            charCapped -> "[partial: result cut at ${AiToolLimits.MAX_RESULT_CHARS} chars]"
            end >= total -> "[complete]"
            else -> "[partial: more lines follow]"
        }
        val next = if (end < total) {
            "; read ${end + 1}-${minOf(total, end + AiToolLimits.MAX_READ_LINES)} next"
        } else ""
        val header = "FILE $path — lines $start-$end of $total" +
            (if (fromBuffer) " [unsaved edits]" else "") + " $coverage" + next
        val body = buildString {
            append(header)
            for ((i, line) in slice.withIndex()) append('\n').append(start + i).append(": ").append(line)
            // Keep the cut marker consistent with list_files / search_project: a
            // partial read ends with CUT_NOTE, so the Phase 84 timeline clip still
            // preserves an explicit "this was cut" signal alongside the header token.
            if (charCapped || stopped) append('\n').append(CUT_NOTE)
        }
        return clip(body)
    }

    // ---- shared -----------------------------------------------------------

    private fun safeChild(file: File, rootDir: File): Boolean =
        !AiProjectReader.isSymlink(file) && AiProjectReader.insideRoot(file, rootDir)

    private fun clip(text: String): String =
        if (text.length <= AiToolLimits.MAX_RESULT_CHARS) text
        else text.substring(0, AiToolLimits.MAX_RESULT_CHARS - CUT_NOTE.length - 1) + "\n" + CUT_NOTE
}
