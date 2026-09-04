# CodeC Phase 25.4 — Caret, Selection & Magnifier Layer

**Status:** 📋 PLANNED · **Cost:** `[client-only]` · **Effort:** M
· **Depends on:** the winning core (25.2 or 25.3)
· **Target files:** `ui/editor/*` (new pointer layer), `EditorScreen.kt`,
   Settings (gesture toggles)

---

## 1. Design

Touch caret work is where phone editors live or die, and it's largely
independent of which core wins. Behavior checklist synthesized from Sora
(Magnifier, SelectionHandle handles, `SelectionMovement` word-drag), Squircle
CE (extended-keyboard selection aids), FlorisBoard/Gboard gesture vocabulary,
and the Phase 9 editor rules already in CodeC:

| Gesture | Behavior | Source behavior referenced |
|---|---|---|
| Tap | place caret; snap to grapheme boundary | every editor |
| Long-press + drag | enter precise-caret mode; **magnifier loupe** above finger (its own toggle) | Sora Magnifier |
| Double-tap | select word (language-aware identifier rules: `_` inside words) | Sora SelectionMovement |
| Double-tap-and-hold, drag | word-grab selection extension | Gboard/Sora vocabulary |
| Selection handles | draggable start/end handles that also show the magnifier | Sora SelectionHandle |
| Selection action bar | Cut/Copy/Paste/Select-all/Comment/Duplicate — compact floating bar, never blocking the strip | Sora `EditorTextActionWindow` (behavior only; GPL-free impl) |
| Spacebar-slide (IME-level) | recorded as **Settings tip** pointing at IMEs that support it (FlorisBoard/Gboard) — CodeC does not re-implement inside the editor | FlorisBoard |
| Arrow caps | Phase 22 strip arrows keep working; **repeat-on-hold** (150 ms delay, 40 ms repeat) | Termux key repeat behavior |
| Fast scroll | drag-scrollbar with line-number bubble when file > 400 lines | Squircle/Sora scrollbar practices |

All gestures individually toggleable in Settings → Editor (research finding:
coding users are split on gestural input; every gesture needs an off-switch,
and the conflict list with pinch-zoom (`FontSizeZoom`) and the find dialog's
focus must be enumerated in code review).

## 2. Implementation steps

1. Pointer-event layer above the editor surface with a gesture state machine
   (tap / long-press-drag / double-tap / double-tap-drag / handle-drag) —
   pure function `reduce(state, event, config) → gesture/action`, host-tested.
2. Magnifier implementation on chosen core (Sora: feature-flag on;
   compose2: draw loupe from layout snapshot).
3. Handles + action window wiring to existing VM methods
   (`toggleLineComment`, `duplicateLine`, cut/copy/paste).
4. Strip arrows: add hold-repeat in `EditorKeysRow` (pure timing tests
   host-side).
5. Fast-scroll bubble bound to the scroll state.
6. Settings toggles + defaults table (magnifier ON, word-grab ON, action bar ON,
   repeat ON; spacebar-slide: tip only).

## 3. Exit condition

```text
(Device)
1. Long-press-drag places caret between any two chars on a dense line —
   magnifier shows the target char throughout.
2. Double-tap on `snake_case_name` selects the WHOLE identifier; drag extends
   word by word.
3. Handles adjust both selection ends; action bar offers Comment/Duplicate and
   they perform line ops correctly.
4. Holding a strip ← /→ repeats smoothly; pinch zoom still works; no gesture
   eats strip taps (regression check vs Phase 22.2 recipe).
5. Every gesture can be disabled from Settings singly and stays off across restart.
PASS = all five.
```
