# W1 — fresh scaffold and Home, 2026-10-07

## Owner command

> “You can watch the pr 42 for reference but you have build it with updated docs
> and more powerful website”

Interpreted and stated before edits as **Build the website = Start W1**, not all
six phases and not permission for PR/merge/deploy. Branch arena/8b8f8edc-codec,
base d4231f0. W1 implementation/static checks complete; review/merge gate held.
Full v2.3 reconciliation: [chat-web3/SUMMARY.md](../chat-web3/SUMMARY.md).

## Evidence before implementation

Read rule.md, web docs entry points, W1 README + PARTs, actual chat-web2 history,
README first, AI/privacy/BETA guides and relevant source records. Mirror phase
ceremony per docs/README + HOW_TO_CREATE_A_PHASE without modifying app history.
Live GitHub verification: main d4231f0, PR116 merged, release universal APK
7 217 532 B/versionCode22; main CI 37505147370 and tag publish37505175304 success.
PR115 actual merge0edf4dd. Open PR42 and83 left untouched.

PR42 body and Home inspected via gh API at 6f6a3cf. Useful reference: simple
static chrome, hero/features/learning structure, final relative URLs, zero resource
dependencies. Not reused: old checkbox nav, stale install/updater/engine copy,
17-chapter assumptions, old hand-drawn mark, old W2 pages. No code imported or
branch switched. No Termux source fetched/copied. Deployment note preserved:
existing package Pages endpoints must survive W6 (no deployment now).

## What changed / existing feature served / invariant

| Change | Serves | Invariant |
|---|---|---|
| Three-file site, single CSS, native details nav | Product website scaffold | Static locked stack, no external resources/JS/backend |
| Dark green editorial Home, semantic headings/skip/focus | Discoverability, mobile/keyboard reading | Source-backed, AA checks; no fabricated screenshot |
| Labelled hello.c/terminal example | Built-in offline C | ANSI C, cc=TCC, -o last, ./ explicit, no live execution implied |
| Six cards + AI and safety | Current shipped app story | BYOK/approvals/D6, Auto-only ABI truth, signed packages, no hidden defects claims |
| 19-chapter grouped preview | Learning wing | Planned not available, ch18/19 included, no unbuilt course claimed |
| Current version/size + honest consent strip | Install decision and trust | 7 217 532 B not -74%, direct provider not false never-leaves claim |
| Docs reconciliation, four new specs | Correct continuation | Preserve old histories, date actual work, W4/W5/W6 gates intact |

Only index/style/favicon implemented. No AI service/search/theme toggler/newsletter
or simulated app controls added for the sake of looking “powerful”. No screenshot
without O1 approval. The example is ordinary selectable HTML, not generated art.

## Claim traceability (read at base d4231f0)

Line numbers refer to unchanged app sources; web docs are distilled, not copied.

| Home section / claim | Source |
|---|---|
| Hero C Android IDE, Python/Node optional, real terminal/web preview | README.md:7–13, 63–107 |
| Primary GitHub Releases CTA, universal APK/updater | README.md:35–61; live gh release app-v1.3.18 (one asset 7 217 532 B; body versionCode22 + SHA256) |
| C card: built-in TCC offline, Auto, arm64/x86_64 qualifier | README.md:67–84; BETA.md:39 (B-8, 32-bit absent) |
| Textual example, cc -o last / ./ | README.md:93–104 (command form); new ANSI C puts example and literal expected output, not an app UI claim |
| Editor: icons, highlighting, inline suggestions, snippets/Emmet/autosave/projects | README.md:164–207, 218–249; docs/phases/03-editor/chat-phase34/README.md:1–5, 24–55, 58–76 (Seti file icon scope) |
| Terminal/web/LAN: VT/ANSI, sessions, preview, opt-in sharing | README.md:86–107, 255–259; docs/phases/01-terminal-userland/chat-phase36/README.md:3–37; docs/journal/JOURNEY.md:1132 onward (§50/51), docs/phases/07-platform-capabilities/chat-phase37/README.md:3–31 |
| Package examples/signed repository/live badges/not forced setup | README.md:105–127, 150–158; no exact enumerated package count claimed on Home |
| AI card BYOK/eight tools/read-only/request-run/Apply/Run/D6 | README.md:277–319; docs/guides/AI.md:1–205, specifically §§1/5/6/7/9/10 |
| Safe workflow export-all/project backups/crash-loop/checksum/honest Git | BETA.md:36–39, 67–73; DATA_AND_PRIVACY.md:231–244; docs/phases/08-release-support/chat-phase42/PART_42_3_LAUNCH_SAFETY.md:12–49; README.md:54–59, 198–202 |
| Consent strip: no CodeC server, task reads after Send, separate Apply/Run | DATA_AND_PRIVACY.md:56–109; README.md:279–315; never promises zero provider retention |
| Course title/count/groups/upcoming state | Owner supplied v2.3 D28 scope; WEBSITE_PLAN.md §3.2; actual no chapter files in W1 |
| Original >_ mark | docs/brand/icon/codec-mark.svg byte-for-byte copy; no app assets modified |
| Footer open-source and site tracking claim | README/actual website has no JS, storage or external resource requests; “site” claim not external provider guarantee |

