package com.codeci.ide.ui.support

import com.codeci.ide.ui.projects.GitRedactor

/**
 * Phase 41.1 — the inputs of one feedback report.
 *
 * Everything here is plain data so the report is built by a PURE function
 * ([FeedbackDraft.build]): no clock, no Android, no I/O. The same input
 * twice gives the same string, which is pinned by test — a device round can
 * diff two reports meaningfully. Timestamps, versions and the device facts
 * all come in from the caller; this class only carries them.
 *
 * @param project the project the user last had open in the editor, or null.
 * @param screen where the report was composed from ("Settings", later the
 *   crash overlay via 42.3) so the owner can tell a crash report from a
 *   typed one at a glance.
 * @param maxChars the budget the REPORT text must fit (WhatsApp URLs have
 *   to survive OEM browsers; ~2 k is the documented truncation zone, so the
 *   default leaves headroom). `Int.MAX_VALUE` means "no budget" — the COPY
 *   FULL REPORT escape hatch, which has no length limit.
 * @param secretToScrub the stored git token, ONLY so [FeedbackDraft.redact]
 *   can scrub its literal from log/crash lines. It is never rendered.
 */
data class FeedbackInput(
    val appVersion: String,
    val androidRelease: String,
    val apiLevel: Int,
    val device: String,
    val abis: String,
    val project: String? = null,
    val screen: String? = null,
    val userText: String = "",
    val includeLog: Boolean = false,
    val logTail: List<String> = emptyList(),
    val includeCrash: Boolean = false,
    val crashRecord: String? = null,
    val maxChars: Int = FeedbackDraft.WHATSAPP_BUDGET,
    val secretToScrub: String? = null,
    val paths: FeedbackDraft.RedactionPaths = FeedbackDraft.RedactionPaths()
)

/**
 * Phase 41.1 — the feedback report and the links that carry it, as pure
 * code (host-testable, the house pattern). Four jobs:
 *
 *  1. `build` — one fixed report layout the owner can skim: header line
 *     (version · Android · device · ABI), project/screen line, *what I saw*
 *     (the user's text, verbatim, never re-wrapped and never cut by the
 *     budget), then the optional log tail and crash record sections. The
 *     budget trims the LOG first (oldest lines go), then the CRASH (from
 *     the bottom — the header + exception line are the diagnosis), and
 *     NEVER the user's own text; when a trim happened the report says so
 *     and the caller offers COPY FULL REPORT, which has no budget.
 *  2. `redact` — secrets and private paths are scrubbed BEFORE any budget
 *     is applied, so a budget cut can never keep a secret a later line
 *     would have cut. Two code paths only (the law of PART_41_1): this
 *     object's token-shape table plus [GitRedactor] (the stored literal +
 *     `user:password@` URL credentials).
 *  3. `normaliseNumber` — an owner-pasted phone number becomes strict
 *     E.164 digits or null, because a wrong number is the documented #1
 *     failure of a wa.me link (it opens a chat with the WRONG person).
 *  4. the URL builders — wa.me / mailto / GitHub new-issue, all UTF-8
 *     percent-encoded (a raw `\n` in a URL is a documented no-op; a stray
 *     `%` truncates everything after it).
 *
 * Sources: the wa.me click-to-chat guides (u2l.ai, dotdigital, wati.io —
 * international digits, no `+`/spaces/dashes/trunk-0, `?text=` encoded,
 * message lands in the composer UNSENT) and ITU-T E.164's own bounds
 * (8–15 digits, country code 1–3 digits, no leading 0 in the international
 * number). Full list in docs/chat-phase41/PART_41_1_REPORT_AND_LINKS.md.
 */
object FeedbackDraft {

    /** Report budget for the WhatsApp path (URLs truncate around ~2 k). */
    const val WHATSAPP_BUDGET = 1800

    /** The log section carries at most the newest 120 ring lines. */
    const val MAX_LOG_LINES = 120

    /**
     * The crash section carries the record's `==== ` header + the exception
     * line + at most 12 frames (PART_41_1 layout). The full record stays in
     * CrashReportOverlay's COPY ALL — one sink, one reader list (41.2).
     */
    const val MAX_CRASH_LINES = 14

