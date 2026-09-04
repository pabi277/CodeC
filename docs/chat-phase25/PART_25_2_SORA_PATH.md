# CodeC Phase 25.2 — Sora Editor Integration (research-recommended path)

**Status:** ⭐ **CHOSEN BY THE 25.1 GATE (2026-09-04)** — C-sora passed every
device budget on both corpora (decision table:
[`docs/EDITOR_MOBILE_RESEARCH.md`](../EDITOR_MOBILE_RESEARCH.md) §3.1; raw
numbers: [`PART_25_1_SPIKE_BENCH.md`](PART_25_1_SPIKE_BENCH.md) §4.5). Awaits
the owner's **"Start Phase 25.2"** — not started yet. **Cost:** `[client-only]` ·
**Effort:** L · **Depends on:** PART 25.1 decision = C-sora ✅
· **Target files:** `ui/screens/EditorScreen.kt`, `ui/viewmodels/EditorViewModel.kt`,
`ui/editor/*` (adapters), `app/build.gradle.kts` (dependency only),
About/licenses screen, host tests

> 25.1 notes for this part: the spike pinned `io.github.rosemoe:editor:0.24.6`
> + `language-java:0.24.6` (group moved since the 0.23.6 pin — research note
> in `gradle/libs.versions.toml`); prefer `editor-bom` at integration. The
> bench's `Typed=62` artifact (60 keys dispatched) already showed Sora's
> SymbolPairMatch working on the owner's device.

---

## 1. Design

Replace **only the text widget**; keep the whole Compose shell (tabs, drawer
file tree, status bar, output panel, git chrome). `CodeEditor` lives in an
`AndroidView` — the standard Compose↔View interop, used exactly this way by
Squircle-CE-style apps (behavior reference).

Mapping (CodeC feature → Sora surface):

| CodeC today | Sora mechanism |
|---|---|
| `SyntaxVisualTransformation` + `MultiLanguageSyntaxHighlighter` | `AsyncIncrementalAnalyzeManager` via a language module; **start with a small CodeC-side lexer adapter** reusing existing regex rules — TextMate/Tree-sitter grammars are a *later* upgrade, both supported by Sora out of the box |
| `CodeCompletionEngine` (keywords/snippets, 120 ms debounce) | Sora completion **provider adapter** calling the same engine; panel hidden — Phase 27 strips/ghost UI renders the results instead (provider pipe shared) |
| `EditorKeySet` row | drives insertion through Sora's text API; strip UI stays Compose, keys feed the editor |
| `EditorUndoManager` | delegate to Sora's `UndoManager` (cross-session persistence recorded as 26.2 stretch) |
| CodeC themes (`ui/theme/EditorThemes.kt`) | `EditorColorScheme` adapter — each Sora instance needs its **own** scheme object (Sora enforce single-owner schemes) |
| Pinch zoom (`FontSizeZoom`) | built-in text-scale gestures |
| Autosave (~2 s) | editor listener → existing `EditorViewModel` save pipeline, unchanged |
| Run/output, terminal | untouched |

