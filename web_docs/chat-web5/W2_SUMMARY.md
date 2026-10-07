# W2 — Install + Getting Started, fresh implementation

**2026-10-07 · v2.3 · IMPLEMENTED, local web/ZIP checks PASS.**
Owner review pending. No PR created, merge, release or deployment. W3 not authorized.
The existing Build APK workflow runs on the phase push; see the final report / GitHub
Actions for that commit's result. This record does not pre-claim a future CI result.

## Authorization and verified baseline

Owner explicitly said **“Now start w2”** (D32), and made the downloadable phase ZIP
mandatory (D31). See [the binding rule](../REVIEW_DOWNLOAD_RULE.md).

Baseline **47c09a6** on `arena/8b8f8edc-codec`. The snapshot's stale git index/HEAD
was reconciled to the fetched session tip with **reset --mixed**, preserving the
working files; tree was clean before edits. No checkout/branch change/hard reset.
Remote main remains **d4231f0**; open PRs #42 (old website) and #83 (app records)
untouched. Existing checks: W1 source **0d16467 / 37518457228 success**;
W1 phone-review follow-up **47c09a6 / 37524577493 success**, rechecked this phase.

## Delivered scope

- **Install:** recommended signed universal release first, release/developer/update
  path cards, exact v1.3.18 digest/size, phone steps, updater guard/checksum,
  export before changing signing channels, honest Android/ABI limits, optional
  Termux-only setup disclosure and real privacy/source links.
- **Start:** current intro/privacy/Arcade, optional Linux, five practical steps:
  hello.c/RUN, terminal cc and ./a.out, scanf input, Python package, HTML/LAN.
  Complete code examples and expected output; five-tab map and final cross-links.
- **Home:** availability notice now says Home/Install/Start; first-program CTA
  points to the working Start page. Other guide/course links remain visibly
  upcoming. Small validator-driven semantics fixes, not a Home redesign.
- **Shared CSS:** guide hero, readable 760px prose, sticky desktop contents,
  mobile contents, release facts, scrollable code/table, native disclosures,
  tab map, install routes, next steps and source notes. No author JavaScript.
- **Docs:** D31/D32, download procedure, phase-law banners in all six phase READMEs,
  W2 acceptance/source/check records and updated living handoff/queue.

The website now has **3 of 29 HTML pages**, five files total. No app source,
app docs, package config, Gradle, scripts, APK workflow or deployment edits.

## Download the full W2 review site

**[Direct W2 ZIP download](https://raw.githubusercontent.com/pabi277/CodeC/arena/8b8f8edc-codec/web_docs/chat-web5/CodeC-website-W2.zip)**

[GitHub file page](https://github.com/pabi277/CodeC/blob/arena/8b8f8edc-codec/web_docs/chat-web5/CodeC-website-W2.zip)
· [Archive manifest](W2_ARCHIVE.json)

- **25,119 bytes**, five files at archive root: index.html, install.html,
  start.html, style.css, favicon.svg. Uncompressed **87,177 bytes**.
- SHA256: `29bc163b428c92a766c7c5407516676de9b154f42e3e5888504681df5ec42b89`.
- CRC, safe paths and exact file-list/byte comparison with website/: PASS.
- Extracted ZIP rendered and navigated offline with JavaScript disabled: PASS.
- GitHub uploaded-file readback must be checked after push before final handoff;
  use a commit-pinned download URL in the final reply. Do not claim that a local
  ZIP verification proves the uploaded/public bytes until that readback is done.
- W1 ZIP is unchanged and remains historical; do not offer it as the current site.

**On the phone:** download → CodeC Projects → + → Import ZIP → open index.html →
RUN. Keep all five files in the same project. On a computer, extract and open
index.html. Local pages/CSS/icon need no internet; outbound GitHub links do.

## Checks and honest limits

[W2_CHECKS](W2_CHECKS.md), [source ledger](W2_SOURCES.md), retained browser report
and rerunnable check scripts contain the evidence. Three pages × seven widths
320–1440, WCAG automated checks, keyboard/focus, local-only requests, extracted
ZIP offline/no-JS, HTML validation and example output all checked.
Seven unique unbuilt link targets remain disclosed (not passing pages): Engines,
Packages, AI, Learn, FAQ, About, Privacy. No full-course, full-site, live-Pages or
Android device acceptance claim. Screenshots in sandbox are website QA, not app
screenshots or O1 approval. External F-Droid direct TLS failed in sandbox; the
page-fetch service successfully retrieved its Termux page.

## Numbered W2 exits

1. **Install at 360/1440:** release/developer/updater routes in order; all detailed
   topics and architecture table present. Checksum and Termux disclosures open
   with native keyboard controls. No engine picker or Settings Termux card.
2. **Commands/current facts:** APK filename/digest/size release-verified; Terminal
   compile/run matches README, output option last; Termux commands match README
   and are expressly not for CodeC. Old -74% / 6.6 MB facts not copied.
3. **New-reader install trace:** Releases → app-v1.3.18 → named universal APK →
   Android unknown-app permission → install/open; debug switch branches to export
   first. Source walkthrough, not a performed Android installation.
4. **Five-step Start path:** first launch agrees with README; hello and scanf
   examples have complete code/expected output; terminal ./ and input rules stated;
   package and HTML/LAN are practical, optional setup is not a first-run blocker.
5. **Crosslinks/sweep:** all built local links/anchors pass; final future URLs are
   explicitly upcoming. No loaded external files, JS, fonts or frameworks.
6. **Docs/phase scope:** source lines/sha in this new record, v2.3 amendments applied,
   no protected/app changes. Next phase and merge remain owner gates.
7. **D31 delivery:** exact full-site ZIP prepared and extracted-browser-tested;
   push/readback/final clickable link required before the handoff is complete.

## Shared chrome update for later phases

Continue copying header/footer from current index.html, changing only active nav.
Footer remains byte-identical on all three pages; only Home/Install/Start are built.
The W2 validator normalized HTML doctype case, made Home's figcaption the final
figure child, removed an unnecessary dl aria-label, named guide sidebars and used
native section for the accessible table region. Those are the only incidental
W1 semantic adjustments. Apply W3's Privacy availability change to all pages.
