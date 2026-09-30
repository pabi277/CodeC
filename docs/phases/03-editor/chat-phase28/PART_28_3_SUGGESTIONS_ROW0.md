# CodeC Phase 28.3 — Suggestions as Keyboard Row 0

**Status:** 📋 PLANNED — gated on 28.1 go · **Cost:** `[client-only]` ·
**Effort:** S · **Depends on:** 28.2, 27.1/27.2 logic
· **Target files:** `ui/keyboard/*`, `ui/editor/CompletionPolicy.kt`,
   `ui/components/SuggestionStrip.kt` (moves into keyboard surface)

---

## 1. Design

When CodeC Keys is on, the keyboard's **row 0 IS the suggestion surface** —
the Phase 27 strip relocates flush into the keyboard, becoming what Gboard's
predictive row is for prose, but for code symbols. This completes the owner's
kill-shot on "suggests and can't do anything": there is no longer any
competition between an IME's idea of the bottom bar and CodeC's; a chip is one
thumb-move from anywhere, and the accept affordances are ours.

| # | Rule |
|---|---|
| R1 | Row 0 height constant (suggestions, key macros fallback, or run-keys context) — keyboard never jumps. |
| R2 | Content priority: **suggestions (chips, per 27.2)** > macros row (when no suggestions) > RunKeySet (during interactive run, 23.2 law; completions suppressed). |
| R3 | Ghost-accept cap "TAB ▸" is the leftmost row-0 cap while a ghost shows (same state bit as 27.1; label tells the truth per 27.3 matrix). |
| R4 | Swipe-down anywhere on row 0 dismisses suggestions for the identifier; first row-0 "⌄ more" cap opens browse panel (27.2 S5) — panel slides UP over the keyboard area, never floats over code while keyboard is open. |
| R5 | All CompletionPolicy matrix cells (27.3 §1.1) apply verbatim — the policy file doesn't care whether suggestions render in a strip or in row 0. |

## 2. Implementation steps

1. Lift `SuggestionStrip` into `ui/keyboard` as row 0 content provider (strip
   remains as its own composable for the L0/system-IME mode — one component,
   two mount points).
2. Sealed `Row0Content = Suggestions|Macros|RunKeys` driven off existing state;
   tests pin priority law R2.
3. Swim-lane check: 27.x on the L0 path (system IME) must keep working exactly
   — the relocation is mount-point-only.
4. Browse panel relayout for keyboard-open state (occupies keyboard area).

## 3. Exit condition

```text
(Device)
1. CodeC Keys ON: type `pri` → chips appear as row 0; tap accepts; swipe-down
   dismisses; macro row returns instantly; row heights never moved.
2. scanf run → row 0 = RunKeySet; suggestions gone; end run → macros return.
3. "TAB ▸" appears only with ghost; tapping accepts exactly per 27.3 matrix.
4. Toggle KEYBOARD off → system IME + Phase 27 strip behaves per 27.x recipes
   (regression: identical).
PASS = all four.
```
