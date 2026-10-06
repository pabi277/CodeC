# WEBSITE_PLAN.md — master spec for the CodeC website

> **v2.3 — scope dated 2026-10-06; reconciled with the actual tree 2026-10-07.**
> The owner supplied the v2.3 handoff in chat. It had not landed in this checkout:
> the base `d4231f0` held v2.2 and 35 phase docs. This session restores the missing
> specifications, without inventing a past sync or overwriting session history.
> **All29 pages are implemented for review.** Owner D33 authorized W3–W6
> content/polish together, one end commit/watch, and retained device gates.
> W4.2 facts were verified first; ch08/P1/P5 owner transcripts remain required.
> No PR/merge/deploy authorization. D31 full-site ZIP/link rule remains.
> Head state: [NEXT_STEPS.md](NEXT_STEPS.md). Prior v2.2 narrative remains in
> [chat-web2/SUMMARY.md](chat-web2/SUMMARY.md) and [WEB_JOURNEY.md](WEB_JOURNEY.md).

## 1. What we are building

A public, self-dependent website for CodeC, the Android C IDE, with two wings:

1. **Product:** Home, Install, Start, Engines, Packages, AI, FAQ, About, Privacy — **9 pages**.
2. **Learning:** **Master CodeC from Zero to Advanced**, course home and **19 numbered chapters**.

**29 pages total.** Termux's public site is a structural reference only (never
copy its source). The owner's Termux-Mastery supplies the book-like learning
model, not content. PR #42 is an older, unmerged reference, not a source of
current product truth or authorization to import its W2 implementation.

## 2. Goals and non-goals

- Accurate, source-backed, mobile-first, accessible, fast, useful without opening the repo.
- A more polished presentation: strong typography, clear product/learning paths,
  six concise feature cards, readable code examples, explicit limitations.
- **Locked stack:** plain HTML + one CSS file; minimal vanilla JS only when truly
  necessary. No framework, build step, backend, CMS, database or runtime dependency.
- Dark theme only. No theme-switch hooks, fake testimonials, invented statistics,
  unapproved screenshots, app emulator, website AI chat or automatic APK download.
- GitHub-only app distribution. No Play Store or F-Droid listing for CodeC.
- Website work changes only `website/`, `web_docs/`, `web_prompt.md`; W6 alone
  permits an additive Pages workflow and a one-line root README site link.

## 3. Pages and chapter content

### 3.0 Shared chrome

Header in order: **Home · Install · Start · Engines · Packages · AI · Learn · FAQ · About · GitHub**.
Footer on every page: **Privacy** plus repo, README, Releases, Issues, JOURNEY,
site-source link. Privacy is also linked from Install, About, AI, and the FAQ privacy answer.
Use the original **>_** mark from `docs/brand/icon/codec-mark.svg`, copied locally.
System sans and monospace fonts. Green identity accent with restrained amber;
near-black surfaces, visible keyboard focus, WCAG AA contrast, skip link,
semantic headings, reduced-motion support, 360 px through desktop and zoom.
Mobile navigation works without JS using native `<details>`.

Final links are relative `.html` paths (compatible with a project Pages subpath
and a local file copy), not origin-root `/install` paths. W1 retains final URLs;
unbuilt pages are explicitly labelled upcoming in the preview, not counted as
passing links. No placeholder pages just to conceal the phase boundary.

### 3.1 Product wing

**Home (`index.html`, W1).** Hero names the Android C IDE; one primary CTA
**Get the APK on GitHub** → canonical Releases page, secondary current **Write your first program** → Start (README remains in footer).
Original textual C example may illustrate write → compile → output, explicitly
labelled an example, not an app screenshot or live compiler. Six cards:

| Card | Required content | Final link |
|---|---|---|
| Built-in C | Offline TCC, Auto only; arm64-v8a and x86_64; 32-bit caveat | engines.html |
| Editor | Official file icons, typing feel, ghost text, snippet packs, Emmet, TextMate Dark+ | about.html |
| Terminal and web/LAN | Real VT/ANSI terminal, multiple sessions; in-app web preview; opt-in LAN sharing | start.html |
| Packages | CodeC's signed repository, install on request, real packages not inflated counts | packages.html |
| AI | BYOK; eight bounded tools, no write/exec tool, Apply/Run approval, session-only history | ai.html |
| Safe workflow | Export-all, project-only backups, crash-loop guard, checksum updater, honest Git | install.html |

Learning banner: full course title, **19 chapters written for review**, chapter-group preview,
link to `learn.html`. All chapters are now locally available; never equate written content with device
acceptance or public deployment.
Footnote: free/open source, Android, release **app-v1.3.18**, universal APK
**7 217 532 B** (7.22 MB decimal), versionCode 22. Never attach “-74%” to this size.

