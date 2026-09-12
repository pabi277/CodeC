# CodeC Phase 45 — device round (the guide: slides, then the tour)

> **Status:** 📋 ROUND 3 WRITTEN, **NOT RUN** (2026-09-12). Round 1 came back with
> four reports and 45.2 became **one ordered tour**; round 2 came back with *"You add
> the skip option and it's not a trough guide mean it got cut"* and the tour became
> **first beat to last with no exit until the end** (see [`README.md`](README.md)
> §"Round 3"). 45.1's slides are unchanged — the owner's own decision: *"Slides stay
> as they are (GOT IT / START CODING / SKIP)."*
>
> **The build to install:** `Build APK` **`34707337429`** on `arena/01a0955a-codec`
> — round-3 commit `0fcb3b6`, ✅ `success`, job `build` 10m51s, release APK
> 6,669,858 B (**+5,288 B / +0.08%** over round 2), debug 25,676,356 B (Actions →
> that run → **Artifacts** → `CodeC-IDE-debug`). Round 2's build (`34704379023`)
> still has **SKIP TOUR on every card** and still passes beats over — installing it
> repeats exactly what the owner reported.
>
> **Two ways to get a first run** (rows G1-G5 and the whole tour need one):
> install as a **fresh install** (uninstall first, or a second profile), **or**
> Settings → About → **Reset tips** (+ **Show the welcome screen again** for the
> tiles), then kill and relaunch.
>
> **If you already ran round 1 or 2 on this phone:** the beats you saw keep their
> ids, so an upgraded install shows only the beats you never reached. Tap **Reset
> tips** first and the whole tour starts at 1 of 10 — that is the way to test this
> round.

## Before you start

1. **Phase 44 shares the phone.** On a fresh install the Linux tools download
   first, and while a download is actually moving **no box appears at all** (a
   spotlight over a progress bar is noise). The tour starts when the editor opens
   after the download settles. Phase 44's own rows are
   [`../chat-phase44/DEVICE_ROUND.md`](../chat-phase44/DEVICE_ROUND.md) (R1-R8,
   then D1-D12) and are still pending.
2. **The tour needs `demo_flask`.** It is seeded on first list and **re-seeded
   whenever it is missing** (row G25), because beats 2-3 teach it by name.
3. **Waiting is silent, and that is the design.** While a beat's control is not on
   screen the tour draws *nothing* — no scrim, no card — and the app works normally.
   The box appears the moment the control does. Round 2 instead walked on to a later
   beat, which is how the flow got holes in it.

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

## The rows — 45.2, the tour (round 3: ten beats, no skip, a close at the end)

Walk it in order; the counter on each card tells you where you are. **No card in this
section has a button on it** — that is the point of the round.

