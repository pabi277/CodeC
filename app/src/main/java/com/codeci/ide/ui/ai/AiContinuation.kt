package com.codeci.ide.ui.ai

/**
 * Phase 81 — **continuing an answer CodeC cut short**.
 *
 * The problem (owner, 2026-10-02: *"Add a continue open on Gemini so i can
 * continue after cut off"*): a long answer stops either because the model hit
 * its own output budget or because the app's cap
 * ([AiLimits.MAX_REPLY_CHARS]) was reached, and the user could only copy what
 * arrived and ask again from zero.
 *
 * The fix keeps every law intact:
 *
 *  - **Nothing is sent here.** This object only *describes* the follow-up: the
 *    tail of the answer so far, and the plain sentence that tells the model to
 *    resume. The caller builds a new prompt and lands on the ordinary preview,
 *    so the user still reads the exact two strings and taps Send (D4).
 *  - **Stateless like every request** (D6, `store:false`): the API has no
 *    memory, so the continuation carries the last lines of the answer inside the
 *    new user message, on top of the same original request.
 *  - **Bounded**: at most [MAX_CONTINUATIONS] taps, at most
 *    [MAX_TOTAL_CHARS] characters in one visible answer, and each continuation
 *    may add at most [AiLimits.MAX_REPLY_CHARS] new characters.
 *
 * Pure — no `java.io`, no `android.*` — and host-tested by `AiContinuationTest`.
 */
data class AiContinuationRequest(
    /** 1 for the first continuation of an answer, 2 for the next, … */
    val index: Int,
    /** The last lines of the answer so far, exactly as they are sent. */
    val tail: String
)

object AiContinuation {

    /** How many times one answer may be continued before the app stops offering it. */
    const val MAX_CONTINUATIONS = 8

    /**
     * The most characters one visible answer may grow to. Phase 82 deliberately keeps
     * 64,000: a full 48,000-character first reply leaves 16,000 for Continue.
     */
    const val MAX_TOTAL_CHARS = 64_000

    /** How much of the answer's tail travels back with the follow-up. */
    const val TAIL_CHARS = 1_400

    /**
     * When the tail starts mid-line, look this far for a line break and start
     * there instead — resuming at a line boundary reads better to a model.
     */
    const val TAIL_LINE_SNAP = 200

    /** Below this many characters of room, a continuation is not worth sending. */
    const val MIN_REMAINING_CHARS = 600

    /** Characters still available for the answer inside [MAX_TOTAL_CHARS]. */
    fun remainingChars(answerChars: Int): Int = (MAX_TOTAL_CHARS - answerChars).coerceAtLeast(0)

    /**
     * What this continuation request may add: the per-reply cap, never more than
     * what is left of the total — and never zero (`AiAnswerAccumulator` needs a
     * positive budget; callers check [canContinue] first).
     */
    fun requestBudget(answerChars: Int): Int =
        minOf(AiLimits.MAX_REPLY_CHARS, remainingChars(answerChars)).coerceAtLeast(1)

    /** True while both the tap count and the total length still allow a continuation. */
    fun canContinue(continuations: Int, answerChars: Int): Boolean =
        continuations < MAX_CONTINUATIONS && remainingChars(answerChars) >= MIN_REMAINING_CHARS

    /**
     * The last [maxChars] characters of [answer], starting at a line break when
     * one is close, prefixed with `…` so the model can see it is a fragment.
     * Short answers are returned whole (no prefix).
     */
    fun tailOf(answer: String, maxChars: Int = TAIL_CHARS): String {
        val text = answer.trimEnd()
        if (text.length <= maxChars) return text
        var start = text.length - maxChars
        val newline = text.indexOf('\n', start)
        if (newline >= 0 && newline - start <= TAIL_LINE_SNAP) start = newline + 1
        return "…" + text.substring(start)
    }

    /**
     * The block appended to the re-sent request. Written for the model: resume,
     * do not repeat, do not restart.
     */
    fun block(request: AiContinuationRequest): String = buildString {
        append("\n\n[The answer above stopped before it was finished: it reached this app's ")
        append("length limit. Continue it from exactly where it stopped. Do not repeat ")
        append("anything you already wrote, do not apologise, and do not start the answer ")
        append("again.]\n[The last lines of your answer so far:\n")
        append(request.tail)
        append("\n]")
    }

    /**
     * Why the offer is gone, or null while there is still room. Shown instead of
     * the button, so a missing Continue is always explained.
     */
    fun limitNote(continuations: Int, answerChars: Int): String? = when {
        continuations >= MAX_CONTINUATIONS ->
            "This answer has been continued $MAX_CONTINUATIONS times — that is the limit. " +
                "Ask a new question to go further."
        remainingChars(answerChars) < MIN_REMAINING_CHARS ->
            "This answer has reached the app's overall length limit " +
                "($MAX_TOTAL_CHARS characters). Ask a new question to go further."
        else -> null
    }
}
