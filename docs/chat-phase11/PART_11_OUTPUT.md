# CodeC Phase 11 — Output Panel & Integrated Run (Spck / C4droid Experience)

**Status:** ✅ **IMPLEMENTED 2026-08-30** (`arena/01a0508b-codec`) — code + host unit
tests written; **CI GREEN** (runs `33289190964`, `33290932427`: assemble +
`:app:testDebugUnitTest` + lint via the `gradle-bootstrap` chain; first attempt
`33289110743` caught two compile errors — missing `TerminalHandoff` import, suspend
`incrementRuns` outside a coroutine — fixed in `15a0bc6`). **Device rounds in
progress** — §6.4 records the owner's transcripts (single-file run ✅, error
display ✅, Apply-fix shipped in response). **Cost:** `[client-only]` · **Depends on:** Phase 8 (Project Config) + Phase 9 (Editor Ready)  
**Target Files:** `EditorScreen.kt`, `OutputPanelView.kt`, `ExecutionRunner.kt` (+
`OutputLineParser.kt`, `EditorViewModel.kt`, `TerminalHandoff.kt`)

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

---

## 6. Implementation record (2026-08-30, `arena/01a0508b-codec`)

Implemented against the current codebase (post-Phase 9.2, PR #28 merged). The
editor at that point already had: a RUN button (project → terminal handoff,
scratch → legacy `CompilerService` in-editor pipeline), a `TerminalOutput`
bottom panel driven by `terminalSegments`, and Phase 9's squiggle
(`CompilerDiagnostics`) + tap-to-inspect machinery. What changed:

### 6.1 Delivered

| Piece | File | Notes |
|---|---|---|
| Output line parser (clickable diagnostics) | `ui/editor/OutputLineParser.kt` (new) | Clang `file:line:col: kind:` and TCC `file:line: kind:` forms; `fatal error`/`warning`/`note`; column 0 for TCC. |
| Process runner | `ui/services/ExecutionRunner.kt` (new) | Android-free (JVM-testable). Runs build/run command strings via the resolved userland shell with the CodeC env; merged stdout+stderr streaming; build then run (failing build skips run); 30 s build / 10 s run timeouts (exit 124); responsive Stop (50 ms poll + destroy, `destroyForcibly` reflective for API 26+). |
| Output panel | `ui/components/OutputPanelView.kt` (new) | Header: status summary, Stop (while busy), Open in Terminal, Copy, Clear, expand/collapse chevron; auto-scroll; collapsed one-line strip (tap expands); diagnostic lines red/orange + underlined + clickable → editor jump. `extractUrls` moved here from the deleted `TerminalOutput.kt` (same package, `TerminalUxTest` untouched). |
| VM pipeline | `ui/viewmodels/EditorViewModel.kt` | New `OutputRunState` (phase, lines, build/run exit+duration, summary, lastTerminalCommand) replacing `terminalSegments`; `runActiveFile` / `stopRun` / `clearOutput` / `toggleOutput` / `jumpToOutputDiagnostic`; legacy `runCode`/`CompilerService` editor pipeline removed. |
| Build/run split | `ui/terminal/TerminalHandoff.kt` | Added `compileParts` and `projectRunParts` (pure); `compileAndRunCommand`/`projectRunCommand` refactored onto them with byte-identical output (existing tests still pass). |
| Editor screen | `ui/screens/EditorScreen.kt` | RUN ▶ → `runActiveFile` (all non-web contexts); draggable `OutputPanelSplitter` (10 dp, vertical drag, panel height 120 dp … 55 % screen); panel replaces the old TerminalOutput block. |
| Strings | `res/values/strings.xml` | 11 new `output_*` strings. |
| Tests | `OutputLineParserTest`, `ExecutionRunnerTest` (new, host JVM via `/bin/sh`); `TerminalHandoffTest` +4 | |

### 6.2 Design decisions (D1–D5)

- **D1 — RUN now uses the real toolchain (`cc` frontend → embedded TCC), the
  same command strings the terminal handoff builds.** The legacy in-editor
  `CompilerService` pipeline (which honored the Settings "Compiler Engine"
  backend for scratch mode only) is removed — the terminal has used `cc` since
  Phase 9.1, and the output panel is the batch twin of that path. The Settings
  backend picker remains in place (it still documents/installs the bundled
  Clang); its runtime effect on the editor is superseded — flagged for the
  owner as a possible follow-up.
- **D2 — Project contexts run `project.json` build/run via
  `projectRunParts`; single files compile in place
  (`cc <abs> -o a.out`, then `./a.out`).** Web projects keep the preview path.
  A project RUN no longer navigates to the Terminal screen — the panel IS the
  batch runner; "Run in Terminal" stays in the ⋮ menu and the panel header
  ("Open in Terminal") as the interactive escape hatch (Phase 11 non-goal).
- **D3 — Environment from `ShellBootstrap.prepare()`** (`ShellEnvironment.buildEnv`):
  PATH with `$PREFIX/bin`, `PREFIX`/`HOME`/`TMPDIR`, termux-exec `LD_PRELOAD`
  when present, `CC_STD/CC_WARN/CC_OPT` from Settings, `TCC_BIN/TCC_BUNDLE`.
  This guarantees `cc` exists (the frontend script is written by `prepare` even
  before a Phase 2/3 userland) and keeps every Phase 1–3 invariant (no `.` on
  PATH, `-o` last, no Termux identity).
- **D4 — Squiggles on failed build:** the accumulated build output is fed to
  `CompilerDiagnostics.parse` (filtered to the active file), so the existing
  Phase 9 error underlines + tap-to-inspect tooltip light up from a failed
  RUN — and the panel's error lines are clickable into any file of the current
  folder (jump is path-confined to the project root / single-files dir).
- **D5 — Stop is a real kill:** the runner polls `exitValue` every 50 ms
  instead of blocking `waitFor`, so cancelling the collection (Stop) destroys
  the live process within one tick instead of after the timeout.
- **D6 — One-tap Apply fix (owner: "Write a code to apply", device round 2):**
  fixable diagnostics (`';' expected`-style) render an **Add missing ;** button
  under the red line; tapping opens/jumps the file and applies the Phase 9
  quick fix, then the user re-runs. Two device-evidence fixes landed with it:
  (a) TCC prints `file:line:` with **no column**, which the Phase 9 squiggle
  parser did not understand — `CompilerDiagnostics.parse` now accepts the TCC
  line-only form (column → 1) so failed builds light up squiggles for the
  embedded `cc`; (b) TCC reports `';' expected (got "}")` **at the closing
  brace line** while the missing `;` belongs to the line above —
  `applyQuickFix` gained a brace fallback (fixes the previous line when the
  reported line ends with `}`). Pure label overload
  `CompilerDiagnostics.semicolonFixLabel(OutputDiagnostic)` gates the button.

### 6.4 Device evidence (owner transcripts, 2026-08-30)

**Round 1 — single-file run (PASS):**
```
$ cc /data/user/0/com.codeci.ide/files/CodeC/projects/main.c -o a.out
Build OK (51ms)
Hello, World!
Process finished with exit code 0 (50ms)
```
**Round 2 — intentional error (PASS — display + stop; tap-jump/apply pending
re-test with the new build):**
```
$ cc /data/user/0/com.codeci.ide/files/CodeC/projects/main.c -o a.out
/data/user/0/com.codeci.ide/files/CodeC/projects/main.c:6: error: ';' expected (got "}")
Build failed with exit code 1
```
Owner's follow-up: "2. Write a code to apply" → shipped D6 (commit `bc4efea`,
CI `33290932427`).

### 6.3 Device recipe (the §4 exit condition — needs the owner's device run)

1. Open the Editor with `main.c` containing `#include <stdio.h>` / `int main()
   { printf("Output panel working!\n"); return 0; }`.
2. Tap **RUN ▶** → the Output panel opens at the bottom: `$ cc …` (single file)
   or the project's build command, output lines, `Build OK (Nms)`, the program
   output, and `Process finished with exit code 0 (Nms)`.
3. Introduce an intentional error at line 3 (`printf("Error)`), RUN again →
   the panel shows the red diagnostic `main.c:3:12: error: missing terminating
   " character`, the editor shows the squiggle, and tapping the red panel line
   places the cursor at line 3, col 12.
4. Drag the splitter bar to resize; collapse the panel and tap the strip to
   re-expand; with `scanf`-style programs tap **Open in Terminal** to get the
   interactive PTY.

Known follow-ups (non-blocking): Settings "Compiler Engine" runtime effect
(D1); `extractUrls` has no in-app consumer (moved, not removed).
