# NEXT_STEPS.md — website head state

> **Owner law D31:** every website phase ends with a verified full-site ZIP and a
> direct GitHub download link in the final reply. [Required procedure](REVIEW_DOWNLOAD_RULE.md).
> **W2 authorized (D32)**; W3–W6 still need their own commands. No PR/merge/deploy.

> **2026-10-07 · v2.3 · W1 + W2 IMPLEMENTED, local website/ZIP checks PASS; NOT
> merged or deployed. W3–W6 NOT STARTED.** Session `arena/8b8f8edc-codec`.
> Current website: Home, Install, Start + style.css/favicon.svg; **3/29 pages**.
> W2 was explicitly authorized (D32); D31 makes full-site download mandatory at
> every phase end. No next phase/PR/merge/deploy authority imported from that.
>
> **Current review:** [W2 summary + ZIP](chat-web5/W2_SUMMARY.md),
> [checks](chat-web5/W2_CHECKS.md), [source ledger](chat-web5/W2_SOURCES.md).
> The commit carrying this update includes the exact five-file W2 archive.
> Observe its automatic Build APK CI after push and verify uploaded ZIP bytes
> before reporting handoff complete; do not infer CI success from web checks.
>
> **Planning:** 9 product + course home +19 chapters; **39 docs** (6 phase
> READMEs +33 PARTs), parts2/2/6/8/6/9. W1 remains in chat-web4; W2 in chat-web5.

## Current download — W2

