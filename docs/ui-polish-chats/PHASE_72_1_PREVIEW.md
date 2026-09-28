# Proposed Phase 72.1 — Web preview, console and viewport tools

**Status: discussion draft, not approved for implementation.** One part = one
future chat. No deadline, dependency, new control or replacement engine promised.

## Copy into a new chat

> Review `docs/UI_POLISH_REVIEW_20260927.md` and this brief. Re-read the current
> implementation and real reference shots. Tell me what already works, what
> detail you recommend changing, and ask for my thoughts before implementing.
> Keep Phase 64’s no-guide/no-installation-UI-lock decision. Do not merge or open
> a PR without my instruction.

## First move: current evidence

Main entry: [`ui/screens/WebPreviewScreen.kt:90`](../../app/src/main/java/com/codeci/ide/ui/screens/WebPreviewScreen.kt#L90), reviewed on 2026-09-27.
Recheck line numbers against the future checkout; these are this review’s anchors.

**Reference boundary:** No preview reference shot. Phase 61 is implemented; remaining WebView/device evidence is not invented.

## My recommendation

Polish the newly built tools rather than building a second browser. Keep the webpage viewport primary and tools easy to dismiss.

## Bounded review checklist

Static/live page; load error/retry; hamburger links; reload; open browser; LAN sharing/QR; console empty/error/filter/resize; observed-request Network; zoom; fitted resolution; narrow landscape.

## Your thoughts — ask before code

**Should Console stay in the menu, or have a permanently visible shortcut?**

Suggested starting point, not your decision: Keep the menu entry unless console use is frequent. Do not call observed Network entries a full browser DevTools capture.

**Owner answer:** superseded on 2026-09-28 — see *Owner input* below. The owner's
own screenshots answer the reference boundary this brief declared (“no preview
reference shot”), and the question they raise is no longer where the console entry
lives but what the console **is**.

---

# Owner input — 2026-09-28 (this chat's first material)

Six phone shots arrived with the message, verbatim:

> *"Try to make output exactly same for web view"* · *"And terminal output is good
> enough but is hard to understand the error line from terminal"*

The shots (saved to `uploads/`, names ending `134154`, `134206`, `134212`,
`134218`, `134226`, `134232`) are **SPCK Editor's Web Preview** of the owner's own
site — the first real preview reference this project has, and the one this brief's
“Reference boundary” said did not exist. What they establish, read plainly:

1. **The bar.** Page title, **“Preview · 100 % Zoom”** subtitle, a console-toggle
   icon (rectangle-with-`>`; it becomes ⊘ while the console is open), refresh, ⋮.
2. **The page.** The site renders phone-sized at 100 % on *every* page shown, not
   only `index.html`.
3. **The console.** A five-tab strip — **Console · Elements · Network · Resources ·
   Settings** — a filter row **All · Info · Warning · Error** with filter and copy
   icons, an empty body with a caret, and, with the keyboard up, **Cancel ·
   Execute**. **The console takes commands.**
4. **Network.** A table: **Name · Method · Status · Type · Size · Time**.
5. **The ⋮ menu.** **Open in Browser… · Back · Forward · Zooming · Screen
   Resolution · Clear Cache · Exit**.

## What CodeC has today (read on this checkout)

- `WebPreviewScreen.kt` (592 lines): a top row with **Back**, the file name, a ⋮
  (`preview_menu`) and **Refresh**; the ⋮ holds *Copy page address · Share rows ·
  LAN on/off · Server options… · Show/Hide console / Network · Zoom… · Screen
  resolution… · Stop servers*.
- `PreviewToolsPanel.kt`: a drag handle (48 dp, 44 × 4 grip), a row of **two**
  `FilterChip`s — **Console / Network** — with **Clear** and **Close**, a level
  filter row (`PreviewLevel` entries, lowercase labels), then **monospace text
  lines**. `Network` renders one line per observed request
  (`method [document|resource] address`) and states its own limits: *“Observed
  requests only — not responses or timings.”*
- **The console is read-only.** There is no input line, no Execute and no Cancel
  anywhere in the panel — the owner's *“i can't run any console command”* is exact.
- `PreviewWebView.kt`: `javaScriptEnabled`, DOM storage, file access, zoom via
  `setInitialScale` + `zoomBy`, `onConsoleMessage` capture, request observation in
  `shouldInterceptRequest`. `useWideViewPort = true`, `loadWithOverviewMode = false`.
- `PreviewToolsPolicy`: capacity 200, text limit 4096, zoom presets 50–200 %,
  resolutions DEVICE/PHONE/TABLET/DESKTOP, request redaction (never credentials,
  query or fragment).

## The honest gaps against the shots

| Shot | CodeC today | Feasible now? |
|---|---|---|
| Console with a command line + Execute/Cancel | read-only lines | **Yes** — `evaluateJavascript` on the existing WebView; no new dependency |
| Tab strip **Console · Elements · Network · Resources · Settings** | two chips: Console / Network | Console + Network **yes**; **Elements** is a DOM inspector — it needs the WebView DevTools protocol over a socket (a new subsystem), and Phase 61 deliberately dropped it; Resources/Settings are partial at best |
| Filter row **All · Info · Warning · Error**, copy | level chips, lowercase | **Yes** |
| Network table **Name · Method · Status · Type · Size · Time** | one text line per request; already states “not responses or timings” | **Partly** — method/URL are free; status/type/size/time are not available from `shouldInterceptRequest` |
| Bar subtitle **“Preview · 100 % Zoom”** | no zoom readout | **Yes** |
| ⋮ **Open in Browser… / Back / Forward / Zooming / Screen Resolution / Clear Cache / Exit** | most of these exist in different words/shapes (OpenInBrowser, Zoom…, Screen resolution…, Back, Settings→Clear Cache) | **Yes**, as a re-shape |
| Other pages render phone-sized | `useWideViewPort = true` + `loadWithOverviewMode = false` — a page with no mobile `viewport` meta renders at desktop width, i.e. tiny | **Probably** — this is the likely cause of *“others are very small screen”* |

**No code written.** The owner is asked which of these is the part, and in which
order (his last instruction was to wait for the shots, which have now arrived).
This brief must not be read as approval to rebuild the preview or to copy SPCK's
DOM inspector.


## Implementation and exit, after agreement

PreviewToolsPolicy, PreviewChromePolicy, PreviewToolsPanel, PreviewWebView and ServerRegistry; keep request redaction and no injected DOM inspector.

Use existing platform/components and pure policies first. Add focused regression
coverage for the agreed behaviour, check loading/empty/failure states, long text,
Back, touch targets, light/dark, narrow layouts and keyboard overlap. Run CI on
the session branch. Report separately what source tests, Android tests and real
device evidence establish. Do not make the owner run declined device rounds.

No new dependencies, permissions, persistent preferences or telemetry by default.
Stop after this agreed part and update its record; do not roll into the next chat.


## Implementation (2026-09-28) — the preview, delivered first

**The owner's round-3 answers, taken before any code, verbatim:** the surface is
**“Both, one after the other”**; the console takes **“The whole strip, Elements
included”**; **“Yes — fix the tiny rendering in the same part”**; the line that
is hard to understand is **“The run output panel's”**; the order is **“Both in
this chat, preview first”**.

**Branch** `arena/01a0e704-codec` (base `main` @ `fbb3056`), commits `090596e`
(this part) and `2a707be` (the icon import the first push was missing).
**CI: `Build APK` run [36399610563](https://github.com/pabi277/CodeC/actions/runs/36399610563)
GREEN on tip `d86b4a3`** (12 m 2 s): `:app:testDebugUnitTest` **2347 tests, 0
failed**; debug and release APKs assembled (6,852,680 / 26,146,308 bytes); the
release manifest carries no `android:debuggable` flag. Every red round was fixed
for cause: `36397545127` — `Unresolved reference 'Refresh'` in
`PreviewToolsPanel.kt` (a missing import, not a test); `36397873242` — one
policy test compared the merged rows with the unmerged ones (the law is: nothing
to join, rows untouched); `36398106111` and later rounds — `OutputPanelView.kt`'s
`is OutputHead.Done` branches and the `motion` hook (see the 70.1 brief).

### What the shots asked for, and what is there now

| Shot | Delivered |
|---|---|
| Bar: title, **Preview · N % Zoom**, console toggle, refresh, ⋮ | `WebPreviewScreen`'s bar: `preview_bar_subtitle`, the `SpckIcons.Console` toggle (`preview_console_show/hide`), Refresh, ⋮; zoom and fit are `rememberSaveable` |
| Five tabs **Console · Elements · Network · Resources · Settings** | `PreviewToolTab` + `PreviewToolsPanel`'s strip; the empty console is still openable (the Phase 61 reversal of the old “no console when empty” gate) |
| Filter row **All · Info · Warning · Error**, copy | level chips; filtering never destroys entries — it hides them |
| Console with **Cancel · Execute** and a command line | `PreviewConsolePolicy`: the line is echoed as an INFO `› …`, run through `command()` (one line, `eval(<quoted>)`, a 4096 cap), and the WebView's answer becomes a LOG or ERROR line (`ok\u0001…`/`err\u0001…`) |
| Network table **Name · Method · Status · Type · Size · Time** | Name/Method from `shouldInterceptRequest` (redacted: no credentials, query or fragment); Type/Size/Time joined from the page's own Resource Timing entries by `PreviewToolsPolicy.merge`, keyed on the redacted address; **Status stays “—”**, and the panel still says why |
| Resources | the page-reported list, redacted the same way, with refresh and copy |
| Settings | zoom presets, screen resolution, **Fit page to phone**, the viewport outcome in words (authored / added / not reported / off), **Clear cache** |
| Other pages render phone-sized | `PreviewToolsPolicy.viewportScript()` asks once per load: a page **without** a viewport meta gets `width=device-width, initial-scale=1` appended; a page that declared one is left byte-for-byte as authored. The outcome is recorded (`PreviewViewport.AUTHORED/ADDED/UNKNOWN/DISABLED`) |
| Elements | `PreviewInspectorPolicy`: a DOM walk into `window.__codecDom` (caps 300 nodes / depth 12 / 80 text chars / 40 attributes / 4000 HTML chars), a tree, a details view, “Highlight on page” (`2px solid #4FC3F7` + `scrollIntoView`) and Copy selector / Copy HTML |

**One Phase 61 pin was deliberately reversed, with the reason recorded in the
test:** `PreviewToolsWiringTest` used to assert the native view contained **no**
`evaluateJavascript` — the Network tab observed and injected nothing. The
owner's shots (*Execute*) and his report (*“i can't run any console command”*)
asked for the opposite, so `PreviewWebView` now exposes `evaluate(script,
onResult)` → `evaluateJavascript(script)` on the view's own thread. The scripts
are still only the pure policies' (never a page's payload), `shouldInterceptRequest`
still returns null, still fetches and replaces nothing, and the view still has no
`openConnection` and no `reload()`.

