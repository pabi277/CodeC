# CodeC Phase 33.3 — Identity copy

**Status:** ✅ IMPLEMENTED + CI ✅ (`34346424311`) + DEVICE-PASSED (owner: "Device test pass", 2026-09-09) — merge held · **Cost:** `[docs + client strings]` · **Effort:** S
· **Target:** `README.md`, About in Settings, Projects empty state,
  `prompt.md` one-liner if needed

---

## 1. Design

Replace "A C programming IDE" with:

> **CodeC — write and run C, Python, JavaScript, and HTML on your phone.
> C works offline with no setup.**

Do not claim Mini-Termux on the first screen. Keep honest TCC / userland
details in README further down.

## 2. Exit condition

```text
1. README first paragraph matches the sentence above.
2. Settings → About does not say C-only.
3. Empty Projects points at the three starters (33.1), not a blank list.
PASS = all three.
```

## 3. Implementation (2026-09-09, `arena/01a085e0-codec`)

1. **README** — the first paragraph is now the sentence above, followed by a
   two-line honest summary (built-in TCC compiler that works offline, Python /
   Node runtimes installed from Packages, the in-app terminal + signed `pkg`
   repository, the HTML preview). No "Mini-Termux" claim; the detailed
   TCC / userland notes stay further down unchanged.
2. **Settings → About** — a new "CodeC" identity item carries the sentence
   (the About section now states the multi-language identity rather than
   implying C-only).
3. **Projects empty state** — `EmptyProjectsState` now points at the three
   33.1 starters (`StarterTile`s) instead of a blank list, with the full
   create / clone / import sheet still one tap away.

## 4. Tests

- The starter tiles themselves are covered by `WelcomeStartersTest` (33.1);
  this part is copy + a shared composable, with no new pure logic.
