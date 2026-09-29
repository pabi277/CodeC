# Phase 65.1 — Shell and navigation

**Owner approved both remaining discussion parts in this chat (2026-09-29).** The owner delegated Phase 65.1 design decisions to the agent and approved all recommendations: retain the current hide-while-typing navigation-bar behavior; keep the existing four main destinations and editor side panel; do not redesign navigation. The existing policy already implements the chosen behavior, so no navigation behavior was changed. Review anchor: `MainActivity.kt`'s `NavBarPolicy.hideNavBar` wiring and `ui/editor/NavBarPolicy.kt`; regression coverage remains in `NavBarPolicyTest` and `SidePanelWiringTest`.

## Related editor defect reported in the same chat

Owner: Python `def` auto-indents, but `for` does not (choice A); repeated Backspace while removing indentation jumps across the indentation instead of removing one space.

### Root cause and fix

`CodeCLanguage.getIndentAdvance` delegated to `indentAdvanceFor(line)` without forwarding the analyzer's active language. The helper therefore used its default C language for the Sora analyzer path, so Python's trailing-colon rule was not applied consistently there. It now passes `language`; coverage pins both `def f():` and `for item in items:` plus the analyzer dispatch.

Some IME deletion updates can remove a whole leading space-run in one edit. `SmartTyping.normalizeIndentBackspace` now detects only multi-space deletions wholly inside a line's leading spaces and converts them into a one-space deletion; ordinary text/word deletions are left alone. `EditorViewModel.updateCode` applies that correction before smart typing and undo recording. Host tests cover this path and its negatives.

**Evidence boundary:** source-level root cause and host regression tests are added; this report does not claim handset reproduction or acceptance. Android CI `Build APK` is the only execution-of-record. Device validation is useful for the actual IME behavior but is not represented as complete here.

## Exit

Build APK round 1 `36607240454` failed at test compilation because a new test function omitted `()`; corrected in the follow-up commit, next run pending. No PR opened and no merge performed. See `docs/NEXT_STEPS.md` and `rule.md` §3.
