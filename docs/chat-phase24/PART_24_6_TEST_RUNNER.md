# CodeC Phase 24.6 — Test-Runner UI (pytest / go test output tab)

**Status:** 📋 **PLANNED** · **Cost:** `[client-only]` · **Effort:** S
· **Depends on:** Phase 21.1 (LanguageRegistry — Python and Go profiles exist)
· **Target files:** `ui/screens/EditorScreen.kt`, `ui/viewmodels/EditorViewModel.kt`,
  `ui/components/OutputPanelView.kt`, `ui/editor/OutputLineParser.kt`

---

## 1. Design

For test files (`test_*.py`, `*_test.py`, `*_test.go`), a **Test ▷** button
appears in the editor toolbar alongside **▶ RUN**. Tapping it runs the test
command and streams results to a dedicated **Tests** tab in the Output Panel,
with color-coded PASS (green) / FAIL (red) lines.

### Test profiles (extends `LanguageRunProfile`)

Add to `LanguageRegistry`:

```kotlin
LanguageRunProfile(
    displayName = "Python test",
    extensions  = listOf("py"),      // overridden only for test files
    requiredPackage = "python",
    buildTemplate   = null,
    runTemplate     = "python3 -m pytest \$SRC -v",
    // OR: "python3 -m unittest \$SRC" for unittest
),
LanguageRunProfile(
    displayName = "Go test",
    extensions  = listOf("go"),
    requiredPackage = "golang",
    buildTemplate   = null,
    runTemplate     = "go test ./...",
),
```

Test profile selection: `LanguageRegistry.testProfileForFile(path)` checks
whether the filename matches `test_*.py` / `*_test.py` / `*_test.go` and
returns the test profile if so.

### Test ▷ button visibility

```kotlin
val showTestButton by remember(activeTabPath) {
    derivedStateOf { LanguageRegistry.testProfileForFile(activeTabPath ?: "") != null }
}
```

### PASS / FAIL line parsing in `OutputLineParser`

```kotlin
// Extended in OutputLineParser:
fun parseTestLine(line: String): OutputLine {
    return when {
        line.startsWith("PASSED")  || line.contains("passed") -> OutputLine(line, color = Green)
        line.startsWith("FAILED")  || line.contains("failed") -> OutputLine(line, color = Red)
        line.startsWith("ERROR")                              -> OutputLine(line, color = Amber)
        line.startsWith("ok ")     || line.startsWith("---")  -> OutputLine(line, color = Green)
        line.startsWith("FAIL")                               -> OutputLine(line, color = Red)
        else -> OutputLine(line, color = Default)
    }
}
```

---

## 2. Implementation steps

1. Add `fun testProfileForFile(path: String): LanguageRunProfile?` to
   `LanguageRegistry`.
2. Add **Test ▷** button to `EditorScreen` toolbar (visible when `showTestButton`).
3. Add `fun runTests(context: Context)` to `EditorViewModel` (uses the test
   profile from the registry; same `ExecutionRunner` pipeline as `runActiveFile`).
4. Extend `OutputLineParser` with `parseTestLine` per §1.
5. Write host unit tests for `testProfileForFile` (returns non-null for test
   files, null for non-test files) and `parseTestLine` (correct color mapping).

---

## 3. Exit condition

```text
1. Create test_hello.py:
     def test_pass(): assert 1 == 1
     def test_fail(): assert 1 == 2
2. Open it in the editor.
   EXPECT: a "Test ▷" button appears in the toolbar.
3. Tap Test ▷.
   EXPECT: Output Panel shows pytest output;
   PASSED line is green; FAILED line is red.
4. Open main.c (not a test file).
   EXPECT: no "Test ▷" button.
PASS = steps 1–4 behave as described.
```
