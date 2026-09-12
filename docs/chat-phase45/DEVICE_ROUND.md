# CodeC Phase 45 — device round (the guide: slides, then the tour)

> **Status:** 📋 ROUND 2 WRITTEN, **NOT RUN** (2026-09-12). Round 1 was run by the
> owner and came back with four reports; 45.2 was rebuilt as **one ordered tour**
> in response (see [`README.md`](README.md) §"Round 2"). 45.1's slides are
> unchanged — the owner's own decision: *"Slides stay as they are (GOT IT / START
> CODING / SKIP)."*
>
> **The build to install:** the newest green `Build APK` run on
> `arena/01a0955a-codec` **after the round-2 commit** (Actions → the run →
> **Artifacts** → `CodeC-IDE-debug`). Round 1's build (`34698914219`) does NOT
> contain the tour — installing it repeats the round-1 behaviour.
>
> **Two ways to get a first run** (rows G1-G5 and the whole tour need one):
> install as a **fresh install** (uninstall first, or a second profile), **or**
> Settings → About → **Reset tips** (+ **Show the welcome screen again** for the
> tiles), then kill and relaunch.
>
> **If you already ran round 1 on this phone:** the five beats you saw keep their
> ids, so an upgraded install shows only the NEW beats. Tap **Reset tips** first
> and the whole tour starts at 1 of 10 — that is the way to test this round.

## Before you start

1. **Phase 44 shares the phone.** On a fresh install the Linux tools download
   first, and while a download is actually moving **no box appears at all** (a
   spotlight over a progress bar is noise). The tour starts when the editor opens
   after the download settles. Phase 44's own rows are
   [`../chat-phase44/DEVICE_ROUND.md`](../chat-phase44/DEVICE_ROUND.md) (R1-R8,
   then D1-D12) and are still pending.
2. **The tour needs `demo_flask`.** It is seeded on first list and — new in round
   2 — **re-seeded whenever it is missing** (row G22), because beats 2-3 teach it
   by name.

## The rows — 45.1, the five slides (unchanged)

| # | What to do | PASS looks like |
|---|---|---|
| G1 | Fresh install → pick a starter tile (C) | The **guide** appears: `Guide · 1 of 5`, a progress bar, **SKIP** at the top right, the `>_` mark, the title *Your files live in the ☰ menu* |
| G2 | Tap **SKIP** on slide 1 | The app opens normally (Terminal or Editor per Phase 44). Kill and relaunch: **no guide** |
| G3 | Reset tips + relaunch, then tap **GOT IT** four times | Slides 2-5 in order, the bar fills a fifth per slide, slide 5's button reads **START CODING** and lands in the app; relaunch shows no guide |
| G4 | On slide 3, read it | *One download, one time — Python, Node and the Linux tools download once on first use. Keep CodeC open while it finishes.* (This is the slide that makes the Phase 44 bar make sense) |
| G5 | Press the **back button** on any slide | The guide closes like SKIP (it never traps you); relaunch shows no guide |
| G6 | Settings → About → **Help & guide** | The guide opens **at slide 1**; finishing or skipping it changes nothing (it stays re-openable) |
| G7 | Projects tab → ⋮ → **Guide** | Same guide, same behaviour (with a project open *and* with none) |
| G8 | Editor → ☰ → footer → **Guide** | Same guide; the drawer's footer row is labelled **Guide** with a book icon |

## The rows — 45.2, the tour (round 2: ten beats, one flow, no next button)

Walk it in order; the counter on each card tells you where you are.

