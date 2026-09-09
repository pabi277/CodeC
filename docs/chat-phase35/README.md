# CodeC Phase 35 — Editor typing feel

> **Status:** 📋 PLANNED (researched + specced, not implemented) ·
> **Cost:** `[client-only]` · **Effort:** M · **Owner row:** *"The editor is
> good but some option like always keyboard stay open, the typing is not smoth
> like normal keyboard, the cursor is very bouci while typing, and open a file
> initialization the corsure to the top of the file i want it to be fully
> remove if user clicks anywhere the cursor than start working"*

Four sub-points → four parts, one shared surface (`EditorScreen` +
`SoraEditorHost` + `EditorViewModel` + `CodecKeyboard`):

```text
  35.1  "always keyboard stay open"      → a Settings toggle, no silent dismiss
  35.2  typing not smooth                → measure keystroke p95, cut main-thread work
  35.3  cursor very bouncy while typing  → solid caret while typing (no re-animation)
  35.4  caret initialized to top of file → NO caret on open until the first tap
```

| Part | Title | Cost | Effort | Status |
|---|---|---|---|---|
| [35.1](PART_35_1_KEYBOARD_STAY.md) | Always keyboard stay open | client-only | S | 📋 planned |
| [35.2](PART_35_2_SMOOTH_TYPING.md) | Smooth typing | client-only | M | 📋 planned |
| [35.3](PART_35_3_CURSOR_BOUNCE.md) | Non-bouncy cursor | client-only | S | 📋 planned |
| [35.4](PART_35_4_NO_CARET_ON_OPEN.md) | No caret until first tap | client-only | S | 📋 planned |

**Open-source-first reference** (`docs/PHASE34_37_OSS_RESEARCH.md` §2): sora-editor
is an **LGPL-2.1 binary dependency** — its cursor/selection config is read from
its public API (exact method verified at implementation), never pasted; jackpal's
Apache-2.0 terminal documents the "solid caret + don't re-animate per keystroke"
behavior target; Gboard/SwiftKey are behavior references only. **No new
dependency** — this phase is the in-tree typing path + sora's cursor config.

## The typing path today (evidence — read from source)

CodeC Keys cap press → `CodecKeyboard` `commitKey(key)` →
`EditorViewModel.applyEditorKey` → `EditorKeySet.apply` (allocates a new
`TextFieldValue`) → `updateCode` → `SmartTyping.transform`, `undo.recordChange`,
dirty compute, `scheduleAutoSave()`, `scheduleDecorationRefresh()` →
`_codeText` StateFlow → `SoraEditorHost` AndroidView update → `ed.setText`
(text changed) **or** `ed.setSelection` (caret moved) → sora `ContentListener`
echoes back into `updateCode`. Every keystroke therefore:
- allocates a full-buffer `TextFieldValue`,
- recomputes decorations (`refreshDecorationsNow`: line/column scan +
  `BracketMatcher` + find highlights) after a 20 ms debounce,
- replays the caret into sora (`setSelection`), which restarts sora's cursor
  blink phase → the **"bouncy"** caret.

This is not a guess to be fixed blind: **35.2 ships a measurement card first**
and each part fixes the specific number it finds. See the part docs.

## Cross-device risks to watch

- OEM IME composing spans (Gboard/SwiftKey) — the ghost already documented
  this; the keyboard-stay and smoothness work must not regress composition.
- Low-end SoCs: a 14.5 ms p95 measured on a bench device is not the p95 on
  the owner's slowest phone — the measurement card must run there.
- `WindowInsets.ime` animation differs across OEMs (the keys row rides it).
