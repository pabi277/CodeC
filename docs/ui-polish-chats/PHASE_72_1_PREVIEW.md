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
