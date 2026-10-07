# NEXT_STEPS.md — current website delivery and maintenance

**2026-10-07 · D36–D38 · 30 pages / 19 chapters / 38 website files.**
Owner authorized course licensing, completion, one current-session PR, merge and
website deployment. The only current PR is [#117](https://github.com/pabi277/CodeC/pull/117)
for this workstream; older PR42/83 remain untouched. Work stays on the assigned
`arena/8b8f8edc-codec` branch. No app/runtime/signing changes.

## Current deliverables

- Website: **https://pabi277.github.io/CodeC/**; course: **https://pabi277.github.io/CodeC/learn/**.
  Publication is performed and publicly verified by the main-only Pages workflow;
  consult [its runs](https://github.com/pabi277/CodeC/actions/workflows/website-pages.yml)
  and PR117's delivery record for the actual deploy/merge result, not a local preview.
- [Current implementation and checks](chat-web9/SUMMARY.md).
- [Safe deployment/rollback procedure](deploy/README.md).
- [License scope](../website/learn/licenses/SCOPE.txt): original course content
  CC BY 4.0, original code/shell examples MIT. Full texts ship offline. **O7 closed**;
  this does not relicense the app, third-party material, implementation or marks.
- Redesigned README and disclosed concept artwork: [D35 record](chat-web8/SUMMARY.md).

## Current full-site ZIP — D31

**[Download the licensed website/course ZIP](https://raw.githubusercontent.com/pabi277/CodeC/7254304296c5799ab97b103086deadf9fb305413/web_docs/chat-web9/CodeC-website-licensed.zip)**

38 files, **209,200 bytes**, SHA-256
`c8761230c75acbcc1342ed2784ee1728d6f7c34f40905b1149d0f3b505311cbb`.
[Exact manifest](chat-web9/ARCHIVE.json). GitHub contents API readback matched the
committed ZIP byte-for-byte at7254304. This archive supersedes chat-web7/6 for
current downloads; old snapshots and their original evidence remain historical.

Phone: Projects → + → Import ZIP into a **new project** → preserve folders →
open root `index.html` → RUN. Keep lesson practice files in separate projects.
The website/course/license texts work offline; outbound links need internet.

## Confirmed checks

- 1,139 internal links/anchors; zero missing/orphan pages; 30 valid HTML documents
  with unique metadata/JSON-LD/canonicals and 30 sitemap URLs.
- 120 responsive/axe cases, 30 offline/no-JS pages, three local browser fixtures
  and 38 local HTTP exact-byte routes. Accepted lesson examples remain unchanged.
- 14 deployment-safety tests pass. Corrected package suite:94 tests, no local
  failures, four signer-dependent local skips; full runner suite passed in
  [package CI37589923026](https://github.com/pabi277/CodeC/actions/runs/37589923026).
- [Read-only Pages preflight37589923128](https://github.com/pabi277/CodeC/actions/runs/37589923128)
  passed on7254304: recovered and signature/hash-verified the actual public package
  repository, added the website without changing package/key bytes. Branch deployment
  correctly skipped. No package rebuild or signing operation was dispatched.
- Build APK is required on the final PR head. Main's normal Build APK and Pages
  deployment/public-byte checks are separately observed after merge. Actual results
  are retained in PR117's delivery record; don't invent future success here.
- [Owner device rounds1–3](chat-web6/DEVICE_ROUND_1.md): required Chapter8/P1/P5
  gates passed on Android16/aarch64, including audible speech and approved package
  maintenance. App reported as “latest”, exact build unspecified. No repeat needed.

## Publishing and preservation

The new workflow preserves the existing signed `/dev` repository and `/keys`, checks
pinned-key signatures, signed indexes and all package hashes, refuses unsafe paths,
then composes the site. Main only publishes; a shared non-cancelling concurrency
lock serializes the website and package publishers. The package publisher retains
the website on future updates. Do not run an older publishing branch that predates
this integration. Recovery stops safely if live package files cannot be verified.

After successful deployment, all website and protected package bytes are verified
via public HTTP and the result is saved as an Actions artifact. Hosting success
is not a search-indexing or ranking guarantee. Host-root robots coordination and
Search Console/Bing ownership/submission remain separate, unperformed work.

## Next owner-led work

Finish observing the authorized PR117/main pipeline if it is still running;
otherwise wait for the next owner request. Do not restart completed phases/tests.
Optional actual screenshots O1, copy-tone feedback O3 and an in-app link O5 remain
separate requests. O2 address, O4 ordering, O6 chapter set, O7 licensing and O8
contact handling are resolved. No further feature, PR, release, package rebuild
or SEO-account action is automatically authorized by this handoff.

## Boundaries

Static HTML/CSS and local assets; no framework/browser build, CDN, analytics or
executable site JavaScript. Current narrow scope includes the root README/assets,
website/docs, additive Pages integration and the two explicitly approved package
test fixtures. App source, package runtime/build/signing recipes, keys and Build APK
workflow remain unchanged. Follow [rule.md](../rule.md), decisions D36–D38 and the
[download rule](REVIEW_DOWNLOAD_RULE.md). Update the full ZIP after any site edit;
keep prior history and snapshot reports attached to their original commits.
