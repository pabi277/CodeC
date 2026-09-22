# CodeC — Phases 54–58 · the phone UI, from the real shots

> **Owner (2026-09-22, verbatim):** *“Now create phase wise plan remove add what needed always say to research and take the reference seriously.”*
>
> **Earlier rows this plan is built on, also verbatim:**
> - *“They accept only this UI”* — the phone app in the seven screenshots, not store art, not a new design.
> - *“after that the project bar no need a separate space at the bottom.”*
> - First open is the editor, on sample snake code, not a picker that dumps the user on a locked terminal.
> - Userland installs silently. A language run that needs a download before userland is ready must warn: finish installing userland first.
> - The HTML page view’s upper links are not phone-friendly; they want a hamburger-style treatment.
> - *“if need say me”* — if a screen is not in the shots, ask. Do not invent it.

> **Status:** 📋 **PLANNED. No app code.** This file is the plan. Part docs are written when the owner says **“Start Phase N”**, and only after that phase’s research pass.
>
> **Numbering:** the last used number is 53 (❌ cancelled). 52 is a different series and is not cancelled by this plan. These phases are **54, 55, 56, 57, 58**, in that order.
>
> **Reference of record:** the seven files in `docs/spck-ui/Screenshot_20260922_*.jpg` (commit `dd81a6d`, “Real ss”). The drawings in the same folder are not the reference. Store art in `image-search/` is not the reference. A later agent who codes from a drawing, or from memory of a drawing, has already failed the phase.

---

## The law on every phase

**Research first. Take the reference seriously. Then code.**

Before a line of app code in 55–58:

1. Open the seven phone shots again. Do not trust this roadmap’s summary over the pixels.
2. Re-read the CodeC file the part will touch. Cite `file:line` on the current `main`, not a remembered line.
3. If a claim is about SPCK behavior that is **not in the shots**, fetch the official page again and name the URL and the date. Docs already used, and they do not outrank a shot:
   - `https://spckio.github.io/spck-documentation/getting-started.html`
   - `https://docs.spck.io/en/editor`
4. If the control is in neither a shot nor a freshly fetched page, **stop and ask**. Do not invent a menu, a preview bar, or an action for `>>`.
5. Reject, in writing, anything that came from store art or from `docs/spck-ui/0*.png`.

Closed source means visible behavior only. Do not copy SPCK’s code, their snake sample, their git engine, or their shop.

---

## Remove / add

This is the whole product change. A phase that adds a shop page, a bottom tab, or a control the shots do not show has left the plan.

### Remove

| What | Where it is today (`main` @ `afc5979`, 2026-09-22) | When |
|---|---|---|
| The five-tab project bar: Projects · Editor · Terminal · Packages · Settings | `MainActivity.kt` `FlatBottomBar` (~:1677), wired from the scaffold `bottomBar` | **56**, only after 55 can open those rooms |
| The “Show tabs” handle that exists only to bring that bar back | `EditorNavRevealHandle` in `MainActivity.kt`; `NavBarPolicy.hideNavBar` (~:1060) | **56** |
| Coach marks that point at that bar or that handle | Phase 45 tour. A mark must not point at a deleted control | **56**, move or drop the beat. Do not leave a hole in the tour |
| The word RUN, and the ⋮, in the editor top row | `EditorScreen.kt` top bar (RUN label + `MoreVert`) | **57**. The actions move. They are not deleted until research names a home the shots allow |
| The permanent setup strip across every tab | `SetupBar` call in `MainActivity.kt` (~:1272) | **58**. Silent install. A warning only when a run needs the download |
| First open landing on the Projects hub | `MainActivity.kt` ~:932–937: no resume → `Screen.FileManager.route`. Welcome tiles are C / Python / HTML, not snake (`WelcomeStarters.kt`) | **58** |
| The full-screen files-only drawer as the only thing ☰ opens | `EditorProjectDrawer.kt` | **55**, replaced by the side panel. The tree itself stays |

