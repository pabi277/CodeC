package com.codeci.ide.ui.editor.snippets

import com.codeci.ide.ui.utils.LanguageType

/**
 * Phase 30.1 — the pure snippet-pack asset map. NO Android imports, exactly
 * like `TextMateGrammars` is for the Phase 29 grammars: which vendored pack
 * files belong to which [LanguageType] is host-testable on the JVM
 * (`SnippetAssetsTest`), while [SnippetLibrary] does the actual reading.
 *
 * The files live in `assets/snippets/` and are UNMODIFIED copies of
 * rafamadriz/friendly-snippets (MIT) pinned to one upstream commit — see
 * `assets/licenses/FRIENDLY_SNIPPETS_MIT.txt` and `scripts/vendor_snippets.py`,
 * whose `PACKS` table this mirrors (the Robolectric `SnippetLibraryTest`
 * asserts every path below really ships, so the two lists cannot drift).
 *
 * A language's pack list is ORDERED: the main pack first, then the doc/debug
 * packs — [SnippetPacks.items] lets the first entry claiming a prefix win, so
 * the everyday snippet outranks the exotic one.
 */
data class SnippetPackAsset(
    /** Asset path under `assets/`. */
    val path: String
)

object SnippetAssets {

    /** Asset directory the vendor script writes into. */
    const val DIR = "snippets"

    private fun pack(path: String) = SnippetPackAsset("$DIR/$path")

    val C = listOf(pack("c/c.json"), pack("c/cdoc.json"))
    val CPP = listOf(pack("cpp/cpp.json"), pack("cpp/cppdoc.json"))
    val PYTHON = listOf(
        pack("python/python.json"),
        pack("python/comprehension.json"),
        pack("python/debug.json"),
        pack("python/pydoc.json"),
        pack("python/unittest.json")
    )
    val JAVASCRIPT = listOf(
        pack("javascript/javascript.json"),
        pack("javascript/jsdoc.json"),
        pack("javascript/react.json")
    )
    val TYPESCRIPT = listOf(
        pack("javascript/typescript.json"),
        pack("javascript/tsdoc.json"),
        pack("javascript/react-ts.json")
    )
    val SHELL = listOf(pack("shell/shell.json"), pack("shell/shelldoc.json"))
    val HTML = listOf(pack("html.json"))
    val CSS = listOf(pack("css.json"))
    val MARKDOWN = listOf(pack("markdown.json"))
    val GO = listOf(pack("go.json"))
    val RUST = listOf(pack("rust/rust.json"), pack("rust/rustdoc.json"))
    val PHP = listOf(pack("php/php.json"), pack("php/phpdoc.json"))
    val RUBY = listOf(pack("ruby/ruby.json"), pack("ruby/rdoc.json"))
    val LUA = listOf(pack("lua/lua.json"), pack("lua/luadoc.json"))

    /**
     * The pack files for [language]; empty when CodeC ships none.
     *
     * Plan rule S4: JSON and TEXT get NO pack (a `.json` file's own content is
     * the completion, and the engine returns nothing for both anyway); XML and
     * YAML have no upstream pack worth its weight, so they stay on the
     * buffer-identifier scan.
     */
    fun packsFor(language: LanguageType): List<SnippetPackAsset> = when (language) {
        LanguageType.C -> C
        LanguageType.CPP -> CPP
        LanguageType.PYTHON -> PYTHON
        LanguageType.JAVASCRIPT -> JAVASCRIPT
        LanguageType.TYPESCRIPT -> TYPESCRIPT
        LanguageType.SHELL -> SHELL
        LanguageType.HTML -> HTML
        LanguageType.CSS -> CSS
        LanguageType.MARKDOWN -> MARKDOWN
        LanguageType.GO -> GO
        LanguageType.RUST -> RUST
        LanguageType.PHP -> PHP
        LanguageType.RUBY -> RUBY
        LanguageType.LUA -> LUA
        LanguageType.JSON, LanguageType.XML, LanguageType.YAML, LanguageType.TEXT -> emptyList()
    }

    /** Every shipped pack path (asset-existence tests, warm-up). */
    val all: List<SnippetPackAsset> = LanguageType.entries
        .flatMap { packsFor(it) }
        .distinctBy { it.path }
        .sortedBy { it.path }

    /**
     * Warm-up order: the languages a phone user actually opens first (the same
     * core set `TextMateGrammars.warmUpLanguages` uses), then the long tail.
     * Parsing is a few milliseconds per pack, so this is a latency nicety, not
     * a requirement — [SnippetLibrary.snippetsFor] loads on demand.
     */
    val warmUpLanguages: List<LanguageType> = listOf(
        LanguageType.C, LanguageType.CPP, LanguageType.PYTHON,
        LanguageType.JAVASCRIPT, LanguageType.TYPESCRIPT,
        LanguageType.HTML, LanguageType.CSS, LanguageType.SHELL,
        LanguageType.MARKDOWN, LanguageType.GO, LanguageType.RUST,
        LanguageType.PHP, LanguageType.RUBY, LanguageType.LUA
    )
}
