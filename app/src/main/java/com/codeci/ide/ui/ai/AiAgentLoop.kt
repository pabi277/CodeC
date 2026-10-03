package com.codeci.ide.ui.ai

/**
 * Phase 80 (AI Level 4) — **the agent loop, as pure policy**: how many model
 * turns and tool calls a task may spend, what the next request carries, and
 * when the loop must stop.
 *
 * ## Owner decisions this file implements (2026-10-02, before any code)
 *
 * | # | Question | Owner answer |
 * |---|---|---|
 * | 1 | D4 vs a multi-step loop | **One task preview, then read-only tool steps.** The first preview shows the question, the map, the tool allowlist and the caps; after Send, `list_files`/`search_project`/`read_file` run one at a time with a visible timeline and Stop, and no extra taps. **Every run and every file apply still needs its own tap.** |
 * | 2 | What may the agent run | **The RUN ▶ path only** — the exact action the editor's RUN button performs, through the existing Output pipeline and runner guards. No terminal typing, no package installation, no Git. |
 * | 4 | Caps | **≤ 12 model turns · ≤ 24 tool calls · ≤ 2 runs · ≤ 8 000 chars per tool result · ~5 min wall clock**, all enforced here and shown in the timeline. |
 *
 * The loop is a decision function, not a coroutine: [AiViewModel] owns the
 * jobs; this object says what may happen next and what the next request holds,
 * so `AiAgentLoopTest` can prove every cap on the host JVM.
 *
 * ## Why the caps are not negotiable by the model
 *
 * `04_AGENT_TOOLS_AND_RUN_LOOP.md`: *"The model never decides whether it is
 * authorized. The app enforces permissions after every proposed tool call."*
 * A tool block asking for a 25th call is answered with a denial result, not
 * with a 25th call.
 */
object AiAgentLimits {
    /** Model turns (request + answer pairs) one task may use. */
    const val MAX_TURNS = 12

    /** Tool executions one task may use, across all turns. */
    const val MAX_TOOL_CALLS = 24

    /** Runs one task may request, each separately approved by the user. */
    const val MAX_RUNS = 2

    /** The whole task is stopped at this point, whatever it is doing. */
    const val MAX_WALL_CLOCK_MS = 300_000L

    /** Characters one request body may carry (map + kept tool results + task text). */
    const val MAX_REQUEST_CHARS = 24_000

    /** Characters of any one tool result included in the next request. */
    const val MAX_RESULT_CHARS = AiToolLimits.MAX_RESULT_CHARS

    /** How many of the newest tool results are offered before older ones are dropped. */
    const val KEEP_LAST_RESULTS = 4

    /**
     * Phase 85 (Level 8, item 4) — the budget for the restorable-eviction pointer
     * list. A dropped result is no longer just a count: each is named by path and
     * range so the model can re-read it on demand. This caps that list so a long
     * task cannot crowd out the results that did fit.
     */
    const val MAX_EVICTION_POINTER_CHARS = 1_200

    /**
     * Phase 85 (Level 8, S11) — how many times the model may issue the *same* call
     * with no other call in between before the loop stops as no-progress. Duplicate
     * reads are already served from the working set at zero execution cost; this is
     * the backstop that ends a loop spinning on one unanswerable request.
     */
    const val MAX_IDENTICAL_REPEATS = 3

    /** One line of the activity timeline shown to the user. */
    const val MAX_STEP_DETAIL_CHARS = 1_200

    /**
     * Phase 84 (Level 7, fix 5 / **S2**) — clip a full tool result down to the
     * on-screen [MAX_STEP_DETAIL_CHARS] preview **without losing the honest cut
     * marker**. The old code did `full.take(MAX_STEP_DETAIL_CHARS)`, which threw
     * away the runner's `CUT_NOTE` sitting at the end of a truncated result, so a
     * fragment looked complete on the timeline. The full text still reaches the
     * model untouched through [AiAgentStep.modelResult]; this only shapes what
     * the user reads. When [full] carried [cutMarker], the clipped preview keeps
     * it; when it did not, no marker is invented.
     */
    fun timelineDetail(full: String, cutMarker: String): String {
        if (full.length <= MAX_STEP_DETAIL_CHARS) return full
        val marker = if (cutMarker.isNotEmpty() && full.contains(cutMarker)) "\n" + cutMarker else ""
        val room = (MAX_STEP_DETAIL_CHARS - marker.length).coerceAtLeast(0)
        return full.substring(0, room.coerceAtMost(full.length)) + marker
    }

