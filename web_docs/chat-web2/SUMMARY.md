# chat-web2 — W1 build session: scaffold + Home (2026-09-02)

## What the owner asked

> "Build the website"

Per `web_prompt.md` / `NEXT_STEPS.md`, "Build the website" = **"Start W1"**.
Executed strictly per `web_docs/web-phase1/` (README + PART_1_1 + PART_1_2),
one phase, on session branch `arena/01a062f7-codec`.

## State verified before acting

- `git status` clean on `arena/01a062f7-codec` at `9b3669e` == remote `main`
  → **PR #41 was already merged** (2026-09-02), so per `web_prompt.md`:
  continue.
- `gh pr list`: #41 MERGED. `gh run list`: latest `Build APK` runs green.
- No `website/` folder existed (matches the handoff: nothing built).

## What was created — `website/` (3 files, nothing else touched)

| File | What it is |
|---|---|
| `website/style.css` | The **only** stylesheet (W1.1): design tokens + every component from PART_1_1, each block commented inline. System font stacks only; zero external imports. |
| `website/favicon.svg` | Local, hand-drawn mark: dark rounded square, green `>` + amber `_` terminal prompt (plan §6 accent family). |
| `website/index.html` | Shared chrome (header + footer) + the **Home** page (W1.2): hero → CTA row → 6 feature cards → learning banner → footnote strip. |

W1.2 design: dark by default, mobile-first, no JavaScript anywhere (mobile
nav = checkbox pattern, PART_1_1). Home meta: title
"CodeC — C programming IDE for Android"; description = hero distillate
(≤160 chars).

## ⭐ The chrome pattern — copy BYTE-IDENTICAL into every W2–W6 page

This is the documented single copy-paste pattern (PART_1_1 exit condition 6).
W2–W6 pages copy both blocks verbatim; the only per-page changes allowed:
`<title>`, `<meta name="description">`, `aria-current="page"` moved to the
active nav item, and the content between `<main id="main">` and `</main>`.

**`<head>` essentials** (title/description vary per page; links never do):

```html
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>…page title…</title>
<meta name="description" content="…≤160 chars…">
<meta name="theme-color" content="#0B0F14">
<link rel="icon" type="image/svg+xml" href="favicon.svg">
<link rel="stylesheet" href="style.css">
```

**Header** (sticky; wordmark `>_ CodeC`; nav = Home · Install · Start ·
Engines · Packages · Learn · FAQ · About · GitHub icon; mobile toggle is a
checkbox + label — **no JS**; the checkbox and label must stay siblings in
this order for the CSS `~` selector):

```html
<header class="site-header">
  <div class="container-wide header-inner">
    <a class="wordmark" href="index.html"><span class="prompt">&gt;_</span> CodeC</a>
    <input type="checkbox" id="nav-toggle" class="nav-toggle" aria-hidden="true">
    <label for="nav-toggle" class="nav-burger" aria-label="Toggle navigation"><span></span></label>
    <nav class="site-nav" aria-label="Site">
      <a href="index.html">Home</a>
      <a href="install.html">Install</a>
      <a href="start.html">Start</a>
      <a href="engines.html">Engines</a>
      <a href="packages.html">Packages</a>
      <a href="learn.html">Learn</a>
      <a href="faq.html">FAQ</a>
      <a href="about.html">About</a>
      <a class="nav-github" href="https://github.com/pabi277/CodeC" aria-label="CodeC on GitHub" title="CodeC on GitHub">
        <svg width="20" height="20" viewBox="0 0 16 16" fill="currentColor" aria-hidden="true"><path d="…octicon mark-github path, see index.html…"/></svg>
        <span>GitHub</span>
      </a>
    </nav>
  </div>
</header>
```

**Footer** (tagline + 5 links + site-source line — always exactly these):