Bridged surfaces that must not break: tab open/close/dirty, find/replace
(replace with Sora `EditorSearcher` — keep CodeC's dialog UI), line ops
(`EditorLineOps`: duplicate/comment/indent — keep VM methods operating on
Sora content), hardware shortcuts (Phase 24.3 keys reach the editor widget),
RunKeySet context swap (Phase 23.2 — strip swap logic is editor-agnostic).

### LGPL-2.1 obligation checklist (gate before merge)
- [ ] Sora used strictly as a Gradle dependency (`editor` + chosen language
      module from Maven, `editor-bom` for versions) — **no source copied, no
      shaded classes, no fork** (the Xed `soraX` fork path is rejected here).
- [ ] About/Settings screen lists "sora-editor © Rosemoe, LGPL-2.1" with the
      license text and the project link.
- [ ] Library stays user-replaceable (ordinary dependency substitutability;
      this is also realistically verified via a dependency-substitution build
      in CI).
- [ ] `rule.md` clean-room law still binds every *other* surveyed project
      (GPL/closed) — this part imports **nothing** from them.
- [ ] Owner has seen and accepted this checklist (explicit comment in chat).

### Risks called out now
1. **Compose↔View focus/IME juggling** — mitigated by confining the View to the
   editor surface only; strip/IME events already flow through the VM.
2. **APK size** (~1–2 MB) — measured in 25.1's budget table, capped at +2 MB.
3. **Autosave drive** — Sora buffers text internally; the VM pulls content on
   the debounced listener (never per keystroke).
4. **ProGuard/R8** keep rules for Sora widgets — ship in the same commit, CI
   release build must run the editor screen in CI smoke (screenshot test).

## 2. Implementation steps

1. Dependency + `AndroidView` host composable; load a file; caret works.
2. Highlighter adapter (CodeC rules → spans) on the incremental manager.
3. Input bridge: EditorKeySet/RunKeySet → Sora text API; IME options verified
   against Gboard + a code IME.
4. Completion provider adapter (panel suppressed; results to existing state —
   Phase 27 renders them).
5. Find/replace dialog → `EditorSearcher`; verify against existing host tests'
   cases (re-implemented against the bridge).
6. Undo/redo, autosave, tabs, dirty dots — full parity checklist with the
   current editor's Phase 9/22 devices recipes re-run.
7. LGPL checklist merged with this part; CI green incl. release-APK smoke.

## 3. Exit condition

```text
(Device, release APK)
1. Open 5k-line bench.c: type 60 chars burst → no visible jank (25.1 budgets).
2. Type "(" → paired ")" with caret inside; typing ")" types OVER (no doubling).
3. Caret drag → magnifier appears and tracks finger.
4. TAB/()/{} strip keys, HW Ctrl+S/Ctrl+R all work; RunKeySet swap on interactive
   run still works.
5. Autosave on ~2 s idle; close/reopen app → file intact; undo survives tab switch.
6. Settings → About lists sora-editor + LGPL text.
PASS = all six.
```

## 4. Implementation record (2026-09-04)

All changes on `arena/01a06b20-codec`; CI = first compile check (sandbox has
no JVM/device). Files:

- NEW `app/src/main/java/com/codeci/ide/ui/editor/sora/CodeCScheme.kt` —
  `CodeCThemeMap.entries(EditorThemeColors): List<Pair<Int,Int>>` (pure,
  host-tested) + `CodeCScheme(EditorThemeType)` overriding `applyDefault()`
  (26 sora slots incl. TEXT_NORMAL, KEYWORD, LITERAL, COMMENT, FUNCTION_NAME,
  OPERATOR, ANNOTATION, search-match, scrollbar, block-line). Fresh scheme
  object per editor/theme change (sora single-ownership rule).
- NEW `.../sora/CodeCAnalyzer.kt` — `TokenStyleIds.styleIdFor(TokenKind)`
  (STRING+NUMBER → LITERAL; sora 0.24 has no STRING slot); `CodeCAnalyzer`:
  `SimpleAnalyzeManager<Int>` whose `analyze` re-tokenizes the WHOLE buffer
  with the existing `MultiLanguageSyntaxHighlighter.tokenize` on sora's own
  background thread (latest-request-wins) and emits `MappedSpans`;
  `LineColumnCursor` (forward-only offset→(line,col), pure, host-tested);
  `CodeCLanguage(LanguageType)`: analyzer + `INTERRUPTION_LEVEL_NONE` +
  no-op `requireAutoComplete` + pure `indentAdvanceFor` (`{`, or `:` for
  Python) + `useTab=false` + NoOpFormatter + pure `symbolPairsFor`
  (C-family pairs + quotes; none for TEXT/MARKDOWN) + no newline handlers.
- NEW `.../sora/SoraEditorHost.kt` — the two-way bridge. Sora→VM:
  `ContentListener` (afterInsert/afterDelete) + `SelectionChangeEvent`
  subscription → `viewModel.updateCode(TextFieldValue(...))` — the same
  entry point the old `BasicTextField.onValueChange` used, so undo
  recording, dirty flags, autosave scheduling and decoration refresh are
  byte-identical to before. VM→sora: AndroidView.update replays foreign
  changes (tab switch, undo/redo, find/replace, formatter, keys strip,
  completion insert) as ONE `batchEdit` delete-all+insert + selection
  restore; selection-only VM changes (find-next navigation, quick fixes,
  strip cursor keys) replay as caret/region moves without touching text.
  Reference-equality fast path: typing echoes return the same String
  instance the listener pushed → zero work per keystroke. Sora's own undo
  DISABLED (`setUndoEnabled(false)`) — VM `EditorUndoManager` canonical.
- `EditorScreen.kt` — BasicTextField + custom gutter + per-line scroll Rows
  replaced by `SoraEditorHost(editor = remember { CodeEditor(context) })`
  (editor declared early — the find effect and the host both need it);
  Phase 16 pinch handler deleted (sora `setScalable(true)` is on by
  default); diagnostics tap-popup + `pointerInputDiagnosticsTap` +
  `EditorPopupAnchor` + `textLayoutResult`/`fontSizeState`/popup state
  deleted; completion popup re-anchored `Alignment.BottomStart` above the
  keys strip; Ctrl+Z / Ctrl+Shift+Z / Ctrl+Y → `viewModel.undo/redo()`;
  Phase 22.1 decorations builder + `currentLineRange`/`bracketRanges`
  collectors removed; find bar wired to `soraEditor.searcher`
  (`SearchOptions(TYPE_* by wholeWord/regex, !matchCase)`).
- `EditorViewModel.kt` — Phase 22.1 highlight pipeline removed
  (highlightContext/highlightJob/HighlightRequest); Phase 23 decoration
  pipeline kept (cheap pure Kotlin; its line/bracket outputs are currently
  unconsumed by the screen — a v1 trim candidate, harmless).
- `app/build.gradle.kts` — compileOptions 17 (mirrors :bench, proven green
  in CI) + `implementation(libs.sora.editor)`.
- `SettingsScreen.kt` — About: "Open-source licenses — sora-editor ©
  Rosemoe — LGPL-2.1 · github.com/Rosemoe/sora-editor".
- `app/src/main/assets/licenses/SORA_EDITOR_LGPL.txt` — verbatim LGPL-2.1
  text from the upstream repo.
- Host tests (new): `CodeCThemeMapTest` (slot coverage × all 3 themes,
  contrast guards, token-style mapping), `CodeCLanguageLogicTest`
  (indentAdvanceFor, symbolPairsFor, LineColumnCursor walk/clamp, analyzer
  span ordering).

### Deviations from the 25.2 plan (honest list)

1. **Analyzer v1 = full re-tokenize** per settled edit on
   `SimpleAnalyzeManager` — NOT `AsyncIncrementalAnalyzeManager` as sketched
   in §1. Rationale: 25.1 measured the full pipeline (tokenize is ~2 ms on
   the 517-line HTML; the 5 000-line bench.c cost the OLD renderer ~400 ms
   per frame, the tokenizer itself far less), and incremental lexing is the
   single riskiest adapter piece. Follow-up candidate once the device round
   confirms the v1 budgets.
2. **Completions stay VM-driven** — sora's panel closed
   (`requireAutoComplete` no-op); the app popup renders above the keys
   strip (bottom-start) instead of the old cursor-rect anchor. Cursor-rect
   anchoring returns with the Phase 27 shared pipe.
3. **Diagnostics tap-popup removed** with the BasicTextField surface
   (feature regression, recorded). Jump-to-diagnostic survives via the
   problems list paths; a sora-side tap consumer is a follow-up.
4. **Undo is VM-canonical** (sora stack disabled) — this is what keeps
   "undo survives tab switch" true.
5. **Pinch text-scale is sora-native** and does not write back to the
   Settings font-size (Settings change overrides; same non-persistence as
   the old Phase 16 pinch).
6. **R8**: :app ships minifyEnabled=false (unchanged) — no keep rules
   needed; revisit with §1 risk 4 if minification ever lands.

### LGPL-2.1 checklist status (merge gate)

- [x] Binary Gradle dependency ONLY (`libs.sora.editor` = 0.24.6 AAR from
      Maven Central; no source copied, no fork, no shading; upstream source
      read for interfaces only, `/tmp/sora-ref`).
- [x] Attribution in Settings → About + license text in
      `assets/licenses/SORA_EDITOR_LGPL.txt`.
- [x] Copyright/licence headers unchanged (our files are original code;
      sora is linked, not modified).
- [ ] **Owner's explicit acceptance of LGPL-2.1 in chat — REQUIRED before
      any merge** (rule.md §3; this gate is not yet satisfied).
- [x] APK delta provisional: `CodeC-IDE` artifact 21 854 392 B (20.84 MiB,
      CI `33857318159`) vs 25.1's 20.3 MiB → **+0.55 MiB artifact delta**
      (sora compresses well; Phase 22.1 code removal offsets). Final APK
      re-measure at merge time — budget ≤ +2 MB comfortably met so far.

### 4.1 Device round 1 — CRASH on editor tap (owner report 2026-09-04)

Owner: tapping/using the editor crashes the app (system crash dialog). No
stack available (no adb; sandbox cannot run the app). Fixes shipped on
inspection of the bridge:

1. **FATAL bridge race — FIXED.** The `SelectionChangeEvent` receiver did
   not honor the `pushing` guard. Every VM→sora text replay (tab switch,
   undo/redo, find/replace, completion insert, keys-strip text keys) fires a
   sora selection event MID-replay, and the receiver pushed the STALE
   pre-replay string to `viewModel.updateCode` — the VM rolled back to the
   old text, recorded a phantom undo step, and the next recomposition
   replayed the old text into sora, whose echo pushed the new text back:
   an endless two-way replay ping-pong (2 full-file replays + 2 undo records
   per cycle) → ANR/OOM → crash dialog. The content-listener path was
   already guarded; the selection path now is too.
2. **Startup double-apply — FIXED.** Language/scheme/font/size/tab/wrap/
   line-numbers were applied in the `remember{}` block AND again by their
   `LaunchedEffect` first runs; sora destroys + rebuilds the analyzer and
   scheme on each redundant set. Now the remember block only disables sora's
   undo stack; everything keyed is applied by exactly one effect.
3. **Crash capture — ADDED.** `MainActivity.installCrashLog()`: the default
   uncaught handler now appends the stack to
   `Android/data/com.codeci.ide/files/crash-log.txt` (any file manager can
   read it) before delegating to the system handler. Next device round
   produces a real stack if anything remains.

Plain-tap path re-traced after the fixes: tap → sora touch (25.1-bench-
proven on this device) → SelectionChangeEvent (not pushing) → VM
selection-only update → recomposition → both replay branches skip. No app
code executes beyond a cheap selection push. If the crash reproduces, the
crash-log file gives the stack.

### 4.2 Device round 1 follow-up — crash log is UNREACHABLE without root

Owner: still crashing (system dialog screenshot) and — correctly — cannot
browse `Android/data` (no root; Android 11+ hides it from file managers).
Fixes:

1. **In-app crash viewer — `ui/crash/CrashReportOverlay.kt`.** The handler
   now writes to `filesDir/crash-log.txt` (internal, always writable) and on
   the NEXT launch the overlay opens before anything else with the last
   report and COPY ALL / SHARE / CLEAR. No permissions, no root, no file
   manager. The agent finally gets real stacks.
2. **Selection push hardened.** Two residual poison cases in the receiver:
   (a) indices clamped to the synced snapshot (a `TextFieldValue` whose
   selection exceeds its text is undefined behavior downstream); (b) events
   arriving BEFORE the first replay are dropped entirely — the synced
   snapshot is still `""`, and pushing it would overwrite the VM's real file
   text (data wipe + phantom undo).
3. VM-side audit of every tap-path consumer: cursor readout clamps
   (`selection.min.coerceIn`), completion engine coerces the cursor, popup
   modulo guarded by `isNotEmpty` — all already safe; the bridge was the
   only unclamped producer.

NOTE: it is not yet confirmed the owner ran the round-1-fixed APK at all —
the report may predate it. Either way round 2 is strictly additive.

### 4.3 Device round 2 — CRASH ROOT CAUSE (owner pasted the in-app report)

The overlay worked: the owner pasted a full stack from the dialog. Root
cause, no guesswork:

    NullPointerException: Parameter specified as non-null is null:
    EditorThemesKt.getEditorTheme, parameter type
      at CodeCScheme.applyDefault(CodeCScheme.kt)
      at EditorColorScheme.<init>(EditorColorScheme.java:237)
      at CodeCScheme.<init>(CodeCScheme.kt)
      at SoraEditorHost.kt:84  (LaunchedEffect(theme), first composition)

**Leaked `this` in the super-constructor.** sora's `EditorColorScheme()`
constructor calls `applyDefault()`; Kotlin subclass properties (`type`) are
assigned only AFTER `super()` returns, so the override read
`type == null` → `getEditorTheme(null)`'s intrinsic null-check threw —
instantly, every time the editor screen composed. (This is why the crash
was 100 % reproducible on tap and invisible in CI: it is a runtime
initialization-order bug, not a compile error.) The round-1 ping-pong guard
was a real latent bug but never got the chance to run.

**Fix:** `CodeCScheme` no longer overrides `applyDefault()` (the base fills
sora defaults during construction); colors are applied post-construction
via `CodeCScheme.of(theme)` → `apply(EditorThemeColors)`. `CodeCThemeMap`
unchanged (pure, host-tested). No other sora subclass override can hit the
same trap (CodeCAnalyzer's `analyze` runs on sora's thread post-construction).

APK note: the crash-log overlay (round 2) is what produced this stack —
the no-root debugging loop is now proven end-to-end.

### 4.4 Device round 3 — PASS with two owner-reported items (both fixed)

Owner: "Everything working fine" + crash gone; two problems:

1. **File drawer opened during editor scrolls.** The screen's
   `ModalNavigationDrawer` had no `gesturesEnabled` — its edge-swipe zone
   covers the editor's line-number gutter, so a scroll/fling starting at
   the left edge (with any horizontal drift) opened the drawer mid-scroll.
   Fix: `gesturesEnabled = activeTabPath == null && currentFileName.isEmpty()`
   — edge-swipe stays available on the no-file screen; with a file open the
   drawer opens via the folder button. (Per-side gesture zones don't exist
   in M3 drawers.)
2. **"Highlighting/suggestions are still previously used but sora is better
   than that."** Suggestions: `CodeCLanguage.requireAutoComplete` now feeds
   the SAME `CodeCompletionEngine` results into sora's NATIVE panel
   (`SimpleCompletionItem(label, detail, prefixLength, insertText)` + kind
   icons) — at-caret positioning, prefix-replacing commit, sora-managed
   keyboard selection. The Phase 12/22 app popup is RETIRED: produceState
   scan, popup Surface, `insertCompletion`, `completionIndex/Dismissed`,
   and the popup's hardware-key block (plain Tab/Enter/arrows/Escape now
   fall through to sora; all Phase 24.3 shortcuts are Ctrl/F5 and still
   fire first). Owner-directed scope move: this pulls Phase 27's
   "render completions in sora's panel" forward; Phase 27 keeps its other
   scope.
   **Highlighting granularity is UNCHANGED by design** — v1 renders the
   same 7-kind CodeC tokenizer through sora's span pipeline (visually the
   same theme). RICHER highlighting (TextMate/tree-sitter grammars) needs
   a new binary dependency (`language-textmate`) + grammar assets — a
   separate increment; NOT started without the owner's go.

