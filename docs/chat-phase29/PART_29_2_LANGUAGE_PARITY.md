# CodeC Phase 29.2 — Colour every language we already run

**Status:** 📋 PLANNED · **Cost:** `[client-only]` · **Effort:** S
· **Depends on:** 29.1
· **Target:** `LanguageType`, `CodeCLanguage` scope map, extra grammars

---

## 1. Design

Today `LanguageRegistry` has 12 run profiles; `LanguageType` has 9 buckets.
HTML+CSS share one regex; TS is JS.

| Extension | Run today | Colour today | After 29.2 |
|---|---|---|---|
| `.ts` / `.tsx` | node | JS regex | `source.ts` / `source.tsx` |
| `.css` / `.scss` | — | mashed HTML | `source.css` |
| `.php` `.rb` `.lua` `.go` `.rs` | profiles | TEXT | matching TextMate scopes |
| `.xml` `.yaml` / `.yml` | — | TEXT | `text.xml` / `source.yaml` |

Grammars may be extra MIT files in the same assets tree (still inside the
+1.5 MiB budget; gzip). If a grammar blows the budget, ship the run-profile
set first and defer Go/Rust.

## 2. Exit condition

```text
(Device)
1. Open hello.py, index.html, styles.css, app.ts — each has distinct,
   VS Code-like colour (CSS not identical to HTML).
2. Open a .lua / .php / .rb file — not plain white.
3. Unknown .txt stays uncoloured.
PASS = all three.
```
