# CodeC Phase 33.1 — First-run tiles

**Status:** 📋 PLANNED · **Cost:** `[client-only]` · **Effort:** M
· **Target:** first-launch flag in DataStore, Projects empty / welcome

---

## 1. Design

On first launch (no last file): **three tiles only** —

| Tile | Opens | RUN |
|---|---|---|
| **C — works offline** | `main.c` template, caret in `main` | TCC, no dialog |
| **Python** | `main.py` | existing Phase 21 install gate if python missing |
| **HTML preview** | `index.html` | Web preview |

Returning users: last file (Phase 16) unchanged. A “show welcome once”
Settings reset is enough for testers.

## 2. Exit condition

```text
(Device, fresh install)
1. Three tiles; tap C → code + Keys; RUN prints hello with no Packages speech.
2. Second launch opens last file, no tiles.
3. Python tile: install sheet only if python missing.
PASS = all three.
```
