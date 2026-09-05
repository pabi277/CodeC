# CodeC Phase 28.2 — CodeC Keys Layout Engine

**Status:** 📋 PLANNED — gated on 28.1 go · **Cost:** `[client-only]` ·
**Effort:** M · **Depends on:** 28.1 (go), 26.1 keycap model
· **Target files:** new `ui/keyboard/*` (model + renderer + gestures),
   Settings (layout chooser), host tests

---

## 1. Design

A **data-driven** keyboard: layouts are JSON → `KeycapModel` → Compose grid.
No layout is hardcoded pixels; rows define weighted caps with the 26.1
interaction fields (`popup`, `swipeUp`, `swipeDown`, `repeat`). This is what
lets per-language layouts, user remapping, and future themes stay cheap.

### 1.1 Keycap model (extends, never forks, `EditorKeyDef`)
```
KeycapModel(
  key: EditorKey,                    // reuse the pure edit semantics
  label, popup?, swipeUp?, swipeDown?, repeat?: Boolean,
  widthWeight, level: 0 // layer id (letters/symbols/macros)
)
KeyboardLayout(rows: List<List<KeycapModel>>, heightScale)
```
Defaults ship as JSON assets: `kb_c.json` (code-QWERTY), `kb_symbols.json`,
per-language macro rows reusing the Phase 16/22 language hook. Edit the JSON →
edit the keyboard (dev builds only; user edits come through Settings' strip
editor rules, 26.1).

### 1.2 Layouts
- **Code-QWERTY (default, portrait):** letters with swipe-up symbols/digits
  (Unexpected-density, gentle: only digits + `_-=;:/.\"'(){}[]<>`), full-size
  TAB, DEL (hold-repeat + word-delete per 26.2), ⏎, space (wide), arrows
  cluster as ONE wide cap with 4-direction slide OR separate thin row
  (decided on-device: A/B both in spike).
- **Symbols layer** (toggle cap): dense 5×6 grid of every programming symbol +
  common pairs; pairs still insert caret-between (22.5 law).
- **One-handed/landscape:** compressed width, half-height rows; no new model
  — new JSON.

### 1.3 Feel requirements (the vow)
- Key down → visual press state in the SAME frame (ripple too slow; use
  instant color/scale change), haptic tick optional (Settings).
- Hold-repeat: 150 ms initial / 40 ms repeat (26.1 shared timer code).
- Popup/flick gestures identical to 26.1 recognizer (literally the same
  pure state machine — the strip IS row 1..n of the same engine).
- Velocity: 60 dpi-aware layout grid, no allocation in gesture hot path
  (precomputed cap rects).

## 2. Implementation steps

1. `KeyboardLayout` JSON schema + loader + validation tests (weights, unknown
   keys → defaults, corrupt JSON → built-in).
2. `CodecKeyboard` composable: grid renderer reading layout; cap renderer with
   press state; popups (26.1) and flicks.
3. Gesture recognizer shared with strip; hold-repeat timer.
4. Default layouts (code-QWERTY, symbols, macros per language); language hook.
5. Settings: on/off (master), haptics, key-height slider, layout preview.
6. Wire input path chosen in 28.1 (S1 or S2).

## 3. Exit condition

```text
(Device, release)
1. Type a 200-char C program with symbols entirely on CodeC Keys — no IME
   opened once; timing budgets hold (28.1 table).
2. Swipe-up on 'p' yields '0'-style digit per JSON; long-press popup on ';'
   offers ':'; Flick-down on '()' inserts closer only.
3. Symbols layer opens/closes with one tap; layout switch per language loads
   the right JSON (C vs Python macro rows differ).
4. Del hold-repeat + word-delete behave per 26.2 tests; no double events.
5. Settings: keyboard OFF → system IME returns exactly as before (22.x intact).
PASS = all five. **Plus the four 28.1 carry-overs the owner waived** (they
ride this round by GO record): (a) Settings flip is REAL — with the keyboard
OFF, the system IME visibly returns to the editor; (b) a physical Bluetooth
keyboard still types into the editor WHILE the grid is up; (c) caret
movement + selection handles unaffected by the suppression; (d) the one-word
feel line for the full keyboard: *does it feel instant?* Runbook:
`docs/TROUBLESHOOTING.md` §11.
```
