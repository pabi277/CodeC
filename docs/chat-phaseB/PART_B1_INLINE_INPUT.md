# CodeC Phase B.1 — Remove OutputInputRow; Add Inline Input in OutputPanelView

**Status:** 📋 **PLANNED** — not yet started · **Cost:** `[client-only]`
· **Depends on:** nothing (standalone UI change to the Output Panel)
· **Primary target files:** `ui/components/OutputPanelView.kt`,
  `ui/viewmodels/EditorViewModel.kt` (output state),
  `ui/screens/EditorScreen.kt` (remove `OutputInputRow` usage)

---

## 1. Evidence — current state

`OutputPanelView.kt` renders output lines in a `LazyColumn`. Below it,
`EditorScreen` places an `OutputInputRow` — a `TextField` + Send button that
calls `viewModel.sendInput(text)` → `InteractiveRunSession.sendLine(text)`.

The separate input row is the problem:
- It feels disconnected from the output (output above, input below — like a chat UI).
- It persists even for non-interactive programs (just disabled/invisible).
- It does not feel like a real terminal or IDE output pane.

---

## 2. Design

### 2.1 Output panel state in `EditorViewModel`

```kotlin
// Existing (unchanged):
data class OutputState(
    val lines: List<OutputLine>,
    val busy: Boolean,
    val phase: RunPhase?,
    ...
)

// New field:
data class OutputState(
    ...
    /** True when the program is running AND interactive (PTY mode). */
    val waitingForInput: Boolean = false,
    /** The text the user is typing for the next stdin line. */
    val inputBuffer: String = "",
)

fun onInputChange(text: String) { _outputState.update { it.copy(inputBuffer = text) } }

fun submitInput() {
    val line = _outputState.value.inputBuffer
    _outputState.update { it.copy(inputBuffer = "") }
    viewModelScope.launch { activeSession?.sendLine(line) }
}
```

### 2.2 `OutputPanelView` — inline input as the last item

```kotlin
@Composable
fun OutputPanelView(state: OutputState, onInputChange: (String) -> Unit, onSubmit: () -> Unit) {
    LazyColumn(...) {
        items(state.lines) { line -> OutputLineRow(line) }

        // Inline input — only when the program is running and interactive
        if (state.waitingForInput) {
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    // A plain BasicTextField styled as terminal text (no border/box):
                    BasicTextField(
                        value = state.inputBuffer,
                        onValueChange = onInputChange,
                        textStyle = LocalTextStyle.current.copy(
                            color = MaterialTheme.colorScheme.onSurface,
                            fontFamily = JetBrainsMono,
                            fontSize = 13.sp,
                        ),
                        singleLine = true,
                        modifier = Modifier
                            .weight(1f)
                            .onPreviewKeyEvent { event ->
                                // Enter key submits the line
                                if (event.key == Key.Enter &&
                                    event.type == KeyEventType.KeyDown) {
                                    onSubmit(); true
                                } else false
                            },
                    )
                    // A subtle ↵ button for touch users (Enter on soft keyboard works too)
                    IconButton(onClick = onSubmit, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.KeyboardReturn, contentDescription = "Send")
                    }
                }
            }
        }
    }
}
```

### 2.3 Remove `OutputInputRow`

- Delete the `OutputInputRow` composable from `OutputPanelView.kt` (or
  `EditorScreen.kt` — check where it currently lives).
- Remove all call sites of `OutputInputRow` from `EditorScreen.kt`.
- Confirm no other screen uses `OutputInputRow`.

### 2.4 Auto-scroll to the inline input

When `waitingForInput` becomes `true`, the `LazyColumn` should scroll to the
last item (the input row) automatically:

```kotlin
val listState = rememberLazyListState()
LaunchedEffect(state.waitingForInput, state.lines.size) {
    if (state.waitingForInput || state.lines.isNotEmpty()) {
        listState.animateScrollToItem(state.lines.size)  // scroll to last = input row
    }
}
```

