# CodeC Phase 29.3 — Retire the regex hot path

**Status:** 🚧 IMPLEMENTED (2026-09-05) · **Cost:** `[client-only]` · **Effort:** S
· **Depends on:** 29.1 device-accepted
· **Target:** `CodeCAnalyzer`, `MultiLanguageSyntaxHighlighter.tokenize`
  used by the editor

---

## 1. Design

After TextMate is the live analyzer:

- `CodeCAnalyzer` regex path is deleted or becomes a test-only fallback.
- `SyntaxVisualTransformation` / `HighlightedCode` windowing is **dead
  Compose-BTF code**. Do not keep it “just in case” on the editor path.
- Keep `LanguageType.fromFileName` and keyword sets **only if**
  `CodeCompletionEngine` still needs them (until Phase 30). If 30 has not
  started, leave keyword sets as the snippet/keyword fallback.

Do not delete host tests until replacements exist; rewrite tests to assert
scope mapping, not regex spans.

## 2. Exit condition

```text
(CI + device)
1. Editor no longer calls MultiLanguageSyntaxHighlighter.tokenize on
   keystroke (code search / test).
2. 25.1 typing budget still holds on bench.c.
3. Phase 27 ghost/strip still receive completions (engine unchanged).
PASS = all three.
```

---

## 3. IMPLEMENTATION RECORD (2026-09-05, owner: "Start phase 29")

### 3.1 What shipped

- **The swap:** `CodeCLanguage.getAnalyzeManager()` returns sora's
  **TextMate analyzer (`AsyncIncrementalAnalyzeManager` subclass)** for
  every language with a grammar — an *incremental, line-based* analyzer
  (edits re-tokenize the changed lines and their state-dependents only),
  a strict upgrade over 25.2's `CodeCAnalyzer` full-file regex sweep per
  settled edit.
- **`CodeCAnalyzer` (regex) is the FALLBACK, not deleted**: it serves
  TEXT files (no grammar) and any file whose grammar failed to load
  (`CodeCLanguage.create` wraps `TextMateLanguage.create` in
  `runCatching` — a broken asset degrades to 22.x colour, never a crash).
  `TokenStyleIds` stays for that path.
- **`SyntaxVisualTransformation` / `HighlightedCode` windowing:** NOT
  deleted — it is **off the editor path** (nothing in the editor uses it
  since 25.2) but still powers the Templates screen preview, so it stays
  as a preview-only component. The spec's "dead Compose-BTF code" was the
  EDITOR's copy of that pipeline; that copy is gone with the 25.2 swap.
- **Kept, as spec'd:** `LanguageType.fromFileName` (still THE
  file→language mapping) and the keyword sets (`CodeCompletionEngine`
  still reads them — Phase 30 snippets will build on them). TS keywords
  added (29.2 record).
- **`CodeCScheme` + `CodeCThemeMap` DELETED** (slot-based scheme): the
  editor colour now resolves through `TextMateColorScheme` + the theme
  assets; `CodeCThemeMapTest` replaced by `TextMateSupportTest` theme
  assertions.

### 3.2 Exit condition status

1. No regex tokenize on keystroke — **pinned by host test**
   (`TextMateSupportTest.colourable languages use the textmate analyzer
   not the regex one`: for all 17 colourable buckets the analyze manager
   is the TextMate one; `CodeCAnalyzer` only for TEXT).
2. Typing budget on bench.c — device round pending (incremental analyzer
   + 25.2's off-main-thread law unchanged).
3. Phase 27 ghost/strip completions — engine UNTOUCHED
   (`CodeCLanguage.requireAutoComplete` is byte-identical); the language
   factory change only swaps the analyzer. Device confirmation in the
   round.
