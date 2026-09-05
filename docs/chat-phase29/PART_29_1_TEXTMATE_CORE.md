# CodeC Phase 29.1 — TextMate core

**Status:** 📋 PLANNED · **Cost:** `[client-only]` · **Effort:** M
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
