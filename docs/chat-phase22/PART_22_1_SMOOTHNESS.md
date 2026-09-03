# CodeC Phase 22.1 — Scroll + Recomposition Decoupling (fix the "stuck" feeling)

**Status:** 📋 **PLANNED** — not yet started · **Cost:** `[client-only]`
· **Depends on:** nothing
· **Primary target files:** `ui/screens/EditorScreen.kt`,
  `ui/utils/MultiLanguageSyntaxHighlighter.kt`,
  `ui/viewmodels/EditorViewModel.kt`,
  `ui/utils/SyntaxVisualTransformation.kt` (or `CSyntaxVisualTransformation.kt`)

---

## 1. Evidence — where the "stuck" comes from

Current `EditorScreen.kt` has four compounding sources of per-keystroke work:

| # | Source | Current code | Cost |
|---|---|---|---|
| 1 | **Full-file highlight rebuild** | `visualTransformation = SyntaxVisualTransformation(codeText, language, decorations)` — rebuilds the entire `AnnotatedString` on every recomposition | O(n) per keystroke |
| 2 | **Gutter string rebuild** | `(1..lineCount).joinToString("\n")` — string allocations per keystroke | O(lines) per keystroke |
| 3 | **Double-scroll modifier** | `BasicTextField` wrapped in both `verticalScroll(scrollState)` and `horizontalScroll(hScrollState)` — two scroll modifiers fighting the field's internal scroll; manual caret follow via `getCursorRect` + offset | Frame drops on fling |
| 4 | **Broad `remember` keys** | `tabViews` on `remember(openTabs, activeTabPath, codeText, isDirty)` — triggers on every keystroke even when tabs are unchanged; `completionItems` on `remember(codeText, language)` — rebuilds the completion list on every character | Unnecessary recomposition |

---

## 2. Design — three targeted fixes

### 2.1 Debounced off-thread syntax highlighting

Move `SyntaxVisualTransformation` off the per-keystroke path:

```kotlin
// In EditorViewModel (or a dedicated HighlightEngine)
private val _annotatedCode = MutableStateFlow<AnnotatedString>(AnnotatedString(""))
val annotatedCode: StateFlow<AnnotatedString> = _annotatedCode

// Launched once; debounces 80 ms; runs on Default dispatcher
init {
    viewModelScope.launch {
        codeText
            .debounce(80)
            .collect { text ->
                val highlighted = withContext(Dispatchers.Default) {
                    MultiLanguageSyntaxHighlighter.highlight(text, language, decorations)
                }
                _annotatedCode.value = highlighted
            }
    }
}
```

`EditorScreen` observes `annotatedCode` and passes it to a **`StaticTransformation`**
(just wraps the pre-built `AnnotatedString` — no work on the main thread):

```kotlin
val annotated by viewModel.annotatedCode.collectAsState()
BasicTextField(
    value = textFieldValue,
    visualTransformation = StaticTransformation(annotated),
    ...
)
```

The **current-line highlight** and **bracket match** decorations (which change
on every caret move and must feel live) are computed *separately* on the main
thread but are cheap (O(1) range lookups) — kept out of the debounced path.

### 2.2 Narrow `remember` keys

```kotlin
// BEFORE (broad — recomputes on every keystroke):
val tabViews = remember(openTabs, activeTabPath, codeText, isDirty) { ... }

// AFTER (narrow — recomputes only when tabs actually change):
val tabViews = remember(openTabs, activeTabPath) { ... }
// isDirty and codeText are only needed for the dirty-dot indicator
// on the active tab — handle that with a derivedStateOf inside the tab view.

// BEFORE:
val completionItems = remember(codeText, language) { ... }

// AFTER (debounced in the VM, not recomputed in the composable):
val completionItems by viewModel.completionItems.collectAsState()
// EditorViewModel already debounces completionItems — just remove
// the inline remember() in EditorScreen.
```

### 2.3 Let `BasicTextField` own its scroll

Replace the double-scroll wrapper with `BasicTextField`'s own scroll support:

```kotlin
// BEFORE:
Row(Modifier.verticalScroll(scrollState).horizontalScroll(hScrollState)) {
    BasicTextField(...)
}

// AFTER:
val textFieldScrollState = rememberScrollState()
BasicTextField(
    modifier = Modifier.fillMaxWidth(),
    scrollState = textFieldScrollState,   // single scroll, owned by the field
    ...
)
// Horizontal scroll: wrap in horizontalScroll only if the field does not
// support it natively at the resolved BOM (check during implementation).
// Caret follow: use the field's built-in bringIntoView instead of the manual
// getCursorRect + scrollState.scrollTo pattern.
```