The icon/terminal/LAN claims require the guides/journal/phase detail after README;
not every shipped feature is repeated in root README. Package inventory and all
chapter step validation remain W3.2/W4.2, not falsely completed during W1.

## Delivery and checks

- Canonical [chrome/style contract](W1_CHROME.md); shared components for later pages.
- [W1_CHECKS.md](W1_CHECKS.md): static sweep, browser widths, keyboard, axe,
  offline file render with JS/network off, external GETs, known deferred paths.
- Exactly **one of 29 HTML pages**; nine distinct future linked pages deliberately
  missing per phase rule and visibly labelled. These are not “all links green”.
- Main app checks verified; session push triggers existing Build APK automatically.
  Follow its actual run in NEXT_STEPS/final report; no website workflow until W6.
- No device pass needed for W1. W5 ch08 and W6 P1/P5 owner transcripts not waived.

## Decisions / rejected alternatives

- Adopt stronger layout/type/source story, not framework or backend.
- Native details instead of old aria-hidden checkbox; two CSS-exclusive nav
  variants with same ordering and active state.
- No screenshot or screenshot-like phone mockup: labelled code illustration only.
- No direct APK URL, auto-download or analytics. Canonical Releases only.
- No W2 import, placeholder pages, intercepting links or fake course availability.
- Copy tone friendly-technical provisional; O3 not silently closed.
- No Pages change; respect signed-package deployment and owner gate.

## Next

Owner reviews W1. Fix W1 feedback if requested; otherwise **Start W2** authorizes
Install + Start. No automatic phase advancement; no PR creation, merge, PR42
closure or deployment without a separate command. Open O1/O2/O3/O5/O7 remain.

## Phone-only review download — 2026-10-07 follow-up

The owner's screenshot showed the archive in Arena's **changes list**, without
a usable per-file download. Earlier direct sandbox URL also returned403: missing
traffic access token. Do not ask the owner for a token or repeat that direct URL
as a normal public preview. Existing code is unchanged; Pages preparation is
**paused** on the owner's request to review downloaded files before merging.

A small review artifact is now intentionally checked in alongside this record:
[CodeC-website-W1.zip](CodeC-website-W1.zip) (11,400 bytes). It contains exactly
index.html, style.css and favicon.svg at archive root, byte-for-byte from website/
on W1 commit0d16467. CRC/readback checks passed; no app/repo contents included.
Download from the session branch on GitHub (raw URL), then on phone:
CodeC → Projects → + → Import ZIP → open index.html → RUN. No merge/deploy/server
is needed. Only Home is implemented; the nine future page URLs remain deferred.

Archive is a **W1 review snapshot**, not the canonical source or a build step.
Future code edits must refresh it if offering it again, or label it historical.
Review source CI37518457228 was re-verified success on0d16467. This archive/docs
follow-up triggers normal branch CI automatically; no manual dispatch, no PR,
merge, Pages setting/publication or app workflow change.
