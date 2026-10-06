# NEXT_STEPS.md — website head state

> **2026-10-07 · v2.3 + D33 · All29 pages IMPLEMENTED FOR REVIEW.**
> **Not fully accepted, merged or deployed.** W5 Chapter8 and W6 P1/P5 device
> transcripts are still required. Session: `arena/8b8f8edc-codec`; app/main source
> unchanged at d4231f0. Do not start unrelated app work or deploy the site.

## Latest owner direction

The owner asked to finish the remaining website in one go, **then one commit and
one CI watch**, and selected **Keep device verification gates**. D33 supersedes
separate W3–W6 start commands and intermediate commits, not source verification,
D31 downloads, device tests or PR/merge/deploy authorization. W4.2 facts were recorded
FIRST, then product/course content, ch18/19 before polish.

**D31 remains mandatory:** every website phase/batch handoff includes a verified
full-current-site ZIP and a direct GitHub download link, not an Arena-only card.
[Required procedure](REVIEW_DOWNLOAD_RULE.md).

## Download and review now

**[Full29page website ZIP](https://raw.githubusercontent.com/pabi277/CodeC/arena/8b8f8edc-codec/web_docs/chat-web6/review-zips/CodeC-website-W6-review.zip)**

31 files at archive root:29 HTML pages, style.css, favicon.svg. Exact size/hash in
[W6_ARCHIVE.json](chat-web6/W6_ARCHIVE.json). Import into CodeC Projects → + →
Import ZIP → open index.html → RUN. Keep lesson exercise projects separate from
this documentation project. Local pages/assets need no internet; outbound links do.
The final handoff uses a commit-pinned URL after GitHub uploaded-byte verification.

[Batch summary](chat-web6/BATCH_SUMMARY.md) · [Checks](chat-web6/CHECKS.md) ·
[Content trace](chat-web6/CONTENT_TRACE.md) ·
[W4.2 verified facts](chat-web4/VERIFIED_FACTS.md) ·
[Owner device-test instructions](chat-web6/DEVICE_TESTS.md).

W1/W2 ZIPs remain historical, as do the cumulative W3/W4/W5 pre-polish snapshots
listed in [ARCHIVES.json](chat-web6/ARCHIVES.json). They are not the latest site.

## Current gate table

| Scope | State |
|---|---|
| W1 + W2 | Previously delivered; updated shared availability/chrome in the batch |
| W3 | Six guides written; product9/9; sources and local checks recorded |
| W4.2 |43 source-backed fact groups,33 package roots,19 chapters locked BEFORE writing course |
| W4 content | Course home + ch01–06 written |
| W5 content | ch07–12 written; **ch08 owner device pass OPEN** |
| W6 content/polish | ch13–19 written; full29page local sweep; **P1/P5 owner passes OPEN** |
| W6.7 deployment | **HELD / NOT STARTED**; no authorization, device gates open |
| Review acceptance | Owner feedback pending; course license O7 pending |

## Checks and CI boundary

Static/resource/chrome/link checks, HTML validation, browser responsive/axe/offline
checks and example host/stub checks are recorded under chat-web6. No full device
acceptance is inferred. Final site has **zero missing internal targets**; unlike
historical previews, all19 chapter links exist. Long code/tables scroll internally.

The **single batch commit** triggers existing Build APK automatically; observe its
actual run after push, then report tip/run/conclusion and the verified direct ZIP
link. This pre-push record does not invent a future run id or green result. Use the
final handoff / GitHub Actions for that run. No Android/Gradle runs in sandbox,
manual workflow dispatch, package build or website publish was performed.

## Verified baseline and history

- This batch began from W2 **8467ff8**; snapshot's stale HEAD was recovered with
  fetch + reset --mixed, preserving all work and restoring a clean baseline.
- W2 source/ZIP commit8467ff8 was already pushed. Its GitHub ZIP API readback was
 25,119B and exactly matched local bytes; direct sandbox raw GET had TLS EOF.
  Its CI run37528087517 was in progress when the owner stopped the watch; no
  GitHub run was cancelled. Recheck live state rather than infer its conclusion.
- W1 source0d16467 / CI37518457228 success; ZIP follow-up47c09a6 /37524577493 success.
- Main d4231f0, PR116 merged, app-v1.3.18 published: one universal APK7,217,532B,
  versionCode22, SHA256549a8c54cf9ff3fff4f6d985997bbb887f6b3b3098f4ab6ba68fefe7028a60d8.
- App Phase96 shipped; L13 postponed/L14 unauthorized; next app phase97, not in scope.
- Open PR42 (old website) and PR83 (app bookkeeping) were untouched; no session PR.

## What to do next

| Owner provides | Agent does |
|---|---|
| Review feedback | Fix only reported/verified web issues, repeat affected checks, refresh full ZIP/link |
| Chapter8/P1/P5 transcripts | Record actual results, diagnose failures, retain untested rows; never turn expected output into a pass |
| Explicit acceptance or waiver | Record the exact scope; do not infer waiver of unrelated gates |
| PR / merge command | Verify current tip/checks and existing-PR constraints, act only on literal authorization |
| Deployment command after gates | Reverify ownership, preserve signed package /dev and /keys; propose additive artifact safely before publishing |

## Carry-forward laws

- Static/self-dependent29page site; no runtime assets outside website/, no framework,
  build requirement, backend, CDN, analytics, embeds or secret collection.
- W3 inventory33 build roots is not a live installability count. OpenSSH is deferred;
  chapter12 uses curl/wget/HTTPS. Device API inventory11; percentage key; TTS rejects
  oversized payloads. Current Git Credentials is in panel ⋮, not old Settings-only UI.
- Auto engines only; no Open Folder, engine picker, mandatory first-run tour or
  automatic Linux setup. Output temporary versus named output and user .gitignore respected.
- AI BYOK/eight tools/Apply+Run/D6/nine controls/S9/provider retention laws unchanged.
- Website scope only website/, web_docs/, web_prompt.md. No app/docs/workflow/package
  edits. Current session branch only; no force push or other branch; no PR/merge without command.
- Existing Pages serves packages at /dev and /keys. Never replace it with a
  website-only artifact. Deployment prep remains paused; public website not live.
- Open O1 screenshots, O2 domain, O3 tone, O5 app link, O7 course license. O4/O6/O8 closed.

History: [WEB_JOURNEY.md](WEB_JOURNEY.md). Operating manual: [../rule.md](../rule.md).
