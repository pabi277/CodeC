# CodeC Phase 9 Documentation — Editor Foundation

**Status:** ✅ Implemented on `arena/01a04c1c-codec` (2026-08-29); **CI green**
(plus **Phase 9.1** device follow-up: drawer, Save-to-project, tree Run-in-terminal, loopback preview server — run `33241237168`; and **Phase 9.2**: simpler toolbar, open-folder-from-editor sheet, single files as a first-class context — run `33243620762`)
(assemble + unit tests + lint, run `33239651690`) — Phase 8 is fully accepted (PR #27
merged; export/re-import round trip owner-confirmed on device), which closed the gate
Phase 9 was waiting on. **Device acceptance of the §4 recipe is pending owner run.**
See [PART_9_IMPLEMENTATION.md](PART_9_IMPLEMENTATION.md).

Phase 9 implements all foundational editor capabilities (`[client-only]`) in Jetpack Compose:
- **Undo / Redo:** Full history stack with snapshot debouncing.
- **Find & Replace:** Search bar with regex, match count, next/prev navigation, and match highlights.
- **Code Formatter:** `clang-format` integration + built-in indent engine.
- **Bracket Matching:** Pair highlighting for `()`, `{}`, `[]`.
- **Compiler Squiggles:** Inline red error underlines parsed from compiler diagnostics.
- **Status Bar & Multi-File Tabs:** Line/Col indicator, cursor line highlight, and multi-file tabs.

## Contents & References
- **[Part 9.1 — Editor Foundation Implementation Plan](PART_9_EDITOR.md)**