**[Download full W2 website ZIP](https://raw.githubusercontent.com/pabi277/CodeC/arena/8b8f8edc-codec/web_docs/chat-web5/CodeC-website-W2.zip)**

25,119 B; five files at root; CRC/tree-byte and extracted offline browser checks
PASS. [Manifest](chat-web5/W2_ARCHIVE.json). Download → CodeC Projects → + →
Import ZIP → index.html → RUN. The final handoff should use a commit-pinned URL.
Website works offline; outbound links need internet. Seven future local page
paths remain explicitly unavailable. Historical W1 ZIP is not the current site.

## Historical W1 phone-review solution — 2026-10-07

**Deployment preparation PAUSED.** The owner wants to review downloads before
merge. Arena's raw sandbox URL requires a traffic-access header, and the phone
changes panel didn't expose an individual ZIP download. Use the GitHub-hosted
[W1 review ZIP](chat-web4/CodeC-website-W1.zip): just index/style/favicon, no code
changes; import ZIP into CodeC and RUN index.html. Canonical source remains website/.
The snapshot matches **0d16467**, whose Build APK **37518457228 is success**.
No PR/merge/publish authorization; the earlier preparation-only choice is paused.

## Verified GitHub baseline (re-check before next work)

- Remote main/app base **d4231f0**; PR #116 merged, release app-v1.3.18 published.
- Main Build APK **37505147370 success**; release publish **37505175304 success**.
- One universal APK **7 217 532 B**, versionCode **22**, published SHA256 present.
- PR #115 actual merge **0edf4dd**, not earlier main baseline 1c6f910.
- App Phase96 complete; Level13 postponed, Level14 unauthorized; next app phase97.
- Open PRs observed: **#42 old W1+W2** and **#83 app bookkeeping**, both untouched.
  No new PR from this session. Do not import PR42's code or authority implicitly.
- This delivery's session Build APK runs automatically on push; its live result
  belongs to the final report / follow-up here. No manually dispatched build,
  publication, package build or website deployment.

## W2 checks / honest limits

Three pages ×320/360/390/720/768/1110/1440px: no overflow, zero axe violations,
keyboard skip/menu/disclosures, reduced motion, no external resource requests.
HTML Validate clean; local links/anchors/chrome pass; extracted ZIP offline/no-JS
render and navigation pass. Eleven GitHub destinations GET200; F-Droid retrieved
through page-fetch after direct TLS EOF. Seven unbuilt target paths stay disclosed.
No Android device or Pages pass. Existing W1 archive follow-up CI
**37524577493 / 47c09a6 success** rechecked; W2 commit CI is observed after push.

## Historical W1 checks / honest limits

Responsive at 360/390/768/1100/1440; keyboard mobile menu; automated axe WCAG A/AA
0 violations; no console errors/page overflow; local-file offline render with JS
and network off; browser loads only Home/CSS/mark. Six unique outbound URLs GET200.
Nine future internal page targets return404 until W2–W4 (explicit notice on Home),
not counted as passing links. Full 29-page/link/Pages sweep is still W6.
No Android device pass claimed or required for W1.

## What the owner says next

| Owner says | Agent does |
|---|---|
| Review feedback on current W2 ZIP | Revise only authorized W1/W2 pages, repeat affected checks, refresh full ZIP + download link, update records and push session branch |
| **Start W2** | Already received and implemented; do not redo without a reported regression |
| **Start W3** … **Start W6** | Verify predecessors; execute one phase strictly per amended v2.3 specs; don't skip order/gates |
| Answers O1/O2/O3/O5/O7 | Dated decision + affected living specs; O5 app link needs separate app authorization |
| **Create PR and merge** / explicit equivalent | Re-verify tip/checks and existing open PR constraints, act only on authorized session work; never silently merge/close PR42 |

## Queue

- [x] **W0.4 reconciliation** — performed Oct7 from supplied Oct6 scope, no fictitious prior commit.
- [x] **W1 implementation/static gates** — fresh Home and scaffold, source trace, local browser/offline checks. Owner review/merge pending.
- [x] **W2 implementation/local gates** — Install + Start, current release/first-run facts, full-site ZIP. Owner review pending; final upload/link and CI observed after push.
- [ ] **W3** — Engines/Packages/FAQ/About/AI/Privacy, product9/9.
- [ ] **W4** — W4.2 FIRST: package/source/AI/current-filesystem Verified Facts Table,
  technically lock 19 chapters; learn + ch01–06. O6 owner scope already closed.
- [ ] **W5** — ch07–12; **ch08 device pass required** (owner transcript).
- [ ] **W6** — ch13–17 → **W6.8 ch18 + W6.9 ch19 before polish/deploy**;
  **P1+P5 device pass required**; 29-page sweeps before live, package-safe Pages.

## Important carry-forward corrections

- No engine picker / Termux card; Auto fallback with Output Panel setup.
- No Open Folder (+ has New/Clone/Import ZIP); cancelled Phase43 ProjectLink/safe
  walk proposals are not shipped; W4.2 re-verifies actual filesystem teaching.
- First run intro → privacy acknowledgement → CodeC Arcade, no mandatory tour,
  old first-hour tiles, automatic Linux download or install navigation lock.
- AI BYOK, eight bounded tools, no direct write/exec, Apply/Run approval, D6
  ephemeral chat versus bounded persisted task-memory/undo; nine controls/S9.
- Privacy guide claim ceiling; no never-leaves-device claim or public contacts.
- Current APK 7 217 532 B; historical -74% only v1.3.17.
- **Pages risk:** existing signed package repo serves /dev and /keys. W6 must
  verify/preserve them, not replace whole Pages artifact with website alone.
  No branch-source setting for arbitrary /website; additive artifact plan needs
  safe ownership proof. Stop if it requires forbidden workflow changes.
- Historical phase bodies are retained under explicit prior-design labels; current
  top amendments/master plan control. Never treat old counts/UI as current.
- Session numbering: chat-web2=W0.3, chat-web3=reconciliation,
  chat-web4/W1_*=W1; chat-web5/W2_*=W2. Preserve existing filenames; future W4 VERIFIED_FACTS
  can coexist. W6 verification records still go in chat-web6.

## Open owner items

**O1** screenshots, **O2** custom domain, **O3** tone, **O5** in-app link,
**O7** course license. **O4/O6/O8 closed**, do not re-ask. No unapproved images
or course license invented in W1.

Timeline: [WEB_JOURNEY.md](WEB_JOURNEY.md). Operating manual: [../rule.md](../rule.md).
