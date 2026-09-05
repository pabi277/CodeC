# CodeC Phase 32.2 — One meaning row

**Status:** 📋 PLANNED · **Cost:** `[client-only]` · **Effort:** S
· **Depends on:** 28.3 if Keys ON; else 27.2 strip

---

## 1. Design

If 28.3 is merged: Keys ON → chips **are** row 0; do not also show the
Phase 27 strip. If 28.3 is not merged: still **never** show chips +
utility keys + nav at once — hide nav (32.1) and keep strip flush above
Keys/IME (existing 22.2/27.2).

Caret line must stay above the keyboard (22.3 regression).

## 2. Exit condition

```text
(Device)
1. Type a prefix with Keys ON — at most one extra row of chips/macros
   between code and letter keys.
2. L0 (Keys off) — 27.x strip recipes still pass.
PASS = both.
```
