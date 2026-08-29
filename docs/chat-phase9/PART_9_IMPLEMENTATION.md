# Phase 9 — Editor Foundation: implementation record

**Status:** ✅ Implemented on `arena/01a04c1c-codec` (2026-08-29) · **Device acceptance:** pending owner run
**Plan of record:** [`PART_9_EDITOR.md`](PART_9_EDITOR.md) (unchanged exit recipe below).
**Cost:** `[client-only]` — no bootstrap/package-repo impact; zero invariants touched.

## What was implemented

| Plan section | Delivered | Where |
|---|---|---|
| §2.1 Undo/redo | `EditorUndoManager` — per-file snapshot stacks, `maxHistory = 100`, typing-run coalescing (600 ms single-char window), selection-only changes never push, redo cleared on new edit, stacks reset on reload/replace | `ui/editor/EditorUndoManager.kt`; wired per-tab in `EditorViewModel` (`undo()`/`redo()`, `canUndo`/`canRedo` flows); toolbar buttons tint-disabled live |
| §2.2 Find/replace | `FindReplaceEngine` — literal + regex, match case, whole word, wrap-around next/prev, per-match and Replace All, `$n` group references in regex replacements, invalid-pattern surfaced inline | `ui/editor/FindReplaceEngine.kt`, `ui/components/FindReplaceBar.kt`; `FindUiState` in `EditorViewModel`; matches/active match highlighted through the visual transformation |
| §2.3 Formatter | `clang-format` bridge first (`$PREFIX/bin/clang-format -style=Google`, stdin/stdout via redirected temp files, 10 s timeout, fail-safe), built-in C indenter fallback (`CodeFormatter`): brace/paren stack, case-label bodies, preprocessor column 0, string/comment-aware, **line-count-preserving** so the caret maps across format; formatted result is one undo step | `ui/editor/CodeFormatter.kt`, `ui/editor/ClangFormatBridge.kt` |
| §2.4 Bracket matching | `BracketMatcher` — `()`/`{}`/`[]` pair from caret on either side, skips string/char literals and `//`/`/* */`; 300k-char scan guard; both brackets highlighted | `ui/editor/BracketMatcher.kt`; ranges in `EditorViewModel.refreshDecorationsNow()` |
| §2.5 Diagnostics + squiggles | `CompilerDiagnostics` parses `file:line:col: error|warning|fatal error: msg` (basename-filtered to the open file) and folds in the structured `CompilerService` errors; error/warning line tint + red/amber dashed-underline squiggle from column to end-of-line; tapping the squiggled line opens a tooltip with the message, **Apply missing-`;` quick fix**, and jump; status-bar chip opens the full diagnostics list | `ui/editor/CompilerDiagnostics.kt`, decorations in `ui/utils/CSyntaxVisualTransformation.kt` (`EditorDecorations`), tap handling via the custom-text-link gesture pattern in `EditorScreen` |
| §2.6 Status bar | `Ln x, Col y · UTF-8 · Spaces: n · k selected` + diagnostics counts; current line gets a subtle full-width tint | `ui/components/EditorStatusBar.kt`; `EditorCursorPos` from the VM; line highlight in `EditorDecorations` |
| §2.7 Multi-file tabs | `EditorTabBar` over the active project — buffers cached per tab with independent undo history and dirty dot, tap-to-switch without touching navigation, ✕ close with Save/Discard confirm, adjacent-tab activation on close (Phase 7 pattern), up to 12 tabs auto-populated from the project tree (≤256 KB text files), rename re-keys the tab, "Save all" + "Reload from disk" in the overflow menu | `ui/editor/EditorTab.kt`, `ui/components/EditorTabBar.kt`, tab lifecycle in `EditorViewModel` |

`showComingSoon()` on the four editor toolbar actions is removed; the four actions are now live.
Templates screen (`CSyntaxVisualTransformation(theme)` call site) is untouched — decorations default to
empty, so the visual behavior there is byte-identical.

## Debounce/invariant notes (plan §5)

- Decoration recompute (line range, bracket scan, find re-match) is debounced 20 ms in
  `viewModelScope`; bracket scan only runs ≤300k chars, formatter bails >8000 lines.
- `updateCode` now only marks the buffer dirty when the **text** actually changed
  (previously a caret move flipped `isDirty`; that also protected tab dirty-dots).
- ClangFormatBridge only *executes* a user-installed `$PREFIX/bin` ELF by absolute path
  through `ProcessBuilder` (same exec model as `cc` under targetSdk 28). It never writes to
  `$PREFIX`, never touches `cc`/`bash`, never modifies `PATH`. All other Phase 1–8
  invariants are untouched by this diff.

## Tests

Written (host JUnit4, plain JVM): `EditorUndoManagerTest` (8), `FindReplaceTest` (15),
`BracketMatcherTest` (9), `CodeFormatterTest` (13), `CompilerDiagnosticsTest` (9).
Not executed in the agent sandbox (no JDK; `build-apk.yml` runs `assembleDebug` only) — same
status as the Phase 7 unit tests. Engine semantics for find/replace, formatting, bracket
matching and diagnostics were additionally traced through equivalent Python simulations of
each test case during development (all green).

**Owner one-liner to run them from a machine with a JDK:**

```sh
./gradlew :app:testDebugUnitTest --tests 'com.codeci.ide.EditorUndoManagerTest' \
  --tests 'com.codeci.ide.FindReplaceTest' --tests 'com.codeci.ide.BracketMatcherTest' \
  --tests 'com.codeci.ide.CodeFormatterTest' --tests 'com.codeci.ide.CompilerDiagnosticsTest'
```

## Exit condition (from PART_9_EDITOR.md §4)

A fresh APK passes the 7-step device recipe (undo/restore, find+replace highlight, format,
bracket pair highlight, squiggle on `int x =`, two-file tab switching). Build APK CI
compile-green on the session branch is recorded below; device acceptance is owner-gated.

| Gate | Result |
|---|---|
| CI compile (`Build APK`, branch push) | pending — recorded here once observed |
| Device recipe §4 | ⚠️ owner check required |
