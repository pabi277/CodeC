# web_prompt.md — CodeC WEBSITE handoff — v2.3

> **D34 current follow-up:**30 organized pages, no review notices, factual
> CodeC/Termux comparison and SEO. [Current record](web_docs/chat-web7/SUMMARY.md).
> Planned URL chosen: https://pabi277.github.io/CodeC/ (not deployment authority).

> **Required device gates PASSED:** [owner rounds1–3](web_docs/chat-web6/DEVICE_ROUND_1.md).
> Chapter8/P1/P5, including audible speech and approved maintenance, passed on
> Android16 / aarch64. CodeC reported as “latest”; exact build unspecified.
> Batch802532d / CI37533980679 succeeded. Review/license/deploy authority remain.

> **Owner law D31:** every website phase ends with a verified full-site ZIP and a
> direct GitHub download link in the final reply. [Required procedure](web_docs/REVIEW_DOWNLOAD_RULE.md).
> **D33: remaining content authorized as one batch; device gates retained.** No PR/merge/deploy.

> Scope supplied by the owner: **2026-10-06 v2.3**. Verified and updated for
> **the full29page review on 2026-10-07**. This is the website workstream; app handoff is `prompt.md`.
> Read the current-state correction below before trusting the original handoff's
> historical claims. Living state is updated at every website phase gate.

---

Read these first, before doing anything else, then report what you found and
the current git/PR/CI state before making any change:

1. **rule.md** — operating manual (§2 session branch/push, §3 merge gate,
   §5 CI/device, §6 invariants, §7 docs, §8 done, §9 verified snapshot).
2. **web_docs/README.md** — website ground rules and file map.
3. **web_docs/WEBSITE_PLAN.md** — master v2.3, page/chapter content (§3), content
   rules (§4), self-dependent law (§5), phases (§9), acceptance (§10).
4. **web_docs/DECISIONS.md** — D1–D34, binding dated decisions and open items.
5. **web_docs/NEXT_STEPS.md** — current head and owner-command table.

When a page needs facts: **README.md first**, then
`docs/guides/TROUBLESHOOTING.md`, `BETA.md`, `AI.md`, `DATA_AND_PRIVACY.md`,
`RELEASE_NOTES.md`, then `docs/journal/JOURNEY.md`. All app docs/code are read-only.

## Current verified state — 2026-10-07, D34

**30 organized pages; required device gates passed; no deployment/PR/merge.**
Owner accepted the tested site and requested removal of FULL-SITE REVIEW/Review
status, subfolders and stronger Google/other-engine discovery including Termux.
Selected https://pabi277.github.io/CodeC/ for planned canonicals/sitemap, not publishing.

