# Organized website verification — 2026-10-07

Final source:30 HTML pages,35 files,568,549 uncompressed bytes. No app build or
public indexing test performed locally. Checks below are actual completed results.

| Check | Result |
|---|---|
| Recursive static links/resources | PASS1,105 internal links/anchors,0 missing targets,0 orphan pages |
| Structure | Home is the only root HTML file; guides, nested chapters, about/legal and assets in folders;30 routes |
| Shared chrome | Same header/footer after relative-path normalization; active section preserved; Privacy everywhere; chapter17→18→19→course |
| Removed admin notices | No FULL-SITE REVIEW, Review status, stale device-pending notice or review anchor remains in website HTML |
| SEO metadata |30 unique titles, descriptions, canonicals; matching social metadata;30 JSON-LD documents parse with expected types and URLs; visible/schema breadcrumbs agree |
| Sitemap | Exactly30 canonical URLs with explicit content-modification dates; no duplicates or package dev/keys entries |
| Example preservation | Every original preformatted example matches source802532d byte-for-byte across all29 original pages |
| Host examples |49 checks PASS, including C90 outputs, Python, JS/Bash syntax, make, P5 local stub error/consent cases; not Android tests |
| HTML Validate11.16.2 | All30 pages PASS; no suppressed rules added |
| Browser | Chromium153.0.8010.0, Playwright1.63.0, axe4.13.0;30×4 widths320/360/768/1440 =120 cases PASS |
| Responsive/a11y |0 page overflow and0 axe violations at tested widths; native menu keyboard open/close; skip→main; reduced motion; guide disclosures expanded for checks |
| Offline ZIP |30 extracted file:// pages, JavaScript disabled, offline; CSS/icon/menu/navigation work,0 HTTP requests; original page reflow checked |
| Browser resource discipline |0 unexpected external requests,0 page/HTTP errors; only JSON-LD scripts, no executable site JavaScript |
| Web lessons |3 browser fixtures PASS: normal JSON/counter, invalid JSON error state, ES module; intercepted local requests only |
| Deployment-prefix routes | Local /CodeC/ preview:30 public-canonical route shapes +5 assets all HTTP200 and exact source bytes; includes trailing-slash indexes |
| Packaging | CRC/safe paths/exact recursive file list/extracted byte comparison PASS; no flattening, dependencies, secrets or docs in ZIP |
| SEO helper | Refresh idempotence PASS; no source/ZIP changes after full test snapshot |

Machine evidence: [STATIC_REPORT.json](STATIC_REPORT.json),
[BROWSER_REPORT.json](BROWSER_REPORT.json), [SNIPPET_REPORT.json](SNIPPET_REPORT.json),
[HTTP_REPORT.json](HTTP_REPORT.json), [ARCHIVE.json](ARCHIVE.json).

**ZIP:**200,433B; SHA256
`3a6395737e0d6d38a5c4a430dc88268a7494cad00a4a8facda2a84f43ebfb636`.
The manifest contains all35 file hashes. GitHub uploaded-byte verification follows
the single follow-up push; final report pins that commit. This record cannot invent
a future remote-readback or CI result.

## QA findings

The first browser run passed all120 responsive and30 offline cases, then caught a
harness-only error: the extra asset request test used `chromium.request` instead
of the package's `request` export. Fixed the import/call, not an assertion; reran
the whole sweep to completion including assets and the3 web fixtures. No failing
site behavior was hidden. No additional Android test requested because the lesson
code is unchanged and the prior device results stand.

Screenshots generated at360/1440 for each page. Home desktop, comparison mobile
and the local share-card image inspected; readable dark/green design retained,
no review banner. This is not exhaustive manual accessibility/zoom certification,
a Google Rich Results test, Search Console verification or proof of search ranking.

## Reproduce

Tools live outside the site/repository dependency tree (WEB_TOOLS). No website
build step or runtime dependency is introduced.

```sh
python3 web_docs/chat-web7/checks/check_site.py
python3 web_docs/chat-web7/checks/check_examples.py
/path/to/web-tools/node_modules/.bin/html-validate 'website/**/*.html' website/index.html
python3 web_docs/chat-web7/checks/package_review.py
# Separate long-running preview process, binds 0.0.0.0:8000:
python3 web_docs/chat-web7/checks/preview.py
# In another shell:
python3 web_docs/chat-web7/checks/check_http.py
WEB_TOOLS=/path/to/web-tools OFFLINE_ROOT=/tmp/codec-website-seo-extracted \
  node web_docs/chat-web7/checks/browser_check.mjs
```

Browser helper uses playwright1.63.0, @axe-core/playwright4.13.0,
@sparticuz/chromium153.0.0 with its sandbox libraries. Optional share-card helper
uses sharp and the existing project mark, not an external stock image. Screenshot
outputs stay in WEB_TOOLS/seo-site-results. Copy a completed browser report only
after exit0. Historical chat-web6 harnesses target the old flat snapshot; use the
new recursive/path-aware harnesses for this tree.

Required owner device checks: **PASS**, Android16/aarch64; app “latest”, exact
build unspecified. [Actual rounds1–3](../chat-web6/DEVICE_ROUND_1.md). Permission,
ABI, other Android-version and arbitrary-input coverage are not generalized.
No workflow/app/package changes, PR, merge, release or public deployment. Existing
Build APK is the automatic post-push CI executor; its result is separate from all
local web evidence above.
