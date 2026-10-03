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

    /** The instruction text that teaches the format. Part of the sent prompt. */
    const val INSTRUCTIONS: String =
        "To inspect the project, answer with one or more tool blocks and nothing else:\n" +
            "<<<CODEC_TOOL name=\"read_file\">>>\npath: relative/path.ext\nstart: 1\nend: 60\n<<<END_CODEC_TOOL>>>\n" +
            "Tools: list_files(path?, ext?), search_project(query, max?), read_file(path, start?, end?), read_files(paths), request_run(target?).\n" +
            "read_files reads several files in one round trip — paths: a.kt, b.kt:10-40, c.kt (comma-separated, each with an optional :start-end). " +
            "Every path is checked on its own, so one refused path never blocks the others.\n" +
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
            val open = answer.indexOf(OPEN, at)
            if (open < 0) {
                prose.append(answer, at, answer.length)
                break
            }
            prose.append(answer, at, open)
            val nameStart = open + OPEN.length
            val bodyStart = answer.indexOf(">>>", nameStart)
            if (bodyStart < 0) return AiToolParse.Malformed(prose.toString().trim(), "a tool block was never closed", calls.toList())
            val header = answer.substring(nameStart, bodyStart).trim()
            val name = nameValue(header)
            val end = answer.indexOf(CLOSE, bodyStart + 3)
            if (end < 0) return AiToolParse.Malformed(prose.toString().trim(), "a tool block is missing $CLOSE", calls.toList())
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
            at = end + CLOSE.length
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

    /** `name="read_file"` (or bare `read_file`) from a block header. */
    private fun nameValue(header: String): String? {
        val quoted = Regex("name\\s*=\\s*\"([^\"]*)\"").find(header)?.groupValues?.get(1)
        if (quoted != null) return quoted.trim().ifEmpty { null }
        val bare = header.removePrefix("name=").trim().trim('"')
        return bare.ifEmpty { null }
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
}

object AiToolPolicy {

    /**
     * Turns one raw request into either a typed, safe call or a refusal with a
     * reason the model can act on. Nothing here touches the disk; existence is
     * checked against [view], which the caller built from the same walk the
     * map came from.
     */
    fun validate(request: AiToolRequest, view: AiToolProjectView): AiToolVerdict {
        val name = request.name ?: return AiToolVerdict.Denied(
            request, "unknown tool \"${request.rawName}\"; the tools are " +
                AiToolName.entries.joinToString(", ") { it.wire }
        )
        val allowedKeys = when (name) {
            AiToolName.LIST_FILES -> setOf("path", "ext")
            AiToolName.SEARCH_PROJECT -> setOf("query", "max")
            AiToolName.READ_FILE -> setOf("path", "start", "end")
            AiToolName.READ_FILES -> setOf("paths")
            AiToolName.REQUEST_RUN -> setOf("target")
        }
        val extra = request.args.keys - allowedKeys
        if (extra.isNotEmpty()) {
            return AiToolVerdict.Denied(request, "${name.wire} does not take ${extra.sorted().joinToString(", ")}")
        }
        return when (name) {
            AiToolName.READ_FILE -> validateRead(request, view)
            AiToolName.READ_FILES -> validateReadFiles(request)
            AiToolName.SEARCH_PROJECT -> validateSearch(request)
            AiToolName.LIST_FILES -> validateList(request, view)
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
    private fun validateReadFiles(request: AiToolRequest): AiToolVerdict {
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
                end = AiToolLimits.MAX_READ_LINES
            }
            if (pathPart.isEmpty()) return AiToolVerdict.Denied(request, "read_files has an empty path in \"$entry\"")
            if (start < 1 || end < start) {
                return AiToolVerdict.Denied(request, "read_files range for \"$pathPart\" must be start >= 1 and end >= start")
            }
            val cappedEnd = if (end - start + 1 > AiToolLimits.MAX_READ_LINES) start + AiToolLimits.MAX_READ_LINES - 1 else end
            specs += ReadSpec(pathPart, start, cappedEnd)
        }
        return AiToolVerdict.Allowed(
            AiToolCall(name = AiToolName.READ_FILES, rawName = request.rawName, reads = specs)
        )
    }

    private fun validateRead(request: AiToolRequest, view: AiToolProjectView): AiToolVerdict {
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
        } else start + AiToolLimits.MAX_READ_LINES - 1
        if (start < 1 || end < start) return AiToolVerdict.Denied(request, "read_file range must be start >= 1 and end >= start")
        if (end - start + 1 > AiToolLimits.MAX_READ_LINES) {
            return AiToolVerdict.Denied(request, "read_file may read at most ${AiToolLimits.MAX_READ_LINES} lines at once")
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
