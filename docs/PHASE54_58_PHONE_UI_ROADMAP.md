# CodeC — Phases 54–58 · the phone UI, reviewed

> **Owner (2026-09-22, this review, verbatim):** *“I don't think removing the full down ber is a good choice i think only removing the project option is ok.”*
>
> That row **reverses** the earlier reading of *“after that the project bar no need a separate space at the bottom.”* The earlier sentence was taken to mean delete the whole bottom bar. The owner has now said that is not the choice. **Only the Projects option leaves the bottom bar.** Editor, Terminal, Packages, and Settings stay there.
>
> **Also still in force, verbatim:**
> - *“create phase wise plan remove add what needed always say to research and take the reference seriously.”*
> - *“They accept only this UI”* — the phone app in the seven screenshots, not store art, not a new design. Where this review and a shot disagree, **this review wins on the bottom bar only.** Everything else still comes from the shots.
> - First open is the editor, on sample snake code, not a picker that dumps the user on a locked terminal.
> - Userland installs silently. A language run that needs a download before userland is ready must warn: finish installing userland first.
> - The HTML page view’s upper links are not phone-friendly; they want a hamburger-style treatment.
> - *“if need say me”* — if a screen is not in the shots, ask. Do not invent it.

> **Status:** 📋 **PLANNED. No app code.** Reviewed 2026-09-22 after the owner rejected deleting the full bottom bar. Part docs are written when the owner says **“Start Phase N”**, and only after that phase’s research pass.
>
> **Numbering:** the last used number is 53 (❌ cancelled). 52 is a different series and is not cancelled by this plan. These phases are **54, 55, 56, 57, 58**, in that order.
>
> **Reference of record:** the seven files in `docs/spck-ui/Screenshot_20260922_*.jpg` (commit `dd81a6d`, “Real ss”). The drawings in the same folder are not the reference. Store art in `image-search/` is not the reference. A later agent who codes from a drawing, or from memory of a drawing, has already failed the phase.
>
> **One recorded exception to the shots:** those shots have no bottom bar at all. The owner looked at that and rejected copying it. Do not “research” the shots into deleting Editor, Terminal, Packages, or Settings from the bottom. Research the shots for the side panel, the editor, the pills, and the keys. The bottom bar is an owner decision, not a missing screenshot.

---

## The law on every phase

**Research first. Take the reference seriously. Then code.**

Before a line of app code in 55–58:

1. Open the seven phone shots again. Do not trust this roadmap’s summary over the pixels, except on the one point the owner already decided: the full bottom bar stays, and only Projects comes off it.
2. Re-read the CodeC file the part will touch. Cite `file:line` on the current `main`, not a remembered line.
3. If a claim is about SPCK behavior that is **not in the shots**, fetch the official page again and name the URL and the date. Docs already used, and they do not outrank a shot:
   - `https://spckio.github.io/spck-documentation/getting-started.html`
   - `https://docs.spck.io/en/editor`
4. If the control is in neither a shot nor a freshly fetched page, **stop and ask**. Do not invent a menu, a preview bar, or an action for `>>`.
5. Reject, in writing, anything that came from store art or from `docs/spck-ui/0*.png`.
6. Reject, in writing, the old plan sentence “56 removes the bar.” That sentence is withdrawn.

Closed source means visible behavior only. Do not copy SPCK’s code, their snake sample, their git engine, or their shop.

---

## What changed in this review

| Earlier plan | This review |
|---|---|
| Delete `FlatBottomBar` | **Keep it** |
| Delete the “Show tabs” handle | **Keep it.** The bar still hides while typing (Phase 32). The handle still brings that bar back |
| Projects · Editor · Terminal · Packages · Settings | **Editor · Terminal · Packages · Settings.** Projects is the only option removed |
| Side panel replaces the bottom bar as the way to leave the editor | Side panel is where **Projects** lives, plus Files, Search, and Repository. The other rooms stay one tap on the bar |
| Phase 56 cannot start until the panel can open Terminal, Packages, and Settings | Phase 56 cannot start until **Projects** can be opened from the side panel. The other four tabs never leave |

Four tabs, not five. Do not invent a fifth tab to fill the hole. Terminal will no longer sit in the center. That is the result of removing one option, not a redesign. If the owner wants a different remaining set, they say so before 56.

---

## Remove / add

### Remove

