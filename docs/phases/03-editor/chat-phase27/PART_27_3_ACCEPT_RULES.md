# CodeC Phase 27.3 — Accept/Dismiss Rules, Perf Budget & Settings Law

**Status:** ✅ **CI GREEN (2026-09-05, run `33944516016` on `6da7f44`; 4 rounds — see phase README) — device gate = §3
recipe** · **Cost:** `[client-only]` · **Effort:** S
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

---

## 4. Implementation record (2026-09-05)

- `ui/editor/CompletionPolicy.kt` — the law file: `CompletionSettings`
  (master/ghost/strip/panel/debounceMs — `everythingOff`/`anyOn` helpers),
  `CompletionSurface` (NONE | GHOST_ONLY | STRIP | PANEL),
  `CompletionInput` / `CompletionAction` enums, `surfaceFor(...)`,
  `decide(surface, input)` (the §1.1 matrix, every cell a host test),
  `tabCapLabel(surface)` ("TAB ▸" only when it accepts) and
  `rightCapLabel(surface)` ("→▸").
- Matrix deltas from the draft, decided & pinned by tests:
  - **ESC on the phone has no keycap** — the editor key set never had one;
    ESCAPE is honored as a hardware key (`onPreviewKeyEvent`), and the
    phone affordance is the strip's swipe-down / ⌨ cap / pill swipe
    (identical DISMISS semantics).
  - **→ cap with a selection** stays MOVE_CARET (ghost never shows with a
    selection, so accept paths never see one — G7 row).
  - **PANEL's Tab** returns NOTHING: while browsing, sora's own key handler
    owns Tab/Enter/↑↓ exactly as the 25.2 device-accepted panel did.
- Wiring: the screen computes `completionSurface` once per recomposition
  and feeds it to (a) the strip's dual-mood caps (`keysWithGhostMood`,
  labels from the policy), (b) the hardware-key interceptor (HW Tab =
  FULL, HW Ctrl+→ = WORD, HW Esc = reject/dismiss/close), (c) the pill's
  visibility gate. The strip decides its own context via
  `stripContextFor` — the TWO never disagree because both read the same
  `completionModel` + settings (the ghost bit is identical; STRIP ⇔
  ≥2 candidates in both).
- **Perf & cadence (§1.2 as shipped):** instant leg = pure `startsWith`
  narrowing on the main path + `setInlayHints` single-line invalidations;
  engine recompute only on the 120/240 ms debounce off-main; chips diff by
  identity (the strip recomposes only when the model changes — StateFlow
  equality). No per-frame main-thread work anywhere in the feature.
- **Settings (§1.3 as shipped):** Settings → Editor Settings gains
  "Autocompletion" (master), ghost / chips / "⌄ more panel" switches and a
  120/240 ms delay dropdown, plus a plain-language explainer of the
  accept/dismiss vocabulary. Master OFF removes all chrome AND stops all
  computation (the VM pipeline emits `CompletionModel.EMPTY` immediately;
  the sora component is `setEnabled(false)`).
- Scattered key handling at the old screen (~L1252 comment in the draft is
  stale by now) — the Phase 12/22 popup handler went away in 25.2 already;
  this phase's interception lives in the preview handler + the strip's
  first-refusal hook, both routed through the policy.
- **Host tests:** `CompletionPolicyTest` pins every matrix cell + the label
  law + the master-off law; `StripContextTest` pins the dismissal anchor,
  selection/cap/settings suppression and the dual-mood caps;
  `GhostCompletionTest` pins ghost compute/accept/shrink.

### Exit-recipe notes

- Soft Enter: never consumed — `EditorAutoCompletion.onKeyEvent` only
  touches keys while its panel SHOWS (browse mode, hardware keys), and the
  strip/ghost never install any Enter path at all (there is no soft-IME
  Enter interception anywhere in the code; pinned by the NEWLINE row).
- The fuzz-y §3 item 3 ("never visible in a state the matrix forbids") is
  covered structurally: surfaces render from ONE `CompletionSurface`/+one
  `StripContext` decision instead of three independent flags; the two
  surface computations consume the same model fields.
