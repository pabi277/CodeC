package com.codeci.ide.ui.editor.sora

import com.codeci.ide.ui.utils.LanguageType

/**
 * Phase 29.1/29.2 — the pure TextMate asset map. NO Android imports: the
 * file→scope mapping and the per-language grammar sets are host-testable
 * on the JVM (see [TextMateGrammarsTest]).
 *
 * Grammar JSONs live in `assets/textmate/grammars/` (MIT, unmodified copies
 * from microsoft/vscode, Microsoft/TypeScript-TmLanguage and LuaLS/lua.tmbundle
 * — attribution in `assets/licenses/TEXTMATE_GRAMMARS_MIT.txt`). Each entry
 * is loaded into sora's [io.github.rosemoe.sora.langs.textmate.registry.GrammarRegistry]
 * exactly once per process (T2).
 *
 * A language's grammar SET lists every grammar that must be registered for
 * its file to colour fully — embedded scopes (HTML embeds source.js +
 * source.css, PHP embeds HTML, …) resolve only when the included grammar is
 * also in the registry. The file's OWN grammar is always first.
 */
data class GrammarAsset(
    /** Registry name (unique per grammar; doubles as the load-dedupe key). */
    val name: String,
    /** Asset path under `assets/` (extension .json ⇒ IGrammarSource reads it as JSON). */
    val path: String,
    /** Root scope name the grammar registers under. */
    val scope: String
)

object TextMateGrammars {

    // ---- individual grammar assets (all in assets/textmate/grammars/) ----
    val C = GrammarAsset("c", "textmate/grammars/c.tmLanguage.json", "source.c")
    val CPP = GrammarAsset("cpp", "textmate/grammars/cpp.tmLanguage.json", "source.cpp")
    val MAGIC_REGEXP =
        GrammarAsset("regexp-python", "textmate/grammars/magic-regexp.tmLanguage.json", "source.regexp.python")
    val PYTHON = GrammarAsset("python", "textmate/grammars/python.tmLanguage.json", "source.python")
    val JAVASCRIPT = GrammarAsset("javascript", "textmate/grammars/javascript.tmLanguage.json", "source.js")
    val TYPESCRIPT = GrammarAsset("typescript", "textmate/grammars/typescript.tmLanguage.json", "source.ts")
    val TSX = GrammarAsset("typescriptreact", "textmate/grammars/typescriptreact.tmLanguage.json", "source.tsx")
    val HTML = GrammarAsset("html", "textmate/grammars/html.tmLanguage.json", "text.html.basic")
    val HTML_DERIVATIVE =
        GrammarAsset("html-derivative", "textmate/grammars/html-derivative.tmLanguage.json", "text.html.derivative")
    val CSS = GrammarAsset("css", "textmate/grammars/css.tmLanguage.json", "source.css")
    val JSON = GrammarAsset("json", "textmate/grammars/json.tmLanguage.json", "source.json")
    val JSONC = GrammarAsset("jsonc", "textmate/grammars/jsonc.tmLanguage.json", "source.json.comments")
    val SHELL = GrammarAsset("shell", "textmate/grammars/shell.tmLanguage.json", "source.shell")
    val MARKDOWN = GrammarAsset("markdown", "textmate/grammars/markdown.tmLanguage.json", "text.html.markdown")
    val GO = GrammarAsset("go", "textmate/grammars/go.tmLanguage.json", "source.go")
    val RUST = GrammarAsset("rust", "textmate/grammars/rust.tmLanguage.json", "source.rust")
    val PHP = GrammarAsset("php", "textmate/grammars/php.tmLanguage.json", "source.php")
    val PHP_HTML = GrammarAsset("php-html", "textmate/grammars/php-html.tmLanguage.json", "text.html.php")
    val RUBY = GrammarAsset("ruby", "textmate/grammars/ruby.tmLanguage.json", "source.ruby")
    val LUA = GrammarAsset("lua", "textmate/grammars/lua.tmLanguage.json", "source.lua")
    val XML = GrammarAsset("xml", "textmate/grammars/xml.tmLanguage.json", "text.xml")
    val YAML = GrammarAsset("yaml", "textmate/grammars/yaml.tmLanguage.json", "source.yaml")
    val YAML_1_2 = GrammarAsset("yaml-1.2", "textmate/grammars/yaml-1.2.tmLanguage.json", "source.yaml.1.2")
    val YAML_EMBEDDED =
        GrammarAsset("yaml-embedded", "textmate/grammars/yaml-embedded.tmLanguage.json", "source.yaml.embedded")

