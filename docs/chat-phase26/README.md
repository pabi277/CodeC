# CodeC Phase 26 — Typing Experience 2.0

> **Status:** 📋 **PLANNED — research complete, no code written.** Research
> basis: [`docs/EDITOR_MOBILE_RESEARCH.md`](../EDITOR_MOBILE_RESEARCH.md) §2, §5.
> **Owner starts with "Start Phase 26"; no PR/merge without explicit command.**

Owner complaint (2026-09-04, restated): *"good typing experience … shortcuts …
suggestions are good but problematic for phone."* Phase 22 put the strip above
the IME and added pair-caps; Phase 26 is the *interaction depth* round: popup
keys, smart-typing semantics, and honest guidance about IMEs.

| Part | Title | Cost | Effort |
|---|---|---|---|
| [26.1](PART_26_1_KEY_STRIP_2.md) | Key strip 2.0 — long-press popup keys, swipe layers, hold-repeat, user-editable sets | client-only | M |
| [26.2](PART_26_2_SMART_TYPING.md) | Smart typing — auto-indent, pair type-over & wrap-selection, comment toggle from strip, delete-word | client-only | M |
| [26.3](PART_26_3_IME_GUIDE.md) | Code-friendly IME guide panel + spacebar-slide tip + hardware parity check | client-only | S |

Invariant checklist inherited (do not regress): `imePadding()` (22.3),
`adjustResize`, strip rides flush above IME (22.2), RunKeySet context swap
(23.2), HW shortcuts (24.3), autosave (15/16), EditorUndoManager integrity.

Research anchors this phase relies on:
- **Termux extra-keys**: `{key: 'UP', popup: 'PGUP'}` long-press popup caps —
  density without rows (GPL app; *behavior only*).
- **Squircle CE**: read-only toggle in extended keyboard; auto-closing pairs
  that *skip over* a matching closer (Apache-2.0; safest reference).
- **Unexpected-Keyboard / FlorisBoard**: swipe-on-key symbol layers; spacebar
  caret-slide — gestures users already expect on a phone.
- **Sora `SymbolInputView`**: display-vs-insert separation is the same data
  model CodeC's `EditorKeyDef` already has — 26.1 extends it, it does not
  abandon it.
