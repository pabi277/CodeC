package com.codeci.ide.ui.ai

import java.net.URI
import java.net.URISyntaxException

/*
 * Phase 88 (Level 11) — the pure policies behind the agent's phone presentation.
 *
 *  - [AiLinkPolicy]      (88.2) which link targets may ever be offered, and only behind a confirm.
 *  - [AiProgressPolicy]  (88.4) one truthful progress line: the stage, then counters, in one place.
 *  - [AiResultRowPolicy] (88.5) what an activity row may show when tapped: exactly what was packed.
 *
 * Presentation only: nothing here reaches request building, the packer, the tool
 * runner, the key store or the network. java.net.URI only — never android.net.Uri —
 * so every rule is host-testable (minSdk 24 is unaffected: java.net is in the JDK).
 */

// ---- 88.2 link policy ------------------------------------------------------

/** What an answer's link may become. */
sealed class AiLink {
    /** May be offered behind the confirm dialog. [url] is exactly what Open passes to openUri. */
    data class Openable(val url: String, val host: String) : AiLink()

    /** Stays plain text; [reason] is the first rule it failed. */
    data class Inert(val reason: AiLinkRefusal) : AiLink()
}

enum class AiLinkRefusal { NOT_HTTPS, NO_HOST, NON_ASCII_HOST, USERINFO, HIDDEN_CHARACTERS, TOO_LONG, MALFORMED }

/**
 * Model-written links are untrusted (S3). Only `https`, with a plain ASCII host
 * and no user-info or hidden characters, may be offered — and then only behind
 * a dialog that shows the full URL (the owner's 2026-10-03 decision). Everything
 * else (`javascript:`, `data:`, plain `http:`, `intent:`, relative paths…) stays
 * inert text. This is deliberately NOT `MarkdownPreview.safeUrl`, which is a
 * local file-preview allowlist and admits `http:`, `file:` and relative paths.
 */
object AiLinkPolicy {
    const val MAX_URL_CHARS = 2_048

    fun classify(target: String): AiLink {
        // 1. Trim ASCII whitespace at both ends; nothing else is normalised.
        val url = target.trim { it == ' ' || it == '\t' || it == '\n' || it == '\r' || it == '\u000C' }
        // 2. Length.
        if (url.length > MAX_URL_CHARS) return AiLink.Inert(AiLinkRefusal.TOO_LONG)
        // 3. Hidden characters anywhere inside.
        if (url.any(::isHidden)) return AiLink.Inert(AiLinkRefusal.HIDDEN_CHARACTERS)
        // 4. The scheme, case-insensitively (RFC 3986 §3.1). Only https passes.
        val colon = url.indexOf(':')
        if (colon <= 0 || !url.substring(0, colon).equals("https", ignoreCase = true)) {
            return AiLink.Inert(AiLinkRefusal.NOT_HTTPS)
        }
        val rest = url.substring(colon + 1)
        // No authority at all (`https:x`, `https://`, `https:///path`) has no host to show.
        if (!rest.startsWith("//")) return AiLink.Inert(AiLinkRefusal.NO_HOST)
        val authorityEnd = rest.indexOfAny(charArrayOf('/', '?', '#'), startIndex = 2).let { if (it < 0) rest.length else it }
        val authority = rest.substring(2, authorityEnd)
        if (authority.isEmpty()) return AiLink.Inert(AiLinkRefusal.NO_HOST)
        // 5. java.net.URI must accept it.
        try {
            URI(url)
        } catch (_: URISyntaxException) {
            return AiLink.Inert(AiLinkRefusal.MALFORMED)
        }
        // 6. Any user-info: `https://bank.example@evil.example/` really goes to evil.example.
        if (authority.contains('@')) return AiLink.Inert(AiLinkRefusal.USERINFO)
        // 7./8. The host: ASCII letters, digits, '-' and '.', or a bracketed IP literal.
        val host = hostOf(authority) ?: return AiLink.Inert(AiLinkRefusal.MALFORMED)
        if (host.isEmpty()) return AiLink.Inert(AiLinkRefusal.NO_HOST)
        if (!isPlainHost(host)) return AiLink.Inert(AiLinkRefusal.NON_ASCII_HOST)
        // 9. Only the scheme is lowercased; every other character is kept as written.
        return AiLink.Openable("https" + url.substring(colon), host.lowercase())
    }

    /** The host part of an authority without user-info: strips a valid port; null when the port is not digits. */
    private fun hostOf(authority: String): String? {
        if (authority.startsWith('[')) {
            val close = authority.indexOf(']')
            if (close < 0) return null
            val after = authority.substring(close + 1)
            if (after.isNotEmpty() && !(after.startsWith(':') && after.length > 1 && after.substring(1).all { it in '0'..'9' })) return null
            return authority.substring(0, close + 1)
        }
        val colon = authority.lastIndexOf(':')
        if (colon < 0) return authority
        val port = authority.substring(colon + 1)
        if (port.isEmpty() || !port.all { it in '0'..'9' }) return null
        return authority.substring(0, colon)
    }

