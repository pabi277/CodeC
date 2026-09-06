# CodeC Phase 30.1 — friendly-snippets

**Status:** 🚧 IMPLEMENTED (2026-09-06) · **Cost:** `[client-only]` · **Effort:** M
· **Depends on:** Phase 27 pipeline
· **Target:** `assets/snippets/`, `CodeCompletionEngine.kt`

---

## 1. Design

Replace hand-written `snippets(language)` tables with
[rafamadriz/friendly-snippets](https://github.com/rafamadriz/friendly-snippets)
(MIT) JSON. Load per `LanguageType`. Keep buffer-identifier scan as a
lower-priority source.

| # | Rule |
|---|---|
| S1 | Assets, not network. SPDX in `assets/licenses/`. |
| S2 | Prefix match stays case-insensitive (Phase 22.6 law). |
| S3 | Snippet `insertText` may contain VS Code tabstops (`$1`); sora snippet parser already exists — use it if cheap, else insert the body and park caret at first stop. |
| S4 | JSON/TEXT still get few/no snippets unless the pack has them. |

## 2. Exit condition

```text
(Device)
1. Type `for` in C and in Python — more than the old 1–2 snippets; chips scroll.
2. Type `doc` in HTML — DOCTYPE / html skeleton still appears (regression).
3. Master completion switch OFF → zero snippets computed (27.3).
PASS = all three.
```

---

## 3. IMPLEMENTATION RECORD (2026-09-06, owner: "Start phase 30")

### 3.1 What shipped

**Vendored data (MIT, unmodified).** 29 friendly-snippets JSON packs under
`app/src/main/assets/snippets/` — 277 KB raw, ~54 KB deflated in the APK —
pinned to upstream commit `6cd7280adead7f586db6fccbd15d2cac7e2188b9`
(2026-01-23). `scripts/vendor_snippets.py` (python3 + curl, no other tooling)
reproduces the tree and the license file from that pin; its `PACKS` table
mirrors `SnippetAssets.packsFor`, and `SnippetLibraryTest` asserts every mapped
path really ships, so the two lists cannot drift. Notice:
`assets/licenses/FRIENDLY_SNIPPETS_MIT.txt` (MIT text + repo URL + pin +
snapshot date) and a Settings → About line ("snippet packs — MIT
(rafamadriz/friendly-snippets)").

**New pure Kotlin (`ui/editor/snippets/`, 1 158 LOC, no Android imports except
the library's `Context`/`AssetManager`):**

| File | LOC | Role |
|---|---|---|
| `SnippetJson.kt` | 220 | Strict dependency-free JSON reader: order-preserving objects, `\uXXXX` + escape decoding, malformed input → `null` (never throws). `org.json` is not on the host-test classpath, so a hand-rolled reader is what makes the packs host-testable. |
| `SnippetSyntax.kt` | 577 | The VS Code snippet resolver: tabstops/placeholders/mirrors, `${1\|a,b,c\|}` choices (first option), `$0` vs lowest-positive-stop caret, `$TM_*`/`${TM_*}` variables from the open file (`TM_FILENAME`, `TM_FILENAME_BASE`, `TM_FILEPATH`, `RELATIVE_FILEPATH`, `TM_DIRECTORY`/`WORKSPACE_*`), transformations `${var/regex/format/options}` with `g`/`i` and the `/upcase /downcase /capitalize /pascalcase /camelcase` ops + the `:+` `:-` `:?` conditionals, `\$ \} \\ \|` unescaping, `\t` → 4 spaces (`INDENT`), and hard bounds (`MAX_DEPTH` 12, `MAX_REPLACEMENTS` 64) so a pathological or self-referential body terminates. Unknown variables (`$RANDOM`, `$CLIPBOARD`, dates) resolve to "" rather than printing `${RANDOM}` into the buffer. Output is `ResolvedSnippet(text, caretOffset)`. |
| `SnippetPacks.kt` | 99 | `SnippetEntry` parsing (name / `prefix` string-or-array / `body` string-or-array / `description` string-or-array) → `CompletionItem`s: one item per prefix capped at `MAX_PREFIXES_PER_ENTRY` = 3, labels deduped **first entry wins** (the pack list is ordered main-pack-first so the everyday snippet beats the doc/debug one), detail = description → first body line (≥4 chars) → name, one line, ≤ `DETAIL_MAX` = 64. Blank bodies produce nothing. |
| `SnippetAssets.kt` | 107 | Pure `LanguageType` → ordered pack list (the snippet twin of `TextMateGrammars`): 14 languages ship packs; **JSON/TEXT get none (rule S4)** and XML/YAML have no upstream pack worth its weight, so they stay on the buffer-identifier scan. Also `all` (asset-existence tests) and `warmUpLanguages`. |
| `SnippetLibrary.kt` | 155 | Process-wide storage (the snippet twin of `TextMateSupport`): `attach(context)` installs the APK-asset reader (idempotent by AssetManager **identity** — Robolectric recreates the Application per test, and a stale manager fails every later open), `install {}` is the test seam, `reset()` returns to the fallback world, `warmUp()` parses without resolving, `loadedLanguages()` is the bookkeeping view. Two cache layers: parsed entries per language (unbounded — 14 languages) and resolved items per `language\|fileName` (bounded at 12, because `${TM_FILENAME_BASE}` resolution depends on the file). **A total read failure is NOT cached**, so a transient asset error is retried on the next keystroke instead of leaving the phone snippet-less for the session. |

**Engine (`CodeCompletionEngine.kt`, rewritten snippet source).**
`snippetItems(language, fileName)` = the packs **plus CodeC's own tables as a
deduped TAIL** (amended the same day — §3.5: the tables carry the two CodeC-only
snippets, the phone-optimised `<!DOCTYPE html>` skeleton that the 22.6 device
round pinned behind typing `doc` and CodeC's app-private shell shebang, *and*
the descriptive labels a prefix-only pack cannot reach), and the tables are the
WHOLE list whenever no pack loads at all, so a broken asset degrades to the
22.x behaviour instead of leaving nothing (S1's "assets, not network" made
safe). Ranking for a typed
prefix is now a real ordering (`rankSnippets`): labels the prefix directly
starts beat labels that merely contain a matching word, shorter labels beat
longer ones, stable within a tier so a pack's own order breaks exact ties.
**Rule S2 kept verbatim**: matching stays case-insensitive (`snippetMatches`,
also exposed as `labelMatches` for 27.3's instant shrink path). The
empty-prefix trigger path (`def `, `printf `) no longer dumps a 1 400-entry
pack: `triggerMatches` keeps only the snippets the trigger actually names
(label either direction, or the first body line), falling back to the whole
pack — capped — when nothing matches. The buffer-identifier scan is untouched
and still ranks **below** snippets (its own tier, `MAX_IDENTIFIER_ITEMS`).

**Wiring.** `completions()` gained `fileName: String? = null` (the file the
caret is in) which travels from `EditorViewModel` → `CodeCLanguage` /
`CodeCAnalyzer` → the engine, so `TM_*` variables resolve on a device.
`CompletionItem` gained two nullable fields — `replaceLength` (how many chars
before the caret the accept replaces) and `caretOffset` (where to park) — both
`null` for keywords/identifiers/built-in snippets, i.e. **exactly the 27.x
behaviour**; pack items carry `caretOffset`, Emmet items carry both.
`SoraEditorHost` warms the current language's pack inside the TextMate
`withContext` hop; `MainActivity` warms the default set on `Dispatchers.Default`
next to the grammar warm-up.

### 3.2 Host tests (new: 48 in three files)

- `SnippetSyntaxTest` (21, pure) — JSON reader (order, escapes, `\uXXXX`,
  malformed → null, arrays/numbers/bools) and the resolver: placeholders,
  mirrors, nesting, choices, escapes, tab → 4 spaces, `TM_*` variables
  (incl. `TM_DIRECTORY` for a root-level file), the five case ops, global vs
  single-shot transforms, the `:+`/`:-`/`:?` conditionals on both a valued and
  an empty source, an unmatchable regex, a regex that does not compile, cpp's
  `#guard` body **verbatim**, array bodies, and a self-referential placeholder
  (termination).
- `SnippetPacksTest` (12, pure) — entry shapes (string vs array prefix/body/
  description), dropped entries, resolved items + caret, one item per prefix
  with distinct labels, duplicate collapse (first wins), the prefix cap, detail
  truncation, `TM_FILENAME_BASE` through `items(…, fileName)`, and the whole
  `SnippetAssets` map (which languages ship packs, path uniqueness/prefix,
  main-pack-first order, warm-up list).
- `SnippetLibraryTest` (15, Robolectric `sdk=[34]`, REAL assets) — every mapped
  asset opens and is a non-empty strict-JSON object; every language with packs
  produces items; the pack floors (C/Python/HTML/CSS > 40, JS > 100); item
  shape (all `SNIPPET`, distinct non-blank labels, non-blank inserts, chip-sized
  details); JSON/TEXT/XML/YAML empty; cache identity; **the file name is part
  of the cache key** (two `.h` files of one language get different `#guard`
  bodies and neither leaks into the other); `TM_*` end-to-end on the real cpp
  pack (`INCLUDE_INC_THING_H_`, and `INCLUDE_THING_H_` at the project root);
  warm-up (parses, skips pack-less languages); attach idempotence; a failing
  reader degrades to empty and a transient failure is retried; a malformed pack
  is skipped while its siblings still load; `reset()` returns to the built-in
  world.

Existing files updated: `CodeCompletionTest` gained a `@Before` that pins it to
the **fallback** world (built-in tables) — these are Phase 12/22.6 assertions
about the tables, and the packs live in a process-wide singleton, so the reset
removes any dependence on Gradle's test order. The pack-loaded world is
`CompletionCapacityTest` (see PART_30_3 §3.2).

### 3.3 Deviations from the spec (recorded)

- **S3 — no sora snippet parser.** sora's `SnippetItem`/snippet-edit support is
  built for its own popup and its tabstop navigation, which CodeC does not use
  (the app popup is VM-driven, 25.2/27.x law). Instead the body is RESOLVED to
  ordinary text + one caret offset: one accept, caret parked at the first stop.
  Multi-stop tab navigation (Tab → next `$n`) is deliberately **not** offered —
  on a phone keyboard Tab is the indent/accept cap (27.3 invariant 2), so a
  snippet that needs four Tab presses would fight the input model. Recorded as
  the phase's one functional gap versus VS Code.
- **sora's native panel** (⌄ more → `SimpleCompletionItem`) parks the caret at
  the END of the insert, not at the tabstop: `SimpleCompletionItem` has no
  caret field. The strip and the ghost — the surfaces Phase 27 made primary —
  do honour it. Noted rather than hacked around.
- **S4 widened slightly:** XML and YAML also ship no pack (no upstream pack
  worth its weight; both stay on the identifier scan). JSON/TEXT are unchanged
  (the engine returns nothing for either).
- **Labels are the pack prefixes**, not the entry names (`for`, `#inc`, `main`)
  — that is what a phone user types and what the chip must show; the entry
  name/description is the `detail` line. The old tables used whole-body first
  lines as labels (`for (int i = 0; i < n; i++) {`), which is why `doc`-style
  word matching had to be invented in 22.6. **This bullet was too optimistic as
  first written** ("both keep working"): with prefix labels only, the 22.6 word
  matching has nothing to bite on, and four accepted typings broke — corrected
  by §3.5 (the tables ride along as a tail).

### 3.4 Exit condition status

```text
(Device) — PENDING (owner round; card: docs/TROUBLESHOOTING.md §13)
1. Type `for` in C and in Python — more than the old 1–2 snippets; chips scroll.
2. Type `doc` in HTML — DOCTYPE / html skeleton still appears (regression).
3. Master completion switch OFF → zero snippets computed (27.3).
PASS = all three.
```

**CI:** `Build APK` run `34034889209` GREEN on tip `641f6e8` (4m34s —
`:app:assembleDebug` + `:app:testDebugUnitTest` + `:app:lintDebug` through the
gradle-bootstrap shim, plus `:bench:assembleRelease :bench:testDebugUnitTest`);
first try, no for-cause round. Artifact `CodeC-IDE` +116 572 B (+0.11 MiB / +0.117 MB)
vs `main`.

Host mirror (green, real assets — `CompletionCapacityTest`): C `for` → 4
snippets (`for` `fora` `forc` `forg`, one a real `for (` loop) vs 1 before;
Python `for` → 2 (`for` `forr`) vs 1; HTML `doc` → the pack `doctype` **and**
the CodeC skeleton extra; master off → `StripContext.Keys` + `everythingOff` +
`!anyOn` (the exact predicates `EditorViewModel` short-circuits on), so nothing
is computed or painted. Identifiers still rank below pack snippets; JSON/TEXT
still return nothing. *(Post-§3.5 these grow by the tail: C `for` → 5 chips,
Python `for` → 3, `doc` unchanged at 2.)*

### 3.5 Amendment (2026-09-06, same day — found while writing the device card)

The card in `docs/TROUBLESHOOTING.md` §13 names exact strings, so every one of
them was measured first on a host JVM driving the REAL engine over the REAL
assets (`SnippetLibrary.install {}` reading `app/src/main/assets/snippets/`,
the same seam the Robolectric tests use). Five strings were wrong in the build
as committed (`b0eab80`), all from two root causes:

| typed | file | packs only (as committed) | pack + tail (now) |
|---|---|---|---|
| `head` | `notes.md` | **0 items — nothing at all** | 1 — `# Heading` |
| `pr` | `a.py` | 1 — `property` (**`print(...)` gone**) | 2 — `property`, `print(...)` |
| `if ` (trigger) | `run.sh` | **16 items, no if-block**: `echo` `read` `elseif` `else` `for_in` `for_i` `while` `until` … | 2 — `if`, `if [ cond ]; then ... fi` |
| `def ` (trigger) | `a.py` | 3 — `deft` `defs` `defst` (**no `def`**) | 5 — `def` `deft` `defs` `defst` `def function():` |
| `@med` | `site.css` | 1 — `med` (the pack's own `@media screen and (…)`) | 2 — `med`, `@media (max-width: 600px)` |
| `for` | `main.c` | 4 | 5 (the old loop label rides last) |
| `i` | `main.c` / `a.py` | 9 / 9 | 13 / 13 |

**Fix A — the built-in tables are a deduped TAIL, not dead fallback code.**
Pack labels are short prefixes, so a word from the old descriptive labels
matches nothing: Markdown has no `head`-ish prefix at all (the 62-item pack is
`table`, `link`, …), and Python's `pr` only reaches `property`. `snippetItems`
now returns `pack + builtinSnippets(language)` filtered through a label set
built from the pack, so an entry both sources claim keeps its **pack** copy;
`rankSnippets` still sorts a tier by label length, so short prefix labels stay
ahead of the tail's body-line labels and the tail only ever ADDS what the pack
cannot express. The `extras()` function is gone — its two entries already live
in the tables, which the tail now ships. No pack loads → the tables are still
the whole list, so the fallback world the 12.x/22.6 host tests pin is
byte-identical.

**Fix B — the trigger path's "don't offer the word back" test moved from the
LABEL to the INSERT TEXT** (new private `CompletionItem.retypes(word)`). Pack
labels ARE trigger words (`if` in shell.json, `def` in python.json), so the old
`it.label != trigger` discarded the ONE item the trigger matched; `relevant`
then came up empty and the `ifEmpty` fallback dumped the whole pack — that is
exactly how `if ` in a shell file offered 16 snippets with no if-block among
them. An item whose body really is the trigger word is still excluded, so the
rule keeps its purpose: in the fallback world `import ` no longer offers
`import module` (its insert is exactly `import `, a no-op re-type). That is the
ONLY fallback-world delta measured, and no test pinned it.

**Test:** `the built-in tail keeps the labels a prefix-only pack cannot reach`
(`CompletionCapacityTest`, pack world, 12 assertions) — the four restored
typings, the pack still outranking the tail (`property` before `print(...)`,
`med` before `@media (max-width: 600px)`), the shell trigger staying ≤4 chips
instead of 16, and `def` first after `def `.

Exit conditions are unchanged; they are now reachable on a device for every
string the card names. Recorded as a deviation from the plan's letter ("replace
the tables") in favour of its intent (nothing the phone could already do
disappears): the tables went from *source* to *supplement*.
