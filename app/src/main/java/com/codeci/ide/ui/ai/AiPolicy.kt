package com.codeci.ide.ui.ai

import com.codeci.ide.ui.editor.EditorOpenMode

/**
 * Phase 76 (AI Level 1) — the read-only Gemini helper's decisions, as pure
 * code (rule.md §4.4: Android-free, host-tested by `AiPolicyTest`).
 *
 * Every rule here traces to the owner's Level 0 answers
 * (`docs/roadmaps/ai-integration/00_LEVEL0_DECISION_RECORD.md`):
 *  - D1 read-only: there is no apply/run/write action anywhere in this package.
 *  - D4 preview every request: [AiLimits] caps what a preview can hold, so the
 *    user always sees the exact, whole text that is sent.
 *  - D5 open project only: [AiGate.availability] refuses single-file and
 *    scratch mode.
 *  - O1 key-setup gate: [AiKeySetup.canSave] needs the 18+/terms checkbox.
 *  - O3 model: [AiModel.DEFAULT] pre-fills the field; [AiModel.isValid]
 *    keeps whatever the user types from ever becoming part of a URL path it
 *    was not meant to be.
 */
object AiLimits {
    /** The most characters of code/output one request may carry (shown in the preview). */
    const val MAX_CONTEXT_CHARS = 12_000

    /** The user's own optional question. */
    const val MAX_QUESTION_CHARS = 1_000

    /** Run output: at most this many of the newest lines are offered. */
    const val MAX_OUTPUT_LINES = 60

    /** The streamed answer stops growing past this (and says so). */
    const val MAX_REPLY_CHARS = 24_000

    /**
     * Asked of the model too. Generous on purpose: Gemini models "often have
     * thinking enabled by default" (ai.google.dev text-generation guide, read
     * 2026-09-30), and a tight cap can be spent before any visible answer.
     * [MAX_REPLY_CHARS] is the real bound on what the panel shows.
     */
    const val MAX_OUTPUT_TOKENS = 8_192

    const val CONNECT_TIMEOUT_MS = 15_000
    const val READ_TIMEOUT_MS = 60_000
}

/** Why the AI panel can or cannot be used right now. */
enum class AiAvailability {
    /** No project is open (single-file or scratch mode) — D5. */
    NEEDS_PROJECT,

    /** A project is open but no key has been saved yet. */
    NEEDS_KEY,

    READY
}

object AiGate {
    /**
     * D5: the helper works only inside an open CodeC project. Single-file
     * mode and scratch files (no project name) never offer it.
     */
    fun availability(singleFile: Boolean, projectName: String?, hasKey: Boolean): AiAvailability = when {
        singleFile || projectName.isNullOrBlank() -> AiAvailability.NEEDS_PROJECT
        !hasKey -> AiAvailability.NEEDS_KEY
        else -> AiAvailability.READY
    }

    /**
     * The editor's own mode answer (`EditorOpenModePolicy`): only
     * [EditorOpenMode.PROJECT] is a project. SINGLE_FILE keeps a project name
     * set internally (its save/run root), so the name alone must never decide.
     */
    fun availability(mode: EditorOpenMode, projectName: String?, hasKey: Boolean): AiAvailability =
        availability(singleFile = mode != EditorOpenMode.PROJECT, projectName = projectName, hasKey = hasKey)

    /**
     * Whether the latest run has an error worth explaining: the run pipeline
     * said FAILED, a build/run exit code was non-zero, or the editor parsed
     * diagnostics. A clean run offers nothing.
     */
    fun runFailed(phaseFailed: Boolean, buildExitCode: Int?, runExitCode: Int?, hasDiagnostics: Boolean): Boolean =
        phaseFailed || (buildExitCode ?: 0) != 0 || (runExitCode ?: 0) != 0 || hasDiagnostics
}

/** O1 — the key is saved only after the 18+ and Google-terms confirmation. */
object AiKeySetup {
    /**
     * Bump when the confirmation text changes in a way that needs a fresh
     * acceptance (e.g. a Google terms change CodeC must re-show).
     */
    const val TERMS_VERSION = 1

    const val MIN_KEY_LENGTH = 20
    const val MAX_KEY_LENGTH = 200

    /** The key as it will be stored: surrounding whitespace removed, nothing else changed. */
    fun normalize(raw: String): String = raw.trim()

    /**
     * Only shape checks — never a claim that the key is valid (Google decides
     * that; "Test connection" asks). No inner whitespace, no control
     * characters, sane length. The `AIza` prefix is deliberately NOT required:
     * key formats are Google's to change.
     */
    fun looksLikeKey(raw: String): Boolean {
        val key = normalize(raw)
        if (key.length !in MIN_KEY_LENGTH..MAX_KEY_LENGTH) return false
        return key.all { it.code in 0x21..0x7E }
    }

    /** Save stays disabled until the key looks sane AND the checkbox is ticked. */
    fun canSave(raw: String, confirmedAdultAndTerms: Boolean): Boolean =
        confirmedAdultAndTerms && looksLikeKey(raw)

    /** A stored acceptance counts only for the current [TERMS_VERSION]. */
    fun acceptanceValid(storedVersion: Int?): Boolean = storedVersion == TERMS_VERSION
}

object AiModel {
    /**
     * O3 — pre-filled, editable. Phase 76 device round 1 (owner, 2026-10-01):
     * *"Every test passed just i have to use gemini-3-flash-preview this
     * model"* — `gemini-3.8-flash` (read off Google's docs when the phase was
     * briefed) did not answer for his key; `gemini-3-flash-preview` did, on a
     * real phone. The device result wins over the docs.
     */
    const val DEFAULT = "gemini-3-flash-preview"

    private val ID = Regex("^[a-z0-9][a-z0-9.\\-]{1,62}[a-z0-9]$")

    /**
     * A model id becomes one path segment of the request URL, so it must be
     * a plain id: lowercase letters, digits, dots and dashes. No `/`, `?`,
     * `:`, `%` or spaces can ever reach the URL.
     */
    fun isValid(id: String): Boolean = ID.matches(id.trim())

    fun normalize(id: String): String = id.trim()
}
