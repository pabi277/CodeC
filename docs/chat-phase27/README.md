# CodeC Phase 27 — Phone-native Autocomplete

> **Status:** 📋 **PLANNED — research complete, no code written.** Research
> basis: [`docs/EDITOR_MOBILE_RESEARCH.md`](../EDITOR_MOBILE_RESEARCH.md) §6.
> **Owner starts with "Start Phase 27"; no PR/merge without explicit command.**

Owner complaint (2026-09-04, restated): *"the suggestions are good but also
problematic for phone because it suggests and can't do anything."* Diagnosis
from the research: today a **floating popup near the caret** asks for
mouse-era input (Tab/Enter/arrows or precise taps on ~28 dp rows) while a soft
keyboard is up; it can also cover the very text being edited. Phone editors
that solved this either turn suggestions into a **thumbable strip** flush
above the keyboard (Spck-style bars / iPad shortcut-bar completions) or make
the top suggestion **inline ghost text** (VS Code/Copilot norm: accept with
Tab/→; never block typing). CodeC gets both, in a priority pipeline:

```
                 completions (existing CodeCompletionEngine)
                              │
              ┌───────────────┴────────────────┐
              ▼                                ▼
   27.1 Inline GHOST TEXT             27.2 SUGGESTION STRIP
   top-1 at the caret, dimmed,        top-N as ≥44 dp chips REPLACE
   accept: strip-TAB / → / tap        the strip content contextually,
   pill; never blocks typing          accept: tap chip; dismiss: swipe↓
              └───────────────┬────────────────┘
                              ▼
              classic floating panel ONLY behind an explicit
              "⌄ more" tap (browse mode; paging, lazy-rendered)
```

Guarding design law for this phase (from the AI-UX pattern literature + the
owner's pain): **suggestions never own Enter, never auto-commit, never trap
the caret, and a single Settings toggle disables the whole feature.**

| Part | Title | Cost | Effort |
|---|---|---|---|
| [27.1](PART_27_1_GHOST_TEXT.md) | Inline ghost-text completion (top-1, partial-accept word) | client-only | M |
| [27.2](PART_27_2_SUGGESTION_STRIP.md) | Suggestion strip — completions as chips in the IME-adjacent bar | client-only | M |
| [27.3](PART_27_3_ACCEPT_RULES.md) | Accept/dismiss rules matrix, perf budget, settings & conflict law | client-only | S |

Dependency note: works on the CURRENT Compose core (ships before/with the
Phase 25 migration either way); on the Sora path the same pipeline is fed by
Sora's completion provider — one adapter line, not a redesign.