    /** The user-facing sentence for each stop reason. */
    fun stopSentence(reason: AiAgentStopReason): String = when (reason) {
        AiAgentStopReason.TURN_BUDGET -> "Stopped after $MAX_TURNS steps with the AI. Ask a narrower question to continue."
        AiAgentStopReason.TOOL_BUDGET -> "Stopped after $MAX_TOOL_CALLS tool calls. Ask a narrower question to continue."
        AiAgentStopReason.RUN_BUDGET -> "The AI reached its run limit for this task."
        AiAgentStopReason.WALL_CLOCK -> "Stopped after ${MAX_WALL_CLOCK_MS / 60_000} minutes. Ask a narrower question to continue."
        AiAgentStopReason.USER_STOP -> "Stopped."
        AiAgentStopReason.PROVIDER_FAILURE -> "The AI could not finish. Nothing in your project was changed."
        AiAgentStopReason.NO_PROGRESS ->
            "Stopped: the AI repeated the same read $MAX_IDENTICAL_REPEATS times without making progress. " +
                "Ask a narrower question to continue."
    }
}

/** Why the loop stopped. Every value is shown to the user, never swallowed. */
enum class AiAgentStopReason {
    TURN_BUDGET, TOOL_BUDGET, RUN_BUDGET, WALL_CLOCK, USER_STOP, PROVIDER_FAILURE,
    /** Phase 85 (Level 8, S11): the model repeated one call with no progress in between. */
    NO_PROGRESS
}

/** One row of the visible activity timeline. In memory only (D6). */
data class AiAgentStep(
    val kind: AiAgentStepKind,
    /** Short label, e.g. `read_file src/main.c (lines 1-60)`. */
    val title: String,
    /**
     * The result text as the user should see it — a short **preview**, already
     * clipped to [AiAgentLimits.MAX_STEP_DETAIL_CHARS]. Phase 84 (**S1**): this
     * is a UI string only and is **never** packed into a model request.
     */
    val detail: String = "",
    /**
     * Phase 84 (Level 7, fix 1 / **S1**) — the full-fidelity result text that the
     * **next model request** carries, clipped only to [AiAgentLimits.MAX_RESULT_CHARS]
     * (the owner's 8 000-char per-result cap), never to the 1 200-char preview.
     * [AiAgentPrompt.renderStep] reads this, not [detail]. Empty for steps that
     * carry no model-facing payload (TASK/REQUEST/STOPPED).
     */
    val modelResult: String = "",
    /** False when the step was refused or failed. */
    val ok: Boolean = true,
    /** Phase 82B D4: REQUEST rows hold the exact two sent strings, never clipped. Memory only. */
    val sentSystemInstruction: String? = null,
    val sentUserText: String? = null
)

enum class AiAgentStepKind {
    /** Exact request disclosure, not a tool result and never re-packed as one. */
    REQUEST,

    /** The user's own question, shown first. */
    TASK,

    /** A model turn: the answer text and how much was sent. */
    ANSWER,

    /** A read-only tool that ran. */
    TOOL,

    /** A tool call the policy refused, with its reason. */
    DENIED,

    /** The AI asked to run the project; waiting for the user's tap. */
    RUN_REQUEST,

    /** The user's decision on that request. */
    RUN_DECISION,

    /** The run finished; this is the digest that went back to the model. */
    RUN_RESULT,

    /** A cap or the user ended the task. */
    STOPPED
}

/**
 * Usage counters. [startedAtMs] is captured when the user taps **Send** on the
 * task preview, so the wall clock measures the task, not the typing.
 */
