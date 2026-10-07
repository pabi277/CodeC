# Website cleanup, folders and SEO — 2026-10-07

Owner: “Ok all good now remove this FULL-SITE REVIEW … Review status … make
folder subfolder … add seo … if someone search for Termux the CodeC also come”.
Then selected **GitHub Pages URL**: `https://pabi277.github.io/CodeC/`, metadata only,
no publication. D34 supersedes the flat layout and visitor review notices, not
privacy disclosures, technical limits, course license or deployment authorization.

## Delivery

- Removed every FULL-SITE REVIEW banner, Review status link/section and stale
  owner-device-pending prose. Public lessons retain ordinary practice instructions,
  expected-versus-actual distinctions and safety/permission cautions.
- Course device gates PASSED from owner rounds1–3, Android16/aarch64. CodeC version
  reported only as “latest”; exact installed build not inferred. The same commit
  also preserves those previously local evidence records. No tests fabricated.
- Organized **30 pages / 35 files** (original29 plus one useful comparison guide):

```text
website/
├── index.html
├── guides/
│   ├── install.html, start.html, engines.html, packages.html
│   └── ai.html, faq.html, codec-vs-termux.html
├── learn/
│   ├── index.html
│   └── chapters/01-getting-started.html … 19-ai-tools-and-approvals.html
├── about/index.html
├── legal/privacy.html
├── assets/
│   ├── css/style.css
│   └── images/favicon.svg, social-card.png
├── sitemap.xml
└── robots.txt
```

All HTML navigation/assets remain relative and explicit-file based for offline
ZIP imports. Public canonicals use the selected absolute base. Home is the only
root HTML file. Old flat paths were branch-only review files, not an authorized
published site; no root redirect stubs or duplicate indexed content added.
[SITE_MAP.json](SITE_MAP.json) records every move. Historical ZIPs stay unchanged.

## SEO and content

Unique titles/descriptions; canonical, Open Graph/Twitter image metadata; local
1200×630 share image; structured breadcrumbs visible and machine-readable;
WebSite/WebPage/LearningResource, SoftwareApplication and Course JSON-LD;
30-URL sitemap; robots stanza with documented host-root limitation.

The comparison honestly explains CodeC and Termux workflows, common questions,
optional fallback and package separation, linking primary sources and relevant
CodeC guides. No fake affiliation, rankings, ratings or keyword stuffing. All
original preformatted code examples are unchanged byte-for-byte. No app, package,
root README, app docs, workflows, `/dev` or `/keys` hosting modified.

[SEO_HANDOFF.md](SEO_HANDOFF.md) covers source evidence, exact maintenance commands
and future Google/Bing submission. Neither live indexing nor rank for “Termux” is
claimed. Website deploy, Search Console verification and sitemap submission have
NOT happened. A project-path robots file alone does not control host crawlers.

## Download / D31

[Download the complete organized website ZIP](https://raw.githubusercontent.com/pabi277/CodeC/arena/8b8f8edc-codec/web_docs/chat-web7/CodeC-website-organized-SEO.zip)

[Archive manifest](ARCHIVE.json) records the exact current size/hash and all files.
Import into a **new** CodeC project: Projects → + → Import ZIP → root index.html →
RUN. Preserve subfolders; do not merge into the old flat website project. Keep
coding exercises separate so their index.html/style.css cannot overwrite the site.

Final handoff pins the new commit and verifies GitHub uploaded bytes. Existing
W6 ZIP at802532d remains the old flat29page review, not this deliverable.

## Verification and boundary

[CHECKS.md](CHECKS.md), STATIC_REPORT.json, SNIPPET_REPORT.json and BROWSER_REPORT.json
record final results, not estimates. App CI runs automatically after the single
website follow-up commit/push; its observed run/conclusion belongs in the final
handoff rather than an invented future success in this pre-push record.

Remaining: explicit PR/merge/package-safe publication authority, course license
O7 and any separately requested screenshots/app link. O2's initial public URL is
now selected. No broad Android-version/ABI, accessibility or ranking guarantee.
