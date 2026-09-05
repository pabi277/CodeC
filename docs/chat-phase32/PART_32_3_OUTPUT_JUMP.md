# CodeC Phase 32.3 — Output peek and jump

**Status:** 📋 PLANNED · **Cost:** `[client-only]` · **Effort:** S
· **Depends on:** Phase 11 output panel, `CompilerDiagnostics`

---

## 1. Design

First RUN on a session **expands** the output peek (Pydroid yellow-▶
feel), then may collapse. Tap `file:line` in output / squiggle → caret
on that line in the **user** filename, not `source_<stamp>.c`.

## 2. Exit condition

```text
(Device)
1. RUN hello.c — output visible without opening Terminal tab.
2. A compile error tap jumps to the right line in the open tab.
PASS = both.
```