**Tests added or rewritten:** `PreviewToolsPolicyTest` 17 cases (five tabs in
order, redaction, the merge and its identity case, resource parsing, viewport
outcomes, the honest cell labels, `panelHeight` 220 floor / 65 % cap),
`PreviewConsolePolicyTest` 7, `PreviewInspectorPolicyTest` 5,
`PreviewToolsWiringTest` 7 pins, `PreviewToolsLayoutTest` 3 Robolectric cases
(resizing never consumes the page, the five tabs, the Settings readouts).

**Honest limits, unchanged by this part:** Elements is **read-only** — a DOM
walk, not SPCK's DevTools protocol; there is no element editing, no computed
styles panel and no network-status capture; Network's Status column stays “—”;
and **nothing here has been on a handset** — source tests and CI only.

### Render fix (2026-09-28, the owner's screenshot)

The owner's two screenshots of the same snake page — whole in Samsung Browser, clipped in
CodeC's Web Preview (pad near the top, hint under it, a long empty band, header/score/START
off-screen) with *"correct it CodeC preview sucs"* — reproduced in headless Chrome: the seed
page fixed its own height, so a box shorter than its column clips the top of the content
irrecoverably (header at −53 px at 360×520 CSS px). Fixed in the page (`min-height`, a board
capped by the viewport, no stray paragraph margins, the gesture only on the board) and in this
screen (the first load waits, bounded at 1 s, for a measured page box). Renders and numbers:
[`../chat-phase70/README.md`](../chat-phase70/README.md); pins in `FirstOpenSampleTest` and
`PreviewToolsWiringTest`. **Headless Chrome evidence only — no device pass.**

