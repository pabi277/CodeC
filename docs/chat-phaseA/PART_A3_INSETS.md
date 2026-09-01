# CodeC Phase A.3 — IME Insets + Caret Visibility

**Status:** 📋 **PLANNED** — not yet started · **Cost:** `[client-only]`
· **Depends on:** Phase A.1 (scroll model fixed), Phase A.2 (keys strip placed)
· **Primary target files:** `ui/screens/EditorScreen.kt`, `MainActivity.kt`

---

## 1. Evidence — why the caret hides behind the keyboard

Without `imePadding()` on the editor's scroll container, when the soft IME
opens the caret may:
1. Be positioned below the visible area (the keyboard covers it).
2. Cause a relayout jump (the whole screen shifts when the IME animates in).
3. Make the last line of a file invisible while typing at the end.

The fix is a combination of:
- `WindowCompat.setDecorFitsSystemWindows(window, false)` in `MainActivity.onCreate`
  (opt into manual inset handling — the Compose way for SDK 28+).
- `Modifier.imePadding()` on the editor's scroll container (reserves space at
  the bottom equal to the IME height).
- `Modifier.imeNestedScroll()` (if available at the resolved Compose version) to
  synchronize the IME animation with the scroll fling.

---

## 2. Design

### 2.1 `MainActivity.onCreate`

```kotlin
override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    // Opt into edge-to-edge + manual inset handling.
    WindowCompat.setDecorFitsSystemWindows(window, false)
    // ...existing setContent { ... }
}
```

### 2.2 `EditorScreen` root layout

```kotlin
// Root column of EditorScreen:
Column(
    modifier = Modifier
        .fillMaxSize()
        .imePadding()          // ← reserves IME space at the bottom
        .navigationBarsPadding() // ← already present or add
) {
    // TopAppBar (tabs, RUN, overflow)
    EditorTopBar(...)
    // Editor body
    BasicTextField(modifier = Modifier.weight(1f), ...)
    // Status bar
    EditorStatusBar(...)
    // IME-anchored keys (from A.2)
    AnimatedVisibility(imeVisible) { EditorKeysRow(...) }
    // imePadding() reserves the remaining space below
}
```

### 2.3 Caret follow

After fixing the scroll model (A.1), `BasicTextField`'s own scroll parameter
handles `bringIntoView` automatically when the caret moves. Verify this works
correctly after the `imePadding` change — the caret should always be visible
above the keys row when the keyboard is open.

If `bringIntoView` is not sufficient (the caret still hides under the keys row),
add a `LaunchedEffect(cursorPosition)` that measures the keys-row height and
applies an additional bottom padding to the `scrollState`.

### 2.4 Predictive-back compatibility

On Android 14+ with predictive-back gesture, the back gesture animates the
Activity behind the previous screen. Verify `imePadding()` does not cause a
visible layout jump during the predictive-back swipe from the editor. If it does,
use `Modifier.windowInsetsPadding(WindowInsets.ime)` with
`consumeWindowInsets = false` (keeps the inset for child layouts without
consuming it at this level).

---

## 3. Implementation steps

1. Add `WindowCompat.setDecorFitsSystemWindows(window, false)` to
   `MainActivity.onCreate` (if not already present).
2. Add `Modifier.imePadding()` to the root layout of `EditorScreen`.
3. Add `Modifier.navigationBarsPadding()` if not already present.
4. Remove any `android:windowSoftInputMode` hardcoded value that conflicts
   (if `adjustNothing` or `adjustPan` is set, change to `adjustResize` or
   remove it and let the Compose inset API handle everything).
5. Test for regressions in `TerminalScreen` — it may already handle insets
   differently; verify the terminal PTY still fills the screen correctly.
6. Verify the Settings, dialogs, and bottom sheets are not affected
   (they use `ModalBottomSheet` which handles its own insets).

---

## 4. Exit condition & device recipe

**CI gate:** `Build APK` green; no regressions.

**Device recipe (owner):**

```text
1. Open a long C file; scroll to the last line.
2. Tap the last line to place the caret there and open the keyboard.
   EXPECT: the caret (last line) is visible above the keyboard and above
   the EditorKeysRow. No layout jump.
3. Type several characters at the end of the file.
   EXPECT: caret stays visible; the editor scrolls automatically.
4. Rotate to landscape; type.
   EXPECT: same behavior in landscape — caret visible, no jump.
5. Open Settings (a different screen) and back.
   EXPECT: no visual regression on the Settings screen from the inset change.
6. Open the Terminal tab.
   EXPECT: terminal still fills the screen correctly; no white strip at the bottom.
PASS = all 6 steps behave as described.
```

---

## 5. Non-goals & invariants

- **Not in A.3:** the keys strip itself (→ A.2); the scroll model (→ A.1).
- `TerminalScreen` insets are **unchanged** — A.3 only touches `EditorScreen`
  and `MainActivity`.
- The `safeDrawingPadding()` already applied to the terminal (Phase 6) is kept.

---

## 6. Design decisions

- **D1 — `imePadding()` on the editor root, not the Scaffold:** applying it at
  the Scaffold level would affect every tab (Terminal, Settings, etc.), which
  have their own inset handling. Apply only to `EditorScreen`'s root.
- **D2 — remove `windowSoftInputMode` if possible:** the Compose inset API
  (`WindowCompat.setDecorFitsSystemWindows + imePadding`) is the modern approach.
  `adjustResize` is deprecated on Android 12+. Removing the manifest value and
  letting Compose handle everything gives the best future compatibility.
- **D3 — verify Terminal before committing:** the Terminal tab's inset handling
  (Phase 6 `safeDrawingPadding`) must survive this change. Run the Terminal
  recipe from Phase 6/19 on device before reporting A.3 done.

---

## 7. Research notes (fill in before implementing)

> **TODO for the implementer:**
> - Check if `WindowCompat.setDecorFitsSystemWindows(window, false)` is already
>   set in `MainActivity.onCreate` (Phase 6 or Phase 19 may have added it).
>   If so, step 1 is a no-op.
> - Check the current `windowSoftInputMode` value in `AndroidManifest.xml`.
> - Confirm `Modifier.imeNestedScroll()` exists in the resolved Compose version
>   (it is in `accompanist` and may be in `foundation` by now). If available,
>   add it to the editor scroll container for smoother IME animation sync.