Phase 32 hid the bottom bar while typing. That was the right patch for a bar that had to exist. **56 removes the bar.** Do not “fix” 56 by hiding it again.

### Add

| What the shots show | Phase |
|---|---|
| A side panel over most of the width. A strip of the editor stays, including the green play. `>>` peeks at the bottom of that strip | 55 |
| Rail of five icons, selected one underlined: outline triangle, folder, search-with-`<>`, branch, person | 55 |
| Navigation: one card, **3 columns × 2 rows**. Top row in the shot: Projects, Editor (selected, center), Settings. Bottom row in the shot: Discover, My Labs, Change Log — **do not copy those three** | 55, after the owner names the bottom row |
| Recent: a stacked list. Name, relative time, External only when the project really came from outside | 55 |
| Files: header actions (search, locate, new file, new folder, `...`), project name as the tree root, green triangle on the launch-default file, full-width selected row, HTML5 shield, TXT page icon | 55. The `...` **menu** is not in the shots. Do not build it |
| Search: icon toggles (regex, Aa, whole word, filter, replace), “Find Text”, RESULTS with collapse / refresh / clear. Empty means empty | 55 |
| Repository, empty: one left-aligned sentence, one centered blue “Initialize Repository” button | 55. The screen after the button is not in the shots. Do not invent it |
| Editor top row: ☰, file icon, filename, search, green triangle. No RUN word. No ⋮ | 57 |
| Tab row under that, with a colored top edge on the active tab | 57 |
| Status line: `Ln …, Col …` left, `Sp: 4  HTML  LF  UTF-8` right. Hidden while the system keyboard is up | 57 |
| Touch row, always docked: `→|`, up, down, left, right, quote bubble, braces mark, `>>` | 57. The action of `>>` is unknown. Draw it only if it does nothing guessed, or omit it and ask |
| Predictive row, only while the system keyboard is up. The shot shows `<tag> div class = ""` plus a small icon on the right. Gboard stays, including its own toolbar and number row | 57 |
| A large blue drop under the caret while typing (the keyboard-up shot, on `</html>`). Keyboard-down has a thin caret, no drop | 57, after research of the existing sora caret. Do not add a second caret by accident |
| Short errors as a pill: app mark + a few words. “Error opening file.” and “Refreshed Files” | 57 |
| First open: the editor, on a snake sample **we write**. Do not copy SPCK’s snake | 58 |
| Silent userland. One clear warning when a run needs a download and userland is not ready | 58 |
| Hamburger for the page’s upper links (Content / Home / More are in `index.html` in the shots) | 58, after the research pass says whether the owner meant the page, the preview chrome, or both |

### Do not add

- Upgrade, credits, AI model, username, Messages, Activity Log
- Discover, My Labs, Change Log
- A bottom tab bar, or a new strip where the project bar was
- A permanent output dock. No shot shows a run
- A file `...` menu, a Play/preview screen, a long-press bar, or an action for `>>` or the tab-row down-arrow, until a shot or a freshly fetched doc shows it
- SPCK’s git engine. The empty Repository panel is the visual. The implementation stays CodeC’s git
- A replacement for the system keyboard

---

## Order

```text
  54  Research lock. No chrome change. The shots are re-read. Missing screens are asked for.
        │
        ▼
  55  Add the side panel. The bottom bar stays, so nothing is unreachable.
        │
        ▼
  56  Remove the project bar and the “Show tabs” handle.
        │
        ▼
  57  Editor chrome: top row, tab row, status line, two key rows, pills.
        │
        ▼
  58  First open is the editor on snake. Userland is silent. HTML links get a hamburger.
```

55 cannot remove the bar. 56 cannot start until 55 can open Terminal, Packages, and Settings. 57 does not guess unseen actions. 58 does not invent the preview chrome.

One command per phase: **“Start Phase 54”**, then 55, then 56, then 57, then 58.

---

## 54 — Reference lock

