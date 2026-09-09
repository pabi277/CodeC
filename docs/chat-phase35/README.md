# CodeC Phase 35 — Editor typing feel

> **Status:** 🚧 IMPLEMENTED on the Phase 35 session branch; CI + owner
> cross-device round pending · **Cost:** `[client-only]` · **Effort:** M ·
> **Owner row:** *"The editor is good but some option like always keyboard
> stay open, the typing is not smoth like normal keyboard, the cursor is very
> bouci while typing, and open a file initialization the corsure to the top i
> want it to be fully remove if user clicks anywhere the cursor than start
> working"*

Four sub-points → four parts, one shared surface (`EditorScreen` +
`SoraEditorHost` + `EditorViewModel` + `CodecKeyboard`):

```text
  35.1  "always keyboard stay open"      → a Settings toggle, no silent dismiss
  35.2  typing not smooth                → coalesce decoration work off main
  35.3  cursor very bouncy while typing  → solid caret while typing (no re-animation)
  35.4  caret initialized to top of file → NO caret on open until first tap
```

| Part | Title | Cost | Effort | Status |
|---|---|---|---|---|
| [35.1](PART_35_1_KEYBOARD_STAY.md) | Always keyboard stay open | client-only | S | 🚧 implemented |
| [35.2](PART_35_2_SMOOTH_TYPING.md) | Smooth typing | client-only | M | 🚧 implemented; measurement pending |
| [35.3](PART_35_3_CURSOR_BOUNCE.md) | Non-bouncy cursor | client-only | S | 🚧 implemented |
| [35.4](PART_35_4_NO_CARET_ON_OPEN.md) | No caret until first tap | client-only | S | 🚧 implemented |

**Open-source-first reference** (`docs/PHASE34_37_OSS_RESEARCH.md` §2): sora-editor
is an **LGPL-2.1 binary dependency** — its cursor/selection config is read from
its public API (verified against the resolved 0.24.6 surface), never pasted;
the cursor target is the solid-caret/no-per-key-reanimation behavior used by
mobile terminals and editors. **No new dependency** — this phase is the
in-tree typing path + sora's public cursor config.

## Implementation record

- `SettingsManager.EDITOR_KEEP_KEYS_OPEN` stores `editor.keep_keys_open` with
  a default of `true`; `KeysStayPolicy` keeps the keyboard mounted while the
  editor session is focused, preserves the explicit toolbar collapse, and
  hands the system IME back for interactive stdin.
- `CaretPlacementPolicy` and the VM's per-open `caretPlaced` flow keep a newly
  opened file quiet. `SoraEditorHost` clears focus, skips selection replay and
  scroll-to-caret until a real tap/focus event; a CodeC Keys cap places an
  unplaced edit at the end of line one.
- `SoraEditorHost` disables sora cursor animation, uses
  `setCursorBlinkPeriod(0)` during the active typing window, and restores a
  normal blink period after `CaretBlinkPolicy.REARM_AFTER_MS`.
- `EditorDecorationSnapshot` computes line/current-line/bracket state off the
  main dispatcher after the 80 ms debounce. `DecorationDirtyPolicy` prevents
  find matching from being repeated for every typed character. Stale snapshots
  are discarded and VM→sora selection replay is deduplicated.
- Pure tests cover key visibility, caret placement, blink timing, decoration
  invalidation, and the extracted snapshot math. The complete build/test gate
  is CI; this sandbox has no JDK.

## Measurement gate

See [`MEASUREMENT_CARD.md`](MEASUREMENT_CARD.md). The prior Phase 28.1 bench
reference is 14.5 ms keystroke p95 on 5,000 lines; Phase 35 does not claim a
new p95 until the owner runs the 2,000/10,000-line card on the slowest phone.

## Cross-device risks to watch

- OEM IME composing spans (Gboard/SwiftKey) — the ghost already documented
  this; the keyboard-stay and smoothness work must not regress composition.
- Low-end SoCs: the prior 14.5 ms p95 is not a result for the owner’s slowest
  phone — complete the measurement card there.
- Screen size / density and `WindowInsets.ime` animation differences — the
  keys row must not disappear during an IME transition.
