package com.codeci.ide.ui.ai

/**
 * Phase 92 — the self-check (the owner: *"I am tired of testing — give some
 * command and I will run and share what is wrong"*).
 *
 * One scripted session, five checks, and a report that says which of them failed
 * and **why**, so nobody has to judge a row by eye. Four checks run as ordinary
 * tasks — each one still goes through the normal preview and waits for the
 * owner's **Send** (D4), because the packed bytes are the thing under test — and
 * one check needs no request at all.
 *
 * This object is **pure**: no `android.*`, no `java.io`, no clock, no logging,
 * no state. It is host-tested by `AiSelfCheckTest`.
 *
 * What the report carries is a deliberate choice (**D6**): sizes, recipients,
 * booleans and the app's own error lines — **never a prompt, never an answer,
 * never a key**. The evidence needed to see whether the app carried the
 * conversation is a *count*, not the text.
 */
enum class AiSelfCheckOutcome { PASS, FAIL, PENDING }

/** Which door a step's question goes through. `null` = the step needs no request. */
enum class AiSelfCheckDoor { ASK, PROPOSE }

/** One scripted check. [prompt] is the exact question the preview will carry. */
data class AiSelfCheckStep(
    val id: String,
    val title: String,
    val door: AiSelfCheckDoor?,
    val prompt: String? = null
)

/**
 * A redacted snapshot of the settled task on screen. Every field is a number, a
 * label or a boolean; no prompt and no answer text enters this class, so none of
 * them can reach a verdict or the report.
 */
data class AiSelfCheckObserved(
    /** The settled prompt's own question, compared with the step's script. */
    val question: String?,
    /** True when the task on screen is DONE or FAILED. */
    val settled: Boolean,
    val answerChars: Int,
    /** Whether the answer carried the code word, computed where the text lives. */
    val usedCodeWord: Boolean,
    val cutShort: Boolean,
    val errorLine: String?,
    val proposalFiles: Int,
    val proposalInvalidReason: String?,
    val runRequested: Boolean,
    /** Characters the request actually sent (D4's number). */
    val sentChars: Int,
    /** Characters of conversation the request carried — the follow-up evidence. */
    val transcriptChars: Int,
    val provider: AiProviderId?,
    val model: String,
    val keySaved: Boolean,
    val sdkInt: Int,
    val allFilesAccess: Boolean,
    val storageGranted: Boolean
)

data class AiSelfCheckVerdict(
    val stepId: String,
    val outcome: AiSelfCheckOutcome,
    val detail: String
)

object AiSelfCheck {

    /**
     * The code word the follow-up check asks the model to remember. It is not a
     * secret and it is never written into the report — the report says whether
     * the answer used it, not what it is.
     */
    const val CODE_WORD = "KIWI-42"

    /** One run: the step awaiting action, and the verdicts of the steps already judged. */
    data class Run(
        val stepIndex: Int = 0,
        val verdicts: List<AiSelfCheckVerdict> = emptyList()
    )

    /** Check 1 needs no request; 2–3 are the follow-up proof; 4 is the diff door; 5 is the run tool. */
    val STEPS: List<AiSelfCheckStep> = listOf(
        AiSelfCheckStep(
            id = "access",
            title = "File access",
            door = null
        ),
        AiSelfCheckStep(
            id = "remember",
            title = "Remember a code word",
            door = AiSelfCheckDoor.ASK,
            prompt = "Remember this code word for my next message: $CODE_WORD. Reply with just: OK"
        ),
        AiSelfCheckStep(
            id = "recall",
            title = "Follow-up uses it",
            door = AiSelfCheckDoor.ASK,
            prompt = "What was the code word I told you in my previous message? Reply with just the word."
        ),
        AiSelfCheckStep(
            id = "proposal",
            title = "Edit proposal parses",
            door = AiSelfCheckDoor.PROPOSE,
            prompt = "Propose one small edit using the CODEC_EDIT block format: create a file called " +
                "codec-check.txt whose only line is OK."
        ),
        AiSelfCheckStep(
            id = "run",
            title = "Run request reaches the card",
            door = AiSelfCheckDoor.ASK,
            prompt = "To check the run tool: use your run-request tool to ask me to run the shell " +
                "command `echo codec-check`. Do not answer in prose."
        )
    )

