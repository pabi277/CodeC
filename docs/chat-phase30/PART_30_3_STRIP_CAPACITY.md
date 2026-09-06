# CodeC Phase 30.3 — Strip capacity

**Status:** 🚧 IMPLEMENTED (2026-09-06) · **Cost:** `[client-only]` · **Effort:** S
· **Depends on:** 27.2, 30.1

---

## 1. Design

`CodeCompletionEngine.MAX_ITEMS = 8` is why the list feels empty.
The **strip** may still show top N chips (thumb); the **engine** must
return a longer ranked list. Ghost = rank 0. ⌄ more = the rest (already
27.2). Horizontal scroll on chips is required.

Raise/remove the engine cap; keep a safety cap (e.g. 50) for LSP-later.
Host tests: prefix `i` in a C file with snippets loaded → more than 8
candidates available to policy.

## 2. Exit condition

```text
(Device)
1. Type a short prefix in Python — chips scroll; ⌄ shows more than 8.
2. Ghost still only the top-1; Enter still newline.
PASS = both.
```

---

## 3. IMPLEMENTATION RECORD (2026-09-06, owner: "Start phase 30")

### 3.1 What shipped

**The caps.** `MAX_ITEMS = 8` is gone; the engine now returns a ranked list up
to a **safety cap of 50** (the plan's "e.g. 50", kept deliberately so a future
LSP has a bounded surface to hand the same pipeline), with per-source bounds so
one source cannot eat the list:

| Bound | before | after | Why |
|---|---|---|---|
| `MAX_ITEMS` | 8 | **50** | The whole ranked list the policy/strip/panel see. |
| `MAX_SNIPPET_ITEMS` | — (implicit in the 8) | **40** | A 367-item JavaScript pack must not fill all 50 slots; the everyday match is in the first dozen anyway (`rankSnippets` puts it there). |
| `MAX_IDENTIFIER_ITEMS` | 3 | **6** | Buffer symbols stay a *lower-priority* source (30.1) but six near the caret is more useful than three. |
| `MAX_KEYWORD_ITEMS` | 3 | **6** | Same. |
| `SuggestionStripModel.MAX_CHIPS` | 8 | **8 (unchanged)** | The thumb law: a chip row is not a list. |

**Order is the feature.** Tier order is exactly what it was pre-30 — Emmet
(rank 0, new in 30.2) → snippets → buffer identifiers → keywords →
`distinctBy { label }` → `take(MAX_ITEMS)` — so nothing that used to appear
first appears later now. Inside the snippet tier, `rankSnippets` (30.1) sorts
direct-prefix matches before word-inside-label matches and shorter labels
before longer ones, stable within a tier, which is what makes a 40-slot tier
readable instead of alphabetical noise.

**Strip / ghost / panel split (unchanged surfaces, longer list behind them).**
The strip shows the top `MAX_CHIPS` = 8 chips through
`SuggestionStripModel.buildStripModel` (engine order, ghost item pinned first,
recency boost — all 27.2), and `SuggestionStrip` already scrolls horizontally,
so the row is swipeable as well as capped. **⌄ more = the rest**: it emits
`requestCompletionPanel()` (gated by `master && panel`, 27.3) and sora's native
panel browses the same engine list — now up to 50 rows — without a second
engine run. **Ghost = rank 0**, unchanged: `GhostCompletion.compute` paints the
first candidate whose insert text aligns with the line tail (G1), ghosts its
first line only (G6), and never commits on typing (G2).

**One narrow exception to 27.2's S1 rule (30.2 fallout).** S1 says "a single
candidate stays in key mode because the ghost covers it". An Emmet expansion
breaks the premise — `<ul>…` never starts with `ul>li*3`, so the ghost is
`Hidden` and a lone expansion would be invisible. `StripContext.stripContextFor`
therefore chips a single candidate **iff** `items.size == 1 &&
items[0].detail == Emmet.DETAIL && ghost !is Visible`; every other
single-candidate case is byte-for-byte the 27.2 behaviour, and both halves are
pinned by test (`StripContextTest`, `CompletionCapacityTest`).

**Accept math.** `CompletionItem.replaceLength` / `caretOffset` (both nullable,
both `null` for keywords, identifiers and built-in snippets = exactly the 27.x
shape). `EditorViewModel.acceptCompletionItem` computes
`start = replaceLength?.let { (caret - it).coerceIn(0, caret) } ?: identifierStart`
and parks at `caretOffset ?: insert.length`; `GhostCompletion.accept(FULL)`
parks at `prefixStart + caretOffset` when the item declares one. **WORD and
LINE partial accepts deliberately ignore `caretOffset`** — they commit a piece
of the suffix, and teleporting the caret into a snippet body the user has not
accepted yet would be a lie.

### 3.2 Host tests

`CompletionCapacityTest` (19, Robolectric + real packs) is the host mirror of
all three parts' exit conditions; the 30.3 ones:

