# CodeC Phase 56 — device round (Q1-Q6)

> **Not run.** Written with the phase (2026-09-22). Run it on a fresh install **and** on the
> upgrade path (the bar is where the change lives, and the tour is where it could show).
>
> **Record format:** `Q<n> — device / OS / theme (dark|light) / result (PASS|FAIL) / one sentence.`
> Paste it into the `## Test log (Phase 56 — the bar keeps four)` section below.
>
> **Before Q5/Q6:** Settings → About → **Reset tips**, so the tour runs from beat 1.

| # | What to do | What must happen |
|---|---|---|
| Q1 | Open the app, look at the bottom | **Editor · Terminal · Packages · Settings** — four options, **no Projects**, and no empty gap where it used to be. Terminal is no longer dead-centre |
| Q2 | Tap each of the four | Each opens its room; the active option is highlighted; tapping the tab you are on does nothing surprising |
| Q3 | In the editor with a file open, tap inside the code | CodeC Keys comes up and the bar hides — then the thin **Show tabs** handle appears and brings the four back (Phase 32, unchanged). With the bar visible, a swipe-up still reveals it |
| Q4 | Open the panel (☰) → Navigation → **Projects** | The Projects screen opens with **no** `+` sheet; **system back** returns to the editor (not out of the app) |
| Q5 | Run the tour from beat 1 (Reset tips), all the way to the finish card | 11 beats, in order, and **beat 7's card says “Four tabs, one tap away”** — the lesson still points at the real bar (or at the reveal handle when the bar is hidden) |
| Q6 | Deep link / first-launch case: open the Projects screen when it is the *start* destination (fresh install with no last file, before Phase 58), then press back | The **exit prompt** appears (or the app exits when prompts are off) — the same behaviour as any other room; it must not silently exit as if Projects were a sub-screen |

## Test log (Phase 56 — the bar keeps four)

_(empty — the owner runs this round)_

## What this round is *not* allowed to see

- A bar with **three** options or **five** (no filler tab was added).
- Any change to the **Projects screen** itself, its hub, its `+` sheet, or its route name.
- A tour that skips a beat, re-orders, or gained a “Projects” beat that never existed.
