# CodeC Phase 29.1 — TextMate core

**Status:** 🚧 IMPLEMENTED (2026-09-05) · **Cost:** `[client-only]` · **Effort:** M
· **Depends on:** Phase 25.2 (Sora host)
· **Target:** `app/build.gradle.kts` (Sora BOM already present),
  `ui/editor/sora/CodeCLanguage.kt` / `CodeCAnalyzer.kt`,
  `app/src/main/assets/textmate/`

---

## 1. Design

Replace `CodeCAnalyzer`’s full-file regex tokenize with Sora
`TextMateLanguage` (module `io.github.rosemoe:language-textmate`, tm4e).

| # | Rule |
|---|---|
| T1 | Add `language-textmate` from the **same BOM** as `editor` (0.24.x). Do not pin a second version. |
| T2 | Load grammars once per process (`GrammarRegistry`). Files live under `assets/textmate/`. |
| T3 | Core set in APK (offline day one): C, C++, Python, JS, TS, HTML, CSS, JSON, Shell, Markdown. |
| T4 | Default theme = VS Code Dark+ JSON; keep Monokai + Dracula as the existing Settings editor-theme enum. |
| T5 | `CodeCLanguage` still owns completions, indent, symbol pairs, no-op formatter. Only `getAnalyzeManager()` changes. |
| T6 | Do **not** shade/repackage tm4e (LGPL replaceability, same 25.2 law). |

