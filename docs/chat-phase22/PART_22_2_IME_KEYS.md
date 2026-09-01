# CodeC Phase 22.2 — IME-Anchored Editor Keys Strip (fix "not above keyboard")

**Status:** 📋 **PLANNED** — not yet started · **Cost:** `[client-only]`
· **Depends on:** Phase 22.3 (`imePadding()` / `WindowInsets.ime` wired in A.3,
  but A.2 can be implemented concurrently — just needs the inset to be readable)
· **Primary target files:** `ui/screens/EditorScreen.kt`,
  `ui/components/EditorKeysRow.kt` (exists; to be reimplemented as IME-pinned),
  `ui/viewmodels/EditorViewModel.kt` (expose `isFocused`, `activeLanguage`)

---

## 1. Evidence — why keys are not above the keyboard

`EditorKeysRow` is currently placed **inside the `Column` layout** of
`EditorScreen`, above the status bar at the bottom. When the soft IME opens:

- On `adjustResize`: the whole Activity is resized to fit above the IME; the
  `EditorKeysRow` stays in its layout position (below the editor text area) and
  may be pushed off-screen or sit far above the keyboard.
- On `adjustPan` (if set): the layout is panned up; same problem.
- In neither case does the row "ride" directly above the keyboard as the user
  expects from Termux's extra-keys row.

Termux's proven approach: the extra-keys row is a **sibling of the terminal
view in a vertical layout**; with `adjustResize`, the IME pushes both up
together, so the keys land directly above the keyboard. In Compose, the
equivalent is using `WindowInsets.ime` to pin the row to the top of the IME.

---

## 2. Design

### 2.1 Layout change in `EditorScreen`

```
┌─────────────────────────────────┐   ← TopAppBar (tabs, ▶ RUN, ⋮)
│                                 │
│   BasicTextField (editor body)  │   ← fills available space
│         Modifier.weight(1f)     │
│                                 │
├─────────────────────────────────┤
│   EditorStatusBar               │   ← Ln/Col, UTF-8, language
├─────────────────────────────────┤
│   EditorKeysRow  ← NEW POSITION │   ← pinned to bottom; rides above IME
├─────────────────────────────────┤
│   [  IME occupies space here  ] │   ← `imePadding()` reserves this
└─────────────────────────────────┘
```

Key change: the root `Scaffold` (or root `Column`) gets
`Modifier.windowInsetsPadding(WindowInsets.ime)` (or equivalently `imePadding()`
on the bottom-most content container). This makes the editor body shrink when
the IME opens, and the `EditorKeysRow` — being the last item before the IME
reservation — lands directly above the keyboard.

### 2.2 Visibility — show only when IME is open

The keys strip shows only when the soft keyboard is up; when it is closed, the
strip collapses (height 0, not `gone` — keeps the layout stable):

```kotlin
val imeVisible by rememberUpdatedState(
    WindowInsets.ime.getBottom(LocalDensity.current) > 0
)

AnimatedVisibility(
    visible = imeVisible,
    enter   = slideInVertically { it },
    exit    = slideOutVertically { it },
) {
    EditorKeysRow(
        language    = viewModel.activeLanguage,
        onKeyAction = viewModel::handleKeyAction,
    )
}
```

### 2.3 Language-adaptive key set

The keys shown depend on the active file's language (from `LanguageRegistry`
or `MultiLanguageSyntaxHighlighter.LanguageType`):

| Language | Keys shown |
|---|---|
| C / C++ | `{` `}` `(` `)` `;` `→` `←` `↑` `↓` Tab Undo Redo |
| Python | `:` `"` `'` `(` `)` `→` `←` Tab `def` `print` Undo Redo |
| JavaScript | `{` `}` `(` `)` `;` `=>` `→` `←` Tab Undo Redo |
| Shell | `$` `"` `'` `|` `>` `&&` Tab Undo Redo |
| Generic | `→` `←` `↑` `↓` Tab Undo Redo |

The key set is declared as a `List<EditorKey>` per language in
`EditorKeysRow.kt`. This replaces the single hard-coded key list.

### 2.4 User-editable keys (stretch — defer if complex)

The owner can long-press a key to edit/replace it (same macro format as
`TerminalExtraKeys`). This is a **stretch goal** — implement only if the
fixed key sets are accepted first. Record as a follow-up if deferred.

### 2.5 Reuse vs. fork `TerminalExtraKeys`

`TerminalExtraKeys.kt` is a PTY-oriented grid (sends bytes to the PTY). The
editor keys need to send `TextFieldValue` transformations (insert text, move
cursor, call `viewModel.undo()`, etc.) — a different call target.

