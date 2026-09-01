# CodeC Phase 24.3 — Hardware Keyboard Shortcuts

**Status:** 📋 **PLANNED** · **Cost:** `[client-only]` · **Effort:** S
· **Depends on:** Phase 22.1 (editor is the keyboard target; scroll model fixed)
· **Target files:** `ui/screens/EditorScreen.kt`,
  `ui/editor/EditorKeySet.kt` (extend), `ui/viewmodels/EditorViewModel.kt`

---

## 1. Design

Many Bluetooth keyboard and tablet users expect standard shortcuts. The editor
already handles some via `EditorKeySet.kt`. This part expands the set and
ensures all shortcuts are handled correctly in Compose's `onKeyEvent` modifier.

### Shortcut table

| Shortcut | Action | VM method |
|---|---|---|
| Ctrl+S | Save file | `saveFile()` (already works) |
| Ctrl+Z | Undo | `undo()` (already works) |
| Ctrl+Shift+Z | Redo | `redo()` (already works) |
| Ctrl+F | Open Find/Replace | `openFindReplace()` |
| **Ctrl+R** | **Run active file (▶)** | `runActiveFile(context)` |
| Ctrl+/ | Toggle line comment | `toggleLineComment()` |
| Ctrl+D | Duplicate current line | `duplicateLine()` |
| Ctrl+W | Close active tab | `closeActiveTab()` |
| Ctrl+Tab | Next tab | `nextTab()` |
| Ctrl+Shift+Tab | Previous tab | `prevTab()` |
| **F5** | Run (alias for Ctrl+R) | `runActiveFile(context)` |
| Ctrl+A | Select all | native (Compose handles) |
| Ctrl+C / X / V | Copy/Cut/Paste | native (Compose handles) |

Bold items are new; the rest either already work or are native Compose.

### Implementation

```kotlin
// In EditorScreen's BasicTextField or root Scaffold:
Modifier.onKeyEvent { event ->
    if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
    val ctrl  = event.isCtrlPressed
    val shift = event.isShiftPressed
    when {
        ctrl && event.key == Key.R            -> { viewModel.runActiveFile(context); true }
        ctrl && event.key == Key.F            -> { viewModel.openFindReplace(); true }
        ctrl && event.key == Key.Slash        -> { viewModel.toggleLineComment(); true }
        ctrl && event.key == Key.D            -> { viewModel.duplicateLine(); true }
        ctrl && event.key == Key.W            -> { viewModel.closeActiveTab(); true }
        ctrl && !shift && event.key == Key.Tab -> { viewModel.nextTab(); true }
        ctrl && shift  && event.key == Key.Tab -> { viewModel.prevTab(); true }
        event.key == Key.F5                   -> { viewModel.runActiveFile(context); true }
        else -> false
    }
}
```

`toggleLineComment` and `duplicateLine` are new `EditorViewModel` methods that
operate on the current `TextFieldValue` — pure text operations, host-testable.

---

## 2. Implementation steps

1. Add `toggleLineComment()` and `duplicateLine()` to `EditorViewModel`.
2. Add the `onKeyEvent` block to `EditorScreen` per §1.
3. Write host unit tests for `toggleLineComment` (C `//`, Python `#`, Go `//`)
   and `duplicateLine`.
4. CI green.

---

## 3. Exit condition

```text
(With a Bluetooth keyboard connected or on a tablet)
1. In the editor: Ctrl+R → EXPECT: run starts (same as tapping ▶).
2. Ctrl+/ on a C line → EXPECT: // prepended; again → removed.
3. Ctrl+D → EXPECT: current line duplicated below.
4. Ctrl+W → EXPECT: current tab closes.
5. Ctrl+Tab → EXPECT: next tab focused.
6. F5 → EXPECT: run starts.
PASS = all 6 steps behave as described.
```
