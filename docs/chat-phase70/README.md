# Phase 70.1 + 72.1 — one chat, preview first

**Date:** 2026-09-28 → 2026-09-29. **Branch:** `arena/01a0e704-codec` (rounds 1–4),
continued verbatim on `arena/01a0e84c-codec` (the owner's *"copy the whole branch"*;
round 5 and the editor fix), based on `main` @ `fbb3056` (the merge of Phase 69.4 /
PR #91). **Status: ✅ OWNER-TESTED AND MERGE COMMANDED (2026-09-29, verbatim:
*"Yes all test passed complete docs and merge to main"*) → [PR #93](https://github.com/pabi277/CodeC/pull/93).**
The last code commit is `308441e` (CI ✅ `36467347590`); the docs stamps that
follow it are CI ✅ too (`36468730204` on `1b68266`). The first delivery's own
numbers — **CI ✅ `Build APK`
[36399610563](https://github.com/pabi277/CodeC/actions/runs/36399610563) on
`d86b4a3`** (12 m 2 s: `:app:testDebugUnitTest` **2347 tests, 0 failed**) — stand
as the record of that round; every later round has its own stamp below.

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
- **Real device:** at the time of this section, **none**. On 2026-09-29 the owner
  ran the rows listed at the end of §Round 5 and the editor row of the
  composing-replay fix (TROUBLESHOOTING §47) and reported *"Yes all test passed"* — no device,
  OS or theme named; the record keeps exactly that. The five owed handset rows for
  69.1–69.4 are not part of that report and remain as recorded in their own docs.

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

**The owner sent four console lines (2026-09-28, later). Decoded:**

| # | page box | view | page/view | verdict |
| --- | --- | --- | --- | --- |
| 1 | 411×655 CSS px (meta without `viewport-fit`) | 411×656 dp | 1.000 | **1:1 correct** |
| 2 | 411×655 CSS px | 411×656 dp | 1.000 | **1:1 correct** |
| 3 | **457×728 CSS px** | 411×656 dp | **1.112** | drawn **0.9× — 10 % small** |
| 4 | 411×655 CSS px | 411×656 dp | 1.000 | **1:1 correct** |

Three of four loads were already pixel-exact — one CSS pixel per dp, which is the browser's own
`initial-scale=1`. One load was not: the page laid itself out **457 CSS px wide in a 411 dp box**,
i.e. the page was laid out for a box it does not have, because Chromium decides a page's scale
*before* the page's `viewport` meta is in effect and never revisits that decision. (Line 1's meta
has no `viewport-fit=cover` — that load had no page meta when the report ran, so `fitToPhone`
added ours; the other three carry `viewport-fit=cover`, which is **Chromium's own rewrite for an
edge-to-edge WebView**, not something in the owner's file. Verified against his repository:
`index.html` declares `width=device-width, initial-scale=1.0` and nothing else.)

**The fix (`c954599`, CI ✅ `36421203364`).** `PreviewWebView` now asks a pure rule —
`PreviewToolsPolicy.boxMismatch` — whether the page's own box disagrees with the view's, and if it
does, logs one sentence to the console and **loads the page once more**, with the box and the meta
both known. The rule is deliberately narrow: it only judges pages that declare `width=device-width`
(or declared nothing — the case `fitToPhone` answers), it accounts for the user's zoom (at 150 % a
page *should* lay out at two-thirds width), it ignores a 4 dp rounding tolerance, and it never
touches a page that declares its own width (`width=1024`). One correction per load the app asked
for — the app's own loads re-arm the check, the correction never re-arms itself, so it cannot loop.

**A Phase 61 pin was reversed, with its reason in the test.** `PreviewToolsWiringTest` pinned
`assertFalse(native.contains("reload()"))` — the native view observed, it never drove the page.
It now asserts the **two and only two** reload paths (the app's own request passing through, and
the policy-gated correction) and names the owner's 457-in-a-411 load as the reason. The red round
`36420734801` is that pin doing its job.

## Round 4 (2026-09-28, the modal that collapsed) — the cause was ours, and it was there all along

The owner's next pair of screenshots, same page ([Code-with-C](https://github.com/priyajitpaul4-cmyk/Code-with-C)),
same phone, a program card tapped open. **Samsung Browser:** the detail card fills the
screen — pill *Program 01 · Basic Programs*, ×, the heading, the description, the C CODE block,
*Sample Output*, scrolling inside. **CodeC:** the page behind is correctly blurred, and the card
is a **thin strip, vertically centred** — the pill and the × survive, the heading, the text and
the code are gone. His console lines for the same loads read as *healthy*:

```
page box 411×655 CSS px · meta width=device-width, initial-scale=1.0 · view 411×656 dp · scale 2.63 · dpr 2.63
```

> *"I have problems with some screen … also other code but same problem"*

**What the page does (read from its source, not guessed).** `showDetail()` builds the whole
card in one `innerHTML`, so every element was in the DOM in CodeC too. The card's rule is
`.detail-box { max-height: 88vh; overflow: auto; padding: 25px }` — at phone width
`padding: 20px 15px` — under a global `* { box-sizing: border-box }`, and `88vh` is the **only**
`vh` in the entire stylesheet. If `88vh` resolves to ~0, a border-box `max-height` clamps the
content box to 0 and the card becomes exactly **padding + border = 20 + 20 + 2 = 42 px**, the
22 px pill (starting 20 px down) stays visible inside the padding box with ~2 px clipped off its
bottom, and `overflow: auto` hides everything below. That is the strip in the screenshot, to the
pixel. So the question became: *why is `vh` zero in a view whose `innerHeight` is 655?*

**Why `vh` was 0 — two sources, read this round, not remembered.**

1. **Compose.** `AndroidViewHolder` (`compose/ui/ui/…/viewinterop/AndroidViewHolder.android.kt`)
   adds the factory's view with a plain `addView(view)`, which stamps
   `WRAP_CONTENT × WRAP_CONTENT` on any view that arrives without layout params — and
   `PreviewWebView` arrived bare. The holder's own `getLayoutParams()` *returns the child's*,
   and its `onMeasure` forwards the exact spec straight to the child, so with
   `Modifier.requiredSize` the WebView was measured **`EXACTLY` 411×656 dp** all along. The
   console line never lied.
2. **Chromium.** `android_webview/…/AwLayoutSizer.java`, verbatim:
   ```java
   private void updateLayoutSettings() {
       mDelegate.setForceZeroLayoutHeight(mDelegate.isLayoutParamsHeightWrapContent());
   }
   ```
   The WebView decides its **layout** height from the *layout params*, not from the measure
   spec. With that flag on, Blink's layout viewport (the initial containing block) is **0 px
   tall**: every `vh` unit and every `height: 100%` chain from `<html>` resolves to 0, while
   `window.innerHeight` — the **visual** viewport — still reports the true 655. That is
   precisely the split the console line could not see: it measured the visual viewport.

**Re-reading rounds 1 and 2 with this in hand.** Round 1's clip (`html, body { height: 100% }`
with a centred flex column → the header at −53 px, unreachable) and round 2's small board
(`calc(100vh − 320px)` → its 120 px floor) are both what a 0 px layout viewport does to those
rules. The page-side changes in those rounds were sound as pages and stay; but the headless-Chrome
reproductions explained the *shape* of the symptoms, not the device's cause, and this record
should say so. The cause was in `WebPreviewScreen`/`PreviewWebView`, not in any page.

**The fix (`PreviewWebView`).** One property, set in the constructor so it exists before any
host can add the view:

```kotlin
layoutParams = ViewGroup.LayoutParams(MATCH_PARENT, MATCH_PARENT)
```