    const val LOG_TRIM_NOTICE = "[log trimmed — use COPY FULL REPORT for the whole thing]"
    const val CRASH_TRIM_NOTICE = "[crash trimmed — use COPY FULL REPORT for the whole thing]"

    /** Where the report's project line points when nothing is open. */
    const val NO_PROJECT = "(none)"

    /** What the *what I saw* section says when the user typed nothing. */
    const val NO_TEXT = "(no text)"

    /**
     * Private-path shortening. The defaults are the standard app-private
     * spellings (kept as data so tests can pin them); the UI passes the
     * device's real `filesDir` via [RedactionPaths.forApp]. Both
     * `/data/user/0/<pkg>/…` and `/data/data/<pkg>/…` spellings are
     * shortened (they are the same directory; a log line may carry either).
     */
    data class RedactionPaths(
        val appFilesDir: String = "/data/user/0/com.codeci.ide/files",
        val projectsDir: String = appFilesDir + "/CodeC/projects",
        val userlandHome: String = appFilesDir + "/usr"
    ) {
        companion object {
            fun forApp(filesDir: String): RedactionPaths {
                val base = filesDir.trimEnd('/')
                return RedactionPaths(
                    appFilesDir = base,
                    projectsDir = "$base/CodeC/projects",
                    userlandHome = "$base/usr"
                )
            }
        }
    }

    // ---------------------------------------------------------------------
    // 1. The report
    // ---------------------------------------------------------------------

    fun build(i: FeedbackInput): String {
        // Redaction BEFORE truncation (the law): a budget cut can never keep
        // a secret a later line would have cut.
        val logAll = if (i.includeLog && i.logTail.isNotEmpty()) {
            redact(i.logTail, i.secretToScrub, i.paths).takeLast(MAX_LOG_LINES)
        } else {
            emptyList()
        }
        val crashAll = if (i.includeCrash && !i.crashRecord.isNullOrBlank()) {
            redact(i.crashRecord.lines(), i.secretToScrub, i.paths).take(MAX_CRASH_LINES)
        } else {
            emptyList()
        }

        /** Assemble with logCount newest log lines and crashCount top crash lines. */
        fun assemble(logCount: Int, crashCount: Int): String = buildString {
            append("CodeC ").append(i.appVersion.trim())
            append(" · Android ").append(i.androidRelease.trim())
            append(" (API ").append(i.apiLevel).append(')')
            append(" · ").append(i.device.trim().ifEmpty { "unknown device" })
            append(" · ").append(i.abis.trim().ifEmpty { "unknown abi" })
            append("\nProject: ").append(i.project?.trim()?.takeIf { it.isNotEmpty() } ?: NO_PROJECT)
            i.screen?.trim()?.takeIf { it.isNotEmpty() }?.let { append(" · Screen: ").append(it) }
            append("\n--- what I saw ---\n")
            // The user's own text: verbatim, never re-wrapped, never cut.
            append(i.userText.trim().ifEmpty { NO_TEXT })
            if (i.includeLog) {
                if (logCount > 0) {
                    append("\n--- log (last ").append(logCount).append(" lines, redacted) ---\n")
                    append(logAll.takeLast(logCount).joinToString("\n"))
                    if (logCount < logAll.size) append('\n').append(LOG_TRIM_NOTICE)
                } else if (logAll.isNotEmpty()) {
                    // The budget ate the whole log section — say so where the
                    // section would have been, never silently.
                    append('\n').append(LOG_TRIM_NOTICE)
                }
            }
            if (i.includeCrash) {
                if (crashCount > 0) {
                    append("\n--- crash ---\n")
                    append(crashAll.take(crashCount).joinToString("\n"))
                    if (crashCount < crashAll.size) append('\n').append(CRASH_TRIM_NOTICE)
                } else if (crashAll.isNotEmpty()) {
                    append('\n').append(CRASH_TRIM_NOTICE)
                }
            }
        }

        val full = assemble(logAll.size, crashAll.size)
        if (full.length <= i.maxChars) return full

        // Over budget: trim the LOG first (oldest lines go — the newest ring
        // lines are the ones nearest the failure)…
        for (logCount in logAll.size - 1 downTo 0) {
            val candidate = assemble(logCount, crashAll.size)
            if (candidate.length <= i.maxChars) return candidate
        }
        // …then the CRASH, from the bottom (the record's first lines — its
        // header and the exception line — are the diagnosis and stay).
        for (crashCount in crashAll.size - 1 downTo 1) {
            val candidate = assemble(0, crashCount)
            if (candidate.length <= i.maxChars) return candidate
        }
        // Nothing left to cut but the user's text — which is never cut. A
        // report that is over budget because the human typed a lot is the
        // correct output; the caller's COPY FULL REPORT has no budget anyway.
        return assemble(0, if (crashAll.isEmpty()) 0 else 1)
    }

