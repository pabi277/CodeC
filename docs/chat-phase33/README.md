# CodeC Phase 33 — First hour (Pydroid-easy, Acode-install)

> **Status:** 🚧 **33.0 done (RUN ▶ chooser) — 33.1–33.3 still PLANNED.**
> Product identity and first-run. Research: `PHONE_UX_ANALYSIS.md` changes
> 1–3, 10, 13, 15. **33.1–33.3 start on `"Start Phase 33"`.** Independent of
> 29–31 but embarrassing if colour is still regex.
>
> **33.0 (owner, 2026-09-09):** the default run file is now a USER choice —
> "make the default run option as a user task if user set any default file than
> it will open with a option default or current file" — ✅ IMPLEMENTED on
> `arena/01a083fc-codec` ([record](PART_33_0_RUN_CHOOSER.md)): RUN ▶ runs the
> open file by its own type (a `.c` in a web project compiles/runs, never
> previews `index.html`), and when the user has set a default (⋮ → "Set as
> launch default") that differs from the open file it asks "Run default / Run
> open". **CI `34307172630` ✅ GREEN first try (tip `788ba46`).**

```
  33.1  First-run tiles: C (offline) / Python / HTML
              │
              ▼
  33.2  Packages hub: Languages & IntelliSense on top; Unix tools collapsed
              │
              ▼
  33.3  Identity copy: README, About, empty Projects — not “a C IDE”
```

| Part | Title | Cost | Effort | Status |
|---|---|---|---|---|
| [33.0](PART_33_0_RUN_CHOOSER.md) | RUN ▶ chooser (main/index vs open file) | client-only | S | ✅ implemented |
| [33.1](PART_33_1_FIRST_RUN.md) | First-run language tiles | client-only | M | 📋 planned |
| [33.2](PART_33_2_PACKAGES_HUB.md) | Recategorize Packages | client-only | S | 📋 planned |
| [33.3](PART_33_3_IDENTITY.md) | Copy / About / empty states | docs + client | S | 📋 planned |

**Invariant:** `.c` still RUN with TCC, no install gate (Phase 21).
Templates stay; they become the empty-state of Projects, not a hidden tab.
