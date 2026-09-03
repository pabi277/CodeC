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

## 7. Research notes — ✅ RESOLVED

Every question in this section was answered during implementation. The answers
are in **§7 (filled in)** immediately below, and the device-round findings that
superseded several of them are in §8–§13. Summary of the original checklist:

| Question | Answer |
|---|---|
| Compose BOM version | `2024.09.00` (Foundation 1.7.x), Kotlin `2.2.10` |
| Is `BasicTextField(scrollState = …)` available? | **Only on the `TextFieldState` overload**, not the `TextFieldValue` one we use. Scroll rewrite deferred — see §2.3 and the ceiling note in §12. |
| Every `remember(codeText, …)` in `EditorScreen.kt` | Enumerated in §7 (filled in); all narrowed or removed across rounds 1–6. |
| Does `EditorViewModel` already expose `completionItems`? | **No** — it lived in `EditorScreen`. Moved off the main thread in Phase 22.6 (§11, D11). |
| `windowSoftInputMode` | `adjustResize` (`AndroidManifest.xml:64`); kept deliberately — see `PART_22_3_INSETS.md` §8. |

---

### 7a. Research notes as gathered (2026-09-03 — implementation round)

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

---

## 10. Device round 2 — owner feedback (2026-09-03)

> Owner: *"Most of the problems solved but still in a long file it lags. And
> the quick keys make pare in single key like (),[],<>,{} etc"*

Fixed in `a751cf4` (CI `33722090650`).

### D8 — the tab stash made every keystroke cost O(file)

**This was the long-file lag, and it was never in the rendering layer at all**
— which is why rounds 1 and 2 (both render-side) improved things without
curing it.

`EditorViewModel.updateCode` called `stashActiveTabBuffer(next)` on **every
character**. That function does:

```kotlin
_openTabs.value = _openTabs.value.map { … it.copy(buffer = buffer) … }
```

Three compounding costs per keystroke, all scaling with the **file**, not the
edit:
1. the whole `List<EditorTab>` is rebuilt and a new `EditorTab` allocated;
2. `EditorTab` holds the entire `TextFieldValue`, so the copy carries the full
   buffer reference through a fresh data-class instance;
3. `_openTabs` is a `StateFlow`, so a **new list identity** is published on
   every character — waking every collector (the tab strip, the drawer's dirty
   marks) and invalidating their recomposition scopes, on top of the edit
   itself.

