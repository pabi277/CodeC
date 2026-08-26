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
     * Keeps a recognized source/web extension; otherwise appends the `.c`
     * default. This preserves the existing C flow exactly (a bare "foo" is
     * still "foo.c") while letting an explicitly web-typed name keep its
     * extension ("index.html" stays "index.html").
     */
    fun normalizeFileName(name: String): String =
        if (name.lowercase().endsWith(".c") || isWeb(name)) name else "$name.c"

    /** Per-type starter template for a newly created file. */
    fun starterContent(name: String): String {
        val lower = name.lowercase()
        return when {
            lower.endsWith(".html") || lower.endsWith(".htm") -> HTML_STARTER
            lower.endsWith(".css") -> CSS_STARTER
            lower.endsWith(".js") -> JS_STARTER
            else -> C_STARTER
        }
    }

    const val C_STARTER = "#include <stdio.h>\n\nint main() {\n    return 0;\n}\n"

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