    // ---------------------------------------------------------------------
    // 2. Redaction (before truncation, before anything is shown)
    // ---------------------------------------------------------------------

    /**
     * The two code paths of feedback redaction (PART_41_1 law):
     *  1. the token-shape table below — a bug report must scrub ANY secret
     *     that could be in it (a `gh` failure, a curl the user typed, a
     *     future feature logging the wrong thing), not just the one secret
     *     one call site injected. [GitRedactor] is written for "scrub the
     *     secret we just injected into this child process"; this is the
     *     different question "scrub any secret that could possibly be in
     *     here".
     *  2. [GitRedactor] itself — the stored literal (when known) and
     *     `user:password@` URL credentials (its `redactUrls`, so the rule
     *     is not duplicated here).
     * Plus private-path shortening (~proj/~home/~app), which is cosmetic —
     * but honest: `/storage/…` and account names have no business in a
     * report the owner skims.
     */
    fun redact(
        lines: List<String>,
        secret: String? = null,
        paths: RedactionPaths = RedactionPaths()
    ): List<String> {
        if (lines.isEmpty()) return lines
        val shaped = lines.map(::redactTokenShapes)
        return GitRedactor(secret).redactAll(shaped).map { shortenPaths(it, paths) }
    }

    /** GitHub classic PAT (real ones are 36–40 chars; 8 is the safer floor — a truncated paste is still a secret). */
    private val shapeClassicPat = Regex("ghp_[A-Za-z0-9]{8,}")

    /** Fine-grained PAT (github_pat_ + base64-ish). */
    private val shapeFineGrainedPat = Regex("github_pat_[A-Za-z0-9_]{8,}")

    /** Server / OAuth / user / refresh tokens. */
    private val shapeGhFamily = Regex("gh[soru]_[A-Za-z0-9]{8,}")

    /** The CI-style remote username spelling: x-access-token:<secret>. */
    private val shapeXAccessToken = Regex("(?i)(x-access-token:)[A-Za-z0-9_\\-]+")

    /** Header-style credentials, with or without a scheme word. */
    private val shapeAuthorization = Regex("(?i)(authorization\\s*:\\s*(?:bearer|token|basic)?\\s*)[A-Za-z0-9\\-._~+/]+=*")

    /**
     * KEY=VALUE shapes — `CODEC_GIT_TOKEN=…`, `client_secret=…`,
     * `api_key: …`. Over-redaction is the safe direction here: the value
     * word is replaced, the key name is kept so the log line stays useful.
     */
    private val shapeKeyValue = Regex("(?i)\\b([A-Za-z0-9_]*(?:token|secret|password|passwd|api[_-]?key)[A-Za-z0-9_]*)\\s*[:=]\\s*[^\\s,;]+")

    private fun redactTokenShapes(line: String): String {
        var r = line
        r = r.replace(shapeClassicPat, "<redacted>")
        r = r.replace(shapeFineGrainedPat, "<redacted>")
        r = r.replace(shapeGhFamily, "<redacted>")
        r = r.replace(shapeXAccessToken, "$1<redacted>")
        r = r.replace(shapeAuthorization, "$1<redacted>")
        r = r.replace(shapeKeyValue, "$1=<redacted>")
        return r
    }

