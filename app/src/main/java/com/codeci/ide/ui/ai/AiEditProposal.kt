package com.codeci.ide.ui.ai

import com.codeci.ide.ui.projects.DiffEngine
import com.codeci.ide.ui.projects.DiffLine
import com.codeci.ide.ui.projects.DiffOp

/**
 * Phase 79 (AI Level 3) — structured edit proposals, path/target validation,
 * baseline staleness checks, and local unified diff computation.
 *
 * Pure Kotlin: strings, numbers, and collections only — no `java.io`, no
 * Android imports, no file writes. Host-tested by `AiEditProposalTest`.
 *
 * ## Binding Level 3 rules enforced here (`03_EDIT_REVIEW_AND_UNDO.md`)
 *
 *  - **Never treat model prose as a patch.** Only explicit
 *    `<<<CODEC_EDIT path="…" op="modify|create|delete">>> … <<<END_CODEC_EDIT>>>`
 *    blocks are parsed; anything outside them is explanatory prose.
 *  - **Validate every path.** Rejects blank paths, absolute paths (`/`, `~`,
 *    drive letters), `.` and `..` traversal segments, excluded directories
 *    ([AiProjectFiles.isExcludedDirectory], `.git`, `.codec`), secret-like
 *    files ([AiProjectFiles.isSecretLike]), and non-text files
 *    (![AiProjectFiles.isTextFile]).
 *  - **Protect truncated files.** When a file was cut to fit the context
 *    budget ([AiFileBaseline.cut]), a full-file `modify` is rejected so the
 *    unsent tail of the file can never be silently dropped; targeted
 *    `<<<SEARCH>>> … <<<REPLACE>>> … <<<END_SEARCH>>>` blocks are required.
 *  - **Compute the diff locally.** Diffs are computed from `(oldContent,
 *    newContent)` via [DiffEngine.compute], never trusted from model text.
 *  - **Detect stale baselines.** [AiEditProposal.checkStale] compares each
 *    selected file's captured baseline against the current editor/disk state
 *    before anything is applied.
 */
enum class AiEditOp {
    MODIFY,
    CREATE,
    DELETE
}

/**
 * The state of one project file when the edit-proposal prompt was built.
 * [content] is normalized to LF (`\n`).
 */
data class AiFileBaseline(
    val path: String,
    val exists: Boolean,
    val content: String,
    /** True when only a prefix of the file fitted into the outgoing prompt. */
    val cut: Boolean = false,
    /** True when [content] came from an unsaved editor buffer. */
    val fromBuffer: Boolean = false
)

/**
 * Current state of a file (from the live editor buffer when dirty, else disk)
 * used to check whether the baseline has drifted before applying edits.
 */
data class AiCurrentFileState(
    val path: String,
    val exists: Boolean,
    val content: String
)

/** One validated file operation inside a proposal, with its local unified diff. */
data class AiProposedFileEdit(
    val path: String,
    val op: AiEditOp,
    val oldContent: String,
    val newContent: String,
    val unifiedDiff: String,
    val addedLines: Int,
    val removedLines: Int,
    /** Per-file checkbox state in the diff review UI (owner Q3: per-file selection). */
    val selected: Boolean = true
)

/** A validated multi-file change set ready for user review. */
data class AiEditProposal(
    val prose: String,
    val files: List<AiProposedFileEdit>,
    val baselines: Map<String, AiFileBaseline>
) {
    val selectedFiles: List<AiProposedFileEdit> get() = files.filter { it.selected }
    val selectedCount: Int get() = files.count { it.selected }
    val totalAddedLines: Int get() = files.sumOf { it.addedLines }
    val totalRemovedLines: Int get() = files.sumOf { it.removedLines }
    val selectedAddedLines: Int get() = selectedFiles.sumOf { it.addedLines }
    val selectedRemovedLines: Int get() = selectedFiles.sumOf { it.removedLines }

    /** Flips the per-file review checkbox for [path]. */
    fun toggleFile(path: String): AiEditProposal = copy(
        files = files.map { f ->
            if (AiProjectFiles.samePath(f.path, path)) f.copy(selected = !f.selected) else f
        }
    )

    /** Checks or unchecks every file in the proposal. */
    fun selectAll(selected: Boolean): AiEditProposal = copy(
        files = files.map { it.copy(selected = selected) }
    )

    /**
     * Returns the paths of any **selected** target files whose current state
     * differs from the baseline captured when the proposal was built. An empty
     * list means the baseline is still fresh and safe to apply.
     */
    fun checkStale(currentStates: Map<String, AiCurrentFileState>): List<String> {
        val stale = mutableListOf<String>()
        for (edit in selectedFiles) {
            val current = currentStates[edit.path]
            val baseline = baselines[edit.path]
            when (edit.op) {
                AiEditOp.CREATE -> {
                    if (current != null && current.exists) {
                        stale += edit.path
                    }
                }
                AiEditOp.MODIFY, AiEditOp.DELETE -> {
                    if (baseline == null || !baseline.exists) {
                        stale += edit.path
                    } else if (current == null || !current.exists) {
                        stale += edit.path
                    } else {
                        val normBaseline = AiEditProposalParser.normalizeLf(baseline.content)
                        val normCurrent = AiEditProposalParser.normalizeLf(current.content)
                        if (normBaseline != normCurrent) {
                            stale += edit.path
                        }
                    }
                }
            }
        }
        return stale
    }
}

