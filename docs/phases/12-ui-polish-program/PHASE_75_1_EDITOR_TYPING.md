# Phase 75.1 — Editor typing reliability (75.1 + 75.2 + 75.3)

**Status: ✅ COMPLETE, DEVICE-PASSED & MERGED via PR #98 (2026-09-30, branch `arena/01a0f0fb-codec`,
CI runs `36679767045` / `36692498787` / `36701799600` ✅ GREEN; owner device pass: *"Ok device
test passed … You can complete the docs part and merged"*).** This page was the draft; the owner
then commanded it into being:

> *"1st you read this file than find the problems with relevant with this and fix
> after that i will test on device give you latter instructions"*

No MCQ was answered line by line. The command is read as: start the phase, take the
recommended narrow scope (**1A** — the two reported defects plus directly affected
regressions), keep **3A** (one literal space per Backspace press), and hand the build
back for the owner's device round on **2A**'s surfaces (system keyboard + CodeC Keys).
Everything under "Proposed bounded scope" that needed the owner's handset is still
unverified — the record below claims source evidence only, never on-device behaviour.

The two drafts' earlier note stays true: no device, Android/IME version, code sample or
input surface has been recorded for the original report, so the symptoms are **not**
described as reproduced on-device — they are described as *explained from source*, which
is what the evidence supports.

## Owner-reported symptoms

1. In Python, `def` auto-indents, but `for` does not (owner selected reproduction A).
2. Backspace at the indentation of a `for` loop jumps back by the whole indent rather than deleting one space; owner recommends one space per press.
3. The owner wants typing behavior treated as a high-priority, dedicated editor area—not as a quick side-fix bundled into unrelated UI polish.

Exact device, Android/IME version, code sample and input surface have not yet been recorded. Do not describe the symptoms as reproduced on-device.

## Current source map and evidence

- `ui/editor/SmartTyping.kt:151` implements the VM-side newline transform; its Python path checks a trailing colon. `SmartTypingTest` covers basic Python-colon indentation and common pair behavior, but does not cover the reported `def` versus `for` discrepancy or the Backspace-by-one-space contract.
- `ui/editor/sora/CodeCAnalyzer.kt:219–221` implements Sora's `Language.getIndentAdvance` adapter. Its helper `indentAdvanceFor` takes a language parameter defaulting to C at `:258`; this adapter currently calls it without that parameter. This is a concrete suspect for the mismatch between Sora's indentation route and the VM smart-typing route, but the user's contrast (`def` works, `for` doesn't) is not yet explained by source inspection alone. Reproduce both through the same editor/input route before selecting a root cause.
- `ui/viewmodels/EditorViewModel.kt:1350` is the shared change entry for undo, dirty-state and autosave; `applyEditorKey` at `:1414` owns CodeC Keys edits.
- `ui/editor/sora/SoraEditorHost.kt:315–342` receives Sora content changes, and the host replays VM edits as a delta. A correction must preserve the single-edit/undo and composition/restart-input invariants; do not modify the replay path speculatively.
- Existing host tests: `SmartTypingTest`, `CodeCLanguageLogicTest`, `IncrementalEditTest`, plus editor/sora wiring and replay-path tests. CI `Build APK` is the only execution-of-record; no local Android compile or device test is claimed.

