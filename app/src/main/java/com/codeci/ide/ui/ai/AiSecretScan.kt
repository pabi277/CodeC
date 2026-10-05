package com.codeci.ide.ui.ai

/**
 * Phase 94 — **the content-level secret guard**: the second half of the
 * credential rule.
 *
 * Phase 78/79 refuse a file whose *name* looks like a credential store
 * ([AiProjectFiles.isSecretLike]) — `.env`, `id_rsa`, `*.pem`. That rule has a
 * blind spot the wider tool surface makes bigger: a perfectly ordinary file can
 * carry a credential *value* — `config.py` holding `GEMINI_API_KEY = "AIza…"`,
 * a test fixture with a real token pasted into it, a build log line printing a
 * secret. The agent now reads more files, so this object redacts the value
 * itself before any of it can travel.
 *
 * ## The rules (deliberately over-inclusive, like the name list)
 *
 *  1. **Known credential shapes** are redacted wherever they appear: Google
 *     (`AIza…`), NVIDIA (`nvapi-…`), OpenAI/Anthropic-style (`sk-…`), GitHub
 *     (`ghp_…`, `github_pat_…`), Slack (`xox…-…`), GitLab (`glpat-…`), AWS
 *     (`AKIA…`) and JWTs (`eyJ….eyJ….…`).
 *  2. **Private-key blocks** (PEM `BEGIN … PRIVATE KEY` … `END …`) are replaced
 *     by one marker line — a real key file can never be read anyway, but a key
 *     pasted into a source file is caught here.
 *  3. **Assignment-shaped lines** whose *name* says credential
 *     (`api_key`, `secret`, `token`, `password`, `credential`, `client_secret`,
 *     `private_key`, …) and whose *value* is a plausible literal — quoted, or a
 *     bare token of at least [MIN_VALUE_CHARS] characters that is not a
 *     placeholder — get their value replaced.
 *  4. **Placeholders and code are left alone**: `os.environ["X"]`,
 *     `System.getenv(…)`, `process.env.X`, `<your-key>`, `YOUR_API_KEY`,
 *     `changeme`, `xxxx`, `null`, `""`, or any value with brackets/parens is a
 *     reference, not a secret, and redacting it would only cost the model
 *     context.
 *
 * Nothing is ever hidden silently: every redaction is replaced with [MARKER],
 * and when a result carried any, one honest count line is appended
 * ([noteFor]) so the model — and the owner's timeline — can see that something
 * was withheld rather than wonder why a line looks odd.
 *
 * Pure Kotlin: strings and regexes only, no `android.*`, no `java.io`, so
 * `AiSecretScanTest` runs on the host JVM.
 */
object AiSecretScan {

    /** What replaces one withheld value. Short, unmistakable, greppable. */
    const val MARKER = "[redacted: credential-shaped value]"

    /** One line for a whole withheld PEM block. */
    const val KEY_BLOCK_MARKER = "[redacted: private key block]"

    /** A value shorter than this is never treated as a secret literal. */
    const val MIN_VALUE_CHARS = 8

    data class Result(val text: String, val redactions: Int) {
        val clean: Boolean get() = redactions == 0
    }

    /** `[withheld: 2 credential-shaped value(s)]` — appended once per result. */
    fun noteFor(redactions: Int): String = "[withheld: $redactions credential-shaped value(s)]"

    /** The complete, one-place rule: redact [text], and say how much was withheld. */
    fun redact(text: String): Result {
        if (text.isEmpty()) return Result(text, 0)
        var count = 0
        var inKeyBlock = false
        val outLines = mutableListOf<String>()
        for (line in text.split('\n')) {
            val trimmed = line.trim()
            when {
                // Inside a key block: nothing is emitted — the marker was written
                // at its BEGIN line, and a truncated block must not leak its tail.
                inKeyBlock -> {
                    count++
                    if (trimmed.contains(END_KEY_BLOCK)) inKeyBlock = false
                }
                trimmed.contains(BEGIN_KEY_BLOCK) -> {
                    count++
                    outLines += KEY_BLOCK_MARKER
                    inKeyBlock = !trimmed.contains(END_KEY_BLOCK)
                }
                else -> {
                    val (redacted, hits) = redactLine(line)
                    count += hits
                    outLines += redacted
                }
            }
        }
        if (count == 0) return Result(text, 0)
        return Result(outLines.joinToString("\n") + "\n" + noteFor(count), count)
    }

