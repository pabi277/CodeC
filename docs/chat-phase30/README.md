# CodeC Phase 30 — Offline completeness (snippets + Emmet)

> **Status:** 🚧 **IMPLEMENTED (2026-09-06, owner: "Start phase 30") — all
> three parts in one build on `arena/01a07646-codec`; CI + device round
> pending at write time.** 89 new host tests (five files) + 6 new cases in
> three existing ones; every law of Phase 27 re-pinned (`CompletionPolicy`
> untouched). **No PR/merge without the owner's command.**
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
| [30.1](PART_30_1_FRIENDLY_SNIPPETS.md) | MIT snippet packs as assets | client-only | M | ✅ implemented |
| [30.2](PART_30_2_EMMET.md) | Emmet expansions into the same pipeline | client-only | M | ✅ implemented |
| [30.3](PART_30_3_STRIP_CAPACITY.md) | Completeness vs chip UX | client-only | S | ✅ implemented |

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

**Budgets:** APK weight of the packs = **277 KB raw / ~54 KB deflated** (29
JSON assets) — noise next to Phase 29's +2.2 MB engine chain, and well inside
any reading of the 25.1 size law. Keystroke cost: the whole `completions()`
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
· `EmmetTest` (23, pure) · `SnippetLibraryTest` (15, Robolectric + real
assets) · `CompletionCapacityTest` (18, Robolectric — the host mirror of all
three exit conditions) · + `StripContextTest` (lone-Emmet chip, 50-item chip
cap), `GhostCompletionTest` (caretOffset parking), `CodeCompletionTest`
(fallback world).

**Not this phase:** clangd / pylsp (Phase 31).
