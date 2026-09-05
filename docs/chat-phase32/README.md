# CodeC Phase 32 — Phone canvas (see the code)

> **Status:** 📋 **PLANNED — no code.** Typing + Keys + chips + 5-tab bar
> leave a postage stamp of code (`PHONE_UX_ANALYSIS.md` changes 7–8, 11).
> **Starts only on `"Start Phase 32"`.**
>
> **28.3 already owns “chips as Keys row 0.”** Do not duplicate it. If
> 28.3 is not merged, 32.2 waits or degrades to “hide nav only.”

```
  32.1  Hide bottom nav while the editor is focused / Keys visible
              │
              ▼
  32.2  Coordinate with 28.3: one meaning row (chips vs keys)
              │
              ▼
  32.3  First RUN peeks output; tap diagnostic jumps to the user-facing file
```

| Part | Title | Cost | Effort |
|---|---|---|---|
| [32.1](PART_32_1_HIDE_NAV.md) | Auto-hide 5-tab bar in editor focus | client-only | S |
| [32.2](PART_32_2_ONE_ROW.md) | Don’t stack chips + Keys + nav | client-only | S |
| [32.3](PART_32_3_OUTPUT_JUMP.md) | Output peek + tap-to-line | client-only | S |
