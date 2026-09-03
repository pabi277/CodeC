# CodeC Phase 22 — Editor Touch Smoothness & Keyboard-Anchored Shortcuts

**Status:** 🟡 **IMPLEMENTED — awaiting CI green + owner device pass** (started
2026-09-03 on owner command "start phase 22"; branch `arena/01a065a0-codec`).
A.1 shipped minus the scroll-model rewrite (deferred — the `scrollState`
parameter does not exist on the `TextFieldValue` overload of `BasicTextField`
at Compose BOM 2024.09.00); A.2 and A.3 shipped in full.
· **Cost:** `[client-only]` — pure Kotlin/Compose; no `[repo-build]`, no native changes
· **Depends on:** nothing (can start in parallel with Phase 20)
· **Blocks:** Phase 23 (Phase 23's inline PTY input builds on the IME inset work from Phase 22.2)

> **Owner signal:** "the editor smoothness because it not good at touch, feels like
> stuck, the shortcuts key are not above the keyboard etc"
>
> Full research & design rationale:
> [`docs/RESEARCH_NEXT_PHASES.md`](../RESEARCH_NEXT_PHASES.md) §Phase 22.

---

## Why this exists

Two distinct problems, both reported on device:
1. **Jank / "stuck" feeling** — typing, scrolling, and selecting in the editor
   on a phone drops frames. The root cause is O(n) work per keystroke:
   full-file syntax highlight rebuild, gutter string rebuild, and double-scroll
   modifier fighting with `BasicTextField`'s own scroll.
2. **Shortcut keys are not above the keyboard** — when the soft IME opens,
   the existing `EditorKeysRow` is pushed below the keyboard or hidden; there
   is no quick-key strip (Tab, `{}`, `()`, `;`, arrows, Undo/Redo, snippets)
   floating *above* the keyboard the way Termux's extra-keys row does.

Both problems are phone-first issues. Tablet/foldable gets better with A.3,
but the measurement target is a mid-range phone in release mode.

---

## The three parts

| Part | Title | What it delivers | Doc |
|---|---|---|---|
| **A.1** ✅* | Scroll + recomposition decoupling (fix the "stuck" feeling) | `BasicTextField` owns scrolling; debounced off-thread highlight; narrowed `remember` keys; baseline profile | [PART_22_1_SMOOTHNESS.md](PART_22_1_SMOOTHNESS.md) |
| **A.2** ✅ | IME-anchored editor keys strip (fix "not above keyboard") | Keys strip pinned to `WindowInsets.ime`; language-adaptive key set; user-editable; only visible when IME is open | [PART_22_2_IME_KEYS.md](PART_22_2_IME_KEYS.md) |
| **A.3** ✅ | IME insets + caret visibility | `imePadding()` / `WindowCompat.setDecorFitsSystemWindows`; caret never hidden; orientation + predictive-back safe | [PART_22_3_INSETS.md](PART_22_3_INSETS.md) |

---

## ⚖️ Ground rules

- **Clean-room law:** `TerminalExtraKeys` (Phase 6) is CodeC's own code —
  reuse or fork it freely. The Termux extra-keys *concept* is public behavior;
  the clean-room rule applies to closed-source apps (Spck, Coding C, Pydroid 3).
- **Phone-first:** all three parts are measured on a mid-range phone in a
  **release APK** (debug builds are notoriously slow in Compose; never benchmark
  debug). Never regress on tablet — but phone is the primary target.
- **No PR/merge and no push to `main` without the owner's explicit command.**
- **Client-only:** no `[repo-build]`, no bootstrap changes, no native (JNI) changes.
- All new logic must be host-unit-testable where possible.

## 🔎 Research prompts (do before implementing each part)

- **A.1 — scroll model:** Read the Compose Foundation 1.7 `TextFieldState` +
  `BasicTextField(TextFieldState)` docs before deciding whether to migrate
  (migration may require a Compose BOM bump). Check the current BOM version in
  `app/build.gradle.kts`. If the BOM version does not include `TextFieldState`,
  the A.1 fix targets the `scrollState` param of the existing `BasicTextField`
  overload instead. Record the BOM version in `PART_22_1_SMOOTHNESS.md` §7.
- **A.1 — highlight cost:** Look up `derivedStateOf` vs `remember(key)` in the
  Compose performance guide before auditing `EditorScreen`. These are two different
  tools with different semantics; the audit may find places where both are wrong.
- **A.2 — IME insets:** Read the `WindowInsets.ime` / `imePadding()` Compose
  docs for the resolved BOM (the API changed between Compose 1.5 and 1.7).
  Confirm whether `windowSoftInputMode="adjustResize"` is already set in
  `AndroidManifest.xml` (check `MainActivity` or the `<activity>` tag).
- **A.2 — `TerminalExtraKeys` reuse:** Read `TerminalExtraKeys.kt` and
  `TerminalKeyView.kt` to understand the key-grid + macro format before deciding
  whether to fork or parameterize for the editor use case.

## Standing rules (unchanged)

- **No PR/merge and no push to `main` without the owner's explicit command.**
- CI (`Build APK`) = assemble + unit tests + lint — the only test executor.
- Verify state (`git status`, `gh run list`) before acting.
- Honor all Phase 6/7/9/11 invariants (PTY contract, multi-session routing,
  editor undo history, output panel, find/replace).

---

## Round 1 outcome (2026-09-03)

| Part | State | Notes |
|---|---|---|
| A.1 | ✅ partial | Debounced (80 ms) off-thread highlight via `HighlightedCode` + `EditorViewModel.highlighted`; decoration fast path; `tabViews`/`completionItems` keys narrowed; gutter cached. **Deferred:** the double-scroll rewrite (needs a `TextFieldState` migration) and the baseline profile (needs a device Macrobenchmark run). |
| A.2 | ✅ | Keys row is now the last child of the `imePadding()`'d column while the IME is open, so it rides flush on the keyboard; docked Phase 16 position when the keyboard is closed. Key sets were already language-adaptive. |
| A.3 | ✅ | `imePadding()` on `EditorScreen`'s root column only. `enableEdgeToEdge()` and `adjustResize` were already in place. |

**New tests:** `EditorHighlightCacheTest` (8 host tests).

**Device pass required** before this phase can be called complete — run
`PART_22_1_SMOOTHNESS.md` §4, `PART_22_2_IME_KEYS.md` §4 and
`PART_22_3_INSETS.md` §4 (the last one includes the Terminal regression check).

---

## Device round 1 (2026-09-03) — owner: *"still lacks when i open keyboard, typing not good, terminal blocks the editor"*

Fixed in `1417642` (CI `33720029914`) — full analysis in
[`PART_22_1_SMOOTHNESS.md`](PART_22_1_SMOOTHNESS.md) §9.

| # | Symptom | Root cause | Fix |
|---|---|---|---|
| D5 | Keyboard-open lags | `filter()` runs per **layout**, and the IME animation relayouts every frame → the whole decorated string was rebuilt 30–60× during the slide. The 22.1 debounce couldn't help: the text never changed. | Single-entry memo in `SyntaxVisualTransformation`, keyed on the buffer text. |
| D6 | Typing still not good | The gutter's enclosing scope still read `codeText.text` to get `lineCount`, so every keystroke invalidated it — `remember(lineCount)` only saved the string, not the recomposition/measure/draw. | `derivedStateOf` line count: the scope invalidates only when the count changes. |
| D7 | "Terminal" blocks the editor | The collapsed Output Panel strip was an unconditional fixed `64.dp`, reserved before anything had ever run. | New pure `OutputRunState.hasContent()` gates it; strip **and** status bar also hidden while the IME is up. The **expanded** panel is left alone. |

**New tests:** `OutputPanelVisibilityTest` ×5, plus 2 memo cases in `EditorHighlightCacheTest`.

---

## Device round 2 (2026-09-03) — owner: *"still in a long file it lags"* + *"quick keys make pare in single key"*

Fixed in `a751cf4` (CI `33722090650`) — full analysis in
[`PART_22_1_SMOOTHNESS.md`](PART_22_1_SMOOTHNESS.md) §10.

| # | Symptom | Root cause | Fix |
|---|---|---|---|
| D8 | Long files still lag | **Not a rendering bug** — `updateCode` stashed the active buffer into `_openTabs` on every character, rebuilding the whole tab list, re-copying a data class that holds the entire `TextFieldValue`, and publishing a new `StateFlow` identity that woke every tab collector. Cost scaled with the FILE, not the edit. | Stash only at the real boundaries (switch/open/close/save/context), as `stashActiveTabBuffer`'s own KDoc always described. All `tab.buffer` readers audited for staleness. |
| D9 | (same) | `refreshDecorationsNow` built the caret line with `text.take(cursor).count { … }` — a copy of the entire prefix per keystroke — then rescanned for the line start. | One in-place pass yields line **and** line start, no allocation. Pinned by `EditorCursorMathTest` against the old code as an oracle. |
| D10 | Quick keys insert one character | Each bracket half was its own cap. | New `EditorKey.Pair`: `()`, `{}`, `[]`, `<>`, `""`, `''`, JS `` `` `` — caret lands between, or the pair surrounds the selection. Also frees row space. |

**New tests:** `EditorCursorMathTest` ×6 (incl. a 500-line oracle comparison), 4 new pair cases in `EditorKeySetTest`.

> **Why rounds 1–2 didn't fix the lag:** both were render-side (highlight
> memo, gutter scope). D8/D9 are in the **ViewModel's edit path** — work done
> before a frame is even requested. Worth remembering if long-file lag ever
> resurfaces: measure the edit path, not just recomposition.

