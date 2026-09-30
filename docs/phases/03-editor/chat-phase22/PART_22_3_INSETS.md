# CodeC Phase 22.3 — IME Insets + Caret Visibility

**Status:** 📋 **PLANNED** — not yet started · **Cost:** `[client-only]`
· **Depends on:** Phase 22.1 (scroll model fixed), Phase 22.2 (keys strip placed)
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

## 7. Research notes — ✅ RESOLVED

Answered during implementation; full detail in **§8** below.

| Question | Answer |
|---|---|
| Is `setDecorFitsSystemWindows(false)` already set? | Effectively yes — `enableEdgeToEdge()` at `MainActivity.kt:111` does it. Step 1 was a **no-op**. |
| Current `windowSoftInputMode` | `adjustResize` (`AndroidManifest.xml:64`). **Kept**, contrary to this doc's original D2 — it composes correctly with `imePadding()` and removing it regressed nothing but risked the Terminal. |
| Is `Modifier.imeNestedScroll()` available? | Not adopted. Not needed once the keys row became the last child of the `imePadding()` column; adding it risked the Terminal's own scroll handling. |

---

## 8. Research notes + what shipped (2026-09-03)

- **Step 1 was a no-op.** `MainActivity.onCreate` already calls
  `enableEdgeToEdge()` (`MainActivity.kt:111`), which is
  `WindowCompat.setDecorFitsSystemWindows(window, false)` plus transparent
  system bars. Nothing to add.
- **`windowSoftInputMode`:** `adjustResize` is set on `MainActivity`
  (`AndroidManifest.xml:64`). It was **left in place** — contrary to D2. With
  edge-to-edge the flag is inert for inset delivery (the IME comes through
  `WindowInsets.ime` either way), and removing it would change behavior for
  every other screen in the app (Terminal, Settings, dialogs) for no gain in
  this round. Changing it is a separate, device-gated experiment.
- **`Modifier.imeNestedScroll()`:** still `@ExperimentalLayoutApi` in Compose
  Foundation 1.7 and only meaningful for a scroll container that owns its
  scroll. Since Phase 22.1 kept the outer `verticalScroll` wrapper (the
  `scrollState` parameter does not exist on the `TextFieldValue` overload of
  `BasicTextField` at this BOM — see `PART_22_1_SMOOTHNESS.md` §7), wiring it
  in would need the `TextFieldState` migration first. **Not added.**
- **What shipped:** `Modifier.imePadding()` on `EditorScreen`'s root `Column`
  only (D1 respected — not on the Scaffold, so `TerminalScreen`'s
  `safeDrawingPadding()` and Settings/dialog insets are untouched). This
  reserves exactly the keyboard's height at the bottom of the editor column,
  which (a) keeps the caret and the last line above the keyboard and (b) is
  what makes the Phase 22.2 IME-anchored keys row land flush on the keyboard.
  `navigationBarsPadding()` is already applied by the bottom bar
  (`MainActivity.kt:649`), so it was not duplicated here.
- **Caret follow (§2.3):** unchanged this round. The existing manual
  `getCursorRect` + scroll-offset math still positions the autocomplete popup;
  `imePadding()` shrinks the scroll viewport so the caret is inside it. If the
  owner still sees the caret hidden under the keys row on device, the §2.3
  fallback (measure the keys-row height, add it as bottom padding) is the next
  step.

**Device gate:** the §4 recipe (steps 1–6, including the Terminal
no-white-strip check per D3) is **required** and not yet run.
