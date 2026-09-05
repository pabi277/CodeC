# CodeC Phase 30.1 — friendly-snippets

**Status:** 📋 PLANNED · **Cost:** `[client-only]` · **Effort:** M
· **Depends on:** Phase 27 pipeline
· **Target:** `assets/snippets/`, `CodeCompletionEngine.kt`

---

## 1. Design

Replace hand-written `snippets(language)` tables with
[rafamadriz/friendly-snippets](https://github.com/rafamadriz/friendly-snippets)
(MIT) JSON. Load per `LanguageType`. Keep buffer-identifier scan as a
lower-priority source.

| # | Rule |
|---|---|
| S1 | Assets, not network. SPDX in `assets/licenses/`. |
| S2 | Prefix match stays case-insensitive (Phase 22.6 law). |
| S3 | Snippet `insertText` may contain VS Code tabstops (`$1`); sora snippet parser already exists — use it if cheap, else insert the body and park caret at first stop. |
| S4 | JSON/TEXT still get few/no snippets unless the pack has them. |

## 2. Exit condition

```text
(Device)
1. Type `for` in C and in Python — more than the old 1–2 snippets; chips scroll.
2. Type `doc` in HTML — DOCTYPE / html skeleton still appears (regression).
3. Master completion switch OFF → zero snippets computed (27.3).
PASS = all three.
```