| What | Where it is today (`main` @ `afc5979`, 2026-09-22) | When |
|---|---|---|
| The **Projects** option on the bottom bar only. The screen stays. The tab goes | `MainActivity.kt` tab list: `Screen.FileManager` (“Projects”), then Editor, Terminal, Packages, Settings. `FlatBottomBar` (~:1677) | **56**, only after 55 can open Projects from the side panel |
| The coach-mark beat that spotlights the Projects tab | Phase 45 tour. A mark must not point at a tab that is gone | **56**, move that beat to the Navigation card’s Projects cell. Do not drop the Packages or Terminal beats. Those tabs stay |
| The word RUN, and the ⋮, in the editor top row | `EditorScreen.kt` top bar (RUN label + `MoreVert`) | **57**. The actions move. They are not deleted until research names a home the shots allow |
| The permanent setup strip across every tab | `SetupBar` call in `MainActivity.kt` (~:1272) | **58**. Silent install. A warning only when a run needs the download |
| First open landing on the Projects hub | `MainActivity.kt` ~:932–937: no resume → `Screen.FileManager.route`. Welcome tiles are C / Python / HTML, not snake (`WelcomeStarters.kt`) | **58** |
| The full-screen files-only drawer as the only thing ☰ opens | `EditorProjectDrawer.kt` | **55**, replaced by the side panel. The tree itself stays |

**Not removed.** `FlatBottomBar`. `EditorNavRevealHandle`. `NavBarPolicy.hideNavBar`. The Editor, Terminal, Packages, and Settings tabs. The status line. The touch row. Android’s own gesture bar.

### Add

| What | Phase |
|---|---|
| A side panel over most of the width. A strip of the editor stays, including the green play. `>>` peeks at the bottom of that strip. Research the shots for this. The bottom bar is not part of that copy | 55 |
| Rail of five icons, selected one underlined: outline triangle, folder, search-with-`<>`, branch, person | 55 |
| Navigation: one card, **3 columns × 2 rows**. Top row in the shot: Projects, Editor (selected, center), Settings. Bottom row in the shot: Discover, My Labs, Change Log — **do not copy those three**. Projects in this card is how you open a project once the bottom tab is gone | 55, after the owner names the bottom row |
| Recent: a stacked list. Name, relative time, External only when the project really came from outside | 55 |
| Files, Search, Repository: only what the shots show | 55 |
| Editor top row, tab row, status line, touch row, predictive row, pills: only what the two editor shots show | 57 |
| First open on a snake sample we write. Silent userland. One warning. Hamburger for the upper links after research | 58 |

### Do not add

- A second home for Projects on the bottom bar
- A filler tab so the bar stays at five
- Upgrade, credits, AI model, Discover, My Labs, Change Log
- A replacement for the system keyboard
- A file `...` menu, a Play/preview screen, or an action for `>>` or the tab-row down-arrow, until a shot or a freshly fetched doc shows it
- SPCK’s git engine. The empty Repository panel is the visual. The implementation stays CodeC’s git
- A permanent output dock. No shot shows a run

---

## Order

```text
  54  Research lock. No chrome change. Re-read the shots. Record the owner's bar decision.
        │
        ▼
  55  Add the side panel. Projects is a cell in the Navigation card. The bottom bar is unchanged.
        │
        ▼
  56  Remove only the Projects tab. The other four tabs stay. “Show tabs” stays.
        │
        ▼
  57  Editor chrome from the two editor shots. The bottom bar is not part of this copy.
        │
        ▼
  58  First open is the editor on snake. Userland is silent. HTML links get a hamburger.
```

56 cannot start until a tap on Projects in the side panel opens the Projects screen. 57 does not guess unseen actions. 58 does not invent the preview chrome.

One command per phase: **“Start Phase 54”**, then 55, then 56, then 57, then 58.

---

## 54 — Reference lock

```text
  54.1  Re-open the seven shots. Write a reference card a later phase must cite
  54.2  file:line of every CodeC control this series will remove or move
  54.3  The missing-shot list, and the bar decision, written so 56 cannot drift
```

| Part | Title | Effort | Status |
|---|---|---|---|
| 54.1 | The shots, not the drawings | S | 📋 PLANNED |
| 54.2 | What CodeC has today | S | 📋 PLANNED |
| 54.3 | What is not in the shots, and what the owner already decided | S | 📋 PLANNED |

**Research this phase must do.** Open each jpg. Note the rail, the grid shape, the touch-row glyphs, the pill, the status line, and the editor strip. Also note, in the same card, that the shots have no bottom bar and that the owner rejected copying that absence. Re-fetch the two SPCK doc pages if a sentence here is about to be treated as a fact that is not in a shot. Record what was rejected: store art, the 2-column drawing, the Account shop, and “delete the whole bottom bar.”

**Exit.** A reference card in `docs/chat-phase54/`. Zero app files changed. The card states, in one place:

