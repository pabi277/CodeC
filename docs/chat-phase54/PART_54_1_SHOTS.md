# PART 54.1 — The reference card: the seven phone shots, read pixel by pixel

> **Status:** ✅ DONE (2026-09-22, docs only). · **Effort:** S ·
> **What this is:** the single card every one of 55–58 cites instead of
> re-interpreting the shots. Written from the seven JPEGs **read in this
> session**, not from `SPCK_PHONE_UI_REPORT.md`'s summary of them and not from
> the drawings.
>
> **Files:** `docs/spck-ui/Screenshot_20260922_<hhmmss>_Spck Editor.jpg`,
> all **1080×2340** px. The renders below were viewed at 720 px wide (⅔ scale),
> so **1 viewed px = 1.5 device px = 0.5 dp** on a 360×780 dp screen. Every
> number below is rounded and marked ≈; it is a reading aid, not a spec sheet.
>
> **Colours are described, never sampled.** There is no pixel sampler in this
> sandbox pass, and copying SPCK's RGB values would be wrong anyway: CodeC draws
> this chrome from `ui/theme/CodecPalette.kt` + the Phase 50 tokens
> (`CodecTokens`). A later phase matches **structure and emphasis**, then picks
> the token that carries it.

---

## 0. What all seven share

| Fact | Reading |
|---|---|
| The drawer is a **side panel, not full width** | The panel's right edge sits at ≈ x 611 of 720 viewed px → ≈ **85 % of the width (≈305 dp of 360 dp)**. A ≈55 dp strip of the **editor** stays visible on the right, under the panel's edge, in all five panel shots |
| That strip is the running editor | In every panel shot the strip shows: the **green play triangle** at the top right, the tab row's small icon just below it, five lines of the same syntax-coloured HTML, then `UTF-8`, then `>>` at the bottom |
| The strip is live, not a picture | It is offset differently per shot (the code under it differs: `" id`, `</i>`, `add -->`, `circle"`), so it is the editor scrolled at that moment. Play is not disabled while the panel is open |
| Rail on top, tabs under it | Five icons across the top of the panel, the **selected one underlined in white**; a section label under the rail, uppercase and letter-spaced, with the section's own controls on the right |
| **No bottom bar anywhere** | Not in the panel shots, not in the editor shots.⚠️ **This absence is NOT copied.** The owner rejected it (54.3 §1). `FlatBottomBar` stays in every phase of this series |
| Status bar is Android's | Battery, network, clock — not SPCK chrome |
| Font | The panel's own text is a sans (Roboto-like). **Code and filenames are monospace.** CodeC already owns `CodecType.codeFamily` (Phase 50.3) |

**The five rail icons, in order, from every shot that shows the rail:**

```text
  1  outline triangle (navigation)      ← the FIRST icon: ◁ outline, not a filled ▶
  2  folder outline
  3  magnifier with <> inside
  4  git branch (three dots joined)
  5  person outline
```

Selected state: the icon turns white-ish **and** a ≈2 dp white bar appears
under it, roughly one icon-cell wide. First shot = triangle underlined, Files
shot = folder underlined, Search shot = magnifier underlined, Repository shot =
branch underlined, Account shot = person underlined. **Where it is underlined is
which panel is open** — there is no separate "current panel" chip.

---

## 1. `124049` — NAVIGATION (the panel 55 must build)

```text
  RAIL      ◁ (selected, underlined) · ▭ · ⌕<> · ⑂ · ◯
  LABEL     "NAVIGATION"                        …  "..."
  CARD      one bordered rounded rect, 3 columns × 2 rows
            ┌──────────┬──────────┬──────────┐
            │ Projects │ Editor   │ Settings │     Editor cell = raised lighter
            │  ▭▼      │  <> in ▭ │    ⚙     │     rounded square, selected
            ├──────────┼──────────┼──────────┤
            │ Discover │ My Labs  │ ChangeLog│     ← NOT copied (54.3 §2)
            │  🔭      │   ⚗      │  ▤✔      │
            └──────────┴──────────┴──────────┘
  RECENT    label, then a stacked list, one row per entry:
            name (bold, 2 lines high) · relative time (muted, under the name)
            · "External" badge (muted, right, vertically centred)
```

Readings that matter to 55:

- **One card, 3 × 2**, with a 1 px border and rounded corners — **not** the
  2-column drawing. Icon above label; labels are small, centred, and the
  selected cell's label is white where the others are muted.
- The **selected cell has its own raised background square** (lighter than the
  card) behind the icon — selection is a filled tile, and the tile is taller
  than it is wide.
