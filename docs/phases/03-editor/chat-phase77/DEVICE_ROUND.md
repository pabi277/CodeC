# Phase 77 — device round 1 (AI UI for phones: bubble, chat sheet, AI home)

> **Result: ⏳ waiting for the owner.** Fill each row ✅ / ❌ + one line. Row **F** is the decision
> the code is waiting on: pick **A** or **B**; the loser is deleted before merge.

Build: `CodeC-IDE-release` (or `-debug`) from **Build APK run `36784685021`** (tip `3661264`, ✅ green) on
`arena/01a0f452-codec` — https://github.com/pabi277/CodeC/actions/runs/36784685021
(Run `36784200759` on `90fec07` was red for cause: `MotionWiringTest` bans a `snap(` helper name; renamed
`snapToEdge`. Nothing device-visible changed.)

You need: Phase 76's Gemini key already saved (or a fresh one from https://aistudio.google.com/apikey),
internet, and a CodeC project with a Python file that can fail (a typo). Phase 76's Send/Stop/Copy flow
already passed on a phone; these rows test the **new surface**.

**Not in this build (by decision, not a bug):** "Ask AI" in the text-selection menu. Sora 0.24.6 has no
way to add a menu item without a fork. Instead: select code, then tap the bubble.

## A. The bubble appears only when it should

| # | Do | Expect |
|---|---|---|
| A1 | Fresh install (or ✨ → Delete key). Open a project in the editor. | **No** floating button. No tooltip, no pulse. |
| A2 | Open a file from the Projects hub file list (single-file mode). | No button. |
| A3 | Open the project in the editor, ☰ → ✨, save a key. | ✨ home shows model, Test connection, Delete key, **Show AI button** (on), **Open AI chat**. After closing ☰ a small round ✨ button sits on the right edge, a bit below the middle. |
| A4 | ☰ → ✨ → switch **Show AI button** off. | Button disappears. Switch on → it returns at the same place. |
| A5 | ✨ → **Delete key**. | Button disappears. Save the key again → it returns where you last left it. |

## B. Dragging, snapping, space

| # | Do | Expect |
|---|---|---|
| B1 | Drag the button to the far left, release mid-screen. | It snaps to the left edge at the height you released. |
| B2 | Drag it to the very top and very bottom. | It stops short of the tab strip at the top and of the coding row / bottom bar at the bottom. It never covers them. |
| B3 | Tap into the code so the keyboard comes up (system keyboard, then CodeC keyboard). | The button stays above the keyboard. |
| B4 | Force-stop CodeC and reopen the project. | Button is where you left it (side and height). |
| B5 | Rotate to landscape and back. | Stays on its side, in bounds, at about the same relative height. |
| B6 | Press and hold the button. | Menu: **Hide AI button** / **Move to other side**. |
| B7 | **Move to other side**. | Jumps to the opposite edge, same height. |
| B8 | **Hide AI button**. | Button goes; snackbar says to turn it back on in the AI tab; ✨ → Show AI button is off. |

## C. The sheet (one tap)

| # | Do | Expect |
|---|---|---|
| C1 | Tap the button once (nothing selected). | A **half-height** sheet opens at the bottom; code stays visible above. Header: ✨ title, model, ⤢, ▾. Chips **Explain selection** / **Explain last error**, question field. The bubble hides while the sheet is open. |
| C2 | Type a question, tap **Explain selection** with nothing selected. | Red line asking you to select code first. Nothing sent. |
| C3 | Select a few lines, tap the button. | Sheet opens with **Explain selection** already chosen/previewed: **Check before sending** card with the exact text. **Nothing is sent yet.** |
| C4 | **Cancel**, then again → **Send**. | Streaming answer with **Stop**; then **Copy answer** / **New question**. File unchanged. |
| C5 | Tap ⤢. | Sheet goes full screen. ▾/⤢ (collapse) returns to half. |
| C6 | Drag the handle up past ¾ of the screen, release. | Goes full. Drag down below ⅕ and release → closes. In between → half. |
| C7 | Tap ▾ mid-answer or after an answer, then tap the button again. | Same exchange is still there (minimizing keeps it). |
| C8 | **New question**. | Cleared. Switch to another project and back → sheet closed and cleared. |
| C9 | Open the keyboard in the question field (half sheet). | Sheet stays usable above the keyboard (height obeys the Output panel's keyboard rule, ~38%); question field visible. |
| C10 | **Back** with the sheet full, then half. | Full → half → closed, one step per Back. (With the keyboard up, the first Back hides the keyboard, as everywhere else.) |
| C11 | Code row / Codec keys / status bar while the sheet is open. | They step aside (no row that types into the code under a chat field). They return on close. |

## D. Explain with AI on a failed run

| # | Do | Expect |
|---|---|---|
| D1 | Run a file that works. Expand the Output panel. | **No** "Explain with AI" on its header. |
| D2 | Run a file with a typo (fails). | **Explain with AI** appears on the Output header. |
| D3 | Tap it. | Sheet opens with the run-output **Check before sending** card already showing (variant behaviour per row F). Send is still a separate tap. |
| D4 | No key saved → repeat D2. | No **Explain with AI** button. |

## E. AI home (✨ rail slot)

| # | Do | Expect |
|---|---|---|
| E1 | ☰ → ✨. | Setup/model/Test connection/Delete key/Show AI button/**Open AI chat** only. **No** question field, chips, preview or answer here. |
| E2 | **Open AI chat**. | Drawer closes and the sheet opens at half. |
| E3 | Test connection still works. | *"Connected. Gemini answered."* |

## F. THE DECISION — the Output panel when the chat opens

✨ home has a temporary **"Test build: chat while the Output panel is open"** row with two options
(resets on app restart). With the Output panel **open** (after a run) try both:

| # | Do | Expect |
|---|---|---|
| F1 | Pick **A**, tap the bubble (or Explain with AI). | Half sheet takes the Output panel's place; code stays above. Close the sheet → the Output panel is back exactly as it was (same scroll, same text). |
| F2 | Pick **B**, tap the bubble. | Chat opens **full screen**; the Output panel is untouched beneath; close → back as before. |
| F3 | **Your pick** (write A or B): ______ | I delete the other variant and these rows, then record the pick here and in PART_77_2. |

## Report

Paste the table with ✅ / ❌ per row and your pick in F3. On ❌ a screenshot helps. Then say "merge" only
when you want it merged — nothing merges without that word.
