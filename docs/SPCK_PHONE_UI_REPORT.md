# SPCK phone UI — review, and the bottom bar

**Date:** 2026-09-22  
**Status:** review only. No app code was changed.  
**What this accepts:** the seven phone screenshots of SPCK Editor, not Play Store art, and not a new layout.  
**Your point, checked:** after this UI, the project bar does not need its own space at the bottom. That is right.

**Plan:** [`PHASE54_58_PHONE_UI_ROADMAP.md`](PHASE54_58_PHONE_UI_ROADMAP.md). No app code until you say **Start Phase 54**.

The real shots are in [docs/spck-ui](spck-ui/README.md), commit `Real ss`. They win over the drawings. The drawings got the shell right and the grid wrong. See the check below.

## 0. Checked against the seven real shots

Commit `dd81a6d` (`Real ss`) is the phone. Every shot confirms there is no project bar.

What the shots confirm:

- Top of the editor is ☰, the HTML5 shield, `index.html`, search, a green triangle. No word RUN. No ⋮.
- The tab row sits under that. The active tab has an orange top edge. A right-hand icon (lines and a down arrow) is still unnamed.
- Keyboard down: status line `Ln 81, Col 28` on the left, `Sp: 4  HTML  LF  UTF-8` on the right, then the touch row. A pill, “Refreshed Files”, floats above that line. The caret on line 81 is a thin bar. No blue drop in that shot.
- Keyboard up: the status line hides. A predictive row appears (`<tag>` `div` `class` `=` `""`, plus a small icon on the right). The same touch row stays. Gboard stays, including its own emoji/clipboard row and the number row. The blue drop is large, under the caret on `</html>`.
- The side panel leaves a strip of the editor, including the green play. `>>` from the touch row peeks at the bottom of that strip.
- Rail, selected icon underlined: outline triangle, folder, search-with-`<>`, branch, person.
- Files: the project name is the tree root. `index.html` has a green triangle and a full-width selected row. Errors are a pill with the app mark: “Error opening file.”
- Search is empty. Toggles are icons (regex, Aa, whole word, filter, replace), not text chips.
- Repository, empty: one left-aligned sentence, then a centered blue Initialize Repository button.
- Account is a shop. Do not copy it.

What the drawings got wrong, and the shots correct:

- The grid is **3 columns by 2 rows, inside one card**, not 2 columns. Real cells: Projects, Editor (selected, center of the top row), Settings, then Discover, My Labs, Change Log. Editor is not the top-right of a 2-column grid.
- Recent is a long stacked list. Every row in this shot has an External badge. Not one row.
- The first rail icon is an outline triangle, not a filled play. The green play is only the run button.
- The touch row is icons, not the word Tab: `→|`, up, down, left, right, a quote bubble, a braces mark, `>>`.
- The HTML file icon is the HTML5 shield. Text files are a TXT page.
- The real tree is longer than the drawing: app-resources, Chemistry, css, English, js, Math, Physics, Practice_question, pyq-assets, then `4.html`, `index.html`, `last_english.html`, `prompt.txt`, `structure.txt`.
- The Navigation title has a `...` on the right. The drawing omitted it.
- The repository sentence is left-aligned, not centered.

The 2-column CodeC room map in section 4 was a proposal. It is not what the phone shows. The phone’s shape is the 3×2 card. Discover, My Labs, Change Log, Upgrade, and the AI credits stay out. Where Terminal, Packages, and Guide sit in that 3×2 card is still an open choice.

---

## 1. The decision

CodeC today keeps a five-tab bar under every screen:

**Projects · Editor · Terminal · Packages · Settings**

When that bar hides, a second strip appears in its place: **Show tabs**. Both exist only so those five rooms have a home at the bottom of the phone.

SPCK does not do that. On your phone the editor goes to the bottom of the app. Rooms live in the side panel, behind ☰, on the first icon (Navigation). The project name is the root of the file tree (`1st semester` in your Files shot), not a tab.

So the project bar can go. Nothing has to replace it at the bottom.

What the bottom is allowed to be, from your shots, and nothing else:

| Keyboard | What sits at the bottom, top to bottom |
|---|---|
| Down | One status line, then the touch-key row |
| Up | The status line hides. A predictive row, then the same touch-key row, then the system keyboard (Gboard) |

A short message is a pill floating over that, not a new bar. “Error opening file.” and “Refreshed Files” are the two you already accept.

These are not the project bar, and they stay:

- the status line (`Ln 81, Col 28` on the left, `Sp: 4  HTML  LF  UTF-8` on the right)
- the touch-key row, docked even when the keyboard is closed
- Android’s own gesture or button bar (the system’s, not ours)

These leave with the project bar:

- the five tabs
- the **Show tabs** handle that only exists to bring the five tabs back
- any tour step that points at that bar or that handle

