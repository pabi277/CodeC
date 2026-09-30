# Part 77.1 — the floating AI button

Phase: [77](README.md) · 📋 PLANNED (docs only)

## First move: evidence, not code (do this before writing any code)

- Re-read `ui/screens/EditorScreen.kt` for the editor area's `Box` that hosts the Sora editor, the tab strip, the coding row (`EditorKeysRow`/`BottomStrip`) and the Output panel — the bubble's bounds are **the code area only**. Record file:line in this doc.
- `imeVisible` already exists in `EditorScreen` (`WindowInsets.ime.getBottom(...) > 0`, Phase 22.2) — reuse it.
- `AiKeyStore` (`ui/ai/AiKeyStore.kt`) already owns `no_backup/ai/ai_settings.properties` with `Properties` read/write helpers — the persistence home.
- Check `TokenAdoptionTest` / `AppContrastTest` / `TypeAdoptionTest` scopes before styling (Phase 76 CI lesson).

### Evidence found (2026-10-01, before coding)

- Bounds: the bubble lives in the code-area `Box` of `EditorScreen.kt` (the Box that hosts `SoraEditorHost(`), *above* the `BottomStrip(` / status bar, so the tab strip, coding row, keyboard and bottom bar are outside it by construction. `imeVisible` already exists there (Phase 22.2) and the IME top is fed to `AiBubblePolicy` as a lift.
- Persistence: `AiKeyStore.kt` already owns `no_backup/ai/ai_settings.properties` (`settings()` / `Properties`). Added `bubble()` / `setBubble()` (prop `bubble_pos`, `"RIGHT:0.620"`) and `showBubble()` / `setShowBubble()` (prop `bubble_show`, absent = shown). `deleteKey()` (`AiKeyStore.kt:129`) does not touch either.
- Adoption scopes checked first: `TokenAdoptionTest` covers `EditorScreen` (CodecTokens only, no `Color(0x`), `TypeAdoptionTest` forbids `FontFamily.Monospace`. The bubble uses `secondaryContainer` / `onSecondaryContainer` and no raw numbers beyond `AiBubblePolicy` constants.

### Built

- `ui/ai/AiBubblePolicy.kt` (pure) — constants `VISUAL_DP 40`, `TOUCH_DP 48`, `EDGE_MARGIN_DP 8`, `MIN_Y_FRACTION 0.05`, `MAX_Y_FRACTION 0.95`; default RIGHT / 0.62; snap to nearest edge (exact middle → RIGHT), y clamp, keyboard lift above `imeTopPx`, tiny-area bounds, encode/decode (garbage → default). `AiBubblePolicyTest` — 14 cases.
- `ui/ai/AiFloatingButton.kt` — draggable circle `Surface`, content description "AI chat", tap → sheet, long-press → DropdownMenu *Hide AI button* / *Move to other side*; drop persists via `AiKeyStore`. Hide also shows a snackbar (`AiCopy.BUBBLE_HIDDEN_NOTE`) pointing at ✨ → Show AI button.
- Shown only when `aiReady` (`AiGate` READY) && `showBubble` && sheet HIDDEN. No pulse, tooltip or coach mark.

## Design — pure `ui/ai/AiBubblePolicy.kt`

```kotlin
enum class AiBubbleEdge { LEFT, RIGHT }

/** Size-independent: survives rotation, screen-size and keyboard changes. */
data class AiBubblePosition(val edge: AiBubbleEdge = AiBubbleEdge.RIGHT, val yFraction: Float = 0.62f)

object AiBubblePolicy {
    const val VISUAL_DP = 40f          // drawn size
    const val TOUCH_DP = 48f           // CodecTokens.MIN_TOUCH
    const val EDGE_MARGIN_DP = 8f      // CodecTokens.Space.S from the edge
    const val MIN_Y_FRACTION = 0.05f
    const val MAX_Y_FRACTION = 0.95f

    /** Release after a drag: nearest edge wins; y clamped into the code area. */
    fun snap(xPx: Float, yPx: Float, areaW: Float, areaH: Float): AiBubblePosition

    /** Where to draw it now; lifts above the keyboard (never under it). */
    fun offsetPx(p: AiBubblePosition, areaW: Float, areaH: Float, imeTopPx: Float?, bubblePx: Float, marginPx: Float): Pair<Float, Float>

    fun encode(p: AiBubblePosition): String          // "RIGHT:0.620"
    fun decode(s: String?): AiBubblePosition         // garbage/null → default
}
```

Visibility rule (pure, in `AiGate` or the policy): bubble shows **only** when `availability == READY` **and** the *Show AI button* setting is on (default **on** once a key is saved) **and** the chat sheet is not open.

## The Android edge

- `AiFloatingButton(position, visible, hasSelection, onTap, onLongPress, onMoved)` drawn in the editor's code-area `Box` (above the editor, below dialogs/drawer). `pointerInput { detectDragGestures }` moves it live; on drag end → `AiBubblePolicy.snap` → `AiViewModel.saveBubblePosition` (IO).
- Look: `Surface` circle, `MaterialTheme.colorScheme` roles (e.g. `secondaryContainer` / `onSecondaryContainer`), `Icons.Filled.AutoFixHigh` (same glyph as the rail slot), small elevation token, content description "AI chat". Optional small dot when there is a selection (no animation, no pulse).
- Long-press → small `DropdownMenu`: *Hide AI button* (→ setting off; re-enable in AI home) · *Move to other side*.
- Persistence: `AiKeyStore` gains `bubble()` / `setBubble(p)` and `showBubble()` / `setShowBubble(b)` in **the same `ai_settings.properties`** (props `bubble_pos`, `bubble_show`). `deleteKey()` keeps them (like the model).

## Exit condition

Drag anywhere → snaps to an edge, never overlaps tab strip / coding row / keyboard / bottom bar; position survives restart and rotation; hidden when not READY or when switched off.

## Tests (plan)

`AiBubblePolicyTest` ≈12: nearest-edge snap (left half/right half/exact middle → RIGHT); y clamp at both ends; fraction survives a different `areaH`; keyboard lift puts the bubble fully above `imeTopPx`; tiny area (landscape + keyboard) still returns an in-bounds offset; encode/decode round-trip; `decode(null|"" |"UP:2"|"LEFT:NaN")` → default.

## Deferred / rejected with reasons

- **New DataStore key** — rejected (standing law): position + visibility are AI-surface settings and live beside the model in `no_backup/ai/ai_settings.properties`; not backed up, which is fine for a UI position.
- **Free placement without edge snap** — rejected: a bubble parked mid-screen covers code; the owner's "moved anywhere" is honoured vertically and on either side.
- **Auto-hide while typing** — not in 77; keyboard lift is enough. Revisit only if the device round says it is in the way.
