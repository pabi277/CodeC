package com.codeci.ide.ui.editor

/** Shared drawer/search visibility; no preference and no change to the Projects hub. */
object ProjectFilesPolicy {
    val excludedDirectories = setOf(
        ".git", ".hg", ".svn", ".gradle", ".idea", ".cache", "node_modules",
        "build", "bin", "dist", "__pycache__", ".venv", "venv", ".next"
    )
    private val configNames = setOf(
        ".env", ".gitignore", ".gitattributes", ".editorconfig", ".npmrc",
        ".prettierrc", ".eslintrc", "dockerfile", "makefile", "license", "gemfile"
    )
    fun usefulConfig(name: String): Boolean = name.lowercase() in configNames || name.startsWith(".env.")
    fun visible(name: String, directory: Boolean): Boolean =
        if (directory) name !in excludedDirectories && (!name.startsWith(".") || name in setOf(".github", ".vscode"))
        else !name.startsWith(".") || usefulConfig(name) || name in setOf(".eslintrc.json", ".prettierrc.json")

    /** Exact entry plus descendants, never a sibling with a similar prefix. */
    fun affects(entry: String, path: String): Boolean = path == entry || path.startsWith("$entry/")
}