```text
  54.1  Re-open the seven shots. Write a reference card a later phase must cite
  54.2  file:line of every CodeC control this series will remove or move
  54.3  The missing-shot list, asked, not filled in
```

| Part | Title | Effort | Status |
|---|---|---|---|
| 54.1 | The shots, not the drawings | S | 📋 PLANNED |
| 54.2 | What CodeC has today | S | 📋 PLANNED |
| 54.3 | What is not in the shots | S | 📋 PLANNED |

**Research this phase must do.** Open each jpg. Note the rail, the grid shape, the touch-row glyphs, the pill, the status line, and the editor strip. Re-fetch the two SPCK doc pages if a sentence in this roadmap is about to be treated as a fact that is not in a shot. Record what was rejected: store art, the 2-column drawing, the Account shop.

**Exit.** A reference card in `docs/chat-phase54/`. Zero app files changed. The owner has been asked, in one place:

1. Bottom row of the 3×2 card. The shot’s cells are Discover, My Labs, Change Log. Those stay out. A proposal, not a decision: Terminal, Packages, Guide. Say if that is wrong.
2. Fifth rail icon. The shot opens Account. We will not build that. About, or Settings, or nothing until you say.
3. The shots still missing: Play on `index.html`, the file `...` menu, a long-press on code, what `>>` opens, what the tab-row down-arrow does.

**No code until those three are answered or explicitly deferred.** Deferring means the later phase omits that control. It does not mean the agent guesses.

---

## 55 — Add the side panel

```text
  55.1  Panel shell: most of the width, play stays visible, rail with underline
  55.2  Navigation card, 3×2, plus Recent. Bottom row = the owner’s answer from 54
  55.3  Files, Search, Repository: only what the shots show
```

| Part | Title | Effort | Status |
|---|---|---|---|
| 55.1 | The panel and the rail | M | 📋 PLANNED |
| 55.2 | Navigation and Recent | M | 📋 PLANNED |
| 55.3 | Files, Search, Repository | M | 📋 PLANNED |

**Research before code.** Re-open `Screenshot_20260922_124049` (Navigation), `122203` (Files), `124052` (Search), `124055` (Repository). Measure the strip: the green play, the tab-row icon, and `>>` must remain tappable. Do not start from `03-navigation.png`.

**Add.** The panel. The rail. The 3×2 card. Recent. The three panels’ empty and tree states as the shots show them.

**Do not add.** Discover, My Labs, Change Log, Upgrade. The file `...` menu. The screen after Initialize Repository. A second project bar.

**The bottom bar stays** until 56. ☰ opens the panel. The tabs still work. That is temporary, and the device round must say so.

**Exit.** On a phone, ☰ opens a panel that matches the shots’ shell. Play works with the panel open. Files shows the open project’s name as the root and a green triangle on the launch-default HTML file. Search empty is empty. Repository empty is one sentence and one button. A host test pins the rail order and that the bottom row is the owner’s named rooms, not Discover.

**Device pass required.** Yes.

---

## 56 — Remove the project bar

```text
  56.1  Delete the five-tab bar and the “Show tabs” handle
  56.2  The guide no longer points at a deleted control
```

| Part | Title | Effort | Status |
|---|---|---|---|
| 56.1 | The strip is gone | M | 📋 PLANNED |
| 56.2 | The tour still has a next beat | S | 📋 PLANNED |

**Research before code.** Re-open every shot and confirm none of them has a bottom project bar. Re-read `NavBarPolicy.kt` and the Phase 45 coach-mark plan. Phase 32’s hide-while-typing is the thing this phase retires, not a pattern to copy.

**Remove.** `FlatBottomBar`. `EditorNavRevealHandle`. Any “Show tabs” string. The tour beat that spotlights that handle.

**Do not remove.** The status line. The touch row. Android’s own gesture bar. The way to reach Terminal, Packages, and Settings — that now lives in the 55 card. If 55 did not ship that card, 56 does not start.

