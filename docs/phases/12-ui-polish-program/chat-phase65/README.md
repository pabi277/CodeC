# Phase 65.1 — Shell and navigation

**Owner-approved and implemented 2026-09-29 in the same chat as Phase 74.1.** The owner delegated 65.1 design decisions; we kept the current bottom navigation and hide-while-typing behavior, avoiding a speculative navigation rewrite. Existing policy/test coverage remains unchanged. No navigation source changes were needed.

## Typing symptoms deferred to a dedicated phase

In that chat the owner also reported two code-writing defects: Python `def` auto-indents but `for` does not (choice A), and Backspace on indentation jumps across the indentation rather than removing a single space. I initially patched the analyzer-language dispatch and multi-space Backspace normalization, but the owner subsequently requested that **all code-writing changes be undone** and that typing work move into a focused editor phase. Those code changes and their tests were reverted in the follow-up commit; the defects are **not fixed** and remain for proposed Phase 75.1. Their evidence, hypotheses, test matrix and scope are recorded in `docs/phases/12-ui-polish-program/PHASE_75_1_EDITOR_TYPING.md`.

## Current status

Navigation selection remains as implemented. The independent Phase 74.1 Settings work remains. The earlier green Build APK runs predate the revert. Post-revert Build APK `36625653233` is green on `902c9f6`; it validates the restored source, not the typing behavior, which remains unresolved. No PR opened and no merge performed.
