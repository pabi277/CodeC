# Full website review — verification record

**2026-10-07 · W3–W6 combined content batch, D33.** These are local source,
syntax, host/fixture and browser results. They are NOT Android device acceptance,
manual screen-reader certification, owner visual acceptance or public deployment.
The final source is29 HTML pages + CSS + SVG,404,036 uncompressed bytes.

## Final checks

| Check | Actual result / evidence |
|---|---|
| Static shape, local resources, chrome, chapter navigation, unique IDs, anchors | PASS;31 files,29 pages,1,065 local links/anchors,0 missing; [STATIC_REPORT.json](STATIC_REPORT.json) |
| Configured package inventory | PASS;33 website roots equal CODEC_REPOSITORY_PACKAGES; not live installability |
| HTML Validate11.16.2 | PASS all29 HTML files, no disabled rules added |
| Chromium153.0.8010.0 / Playwright1.63.0 | PASS29×4 widths320/360/768/1440 =116 cases; [BROWSER_REPORT.json](BROWSER_REPORT.json) |
| Responsive/keyboard/reduced motion | No document overflow; skip→main; keyboard menu open/close; all guide disclosures open during axe/reflow; reduced scroll motion |
| axe4.13.0 checks |0 reported violations across116 cases, WCAG2/2.1/2.2 AA tags; automated results, not full conformance certification |
| Browser runtime resources/errors |0 external requests;0 page errors; no author JavaScript, third-party fonts, analytics or CDN assets |
| Extracted ZIP offline, JavaScript disabled | PASS29 file:// pages: local CSS/icon; native menu; navigation to another extracted page; disclosure on linked guide;0 HTTP requests |
| Web lesson fixtures | PASS normal JSON+counter, invalid JSON error state, ES-module import+counter; all requests intercepted locally, no network service or provider |
| Host/compiler/example preflight |49 PASS; [SNIPPET_REPORT.json](SNIPPET_REPORT.json). ANSI C90 compile/output, Python fixture/output/error, JavaScript syntax, Bash syntax, make, P5 stub control flow |
| Final ZIP | PASS CRC, safe paths, exact file list and byte comparison with website/, extraction; [W6_ARCHIVE.json](W6_ARCHIVE.json) |
| Earlier cumulative snapshots | W3/W4/W5 CRC/hash/path/asset checks PASS; [SNAPSHOT_REPORT.json](SNAPSHOT_REPORT.json). Historical pre-polish views, later-page links explicitly missing there |
| `.codec.json` sample | Source checked in CodecJsonParser.kt: build/run string fields recognized; `make`/`./hello` matches the accompanying Makefile, whose host build/run passes |

**Final ZIP:**134,916 bytes; SHA256
`312d6c1c62bece7c7b9f338c5c250b9c87e50e7e7affecc2db48270b20e2b289`.
It contains the final website, not documentation records or test dependencies.
The packaging script uses deterministic timestamps; per-file hashes are recorded.
GitHub upload is verified AFTER the single batch push; this local record cannot
pre-certify remote bytes or future CI. Final handoff supplies the pinned link.

## Failures found and fixed before the final passing run

- HTML validator found literal ampersands; corrected entity encoding.
- A broad code formatter accidentally escaped Home's real highlighting spans.
  The browser caught an oversized/unfocusable code surface; restored the canonical
  Home figure from HEAD and added a static regression assertion. No test weakened.
- The host fixture parser initially included whitespace outside `code` inside
  `pre`, changing sample line counts. Fixed extraction, not expected output.
- Privacy at320px overflowed by4px because a long provider hostname was plain text.
  Text-range diagnosis identified it. Added prose `overflow-wrap:anywhere`, not
  hidden overflow or relaxed bounds. The full final sweep passed after the fix.
- Readable chapter/number prose spacing was corrected, then static/examples/HTML,
  ZIP and the complete browser sweep were rerun against the final source.

Screenshots were generated for every page at360/1440. Representative Learn/AI/C/
project crops were inspected; screenshot production is not a claim of exhaustive
manual visual review. Actual phone zoom, assistive technology and Android RUN
behavior remain unobserved. No device logs were fabricated.

## Reproduce without changing the app

From repository root:

```sh
python3 web_docs/chat-web6/checks/check_site.py
python3 web_docs/chat-web6/checks/check_examples.py
# Use an external tooling folder, not website/node_modules:
/path/to/web-tools/node_modules/.bin/html-validate website/*.html
python3 web_docs/chat-web6/checks/package_review.py
# Serve website/ separately on port8000, bound to0.0.0.0 for a browser preview.
WEB_TOOLS=/path/to/web-tools \
OFFLINE_ROOT=/tmp/codec-website-full-extracted \
node web_docs/chat-web6/checks/browser_check.mjs
```

Browser tooling used: playwright1.63.0, @axe-core/playwright4.13.0,
@sparticuz/chromium153.0.0, html-validate11.16.2. Harness expands the Lambda Linux
libraries for this sandbox. Output lives in WEB_TOOLS/full-site-results; copy the
completed browser-report.json to this record only after exit0. Site itself has
no tool dependency or build step. Host checks require cc, make, Python, Node, Bash;
temporary files/stubs are isolated and removed. P5 stubs do NOT install/upgrade
packages or invoke a phone API. Do not run authoring generators over polished HTML.

## Still open

- [Chapter8 + P1/P5 Android transcripts](DEVICE_TESTS.md), including genuine output,
  compiler path/ABI, permission/API/audio behavior and honest upgrade-path status.
- Owner review of the full ZIP; actual phone accessibility/zoom/long-session reading.
- O1 screenshots/O2 domain/O3 tone/O5 in-app link/O7 course license decisions.
- Explicit PR/merge and package-safe deployment authorization. Existing /dev and
  /keys hosting and every workflow are untouched.
- CI for the single batch commit is watched after push. No manual dispatch,
  Android/Gradle sandbox build or claim that APK CI executes these web tests.

External destinations are the same source/release links inventoried during W2:
11 unique GitHub pages were GET200 there; F-Droid page retrieval succeeded via
page-fetch but direct sandbox TLS failed. Those historical checks do not guarantee
future availability. Opening outbound links is optional and needs internet.
