# CodeC Phase 32.2 — One meaning row

**Status:** ✅ NO CODE NEEDED & DEVICE-PASSED (owner: "All test passed on device", 2026-09-09) · **Cost:** `[client-only]` · **Effort:** S
· **Depends on:** 28.3 if Keys ON; else 27.2 strip

---

## 1. Design

If 28.3 is merged: Keys ON → chips **are** row 0; do not also show the
Phase 27 strip. If 28.3 is not merged: still **never** show chips +
utility keys + nav at once — hide nav (32.1) and keep strip flush above
Keys/IME (existing 22.2/27.2).

Caret line must stay above the keyboard (22.3 regression).

## 2. Exit condition

```text
(Device)
1. Type a prefix with Keys ON — at most one extra row of chips/macros
   between code and letter keys.
2. L0 (Keys off) — 27.x strip recipes still pass.
PASS = both.
```

## 3. Why this part needed no new code

28.3 IS merged, so the one-meaning-row is already the codebase's shape —
verified against the editor's existing gates, not re-implemented:

- **Utility keys never stack under chips with Keys ON:** the docked strip is
  `BottomStrip(suppressKeysVariant = codecKeysUp)` — `StripContext.Keys`
  returns early when Keys is up (28.2), while `StripContext.Suggestions`
  (chips) still renders. One strip, one row.
- **Chips are row 0 (28.3):** the chips ride `StripContext.Suggestions`
  immediately above `CodecKeyboard` in the editor column — no separate
  Phase 27 strip is drawn above them.
- **Nav never stacks (32.1):** with Keys up, `MainApp` hides the 5-tab bar,
  so code → chips → Keys is the whole bottom of the screen.
- **Caret stays above the keyboard (22.3):** untouched — `imePadding()` +
  the 22.2 IME-anchored row logic still apply.

Device verification is folded into `TROUBLESHOOTING.md` §16 step 4 (and step
6 for the L0 Keys-off strip recipes).
