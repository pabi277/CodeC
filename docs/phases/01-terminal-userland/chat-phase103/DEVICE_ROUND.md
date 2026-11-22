# Phase 103 — device round (owner)

> **Nothing here is pre-ticked.** Run each row on your phone and answer ✅ / ❌ + one
> line. A screenshot on a ❌ is what makes the fix possible.

**Build:** `CodeC-IDE-debug` (or `-release`) from **Build APK run
[`45001976378`](https://github.com/pabi277/CodeC/actions/runs/45001976378)** — branch
`arena/3f8a6afe-codec`, tip `e1e17f2`, ✅ green (compile, full host unit suite, debug +
release assembly, lint).

**Where to get it:** GitHub → **Actions** → that run → scroll to **Artifacts** →
`CodeC-IDE-debug`. (The release artifact is signed only when the signing secrets exist on
the run; the debug APK is always there.)

**Nothing extra needed.** No API key, no package install, no internet — this phase is the
terminal surface only.

---

## A. Partial copy — your *"partial text copy not working"* row

| # | Do | Expect |
|---|---|---|
| A1 | Open the terminal. Run `ls` or `echo` a few times so there is output on screen. Long-press on a **word** in the output and hold. | That word highlights (a band over it), and a **Copy · Select all · Paste · ⋮** bar appears at the top of the terminal. The word is still selected — it must **not** collapse to a single character, and no old popup opens. |
| A2 | Without lifting, **slide your finger** along the line and up/down over several lines. | The band follows your finger; the end grows/shrinks. The two round handles sit at the selection's corners. |
| A3 | Lift. | The selection stays on screen with both handles and the toolbar. |
| A4 | Drag the **bottom-right handle** up two rows, then lift. | The band shrinks to where you dropped it; the toolbar stays. |
| A5 | Drag a handle **past the other end** (right-to-left past the anchor). | The band inverts cleanly; the handle you are holding stays under your finger. Nothing jumps, nothing disappears. |
| A6 | Tap **Copy**. | *"Copied to clipboard"*. Paste into any text field: the clipboard holds **exactly** the highlighted span — not the whole terminal, not empty. |
| A7 | Repeat A1–A6 on a line that **wrapped** (make one: `printf 'x%.0s' {1..200}; echo`), selecting across the fold. | The pasted text is **one unbroken string** at the fold (no stray newline in the middle of the run). |

## B. Copy law — it must never fall back to the transcript

| # | Do | Expect |
|---|---|---|
| B1 | Clear your selection (tap once on the terminal). Tap the **Copy icon in the top bar**. | Toast *"Nothing to copy"*. The clipboard is **unchanged** — it must not receive the whole terminal. |
| B2 | Make no selection, open ⋮ on the toolbar's **⋮** → **Copy all**. | This one *is* allowed to copy everything, because you asked for it by name. |

## C. Selection while output scrolls — your *"selection stays on its text"* row

| # | Do | Expect |
|---|---|---|
| C1 | Make a selection in the terminal output, then run something that keeps printing (e.g. `for i in $(seq 200); do echo line $i; sleep 0.2; done`). | Your selected text **scrolls up with its own lines** and stays selected on the same characters. The band never slides onto different text. |
| C2 | Let it run until your selected rows have scrolled far up, then Copy. | The clipboard holds the text **you selected**, not whatever moved under it. |
| C3 | Select a line, then overwrite that same line (`printf '\033[1;1HZZZZZ'`). | The selection is **dropped** (band disappears) rather than highlighting the new characters. |
| C4 | Scroll up into history, make a selection there, and let more output arrive. | The history selection stays put on its own text. |

## D. Fresh open and the blank band — your *"first open scroll up, empty space"* row

| # | Do | Expect |
|---|---|---|
| D1 | Force-stop CodeC, reopen, open the terminal. | **No empty strip above the first line of text.** The grid starts flush at the top. |
| D2 | Scroll down (finger moves content down) as far as it goes. | It stops exactly at the live screen — no white/blank band appears above row 0, no bounce past the top. |
| D3 | Let go **mid-scroll** (lift your finger during a fast drag). | The grid settles onto a whole row; no partial-row gap stays painted after the lift. |

## E. Fling and rails

| # | Do | Expect |
|---|---|---|
| E1 | With a few hundred lines of scrollback, flick the terminal up hard. | It keeps moving after your finger leaves (**inertia**), slows down, and stops. It does not jump instantly. |
| E2 | Fling hard toward the very top of history. | It stops at the **oldest** line — no overshoot into blank space, no rubber band. |
| E3 | Fling hard toward the live end. | Stops exactly at live (the newest line at the bottom, cursor row visible). |
| E4 | Touch the screen **while a fling is still moving**. | The fling stops immediately and your finger takes over. |
| E5 | Settings → Accessibility → **Remove animations** ON, then repeat E1. | The fling becomes one instant jump instead of a glide (this is the reduced-motion rule, now read from the same platform setting the rest of the app uses). |

## F. Scrollbar

| # | Do | Expect |
|---|---|---|
| F1 | Scroll while there is scrollback. | A thin bar appears at the right edge with a thumb proportional to how much of the buffer you can see; it **fades out** a moment after you stop. |
| F2 | On a fresh terminal with no scrollback at all. | **No scrollbar** is drawn. |
| F3 | At the very bottom (live). | The thumb sits at the **bottom** of the track. At the oldest line, at the **top**. |

## G. Things that must NOT have changed (regression rows)

| # | Do | Expect |
|---|---|---|
| G1 | Swipe to scroll, tap a URL in the output. | Still opens the URL; scrolling still works with no selection up. |
| G2 | Run something that reports the mouse (`htop`, or `vim`), then swipe. | Swipes still act as the **wheel**; no local scrollback scrolling. |
| G3 | Type with the keyboard after scrolling up. | Typing still jumps back to the live screen. |
| G4 | Pinch to zoom the terminal. | Still resizes text; the grid stays aligned (no letter drift). |
| G5 | Tap once with nothing selected. | Keyboard opens; nothing else changes. |

---

## If something fails

Report the row number and one line — e.g. *"A5 ❌ the band disappeared when I crossed the
anchor"* — plus a screenshot. Rows A, C and D are the three that decide the phase; B, E, F
and G are the guards around them.

**Not tested by CI:** every row above. CI compiled the file and ran the pure model +
34 pre-existing pins, but no automated run can see a touch on a phone.
