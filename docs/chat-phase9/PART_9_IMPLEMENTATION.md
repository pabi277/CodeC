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
| §2.5 Diagnostics + squiggles | `CompilerDiagnostics` parses `file:line:col: error|warning|fatal error: msg` (basename-filtered to the open file) and folds in the structured `CompilerService` errors; error/warning line tint + red/amber underline from the reported column to end-of-line; tapping the flagged line opens a tooltip with the message, **Apply missing-`;` quick fix**, and jump; status-bar chip opens the full diagnostics list | `ui/editor/CompilerDiagnostics.kt`, decorations in `ui/utils/CSyntaxVisualTransformation.kt` (`EditorDecorations`), tap handling via the custom-text-link gesture pattern in `EditorScreen`) — **known limitation:** Compose BOM 2024.09.00 `SpanStyle` has no `drawStyle`/path-effect (ui-graphics 1.8+), so the underline is straight, not wavy; re-adding a one-liner restores the dashed path effect whenever the BOM moves |
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

Host JUnit4 (plain JVM): `EditorUndoManagerTest` (8), `FindReplaceTest` (16),
`BracketMatcherTest` (9), `CodeFormatterTest` (13), `CompilerDiagnosticsTest` (9).

**Executed and green in CI** — correction of the stale "build-apk.yml runs assemble
only" note in `prompt.md`: on current `main` the `Build APK` workflow fails the build
on unit-test regressions too (`:app:testDebugUnitTest` runs as part of the build, and
`:app:lintDebug` gates on lint errors). The agent sandbox has no JDK, so CI is the
test executor; the first three CI iterations caught (and this session fixed) one real
API-compat bug (Compose BOM 2024.09.00 has no `SpanStyle.drawStyle` — squiggle
path-effect dropped), one real lint/`NewApi` error (`ProcessBuilder.redirect*` file
overloads need API 26 → replaced with daemon-thread pipe pumps), and two wrong test
expectations of mine (regex-replace `from` semantics and the indented-semicolon fix).

Locally equivalent command (any machine with a JDK):

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
| CI compile + unit tests + lint (`Build APK`, branch push) | ✅ **GREEN** — run `33239651690` (2026-08-29, head `1a0170e`-chain on `arena/01a04c1c-codec`) |
| Device recipe §4 | ⚠️ owner check required |
