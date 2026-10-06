# W1 checks — 2026-10-07

**Scope:** index.html + style.css + favicon.svg only. Local static/browser checks
are website evidence, **not** an Android test/device pass, full-site link pass,
Pages deployment, or blanket accessibility certification. No app code changed.
Implementation summary/source trace: [W1_SUMMARY.md](W1_SUMMARY.md).

## Observed results

| Check | Result |
|---|---|
| Static file set | Exactly one HTML, one CSS, one SVG; no JS/package/build runtime |
| Home structure | One h1, six feature articles, hero/CTA/course/footnote, ten-link header in each responsive variant, Privacy footer |
| Duplicate ids / anchors | No duplicates; #main and #course resolve |
| Original identity | favicon.svg byte-identical to docs/brand/icon/codec-mark.svg |
| External resources | Zero external src/srcset/poster/link targets; no CSS @import/url(), scripts, frames, objects, media or event handlers |
| Browser network | Exactly index.html, style.css, favicon.svg from local server; zero external requests |
| Responsive widths | 360 / 390 / 768 / 1100 / 1440 px, document scrollWidth equals viewport at every width |
| Mobile menu | Native summary Enter opens/closes; ten links; no JS required |
| Console | No page errors at all five widths |
| Automated accessibility | axe-core WCAG2A/AA, WCAG21AA, WCAG22AA tags: **0 violations** at all five widths |
| Offline | Fresh file:// load with network OFF and JS OFF, dark stylesheet applied, local SVG loaded, full-page render succeeds |
| Visual review | Full-page screenshots inspected at 360 and 1440; no overlapping main content or cropped page edges; textual illustration explicitly labelled |
| Copy law | No private developer contacts, banned privacy claim, script, claimed autonomy, old APK/-74% pairing in website |
| Outbound hyperlinks | All six unique outbound href URLs HTTP **200** via GET; table below |
| Whitespace / scope | git diff --check clean; only website/, web_docs/, web_prompt.md allowed |

The original diagnostic also enumerated offscreen descendants inside CLOSED
native details. Those are hidden contents, not visible page overflow. Actual
page-width assertion passes; further open-menu/reflow checks are recorded in
this file's follow-up section when run.

### Outbound GET results

| URL | HTTP |
|---|---|
| https://github.com/pabi277/CodeC | 200 |
| https://github.com/pabi277/CodeC#readme | 200 (fragment is client-side) |
| https://github.com/pabi277/CodeC/releases | 200 |
| https://github.com/pabi277/CodeC/issues | 200 |
| https://github.com/pabi277/CodeC/blob/main/docs/guides/DATA_AND_PRIVACY.md | 200 |
| https://github.com/pabi277/CodeC/blob/main/docs/journal/JOURNEY.md | 200 |

Local index/style/favicon HTTP 200; Home uses the existing root repo as site-source
link until website/ is merged, rather than linking an absent main/website path.

### Explicitly deferred internal links — NOT passing links

| Target | Phase |
|---|---|
| install.html, start.html | W2 |
| engines.html, packages.html, faq.html, about.html, ai.html, privacy.html | W3 |
| learn.html | W4 |

These **nine unique URLs return 404** on the W1-only server. This is the inherited
W1 final-URL rule, not a test failure concealed by placeholder pages. Notice at
top, note under cards, course availability and footer label disclose it. No
course/chapter was built, no full link-sweep or deployment pass claimed. W6 may
not retain any of these missing paths. Chapter group preview is not a fake
clickable chapter list.

## Tooling and reproducibility

Static server: `python -m http.server 8000 --bind 0.0.0.0 --directory website`.
It is only a preview tool, no production backend. Browser tooling installed
**outside Git/site** in `/home/user/.cache/codec-web-tools`:
Playwright + axe-core/Playwright + @sparticuz/chromium (Chromium 153.0.8010.0).
Normal Playwright CDN download and apt chromium were unavailable; npm-distributed
headless binary worked after extracting its bundled runtime libraries. Disabled
web-security/insecure-content flags from serverless defaults were removed.
Single-process default crashed across contexts; removed, checks re-run successfully.
No weakening of a website assertion to pass tooling failures. No dependencies,
logs, browser binary, screenshots or caches committed to the website.