sealed class AiProposalResult {
    /** The reply is plain explanation prose with no edit blocks. */
    object NoProposal : AiProposalResult()

    /** Every edit block passed validation and has a locally computed diff. */
    data class Proposal(val proposal: AiEditProposal) : AiProposalResult()

    /** The reply attempted an edit block that failed syntax, path, or baseline validation. */
    data class Invalid(val reason: String, val prose: String = "") : AiProposalResult()
}

object AiEditProposalParser {

    /** Maximum number of files a single AI proposal may touch. */
    const val MAX_EDIT_FILES = 5

    /** Maximum resulting character length of any created or modified file. */
    const val MAX_EDIT_FILE_CHARS = 24_000

    const val OPEN_TAG_PREFIX = "<<<CODEC_EDIT"
    const val CLOSE_TAG = "<<<END_CODEC_EDIT>>>"
    const val SEARCH_TAG = "<<<SEARCH>>>"
    const val REPLACE_TAG = "<<<REPLACE>>>"
    const val END_SEARCH_TAG = "<<<END_SEARCH>>>"

    private val HEADER_ATTR = Regex("(path|op)\\s*=\\s*\"([^\"]*)\"")
    private val DRIVE_PREFIX = Regex("^[A-Za-z]:")

    fun normalizeLf(text: String): String =
        if (text.indexOf('\r') < 0) text else text.replace("\r\n", "\n").replace('\r', '\n')

    /**
     * Validates a project-relative target path. Returns `Pair(safePath, null)`
     * when valid, or `Pair(null, humanReason)` when rejected.
     */
    fun validateTargetPathWithReason(rawPath: String): Pair<String?, String?> {
        val trimmed = rawPath.trim()
        if (trimmed.isEmpty()) return null to "A proposed edit had an empty file path."
        val norm = trimmed.replace('\\', '/')
        if (norm.startsWith("/") || norm.startsWith("~") || DRIVE_PREFIX.containsMatchIn(norm)) {
            return null to "Rejected path outside the project: $trimmed"
        }
        val parts = norm.split('/')
        if (parts.any { it.isEmpty() || it == "." || it == ".." }) {
            return null to "Rejected unsafe relative path: $trimmed"
        }
        for (part in parts) {
            if (part.contains('\u0000') || part.any { it.isISOControl() }) {
                return null to "Rejected path with control characters: $trimmed"
            }
        }
        for (i in 0 until parts.size - 1) {
            val dir = parts[i]
            if (dir.equals(".codec", ignoreCase = true) ||
                dir.equals(".git", ignoreCase = true) ||
                AiProjectFiles.isExcludedDirectory(dir)
            ) {
                return null to "Rejected edit inside protected directory '$dir': $trimmed"
            }
        }
        val fileName = parts.last()
        if (AiProjectFiles.isSecretLike(fileName)) {
            return null to "Rejected edit to credential-like file: $fileName"
        }
        if (!AiProjectFiles.isTextFile(fileName)) {
            return null to "Rejected edit to non-code/non-text file: $fileName"
        }
        return parts.joinToString("/") to null
    }

    fun validateTargetPath(rawPath: String): String? = validateTargetPathWithReason(rawPath).first