**Decision (D1):** parameterize `EditorKeysRow` to take a `onKeyAction: (EditorKeyAction) -> Unit`
callback instead of a PTY session. Do not fork `TerminalExtraKeys`; instead,
create `EditorKeysRow` as a parallel composable with the same visual structure
but a different action model. Reuse `TerminalKeyView` (the individual key button)
as-is — it is already a generic pressable tile.

---

## 3. Implementation steps

1. **Check `AndroidManifest.xml`** — verify `windowSoftInputMode` on `MainActivity`.
   If it is not `adjustResize`, add it (or `adjustPan`; research which survives
   predictive-back + gesture navigation on Android 12+; record in §7).
2. **Add `Modifier.imePadding()`** to the root layout of `EditorScreen`
   (or the Scaffold's content lambda). Verify it does not break the terminal tab
   (terminal has its own IME handling via `TerminalScreen`).
3. **Move `EditorKeysRow` to the bottom** of the editor layout, above the IME
   reservation. Wrap in `AnimatedVisibility(imeVisible)` per §2.2.
4. **Implement language-adaptive key sets** per §2.3. Add a
   `fun keysForLanguage(language: LanguageType): List<EditorKey>` function to
   `EditorKeysRow.kt`.
5. **Wire `viewModel.activeLanguage`** — already derivable from `activeTabPath`
   via `LanguageRegistry.forFile` (or `MultiLanguageSyntaxHighlighter.languageFor`).
6. **Write host unit tests:**
   - `keysForLanguage("c")` contains the C key set and not the Python key set.
   - `keysForLanguage("py")` contains `:` and `def` snippet.
   - `keysForLanguage("unknown")` returns the generic set (no crash).

---

## 4. Exit condition & device recipe

**CI gate:** `Build APK` green; no regressions.

**Device recipe (owner):**

```text
1. Open any file in the editor.
2. Tap the editor text area to focus it (soft keyboard opens).
   EXPECT: the EditorKeysRow appears DIRECTLY ABOVE the keyboard
   (no gap; no key row below the keyboard; no key row at the bottom
   of the editor body far from the keyboard).
3. For a .c file: verify { } ( ) ; arrow keys are visible.
4. For a .py file: verify : def print are visible.
5. Tap outside the editor (keyboard closes).
   EXPECT: the key row hides (animated slide-down).
6. Rotate the device (landscape).
   EXPECT: key row still rides above the keyboard.
7. Tap Tab key: EXPECT: 4 spaces inserted in the editor.
8. Tap Undo key: EXPECT: last action undone.
9. Tap → arrow key: EXPECT: caret moves right one character.
PASS = all 9 steps behave as described.
```

---

## 5. Non-goals & invariants

- **Not in A.2:** IME inset for the editor body itself (caret visibility) → A.3.
- The Terminal tab's `TerminalExtraKeys` is **unchanged** — A.2 only touches
  the editor. Terminal keys are already PTY-correct.
- The existing `⋮ → show/hide keys row` toggle is kept as a manual override.
- No regression to: find/replace bar (it opens at the top — unaffected by
  bottom IME pinning), autocomplete popup, diagnostics tooltip.

---

## 6. Design decisions

- **D1 — parameterized `EditorKeysRow`, not a fork:** see §2.5. Keeps
  `TerminalKeyView` shared; avoids duplicating the visual grid.
- **D2 — `AnimatedVisibility` on `imeVisible`, not a `height` animation:**
  `AnimatedVisibility` with slide-in/out is smoother and does not interfere
  with the IME's own slide animation.
- **D3 — language keys are static lists, not user macros initially:** user-editable
  keys (§2.4) are a stretch goal deferred to after device acceptance. The static
  list is easier to test and keeps the first round simple.
- **D4 — `windowSoftInputMode = adjustResize`:** required for the IME inset to
  drive layout changes. If the manifest already sets `adjustPan`, this changes
  behavior across the whole app — test for regressions in the Terminal tab,
  Settings, and dialogs.

---

## 7. Research notes (fill in before implementing)

> **TODO for the implementer:**
> - Check `AndroidManifest.xml` `<activity android:windowSoftInputMode="...">` for
>   `MainActivity`. Record the current value.
> - On Android 12+, `adjustResize` is deprecated in favor of
>   `WindowCompat.setDecorFitsSystemWindows(window, false)` + Compose insets.
>   Determine which path is cleaner for the current `targetSdk` (28) + Compose
>   version. Research and record the decision.
> - Confirm `TerminalKeyView.kt` is Android-free enough to reuse in `EditorKeysRow`
>   without pulling in terminal-specific dependencies.
> - Check whether `WindowInsets.ime.getBottom(density) > 0` is the right test
>   for "keyboard is open" in the current Compose version, or whether
>   `WindowInsetsCompat.isVisible(WindowInsetsCompat.Type.ime())` is needed.
