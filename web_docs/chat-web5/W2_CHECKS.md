# W2 checks — 2026-10-07

**Local website preflight PASS.** Not Android CI/device certification, not a Pages
publish. Checks exercised the final site + extracted W2 ZIP before phase push.
Build APK runs automatically after push; its result must be observed, not inferred
from these checks. No Gradle/Android/Robolectric run attempted in sandbox.

## Static / semantic checks

- `python3 web_docs/chat-web5/checks/static_check.py`: PASS. Three HTML pages;
  one h1/main each; unique IDs, language/title/viewport; active page in both navs;
  shared header equal except aria-current; footer byte-identical; local resource
  paths resolve and stay inside website/; all built links and fragments resolve.
- HTML Validate recommended rules: **0 errors / 0 warnings** on all three pages.
  Initial run found eight issues: lower-case doctype (three), unnamed guide aside
  (two), div/region instead of native section, Home figcaption order and unnecessary
  aria-label on dl. Fixed the markup, not validator rules. No suppression config.
- No script/iframe/embed/base, inline handlers, srcset, JS files, CSS imports or URL
  fetches. Local stylesheet + original copied favicon only. Icon still byte-equal
  to docs/brand/icon/codec-mark.svg. No local asset references outside website/.
- Release checksum and exact **7,217,532 B** checked against GitHub release API;
  `versionCode: 22`, published 2026-10-06T17:49:02Z, one universal asset.
- C examples extracted from HTML, decoded and compiled with sandbox host `cc
  -std=c99 -Wall -Wextra -Werror`: hello output matched; input21 produced42.
  **Host-C syntax/output preflight only, NOT CodeC/TCC or Android execution.**
- Terminal command blocks keep `cc hello.c -o a.out` then `./a.out` and the analogous
  double.c pair. Termux setup block matches the README's three commands and is
  explicitly labelled Termux-only. No dot PATH, fake compiler, repository mixing
  or direct executable-download shortcut taught.
- `git diff --check`: PASS before push. Scope audit limited to website/, web_docs/
  and web_prompt.md; app/docs/workflows unchanged.

## Browser / responsive / keyboard

Chromium **153.0.8010.0**, Playwright + axe-core tooling installed in sandbox cache
outside the repo. [Re-runnable browser script](checks/browser_check.mjs) and
[retained result](W2_BROWSER_REPORT.json).

| Coverage | Result |
|---|---|
| Home, Install, Start × 320/360/390/720/768/1110/1440 px | 21 page/width checks PASS |
| Document width and visible element overflow, closed/open menus + all guide disclosures open | No page-level horizontal overflow |
| Long code / architecture table | Deliberate internal horizontal scroll, focusable for keyboard use |
| axe WCAG2/2.1 A/AA + 2.2 AA tags, all guide disclosures open, mobile menu open | 0 automated violations at all 21 combinations |
| First Tab → skip link → Enter | Focus goes to main |
| Native mobile Menu Enter/Space | Opens/closes; all 10 links visible on tested mobile widths |
| Reduced-motion preference | Computed root scroll-behavior auto |
| Browser page errors | 0 |
| External resource requests | 0 |
| Website screenshots | Full-page 360/1440 captured; Install desktop and Start/mobile plus cropped text inspected; no app screenshot invented |

Automated axe is not a comprehensive accessibility certification. No physical
phone or real browser-zoom claim. Reflow widths down to320 tested. Small-screen
code/table scrolling is intentional, not hidden content/page overflow.

## Offline — delivered ZIP, not just cached HTTP

`package_review.py` built the archive, validated CRC/safe paths/exact tree bytes
and extracted it to `/tmp/codec-website-w2-extracted`. The final browser run used
that directory as OFFLINE_ROOT:

- Fresh contexts, network **offline**, author JavaScript **disabled**, file:// URLs.
- Home, Install and Start all render styled with their local mark.
- Each page's native mobile menu works; links navigate to another built page.
- Guide disclosures open by keyboard with no JS/network.
- **Zero HTTP(S) requests**, no warmed-cache dependency.
- ZIP contains five files only; no tooling/docs/app files, no directory traversal,
  symlinks or extra enclosing folder. [Exact hashes](W2_ARCHIVE.json).

## Links

**Built local targets and anchors:** PASS by static resolution; HTTP pages returned
200 during browser tests. Home → Start and Install ↔ Start also exercised offline.

**Seven intentionally unbuilt paths:** about.html, ai.html, engines.html, faq.html,
learn.html, packages.html, privacy.html. Notices on every page + next-link labels
explain their status. They remain unavailable, NOT counted as passing full-site links.

**External:** 13 distinct hrefs, 12 unique destination URLs after removing the
README fragment. Eleven GitHub destinations returned HTTP200 using Python urllib
with normal TLS verification: repository, Actions, Issues, Releases, app-v1.3.18,
BETA, DATA_AND_PRIVACY, RELEASE_NOTES, TROUBLESHOOTING, JOURNEY and Termux releases.
F-Droid direct request failed with TLS EOF; the page-fetch tool successfully
retrieved **https://f-droid.org/packages/com.termux/** and its Termux listing.
This is retrieval evidence, not an invented sandbox HTTP200 for that request.
Outbound links need internet but are never loaded just to display the website.

## Reproduction

Run from repo root (dependencies are QA tools, never website assets/build inputs):

```sh
python3 web_docs/chat-web5/checks/static_check.py
python3 web_docs/chat-web5/checks/package_review.py
# Serve website/ with any local static server; default browser-check base is port8000.
# Install playwright, @axe-core/playwright and @sparticuz/chromium in WEB_TOOLS.
OFFLINE_ROOT=/tmp/codec-website-w2-extracted node web_docs/chat-web5/checks/browser_check.mjs
# HTML Validate installed outside the repository:
html-validate website/*.html
```

The browser script's local default HTTP endpoint is a **test-runner input**, not
browser-facing app code. Preview server binds0.0.0.0; site links/assets are relative.
The Sparticuz harness inflates AL2023 libraries and omits single-process mode for
multi-context stability; these sandbox flags have no shipped-site counterpart.

## Boundaries still open

Owner review; full29page/Pages sweep at W6; W4.2 source verification; W5 ch08 and
W6 P1/P5 owner transcripts. No new PR/merge/deployment authorization. Existing
signed package /dev and /keys deployment remains untouched. The current phase
handoff additionally needs GitHub ZIP readback and the final direct link (D31).