Browser procedure:
1. Create independent context at each width with reduced-motion preference.
2. Load local HTTP index; record requests and page errors; screenshot full page.
3. Assert document scrollWidth == innerWidth; run axe WCAG A/AA tags.
4. At ≤1100 focus native summary and press Enter, assert visible ten-link nav;
   press Enter again, assert hidden. Desktop uses full nav.
5. Create a **fresh** context with JavaScript disabled and offline true; load
   file:///…/website/index.html; assert computed background rgb(11,16,14) and
   image complete/naturalWidth>0; full-page screenshot.

Minimal repeatable static gate (from repo root, no dependencies):

```python
from html.parser import HTMLParser
from pathlib import Path
from urllib.parse import urlsplit
import re
root = Path('website')
class Scan(HTMLParser):
    def __init__(self):
        super().__init__(); self.resources=[]; self.ids=[]; self.links=[]
    def handle_starttag(self, tag, attrs):
        a=dict(attrs)
        assert tag not in ('script','iframe','object','embed','video','audio')
        assert not any(k.startswith('on') for k in a)
        if 'id' in a: self.ids.append(a['id'])
        if tag == 'a': self.links.append(a['href'])
        for k,v in attrs:
            if k in ('src','srcset','poster') or (tag=='link' and k=='href'):
                self.resources.append(v)
s=Scan(); s.feed((root/'index.html').read_text())
assert len(s.ids)==len(set(s.ids))
assert all(not urlsplit(v).scheme and not v.startswith('//')
           and (root/v).is_file() for v in s.resources)
assert not re.search(r'@import|url\s*\(', (root/'style.css').read_text(), re.I)
assert all(x[1:] in s.ids for x in s.links if x.startswith('#'))
assert (root/'favicon.svg').read_bytes() == Path('docs/brand/icon/codec-mark.svg').read_bytes()
missing=sorted({x for x in s.links if not x.startswith(('http','#')) and not (root/x).exists()})
assert missing == ['about.html','ai.html','engines.html','faq.html','install.html',
                   'learn.html','packages.html','privacy.html','start.html']
print('W1 static PASS; nine known future pages remain deferred')
```

W2+ must update expected deferred paths; W6 must assert **none**. The static gate
is not an HTML/CSS validator, a package inventory check or a substitute for browser
rendering. No support for a browser is claimed solely from a Chromium check.

## CI / device / deployment boundary

Existing main app CI 37505147370 and release publish 37505175304 were green before
this change. Session-branch Build APK (automatically triggered by push) is tracked
in NEXT_STEPS / final report; it is not the website's Pages proof. Website Pages
workflow and full deploy test are W6 only. No workflow manually dispatched.
**Device pass required for W1: no.** W5 ch08 and W6 P1/P5 remain owner gates.

## Final interaction / reflow follow-up

Re-run after the text/touch-target refinement: 320/360/720/1110/1440 CSS-px
viewports. Zero **visible** offscreen elements (checkVisibility filters native
closed-details descendants). Skip link is first Tab and Enter focuses main at
every width. Open mobile menu at320/360/720 has no width overflow and zero axe
A/AA violations. Reduced-motion yields scroll-behavior:auto. Fresh offline/no-JS
360px context opens the mobile menu successfully. 720px is additional reflow
coverage, not a claim of a separate real-browser zoom or Android device round.
Final three-file footprint **40,398 bytes uncompressed** (HTML16,092 +CSS22,624
+SVG1,682); no assets generated/loaded remotely. Current Markdown link targets
checked (excluding explicitly historical bodies): no missing local targets;
phase-doc recount39=6+33. No Pages/full-site acceptance claimed.
