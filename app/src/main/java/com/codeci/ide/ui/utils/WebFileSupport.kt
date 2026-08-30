package com.codeci.ide.ui.utils

/**
 * Pure helpers for Phase 5.2 web preview. Web files (`.html`, `.htm`, `.css`,
 * `.js`) join `.c` as first-class project files so an HTML page and its local
 * CSS/JS can be edited and previewed in-app. Host-testable on the JVM.
 */
object WebFileSupport {

    val WEB_EXTENSIONS: Set<String> = setOf("html", "htm", "css", "js")

    fun isHtml(name: String): Boolean {
        val lower = name.lowercase()
        return lower.endsWith(".html") || lower.endsWith(".htm")
    }

    fun isWeb(name: String): Boolean {
        val lower = name.lowercase()
        return WEB_EXTENSIONS.any { lower.endsWith(".$it") }
    }

    /**
     * Keeps a recognized source/web/script extension; otherwise appends the
     * `.c` default. This preserves the existing C flow exactly (a bare "foo"
     * is still "foo.c") while letting an explicitly typed name keep its
     * extension ("index.html" stays "index.html"). Phase 12: every language
     * the editor highlights (python, shell, json, markdown, cpp, …) keeps
     * its extension too — previously `test.py` was normalized to
     * `test.py.c` on save, silently reclassifying the file as C and routing
     * RUN ▶ through `cc` instead of python3.
     */
    fun normalizeFileName(name: String): String {
        val lower = name.lowercase()
        if (lower.endsWith(".c") || isWeb(name)) return name
        val keepsExtension = LanguageType.entries
            .filterNot { it == LanguageType.TEXT }
            .any { type -> type.extensions.any { lower.endsWith(".$it") } }
        return if (keepsExtension) name else "$name.c"
    }

    /**
     * Phase 12 — detects Python in a buffer that has never been named by the
     * user (the app's default `main.c`/`untitled.c` scratch buffer). Uses
     * strong Python-only markers so C code is never misclassified; returns
     * true only when the text clearly is Python. The default scratch buffer
     * is a C starter, so an untouched buffer never matches.
     */
    fun looksLikePython(text: String): Boolean {
        val t = text.trimStart()
        if (t.isEmpty()) return false
        if (t.startsWith("#!")) return t.substringAfter("#!", "").contains("python")
        val markers = listOf(
            "def ", "import ", "from ", "class ", "print(", "lambda ",
            "if __name__", "async def", "yield ", "elif ", "else:"
        )
        val hits = markers.count { text.contains(it) }
        if (hits == 0) return false
        // C code must never match: a C starter or any #include/stdio code is
        // not Python even if it happens to contain print( / else:.
        if (text.contains("#include") || text.contains("int main")) return false
        return hits >= 1
    }

    /** Per-type starter template for a newly created file. */
    fun starterContent(name: String): String {
        val lower = name.lowercase()
        return when {
            lower.endsWith(".html") || lower.endsWith(".htm") -> HTML_STARTER
            lower.endsWith(".css") -> CSS_STARTER
            lower.endsWith(".js") -> JS_STARTER
            lower.endsWith(".py") || lower.endsWith(".pyw") -> PYTHON_STARTER
            else -> C_STARTER
        }
    }

    const val C_STARTER = "#include <stdio.h>\n\nint main() {\n    return 0;\n}\n"

    const val PYTHON_STARTER = "def main():\n    pass\n\n\nif __name__ == \"__main__\":\n    main()\n"

    const val HTML_STARTER =
        "<!doctype html>\n" +
            "<html lang=\"en\">\n" +
            "<head>\n" +
            "  <meta charset=\"utf-8\">\n" +
            "  <title>Page</title>\n" +
            "  <link rel=\"stylesheet\" href=\"style.css\">\n" +
            "</head>\n" +
            "<body>\n" +
            "  <h1>Hello, CodeC!</h1>\n" +
            "  <script src=\"script.js\"></script>\n" +
            "</body>\n" +
            "</html>\n"

    const val CSS_STARTER = "body {\n  background: #1e1e2e;\n  color: #cdd6f4;\n}\n"

    const val JS_STARTER = "console.log(\"script.js loaded\");\n"
}
