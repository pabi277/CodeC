# CodeC Phase 45 — device round (the guide: slides, then the tour)

> **Status:** 📋 ROUND 5 WRITTEN, **NOT RUN** (2026-09-12). Round 1 came back with
> four reports and 45.2 became **one ordered tour**; round 2 came back with *"You add
> the skip option and it's not a trough guide mean it got cut"* and the tour became
> **first beat to last with no exit until the end**; round 3 came back with *"1st click
> disappear the massage and i have to click 2nd time to really work"* plus a request
> that an install pause the other options, so round 4 made **one tap do both halves**
> and added **the chrome lock**; round 4 came back with *"The lock option is good but
> still it late user can switch before the start of userland download … Make it
> instantly after 1st open"*, so round 5 made the pause **prefix-keyed instead of
> stage-keyed** — on from the first frame (see [`README.md`](README.md) §"Round 4" and
> §"Round 5"). 45.1's slides are unchanged — the owner's own decision: *"Slides stay
> as they are (GOT IT / START CODING / SKIP)."*
>
> **The build to install:** `Build APK` **`34714305062`** on `arena/01a0955a-codec`
> — round-5 commit `6c3cfea`, ✅ `success`, job `build` 10m47s, release APK
> 6,675,254 B, debug 25,693,272 B (Actions → that run → **Artifacts** →
> `CodeC-IDE-debug`). Round 4's build (`34711827176`, commit `e7759f1`) has the
> one-tap tour and the lock, but that lock **waits for the download to start**, so the
> first seconds after opening the app are still switchable — exactly what the owner
> reported. Round 3's build (`34707337429`) needs two taps per beat and has no lock.
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
4. **Round 4 added the chrome lock.** While an install is really moving — the
   one-time Linux tools, or a language you asked for — the options that cannot work
   are **paused** and say why in one sentence, and the tour pauses with them. The
   surface the install can be watched from is never paused: the **Terminal** tab for
   the Linux tools, the **Editor** for a package install. Round 5 made that pause
   **instant**: it is decided by whether the Linux tools work, not by whether the
   download has started, so it is already on in the first second after the app opens.
   Rows G34-G40.

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
| G15 | Back in the editor, look at the bottom | **6 of 10** *The tabs are here* — on the **whole tab bar** while the bar is visible, or on the thin reveal handle if the keyboard is hiding it. Both are the same beat: *"Five tabs, one tap away. They hide while you type — tap this handle to bring them back."* (round 4: the copy says **tap**, because while a box is up a swipe is not a tap — see G32) |
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

## The rows — rounds 4 and 5 (one tap does both halves; an install pauses the app, instantly)

G29-G33 are the owner's *"1st click disappear the massage and i have to click 2nd time
to really work but if someone don't click 2nd time it just cut off the flow of
tutorial"*; G34-G38 are *"When the userland is installing and unpacking the user can
not access any other other option and it will show a sweet massage of why"*; **G39-G40
are round 5's** *"still it late user can switch before the start of userland download
because is takes a little time to connect and user can switch task between them / Make
it instantly after 1st open and others are ok"*. Walk G29-G33 on the same tour as
G9-G19 — they are the same ten beats, tapped once each. G34, G38 and G39 want a
**fresh install**: they are about the first seconds.

