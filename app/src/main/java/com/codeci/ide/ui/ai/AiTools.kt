package com.codeci.ide.ui.ai

/**
 * Phase 80 (AI Level 4) — **the bounded tool surface**: the wire format the
 * model uses to ask CodeC for something, the typed arguments CodeC builds from
 * it, and the validation that decides whether a call may run at all.
 *
 * ## The wire format, and why it is text
 *
 * ```text
 * <<<CODEC_TOOL name="read_file">>>
 * path: src/main.c
 * start: 1
 * end: 60
 * <<<END_CODEC_TOOL>>>
 * ```
 *
 * This is CodeC's own protocol, parsed locally, exactly like Phase 79's
 * `<<<CODEC_EDIT …>>>` blocks. Gemini's native function calling was
 * **rejected for this phase** and the reason is recorded in
 * `docs/phases/03-editor/chat-phase80/PART_80_1_REPO_MAP_AND_TOOLS.md`: it
 * would change the request body and the streamed response shape
 * ([GeminiRequest]/[GeminiResponse], the Level 0 §4.2 decision), it would move
 * tool semantics into the provider layer that Levels 5-6 exist to replace, and
 * the text form keeps "what you saw is what was sent" true in the timeline
 * (`04_AGENT_TOOLS_AND_RUN_LOOP.md`: *"Every call has validated typed
 * arguments"* — validation happens HERE, not in the model).
 *
 * ## Fail closed
 *
 * Every rule below is a refusal, never a repair: unknown tool names, unknown
 * argument keys, unparsable numbers, paths outside the project, credential
 * names, excluded directories, missing files and over-budget ranges are all
 * **Denied** with one short reason. A denial is reported back to the model as
 * a tool result, so it can try something else — it is never silently ignored,
 * and it never becomes a write or a command.
 *
 * Pure Kotlin (`rule.md` §4.4): strings, numbers and sets, no `java.io`, no
 * Android. The disk half is [AiToolRunner].
 */
enum class AiToolName(val wire: String) {
    LIST_FILES("list_files"),
    SEARCH_PROJECT("search_project"),
    READ_FILE("read_file"),
    READ_FILES("read_files"),
    /**
     * Phase 94 — glob over the walk's admitted paths. Read-only: it opens
     * nothing; the list it filters is the one the Level 2 filter already made.
     */
    FIND_FILES("find_files"),
    /** Phase 94 — the definitions of one file with their line numbers. */
    OUTLINE_FILE("outline_file"),
    /**
     * Phase 94 — the last build/run output the Output panel held when the task
     * started. Read-only: it reads a snapshot the app already has on screen.
     */
    READ_RUN_OUTPUT("read_run_output"),
    REQUEST_RUN("request_run");

    companion object {
        fun fromWire(name: String): AiToolName? = entries.firstOrNull { it.wire == name.trim() }
    }
}

/** One `<<<CODEC_TOOL …>>>` block as it arrived, before validation. */
data class AiToolRequest(val rawName: String, val args: Map<String, String>) {
    val name: AiToolName? get() = AiToolName.fromWire(rawName)
}

/** The outcome of reading a model answer: prose plus whatever tool blocks it carried. */
sealed class AiToolParse {
    /** [calls] is empty when the answer is just prose — that ends the task. */
    data class Calls(val prose: String, val calls: List<AiToolRequest>) : AiToolParse()

    /**
     * A block that could not be read (unclosed, no name, a line that is not
     * `key: value`). Nothing in it executes; the loop sends the reason back as
     * a tool result so the model can correct itself.
     *
     * Phase 84 (Level 7, fix 6): [calls] carries the blocks that **were** parsed
     * successfully before the malformed one. The old parser did an early
     * `return`, discarding them, so three good `read_file` blocks beside one
     * broken block were all thrown away and the model re-read from scratch.
     */
    data class Malformed(
        val prose: String,
        val reason: String,
        val calls: List<AiToolRequest> = emptyList()
    ) : AiToolParse()
}

object AiToolProtocol {

    const val OPEN = "<<<CODEC_TOOL"
    const val CLOSE = "<<<END_CODEC_TOOL>>>"