data class AiAgentBudget(
    val turnsUsed: Int = 0,
    /**
     * Phase 84 (fix 3): tool calls that actually **executed**. This is the only
     * counter that gates [AiAgentLimits.MAX_TOOL_CALLS]; refusals no longer
     * inflate it (that inflation is what rendered "49 of 24 reads").
     */
    val toolCallsUsed: Int = 0,
    /**
     * Phase 84 (fix 3): tool blocks that were **refused** — malformed, denied by
     * policy, or over-budget. Counted and shown separately; it never gates the
     * execution cap and never mixes into [toolCallsUsed].
     */
    val toolCallsRefused: Int = 0,
    /**
     * Phase 84 (fix 3) / Phase 85 (**S11**): duplicate reads served from the
     * loop-local working set at zero execution cost. Populated by
     * [AiViewModel.executeToolBatch] when an exact-duplicate read (same path, same
     * range) is answered from [AiAgentWorkingSet] instead of touching the disk. It
     * never gates [AiAgentLimits.MAX_TOOL_CALLS] and never mixes into
     * [toolCallsUsed] — the three counts stay structurally separate.
     */
    val toolCallsReused: Int = 0,
    val runsUsed: Int = 0,
    val startedAtMs: Long = 0L
) {
    /** The reason the loop may not take another model turn, or null when it may. */
    fun blockModelTurn(nowMs: Long): AiAgentStopReason? = when {
        turnsUsed >= AiAgentLimits.MAX_TURNS -> AiAgentStopReason.TURN_BUDGET
        elapsedMs(nowMs) > AiAgentLimits.MAX_WALL_CLOCK_MS -> AiAgentStopReason.WALL_CLOCK
        else -> null
    }

    /** The reason no further tool may run, or null when one may. */
    fun blockTool(nowMs: Long): AiAgentStopReason? = when {
        toolCallsUsed >= AiAgentLimits.MAX_TOOL_CALLS -> AiAgentStopReason.TOOL_BUDGET
        elapsedMs(nowMs) > AiAgentLimits.MAX_WALL_CLOCK_MS -> AiAgentStopReason.WALL_CLOCK
        else -> null
    }

    /**
     * Phase 84 (fix 2): the reason the loop must stop on **any** resume path —
     * the turn/wall-clock gate first, then the tool gate. `resumeAgentOrStop`
     * consults this so the malformed-parse and denied-only paths can no longer
     * walk past an exhausted tool budget the way `AiAgentPolicy.decide` alone
     * could not stop them.
     */
    fun blockResume(nowMs: Long): AiAgentStopReason? = blockModelTurn(nowMs) ?: blockTool(nowMs)

    /** How many tool calls are still available, for the timeline and the packer. */
    fun toolCallsRemaining(): Int = (AiAgentLimits.MAX_TOOL_CALLS - toolCallsUsed).coerceAtLeast(0)

    fun runsRemaining(): Int = (AiAgentLimits.MAX_RUNS - runsUsed).coerceAtLeast(0)

    fun elapsedMs(nowMs: Long): Long = if (startedAtMs <= 0L) 0L else (nowMs - startedAtMs).coerceAtLeast(0L)

    fun withTurn() = copy(turnsUsed = turnsUsed + 1)

    /**
     * Records [n] executed tool calls, **clamped** to the cap so the displayed
     * count can never read past `MAX_TOOL_CALLS` (fix 3: "never exceed their
     * caps").
     */
    fun withToolCalls(n: Int) =
        copy(toolCallsUsed = (toolCallsUsed + n).coerceIn(0, AiAgentLimits.MAX_TOOL_CALLS))

    /** Records [n] refused tool blocks (malformed/denied/over-budget), separately. */
    fun withRefused(n: Int) =
        copy(toolCallsRefused = (toolCallsRefused + n).coerceAtLeast(0))

    /** Phase 85 (S11): records [n] duplicate reads served from the working set. */
    fun withReused(n: Int) =
        copy(toolCallsReused = (toolCallsReused + n).coerceAtLeast(0))

    fun withRun() = copy(runsUsed = runsUsed + 1)
}

/**
 * Phase 85 (Level 8, **S11**) — the loop-local working set. The agent loop is
 * read-only (S6/S7: `ui/ai` never writes the project), so within one task an
 * exact-duplicate read — same path, same range — returns the same bytes. Serving it
 * from here costs zero execution budget (counted as `toolCallsReused`, never
 * `toolCallsUsed`), and watching for a run of identical, no-progress calls lets the
 * loop stop instead of spinning. The exact-result map remains loop-local; Level 9
 * adds a separate bounded file-content cache in [AiTaskMemory], validated against
 * the current admitted file version before it can be reused.
 */