| # | What to do | PASS looks like |
|---|---|---|
| G9 | After the slides, arrive at the **Editor** | **Tour · 1 of 10** — a dark scrim, a hole exactly around **☰**, a card beside it: *Your files*. The card has **no button at all**: no SKIP TOUR, no NEXT, no GOT IT |
| G10 | Tap the highlighted **☰** | The drawer opens (its own action) **and** the box moves on its own to the project name: **2 of 10** *Change project*. This box appears whatever is open — another project, `demo_flask` already, or scratch mode |
| G11 | Tap the project name, then choose **demo_flask** | The "Open folder" picker opens with **no box cut into it** (a dialog is its own window — the tour teaches it in copy instead). After choosing, **the drawer reopens by itself** and **3 of 10** *Open app.py* is already on the `app.py` row — no second ☰ tap, no silence |
| G12 | Tap `app.py`, then tap the highlighted **RUN ▶** (**4 of 10**) | `app.py` opens in the editor; RUN ▶ runs it. Its card says *Runs the open file. If Python is missing, tap **Install** — one download, one time.* |
| G13 | If the *Install Python?* prompt appears, tap **Install** | The prompt has **no box over it**; the install streams into the Output Panel. **During the install no box appears and beat 5 is NOT skipped** — the tour waits for the preview (round 2 would have walked past it) |
| G14 | Let the run finish and the Flask preview open | **5 of 10** *Your app is running* on the preview's **Back** arrow. Tapping it closes the preview (its own action) and the tour carries on |
| G15 | Back in the editor, look at the bottom | **6 of 10** *The tabs are here* — on the **whole tab bar** while the bar is visible, or on the thin reveal handle if the keyboard is hiding it. Both are the same beat: *"Five tabs, one tap away. They hide while you type — swipe up to bring them back."* |
| G16 | Tap inside that hole (a tab, or the handle) | The bar does its own job (navigate / reveal) **and** the tour advances: **7 of 10** *Packages* on the **Packages tab itself**. Tap it → the Packages tab opens |
| G17 | On the Packages tab | **8 of 10** *One-time download* on the first install card |
| G18 | Continue | **9 of 10** *Terminal* on the **Terminal tab** → tap it → **10 of 10** *What it is doing* on the terminal's status chip → tap it |
| G19 | After the tenth tap | **The finish card**: a dim backdrop, `Tour · 10 of 10`, *That is the whole tour*, one line of summary, and the tour's only two buttons — **VIEW AGAIN** and **CLOSE** (owner: *"At the end option to close and view again"*) |
| G20 | Tap **CLOSE** | The card goes and **nothing returns**: no box on any tab, and after kill + relaunch still none (the beats are recorded, one per tap) |
| G21 | Instead, tap **VIEW AGAIN** | The card goes, the app **navigates to the Editor**, and the tour restarts at **1 of 10** on ☰ — all ten beats, nothing remembered |
| G22 | Relaunch after G21's replay, mid-tour | **No finish card greets you.** The card is earned by watching a tour end, not by a preference — an install that starts complete never sees it |
| G23 | On any box, tap **outside** the hole | **Nothing happens.** The box stays exactly as it was, and the tap does NOT reach the UI under the scrim (owner: *"even tap outside will not end that box"*) |
| G24 | On any box, press the **back button** | You navigate away as normal (editor → hub) and **no box follows you**. Come back to the editor: the **same beat, same number** — Back pauses a tour, it cannot cut one, and nothing was spent |
| G25 | Projects tab → ⋮ on **demo_flask** → Delete | It is **back on the next list refresh** (pull to refresh, or leave and return to the tab). A demo you edit is never touched — only a missing one is re-seeded |
| G26 | While a box is pending, open an editor dialog (⋮ menu, Save to project, Go to line, the Install? prompt) | **No box is drawn under the dialog.** It comes back when the dialog closes, on the same beat (nothing was consumed) |
| G27 | Settings → About → **Reset tips** → kill → relaunch | The slides return **and** the tour restarts at **1 of 10**. No project, file, theme or other setting changed |
| G28 *(optional, 20 s)* | At G11, choose **some other project** instead of `demo_flask` | Beat 3 cannot exist (its row is `demo_flask/app.py`), so the tour waits about **20 seconds** and then teaches **4 of 10** on RUN ▶. Beat 3 is *owed, not spent*: it returns on the next launch, or via Reset tips |

## Also worth one look each (not rows)

- **The card is fully on screen** — round 1's *"Not showing the full box guide at
  one"*: the card's height is measured instead of guessed, so on a 5" screen at the
  largest font the whole card (counter, title, body) is visible and never covers its
  own hole.
- **Beat 6's hole is the whole bottom bar** when the bar is visible. That is
  deliberate (deviation 16): the lesson is the bar, and a tap inside it lands on a
  tab, which is where beat 7 wanted you anyway.
- **Small screen / largest font:** slide 5, every box and the finish card are
  readable; the slides' copy column scrolls if it must and its button stays
  reachable (45.1 exit 5).
- **Both themes and safe mode:** legible in light and dark; **in safe mode
  (crash-loop start) neither the slides nor a box appears** — and a normal launch
  afterwards still shows them once.
- **Rotation / split screen:** a hole stays on its control and the card stays on
  screen after a rotation (anchors are window rects, placement is pure).
- **A download in flight:** while the Phase 44 bar is really downloading,
  verifying or extracting, no box appears and no beat is timed out; when it settles
  the pending beat does.
- **Leaving mid-tour:** kill the app after beat 4 and relaunch — the tour resumes
  at beat 5 (beats are recorded one at a time; nothing repeats, nothing is lost).
- **Wandering off-order:** go straight to the Terminal tab before finishing the
  editor's beats — no box appears there (beat 1 waits for the editor's ☰), and the
  terminal beats are still pending when you get back to the flow.

## If a row fails

Say which row, in your own words, and **COPY the logs**: Settings → **Logs** →
COPY (or the crash dialog → COPY REPORT). For a misplaced box, one detail helps
more than any log: **which control was lit, and where the hole actually was**
(above/below/left of it, or on the wrong control entirely). For a tour that stops,
the useful detail is **which number it stopped on and what was on screen**.

## Exit condition (from the part docs)

```text
45.1  rows G1-G8    (guide once, never again, three doors, upgrade path)
45.2  rows G9-G28   (ten beats in order and nothing skipped, no button on a tour
                     card, the control is the only way on, outside taps inert,
                     Back pauses without spending, the drawer reopens after the
                     project pick, beat 6 has a target whether the bar is hidden
                     or not, the finish card with VIEW AGAIN + CLOSE, no box
                     behind a dialog or a closed drawer, demo_flask always
                     present, Reset tips restarts everything)
PASS = all twenty-eight, plus Phase 44's round 2 on the same phone.
```
