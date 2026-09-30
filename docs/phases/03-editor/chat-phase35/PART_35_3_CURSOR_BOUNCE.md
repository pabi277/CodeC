# CodeC Phase 35.3 — Non-bouncy cursor

**Status:** 🚧 IMPLEMENTED · **Cost:** `[client-only]` · **Effort:** S

## Symptom (owner)

*"the cursor is very bouci while typing"* — the caret visibly jumps/blinks
during typing instead of gliding with the inserted text.

## What exists today (evidence — why it bounces)

Every keystroke ends in `SoraEditorHost`'s AndroidView update:
- text changed → `ed.setText(target.text)` then `ed.setSelection(…)`;
- or caret moved → `ed.setSelection(…)` alone.

sora re-renders its cursor on each of those calls, and each `setSelection`
restarts the cursor's blink phase — so while typing, the caret re-animates on
every character (blink-off/on re-arm) and can visibly jump between the
programmatic replay and sora's own native caret. That is the "bouncy" feel.
(The `ContentListener` echo also round-trips the selection through the VM.)

## Design

1. **Solid caret while typing** — the standard "normal keyboard" behaviour: a
   cursor that is **steady (no blink)** during active input, blinking only
   when idle. Implement by holding sora's cursor in a solid state while
   keystrokes are within a short window (e.g. blink re-arms only after ~500 ms
   of no input).
2. **One caret move per keystroke, not two** — dedupe the VM→sora selection
   replay against the selection sora already holds (the existing
   `syncedSelection` fast-path is for caret-only moves; make the text-change
   replay also skip a redundant `setSelection` when the listener echo already
   carries the same selection).
3. **Use sora's public cursor knobs** — the resolved 0.24.6 `CodeEditor`
   surface exposes `setCursorAnimationEnabled(false)` and
   `setCursorBlinkPeriod(int)`. CodeC disables animated travel once, sets the
   period to zero during the active typing window, and restores the normal
   period after 500 ms without input. No new dependency or custom overlay is
   needed.

## Exit condition

```text
(Device)
1. Type a burst of characters: the caret glides with the text, no per-key
   blink/jump.
2. Stop typing ~0.5 s: the caret resumes its normal idle blink.
3. Selection (drag/tap) still renders and does not regress (Phase 25/32 laws).
PASS = all three.
```

## Tests

- Pure policy (e.g. `CaretBlinkPolicy`): `(lastEditDelta, isTyping) → solid|blink`
  — pins the re-arm window so the behaviour is a law, not a magic number.
- No regression to `SoraEditorHost`'s selection sync (existing CI tests).
