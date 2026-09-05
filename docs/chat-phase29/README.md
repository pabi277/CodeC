# CodeC Phase 29 — VS Code colour (TextMate)

> **Status:** 📋 **PLANNED — no code.** Owner (2026-09-05): colour is very
> bad; the app is universal, not C-only. Research:
> [`OSS_REPLACEMENT_RESEARCH.md`](../OSS_REPLACEMENT_RESEARCH.md) §2.1 / §8,
> [`PHONE_UX_ANALYSIS.md`](../PHONE_UX_ANALYSIS.md) change 4.
> **Starts only on `"Start Phase 29"`.** No PR/merge without the owner's
> command. **Do not start while Phase 28.3/28.4 are the active head** unless
> the owner names 29 anyway — 29 does not depend on Keys.

Same move as Phase 25.2: **depend on a Sora module, delete our copy.**
The widget is already Sora; the tokenizer is still
`MultiLanguageSyntaxHighlighter` regex (~690 LOC) fed through
`CodeCAnalyzer`. Go / Rust / PHP / Ruby / Lua **run**
(`LanguageRegistry`) but colour as `TEXT`.

```
  29.1  language-textmate + core grammars + Dark+ / Monokai / Dracula
              │
              ▼
  29.2  LanguageType matches every LanguageRegistry extension
              │
              ▼
  29.3  Regex tokenize OFF the editor hot path (keep LanguageType.fromFileName)
```

| Part | Title | Cost | Effort |
|---|---|---|---|
| [29.1](PART_29_1_TEXTMATE_CORE.md) | Sora `language-textmate` + VS Code grammars + themes | client-only | M |
| [29.2](PART_29_2_LANGUAGE_PARITY.md) | Colour every run-profile language (split HTML/CSS/TS) | client-only | S |
| [29.3](PART_29_3_RETIRE_REGEX.md) | Drop regex analyzer from the live editor | client-only | S |

**Budgets (device, release APK, same 25.1 law):** keystroke p95 still
≤ 16.7 ms on bench.c; APK delta **≤ +1.5 MiB**; a `.c` / `.py` / `.html` /
`.ts` file looks like VS Code Dark+ (owner screenshot vs desktop).

**License:** `language-textmate` = LGPL-2.1 (same as sora-editor — depend,
never fork; About already ships `SORA_EDITOR_LGPL.txt`). Grammars/themes =
MIT from vscode / TypeScript-TmLanguage. Owner already accepted LGPL for
the editor widget.

**Not this phase:** LSP, snippets, first-run UX, hiding the tab bar.