Device round 3 = §3 recipe items 1–6 PASS (typing, pairing/skip, magnifier,
strip+run keys, autosave+undo-across-tabs, About entry).

### CI history

| # | Run | Commit | Result |
|---|-----|--------|--------|
| 1 | `33855565141` | `b6e8257` | ❌ 2 root causes: `setTextSizeUnit` doesn't exist (sora's `setTextSize(float)` already takes **Sp**); `FormatResultReceiver` is NESTED in `Formatter`. Everything else in the 1 360-line change compiled first try. |
| 2 | `33856448309` | `4268bdb` | ❌ main sources now compile — test compile: `Pair >= Pair` has no compareTo (span-ordering assertion). |
| 3 | `33857318159` | `f78864a` | ✅ **GREEN** — `:app` compiles against sora 0.24.6 (release too), host tests pass: `CodeCThemeMapTest` ×5, `CodeCLanguageLogicTest` ×6. Artifacts: CodeC-IDE 20.84 MiB, CodeC-Bench 1.26 MiB. |
| 4 | `33859414468` | `8727941` | ❌ Kotlin: `return@subscribeEvent` — with SAM-constructor syntax the lambda label is the CONSTRUCTOR name (`return@EventReceiver`). |
| 5 | `33860045301` | `08fb542` | ✅ **GREEN** — §4.1 fixes in (selection-echo guard, single-apply config, crash log). CodeC-IDE 21 855 224 B (20.84 MiB, delta +0.55 MiB vs 25.1 — unchanged). |
| 6 | `33861505252` | `c1c7cb3` | ❌ §4.2 fixes: `val left/right` declarations lost in the clamp edit + missing `Modifier` import in the overlay. |
| 7 | `33861977324` | `5298b00` | ✅ **GREEN** — in-app crash viewer + clamped selection push live. Device round 2 ran this; the overlay produced the §4.3 stack. |
| 8 | `33863407938` | `fe7ae11` | ✅ **GREEN** — **THE crash fix** (`CodeCScheme.of()`, applyDefault override removed). Device round 3: §3 recipe PASS. |
| 9 | `33866749797` | `c54228d` | ✅ **GREEN** — round-3 fixes (drawer gesture scoped, sora-native completions, app popup retired). **Build for device round 4.** |

## 5. Exit condition (unchanged from §3 above)

Owner device round on the new CodeC-IDE release APK (recipe in §3), plus
optional CodeC Bench re-run (C-sora scenario should now match the shipped
editor). PASS = all six §3 items.