### Render fix, round 2 (2026-09-28, the owner's next screenshot)

*"It's better than before but still i don't think it's good enough. Because browser have a good
view and code is very smaller view."* Round 1 had traded a **clip** for a **small board** — the
page's board was capped by `calc(100vh - 320px)`, and this screen's box is shorter than a
browser window (bar, address row, keys row, nav bar), so the board fell from 380 px to 200 px
at 360×520. Round 2: the board is width-driven again (a short box scrolls, like a browser), the
screen no longer reserves the IME inset unless the **tools panel** (the only text field on this
screen) is open, and the WebView logs the page's own box — `page box 360×430 CSS px · view
360×430 dp · dpr 1.75` — into the console after every load, so “the page renders small” and “the
preview box is short” can never be confused again. Renders and numbers:
[`../chat-phase70/README.md`](../chat-phase70/README.md). **CI ✅ green `36409241166` on `0ca08dc`;
no device pass.**

### Round 3 (2026-09-28, a third-party page) — the measurement line

The owner's snake problem is fixed (his confirmation); the next report is his cloned
Code-with-C site being *"very small"* next to Samsung Browser. Two shapes look identical in a
screenshot — a layout **wider than the phone** (wide-viewport fallback, squeezed) and a
**smaller scale** (right layout, drawn below density) — and they need opposite fixes, so this
round ships measurement instead of a hunch. The per-load console line became:

`page box 360×619 CSS px · meta width=device-width, initial-scale=1.0 · view 360×430 dp · scale 3 · dpr 3`

(`PreviewToolsPolicy.parsePageBox`/`pageBoxLabel`; the scale comes back from the WebView's own
`getScale()`, pinned in `PreviewToolsWiringTest`). Both candidate fixes are one line of change
once the line says which one it is. **CI ✅ `36417473156` on `e436473`; no device pass.**

### Round 3b (2026-09-28) — the console answered, and the answer was acted on

The owner's four lines: three loads laid the page out **411 CSS px in a 411 dp box at scale = dpr**
(the browser's own 1:1), one laid it out **457 in a 411** — drawn at 0.9×, 10 % small. That is a
page laid out for a box it does not have (Chromium fixes the scale before the page's `viewport`
meta is in effect). `PreviewToolsPolicy.boxMismatch` is now the pure rule that detects it —
device-width pages only, zoom-aware, 4 dp tolerance, never for a page that declares its own width —
and `PreviewWebView` loads the page once more when it fires, logging the reason to the console.
The Phase 61 “the native view never reloads” pin was amended **with its reason** (red round
`36420734801`, green `36421203364` on `c954599`). **No device pass.**

### Round 4 (2026-09-28) — the modal strip: a 0 px layout viewport, and it was ours

The owner's next pair: the same Code-with-C page with a program card open — a full card in
Samsung Browser, a **42 px strip** (the pill and the ×, nothing below) in CodeC, under a page-box
line that read healthy (`411×655 CSS px · view 411×656 dp · scale = dpr`). The card is
`max-height: 88vh` in border-box sizing with `20px` vertical padding: `88vh → 0` gives exactly
padding + border = 42 px, the pill visible inside the padding, everything else clipped. `vh` was 0
because Chromium's WebView forces a zero **layout** height whenever its layout params say
wrap-content (`AwLayoutSizer.updateLayoutSettings()` →
`setForceZeroLayoutHeight(isLayoutParamsHeightWrapContent())`), and Compose's `AndroidView` stamps
`WRAP_CONTENT` on a bare view via `addView` — while still measuring it `EXACTLY`, which is why
`innerHeight` (the visual viewport) kept telling the truth. Fix: `PreviewWebView` is born with
`MATCH_PARENT × MATCH_PARENT` params. Instrument: the per-load line now prints what the page's CSS
gets for `100vh` (`· 100vh 655 px ·`), and `PreviewToolsPolicy.layoutHeightCollapsed` raises a
console **warning** when it is shorter than half the page's own `innerHeight`. Rounds 1 and 2 are
re-read in the chat README: their symptoms were this same quirk acting on `height:100%` and
`calc(100vh − …)`. Full account: [`../chat-phase70/README.md`](../chat-phase70/README.md) §Round 4.
**CI ✅ `36436647849` on `146986b`; no device pass — the owner re-installs to confirm: the line
should read `100vh 655 px` and the card should open whole.**

### Round 5 (2026-09-28) — the phone pass

The owner: *"make everything from this and only this phase whatever is done, phone friendly …
the console input etc. is not looking good on phone"*. Measured first, on the 411 × 656 dp page
area his own Round 4 line reported: at the fixed 240 dp default the Console tab spent 209 dp on its
own rows (48 handle + 49 strip + 48 filters + 64 text field) and showed **about one line**; with
the keyboard up, the 65 % cap plus a second Cancel · Execute row left **none**; the Network table's
288 dp of fixed columns left Name ≈ 107 dp (`http://127.0.…` on every row) under a note that wrapped
to ≈ 90 dp; one tap in Elements gave the tree negative room. Asked before code: two-line rows
(**"Two-line rows, no header; note collapses to one line"**) and the height rule (**"I don't know do
which will be best"** → this chat's choice). Delivered: the strip is the drag handle (no 48 dp
handle row), labelMedium tabs with a 48 dp × Close so all five fit 411 dp, chips that fit beside
their two icons, level-coloured console lines that follow the newest entry, a **one-row** command
line (`›` + flat field + Cancel · Execute inside it), two-line Network/Resources rows through pure
`nameLabel` / `requestSummary` / `resourceSummary` (no header, its six strings removed), notes that
collapse to one tappable line once rows exist, an Elements details header that opens on selection
and splits the tab with the tree, the cache note under its label. Height: the default is **half the
page area** (`DEFAULT_FRACTION`), and while a command is typed the panel and the page keep their
size — the keyboard's height is added back into what the panel is sized against, the page reserves
`panel − keyboard`, the panel is drawn over the page above the keyboard (no 0 dp relayout of the
WebView; Round 4's lesson). Tests: `PreviewToolsLayoutTest` +2, `PreviewToolsPolicyTest` +1 and the
default cases, `PreviewToolsWiringTest` +1. Full account and the CI stamp:
[`../chat-phase70/README.md`](../chat-phase70/README.md) §Round 5. **No device pass.**

