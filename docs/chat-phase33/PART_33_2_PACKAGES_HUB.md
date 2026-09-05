# CodeC Phase 33.2 — Packages hub for humans

**Status:** 📋 PLANNED · **Cost:** `[client-only]` · **Effort:** S
· **Target:** `ModuleCatalog` / `ModulesScreen`

---

## 1. Design

Two sections:

1. **Languages & IntelliSense** (python, nodejs, clang, clangd/pylsp/tsserver
   cards from Phase 31 if present)
2. **Unix tools** (nano, ripgrep, tmux, …) collapsed by default

Same `pkg install` plumbing. No new store format.

## 2. Exit condition

```text
(Device)
1. Packages opens on Languages; Unix is a collapsed header.
2. INSTALL python still streams in Terminal (Phase 10 regression).
PASS = both.
```