**Exit.** The editor’s bottom is the status line and the touch row, then the system keyboard when it is up. No fifth strip. A source-scan test fails if `FlatBottomBar` or `Show tabs` returns. The guide still runs from first beat to last without pointing at a missing control.

**Device pass required.** Yes. Side by side with `Screenshot_20260922_122157`.

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

**Remove from the top row.** The word RUN. The ⋮. Undo, save, and format do not disappear as features. Their new home is **not in the shots**. 57.1 asks, or leaves them on the existing overflow **off** the top row, and records that as deferred. It does not invent a toolbar.

**Add.** The tab row under the filename. The status split (line/column left, spaces / language / LF / encoding right). Hide that line while the IME is up. The touch row from the shot, docked even when the keyboard is closed. The predictive row only while the IME is up. A pill for “Error opening file.” and “Refreshed Files”, not a dialog and not a dump.

**Unknown, so not built as a behavior.** What `>>` does. What the tab-row down-arrow does. What the small icon on the right of the predictive row does. What a long-press on selected code shows. A glyph may be drawn to match the shot only if tapping it does not perform a guessed action. Prefer omit-and-ask.

**Exit.** Keyboard down matches `122157`: no project bar, status line, touch row. Keyboard up matches `124105`: no status line, predictive row, touch row, system keyboard. A wiring test pins that the top row has no RUN label and no overflow icon.

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

- 58.1: there is no snake sample in the repo (search on 2026-09-22 found none). Write a small original HTML snake. Do not transcribe SPCK’s. C and HTML preview must run without waiting on userland. Python still needs its download.
- 58.2: re-read `UserlandInstaller.kt` and `SetupGatePolicy`. The standing row is silent install, plus one sentence when a language run needs a download and userland is not ready: finish installing userland first. C and the HTML preview do not wait. Do not bring back “hang tight” or “don’t close”.
- 58.3: the shots show Content / Home / More as buttons in `index.html`. They do **not** show the Play screen. Two different fixes are possible: a hamburger in that page’s nav, and a hamburger in CodeC’s preview chrome (`WebPreviewScreen.kt` stacks an address row and `ServerSharePanel` above the page). Research both. If a Play shot has arrived, use it. If not, ask. Do not invent the preview chrome from the store Snake image.

**Remove.** The setup bar as a permanent strip. The first-open path that lands on the Projects hub when there is no last file.

**Add.** A snake sample, opened in the editor on first launch. One warning pill or one line, only in the case the standing row names.

**Exit.** A fresh install opens the editor on snake, not the hub, and not a locked terminal. Userland can be downloading with no bar on the editor. Running that HTML previews it. Running a language that needs a download, before userland is ready, says to finish the userland install first. The hamburger change matches whichever of the two the research pass proved, and says which one it was.

**Device pass required.** Yes.

---

## What this plan does not touch

- Phase 52 (the return). It is a different row. Do not fold it into 54–58, and do not cancel it from here.
- Phase 53. Already cancelled. The number stays used.
- Phase 32’s history. It stays merged. 56 is the new row that removes the bar 32 only hid.
- Git’s implementation, the compiler, the package signer, the clean-room rule. Repository’s **empty panel** is a visual. The engine is not replaced.
- No new dependency, permission, or telemetry unless a part doc’s Deferred section justifies it when that phase starts.

---

## How a phase starts

The owner says **“Start Phase N”**. The agent then:

1. Verifies git state (`docs/HOW_TO_CREATE_A_PHASE.md` step 1).
2. Does that phase’s research pass, against the shots, and writes the part docs.
3. Implements only that phase. Pure policy where the decision can be tested without a phone. Wiring pin so the policy is not decoration.
4. Pushes the session branch. CI is the executor of record. Device rows are marked device-pass-required.
5. Stops at the merge gate. No PR to `main` without the owner’s command.

If the research pass finds the shot and this roadmap disagree, **the shot wins**, and the roadmap is corrected in the same commit. That is what “take the reference seriously” means.