| # | What to do | PASS looks like |
|---|---|---|
| G29 | On **1 of 10**, tap the highlighted **☰** ONCE | The drawer opens **and** the box is already **2 of 10** on the project name — one tap, both halves. Round 3 needed a second tap for the drawer |
| G30 | Continue with ONE tap per beat: project name → `demo_flask` → `app.py` → **RUN ▶** | Each tap does its own work *and* moves the counter: the picker opens, `app.py` opens in the editor, RUN ▶ runs (its Output Panel opens). **Nothing runs twice** — one `$ python app.py`, one file opened, no double toast |
| G31 | On **6 of 10** with the **whole bar** lit, tap the **Packages tab** inside the hole | The Packages tab opens **and** the tour advances to **7 of 10**. Then tap a tab the tour does NOT use (Projects, Editor, Settings) while a bar-wide box is up: **that tab opens** (the tap is left to it) and the tour still advances |
| G32 | On **6 of 10** with the thin **handle** lit (keyboard up), TAP it, then SWIPE it up | The tap reveals the bar **and** advances. A swipe while the box is up does **nothing at all** — the box stays, no beat is spent — and the card's copy tells you to tap, not swipe. With the tour over, the swipe works as it always did |
| G33 | On **8 of 10** (the Packages card), start a scroll **inside** the hole and drag out | **Nothing happens**: no install starts, the box stays on the same beat. Only a tap performs the card's button. If Python is already installed, the card publishes no click at all — the tap behaves like an ordinary tap on the card and the tour advances |
| G34 | Fresh install (or Terminal → ⬇ to repair), stay on the **Terminal** tab while it downloads/unpacks | The other four tabs are **dimmed with a small 🔒** on each, **already at the first frame** — before any percentage exists (round 5). The sentence arrives by itself as the app opens: first *"Hang tight — CodeC is getting ready to set up its Linux tools. Other options are paused for a moment so this one-time setup finishes cleanly. The Terminal tab shows every step."*, then the download's own version with the moving *(NN %)*. The **Terminal tab stays open**, and the setup bar keeps the percentage |
| G35 | During that pause, tap **Projects**, **Editor**, **Packages** and **Settings** | Each shows the same sentence and **does not navigate** — you stay on the Terminal. No box from the tour appears either (the tour pauses with the chrome), and it comes back on the same beat once the setup settles |
| G36 | In the editor, RUN ▶ a Python file → **Install** | The install streams into the Output Panel; **Projects / Packages / Settings / Terminal are paused** with *"Hang tight — CodeC is installing what you asked for … The Output panel shows every step."*, while the **Editor stays open** (that is where the panel is). ☰ and RUN ▶ show the same sentence instead of acting, and the drawer's edge swipe is dead. When the install finishes, **everything unlocks by itself** and the Flask run/preview continues |
| G37 | Run a program of your own (C, or a Flask server) and leave it running | **Nothing is paused.** A run or a server is not an install: tabs, ☰ and RUN ▶ all work while the Output Panel streams |
| G38 | Kill the app mid-install and relaunch | No stale pause: the app opens on the **Terminal** tab (44.1's divert), the other tabs are paused from the first frame, and the download resumes or restarts. An in-flight **upgrade** of a working userland pauses nothing at all (its own line is *"everything still works"*) |
| G39 | **Fresh install, first second:** the moment the app opens — before any percentage, and with the network slow or OFF, before any progress at all — tap **Projects**, **Packages** and **Settings** | Each is already dimmed with a 🔒, already answers *"Hang tight — CodeC is getting ready to set up its Linux tools … The Terminal tab shows every step."*, and **does not navigate**. There is no window in which a tab can be switched. With the network OFF the taps stay paused only until the setup reports *offline* — then the app reopens and the refusal sentence + the ⬇ retry take over, because a setup that has stopped never keeps the app paused |
| G40 | On an **installed** phone: kill, relaunch, watch the first frame; then run a program of your own | **No pause flashes at launch** — the prefix is read from the disk before the first frame is drawn, so a working phone never sees the lock. With a C build or a Flask server running, nothing is paused either |

## Also worth one look each (not rows)

- **The card is fully on screen** — round 1's *"Not showing the full box guide at
  one"*: the card's height is measured instead of guessed, so on a 5" screen at the
  largest font the whole card (counter, title, body) is visible and never covers its
  own hole.
- **Beat 6's hole is the whole bottom bar** when the bar is visible. That is
  deliberate (deviation 16): the lesson is the bar, and a tap inside it lands on a
  tab, which is where beat 7 wanted you anyway. Round 4 made that literal — the tap
  resolves to the tab under the finger and performs **that tab's** click.
- **One tap per beat, everywhere.** Round 4's rule is that the highlighted control
  performs its own action *and* the tour advances, in the same gesture, with the
  gesture swallowed so nothing can fire twice. If any beat ever needs two taps again,
  that is the bug this round exists to remove — say which beat.
- **The tour's tenth beat is a label.** The terminal's status chip has no click, so
  its box advances on a tap and performs nothing: that is intended, not a dead tap.
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
r4    rows G29-G38  (ONE tap per beat does both halves and nothing fires twice,
                     a bar-wide hole resolves to the tab under the finger, a
                     drag is not a tap, an installed card performs nothing, and
                     the chrome lock: an install pauses the other options with a
                     sweet sentence, keeps the surface that shows it open, never
                     pauses a run or a server, and clears by itself)
r5    rows G39-G40  (the pause is on in the FIRST SECOND — before any percentage
                     and before the network answers — a stopped setup reopens the
                     app, safe mode is never funnelled, and a working prefix
                     never flashes a pause at launch)
PASS = all forty, plus Phase 44's round 2 on the same phone.
```