File tabs are not the project bar. In the shots they sit in their own row under the filename, near the top. They do not move to the bottom to fill the gap.

---

## 2. What the phone actually shows

These seven shots are the chrome to copy. Store pictures are not. Three things the store art got wrong, and your phone corrects:

- the first side-panel icon is Navigation, not preview
- the panel is not full screen; a strip of the editor stays visible, including the green play triangle
- there is no bottom tab bar

### Side panel

☰ opens a panel over most of the width. Play still works with the panel open, because the triangle stays on the right strip. Five icons across the top of the panel. The selected one has a white underline.

| Icon | Panel | On your phone |
|---|---|---|
| Triangle | Navigation | A 2×3 grid, then Recent |
| Folder | Files | The project tree |
| Search | Search | Find across the project |
| Branch | Repository | Git, or one button if there is no repo |
| Person | Account | SPCK’s shop. Do not copy this page |

**Navigation**, from your shot: Projects, Editor (selected), Settings, Discover, My Labs, Change Log. Recent rows are a name, a relative time, and an External badge. That grid is how you leave the editor. It is the replacement for the bottom bar, not an extra bar.

**Files.** Header: FILES, then search, a pin (Locate — jumps to the open file), new file, new folder, `...`. The tree root is the project name. Folders use a chevron. Every row has `...` on the right. The selected row is a full-width bar. `index.html` has a green triangle to the left of its icon. Official docs confirm that mark: it is the launch-default file. Play still prefers the HTML file you are editing. The default is only the fallback.

**Search.** Header toggles: regex, Aa, whole word, a filter, replace arrows. One field, “Find Text”. A RESULTS header with collapse, refresh, and clear. Empty means empty. No paragraph.

**Repository**, empty: one sentence — “No Git Repository initialized. Initialize one to version your work.” — and one blue button, Initialize Repository. The screen after that button is not in the shots. Do not invent it.

**Account.** Upgrade, credits, an AI model, a credit cap. That is their store. CodeC has no account and should not grow a fake one.

### Editor, top to bottom

From the two editor shots, the order is fixed.

**Top row.** ☰, the file-type icon, the filename (`index.html`), search, a green triangle. No word “RUN”. No ⋮. No undo, save, or format in this row.

**Tab row.** Its own line under the top row. The active tab has a colored top edge matching the file icon (orange for HTML). On the right of that row is one small icon, a box with a down arrow. The shots do not show what that tap does.

**Code.** Near-black background, line numbers, indent guides, the current line as a gray bar. Tags blue, attributes yellow, strings green, comments gray. Word wrap is on. The blue drop under the caret is the touch cursor, not the system text handle. Your shot has it on `</html>`.

**Keyboard down.** Status line, then the touch row, stuck to the bottom. Touch row: Tab, up, down, left, right, a comment key, `{}`, `>>`.

**Keyboard up.** Status line hides. A second row appears above the touch row: `<tag>` `div` `class` `=` `""`, plus a small icon on the right. That is the predictive row. Docs say those keys change with the file and with where the caret is, and that a long-press opens more symbols. Emmet and the CSS color chip live on that row. Arrow keys on the touch row also move the autocomplete highlight. Tab accepts it. SPCK does not replace Gboard.

### Errors

A dark pill, bottom center, the app mark plus a few words. It does not open a dialog. It does not lock the screen. The tree stays open when a file fails to open. That is the error style to keep.

---

## 3. CodeC today, against that

This is the current app, read from the code, not from memory of an old mockup.

**Bottom.** `MainActivity` draws `FlatBottomBar`: Projects, Editor, Terminal, Packages, Settings. `NavBarPolicy` hides it while the keyboard is up, and `EditorNavRevealHandle` puts **Show tabs** in the same slot. That slot is the separate project space. It is the piece that goes.

**Top of the editor.** One bar holds ☰, the file tabs, search, a ⋮ menu (undo, redo, keys, save, rename, and more), and a button that says RUN. The shots split this: filename in the top row, file tabs on the next row, play as a triangle with no word, no ⋮ in the bar.

**Drawer.** Files only, full screen, project name as a header. The shots use one panel with five icons, and the project name is the tree root.

**Status line.** CodeC already has line, column, UTF-8, language, spaces, LF. The shots put line and column on the left and the rest on the right, and they hide the whole line while typing. Close, not the same.

**Keys.** One row, and it can be turned off. The shots have two rows when typing, and the touch row never leaves.

**Install strip.** A setup bar sits across the top of every tab while userland downloads, and it sends you to the Terminal tab. You already decided the opposite: userland installs silently. A language run that needs a download before that install is finished must say so in one clear line: finish installing userland first. That line is a warning, not a project bar, and it is not a reason to keep the bottom tabs.

**Preview.** The page sits under an address row, a live badge, and a share panel (LAN, QR, stop). Your shots do not show Play. That chrome is not copied from these pictures. See section 6.

