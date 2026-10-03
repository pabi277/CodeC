package com.codeci.ide.ui.ai

/**
 * Phase 87 (AI Level 10) — the nine bounded agent controls, as pure code
 * (rule.md §4.4: Android-free, host-tested by `AiOptionsPolicyTest`).
 *
 * **S9 governs this whole file: options tune within caps; they never raise a
 * permission.** Every range here ends at or below an existing hard ceiling,
 * and no value produced by this policy can widen D1/D5 tool, write, run or
 * path permissions. The two ceilings that matter are referenced, never
 * restated:
 *
 * - [MAX_READ_WINDOW_LINES] *is* [AiToolLimits.MAX_READ_LINES], so the option
 *   can never out-run what the runner will actually serve.
 * - [DEFAULT_WORKING_SET_DEPTH] *is* [AiAgentLimits.KEEP_LAST_RESULTS], so the
 *   shipped default is today's behaviour.
 *
 * The caps that no control may reach are asserted by `AiLevel10CeilingTest`:
 * `MAX_TURNS`, `MAX_TOOL_CALLS`, `MAX_RUNS`, `MAX_BATCH_READS`,
 * `MAX_READ_CHARS`, `MAX_RESULT_CHARS`, `MAX_IDENTICAL_REPEATS`.
 *
 * Owner decisions, 2026-10-03: all nine controls · rendered in [AiHome] ·
 * backup provider **manual offer only** · answer detail defaults to [AiAnswerDetail.NORMAL].
 */

/**
 * Defect 11 — how much the agent is asked to write. `NORMAL` reproduces the
 * sentence `AiContext` hardcoded before this phase, verbatim.
 */
enum class AiAnswerDetail { BRIEF, NORMAL, THOROUGH }

/** Presentation only (**S8**): disclosure may be collapsed, never removed. */
enum class AiActivityDisplay { COLLAPSED, EXPANDED }

/** **S8** — a provider switch needs a fresh tap and a fresh preview. */
enum class AiBackupMode { OFF, MANUAL }

/** Whether a read-only budget extension is offered at a budget stop (**S12**). */
enum class AiBudgetOffer { OFFER, NO_OFFER }

/** **S7** — a read-only second opinion, never a second writer. */
enum class AiReviewer { OFF, ON }

/**
 * The nine controls. Eight are stored; *request inspection* is not, because it
 * has no off state — see [AiOptionsPolicy.REQUEST_INSPECTION_NOTE].
 */
data class AiOptions(
    val readWindowLines: Int = AiOptionsPolicy.DEFAULT_READ_WINDOW_LINES,
    val workingSetDepth: Int = AiOptionsPolicy.DEFAULT_WORKING_SET_DEPTH,
    val taskMemory: Boolean = true,
    val answerDetail: AiAnswerDetail = AiAnswerDetail.NORMAL,
    val activity: AiActivityDisplay = AiActivityDisplay.COLLAPSED,
    val backup: AiBackupMode = AiBackupMode.OFF,
    val budgetOffer: AiBudgetOffer = AiBudgetOffer.OFFER,
    val reviewer: AiReviewer = AiReviewer.OFF
)

object AiOptionsPolicy {

    /**
     * Lines one `read_file` may return when the option is at its ceiling.
     * Declared as the tool limit itself, not as a copy of `400`: raising one
     * without the other must not compile silently into a runner that refuses
     * what the model was told it could ask for.
     */
    const val MAX_READ_WINDOW_LINES = AiToolLimits.MAX_READ_LINES

    /**
     * The shipped default. This is **today's behaviour**: `AiTools` defaults an
     * omitted `end` to `MAX_READ_LINES`, so an untouched install reads 400.
     * The Level 10 spec proposed 150 (SWE-agent's optimum); shipping that would
     * change behaviour for every install on upgrade, which contradicts the
     * owner's own `NORMAL` answer-detail choice. Recorded as *Open decision 1*
     * in the Phase 87 ledger — the agent's decision, not the owner's.
     */
    const val DEFAULT_READ_WINDOW_LINES = AiToolLimits.MAX_READ_LINES

    const val MIN_READ_WINDOW_LINES = 50

    /** Today's packed-context depth. */
    const val DEFAULT_WORKING_SET_DEPTH = AiAgentLimits.KEEP_LAST_RESULTS
    const val MIN_WORKING_SET_DEPTH = 2
    const val MAX_WORKING_SET_DEPTH = 8

