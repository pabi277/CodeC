# CodeC Phase 32 — Phone canvas (see the code)

> **Status:** ✅ **IMPLEMENTED (2026-09-09, `arena/01a083fc-codec`)** — see the
> per-part records below. **CI `34305070875` ✅ GREEN first try (tip `b5feb55`,
> 5m37s)**. **Not merged** — the owner device round (`TROUBLESHOOTING.md` §16)
> is the remaining gate; the owner merges (§3).
>
> **28.3 already owns “chips as Keys row 0.”** It is NOT duplicated here —
> 32.2 is satisfied by 28.2/28.3 + the 32.1 nav-hide (recorded in its part doc).

```
  32.1  Hide bottom nav while the editor is focused / Keys visible   ✅ new code
              │
              ▼
  32.2  Coordinate with 28.3: one meaning row (chips vs keys)        ✅ no code needed
              │
              ▼
  32.3  First RUN peeks output; tap diagnostic jumps to the user file ✅ hardened
```

| Part | Title | Cost | Effort | Status |
|---|---|---|---|---|
| [32.1](PART_32_1_HIDE_NAV.md) | Auto-hide 5-tab bar in editor focus | client-only | S | ✅ implemented |
| [32.2](PART_32_2_ONE_ROW.md) | Don’t stack chips + Keys + nav | client-only | S | ✅ no code (28.x owns it) |
| [32.3](PART_32_3_OUTPUT_JUMP.md) | Output peek + tap-to-line | client-only | S | ✅ implemented |

New pure files: `ui/editor/NavBarPolicy.kt`, `ui/editor/EditorChromeState.kt`,
`ui/editor/OutputDiagnosticTarget.kt`. Tests: `NavBarPolicyTest`,
`OutputDiagnosticTargetTest`. Local pre-validation: Temurin 25 + kotlinc
2.4.10 (rule.md §9 exception) — pure files pass a main-harness (which caught
the `toRelativeString` direction bug pre-push); JUnit sources type-check
against a shim. CI is the executor of record.