**Install (`install.html`, W2).** README first, release channel first. One universal
`CodeC-IDE-<version>-universal.apk`; shipped v1.3.18 = 7 217 532 B, versionCode 22,
signed, non-debuggable; release SHA256 verified from GitHub, not guessed.
Check for updates = Settings → About → Check for updates: numeric version guard,
checksum verification, refuses downgrade, opens Releases if checksum absent,
never installs userland bootstrap as app. Debug→release signature change means
fresh install: export first, Files → Export all. Include supported ABIs and
32-bit TCC limitation; optional Termux fallback setup from current README.
F-Droid is mentioned only as a README-supported source of Termux, not CodeC.
Link Privacy. GitHub Releases CTA is not a transient artifact/download URL.

**Start (`start.html`, W2).** Fresh install now has the short skippable introduction,
mandatory privacy acknowledgement (except safe-mode recovery), then the editable
**CodeC Arcade** HTML project. Returning launches resume the last file. No mandatory
tour, first-hour tiles or automatic userland download. C and HTML work first;
Linux userland is opt-in from Terminal, progress/retry never locks navigation.
Teach a simple `hello.c` and RUN, terminal `cc hello.c -o a.out` then `./a.out`,
interactive input in Term, package install, web preview, five-tab map, chapter 1 link.

**Engines (`engines.html`, W3.1).** **Auto only**: built-in TCC, optional Clang module,
Termux fallback when needed. No picker (Phase 21), no Termux settings card (38.2).
Explain conceptual engines, not selectable controls. Four setup steps appear in
Output Panel only when needed. TCC arm64-v8a/x86_64; null on armeabi-v7a/x86;
Clang module arm64 limitation; no “switch engine” instructions from stale paragraphs.

**Packages (`packages.html`, W3.2).** Verify the actual package list against
`codec-packages/` build config, record source sha and count. README gives examples,
not an authoritative enumerated total. Signed metadata (`signed-by=`, never
`trusted=yes`), verified bootstrap, atomic installs; UI badges and quick actions;
CodeC packages only, never official com.termux packages.

**FAQ (`faq.html`, W3.3).** Keep BETA B-1…B-8 visible, plus compiler errors,
32-bit limits, long-running commands, backup, crash-loop safe mode on third launch,
export-all, debug/release signing, checksums, long lines, huge imports and Git push
truth. Phase 46 removed Open Folder; do not recommend that deleted UI. Include AI
caps/history/provider costs and Privacy link. Feedback → GitHub Issues; personal
contact details stay in the app. Every answer has a source, optional deeper link.

**About (`about.html`, W3.4).** >_ identity, built-in compiler, terminal, editor,
file icons, LAN, packages, honest Git readiness/push/publish, outputs temporary,
export-all/backup/crash guard, Settings trim and feedback, targetSdk 28 deliberate,
R8 history and public development through Phase 96. Explain AI without autonomy
or on-device-model promises. Links: Privacy, Issues, README, JOURNEY, Releases,
BETA, data/privacy guide. No developer phone/email displayed.

**AI (`ai.html`, W3.5).** Optional and off until BYOK setup; Gemini default,
NVIDIA Build manual dev/test only; provider terms/bill apply. No CodeC server,
shared key or proxy. Preview exact provider/model/text; Send starts the task and
bounded read follow-ups may send more admitted text without a new tap. Eight tools:
`list_files`, `search_project`, `read_file`, `read_files`, `find_files`,
`outline_file`, `read_run_output`, `request_run`; last one requests approval only.
No write or exec tool. Apply diff / Reject; Run / Skip; bounded Undo AI changes.
Secret-like files, .git, .codec, outputs and symlink escapes refused before read.
D6 raw chat never persisted; drawer titles also memory-only, ≤20 chats/project,
8 turns carried. Nine controls, inspection always on, **S9** no cap widening.
Hard caps: 12 turns, 24 calls, 2 approved runs, 24 000-char reads, 8 000-char
results, 8 batched files, 3 identical repeats; read window 50–400, default 400.
Keystore key under no_backup/ai; redaction also on Explain last error. Provider
retention is not zero-retention. Link Privacy; L13 postponed, L14 unauthorized.

**Privacy (`privacy.html`, W3.6).** Two claims no stronger than
DATA_AND_PRIVACY.md: no telemetry; nothing leaves unless the user starts it.
Keep the honest correction **“with ONE approval it can read your files”**, optional
shared-storage access and permission table with named proving source files. AI
surface: provider recipients/terms, preview and task follow-ups, visible one-retry,
manual provider changes, Apply/Run, keys/undo/task memory versus ephemeral chat.
Use: **“nothing leaves unless you send it; when you do it goes to the provider
you configured — there is no CodeC server.”** Never claim “your code never leaves
the device”. No tracking on the site itself.

### 3.2 Learning wing

