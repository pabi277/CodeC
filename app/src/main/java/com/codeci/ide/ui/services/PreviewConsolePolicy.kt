package com.codeci.ide.ui.services

import java.util.Locale

/**
 * Phase 72.1 (2026-09-28) — the preview console's **command line**, as a pure
 * policy.
 *
 * The owner's own shots (SPCK's Web Preview, `uploads/…134212`, `…134218`) show
 * a console with a caret, and **Cancel · Execute** above the keyboard; his
 * report was *“i can't run any console command”*. So the console takes a typed
 * JavaScript line now. What stays pure and host-testable is everything between
 * the keyboard and the WebView:
 *
 * - [command] wraps one typed line into the script `evaluateJavascript` runs;
 * - [quote] encodes the typed line so no quote, backslash or newline in it can
 *   break out of that script;
 * - [unquote] decodes what `evaluateJavascript` answers (a JSON string);
 * - [result] turns that answer into the console line the user reads;
 * - [echo] is the `› command` line the console shows for what was typed.
 *
 * The wrapper returns `ok`/`err` plus a payload separated by `U+0001`, so no
 * JSON parser and no Android API is needed to read a result — the whole path
 * is exercised on a host JVM, including the escaping.
 */
object PreviewConsolePolicy {

    /** One typed line is bounded like every other console payload. */
    const val COMMAND_LIMIT = 4096
    const val RESULT_LIMIT = 4096

    private const val UNIT = '\u0001'
    private const val OK = "ok\u0001"
    private const val ERR = "err\u0001"

    /**
     * The script for one typed line, or null when there is nothing to run.
     *
     * `eval` runs the line in the page's own context, which is the point: the
     * console is the page's console. The result is stringified for display —
     * objects through `JSON.stringify`, everything else through `String` — and
     * a thrown error becomes an `err` payload instead of a swallowed callback.
     */
    fun command(text: String): String? {
        val line = text.trim().take(COMMAND_LIMIT)
        if (line.isEmpty()) return null
        return "(function(){try{var v=eval(${quote(line)});var s;" +
            "if(typeof v==='undefined')s='undefined';" +
            "else if(v===null)s='null';" +
            "else if(typeof v==='object'){try{s=JSON.stringify(v);}catch(e2){s=String(v);}}" +
            "else s=String(v);" +
            "return 'ok\\u0001'+s;" +
            "}catch(e){return 'err\\u0001'+((e&&e.message)?e.message:String(e));}})()"
    }

    /** A JSON/JavaScript string literal for [text]; nothing in it can escape. */
    fun quote(text: String): String {
        val out = StringBuilder(text.length + 2)
        out.append('"')
        for (c in text) {
            when {
                c == '"' -> out.append("\\\"")
                c == '\\' -> out.append("\\\\")
                c == '\n' -> out.append("\\n")
                c == '\r' -> out.append("\\r")
                c == '\t' -> out.append("\\t")
                c.code < 0x20 || c == '\u2028' || c == '\u2029' ->
                    out.append(String.format(Locale.ROOT, "\\u%04x", c.code))
                else -> out.append(c)
            }
        }
        out.append('"')
        return out.toString()
    }

    /**
     * Decode an `evaluateJavascript` answer. WebView hands back a JSON string
     * (`"ok\u0001hello\"world"`), and the two characters WebView never escapes
     * for us are the ones we must: the pair of quotes it wraps the value in.
     * A value that is not a quoted literal is returned trimmed, never dropped.
     */
    fun unquote(json: String?): String {
        val text = json?.trim().orEmpty()
        if (text.length < 2 || text.first() != '"' || text.last() != '"') return text
        val body = text.substring(1, text.length - 1)
        val out = StringBuilder(body.length)
        var i = 0
        while (i < body.length) {
            val c = body[i]
            if (c != '\\') {
                out.append(c)
                i++
                continue
            }
            i++
            if (i >= body.length) break
            when (val escaped = body[i]) {
                'n' -> out.append('\n')
                'r' -> out.append('\r')
                't' -> out.append('\t')
                'b' -> out.append('\b')
                'f' -> out.append('\u000C')
                'u' -> {
                    val hex = body.substring(i + 1, minOf(body.length, i + 5))
                    val code = hex.toIntOrNull(16)
                    if (code == null) {
                        out.append(escaped)
                    } else {
                        out.append(code.toChar())
                        i += 4
                    }
                }
                else -> out.append(escaped)
            }
            i++
        }
        return out.toString()
    }

    /**
     * The console line for one evaluation. An empty [raw] is a refused
     * evaluation (the view was gone), not a success — it reads as a failure
     * rather than pretending the command ran.
     */
    fun result(raw: String?): PreviewConsoleEntry {
        val value = unquote(raw)
        val ok = value.startsWith(OK)
        val payload = when {
            ok -> value.removePrefix(OK)
            value.startsWith(ERR) -> value.removePrefix(ERR)
            else -> value
        }
        val message = when {
            payload.isNotEmpty() -> payload
            ok -> "\"\"" // A string result that really was empty.
            else -> "Command could not be evaluated."
        }
        return PreviewConsoleEntry(
            level = if (ok) PreviewLevel.LOG else PreviewLevel.ERROR,
            message = message.take(RESULT_LIMIT),
            line = 0,
        )
    }

    /**
     * The console shows what was typed before it shows what came back, exactly
     * like a REPL. `INFO` is the level the filter row calls “Info”, so the echo
     * obeys the same filter as every other line — a console filtered to errors
     * only is a console of errors only, typed lines included.
     */
    fun echo(text: String): PreviewConsoleEntry = PreviewConsoleEntry(
        level = PreviewLevel.INFO,
        message = "› ${text.trim().take(COMMAND_LIMIT)}",
        line = 0,
    )
}
