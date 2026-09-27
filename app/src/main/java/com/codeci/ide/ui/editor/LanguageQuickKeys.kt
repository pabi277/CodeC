package com.codeci.ide.ui.editor

import com.codeci.ide.ui.utils.LanguageType

/**
 * Phase 69.2 — the file language's own quick keys, and they LEAD the row.
 *
 * Owner (2026-09-28), verbatim:
 *  - the report: *"What language i am using don't matter it always give me
 *    same fixed quick keys"* — the Phase 22/29 tails were appended AFTER the
 *    14 general caps, i.e. in slots 15-17 of a row that shows about seven
 *    caps at a time and opens at the left, so the caps really were invisible
 *    in every language;
 *  - the placement answer: *"Can it be auto detection my file extension and
 *    set the the quick keys order as per requirement and if it is not a
 *    standard file than a default quick key option"*;
 *  - the content answer: *"B — Give each language a few real caps
 *    (recommended, Spck parity)"*.
 *
 * So the rule this file implements:
 *  - detection is the file EXTENSION, through the one existing mapper
 *    ([LanguageType.fromFileName]) — `main.c` is C, `index.html` is HTML, no
 *    picker and no second language concept;
 *  - the detected language's caps come FIRST, before TAB and the general set,
 *    so the language visibly changes the row the moment a file is open;
 *  - a non-standard file (no extension, unknown extension, `.txt`/`.log` —
 *    everything [LanguageType.TEXT]) keeps the DEFAULT row untouched, which
 *    is exactly the owner's "default quick key option".
 *
 * Content law (Spck parity, 68.1's "no extra spaces"): a handful of caps per
 * language, every one a real construct for that language — the same kind of
 * thing Spck puts in its quick-key row for HTML (`<tag> div class`). They are
 * plain [EditorKey.Insert] / [EditorKey.Pair] caps: no tabstop syntax (`${1:}`
 * would be inserted literally), no new key kind, no extra row or button.
 *
 * Every cap that shipped before this part is KEPT (C's `->`, C++'s `->`/`::`,
 * Python's `:`/`_(self)`, HTML's `</>`, CSS's `:`/`;`, JS/TS's
 * backticks/`=>`, shell's `$`, JSON's `:`/`,`/`null`) — nothing was removed
 * to make room; only the position changed (first instead of last).
 */
object LanguageQuickKeys {

    private fun i(label: String, text: String) = EditorKeyDef(label, EditorKey.Insert(text))

    private fun p(label: String, open: String, close: String) =
        EditorKeyDef(label, EditorKey.Pair(open, close))

    /**
     * The row's leading caps for [language]; empty for a non-standard file
     * (TEXT) or no file at all (null) so the DEFAULT row shows unchanged.
     */
    fun forLanguage(language: LanguageType?): List<EditorKeyDef> = when (language) {
        LanguageType.C -> listOf(
            i("#include", "#include <stdio.h>\n"),
            p("printf", "printf(\"", "\");"),
            i("int", "int "),
            i("->", "->")
        )
        LanguageType.CPP -> listOf(
            i("#include", "#include <iostream>\n"),
            p("cout", "std::cout << ", ";"),
            i("std::", "std::"),
            i("->", "->"),
            i("::", "::")
        )
        LanguageType.PYTHON -> listOf(
            i("def", "def "),
            p("print", "print(\"", "\")"),
            i(":", ":"),
            i("_(self)", "self ")
        )
        LanguageType.JAVASCRIPT, LanguageType.TYPESCRIPT -> listOf(
            p("log", "console.log(", ")"),
            i("=>", "=>"),
            // Backticks are a pair too — template literals.
            p("``", "`", "`"),
            i("function", "function ")
        )
        LanguageType.HTML -> listOf(
            p("<tag>", "<tag>", "</tag>"),
            p("div", "<div>", "</div>"),
            p("class", " class=\"", "\""),
            i("</>", "</>")
        )
        LanguageType.CSS -> listOf(
            i("color:", "color: "),
            i("px", "px"),
            i(":", ":"),
            i(";", ";")
        )
        LanguageType.JSON -> listOf(
            // Phase 68.1 (completed part) — Spck's JSON symbol row (shot
            // 204937: `{} prop: = null , ""`): the colon after a key (with
            // `: ` on the press-and-hold popup), the comma between members,
            // and the three literals — one cap, flick for true/false.
            EditorKeyDef(":", EditorKey.Insert(":"), popup = EditorKey.Insert(": ")),
            i(",", ","),
            EditorKeyDef(
                "null", EditorKey.Insert("null"),
                swipeUp = EditorKey.Insert("true"), swipeDown = EditorKey.Insert("false")
            )
        )
        LanguageType.SHELL -> listOf(
            i("echo", "echo "),
            i("|", " | "),
            i("#!", "#!/bin/sh\n"),
            i("$", "$")
        )
        LanguageType.GO -> listOf(
            i(":=", " := "),
            i("func", "func "),
            i("fmt", "fmt."),
            i("err", "err != nil")
        )
        LanguageType.RUST -> listOf(
            p("println!", "println!(\"{}\", ", ")"),
            i("fn", "fn "),
            i("let", "let "),
            i("->", "->")
        )
        LanguageType.PHP -> listOf(
            i("<?php", "<?php\n"),
            i("$", "$"),
            i("echo", "echo "),
            i("->", "->")
        )
        LanguageType.RUBY -> listOf(
            i("def", "def "),
            i("end", "end"),
            i("puts", "puts "),
            p("#{}", "#{", "}")
        )
        LanguageType.LUA -> listOf(
            i("local", "local "),
            i("function", "function "),
            i("then", "then"),
            i("end", "end")
        )
        LanguageType.XML -> listOf(
            i("<?xml", "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"),
            p("<tag>", "<tag>", "</tag>"),
            i("/>", "/>"),
            i("</>", "</>")
        )
        LanguageType.YAML -> listOf(
            i(":", ": "),
            i("-", "- "),
            i("#", "# ")
        )
        LanguageType.MARKDOWN -> listOf(
            i("#", "# "),
            i("-", "- "),
            p("**", "**", "**"),
            p("`", "`", "`")
        )
        // Non-standard file — the DEFAULT row stands (the owner's fallback).
        LanguageType.TEXT, null -> emptyList()
    }
}
