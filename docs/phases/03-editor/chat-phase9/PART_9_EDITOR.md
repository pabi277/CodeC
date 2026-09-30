# CodeC Phase 9 — Editor Foundation (Undo/Redo, Find/Replace, Format, Squiggles)

**Status:** ✅ Implemented 2026-08-29 on `arena/01a04c1c-codec`; §4 device recipe
was run by the owner that day ("Yes working" + three problems) — follow-ups shipped
as **Phase 9.1** and **Phase 9.2** (see
[`PART_9_IMPLEMENTATION.md`](PART_9_IMPLEMENTATION.md)); final acceptance = owner
closed by the owner's finalization + PR #28 instruction 2026-08-29 (the recipe
stays as the regression checklist) ·
**Cost:** `[client-only]` · **Depends on:** Phase 8 (fully accepted 2026-08-29)
**Target Files:** `EditorScreen.kt`, `CSyntaxVisualTransformation.kt`, `EditorViewModel.kt`

---

## 1. Context & Motivation

In current versions of CodeC, `EditorScreen.kt` contains placeholder toolbar actions (Undo, Redo, Format, Find) that display `showComingSoon()`. The editor lacks core modern IDE conveniences:
- No undo/redo history for typing or editing mistakes.
- No in-editor search and replace with regex or match highlighting.
- No code formatting.
- No matching bracket/parenthesis highlighting.
- Compiler errors are only visible in the terminal rather than highlighting the exact line/column with red squiggles in the editor.
- No multi-file tabs for switching between open headers and source files.

Phase 9 implements all these editor foundation capabilities natively in Jetpack Compose.

---

## 2. Architectural Design (Decision D1)

### 2.1 Undo / Redo Command History
```kotlin
class EditorUndoManager(private val maxHistory: Int = 100) {
    private val undoStack = ArrayDeque<TextFieldValue>()
    private val redoStack = ArrayDeque<TextFieldValue>()

    fun pushState(state: TextFieldValue) { ... }
    fun undo(current: TextFieldValue): TextFieldValue? { ... }
    fun redo(current: TextFieldValue): TextFieldValue? { ... }
    val canUndo: Boolean get() = undoStack.isNotEmpty()
    val canRedo: Boolean get() = redoStack.isNotEmpty()
}
```

### 2.2 Find & Replace Bar
- Top overlay bar in `EditorScreen`:
  - Search input field + Replace input field.
  - Buttons: Previous Match (`↑`), Next Match (`↓`), Replace (`1`), Replace All (`All`), Close (`✕`).
  - Options: Match Case (`Aa`), Whole Word (`\b`), Regular Expression (`.*`).
  - Active match count badge (e.g., `3 / 14`).
  - Matching occurrences highlighted with yellow/orange spans in `VisualTransformation`.

### 2.3 Code Formatter
- Format button executes formatting:
  1. Checks if `clang-format` exists in `$PREFIX/bin/clang-format`.
  2. If present, runs `clang-format -style=Google` or `LLVM` over the code buffer.
  3. If not present, applies built-in C/C++ AST indent formatter (preserves cursor relative offset).
  4. Pushes formatted state to `EditorUndoManager` so format can be undone in 1 tap.

### 2.4 Bracket Pair Highlight
- Scans character at current cursor position (`(`, `)`, `{`, `}`, `[`, `]`).
- Finds the corresponding opening/closing match accounting for nesting depth and string/comment boundaries.
- Highlights both matching brackets with a subtle rounded border / accent background.

### 2.5 Error Diagnostics & Inline Squiggles
- Parses compiler stderr output format: `filename:line:col: error: message`.
- Annotates lines in editor with red wavy squiggly underlines.
- Tapping the error annotation displays a floating tooltip with the compiler message and auto-suggested fix.

### 2.6 Status Bar & Line/Col Tracking
- Bottom editor status bar:
  - `Ln 42, Col 15`
  - `UTF-8`
  - `Spaces: 4`
  - Selected character count (when text selection is active).
  - Current line highlighted with subtle background tint (`MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)`).

### 2.7 Multi-File Tab Bar
- Horizontal scrollable tab bar above editor showing all open files in active project (e.g. `main.c`, `calc.h`, `calc.c`).
- Close tab button (`✕`), dirty dot indicator for unsaved changes, and tap to switch active buffer.

---

## 3. Implementation Steps

1. **Step 1:** Implement `EditorUndoManager.kt` with debounced text change snapshots.
2. **Step 2:** Build `FindReplaceBar.kt` with match highlighting and regex search.
3. **Step 3:** Implement `CodeFormatter.kt` with `clang-format` bridge + built-in fallback.
4. **Step 4:** Extend `CSyntaxVisualTransformation.kt` to support bracket matching and diagnostic squiggles.
5. **Step 5:** Integrate Multi-File Tab Bar in `EditorScreen.kt`.
6. **Step 6:** Write unit tests in `EditorUndoManagerTest.kt` and `FindReplaceTest.kt`.

---

## 4. Exit Condition & Verification Recipe

A fresh APK passes the following recipe on device:

```sh
# Setup & Editor Foundation Test
# 1. Open Editor, type: int main() { printf("Hello\n"); return 0; }
# 2. Delete a line -> Tap UNDO button -> Verify line restored -> Tap REDO -> Verify line deleted.
# 3. Tap FIND icon -> Search "printf" -> Verify match is highlighted in yellow -> Replace with "puts" -> Verify replaced.
# 4. Introduce unindented code -> Tap FORMAT button -> Verify code indented cleanly.
# 5. Place cursor on '{' -> Verify matching '}' highlights in accent color.
# 6. Type intentional syntax error "int x =" -> Observe red squiggly underline on line.
# 7. Open second file "header.h" -> Verify tabs "main.c" and "header.h" appear -> Tap between tabs.
# PASS
```

---

## 5. Non-Goals & Invariants

- **Not in Phase 9:** Output pane below editor (Phase 11), Python syntax transformation (Phase 12).
- **Invariants:** Editing large files must not block Compose UI rendering (debounce syntax analysis and bracket scanning on IO/Default dispatcher).
