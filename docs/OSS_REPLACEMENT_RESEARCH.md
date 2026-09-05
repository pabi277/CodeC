# CodeC — Replace hand-rolled (AI) editor brains with free OSS

> **Status:** 📋 **RESEARCH ONLY — NO APP CODE.** Owner brief (2026-09-05):
> *read how phases work; search OSS for autocomplete + syntax highlight;
> ideal form is VS Code; find every place we can drop custom/AI code and
> plug in a free library — the same pattern as Phase 25.2 swapping the
> editor widget for Sora.*
>
> This chat writes **this dossier**. Implementation waits for
> `"Start Phase N"`. Clean-room law still applies (features, never GPL
> paste; depend on LGPL, never fork).

---

## 0. How phases work (the pattern you already used with Sora)

Every CodeC editor upgrade follows the same loop (`rule.md`, `prompt.md`,
`docs/JOURNEY.md`):

1. **Research dossier** (this file; same role as
   `docs/EDITOR_MOBILE_RESEARCH.md` for Sora).
2. **Owner says `"Start Phase N"`** — nothing ships without that sentence.
3. **Spike + device budgets** if feel/perf is the gate (25.1, 28.1).
4. **Implementation on the session branch** + host tests + CI green.
5. **Owner device round** → `"All passed"` / `"Go"`.
6. **Owner merge command** → PR. Agent never opens a PR on its own.

**Sora was the widget.** What is still *ours* (regex tokenizer, keyword
lists, snippet tables) is the **language brain**. VS Code’s brain is
**not** custom Kotlin: it is **TextMate grammars + themes** for colour,
and **Language Server Protocol (LSP)** for completion/diagnostics. Sora
already ships both as optional Gradle modules. That is the same “plug a
library in, delete our copy” move as 25.2.

---

## 1. What VS Code actually uses (the ideal form)

