# CodeC Phase 30.3 — Strip capacity

**Status:** 📋 PLANNED · **Cost:** `[client-only]` · **Effort:** S
· **Depends on:** 27.2, 30.1

---

## 1. Design

`CodeCompletionEngine.MAX_ITEMS = 8` is why the list feels empty.
The **strip** may still show top N chips (thumb); the **engine** must
return a longer ranked list. Ghost = rank 0. ⌄ more = the rest (already
27.2). Horizontal scroll on chips is required.

Raise/remove the engine cap; keep a safety cap (e.g. 50) for LSP-later.
Host tests: prefix `i` in a C file with snippets loaded → more than 8
candidates available to policy.

## 2. Exit condition

```text
(Device)
1. Type a short prefix in Python — chips scroll; ⌄ shows more than 8.
2. Ghost still only the top-1; Enter still newline.
PASS = both.
```