**First open.** The app can still land on the Projects hub, or on a welcome of three tiles. You want the first open to be the editor, on sample snake code, not a picker that dumps the user on a locked terminal.

---

## 4. Where each CodeC room goes

The real grid is 3×2, one card. Copy that shape. Do not copy Discover, My Labs, or Change Log. A CodeC filling of the same card, still a proposal:

| | | |
|---|---|---|
| Projects | Editor | Settings |
| Terminal | Packages | Guide |

Editor stays the selected center cell, as in the shot. Recent stays a stacked list under the card: name, relative time, and External only when the project came from outside the app.

The fifth rail icon stays, so the panel still matches the shots, but it does not open a shop. It opens a quiet About: version, licenses, feedback. Settings stays in the grid. If you would rather the fifth icon open Settings, say so. That is the only choice in this map that is not already a CodeC room.

Terminal, Packages, Settings, and Guide are full screens opened from that grid. They do not reserve a strip at the bottom of the editor. Coming back is the Editor cell, or Back onto the editor. The editor is the app. The other rooms are places you visit.

---

## 5. What not to copy, and what not to invent

Do not copy:

- Upgrade, credits, AI model, credit cap, username
- Discover, My Labs, Change Log
- Their git engine. The empty Repository panel is the visual to match. The implementation stays CodeC’s git
- A bottom tab bar “because phones have tab bars”

Do not invent, until a shot shows it:

- the `...` menu on a file row
- the Play / preview screen
- the long-press bar on selected code
- what `>>` opens, and what the small icon on the predictive row does
- what the box-with-arrow on the tab row does
- the new-file and new-folder prompts
- a search result row
- Repository after Initialize
- a permanent output strip at the bottom. Your shots never show a run. Output appears only when a run has something to say, and it must not bring the project bar back

Play on a phone opens a preview, not a side-by-side pane. Side preview is a tablet thing in their docs. That is a fact about SPCK. It is not a drawing of the preview chrome.

---

## 6. The HTML links

Two different things have been called “upper links”. These shots settle only one of them.

The page you are editing, `index.html`, has a row of buttons: Content, Home, More. On a phone that row is the problem if you mean the page itself. A hamburger belongs in that page’s nav. That is the page, not the editor.

If you mean the preview chrome — address, LAN, QR stacked above the page — that is CodeC’s preview, and these shots do not show SPCK’s. One Play shot of this file answers it. Both can be true. They are different fixes, and neither of them is a reason to keep a project bar.

---

## 7. Standing rules this review does not change

- First open is the editor, with the sample snake code.
- Userland installs silently. No “hang tight”, no “don’t close”, no bar that sends you to a locked terminal.
- If a run needs a download and userland is not ready, one clear warning: finish installing userland first. C and the HTML preview do not wait on that install.
- The editor target is this phone UI, not a redesign.
- Nothing in this report is a build order. No piece is implemented until you name it.

---

## 8. Still needed from the phone

Six shots, if the copy should be exact rather than guessed:

1. Play on `index.html`, the whole preview, including whatever sits above the page.
2. The `...` menu on a file row.
3. A long-press on selected code.
4. Repository after Initialize, only if that panel should match too.
5. Search with at least one result.
6. The tab-row down-arrow, and what `>>` on the touch row opens.

Not needed: another Account shot, another Navigation shot, or another editor shot with the keyboard up or down. Those are clear, and the shop page stays out.

---

## 9. Bottom, in one picture

Keyboard down. This is the whole bottom. There is no project bar under it.

```
┌──────────────────────────────────────┐
│ ☰   icon  index.html          🔍   ▶ │
│ [ index.html ]                   [↓] │
│                                      │
│  code                                │
│                                      │
│ Ln 81, Col 28     Sp: 4  HTML LF UTF │
│ →|   ↑  ↓  ←  →    "    {}      >>  │
└──────────────────────────────────────┘
```

Keyboard up. The status line is gone. The project bar is still gone.

```
┌──────────────────────────────────────┐
│ ☰   icon  index.html          🔍   ▶ │
│ [ index.html ]                   [↓] │
│  code                                │
│ <tag>  div  class  =  ""          ⌨  │
│ →|   ↑  ↓  ←  →    "    {}      >>  │
│ Gboard                               │
└──────────────────────────────────────┘
```

☰, if you need another room:

```
┌────────────────────────────┬───┐
│ △   📁   🔍   branch   👤  │ ▶ │
│                            │   │
│  Projects    Editor        │   │
│  Terminal    Packages      │   │
│  Settings    Guide         │   │
│                            │   │
│  RECENT                    │   │
│  1st semester    2h        │   │
└────────────────────────────┴───┘
```

The right strip is the editor. Play stays tappable. The project does not get a second home at the bottom.