    /**
     * Phase 94 — the canonical spellings above, read with room for the model's
     * own: any case, spaces inside the brackets. The app still writes and teaches
     * the canonical form; only the reader widened.
     */
    private val OPEN_MARKER = Regex("""<<<\s*CODEC_TOOL(?![A-Za-z0-9_])""", RegexOption.IGNORE_CASE)
    private val CLOSE_MARKER = Regex("""<<<\s*END_CODEC_TOOL\s*>>>""", RegexOption.IGNORE_CASE)

    /** The bare protocol word: an answer that names it is attempting the wire. */
    private val BLOCK_WORD = Regex("""(?<![A-Za-z0-9])CODEC_TOOL(?![A-Za-z0-9_])""", RegexOption.IGNORE_CASE)

    /** The instruction text that teaches the format. Part of the sent prompt. */
    const val INSTRUCTIONS: String =
        "To inspect the project, answer with one or more tool blocks and nothing else:\n" +
            "<<<CODEC_TOOL name=\"read_file\">>>\npath: relative/path.ext\nstart: 1\nend: 60\n<<<END_CODEC_TOOL>>>\n" +
            "Tools: list_files(path?, ext?), search_project(query, max?), read_file(path, start?, end?), read_files(paths), " +
            "find_files(pattern, max?), outline_file(path), read_run_output(lines?), request_run(target?).\n" +
            "read_files reads several files in one round trip — paths: a.kt, b.kt:10-40, c.kt (comma-separated, each with an optional :start-end). " +
            "Every path is checked on its own, so one refused path never blocks the others.\n" +
            "find_files matches project paths with * (any run of characters) and ? (one character): *.md matches by file name anywhere, " +
            "src/*.kt matches a whole relative path.\n" +
            "outline_file lists the definitions (functions, classes, headings) of one file with their line numbers — call it before " +
            "reading a long file, then read only the range you need.\n" +
            "read_run_output returns the tail of the build/run output that was on screen when this task started — read it to fix a " +
            "compile error, and never invent output that is not there.\n" +
            "All paths are relative to the project root. When you have enough information, answer normally with no tool block."

    /**
     * Reads [answer] for tool blocks. Prose outside blocks is kept (it is what
     * the timeline shows and what remains if the task ends), and the block
     * bodies are parsed as `key: value` lines — a key may not repeat, and a
     * line without a colon is malformed.
     */
    fun parse(answer: String): AiToolParse {
        val calls = mutableListOf<AiToolRequest>()
        val prose = StringBuilder()
        var at = 0
        while (at < answer.length) {
            // Phase 94 — the reader is as forgiving about the model's spacing and
            // case as the edit parser is (Phase 93b): `<<<CODEC_TOOL`, `<<< CODEC_TOOL`,
            // `<<<codec_tool` and `<<<END_CODEC_TOOL >>>` all read the same. The
            // FORMAT is wide, the PERMISSION is not: nothing here decides whether a
            // call may run — the policy does, and it never loosened.
            val found = OPEN_MARKER.find(answer, at)
            if (found == null) {
                prose.append(answer, at, answer.length)
                break
            }
            val open = found.range.first
            prose.append(answer, at, open)
            val nameStart = found.range.last + 1
            val bodyStart = answer.indexOf(">>>", nameStart)
            if (bodyStart < 0) return AiToolParse.Malformed(prose.toString().trim(), "a tool block was never closed", calls.toList())
            val header = answer.substring(nameStart, bodyStart).trim()
            val name = nameValue(header)
            val endMarker = CLOSE_MARKER.find(answer, bodyStart + 3)
            if (endMarker == null) return AiToolParse.Malformed(prose.toString().trim(), "a tool block is missing $CLOSE", calls.toList())
            val end = endMarker.range.first
            val body = answer.substring(bodyStart + 3, end)
            val args = LinkedHashMap<String, String>()
            for (raw in body.lines()) {
                val line = raw.trim()
                if (line.isEmpty()) continue
                val colon = line.indexOf(':')
                if (colon <= 0) {
                    return AiToolParse.Malformed(prose.toString().trim(), "a tool argument line has no name: $line", calls.toList())
                }
                val key = line.substring(0, colon).trim().lowercase()
                val value = line.substring(colon + 1).trim()
                if (key in args) return AiToolParse.Malformed(prose.toString().trim(), "the argument $key was given twice", calls.toList())
                args[key] = value
            }
            calls += AiToolRequest(name ?: "", args)
            at = endMarker.range.last + 1
        }
        return AiToolParse.Calls(prose.toString().trim(), calls)
    }

