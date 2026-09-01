# CodeC Phase 24.1 — Per-language Code Formatter (Format menu item)

**Status:** 📋 **PLANNED** · **Cost:** `[client-only]` · **Effort:** XS
· **Depends on:** Phase 21.1 (`formatterTemplate` field in `LanguageRunProfile`)
· **Target files:** `ui/screens/EditorScreen.kt` (overflow menu),
  `ui/viewmodels/EditorViewModel.kt` (`formatActiveFile`)

---

## 1. Design

`LanguageRunProfile.formatterTemplate` already holds the formatter command
(e.g. `clang-format -i $SRC` for C/C++, `black $SRC` for Python). This part
wires it to a **Format** menu item in the editor's overflow (`⋮`) menu.

### Format action flow

```
User taps ⋮ → Format
    └─ EditorViewModel.formatActiveFile(context)
           ├─ look up profile = LanguageRegistry.forFile(activeTabPath)
           ├─ if profile?.formatterTemplate == null → show "No formatter for this language"
           ├─ if formatter binary not installed → same auto-install gate as D.2
           ├─ save file (flush buffer to disk first — formatter reads the file)
           ├─ run formatter command via ExecutionRunner (build-only, no run phase)
           ├─ on success (exit 0): reload file from disk into the editor buffer
           │    (formatter rewrote it in-place)
           └─ on failure: show error in Output Panel; keep original buffer
```

### Menu visibility

The **Format** item is visible in `⋮` only when:
- The active file has a language with a non-null `formatterTemplate`.
- (Optional) the formatter binary is installed (otherwise show it greyed out
  with a tooltip "Install <tool> to format").

### Undo after format

The formatted result must be pushed to `EditorUndoManager` as one undo step
so the user can undo the entire format with one tap. This matches the
Phase 9 `CodeFormatter` (built-in C formatter) behavior — same pattern.

---

## 2. Implementation steps

1. Add `fun formatActiveFile(context: Context)` to `EditorViewModel` per the
   flow above.
2. Add **Format** to the `⋮` overflow menu in `EditorScreen`; conditionally
   visible based on `profile?.formatterTemplate != null`.
3. Reload the file from disk after a successful format and push to undo stack.
4. Write host unit tests — verify that `formatActiveFile` uses
   `formatterTemplate` from the registry (mock the runner, check the command).

---

## 3. Exit condition

```text
1. Open a messy C file (inconsistent indentation).
2. Tap ⋮ → Format.
   EXPECT: file is reformatted (clang-format style); editor shows the new content.
3. Tap Undo: EXPECT: original messy file restored.
4. Open a Python file (with black installed).
   Tap ⋮ → Format: EXPECT: black formats the file.
5. Open a .go file (with golang installed; gofmt bundled).
   Tap ⋮ → Format: EXPECT: gofmt formats the file.
6. Open a file with no formatter (e.g. a .lua file, no formatter configured).
   Tap ⋮: EXPECT: Format item is absent from the menu.
PASS = steps 1–6 behave as described.
```
