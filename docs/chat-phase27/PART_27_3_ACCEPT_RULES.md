# CodeC Phase 27.3 — Accept/Dismiss Rules, Perf Budget & Settings Law

**Status:** 📋 PLANNED · **Cost:** `[client-only]` · **Effort:** S
· **Depends on:** 27.1 + 27.2 implemented together
· **Target files:** `ui/editor/CompletionPolicy.kt` (new, pure), Settings,
   host tests

---

## 1. Design

One file that **owns the law** so the three surfaces (ghost, strip, panel) can
never disagree — the meta-lesson of every completion UX postmortem (ambiguous
Enter/Tab ownership is the #1 generator of "the editor is fighting me"
reports; the AI-UX pattern list's core anti-patterns: auto-commit, key-steal,
no disable path).

### 1.1 The matrix (decision table = `CompletionPolicy.decide(...)`)

| Input | No suggestions | Ghost visible | Strip/chips visible (browsed) | Panel open (browse mode) |
|---|---|---|---|---|
| `Enter` (soft IME) | newline | **newline** (ghost unrejected but unaffected) | newline | newline unless an item was arrow-navigated-to → accept |
| `TAB` cap (strip) | indent | **accept ghost** | **accept selected chip** | accept focused item |
| `TAB` cap long-press | indent | raw indent | raw indent | raw indent |
| `→` cap | caret right | accept next **word** | caret right | caret right |
| `↑↓` caps | caret | caret | caret (strip untargeted) | browse items |
| `←→` caps | caret | caret | caret | caret |
| `ESC` cap | — | reject ghost | dismiss strip for identifier | close panel |
| Any unmatched char | insert | insert (ghost recomputes) | insert + keys return | insert + panel updates |
| selection exists | — | nothing shows | strip suppressed | panel closed |
| IME composing region | — | nothing shows | strip held | held |

Invariants pinned by host tests:
1. **Enter is sacred**: on a soft keyboard it is NEVER consumed for
   completion unless the user explicitly navigated INTO completion UI
   (panel focused / chip arrow-navigated). Phone law.
2. **Tab is dual-mood but never ambiguous**: label shows "TAB ▸" when it will
   accept; plain "TAB" when it will indent. The LOOK tells the truth.
3. Dismissal state is per-identifier (`completionDismissed` exists today —
   keep semantics, document it), cleared by caret move ≥ identifier boundary.
4. Master Settings switch → all completion chrome off (zero residual ghosts).

### 1.2 Perf & cadence budget
- Completion pipeline remains debounced (today: 120 ms, off-main) + ghost
  render ≤ 2 frames post-keystroke (25.1 budget) + strip model diffed by
  identity + panel first-page-only rendering. Anything that needs per-frame
  work on the main thread is a design bug; fix the design.
- Delay identity: the Phase 22.6 note ("a beat after you pause reads as
  intentional") is revisited with the strip — strip can be immediate (it
  doesn't occlude), ghost keeps the small beat (it reads as thought).

### 1.3 Settings surface
Completion master switch + per-surface: [ghost on/off] [strip on/off]
[panel browse-mode only] [debounce beat 120/240 ms]. Defaults: ghost ON,
strip ON, panel on-demand, 120 ms.

## 2. Implementation steps

1. `CompletionPolicy` pure state machine + exhaustive table tests (every cell
   above is one test).
2. Rewire 27.1/27.2 handlers through it (delete today's scattered key
   handling at `EditorScreen.kt` ~L1252).
3. Labels: TAB cap label state; accessibility descriptions.
4. Settings rows + migration (existing toggle folded into master switch).
5. Frame-budget spot-check on device (25.1 harness reused).

## 3. Exit condition

```text
(Host tests) every matrix cell passes as specified.
(Device, release)
1. Soft-keyboard Enter during visible suggestions → newline inserted, chars
   never lost or swallowed.
2. TAB accepts ONLY when the cap reads "TAB ▸".
3. Ghost + strip + panel never visible in a state the matrix forbids
   (fuzz over the UI states with scripted input).
4. Master switch OFF → all three surfaces gone, suggestions never computed.
PASS = all four.
```