    /**
     * Parses a model response into [AiProposalResult].
     *
     * [baselines] maps normalized project-relative paths to the [AiFileBaseline]
     * captured when the prompt was built; [existingPaths] contains all known
     * text file paths in the project so `CREATE` cannot overwrite an existing
     * file that was not in the top 5 sent files.
     */
    fun parse(
        answerText: String,
        baselines: Map<String, AiFileBaseline>,
        existingPaths: Set<String> = baselines.filterValues { it.exists }.keys
    ): AiProposalResult {
        val text = normalizeLf(answerText)
        val hasOpen = text.contains(OPEN_TAG_PREFIX)
        val hasClose = text.contains(CLOSE_TAG)
        val hasSearch = text.contains(SEARCH_TAG) || text.contains(REPLACE_TAG) || text.contains(END_SEARCH_TAG)
        if (!hasOpen && !hasClose && !hasSearch) {
            return AiProposalResult.NoProposal
        }
        if (!hasOpen) {
            return AiProposalResult.Invalid(
                reason = "The AI response contained an incomplete edit marker without $OPEN_TAG_PREFIX.",
                prose = text.trim()
            )
        }

        val proseBuilder = StringBuilder()
        val rawBlocks = mutableListOf<Pair<String, String>>()
        var cursor = 0
        while (cursor < text.length) {
            val openStart = text.indexOf(OPEN_TAG_PREFIX, cursor)
            if (openStart < 0) {
                val tail = text.substring(cursor)
                if (tail.contains(CLOSE_TAG)) {
                    return AiProposalResult.Invalid(
                        reason = "The AI response had an unmatched $CLOSE_TAG marker.",
                        prose = proseBuilder.toString().trim()
                    )
                }
                proseBuilder.append(tail)
                break
            }
            val before = text.substring(cursor, openStart)
            if (before.contains(CLOSE_TAG)) {
                return AiProposalResult.Invalid(
                    reason = "The AI response had an unmatched $CLOSE_TAG marker.",
                    prose = proseBuilder.toString().trim()
                )
            }
            proseBuilder.append(before)

            val openEnd = text.indexOf(">>>", openStart + OPEN_TAG_PREFIX.length)
            if (openEnd < 0) {
                return AiProposalResult.Invalid(
                    reason = "The AI response had an unclosed $OPEN_TAG_PREFIX header.",
                    prose = proseBuilder.toString().trim()
                )
            }
            val header = text.substring(openStart + OPEN_TAG_PREFIX.length, openEnd).trim()
            val bodyStart = openEnd + 3
            val closeStart = text.indexOf(CLOSE_TAG, bodyStart)
            val nextOpen = text.indexOf(OPEN_TAG_PREFIX, bodyStart)
            if (closeStart < 0 || (nextOpen in 0 until closeStart)) {
                return AiProposalResult.Invalid(
                    reason = "The AI response was cut off before $CLOSE_TAG.",
                    prose = proseBuilder.toString().trim()
                )
            }
            val body = text.substring(bodyStart, closeStart)
            rawBlocks += header to body
            cursor = closeStart + CLOSE_TAG.length
        }

        val cleanProse = stripEmptyFenceWrappers(proseBuilder.toString()).trim()
        if (rawBlocks.isEmpty()) {
            return AiProposalResult.NoProposal
        }
        if (rawBlocks.size > MAX_EDIT_FILES) {
            return AiProposalResult.Invalid(
                reason = "The proposal touches ${rawBlocks.size} files (maximum is $MAX_EDIT_FILES per task).",
                prose = cleanProse
            )
        }

        val normalizedExisting = existingPaths.mapTo(mutableSetOf()) {
            it.replace('\\', '/').removePrefix("./").trim('/')
        }
        val normalizedBaselines = LinkedHashMap<String, AiFileBaseline>()
        for ((k, v) in baselines) {
            val nk = k.replace('\\', '/').removePrefix("./").trim('/')
            normalizedBaselines[nk] = v.copy(path = nk, content = normalizeLf(v.content))
            if (v.exists) normalizedExisting += nk
        }

        val seenPaths = mutableSetOf<String>()
        val validatedEdits = mutableListOf<AiProposedFileEdit>()
        val proposalBaselines = LinkedHashMap<String, AiFileBaseline>()

        for ((header, rawBody) in rawBlocks) {
            val attrs = HEADER_ATTR.findAll(header).associate { it.groupValues[1] to it.groupValues[2] }
            val rawPath = attrs["path"]
                ?: return AiProposalResult.Invalid("An edit block is missing path=\"…\".", cleanProse)
            val rawOp = attrs["op"]
                ?: return AiProposalResult.Invalid("Edit block for '$rawPath' is missing op=\"modify|create|delete\".", cleanProse)

            val (safePath, pathError) = validateTargetPathWithReason(rawPath)
            if (safePath == null) {
                return AiProposalResult.Invalid(pathError ?: "Invalid path: $rawPath", cleanProse)
            }
            if (!seenPaths.add(safePath)) {
                return AiProposalResult.Invalid(
                    "The proposal lists '$safePath' more than once. Combine changes for a file into one block.",
                    cleanProse
                )
            }

            val op = when (rawOp.trim().lowercase()) {
                "modify", "edit", "update" -> AiEditOp.MODIFY
                "create", "add", "new" -> AiEditOp.CREATE
                "delete", "remove" -> AiEditOp.DELETE
                else -> return AiProposalResult.Invalid(
                    "Unknown edit operation '$rawOp' for $safePath (expected modify, create, or delete).",
                    cleanProse
                )
            }

            val trimmedBody = rawBody.removePrefix("\n").removeSuffix("\n")

            when (op) {
                AiEditOp.MODIFY -> {
                    val baseline = normalizedBaselines[safePath]
                    if (baseline == null || !baseline.exists) {
                        return AiProposalResult.Invalid(
                            "Cannot modify '$safePath' because its current contents were not in the shared project context.",
                            cleanProse
                        )
                    }
                    val oldContent = baseline.content
                    val newContentResult = applyModifyBody(safePath, oldContent, trimmedBody, baseline.cut)
                    val newContent = newContentResult.first
                        ?: return AiProposalResult.Invalid(newContentResult.second ?: "Invalid edit for $safePath", cleanProse)

                    if (newContent.contains('\u0000')) {
                        return AiProposalResult.Invalid("Rejected binary content in '$safePath'.", cleanProse)
                    }
                    if (newContent.length > MAX_EDIT_FILE_CHARS) {
                        return AiProposalResult.Invalid(
                            "Proposed content for '$safePath' exceeds the $MAX_EDIT_FILE_CHARS-character limit.",
                            cleanProse
                        )
                    }
                    if (newContent == oldContent) {
                        return AiProposalResult.Invalid(
                            "Proposed edit for '$safePath' makes no changes to the file.",
                            cleanProse
                        )
                    }
                    val diff = AiUnifiedDiff.build(safePath, AiEditOp.MODIFY, oldContent, newContent)
                    validatedEdits += AiProposedFileEdit(
                        path = safePath,
                        op = AiEditOp.MODIFY,
                        oldContent = oldContent,
                        newContent = newContent,
                        unifiedDiff = diff.text,
                        addedLines = diff.added,
                        removedLines = diff.removed
                    )
                    proposalBaselines[safePath] = baseline
                }

                AiEditOp.CREATE -> {
                    if (safePath in normalizedExisting) {
                        return AiProposalResult.Invalid(
                            "Cannot create '$safePath' because that file already exists in the project.",
                            cleanProse
                        )
                    }
                    if (trimmedBody.contains(SEARCH_TAG) ||
                        trimmedBody.contains(REPLACE_TAG) ||
                        trimmedBody.contains(END_SEARCH_TAG)
                    ) {
                        return AiProposalResult.Invalid(
                            "Create block for '$safePath' must contain full file content, not search/replace markers.",
                            cleanProse
                        )
                    }
                    val newContent = unwrapOptionalCodeFence(trimmedBody)
                    if (newContent.isBlank()) {
                        return AiProposalResult.Invalid(
                            "Cannot create '$safePath' with empty content.",
                            cleanProse
                        )
                    }
                    if (newContent.contains('\u0000')) {
                        return AiProposalResult.Invalid("Rejected binary content in '$safePath'.", cleanProse)
                    }
                    if (newContent.length > MAX_EDIT_FILE_CHARS) {
                        return AiProposalResult.Invalid(
                            "Proposed content for '$safePath' exceeds the $MAX_EDIT_FILE_CHARS-character limit.",
                            cleanProse
                        )
                    }
                    val diff = AiUnifiedDiff.build(safePath, AiEditOp.CREATE, "", newContent)
                    validatedEdits += AiProposedFileEdit(
                        path = safePath,
                        op = AiEditOp.CREATE,
                        oldContent = "",
                        newContent = newContent,
                        unifiedDiff = diff.text,
                        addedLines = diff.added,
                        removedLines = diff.removed
                    )
                    proposalBaselines[safePath] = AiFileBaseline(
                        path = safePath,
                        exists = false,
                        content = ""
                    )
                }

                AiEditOp.DELETE -> {
                    if (safePath !in normalizedExisting) {
                        return AiProposalResult.Invalid(
                            "Cannot delete '$safePath' because it does not exist in the project.",
                            cleanProse
                        )
                    }
                    if (trimmedBody.isNotBlank()) {
                        return AiProposalResult.Invalid(
                            "Delete block for '$safePath' must not contain replacement content.",
                            cleanProse
                        )
                    }
                    val baseline = normalizedBaselines[safePath]
                        ?: AiFileBaseline(path = safePath, exists = true, content = "")
                    val oldContent = baseline.content
                    val diff = AiUnifiedDiff.build(safePath, AiEditOp.DELETE, oldContent, "")
                    validatedEdits += AiProposedFileEdit(
                        path = safePath,
                        op = AiEditOp.DELETE,
                        oldContent = oldContent,
                        newContent = "",
                        unifiedDiff = diff.text,
                        addedLines = diff.added,
                        removedLines = diff.removed
                    )
                    proposalBaselines[safePath] = baseline
                }
            }
        }

        return AiProposalResult.Proposal(
            AiEditProposal(
                prose = cleanProse,
                files = validatedEdits,
                baselines = proposalBaselines
            )
        )
    }

