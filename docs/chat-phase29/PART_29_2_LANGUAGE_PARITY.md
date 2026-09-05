# CodeC Phase 29.2 — Colour every language we already run

**Status:** 🚧 IMPLEMENTED (2026-09-05) · **Cost:** `[client-only]` · **Effort:** S
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

---

## 3. IMPLEMENTATION RECORD (2026-09-05, owner: "Start phase 29")

### 3.1 What shipped

- **`LanguageType` split** (`MultiLanguageSyntaxHighlighter.kt`):
  `JAVASCRIPT` lost `ts`/`tsx` to a new **`TYPESCRIPT`** bucket;
  `HTML_CSS` split into **`HTML`** and **`CSS`** (scss rides CSS); new
  buckets **`GO`, `RUST`, `PHP`, `RUBY`, `LUA`, `XML`, `YAML`**. Every
  `LanguageRegistry` run-profile extension now lands in a bucket with a
  TextMate grammar (pinned by `TextMateGrammarsTest`).
- **Scope map** (`TextMateGrammars.kt`): `source.ts` vs `source.tsx` per
  FILE (`SoraEditorHost` now receives `fileName` — one bucket, two
  grammars), `text.html.basic` (+ `text.html.derivative`, `source.js`,
  `source.css` embeds), `source.css`, `source.go`, `source.rust`,
  `text.html.php` (wrapping `source.php` + HTML), `source.ruby`,
  `source.lua`, `text.xml`, `source.yaml` (+ `source.yaml.1.2`,
  `source.yaml.embedded`).
- **Downstream split fallout** (all `else`-branched, no behavior cliff):
  - `CodeCompletionEngine`: TS gets JS snippets + keywords **+ a TS-only
    keyword set and interface/type/enum snippets**; HTML keeps the HTML
    snippets/triggers; CSS keeps the CSS ones; new languages fall to
    identifiers-only.
  - `EditorLineOps.commentPrefixFor`: `#` for Ruby/Lua/YAML, `//` for
    TS/Go/Rust, `<!--` for HTML/XML (PHP keeps `//` — its lines are PHP).
  - `EditorKeySet.languageTail`: HTML keeps `</>`, CSS gets `:`/`;`, TS
    shares the JS tail (`` `` `` pair, `=>`).
  - **File-save fix (unplanned but right):** `WebFileSupport.normalizeFileName`
    used to rename `test.go` to `test.go.c` (no bucket knew `.go`); with
    the new buckets every run-profile extension is kept as typed.
- **Regex fallback** (`tokenGroupKinds`/`pattern`): TS rides the JS regex;
  HTML/CSS ride the old html/css regex; Go/Rust/PHP/Ruby/Lua/XML/YAML have
  NO regex rules (TextMate only) — tokenize returns empty for them, which
  is exactly their pre-29 TEXT behaviour if a grammar ever fails to load.

### 3.2 Trimmed / deferred (all inside the +1.5 MiB budget law)

- `cpp.embedded.macro` (385 KB) NOT shipped — macro BODIES inside `#define`
  colour plainer; every other C/C++ construct is full-fidelity.
- `.jsx` stays on `source.js` (the plan's table only split `.ts`/`.tsx`);
  `.scss` stays on `source.css` per the same table.
- Markdown fence embeds beyond the common set (html/js/css/python/shell)
  load only after that language has been opened once; rarer fences
  (java, cs, sql…) stay uncoloured (not shipped at all).
- Ruby's rare embeds (sql, graphql, haml…) not shipped — heredocs for
  those stay plain.
- `.tsx` grammar (`typescriptreact`, 228 KB) IS shipped (the plan's table
  demands it).

### 3.3 Exit condition status

1. Distinct py/html/css/ts colour — host tests pin scope + asset loading;
   **device round pending** (TROUBLESHOOTING §12).
2. `.lua` / `.php` / `.rb` coloured — same.
3. `.txt` uncoloured — pinned by test (`TEXT → null` scope, regex fallback
   analyzer).