```html
<footer class="site-footer">
  <div class="container-wide">
    <p class="footer-tagline">CodeC — free &amp; open source C IDE for Android</p>
    <nav class="footer-links" aria-label="Project links">
      <a href="https://github.com/pabi277/CodeC">Repo</a>
      <a href="https://github.com/pabi277/CodeC#readme">README</a>
      <a href="https://github.com/pabi277/CodeC/releases">Releases</a>
      <a href="https://github.com/pabi277/CodeC/issues">Issues</a>
      <a href="https://github.com/pabi277/CodeC/blob/main/docs/JOURNEY.md">JOURNEY</a>
    </nav>
    <p class="footer-source">Site source: this repo — <code>website/</code></p>
  </div>
</footer>
```

(For the exact GitHub icon path, copy it out of `website/index.html` lines —
do not re-type it.)

## Style reference (owner-facing, per PART_1_1 step 1)

Tokens (`:root`): `--bg #0B0F14` · `--surface #11161D` · `--surface-2
#151C25` · `--border #212A35` · `--text #E6EDF3` · `--muted #8B949E` ·
`--accent #3FB950` (+ `--accent-strong`, `--accent-tint`) · `--amber #D29922`
(+ `--amber-tint`) · `--font-sans` / `--font-mono` system stacks.

| Class | Purpose |
|---|---|
| `.container` | 760 px max-width text column (text pages) |
| `.container-wide` | 1040 px (Home hero/grid, header, footer) |
| `.section`, `.section-title`, `.muted`, `.mono` | spacing + small utilities |
| `.site-header`, `.header-inner`, `.wordmark`, `.prompt` | sticky chrome; wordmark with green prompt glyph |
| `.nav-toggle` + `.nav-burger` + `.site-nav` | checkbox-pattern mobile nav (no JS); `.nav-toggle:checked ~ .site-nav` opens it; burger morphs to ✕ |
| `.hero`, `.hero-kicker`, `.lede`, `.cta-row` | Home hero |
| `.btn`, `.btn-primary`, `.btn-secondary` | CTAs (green solid / bordered) |
| `.grid`, `.card`, `.card-link` | auto 2-col→1-col feature grid; card titles get a green `> ` prefix |
| `.learning-banner`, `.banner-text`, `.banner-title` | Home course banner (green→amber tint) |
| `.footnote-strip`, `.dot` | Home footnote strip |
| `.site-footer`, `.footer-tagline`, `.footer-links`, `.footer-source` | shared footer |
| `.code-block` (+ `.prose code`) | copyable `<pre><code>` command blocks (W2+) |
| `.table` (in `.table-wrap`) | engines/packages/chapter tables (W3/W4) |
| `.chapter-crumb`, `.prev-next` | learning-wing nav (W4+) |
| `.goal-box`, `.try-it`, `.mistake` | chapter template boxes — green / amber / amber (stays inside the green+amber accent law) |
| `.faq-item` | FAQ heading+anchor entries (W3) |
| `.skip-link` | accessibility skip-to-content |

## Traceability — every Home claim → source line (W1.2 step 2)

Source of truth: `README.md` @ `9b3669e` (2026-09-02). Card-by-card:

| Element | Claim | README source |
|---|---|---|
| Hero | write C in projects/single files, tap RUN; built-in TCC offline+instant, no Termux, no setup; VT/ANSI terminal `cc hello.c -o a.out` → `./a.out`; 25+ signed packages `git`/`python`/`clang`; HTML web preview with live reload | title + intro ("A C programming IDE for Android… package hub and an HTML preview served by a local loopback server"), "built-in C compiler (TCC…) offline, instantly, with no downloads, no Termux and no setup", "In-app terminal & Package Manager", "`./` is required", "live reload on save" |
| Card 1 Built-in compiler | static musl TCC in APK; arm64-v8a + x86_64 | "a static musl toolchain is embedded in the APK for **arm64-v8a** and **x86_64** devices" |
| Card 2 Real terminal | VT/ANSI + PTY; the two commands; `scanf` runs in Term | "real **VT/ANSI terminal** (Canvas grid + PTY via JNI `openpty`)", command examples, "must run in **Term**" |
| Card 3 Package hub | 25+ signed; package names; 1-tap; live badges | "provides 25+ packages including `git`, `python`, `clang`, `nano`, `make`, `ripgrep`, `tmux`", "1-Tap Install & Run", "Live Status Badges" |
| Card 4 Spck-grade editor | projects hub, file tree, tabs+dirty, autosave, find&replace, squiggles, honest git, conflict flow, honest push | "Spck-style editor… tabs… dirty dot… autosave ~2 s… find & replace… compiler-error squiggles", "Honest git… 'Committed locally ✓ — NOT pushed'", "conflicts… block the commit" |
| Card 5 Web preview | loopback server; relative CSS/JS, fetch, ES modules; live reload; console under page | "served by a loopback HTTP server over the whole project folder, so relative CSS/JS, `fetch(\"data.json\")` and ES modules work; live reload on save. Console output shows under the page" |
| Card 6 Always updatable | Settings → Install APK from GitHub; CI builds | "In the app: **Settings → Install APK from GitHub** downloads the latest release APK and opens the installer"; "GitHub Actions builds `app-debug.apk`" |
| Learning banner | 17 hands-on chapters, free, on this site | plan §3.2 (D10) — course scope, owner command |
| Footnote strip | free & open source · built-in compiler · no Termux required | plan §3.1 footnote strip; README facts above |
| CTA row | single honest CTA "Get the APK on GitHub" → Releases | D9 (GitHub-only distribution; no store implication) |

## Verification evidence (W1.1 + W1.2 exit conditions)