    /**
     * Phase 84 (fix 4 / **S12**) — the answer text with every tool block removed,
     * so a stop never leaves a raw `<<<CODEC_TOOL …>>>` on screen as the answer.
     * Complete blocks are dropped; text from an unclosed/malformed block onward is
     * cut. Returns prose only (possibly blank).
     */
    fun proseOnly(answer: String): String = when (val parsed = parse(answer)) {
        is AiToolParse.Calls -> parsed.prose
        is AiToolParse.Malformed -> parsed.prose
    }

    /**
     * Phase 94 — does [text] contain a tool block at all, in any spelling this
     * parser accepts? One rule for the review's "markup is shown, never run"
     * branch, so widening the reader cannot leave that branch behind.
     */
    fun containsBlock(text: String): Boolean =
        OPEN_MARKER.containsMatchIn(text) || CLOSE_MARKER.containsMatchIn(text) ||
            BLOCK_WORD.containsMatchIn(text)

    /** `name="read_file"`, `name = read_file`, `name: read_file` or a bare name — any spacing/case. */
    private fun nameValue(header: String): String? {
        val quoted = Regex("name\\s*[=:]?\\s*\"([^\"]*)\"").find(header)?.groupValues?.get(1)
        if (quoted != null) return quoted.trim().ifEmpty { null }
        val stripped = Regex("^name\\s*[=:]?\\s*", RegexOption.IGNORE_CASE).find(header)?.let {
            header.removeRange(it.range)
        } ?: header
        return stripped.trim().trim('"').ifEmpty { null }
    }

    /**
     * The same line for a **validated** call (the timeline shows what really
     * ran, not what was asked for), with the normalized path and clamped range.
     */
    fun describeCall(call: AiToolCall): String = when (call.name) {
        AiToolName.READ_FILE -> "read_file ${call.path}" +
            (if (call.start != null && call.end != null) " (lines ${call.start}-${call.end})" else "")
        AiToolName.READ_FILES -> "read_files " + (call.reads?.joinToString(", ") { it.path }.orEmpty())
        AiToolName.SEARCH_PROJECT -> "search_project \"${call.query}\""
        AiToolName.LIST_FILES -> "list_files" +
            (call.path?.let { " $it/" } ?: "") + (call.ext?.let { " *.$it" } ?: "")
        AiToolName.FIND_FILES -> "find_files \"${call.pattern}\""
        AiToolName.OUTLINE_FILE -> "outline_file ${call.path}"
        AiToolName.READ_RUN_OUTPUT -> "read_run_output" +
            (call.lines?.let { " (last $it lines)" } ?: "")
        AiToolName.REQUEST_RUN -> "request_run" + (call.path?.let { " $it" } ?: "")
    }

    /** One line for the timeline: what was asked, never longer than the wire name. */
    fun describe(request: AiToolRequest): String = when (val n = request.name) {
        AiToolName.READ_FILE -> {
            val path = request.args["path"].orEmpty()
            val start = request.args["start"]
            val end = request.args["end"]
            val range = if (start != null || end != null) " (lines ${start ?: "1"}-${end ?: "?"})" else ""
            "read_file $path$range"
        }
        AiToolName.READ_FILES -> "read_files " + request.args["paths"].orEmpty()
        AiToolName.SEARCH_PROJECT -> "search_project \"${request.args["query"].orEmpty()}\""
        AiToolName.LIST_FILES -> {
            val path = request.args["path"]
            val ext = request.args["ext"]
            "list_files" + (if (path != null) " $path/" else "") + (if (ext != null) " *.$ext" else "")
        }
        AiToolName.FIND_FILES -> "find_files \"${request.args["pattern"].orEmpty()}\""
        AiToolName.OUTLINE_FILE -> "outline_file ${request.args["path"].orEmpty()}"
        AiToolName.READ_RUN_OUTPUT -> "read_run_output" +
            (request.args["lines"]?.let { " (last $it lines)" } ?: "")
        AiToolName.REQUEST_RUN -> "request_run" + request.args["target"]?.let { " $it" }.orEmpty()
        null -> "unknown tool \"${request.rawName}\""
    }
}

