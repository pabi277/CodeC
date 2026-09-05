# CodeC Phase 29.3 — Retire the regex hot path

**Status:** 📋 PLANNED · **Cost:** `[client-only]` · **Effort:** S
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