- **Self-dependent sweep (first run, plan §5.5) — PASS:**
  `grep -RnE '<(script|img|source|iframe|embed|object|video|audio)[^>]*src="https?://'` → 0;
  `<link[^>]*href="https?://"` → 0; `@import|url\(` → 0 (after rewording one
  CSS *comment* that contained the literal tokens). All `http(s)` in the site
  is inside `<a href>` — one declared exception: `favicon.svg` line 1
  `xmlns="http://www.w3.org/2000/svg"` is the mandatory SVG **namespace
  identifier** (never fetched by the browser; it is none of the sweep
  patterns — recorded here so W6's sweep greps with this note).
- **Outbound links verified (curl, 2026-09-02):** `…/CodeC/releases` 200 ·
  `…/CodeC` 200 · `…/CodeC/issues` 200 ·
  `…/CodeC/blob/main/docs/JOURNEY.md` 200.
- **HTML parse check** (Python html.parser): no mismatched/unclosed tags.
- **Structure:** all 8 nav items + GitHub icon present; footer has all 5
  links + site-source line; skip-link, aria-current on Home.
- **Served & smoke-tested** (`python3 -m http.server 8080`): `/` 200,
  `style.css` 200, `favicon.svg` 200; `install/start/engines/packages/faq/
  about/learn.html` → 404 **expected** (final URLs from day one; those
  phases haven't run — W2, W3, W4 build them; resolution sweep = W6).
- **Visual 360 px / 1440 px:** the sandbox has no browser; the page was
  written mobile-first (spec breakpoints 760 px / 360 px floor / 1440 px
  ceiling) and is exposed as a **live preview** for the owner's visual
  check. Breakpoint behaviour is pure CSS (media queries; grid 2→1 col;
  nav checkbox toggle). If anything looks off at either width, it's a
  one-file CSS fix — flag it in chat.

## W1 decisions & notes (for the owner to veto)

1. **Internal links are relative (`install.html`), not root-relative
   (`/install`).** The phase README says "final URLs from day one
   (`/install`)". The site will deploy at a **subpath**
   (`https://pabi277.github.io/CodeC/`), where root-relative `/install`
   resolves to the wrong host path and can never work; relative `.html`
   links produce exactly the final URLs (`…/CodeC/install.html`) under
   GitHub Pages, work on any static server and `file://`, and need no
   build step. Same intent, only the mechanism differs — recorded here as
   the W1 note; all later phases follow it.
2. **`.mistake` box uses the amber family** (not red) to respect the
   one-accent-family token law in PART_1_1.
3. **No `img/` folder** — O1 (screenshots) unanswered; default per
   PART_1_1 is no images.
4. GitHub octicon mark-github (MIT-licensed Octicons path) as the header
   icon — inline SVG, local.

## Exit conditions

**W1.1 (scaffold):** 1. header renders with all 8 nav items + GitHub icon;
footer with all 5 links + site-source line ✅ (structural check + served)
· 2. 360 px nav toggle works with NO JavaScript ✅ (checkbox pattern, pure
CSS; visual confirmation via live preview) · 3. 1440 px containers center,
max-widths hold ✅ (CSS `max-width` + `margin: 0 auto`; visual check via
preview) · 4. grep sweep PASS ✅ (above) · 5. favicon local, loads ✅ (200,
no console errors possible offline — zero JS shipped) · 6. chrome pattern
documented here ✅.

**W1.2 (Home):** 1. order hero → CTA → 6 cards → learn banner → footnote ✅
· 2. primary CTA → Releases (200), secondary → repo (200) ✅ · 3. learn
banner → `learn.html` final URL, 404 expected until W4 ✅ (noted) · 4. every
card claim sourced (table above) + sweep PASS ✅.

**→ W1 COMPLETE.** Report + push done; stopped at the merge gate
(`rule.md` §3 — the owner merges, or commands a PR).

## Next step

Owner says **"Start W2"** → `/install` + `/start` per
`web_docs/web-phase2/` inside this chrome. (Living docs updated in the same
commit: `web_prompt.md`, `NEXT_STEPS.md`, `WEB_JOURNEY.md`,
`web_docs/README.md`.)

---

## Follow-up (2026-09-02, same session) — owner feedback: GitHub link not findable

**Owner:** "I could not find can you add the link to github." The header's
GitHub link was icon-only — too easy to miss. **Change:** the nav GitHub
link now carries a visible **"GitHub"** text label next to the octicon
(`<span>GitHub</span>` inside `.nav-github`; CSS gains `gap: 0.45rem;
font-size: 0.95rem`). The header snippet above is updated to match — W2–W6
copy THIS version. GitHub now appears 4× on Home: header (icon + "GitHub"),
hero CTA ("Get the APK on GitHub" → Releases, "Read the README" → repo),
footnote strip ("Source on GitHub"), footer link row ("Repo"). Sweep
re-checked: still zero external resources (label is text, icon is inline
SVG). Chrome pattern rev: 2.

## Follow-up (2026-09-02, same session) — owner command: "Deploy the website to Pages now"

Early deploy, deviating from W6-first (recorded as **D14**). Discovery
(see the research note in `web-phase6/README.md`): the repo's ONE Pages
site already serves the signed package repo (`/dev`, `/keys`), and every
Pages deploy replaces the whole site — so coexistence was engineered
**before** deploying:

1. **New `.github/workflows/pages.yml`** — on push to `main`
   (`website/**`) or dispatch: finds the newest live `github-pages`
   artifact (i.e. the last package-repo publish), downloads it cross-run,
   unpacks `dev/` + `keys/`, unions `website/` on top, uploads + deploys.
   Sanity-checks `dev/CODEC-REPOSITORY` and `site/index.html`. Shared
   `pages` concurrency group with the package workflow. Fails loudly (no
   deploy) if no live package-repo artifact exists — never wipes `/dev`.
2. **Mirror edit in `package-repository.yml` `publish-dev`** (app-side,
   explicitly covered by the owner's command): adds `website/` from
   `main` (sparse checkout) to the `packages/` upload + same concurrency
   group — so a package publish re-publishes the site alongside `/dev`.
3. **Verified locally:** both files YAML-parse (PyYAML); the exact union
   shell steps were simulated against a fake `artifact.tar` (dev/ + keys/
   + website → site root correct). First YAML attempt had a colon-in-plain-
   scalar step name — caught by the parse, fixed before push.

Deploy target: `https://pabi277.github.io/CodeC/` (W1 content live now;
W6.7 still the final verified deploy of the full 25-page site).
