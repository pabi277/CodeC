# W1 shared chrome and style contract — 2026-10-07, v2.3

Canonical implementation: [`../../website/index.html`](../../website/index.html).
Original implementation, PR #42 structure inspected but not copied/imported.
One stylesheet: [`../../website/style.css`](../../website/style.css).

## Copy-paste rules for W2–W6

1. Copy doctype, html lang, head charset/viewport/theme-color/local icon+stylesheet,
   body skip-link and **shared header** through `</header>` from index.html.
2. Set unique title/description, move aria-current="page" to the active link in
   **both** desktop and mobile nav. Each has Home/Install/Start/Engines/Packages/
   AI/Learn/FAQ/About/GitHub in that exact order. Chapter pages use Learn active.
   Privacy has no header item: identify its footer link as current instead.
3. Content goes in `<main id="main" tabindex="-1">`; every page one h1. Narrow
   pages use `.container`, Home `.container-wide`. Skip link targets main.
4. Copy **shared footer** byte-identically. Privacy is always present. Shared
   changes must propagate to ALL existing pages in the same phase and be diffed;
   only active state is a per-page chrome exception.
5. **Temporary W1 state:** Home notice and course availability labels disclose
   unbuilt pages. Footer Privacy says upcoming until W3.6, at which point change
   the canonical footer on every existing page together. Don't retain upcoming
   labels after pages actually ship, or falsely mark pages available beforehand.
6. Relative `.html`, `style.css`, `favicon.svg` paths only, no leading slash.
   Works at project Pages prefix and file://. All new assets stay local.
7. Native `<details>/<summary>` mobile navigation at ≤1100px; desktop menu above.
   CSS hides the inactive navigation, so screen readers don't get two visible
   menus. Native summary carries expanded state and Enter/Space behavior, no
   faux checkbox/aria-hidden control, no JS. Open menu may be closed by summary;
   no undocumented Escape/outside-click behavior is promised.
8. SVG favicon is **byte-identical** to docs/brand/icon/codec-mark.svg. It is copied
   into website, not fetched from the source path. Its xmlns is not a request.
   Decorative img marks have empty alt; wordmark link accessible name says CodeC.
   Feature glyphs are original inline stroke icons, not vendor/icon-font imports.

## CSS design tokens

| Token | Value / role |
|---|---|
| --bg | #0b100e dark page |
| --surface / --surface-raised | #101713 / #141e18 cards/chrome |
| --surface-code | #0c120f example/code |
| --text / --muted / --subtle | #edf2ed / #a4b0a6 / #89998d |
| --accent / --accent-light | #3ddc84 identity / #8ce9b3 links |
| --amber | #e6c278 preview/status caution |
| --line / --line-bright | #28392e / #3e5948 borders (not text) |
| --sans / --mono | System fonts only, no font loads |
| --radius | 12px panels |

No theme toggle/hooks, external images, imported CSS, webfonts, analytics, embeds,
script, service worker or generated site build. Motion only modest hover/smooth
scroll; reduced-motion disables both. Native sticky header background is opaque
enough for contrast. Main targets are ≥44px high, primary CTA 48px.

## Components (one owner: style.css)

| Class / pattern | Purpose |
|---|---|
| .site-header, .header-inner, .wordmark | Sticky shared brand/nav |
| .desktop-nav, .mobile-menu | Responsive navigation with native details menu |
| .skip-link, :focus-visible | Keyboard path + visible focus |
| .site-footer, .footer-links, .footer-bottom | Shared project/source/Privacy links |
| .container-wide / .container | 1160px Home maximum / 760px prose maximum |
| .hero, .eyebrow, .cta-row | Home hierarchy and single APK CTA |
| .btn, .btn-primary, .btn-secondary | Anchors styled as clear link buttons |
| .code-showcase, .editor-example, .terminal-example | Labelled static textual illustration; NEVER a screenshot/live UI |
| .quick-facts | Semantic dl summary, 4 → 2 columns |
| .grid, .card, .feature-card, .card-link | Six feature articles, 2 → 1 column |
| .learning-banner, .course-path | Course overview / planned groups |
| .trust-strip, .footnote-strip | Consent note / precise released-version facts |
| .code-block | Scrollable pre/code for later guide examples |
| .table-wrap, .table | Responsive semantic tables; wrapper region gets accessible label/tabindex if scrollable |
| .chapter-crumb, .prev-next | Chapter N of 19 and prev/next anchors |
| .goal-box, .try-it, .mistake | Chapter learning/exercise/caution boxes |
| .faq-item | Anchored question/answer sections |

Breakpoints: 1100 native menu, 800 stacked hero/tablet, 540 single-column cards.
No artificial min-width on body. Code can scroll locally without page overflow.
Unused chapter components are deliberately defined in W1 as required by scaffold
spec, not counted as implemented chapter pages.

## Verification recipe

Diff extracted header/footer between pages (allow only aria-current shifts).
Use keyboard from skip link through header, menu, main links and footer; review
at 360/1440 and zoom. Resource scan is not just a grep for script: inspect
src/srcset/link href/style/url/@import/media/frames and actual browser requests.
Fresh offline file:// render must work with JS disabled and no cached HTTP assets.
See [W1_CHECKS.md](W1_CHECKS.md) for observed results and deferred links.