    private fun shortenPaths(line: String, p: RedactionPaths): String {
        val rules = listOf(
            p.projectsDir to "~proj",
            p.userlandHome to "~home",
            p.appFilesDir to "~app"
        )
        var r = line
        for ((dir, replacement) in rules) {
            for (spelling in alternateSpellings(dir)) {
                // A guard so a too-short custom dir cannot replace half the
                // report; the standard spellings are far longer than this.
                if (spelling.length >= 8 && r.contains(spelling)) {
                    r = r.replace(spelling, replacement)
                }
            }
        }
        return r
    }

    /** `/data/user/0/<pkg>/…` and `/data/data/<pkg>/…` are the same directory. */
    private fun alternateSpellings(dir: String): List<String> {
        val d = dir.trimEnd('/')
        if (d.isEmpty()) return emptyList()
        val out = mutableListOf(d)
        when {
            d.startsWith("/data/user/") -> {
                // "0/<pkg>/…" → "<pkg>/…" under /data/data
                val rest = d.removePrefix("/data/user/")
                val tail = rest.substringAfter('/')
                if (tail.isNotEmpty()) out += "/data/data/$tail"
            }
            d.startsWith("/data/data/") -> {
                out += "/data/user/0/" + d.removePrefix("/data/data/")
            }
        }
        return out
    }

    // ---------------------------------------------------------------------
    // 3. The number (strict, because a wrong number opens the wrong chat)
    // ---------------------------------------------------------------------

    /**
     * `"+91 98765 43210"` → `919876543210`; anything suspicious → null.
     *
     * Rules (E.164's own structure, not a regex vibe):
     *  - separators (space, dash, parens, dot, NBSP) are dropped; a leading
     *    `+` is dropped; a leading `00` (the international dial prefix) is
     *    dropped. ANY other character rejects the input.
     *  - the remainder must be 8–15 digits (E.164's own bound).
     *  - the first 1–3 digits must be an ASSIGNED country code, and the
     *    rest must be 7–12 digits that do NOT start with `0` — a pasted
     *    trunk `0` (or a domestic `8-800-…` form) is the #1 way a wa.me
     *    link ends up in the wrong place. Known cost, recorded in
     *    PART_41_1: Italian-style landlines that legitimately keep a `0`
     *    (+39 06 …) are rejected; the number stored here is the OWNER's
     *    single support number, so strictness beats completeness.
     */
    fun normaliseNumber(raw: String): String? {
        var s = raw.trim()
        if (s.startsWith("+")) s = s.substring(1)
        val digits = StringBuilder()
        for (c in s) {
            when (c) {
                ' ', '-', '(', ')', '.', '\u00A0' -> Unit // separators
                in '0'..'9' -> digits.append(c)
                else -> return null // letters, slashes, a stray '+' mid-number…
            }
        }
        var d = digits.toString()
        if (d.startsWith("00")) d = d.substring(2)
        if (d.length !in 8..15) return null
        if (d.startsWith("0")) return null
        for (ccLen in 3 downTo 1) {
            if (d.substring(0, ccLen) in COUNTRY_CODES) {
                val subscriber = d.substring(ccLen)
                if (subscriber.length in 7..12 && !subscriber.startsWith("0")) return d
            }
        }
        return null
    }

