# CodeC Phase 29 — VS Code colour (TextMate)

> **Status:** 🚧 **IMPLEMENTED (2026-09-05, owner: "Start phase 29") — CI
> pending, device round pending.** All three parts built on the session
> branch (29.1 core + 29.2 language parity + 29.3 regex retirement —
> records below). The gate is the owner's device round (retest card
> [`docs/TROUBLESHOOTING.md` §12](../TROUBLESHOOTING.md)) + the 25.1-law
> budgets (keystroke p95 ≤ 16.7 ms on bench.c; APK delta ≤ +1.5 MiB).
> No PR/merge without the owner's command.
>
> Original plan (2026-09-05, docs only): colour is very bad; the app is
> universal, not C-only. Research:
> [`OSS_REPLACEMENT_RESEARCH.md`](../OSS_REPLACEMENT_RESEARCH.md) §2.1 / §8,
> [`PHONE_UX_ANALYSIS.md`](../PHONE_UX_ANALYSIS.md) change 4.

Same move as Phase 25.2: **depend on a Sora module, delete our copy.** The
widget was already Sora; the tokenizer is now sora `language-textmate`
(tm4e, VS Code grammars) — the ~690-LOC regex highlighter is off the live
editor hot path (fallback only).

```
  29.1  language-textmate + core grammars + Dark+ / Monokai / Dracula   ✅ BUILT
              │
              ▼
  29.2  LanguageType matches every LanguageRegistry extension           ✅ BUILT
              │
              ▼
  29.3  Regex tokenize OFF the editor hot path (keep LanguageType.fromFileName) ✅ BUILT
```

| Part | Title | Cost | Effort | State |
|---|---|---|---|---|
| [29.1](PART_29_1_TEXTMATE_CORE.md) | Sora `language-textmate` + VS Code grammars + themes | client-only | M | ✅ implemented |
| [29.2](PART_29_2_LANGUAGE_PARITY.md) | Colour every run-profile language (split HTML/CSS/TS) | client-only | S | ✅ implemented |
| [29.3](PART_29_3_RETIRE_REGEX.md) | Drop regex analyzer from the live editor | client-only | S | ✅ implemented |

**Budgets (device, release APK, same 25.1 law):** keystroke p95 still
≤ 16.7 ms on bench.c; APK delta **≤ +1.5 MiB**; a `.c` / `.py` / `.html` /
`.ts` file looks like VS Code Dark+ (owner screenshot vs desktop).

**License:** `language-textmate` = LGPL-2.1 (same as sora-editor — depend,
never fork; notices: `assets/licenses/SORA_LANGUAGE_TEXTMATE_LGPL.txt`).
Grammars/themes = MIT from vscode / TypeScript-TmLanguage / LuaLS
(`assets/licenses/TEXTMATE_GRAMMARS_MIT.txt`). Owner already accepted LGPL
for the editor widget (Phase 25.2).

**Not this phase:** LSP, snippets, first-run UX, hiding the tab bar.
