# CodeC Phase 31 — IntelliSense as Packages (Acode-style install)

> **Status:** 🚧 **31.1 IMPLEMENTED** (2026-09-07, owner: "Start phase 31").
> L1–L5 host-tested (25/25 pass on a local JVM); the production
> sora-LSP wire is the device round's gate (see
> [PART_31_1 §3.1](PART_31_1_EDITOR_LSP.md#31-production-provider-the-real-wire--not-in-this-commit)).
> Acode: tap “Acode LSP” in the plugin store. CodeC: tap a **Packages**
> card → existing `pkg install`. Do **not** build an Ace/JS plugin
> runtime. Research: `OSS_REPLACEMENT_RESEARCH.md` §8.2–8.4,
> `PHONE_UX_ANALYSIS.md` changes 6 and 13.
> **Never bundle clangd in the APK.** Never start every language server
> at once — attach **one** for the active file after the package exists.

```
  31.1  editor-lsp client + map LSP items → CompletionItem / diagnostics
              │  ← IMPLEMENTED (engine + tests); production wire in follow-up
              ▼
  31.2  C/C++: clangd via existing clang package + Packages card
              │
              ▼
  31.3  Python pylsp + JS/TS tsserver cards (node/python already in repo)
```

| Part | Title | Cost | Effort | Status |
|---|---|---|---|---|
| [31.1](PART_31_1_EDITOR_LSP.md) | Sora `editor-lsp` adapter | client-only | L | 🚧 engine + tests; wire pending |
| [31.2](PART_31_2_CLANGD.md) | clangd install + attach | client + pkg (clang already) | M | 📋 PLANNED |
| [31.3](PART_31_3_PY_JS.md) | pylsp / tsserver cards | client + pkg | M | 📋 PLANNED |

**Phone law:** missing server → snippets (Phase 30) still work; no modal
blocking typing. Optional sheet: “Install Python IntelliSense?” like
Phase 21 RUN gate.

**Heavy packages (gopls, rust-analyzer):** not 31. Catalog may list them
disabled until those compilers are `inRepository`.
