# CodeC Website Phase W1.1 — Shared scaffold (chrome + stylesheet)

**Status:** 📋 **PLANNED** · **Cost:** `[static]` · **Effort:** M
· **Depends on:** nothing
· **Target files:** `website/style.css`, `website/index.html` (chrome
skeleton only — content is W1.2), `website/favicon.svg` (or `.png`),
`website/img/` **only if** the owner approves screenshots (O1 — default:
no `img/` at all)

---

## 1. Design

### Files (exact)

- `website/style.css` — the **only** stylesheet, hand-written, no framework.
- `website/index.html` — chrome + empty main (content in W1.2).
- `website/favicon.svg` — a simple, local, hand-drawn mark (e.g. a terminal
  prompt glyph in the accent color). No icon fonts, no external services.

### Design tokens (CSS custom properties — proposed palette, tunable in W1)

- Background near-black (`#0B0F14` family), surface (`#11161D`), text
  (`#E6EDF3`), muted text (`#8B949E`).
- **One accent family, terminal-style:** green (success/primary,
  `#3FB950` family) + amber (warning/highlight, `#D29922` family).
- Fonts: **system font stacks only** (self-dependent law) — a sans stack for
  text, a monospace stack (ui-monospace / SF Mono / Consolas family) for
  code, commands, labels. No webfonts, ever.
- Breakpoints: phone (default, 360 px floor) → ~760 px → desktop (1440 px
  ceiling centered).

### Components (define all in W1.1; later phases only consume)

- `.site-header` — sticky; wordmark "CodeC" (monospace, accent prompt glyph)
  + nav: Home · Install · Start · Engines · Packages · **Learn** · FAQ ·
  About · GitHub (inline SVG icon → repo root). Mobile: collapses to a
  toggle that works **with no JavaScript** (`<details>`/checkbox pattern).
- `.site-footer` — tagline "CodeC — free & open source C IDE for Android";
  link rows: Repo · README · Releases · Issues · JOURNEY; final line
  "Site source: this repo — `website/`".
- `.container` — 760 px max-width for text pages; 1040 px for the Home grid.
- `.card` / `.grid` (auto-fit, 2-col → 1-col) — feature callouts, chapter
  cards.
- `.btn` / `.btn-primary` / `.btn-secondary`.
- `.code-block` (`<pre><code>`) — the copyable command block used everywhere.
- `.table` — engines, packages, chapter index.
- `.chapter-crumb` ("Chapter N of 17") + `.prev-next` — learning wing nav
  (used from W4).
- `.goal-box`, `.try-it`, `.mistake` — the three chapter template boxes
  (used from W4).
- `.learning-banner` — Home's course entry point (W1.2).
- `.faq-item` — heading+anchor FAQ entries (W3).

### Self-dependent invariants (checked in W1.1)

- The **only** CSS file the browser loads is local `style.css`; the only
  image is the local favicon (until O1 is answered).
- Every `http(s)` occurrence in W1.1 output may appear **only** in
  `<a href>` (github.com links).

## 2. Implementation steps

1. Create `website/`; write `style.css` with tokens + every component above
   (document each class's purpose in the `chat-web2/` record — this is the
   future owner's style reference).
2. Build the chrome (header + footer) in `index.html` as the documented
   copy-paste pattern; add the local favicon.
3. Render at 360 px and 1440 px; fix layout.
4. Run the **first self-dependent grep sweep** (plan §5.5) and record the
   result in `chat-web2/`.

## 3. Exit condition

```text
1. index.html opens (any static file server): header renders with all 8 nav
   items + GitHub icon; footer renders with all 5 links + site-source line.
2. At 360 px the nav collapses to a working toggle with NO JavaScript.
3. At 1440 px the container centers and max-widths hold.
4. Grep sweep (plan §5.5): zero external src/link/@import/url; only
   <a href> carries http(s). PASS recorded in chat-web2/.
5. Favicon loads from the local file; no console errors.
6. Chrome pattern documented in chat-web2/ (so W2–W6 copy it byte-identical).
```