    /**
     * Applies either targeted `<<<SEARCH>>> … <<<REPLACE>>> … <<<END_SEARCH>>>`
     * blocks or full-file replacement (when `!wasCut`) to [oldContent].
     */
    private fun applyModifyBody(
        path: String,
        oldContent: String,
        body: String,
        wasCut: Boolean
    ): Pair<String?, String?> {
        val hasSearch = body.contains(SEARCH_TAG)
        val hasReplace = body.contains(REPLACE_TAG)
        val hasEndSearch = body.contains(END_SEARCH_TAG)

        if (!hasSearch && !hasReplace && !hasEndSearch) {
            if (wasCut) {
                return null to "Cannot replace all of '$path' because only the first part of the file fitted in the prompt. Use $SEARCH_TAG / $REPLACE_TAG blocks instead."
            }
            val full = unwrapOptionalCodeFence(body)
            if (full.isBlank()) {
                return null to "Modify block for '$path' is empty. Use op=\"delete\" to delete a file."
            }
            return full to null
        }

        var cursor = 0
        var working = oldContent
        var hunkCount = 0

        while (cursor < body.length) {
            val sIdx = body.indexOf(SEARCH_TAG, cursor)
            if (sIdx < 0) {
                val trailing = body.substring(cursor)
                if (trailing.contains(REPLACE_TAG) || trailing.contains(END_SEARCH_TAG)) {
                    return null to "Malformed search/replace markers in '$path'."
                }
                if (trailing.isNotBlank()) {
                    return null to "Unexpected text outside $SEARCH_TAG … $END_SEARCH_TAG in '$path'."
                }
                break
            }
            val leading = body.substring(cursor, sIdx)
            if (leading.isNotBlank()) {
                return null to "Unexpected text before $SEARCH_TAG in '$path'."
            }
            val searchStart = sIdx + SEARCH_TAG.length
            val rIdx = body.indexOf(REPLACE_TAG, searchStart)
            val eIdx = if (rIdx >= 0) body.indexOf(END_SEARCH_TAG, rIdx + REPLACE_TAG.length) else -1
            val nextS = body.indexOf(SEARCH_TAG, searchStart)
            if (rIdx < 0 || eIdx < 0 || (nextS in 0 until eIdx)) {
                return null to "Unclosed $SEARCH_TAG / $REPLACE_TAG / $END_SEARCH_TAG block in '$path'."
            }

            val searchBlock = body.substring(searchStart, rIdx).removePrefix("\n").removeSuffix("\n")
            val replaceBlock = body.substring(rIdx + REPLACE_TAG.length, eIdx).removePrefix("\n").removeSuffix("\n")
            if (searchBlock.isEmpty()) {
                return null to "Empty $SEARCH_TAG block in '$path'."
            }
            val matchAt = working.indexOf(searchBlock)
            if (matchAt < 0) {
                return null to "The $SEARCH_TAG block for '$path' did not match the current file content."
            }
            working = working.substring(0, matchAt) +
                replaceBlock +
                working.substring(matchAt + searchBlock.length)
            hunkCount++
            cursor = eIdx + END_SEARCH_TAG.length
        }

        if (hunkCount == 0) {
            return null to "No valid $SEARCH_TAG block found in '$path'."
        }
        return working to null
    }