**Current ZIP:**
[Download organized website](https://raw.githubusercontent.com/pabi277/CodeC/arena/8b8f8edc-codec/web_docs/chat-web7/CodeC-website-organized-SEO.zip).
35 files /200,433B, manifest/hash in chat-web7/ARCHIVE.json. Final handoff pins new
commit and verifies uploaded bytes. Import into NEW project, retain folders, open
root index.html → RUN. Old flat ZIPs stay historical; don't overlay an old project.

**Structure:** index.html root; guides/, learn/index.html, learn/chapters/ (19),
about/index.html, legal/privacy.html, assets/css + images, sitemap.xml/robots.txt.
Original29 pages plus CodeC/Termux comparison. Relative .html links work offline;
all original preformatted examples unchanged from802532d.
**SEO:** unique titles/descriptions, self-canonicals, local social card/metadata,
visible/schema breadcrumbs, non-executable JSON-LD,30-URL sitemap. Useful comparison
and contextual links, no fake affiliation/ratings/keywords spam/ranking guarantees.
**Robots caution:** project /CodeC/robots.txt is not host crawl control. Engines
read https://pabi277.github.io/robots.txt. Coordinate separately; no root-host or
workflow changes. Search Console/Bing verification and submission not performed.

**Evidence:** chat-web7/CHECKS.md and reports.1,105 internal targets,0 missing,
120 responsive/axe cases,30 offline/no-JS pages,49 host checks,3 web fixtures,
35 canonical/asset HTTP200 exact-source matches. No executable website JavaScript.
**Device:** chat-web6/DEVICE_ROUND_1.md rounds1–3 PASS for Chapter8/P1/P5, including
voice heard and approved package maintenance. Android16/aarch64, app “latest”,
exact installed build unspecified. Do not demand repeats or invent a version.

Previous batch802532d / CI37533980679 succeeded. Current single follow-up commit/
push triggers new automatic Build APK; its actual result and verified pinned ZIP
belong in final handoff, not an invented future CI pass here. No app/Gradle sandbox
builds or workflow dispatch. All app/docs/packages/workflows remain unchanged.

W6.7 deployment stays unauthorised; preserve signed package /dev and /keys. O7
course license remains open. O2 initial address closed by D34; future custom domain
is separate. No PR/merge/release/publication/IndexNow without explicit authority.

### Why the supplied handoff needed reconciliation

At base **d4231f0**, remote main and this clean shallow checkout agreed:

- `web_docs/` was still **v2.2**, decisions D1–D22, W0.3 timeline; **35 phase docs**
  (6 READMEs + 29 PARTs), not the claimed v2.3/37/39 documents.
- `chat-web3/SUMMARY.md`, AI/Privacy specs and ch-18/ch-19 specs were absent.
- `chat-web2/SUMMARY.md` was the actual **W0.3 sync**, not a free filename for W1.
- No website/ on main, **but open PR #42** held old W1+W2 code at
  `6f6a3cfcccb6abbe910160cb78a1a9165e565ac3` on `arena/01a062f7-codec`.
  Reviewed via gh as reference only; none of its code/commits/authority imported.
  Its known stale facts include 17 chapters, old updater wording and engine copy.
- PR #83 (app bookkeeping) also open; no PR from this session was created.

The missing v2.3 scope was reconciled **in this session, dated Oct 7**, not
misrepresented as an existing Oct 6 commit. D23–D28 record the supplied decisions;
D29–D30 record the W1 authorization and concrete findings. Historical bodies of
old phase docs remain, explicitly labelled prior v2.2 design; the binding v2.3
amendment at each top and master spec override conflicting old facts/exits.
The correct count is now **39 docs (6 phase READMEs + 33 PART docs)**, split
2/2/6/8/6/9 PARTs. Reconciliation record is `chat-web3/SUMMARY.md`, W1 records
are `chat-web4/W1_*`; do not overwrite either in a later phase.

**App head verified through GitHub:** PR #116 merged at **d4231f0**; annotated
release tag app-v1.3.18 and published release CodeC IDE v1.3.18; one asset
`CodeC-IDE-1.3.18-universal.apk` **7 217 532 B**; release body versionCode **22**.
Main Build APK **37505147370** and tag publish **37505175304** both success.
SHA256: `549a8c54cf9ff3fff4f6d985997bbb887f6b3b3098f4ab6ba68fefe7028a60d8`.
Phase 96 PR #115 actually merged at **0edf4dd**, not its earlier main baseline
1c6f910. App docs report 3 436 tests / 0 failed; that count wasn't recomputed here.
Level 13 local/on-device model postponed, nothing scaffolded; Level 14 unauthorized.
Next free app phase **97**. Re-check git/gh and app NEXT_STEPS before trusting this.

### Historical W1 phone-review follow-up — 2026-10-07

Owner paused deployment preparation and wants a download before merge. Raw sandbox
preview requires a traffic-access header; never offer it as an unauthenticated
public link or ask for tokens. Mobile Arena showed the ZIP only in the changes
list. A review copy of website/ is therefore stored at
`web_docs/chat-web4/CodeC-website-W1.zip` on the session branch for a direct GitHub
download: three files at ZIP root, import in CodeC → index.html → RUN. Snapshot
matches W1 source0d16467 (CI37518457228 success). No site/app code changed.
No PR/merge/deploy authorized. Later W2 authorization and D31 are recorded above; W1 ZIP remains historical.

## What we are building

Public website for CodeC Android C IDE, two wings:

1. **Product**, Termux public structure reference: Home, Install, Start, Engines,
   Packages, **AI**, FAQ, About, **Privacy** — **9 pages**.
2. **Learning**, owner's Termux-Mastery book structure reference:
   **Master CodeC from Zero to Advanced**, learn.html course home +
   **19** numbered chapters ch-01.html … ch-19.html, hands-on exercises.

**29 pages when complete**, fully self-dependent, no content copied from Termux.
Every session works on its assigned `arena/*` branch only. Never main/another
branch, never switch/create another branch or force push. No app changes.

## Rules and evidence files

| File | Binding content |
|---|---|
| rule.md | Branch/push discipline, no PR/merge without literal command, CI executor of record for app, fix red for cause never weaken assertions, invariants, same-commit docs |
| web_docs/WEBSITE_PLAN.md | Source chain, shipped vs roadmap, banned privacy claim, self-dependent law, 29-page scope, refusals as features, phase/acceptance gates |
| web_docs/DECISIONS.md | D3 stack, D7 location, D8 merge gate, D11 self-dependent, D23–28 v2.3 scope, D29 fresh W1/PR42 reference, D30 reconciliation/deployment safety, D31 mandatory ZIP, D32 W2 authorization, D33 single remaining-site batch/device gates |
| web_docs/README.md | No app/docs changes; preserve history; clean-room; no next phase without command |
| docs/README.md; docs/getting-started/HOW_TO_CREATE_A_PHASE.md | Mirror evidence → design → numbered exits → implementation/check record; don't write app phase docs for website work |
| docs/guides/DATA_AND_PRIVACY.md | Two claims, honest correction, permission/AI surface and named proving files; site cannot out-claim it |
| docs/guides/BETA.md | Known issues B-1…B-8; never hide shipped defects |
| docs/guides/AI.md | Shipped BYOK, eight tools, approvals, D6, S9, caps, providers, history and limits |
| docs/guides/UPLOAD_KEY_SETUP.md | Owner-only signing; never touch secrets or release workflow |
| docs/roadmaps/ai-integration/00_AGENTIC_MAP_AND_SECURITY_RULES.md | Roadmap-side; don't claim unshipped rules/features as product |
| app/proguard-rules.pro | Named proven failure before any R8 keep (read-only in this workstream) |
| app/src/main/res/xml/backup_rules.xml; data_extraction_rules.xml | Include-list-only backup law, exclude-under-include lint error |
| scripts/check_release_{version,notes,apk_set}.sh; check_icon_assets.sh | Release/tag/version/notes/universal-ABI/icon checks; read-only |
| .github/workflows/build-apk.yml | App-v* publish lane — NEVER EDIT |

## Owner commands and phase queue

D33 authorized W3–W6 content in one batch after source verification; all original29 pages
are written; D34 adds the30th comparison page and folder/SEO changes. The original phase scope below remains the acceptance map, not
a queue to re-run. Next: review/device transcripts, then explicit PR/merge/deploy
commands only. PR42 supplies no authority or implementation.

- **W1** scaffold + Home: ten-link header incl AI, Privacy footer, original >_,
  shared styles/components, six cards incl file icons/LAN/AI/safe workflow,
  learning preview. Fresh Home delivered, no app screenshot fabricated.
- **W2 (implemented)** Install + Start: shipped v1.3.18 facts (universal 7 217 532 B, versionCode
  22, checksum), updater guard, debug/release export/fresh install, automatic
  fallback; current intro → privacy acknowledgement → CodeC Arcade, optional
  userland, no old first-hour tiles/tour/install locks.
- **W3 (implemented)** Engines, Packages, FAQ, About, **W3.5 AI**, **W3.6 Privacy**. Auto only;
  package config source/sha; BETA B-1…B-8; export/backup/crash/Git/LAN/feedback;
  eight tools, approvals and limits; privacy honest correction and source files.
  After W3 product wing **9/9**. Product pages are complete for review.
- **W4 (implemented; gate recorded first)** **W4.2 verification gate FIRST**, then learn + ch-01…06. Committed
  Verified Facts Table re-checks README/package config/release/icon/backup/export/
  LAN/feedback/output/Git/AI and **Phase 46 removed Open Folder**. Lock 19-chapter
  teaching set; O6 owner scope already closed, don't re-ask count or silently cut.
- **W5 (content implemented; required device gate PASSED)** ch-07…12: editor, TCC-safe C, shell, Python, honest Git, networking/LAN.
  **Ch-08 owner device pass recorded**, actual transcript, not browser-test inference.
- **W6 (content implemented; required device gates PASSED; deploy HELD)** ch-13…17, **W6.8 ch-18 + W6.9 ch-19 BEFORE W6.6 polish/W6.7 deploy**.
  **P1+P5 owner device pass recorded**. 30-page source/resource/offline/full-link
  verification before live, evidence in chat-web6. No deployment shortcut.

**Deployment safety:** existing repository Pages serves the signed package repo
(`/dev`, `/keys`), noted by PR #42. At W6 re-verify real Pages/package workflows;
never replace package endpoints with a website-only artifact. Branch-source
settings cannot serve arbitrary /website; plan a verified additive artifact
approach without changing branches or APK/package workflows. If forbidden edits
are needed, STOP and ask. No Pages configuration/publication in this batch; deployment remains held for review/device gates and explicit authorization.

## Laws — no exceptions

- **D31: EVERY phase ends with a tested full-website ZIP and direct GitHub download
  link in the final reply.** Store outside website/, verify uploaded bytes, keep
  historical snapshots distinct. Arena attachment alone is not sufficient.

- No PR/merge without owner's literal chat command. Commit/push current session
  branch is permitted; no auto-merge authority imported from another session.
- Website never edits app/, codec-packages/, gradle*, scripts/, docs/, app tests
  or APK workflow. Only W6 can add its own Pages workflow + one root README link,
  while preserving package publishing; an in-app site link needs separate command.
- Web history append-only. Update web_prompt.md, web_docs/NEXT_STEPS.md and
  WEB_JOURNEY.md as gates close in same commit. No invented previous session or
  run evidence; distinguish local checks from CI and from owner device acceptance.
- Clean-room: distill public CodeC sources; Termux public layout/behavior only,
  never its site source, GPL code or decompiled proprietary materials.
- CI is app executor of record; no Android Gradle execution here. Website static
  checks can run locally. Full Pages build/deploy/link proof only at W6.
- Invariants: no dot on PATH; no build-package.sh -I; don't overwrite cc/real bash;
  cc is TCC, not clang symlink; TCC -o last; no com.termux packages/repos;
  bootstrap never bundled in APK; signed metadata, never trusted=yes.
- Before each change state what changes, which existing feature it serves,
  affected invariant, and source docs relied on.

## Self-dependent = law (D11)

Zero fetched external resources: no CDN, external fonts/JS/CSS/images, analytics,
third-party embeds or remote runtime. Every loaded file in website/. System
font stack. Plain static HTML/CSS; minimal vanilla JS only for a justified need
(the complete review has none). No framework/build step/backend/CMS/database.
Outbound hyperlinks allowed to stable repo/README/Releases/Issues/guides/package
URLs, and F-Droid/GitHub where README links Termux. Course completable without
opening repo; inline commands/examples/results. All pages render fully offline
from a local copy, not merely a warmed cache. SVG namespace URL isn't a fetch.
W6 sweeps src/link/@import/url and other fetch surfaces, browser network, offline
render, every link/anchor. Record truthfully; all29 final paths now exist; historical intermediate ZIPs still disclose later paths.

## Facts that must not regress

### Navigation and course

Header **Home · Install · Start · Engines · Packages · AI · Learn · FAQ · About · GitHub**.
Footer **Privacy on every page**, contextual links from About/Install/AI and FAQ.
Relative .html URLs for Pages subpath/local-file portability. No external asset.
Course title fixed, **Chapter N of 19**; goals → prerequisites → steps → try-it
(1–3, expected result) → common mistakes → prev/next. Ch-19 is completion.

- Ch-04 Auto only, no picker (21), Termux card deleted (38.2), automatic fallback,
  four setup steps in Output Panel only when needed.
- Ch-06 current New/Clone/Import ZIP; **Open Folder removed Phase 46**; tapping
  a project file differs from ⋮ → Open in editor. Phase 43 TreeWalkPolicy /
  ProjectLink/mirror proposal was cancelled, not shipped teaching material.
- Ch-07 official Seti MIT icons, typing feel, ghost TAB ▸, strip ƒ/λ/≠, snippets
  29 MIT packs, Emmet, TextMate Dark+, CodeC Keys optional/system keyboard default.
- Ch-08 ANSI C / demonstrated TCC coverage only, no C11-only samples; device pass.
- Ch-11 GitReadiness, true local-versus-remote result, publish private by default.
- Ch-12 networking/LAN, ch-13 actual verified codec-* scripts (don't assume old
  conflicting counts), ch-14 web/LAN, ch-15 backup/export/crash/feedback,
  ch-17 BETA/crash-log/feedback → ch-18 (not completion).
- Ch-18 Ask about your code: BYOK, first question, exact preview/timeline,
  session history. Ch-19 Agent, tools and approvals: eight tools/caps/refusals,
  Apply/Run, nine controls/S9, Undo, redaction → Back to course home.

### Install and current first launch

GitHub only, release `app-v*` tags → sole universal APK, signed/non-debuggable;
app-v1.3.18 **7 217 532 B, versionCode 22**. Notes include SHA256/date/changelog.
Branch Actions artifacts CodeC-IDE-debug / CodeC-IDE-release are not the primary
visitor CTA. Updater Settings → About → Check for updates, numeric versions,
checksum before install, refuses downgrade, opens Releases on missing checksum,
never treats userland-* as app or checks unprompted. Debug→release = fresh install,
export first (Files → Export all). No Play/F-Droid CodeC listing. Primary CTA
**Get the APK on GitHub → Releases**, never artifact or direct APK URL.
Historical -74% R8 figure = 25 553 564 B → 6 630 554 B at v1.3.17, NOT v1.3.18.

New installs: short intro, privacy acknowledgement, opens editable CodeC Arcade
index.html (Snake/Block Party/Tic-Tac-Toe); seeding once, no overwrite of edits.
Return resumes last file. No mandatory guide, no auto-download of Linux userland;
C/HTML work first, Linux installed when asked, setup doesn't lock navigation.

### AI — shipped Phases 76–96 / Levels 1–12

Optional, idle until BYOK. Gemini default, NVIDIA Build manual **dev/test only**.
No CodeC server/shared key/proxy, phone goes directly to chosen provider with its
terms/bill. Exact preview provider/model/two strings before Send; task Send permits
bounded follow-up reads, not a fresh tap for each. Stop cancels; Continue requires
new preview; one disclosed cancelable rate retry. Provider change manual only.

Eight tools: list_files (200 paths); search_project (40 hits/300 files, narrowing
max/path/ext); read_file (400 lines/24 000 chars); read_files (8 files); find_files
(80-char pattern); outline_file; read_run_output; request_run (2/task, **asks only**).
No write/exec tool. Edit arrives as <<<CODEC_EDIT>>> diff; Apply via
ui/projects/AiEditApplier.kt writes, Reject discards. Run card needs user's Run
into normal RUN pipeline, Skip runs nothing. One Undo AI changes bounded last-task
journal, not undo of network/run/Git. .env/.npmrc/private keys/.git/.codec/outputs/
symlink escapes refused before reads. Refusals are features, no cap bypass advice.

Nine controls: read window, working set, task memory, answer detail, tool activity,
request inspection **Always on**, backup provider, budget extension, read-only
reviewer. **S9** tunes within caps, none widens what may be read/written/run/reached.
Hard caps 12 turns / 24 calls / 2 runs / 24 000-char reads / 8 000-char results /
8 files / 3 repeats; window default 400, adjustable 50–400. Stops visible.

D6 raw chat text never written to file/store/log/backup. Drawer titles (≤20
chats/project, 8 carried turns) also memory-only. **Do not overstate this:** bounded
admitted file/task memory and last-task undo preimages have separate no_backup
storage per privacy guide; ephemeral chat does not mean all AI state ephemeral.
Key Keystore AES-256-GCM in no_backup/ai, not backed up; key-shaped redaction on
support exports, read_run_output and Explain last error. Provider retention is
not zero retention. No autonomy; L13 postponed/nothing scaffolded, L14 unauthorized.

### Privacy and other shipped facts

Privacy guide is claim ceiling: no account requirement/telemetry/analytics;
nothing leaves unless the user starts it; retain **“with ONE approval it can
read your files”** honest correction and permission surface/proving filenames.
Never say **“your code never leaves the device”**. AI formulation:
**“nothing leaves unless you send it; when you do it goes to the provider you
configured — there is no CodeC server.”** Explain scoped follow-up reads.
Developer WhatsApp/email never published by site; GitHub Issues instead (D27).

Original >_ mark in docs/brand/icon/codec-mark.svg (adaptive/monochrome/rasters,
notification silhouette, About app_mark); copy needed asset into website/.
Built-in TCC offline arm64-v8a/x86_64; null on armeabi-v7a/x86 → compatible module
or automatic Termux fallback. Clang module arm64. Auto only. VT/ANSI multi-session
terminal, progressive apt, background survival. LAN opt-in 0.0.0.0, two URLs,
QR, foreground keep-alive, open browser. Signed CodeC package repo
https://pabi277.github.io/CodeC/dev. Editor/projects/honest Git. RUN outputs
temporary via RunArtifacts/RepoHygiene; user's .gitignore wins; manual shell -o
still chooses its own file. Export-all project roots; backup include-list-only;
third-launch crash guard; user-initiated feedback, no telemetry; targetSdk 28
deliberate for downloadable compilers; R8/shrinkResources; BETA B-1…B-8 honest.

## Order of work

1. Verify git status/log, gh pr list/run list, remote main; trust actual tree over handoff.
2. No new phase command: answer/refine planning only, no implementation expansion.
3. With phase command: current phase only, spec/part exits, source trace, web record,
   living docs same commit, push current branch, report checks/device gates, stop.
4. Keep this handoff and current head truthful, not historical wishful status.
5. Open **O1 screenshots, O2 domain, O3 tone, O5 in-app link, O7 course license**.
   O4 order, O6 19-chapter scope, O8 contacts **closed — do not re-ask**.
6. No PR/merge until literal command. Old PR #42 remains reference only.

---