    /**
     * The ITU-T E.164 assigned country codes (public numbering-plan data).
     * Not an exhaustive numbering-plan validator — it exists so a domestic
     * format (trunk 0, `8-800-…`) is rejected instead of silently opening a
     * chat with whatever those digits mean internationally.
     */
    private val COUNTRY_CODES: Set<String> = setOf(
        // Zone 1 / 7
        "1", "7",
        // Zone 2 (Africa + Atlantic)
        "20", "27",
        "211", "212", "213", "216", "218", "220", "221", "222", "223", "224",
        "225", "226", "227", "228", "229", "230", "231", "232", "233", "234",
        "235", "236", "237", "238", "239", "240", "241", "242", "243", "244",
        "245", "246", "247", "248", "249", "250", "251", "252", "253", "254",
        "255", "256", "257", "258", "260", "261", "262", "263", "264", "265",
        "266", "267", "268", "269", "290", "291", "297", "298", "299",
        // Zones 3–4 (Europe)
        "30", "31", "32", "33", "34", "36", "39", "40", "41", "43", "44",
        "45", "46", "47", "48", "49",
        "350", "351", "352", "353", "354", "355", "356", "357", "358", "359",
        "370", "371", "372", "373", "374", "375", "376", "377", "378", "379",
        "380", "381", "382", "383", "385", "386", "387", "389",
        "420", "421", "423",
        // Zone 5 (Americas)
        "51", "52", "53", "54", "55", "56", "57", "58",
        "500", "501", "502", "503", "504", "505", "506", "507", "508", "509",
        "590", "591", "592", "593", "594", "595", "596", "597", "598", "599",
        // Zone 6 (SE Asia + Oceania)
        "60", "61", "62", "63", "64", "65", "66",
        "670", "672", "673", "674", "675", "676", "677", "678", "679", "680",
        "681", "682", "683", "685", "686", "687", "688", "689", "690", "691",
        "692",
        // Zone 8 (East Asia + international networks)
        "81", "82", "84", "86",
        "850", "852", "853", "855", "856",
        "870", "875", "876", "877", "878", "880", "881", "882", "883", "886",
        "888",
        // Zone 9 (West/South Asia + Middle East)
        "90", "91", "92", "93", "94", "95", "98",
        "960", "961", "962", "963", "964", "965", "966", "967", "968",
        "970", "971", "972", "973", "974", "975", "976", "977", "979",
        "992", "993", "994", "995", "996", "998"
    )

    // ---------------------------------------------------------------------
    // 4. The links (every one optional, every one encoded)
    // ---------------------------------------------------------------------

    /**
     * `https://wa.me/<digits>?text=<encoded>` — or null when the number is
     * unusable (the caller then HIDES the row instead of building a link
     * that opens a chat with the wrong person). The message lands in the
     * composer UNSENT; the user always presses send.
     */
    fun whatsappUrl(numberE164: String, text: String): String? {
        val n = normaliseNumber(numberE164) ?: return null
        return "https://wa.me/$n?text=" + encode(text)
    }

    /** A `mailto:` composer link (no account needed, composes offline). */
    fun mailto(address: String, text: String, subject: String = "CodeC feedback"): String =
        "mailto:" + address.trim() + "?subject=" + encode(subject) + "&body=" + encode(text)

    /** A prefilled new-issue URL — plain link, no token, no API call. */
    fun gitHubIssueUrl(owner: String, repo: String, title: String, body: String): String =
        "https://github.com/" + owner.trim() + "/" + repo.trim() +
            "/issues/new?title=" + encode(title) + "&body=" + encode(body)

    /**
     * UTF-8 percent-encoding, RFC 3986 unreserved characters kept
     * (`A–Z a–z 0–9 - . _ ~`). Spaces are `%20` (a `+` is NOT a space in a
     * query under every handler's interpretation), newlines `%0A`, `%`
     * itself `%25`; non-Latin text (Hindi, Bengali) and emoji encode as
     * their UTF-8 byte sequences — all pinned by test.
     */
    private fun encode(s: String): String {
        val bytes = s.toByteArray(Charsets.UTF_8)
        val sb = StringBuilder(bytes.size + 16)
        for (b in bytes) {
            val c = b.toInt() and 0xFF
            if (isUnreserved(c)) {
                sb.append(c.toChar())
            } else {
                sb.append('%').append(HEX[c ushr 4]).append(HEX[c and 0x0F])
            }
        }
        return sb.toString()
    }

    private fun isUnreserved(c: Int): Boolean =
        (c in 'A'.code..'Z'.code) || (c in 'a'.code..'z'.code) ||
            (c in '0'.code..'9'.code) ||
            c == '-'.code || c == '.'.code || c == '_'.code || c == '~'.code

    private const val HEX = "0123456789ABCDEF"
}
