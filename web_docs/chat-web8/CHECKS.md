# README validation

## Results

- GitHub Markdown API (`mode=gfm`, `context=pabi277/CodeC`): successful sanitized
  rendering of the actual README, including images, tables and five disclosures.
- Static preflight: 29 local link/image references resolve, 16 heading slugs checked
  for local navigation, both images have alt text; no missing local target.
- Small C example: host compiler with `-std=c90 -pedantic -Wall -Wextra -Werror`
  compiles successfully and produces the exact documented two output lines.
  This is not a fresh Android/ABI acceptance test.
- Chromium: eight cases, 320/360/768/1440 CSS px × light/dark, JavaScript disabled.
  Both images load at their correct dimensions, Quick start navigation works,
  all five native disclosures open with Enter, and the document has no horizontal
  overflow before/after opening them. Tables/code may scroll within their bounds.
- Visual inspection: composited hero plus desktop-light/mobile-dark crops; brand
  and text remain clear. On narrow phones the hero is decorative; the actual title,
  product explanation, links and every essential instruction remain ordinary text.
- Four axe scans (360/1440 × light/dark) recorded in BROWSER_REPORT.json. This local
  GitHub-style wrapper is NOT GitHub's full repository page: its external CSS lacks
  full-page link underlining and its API tables/code lack host keyboard-scroll
  enhancements. `link-in-text-block` and `scrollable-region-focusable` findings are
  retained, not hidden or called passes. Actual host behavior needs live/manual
  review; no WCAG conformance certification is made. Removed a needless `text`
  fence label after the API emitted invalid `lang="text"` markup.
- Protected trees (`website`, `app`, `.github/workflows`, `codec-packages`, `docs`,
  `scripts`, `gradle`) have no changes from baseline 0715ab8. No website ZIP rebuild
  or repeat device gate. `git diff --check` clean.

External URLs are inventoried, not all fetched. The app release was inspected via
GitHub; the pinned website ZIP retains the previous exact-byte remote verification.

## Reproduction

Use an external scratch directory for npm packages and browser screenshots; no
node_modules, generated full-page screenshots or raw PNG intermediates are shipped.
Requires Python 3, host `cc`, Node, authenticated `gh`, and scratch installations of
`playwright`, `@sparticuz/chromium`, `@axe-core/playwright`, `github-markdown-css`.
The Chromium helper targets this Linux sandbox; use a local Playwright browser
instead on other platforms. No dependency or app build configuration was changed.

```sh
mkdir -p /home/user/.cache/codec-readme-tools
gh api markdown -X POST -F text=@README.md -f mode=gfm \
  -f context=pabi277/CodeC \
  > /home/user/.cache/codec-readme-tools/github-body.html
python web_docs/chat-web8/checks/check_readme.py
node web_docs/chat-web8/checks/browser_check.mjs
git diff --check
```

Override `README_RENDER` (static script) or `README_TOOLS` (browser script) if needed.
Browser screenshots stay in the scratch directory. STATIC_REPORT.json and
BROWSER_REPORT.json are the saved results. These are documentation checks;
Android/Gradle execution remains with the existing GitHub Actions workflow.
