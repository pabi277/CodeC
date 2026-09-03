# CodeC Phase 23.2 — Extra-Keys Integration for Interactive Runs

**Status:** 🟡 **IMPLEMENTED** (CI pending) · **Cost:** `[client-only]`
· **Depends on:** Phase 22.2 (IME-anchored keys strip infrastructure),
  Phase 23.1 (inline input and `waitingForInput` state)
· **Primary target files:** `ui/components/EditorKeysRow.kt`,
  `ui/screens/EditorScreen.kt`, `ui/viewmodels/EditorViewModel.kt`

---

## 1. Context & motivation

After B.1 removes the separate input row, users type in the inline input field
at the bottom of the Output Panel. But they still need quick access to:
- **Enter** — submit the typed line to stdin (most common action).
- **Ctrl+C** — send SIGINT to kill the running program.
- **Tab** — some CLI programs use Tab completion.
- **↑ / ↓** — some programs (like Python REPL) support history navigation.

These should appear in the **IME-anchored keys strip** (Phase 22.2) when the
Output Panel's inline input is focused — replacing the editor keys that appear
when the editor is focused.

---

## 2. Design

### 2.1 Context-aware keys strip

The `EditorKeysRow` (Phase 22.2) already receives an `activeLanguage` parameter
to select the key set. Extend it with a `context` parameter:

```kotlin
sealed class KeysContext {
    data class Editor(val language: LanguageType) : KeysContext()
    object InteractiveRun : KeysContext()
    object Idle : KeysContext()   // no keys shown
}
```

`EditorScreen` exposes the context to the keys strip:

```kotlin
val keysContext by remember(outputState.waitingForInput, editorFocused) {
    derivedStateOf {
        when {
            outputState.waitingForInput -> KeysContext.InteractiveRun
            editorFocused               -> KeysContext.Editor(activeLanguage)
            else                        -> KeysContext.Idle
        }
    }
}

AnimatedVisibility(visible = keysContext !is KeysContext.Idle && imeVisible) {
    EditorKeysRow(context = keysContext, onKeyAction = viewModel::handleKeyAction)
}
```

### 2.2 Interactive run key set

```
┌────┬────┬────┬────┬────┬────┐
│ ↵  │Ctrl│ C  │ Tab│ ↑  │ ↓  │
└────┴────┴────┴────┴────┴────┘
```

Actions:
- **`↵ Enter`** → `viewModel.submitInput()` (same as tapping the `↵` icon in B.1).
- **`Ctrl+C`** → `viewModel.sendSignal(SIGINT)` — kills the running program.
- **`Tab`** → `viewModel.onInputChange(current + "\t")` — appends a tab character.
- **`↑` / `↓`** → stub for now (REPL history is a future enhancement; these
  keys do nothing in B.2 but show the slot for future use).

### 2.3 `sendSignal(SIGINT)` in `EditorViewModel`

```kotlin
fun sendSignal(signal: Int) {
    viewModelScope.launch {
        activeSession?.sendSignal(signal)  // InteractiveRunSession.sendSignal(Int)
    }
}
```

`InteractiveRunSession` already has PTY access; sending SIGINT via `kill(pid, SIGINT)`
is a one-liner through the existing `PtyNative` JNI shim (which already calls
`openpty`/`fork`/`waitpid` — add `kill` if not present).

---

## 3. Implementation steps

1. **Add `KeysContext` sealed class** to `EditorKeysRow.kt`.
2. **Add `context: KeysContext` parameter** to `EditorKeysRow`; replace the
   current `language` parameter with it (language lives inside `Editor` context).
3. **Add `fun keysForContext(context: KeysContext): List<EditorKey>`** returning:
   - `InteractiveRun` → the 6-key run set (§2.2).
   - `Editor(lang)` → the language key set from A.2.
   - `Idle` → empty list.
4. **Wire `keysContext`** in `EditorScreen` per §2.1.
5. **Add `sendSignal` to `EditorViewModel`** and wire the `Ctrl+C` action.
6. **Add `kill()` to `PtyNative` JNI** if not already present (check `pty.c`).
7. **Write host unit tests:**
   - `keysForContext(InteractiveRun)` contains Enter and Ctrl+C.
   - `keysForContext(Editor(C))` contains `{` and `}`.
   - `keysForContext(Idle)` is empty.

---

## 4. Exit condition & device recipe

```text
1. Run an interactive C program (scanf).
2. When the program prompts for input and the keyboard opens:
   EXPECT: keys strip shows ↵ Enter, Ctrl+C, Tab.
   (Not the C-language keys like { } ; )
3. Tap ↵ Enter: EXPECT: the typed line is submitted to the program.
4. Run the program again; while waiting for input, tap Ctrl+C:
   EXPECT: the program terminates (exit code shows "Killed" or 130).
5. Close the Output Panel; open the editor.
   EXPECT: the editor key strip (e.g. { } for a .c file) reappears above
   the keyboard when the editor is focused.
PASS = steps 1–5 behave as described.
```

---

## 5. Non-goals & invariants

- **Not in B.2:** REPL history (↑/↓ send nothing yet — future enhancement).
- The Terminal tab's `TerminalExtraKeys` is **unchanged**.
- `PtyNative.kill()` is a one-line addition to the existing JNI shim —
  not a new native module.