data class AiAgentWorkingSet(
    /** Read identity (`path\u0000start-end`) to the result text already delivered. */
    val results: Map<String, String> = emptyMap(),
    val lastSignature: String? = null,
    val repeats: Int = 0
) {
    /** The cached result for [key], or null when this exact read has not run yet. */
    fun cached(key: String): String? = results[key]

    fun record(key: String, result: String): AiAgentWorkingSet =
        copy(results = results + (key to result))

    /** Notes one issued call by [signature]; consecutive identical calls accumulate. */
    fun note(signature: String): AiAgentWorkingSet =
        if (signature == lastSignature) copy(repeats = repeats + 1)
        else copy(lastSignature = signature, repeats = 1)

    /** True when one call has repeated [AiAgentLimits.MAX_IDENTICAL_REPEATS] times with no progress between. */
    fun stalled(): Boolean = repeats >= AiAgentLimits.MAX_IDENTICAL_REPEATS

    companion object {
        /** A read identity includes the current content version when one is available. */
        fun readKey(path: String, start: Int, end: Int, contentVersion: String? = null): String =
            "$path\u0000$start-$end" + (contentVersion?.let { "\u0000$it" } ?: "")

        /** An ordered batch identity; order and each file version affect the result. */
        fun readFilesKey(reads: List<AiAgentReadIdentity>): String =
            "read_files\u0000" + reads.joinToString("\u0001") {
                readKey(it.canonicalPath, it.start, it.end, it.contentVersion)
            }
    }
}

/**
 * What the loop does next, given the parsed answer and the budget. Pure data —
 * [AiViewModel] executes it, the tests prove it.
 */
sealed class AiAgentDecision {
    /**
     * Run these validated calls (at most the remaining tool budget), append
     * their results, then take another model turn if the budget allows.
     */
    data class ExecuteTools(val calls: List<AiToolCall>, val denied: List<AiToolVerdict.Denied>) : AiAgentDecision()

    /**
     * The AI asked to run the project: the loop pauses, the UI shows the
     * approval card, and **nothing runs** until the user taps. [call] is the
     * validated `request_run` call; the other calls that arrived in the same
     * answer are held in [alsoQueued] (and their refusals in [denied]) so they
     * run after the user's decision instead of being silently dropped.
     */
    data class AskRunApproval(
        val call: AiToolCall,
        val alsoQueued: List<AiToolCall> = emptyList(),
        val denied: List<AiToolVerdict.Denied> = emptyList()
    ) : AiAgentDecision()

    /** No tool calls: the answer is final. Propose-edits flows parse it further. */
    data class Finish(val answer: String) : AiAgentDecision()

    /** A cap, the user, or the provider ended the task. */
    data class Stop(val reason: AiAgentStopReason) : AiAgentDecision()
}

object AiAgentPolicy {

    /**
     * Decides what happens after one model answer was parsed.
     *
     * A malformed block is not a decision: the caller has already turned it
     * into a [AiToolVerdict.Denied]-shaped result so the model can fix its own
     * format, and that counts as a tool call (it consumed a turn and a read of
     * the budget, and pretending otherwise would let a model loop for free).
     */
    fun decide(
        parsed: AiToolParse.Calls,
        budget: AiAgentBudget,
        nowMs: Long,
        projectView: AiToolProjectView
    ): AiAgentDecision {
        if (parsed.calls.isEmpty()) return AiAgentDecision.Finish(parsed.prose)
        budget.blockTool(nowMs)?.let { return AiAgentDecision.Stop(it) }

        val checked = parsed.calls.map { it to AiToolPolicy.validate(it, projectView) }
        val denied = checked.mapNotNull { (_, v) -> v as? AiToolVerdict.Denied }
        val allowed = checked.mapNotNull { (_, v) -> (v as? AiToolVerdict.Allowed)?.call }

        // A run request pauses the whole loop — the other calls in the same
        // answer are carried along and executed after the user decides.
        allowed.firstOrNull { it.name == AiToolName.REQUEST_RUN }?.let { run ->
            return AiAgentDecision.AskRunApproval(
                call = run,
                alsoQueued = allowed.filter { it !== run },
                denied = denied
            )
        }

        val room = budget.toolCallsRemaining()
        if (room <= 0) return AiAgentDecision.Stop(AiAgentStopReason.TOOL_BUDGET)
        val runnable = allowed.take(room)
        // Calls past the budget are answered with a refusal, so a model that
        // ignores the caps is told instead of being obeyed.
        val overflowDenied = checked
            .filter { (_, v) -> v is AiToolVerdict.Allowed }
            .drop(room)
            .map { (request, _) ->
                AiToolVerdict.Denied(request, "the ${AiAgentLimits.MAX_TOOL_CALLS}-call budget for this task is used up; answer with what is known")
            }
        return AiAgentDecision.ExecuteTools(runnable, denied + overflowDenied)
    }
}

