# CodeC Phase 30.2 — Emmet

**Status:** 🚧 IMPLEMENTED (2026-09-06) · **Cost:** `[client-only]` · **Effort:** M
· **Depends on:** 30.1 or 27 pipeline
· **Target:** HTML/CSS completion path

---

## 1. Design

Acode’s Emmet plugin is the phone-web expectation: `ul>li*3` expands.
Use [emmetio/emmet](https://github.com/emmetio/emmet) (MIT) **behavior**:
abbreviation → expansion. Implementation must be **clean-room / library
depend**, not Ace. Prefer a small JVM/Kotlin expansion or a trimmed JS
interpreter only if size stays in budget; otherwise a **host-tested
abbreviation subset** (`!`, tags, `>`, `+`, `*n`, `.class`, `#id`) is
enough for phone HTML.

Trigger: current token looks like an Emmet abbr in HTML/CSS; item kind
SNIPPET detail `emmet`.

## 2. Exit condition

```text
(Device, HTML file)
1. Type `ul>li*3` → chip/ghost offers expansion; tap inserts a list.
2. Type `!` → HTML5 skeleton (or keep the 22.6 DOCTYPE snippet).
3. C file: Emmet does not fire on `ul>li`.
PASS = all three.
```

---

## 3. IMPLEMENTATION RECORD (2026-09-06, owner: "Start phase 30")

### 3.1 What shipped

**One new file, no new dependency:** `ui/editor/Emmet.kt` (859 LOC, pure
Kotlin, no Android imports). **Clean-room** per rule.md §6 — no emmetio source
was read or copied; the abbreviation grammar was reimplemented from the
documented operator subset the plan names, then extended only where a phone
user would immediately hit the gap. The plan's "library depend" option was
rejected on purpose: upstream Emmet is TypeScript, so shipping it means a JS
interpreter on the completion path (size + a per-keystroke bridge), and the
plan's own fallback — *"a host-tested abbreviation subset is enough for phone
HTML"* — is exactly what this is.

**Public surface (everything the engine and the tests use):**

| Member | Role |
|---|---|
| `enabledFor(language, fileName)` | The language gate: HTML/XML/CSS always; JS/TS **only** for `.jsx`/`.tsx`; everything else never. |
| `abbreviationAt(text, caret, language, fileName)` | The token under the caret, or null. Walks back over token chars to the line start, then applies the context guards and the signal test. |
| `expand(abbr, language, baseIndent, fileName)` | `Expansion(text, caretOffset)?` — markup or CSS by language, re-indented onto `baseIndent`. |
| `completionItemFor(text, caret, language, fileName)` | The `CompletionItem` the engine prepends: label = the abbreviation, kind `SNIPPET`, detail `Emmet.DETAIL` = `"emmet"`, `replaceLength` = the whole token, `caretOffset` = the expansion's caret. |

**Markup subset** (the plan's `!`, tags, `>`, `+`, `*n`, `.class`, `#id`, plus
the operators a phone user hits in the first minute):

- `!` and `html:5` → the HTML5 skeleton, shaped exactly like the engine's 22.6
  `HTML_SKELETON` extra (`<meta charset="utf-8">`, viewport meta, `<title>Page`)
  so the two paths produce the same document; the caret parks on the blank line
  inside `<body>`.
- `>` child, `+` sibling, `^` climb-out, `*n` repeat (capped at `MAX_REPEAT`
  100), `(…)` groups — including a repeated group (`(div>p)*2`).
- `.class` (several → one `class="a b"` attribute), `#id` (rendered **before**
  the classes, as upstream does), `[href=# target=_blank disabled]` (a bare
  attribute becomes `disabled="disabled"`), `{text}`.
- `$` numbering: `$` → 1,2,3…, `$$` → zero-padded 01,02…, `$$$` → 3 wide. The
  repetition counter **propagates into nested repeats**, so
  `ul>li*3>a{Link $}` numbers the links 1..3 even though each `a` repeats once.
- Implicit tags: `.card` → `div`, `ul>*` → `li`, `table>tr*2>td` → cells,
  `select>`, `dl>`, `object>`, `audio|video|picture>` (Emmet's own table).
- Void elements (16: `br img input meta link hr …`) never get a closing tag;
  `/` self-closes anything else (`custom/` → `<custom />`).
- **JSX flavour:** in `.jsx`/`.tsx` an empty or void element self-closes with a
  space (`<br />`, `<img />`) because JSX requires it.
- Hard bounds: `MAX_ABBREVIATION` 120 chars, `MAX_NODES` 400 rendered nodes,
  `MAX_DEPTH` 24 nesting levels, 4-space indent.

**CSS subset:** 82 property abbreviations resolved by **longest match** (`m` →
`margin`, `mt` → `margin-top`, `m10` → `margin: 10px;`), value units `%`(or
`p`) `e`/`em` `r`/`rem` `x`/`px` `vh vw vmin vmax` `cm mm in pt` `s ms fr deg`
`ch ex`, default `px`, unit-less when the number is 0 or the property is one of
the 7 in `UNITLESS` (`z-index`, `opacity`, …); `-` separates values
(`p10-20` → `padding: 10px 20px;`) while a LEADING `-` is a negative
(`mt-5` → `margin-top: -5px;`); `m0-a` → `margin: 0 auto;`; `!` suffix →
`!important`; `+` chains up to 6 declarations (`m10+p20`); `#fff` colours and
`url() rgb() var() calc() hsl()` pass through untouched; 22 properties carry
single-letter keyword tables (`d:f` → `display: flex;`, `pos:a` →
`position: absolute;`, `fld:c`, `bd1-s`, `ta:c`, `fw:b`, …) plus the common
`a/n/i/ini/u` → `auto/none/inherit/initial/unset`. The caret parks right after
the first `": "`, which is where you type the value you actually meant.

**Guards — refusing beats guessing** (this is the part that keeps a `!` in C or
a `{` in a CSS string from ever producing a chip):

- A token must carry a **structural signal**: `!`, `html:5`, one of `> + * ^ [ { ( #`,
  or a leading `.`. So prose (`Hello.World`), member access (`obj.method`) and
  a bare tag name (`div` — the snippet pack already offers it) never fire.
- Markup context: refused inside an open tag (`<div class=x>`), inside an
  attribute value (odd quote count on the line), and inside an unterminated
  `<!--` comment.
- CSS context: refused inside a string, inside an open `/*` comment, and when
  the trimmed line-before ends in a selector character (`.card, m10` is a
  selector list, not an abbreviation). A CSS token must also start with a
  letter and contain a digit, `#`, `%` or `:` — so the bare word `margin`
  (a keyword/snippet job) never expands.
- JSX-ish files only fire on a real structural operator (`> * ^ +`) and never
  on a bare `!`; a plain `.js`/`.ts` file never fires at all.
- Malformed input is refused, never repaired: `(div>p`, `>div`, `div*`,
  `div*99999`, `p{hello`, `a[href=#`, a 240-char abbreviation, an unknown CSS
  property (`qq10`), a valueless CSS token (`m`), markup operators in CSS
  (`div>p`).

**Indentation law (`finish()`).** The renderer emits RELATIVE indentation and
then re-indents every **continuation** line onto the caret's own base indent.
The first line is deliberately NOT prefixed — it already sits where the caret
is, and prefixing it too doubles the indent (`  ` + `  <ul>`); the caret offset
is shifted by `baseIndent.length × (newlines before the caret)` so it still
lands inside the first `<li>`. CSS routes through the same function.

**Pipeline placement.** `CodeCompletionEngine.completions()` prepends
`Emmet.completionItemFor(...)` before every other source, so an expansion is
**rank 0 by construction** — first chip, first panel row, and the ghost's
candidate when it can be painted. Because an expansion's insert text never
starts with what was typed (`ul>li*3` → `<ul>…`), `GhostCompletion.compute`
stays `Hidden` for it, which is why `StripContext` grew one narrow exception to
27.2's S1 single-candidate rule (see PART_30_3 §3.1). Accepting replaces the
WHOLE abbreviation via `replaceLength` — the identifier prefix at the caret is
only its last fragment (`ul>li*3` → prefix `3`), so the 27.x accept math would
have left `ul>li*` behind. Both the ViewModel and the ghost's FULL accept
honour `replaceLength`/`caretOffset`.

### 3.2 Host tests (new: 24 + the capacity mirror)

`EmmetTest` (24, pure — public surface only; the markup and CSS parsers are
private by design): the `!` skeleton incl. its caret, child/sibling/repeat/
group/climb, implicit tags (`.card`, `ul>*`, `table>tr*2>td`), classes+id+
attributes+text, `$`/`$$` numbering incl. propagation into nested repeats, void
vs self-closing, the JSX flavour, caret parking in the first empty element,
continuation-line re-indentation, 17 CSS expansions + the `+` chain + colon
caret, CSS/markup refusals, the length cap, the token walk-back and its signal
rule, the three markup context guards, the four CSS context guards, the full
language gate (C / Python / plain `.js` never; HTML / CSS / `.jsx` / `.tsx` do),
the JSX operator requirement, and the completion-item shape (label, `DETAIL`,
kind, `replaceLength`, caret, base-indent carry-over) for both markup and CSS.

`CompletionCapacityTest` (Robolectric) mirrors the device round on the real
pipeline: rank 0 in an HTML file, the tap-accept math (ViewModel formula)
producing the exact expected buffer + a caret inside the first `<li>`, `!` from
the strip, **no Emmet row for five different C buffers** (nor Python, nor a
plain `.js`), a `.jsx` file that does fire, CSS declarations vs a selector list,
and the lone-Emmet chip vs the lone identifier that stays in key mode.

### 3.3 Deviations from the spec (recorded)

- **A subset, as the plan allows.** Not implemented: filters (`|bem`, `|c`,
  `|s`, `|t`), numbering direction/start (`@-`, `@3`), `lorem` text generation,
  custom snippets/aliases, `select>option` style snippet aliases beyond the
  implicit-tag table, CSS `@media`/`@f` at-rule abbreviations, and multiple
  carets (one `caretOffset`, per 30.1's S3 deviation — a phone has no Tab-stop
  navigation to walk them with).
- **`html:5` accepted as a synonym for `!`** (upstream behaviour, one line).
- **XML is enabled** by the language gate (`enabledFor` returns true for it) but
  there is no XML-specific void/implicit table; it expands as markup. Cheap and
  harmless — recorded because the plan only names HTML/CSS/JSX.
- **The skeleton is CodeC's shape, not upstream's** (`charset="utf-8"`,
  `initial-scale=1`, `<title>Page`) so that `!` and the 22.6 `doc` snippet
  produce the same document; exit condition 2 explicitly allows either.

### 3.4 Exit condition status

```text
(Device, HTML file) — ✅ PASSED 2026-09-07 (owner round, card: TROUBLESHOOTING §13)
1. Type `ul>li*3` → chip/ghost offers expansion; tap inserts a list.
2. Type `!` → HTML5 skeleton (or keep the 22.6 DOCTYPE snippet).
3. C file: Emmet does not fire on `ul>li`.
PASS = all three.
```

**Device round 1 amendment (2026-09-07).** Emmet itself needed no change: an
expansion carries its own `replaceLength` (the whole abbreviation), so it wins
over the generic accept span that this round introduced for pack snippets
(`CodeCompletionEngine.replaceSpanLength`). The round's second fix is adjacent
but real for markup authoring — CodeC Keys now auto-closes `(`/`[`/`{`/`"`/`'`
and `{` + Enter splits the pair with the caret indented, so writing the HTML an
expansion produces no longer means fighting the keyboard. Both device-confirmed
**PASSED** on the `c2b392e` build (owner: "Yes working"); card:
`docs/TROUBLESHOOTING.md` §14.

**CI:** `Build APK` run `34034889209` GREEN on tip `641f6e8` (4m34s —
`:app:assembleDebug` + `:app:testDebugUnitTest` + `:app:lintDebug` through the
gradle-bootstrap shim, plus `:bench:assembleRelease :bench:testDebugUnitTest`);
first try, no for-cause round. Artifact `CodeC-IDE` +116 572 B (+0.11 MiB / +0.117 MB)
vs `main`.

**CI (the §3.5 amendment):** run `34040754444` on tip `d3a2443` went RED on
exactly one test — the phase's single for-cause round — and it was the
ASSERTION, not the engine: the new `EmmetTest` case handed `abbreviationAt` a
hand-counted caret of 16 for the 17-char `div>p{a b}+p{c d}`, so the walk-back
stopped before the closing `}` and null was the correct answer for that caret.
Fixed in `ca8ec57` (carets are `.length` now, with a comment saying why); run
**`34041185149` GREEN** on tip `ca8ec57` (4m51s, same steps). Artifact
`CodeC-IDE` 24 374 374 → **24 374 688 B = +116 886 B (+0.11 MiB)** vs `main`
(+314 B for the amendment: no new assets, just the tail merge and the
brace-depth walk-back).


**CI (the device round, 2026-09-07):** `d63a645` (both fixes) + `7c7f227` (the
§14 card) went out as run `34077539890` — **RED on the known sora/Robolectric
flake** (`EditorLaunchMeasureReproTest` → `IllegalThreadStateException` inside
sora's unsynchronized `AsyncIncrementalAnalyzeManager.rerun`; the same test
failed the same way on the docs-only run `34041572778` and was green in
`34041185149`). `c2b392e` makes that smoke tolerate exactly that one
third-party signature and nothing else → **run `34078739941` GREEN (tip
`c2b392e`, 8m33s)**, artifact `CodeC-IDE` 24 375 211 B = **+117 409 B
(+0.11 MiB)** vs `main`. **Merged to `main` via PR #55** on the owner's command.

Host mirror (green): item 1 — `ul>li*3` is rank 0 with detail `emmet`, and the
accept math yields
`<body>\n  <ul>\n      <li></li>\n      <li></li>\n      <li></li>\n  </ul>`
with the caret inside the first `<li>`; the lone candidate still gets its chip.
Item 2 — `!` offers the skeleton (and the 22.6 DOCTYPE snippet still answers
`doc`). Item 3 — five C buffers (`ul>li`, `if (!x)`, `div>p`, `!`,
`a[href=#]{x}`) produce **zero** items with detail `emmet`, and
`abbreviationAt` returns null for the C language outright.

### 3.5 Amendment (2026-09-06, same day — found while writing the device card)

**A text node containing a SPACE was unreachable when TYPED.** `expand()`
always handled `a{Link $}` and `nav>ul>li*2>a[href=#]{Link $}` — the numbered
text case this part's own §1 advertises — and `EmmetTest` pinned it, but those
tests call `expand()` directly. The editor path goes through `abbreviationAt`,
whose walk-back stopped at the first space, so on a phone the token under the
caret was the fragment AFTER the space (`$}`) and the gate returned null:
measured, `nav>ul>li*2>a[href=#]{Link $}` in `index.html` produced **zero**
items — the card's own step 7 would have been a FAIL.

**Fix (markup only, 12 lines).** While walking back, a space belongs to the
token as long as the walk is inside a `{…}` it has not closed yet: brace depth
counted from the caret backwards, so the opening `{` ends the allowance. CSS is
untouched (no brace-text syntax, and `{`/`}` are not CSS token chars, so the
allowance is behind `!css`).

**Guards kept, measured after** (JVM harness, real engine):
`abbreviationAt("ul> li*2") == "li*2"` still holds (a space OUTSIDE a text node
ends the token exactly as before); `<p>Hello World` → null (prose, no signal);
`<p>Hello {World` → the walk-back returns `{World` but `expand` REFUSES it, so
`completionItemFor` is null and no chip appears (an unbalanced text node is
malformed input, §3.3 — refusing beats guessing); JSX `{ const x = "a b" }` →
null (odd quote count); `a { p10 20` in CSS → null (space, and `20` is not a
letter-led abbreviation); C/`.js` unchanged. New shapes now work: `a{Link $}` → `<a>Link 1</a>`,
`p{Hello World}` → `<p>Hello World</p>`, and
`nav>ul>li*2>a[href=#]{Link $}` → `<nav><ul><li><a href="#">Link 1</a></li>` …
with `Link 1`/`Link 2` and the caret in the first link text.

**Test:** `a text node keeps its spaces, prose still stops at one`
(`EmmetTest`, 9 assertions — both positive shapes plus `div>p{a b}+p{c d}` (two
spaced text nodes in one abbreviation), the end-to-end `completionItemFor`, the
pre-existing `ul> li*2` guard, the prose guard, the unbalanced-`{` refusal at
both layers, and the CSS refusal).
