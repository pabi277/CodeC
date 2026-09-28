# Phase 70.1 + 72.1 — one chat, preview first

**Date:** 2026-09-28. **Branch:** `arena/01a0e704-codec`, based on `main` @
`fbb3056` (the merge of Phase 69.4 / PR #91). **Tip:** `d86b4a3`.
**Status:** 🚧 IMPLEMENTED, **CI ✅ GREEN — `Build APK`
[36399610563](https://github.com/pabi277/CodeC/actions/runs/36399610563) on
`d86b4a3`** (12 m 2 s: `:app:testDebugUnitTest` **2347 tests, 0 failed**, debug
and release APKs assembled, release manifest with no `android:debuggable` flag).
**No PR opened, nothing merged, nothing pushed to `main`.** No device evidence
was produced, and none is implied.

This chat began as **Phase 70.1 — Run, output and error recovery** and ended up
delivering **both** the Web Preview console (Phase 72.1) and the run Output
Panel (70.1), because the owner said so. His answers, verbatim: the surface is
**“Both, one after the other”**; the console takes **“The whole strip, Elements
included”**; the viewport is **“Yes — fix the tiny rendering in the same part”**;
the hard-to-understand error line is **“The run output panel's”**; and the order
is **“Both in this chat, preview first”**. The two sentences that started it:

> *"Try to make output exactly same for web view"* and *"And terminal output is
> good enough but is hard to understand the error line from terminal"*

Briefs (the owner's questions and answers are recorded verbatim inside them):

- [`docs/ui-polish-chats/PHASE_72_1_PREVIEW.md`](../ui-polish-chats/PHASE_72_1_PREVIEW.md)
  — the preview part, delivered first.
- [`docs/ui-polish-chats/PHASE_70_1_RUN_OUTPUT.md`](../ui-polish-chats/PHASE_70_1_RUN_OUTPUT.md)
  — the editor's run panel, delivered second.

## What shipped

| Part | Commits | What it is |
|---|---|---|
| **72.1 — the Web Preview** | `090596e`, `2a707be` | The five-tab strip the shots show (**Console · Elements · Network · Resources · Settings**), a console that takes commands (echo, `eval` of a policy-built one-liner, LOG/ERROR result, Cancel · Execute, Send on the IME), the level filter row, a Network table whose Type/Size/Time come from the page's own Resource Timing entries (Status honestly stays “—” because WebView cannot see a response), a read-only Elements tree with details/highlight/copy, Resources, a Settings page (zoom, resolution, Fit page to phone, viewport outcome, Clear cache), the **“Preview · N % Zoom”** bar with the console toggle, and a **phone-sized default viewport** added only to pages that never declared one. |
| **70.1 — the run Output Panel** | `11345c0`, `2590429`, `3ad83c7`, `d86b4a3` | A state row derived from facts (`Idle / Building / Installing / Running / Waiting / Serving / Done · exit N / Failed · exit N / Stopped`), a 48 dp action row led by a labelled **Stop**, an error/warning count banner, compiler errors as two rows (location + severity, then the message) with the *Add missing ;* fix, a collapsed strip that says *“Waiting for input — tap to answer”* while a program waits, the run keys (↵ / Ctrl+C / Tab) restored for that same case, the panel capped at 55 % — **38 % with the keyboard up** — and, on the owner's own answer, **the panel's line as a console**: a command typed there runs in the app's real shell, and a line typed while something is already running is refused rather than queued. |

## Evidence, told apart

- **Source (host JVM) tests:** pure-policy cases and wiring pins — 2347 tests in
  the CI run above, including `PreviewConsolePolicyTest` (7),
  `PreviewInspectorPolicyTest` (5), `PreviewToolsPolicyTest` (17),
  `OutputPanelStatusTest` (9), `OutputPanelWiringTest` (5),
  `OutputPanelContrastTest` (3, the panel's palette against WCAG AA).
- **Android tests (Robolectric, real Compose/WebView classes on the host):**
  `PreviewToolsLayoutTest` (3 — resizing never consumes the page, the five tabs,
  the Settings readouts) and `PreviewWebViewTest` (the view's zoom, file-access
  pins, console mapping and dispose path). These are **not** a device pass.
- **Real device:** **none.** Nothing in this chat has been installed or driven on
  a handset; the five owed handset rows for 69.1–69.4 remain owed.

## Open, and not delivered

- **N1 — the “above ber”** is still unanswered: none of the six shots identifies
  that bar, so no shape was designed for it.
- **Elements is read-only** (a DOM walk, not SPCK's DevTools protocol): no
  element editing, no computed styles.
- **Network's Status column stays “—”**, with the panel saying why.
- The panel's console refuses a command while a run is busy (by design); it does
  not queue, and it never becomes a second Terminal.
- **The preview's own chrome height** — the bar, the address row and the bottom
  navigation — is the remaining difference from a browser window at the same
  width (browser chrome is thinner, so its page box is taller). The console's
  page-box line now measures it; folding the address row into the bar is a
  layout decision for the owner and was deliberately not taken.

## Render fix (2026-09-28, after the report)

The owner sent two screenshots of the *same* snake page — Samsung Browser showed it whole
(SNAKE, `score 0 · best 0`, *Tap to start* and START, the pad, the hint); CodeC's Web
Preview showed the arrow pad near the top, the hint under it and a long empty band, with
the header, the score and *Tap to start / START* off-screen — and said:

> *"the better one is in browser and other is from code c preview correct it CodeC preview
> sucs"*

**Reproduced, not guessed.** The seed page fixed its own height (`body { height:100% }`)
and centred its flex column, so in a box shorter than the column the content is centred
*around* the box: the overflow sits above y=0 and can never be scrolled to. Rendered from
the page source in headless Chrome at **360×520 CSS px** (the preview box minus CodeC's bar
and keys row):

| 360×520 CSS px | legacy | fixed |
| --- | --- | --- |
| header top | **−53 px** (off-screen) | 23 px |
| board top | **−18 px** (clipped) | 58 px (200 px square) |
| pad top | 324 px | 272 px |
| hint | 534 px (below the fold) | 470 px, inside |
| page height | 562 px over a 520 px box | 520 px, fits |

At 360×600 and 412×660 the legacy page still clipped the header by 13 px and 2 px. The
fixed page renders whole at all three sizes. Evidence renders:
[`render-fix/before-preview-height-360x520.png`](render-fix/before-preview-height-360x520.png)
and [`render-fix/after-preview-height-360x520.png`](render-fix/after-preview-height-360x520.png).

**What changed**

1. The seed page (`SnakeSample`): `html { height:100%; }` + `body { min-height:100%; }` so
   the page grows and a short box scrolls instead of clipping; the square board is capped by
   `width:min(100%, 420px, calc(100vh - 320px))` with a 120 px floor; the browser-default
   paragraph margins are gone (they added 24 px to the column); and `touch-action:none` moved
   from the body to the board, so the board still owns the swipe while a too-short page can
   still be scrolled.
2. `WebPreviewScreen`: the first load waits — **bounded at 1 s** — for the page box to be
   measured, so no page takes its first layout against a 0×0 view (the tools panel can hold
   every pixel of the preview area). Reloads stay immediate.

**Coverage.** `FirstOpenSampleTest` +1 — *"the page survives the preview's shorter viewport"*
— pins every rule above, including that the body no longer carries `touch-action:none`.
`PreviewToolsWiringTest` +1 — *"the first load waits for a measured page box"* — pins the
state, the `onSizeChanged` report and the 1 s bound. Both are source-level pins.

**What this does not establish.** The renders are headless Chrome on the machine that wrote
the fix, **not** a phone and not the app; no device pass is claimed. A page a user writes that
fixes its own height will still clip in a box shorter than it needs — exactly as it would in a
short browser window. **CI ✅ green** — `Build APK` `36404697868` on `ced2821` (host unit/screenshot tests and both APK assembles succeeded) and `36404764414` on the record commit; a side-by-side of the two renders is [`render-fix/side-by-side-360x520.png`](render-fix/side-by-side-360x520.png).

## Render fix, round 2 (2026-09-28, the owner's next screenshot)

> *"It's better than before but still i don't think it's good enough. Because browser have a
> good view and code is very smaller view."*

The next install was no longer clipped — the header, the score and *Tap to start* were on
screen — but the board had become **small**. The round-1 page capped the board by the
viewport height (`calc(100vh - 320px)`), which is right in a browser window and wrong in
CodeC's preview box: the box is shorter than a phone window (the app bar, the address row,
the keys row and the nav bar all take height), so the board collapsed from the browser's
380 px to **200 px**, with dead space where the page still had room. Trading “clipped” for
“small” was my mistake, and it is reversed:

**What changed (round 2)**

1. **The board is width-driven again**, exactly like a browser: `.wrap { width:min(100%, 420px) }`.
   A box shorter than the page scrolls (`min-height`, already in place) instead of shrinking
   the board. Measured in headless Chrome: **360×520 → board 328 px** (was 200; the browser
   shows 380 only because its window is taller), **412×915 → 380 px with nothing to scroll**.
2. **The phantom band under the page is gone.** `WebPreviewScreen` reserved the keyboard's
   inset on this screen unconditionally, but the console's command line is the only text
   field here: the inset is now reserved **only while the tools panel is open** — and, since
   an inset can no longer be reserved for a keyboard nobody can see, the two taps that take
   that field away (Close, and a tab switch away from the console) now release focus in the
   same tap, so the keyboard goes down with it (`PreviewToolsPanel`). Pinned by
   `PreviewToolsWiringTest` — *"closing the panel or leaving the console takes the keyboard
   with it"*.
3. **The console now measures the page.** After every load the WebView logs one line —
   `page box 360×430 CSS px · view 360×430 dp · dpr 1.75` — the page's own `window.innerWidth`
   × `innerHeight` and `devicePixelRatio` next to the box the view was given
   (`PreviewToolsPolicy.pageBoxScript` / `parsePageBox` / `pageBoxLabel`). Two screenshots
   have now been ambiguous about *which* box was short; this line ends that, from the page's
   own numbers rather than a guess.

**Evidence for round 2**

- Renders (headless Chrome, this machine): [`render-fix/side-by-side-round2.png`](render-fix/side-by-side-round2.png)
  (round-1 board vs round-2 board at the same 360×520 box), plus
  [`render-fix/after2-board-like-the-browser-360x520.png`](render-fix/after2-board-like-the-browser-360x520.png)
  and [`render-fix/browser-window-412x915.png`](render-fix/browser-window-412x915.png).
- **CI ✅ green** — `Build APK` `36409241166` on `0ca08dc` (host unit and screenshot tests, both APK
  assembles).
- **No device pass.** Nothing here has been installed on a handset; the owner's own screenshot
  is the only device evidence in this chat, and it is his.

**What the next screenshot can settle.** The preview box is genuinely shorter than a browser
window because CodeC draws more chrome above the page (bar + address row + keys row + nav
bar). That is a layout fact, not a rendering bug; the console's page-box line now reports it
with numbers. If the page still reads small against Samsung Browser at the same width, the
remaining difference is the chrome, and the options are the panel/rows — a decision for the
owner, not a silent change.

## Round 3 (2026-09-28, a third-party page) — measured, not guessed

The owner installed round 2: **the snake problem is gone** (his own confirmation), and the
next report is about a page he cloned into CodeC —
[`priyajitpaul4-cmyk/Code-with-C`](https://github.com/priyajitpaul4-cmyk/Code-with-C) — with
Samsung Browser and CodeC side by side again:

> *"Snake problem is gone but still it have problem like the screenshot see in browser it
> opens a wide window but in CodeC it's very small"*

**What is known and what is not.** The two screenshots are not on this machine (the uploads
directory is not mounted here — the same limitation as every earlier round), so the page's
numbers had to come from somewhere better than my reading of a picture. Two candidate shapes
produce a page that “looks small”, and they need opposite fixes:

1. **Laid out wider than the phone** — the WebView falls back to a wide viewport (Chromium's
   classic 980 px) and squeezes it into the box. The page's own media queries then run at
   ~1000 px, so its text is *relatively* tiny while the page still fills the width.
2. **Drawn at a smaller scale** — the layout is right (phone width) but the WebView applied a
   scale below the screen density, so every CSS pixel is drawn smaller than one dp.

Both pages involved declare a valid viewport (`width=device-width, initial-scale=1`), which is
why this needed measuring rather than guessing — the round-1 lesson, learned the expensive way.

**The instrument (this round's change, `e436473`, CI ✅ `36417473156`).** Every load already logged a
page-box line; it now prints every number that separates the two shapes, from the page itself
and from the view:

```
page box 360×619 CSS px · meta width=device-width, initial-scale=1.0 · view 360×430 dp · scale 3 · dpr 3
```

- `page box` = the page's own `window.innerWidth × innerHeight` (the layout it got).
- `meta` = the page's own `viewport` declaration, or `none` (the pages `fitToPhone` answers).
- `view` = the box the WebView was given, in dp.
- `scale` = device pixels per CSS pixel, read back from the WebView's own `getScale()`;
  **`scale` should equal `dpr`** (one CSS pixel per dp — the browser's `initial-scale=1`).
- `dpr` = the page's `devicePixelRatio`.

Reading it: if `page box` ≈ the view's `dp` and `scale` ≈ `dpr`, the preview is rendering 1:1
like the browser and the only difference left is the box's height (this app's chrome). If
`scale` is ~1 against a `dpr` of ~3, the page is drawn ~3× smaller → the initial-scale recipe
in `PreviewWebView` is the fix. If `page box` is ~980 while the box is 360 dp wide, the wide
viewport fallback is in play → the viewport-meta path is the fix.

**The exhibit** — [`render-fix/third-party/what-wider-than-the-phone-looks-like.png`](render-fix/third-party/what-wider-than-the-phone-looks-like.png)
renders the owner's page twice on this machine: at phone width (left) and laid out 980 px wide
then squeezed to phone width (right). It is **a simulation of shape 1, not a measurement of his
phone** — a visual reference for comparing against his own screenshot.

**Nothing was changed on a hunch this round.** No viewport setting, no scale, no chrome: the
next install's console line decides which shape it is, and the fix follows from it.

## Stop point

This part only. No PR, no merge, no `main` push without the owner's explicit
instruction, and no claim of device acceptance.
