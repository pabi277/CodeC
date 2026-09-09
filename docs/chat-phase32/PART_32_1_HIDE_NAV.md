# CodeC Phase 32.1 — Hide bottom navigation while typing

**Status:** ✅ IMPLEMENTED & DEVICE-PASSED (owner: "All test passed on device", 2026-09-09) · **Cost:** `[client-only]` · **Effort:** S
· **Target:** `MainActivity` Scaffold `bottomBar`, `EditorScreen`

---

## 1. Design

While `Editor` is the destination **and** (IME visible **or** CodeC Keys
visible), hide the 5-item tab bar. A thin handle — tap OR swipe-up — restores
it (sticky until the user leaves the editor). Leaving the editor restores it.

Do not hide on Projects / Terminal / Packages / Settings.

## 2. Exit condition

```text
(Device)
1. Open editor, open Keys or IME — tab bar gone; more lines of code visible.
2. Navigate away — bar back.
3. Hardware back / handle restores bar without losing buffer.
PASS = all three.
```

## 3. Implementation

- **`ui/editor/NavBarPolicy.kt` (new, pure)** — `hideNavBar(inEditor,
  imeVisible, keysVisible, revealed)`: `revealed` always wins; else
  `imeVisible || (inEditor && keysVisible)`. `revealOnSwipe(dp)` with
  `REVEAL_SWIPE_DP = 32f`. Host-testable, no Android imports.
- **`ui/editor/EditorChromeState.kt` (new)** — a `StateFlow<Boolean>` bridge
  (`keysVisible`). The editor owns its keyboard state; MainApp reads it.
- **`EditorScreen`** — `LaunchedEffect(codecKeysUp)` writes
  `EditorChromeState.setKeysVisible(codecKeysUp)`; a `DisposableEffect(Unit)`
  clears it on dispose so no other surface inherits a stale signal. (The
  existing `codecKeysUp = codecKeysOn && !outputState.waitingForInput` gate is
  reused — no new state.)
- **`MainActivity.MainApp`** — collects `inEditor` (route starts with
  `Screen.Editor.route.substringBefore("?")`), `editorKeysVisible`, the
  existing `isImeVisible`, and a `navRevealed` sticky flag (reset by a
  `LaunchedEffect(inEditor)` when leaving the editor). The Scaffold
  `bottomBar` is now a `when`:
  - `!hideNav` → the existing `FlatBottomBar` (unchanged);
  - `inEditor && !isImeVisible` → `EditorNavRevealHandle` (thin pill +
    "Show tabs"; tap `clickable` OR swipe-up `detectVerticalDragGestures`
    past 32 dp reveals — px→dp via `LocalDensity`, same gesture pattern as
    the 26/27 ghost pill);
  - else → nothing (the pre-32 rule: soft IME up ⇒ no bar).
  Revealing the bar only toggles the Scaffold `bottomBar`; the editor buffer
  (EditorViewModel) is untouched, so a reveal cannot lose text.

## 4. Tests & validation

- `NavBarPolicyTest` (6 host cases): bar shows unfocused; IME hides on every
  tab; Keys hide only in-editor; editor-no-keyboard keeps the bar; a reveal
  beats every hide condition; the 32 dp swipe threshold.
- Local pre-validation (Temurin 25 + kotlinc 2.4.10): the pure file passes a
  main-harness; Compose compiles only on CI (no Android SDK in-sandbox).
- **Device gate:** `TROUBLESHOOTING.md` §16.