/** What the policy is allowed to know about the project. Pure data. */
data class AiToolProjectView(
    /** Every existing code/text path the Level 2 walk admitted. */
    val existingPaths: Set<String>,
    /** Model turns still available; a tool call costs none, but request_run does. */
    val runsRemaining: Int
)

/** A validated call. Only [AiToolVerdict.Allowed] may reach [AiToolRunner]. */
data class AiToolCall(
    val name: AiToolName,
    val rawName: String,
    val path: String? = null,
    val start: Int? = null,
    val end: Int? = null,
    val query: String? = null,
    val max: Int? = null,
    val ext: String? = null,
    /** Phase 94 — the glob of a `find_files` call. */
    val pattern: String? = null,
    /** Phase 94 — how many tail lines a `read_run_output` call asked for. */
    val lines: Int? = null,
    /** Phase 85 (Level 8, item 3): the per-file specs of a `read_files` batch. */
    val reads: List<ReadSpec>? = null
)

/**
 * One file inside a `read_files` batch: a normalized path and the line range to
 * deliver. Each spec is validated and read independently, so one refused path
 * never blocks its siblings (**S5**).
 */
data class ReadSpec(val path: String, val start: Int, val end: Int)

sealed class AiToolVerdict {
    data class Allowed(val call: AiToolCall) : AiToolVerdict()
    data class Denied(val request: AiToolRequest, val reason: String) : AiToolVerdict()
}

/**
 * The argument limits. All of them are shown to the user in the timeline and
 * in the task preview, and all of them are enforced here — the model is told
 * the format, never the permissions (`04_AGENT_TOOLS_AND_RUN_LOOP.md`: *"The
 * model never decides whether it is authorized"*).
 */
object AiToolLimits {
    /** Lines one `read_file` may return. */
    const val MAX_READ_LINES = 400

    /** Files one `read_files` batch may name (bounded, parallel read-only IO). */
    const val MAX_BATCH_READS = 8

    /** Characters one tool result may carry into the next request (owner cap). */
    const val MAX_RESULT_CHARS = 8_000

    /** Paths one `list_files` may name. */
    const val MAX_LIST_ENTRIES = 200

    /** Matches one `search_project` may return. */
    const val MAX_SEARCH_HITS = 40

    /** Files one `search_project` may open (bounded work, like ProjectSearch). */
    const val MAX_SEARCH_FILES = 300

    const val MAX_QUERY_CHARS = 120

    /** A search term shorter than this matches half the project and helps nobody. */
    const val MIN_QUERY_CHARS = 2

    /** Characters a `find_files` pattern may carry. */
    const val MAX_PATTERN_CHARS = 80

    /** Definitions one `outline_file` may return. */
    const val MAX_OUTLINE_ROWS = AiOutline.MAX_ROWS

    /** Output lines one `read_run_output` may return, and its default. */
    const val MAX_OUTPUT_LINES = 200
    const val DEFAULT_OUTPUT_LINES = 60

    /**
     * Output lines kept in a task's frozen snapshot at Send. The panel can hold
     * far more; a task carries a bounded tail, because the model's result cap
     * would cut it anyway and the snapshot lives in memory for the whole task.
     */
    const val MAX_OUTPUT_SNAPSHOT_LINES = 400
}

object AiToolPolicy {