> **Open question (resolve in §7 Research notes):** The `scrollState` parameter
> on `BasicTextField` is available from Compose Foundation 1.6+. Horizontal
> scroll via `scrollState` may not be supported for multi-line fields in the
> current BOM. If horizontal scroll is unsupported natively, keep one
> `horizontalScroll` wrapper but remove the vertical one (the bigger source
> of jank), and track the gap as a known limitation.

### 2.4 Gutter cache

```kotlin
// BEFORE (rebuilt every keystroke):
val gutterText = (1..lineCount).joinToString("\n")

// AFTER (only rebuilt when lineCount actually changes):
val gutterText = remember(lineCount) { (1..lineCount).joinToString("\n") }
```

This is a one-line fix but contributes to the per-keystroke allocation storm.

### 2.5 Baseline profile (optional — low effort, measurable gain)

Add a `baselineProfile { }` block to `app/build.gradle.kts` (Gradle plugin
`androidx.baselineprofile`). The profile is generated once from a Macrobenchmark
run and ships in the APK. Compose reports ~30% scroll improvement with a
baseline profile. This is a build-config change only — no Kotlin code.

> **Note:** baseline profile generation requires a connected device (the
> Macrobenchmark test runs on-device). This can be deferred if the other fixes
> (2.1–2.4) already resolve the owner's "stuck" report. Mark as optional.

---

## 3. Implementation steps

1. **Audit `EditorScreen.kt`** — list every `remember(codeText, ...)` block and
   every call to `SyntaxVisualTransformation`. Record in §7.
2. **Move highlight to `EditorViewModel`** (§2.1) — add `annotatedCode: StateFlow`,
   `debounce(80)` + `Dispatchers.Default`, `StaticTransformation` class.
3. **Narrow `remember` keys** (§2.2) — fix `tabViews` and `completionItems`.
4. **Fix scroll** (§2.3) — remove double-scroll wrapper; use `BasicTextField`
   scroll param or the single `horizontalScroll` wrapper (see §7 research).
5. **Cache gutter** (§2.4) — one-line `remember(lineCount)`.
6. **Write host unit tests:**
   - `StaticTransformation` returns the pre-built `AnnotatedString` unchanged.
   - Highlight debounce is tested via a fake `TestCoroutineScheduler` (verify
     the 80 ms window collapses multiple emits into one highlight call).
7. Commit and push; watch CI.

---

## 4. Exit condition & device recipe

**CI gate:** `Build APK` green. No test regressions (Phase 9 editor tests,
Phase 11 output tests, Phase 12 completion tests must all pass).

**Device recipe (owner):**

```text
1. Open a file with ~500+ lines of C code.
2. Type continuously for 5 seconds.
   EXPECT: no dropped frames, no "stuck" lag — typing feels as smooth as
   a stock Notes app.
3. Fling-scroll the file.
   EXPECT: smooth scroll at ~60 fps; no stutter at the top/bottom.
4. Pinch-zoom the font.
   EXPECT: font scales smoothly; no frame drop during the gesture.
5. Place the caret at line 1; then at the last line.
   EXPECT: caret is always visible above the keyboard (A.3 prerequisite —
   partial if A.3 is not yet done).
6. Existing features still work: bracket highlight, find/replace, undo/redo,
   diagnostics tap, autocomplete popup.
PASS = steps 1–4 feel smooth; step 6 has zero regression.
```

---

## 5. Non-goals & invariants

- **Not in A.1:** IME-anchored keys strip (→ A.2); inset handling (→ A.3).
- The highlight debounce does **not** affect the diagnostics underlines or
  bracket-match highlight — those are in a separate `decorations` flow that
  updates on caret move (fast, cheap).
- No change to `SyntaxVisualTransformation`'s *output* — only its *trigger*
  moves off the keystroke path. The highlighting rules are unchanged.
- All Phase 9 editor invariants (undo/redo stacks, tab dirty state, find/replace)
  are unaffected.

---

## 6. Design decisions

- **D1 — 80 ms debounce, not per-frame:** 80 ms is below the "noticeable lag"
  threshold for visual changes (150 ms is the typical perception threshold) but
  high enough to collapse rapid key events into one highlight pass. Tune if the
  owner reports that the highlight "lags" visually.
- **D2 — `StaticTransformation` wrapper, not raw `AnnotatedString`:** `BasicTextField`
  requires a `VisualTransformation`; wrapping the pre-built string in a trivial
  transformation is the cleanest adapter.
- **D3 — keep current-line highlight and bracket match on the main thread:**
  these are O(1) range operations; the user expects them to respond to caret
  moves instantly (not after an 80 ms debounce). Only the O(n) full-file
  tokeniser is moved off-thread.