    /**
     * Control 6 is deliberately **not** a stored option: a persisted "on"
     * implies a persisted "off", and D4 says request inspection is not
     * user-removable. The absence of a setter is the guarantee.
     */
    const val REQUEST_INSPECTION_ALWAYS_ON = true
    const val REQUEST_INSPECTION_NOTE =
        "Every request is shown to you before it is sent. This cannot be turned off."

    val DEFAULT = AiOptions()

    /**
     * The values the panel offers. Declared here, not in the UI, so a row can
     * never present a value outside the declared range (**S9**) and the ceiling
     * test can check the offered set directly.
     */
    fun readWindowChoices(): List<Int> =
        listOf(50, 100, 150, 200, 300, MAX_READ_WINDOW_LINES).distinct().sorted()

    fun workingSetChoices(): List<Int> =
        (MIN_WORKING_SET_DEPTH..MAX_WORKING_SET_DEPTH).toList()

    /** Out-of-range, garbage or absurd values land inside the range, never outside it. */
    fun clampReadWindow(raw: Int): Int =
        if (raw <= 0) DEFAULT_READ_WINDOW_LINES
        else raw.coerceIn(MIN_READ_WINDOW_LINES, MAX_READ_WINDOW_LINES)

    fun clampWorkingSetDepth(raw: Int): Int =
        if (raw <= 0) DEFAULT_WORKING_SET_DEPTH
        else raw.coerceIn(MIN_WORKING_SET_DEPTH, MAX_WORKING_SET_DEPTH)

    /**
     * The sentence appended to the agent's system instruction.
     *
     * `NORMAL` returns the line `AiContext.AGENT_ASK_SYSTEM_INSTRUCTION` used to
     * end with, **verbatim** — `AiOptionsPolicyTest` asserts the equality, so
     * rewording the default fails the build instead of silently changing every
     * install on upgrade.
     */
    fun detailSentence(detail: AiAnswerDetail): String = when (detail) {
        AiAnswerDetail.BRIEF -> "Keep the answer as short as you can: it is read on a phone."
        AiAnswerDetail.NORMAL -> "Keep answers short: they are read on a phone."
        AiAnswerDetail.THOROUGH ->
            "Explain thoroughly: the user asked for the full detail, so prefer a complete " +
                "explanation over brevity, and still keep it readable on a phone."
    }

    // ---- total, fail-safe decoders (garbage in → default out) ---------------

    fun readWindow(raw: String?): Int =
        clampReadWindow(raw?.trim()?.toIntOrNull() ?: DEFAULT_READ_WINDOW_LINES)

    fun workingSetDepth(raw: String?): Int =
        clampWorkingSetDepth(raw?.trim()?.toIntOrNull() ?: DEFAULT_WORKING_SET_DEPTH)

    /** Mirrors `AiKeyStore.showBubble`: absent means **on**, because memory defaults on. */
    fun booleanOn(raw: String?): Boolean = raw?.trim() != "false"

    fun answerDetail(raw: String?): AiAnswerDetail =
        decodeEnum(raw) ?: AiAnswerDetail.NORMAL

    fun activity(raw: String?): AiActivityDisplay =
        decodeEnum(raw) ?: AiActivityDisplay.COLLAPSED

    fun backup(raw: String?): AiBackupMode = decodeEnum(raw) ?: AiBackupMode.OFF

    fun budgetOffer(raw: String?): AiBudgetOffer = decodeEnum(raw) ?: AiBudgetOffer.OFFER

    fun reviewer(raw: String?): AiReviewer = decodeEnum(raw) ?: AiReviewer.OFF

    /** One read of every stored property; nothing here can throw. */
    fun decode(
        readWindow: String?,
        workingSet: String?,
        taskMemory: String?,
        answerDetail: String?,
        activity: String?,
        backup: String?,
        budgetOffer: String?,
        reviewer: String?
    ): AiOptions = AiOptions(
        readWindowLines = readWindow(readWindow),
        workingSetDepth = workingSetDepth(workingSet),
        taskMemory = booleanOn(taskMemory),
        answerDetail = answerDetail(answerDetail),
        activity = activity(activity),
        backup = backup(backup),
        budgetOffer = budgetOffer(budgetOffer),
        reviewer = reviewer(reviewer)
    )

    private inline fun <reified T : Enum<T>> decodeEnum(raw: String?): T? {
        val v = raw?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        return enumValues<T>().firstOrNull { it.name == v }
    }
}