    /**
     * Turns one raw request into either a typed, safe call or a refusal with a
     * reason the model can act on. Nothing here touches the disk; existence is
     * checked against [view], which the caller built from the same walk the
     * map came from.
     */
    /**
     * [readWindow] is the Level 10 *read window* option: how many lines one read
     * may return. It is clamped here and can never exceed
     * [AiToolLimits.MAX_READ_LINES], so **S9** holds even if a stored value is
     * tampered with. The same value must reach [AiToolRunner.execute] and
     * [AiTaskMemory.prepareRead], or the runner would refuse what the model was
     * told it could ask for.
     */
    fun validate(
        request: AiToolRequest,
        view: AiToolProjectView,
        readWindow: Int = AiToolLimits.MAX_READ_LINES
    ): AiToolVerdict {
        val name = request.name ?: return AiToolVerdict.Denied(
            request,
            // Phase 93c — the owner's *"why can't the agent write code?"*: a
            // coding model very naturally calls `write_file`/`edit_file`/
            // `apply_patch`, and the old sentence stopped at "unknown tool" plus a
            // list. The list is true and useless — the model then answers in
            // prose and no file changes. The way a write *can* happen is named
            // here instead, and this text is exactly what the model reads back as
            // the tool result, so it can correct itself inside the same task.
            "unknown tool \"${request.rawName}\"; this agent has no write tool and no command tool, and " +
                "the tools it does have are " + AiToolName.entries.joinToString(", ") { it.wire } + ". " +
                "To change a file, do not call a tool: answer with a " +
                "${AiEditProposalParser.OPEN_TAG_PREFIX} path=\"…\" op=\"modify|create|delete\">>> block " +
                "(SEARCH/REPLACE for part of a file, the full content for a new one) — CodeC turns it " +
                "into a diff the user reviews and applies."
        )
        val allowedKeys = when (name) {
            AiToolName.LIST_FILES -> setOf("path", "ext")
            AiToolName.SEARCH_PROJECT -> setOf("query", "max")
            AiToolName.READ_FILE -> setOf("path", "start", "end")
            AiToolName.READ_FILES -> setOf("paths")
            AiToolName.FIND_FILES -> setOf("pattern", "max")
            AiToolName.OUTLINE_FILE -> setOf("path")
            AiToolName.READ_RUN_OUTPUT -> setOf("lines")
            AiToolName.REQUEST_RUN -> setOf("target")
        }
        val extra = request.args.keys - allowedKeys
        if (extra.isNotEmpty()) {
            return AiToolVerdict.Denied(request, "${name.wire} does not take ${extra.sorted().joinToString(", ")}")
        }
        // S9: clamp once, here, and pass the same value to both readers.
        val window = AiOptionsPolicy.clampReadWindow(readWindow).coerceAtMost(AiToolLimits.MAX_READ_LINES)
        return when (name) {
            AiToolName.READ_FILE -> validateRead(request, view, window)
            AiToolName.READ_FILES -> validateReadFiles(request, window)
            AiToolName.SEARCH_PROJECT -> validateSearch(request)
            AiToolName.LIST_FILES -> validateList(request, view)
            AiToolName.FIND_FILES -> validateFind(request)
            AiToolName.OUTLINE_FILE -> validateOutline(request, view)
            AiToolName.READ_RUN_OUTPUT -> validateOutput(request)
            AiToolName.REQUEST_RUN -> validateRun(request, view)
        }
    }

