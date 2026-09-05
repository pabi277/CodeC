# CodeC Phase 30 — Offline completeness (snippets + Emmet)

> **Status:** 📋 **PLANNED — no code.** Owner: suggestions don’t give every
> suggestion; phone coding is painful. Phase 27 already fixed **accept UX**
> (ghost + chips). This phase fixes **what is offered** without a 90 MB LSP.
> Research: `OSS_REPLACEMENT_RESEARCH.md` §8.3.B,
> `PHONE_UX_ANALYSIS.md` change 5.
> **Starts only on `"Start Phase 30"`.** Prefer after 29 (colour) but **does
> not require 29**.

```
  30.1  friendly-snippets JSON → CompletionItem (replace Kotlin tables)
              │
              ▼
  30.2  Emmet for HTML/CSS (and JSX-ish) as completion items
              │
              ▼
  30.3  Engine: no MAX_ITEMS=8 hard cap; strip scrolls; ghost still top-1
```

| Part | Title | Cost | Effort |
|---|---|---|---|
| [30.1](PART_30_1_FRIENDLY_SNIPPETS.md) | MIT snippet packs as assets | client-only | M |
| [30.2](PART_30_2_EMMET.md) | Emmet expansions into the same pipeline | client-only | M |
| [30.3](PART_30_3_STRIP_CAPACITY.md) | Completeness vs chip UX | client-only | S |

**Law:** Phase 27 `CompletionPolicy` unchanged — Enter sacred, master
switch, no auto-commit. Snippets fill `CompletionItem`; ghost/strip/panel
only render.

**Not this phase:** clangd / pylsp (Phase 31).
