# CodeC Phase 33 — First hour (Pydroid-easy, Acode-install)

> **Status:** ✅ **33.0 done + DEVICE-PASSED + MERGED (RUN ▶ chooser); 33.1–33.3 IMPLEMENTED + CI ✅ GREEN (`34346424311`, tip `5db9e33`) + DEVICE-PASSED (owner: "Device test pass", 2026-09-09) + **MERGED to `main` via PR #58** (owner: "Ok Merge", merge commit `c4e9aebe`). Phases 34–37 (UX/UI) are PLANNED next.**
> Product identity and first-run. Research: `PHONE_UX_ANALYSIS.md` changes
> 1–3, 10, 13, 15. Independent of 29–31 but embarrassing if colour is still
> regex.
>
> **33.0 (owner, 2026-09-09):** the default run file is now a USER choice —
> "make the default run option as a user task if user set any default file than
> it will open with a option default or current file" — ✅ IMPLEMENTED +
> DEVICE-PASSED ("Ok working as i wanted") + **MERGED via PR #57** on
> `arena/01a083fc-codec` ([record](PART_33_0_RUN_CHOOSER.md)).
>
> **33.1–33.3 (owner: "Start phase 33", 2026-09-09, `arena/01a085e0-codec`):**
> all three in one build — the first-run welcome (three starter tiles), the
> Packages hub recategorized for humans, and the identity copy (README / About
> / empty Projects). Records: [33.1](PART_33_1_FIRST_RUN.md) ·
> [33.2](PART_33_2_PACKAGES_HUB.md) · [33.3](PART_33_3_IDENTITY.md).

```
  33.1  First-run tiles: C (offline) / Python / HTML
              │
              ▼
  33.2  Packages hub: Languages & IntelliSense on top; Unix tools collapsed
              │
              ▼
  33.3  Identity copy: README, About, empty Projects — not "a C IDE"
```

| Part | Title | Cost | Effort | Status |
|---|---|---|---|---|
| [33.0](PART_33_0_RUN_CHOOSER.md) | RUN ▶ chooser (default vs open file) | client-only | S | ✅ implemented + device-passed + merged |
| [33.1](PART_33_1_FIRST_RUN.md) | First-run language tiles | client-only | M | ✅ implemented + CI ✅ + device-passed (merged via PR #58) |
| [33.2](PART_33_2_PACKAGES_HUB.md) | Recategorize Packages | client-only | S | ✅ implemented + CI ✅ + device-passed (merged via PR #58) |
| [33.3](PART_33_3_IDENTITY.md) | Copy / About / empty states | docs + client | S | ✅ implemented + CI ✅ + device-passed (merged via PR #58) |

**Invariant:** `.c` still RUN with TCC, no install gate (Phase 21).
Templates stay; they become the empty-state of Projects, not a hidden tab.
