# CodeC Phase 33.3 — Identity copy

**Status:** 📋 PLANNED · **Cost:** `[docs + client strings]` · **Effort:** S
· **Target:** `README.md`, About in Settings, Projects empty state,
  `prompt.md` one-liner if needed

---

## 1. Design

Replace “A C programming IDE” with:

> **CodeC — write and run C, Python, JavaScript, and HTML on your phone.
> C works offline with no setup.**

Do not claim Mini-Termux on the first screen. Keep honest TCC / userland
details in README further down.

## 2. Exit condition

```text
1. README first paragraph matches the sentence above.
2. Settings → About does not say C-only.
3. Empty Projects points at the three starters (33.1), not a blank list.
PASS = all three.
```