    private fun unwrapOptionalCodeFence(text: String): String {
        val t = text.trim()
        if (!t.startsWith("```") || !t.endsWith("```")) return text
        val firstNl = t.indexOf('\n')
        if (firstNl < 0) return text
        val inner = t.substring(firstNl + 1, t.length - 3)
        return inner.removeSuffix("\n")
    }

    private fun stripEmptyFenceWrappers(prose: String): String =
        prose.replace(Regex("```[a-zA-Z0-9_-]*\\s*```"), "")
}

/**
 * Pure local unified diff builder on top of [DiffEngine.compute].
 * Never trusts diff lines from the model.
 */
object AiUnifiedDiff {

    private const val CONTEXT_LINES = 2

    data class RenderedDiff(
        val text: String,
        val added: Int,
        val removed: Int
    )

    fun build(path: String, op: AiEditOp, oldText: String, newText: String): RenderedDiff {
        val lines = DiffEngine.compute(oldText, newText)
        val added = lines.count { it.op == DiffOp.ADD }
        val removed = lines.count { it.op == DiffOp.REMOVE }

        val oldHeader = if (op == AiEditOp.CREATE) "--- /dev/null" else "--- a/$path"
        val newHeader = if (op == AiEditOp.DELETE) "+++ /dev/null" else "+++ b/$path"

        val hunks = buildHunks(lines)
        val body = buildString {
            append(oldHeader).append('\n')
            append(newHeader)
            for (hunk in hunks) {
                append('\n').append(hunk)
            }
        }
        return RenderedDiff(text = body, added = added, removed = removed)
    }

