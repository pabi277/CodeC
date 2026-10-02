package com.codeci.ide.ui.ai

/**
 * Phase 76 — the Gemini request, as pure strings (host-tested by
 * `GeminiRequestTest`). Decision record §4.2:
 *
 *  - Endpoint: stateless `models/{model}:streamGenerateContent?alt=sse`
 *    (https://ai.google.dev/api/generate-content, read 2026-09-30). Each
 *    request carries its own context; there is no conversation id to leak.
 *  - `"store": false` on EVERY body — the field "configures the logging
 *    behavior for a given request … takes precedence over the project-level
 *    logging config" (same page). D6: nothing saved.
 *  - The key travels in the `x-goog-api-key` header ONLY — never `?key=` in
 *    the URL, because URLs end up in exception messages and logs.
 *
 * The body is hand-built (with [json]) instead of `org.json` so a plain JVM
 * test can prove every body's shape without Robolectric.
 */
object GeminiRequest {

    const val HOST = "generativelanguage.googleapis.com"
    private const val BASE = "https://$HOST/v1beta/models/"

    const val HEADER_KEY = "x-goog-api-key"

    /** Short, fixed and content-free: the only text "Test connection" sends. */
    const val TEST_PROMPT = "Reply with the single word OK."

    /** The streaming URL, or null when [model] is not a plain id ([AiModel.isValid]). */
    fun streamUrl(model: String): String? {
        val id = AiModel.normalize(model)
        if (!AiModel.isValid(id)) return null
        return "$BASE$id:streamGenerateContent?alt=sse"
    }

    /** Headers for one request. The key appears here and nowhere else. */
    fun headers(apiKey: String): Map<String, String> = linkedMapOf(
        "Content-Type" to "application/json; charset=utf-8",
        "Accept" to "text/event-stream",
        HEADER_KEY to apiKey,
        "User-Agent" to "CodeC-IDE"
    )

    /** The body for one helper request, built from the SAME strings the preview shows. */
    fun body(prompt: AiPrompt): String = body(
        systemInstruction = prompt.systemInstruction,
        userText = prompt.userText,
        maxOutputTokens = AiProviders.outputBudget(AiProviderId.GEMINI, prompt.model)
    )

    /** "Test connection": no code, no project text — [TEST_PROMPT] only. */
    fun testBody(): String = body(systemInstruction = null, userText = TEST_PROMPT, maxOutputTokens = AiLimits.MAX_OUTPUT_TOKENS)

    fun body(systemInstruction: String?, userText: String, maxOutputTokens: Int): String = buildString {
        append('{')
        if (systemInstruction != null) {
            append("\"systemInstruction\":{\"parts\":[{\"text\":").append(json(systemInstruction)).append("}]},")
        }
        append("\"contents\":[{\"role\":\"user\",\"parts\":[{\"text\":").append(json(userText)).append("}]}],")
        append("\"generationConfig\":{\"maxOutputTokens\":").append(maxOutputTokens).append("},")
        append("\"store\":false")
        append('}')
    }

    /** A JSON string literal (RFC 8259 §7): quotes, backslash and every control character escaped. */
    fun json(s: String): String = buildString(s.length + 2) {
        append('"')
        for (c in s) {
            when (c) {
                '"' -> append("\\\"")
                '\\' -> append("\\\\")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                '\b' -> append("\\b")
                '\u000C' -> append("\\f")
                // U+2028/2029 are legal JSON but break some JS-based parsers; escaping costs nothing.
                '\u2028' -> append("\\u2028")
                '\u2029' -> append("\\u2029")
                else -> if (c < ' ') append("\\u%04x".format(c.code)) else append(c)
            }
        }
        append('"')
    }
}

/**
 * Server-Sent Events, the subset Gemini's `alt=sse` stream uses: `data:`
 * lines accumulate, a blank line dispatches, `:` comments and `event:`/`id:`/
 * `retry:` fields are ignored. Fed one line at a time (`BufferedReader.readLine`
 * already strips CR/LF), so it is pure and host-tested (`SseLineSplitterTest`).
 */
class SseLineSplitter {
    private val data = StringBuilder()
    private var hasData = false

    /** Feeds one line; returns a complete event's data when [line] ends one, else null. */
    fun feed(line: String): String? {
        val l = line.removeSuffix("\r")
        if (l.isEmpty()) return dispatch()
        if (l.startsWith(":")) return null
        val colon = l.indexOf(':')
        val field = if (colon < 0) l else l.substring(0, colon)
        var value = if (colon < 0) "" else l.substring(colon + 1)
        if (value.startsWith(" ")) value = value.substring(1)
        if (field == "data") {
            if (hasData) data.append('\n')
            data.append(value)
            hasData = true
        }
        return null
    }

    /** End of stream: a last event without its trailing blank line still counts. */
    fun finish(): String? = dispatch()

    private fun dispatch(): String? {
        if (!hasData) return null
        val out = data.toString()
        data.setLength(0)
        hasData = false
        return out
    }
}