/**
 * Builds the exact text of one agent request. [mapText] is
 * [AiRepoMap.MapResult.text]; [steps] is the timeline so far. The packing rule
 * is the honest one:
 *
 * 1. the task and map are prioritized as the static head (the map is the floor
 *    of the model's knowledge — that is the whole point of Level 4); if a caller
 *    supplies an unusually small budget, the fixed plan suffix is reserved first;
 * 2. the newest [AiAgentLimits.KEEP_LAST_RESULTS] tool results are added,
 *    newest first, while [AiAgentLimits.MAX_REQUEST_CHARS] allows;
 * 3. anything dropped is **named, not just counted** (Phase 85, Level 8, item 4):
 *    the text still says `(N earlier tool results were dropped to fit …)`, and
 *    then lists one re-read pointer per dropped result — `path — lines a-b —
 *    status` — so every evicted result is re-acquirable on demand instead of
 *    silently gone. The list is capped at [AiAgentLimits.MAX_EVICTION_POINTER_CHARS].
 */
object AiAgentPrompt {

    data class Packed(
        val text: String,
        val droppedResults: Int,
        val chars: Int
    )

    fun pack(
        question: String,
        mapText: String,
        steps: List<AiAgentStep>,
        budget: Int = AiAgentLimits.MAX_REQUEST_CHARS,
        memory: AiTaskMemory? = null,
        includePlanAtEnd: Boolean = true,
        includeContinueTail: Boolean = true
    ): Packed {
        val results = steps.filter {
            it.kind == AiAgentStepKind.TOOL || it.kind == AiAgentStepKind.DENIED || it.kind == AiAgentStepKind.RUN_RESULT
        }
        val staticHead = buildString {
            append("Task: ").append(question.trim()).append("\n\n")
            append(if (mapText.isBlank()) "(the project map could not be built)" else mapText).append('\n')
        }
        val planTail = if (includePlanAtEnd) memory?.renderPlanRecitation().orEmpty() else ""
        // Level 9 replaces the generic tail with the recited plan. Legacy/test
        // calls without task memory keep the original tail for compatibility.
        val continueTail = if (includeContinueTail && memory == null) {
            "\n\nContinue the task. Emit one or more tool blocks if you need more of the project, " +
                "or answer normally when you have enough. Do not repeat a tool call whose result you already have."
        } else ""
        val tail = continueTail + if (planTail.isNotEmpty()) "\n\n$planTail" else ""
        val safeBudget = budget.coerceAtLeast(0)
        // The plan is the fixed suffix. If an unusual caller supplies an
        // oversized task/map, trim that head first rather than cutting off the
        // recitation at the request boundary.
        val headRoom = (safeBudget - tail.length).coerceAtLeast(0)
        val boundedStaticHead = staticHead.take(headRoom)
        val memoryContext = memory?.renderContextForPrompt().orEmpty()
        val memoryHeader = if (memoryContext.isEmpty()) "" else "\n\nTask memory (untrusted derived data):\n"
        val memoryRoom = (safeBudget - boundedStaticHead.length - tail.length - memoryHeader.length).coerceAtLeast(0)
        val head = boundedStaticHead + if (memoryRoom > 0 && memoryContext.isNotEmpty()) {
            memoryHeader + memoryContext.take(memoryRoom)
        } else ""

        val kept = mutableListOf<AiAgentStep>() // newest first while packing
        val dropped = mutableListOf<AiAgentStep>()
        for (step in results.asReversed()) {
            if (kept.size >= AiAgentLimits.KEEP_LAST_RESULTS) dropped += step
            else kept += step
        }

        fun assemble(): String {
            val keptText = kept.asReversed().joinToString(separator = "") { "\n${renderStep(it)}" }
            val fixed = head.length + keptText.length + tail.length
            val pointerBudget = (budget - fixed).coerceAtLeast(0)
                .coerceAtMost(AiAgentLimits.MAX_EVICTION_POINTER_CHARS)
            val droppedText = renderDropped(dropped, pointerBudget)
            return head + droppedText + keptText + tail
        }

        var text = assemble()
        // The newest full results win; if a tight budget still cannot fit,
        // evict the oldest kept result and leave its honest re-read pointer.
        while (text.length > budget && kept.isNotEmpty()) {
            dropped += kept.removeAt(kept.lastIndex)
            text = assemble()
        }
        if (text.length > budget) text = text.take(budget.coerceAtLeast(0))
        return Packed(text = text, droppedResults = dropped.size, chars = text.length)
    }