`learn.html`: about course, prerequisites, why CodeC, how to use, 19-row chapter
index, exercises, educational disclaimer, license per O7 (still pending).
Every `ch-NN.html`: **Chapter N of 19 → learning goals → prerequisites → steps
→ Try it yourself (1–3 exercises, expected results) → common mistakes → prev/next**.

| # | Chapter | Shipped teaching scope |
|---|---|---|
| 01 | Getting Started | Current intro + Arcade, GitHub APK, acknowledgement, no forced download |
| 02 | Your First C Program | hello.c, RUN Auto, terminal compile, explicit ./ rule |
| 03 | The CodeC Terminal | Commands, one per line, input, sessions, opt-in userland |
| 04 | Compiler Engines | Auto-only fallback, no picker/card, error-path setup |
| 05 | Package Manager | Verified package list, signed repo, badges/install/repair |
| 06 | Files & Projects | New/Clone/Import ZIP; Open Folder deleted Phase 46; outputs temporary/export-all |
| 07 | The Editor | File icons, typing feel, ghost TAB, strip, snippets, Emmet, TextMate, optional CodeC Keys |
| 08 | C Programming Basics | ANSI C / TCC-safe examples, no C11-only constructs; device pass |
| 09 | Shell Scripting | Scripts/variables/loops/conditions in CodeC userland |
| 10 | Python in CodeC | Install verified Python package, run/REPL/utility |
| 11 | Git & GitHub | Readiness, clone, local commit vs push truth, publish private by default |
| 12 | Networking & SSH | Only shipped tools; opt-in LAN bind, two URLs, QR, foreground keep-alive |
| 13 | Device APIs | Verify actual shipped codec-* scripts; runtime permission/refusal paths |
| 14 | Web Projects | HTML/CSS/JS preview, modules/fetch, live reload, LAN |
| 15 | Custom Setup & Advanced Tools | Settings, verified tools, export-all, backup, recovery, feedback |
| 16 | Real World Projects | C calculator, website/LAN, Python utility, Git, automation; P1+P5 device passes |
| 17 | Troubleshooting | BETA, errors/logs/crash-log, feedback; next is ch-18, not course completion |
| 18 | Ask about your code | BYOK setup, first question, exact preview/timeline, session-only history |
| 19 | Agent, tools and approvals | Eight tools/caps, refusals, Apply/Run doors, nine controls/S9, Undo, redaction |

Chapter laws:
1. Only verified shipped CodeC features; final facts and package inventory at W4.2.
2. Commands work on a fresh install after named prerequisites; never `.` on PATH,
   never replace cc, TCC link order with -o last, never use com.termux packages.
3. Ch-08 examples are TCC-safe, not a C11 course; owner device transcript required.
4. Samples and expected output are inline; repo links are optional further reading.
5. Outputs temporary; the user's own .gitignore wins; no deleted Open Folder UI.
6. **Refusals are features**: secret/cap/path restrictions taught honestly in ch-19.
   Completion / Back to course home belongs to **ch-19**, not ch-17.

## 4. Content rules (both wings)

1. **Source chain:** README first → TROUBLESHOOTING → BETA → AI → DATA_AND_PRIVACY
   → RELEASE_NOTES → JOURNEY, under `docs/guides/` and `docs/journal/` as appropriate.
   Detail can use named app implementation files read-only. No unsourced claim.
2. Distill, don't dump. Store claim/source/line/sha notes in the session record.
3. Stable outbound hyperlinks only: repo, README, Releases, Issues, guide paths.
   No CI artifact URLs or run IDs in visitor copy.
4. Version-aware: shipped = app Phases 1–96 + app-v1.3.18; roadmap = 97+.
   Cancelled work is not shipped merely because its phase number is below 96.
   Phase 43 proposed safe walk/ProjectLink must not be taught as shipped.
5. **Banned privacy sentence:** “your code never leaves the device”. AI requests
   leave for the chosen provider. Describe task-level consent, not a fresh prompt
   for every read. No autonomy or on-device-model promise; no personal contacts.

## 5. Self-dependent law (D11)

