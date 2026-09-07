# CodeC Phase 30 — Offline completeness (snippets + Emmet)

> **Status:** ✅ **COMPLETE, DEVICE-PASSED & MERGED to `main` via PR #55
> (2026-09-07, owner: "If all done then merge it").** Implemented 2026-09-06
> (owner: "Start phase 30"), all three parts in one build on
> `arena/01a07646-codec`; `Build APK` GREEN first try (run `34034889209`, tip
> `641f6e8`, 4m34s: assemble + `:app:testDebugUnitTest` + `:app:lintDebug` +
> the bench module); device round 1 (`docs/TROUBLESHOOTING.md` §13, build
> `ca8ec57`) reported TWO bugs, both fixed in `d63a645` and **device-confirmed
> PASSED on §14 (owner: "Yes working", 2026-09-07)**; final CI **run
> `34078739941` GREEN (tip `c2b392e`, 8m33s)**, artifact `CodeC-IDE`
> **24 375 211 B = +117 409 B (+0.11 MiB)** vs `main` (24 257 802 B).
> **No gate is open.** §13/§14 stay as the standing regression cards.
>
> **Amended the same day, before the device round** (PART_30_1 §3.5,
> PART_30_2 §3.5): writing the card meant measuring every string in it against
> the real engine + real assets, and five were wrong — Markdown `head` returned
> **nothing**, Python `pr` lost `print(...)`, shell `if ` dumped 16 snippets
> with **no if-block**, Python `def ` had no `def`, and a typed
> `nav>ul>li*2>a[href=#]{Link $}` never fired (the walk-back stopped at the
> space inside `{text}`). Fixed by keeping the built-in tables as a **deduped
> tail** after the pack, moving the trigger path's "don't offer the word back"
> test from the label to the **insert text**, and making the Emmet walk-back
> brace-depth aware. Two new host tests (18 assertions), so the phase total is
> **91 new host tests** (five files) + 6 new cases in three existing ones;
> every law of Phase 27 re-pinned (`CompletionPolicy` untouched). Amended build
> **CI GREEN: run `34041185149`, tip `ca8ec57`, 4m51s** (one for-cause round —
> `34040754444` red on a hand-counted caret literal in the new `EmmetTest`
> case, i.e. a wrong assertion, not a wrong engine); artifact `CodeC-IDE`
> 24 374 688 B = **+116 886 B (+0.11 MiB)** vs `main`. **That is the build the
> §13 card's chip lists were measured against — install it.**
> **No PR/merge without the owner's command.** *(Superseded 2026-09-07: the
> owner commanded the merge and PR #55 landed it — see the status block.)*
>
> **Device round 1 came back with two bugs — both FIXED on this branch
> (2026-09-07), retest card `docs/TROUBLESHOOTING.md` §14:**
> **(a)** accepting a suggestion left the typed prefix behind (`#in` + tap →
> `##include <stdio.h>`): the accept span was 22.3's *identifier* word-run and
> `#`/`@`/`!`/`.`/`>` are not word characters, which was harmless while every
> built-in snippet's insert began with its trigger word and wrong for the packs
> + Emmet. Now `CodeCompletionEngine.replaceSpanLength` = word-run span ∪ the
> insert's aligned head, bounded by the caret's line; `alignedTailLength`
> gained `ignoreCase` — the **ghost stays literal-case** (it paints the suffix,
> so its claim must be byte-true) while the **accept path is case-insensitive**
> (22.6's matching law), wired at both accept surfaces (VM chip + CodeC
> Analyzer panel). Measured: `#in`→3, `int mai`→7, `@med`→4, `<!doc`→5,
> Markdown `head`→4, `obj.meth`+`method()`→4, empty→0, never across a newline.
> **(b)** CodeC Keys did not auto-close brackets and `{` + Enter did not split
> the pair: SmartTyping's suppression parameter was named `isStrip` and both
> CodeC Keys paths still passed `true` — a leftover from when the strip was the
> only non-IME surface, while 28.2 made CodeC Keys a full typing surface (the
> system-IME path already paired). Renamed to `suppressAutoPair`; CodeC Keys
> now pairs (`{` + Enter → caret indented with `}` on its own line), the
> BottomStrip and programmatic caret moves keep suppression.
> Host: **3 new test cases** with every value measured on a host JVM first
> (`CodeCompletionTest` 19, `SmartTypingTest` 13) — **124 tests green locally**
> over the real production files. `CompletionPolicy` is still untouched.
> **CI for this round:** `d63a645` (both fixes) + `7c7f227` (these docs) went
> out as run `34077539890` — **RED on the known sora/Robolectric flake**
> (`EditorLaunchMeasureReproTest`, `IllegalThreadStateException` in sora's
> unsynchronized `AsyncIncrementalAnalyzeManager.rerun`; the same test failed
> the same way on the docs-only run `34041572778` and was green in
> `34041185149`). `c2b392e` retires the flake: the smoke tolerates exactly that
> one third-party signature and still fails on everything else. Re-run
> **`34078739941` GREEN (tip `c2b392e`, 8m33s)** — artifact `CodeC-IDE`
> 24 375 211 B = **+117 409 B (+0.11 MiB)** vs `main`, i.e. +523 B over the
> `ca8ec57` build for the two fixes. (A RED run uploads NO APK artifact — the
> `gradle-bootstrap` shim runs `:app:testDebugUnitTest` inside the assemble
> step, before the upload — so a flaky third-party test blocks the owner's
> build, which is why it was retired rather than re-rolled.)
> **The owner installed that build and re-tested §14: PASSED ("Yes working",
> 2026-09-07).** Phase 30 closed and merged via PR #55 on the owner's command.
>
> Original plan (2026-09-05, docs only): suggestions don't give every
> suggestion; phone coding is painful. Phase 27 already fixed **accept UX**
> (ghost + chips). This phase fixes **what is offered** without a 90 MB LSP.
> Research: [`OSS_REPLACEMENT_RESEARCH.md`](../OSS_REPLACEMENT_RESEARCH.md)
> §8.3.B, [`PHONE_UX_ANALYSIS.md`](../PHONE_UX_ANALYSIS.md) change 5.