The fix is to stop doing it. The active tab's truth already lives in
`_codeText`; the stash exists to hand the buffer **to the tab record when you
leave it**, which is exactly what `stashActiveTabBuffer`'s own KDoc says
("the ACTIVE tab's live buffer lives in the ViewModel and is stashed into this
record on switch/close"). The per-keystroke call was contradicting the
documented design. The stash still runs at all seven real boundaries (tab
switch, open, close, close-all, save-all, context switch, undo/redo — the
latter are discrete, so they stay).

Audited every reader of `tab.buffer` for staleness; two touch the active tab
and both were already correct (`tabViews` and the tab-close dirty check use
the VM's `isDirty` for the active path), and `trimTabs` only ever considers
non-active tabs. Comments added at all three so the invariant is not
re-broken.

### D9 — the caret readout copied the whole prefix per keystroke

`refreshDecorationsNow` computed the line number as:

```kotlin
val line = current.text.take(cursor).count { it == '\n' } + 1
```

`take(cursor)` **allocates a copy of the entire prefix** purely to count
newlines in it. With the caret near the end of a long file that is a full-file
copy for every character typed — then `lineStartOffset` rescanned the text
again for the line start. Replaced with one in-place loop that produces the
line number and the line start together, no allocation.

`EditorCursorMathTest` pins the arithmetic against the original implementation
as an oracle, probing every line start, midpoint and end of a 500-line file.

### D10 — paired bracket keys

New `EditorKey.Pair(open, close)`: `()`, `{}`, `[]`, `<>`, `""`, `''` and JS
template backticks are now single caps. Empty caret → both halves inserted
with the caret **between** them; non-empty selection → the pair **surrounds**
the selection and the selection is preserved inside it (standard editor
surround). The row is also shorter than before — six pair caps replace ten
single-character caps, leaving more room on a phone.

The HTML `</>` tail was left as-is (it is a fragment marker, not a pair).

---

## 11. Device round 3 — owner feedback (2026-09-03)

> Owner: *"Still lags and not even less lag then before. Also can you add
> different quick suggestions accordingly to the language we are using"*

Fixed in `1b06dec` + `ba81bf0` (CI `33724364238`).

### D11 — the autocomplete scan, and a correction to the Phase 22.1 record

**This is the lag.** `CodeCompletionEngine.completions()` ran **synchronously,
on the main thread, for every keystroke**, and inside it `identifiers()`:

```kotlin
Regex("\\b[A-Za-z_][A-Za-z0-9_]*\\b").findAll(text)   // the WHOLE buffer
    .map { … }.filter { … }.distinct().toList().sorted()
```

So every character typed **compiled a fresh `Regex` and swept the entire
file**, then allocated a distinct + sorted list — in the frame, before the
keystroke could be drawn. On a long file that dwarfs everything rounds 1–3
touched, which is exactly why those rounds "didn't even make it less".

**I have to correct my own §8 claim.** Phase 22.1 said it fixed this by
converting the `remember(codeText, language)` into a `derivedStateOf`. That
was wrong, and it is worth being precise about why, because the same mistake
is easy to repeat:

> `derivedStateOf` only helps when the derived **value** changes less often
> than the state it reads. Here the derivation reads `codeText` (changes every
> keystroke) and the popup reads the result **in the same frame**, so it was
> invalidated and recomputed on every keystroke regardless. It looked like a
> fix in the diff and did nothing at runtime.

The actual fix has three parts:
1. **Off the main thread + debounced** — `produceState` keyed on the text,
   selection and language, `delay(120 ms)` then `withContext(Dispatchers.Default)`.
   Type fast and the scan never runs at all.
2. **Compile the `Regex` once** (`IDENTIFIER`), instead of per keystroke.
3. **Window the scan** — `SCAN_WINDOW` (20 000 chars) either side of the
   caret, with an early break once enough distinct matches are found. Cost is
   now bounded by the window, not the file. Identifiers you want to reuse are
   near where you are typing; both edges are covered by tests.

I also corrected the overclaiming comment on the **gutter** `derivedStateOf`
(§D6). It does not stop the newline count from running per keystroke — it
stops the gutter *scope and `Text`* from recomposing when the count is
unchanged. That is a genuine but much smaller win, and the comment now says
so rather than implying the work is skipped.

### D12 — language-aware suggestions (owner request)

`snippets()` covered Python, C/C++, JavaScript and Shell; **HTML/CSS and
Markdown fell through to `else -> emptyList()`**, so on a `.html` or `.md`
file the popup never appeared at all — despite HTML being a first-class CodeC
target (Web Preview runs it directly). Added:

- **HTML/CSS** — full `<!DOCTYPE html>` skeleton (charset + viewport),
  `<div>`, `<a>`, `<img>`, `<ul>/<li>`, `<script>`, `<link rel=stylesheet>`,
  `<style>`, a bare CSS rule, `@media`, `display: flex;`, plus 18 element
  trigger words so the snippet list also opens after `<div ` etc.
- **Markdown** — headings, bold/italic, link, image, bullet and numbered
  lists, blockquote, fenced code block, table skeleton.
- **Shell** — the CodeC-correct shebang and `read -r`.

### D13 — case-insensitive snippet matching (found by CI)

Writing the D12 tests surfaced a real UX bug: `snippetMatches` used
case-sensitive `startsWith`, so lowercase `doc` never matched
`<!DOCTYPE html>` and `head` never matched `# Heading`. CI failed on my two
new tests — **the tests were right and the product was wrong**. Matching is
now case-insensitive (you type lowercase on a phone; guessing a snippet's
capitalization defeats a prefix search), and the word-split `Regex` is a
compiled constant instead of being rebuilt per candidate label.

**Tests:** 7 new in `CodeCompletionTest` — HTML skeleton/element, CSS
`@media`, Markdown heading/table, the "used to return nothing at all"
regression guard, case-insensitivity both ways, and both edges of the scan
window.

### If it STILL lags

Everything cheap is now done. The remaining structural suspect is the one
thing untouched since the phase began: `BasicTextField` wrapped in
`verticalScroll` + `horizontalScroll`, fighting the field's own scrolling.
That needs the `TextFieldValue` → `TextFieldState` migration (Compose BOM
2024.09.00 has `scrollState` only on the `TextFieldState` overload), which
touches the undo manager, auto-indent, tab buffers, find/replace and
quick-fixes. It is a real piece of work and should be its own round, with the
owner's go-ahead — not folded into a polish batch.

---

## 12. Device round 4 — the actual root cause (2026-09-03)

> Owner: *"Scroll is working better than before but still writing strucs.
> Analysis the real problem search online, open sorce projects for solutions
> don't just guess"*

Fair criticism. Rounds 1–3 were inference. This round is research, and it
found a cause none of the previous rounds could have reached by reading our
own code — because **the bottleneck is in Compose, not in CodeC.**

### Research notes (sources)

1. **JetBrains, `compose-multiplatform#4023` — "VisualTransformation freezes
   the UI when there are lots of span styles"**
   <https://github.com/JetBrains/compose-multiplatform/issues/4023>
   (moved to YouTrack `CMP-4023`). A `VisualTransformation` returning ~40 000
   span styles froze the UI for **6 seconds**. Maintainer
   (Alexander Maryanovsky) closed it **not planned**:
   > *"I don't think `TextField` was meant to be used with such a large amount
   > of text, much less styled text… You need a widget that will layout and
   > render the text in a lazy fashion; `TextField` and `Text` aren't lazy."*

   Crucially, the reporter noted the field does **not** freeze with the same
   text when the transformation is removed — even at 10× the line count. **The
   cost is the SPANS, not the characters.**
2. **`r/Kotlin` — "Compose cannot be used for large amount of text"**
   <https://www.reddit.com/r/Kotlin/comments/18fvj45/> — same conclusion, and
   the reason: real editors use ropes/skip-lists and render only the viewport;
   `TextField` does neither because it targets small UI inputs.
3. **`sunny-chung/bigtext`** <https://github.com/sunny-chung/bigtext> — a
   from-scratch replacement built precisely because of #4023. Its headline
   claim ("does not freeze when a 300 K text **styled with text
   transformation** is rendered") confirms styling is the axis, and its design
   rule is *incremental/windowed transformation, never whole-buffer*.
4. **`Qawaz/compose-code-editor`**, **`hossain-khan/android-compose-highlight`**
   — both avoid handing a big styled buffer to one field (separate renderer /
   off-thread highlight + caching).

### D14 — the measurement that made it obvious

Our tokenizer over a representative 500-line C file:

```
chars: 34 780   lines: 500   SPANS handed to BasicTextField: ~4 500
```

Every one of those spans was re-laid-out by `BasicTextField` on **every layout
pass**. That is the "writing strucs" — and it explains why **rounds 1–3 all
missed**:

| Round | What it fixed | Why the lag survived |
|---|---|---|
| 22.1 | tokenize off-thread, debounced | the tokenizer was never the cost |
| 22.4 | memoized `filter()` per layout | returned the same 4 500-span object faster |
| 22.5 | tab-stash + prefix-copy per keystroke | real wins, but not this |
| 22.6 | completion scan off-thread | real win, but not this |

Each round removed real work and each made things *somewhat* better — which is
exactly what the owner reported — while the dominant term went untouched.

### D15 — the fix: window the spans, not the text

`highlight()` and `tokenize()` now take `from`/`to` and emit token spans only
inside that window:

- **Scanning still starts at offset 0**, so multi-line constructs (block
  comments, multi-line strings) are classified with the same context as
  before. Only span **emission** is bounded, so colours inside the window are
  byte-identical to a full-file highlight — proven by a test.
- `HighlightedCode` carries its `from`/`to` and reports itself **stale when
  the caret leaves the window**, so scrolling re-colours.
- The window is `±20 000` chars around the caret — far more than a phone
  screen shows, so the user never scrolls into an uncoloured edge in normal
  use.
- The window is **quantized to `WINDOW/4`** in both the VM's flow key and the
  screen's `remember` key, so ordinary typing and short scrolls do **not**
  re-tokenize or discard the transformation memo.
- The **inline fallback** inside `SyntaxVisualTransformation` is windowed too
  — it was colouring the whole buffer on the frames before the debounced
  snapshot arrived, i.e. re-introducing the exact cost being removed.

The text handed to the field is always complete and offsets stay
identity-mapped, so selection, find/replace, diagnostics and quick-fixes are
untouched.

**Tests (5):** span count drops on a long file; text never truncated; colours
inside the window match a full-file highlight exactly; a snapshot goes stale
once the caret leaves its window; the window follows the caret.

### Honest limits of this fix

This bounds the **steady-state** cost. It does not make `BasicTextField`
lazy — per JetBrains it never will be. A file large enough that even the
*unstyled* text is expensive to lay out will still be slow, because that cost
is in Compose's text layout, not ours. The genuine ceiling-raiser is replacing
the field (the `bigtext` route, or a `TextFieldState` migration plus a
viewport-driven window), which is a much larger piece of work and should be
its own phase with the owner's go-ahead — not folded into a polish round.

---

## 13. Device round 5 — the window was too big to bite (2026-09-03)

> Owner: *"Still lags it's a 517 lines html code do as much as you can to
> fixed it"*

Fixed in `f718e10` (CI `33728499748`).

### D16 — Phase 22.7 never engaged for the owner's file

The diagnosis in §12 was right; the **constant was wrong**, and I picked it
without measuring the file the owner actually had.

A 517-line HTML file is about **25 000 characters**. `WINDOW` was `20_000`,
so the coloured window spanned ±20 000 = **40 000 characters — the entire
file**. Windowing was a no-op for exactly the case being reported.

Measured on a representative 517-line HTML fixture:

| Window | Spans handed to `BasicTextField` |
|---|---|
| ±20 000 (Phase 22.7) | **1 753** — the whole file |
| ±8 000 | 1 092 |
| ±4 000 | 545 |
| **±3 000 (now)** | **409** |

`WINDOW` is now `3_000`. A phone shows roughly 40 lines ≈ 2 000 characters, so
±3 000 is still more than a screenful in each direction. A unit test now fails
if anyone raises it above 5 000.

**HTML is the worst case for span density** — every tag name, every attribute
string and every numeric literal is its own token, so a markup file generates
far more spans per line than C or Python. Worth remembering when judging
"how big is a big file" for this editor: it is spans, not lines.

### D17 — the fallback still swept the whole file, on the main thread

`tokenize()` deliberately scanned from offset 0 so multi-line constructs kept
their context. That is fine on the ViewModel's debounced background pass — but
the **inline fallback** in `SyntaxVisualTransformation.filter()` runs on the
**main thread**, and it runs whenever the debounced snapshot is stale, which
is *by definition* every keystroke. So an O(file) regex sweep sat on the
typing path regardless of the window.

Scanning now starts at a **safe anchor**: `LOOKBEHIND` (4 000) characters
before the window, snapped forward to just after a blank line if one exists in
that span. A blank line cannot appear inside a string in any grammar here, so
it is a reliable resynchronisation point; near the top of a file we still scan
from 0, so small files stay exact. Total scan cost is now bounded by
`LOOKBEHIND + 2*WINDOW` ≈ 10 000 characters instead of by the file.

### D18 — the memo ignored the caret

`filter()`'s single-entry memo was keyed on the text only. Once the window
became caret-dependent that was a **correctness bug**, not just a perf one: it
could keep serving a snapshot coloured for a window the user had scrolled away
from, showing visibly uncoloured text. Now keyed on `(text, caret)`.

**Tests (6):** a 517-line HTML fixture is provably windowed (>2× fewer spans),
a guard that `WINDOW` stays small, and four `safeAnchor` cases — top of file,
exactly at `LOOKBEHIND`, deep in a buffer, and blank-line resync.

### Where this leaves us

Per-keystroke work on a 517-line HTML file is now bounded by a constant
(~400 spans, ~10 000 chars scanned) rather than by the file. That is as far as
this architecture goes. If it is *still* not smooth, the remaining cost is
`BasicTextField` laying out the text itself, which we cannot reach from here —
see the §12 ceiling note. The next real step is replacing the field
(`bigtext`-style, or `TextFieldState` + viewport-driven windowing), which is
its own phase and needs the owner's go-ahead.
