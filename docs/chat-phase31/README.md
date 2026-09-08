# CodeC Phase 31 — IntelliSense as Packages (Acode-style install)

> **Status:** 🚧 **31.1–31.6 IMPLEMENTED** (2026-09-07/08, owner:
> "Start phase 31" … previous chat dropped, this chat wired the
> editor). L1–L5 host-tested. Production wire is the hand-rolled
> stdio client (sora `editor-lsp` AAR gated off — minSdk 26 / AGP
> red). Chip strip + ghost now merge LSP on the VM's debounced leg. Acode: tap
> "Acode LSP" in the plugin store. CodeC: tap a **Packages** card →
> existing `pkg install` or `npm i -g`. Do **not** build an Ace/JS
> plugin runtime. Research: `OSS_REPLACEMENT_RESEARCH.md` §8.2–8.4,
> `PHONE_UX_ANALYSIS.md` changes 6 and 13. **Never bundle clangd in
> the APK.** Never start every language server at once — attach **one**
> for the active file after the package exists.

```
  31.1  editor-lsp client + map LSP items → CompletionItem / diagnostics
              │  ← IMPLEMENTED (engine + tests); production wire in follow-up
              ▼
  31.2  C/C++: clangd via existing clang package + Packages card
              │  ← IMPLEMENTED (Packages hub install card)
              ▼
  31.3  Python pylsp + JS/TS tsserver cards (node/python already in repo)
              │  ← IMPLEMENTED (Packages hub install cards)
              ▼
  31.4  Shell + HTML + CSS + JSON + YAML (5 npm cards; device round)
              │  ← IMPLEMENTED (5 packages hub install cards)
              ▼
  31.5/31.6  hand-rolled stdio wire + editor (chip/ghost) attach
              ← IMPLEMENTED (host-tested; device recipe pending)
```

| Part | Title | Cost | Effort | Status |
|---|---|---|---|---|
| [31.1](PART_31_1_EDITOR_LSP.md) | Sora `editor-lsp` adapter | client-only | L | ✅ engine + tests; AAR gated off |
| [31.2](PART_31_2_CLANGD.md) | clangd install + attach | client + pkg (clang already) | M | ✅ card shipped; attach = stdio client |
| [31.3](PART_31_3_PY_JS.md) | pylsp / tsserver cards (Python install path updated 2026-09-07 — chained `python-pip && pip install`) | client + pkg | M | ✅ cards shipped; attach = stdio client |
| [31.4](PART_31_4_LANGUAGE_EXPANSION.md) | shell + HTML + CSS + JSON + YAML (5 npm cards) | client + npm | M | ✅ cards shipped; attach = stdio client |
| [31.5](PART_31_5_STDIO_WIRE.md) | Hand-rolled stdio wire + editor attach | client-only | L | 🚧 wired; device recipe pending |

**Phone law:** missing server → snippets (Phase 30) still work; no modal
blocking typing. Optional sheet: "Install Python IntelliSense?" like
Phase 21 RUN gate.

**Heavy packages (gopls, rust-analyzer):** not 31. Catalog may list them
disabled until those compilers are `inRepository`.