    fun stepAt(run: Run): AiSelfCheckStep? = STEPS.getOrNull(run.stepIndex)

    fun isFinished(run: Run): Boolean = run.stepIndex >= STEPS.size

    /** `Check 2 of 5` — the card's own line. */
    fun progressLabel(run: Run): String {
        val shown = (run.stepIndex + 1).coerceAtMost(STEPS.size)
        return "Check $shown of ${STEPS.size}"
    }

    /**
     * The model answered with the code word somewhere in any casing or spacing —
     * `KIWI-42`, `kiwi 42`, `K I W I - 4 2`. Computed where the answer lives; the
     * answer itself never reaches this object's state or the report.
     */
    fun usedCodeWord(answer: String): Boolean =
        answer.uppercase().filter { it.isLetterOrDigit() }.contains(CODE_WORD.filter { it.isLetterOrDigit() })

    /**
     * The verdict for [step], from a redacted snapshot. `PENDING` means "this
     * step's own task has not settled yet" — never a guess.
     */
    fun judge(step: AiSelfCheckStep, observed: AiSelfCheckObserved): AiSelfCheckVerdict {
        if (step.door == null) {
            return when (step.id) {
                "access" -> if (observed.sdkInt >= 30) {
                    if (observed.allFilesAccess) {
                        verdict(step, AiSelfCheckOutcome.PASS, "all-files access granted (API ${observed.sdkInt})")
                    } else {
                        verdict(
                            step, AiSelfCheckOutcome.FAIL,
                            "all-files access NOT granted (API ${observed.sdkInt}) — " +
                                "Settings → Privacy & permissions → all files access"
                        )
                    }
                } else {
                    if (observed.storageGranted) {
                        verdict(step, AiSelfCheckOutcome.PASS, "storage permission granted (API ${observed.sdkInt})")
                    } else {
                        verdict(
                            step, AiSelfCheckOutcome.FAIL,
                            "storage permission NOT granted (API ${observed.sdkInt}) — " +
                                "Settings → Privacy & permissions"
                        )
                    }
                }
                else -> verdict(step, AiSelfCheckOutcome.PENDING, "no check is defined for this step")
            }
        }
        // Every other step judges the task on screen, and only when that task is
        // this step's own question — a later question of the owner's can never be
        // read as the step's result.
        if (!observed.settled || observed.question != step.prompt) {
            return verdict(step, AiSelfCheckOutcome.PENDING, "waiting for Send")
        }
        observed.errorLine?.let { line ->
            return verdict(step, AiSelfCheckOutcome.FAIL, "the request failed: $line")
        }
        return when (step.id) {
            "remember" -> when {
                observed.cutShort -> verdict(step, AiSelfCheckOutcome.FAIL, "the answer was cut off before it finished")
                observed.answerChars == 0 -> verdict(step, AiSelfCheckOutcome.FAIL, "the model answered nothing")
                else -> verdict(step, AiSelfCheckOutcome.PASS, "answered (${observed.answerChars} chars, ${observed.sentChars} sent)")
            }
            "recall" -> when {
                observed.transcriptChars == 0 ->
                    verdict(step, AiSelfCheckOutcome.FAIL, "this follow-up carried NO earlier turns — the app, not the model")
                observed.usedCodeWord ->
                    verdict(step, AiSelfCheckOutcome.PASS, "carried ${observed.transcriptChars} chars of conversation; the model used it")
                else ->
                    verdict(step, AiSelfCheckOutcome.FAIL, "carried ${observed.transcriptChars} chars of conversation; the model did not use it (answer ${observed.answerChars} chars)")
            }
            "proposal" -> when {
                observed.proposalFiles > 0 ->
                    verdict(step, AiSelfCheckOutcome.PASS, "${observed.proposalFiles} file(s) parsed into a reviewable diff")
                observed.proposalInvalidReason != null ->
                    verdict(step, AiSelfCheckOutcome.FAIL, "a block came back but was rejected: ${observed.proposalInvalidReason}")
                else ->
                    verdict(step, AiSelfCheckOutcome.FAIL, "no edit block came back (answer ${observed.answerChars} chars)")
            }
            "run" -> if (observed.runRequested) {
                verdict(step, AiSelfCheckOutcome.PASS, "the run approval card appeared")
            } else {
                verdict(step, AiSelfCheckOutcome.FAIL, "the model never asked to run (answer ${observed.answerChars} chars)")
            }
            else -> verdict(step, AiSelfCheckOutcome.PENDING, "no check is defined for this step")
        }
    }