| Layer | VS Code | CodeC today | Drop-in OSS that already talks to Sora |
|---|---|---|---|
| Text widget | Monaco (desktop; **not** mobile) | **sora-editor 0.24.6** ✅ (Phase 25.2) | Keep Sora. Do **not** put Monaco/WebView in the phone editor ([Monaco FAQ: mobile unsupported](https://github.com/microsoft/monaco-editor)). |
| Syntax colour | **TextMate** `.tmLanguage.json` + VS Code themes; Tree-sitter only in some extensions | Hand-rolled regex in `MultiLanguageSyntaxHighlighter.kt` (~690 LOC) fed into `CodeCAnalyzer` | **`io.github.rosemoe:language-textmate`** (tm4e inside Sora). Optional later: `language-treesitter`. |
| Completions | **LSP** (`clangd`, Pylance/Pyright, typescript-language-server, …) + snippets | `CodeCompletionEngine.kt` (~260 LOC): buffer identifier scan + hardcoded snippets | **`io.github.rosemoe:editor-lsp`** + on-device servers (`clangd`, `pylsp`/`jedi`, `typescript-language-server`) via the existing package repo. Keep our **ghost/strip UX** (Phase 27). |
| Language config | `language-configuration.json` (brackets, comments, onEnter) | `CodeCLanguage.symbolPairsFor` / `indentAdvanceFor` | Same TextMate module loads VS Code `language-configuration.json`. |
| Format | `clang-format`, Prettier, Black via LSP `documentFormatting` | `ClangFormatBridge` + `CodeFormatter` (E.1) | Keep; later LSP formatters. |
| Themes | JSON `tokenColors` (Dark+, Monokai, …) | `CodeCScheme` maps a few slots | TextMate themes (same JSON VS Code uses). |

**Do not replace Sora with Monaco.** Monaco is the desktop cousin; Sora
is the phone one. The VS Code *look* on Android is **Sora + TextMate +
LSP**, which is exactly what Squircle CE / AndroidIDE / Xed-Editor did.

Sources: [Sora modules](https://project-sora.github.io/sora-editor-docs/guide/getting-started),
[TextMate language guide](https://project-sora.github.io/sora-editor-docs/guide/using-language),
[Sora repo](https://github.com/Rosemoe/sora-editor).

---

## 2. Inventory: custom code that can be *replaced* (not rewritten)

Only **language intelligence** is the Sora-style swap. Phone UX we
already own (ghost, chips, Keys, strip) stays.

### 2.1 REPLACE — syntax highlighting (highest leverage)

| File | What it is | Why replace |
|---|---|---|
| `app/.../ui/utils/MultiLanguageSyntaxHighlighter.kt` | Single-pass Java regex per language; keyword sets; windowed `HighlightedCode` leftover from BasicTextField | Not VS Code quality (no scopes, no nested grammars, HTML/CSS mashed together, TS treated as JS). After Sora, the windowing was a Compose workaround — Sora does not need it. |
| `app/.../ui/editor/sora/CodeCAnalyzer.kt` | Feeds those regex tokens into sora `MappedSpans` via `SimpleAnalyzeManager` (full re-lex each edit) | Sora’s own comment: incremental lex is the follow-up. TextMate module *is* that follow-up. |
| `SyntaxVisualTransformation` in the same highlighter file | Compose `VisualTransformation` for the **dead** BasicTextField path | Dead once nothing composes BTF. Delete with the highlighter. |

**OSS replacement:**

- Gradle: `io.github.rosemoe:language-textmate` (already in the Sora BOM
  you use for `editor`).
- Engine: **tm4e** (Eclipse port of `vscode-textmate`).
- Grammars: the same files VS Code ships
  (`source.c`, `source.cpp`, `source.python`, `source.js`, `text.html.basic`,
  `source.css`, `source.json`, `source.shell`, `text.html.markdown`) from
  [microsoft/vscode](https://github.com/microsoft/vscode) (`extensions/*/syntaxes`,
  MIT) or [textmate/*-tmbundle](https://github.com/textmate).
- Themes: VS Code JSON themes (Monokai, Dracula, Dark+) instead of
  hand-mapping 6 `TokenKind`s in `CodeCScheme.kt`.

**License:** sora `language-textmate` = **LGPL-2.1** (same as the editor
you already accepted). Grammars = **MIT**. Depend, don’t fork (same
rule as 25.2).

**Keep:** `LanguageType.fromFileName` (extension → language) as a thin
table that picks a TextMate scope name.

### 2.2 REPLACE the *engine*, KEEP the *UX* — autocomplete

| File | Keep or drop |
|---|---|
| `CodeCompletionEngine.kt` | **Drop as the source of truth.** Snippet tables can remain as a *fallback* when no LSP is installed (airplane mode / first launch). |
| `CompletionPolicy.kt`, `GhostCompletion.kt`, `StripContext.kt`, `CodeCCompletionComponent.kt`, `GhostHintRenderer.kt` | **KEEP.** Phase 27 is phone UX, not an engine. Ghost + chips + ⌄-more is what VS Code mobile *doesn’t* have. Feed LSP items into the same `CompletionItem` model. |
| `CodeCLanguage.requireAutoComplete` | Swap publisher source: LSP / TextMate word list instead of `CodeCompletionEngine.completions`. |

**OSS replacement (VS Code stack on the phone):**

| Language | Server | How it lands on device | License |
|---|---|---|---|
| C / C++ | **clangd** | Already in CodeC’s clang package story; `editor-lsp` speaks stdio LSP | Apache-2.0 (LLVM) |
| Python | **jedi** via **pylsp**, or **Pyright** | `pkg install` python + pylsp (Phase 12 python is already in the repo) | MIT / MIT |
| JS / TS | **typescript-language-server** + `typescript` | nodejs toolchain (Phase 20.1) | Apache-2.0 |
| HTML/CSS/JSON | vscode-html/css/json language features (node) or simpler **emmet** + TextMate | Optional; snippets already cover 80% | MIT |
| Shell | **bash-language-server** | node | MIT |
| Markdown | none needed | TextMate colour is enough | — |

**Sora module:** `io.github.rosemoe:editor-lsp` — completion, diagnostics,
hover, code actions, format. AndroidIDE used this pattern (GPL app =
behavior reference only).

**Phone law (do not regress Phase 27):** LSP results still flow
`ghost (best 1) → strip chips → ⌄ panel`. Enter stays Enter. Master
switch still kills chrome. A clangd popup that covers the caret is the
bug Phase 27 already fixed.

### 2.3 KEEP — not “AI code”, they *are* the product

Do **not** replace these with a library. They are CodeC-specific and
already host-tested:

- Phase 26/28 **Keys** (`ui/keyboard/*`, `EditorKeySet`, `SmartTyping`)
- Phase 27 **policy** (`CompletionPolicy`, ghost/strip)
- `SoraEditorHost` bridge (VM canonical)
- Terminal emulator (clean-room vs Termux GPL)
- `CompilerService` / TCC / package repo
- Git / Projects / CodeCApi

### 2.4 OPTIONAL later (not the VS Code gap)

| Piece | OSS if we ever want it | Notes |
|---|---|---|
| `FindReplaceEngine` | Sora built-in searcher | 25.2 already considered this; VM search is fine. |
| `EditorUndoManager` | Sora `UndoManager` | Disabled on purpose so undo survives tabs. Keep VM. |
| `BracketMatcher` | TextMate `language-configuration` + sora bracket pair | Cheap follow-on after TextMate. |
| `CodeFormatter` / `ClangFormatBridge` | LSP `formatting` + clang-format package | Already half OSS (clang-format). |
| Compose highlighter leftovers | — | Delete, don’t replace. |

---

## 3. What we searched (open source + online)

### Syntax highlight

| Project | License | Fit |
|---|---|---|
| **Sora `language-textmate`** (tm4e) | LGPL-2.1 | **Winner.** Same editor we already ship. VS Code grammars. [docs](https://project-sora.github.io/sora-editor-docs/guide/using-language) |
| **Sora `language-treesitter`** + android-tree-sitter | LGPL + MIT | More accurate AST; bigger native `.so`s. Phase *after* TextMate if C/Python colour still lies. |
| **Sora `language-java`** | LGPL | Java only. Skip. |
| **Sora `language-monarch`** | LGPL | Monaco-style monarch grammars. Redundant if we take TextMate. |
| eclipse-tm4e | EPL-2.0 | Already inside `language-textmate`. Don’t depend twice. |
| [ivan-magda/kotlin-textmate](https://github.com/ivan-magda/kotlin-textmate) | MIT | Compose `AnnotatedString` — we left Compose editing. Ignore. |
| Shiki / vscode-textmate (JS) | MIT | WebView. Against the 25.1 verdict. |
| Highlights (regex 17 langs) | — | Same class of hack we have now. |

### Autocomplete

| Project | License | Fit |
|---|---|---|
| **Sora `editor-lsp`** | LGPL-2.1 | **Winner** for the client. |
| clangd | Apache-2.0 | C/C++ IntelliSense VS Code uses (`ms-vscode.cpptools` wraps it). |
| python-lsp-server + Jedi | MIT | Lightweight vs Pyright; better for phones. |
| typescript-language-server | Apache-2.0 | JS/TS. |
| Emmet | MIT | HTML expansions; can sit *beside* LSP. |
| TabNine / Copilot / Gemini | proprietary | Owner already has dead Gemini config in `metadata.json`. Out of scope. |

Monaco on mobile: **officially unsupported**. Acode’s Ace/WebView path
was already rejected vs Sora in `EDITOR_MOBILE_RESEARCH.md` §2.

---

## 4. Proposed phases (planning only — owner starts them)

Mirror 25.2: **depend on Sora modules, ship VS Code assets as
`assets/textmate/`, delete our regex/snippet engine once the device
round passes.**

| Phase | Title | Replaces | Gate |
|---|---|---|---|
| **29** | **VS Code colour (TextMate)** | `MultiLanguageSyntaxHighlighter` + `CodeCAnalyzer` tokenize path | Device: C/Python/HTML/JS files look like VS Code Dark+; keystroke p95 still ≤16.7 ms on bench.c (must not regress 25.1). APK delta budget: **≤ +1.5 MB** (grammars gzip well). |
| **30** | **LSP completion (clangd first)** | `CodeCompletionEngine` as primary source | Device: `#include` / struct members / locals from clangd; ghost+strip still accept with Tab; airplane mode falls back to snippets. clangd from existing clang package, not bundled in APK. |
| **31** | **LSP for Python / JS** (optional) | Remaining snippet-only langs | Only after 30. pylsp / tsserver via `pkg`. |

**29 is the Sora-shaped win:** one Gradle line + grammar JSON +
`TextMateLanguage.create(...)` instead of 690 lines of regex. 30 is the
IntelliSense win. Do **not** start 30 before 29 — LSP on a bad colourer
is wasted.

**29 sketch (not code):**

1. Add `language-textmate` to the existing Sora BOM.
2. Ship MIT grammars + one VS Code theme under `app/src/main/assets/textmate/`.
3. `GrammarRegistry.loadGrammars(...)` once at process start (Sora docs).
4. `CodeCLanguage` uses `TextMateLanguage.create(scopeName, false)` (or a
   thin wrapper that still supplies our completion publisher).
5. Delete regex tokenize from the hot path; keep `LanguageType` mapping.
6. Host tests: grammar files present; scope for `.c` / `.py` / `.html`.
7. Device recipe: open bench.c, a Python file, an HTML file; colour
   matches VS Code screenshots; typing still 60 fps.

**30 sketch:**

1. Add `editor-lsp`.
2. Spawn `clangd` from the installed clang prefix (stdio), project root =
   current project dir, `compile_flags.txt` or `-I` from TCC/clang settings.
3. Map LSP `CompletionItem` → existing `CompletionItem` so Phase 27 UX
   is unchanged.
4. Diagnostics from clangd *replace or merge* `CompilerDiagnostics`
   squiggles (owner call).
5. Fallback: if clangd missing, `CodeCompletionEngine` snippets only.

---

## 5. What we are *not* doing

- No app code in this chat (owner rule).
- No Monaco / Ace / WebView editor.
- No Copilot-style cloud AI (the “AI code” to remove is *our generated
  regex/snippet tables*, not a product AI).
- No forking sora (`soraX`).
- No starting Phase 29 until the owner says so (Phase 28 may still be
  in flight).
- No bundling clangd in the APK (package repo / existing clang module).

---

## 6. Source list

1. Rosemoe/sora-editor modules — [getting started](https://project-sora.github.io/sora-editor-docs/guide/getting-started) · [using language / TextMate](https://project-sora.github.io/sora-editor-docs/guide/using-language) · [GitHub](https://github.com/Rosemoe/sora-editor)
2. monaco-editor mobile FAQ — [microsoft/monaco-editor](https://github.com/microsoft/monaco-editor)
3. VS Code highlighting — TextMate grammars; Tree-sitter as overlay ([EvgeniyPeshkov/syntax-highlighter](https://github.com/EvgeniyPeshkov/syntax-highlighter))
4. tm4e — [eclipse-tm4e](https://github.com/eclipse-tm4e/tm4e)
5. AndroidIDE / Squircle CE as Sora+TextMate/LSP consumers (behavior reference; AndroidIDE GPL)
6. clangd, pylsp/Jedi, typescript-language-server — the VS Code C/Python/JS servers
7. In-tree: `CodeCAnalyzer.kt`, `CodeCompletionEngine.kt`, `MultiLanguageSyntaxHighlighter.kt`, `docs/EDITOR_MOBILE_RESEARCH.md` §4.3 (`editor-lsp` already named as the future)

---

## 7. One-line verdict

**Same move as Sora:** keep the widget and the phone UX; **throw away
the regex highlighter and the keyword/snippet engine** in favour of
**Sora `language-textmate` (VS Code colour) then `editor-lsp` + clangd
(VS Code brains).** That is the full-project map of “AI/custom code →
free OSS.”

---

## 8. Addendum (2026-09-05) — universal IDE + Acode-style *install*,
not a C toy

Owner: *look more at Acode module install; it’s no longer a C app, it’s
universal; find all free resources that fit; colour is very bad;
suggestions don’t give every suggestion — pain on phone coding.*

This section is still **research only**. No app code.

### 8.1 Why colour is bad and suggestions feel empty (code facts)

The editor is already Sora. The *brains* are still a C-era regex toy:

| What you feel | Why, in this repo |
|---|---|
| Colour looks cheap / wrong | `MultiLanguageSyntaxHighlighter` is one Java regex per language. HTML and CSS share one grammar. TypeScript is coloured as JavaScript. Go / Rust / PHP / Ruby / Lua **have RUN profiles** in `LanguageRegistry` but **no `LanguageType`** — they highlight as `TEXT` (plain). Nested constructs (template strings, CSS-in-HTML, markdown fences) are not real scopes. Only 6 token kinds vs VS Code’s hundreds of TextMate scopes. |
| Suggestions miss most of the language | `CodeCompletionEngine.MAX_ITEMS = 8`. Identifier scan is ±20 000 chars of *this file only* — no headers, no `stdio.h`, no `os.path`, no `document.`, no npm types. Snippet tables are ~8–11 hand-written lines per language. JSON and TEXT return **nothing**. Go/Rust/PHP/Ruby/Lua/TS get **zero** completions. Empty prefix only fires after a trigger word (`def `, `#include`). |
| Phone pain | Phase 27 already fixed *how* you accept (ghost + chips). The remaining pain is *what is offered* — the list is incomplete, not the strip. |

So: **universal at RUN time, C-subset at colour/complete time.** That is
the gap. Acode’s users install extra syntax + LSP plugins because Ace
also starts thin; CodeC should do the same with Sora modules + the
existing Package Hub.

### 8.2 What Acode actually does (behavior reference — MIT app, don’t copy)

[Acode](https://github.com/Acode-Foundation/Acode) is MIT, WebView +
**Ace**. Colour for ~100 languages is Ace *modes* (not VS Code
TextMate). Extra languages, IntelliSense, formatters, snippets are
**plugins you install from an in-app store** ([acode.app/plugins](https://acode.app/plugins)).

The plugins that match the owner’s two complaints:

| Acode plugin | Downloads (store) | What it is | CodeC equivalent (native, not Ace) |
|---|---|---|---|
| **Extra Syntax Highlights 2.0** | ~75k | More Ace modes / better colour | **Ship VS Code TextMate grammars** via Sora `language-textmate` — *better* than Ace modes (same files as VS Code). |
| **Acode LSP / Ace Linters** | ~40k | ace-linters: lint + format + autocomplete + hover for JS/TS/HTML/CSS/JSON/YAML/Python… ([repo](https://github.com/Sohil876/acode-plugin-ace-linters), MIT) | Sora **`editor-lsp`** + real servers (`clangd`, pylsp, tsserver). Ace-linters is JS-in-WebView; we already have a native LSP client module. |
| **Snippets** | ~82k | VS Code-style snippet packs | **[rafamadriz/friendly-snippets](https://github.com/rafamadriz/friendly-snippets)** (MIT) — the pack Neovim/VS Code community uses. JSON, not Kotlin tables. |
| **Emmet** | ~37k | HTML/CSS expansions | **[emmetio/emmet](https://github.com/emmetio/emmet)** (MIT). On Android, expand in the completion engine; don’t pull Ace. |
| **Prettier** | ~187k | Format JS/HTML/CSS/MD | Already have clang-format for C; Prettier/Black via `pkg` + Phase 21 `formatterTemplate`. |
| **Path Intellisense / Path Autocomplete** | ~50k | `./foo` file-path completions | Tiny CodeC feature later; LSP often includes this. |
| **Tailwind IntelliSense** | ~41k | CSS class complete | Optional language pack after HTML LSP. |
| **Python / C++ runner plugins** | — | Run language X | **Already CodeC Packages** (`LanguageRegistry` + `pkg install`). Do not copy Acode’s JS runners. |
| Themes (One Dark Pro, VS Code Dark, Ayu) | many | Ace themes | **VS Code JSON themes** in TextMate (Dark+, Monokai, Dracula you already name). |

**Acode lesson that fits CodeC’s architecture (not Ace):**

1. **Core ships colour for the languages you edit on day one.**
2. **IntelliSense is an installable module**, not baked into a 50 MB APK.
3. **The store is “tap Install”** — CodeC already has this UX as
   **Packages** (`ModulesScreen` / Phase 10): `pkg install python`,
   `pkg install clang`. Extend that catalog with **language intelligence**
   rows: “C IntelliSense (clangd)”, “Python IntelliSense (pylsp)”,
   “JS/TS IntelliSense (typescript-language-server)” — same tap, same
   terminal install, then the editor attaches LSP.

Do **not** build an Ace/JS plugin runtime. That would throw away Sora
and Phase 25 numbers. Replicate the *install UX*, not the WebView.

### 8.3 Best free resources that actually fit *this* project

Filter: Android-native or stdio LSP, OSI license, no GPL paste into
the APK, works with Sora 0.24, phone-sized (don’t bundle LLVM in APK).

#### A. Colour — must-ship in the APK (small JSON)

Sora module: `io.github.rosemoe:language-textmate` (LGPL-2.1, already
in the BOM).

Grammars (MIT, same as VS Code). Pull from
[microsoft/vscode](https://github.com/microsoft/vscode) `extensions/*/syntaxes`
and language-specific MIT repos — **assets, not copied engine code**.

| Language | Scope | Grammar source (MIT unless noted) | Why CodeC |
|---|---|---|---|
| C | `source.c` | vscode `cpp` extension (`c.tmLanguage.json`) | Default language |
| C++ | `source.cpp` | same | Phase 21 C++ |
| Python | `source.python` | vscode `python` / MagicPython | Phase 12 |
| JavaScript | `source.js` | vscode `javascript` | nodejs package |
| TypeScript | `source.ts` / `source.tsx` | [microsoft/TypeScript-TmLanguage](https://github.com/Microsoft/TypeScript-TmLanguage) MIT | `.ts` already in registry; **currently coloured as JS/plain** |
| HTML | `text.html.basic` | vscode `html` | Web preview |
| CSS / SCSS | `source.css` | vscode `css` | **split from HTML** (today they share one regex) |
| JSON | `source.json` | vscode `json` | configs |
| Shell | `source.shell` | vscode `shellscript` | terminal scripts |
| Markdown | `text.html.markdown` | [vscode-markdown-tm-grammar](https://github.com/microsoft/vscode-markdown-tm-grammar) MIT | docs |
| PHP | `source.php` | vscode `php` | php package |
| Ruby | `source.ruby` | vscode `ruby` | ruby package |
| Lua | `source.lua` | vscode lua / PRG | lua54 package |
| XML | `text.xml` | vscode `xml` | Android/HTML-adjacent |
| YAML | `source.yaml` | Red Hat / vscode | `project.json` cousins |
| Go | `source.go` | vscode `go` | profile exists (repo later) |
| Rust | `source.rust` | vscode `rust` | profile exists (repo later) |

**Themes (looks like VS Code):** vscode `dark_plus.json`,
`Monokai-color-theme.json`, Dracula (MIT). Map through TextMate, retire
the 6-slot `CodeCScheme` as the *source* of colour (keep it as fallback).

**Optional later (bigger, native .so):** Sora `language-treesitter` +
[tree-sitter/tree-sitter-c](https://github.com/tree-sitter/tree-sitter-c)
etc. Only if TextMate still lies on C macros / Python f-strings.

#### B. Completions — install like Acode plugins, via Packages

Sora module: `io.github.rosemoe:editor-lsp` (LGPL-2.1).

Always-on **offline fallback** (no server):

| Resource | License | Role |
|---|---|---|
| [rafamadriz/friendly-snippets](https://github.com/rafamadriz/friendly-snippets) | MIT | Hundreds of VS Code snippets per language — replace `CodeCompletionEngine` tables |
| Buffer identifiers (keep a tiny scanner) | ours | Local symbols when LSP is off |
| [emmetio/emmet](https://github.com/emmetio/emmet) | MIT | HTML/CSS (`div>ul>li*5`) — the missing web complete |

**Per-language IntelliSense (tap Install in Packages = `pkg install`):**

| Language | Server | Typical size | License | Needs |
|---|---|---|---|---|
| C / C++ | **clangd** (LLVM) | with clang ~90 MB you already install | Apache-2.0 | `compile_flags.txt` or `-I` from settings |
| Python | **python-lsp-server** + **Jedi** | small on top of python | MIT | Phase 12 python |
| JS / TS | **typescript-language-server** + `typescript` | via nodejs | Apache-2.0 | Phase 20 nodejs |
| HTML / CSS / JSON | vscode-langservers-extracted (`vscode-html-languageserver` …) | node | MIT | nodejs |
| PHP | **intelephense** is proprietary; use **phpactor** or **Serenata** if we stay OSI | — | prefer OSI | php package |
| Ruby | **solargraph** | gem | MIT | ruby |
| Lua | **lua-language-server** (sumneko) | native binary | MIT | lua |
| Go | **gopls** | with golang | BSD | when golang is published |
| Rust | **rust-analyzer** | large | Apache/MIT | when rust is published |
| Shell | **bash-language-server** | node | MIT | nodejs |
| YAML | **yaml-language-server** | node | MIT | nodejs |

**Phone rule:** never auto-start every server. Attach **one** LSP for
the *active file’s language*, after the package is installed. Ghost +
chip strip still show the merged list (LSP first, then snippets, then
buffer words). `MAX_ITEMS = 8` must die for the strip — chips scroll;
the engine should offer **dozens**, the strip shows top N, ⌄ the rest.

#### C. Format (already half there)

| Lang | Tool | Already in CodeC? |
|---|---|---|
| C/C++ | clang-format | `ClangFormatBridge` / `formatterTemplate` |
| Python | black / ruff | template `black $SRC` |
| JS/HTML/CSS/MD | prettier | Acode’s most-installed plugin; add as node pkg later |
| Go | gofmt | in profile |
| Rust | rustfmt | in profile |

### 8.4 How “module install” should look in CodeC (Acode UX, our pipes)

CodeC already has three install surfaces. Don’t add a fourth island.

| Surface | Today | Use for language intelligence |
|---|---|---|
| **Packages hub** (Phase 10) | `pkg install python` / clang / node | Add catalog cards: “IntelliSense: C (clangd)”, “IntelliSense: Python (pylsp)”, “IntelliSense: JavaScript (tsserver)”, “HTML/CSS language server”. Same 1-tap → terminal. |
| **Editor install gate** (Phase 21) | RUN ▶ on `.py` → “Install python?” | Same prompt when you *type* in a `.py` and pylsp is missing: “Install Python IntelliSense? (~X MB)” — optional, not blocking colour. |
| **Old Clang module zip** | superseded by pkg | Do **not** revive zip modules for grammars. Grammars are tiny JSON in APK or a future `languages/` asset pack. |

**APK vs download:**

- **In APK:** TextMate engine + core grammars (C, C++, Python, JS/TS,
  HTML, CSS, JSON, Shell, Markdown) + friendly-snippets JSON + one VS
  Code theme. Colour works offline on first launch. This is the Acode
  “100 languages in the editor” feeling without Ace.
- **Download via Packages:** LSP servers (heavy). Colour never waits
  on them.

### 8.5 Universal language map (run vs colour vs complete — today)

| File | RUN (`LanguageRegistry`) | Colour (`LanguageType`) | Completions today |
|---|---|---|---|
| `.c` | TCC `cc` | regex C | 7 snippets + keywords + local ids |
| `.cpp` | clang gate | regex C++ | C snippets + `class` |
| `.py` | python pkg | regex Python | 9 snippets |
| `.js` | node | regex JS | 6 snippets |
| `.ts` | node `ts-node` | **JS regex** (wrong) | **JS snippets only** |
| `.html` | Web preview | HTML/CSS mashed | 11 HTML snippets |
| `.css` | none as run | mashed with HTML | same |
| `.json` | none | weak JSON | **empty** |
| `.md` | none | weak MD | 11 md snippets |
| `.sh` | bash | regex shell | 8 snippets |
| `.php` `.rb` `.lua` `.go` `.rs` | profiles exist | **TEXT (no colour)** | **none** |

Fixing colour for the last row is **one TextMate file each**, not a new
Kotlin tokenizer. That is why the regex file must go.

### 8.6 Recommended order (still waits on “Start Phase N”)

Specs now live under `docs/chat-phase29/` … `docs/chat-phase33/` (2026-09-05).

1. **Phase 29 — VS Code colour for every language we already run**
   TextMate + Dark+ / Monokai / Dracula. Expand `LanguageType` to match
   `LanguageRegistry` (php, ruby, lua, go, rust, yaml, xml, ts split).
   Device gate: a `.py` / `.html` / `.ts` / `.c` file looks like VS Code,
   not “6 crayon colours”. Typing budget from 25.1 must hold.

2. **Phase 30 — snippet packs + Emmet (offline, complete lists)**
   friendly-snippets + Emmet into the existing ghost/strip. Raises
   “every suggestion” *without* a 90 MB download. Phone UX unchanged.

3. **Phase 31 — Acode-style IntelliSense packages**
   `editor-lsp` + Packages cards. clangd first (C is still the default
   file), then pylsp, then tsserver. Install = existing `pkg` path.

Do not start these until the owner says so (Phase 28 may still be open).

### 8.7 Extra sources for this addendum

1. Acode plugin store — [acode.app/plugins](https://acode.app/plugins)
2. Ace Linters (Acode LSP) — [Sohil876/acode-plugin-ace-linters](https://github.com/Sohil876/acode-plugin-ace-linters)
3. Extra Syntax Highlights / Snippets / Emmet / Prettier Acode plugins
4. VS Code syntax highlight guide — [TextMate grammars](https://code.visualstudio.com/api/language-extensions/syntax-highlight-guide)
5. friendly-snippets — https://github.com/rafamadriz/friendly-snippets
6. TypeScript-TmLanguage — https://github.com/Microsoft/TypeScript-TmLanguage
7. In-tree: `LanguageRegistry.kt` (12 run profiles) vs `LanguageType` (9 colour buckets) vs `CodeCompletionEngine.MAX_ITEMS`