### 2.5 `waitingForInput` lifecycle

- Set to `true` when `InteractiveRunSession` is started (PTY mode, `interactive = true`).
- Set to `false` when the run finishes (any `RunFinished` / `Failed` event).
- **Not set for piped `ExecutionRunner` runs** — those are non-interactive
  (`interactive = false` in the profile); the input row never appears.

---

## 3. Implementation steps

1. **Add `waitingForInput` and `inputBuffer` to `OutputState`** in `EditorViewModel`.
2. **Add `onInputChange` and `submitInput` functions** to `EditorViewModel`.
3. **Set `waitingForInput = true`** in `EditorViewModel.runActiveFile` when the
   `InteractiveRunSession` path is taken.
4. **Set `waitingForInput = false`** in the run-completion handler.
5. **Add the inline input item** to `OutputPanelView`'s `LazyColumn` per §2.2.
6. **Remove `OutputInputRow`** from `EditorScreen` per §2.3.
7. **Wire `onInputChange` / `onSubmit`** from `EditorScreen` to the panel.
8. **Write host unit tests:**
   - `OutputState.waitingForInput = false` by default.
   - `submitInput()` calls `sendLine` with the correct text and clears `inputBuffer`.
   - `onInputChange` updates `inputBuffer`.

---

## 4. Exit condition & device recipe

```text
1. Create a C file with scanf:
     #include <stdio.h>
     int main() {
         char name[64];
         printf("Enter your name: ");
         scanf("%s", name);
         printf("Hello, %s!\n", name);
         return 0;
     }
2. Tap RUN ▶.
   EXPECT: "Enter your name: " appears in the Output Panel.
   EXPECT: a text cursor is visible at the bottom of the output area (the inline input row).
   EXPECT: NO separate input row below the output panel.
3. Type "Alice" and tap ↵ (or press Enter on the keyboard).
   EXPECT: "Hello, Alice!" appears in the output.
4. Run exits.
   EXPECT: the inline input row disappears; the output panel becomes read-only.
5. Run a non-interactive Python script (print only, no input).
   EXPECT: no input row appears at any point during the run.
PASS = steps 1–5 behave as described.
```

---

## 5. Non-goals & invariants

- **Not in B.1:** the IME keys for interactive runs (→ B.2).
- Non-interactive runs are **completely unaffected** — the `waitingForInput`
  guard ensures the inline field never appears for batch programs.
- `InteractiveRunSession.sendLine()` is **unchanged** — B.1 only changes the
  UI that calls it.
- The existing "Open in Terminal" button in the Output Panel is kept as the
  escape hatch for programs that need a full PTY interaction (cursor-based TUIs).

---

## 6. Design decisions

- **D1 — `BasicTextField` with no border, terminal font:** looks like the cursor
  is part of the output stream; users type directly into the output area.
- **D2 — `↵` icon button for touch users:** a hardware keyboard sends Enter via
  `onPreviewKeyEvent`; touch users tap the icon. Both paths call `submitInput()`.
- **D3 — `inputBuffer` in `OutputState`, not a separate StateFlow:** keeps the
  Output Panel state self-contained; the panel composable observes one flow.
- **D4 — `waitingForInput` on PTY mode only:** piped `ExecutionRunner` programs
  can also read stdin (via `sendInput`), but they are batch programs; forcing
  interactive-input UI on them is misleading. Only PTY (`InteractiveRunSession`)
  runs get the inline field.

---

## 7. Research notes (fill in before implementing)

> **TODO for the implementer:**
> - Find the exact location of `OutputInputRow` (is it in `OutputPanelView.kt`
>   or in `EditorScreen.kt`?) and list all call sites.
> - Confirm `InteractiveRunSession.sendLine()` signature and whether it suspends
>   or is fire-and-forget (affects how `submitInput()` is written).
> - Check whether `LazyColumn` + `animateScrollToItem` behaves correctly when
>   the new item is added at the END (vs. appended mid-list) — verify scroll
>   doesn't bounce on every output line.
