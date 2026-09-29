# Proposed Phase 75.1 — Editor typing reliability

**Status: owner-requested phase draft; not approved to implement yet.** The owner asked for a dedicated phase focused only on typing experience because writing code is CodeC's core feature. At the owner's request, the initial code-writing changes from the 65.1/74.1 chat were undone; the reports below remain open. Additions here are a scope proposal and source review, not a diagnosis or fix claim.

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
