# web_docs/ — CodeC website history and planning

> **Owner law D31:** every website phase ends with a verified full-site ZIP and a
> direct GitHub download link in the final reply. [Required procedure](REVIEW_DOWNLOAD_RULE.md).
> **D33 authorizes the remaining-site batch**; device gates retained. No PR/merge/deploy.

> **2026-10-07 · v2.3 · All29 pages written for review; W5/W6 device acceptance and deployment still held.**
> This folder is the website's equivalent of `docs/`. App code and app history
> remain read-only. Start with [`../rule.md`](../rule.md), then this file,
> [`WEBSITE_PLAN.md`](WEBSITE_PLAN.md), [`DECISIONS.md`](DECISIONS.md), and
> [`NEXT_STEPS.md`](NEXT_STEPS.md). Handoff: [`../web_prompt.md`](../web_prompt.md).

## File map

| File | Purpose / update when |
|---|---|
| [WEBSITE_PLAN.md](WEBSITE_PLAN.md) | Master spec v2.3: 9 product pages + course home + 19 chapters = **29 pages**; sources, content/self-dependent law, phase sequence, acceptance |
| [DECISIONS.md](DECISIONS.md) | Dated decisions D1–D33; never delete old decisions, supersede them explicitly |
| [REVIEW_DOWNLOAD_RULE.md](REVIEW_DOWNLOAD_RULE.md) | D31: mandatory full-site ZIP + direct GitHub link at EVERY phase exit |
| [chat-web6/BATCH_SUMMARY.md](chat-web6/BATCH_SUMMARY.md) | Current complete29page review, full ZIP, local checks and remaining device/deploy gates |
| [chat-web4/VERIFIED_FACTS.md](chat-web4/VERIFIED_FACTS.md) | W4.2 source gate, recorded before course authoring |
| [chat-web5/W2_SUMMARY.md](chat-web5/W2_SUMMARY.md) | Historical W2 Install/Start and five-file ZIP |
| [NEXT_STEPS.md](NEXT_STEPS.md) | Current state, evidence, next owner command |
| [WEB_JOURNEY.md](WEB_JOURNEY.md) | Append-only narrative; prior W0 entries kept |
| [web-phase1/](web-phase1/README.md) … [web-phase6/](web-phase6/README.md) | **39 docs (6 phase READMEs + 33 PART docs)**: parts by phase 2/2/6/8/6/9, each with implementation steps and numbered exits |
| [chat-web1/](chat-web1/SUMMARY.md) | Original W0 planning; historical, not rewritten |
| [chat-web2/](chat-web2/SUMMARY.md) | Actual W0.3 v2.2 sync (2026-09-12); not W1 despite old spec pointers |
| [chat-web3/](chat-web3/SUMMARY.md) | v2.3 reconciliation, actually performed **2026-10-07**, not claimed to have landed Oct 6 |
| [chat-web4/W1_SUMMARY.md](chat-web4/W1_SUMMARY.md) | This W1 implementation; source trace, tests, deferred paths, reference PR findings |
| [chat-web4/W1_CHROME.md](chat-web4/W1_CHROME.md) | Canonical copy-paste chrome + CSS component contract |
| [chat-web4/W1_CHECKS.md](chat-web4/W1_CHECKS.md) | Static/browser/offline/link evidence; not an Android device pass |

`web-phaseN` means **phase specs**, `chat-webN` means **session records**. Do not
reuse a session filename destructively. W4's `VERIFIED_FACTS.md` coexists
with W1-prefixed records; W6 checks still live in `chat-web6/`.

## Ground rules

1. No PR/merge without the owner's literal command; current Arena branch only.
2. Website never modifies app code, `docs/`, package config, Gradle, scripts or APK
   workflows. W6 alone permits an additive website Pages workflow and root README
   site link; existing signed package deployment must be preserved.
3. Append history, update living summaries/specs; do not fabricate past sessions
   or claim check results without evidence.
4. Clean-room: CodeC README first, then TROUBLESHOOTING, BETA, AI,
   DATA_AND_PRIVACY, RELEASE_NOTES, JOURNEY. Termux public structure only.
5. D33 authorized all remaining content in one batch, one end commit/watch.
   Device gates and explicit PR/merge/deploy commands remain; PR42 code was not imported.
6. **Self-dependent:** browser loads only files inside `website/`. Plain HTML/CSS,
   no build step, system fonts, no CDN/analytics/embeds; outbound links allowed.
7. v2.3 truth: shipped through Phase 96 / app-v1.3.18, cancelled plans excluded;
   19 chapters, AI nav, Privacy every footer, contacts in-app only, honest AI
   consent and storage boundaries. O4/O6/O8 scope closed, W4.2 source verification recorded; owner device gates still open.
