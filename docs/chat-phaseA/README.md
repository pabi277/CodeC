# CodeC Phase A — Editor Touch Smoothness & Keyboard-Anchored Shortcuts

**Status:** 📋 **PLANNED** — not yet started. Awaiting owner's explicit "Start Phase A" command.
· **Cost:** `[client-only]` — pure Kotlin/Compose; no `[repo-build]`, no native changes
· **Depends on:** nothing (can start in parallel with Phase C)
· **Blocks:** Phase B (Phase B's inline PTY input builds on the IME inset work from A.2.4)

> **Owner signal:** "the editor smoothness because it not good at touch, feels like
> stuck, the shortcuts key are not above the keyboard etc"
>
> Full research & design rationale:
> [`docs/RESEARCH_NEXT_PHASES.md`](../RESEARCH_NEXT_PHASES.md) §Phase A.

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
| **A.1** | Scroll + recomposition decoupling (fix the "stuck" feeling) | `BasicTextField` owns scrolling; debounced off-thread highlight; narrowed `remember` keys; baseline profile | [PART_A1_SMOOTHNESS.md](PART_A1_SMOOTHNESS.md) |
| **A.2** | IME-anchored editor keys strip (fix "not above keyboard") | Keys strip pinned to `WindowInsets.ime`; language-adaptive key set; user-editable; only visible when IME is open | [PART_A2_IME_KEYS.md](PART_A2_IME_KEYS.md) |
| **A.3** | IME insets + caret visibility | `imePadding()` / `WindowCompat.setDecorFitsSystemWindows`; caret never hidden; orientation + predictive-back safe | [PART_A3_INSETS.md](PART_A3_INSETS.md) |

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
  overload instead. Record the BOM version in `PART_A1_SMOOTHNESS.md` §7.
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