1. Bottom bar stays. Only the Projects option is removed, and only in 56, after Projects has a home in the side panel.
2. Bottom row of the 3×2 card. The shot’s cells are Discover, My Labs, Change Log. Those stay out. A proposal, not a decision: Terminal, Packages, Guide. Say if that is wrong. Projects, Editor, and Settings are already in the shot’s top row. Projects must be one of the cells, because that is its new door.
3. Fifth rail icon. The shot opens Account. We will not build that. About, or Settings, or nothing until you say.
4. Shots still missing: Play on `index.html`, the file `...` menu, a long-press on code, what `>>` opens, what the tab-row down-arrow does.

**No chrome code until 2, 3, and 4 are answered or explicitly deferred.** Deferring means the later phase omits that control. It does not mean the agent guesses. Item 1 is already answered.

---

## 55 — Add the side panel

```text
  55.1  Panel shell: most of the width, play stays visible, rail with underline
  55.2  Navigation card, 3×2, plus Recent. Projects is a cell. Bottom row = the owner's answer from 54
  55.3  Files, Search, Repository: only what the shots show
```

| Part | Title | Effort | Status |
|---|---|---|---|
| 55.1 | The panel and the rail | M | 📋 PLANNED |
| 55.2 | Navigation and Recent | M | 📋 PLANNED |
| 55.3 | Files, Search, Repository | M | 📋 PLANNED |

**Research before code.** Re-open `Screenshot_20260922_124049` (Navigation), `122203` (Files), `124052` (Search), `124055` (Repository). Measure the strip: the green play, the tab-row icon, and `>>` must remain tappable. Do not start from `03-navigation.png`. Do not treat the absence of a bottom bar in those shots as an instruction to hide CodeC’s bar in this phase.

**Add.** The panel. The rail. The 3×2 card, with Projects on it. Recent. The three panels’ empty and tree states as the shots show them.

**Do not add.** Discover, My Labs, Change Log, Upgrade. The file `...` menu. The screen after Initialize Repository. A change to the bottom bar. That is 56.

**The bottom bar is unchanged in 55.** Five tabs still work, including Projects. That is temporary. The device round must say so.

**Exit.** On a phone, ☰ opens a panel that matches the shots’ shell. Play works with the panel open. A tap on Projects in the card opens the Projects screen. Files shows the open project’s name as the root and a green triangle on the launch-default HTML file. Search empty is empty. Repository empty is one sentence and one button. The bottom bar still has five tabs. A host test pins the rail order and that Projects is in the card.

**Device pass required.** Yes.

---

## 56 — Remove only the Projects option

```text
  56.1  Take Projects off the bottom bar. Leave the other four tabs
  56.2  The guide's Projects beat moves to the side panel. The other tab beats stay
```

| Part | Title | Effort | Status |
|---|---|---|---|
| 56.1 | Projects leaves the bar | S | 📋 PLANNED |
| 56.2 | The tour still teaches where projects are | S | 📋 PLANNED |

**Research before code.** Re-read the tab list in `MainActivity.kt` and `NavBarPolicy.kt`. Re-read the Phase 45 coach-mark plan and name the beat that spotlights Projects. Re-open the Navigation shot and confirm Projects is a cell in the card. Do not re-open the shots in order to delete the bar. The owner already decided that.

**Remove.** `Screen.FileManager` from the bottom-tab list only. The Projects screen, its route, and the hub stay. The tour beat that pointed at the Projects tab moves to the Navigation card’s Projects cell.

**Do not remove.** `FlatBottomBar`. `EditorNavRevealHandle`. The “Show tabs” string. The Editor, Terminal, Packages, and Settings tabs. Phase 32’s hide-while-typing. If 55 did not put Projects in the side panel, 56 does not start.

**Exit.** The bottom bar reads Editor · Terminal · Packages · Settings. It still hides while typing, and “Show tabs” still brings it back. Projects is not on it. Projects opens from the side panel. A source-scan test fails if `Screen.FileManager` is in the bottom-tab list, and fails if `FlatBottomBar` is deleted. The guide still runs from first beat to last.

**Device pass required.** Yes. Confirm the four remaining tabs, and confirm Projects is only in the panel.

---

## 57 — Editor chrome

```text
  57.1  Top row and tab row, as the shots
  57.2  Status line, touch row, predictive row. System keyboard stays
  57.3  Pills, not dialogs, for the short messages the shots show
```

| Part | Title | Effort | Status |
|---|---|---|---|
| 57.1 | Filename, tab, green triangle | M | 📋 PLANNED |
| 57.2 | The two rows above the system keyboard | L | 📋 PLANNED |
| 57.3 | One pill | S | 📋 PLANNED |

**Research before code.** Re-open `122157` (keyboard down) and `124105` (keyboard up). Re-read `EditorScreen.kt` top bar, `EditorStatusBar.kt`, and the keys row. For the blue drop, read the sora caret path before adding a view. If sora already draws a handle, style that. Do not stack a second caret on it.

