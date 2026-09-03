# CodeC Phase 23 — Interactive Run UX: Inline PTY Input (remove the input box)

**Status:** ✅ **COMPLETE & DEVICE-ACCEPTED** (2026-09-03, owner: "Phone test passed")
— B.1 (inline input) + B.2 (run keys) done; CI green `33735687876`; both §4
device recipes passed on device.
· **Cost:** `[client-only]` — pure Kotlin/Compose; no `[repo-build]`, no native changes
· **Depends on:** Phase 22.2 (IME-anchored keys strip — the `↵ Enter` / `Ctrl+C` keys
  that appear above the keyboard during interactive runs come from A.2's infrastructure)
· **Blocks:** nothing

> Full research & design rationale:
> [`docs/RESEARCH_NEXT_PHASES.md`](../RESEARCH_NEXT_PHASES.md) §Phase 23.

---

## Why this exists

The Output Panel currently has a separate `OutputInputRow` text field at the bottom
for interactive programs (scanf/gets). This looks out-of-place and is not how
users expect input to work in an IDE Output Panel. The owner wants:

> "remove the input box — when program asks for input, user types directly in the
> output area"

The goal is the C4droid / Pydroid feel: output scrolls, input cursor appears
inline at the bottom of the output area, user types and presses Enter, the line
is sent to the program's stdin. No separate input row.

---

## The two parts

| Part | Title | What it delivers | Doc |
|---|---|---|---|
| **B.1** ✅ | Remove `OutputInputRow`; add inline input at the bottom of `OutputPanelView` | The last "line" in the Output Panel becomes an editable field when an interactive program is running; Enter sends the line to PTY | [PART_23_1_INLINE_INPUT.md](PART_23_1_INLINE_INPUT.md) |
| **B.2** ✅ | Extra-keys integration for interactive runs | When the Output Panel is in input mode, the IME-anchored keys strip (Phase 22.2) shows `↵ Enter`, `Ctrl+C`, `Tab` instead of editor keys | [PART_23_2_RUN_KEYS.md](PART_23_2_RUN_KEYS.md) |

---

## ⚖️ Ground rules

- **Clean-room law:** C4droid and Pydroid 3 are closed-source; match the visible
  behavior (inline input in the output area) not any internal implementation.
- **No PR/merge and no push to `main` without the owner's explicit command.**
- **Client-only:** no `[repo-build]`, no bootstrap changes, no PTY/JNI contract
  changes. The `InteractiveRunSession.sendLine()` call is reused unchanged.
- Phase 23 is **additive**: non-interactive runs (programs that don't read stdin)
  are completely unaffected. The inline field only appears when `interactive = true`
  and the program is running.

## Standing rules (unchanged)

- CI (`Build APK`) = assemble + unit tests + lint — the only test executor.
- Verify state before acting; part done only when device-verified.
- No regression to: find/replace, autocomplete, editor tabs, terminal tab,
  Python runs, HTML preview.