    /**
     * The grammar set for a file of [language] (the file's own grammar
     * first, then the grammars its file embeds). Null = no TextMate
     * colouring (TEXT files, or an extension CodeC does not know) — the
     * editor then uses the regex fallback analyzer.
     *
     * [fileName] disambiguates buckets whose extensions map to different
     * grammars: `.tsx` needs `source.tsx` (TypeScriptReact) while `.ts`
     * needs `source.ts` — both live in [LanguageType.TYPESCRIPT].
     */
    fun grammarSetFor(language: LanguageType, fileName: String? = null): List<GrammarAsset>? {
        if (language == LanguageType.TYPESCRIPT) {
            val leaf = fileName?.substringAfterLast('/')?.substringAfterLast('\\').orEmpty().lowercase()
            return if (leaf.endsWith(".tsx")) listOf(TSX) else listOf(TYPESCRIPT)
        }
        return when (language) {
            LanguageType.C -> listOf(C)
            // The vscode C++ grammar is self-contained and references
            // source.regexp.python for raw-string regexes.
            LanguageType.CPP -> listOf(CPP, MAGIC_REGEXP)
            LanguageType.PYTHON -> listOf(PYTHON)
            LanguageType.JAVASCRIPT -> listOf(JAVASCRIPT)
            LanguageType.HTML -> listOf(HTML, HTML_DERIVATIVE, JAVASCRIPT, CSS)
            LanguageType.CSS -> listOf(CSS)
            LanguageType.JSON -> listOf(JSON)
            LanguageType.SHELL -> listOf(SHELL)
            // Markdown fences embed other languages; the common fence set is
            // preloaded (the rest of the fences stay uncoloured until their
            // language is opened — recorded in PART_29_2 §3).
            LanguageType.MARKDOWN -> listOf(MARKDOWN, HTML, HTML_DERIVATIVE, JAVASCRIPT, CSS, PYTHON, SHELL)
            LanguageType.GO -> listOf(GO)
            LanguageType.RUST -> listOf(RUST)
            // text.html.php wraps source.php around a full HTML document.
            LanguageType.PHP -> listOf(PHP_HTML, PHP, HTML, HTML_DERIVATIVE, CSS, JAVASCRIPT, JSON, XML)
            // Ruby heredocs/ERB embed HTML+JS+CSS; rarer embeds (sql, graphql…)
            // are not shipped and stay plain (PART_29_2 §3).
            LanguageType.RUBY -> listOf(RUBY, HTML, HTML_DERIVATIVE, JAVASCRIPT, CSS)
            LanguageType.LUA -> listOf(LUA, C)
            LanguageType.XML -> listOf(XML)
            LanguageType.YAML -> listOf(YAML, YAML_1_2, YAML_EMBEDDED)
            LanguageType.TYPESCRIPT, LanguageType.TEXT -> null // handled above
        }
    }

    /** The root scope a file's editor language must be created with. */
    fun scopeFor(language: LanguageType, fileName: String? = null): String? =
        grammarSetFor(language, fileName)?.firstOrNull()?.scope

    /**
     * Phase 29.1 warm-up order (T3 core set first — C, C++, Python, JS, TS,
     * HTML, CSS, JSON, Shell, Markdown — then the small extra grammars).
     * PHP and Ruby sets are intentionally NOT preloaded: their embed chains
     * are the heaviest and the languages are rarer; they load on first open.
     */
    val warmUpLanguages: List<LanguageType> = listOf(
        LanguageType.C, LanguageType.CPP, LanguageType.PYTHON,
        LanguageType.JAVASCRIPT, LanguageType.TYPESCRIPT,
        LanguageType.HTML, LanguageType.CSS, LanguageType.JSON,
        LanguageType.SHELL, LanguageType.MARKDOWN,
        LanguageType.GO, LanguageType.RUST, LanguageType.LUA,
        LanguageType.XML, LanguageType.YAML
    )
}
