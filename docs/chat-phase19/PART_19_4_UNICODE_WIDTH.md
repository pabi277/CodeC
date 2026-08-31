# CodeC Phase 19.4 — Unicode column widths (CJK / emoji / Indic clusters)

**Status:** IMPLEMENTED (2026-08-31, `arena/01a056aa-codec`) — host tests
written, CI pending · **Cost:** `[client-only]`
· **Depends on:** none (landed with 19.1 — both rewrite `TerminalBuffer`)
· **Origin:** the owner's Phase 19 instruction to *"find other things
Termux does better than CodeC terminal and fix it"*. This was gap #1 of the
parity audit: every code point took exactly one cell, so CJK text and emoji
overlapped their neighbours (a second, non-ASCII source of the "letters
overlapping" report), and Bengali/Devanagari vowel signs smeared across
separate cells instead of forming one cluster.

---

## 1. The parity gaps found (audit, 2026-08-31)

| Termux behavior | CodeC before 19.4 | Verdict |
|---|---|---|
| East-Asian Wide/Fullwidth glyphs (CJK, Hangul, Kana, fullwidth punctuation, wide emoji) occupy **two** columns | 1 column → glyph painted over the next cell | **fixed in 19.4** |
| Combining marks (Mn/Me) and Indic spacing vowel signs (Mc) combine with the base character into **one cell/cluster** | each mark took its own cell → smeared output | **fixed in 19.4** |
| Wide glyphs never split at a wrap/resize boundary | n/a (no wide glyphs) | **fixed with 19.1's reflow** |
| Copy/selection returns the *human* text (joined pairs, combining marks intact) | raw per-cell chars | **fixed in 19.4** |
| OSC 8 hyperlinks, mouse reporting, DA queries, OSC 52 | missing | fixed in **19.5** |

## 2. Research notes (2026-08-31)

* **Widths come from UAX #11** ("East Asian Width",
  https://unicode.org/reports/tr11/): property **W** or **F** → 2 columns
  (the CJK blocks, Hangul syllables, Kana, fullwidth forms U+FF00–FF60,
  and the wide emoji ranges). Ambiguous (Greek, Cyrillic, box-drawing
  U+2500…) stays **1** in a non-CJK context — verified against the
  classic `wcwidth` semantics (widths 0 / 1 / 2 for combining / narrow /
  wide).
* **Zero width** = the combining categories — Mn (nonspacing), Me
  (enclosing), **Mc (spacing combining marks — the Indic vowel signs like
  Bengali া U+09BE, ি U+09BF, Devanagari ा U+093E)** plus the Cf
  zero-width characters (ZWJ/ZWNJ, bidi marks, variation selectors
  U+FE00–FE0F, conjoining Hangul jamo medials/finals U+1160–11FF) and the
  variation-selector supplement U+E0100–E01EF. Treating Mc as 0 makes one
  syllable ≈ one cell, which is how Indic text is laid out on fixed grids.
* Astral code points (emoji U+1F600…) occupy 2 columns but are TWO UTF-16
  units — a "one char per column" text model breaks unless the glyph is
  parked separately (see D2).

## 3. Design decisions

* **D1 — lead/continuation cells.** A wide glyph is stored as a lead cell
  (flag `WIDE_LEAD`) plus a continuation cell (`WIDE_CONT`, blank). All
  grid math (cursor advance = 2, wrap points, selection columns, reflow
  boundaries) sees columns; the renderer gives the lead a 2-cell slot and
  the continuation draws nothing.
* **D2 — `TerminalLine.text` keeps exactly one char per column.**
  Continuation cells contribute a space placeholder and astral glyphs are
  parked in a per-line `clusters` map (column → base+marks string), so
  every column↔index computation in the app (URL hit-testing, word
  boundaries, selection) stays exact. `readableText()`/`selectedText()`
  join pairs and expand clusters for human text (copy/share), which is
  what terminals that model wide glyphs do.
* **D3 — clusters render as one draw.** A cell with combining marks keeps
  them in `cell.comb` (capped at 8, `MAX_COMBINING`), `rowToLine` builds
  the cluster string, and the renderer draws that string once — Android
  shapes the cluster correctly instead of drawing isolated marks.
* **D4 — curated table, not the full UCD.** `CharWidth` embeds the W/F
  and zero-width ranges that actually occur in terminals (Brahmic-script
  mark ranges, CJK/Hangul/Kana/fullwidth, common emoji, Tajut/Kana
  supplements). Unknown → width 1 (safe fallback). The table is binary-
  searched; extending it later is data-only.
* **D5 — wide glyph at the right margin wraps whole** (never straddles);
  with DECAWM off it degrades to a single-cell draw rather than corrupting
  the line.

## 4. Implementation record (2026-08-31, commit 843a274)

* `CharWidth.kt` (pure), `CellFlags.WIDE_LEAD/WIDE_CONT`,
  `Cell.comb` (+`appendCombining`, `isBlank` knows about marks),
  `TerminalBuffer.print` classifies width → combining attach (to the
  lead cell, including across a pending wrap), wide placement, wide-margin
  wrap; `rowToLine` builds `clusters`; `visibleText`/`scrollbackText`
  append marks; `Reflow` never splits pairs (§19.1).
* Renderer: cluster draw with a 2-cell slot for leads; squeeze-to-slot if
  a fallback font's advance exceeds it.
* **Tests (11):** `CharWidthTest` (5) — ASCII 1 / marks 0 (incl. Bengali
  া/ি and virama) / CJK+Hangul+Kana+fullwidth 2 / emoji 2 / ambiguous 1
  (box-drawing, Greek, Cyrillic, halfwidth kana, ❤);
  `TerminalUnicodeTest` (6) — wide occupies two cells & advances two
  columns, combining joins without advancing, mark-after-wide attaches to
  the lead, UTF-8 feed lays out `কি` as one cell, `日本` spans four columns
  (grid text vs readable text), astral emoji keeps text column-aligned.

## 5. Exit condition & device recipe

**Verdict: PASS — owner device rounds, final word 2026-08-31: "All ok now"** (Phase 19 accepted as a whole; see README + JOURNEY item 18).

```text
1. echo '漢字 日本語 한글 ひらがな'      EXPECT: readable, no overlapping, wraps whole.
2. printf 'emoji: 😀 👍 ⌚\\n'          EXPECT: emoji occupy 2 cells (no neighbor overlap).
3. echo 'কি বাংলা নমস্কার'              EXPECT: vowel signs cluster with their consonant.
4. Type a long CJK line and pinch-zoom   EXPECT: wrap points never split a glyph.
5. Long-press → select a CJK line → Copy EXPECT: pasted text has no extra spaces INSIDE words
                                            (a space between two separate wide words is fine).
PASS = 1–4 clean; 5 copies usable text.
```

## 6. Invariants

Client-only; pure Kotlin; no PTY/parser-protocol changes; `cc`/`bash`
untouched; nothing in `$PREFIX/bin`; clean-room — the width rules are
implemented from the public Unicode reports (UAX #11 + general category
definitions), not from any terminal's source.
