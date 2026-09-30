# CodeC Phase 9 Documentation — Editor Foundation

**Status:** ✅ Implemented on `arena/01a04c1c-codec` (2026-08-29); **CI green**
(plus **Phase 9.1** device follow-up: drawer, Save-to-project, tree Run-in-terminal, loopback preview server — run `33241237168`; and **Phase 9.2**: simpler toolbar, open-folder-from-editor sheet, single files as a first-class context — run `33243620762`)
(assemble + unit tests + lint, run `33239651690`) — Phase 8 is fully accepted (PR #27
merged; export/re-import round trip owner-confirmed on device), which closed the gate
Phase 9 was waiting on. The owner ran §4 on device 2026-08-29 ("Yes working"; three
reported problems) → fixed by Phase 9.1, then the Phase 9.2 round (simpler toolbar,
open-folder-from-editor sheet, single files as a first-class context).
**Phase 9 is CLOSED: on 2026-08-29 the owner directed finalization + PR creation →
PR #28.** The 9.1/9.2 device recipes below remain the regression checklist.
See [PART_9_IMPLEMENTATION.md](PART_9_IMPLEMENTATION.md).
**Follow-up (2026-09-01):** Web Preview `File not found` after an in-editor
folder switch — root-caused and fixed at `d49ac47` (CI `33471103959`);
recorded in [PART_9_IMPLEMENTATION.md](PART_9_IMPLEMENTATION.md) as a
"Phase 9.2 follow-up" section.

Phase 9 implements all foundational editor capabilities (`[client-only]`) in Jetpack Compose:
- **Undo / Redo:** Full history stack with snapshot debouncing.
- **Find & Replace:** Search bar with regex, match count, next/prev navigation, and match highlights.
- **Code Formatter:** `clang-format` integration + built-in indent engine.
- **Bracket Matching:** Pair highlighting for `()`, `{}`, `[]`.
- **Compiler Squiggles:** Inline red error underlines parsed from compiler diagnostics.
- **Status Bar & Multi-File Tabs:** Line/Col indicator, cursor line highlight, and multi-file tabs.

## Contents & References
- **[Part 9.1 — Editor Foundation Implementation Plan](PART_9_EDITOR.md)**