---

## Device round 3 (2026-09-03) — owner: *"still lags and not even less lag then before"* + *"add different quick suggestions accordingly to the language"*

Fixed in `1b06dec` + `ba81bf0` (CI `33724364238`) — full analysis in
[`PART_22_1_SMOOTHNESS.md`](PART_22_1_SMOOTHNESS.md) §11.

| # | Symptom | Root cause | Fix |
|---|---|---|---|
| D11 | Still lags, no better | **`CodeCompletionEngine.completions()` ran synchronously on the main thread every keystroke**, compiling a fresh `Regex` and sweeping the WHOLE buffer for identifiers. Dwarfed everything rounds 1–3 touched. | `produceState` + 120 ms debounce + `Dispatchers.Default`; `Regex` compiled once; scan windowed to `SCAN_WINDOW` around the caret. |
| D12 | No suggestions on some files | `snippets()` had no HTML/CSS or Markdown branch — both hit `else -> emptyList()`. | Full HTML/CSS + Markdown snippet sets, HTML trigger words, Shell shebang/`read -r`. |
| D13 | (found by CI) | `snippetMatches` was case-sensitive — lowercase `doc` missed `<!DOCTYPE html>`. | Case-insensitive matching; word-split `Regex` hoisted to a constant. |

### ⚠️ Correction to the Phase 22.1 record

§8 claimed the completion cost was fixed by converting `remember(codeText, …)`
to `derivedStateOf`. **That was wrong.** `derivedStateOf` only helps when the
derived *value* changes less often than the state it reads; this derivation
reads `codeText` and is read by the popup in the same frame, so it recomputed
every keystroke anyway. It looked like a fix in the diff and did nothing at
runtime. The same overclaim was corrected in the gutter comment (there
`derivedStateOf` does help — but by skipping *recomposition*, not the count).

**New tests:** 7 in `CodeCompletionTest`.