> **Update from the implementation pass (2026-09-30).** Every suspect above was resolved
> against the real Sora 0.24.6 source, and the `def`-vs-`for` contrast *is* explained by
> inspection — see §“Implementation record” below for the mechanism (a zero delta from the
> language-less adapter, plus the VM rule's “buffer grew by exactly one character” gate) and
> for what changed. The Backspace symptom has a source owner too (`deleteEmptyLineFast`), so
> neither finding is left as a hypothesis; both still await the owner's handset for the UX
> claim.

## Proposed bounded scope

A single typing-correctness pass, no editor redesign:

1. Reproduce the Python `def`/`for` difference across the actual editor's newline paths (system IME, CodeC Keys, editor key strip where applicable); trace the file-language value and Sora/VM path on each.
2. Define and implement Python block-indent behavior for valid block headers, including `def`, `for`, `while`, `if`/`elif`/`else`, and `try`/`except`/`finally`; avoid adding an indent for comments, strings, incomplete expressions or ordinary colons. Keep non-Python brace indentation unchanged.
3. Make Backspace on leading space indentation delete exactly one space per press, even if the IME requests a multi-space deletion. Preserve normal deletion in code, deletion at a line start, selections, empty-pair Backspace, composing text, undo/redo, dirty state and autosave. Confirm behavior for actual user input rather than guessing from a `TextFieldValue` shape.
4. Add pure host tests for decision logic and source/integration pins for every edit path. Keep UI/theme, navigation, Settings defaults, completions/ghost suggestions, formatting and unrelated editor controls out of this phase.
5. Validate on a real phone using the owner's normal keyboard and CodeC's in-app keyboard; record keyboard/device, exact snippets, caret result and undo behavior. CI remains the build/test gate; device evidence is separately required before claiming the reported UX is resolved.

## Acceptance checklist (draft)

- Enter after `def f():` and `for item in items:` creates one matching Python indent; each works through both the normal keyboard and CodeC Keys.
- Enter after a comment or non-block colon does not invent an indent; existing brace indentation stays unchanged.
- On a four-space Python indent, each Backspace removes one space, not four; four presses return to the prior level. Backspace on body characters and selected text retains ordinary editor semantics.
- Undo and redo each restore the corresponding single user edit; caret/selection, composing input, dirty marker and autosave remain correct.
- No regression to typed text, auto-pair/empty-pair handling, paste, accepted completion, or multi-line edits; verify relevant cases on-device.
- `Build APK` is green. Owner supplies the phone transcript before the phase is marked device-passed.

## MCQ decisions for owner before implementation

1. **How broad should this typing phase be?**
   - A. Only the two reported defects plus directly affected regressions **(recommended: start narrow, but trace every real input route)**
   - B. Audit and fix the entire typing stack (IME composition, selection, paste, undo, auto-indent, deletion)
   - C. Add typing features as well as fixes

2. **Which input surfaces must pass the device round?**
   - A. System keyboard and CodeC Keys **(recommended minimum)**
   - B. A + hardware keyboard if available
   - C. Every current typing surface, including system keyboard, CodeC Keys, key strip and hardware keyboard where available **(recommended if the owner can exercise them)**

3. **Backspace contract while the caret is inside leading indentation:**
   - A. Remove exactly one literal space per press **(recommended; matches the owner's request)**
   - B. Remove one configured indentation unit
   - C. Use one space for Python and one configured unit for other languages

## Gate

Please confirm the scope choices (or correct them) and then explicitly say **“Start Phase 75.1”** before implementation. Until then, this document is only the requested phase proposal; do not change the typing implementation again. The unrelated 16sp font, ghost-text default and Settings-folding work remain independent and untouched. No PR/merge without the owner's explicit command (`rule.md` §3).

> **Gate resolved 2026-09-30.** The owner's message quoted at the top *is* the start
> command (“find the problems … and fix”), with the device round promised after it. The
> §3 merge gate is untouched: no PR, no merge, nothing to `main` until the owner says so.

---

## Implementation record (2026-09-30) — symptom → root cause → fix

Sora's side of every claim below was read from the **0.24.6 tag** through the GitHub API
on 2026-09-30 (`CodeEditor.java`, `Language.java`, `EditorInputConnection.java`,
`EditorKeyEventHandler.java`, `DirectAccessProps.java`, `text/TextUtils.java`) — the
version the app pins (`gradle/libs.versions.toml:37`). No Android compile or device run
happened in the sandbox; `Build APK` is the executor of record.

### 1. “`def` auto-indents, `for` does not” — two Enter routes that could not see each other

**The routes.** Enter from the system IME lands in
`EditorKeyEventHandler.handleEnterKeyEvent` → `CodeEditor.commitText("\n", applyAutoIndent = true)`;
`DirectAccessProps.autoIndent` is **true** and CodeC never changes it, so
`CodeEditor.java:2122-2162` owns that newline: it counts the current line's leading
whitespace, adds `Language.getIndentAdvance(...)`, and inserts `\n` + that many spaces.
Enter from CodeC Keys is `EditorKey.Insert("\n")` (`KeyboardDefaults.kt:53`) → the VM's
`SmartTyping.handleAutoIndent`, which computes the indent itself and adds `tabSize`.

**Why they disagreed.** `CodeCLanguage.getIndentAdvance` called
`indentAdvanceFor(content.getLine(line))` — with no language argument — and that helper's
Python branch requires `language == PYTHON`, so on the IME route the delta was **always 0**
for Python. Then `SmartTyping.transform` only applies its own auto-indent when the buffer
grew by *exactly one character* (`newValue.text.length != old.text.length + 1 → null`):

| Line typed | Sora inserts | VM sees | Result |
|---|---|---|---|
| `def f():` at column 0 | `\n` (0 copied + 0 delta) | a bare newline → its Python rule fires | indent + 4 ✔ |
| `for i in items:` at indent 4 | `\n    ` (copied indent) | 5 characters → rule skipped | indent copied, **no level** ✘ |

That is the reported contrast exactly: the top-level `def` is the *only* case where the VM
rule gets to answer, and any block one level deeper is decided by an adapter that was not
told which language it was serving.

**The fix.**
- `CodeCLanguage.getIndentAdvance` now passes `language` **and** the caret column (only what
  sits *before* the caret can open a block) **and** the indent step, and
  `indentAdvanceFor` answers in the unit sora documents (`Language.java:121-130`: “delta
  count of indent spaces”; `TextUtils.createIndent` turns it into that many spaces).
- The Python block test has **one owner**: `SmartTyping.opensPythonBlock(lineText)` —
  `def`/`class`/`for`/`while`/`if`/`elif`/`else`/`try`/`except`/`finally`/`with`/`async`/
  `match`/`case`, a trailing `#` comment removed first (so `for i in items:  # walk` earns
  its level), strings skipped (so `s = """sql:` and `x:` do not), and an ordinary colon
  rejected because the line's *first word* must be a block keyword. Both routes call it; a
  pin test fails if either restates the rule.
- `SoraEditorHost` keeps the language's step equal to the editor's tab width, so the IME
  route and the VM route indent by the same amount on any Settings value.

Net effect on the IME route: the level now comes from sora instead of the VM, one space
count either way, and the *change* the VM records is still one edit for undo/dirty/autosave
(the replay into sora is untouched, as the brief demanded).

### 2. “Backspace at the indentation jumps back by the whole indent” — sora's own fast-delete

`EditorInputConnection.deleteSurroundingText(1, 0)` (the Gboard-shaped single press, and
`KEYCODE_DEL` too) is answered by `CodeEditor.deleteText()`, whose first move is
`props.deleteEmptyLineFast` — **true** by default (`DirectAccessProps.java:90`, never
overridden here). When the caret sits in a line whose rest is whitespace only, that branch
deletes **the whole indentation plus the line break above** (`CodeEditor.java:1947-1992`).
An auto-indented Python body line is precisely that shape, so one press answered “4 spaces
and the join” — the owner's “jumps back by the whole indent”.

**The fix, in two layers.**
- At the source: the host's one-time editor block sets
  `props.deleteEmptyLineFast = false` and pins `props.deleteMultiSpaces = 1` (sora's own
  one-space default, declared so a future default can't turn a press into a tab-width
  erase). One press now deletes one character; at column 0 it still joins the line.
- In the buffer: `SmartTyping.handleIndentBackspace(old, newValue)` corrects a deletion that
  removes **more than one** whitespace character immediately before a collapsed caret when
  that run is the line's leading indentation — the editor performs exactly one character of
  it. Selections, ordinary code, trailing spaces after code, empty-pair backspace
  (`(|)` still deletes both) and any deletion that swallows a newline are returned as `null`
  and left exactly as they arrived. `SmartTyping.transform` runs it first, so every surface
  that reports through the VM is covered, not only Gboard.
- `EditorViewModel.applyEditorKey` opts the guard out for the ⌫ flick-up
  (`EditorKey.DeleteWord`): that key means “a word”, and inside indentation the whitespace
  run *is* the word. Everything else — IME, CodeC Keys ⌫ tap and its hold-repeat, hardware
  backspace — keeps the one-space law.
- The guard is deliberately **not** a Settings toggle: it corrects a wrong deletion, it is
  not a preference. No new preference, key, dependency or telemetry.

### 3. Found, not changed (owner's call, out of this phase)

- `indentAdvanceFor` gives a brace opener a delta of **1**, which sora reads as *one space*,
  not one level — so C-family Enter indents by 1 space today (and its `}`-split rule never
  runs on the IME route, for the same length-mismatch reason as the Python case). The brief
  says non-Python brace indentation stays unchanged, so the number is untouched and pinned as
  unchanged by `CodeCLanguageLogicTest`. **To decide:** make the brace delta the tab size too
  (one-line change + two pins), which would align braces with Python on every surface.
- The key strip computes its edits from a captured `TextFieldValue`, so a strip-mapped
  “delete word” cap gets the one-space correction (it does not carry the CodeC Keys opt-out).
  No shipped strip layout has a delete cap; noted rather than re-plumbed.
- The Python rule reads one line, so a colon inside a multi-line string that *ends* the line
  can still be read as a header. Same exposure as the rule it replaced; narrower, never wider.

### Coverage added

- `SmartTypingTest` — the block-keyword matrix, the comment/string/ordinary-colon negatives,
  `def`-vs-`for` at depth through `handleAutoIndent`, “sora already indented → no second
  level”, one-space-per-press across four presses, the tab case, the four “leave it alone”
  shapes, the word-delete opt-out, and the empty-pair regression.
- `CodeCLanguageLogicTest` — the moved `indentAdvanceFor` pin (with its reason), the brace
  number pinned as unchanged, and a route-agreement table that fails if sora's delta and the
  VM's level ever answer differently.
- `EditorTypingRouteWiringTest` (new) — source pins for the seam: the adapter passes
  `language`/step, one owner for the block rule, the language step follows `setTabWidth`,
  the two sora props are set once in the one-time block, the guard is threaded through
  `updateCode`, the sora→VM push keeps it on and only `DeleteWord` opts out.
- Still device-only by nature: what a particular IME actually sends on a real phone, the
  caret's look, and the feel of one-space-per-press. `Build APK` cannot prove those — the
  owner's round is the only evidence, and no claim here anticipates it.

### CI

**`Build APK` ✅ GREEN round 1 — run [`36679767045`](https://github.com/pabi277/CodeC/actions/runs/36679767045)**
on the pushed branch tip `5acac43` (base: `main` @ `dba1361`, the PR #97 merge). Step 8 *host
unit and screenshot tests* success, step 9 *assemble debug APK* success, steps 10–13 the
release set + weight check success, zero error annotations — one round, no stale pin to move,
no for-cause fix needed. Artifacts: debug 25,434,005 B, release 6,291,527 B
(+1,077 B / +0.02 % over the post-PR-#96 run `36675799563`). The docs follow-up (`b7caa93`)
re-ran the same gate: ✅ `36681074743`. No local Gradle/Robolectric/lint
is claimed — the sandbox has no JVM (`rule.md` §5); the pre-run check was a Python mirror of
every new and changed assertion (72 checks: the block matrix, the Backspace shapes, the pin
strings), which passed and which is NOT a substitute for this run.

What CI proves: the four production files compile, the moved `indentAdvanceFor` pin and the
new `SmartTypingTest`/`EditorTypingRouteWiringTest` cases pass, lint is clean. What CI cannot
prove: anything about a phone. The next line is the owner's.

### Device round owed (the owner's, on the build this branch produces)

1. Python file. Type `def f():` + Enter → one level. Type `for i in items:` at column 0 →
   Enter → one level. Enter a second time inside that body → the level is kept. Then
   `if`, `while`, `try:`/`except:`/`finally:`, `else:`, a `class` and a `match`/`case` pair →
   each takes one level.
2. `for i in items:  # walk the list` + Enter → one level (the comment must not hide it).
   `# note:` + Enter → no level. `x:` + Enter → no level.
3. Same checks through **CodeC Keys** (⏎ cap) — both keyboards must agree.
4. On a body line indented by 4: ⌫ once removes one space, four presses reach the previous
   level, a fifth joins the line above. On an auto-indented *empty* line: same, one space at
   a time. Mid-code ⌫ and ⌫-with-a-selection behave normally. `(|)` + ⌫ deletes both sides.
   ⌫ flick-up on an indented line still removes the whole run.
5. Undo/redo after each of the above restores one user edit at a time; the dirty dot and the
   autosaved file agree with what is on screen; typing over a ghost/`print(` suggestion and
   pasting multi-line text stay correct (regression sweep, acceptance row 5).

---

## Phase 75.2 — Device round 1 follow-up (2026-09-30)

### The owner's device report (verbatim)

1. *"If i write def it's auto completes it def fname():\n\n    pass it's not phone friendly because i have to cut that and again write another thing."*
2. *"Int main(){ not auto indenting"*
3. *"None of is working Indentation"*
4. *"I don't run other that much but {} are not indenting i also tryed java same"*
5. *"One more problem is user can't line up the space/indenting between lines so add some line type some thing that indicates each Indentation"*

### Evidence and root causes

| Owner item | Root cause in source | Fix shipped in 75.2 |
|---|---|---|
| **1. `def` completes to `def fname():\n    pass`** | `assets/snippets/python/python.json` (friendly-snippets) has `"def": ["def ${1:fname}($2):", "\t${3:pass}"]` and CodeC's tail has `"def function():\n    "`. When the user types `def` (or `def `, including the Python `def` quick-key cap), `CodeCompletionEngine.completions` puts the `def` snippet at rank 0 on the suggestion strip / ghost / panel. Tapping it inserts the whole `def fname():\n    pass` skeleton, forcing the user to select and delete `fname` and `pass` on a phone screen — even though Phase 75.1 already made Enter after `def my_func():` indent the body by one level. | `SmartTyping.typedBlockKeyword(word)` checks whether the word at the caret is one of the 14 `pythonBlockKeywords` (shared single source of truth with `opensPythonBlock`). When `language == LanguageType.PYTHON` and the current prefix or trigger word is an exact Python block keyword (`def`, `for`, `if`, `elif`, `else`, `while`, `class`, `try`, `except`, `finally`, `with`, `async`, `match`, `case`), `CodeCompletionEngine.completions` suppresses snippets so the strip stays in key mode and no skeleton is pushed. Typing a partial prefix (`de`, `fo`) or an explicit snippet abbreviation (`deft`, `defm`, `ifmain`, `pr`) still surfaces the full snippet set. |
| **2, 3, 4. `int main(){` and `{}` in C / Java / brace languages not auto-indenting** | Phase 75.1 left `CodeCLanguage.indentAdvanceFor`'s brace branch at its historical `1` space (`trimmed.endsWith('{') -> 1`) as a "found, not changed" note while fixing Python. On a phone at 16 sp, 1 space after `int main(){` + Enter looks identical to 0 spaces ("None of is working Indentation"), and it disagreed with `SmartTyping.handleAutoIndent` (the CodeC Keys Enter route), which adds `tabSize.coerceIn(2, 8)` spaces. And `.java` files map to `LanguageType.TEXT`, which still uses `CodeCLanguage.indentAdvanceFor` on Enter — where `trimmed.endsWith('{')` is language-agnostic. | `CodeCLanguage.indentAdvanceFor` now computes `val level = indentStep.coerceIn(2, 8)` once and returns `level` for both `trimmed.endsWith('{')` and `language == LanguageType.PYTHON && SmartTyping.opensPythonBlock(trimmed)`. Both Enter routes (system IME via Sora and CodeC Keys via VM) now indent by the same full level (`4` spaces by default) in C, C++, Java (`.java`), JS/TS, Go, Rust, Shell, etc. |
| **5a. Can't line up spaces/indentation between lines (visual)** | At 16 sp on a phone screen, 3, 4, and 8 leading spaces are blank whitespace with no column reference. Sora's `TextMateAnalyzer.computeBlocks` only draws vertical block lines when a TextMate `language-configuration.json` supplies folding regexes (`cachedRegExp != null`), and CodeC loads `.tmLanguage.json` grammars without language-configuration files (`DefaultGrammarDefinition.withGrammarSource(..., null)`), so `styles.blocks` is always empty. Meanwhile Sora's built-in `EditorRenderer` has a native, zero-allocation non-printable painting pass (`FLAG_DRAW_WHITESPACE_LEADING` + `FLAG_DRAW_WHITESPACE_FOR_EMPTY_LINE`) that draws a subtle dot at the center of every leading whitespace column — including on empty auto-indented lines — without touching inner or trailing spaces. | `SoraEditorHost` enables `CodeEditor.FLAG_DRAW_WHITESPACE_LEADING or CodeEditor.FLAG_DRAW_WHITESPACE_FOR_EMPTY_LINE` in the one-time `remember(editor)` setup and sets `EditorColorScheme.NON_PRINTABLE_CHAR` to `CodecPalette.INDENT_MARK` (`0x80B0B0B0`) inside `LaunchedEffect(theme)` so every theme switch preserves the subtle leading-space guide dots. |
| **5b. Can't line up spaces/indentation between lines (Tab unit)** | A hardware/IME `\t` inserted a literal tab character while auto-indent put spaces, and pressing `TAB` after 2 leading spaces added 4 more spaces (landing on column 6 instead of column 4). | `SmartTyping.indentRun(text, caret, step)` computes the spaces needed to reach the next tab stop (`step - (width % step)`) when the caret sits in leading indentation (and a full `step` elsewhere). Both `SmartTyping.handleTabAsIndent` (rewriting a naive `\t` insert in `SmartTyping.transform`) and `EditorKeySet.apply(EditorKey.Tab, ...)` call `SmartTyping.indentRun`, so pressing Tab on either keyboard always lands on an exact multiple of `tabSize` in spaces. |

### Coverage added / updated in 75.2

- `CodeCLanguageLogicTest` — `a brace opener adds one level, on every step` (4 spaces at default step, 2 at step 2, clamped 2..8, `.java` via `LanguageType.TEXT` included), `the brace level is the same answer on both Enter routes`, and `a level is asked for on both Enter routes, in every language` (cross-product of 8 languages × 3 steps × 22 lines).
- `CodeCompletionTest` + `CompletionCapacityTest` — `the python keyword moment offers no skeleton` (all 14 Python block keywords, with and without trailing space, offer zero snippets while keeping keywords), `non-block triggers and partial prefixes still surface snippets` (`de`, `fo`, `import `, shell `if `, C `for` still surface snippets).
- `SmartTypingTest` + `EditorKeySetTest` — `typedBlockKeyword matches the exact python block keywords and nothing else`, `tab in leading indentation advances to the next tab stop in spaces` (column 0 → 4, column 2 → 4, column 4 → 8, mid-line → full step), and `TAB in leading indentation aligns to the next tab stop`.
- `EditorTypingRouteWiringTest` — updated brace pin to `trimmed.endsWith('{') -> level` and added 3 new wiring pins: `the keyword moment shares the block keyword table with the indent rule`, `both keyboards speak one tab unit through indentRun`, and `leading indentation is painted at the source and survives theme switches`.

### CI (Phase 75.2)

**`Build APK` ✅ GREEN round 1 — run [`36692498787`](https://github.com/pabi277/CodeC/actions/runs/36692498787)**
on tip `e5d66a8` (11m39s, step 8 host unit and screenshot tests, step 9 debug assemble, steps 10–13
release set + weight check, zero error annotations; release artifact 6,291,637 B = +110 B over 75.1
run `36679767045`, debug artifact 25,435,963 B). Pre-validated with `/tmp/verify75_2.py` (68/68
checks pass). Awaiting the owner's device round 2; the §3 merge gate remains in force.

---

## Phase 75.3 — Owner device round 2 follow-up (2026-09-30)

### The owner's device round 2 report (verbatim)

1. *"You make it even more complex and more errors I want I wanted like that when I type if and click on the suggestions it only write the suggestions and not the full if condition etc other parts"*
2. *"and now in c coding I tried to write int main() then curly brackets it sent the brackets inside the first brackets like ({})"*
3. *"And use #include <stdio.h> And then int main() it's erasing the #include <stdio.h>"_

### Evidence and root causes

| Owner item | Root cause in source | Fix shipped in 75.3 |
|---|---|---|
| **1. Clicking a suggestion writes the full condition/body instead of just the suggestion word** | Phase 75.2's `keywordMoment` hid snippets only when an exact Python block keyword had already been typed (`def`, `for`), which hid suggestions when `< 2` candidates remained on `StripContext` while clicking a suggestion in C (`if` → `if (true) {\n\t\n}`, `if (condition) {`, `for (size_t i = 0; …)`) or in Python at `de`/`tr` still inserted the resolved VS Code placeholder condition and multi-line body skeleton. | Removed `keywordMoment` from `CodeCompletionEngine.completions` so suggestions stay uniformly visible while typing, and added `CodeCompletionEngine.suggestionInsertText(item: CompletionItem)` used by both `EditorViewModel.acceptCompletionItem` (strip chips) and `CodeCLanguage.requireAutoComplete` (`⌄ more` panel). Code snippets resolve to their suggestion word (`if`, `def`, `for`, `while`, `switch`, `try`, `class`, `printf`, `print`, `return`, `import`, `main` / `int main`, `else if`, `typedef struct`), while single-line `#include` directives (`#include <stdio.h>\n`, `#include <>`), shebangs (`#!…`), HTML tags/DOCTYPE (`<…>`), Markdown/CSS, and Emmet expansions keep their `insertText`. |
| **2 & 3 (Cause A). Stale `textFieldValue` captured in `EditorKeysRow.kt` `.pointerInput(def)`** | In `EditorKeysRow.kt`, `EditorKeyCap` (`:236`) and `RunKeyCap` (`:427`) used `.pointerInput(def)` without `rememberUpdatedState(onKey)` and without a live-buffer `commitKey` callback (unlike `CodecKeyboard.kt:78-83,173` from Phase 28.2 round 2). Because `def` is stable across recompositions while `StripContext.Keys` stays mounted, `.pointerInput(def)` never restarted and kept calling the initial `onKey` closure with the `textFieldValue` from when `EditorKeysRow` first mounted: (a) opening a `.c` file (`textFieldValue = ""`), tapping `#include` (`#include <stdio.h>\n`), and then tapping `int ` / `main(` / `()` applied against the stale `""`, **erasing `#include <stdio.h>`**; (b) typing `int main` (which shows `Suggestions`) and then typing `(` (which auto-pairs to `int main(|)` at index 9 and mounts `EditorKeysRow` with `"int main(|)"`), typing `)` to move to index 10, and tapping `{}` on `EditorKeysRow` applied against the stale `"int main(|)"` at index 9, **inserting `{}` inside `()` as `int main({})`**. | `EditorKeyCap`, `RunKeyCap`, and `SuggestionStrip` now wrap their gesture callbacks in `rememberUpdatedState`, and `EditorKeysRow` + `BottomStrip` (`EditorScreen.kt`) pass `commitKey = { key -> viewModel.applyEditorKey(key, autoIndent = autoIndent, tabSize = tabSize, suppressAutoPair = true) }` so every strip keycap applies directly against the ViewModel's live `_codeText.value`. |
| **2 & 3 (Cause B). `SoraEditorHost.kt` `SelectionChangeEvent` ordering & post-replace cursor sync** | (1) In Sora 0.24.6, `CodeEditor` is `contentListeners[0]` and our `contentListener` (`pushToVm`) is `contentListeners[1]`. Inside `CodeEditor.afterInsert`/`afterDelete`, Sora fires `SelectionChangeEvent(CAUSE_TEXT_MODIFICATION)` *before* `pushToVm` runs — while `syncedText` is still the pre-edit text and `event.left.index` is the post-edit cursor. Whenever any character followed the caret (such as `)` in `int main(|)`), `SelectionChangeEvent` advanced `old.selection.start` in `_codeText.value` to `pos + 1` before `pushToVm` ran, causing `if (newPos != pos + 1) return null` in `SmartTyping.handleTypeOver`, `handleAutoPair`, `handleAutoIndent`, `handleDedentOnCloser`, and `handleTabAsIndent` to fail! And if the VM held a pending programmatic edit (`#include <stdio.h>\n`), a pre-replay `SelectionChangeEvent` pushed `syncedText` back over `_codeText.value`. (2) At `SoraEditorHost.kt:626`, after `ed.text.replace` deleted the duplicate `)` from `int main())`, Sora's internal `ed.cursor.left` sat at `9` while both `target.selection` and `syncedSelection` were `10`, so `target.selection != syncedSelection` was `false` and `ed.setSelection` was skipped — leaving Sora's real cursor inside `int main(|)`! | `SoraEditorHost`'s `SelectionChangeEvent` receiver now ignores `SelectionChangeEvent.CAUSE_TEXT_MODIFICATION` and returns early when `syncedText != viewModel.codeText.value.text`; and `AndroidView.update` checks `cursorDrifted` (`ed.cursor.left != start || ed.cursor.right != end`) in addition to `target.selection != syncedSelection` so `ed.setSelection` always restores Sora's cursor after `ed.text.replace`. |
| **2 (Cause C). Tapping `{}` or typing `{` while the caret is still inside empty `(|)`** | After tapping the `()` quick-key cap or typing `(` with auto-pair, the caret sits inside `int main(|)`. Tapping `{}` or typing `{` immediately (without first stepping past `)`) inserted `{}` at the caret inside `()` → `int main({|})`. | Both `EditorKeySet.apply(EditorKey.Pair("{", "}"), ...)` and `SmartTyping.handleAutoPair` + `SmartTyping.handleBraceInEmptyParens` detect when `{` / `{}` is inserted at a collapsed caret inside empty parentheses `(|)` and step past `)` to produce `int main(){|}`. |

### Coverage added / updated in 75.3

- `CodeCompletionTest` + `CompletionCapacityTest` — `clicking a code suggestion writes only the suggestion word and not the condition or body` (verifies `if`, `for`, `while`, `printf`, `def`, `try`, `print` across C, Python, and Shell, plus `#include <stdio.h>\n` and `<!DOCTYPE html>` preservation).
- `SmartTypingTest` + `EditorKeySetTest` — `typing open brace inside empty parens steps outside to form function body` (single `{` step 1, Sora `SymbolPairMatch` step 2, and batch `{}`), `curly brace pair key inside empty parens steps outside to form block`, and `c quick keys sequence keeps include header and places braces after main parens` (`#include` → `int ` → `main` → `()` → `{}` produces `#include <stdio.h>\nint main(){|}`).
- `EditorTypingRouteWiringTest` — `clicking a suggestion on either surface writes only suggestionInsertText`, `the keys row commits against the live buffer and refreshes pointerInput lambdas`, and `selection events during text modification or pending VM edits cannot clobber the buffer` (11 total wiring pins).

### CI & Device Acceptance (Phase 75.3)

- **`Build APK` ✅ GREEN round 1 — run [`36701799600`](https://github.com/pabi277/CodeC/actions/runs/36701799600)** on tip `26dbf9e` (7m 48s, step 8 host unit and screenshot tests, step 9 debug assemble, steps 10–13 release set + weight check, zero error annotations; release artifact `6,292,527 B`, debug artifact `25,440,267 B`).
- **Owner device pass (2026-09-30, verbatim):** *"Ok device test passed … You can complete the docs part and merged"* — merged to `main` via [PR #98](https://github.com/pabi277/CodeC/pull/98).