    private fun isPlainHost(host: String): Boolean {
        if (host.startsWith('[')) {
            val inner = host.substring(1, host.length - 1)
            return inner.isNotEmpty() && inner.all { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' || it == ':' || it == '.' }
        }
        return host.all { it in 'a'..'z' || it in 'A'..'Z' || it in '0'..'9' || it == '-' || it == '.' } &&
            host.any { it in 'a'..'z' || it in 'A'..'Z' || it in '0'..'9' }
    }

    /**
     * Whitespace and controls (U+0000–U+0020, U+007F–U+009F), zero-width and
     * invisible characters (U+200B–U+200F, U+2060–U+2064, U+FEFF) and bidi
     * overrides (U+202A–U+202E, U+2066–U+2069): browsers strip some of these, and
     * bidi overrides make a URL read differently from what it is.
     */
    private fun isHidden(c: Char): Boolean {
        val code = c.code
        return code <= 0x20 || code in 0x7F..0x9F || code in 0x200B..0x200F || code in 0x2060..0x2064 ||
            code == 0xFEFF || code in 0x202A..0x202E || code in 0x2066..0x2069
    }
}

// ---- 88.4 one truthful progress line ----------------------------------------

enum class AiProgressStage {
    WAITING_FOR_MODEL, MODEL_REPLYING, READING_FILES,
    WAITING_FOR_RUN_DECISION, RUNNING_COMMAND, WAITING_TO_RETRY,
    DONE, STOPPED, FAILED
}

/** Everything the stage needs, read from state that already exists (no new field). */
data class AiProgressInput(
    val phase: AiPhase,
    val lastKind: AiAgentStepKind?,
    val answerEmpty: Boolean,
    val runPending: Boolean,
    val runRunning: Boolean,
    val retrying: Boolean
)

/**
 * Phase 88.4 — ONE builder for the agent's progress line, replacing the old
 * pair (the bar's working line counted tool rows as "steps"; the card's usage
 * line counted model turns). Here "steps" means model turns everywhere, the stage is
 * named, the counters are clamped to the task's own caps (Phase 87.6), and
 * [placement] puts exactly one copy on screen.
 */
object AiProgressPolicy {
    enum class Placement { BOTTOM_BAR, CARD, NONE }

    /** First match wins (the 88.4 precedence table). */
    fun stage(input: AiProgressInput): AiProgressStage = when {
        input.phase == AiPhase.FAILED -> AiProgressStage.FAILED
        input.phase == AiPhase.DONE && input.lastKind == AiAgentStepKind.STOPPED -> AiProgressStage.STOPPED
        input.phase == AiPhase.DONE -> AiProgressStage.DONE
        input.retrying -> AiProgressStage.WAITING_TO_RETRY
        input.runRunning -> AiProgressStage.RUNNING_COMMAND
        input.runPending -> AiProgressStage.WAITING_FOR_RUN_DECISION
        input.lastKind == AiAgentStepKind.TOOL || input.lastKind == AiAgentStepKind.DENIED -> AiProgressStage.READING_FILES
        !input.answerEmpty -> AiProgressStage.MODEL_REPLYING
        else -> AiProgressStage.WAITING_FOR_MODEL
    }

    /** Where the one line goes: the bar while work happens, the card's first line after. */
    fun placement(phase: AiPhase, hasAgentSteps: Boolean): Placement = when {
        !hasAgentSteps -> Placement.NONE // a single-shot ask: unchanged, no progress line
        phase == AiPhase.STREAMING -> Placement.BOTTOM_BAR
        phase == AiPhase.DONE || phase == AiPhase.FAILED -> Placement.CARD
        else -> Placement.NONE // IDLE / PREVIEW: nothing has been sent
    }

    /** Stage words, then counters from [usage]; a retry [countdown] replaces the stage words. */
    fun line(stage: AiProgressStage, usage: AiAgentUsage?, countdown: String? = null): String {
        val words = if (stage == AiProgressStage.WAITING_TO_RETRY && countdown != null) countdown else AiCopy.agentProgressStage(stage)
        if (usage == null) return words
        val showRuns = usage.runs > 0 ||
            stage == AiProgressStage.WAITING_FOR_RUN_DECISION || stage == AiProgressStage.RUNNING_COMMAND
        val counters = AiCopy.agentUsageLine(
            turns = usage.turns.coerceIn(0, usage.turnCap),
            toolCalls = usage.toolCalls,
            runs = usage.runs.coerceIn(0, AiAgentLimits.MAX_RUNS),
            refused = usage.refused,
            reused = usage.reused,
            turnCap = usage.turnCap,
            readCap = usage.readCap,
            showRuns = showRuns
        )
        return words + AiCopy.PROGRESS_SEPARATOR + counters
    }
}

// ---- 88.5 activity rows -----------------------------------------------------

/**
 * Phase 88.5 — display-only, stateless. A row's tap view shows exactly the text
 * `AiAgentPrompt.renderStep` packs for it (S1): opening or closing a row reads
 * `modelResult` and writes nothing, so the packed request cannot change.
 */
object AiResultRowPolicy {
    fun hasFullResult(step: AiAgentStep): Boolean = step.modelResult.isNotEmpty()

    /** The same expression renderStep packs: the row shows exactly the model's input for this result. */
    fun fullResult(step: AiAgentStep): String = step.modelResult.take(AiAgentLimits.MAX_RESULT_CHARS)

    /** One line for the collapsed row. TOOL previews start with their honest FILE header. */
    fun teaser(step: AiAgentStep): String =
        step.detail.lineSequence().firstOrNull { it.isNotBlank() }
            ?: step.modelResult.lineSequence().firstOrNull { it.isNotBlank() }.orEmpty()

    fun startsOpen(display: AiActivityDisplay): Boolean = display == AiActivityDisplay.EXPANDED
}