- **`...` on the label row** — the Navigation section's own overflow. Roadmap
  55.2 says only what the shots show; this `...` is real, its contents are not
  in any shot → 54.3 §4.
- **Recent rows**: first row is the *currently open* project and is drawn
  highlighted (lighter row background). Names include a file with an
  extension — `Presentation-6-Slides.pptx (1)` — so "Recent" is **not only
  projects**, it is recent *things opened*. CodeC's own equivalent is its
  recent-projects list (`SettingsManager`); what CodeC writes here is a 55
  decision, not a 54 guess.
- **`External` is on every row in this shot.** The roadmap's rule stands: show
  the badge only when the entry really came from outside (import/share), never
  as decoration to match the shot.

## 2. `122203` — FILES (the tree, moved into the panel)

```text
  RAIL      ▭ (selected, underlined)
  HEADER    "FILES"      ⌕   ⌖(Locate/pin)   ▤+  ▭+   ...
  TREE      1st semester                      ...      ← root = PROJECT NAME, ▼
              app-resources        ...   ← chevron ›, indented, indent guide line
              Chemistry            ...
              css / English / js / Math / Physics / Practice_question / pyq-assets
              4.html               ...   ← HTML5 shield icon
            ▶ index.html           ...   ← SELECTED: full-width bar, blue outline,
                                             green ▶ to the LEFT of the file icon
              last_english.html    ...
              prompt.txt           ...   ← TXT page icon
              structure.txt        ...
  PILL      ( ◈  Error opening file. )   ← floating, centred, above the strip
```

Readings that matter to 55:

- **Root row = the open project's name**, with a chevron-down (expanded) and its
  own `...`.
- Every row (folder and file) carries `...` on the right. **What is inside that
  menu is not in any shot** → 54.3 §4.
- The **selected file row is a full-width bar with a blue outline** and a
  lighter fill; the **green ▶ sits left of the icon** on that row (the
  launch-default marker, per the official docs — see the report). Play still
  prefers the file actually open in the editor.
- Head of a folder row: chevron **›** (collapsed) / **⌄** (expanded). Files have
  no chevron but keep the same icon column.
- **HTML files use the HTML5 shield; `.txt` uses a TXT page.** CodeC already has
  `FileIconView` / `FileIcon` — 55 maps onto those, it does not draw new icons
  from the shot.
- The **pill** (surface `#2b2f36`-ish, rounded, app mark at the left, one
  sentence) floats ≈24 dp above the bottom of the panel. It is the same pill in
  `122157` (“Refreshed Files”). **57.3 builds this as a pill, not a dialog.**

## 3. `124052` — SEARCH (empty)

```text
  RAIL      ⌕<> (selected, underlined)
  HEADER    "SEARCH"      ✳*   Aa   Ab|   ☰=   ⇄
  FIELD     ( ⌕  Find Text )        ← one rounded field, placeholder only
  RESULTS   "RESULTS"     ▭-   ⟳   ⊘
  ( body empty )
```

- The header toggles are **icons, not text chips**: regex (`.*` with a sparkle),
  `Aa` (case), `Ab|` (whole word), a filter mark, and replace arrows.
- `RESULTS` has three controls: collapse, refresh, clear. **Empty means empty** —
  no paragraph, no illustration.
- The field is a bordered rounded rect with the magnifier inside it.

## 4. `124055` — REPOSITORY (empty)

```text
  RAIL      ⑂ (selected, underlined)
  HEADER    "REPOSITORY"                          ⌕
  BODY      No Git Repository initialized. Initialize one to version your
            work.                        ← LEFT-aligned, wraps to 2 lines
            [ Initialize Repository ]    ← centred blue button, bold white label
```

- The sentence is **left-aligned**; only the **button is centred**. (The drawing
  had both centred — the shot wins.)
- A magnifier sits on the label row.
- **The screen after that button is not in any shot.** 55.3 builds the empty
  state, and the initialized state stays CodeC's existing Source Control — the
  engine is not replaced (roadmap “What this plan does not touch”).

## 5. `124058` — ACCOUNT (the shop — **not copied**)

Rail: person, underlined. Avatar, `infinite-analogy-4l`, `Free`, `Upgrade`,
`Messages` / `Activity Log` chips, an `AI ASSISTANT` disclosure with Monthly
Credits / Extra Credits / AI AutoComplete (on) / Use Extra Credits (off) /
AI Model `GLM 5.3 Flash` / AI Credit Cap `Unlimited`, then `ADVANCED SETTINGS`.

**This is a store front.** CodeC has no account, no credits, no model picker and
should not grow a fake one. What the shot *does* settle is the fifth rail icon's
**position and selected style** — nothing else (54.3 §3).

