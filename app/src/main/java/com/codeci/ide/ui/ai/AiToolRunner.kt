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
        shouldStop: () -> Boolean = { false },
        cachedFiles: Map<String, String> = emptyMap(),
        readWindow: Int = AiToolLimits.MAX_READ_LINES,
        /**
         * Phase 94 — the build/run output the Output panel held when this task
         * started, frozen at Send. Only `read_run_output` reads it, and only what
         * the user already has on screen travels.
         */
        runOutput: List<String> = emptyList()
    ): Outcome {
        val rootDir = AiProjectReader.canonicalFileSafe(root)
            ?: return Outcome(false, "The project folder could not be read.").scrubbed()
        if (!rootDir.isDirectory) return Outcome(false, "The project folder was not found.").scrubbed()
        // S9: clamped again here, so the follow-up hint can never name a window
        // the validator would refuse.
        val window = AiOptionsPolicy.clampReadWindow(readWindow).coerceAtMost(AiToolLimits.MAX_READ_LINES)
        val outcome = when (call.name) {
            AiToolName.LIST_FILES -> listFiles(call, paths)
            AiToolName.SEARCH_PROJECT -> search(call, rootDir, paths, shouldStop)
            AiToolName.READ_FILE -> read(call, rootDir, paths, dirtyBuffers, shouldStop, cachedFiles, window)
            AiToolName.READ_FILES -> readFiles(call, rootDir, paths, dirtyBuffers, shouldStop, cachedFiles, window)
            AiToolName.FIND_FILES -> findFiles(call, paths)
            AiToolName.OUTLINE_FILE -> outlineFile(call, rootDir, paths, dirtyBuffers, cachedFiles, shouldStop)
            AiToolName.READ_RUN_OUTPUT -> readRunOutput(call, runOutput)
            // request_run is an approval request; it must never execute anything.
            AiToolName.REQUEST_RUN -> Outcome(false, "request_run is approved by the user, not executed as a tool.")
        }
        // Phase 94 — **the last gate before the model**: whatever a tool read, a
        // credential-shaped VALUE never travels. This is the one place it is
        // applied, so a new tool cannot forget it (the same single-point rule the
        // walk's name filter follows).
        return outcome.scrubbed()
    }

    /**
     * Phase 94 — [AiSecretScan] at the result boundary, with the honest count
     * line appended by the scan itself.
     */
    private fun Outcome.scrubbed(): Outcome {
        val scan = AiSecretScan.redact(text)
        return if (scan.clean) this else copy(text = scan.text)
    }

    // ---- find_files -------------------------------------------------------

    /**
     * Phase 94 — the glob over admitted paths. No disk walk: the list *is* the
     * walk's output, which is why a pattern can never reach a file the Level 2
     * filter refused. A pattern without a slash matches file **names** anywhere
     * (`*.md`), one with a slash matches the whole relative path (`*.kt` after a
     * `src/` prefix), and `**` crosses directories.
     */
    private fun findFiles(call: AiToolCall, paths: List<String>): Outcome {
        val pattern = call.pattern.orEmpty()
        if (pattern.isEmpty()) return Outcome(false, "find_files needs a pattern.")
        val matches = paths.filter { globMatches(pattern, it) }.sorted()
        val limit = call.max ?: AiToolLimits.MAX_LIST_ENTRIES
        if (matches.isEmpty()) {
            return Outcome(true, "FILES matching \"$pattern\" — none of the ${paths.size} admitted files match.")
        }
        val truncated = matches.size > limit
        val shown = if (truncated) matches.take(limit) else matches
        val body = buildString {
            append("FILES matching \"$pattern\" — ${shown.size} of ${matches.size} listed")
            for (p in shown) append('\n').append(p)
            if (truncated) append('\n').append(CUT_NOTE)
        }
        return Outcome(true, clip(body), truncated)
    }

    /** `*` = any run except `/`, `?` = one character except `/`, `**` = any run. */
    internal fun globMatches(pattern: String, path: String): Boolean {
        val target = if (pattern.contains('/')) path else path.substringAfterLast('/')
        val regex = StringBuilder("^")
        var i = 0
        while (i < pattern.length) {
            val c = pattern[i]
            when {
                c == '*' && i + 1 < pattern.length && pattern[i + 1] == '*' -> {
                    regex.append(".*")
                    i += 2
                    if (i < pattern.length && pattern[i] == '/') i += 1
                }
                c == '*' -> { regex.append("[^/]*"); i += 1 }
                c == '?' -> { regex.append("[^/]"); i += 1 }
                else -> { regex.append(Regex.escape(c.toString())); i += 1 }
            }
        }
        regex.append('$')
        return Regex(regex.toString()).matches(target)
    }

    // ---- outline_file -----------------------------------------------------

    /**
     * Phase 94 — the structure of one file. Reads with the same reader, the same
     * admission list and the same "dirty buffer wins" rule as [read]; the answer
     * is [AiOutline]'s rows, capped at [AiToolLimits.MAX_OUTLINE_ROWS].
     */
    private fun outlineFile(
        call: AiToolCall,
        rootDir: File,
        paths: List<String>,
        dirtyBuffers: Map<String, String>,
        cachedFiles: Map<String, String>,
        shouldStop: () -> Boolean
    ): Outcome {
        val path = call.path ?: return Outcome(false, "outline_file needs a path.")
        if (AiProjectFiles.isSecretLike(path.substringAfterLast('/'))) {
            return Outcome(false, "$path is credential-shaped and is never read.")
        }
        if (paths.none { AiProjectFiles.samePath(it, path) }) {
            return Outcome(false, "$path is not a code or text file in this project.")
        }
        val file = File(rootDir, path)
        if (!safeChild(file, rootDir)) return Outcome(false, "$path is outside the project.")
        if (shouldStop()) return Outcome(true, "STRUCTURE $path — $STOP_NOTE", truncated = true)
        val fromBuffer = dirtyBuffers.entries.firstOrNull { AiProjectFiles.samePath(it.key, path) }?.value
        val fromMemory = if (fromBuffer == null) {
            cachedFiles.entries.firstOrNull { AiProjectFiles.samePath(it.key, path) }?.value
        } else null
        val source = fromBuffer ?: fromMemory
        val text = source?.let { AiEditProposalParser.normalizeLf(it) }
            ?: AiProjectReader.readCapped(file)
            ?: return Outcome(false, "$path ${if (file.isFile) "could not be read." else "was not found."}")
        val lineCount = lineCountOf(text)
        val note = when {
            fromBuffer != null -> " [unsaved edits]"
            fromMemory != null -> " [cached memory]"
            else -> ""
        }
        val rows = AiOutline.outline(path, text, AiToolLimits.MAX_OUTLINE_ROWS)
        if (rows.isEmpty()) {
            return Outcome(
                true,
                "STRUCTURE $path — $lineCount lines$note; no definitions were recognised " +
                    "(this file may not be code, or it may hold only prose)."
            )
        }
        val body = buildString {
            append("STRUCTURE $path — $lineCount lines, ${rows.size} definition")
            if (rows.size == 1) append('\n') else append("s\n")
            for (row in rows) append(row.line).append(": ").append(row.text).append('\n')
            if (rows.size >= AiToolLimits.MAX_OUTLINE_ROWS) append(CUT_NOTE)
        }
        return Outcome(true, clip(body.trimEnd()), rows.size >= AiToolLimits.MAX_OUTLINE_ROWS)
    }

    /**
     * The same count the read result reports: a trailing newline does not make a
     * line. (`AiProjectReader.countLines` counts it, which is right for a range
     * walk and wrong for a file's own "N lines" header.)
     */
    private fun lineCountOf(text: String): Int {
        if (text.isEmpty()) return 0
        val lines = text.split('\n').size
        return if (text.endsWith("\n")) lines - 1 else lines
    }

    // ---- read_run_output --------------------------------------------------

    /**
     * Phase 94 — the tail of the build/run output that was on screen at Send.
     * The snapshot is the app's own (never a command, never a fresh run), the
     * caller may ask for fewer lines, and the model is told the size it is
     * seeing — an empty panel says so instead of inventing output.
     */
    private fun readRunOutput(call: AiToolCall, runOutput: List<String>): Outcome {
        if (runOutput.isEmpty()) {
            return Outcome(false, "read_run_output: nothing has been built or run yet in this project session.")
        }
        val wanted = (call.lines ?: AiToolLimits.DEFAULT_OUTPUT_LINES)
            .coerceIn(1, AiToolLimits.MAX_OUTPUT_LINES)
        val shown = runOutput.takeLast(wanted)
        val truncated = shown.size < runOutput.size
        val body = buildString {
            append("OUTPUT (build/run, as it was on screen when this task started) — ")
            append("last ${shown.size} of ${runOutput.size} line")
            if (runOutput.size == 1) append('\n') else append("s\n")
            for (line in shown) append(line).append('\n')
            if (truncated) append(CUT_NOTE)
        }
        return Outcome(true, clip(body.trimEnd()), truncated)
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
        // Phase 96 — the `list_files` scoping, here too: `path` and `ext`
        // narrow the walk's own admitted list and can never widen it, so a
        // question about one folder spends no read budget on the whole project.
        val prefix = call.path?.let { "$it/" }
        val scope = (if (call.path != null) " in ${call.path}/" else "") +
            (if (call.ext != null) " *.${call.ext}" else "")
        val scoped = paths.filter {
            (prefix == null || it.startsWith(prefix)) &&
                (call.ext == null ||
                    it.substringAfterLast('.', "").equals(call.ext, ignoreCase = true))
        }
        val hits = mutableListOf<String>()
        var filesScanned = 0
        var filesSkipped = 0
        var capped = false
        var stopped = false
        for (relative in scoped.sorted()) {
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
                "SEARCH \"$needle\"$scope — no match in $filesScanned files scanned" +
                    (if (filesSkipped > 0) " ($filesSkipped skipped)" else "") +
                    (if (stopped) "." + STOP_NOTE else "."),
                truncated = stopped
            )
        }
        val header = "SEARCH \"$needle\"$scope — ${hits.size} match" +
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
        paths: List<String>,
        dirtyBuffers: Map<String, String>,
        shouldStop: () -> Boolean = { false },
        cachedFiles: Map<String, String> = emptyMap(),
        readWindow: Int = AiToolLimits.MAX_READ_LINES
    ): Outcome {
        val path = call.path ?: return Outcome(false, "read_file needs a path.")
        if (AiProjectFiles.isSecretLike(path.substringAfterLast('/'))) {
            return Outcome(false, "$path is credential-shaped and is never read.")
        }
        if (paths.none { AiProjectFiles.samePath(it, path) }) {
            return Outcome(false, "$path is not a code or text file in this project.")
        }
        if (AiTaskMemoryPolicy.safeRelativePath(path) == null) {
            return Outcome(false, "$path is not an admitted code/text path.")
        }
        val file = File(rootDir, path)
        if (!safeChild(file, rootDir)) return Outcome(false, "$path is outside the project.")
        val requestedStart = (call.start ?: 1).coerceAtLeast(1)
        val requestedEnd = (call.end ?: Int.MAX_VALUE).coerceAtLeast(requestedStart)
        val fromBuffer = dirtyBuffers.entries.firstOrNull { AiProjectFiles.samePath(it.key, path) }?.value
        val fromMemory = if (fromBuffer == null) {
            cachedFiles.entries.firstOrNull { AiProjectFiles.samePath(it.key, path) }?.value
        } else null
        val fromText = fromBuffer ?: fromMemory
        val fromDirtyBuffer = fromBuffer != null
        val fromCachedMemory = fromMemory != null

        // Level 9 reuses only a persisted snapshot whose version was rechecked
        // immediately before this call. A live editor buffer always wins.
        val slice: List<String>
        val start: Int
        val end: Int
        val total: Int
        var charCapped = false
        var stopped = false
        if (fromText != null) {
            val text = AiEditProposalParser.normalizeLf(fromText)
            val lines = text.split('\n')
            total = when {
                text.isEmpty() -> 0
                text.endsWith("\n") -> lines.size - 1
                else -> lines.size
            }
            if (total == 0) {
                val source = if (fromDirtyBuffer) " [unsaved edits]" else if (fromCachedMemory) " [cached memory]" else ""
                return Outcome(true, "FILE $path is empty$source [complete]")
            }
            if (requestedStart > total) {
                return Outcome(
                    false,
                    "$path has $total line" + (if (total == 1) "" else "s") +
                        "; the requested start $requestedStart is past the end. [refused: out of range]"
                )
            }
            start = requestedStart
            end = requestedEnd.coerceAtMost(total)
            slice = lines.subList(start - 1, end)
        } else {
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

        return Outcome(
            true,
            formatRange(
                path, slice, start, end, total, fromDirtyBuffer, charCapped, stopped,
                fromCachedMemory, readWindow
            ),
            truncated = end < total || charCapped || stopped
        )
    }

    // ---- read_files (batch) ----------------------------------------------

    /**
     * Phase 85 (Level 8, item 3) — read several files in one round trip. This is
     * parallel read-only IO, NOT parallel agents (**S7**): one brain still writes.
     * Each path is validated and read on its own, in the order asked, so a secret,
     * escaping, binary, or missing path yields a per-file `[refused: …]` while its
     * siblings still deliver (**S5**). [shouldStop] is checked between files, so
     * Stop ends a batch promptly. The whole batch shares the one
     * [AiToolLimits.MAX_RESULT_CHARS] result cap, divided across the files.
     */
    private fun readFiles(
        call: AiToolCall,
        rootDir: File,
        paths: List<String>,
        dirtyBuffers: Map<String, String>,
        shouldStop: () -> Boolean = { false },
        cachedFiles: Map<String, String> = emptyMap(),
        readWindow: Int = AiToolLimits.MAX_READ_LINES
    ): Outcome {
        val specs = call.reads
        if (specs.isNullOrEmpty()) return Outcome(false, "read_files needs a paths list.")
        val admitted = paths.toSet()
        // Divide the one result cap across the batch, leaving ~120 chars/file for
        // the header and status line, so no file's block is clipped mid-delivery.
        val perFile = ((AiToolLimits.MAX_RESULT_CHARS - specs.size * 120) / specs.size).coerceAtLeast(400)
        val blocks = mutableListOf<String>()
        var truncated = false
        var stopped = false
        for (spec in specs) {
            if (!stopped && shouldStop()) stopped = true
            if (stopped) {
                blocks += "FILE ${spec.path} — [partial: stopped by the user]"
                truncated = true
                continue
            }
            val block = readBatchBlock(
                spec, rootDir, admitted, dirtyBuffers, cachedFiles, perFile, shouldStop, readWindow
            )
            blocks += block.first
            truncated = truncated || block.second
        }
        val body = blocks.joinToString("\n\n")
        val clipped = clip(body)
        return Outcome(true, clipped, truncated = truncated || clipped.length < body.length)
    }

    /** One file's block inside a batch: `(text, truncated)`. Per-path security is
     *  re-applied here, never once for the whole batch. */
    private fun readBatchBlock(
        spec: ReadSpec,
        rootDir: File,
        admitted: Set<String>,
        dirtyBuffers: Map<String, String>,
        cachedFiles: Map<String, String>,
        budget: Int,
        shouldStop: () -> Boolean,
        readWindow: Int = AiToolLimits.MAX_READ_LINES
    ): Pair<String, Boolean> {
        val path = spec.path
        if (AiProjectFiles.isSecretLike(path.substringAfterLast('/'))) {
            return "FILE $path — [refused: credential-shaped]" to false
        }
        if (admitted.none { AiProjectFiles.samePath(it, path) }) {
            return "FILE $path — [refused: not a code or text file in this project]" to false
        }
        if (AiTaskMemoryPolicy.safeRelativePath(path) == null) {
            return "FILE $path — [refused: unsafe or non-text path]" to false
        }
        val file = File(rootDir, path)
        if (!safeChild(file, rootDir)) return "FILE $path — [refused: outside the project]" to false
        val fromBuffer = dirtyBuffers.entries.firstOrNull { AiProjectFiles.samePath(it.key, path) }?.value
        val fromMemory = if (fromBuffer == null) {
            cachedFiles.entries.firstOrNull { AiProjectFiles.samePath(it.key, path) }?.value
        } else null
        val fromText = fromBuffer ?: fromMemory
        val fromDirtyBuffer = fromBuffer != null
        val fromCachedMemory = fromMemory != null
        val requestedStart = spec.start.coerceAtLeast(1)
        val requestedEnd = spec.end.coerceAtLeast(requestedStart)

        if (fromText != null) {
            val text = AiEditProposalParser.normalizeLf(fromText)
            val lines = text.split('\n')
            val total = when {
                text.isEmpty() -> 0
                text.endsWith("\n") -> lines.size - 1
                else -> lines.size
            }
            if (total == 0) {
                val source = if (fromDirtyBuffer) " [unsaved edits]" else if (fromCachedMemory) " [cached memory]" else ""
                return "FILE $path — empty$source [complete]" to false
            }
            if (requestedStart > total) {
                return "FILE $path — [refused: start ${spec.start} is past the end of $total line" +
                    (if (total == 1) "" else "s") + "]" to false
            }
            val start = requestedStart
            val end = requestedEnd.coerceAtMost(total)
            return formatRange(
                path, lines.subList(start - 1, end), start, end, total,
                fromDirtyBuffer, false, false, fromCachedMemory, readWindow
            ) to (end < total)
        }

        val range = AiProjectReader.readLineRange(file, requestedStart, requestedEnd, budget, shouldStop)
        return when {
            !range.ok -> "FILE $path — [refused: ${range.reason}]" to false
            range.binary -> "FILE $path — [refused: not text]" to false
            range.total == 0 -> "FILE $path — empty [complete]" to false
            range.slice.isEmpty() ->
                "FILE $path — [refused: start ${spec.start} is past the end of ${range.total} line" +
                    (if (range.total == 1) "" else "s") + "]" to false
            else -> formatRange(
                path, range.slice, range.start, range.end, range.total,
                false, range.charCapped, range.stopped, false, readWindow
            ) to (range.end < range.total || range.charCapped || range.stopped)
        }
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
        stopped: Boolean,
        fromCachedMemory: Boolean = false,
        readWindow: Int = AiToolLimits.MAX_READ_LINES
    ): String {
        val coverage = when {
            stopped -> "[partial: stopped by the user]"
            charCapped -> "[partial: result cut at ${AiToolLimits.MAX_RESULT_CHARS} chars]"
            end >= total -> "[complete]"
            else -> "[partial: more lines follow]"
        }
        // Level 10: the hint names the SAME window the validator enforces, so the
        // model is never told to ask for a range that would then be refused.
        val next = if (end < total) {
            "; read ${end + 1}-${minOf(total, end + readWindow)} next"
        } else ""
        val header = "FILE $path — lines $start-$end of $total" +
            (if (fromBuffer) " [unsaved edits]" else if (fromCachedMemory) " [cached memory]" else "") +
            " $coverage" + next
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
