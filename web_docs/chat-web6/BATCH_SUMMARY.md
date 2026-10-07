# Remaining website batch — W3 through W6 content/review

> **Historical flat29page snapshot.** The later folder/SEO update is in
> [chat-web7](../chat-web7/SUMMARY.md), with the current30page ZIP. Device evidence
> linked here remains valid; old archives/manifests are not relabelled as current.

> **Required owner device checks PASSED (2026-10-07, rounds1–3).**
> Chapter8/P1/P5 passed, including audible speech and consented maintenance.
> Android16 / aarch64; CodeC reported as “latest” (exact build unspecified).
> No retest needed. Website review/license and PR/merge/deploy authority remain
> separate. [Actual evidence and compiler diagnostic](DEVICE_ROUND_1.md).

**2026-10-07 · v2.3 + D33 · All29 pages written.** This is a **review edition**,
not a deployed or fully design-approved website. Required W5 Chapter8 and W6
P1/P5 owner device checks have now passed (rounds1–3). No PR, merge, release, Pages deployment or in-app
link change was authorized or performed.

## What changed in the owner's instructions

The owner stopped the W2 CI watch and asked to finish the full website together,
then commit/watch once. The clarification choice was **Keep device verification
gates**. D33 therefore authorizes the remaining page content/polish in one batch,
not more Android work or a deployment. Separate phase-start commands and
intermediate commits are superseded; source verification and device gates are not.

The old watcher process was absent after workspace restoration; no GitHub run was
cancelled. Snapshot git HEAD was stale at d4231f0 with W2 files present; fetch plus
**reset --mixed** to **8467ff8** restored a clean, correct baseline without touching
working files. Remote main d4231f0 and open PR42/83 were verified and left alone.

## Delivery

- W3: six complete product guides—Engines, Packages, FAQ, About, AI, Privacy.
  Product wing is9/9. FAQ preserves B1–B8; AI/Privacy include all tools, controls,
  caps, approval/storage/recipient distinctions and named proving sources.
- W4: **verification first**, then course home + Chapters1–6. The43-row
  [Verified Facts Table](../chat-web4/VERIFIED_FACTS.md) was written before course
  files.33 curated package build roots are checked against configuration.
- W5: Chapters7–12, including the ten-section conservative C chapter/capstone,
  shell/Python/Git and the verified networking variant. Ch08 device gate PASSED (owner rounds1–2).
- W6 content: Chapters13–17 plus **18/19 before polish**. Real device APIs;
  four-file web example; advanced tools; all five real projects; troubleshooting;
  optional AI first-question and approval/refusal lessons. P1/P5 device gates PASSED (owner rounds1–3).
- Shared polish: all29 pages in the existing W1 dark/green/system-font design,
  chapter index, prev/next path17→18→19→course, source/privacy links, truthful
  review notices, no obsolete upcoming labels on final pages, keyboard-accessible
  code/table scrolling, no author JavaScript or external assets.
- W6.7 deployment remains **HELD / NOT STARTED**. Existing signed package /dev and
  /keys endpoints and all workflows are untouched. O7 course license is pending.

Website scope is **29 HTML pages + style.css + favicon.svg =31 files**.
The examples are code printed on a documentation site, not a live in-page compiler
or chatbot. No key entry form, remote font, analytics, CDN or framework.

## Source corrections that matter

[Full trace](CONTENT_TRACE.md). OpenSSH is deferred; chapter12 uses curl/wget and
HTTPS rather than inventing an SSH installation. API inventory is11 scripts;
battery uses percentage; oversized TTS is rejected. Current Git Credentials lives
in Source Control’s menu. Current Settings shortcut labels are source-backed.
Temporary app-run artifacts and named user outputs are distinguished. Shared-file
permissions do not resurrect deleted Open Folder UI. Raw AI chat is not persisted;
bounded task memory/undo is separate. No autonomy, per-read-consent fiction or
provider zero-retention promise.

## Downloads — D31 retained

**[Full29page review ZIP](https://raw.githubusercontent.com/pabi277/CodeC/arena/8b8f8edc-codec/web_docs/chat-web6/review-zips/CodeC-website-W6-review.zip)**

[GitHub file page](https://github.com/pabi277/CodeC/blob/arena/8b8f8edc-codec/web_docs/chat-web6/review-zips/CodeC-website-W6-review.zip)
· [Exact final archive manifest](W6_ARCHIVE.json)
· [Cumulative snapshots](ARCHIVES.json).

The final report must use a commit-pinned direct link after push and verify the
uploaded bytes. Local packaging cannot pre-prove a future GitHub upload. One
batch commit/push and one CI watch follow the completed website checks; CI's
actual run id/result belongs to that handoff, not an invented result in this
pre-push record. No extra commit merely to create a self-referential CI record.

Cumulative **pre-polish** review snapshots preserved inside this same batch:

- [W3 /9 pages](https://raw.githubusercontent.com/pabi277/CodeC/arena/8b8f8edc-codec/web_docs/chat-web6/review-zips/CodeC-website-W3.zip)
- [W4 /16 pages](https://raw.githubusercontent.com/pabi277/CodeC/arena/8b8f8edc-codec/web_docs/chat-web6/review-zips/CodeC-website-W4.zip)
- [W5 /22 pages](https://raw.githubusercontent.com/pabi277/CodeC/arena/8b8f8edc-codec/web_docs/chat-web6/review-zips/CodeC-website-W5.zip)

These are historical intermediate views, with later chapter links unavailable;
the final31-file ZIP is the authoritative polished review. W1/W2 archives remain
unchanged. Use the newest full ZIP for feedback, not an earlier snapshot.

**Phone:** download → CodeC Projects → + → Import ZIP → index.html → RUN.
Keep the31 files together. Make lesson projects separately so your exercise's
index.html/style.css do not overwrite the documentation site. External links need
internet; all website pages/assets and code examples are local.

## What happens next

1. Owner reviews the complete ZIP and reports design/content issues.
2. Preserve [actual device evidence](DEVICE_ROUND_1.md): required Chapter8/P1/P5
   checks passed. No retest needed; exact installed CodeC build remains unspecified.
3. Fix reported failures for cause, refresh the full ZIP and recheck affected gates.
4. Resolve outstanding owner decisions and explicitly authorize any future PR/merge
   and package-safe deployment. Do not publish merely because all pages exist.
