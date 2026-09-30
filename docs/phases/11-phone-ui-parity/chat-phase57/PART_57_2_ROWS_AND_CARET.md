# PART 57.2 — the two rows above the system keyboard, and the caret's drop

**Files touched**

| File | What changed |
|---|---|
| `app/src/main/java/com/codeci/ide/ui/editor/StripContext.kt` | `stripContextFor(…, typingSurfaceUp: Boolean = true)`; the chip branch is gated on it (:142) |
| `app/src/main/java/com/codeci/ide/ui/screens/EditorScreen.kt` | the call site passes `typingSurfaceUp = imeVisible || codecKeysUp` (:660) |
| `app/src/main/java/com/codeci/ide/ui/editor/sora/SoraEditorHost.kt` | the caret's handle is styled: `setSelectionHandleStyle(HandleStyleDrop(hostContext))` (:121) and the handle colour rides every theme switch (:277) |
| `app/src/main/java/com/codeci/ide/ui/theme/CodecPalette.kt` | `CARET_HANDLE` (:110) — the reference's blue, a shape colour, no AA row |
| `app/src/test/java/com/codeci/ide/StripContextTest.kt` | the new law as a case + the helper's new parameter |
| `app/src/test/java/com/codeci/ide/EditorRowsWiringTest.kt` | **new** — the four wiring pins below |

## 1. What was already true (verified, then pinned — not changed)

| Roadmap sentence | Evidence on this checkout | Action |
|---|---|---|
| “Hide that line while the IME is up.” | `EditorScreen.kt:2254` `if (caretPlaced && !imeVisible && !codecKeysUp) {` and the surface's own `statusVisible = caretPlaced && !imeVisible && !codecKeysUp` (:2144) | pinned |
| “The touch row from the shot, docked even when the keyboard is closed.” | :2229 `if (keysVisible && !imeVisible) {` docks it at the bottom of the column; :2371 `if (keysVisible && imeVisible) {` re-anchors the same strip on the IME | pinned |
| “System keyboard stays.” | `codecKeysUp` only ever *suppresses* the soft keyboard when CodeC Keys is the keyboard (`soraEditor.setSoftKeyboardEnabled(!codecKeysUp)`) | untouched |

The reason these are pins and not edits: they are the two halves of the exit criterion, and
a phase that “matches a screenshot” by luck is a phase that breaks the next time someone
tidies the column.

## 2. The chip row waits for a keyboard

```kotlin
// StripContext.kt
fun stripContextFor(…, acceptCounts: Map<String, Int> = emptyMap(), typingSurfaceUp: Boolean = true) {
    …
    if (typingSurfaceUp && (items.size >= 2 || emmetOnly)) { … StripContext.Suggestions(chips) … }
```
```kotlin
// EditorScreen.kt — the one call site
typingSurfaceUp = imeVisible || codecKeysUp
```

* 122157 (keyboard down) has **no** predictive row; 124105 has one. The chips are typing
  chrome; the **key caps stay either way** — they are the touch row.
* CodeC Keys counts as a typing surface: when the app replaces the system keyboard with its
  own (`codecKeysUp`), the chip strip is exactly the Phase 27 completion surface.
* The default is `true`, deliberately: every S-matrix rule in `StripContextTest` is a typing
  rule, and the parameter exists so the *screen* has to state when that stops being true.
* Still true, unchanged: an interactive run always wins the row (`StripContext.Run`), and the
  chevron still hides the whole row.

## 3. The blue drop: sora already draws a handle — so it is styled, not rebuilt

Research, in the library's own source (sora-editor **0.24.6**, the pinned version):

```
editor/src/main/java/io/github/rosemoe/sora/widget/EditorRenderer.java:2453
    if (eventHandler.shouldDrawInsertHandle() && !editor.isInMouseMode()) {
        editor.getHandleStyle().draw(canvas, SelectionHandleStyle.HANDLE_TYPE_INSERT, centerX, tmpRect.bottom,
            editor.getRowHeight(), editor.getColorScheme().getColor(EditorColorScheme.SELECTION_HANDLE),
            editor.getInsertHandleDescriptor());
editor/src/main/java/io/github/rosemoe/sora/widget/CodeEditor.java:598
    handleStyle = new HandleStyleSideDrop(getContext());     // the DEFAULT
editor/src/main/java/io/github/rosemoe/sora/widget/style/builtin/HandleStyleDrop.java
    public class HandleStyleDrop implements SelectionHandleStyle { … ic_sora_handle_drop … }   // the reference's drop
```

So the roadmap's conditional is satisfied: **sora draws it** (`shouldDrawInsertHandle()` is
true after a selection change and while the handle is held), and the app only has to choose
the style and the colour:

```kotlin
// SoraEditorHost.kt — the one-time editor config
setSelectionHandleStyle(HandleStyleDrop(hostContext))
```
```kotlin
// SoraEditorHost.kt — with the theme, because a fresh scheme resets custom colours
editor.colorScheme.setColor(EditorColorScheme.SELECTION_HANDLE, CodecPalette.CARET_HANDLE)
```

* **No overlay, no second caret.** The drop is painted by the editor's own renderer, and
  dragging it is sora's own touch path (`holdInsertHandle`) — the reason 57.2's “prefer
  omit-and-ask” trap does not apply here.
* The colour is a **palette role**, not a literal at the call site: `CodecPalette.CARET_HANDLE`
  = the reference's blue `#3B82F6`. It is a filled shape that nothing is ever written on, so
  it carries no contrast row — said out loud in the palette, because that table is what
  `AppContrastTest` measures.
* If a maintainer later wonders why the colour is re-applied on every theme switch: because
  `setColorScheme` replaces the whole scheme, and a stale handle colour is the kind of bug
  that only shows on a phone in bright sun.

## 4. The wiring pins (`EditorRowsWiringTest`)

1. the docked touch row (`keysVisible && !imeVisible`) and the keyboard-anchored one
   (`keysVisible && imeVisible`) both exist;
2. the status line's own gate and the editor surface's `statusVisible` agree;
3. the screen passes `typingSurfaceUp = imeVisible || codecKeysUp`;
4. the host styles sora's handle, the colour rides the theme switch, the colour is a palette
   role, and **no** `CursorHandle` string appears anywhere (no second caret).

## 5. Deliberately not done

* **No new key caps.** The touch row's glyphs (`>>`, the `⧉` cap, `⌘`) are **not** in the
  shots' legend, and the row's key set is configuration (`KeyStripStorage`), not code. The
  owner's standing answer for unseen glyphs is *“Do whatever is good”* — the good thing here
  is not to invent keyboard behaviour that no shot shows.
* **The predictive row's right-edge glyph** (124105) does nothing new: our strip already ends
  with the “⌄ more” cap that opens sora's browse panel (Phase 27.3), and that is an existing
  action, not a guess.