`MATCH_PARENT` is the truth about this view — it is always given an exact box by
`requiredSize` — and it is what turns `isLayoutParamsHeightWrapContent()` off. The Compose
measure path is unchanged (`obtainMeasureSpec` gives `EXACTLY` for fixed constraints either
way); only Chromium's reading of the params changes. This is the same reason Accompanist's
`WebView` composable sets `MATCH_PARENT` params from its constraints (*"WebView changes its layout
strategy based on its layoutParams"*).

**The instrument (`PreviewToolsPolicy`).** The per-load line now also reports what the page's
own CSS gets for `100vh` — a hidden probe sized `100vh`, added and removed inside one script —
because `innerHeight` cannot see this class of fault:

```
page box 411×655 CSS px · 100vh 655 px · meta width=device-width, initial-scale=1.0 · view 411×656 dp · scale 2.63 · dpr 2.63
```

and a pure rule, `layoutHeightCollapsed` (`100vh` shorter than **half** the page's own
`innerHeight` — no browser ever does that: browser controls only make `vh` the *larger* of the
two, and a pinch-zoom shrinks `innerHeight`, never `vh`), turns a collapsed answer into a console
**warning** in one sentence. Had it existed this morning, the owner's line would have read
`100vh 0 px` with the warning under it, and no screenshot would have been needed.

**Coverage.** `PreviewWebViewTest` +1 (Robolectric: the params are `MATCH_PARENT` at
construction and survive a host's `addView`), `PreviewToolsWiringTest` +1 (the source pins:
`MATCH_PARENT` present, `WRAP_CONTENT` absent from the code, the `100vh` probe, the rule and
the warning wired), `PreviewToolsPolicyTest` +1 (the owner's collapsed line → `0` kept as a
number, the rule's edge at half, the healthy line, a pinch-zoom shape) and the page-box cases
moved to the four-field wire format.

**CI ✅ green** — `Build APK` [`36436647849`](https://github.com/pabi277/CodeC/actions/runs/36436647849)
on `146986b` (the host unit and screenshot test step, the debug and release assembles, release
manifest without `android:debuggable`). The debug APK on that run is the one to install.

**What this does not establish.** No device pass — nothing here has been installed on a
handset; the owner's own screenshots are the only device evidence. No rendered exhibit this
round: the sandbox had no Chromium and no package access, and a desktop Chrome cannot enter
the WebView's wrap-content mode anyway — the proof here is the two sources above and the 42 px
arithmetic against his screenshot. The second project's console lines he pasted are the same
four as round 3b (three 1:1, one 457-in-a-411 that the one bounded reload already handles);
they carry nothing new, which is itself the finding — a page-box line could not show this
fault, and now it can.

## Round 5 (2026-09-28) — the phone pass, and the project card that opens the editor

**The owner's request, verbatim:** *"in projects section when I click on a project it should
open the editor by default and not the file structure"* — and — *"make everything from this
and only this phase whatever is done, phone friendly … the console input etc. is not looking
good on phone"*, with *"deep research"* and *"100 % sure"* before changing anything, and doubts
to be cleared by asking.

### Measured before anything changed (411 × 656 dp, the page area his phone reported in Round 4)

| Surface | What the code drew | The arithmetic |
| --- | --- | --- |
| Tools panel, Console tab, at the fixed 240 dp default | a 48 dp drag handle, the 49 dp strip, the 48 dp filter row, a 64 dp `OutlinedTextField` | 209 dp of the panel's own rows → **~31 dp, one line of output** |
| …with the keyboard up | the cap is 65 % of what is left (356 dp → 231) and a second row (Cancel · Execute, 40 dp) appears | 249 dp of rows in 231 → **no output visible while typing**; the page shrank to ~125 dp |
| Tab strip | five `labelLarge` tabs at 12 dp padding + a text "Close" | ≈ 495 dp needed → Settings scrolled off; underline a fixed 56 dp under 64 dp labels |
| Filter row | five `FilterChip`s + two 48 dp icons | ≈ 442 dp → the Error chip half off-screen |
| Console lines | `bodySmall` mono, no level colour, no auto-scroll | a result under the fold looked like no result |
| Network tab | a six-column table, 288 dp of fixed columns | **Name ≈ 107 dp → every row read `http://127.0.…`**; the note above it wrapped to ≈ 90 dp; Status is "—" on every row by construction |
| Elements tab | 48 dp rows, then a 168 dp details block and a row of three text buttons | after one tap the tree had **negative** room at the default height |
| Resources tab | four fixed cells + copy | address ≈ 180 dp at 11 sp |
| Run-output panel (70.1), 220 dp default | status 48 + Stop row 48 + command line 48 | **~76 dp (3–4 lines), ~44 dp (2 lines) with the error-count banner** |
| Projects hub card | `Card(onClick → HubCardAction.OPEN)` → the hub's tree; ⋮ had "Open" (tree) and "Open in editor" (Phase 46.2's entry-file rule) | — |

### The owner's answers (asked before code, recorded verbatim)

| Question | Answer |
| --- | --- |
| Card tap: editor, tree in ⋮ as "Browse files"; also Create? | **"Same, and 'Create project' also lands in the editor"** — supersedes Phase 66.1's *"Keep today: hub file tree"* |
| Tools panel height rule (½ + fill while typing / ½ + keep cap / keep 240) | **"I don't know do which will be best"** — left to this chat |
| Network / Resources rows | **"Two-line rows, no header; note collapses to one line"** |
| Run-output default (220 / ≈ 40 % / ≈ 50 %) | **"Your choice"** — left to this chat |

**The two decisions taken here, with the reason.** (1) Tools panel: the default is **half the
page area** (`PreviewToolsPolicy.DEFAULT_FRACTION`, 328 dp on his phone — the page keeps the other
half; floor 220 and the 65 % cap unchanged). While a command is typed the panel and the page
**both keep their size**: the keyboard's height is added back into the height the panel is sized
against, the page reserves `panel − keyboard` under itself, and the panel is drawn over the
page, bottom-aligned above the keyboard. That is neither of the two options offered — filling
the whole area above the keyboard would have laid the WebView out at 0 dp and back (Round 4's
lesson: a page relaid at a strange height is a page that misbehaves), and keeping the 65 % cap
left three lines. (2) Run output: **40 % of the screen** (`OutputPanelHeight.DEFAULT_FRACTION`,
about 320 dp — nine lines) instead of a fixed 220 dp; the 55 % / 38 % caps and the 160 floor the
owner chose this morning are untouched. Both are recorded as this chat's choices, not his.

### What changed

**Hub (`FileManagerScreen.kt`).** One local `openInEditor(project)` — Phase 46.2's rule via
`viewModel.entryFileForEditor` (launch default → newest source → first source; a project with
nothing to open falls back to the tree). The card's tap sends `HubCardAction.OPEN_IN_EDITOR`
(with the `PROJECT_OPENED` haptic); the wizard's `onCreated` calls the same function; ⋮'s first
item is **Browse files** (`hub_browse_files`, `HubCardAction.OPEN` → the hub tree). The redundant
"Open in editor" menu item and its string are gone; both enum values stay (the `HubSurfaceTest`
inventory). The tree is also still the editor drawer's Files slot.

**Tools panel (`PreviewToolsPanel.kt`).** The strip is the drag handle (a 4 dp pill above it;
the resize semantics moved with it) — the 48 dp handle row is gone. Tabs at `labelMedium`, 10 dp
padding, a full-width underline, Close as a 48 dp ×: all five fit 411 dp at the default font
scale. Chips at `labelMedium` with 2 dp gaps: the row fits beside its two icons. Console lines
coloured by level (error / tertiary tints, info in primary), a hairline between entries, and the
list follows its newest line. The command line is **one 48 dp row**: `›`, a flat
`BasicTextField` (13 sp mono), and the shots' **Cancel · Execute inside the row** while the caret
is there or a line is pending — never a second row. Network and Resources are **two-line rows**
(`nameLabel` — the file; `requestSummary` / `resourceSummary` — `GET · script · 12.3 kB · 45 ms ·
host/path`, absent cells left out, Status never repeated per row); the six-column header and its
strings are gone; the long notes show in full only while a tab is empty and collapse to one
tappable line once there are rows. Elements: the details block is a 48 dp header (tag + box, the
highlight eye, ⋮ → Copy selector / Copy HTML) that opens on a fresh selection and splits the tab
with the tree, and folds on tap. Settings: the clear-cache note sits under its label.

**Screen (`WebPreviewScreen.kt`).** `requestedHeight` starts `NaN` (the policy's default share);
`imeDp` is read above the `imePadding()`'d column; the geometry described above
(`available = maxHeight + imeDp`, `pageReserve`, the panel `align(BottomCenter)` over the page).

**Run output (`OutputPanelHeight.kt`, `EditorScreen.kt`).** `DEFAULT_FRACTION = 0.40`,
`defaultFor(screen)`; the editor opens the panel at `defaultFor(panelScreen)`.

**Coverage.** `PreviewToolsLayoutTest` +2 Robolectric cases (the 411 × 656 default: panel 328,
page 328, the console list ≥ 160 dp with the newest line displayed and Execute reachable; Network
and Resources render the file name and the summary, no "Method" header) and Close by content
description; `PreviewToolsPolicyTest` +1 (the labels and summaries) and the default-share cases;
`PreviewToolsWiringTest` +1 (the geometry, the handle-less strip, the one-row command line, the
two-line rows); `OutputPanelStatusTest` (`defaultFor`, the NaN default) and `OutputPanelWiringTest`
(`defaultFor(panelScreen)`); `HubDialogWiringTest`'s create-landing case rewritten for the
superseding answer (and it pins the card tap, Browse files, and the removed string).

**Research notes.** Measured from the checkout, not remembered: the row heights above are the
modifiers in the files (`heightIn(min = 48.dp)`, M3's 48 dp minimum interactive size on chips and
text buttons, `OutlinedTextField`'s 56 dp + 8 dp padding); the page area is the `view 411×656 dp`
of the owner's own Round 4 console line. Compose: `Modifier.minimumInteractiveComponentSize`
enlarges a chip's *layout* to 48 dp (why the filter row is 48 dp regardless), `imePadding()`
consumes the IME inset for its children (why `imeDp` is read above it, the way `EditorScreen`
already reads `imeVisible`), `TextOverflow.MiddleEllipsis` is not in this BOM (2024.12.01 → UI
1.7), which is why the file name is a pure label instead of a start-ellipsised address. The
reference for a phone console is the same as Phase 72.1's: the owner's SPCK shots (five tabs, a
filter row, Cancel · Execute with the keyboard) — kept, re-fitted.

**CI, round by round (honest, because the first was red).** Run `36452192492` on `c840787`:
**2 363 host tests, 1 failed** — `PreviewToolsLayoutTest › on a phone the default console shows
lines, not just its own rows`. The cause was the test bed, not the panel: Robolectric's default
display is **320 × 470 dp**, the case laid a 411 × 656 dp column out on it, `Modifier.size` yields
to a smaller parent, so the page came out 142 dp instead of 328 (the other phone-sized case passed
only because it asserted no heights). Fix `26cb316`: the two phone cases declare
`@Config(qualifiers = "w411dp-h820dp")`. The same commit widens the workflow's annotation grep so a
failing host test's `AssertionError at FooTest.kt:NN` line is surfaced too — this round had to be
diagnosed by reasoning because the raw log is unreadable from here and the annotations carried only
the test's name. Run `36453064452` on `26cb316` died in **1 m 27 s** before any test:
`Install NDK (Side by side) 27.2.12479018 … ZipException: Archive is not a ZIP archive` — a
corrupt download on the runner, nothing in the tree; the session's token cannot re-run a job
(`rerun-failed-jobs` → 403), so this record's own push is the re-run. **Run `36453466081` on
`0535c0f` (this section's records commit; the same tree as `26cb316` plus prose): ✅ GREEN — the host
suite including the two phone cases, 11 m 55 s, `CodeC-IDE-1.3.17-universal-debug.apk` 26 171 024 B.**
That debug APK is the one to install for the device pass listed below.

**What this did not establish, and then did.** At the time of writing, no device pass — the
heights were arithmetic against his reported page area and the Robolectric layout, and whether
13 sp lines and `labelMedium` chips read well *to him* was a device question. **Device pass ✅ —
owner, 2026-09-29: *"Yes all test passed"*** (the four rows named above: the Projects card tap, the
preview console with the keyboard up, Network on a real page, RUN ▶ output height — plus the
editor row of the fix below). No device, OS or theme was named.

## After round 5 — the suggestion the next letter undid (2026-09-29)

Outside this phase, spotted by the owner right after accepting round 5: on a Python file, `p` →
tap `print(` → the next letter took it away. The cause was the editor host's incremental replay
meeting the system keyboard's *composing* word; the fix is sora's own `restartInput()` bracket,
gated on `hasComposingText()`. Full record: [`TROUBLESHOOTING.md`](../TROUBLESHOOTING.md) §47 and
[`chat-phase48/PART_48_1`](../chat-phase48/PART_48_1_CARET_ABOVE_KEYBOARD.md) (follow-up);
`ComposingReplayTest` ×4 reproduces the mechanism against the real sora classes. CI ✅
`36467347590` on `308441e`. **Device pass ✅ — the same report** (`p`, tap, `r` → `print(r`).

## Closed

The owner's command on 2026-09-29 — *"Yes all test passed complete docs and merge to main"* —
closed the gate: the docs were completed in this commit and the branch went to `main` through
[PR #93](https://github.com/pabi277/CodeC/pull/93) (the PR records the merge state and commit).
`prompt.md` carries the next chat's handoff. Nothing else was started.