- **D4 — baseline profile is optional:** the other three fixes should resolve
  the "stuck" report. If the owner reports residual lag on a specific device,
  the baseline profile is the next tool.

---

## 7. Research notes (fill in before implementing)

> **TODO for the implementer:**
> - Record the Compose BOM version from `app/build.gradle.kts`.
> - Confirm whether `BasicTextField(scrollState = ...)` is available at that BOM
>   and whether it supports horizontal scroll (or only vertical).
> - List every `remember(codeText, ...)` block found in `EditorScreen.kt`.
> - Confirm whether `EditorViewModel` already has a `completionItems` StateFlow
>   (Phase 12 may have moved it there; if so, the `remember()` in EditorScreen
>   is the only thing to remove).
> - Check the `windowSoftInputMode` setting in `AndroidManifest.xml` for
>   `MainActivity` — needed to confirm the IME resize mode (used in A.3).

---

## 7. Research notes (filled in 2026-09-03 — implementation round)

**Compose BOM:** `composeBom = "2024.09.00"` (`gradle/libs.versions.toml:12`),
i.e. Compose Foundation **1.7.x**. Kotlin `2.2.10`, coroutines `1.10.2`.

**`BasicTextField(scrollState = …)`:** at 1.7 the `scrollState` parameter exists
**only on the `TextFieldState` overload** — the `TextFieldValue`/`onValueChange`
overload CodeC uses (value, onValueChange, …, cursorBrush, decorationBox) has no
`scrollState` parameter at all. Migrating to `TextFieldState` would mean
rewriting the whole editing pipeline (undo manager, auto-indent, tab buffers,
find/replace, quick-fixes all speak `TextFieldValue`), which is far outside a
smoothness round and would touch every Phase 9/11/12 invariant.
**Decision: the double-scroll wrapper is KEPT** (`verticalScroll` +
conditional `horizontalScroll`), and §2.3 is deferred as a known limitation.
The three allocation-level fixes below are the ones that ship.

**`remember(codeText, …)` audit in `EditorScreen.kt` (before the fix):**

| Block | Old key set | Action |
|---|---|---|
| `completionItems` | `remember(codeText, language)` | → `derivedStateOf` keyed on `language` (recomputes only when a reader actually reads it) |
| `completionDismissed` | `remember(codeText.text)` | kept — it is a per-edit reset, that IS the intent |
| `tabViews` | `remember(openTabs, activeTabPath, codeText, isDirty)` | `codeText` key **dropped**; the buffer was only read for the active tab, whose truth is already `isDirty` |
| `transformation` | `remember(currentEditorTheme, decorations, language)` | now also keyed on the debounced `highlighted` snapshot |
| gutter `lineNumbers` | *(not remembered at all)* | → `remember(lineCount)` |

**`completionItems` in the VM:** `EditorViewModel` does **not** expose a
`completionItems` StateFlow (Phase 12 left it in the composable). Rather than
add another debounced flow, the composable now uses `derivedStateOf`, which is
the right tool here: the completion popup is the only reader, and
`derivedStateOf` skips the work entirely on frames where nothing reads it.

**`windowSoftInputMode`:** `AndroidManifest.xml:64` already sets
`adjustResize` on `MainActivity`; `MainActivity.onCreate` already calls
`enableEdgeToEdge()` (which is `WindowCompat.setDecorFitsSystemWindows(window,
false)` plus the transparent system bars). So A.3's step 1 was a no-op — see
`PART_22_3_INSETS.md` §7.

**Baseline profile (§2.5):** still optional and **not done** — it needs a
Macrobenchmark run on a connected device, which this sandbox cannot do.

---

## 8. What shipped

1. **Debounced off-thread highlight (§2.1, adapted).** New pure data class
   `HighlightedCode` (in `MultiLanguageSyntaxHighlighter.kt`): a tokenized
   `AnnotatedString` tagged with the exact `(text, theme, language)` it was
   built from, plus `matches(...)`. `EditorViewModel` runs one long-lived
   collector — `combine(codeText, highlightContext)` →
   `distinctUntilChanged()` → `debounce(80 ms)` →
   `withContext(Dispatchers.Default) { HighlightedCode.of(...) }` — and
   publishes it as `highlighted: StateFlow<HighlightedCode?>`. A result whose
   text the buffer already moved past is discarded.

   Rather than the spec's `StaticTransformation`, `SyntaxVisualTransformation`
   gained a fourth `cached: HighlightedCode?` parameter: it reuses the snapshot
   when it matches and **falls back to inline tokenizing when it is stale**.
   This is strictly safer than a static wrapper — during the 80 ms window the
   text is still colored correctly instead of showing colors for the previous
   buffer, and the cache is a pure optimization with no correctness role.
