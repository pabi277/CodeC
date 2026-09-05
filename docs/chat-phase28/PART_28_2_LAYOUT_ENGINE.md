# CodeC Phase 28.2 — CodeC Keys Layout Engine

**Status:** ✅ **MERGED into main 2026-09-05** on owner command (built + three device rounds the same day; CI green) —
engine in `:app` `ui/keyboard/`, Settings + `EditorScreen` wired, host tests
pinning every law; **owner device rounds 1–2 landed same day, fixes below.**
**Default flipped ON (round 2).** · **Cost:** `[client-only]` ·
**Effort:** M · **Depends on:** 28.1 (go ✅), 26.1 keycap model

---

## 0. Build record — where it landed vs the plan

- **Model:** `KeycapModel(def: EditorKeyDef, widthWeight, repeat)` wraps —
  never forks — the 26.1 cap; weights/repeat DERIVE from the model (`wide`,
  `space`, DEL/arrows) unless the JSON overrides `"w"`/`"repeat"`.
- **One schema:** the layout file is `{"heightScale":f,"rows":[[<26.1 cap
  JSON>…]]}` — rows parsed by the strip's own `KeyStripStorage.deserialize`.
  Deviation from "defaults ship as JSON assets": **built-in code IS the
  default** (host-tested; corrupt file → built-in, bad row → row dropped —
  a typo in row 3 never deletes the keyboard).
- **Input path:** S2 as certified — and since round 2 the keyboard commits
  through `EditorViewModel.applyEditorKey` against the **live** buffer (the
  UI-snapshot path swallowed same-frame taps: the arrow complaint), while
  the snapshot pair remains the fallback + inert-preview path.
- **IME handoff:** `CodeEditor.setSoftKeyboardEnabled(!up)` (sora public
  API — no busy-loop needed in production), restored while a run waits for
  stdin (23.2) and on leaving the editor (`DisposableEffect`).
- **`EditorKey.Delete`** = DEL's model home (28.1's promise), serializes as
  `"delete"`; `:app`/`:bench` mirrors stay byte-identical (law).
- **Rows shipped:** 3 letter rows (flick-up `1234567890` / `_-=;:"'/.` /
  `(){}[]<>`), special row `⬆ TAB space ; ⌫ ⏎` (`;` carries its `:` popup —
  exit condition 2 honored on a real cap), utility row `SYM ← ↑ ↓ →` +
  language tail caps (`->` for C). Symbols layer 5×≤10 with the pair caps
  and flick-closer law; `ABC` one tap back. Shift: tap = ONCE (dies with
  the edit), hold on `⬆` = LOCK; uppercase derived, single ASCII letters.
- **Preview law (round 1):** every cap PRINTS its release in the corner
  (`q¹`, `;:`) and the big label SWAPS to it while held — the overflow
  bubble is gone (it was the only outside-bounds draw; the round-1 trace,
  headerless, pointed nowhere else and removing it removed the risk class).
- **Arrow nav = FLICKS (round 1 discovery):** Home/End/PgUp/PgDn as popups
  are unreachable under the 150 ms hold-repeat law — a latent flaw the
  shipped strip still has; on the grid they ride the arrows' flick-up,
  pinned by `arrowNavigationTravelsAsFlicksNotPopups`.
- **Space trackpad (round 2, Samsung law):** hold 260 ms → cap reads
  "⇄ caret" → slide moves the caret (12 dp/column, 28 dp/line, absolute
  origin quantization — the finger can never outrun the buffer); release
  after a slide types NOTHING, a hold without slide still spaces. Pure
  math in `SpaceTrack` + `SpaceTrackTest`; caret travel rides
  `moveCaretBy` → the selection branch (no undo noise).
- **Symbols layer re-cut to ONE KEY PER BUTTON (round 3, owner: "many keys
  in one touch … make one key per button")**: the multi-char caps (`()`,
  `{}`, `->`, `==`, `<=`, `&&`, …) are GONE — three full 10-wide rows of
  single characters (`!@#$%^&*~` + `- = + _ | \ / < > ?` + `[ ] { } ( ) ' "
  , .`) plus `ABC : ; TAB space ⌫ ⏎`. Brackets/quotes are their own keys;
  `->` is two taps. The 22.5 pair behavior stays where it always lived (the
  strip when the keyboard is OFF; the JSON can still express `pair` caps).
  Same round deleted the language-tail multi-char caps from the utility row —
  the letters layer is now language-INDEPENDENT (five rows for everyone);
  `EditorKeySet.languageMacroRow` remains for the strip/dev JSON, unused by
  the shipped grid. Pinned by `oneKeyPerButtonOnEveryShippedLayer` +
  `symbolsLayerIsSingleCharsWithOneTapBack`.
- **Default: ON since round 2** (owner: "make the keyboard default user can
  off it") — off ⇒ L0 strip + system IME exactly as 22.x–27.x shipped.

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
