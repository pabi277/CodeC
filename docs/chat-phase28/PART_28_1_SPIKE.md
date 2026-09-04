# CodeC Phase 28.1 — IME-free Input Path Spike (feel gate)

**Status:** 📋 PLANNED · **Cost:** `[client-only]` · **Effort:** S
· **Depends on:** nothing (gate for the whole phase)
· **Target files:** spike-only (never shipped), feeding a go/no-go note in
   `docs/EDITOR_MOBILE_RESEARCH.md` §9

---

## 1. Design

Prove the ONE thing that decides the phase: CodeC-drawn keys feeding the
editor **without opening the system IME**, at typing speed, with zero jank —
on both candidate cores.

| Spike | Path | Mechanism (documented Android pattern, clean-room) |
|---|---|---|
| S1 — Compose core | Editor stays `BasicTextField`-backed document in VM | IME suppressed on editor focus; key grid composable calls the existing `EditorKeySet.apply`/VM edit ops — **the strip already proves this path works**; spike only validates full-letter typing + focus/insets behavior |
| S2 — Sora core | `CodeEditor` in `AndroidView` | `rawInputType`/`textIsSelectable`-style IME suppression + programmatic insert/commit — the same route Sora's own `SymbolInputView` uses to insert text without IME characters |

Measured budgets (owner device, release build):
- key down → glyph committed + rendered ≤ 1 frame p95 (matches 25.1 budget law);
- 30-key burst with hold-repeat on: no dropped/swapped events;
- `adjustResize` layout settles with keyboard open/close with **no IME flicker**
  (this is where IME-free wins — measure it, don't assert it);
- caret-follow never lags behind held key repeat.

Also answer in the spike (evidence, not reading):
1. With IME fully suppressed, do HW Bluetooth keyboards still reach the editor
   (they must — 24.3 law)? Any focus trick needed?
2. Does suppressing IME break the **interactive run strip** (RunKeySet sends
   lines into the PTY — those edits flow through VM/terminal path, likely
   unaffected — verify)?
3. Any accessibility regression when the editor never owns an IME connection?
   (TalkBack exploration of editor content must still work.)

## 2. Implementation steps

1. Spike harness: editor screen variant with IME suppressed + a 3-row key grid
   (letters only, DEL, space, ⏎, TAB) routed through the key model.
2. Instrument key-latency logging (down→commit timestamps; reuse 25.1 harness).
3. Owner device runs ×3, both cores; fill the budget table; answer the three
   questions in writing.
4. Go/no-go recorded in §9 addendum. No-go = phase stops, strip path (L0)
   remains the product answer. Go = 28.2 starts.

## 3. Exit condition

```text
PASS = budgets met on BOTH cores + three questions answered + owner's
"feels instant" on a 5-minute typing session. Fail any → record no-go and stop.
```