2. **Decoration fast path.** `EditorDecorations.isEmpty()`; when there is no
   current-line tint, bracket match, find match or diagnostic, the
   transformation returns the cached `AnnotatedString` directly with **zero**
   `buildAnnotatedString` allocation per frame.
3. **Narrowed keys (§2.2).** `tabViews` lost its `codeText` key;
   `completionItems` became a `derivedStateOf`.
4. **Gutter cache (§2.4).** `remember(lineCount)` around the
   `(1..lineCount).joinToString("\n")`.
5. **Scroll model (§2.3): deferred** — see the BOM research note above.

**Tests:** `app/src/test/java/com/codeci/ide/EditorHighlightCacheTest.kt`
(8 host tests) — snapshot match/stale on text, theme and language; a cached
render equals the inline render span-for-span (with and without decorations);
a stale cache never leaks the wrong colors; offsets stay identity-mapped;
`EditorDecorations.isEmpty()` across all five layers.

**Still open / device-gated:** the horizontal+vertical double scroll wrapper
(needs the `TextFieldState` migration), and the baseline profile.

---

## 9. Device round 1 — owner feedback (2026-09-03)

> Owner: *"Better than before but still when i open keyboard it's lacks also
> typing not good and why terminal is showing its blocks the editor it's a
> phone app so space matters most"*

Three separate causes; the first two are the residual jank, the third is the
space complaint. All fixed in `1417642` (CI `33720029914`).

### D5 — `filter()` runs per LAYOUT, not per edit (the keyboard-open lag)

This is the one §8 missed. `VisualTransformation.filter()` is called on every
**layout** pass of the text field, not only when the text changes — and the
soft keyboard's slide animation relayouts the field on **every frame** of that
animation. So opening the keyboard on a large file ran the full decorated
`buildAnnotatedString` (an O(n) copy of the whole buffer) 30–60 times in a row,
while the IME animation was already competing for the frame budget. The Phase
22.1 debounce could not help: the *text* never changed, so the cache always
matched — the cost was the per-frame **decoration re-application**, not
tokenizing.

Fix: a single-entry memo inside `SyntaxVisualTransformation`, keyed on the
buffer text. The instance is already `remember`ed on
`(theme, decorations, language, cached)`, so a surviving memo can only mean
"same inputs" — the text key is belt-and-braces. Every frame of the keyboard
animation after the first now returns the identical `TransformedText`.

### D6 — the gutter recomposed on every keystroke

§8 cached the gutter *string* (`remember(lineCount)`), but the enclosing scope
still read `codeText.text` to compute `lineCount`, so **the composable scope
itself** was invalidated by every keystroke — the `remember` only saved the
`joinToString`, not the recomposition, the `Text` measure or the `drawBehind`.
Now the count comes from a `derivedStateOf`: the cheap newline scan still runs,
but the scope is only invalidated when the count actually **changes**, so
typing within a line does not touch the gutter at all.

### D7 — vertical space on a phone

The "terminal" the owner sees is the **collapsed Output Panel strip**, which
was rendered unconditionally at a fixed `64.dp` — reserved from the moment the
editor opened, before anything had ever been run, showing nothing. Fixes:

- New pure `OutputRunState.hasContent()` (`phase != IDLE || lines.isNotEmpty()`)
  gates the collapsed strip. A fresh session shows no strip; the strip appears
  the instant RUN ▶ flips the phase to `BUILDING` (so a run is never invisible)
  and `clearOutput()` — which assigns exactly `OutputRunState(phase = IDLE)` —
  hides it again.
- With the keyboard up, the collapsed strip **and** the status bar are hidden.
  Ln/Col is a glance-value readout, not something consulted mid-keystroke, and
  between them they were ~104dp of chrome held above the keyboard on the most
  space-starved screen the app has.
- **The EXPANDED panel is deliberately untouched** by the IME rule — if the
  user opened it on purpose (e.g. to answer an interactive prompt, Phase 23's
  whole premise) it must stay visible while they type.

**Tests:** `OutputPanelVisibilityTest` ×5 (fresh session hidden; BUILDING and
RUNNING visible; DONE/FAILED/CANCELLED stay; lines alone suffice; the exact
`clearOutput()` value hides it) + 2 memo cases in `EditorHighlightCacheTest`
(`assertSame` across repeated `filter` calls; the memo never serves a result
for different text).

**Still not addressed** (unchanged from §7): the double-scroll wrapper, which
needs the `TextFieldState` migration, and the baseline profile. If typing still
feels heavy after this round, the scroll wrapper is the prime remaining
suspect and that migration becomes the next piece of work.
