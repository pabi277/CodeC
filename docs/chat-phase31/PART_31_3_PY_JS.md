# CodeC Phase 31.3 — Python & JS IntelliSense cards

**Status:** 📋 PLANNED · **Cost:** `[client-only]` + pkg
· **Effort:** M · **Depends on:** 31.1, Phase 12 python / 20.1 nodejs in repo

---

## 1. Design

| Card | Install | Server |
|---|---|---|
| IntelliSense: Python | `python` + `python-lsp-server` (pip or pkg) | pylsp + Jedi (MIT, lighter than Pyright on phone) |
| IntelliSense: JavaScript / TypeScript | `nodejs` + `typescript` + `typescript-language-server` | tsserver stdio |

If a pip/npm name is not in the CodeC apt repo, **do not** invent
`com.termux` packages. Prefer a documented `pip install` / `npm i -g`
**inside PREFIX** after python/node exist — or defer the card until a
CodeC package exists. Record the choice in this file’s §4 at implement time.

Airplane mode: cards show “needs network once.”

## 2. Exit condition

```text
(Device)
1. .py: `os.path.` or `print(` — more than snippet; or honest “install IntelliSense”.
2. .js: `document.` or `console.` after tsserver install.
3. Uninstall / missing binary → fallback snippets, no crash.
PASS = all three.
```