---

## 6. Design decisions

- **D1 — `KeysContext` sealed class replaces the `language` parameter:** cleaner
  than a nullable language + a boolean `interactiveRun`; handles the three states
  (editor, interactive run, idle) without ambiguity.
- **D2 — `↑ / ↓` shown but no-op:** occupies the slot visually (user sees the
  row is for interactive use), does nothing until REPL history is implemented.
  Alternative: hide them; decided to show them as placeholders for discoverability.
- **D3 — `sendSignal` through `InteractiveRunSession`, not a raw `kill`:** keeps
  the signal path through the same session object that manages the PTY; avoids
  race conditions with process exit.
- **D4 (as implemented) — run keys are a distinct `RunKey` type, not `EditorKey`s:**
  `EditorKey` is a buffer edit (insert/pair/caret/tab); the run keys are VM
  *actions* (submit a line, send SIGINT, append a tab), so they cannot be
  `EditorKey`s. The context → keys mapping lives in a pure
  `keysForContext(context) : KeysForContext` (`None` | `EditorKeys(defs)` |
  `RunKeys(defs)`) that the screen renders from; the editor key set itself is
  untouched (`EditorKeySet.keysFor`).
- **D5 (as implemented) — one shared keycap renderer:** `EditorKeysRow` now takes
  a precomputed `keys: List<EditorKeyDef>`; a new `RunKeysRow(onKeyAction)` and
  both rows draw through a single private `KeyCap`. Same 40dp/10dp/hairline
  keycaps, so the run strip is visually identical to the editor strip.
- **D6 (as implemented) — no `editorFocused` tracking:** the screen derives
  `keysContext` from `outputState.waitingForInput` alone (`InteractiveRun` when
  true, else `Editor(language)`), so there is no focus plumbing to get wrong.
  `KeysContext.Idle` exists for the sealed class's three states; the strip's
  on/off visibility is still the existing `keysRowVisible` chevron, and the
  screen renders `None` for Idle.
- **D7 (as implemented) — no JNI change:** `pty.c` already exposes
  `nativeKill(pid, signal)` (signalling the child's **process group** first,
  then the pid) and `PtyNative.kill(pid, signal)` wraps it — the spec's "add
  `kill()` if not present" TODO was a non-issue. `InteractiveRunSession`
  gained `sendSignal(signal)` delegating to `PtyNative.kill(session.pid, signal)`
  (the wrapped `PtySession` already exposes its public `pid`).

---

## 7. Research notes (filled in at implementation)

- **`kill()` already exists:** `app/src/main/cpp/pty.c` →
  `Java_com_codeci_ide_ui_terminal_PtyNative_nativeKill` calls `kill(-pid, sig)`
  then falls back to `kill(pid, sig)`; `PtyNative.kill(pid, signal = SIGHUP)` is
  the Kotlin wrapper (with `SIGINT = 2`). **No native change was needed.**
- **`pid` is reachable:** `InteractiveRunSession` holds `private val session:
  PtySession`, and `PtySession.pid` is public — `sendSignal` delegates through
  the session, so no new `pid` property was required.
- **`editorFocused`:** not tracked (see D6) — `waitingForInput` is the only
  signal needed to switch strips; the editor keys already show whenever the
  strip is visible and no interactive run is active.

---

## 8. What shipped

- Pure `ui/editor/RunKeySet.kt` (`RunKey` enum + `RunKeyDef` + 5-cap `RunKeySet`).
- Pure `ui/editor/KeysContext.kt` (`KeysContext` + `KeysForContext` +
  `keysForContext`).
- `ui/components/EditorKeysRow.kt`: `EditorKeysRow(keys, …)` + new `RunKeysRow`
  + shared `KeyCap`.
- `EditorViewModel`: `sendSignal(signal)`, `interruptRun()` (SIGINT),
  `appendInput("\t")`; `InteractiveRunSession.sendSignal(signal)`.
- `EditorScreen`: `keysContext`/`resolvedKeys`/`handleRunKey`; both strip
  positions (docked + IME-anchored) now render through a `KeysStrip` that shows
  the run keys while an interactive program waits for input and the editor keys
  otherwise.
- Host tests: `RunKeySetTest` (×8 — run-set content/order/labels + the three-way
  `keysForContext` mapping). Editor keys stay covered by the existing
  `EditorKeySetTest` (asserts `{}` pair caps).
- CI: `Build APK` — **run id recorded after green** (assemble + unit tests + lint).

> **TODO for the implementer:**
> - Check `pty.c` (the JNI shim) for a `kill()` wrapper. If it doesn't exist,
>   add `Java_com_codeci_ide_ui_terminal_PtyNative_kill(JNIEnv*, jclass, jint pid, jint sig)`
>   calling `kill(pid, sig)`.
> - Confirm `InteractiveRunSession` exposes the child `pid` to the caller
>   (needed for `kill`). If not, add a `val pid: Int` property.
> - Verify `editorFocused` state is trackable from `EditorScreen` (probably via
>   `BasicTextField`'s `onFocusChanged` modifier or a `FocusRequester`).