Research notes: [Sora using-language](https://project-sora.github.io/sora-editor-docs/guide/using-language);
grammars from microsoft/vscode MIT extensions.

## 2. Implementation steps

1. Gradle: `implementation("io.github.rosemoe:language-textmate")` via BOM.
2. Copy MIT `.tmLanguage.json` + `language-configuration.json` + one theme JSON into assets. Record SPDX / NOTICE in `assets/licenses/`.
3. `GrammarRegistry.loadGrammars` at Application / first editor open (once).
4. Map `LanguageType` → scope name (`source.c`, `source.python`, …).
5. Host tests: scopes for `.c` `.py` `.html`; assets exist; theme enum still round-trips.
6. Device: open bench.c — colour not crayon; typing still 60 fps.

## 3. Exit condition

```text
(Device, release APK)
1. Open a C file with keywords/strings/comments — scopes look like VS Code Dark+.
2. Type 60 keys in bench.c: no “stuck”; owner says colour is no longer “very bad”.
3. Settings editor theme still switches Monokai / Dracula / Dark+.
4. About still shows sora LGPL; APK delta ≤ +1.5 MiB vs pre-29.
PASS = all four.
```

---

## 4. IMPLEMENTATION RECORD (2026-09-05, owner: "Start phase 29")

All six T-rules followed. Built on the session branch; CI + device round
pending at write time.

### 4.1 What shipped

- **Gradle (T1):** `sora-language-textmate = io.github.rosemoe:language-textmate`
  added to `gradle/libs.versions.toml` **version-ref'd to the same
  `soraEditor = 0.24.6`** as the editor widget (no second pin);
  `implementation` in `app/build.gradle.kts`. Binary dependency only (T6).
- **Assets (T2/T3):** `assets/textmate/grammars/` — 24 unmodified MIT
  grammar JSONs (microsoft/vscode ×22, LuaLS/lua.tmbundle ×1,
  MagicRegExp for C++ raw-string regexes; `TypeScript-TmLanguage` grammars
  are consumed via the copies vscode ships in its javascript /
  typescript-basics extensions). Core set per T3: C, C++, Python, JS, TS
  (+TSX), HTML (+derivative), CSS, JSON, Shell, Markdown — plus the 29.2
  set (Go, Rust, PHP, Ruby, Lua, XML, YAML×3). Raw 2.24 MB, ~234 KB
  gzipped in the APK. Attribution + MIT text:
  `assets/licenses/TEXTMATE_GRAMMARS_MIT.txt`.
- **Themes (T4):** `assets/textmate/themes/` — `dark-plus.json`
  (**flattened** from vscode `dark_vs.json` (colors) + `dark_plus.json`
  (tokenColors), JSONC→strict-JSON, with the classic Dark+ editor chrome
  colors vscode moved to workbench defaults restored: caret `#AEAFAD`, line
  highlight `#282826`, line numbers `#858585/#C6C6C6`, selection `#264F78`,
  find-match `#623315`, suggest-widget colors); `monokai.json` (vscode
  theme-monokai, comments stripped); `dracula.json` + `github-dark.json`
  **authored for CodeC** from the app's Phase-12 palettes applied to the
  Dark+ token-scope structure (clean-room: same scopes, our colors — see
  §4.3 deviations).
- **Registry (T2):** new `ui/editor/sora/TextMateGrammars.kt` (pure,
  Android-free: the LanguageType→scope map + per-language grammar SETS —
  embedded scopes resolve only when the included grammar is registered too:
  HTML→[html, html-derivative, js, css], PHP→[php-html, php, html, css, js,
  json, xml], YAML→[yaml, yaml-1.2, yaml-embedded], …) and
  `ui/editor/sora/TextMateSupport.kt` (idempotent, thread-safe):
  `ensureInitialized` (installs `AssetsFileResolver`, makes a real theme
  current — **the TextMate analyzer snapshots the theme in its
  constructor**, so this must precede the first language), 
  `ensureLanguageLoaded` (registers one language's set, once per process),
  `warmUp` (background preload of the core set).
- **Wiring:** `MainActivity.onCreate` launches
  `TextMateSupport.warmUp(applicationContext)` on `Dispatchers.Default`
  (runs while the user navigates to the editor). `SoraEditorHost`'s
  language effect suspends to `Dispatchers.Default` for
  ensure+create, then `setEditorLanguage` on main; its theme effect calls
  `TextMateThemes.applyTheme(type)` (loads the theme asset on first use,
  makes it current, returns a fresh `TextMateColorScheme`) and
  `editor.setColorScheme(scheme)` — attaching the scheme re-runs the
  analysis so token colors switch immediately.
- **`CodeCLanguage` (T5):** `create(language, fileName)` builds a sora
  `TextMateLanguage.create(scope, /*collectIdentifiers=*/false)` and
  **only `getAnalyzeManager()` changed** — completions
  (`CodeCompletionEngine`), indent (`indentAdvanceFor`), symbol pairs
  (`symbolPairsFor`) and the no-op formatter are untouched, so the
  device-accepted 25.2/26/27 behavior carries over verbatim. Lifecycle:
  sora's `setEditorLanguage` destroys the returned analyzer, so
  `CodeCLanguage.destroy()` destroys only what the editor does NOT (the
  TextMate language object, or the fallback analyzer when TextMate is
  absent).
- **Settings:** `EditorThemeType` gained `VS_CODE_DARK_PLUS("VS Code Dark+")`
  (first = default in the picker) and a `displayName` (the picker no longer
  munges enum names); `ThemeManager` defaults new users to Dark+ (stored
  choices untouched; unresolvable names fall back to Dark+ instead of
  Dracula). A Compose palette `VSCodeDarkPlusTheme` mirrors the JSON so the
  ghost color / settings preview / status accents match.
- **License notices:** `assets/licenses/SORA_LANGUAGE_TEXTMATE_LGPL.txt`
  (module = LGPL-2.1, binary-only, same 25.2 checklist) +
  `TEXTMATE_GRAMMARS_MIT.txt` (per-file upstream paths); the Settings
  "Open-source licenses" line now names both.

### 4.2 Host tests

- `TextMateGrammarsTest` (pure JVM): **every `LanguageRegistry` extension
  maps to a TextMate scope** (the 29.2 promise, pinned); ts-vs-tsx
  disambiguation; embed sets; unique names/scopes/paths; warm-up covers the
  T3 core set; TEXT→null.
- `TextMateSupportTest` (Robolectric, real APK assets): every grammar +
  theme asset opens; all four themes load and resolve colors (Dark+
  background asserted = `#1E1E1E`); `warmUp` registers every core scope;
  **the analyzer for every colourable language is sora's TextMate
  analyzer, not the regex `CodeCAnalyzer`** (the 29.3 exit condition,
  asserted at code level); TEXT falls back to the regex analyzer; a missing
  grammar degrades instead of crashing.
- Deleted: `CodeCThemeMapTest` + `CodeCScheme.kt` (the slot-based scheme
  the TextMate themes replace).

### 4.3 Deviations from the spec (recorded)

1. **`GITHUB_DARK` kept** (T4 named only Monokai/Dracula + Dark+ default):
   removing it would silently downgrade users who picked it; it gets a
  TextMate theme like the others (authored, see above). The Settings
  picker shows four themes, Dark+ first.
2. **Dracula / GitHub-Dark are authored, not copied**: the official
   dracula/visual-studio-code repo builds its theme JSON from sources (no
   stable committed file), and github-vscode-theme moved repos; instead of
   chasing builds, both were authored from CodeC's existing palettes with
   the Dark+ scope structure. The exit conditions only demand Dark+ look
   like VS Code; the other two must look like themselves.
3. **`language-configuration.json` files are NOT shipped** (T2 listed them
   as optional "core set" items): T5 keeps CodeC's own indent + symbol
   pairs, and sora's TextMate folding/newline handlers need the
   configuration only for features CodeC does not use (folding was never
   shipped). Saves ~40 KB and one moving part; noted for Phase 31+ if
   folding ever ships.
4. **Warm-up is lazy for PHP/Ruby**: their embed chains (8 grammars) are
   the heaviest and the languages are the rarest — they register on first
   open instead of at app start (one-time ~1 MB parse on that open).
5. **The default `language-textmate` regexp engine is the pure-Java
   oniguruma port** (Joni) — the optional `oniguruma-native` module was
   NOT added (another AAR + native libs for marginal gain; budgets
   first).

### 4.4 Exit condition status

1. C file looks like Dark+ — **device round pending** (§12 retest card).
2. Typing budget on bench.c — device round pending (the analyzer is
   `AsyncIncrementalAnalyzeManager`-based and INCREMENTAL per line, a
   strict upgrade over 25.2's full-file re-tokenize per settled edit).
3. Theme switching — host-tested for all four; device confirmation in the
   round.
4. LGPL/MIT notices ship in-app + in `assets/licenses/`; APK delta
   measured from the CI artifact vs `main`'s (assets ≈ 234 KB compressed +
   module dex; budget ≤ +1.5 MiB — recorded when CI lands).