    /**
     * Phase 85 (Level 8, item 3) — validate a `read_files` batch. Only the
     * STRUCTURE is checked here (a non-empty list within [AiToolLimits.MAX_BATCH_READS],
     * each entry a path with an optional `:start-end`); per-path security and
     * existence are re-applied to EVERY file at run time, so one refused path
     * yields a per-file refusal while its siblings still read (**S5**). The model
     * is told the format, never the permissions.
     */
    private fun validateReadFiles(request: AiToolRequest, window: Int): AiToolVerdict {
        val raw = request.args["paths"]?.takeIf { it.isNotBlank() }
            ?: return AiToolVerdict.Denied(request, "read_files needs a paths list")
        val entries = raw.split(',').map { it.trim() }.filter { it.isNotEmpty() }
        if (entries.isEmpty()) return AiToolVerdict.Denied(request, "read_files needs at least one path")
        if (entries.size > AiToolLimits.MAX_BATCH_READS) {
            return AiToolVerdict.Denied(
                request, "read_files may read at most ${AiToolLimits.MAX_BATCH_READS} files at once"
            )
        }
        val specs = mutableListOf<ReadSpec>()
        for (entry in entries) {
            val ranged = Regex("^(.*?):(\\d+)-(\\d+)$").find(entry)
            val pathPart: String
            val start: Int
            val end: Int
            if (ranged != null) {
                pathPart = ranged.groupValues[1].trim()
                start = ranged.groupValues[2].toIntOrNull() ?: 1
                end = ranged.groupValues[3].toIntOrNull() ?: start
            } else {
                pathPart = entry
                start = 1
                end = window
            }
            if (pathPart.isEmpty()) return AiToolVerdict.Denied(request, "read_files has an empty path in \"$entry\"")
            if (start < 1 || end < start) {
                return AiToolVerdict.Denied(request, "read_files range for \"$pathPart\" must be start >= 1 and end >= start")
            }
            val cappedEnd = if (end - start + 1 > window) start + window - 1 else end
            specs += ReadSpec(pathPart, start, cappedEnd)
        }
        return AiToolVerdict.Allowed(
            AiToolCall(name = AiToolName.READ_FILES, rawName = request.rawName, reads = specs)
        )
    }

    private fun validateRead(request: AiToolRequest, view: AiToolProjectView, window: Int): AiToolVerdict {
        val raw = request.args["path"]?.takeIf { it.isNotBlank() }
            ?: return AiToolVerdict.Denied(request, "read_file needs a path")
        val path = AiEditProposalParser.validateTargetPath(raw)
            ?: return AiToolVerdict.Denied(request, "read_file refused the path \"$raw\": it is outside the project or credential-shaped")
        if (view.existingPaths.none { AiProjectFiles.samePath(it, path) }) {
            return AiToolVerdict.Denied(request, "$path is not a code or text file in this project; use list_files")
        }
        val start = numberOrNull(request.args["start"]) ?: if (request.args.containsKey("start")) {
            return AiToolVerdict.Denied(request, "read_file start must be a whole number")
        } else 1
        val end = numberOrNull(request.args["end"]) ?: if (request.args.containsKey("end")) {
            return AiToolVerdict.Denied(request, "read_file end must be a whole number")
        } else start + window - 1
        if (start < 1 || end < start) return AiToolVerdict.Denied(request, "read_file range must be start >= 1 and end >= start")
        if (end - start + 1 > window) {
            return AiToolVerdict.Denied(request, "read_file may read at most $window lines at once")
        }
        return AiToolVerdict.Allowed(
            AiToolCall(name = AiToolName.READ_FILE, rawName = request.rawName, path = path, start = start, end = end)
        )
    }

    private fun validateSearch(request: AiToolRequest): AiToolVerdict {
        val query = request.args["query"]?.trim().orEmpty()
        if (query.length < AiToolLimits.MIN_QUERY_CHARS) {
            return AiToolVerdict.Denied(request, "search_project needs a query of at least ${AiToolLimits.MIN_QUERY_CHARS} characters")
        }
        if (query.length > AiToolLimits.MAX_QUERY_CHARS) {
            return AiToolVerdict.Denied(request, "search_project query is longer than ${AiToolLimits.MAX_QUERY_CHARS} characters")
        }
        val max = numberOrNull(request.args["max"]) ?: if (request.args.containsKey("max")) {
            return AiToolVerdict.Denied(request, "search_project max must be a whole number")
        } else AiToolLimits.MAX_SEARCH_HITS
        if (max < 1) return AiToolVerdict.Denied(request, "search_project max must be at least 1")
        return AiToolVerdict.Allowed(
            AiToolCall(
                name = AiToolName.SEARCH_PROJECT,
                rawName = request.rawName,
                query = query,
                max = max.coerceAtMost(AiToolLimits.MAX_SEARCH_HITS)
            )
        )
    }

