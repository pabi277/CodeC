# CodeC Phase 31.1 — editor-lsp client

**Status:** 📋 PLANNED · **Cost:** `[client-only]` · **Effort:** L
· **Depends on:** 25.2 Sora host, 27 CompletionItem
· **Target:** new `ui/editor/lsp/*`, `CodeCLanguage.requireAutoComplete`

---

## 1. Design

Add `io.github.rosemoe:editor-lsp` from the Sora BOM. Stdio client to a
binary under `$PREFIX/bin`. Merge:

LSP completions → existing `CompletionItem` (kind from LSP)
→ Phase 27 ghost/strip/panel.

LSP diagnostics → existing squiggles (`EditorDiagnostic`), **merge** with
compiler diagnostics (don’t double-paint).

| # | Rule |
|---|---|
| L1 | One server process per language, lazy on first completion in that language. |
| L2 | Destroy on tab close / language switch / master completion OFF. |
| L3 | Timeout / crash → silent fallback to snippets; log in AppLogger. |
| L4 | No network LSP. |
| L5 | Completions still off-main; 25.1 completion refresh budget ≤ 2 frames for *showing* chips (LSP itself may be slower — ghost may lag; don’t block keys). |

## 2. Exit condition

```text
(Host tests + device with a stub or real clangd)
1. With server missing: snippets still appear (no crash).
2. With server present: a member/local that snippets cannot know appears in chips.
3. Completion master OFF: no LSP process.
PASS = all three.
```
