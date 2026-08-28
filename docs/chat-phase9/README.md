# CodeC Phase 9 Documentation — Editor Foundation

Phase 9 implements all foundational editor capabilities (`[client-only]`) in Jetpack Compose:
- **Undo / Redo:** Full history stack with snapshot debouncing.
- **Find & Replace:** Search bar with regex, match count, next/prev navigation, and match highlights.
- **Code Formatter:** `clang-format` integration + built-in indent engine.
- **Bracket Matching:** Pair highlighting for `()`, `{}`, `[]`.
- **Compiler Squiggles:** Inline red error underlines parsed from compiler diagnostics.
- **Status Bar & Multi-File Tabs:** Line/Col indicator, cursor line highlight, and multi-file tabs.

## Contents & References
- **[Part 9.1 — Editor Foundation Implementation Plan](PART_9_EDITOR.md)**