    /** One line: the known shapes first, then the assignment rule. */
    private fun redactLine(line: String): Pair<String, Int> {
        var current = line
        var hits = 0
        for (shape in SHAPES) {
            val found = shape.findAll(current).count()
            if (found > 0) {
                hits += found
                current = shape.replace(current, MARKER)
            }
        }
        val match = ASSIGNMENT.find(current) ?: return current to hits
        val value = match.groupValues[2]
        if (!looksLikeLiteral(value)) return current to hits
        val replaced = current.replaceRange(match.range, match.value.replace(value, MARKER))
        return replaced to hits + 1
    }

    // ---- 1. known credential shapes ---------------------------------------

    private val SHAPES = listOf(
        Regex("""AIza[0-9A-Za-z_\-]{20,}"""),                                        // Google
        Regex("""nvapi-[0-9A-Za-z_\-]{16,}"""),                                      // NVIDIA
        Regex("""sk-(?:ant-)?[A-Za-z0-9_\-]{16,}"""),                                // OpenAI / Anthropic
        Regex("""(?:ghp|gho|ghu|ghs)_[A-Za-z0-9]{16,}"""),                           // GitHub tokens
        Regex("""github_pat_[A-Za-z0-9_]{20,}"""),                                   // GitHub fine-grained
        Regex("""xox[baprs]-[A-Za-z0-9\-]{10,}"""),                                  // Slack
        Regex("""glpat-[A-Za-z0-9_\-]{16,}"""),                                      // GitLab
        Regex("""AKIA[0-9A-Z]{16}"""),                                               // AWS access key id
        Regex("""eyJ[A-Za-z0-9_\-]{8,}\.[A-Za-z0-9_\-]{8,}\.[A-Za-z0-9_\-]{8,}""")   // JWT
    )

    private const val BEGIN_KEY_BLOCK = "-----BEGIN"
    private const val END_KEY_BLOCK = "-----END"

    // ---- 3. the assignment rule -------------------------------------------

    /**
     * `name = value`, `name: value` or `"name": value` — group 1 is the
     * credential-shaped name, group 2 the value. The separator may not be
     * `==`, `!=`, `<=`, `>=` or `:=`: those are comparisons and declarations,
     * not assignments of a literal (the `(?<![=!<>])` / `(?![=])` guards).
     */
    private val ASSIGNMENT = Regex(
        """(?i)\b(api[_-]?key|apikey|client[_-]?secret|secret[_-]?key|secret|token|access[_-]?token|refresh[_-]?token|""" +
            """passwd|password|pwd|credentials?|access[_-]?key|private[_-]?key|auth[_-]?key|""" +
            """encryption[_-]?key|signing[_-]?key|aws[_-]?secret)\b\s*(?<![=!<>]):?=\s*(?!=)""" +
            """("(?:[^"\\\n]|\\.)*"|'(?:[^'\\\n]|\\.)*'|[^\s,;)}\]#]+)"""
    )

    /** A literal worth hiding; a reference or a placeholder is not. */
    private fun looksLikeLiteral(rawValue: String): Boolean {
        val value = rawValue.trim().trim('"', '\'')
        if (value.length < MIN_VALUE_CHARS) return false
        if (value.contains('(') || value.contains(')') || value.contains('<') || value.contains('>')) return false
        if (value.contains('{') || value.contains('}') || value.contains('[') || value.contains(']')) return false
        val lower = value.lowercase()
        if (PLACEHOLDERS.any { lower == it || lower.startsWith("${it}_") || lower.startsWith("$it-") }) return false
        // `YOUR_API_KEY`, `your-token-here`: the one word every sample config uses.
        if (lower.startsWith("your") || lower.contains("your_") || lower.contains("your-")) return false
        if (lower.startsWith("process.env") || lower.startsWith("os.environ") || lower.startsWith("getenv")) return false
        if (lower.startsWith("system.") || lower.startsWith("env.") || value.startsWith("$")) return false
        // A bare identifier (letters only) is as likely a variable name as a
        // secret; a real key carries digits, dashes or mixed case.
        val hasDigit = value.any { it.isDigit() }
        val hasSymbol = value.any { it == '-' || it == '_' || it == '.' || it == '/' || it == '+' || it == '=' }
        val mixedCase = value.any { it.isLowerCase() } && value.any { it.isUpperCase() }
        return hasDigit || hasSymbol || mixedCase
    }

    private val PLACEHOLDERS = listOf(
        "your_key", "yourkey", "your-key", "your_token", "yourtoken", "api_key_here", "changeme",
        "placeholder", "example", "dummy", "sample", "testing", "local", "development",
        "xxxx", "xxxxxx", "null", "none", "nil", "empty", "todo", "fixme", "foobar"
    )
}