Zero fetched external resources: no CDN, fonts, JS/CSS/images, analytics or embeds.
Every browser-loaded file lives in `website/`. Outbound links are allowed (repo,
Releases, guides, Issues, package URL and README's Termux sources). System fonts.
All pages fully render offline from a local copy, not merely from a warmed cache;
no service worker required. Entire course completable without opening the repo.
SVG namespace URLs are identifiers, not fetches. Code examples can contain URLs
as text; they must not become network dependencies.

W1 first sweep; W6 full proof in `chat-web6/`: inspect src/link/@import/url and
other fetch-capable attributes, browser requests, offline local render, all links
and anchors. No broken links at launch; deliberately unbuilt W1 paths stay listed
as deferred, never counted as passing.

## 6. Design and accessibility

Dark, editorial, terminal-inspired; green #3DDC84 identity with amber for status.
System sans + monospace. Wide Home up to 1160 px, prose up to 760 px. Cards use
2 columns then 1 on phones. At 360 px navigation is a keyboard-operable native
menu; desktop has the whole nav. Min 44 px interactive targets, AA contrast,
no essential animation, visible focus, readable code and scrollable tables.
Screenshots only after O1 approval. Textual examples are explicitly labelled.
Canonical header/footer and CSS component reference are documented in the W1
record so later pages copy consistently (only active link and page content vary).

## 7. Stack and file layout (locked)

```
website/
  index.html
  install.html  start.html
  engines.html  packages.html  faq.html  about.html  ai.html  privacy.html
  learn.html  ch-01.html … ch-19.html
  style.css  favicon.svg
```

Only index/style/favicon in W1. No package manifest, generator or build/runtime
dependency in the site. Test tooling is separate, not shipped as site assets.

## 8. Deployment (W6 only)

GitHub Pages, same repo, desired project URL `https://pabi277.github.io/CodeC/`.
**Deployment safety finding from PR #42:** the existing Pages site serves the
signed package repo (`/dev`, `/keys`). Re-verify current Pages/package workflows
in W6 before proposing an additive deployment that preserves them. Do not deploy
website alone over those endpoints. GitHub's branch-source setting does not serve
an arbitrary `/website` directory: use a verified Pages artifact approach, without
switching/creating another branch, editing the APK workflow or replacing package
publishing. If existing deployment ownership needs a broader change, stop for
owner authorization. No deployment in W1. Custom domain O2 remains open.

## 9. Phase scope and current batching exception (D33)

Original phase boundaries below still define content and acceptance. The owner
authorized the remaining W3–W6 content in one batch, one end commit/watch. W4.2
verification still ran FIRST; ch18/19 precede polish. Separate start commands and
intermediate commits are superseded for this batch only; device/deploy gates stay.

**39 docs: 6 phase READMEs + 33 PART docs** (2/2/6/8/6/9 parts).

| Phase | Scope | Exit |
|---|---|---|
| W1 | Scaffold, shared chrome (AI + Privacy), Home six cards/course preview | Responsive 360/1440, offline, first self-dependent sweep, source trace |
| W2 | Install + Start | Release/current-first-run truth; 3/29 pages |
| W3 | Engines, Packages, FAQ, About, AI, Privacy | Sources/caps/privacy correct; product 9/9 |
| W4 | **W4.2 FIRST** → course home + ch-01…06 | Committed Verified Facts Table at current sha incl AI/Phase46; 19-chapter set verified |
| W5 | ch-07…12 | Self-contained, TCC-safe review, ch-08 owner device transcript |
| W6 | ch-13…17 → **W6.8 ch-18 + W6.9 ch-19 before W6.6** → polish → W6.7 | P1+P5 transcripts, 29 pages, sweeps before live, package-safe Pages green |

O6's owner scope is closed (19 chapters); W4.2 is still the mandatory technical
verification gate, not permission to re-ask that owner decision. Records must not
overwrite an existing chat folder: W0.3 is chat-web2; reconstructed W0.4 is
chat-web3; W1 uses chat-web4/W1_* and W2 uses chat-web5/W2_*. W6's dedicated verification filenames can
live in chat-web6 without replacing any earlier record.

## 10. Acceptance criteria

1. 29 real, complete pages, shared chrome, original mark, no broken links/anchors.
2. Every claim traceable to a repository source; current README re-read at W6.
3. Zero external resource requests; all pages render offline; course self-contained.
4. Responsive at 360/1440; keyboard navigation and AA checks; reduced motion.
5. Each chapter has goals/steps/exercises/common mistakes/prev-next and expected output.
6. Device transcripts for ch-08 and P1+P5 (never invented or replaced by web tests).
7. Pages green with every live URL checked; signed package endpoints preserved.
8. No app code/history/release workflow modifications; all web living docs current.
9. GitHub-only distribution, true AI/approval/privacy boundaries, no personal contacts,
   7 217 532 B not paired with -74%, no deleted engine picker or Open Folder teaching.
10. Owner commands control scope and any PR/merge. D33 combines W3–W6 content
    only; required device transcripts and public deployment gates are unchanged.
11. **Every phase ends with a verified full-website ZIP and a direct GitHub download
    link in the final report** — mandatory [D31 procedure](REVIEW_DOWNLOAD_RULE.md).
    An Arena attachment alone is insufficient; review does not authorize deployment.

## 11. Open items

**O1** approved screenshots; **O2** custom domain; **O3** copy tone;
**O5** in-app site link (separate app work); **O7** course license.
**O4/O6/O8 closed** by the supplied v2.3 scope: W1→W6; 19 chapters;
contacts in app only / site links Issues. Do not re-ask them.