    private fun buildHunks(lines: List<DiffLine>): List<String> {
        if (lines.isEmpty()) return emptyList()
        val changedIndices = lines.indices.filter { lines[it].op != DiffOp.CONTEXT }
        if (changedIndices.isEmpty()) return emptyList()

        val ranges = mutableListOf<IntRange>()
        var curStart = (changedIndices.first() - CONTEXT_LINES).coerceAtLeast(0)
        var curEnd = (changedIndices.first() + CONTEXT_LINES).coerceAtMost(lines.lastIndex)

        for (k in 1 until changedIndices.size) {
            val idx = changedIndices[k]
            val start = (idx - CONTEXT_LINES).coerceAtLeast(0)
            val end = (idx + CONTEXT_LINES).coerceAtMost(lines.lastIndex)
            if (start <= curEnd + 1) {
                curEnd = maxOf(curEnd, end)
            } else {
                ranges += curStart..curEnd
                curStart = start
                curEnd = end
            }
        }
        ranges += curStart..curEnd

        return ranges.map { range ->
            val slice = lines.subList(range.first, range.last + 1)
            val oldStart = slice.firstNotNullOfOrNull { it.oldNumber } ?: 1
            val newStart = slice.firstNotNullOfOrNull { it.newNumber } ?: 1
            val oldCount = slice.count { it.op == DiffOp.CONTEXT || it.op == DiffOp.REMOVE }
            val newCount = slice.count { it.op == DiffOp.CONTEXT || it.op == DiffOp.ADD }
            buildString {
                append("@@ -").append(oldStart).append(',').append(oldCount)
                append(" +").append(newStart).append(',').append(newCount).append(" @@")
                for (d in slice) {
                    append('\n')
                    when (d.op) {
                        DiffOp.CONTEXT -> append(' ').append(d.text)
                        DiffOp.REMOVE -> append('-').append(d.text)
                        DiffOp.ADD -> append('+').append(d.text)
                    }
                }
            }
        }
    }
}
