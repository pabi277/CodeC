# CodeC Phase 27 — Phone-native Autocomplete

> **Status:** ✅ **CI GREEN (2026-09-05) on `arena/01a06f9e-codec` — run
> `33944516016` on `6da7f44` after 4 rounds (assembler+lint+all host tests);
> device gates = each part's §3 recipe.** Round log: `33943917743` ❌
> (KeyStripStorage exhaustive `when`s for the new ghost caps — transient caps
> persist as their physical keys, `51680e5`); `33944038267` ❌ (two test names
> contained `;` — AGP test-name rule, `c25c5b8`); `33944280599` ❌ (G6 fixture
> typed `"int ma"` but the ghost prefix is the single identifier run — fixture
> corrected to typed `"int"`, `6da7f44`); `33944516016` ✅ GREEN (4m51s).
> Research basis:
> [`docs/EDITOR_MOBILE_RESEARCH.md`](../EDITOR_MOBILE_RESEARCH.md) §6.
> **No PR/merge without the owner's explicit command.** Implementation
> records: §4 of each part (27.1 ghost via sora inlay hints, 27.2 chip strip
> + gated native panel, 27.3 the policy surface + Settings surface).

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
