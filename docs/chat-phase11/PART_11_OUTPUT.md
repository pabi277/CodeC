# CodeC Phase 11 — Output Panel & Integrated Run (Spck / C4droid Experience)

**Status:** Planned · **Cost:** `[client-only]` · **Depends on:** Phase 8 (Project Config) + Phase 9 (Editor Ready)  
**Target Files:** `EditorScreen.kt`, `OutputPanelView.kt`, `ExecutionRunner.kt`

---

## 1. Context & Motivation

In previous versions, running code required switching from the Editor tab to the Terminal tab, typing `cc file.c -o a.out && ./a.out`, or using the modal RUN button. Mobile IDEs like Spck and C4droid provide an instant feedback loop with an integrated split Output Panel below the editor:
- One tap on **RUN** builds and executes the project.
- Real-time output streams into a collapsible bottom panel without losing editor context.
- Compiler errors in the output panel are clickable: tapping `main.c:12:4: error:` automatically jumps the editor cursor to line 12.
- Clean separation between batch execution (Output Panel) and interactive shell (Terminal tab).

---

## 2. Architectural Design (Decision D1)

### 2.1 Split-Screen Layout
```text
+------------------------------------------+
| TopAppBar: [File] [Undo] [Redo] [RUN ▶] |
+------------------------------------------+
| Code Editor Pane (Top ~60%)              |
| 1  #include <stdio.h>                    |
| 2  int main() {                          |
| 3      printf("Hello CodeC!\n");         |
| 4      return 0;                         |
| 5  }                                     |
+------------------------------------------+
| === Splitter Bar (Drag to Resize) ===   |
+------------------------------------------+
| Output Panel (Bottom ~40%, Collapsible)  |
| [Build: OK (64ms)] [Execution: 12ms]     |
| > Hello CodeC!                           |
| [Process exited with code 0]             |
+------------------------------------------+
```

### 2.2 Execution Runner
- Runs background execution through `ExecutionRunner.kt` on `Dispatchers.IO`:
  - Executes build command from `project.json` (e.g. `cc src/main.c -o bin/app`).
  - If build succeeds, executes `./bin/app`.
  - Streams output chunks to `StateFlow<OutputState>`.
  - Captures execution duration and exit code.

### 2.3 Clickable Error Diagnostics Jump
- Output parser regex: `(?:^|[\r\n])([^:\r\n]+):(\d+):(?:(\d+):)?\s*(error|warning|note):\s*(.+)`
- Clickable text spans in Output Panel:
  - Tapping navigates active editor tab to the file and moves cursor/scroll to `line` and `col`.

---

## 3. Implementation Steps

1. **Step 1:** Create `OutputPanelView.kt` with clear, stop, copy, expand/collapse, and auto-scroll controls.
2. **Step 2:** Implement `ExecutionRunner.kt` utilizing the app's native toolchain (`cc` / `clang`).
3. **Step 3:** Implement draggable vertical splitter in Compose layout between Editor and Output Panel.
4. **Step 4:** Wire error line regex parser to Editor jump callback.
5. **Step 5:** Write unit tests in `OutputParserTest.kt` for error parsing and jump offsets.

---

## 4. Exit Condition & Verification Recipe

A fresh APK passes the following recipe on device:

```sh
# Setup & Output Panel Test
# 1. Open Editor with "main.c", type:
#    #include <stdio.h>
#    int main() { printf("Output panel working!\n"); return 0; }
# 2. Tap "RUN ▶" button in toolbar.
# 3. Observe Output Panel opens smoothly at bottom:
#    - Compiling...
#    - Executing ./a.out...
#    - Output: "Output panel working!"
#    - [Process finished with exit code 0 (84ms)]
# 4. Introduce an intentional compile error at line 3: `printf("Error)` (missing quote).
# 5. Tap "RUN ▶" -> Observe error in red in Output Panel: `main.c:3:12: error: missing terminating " character`.
# 6. Tap the red error line -> Observe Editor automatically places cursor at Line 3, Col 12.
# PASS
```

---

## 5. Non-Goals & Invariants

- **Interactive Input:** Interactive console applications requiring live keyboard stdin (like `scanf` or `ncurses`) should offer a 1-tap "OPEN IN TERMINAL" button.
