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

    /** One line of the activity timeline shown to the user. */
    const val MAX_STEP_DETAIL_CHARS = 1_200

    /** The user-facing sentence for each stop reason. */
    fun stopSentence(reason: AiAgentStopReason): String = when (reason) {
        AiAgentStopReason.TURN_BUDGET -> "Stopped after $MAX_TURNS steps with the AI. Ask a narrower question to continue."
        AiAgentStopReason.TOOL_BUDGET -> "Stopped after $MAX_TOOL_CALLS tool calls. Ask a narrower question to continue."
        AiAgentStopReason.RUN_BUDGET -> "The AI reached its run limit for this task."
        AiAgentStopReason.WALL_CLOCK -> "Stopped after ${MAX_WALL_CLOCK_MS / 60_000} minutes. Ask a narrower question to continue."
        AiAgentStopReason.USER_STOP -> "Stopped."
        AiAgentStopReason.PROVIDER_FAILURE -> "The AI could not finish. Nothing in your project was changed."
    }
}

/** Why the loop stopped. Every value is shown to the user, never swallowed. */
enum class AiAgentStopReason { TURN_BUDGET, TOOL_BUDGET, RUN_BUDGET, WALL_CLOCK, USER_STOP, PROVIDER_FAILURE }

/** One row of the visible activity timeline. In memory only (D6). */
data class AiAgentStep(
    val kind: AiAgentStepKind,
    /** Short label, e.g. `read_file src/main.c (lines 1-60)`. */
    val title: String,
    /** The result text as the user should see it (already clipped). */
    val detail: String = "",
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
    val toolCallsUsed: Int = 0,
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

    /** How many tool calls are still available, for the timeline and the packer. */
    fun toolCallsRemaining(): Int = (AiAgentLimits.MAX_TOOL_CALLS - toolCallsUsed).coerceAtLeast(0)

    fun runsRemaining(): Int = (AiAgentLimits.MAX_RUNS - runsUsed).coerceAtLeast(0)

    fun elapsedMs(nowMs: Long): Long = if (startedAtMs <= 0L) 0L else (nowMs - startedAtMs).coerceAtLeast(0L)

    fun withTurn() = copy(turnsUsed = turnsUsed + 1)

    fun withToolCalls(n: Int) = copy(toolCallsUsed = toolCallsUsed + n)

    fun withRun() = copy(runsUsed = runsUsed + 1)
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
 * 1. the task and the map are always there (the map is the floor of the
 *    model's knowledge — that is the whole point of Level 4);
 * 2. the newest [AiAgentLimits.KEEP_LAST_RESULTS] tool results are added,
 *    newest first, while [AiAgentLimits.MAX_REQUEST_CHARS] allows;
 * 3. anything dropped is **counted in the text** — `(N earlier tool results
 *    were dropped to fit)` — so the model knows what it cannot see.
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
        budget: Int = AiAgentLimits.MAX_REQUEST_CHARS
    ): Packed {
        val results = steps.filter { it.kind == AiAgentStepKind.TOOL || it.kind == AiAgentStepKind.DENIED || it.kind == AiAgentStepKind.RUN_RESULT }
        val head = buildString {
            append("Task: ").append(question.trim()).append("\n\n")
            append(if (mapText.isBlank()) "(the project map could not be built)" else mapText).append('\n')
        }
        val kept = mutableListOf<AiAgentStep>()
        var dropped = 0
        var used = head.length
        for (step in results.asReversed()) {
            if (kept.size >= AiAgentLimits.KEEP_LAST_RESULTS) {
                dropped++
                continue
            }
            val block = renderStep(step)
            if (used + block.length > budget) {
                dropped++
                continue
            }
            kept += step
            used += block.length
        }
        val tail = "\n\nContinue the task. Emit one or more tool blocks if you need more of the project, " +
            "or answer normally when you have enough. Do not repeat a tool call whose result you already have."
        val text = buildString {
            append(head)
            if (dropped > 0) append("\n(").append(dropped).append(" earlier tool result")
                .append(if (dropped == 1) " was" else "s were").append(" dropped to fit)\n")
            for (step in kept.asReversed()) append('\n').append(renderStep(step))
            append(tail)
        }
        return Packed(text = text, droppedResults = dropped, chars = text.length)
    }

    /** One result block exactly as it enters the request. */
    fun renderStep(step: AiAgentStep): String = buildString {
        append("--- ").append(step.title).append(if (step.ok) "" else " [refused]").append('\n')
        append(step.detail.take(AiAgentLimits.MAX_RESULT_CHARS))
        append('\n')
    }
}