    private fun renderDropped(steps: List<AiAgentStep>, maxChars: Int): String {
        if (steps.isEmpty() || maxChars <= 0) return ""
        val header = "\n(${steps.size} earlier tool result" +
            (if (steps.size == 1) " was" else "s were") +
            " dropped to fit; re-read any on demand:)\n"
        val out = StringBuilder()
        if (header.length <= maxChars) out.append(header) else return header.take(maxChars)
        var shown = 0
        for (step in steps) {
            val pointer = evictionPointer(step) + "\n"
            if (out.length + pointer.length > maxChars) break
            out.append(pointer)
            shown++
        }
        if (shown < steps.size) {
            val more = "- (+${steps.size - shown} more; re-issue the call to re-read)\n"
            if (out.length + more.length <= maxChars) out.append(more)
        }
        return out.toString()
    }

    /**
     * Phase 85 (Level 8, item 4) — one restorable-eviction pointer. A dropped read
     * is named by `path — lines a-b — status` (parsed from its honest header) so the
     * model can re-read exactly that range on demand; any other dropped result falls
     * back to the call it came from, which is enough to re-issue it.
     */
    private fun evictionPointer(step: AiAgentStep): String {
        val firstLine = step.modelResult.lineSequence().firstOrNull().orEmpty()
        val header = Regex("""^FILE (.+?) — lines (\d+)-(\d+) of \d+""").find(firstLine)
        val core = if (header != null) {
            val status = when {
                firstLine.contains("[refused") -> "refused"
                firstLine.contains("[partial") -> "partial"
                firstLine.contains("[complete]") -> "complete"
                else -> "read"
            }
            "${header.groupValues[1]} — lines ${header.groupValues[2]}-${header.groupValues[3]} — $status"
        } else {
            step.title + if (step.ok) "" else " — refused"
        }
        return "- $core"
    }

    /** One result block exactly as it enters the request. */
    fun renderStep(step: AiAgentStep): String = buildString {
        append("--- ").append(step.title).append(if (step.ok) "" else " [refused]").append('\n')
        // Phase 84 (S1): the model reads the full-fidelity result, never the
        // 1 200-char on-screen preview. A wiring pin fails if this reads `detail`.
        append(step.modelResult.take(AiAgentLimits.MAX_RESULT_CHARS))
        append('\n')
    }

    /**
     * Phase 84 (Level 7, fix 4 / **S12**) — the reserved final-synthesis request.
     * Tools are **masked, not removed** (Manus): the system instruction still
     * defines them, so the provider's KV-cache and the tool schema stay intact,
     * but this turn's user text tells the model that tool use is disabled and it
     * must answer in prose from what is already above. This is what guarantees a
     * readable answer on a budget stop instead of a raw `<<<CODEC_TOOL` block.
     */
    const val FINAL_SYNTHESIS_INSTRUCTION: String =
        "Tool use is disabled for this final turn. Using only the task, the project map and " +
            "the tool results above, write the final answer to the user in prose. Do not emit " +
            "any <<<CODEC_TOOL block. If you could not finish, say plainly what is known and what is not."

    /**
     * Builds the final-synthesis request: the ordinary packed context plus the
     * mask instruction. Bounded by the same [AiAgentLimits.MAX_REQUEST_CHARS].
     */
    fun finalSynthesis(
        question: String,
        mapText: String,
        steps: List<AiAgentStep>,
        budget: Int = AiAgentLimits.MAX_REQUEST_CHARS,
        memory: AiTaskMemory? = null
    ): Packed {
        val plan = memory?.renderPlanRecitation().orEmpty()
        val finalTail = "\n\n" + FINAL_SYNTHESIS_INSTRUCTION + if (plan.isNotEmpty()) "\n\n$plan" else ""
        val baseBudget = (budget - finalTail.length).coerceAtLeast(0)
        val base = pack(
            question, mapText, steps, baseBudget, memory,
            includePlanAtEnd = false,
            includeContinueTail = false
        )
        val text = base.text + finalTail
        return Packed(text = text, droppedResults = base.droppedResults, chars = text.length)
    }
}