Same move as Phases 25.2/29: **stop hand-writing what an MIT dataset already
ships.** The snippet tables (7 for C, 9 for Python, 8 for HTML, 3 for CSS) are
now 29 vendored [friendly-snippets](https://github.com/rafamadriz/friendly-snippets)
packs — **84 C, 76 Python, 126 HTML, 156 CSS, 367 JavaScript, 140 TypeScript,
62 Markdown, 16 shell** resolved completion items — plus a clean-room Emmet
engine for HTML/CSS/JSX, plus an engine cap that went 8 → 50 while the thumb
cap (8 chips) stayed where it belongs.

```
  30.1  friendly-snippets JSON → CompletionItem (replace Kotlin tables)   ✅ BUILT
              │
              ▼
  30.2  Emmet for HTML/CSS (and JSX-ish) as completion items              ✅ BUILT
              │
              ▼
  30.3  Engine: no MAX_ITEMS=8 hard cap; strip scrolls; ghost still top-1  ✅ BUILT
```

| Part | Title | Cost | Effort | State |
|---|---|---|---|---|
| [30.1](PART_30_1_FRIENDLY_SNIPPETS.md) | MIT snippet packs as assets | client-only | M | ✅ merged (PR #55) |
| [30.2](PART_30_2_EMMET.md) | Emmet expansions into the same pipeline | client-only | M | ✅ merged (PR #55) |
| [30.3](PART_30_3_STRIP_CAPACITY.md) | Completeness vs chip UX | client-only | S | ✅ merged (PR #55) |

**Law:** Phase 27 `CompletionPolicy` unchanged — Enter sacred, master
switch, no auto-commit. Snippets fill `CompletionItem`; ghost/strip/panel
only render. **Re-pinned by test:** `CompletionPolicyTest` (12) is untouched
and green; `CodeCompletionTest` (18, Phase 12/22.6) is untouched except for a
`@Before` that pins it to the built-in-table fallback world;
`CompletionCapacityTest` asserts master-off ⇒ key mode + `anyOn == false`.

**Measured before/after (host, real assets):**

| Typed | Language | old candidates | new candidates |
|---|---|---|---|
| `i` | C (`main.c`) | 7 | **10** (8 snippets + 2 keywords) |
| `i` | Python | 8 | **9** (7 snippets + 2 keywords) |
| `for` | C | 1 snippet + 1 keyword | **4 snippets** (`for` `fora` `forc` `forg`) |
| `for` | Python | 1 snippet + 1 keyword | **2 snippets** (`for` `forr`) |
| `doc` | HTML | 1 (`<!DOCTYPE html> skeleton`) | **2** (+ pack `doctype`) |
| `ul>li*3` | HTML | nothing | **1 Emmet expansion at rank 0** |
| `m10` | CSS | nothing | **1 Emmet declaration (`margin: 10px;`)** |

*This table was measured against the FIRST build (`641f6e8`) and the §3.5
amendment moved four rows — with the built-in tables riding as a deduped tail,
C `i` is **13** candidates (not 10), Python `i` **13** (not 9), C `for` **5**
(not 4), and the amendment's own five fixes restored Markdown `head` → 1,
Python `pr` → 2, shell `if ` → 2, Python `def ` → 5 (`def` first). The amended
numbers are the ones §13's card was written from; PART_30_1 §3.5 and
PART_30_3 §3.4 carry the measurements.*

**Budgets — MEASURED from the CI artifacts:** `CodeC-IDE` 24 257 802 B (main
`31e319f`, run `34027216565`) → 24 374 374 B (first build) = **+116 572 B (+0.11 MiB / +0.117 MB)**;
§3.5 amendment 24 374 688 B (+116 886 B); device-round fixes 24 375 211 B
(run `34078739941`) = **+117 409 B (+0.11 MiB)**; merge tip `646e213`
(run `34087566165`) 24 375 129 B = **+117 327 B** — the ±82 B between the last
two is zip/DEX ordering noise on a docs-only commit,
i.e. ~54 KB of deflated pack JSON (277 KB raw across 29 assets) plus the new
resolver/Emmet DEX. Noise next to Phase 29's +2.2 MB engine chain, and far
inside any reading of the 25.1 size law. Keystroke cost: the whole `completions()`
call on a **4 000-line** buffer with a ±20 000-char identifier window measured
**1.8–4.5 ms** on the host JVM (the debounce already keeps it off the critical
path; budget is 16.7 ms).

**License:** friendly-snippets is **MIT** — vendored as unmodified data with
`assets/licenses/FRIENDLY_SNIPPETS_MIT.txt` (repo URL + pinned upstream commit
`6cd7280adead7f586db6fccbd15d2cac7e2188b9` + snapshot date) and a Settings →
About attribution line. Re-vendoring is one command:
`python3 scripts/vendor_snippets.py` (python3 + curl only). Emmet is
**clean-room** (rule.md §6): no upstream Emmet code was read or copied — the
abbreviation grammar was reimplemented from the documented operator subset.

**Host tests:** `SnippetSyntaxTest` (21, pure) · `SnippetPacksTest` (12, pure)
· `EmmetTest` (24, pure) · `SnippetLibraryTest` (15, Robolectric + real
assets) · `CompletionCapacityTest` (19, Robolectric — the host mirror of all
three exit conditions) · + `StripContextTest` (lone-Emmet chip, 50-item chip
cap), `GhostCompletionTest` (caretOffset parking), `CodeCompletionTest`
(fallback world) — and from the device round **3 more cases** with every value
measured first (`CodeCompletionTest` 19 for the accept span, `SmartTypingTest`
13 for pairing + the brace split).

**Not this phase:** clangd / pylsp (Phase 31).
