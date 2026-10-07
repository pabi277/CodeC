# Search readiness and publication checklist — 2026-10-07

**Prepared, not deployed or indexed.** Owner selected the planned public base
`https://pabi277.github.io/CodeC/` (D34). This does not authorize a Pages deployment,
PR or merge. Existing signed package `/CodeC/dev` and `/CodeC/keys` stay untouched.

## Implemented on all 30 pages

- Unique descriptive titles and meta descriptions, English language and viewport.
- Absolute self-canonicals at the owner-selected base. Directory index pages use
  their trailing-slash public URL; offline navigation still names `index.html`.
- Matching Open Graph and Twitter card metadata with a local 1200×630 PNG, size and
  image-alt metadata. No externally loaded fonts, images, analytics or JavaScript.
- Non-executable JSON-LD: WebSite, WebPage/LearningResource and BreadcrumbList;
  homepage SoftwareApplication and course-index Course with its 19 lessons.
  Structured data describes visible content; no fake ratings, reviews, awards,
  endorsement, invented authors, sales prices or rich-result eligibility promises.
- Visible breadcrumbs, descriptive chapter filenames, contextual internal links;
  every page reachable by regular HTML anchors with JavaScript disabled.
- XML sitemap containing exactly the30 canonical content URLs. `last_modified`
  records the actual content/layout update date in SITE_MAP.json; do not advance
  dates merely because a script ran. No invented priority/change-frequency scores.
- Generic crawl stanza in robots.txt; its host-root limitation is described below.

## Termux-related search intent, without keyword stuffing

`guides/codec-vs-termux.html` is an original, readable comparison: C IDE versus
terminal-first environment, setup/offline trade-offs, package isolation, supported
fallback, useful questions and links to both projects. Linked from Home, Install,
Engines, Packages and FAQ. It is not a doorway page that repeats the homepage.

No misleading “official Termux” branding, affiliation claim, hidden text, repeated
keyword lists, meta-keywords tag, fake reviews or claim to replace every Termux
capability. Termux is discussed where it helps a reader choose a workflow; its name
is not appended to unrelated chapter titles. Ranking for “Termux” is competitive
and cannot be guaranteed. Nor is a Google/Bing listing implied by valid markup.

## Required publication work — NOT performed

1. Obtain explicit publication/PR/merge authority and resolve remaining owner
   decisions, including course licensing. Coordinate the existing package Pages
   artifact: NEVER replace it with a website-only artifact or overwrite dev/keys.
2. Serve this exact tree at the selected `/CodeC/` base. Verify Home, nested guides,
   chapters, CSS/icon/card, sitemap, successful HTTP status and correct content types.
   Recheck real public URLs with no login requirement, accidental noindex header,
   robots block or canonical pointing to a preview/incorrect domain.
3. **Robots location:** crawlers read `https://pabi277.github.io/robots.txt`, not
   `/CodeC/robots.txt`. The file included in this ZIP is a documented stanza/template,
   not a claim of effective crawl control from a project subfolder. Coordinate any
   root-host change with its owner; preserve unrelated rules/sites. If the host root
   returns404 there is no robots restriction by that fact alone. Submit the sitemap
   directly; do not alter another repository or root-host configuration implicitly.
4. Verify the owner's site property in Google Search Console and Bing Webmaster
   Tools through their official interfaces. Do not request credentials/tokens in
   chat, invent verification metadata or add an unowned IndexNow key.
5. Submit `https://pabi277.github.io/CodeC/sitemap.xml`; inspect the homepage and
   comparison page with URL inspection. Check rendered HTML, selected canonical,
   crawl/indexing errors and structured data using the engines' validation tools.
   Local JSON parsing/axe tests are NOT a Google Rich Results or live indexing pass.
6. Once live and authorized, link from the project's public repository/site profiles
   and relevant genuinely helpful content. Monitor impressions/query data and fix
   real usability/content problems. No ranking deadline or position guarantee.

The local preview server alone adds `X-Robots-Tag: noindex`. That header is not in
website/ and must not be copied to the eventual public server. The shipped page
metadata is prepared for indexing, but this branch ZIP is not a deployment.

## Maintaining static metadata

The browser does not need a build. Complete HTML/CSS/images/XML are checked in.
`SITE_MAP.json` records legacy→new paths, titles, descriptions, visible breadcrumb
labels, actual modified dates and the selected base. After a factual content/URL
change, update the relevant records then run:

```sh
python3 web_docs/chat-web7/checks/refresh_seo.py
python3 web_docs/chat-web7/checks/check_site.py
```

This optional authoring helper refreshes checked-in metadata and sitemap; repeat
runs with unchanged input are byte-identical. Do not republish a domain change
without checking every canonical, social URL, JSON-LD URL and sitemap location.
Final rendered content and metadata must agree. Regenerate/recheck the ZIP after
any site edit. Preserve folder names and case when importing the ZIP.

## Sources consulted for this change

- [Google SEO Starter Guide](https://developers.google.com/search/docs/fundamentals/seo-starter-guide):
  descriptive URLs, helpful unique content, contextual links, title/description,
  canonicalization; no expectation of guaranteed placement.
- [Google robots.txt specification](https://developers.google.com/crawling/docs/robots-txt/robots-txt-spec):
  host-root scope; robots in a subdirectory is not a controlling robots file.
- [Official Termux README](https://github.com/termux/termux-app#termux-application):
  Android terminal/Linux environment; packages separate; device/Android caveats.
  This is a factual citation, not copied website source or a Termux endorsement.
- CodeC README, current W4.2 facts and product/source ledgers: built-in TCC ABIs,
  opt-in userland, automatic compiler fallback, package-prefix isolation, BYOK and
  privacy. No application capabilities changed in this website update.
