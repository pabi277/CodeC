# Phase 75.1 — Editor typing reliability

**Status: 🚧 IMPLEMENTED on `arena/01a0f0fb-codec` (2026-09-30), CI pending/recorded
below; device round owed by the owner.** This page was the draft; the owner then
commanded it into being:

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

`Build APK` on the pushed branch: recorded in `docs/NEXT_STEPS.md` and `prompt.md` (the run
id lands here when green). No local Gradle/Robolectric/lint is claimed — the sandbox has no
JVM (`rule.md` §5).

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