The shots have no bottom bar under the touch row. CodeC will still have one, by the owner’s review. 57 copies the editor above that bar. It does not delete the bar to make the screenshot match.

**Remove from the top row.** The word RUN. The ⋮. Undo, save, and format do not disappear as features. Their new home is **not in the shots**. 57.1 asks, or leaves them on the existing overflow **off** the top row, and records that as deferred. It does not invent a toolbar.

**Add.** The tab row under the filename. The status split (line/column left, spaces / language / LF / encoding right). Hide that line while the IME is up. The touch row from the shot, docked even when the keyboard is closed. The predictive row only while the IME is up. A pill for “Error opening file.” and “Refreshed Files”, not a dialog and not a dump.

**Unknown, so not built as a behavior.** What `>>` does. What the tab-row down-arrow does. What the small icon on the right of the predictive row does. What a long-press on selected code shows. A glyph may be drawn to match the shot only if tapping it does not perform a guessed action. Prefer omit-and-ask.

**Exit.** Above the bottom bar, keyboard down matches `122157`: status line, then touch row. Keyboard up matches `124105`: no status line, predictive row, touch row, system keyboard. The four-tab bar is still under that. A wiring test pins that the top row has no RUN label and no overflow icon, and that `FlatBottomBar` is still composed.

**Device pass required.** Yes.

---

## 58 — First open, silent install, hamburger

```text
  58.1  First open is the editor, on a snake sample we write
  58.2  Userland installs with no strip. One warning when a run needs it
  58.3  The page’s upper links become a hamburger, after the research pass
```

| Part | Title | Effort | Status |
|---|---|---|---|
| 58.1 | Open on snake | M | 📋 PLANNED |
| 58.2 | Silent userland, one warning | M | 📋 PLANNED |
| 58.3 | Upper links | S/M | 📋 PLANNED |

**Research before code.**

- 58.1: there is no snake sample in the repo (search on 2026-09-22 found none). Write a small original HTML snake. Do not transcribe SPCK’s. C and HTML preview must run without waiting on userland. Python still needs its download. First open is the editor, not the Projects tab — which by then is not on the bar anyway.
- 58.2: re-read `UserlandInstaller.kt` and `SetupGatePolicy`. The standing row is silent install, plus one sentence when a language run needs a download and userland is not ready: finish installing userland first. C and the HTML preview do not wait. Do not bring back “hang tight” or “don’t close”.
- 58.3: the shots show Content / Home / More as buttons in `index.html`. They do **not** show the Play screen. Two different fixes are possible: a hamburger in that page’s nav, and a hamburger in CodeC’s preview chrome (`WebPreviewScreen.kt` stacks an address row and `ServerSharePanel` above the page). Research both. If a Play shot has arrived, use it. If not, ask. Do not invent the preview chrome from the store Snake image.

**Remove.** The setup bar as a permanent strip. The first-open path that lands on the Projects hub when there is no last file.

**Add.** A snake sample, opened in the editor on first launch. One warning pill or one line, only in the case the standing row names.

**Exit.** A fresh install opens the editor on snake, not the hub, and not a locked terminal. The bottom bar is there, without a Projects tab. Userland can be downloading with no setup strip on the editor. Running that HTML previews it. Running a language that needs a download, before userland is ready, says to finish the userland install first. The hamburger change matches whichever of the two the research pass proved, and says which one it was.

**Device pass required.** Yes.

---

## What this plan does not touch

- Phase 52 (the return). It is a different row. Do not fold it into 54–58, and do not cancel it from here.
- Phase 53. Already cancelled. The number stays used.
- Phase 32’s hide-while-typing. It stays. 56 does not retire it.
- Git’s implementation, the compiler, the package signer, the clean-room rule. Repository’s **empty panel** is a visual. The engine is not replaced.
- No new dependency, permission, or telemetry unless a part doc’s Deferred section justifies it when that phase starts.

---

## How a phase starts

The owner says **“Start Phase N”**. The agent then:

1. Verifies git state (`docs/HOW_TO_CREATE_A_PHASE.md` step 1).
2. Does that phase’s research pass, against the shots, and writes the part docs. The bottom-bar exception is re-read from this file before any tab is touched.
3. Implements only that phase. Pure policy where the decision can be tested without a phone. Wiring pin so the policy is not decoration.
4. Pushes the session branch. CI is the executor of record. Device rows are marked device-pass-required.
5. Stops at the merge gate. No PR to `main` without the owner’s command.

If the research pass finds the shot and this roadmap disagree, **the shot wins**, except where this file records an owner reversal. The bottom bar is that reversal. A later agent who deletes `FlatBottomBar` because the shots have no bar has failed the phase.