| # | What to do | PASS looks like |
|---|---|---|
| G9 | After the slides, arrive at the **Editor** | **Tour · 1 of 10** — a dark scrim, a hole exactly around **☰**, a card beside it: *Your files*. The card's **only** button is **SKIP TOUR**: no NEXT, no GOT IT |
| G10 | Tap the highlighted **☰** | The drawer opens (its own action) **and** the box moves on its own to the project name: **2 of 10** *Change project* |
| G11 | Tap the project name, then choose **demo_flask** | The "Open folder" picker opens with **no box cut into it** (a dialog is its own window — the tour teaches it in copy instead). After choosing, the editor is on `demo_flask` |
| G12 | Tap **☰** again | **3 of 10** *Open app.py* — the hole is on the `app.py` row and on **no other row** |
| G13 | Tap `app.py`, then tap the highlighted **RUN ▶** (**4 of 10**) | `app.py` opens; RUN runs it. If Python is missing the *Install Python?* prompt appears with **no box over it**; its body already told you to tap **Install**. The install/run streams into the Output Panel |
| G14 | Let the Flask server start and the preview open | **5 of 10** *Your app is running* on the preview's **Back** arrow; tapping it closes the preview and the tour carries on |
| G15 | In the editor, bring up the keyboard so the tab bar hides | **6 of 10** *The tabs are here* on the thin reveal handle — **only** while the handle is really on screen, never at the visible tab bar |
| G16 | Tap the handle | The bar comes back and **7 of 10** *Packages* is on the **Packages tab itself**; tapping it navigates to Packages |
| G17 | On the Packages tab | **8 of 10** *One-time download* on the first install card |
| G18 | Continue | **9 of 10** *Terminal* on the **Terminal tab** → tap it → **10 of 10** *What it is doing* on the terminal's status chip. After beat 10 no box ever returns on its own |
| G19 | On any box, tap **outside** the hole | **Nothing happens.** The box stays exactly as it was, and the tap does NOT reach the UI under the scrim (owner: *"even tap outside will not end that box"*) |
| G20 | On any box, tap **SKIP TOUR** (or press back) | The **whole tour** ends — no further boxes on any tab, ever, until Reset tips. One tap, no confirmation, nothing returns on its own |
| G21 | While a box is pending, open an editor dialog (⋮ menu, Save to project, Go to line, the Install? prompt) | **No box is drawn under the dialog.** It comes back when the dialog closes, on the same beat (nothing was consumed) |
| G22 | Projects tab → ⋮ on **demo_flask** → Delete | It is **back on the next list refresh** (pull to refresh, or leave and return to the tab). A demo you edit is never touched — only a missing one is re-seeded |
| G23 | Settings → About → **Reset tips** → kill → relaunch | The slides return **and** the tour restarts at **1 of 10**. No project, file, theme or other setting changed |

## Also worth one look each (not rows)

- **The card is fully on screen** — round 1's *"Not showing the full box guide at
  one"*: the card's height is now measured instead of guessed, so on a 5" screen
  at the largest font the whole card (counter, title, body, SKIP TOUR) is visible
  and never covers its own hole.
- **Small screen / largest font:** slide 5 and every box are readable; the slides'
  copy column scrolls if it must and the button stays reachable (45.1 exit 5).
- **Both themes and safe mode:** legible in light and dark; **in safe mode
  (crash-loop start) neither the slides nor a box appears** — and a normal launch
  afterwards still shows them once.
- **Rotation / split screen:** a hole stays on its control and the card stays on
  screen after a rotation (anchors are window rects, placement is pure).
- **A download in flight:** while the Phase 44 bar is really downloading,
  verifying or extracting, no box appears; when it settles the pending beat does.
- **Leaving mid-tour:** kill the app after beat 4 and relaunch — the tour resumes
  at beat 5 (beats are recorded one at a time; nothing repeats, nothing is lost).
- **Wandering off-order:** go straight to the Terminal tab before finishing the
  editor's beats — no box appears there (beat 1 waits for the editor's ☰), and the
  terminal beats are still pending when you get back to the flow.

## If a row fails

Say which row, in your own words, and **COPY the logs**: Settings → **Logs** →
COPY (or the crash dialog → COPY REPORT). For a misplaced box, one detail helps
more than any log: **which control was lit, and where the hole actually was**
(above/below/left of it, or on the wrong control entirely).

## Exit condition (from the part docs)

```text
45.1  rows G1-G8    (guide once, never again, three doors, upgrade path)
45.2  rows G9-G23   (ten beats in order, the control is the only way on,
                     outside taps inert, SKIP TOUR ends it, no box behind a
                     dialog or a closed drawer, demo_flask always present,
                     Reset tips restarts everything)
PASS = all twenty-three, plus Phase 44's round 2 on the same phone.
```