- **the plan's named host test** — prefix `i` in a C file with snippets loaded
  → **10 candidates** (8 snippets + 2 keywords) vs **7** from the old tables;
  ≤ `MAX_ITEMS`; > 4 snippet matches (old: 4 of 7); keywords still ride along;
  Python `i` → 9 (old 8).
- a short Python prefix → > 8 candidates, exactly `MAX_CHIPS` chips, more items
  than chips (⌄ has something to open), chip labels ≤ 18 chars, and
  `StripContext.Suggestions`.
- the ghost is still top-1: for `int mai` it paints the rank-0 pack item
  (`main`), its suffix has no newline (G6), FULL accept parks at the declared
  tabstop, and computing a ghost changed no text (G2).
- the safety cap and the per-source bounds hold on a 200-identifier buffer
  (≤ 50 total, ≤ 6 identifiers, ≤ 6 keywords, ≤ 40 snippets) while a
  direct-prefix identifier is still offered.
- the tier order is stable (no identifier before a snippet) for a mixed list.
- master off ⇒ `StripContext.Keys` + `everythingOff` + `!anyOn` (the exact
  predicates `EditorViewModel` short-circuits on) with 10 candidates in hand.

`StripContextTest` +2: the lone-Emmet chip (and the ghost-covered case that
goes back to key mode), and a 50-item list that still yields exactly
`MAX_CHIPS` chips in both `buildStripModel` and `stripContextFor`.
`GhostCompletionTest` +4: caretOffset parking on FULL, WORD/LINE ignoring it,
an out-of-range `caretOffset` falling back to the insert's end, and an item
without one keeping the Phase 27 parking. `CodeCompletionTest`'s existing
`completions are capped` assertion now pins the 50 cap (it reads the constant)
and its `@Before` keeps the file in the built-in-table world.

**Perf (host JVM, no device claim).** The whole `completions()` call on a
**4 000-line / ~100 KB** buffer with the identifier window at ±20 000 chars:
**1.8 ms warm, 2.3–4.5 ms cold-ish** for a prefix that hits a 84-entry pack,
Emmet probing and the windowed scan. The 22.6 window law is untouched
(`SCAN_WINDOW` 20 000, pinned by its two long-buffer tests), the 27.3 debounce
(120/240 ms) and the off-main recompute are untouched, and pack parsing happens
once per language behind `SnippetLibrary`'s cache + background `warmUp`. Budget
for comparison: 16.7 ms per keystroke (25.1 law).

### 3.3 Deviations from the spec (recorded)

- **50, not "removed".** The plan says "raise/remove the engine cap; keep a
  safety cap (e.g. 50)". Kept at 50 with the per-source bounds above; a future
  LSP can raise `MAX_ITEMS` in one place.
- **The snippet tier is capped at 40, below the 50 total**, so identifiers and
  keywords always survive a huge pack (JavaScript ships 367). Strict
  "completeness" would mean 50 snippets and nothing else; the strip only shows
  8 anyway, and ⌄ more is where a long tail is browsable.
- **Chips stay at 8.** The plan allows "top N"; N is `MAX_CHIPS` and did not
  move — the thumb is the constraint, and the horizontal scroll (already
  shipped in 27.2) plus ⌄ more carry the rest.
- **sora's native panel parks the caret at the insert's end**, not at the
  tabstop: `SimpleCompletionItem` has no caret field. Strip and ghost honour
  `caretOffset`. Same deviation as 30.1 §3.3, recorded once here because it is
  a 30.3-surface symptom.

### 3.4 Exit condition status

```text
(Device) — PENDING (owner round; card: docs/TROUBLESHOOTING.md §13)
1. Type a short prefix in Python — chips scroll; ⌄ shows more than 8.
2. Ghost still only the top-1; Enter still newline.
PASS = both.
```

**CI:** `Build APK` run `34034889209` GREEN on tip `641f6e8` (4m34s —
`:app:assembleDebug` + `:app:testDebugUnitTest` + `:app:lintDebug` through the
gradle-bootstrap shim, plus `:bench:assembleRelease :bench:testDebugUnitTest`);
first try, no for-cause round. Artifact `CodeC-IDE` +116 572 B (+0.11 MiB / +0.117 MB)
vs `main`.

Host mirror (green): item 1 — Python `i` → 9 candidates, 8 chips, ⌄ opens the
panel over the same list; the chip row is horizontally scrollable (27.2 code
path unchanged). *(Amended by PART_30_1 §3.5: with the built-in tables riding
as a deduped tail, Python `i` and C `i` are now **13** candidates each — 8
chips + 5 rows behind ⌄ — still one `distinctBy{label}.take(50)` away from the
cap, and the tier order is untouched.)* Item 2 — the ghost paints only `items.first()`; Enter is
`CompletionAction.NEWLINE` on every surface, asserted by the untouched
`CompletionPolicyTest` matrix (12 tests) — Phase 30 never touches
`CompletionPolicy.kt`.