    /**
     * What the card draws for one line: the recorded verdict when the step is
     * behind the run, the live one when it is the step being run, and null when
     * it has not been reached yet.
     */
    fun verdictAt(run: Run, index: Int, live: AiSelfCheckVerdict?): AiSelfCheckVerdict? = when {
        index < run.verdicts.size -> run.verdicts[index]
        index == run.stepIndex -> live
        else -> null
    }

    /**
     * The verdict of the step being run, if the task on screen has already
     * settled it. Null while the run is over.
     */
    fun liveVerdict(run: Run, observed: AiSelfCheckObserved): AiSelfCheckVerdict? {
        val step = stepAt(run) ?: return null
        val v = judge(step, observed)
        return if (v.outcome == AiSelfCheckOutcome.PENDING) null else v
    }

    /**
     * The report the owner pastes back. Redacted by construction: nothing here
     * comes from a prompt or an answer, only from numbers, labels and booleans.
     */
    fun report(appVersion: String, observed: AiSelfCheckObserved, run: Run): String {
        val builder = StringBuilder()
        builder.append("CodeC AI self-check — ${STEPS.size} checks, app ").append(appVersion).append('\n')
        // Both storage facts are printed: the modern all-files switch (API 30+)
        // and the legacy permission below it, so a report from any phone says
        // exactly which one is missing.
        builder.append("Android API ").append(observed.sdkInt)
            .append(" · all-files access: ").append(if (observed.allFilesAccess) "granted" else "not granted")
            .append(" · storage permission: ").append(if (observed.storageGranted) "granted" else "not granted")
            .append(" · API key saved: ").append(if (observed.keySaved) "yes" else "no")
            .append('\n')
        val recipient = observed.provider
        builder.append("Recipient: ").append(recipient?.label ?: "none").append(" · ")
            .append(observed.model.ifBlank { "no model" }).append('\n')
        builder.append('\n')
        var passed = 0
        var failed = 0
        var notRun = 0
        STEPS.forEachIndexed { index, step ->
            val settled = index < run.verdicts.size
            val live = if (!settled && index == run.stepIndex) liveVerdict(run, observed) else null
            val v = when {
                settled -> run.verdicts[index]
                live != null -> live
                else -> verdict(step, AiSelfCheckOutcome.PENDING, "not run")
            }
            val mark = when (v.outcome) {
                AiSelfCheckOutcome.PASS -> "PASS"
                AiSelfCheckOutcome.FAIL -> "FAIL"
                AiSelfCheckOutcome.PENDING -> "----"
            }
            if (v.outcome == AiSelfCheckOutcome.PASS) passed++
            if (v.outcome == AiSelfCheckOutcome.FAIL) failed++
            if (v.outcome == AiSelfCheckOutcome.PENDING) notRun++
            builder.append('[').append(index + 1).append('/').append(STEPS.size).append("] ")
                .append(step.title.padEnd(30, '.')).append(' ').append(mark).append(" — ")
                .append(v.detail).append('\n')
        }
        // The counts carry the state of the run: a check that was never reached
        // is "not run", never a pass and never a failure.
        builder.append('\n').append("Result: ").append(passed).append(" passed, ").append(failed)
            .append(" failed, ").append(notRun).append(" not run.").append('\n')
        builder.append("No prompts, no answers and no keys are in this text — sizes and results only.")
        return builder.toString()
    }

    private fun verdict(
        step: AiSelfCheckStep,
        outcome: AiSelfCheckOutcome,
        detail: String
    ): AiSelfCheckVerdict = AiSelfCheckVerdict(step.id, outcome, detail)
}