## 6. `122157` — EDITOR, keyboard down (the 57.1/57.2 shape)

```text
  TOP ROW   ☰   [HTML5 shield]  index.html            ⌕      ▶(green)
  TAB ROW   ┌────────────┐                                     ┌──┐
            │[shield] index.html│  (orange top edge)              │▤↓│
            └────────────┘                                     └──┘
  CODE      line numbers · indent guides · current line as a full-width grey bar
            tags blue · attributes yellow · strings green · comments grey
            caret on line 81 = thin bar (NO blue drop in this shot)
  PILL      ( ◈  Refreshed Files )
  STATUS    Ln 81, Col 28                     Sp: 4   HTML   LF   UTF-8
  TOUCH     →|   ^   v   <   >   🗨,   ⌘   >>
  ( Android gesture bar )
```

- **No word RUN. No ⋮.** Top right is search + the green play triangle only.
- The filename is **monospace**, medium weight, next to the file-type icon.
- The **active tab has a coloured top edge** (orange here, matching the HTML5
  icon) and the tab is a bordered cell whose right neighbour is empty space.
- **Right of the tab row**: one small icon in a bordered box — three lines with
  a **down arrow** (`▤↓`). **What it opens is in no shot** → 54.3 §4.
- The status line: `Ln 81, Col 28` left; `Sp: 4  HTML  LF  UTF-8` right. **No
  dot, no error badge** in this shot (CodeC's own status bar adds one — keep it,
  it is CodeC's and not part of this copy).
- The touch row is **eight controls**: `→|` (tab), `^`, `v`, `<`, `>`, a speech
  bubble with a comma, a `⌘`-like mark (braces/dots — **unnamed**, 54.3 §4),
  `>>`.
- The pill floats just above the status line, centred.

## 7. `124105` — EDITOR, keyboard up

```text
  TOP ROW   (same as above)
  TAB ROW   (same)
  CODE      line 83 </html>, caret after it, and a LARGE BLUE DROP under the caret
  PREDICT   <tag>   div   class   =   ""              ◁°
  TOUCH     →|   ^   v   <   >   🗨,   ⌘   >>
  KEYBOARD  Gboard: its own emoji/clipboard row, number row, qwerty, …
  ( ⌄ at the bottom right = Gboard's own hide control )
```

- **The status line is gone** when the IME is up (57.2: hide it).
- A **predictive row** replaces it: five chips (`<tag>`, `div`, `class`, `=`,
  `""`) and a small unnamed icon at the right (a rotated triangle with a dot).
- The **touch row stays** above the IME, unchanged.
- The **blue drop** is the editor's touch cursor under the caret, not Android's
  text handle. CodeC draws its caret in sora (`ui/editor/sora/SoraEditorHost.kt`)
  → 57.2 reads sora before adding any view: **if sora already draws a handle,
  style that one; never stack a second caret.**
- The IME itself is Gboard. **CodeC does not replace the system keyboard**
  (`CodecKeyboard.kt` stays CodeC Keys, the optional row — not a replacement).

---

## 8. What the shots do **not** show (the honest list)

Every one of these is a **question or a deferral**, never an invention:

| Missing | Where a phase would need it |
|---|---|
| What the `...` on a file row opens | 55.3 (Files rows) |
| What the `...` on the NAVIGATION label opens | 55.2 |
| The screen after **Initialize Repository** | 55.3 (only the empty state is shown) |
| What `>>` does | 57.2 (it is drawn in the touch row) |
| What the `▤↓` icon right of the tab row does | 57.1 |
| What the `⌘`-like touch-row glyph does | 57.2 |
| What the predictive row's right-hand icon does | 57.2 |
| A long-press on selected code | 57.2 |
| **Play on `index.html`** — any preview/run screen | 58.3 (and the owner's own hamburger row) |
| A file `...` menu | 57/55 |
| A fifth rail panel other than the shop | 54.3 §3 |

## 9. What this card rejects, in writing (roadmap law 5–6)

- ❌ **The drawings** `docs/spck-ui/00-overview.png` … `08-bottom-comparison.png`
  — the shell is right, the grid is wrong (2 columns there, 3 × 2 in the phone).
- ❌ **Store art** in `image-search/` — including the Snake screenshot.
- ❌ **The Account shop** — position only, content never.
- ❌ **“56 removes the bar.”** Withdrawn by the owner. The bar stays.
- ❌ **A bottom bar drawn into the panel copy** — the shots have none, and the
  owner's reversal means CodeC keeps its own.
