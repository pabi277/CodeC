# CodeC Phase 45 — device round (the guide: slides + coach marks)

> **Status:** 📋 WRITTEN, **NOT RUN** (2026-09-12). Phase 45 is 🚧 IMPLEMENTED on
> `arena/01a0955a-codec`; nothing in this file may be described as tested on
> hardware until the owner reports these rows.
>
> **Why a device round:** both parts are *first-impression* features. A host test
> can prove the plan is right (which slide, which anchor, which order) and the
> source pins can prove the Android edge asks it — only a phone can say whether a
> new user actually reads slide 1, whether the spotlight lands on the ☰ instead of
> next to it, and whether the card is reachable on a 5" screen at the largest font.

## Before you start

1. **Build:** the newest green `Build APK` run on `arena/01a0955a-codec`
   (Actions → the run → **Artifacts** → `CodeC-IDE-debug`, or `CodeC-IDE-release`).
2. **Rows G1-G5 need a first run.** Two ways to get one:
   - install the APK as a **fresh install** (uninstall first, or use a second
     profile / another phone), **or**
   - in the installed build: Settings → About → **Reset tips** *and* **Show the
     welcome screen again**, then kill and relaunch the app.
3. **Phase 44 shares the phone.** If the Linux tools are not working, the app opens
   the Terminal tab first *after* the guide — that is intended: the guide explains
   the download the terminal is about to show. Phase 44's own rows are
   [`../chat-phase44/DEVICE_ROUND.md`](../chat-phase44/DEVICE_ROUND.md) (R1-R8,
   then D1-D12) and are still pending.

## The rows

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
| G9 | Reset tips → kill → relaunch → arrive at the **Editor** | **At most two** spotlights: ☰ (*Your files*), then RUN ▶ (*Run your code*) — a dark scrim with a hole around the control and a card beside it |
| G10 | On a spotlight, tap **the highlighted control itself** | The control does its own job (the drawer opens / the file runs) **and** the mark closes |
| G11 | On a spotlight, tap anywhere else, or **GOT IT**, or back | The mark closes; nothing else is tapped through |
| G12 | In the editor, bring up the keyboard so the tab bar hides and the **Show tabs** handle appears | The *The tabs are here* mark appears **only now** — never while the tab bar is visible, never pointing at empty space |
| G13 | First arrival at **Terminal**, then at **Packages** | Terminal: the status chip spotlit (*What it is doing*). Packages: the first install card spotlit (*One-time download*). Each **once** — second arrival shows nothing |
| G14 | Settings → About → **Reset tips** → kill → relaunch | The slides return **and** every coach mark returns. No project, file, theme or other setting changed |

## Also worth one look each (not rows)

- **Small screen / largest font:** slide 5 and a coach-mark card are both fully
  readable, the copy column scrolls if it must, and the button is reachable
  (45.1 exit 5).
- **Both themes and safe mode:** the guide and the marks are legible in light and
  dark; **in safe mode (crash-loop start) neither appears** — and a normal launch
  afterwards still shows the guide once.
- **Rotation / split screen:** a spotlight's hole stays on its control and the card
  stays on screen after a rotation (anchors are window rects, placement is pure).
- **An upgrade, not a fresh install:** an existing user sees the guide exactly once
  after updating, with SKIP visible on slide 1.
- **A download in flight:** while the Phase 44 bar is actually downloading, no
  coach mark appears; when it settles, the pending mark does (nothing was
  consumed).
- **A dialog or sheet open:** the mark is covered, not stacked on top, and it is
  still pending when the dialog closes.

## If a row fails

Say which row, in your own words, and **COPY the logs**: Settings → **Logs** →
COPY (or the crash dialog → COPY REPORT). For a misplaced spotlight, one extra
detail helps more than any log: **which control was lit, and where the hole
actually was** (above/below/left of it).

## Exit condition (from the part docs)

```text
45.1  rows G1-G8   (guide once, never again, three doors, upgrade path, small screen)
45.2  rows G9-G14  (two per surface, tap-through, the handle waits, once each, reset)
PASS = all fourteen, plus Phase 44's round 2 on the same phone.
```