    private fun validateList(request: AiToolRequest, view: AiToolProjectView): AiToolVerdict {
        val rawPath = request.args["path"]?.trim()?.takeIf { it.isNotEmpty() && it != "." && it != "./" }
        val path = if (rawPath == null) null else safeDirectoryPath(rawPath)
        if (rawPath != null && path == null) {
            return AiToolVerdict.Denied(request, "list_files refused the path \"$rawPath\"")
        }
        if (path != null && view.existingPaths.none { it.startsWith("$path/") }) {
            return AiToolVerdict.Denied(request, "$path/ holds no code or text file in this project")
        }
        val ext = request.args["ext"]?.trim()?.lowercase()?.removePrefix(".")?.takeIf { it.isNotEmpty() }
        if (ext != null && !Regex("^[a-z0-9]{1,8}$").matches(ext)) {
            return AiToolVerdict.Denied(request, "list_files ext must be a plain extension such as c or py")
        }
        return AiToolVerdict.Allowed(
            AiToolCall(name = AiToolName.LIST_FILES, rawName = request.rawName, path = path, ext = ext)
        )
    }

    /**
     * Phase 94 — `find_files(pattern, max?)`: a glob over the walk's admitted
     * paths. The pattern is a **name pattern** (`*.md`, `README*`) or a full
     * relative one (`src/*.kt`, `**/build.gradle.kts`). It can never name a
     * path the walk refused, because the list it filters is the walk's own
     * output — but it is still checked like a path ([safeDirectoryPath]'s
     * rules): no absolute, no `..`, no control characters, no `.git`/excluded
     * directory, and a credential-shaped pattern names nothing.
     */
    private fun validateFind(request: AiToolRequest): AiToolVerdict {
        val raw = request.args["pattern"]?.trim().orEmpty()
        if (raw.isEmpty()) {
            return AiToolVerdict.Denied(request, "find_files needs a pattern such as *.md or src/*.kt")
        }
        if (raw.length > AiToolLimits.MAX_PATTERN_CHARS) {
            return AiToolVerdict.Denied(
                request, "find_files pattern is longer than ${AiToolLimits.MAX_PATTERN_CHARS} characters"
            )
        }
        val pattern = raw.replace('\\', '/')
        if (pattern.startsWith("/") || pattern.startsWith("~") || DRIVE_PREFIX.containsMatchIn(pattern)) {
            return AiToolVerdict.Denied(request, "find_files refused the pattern \"$raw\"")
        }
        if (pattern.split('/').any { it == ".." }) {
            return AiToolVerdict.Denied(request, "find_files refused the pattern \"$raw\"")
        }
        if (pattern.any { it.isISOControl() || it == '\u0000' }) {
            return AiToolVerdict.Denied(request, "find_files refused the pattern \"$raw\"")
        }
        if (!pattern.all { it.isLetterOrDigit() || it in "*?._-/@ " }) {
            return AiToolVerdict.Denied(request, "find_files takes a plain glob (* and ? only), not \"$raw\"")
        }
        val max = numberOrNull(request.args["max"])
        if (request.args["max"] != null && max == null) {
            return AiToolVerdict.Denied(request, "find_files max must be a whole number")
        }
        if (max != null && max <= 0) {
            return AiToolVerdict.Denied(request, "find_files max must be at least 1")
        }
        return AiToolVerdict.Allowed(
            AiToolCall(
                name = AiToolName.FIND_FILES,
                rawName = request.rawName,
                pattern = pattern,
                max = max?.coerceAtMost(AiToolLimits.MAX_LIST_ENTRIES)
            )
        )
    }

    /**
     * Phase 94 — `outline_file(path)`: the same path rules `read_file` applies
     * (inside the project, admitted by the walk, text, not credential-shaped),
     * because it reads the same bytes — it just answers with the structure
     * instead of 400 lines.
     */
    private fun validateOutline(request: AiToolRequest, view: AiToolProjectView): AiToolVerdict {
        val raw = request.args["path"]?.trim().orEmpty()
        if (raw.isEmpty()) return AiToolVerdict.Denied(request, "outline_file needs a path")
        val name = raw.replace('\\', '/').substringAfterLast('/')
        if (AiProjectFiles.isSecretLike(name)) {
            return AiToolVerdict.Denied(request, "outline_file never opens credential-shaped files")
        }
        val path = AiEditProposalParser.validateTargetPath(raw)
            ?: return AiToolVerdict.Denied(request, "outline_file refused the path \"$raw\"")
        if (view.existingPaths.none { AiProjectFiles.samePath(it, path) }) {
            return AiToolVerdict.Denied(request, "$path is not a code or text file in this project")
        }
        return AiToolVerdict.Allowed(
            AiToolCall(name = AiToolName.OUTLINE_FILE, rawName = request.rawName, path = path)
        )
    }

    /**
     * Phase 94 — `read_run_output(lines?)`. The snapshot is bounded by the app
     * ([AiToolLimits.MAX_OUTPUT_SNAPSHOT_LINES]); the caller may ask for fewer
     * tail lines, never more than [AiToolLimits.MAX_OUTPUT_LINES]. No path, no
     * command: this tool cannot make anything run, it only reads what already
     * ran.
     */
    private fun validateOutput(request: AiToolRequest): AiToolVerdict {
        val raw = request.args["lines"]
        val lines = numberOrNull(raw)
        if (raw != null && lines == null) {
            return AiToolVerdict.Denied(request, "read_run_output lines must be a whole number")
        }
        // A count is a request, not a permission: 0 and negatives clamp to one
        // line rather than failing the round trip (the tool's whole point is
        // that asking for output never fails).
        val wanted = lines?.coerceAtLeast(1)
        return AiToolVerdict.Allowed(
            AiToolCall(
                name = AiToolName.READ_RUN_OUTPUT,
                rawName = request.rawName,
                lines = wanted?.coerceAtMost(AiToolLimits.MAX_OUTPUT_LINES)
            )
        )
    }

    private fun validateRun(request: AiToolRequest, view: AiToolProjectView): AiToolVerdict {
        if (view.runsRemaining <= 0) {
            return AiToolVerdict.Denied(request, "the run budget for this task is used up; answer with what is known")
        }
        val raw = request.args["target"]?.trim()?.takeIf { it.isNotEmpty() }
        val target = if (raw == null) null else AiEditProposalParser.validateTargetPath(raw)
        if (raw != null && target == null) {
            return AiToolVerdict.Denied(request, "request_run refused the target \"$raw\"")
        }
        if (target != null && view.existingPaths.none { AiProjectFiles.samePath(it, target) }) {
            return AiToolVerdict.Denied(request, "$target is not a code or text file in this project")
        }
        // A run is a request, never an action: the user taps Run on the card.
        return AiToolVerdict.Allowed(AiToolCall(name = AiToolName.REQUEST_RUN, rawName = request.rawName, path = target))
    }

    private fun numberOrNull(s: String?): Int? = s?.trim()?.takeIf { it.isNotEmpty() }?.toIntOrNull()

    /**
     * A project-relative **directory** path, or null. The same rules
     * [AiEditProposalParser.validateTargetPathWithReason] applies to files —
     * no absolute path, no `..`, no control characters, no excluded directory
     * (which is what refuses `.git`, `build`, `node_modules` and every hidden
     * folder), no credential-shaped segment — but the last segment is a
     * directory name, so the text-file check does not apply to it.
     */
    private val DRIVE_PREFIX = Regex("^[A-Za-z]:")

    fun safeDirectoryPath(rawPath: String): String? {
        var norm = rawPath.trim().replace('\\', '/').trimEnd('/')
        while (norm.startsWith("./")) norm = norm.removePrefix("./")
        if (norm.isEmpty() || norm == ".") return null
        if (norm.startsWith("/") || norm.startsWith("~") || DRIVE_PREFIX.containsMatchIn(norm)) return null
        val parts = norm.split('/')
        if (parts.any { it.isEmpty() || it == "." || it == ".." }) return null
        if (parts.any { part -> part.any { it.isISOControl() || it == '\u0000' } }) return null
        if (parts.any { AiProjectFiles.isExcludedDirectory(it) }) return null
        if (AiProjectFiles.isSecretLike(parts.last())) return null
        return parts.joinToString("/")
    }
}
