# CodeC — Phases 38–43 · open-source-first research dossier

> **Status:** 📋 **RESEARCH ONLY — NO APP CODE.** Owner brief (2026-09-10):
> *"analysis every idea research throughly and also check open source for
> solutions … Everything research throughly than write new phases"*. Scope: the
> six share-readiness ideas that became phases 38–43. This is the licence-and-
> prior-art reference for those phase docs; where research changed a spec, the
> part doc says so.
>
> Clean-room law still applies (`rule.md` §6): replicate **features**, never
> paste copyleft code; depend on or vendor only MIT / Apache-2.0 / BSD / CC0;
> no `com.termux` code, no CC BY-SA assets, no trademarked logos.

---

## 0. What "open source first" meant for this round

For every idea the question was asked in this order: **is there a library that
already does this? → is there a canonical data set we may vendor? → is there an
app whose behaviour we should copy (reference only)? → only then: custom
Kotlin.** Two of the six ideas (git, filesystem) have huge OSS answers that we
deliberately did **not** take, and the reasons are recorded, because "we
researched it and chose the platform" is a decision the next phase must not
re-litigate.

| Phase | Best OSS answer found | Verdict |
|---|---|---|
| 38 Git UX | **Eclipse JGit** (pure Java, EDL/BSD) | ❌ not adopted — reasons in §1 |
| 38 Git UX | `git` CLI from the CodeC userland (already in the app) | ✅ keep, and *gate* it (readiness) |
| 38 Publish | GitHub REST `POST /user/repos` | ✅ one HTTPS call, no new dependency |
| 39 File system | `androidx.documentfile` (`DocumentFile`/`TreeDocumentFile`, Apache-2.0) | ⚠ reference + optional helper; not the walker |
| 39 File system | `MANAGE_EXTERNAL_STORAGE` — **already declared + offered by CodeC** (`AndroidManifest.xml:12`; three `isExternalStorageManager()` call sites) | ⚠ keep as-is, never make it load-bearing; audited in 43.3 |
| 39 File system | Rclone, a jsoup-style "own picker", SAF-as-the-only-path | ❌ rejected |
| 40 Ignore rules | **`github/gitignore` templates, CC0-1.0** | ✅ vendor the **patterns** |
| 40 Temp outputs | git's own `.git/info/exclude` + `git rm --cached` | ✅ already the house method; extend it |
| 41 Feedback | WhatsApp **click-to-chat** (`wa.me`) · `ACTION_SENDTO` mailto · prefilled GitHub issue URL | ✅ all three, no SDK |
| 41 Feedback | Firebase Crashlytics / Telegram bot / Google Form | ❌ rejected — reasons in §4 |
| 42 Icon | AOSP adaptive-icon spec (108/72/66 dp, monochrome layer) | ✅ follow it exactly |
| 42 Icon tooling | `sharp` (BSD-3, npm) · `@resvg/resvg-js` (MPL-2.0) | ✅ build-time only, outputs committed |
| 43 Distribution | **GitHub Releases** (workflow already attaches APKs on `release` events) | ✅ make it the channel |
| 43 Size | R8 (`minifyEnabled` + `shrinkResources`) + ABI splits | ✅ enable, measure |

---

## 1. Phase 38 — Git on Android: what exists, and why we stay with the CLI

**Eclipse JGit** (`org.eclipse.jgit`) is the only serious pure-Java git
engine. Its licence is the **Eclipse Distribution License (EDL/BSD-3-clause)**
and the project states "All portions of JGit are covered by the EDL. Absolutely
no GPL, LGPL or EPL contributions are accepted within this package" — so it
would be *legal* here [jgit README, gerrit.googlesource.com/jgit + github.com/spearce/jgit].
It was rejected anyway, on three measured grounds:

1. **Java level.** JGit's own line moved 5 → 7 → 8 → **11** (EGit 6.0 BREE =
   Java 11) [wiki.eclipse.org/EGit/FAQ]. CodeC is `sourceCompatibility 17`
   *for the toolchain* but runs on **Android API 24**, where the missing
   `java.time`/NIO/SASL surface means desugaring rules and a class of
   runtime surprises on the oldest devices we support — exactly the failure
   class that cost the last three phases device rounds.
2. **Weight.** One more multi-MB jar on an APK that is already +1.47 % from a
   *single* 3.5.4-class jar (zxing `core` was 355 660 B, `PART_37_1` §Tests).
   JGit is not a small friend.
3. **Two engines, one truth.** The CLI `git` already installed from Modules is
   what creates `.git`, what the user can run themselves in the terminal, and
   what Phase 17's device round calibrated (`GIT_TERMINAL_PROMPT=0`,
   askpass-through-env, `push --set-upstream <remote> <branch>`). A second
   implementation would fork that hard-won behaviour.

So 38 buys **no dependency**; it spends the effort on the layer that is
actually missing (readiness, visible errors, push truth, publish). Research
value: the CLI's own contract tells us the error strings worth classifying —
`fatal: not a git repository`, `could not read Username for
'https://github.com'` (no token), `The current branch <x> has no upstream
branch`, `rejected … (non-fast-forward)`, `remote origin does not exist`.
`GitErrors` (220 LOC) already maps most of these; the gap is that nothing
*asks before acting* and one message route is invisible.

**Publish-to-GitHub without a client library.** GitHub's REST docs for
*Create a repository for the authenticated user* (`POST /user/repos`) list
fine-grained PAT support **with "Administration repository permissions
(write)"**, and classic tokens with `public_repo`/`repo`
[docs.github.com/en/rest/repos/repos]. Two practical consequences are written
into 38.3: (a) a fine-grained token scoped to *specific* repositories cannot
create a **new** one — it must be an all-repositories token, and the UI must
say so instead of showing a 403; (b) GitHub answers a permission shortfall
with the `X-Accepted-GitHub-Permissions` header, which the docs recommend for
exactly this troubleshooting [same page + getknit.dev PAT guide] — so CodeC
can tell the user *which* permission to add, verbatim from GitHub's reply,
rather than "auth failed".

**Reference behaviour (behaviour only, no code):** VS Code's
"Publish to GitHub" and GitHub Desktop's publish/push flow — both make the
*remote* an explicit state with a one-button remedy, and both show a
"pending push" count instead of a spinner. Also noted: the Modules catalog
already ships a **GitHub CLI (`gh`)** module, but its output is not a stable
API for a parser, and one HTTPS call is smaller and testable — so `gh` stays
an option for the user's terminal, not the app's engine.

---

## 2. Phase 39 — SAF/`DocumentFile`, and the crash class we must kill

`androidx.documentfile` (Apache-2.0) is the OSS answer to "walk a SAF tree"
and its `TreeDocumentFile` is the reference implementation of child URIs. It
is *not* the right engine for a bulk copy, for two documented reasons:
`listFiles()`/`findFile()` are slow because `getName()` is a provider query
per child (workarounds exist precisely because of this [stackoverflow.com/q/46180851,
reddit.com/r/androiddev "Fixing TreeDocumentFile#findFile lousy performance"]),
and sub-tree roots lose write permission unless you keep the original tree URI
[same]. CodeC already does the right thing — raw `DocumentsContract.buildChildDocumentsUriUsingTree`
— and 39.1 keeps it.

Three research findings become requirements in 39.1:

- **Persist the grant.** `ACTION_OPEN_DOCUMENT_TREE` returns a tree URI whose
  permission **dies with the process unless the app calls
  `takePersistableUriPermission`**; the widely-cited failure is "after a
  reboot the URI is inaccessible" [stackoverflow.com/q/37157765,
  stackoverflow.com/q/57747643 (CommonsWare: the flags on the *intent* are
  not the grant), flutter_file_picker#1825]. CodeC calls it **nowhere** today
  (grep: zero uses), so a linked folder could never survive a relaunch —
  hence "persist, or don't promise linking".
- **Some picks are not directories.** Picking *Downloads* from the picker's
  drawer yields `content://com.android.providers.downloads.documents/tree/downloads`,
  a **provider**, not a filesystem directory; a normal tree looks like
  `content://com.android.externalstorage.documents/tree/primary%3ADCIM`
  [stackoverflow.com/q/34927748]. Traversal of such providers can hand back a
  child that *is* the parent — which in CodeC's current recursion is an
  unbounded descent, and since `importFolder` catches only `Exception`, the
  resulting `StackOverflowError` is a **crash**. 39.1's pure policy must
  therefore carry a visited-set and budgets, and the Android boundary must
  catch `Throwable`.
- **Do not "improve" this by *leaning on* a permission — and note the app
  already has it.** Correction made while writing the parts: earlier research
  notes in this series said "no `MANAGE_EXTERNAL_STORAGE`", which reads as
  "CodeC lacks it". It does not: the manifest declares it and
  `MainActivity.kt:354-360`, `SettingsScreen.kt:846-852` and
  `ShellEnvironment.kt:1771-1775` all check
  `Environment.isExternalStorageManager()` and route the user to
  `ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION`, with
  `READ_EXTERNAL_STORAGE`/`WRITE_EXTERNAL_STORAGE (maxSdk 32)` as the legacy
  pair. The rule this phase keeps is therefore the useful one: **all-files
  access must not become a requirement** for opening a folder (the picker path
  has to work for anybody who declines), and 43.3 audits the declared list for
  accuracy rather than pretending the grant doesn't exist. Neither variant makes
  emulated storage `exec`-able, and `getExternalFilesDir` is `noexec` too — the
  same law `FileManager`'s comment documents and the reason 39.2 mirrors a
  *running* copy inside app storage.

---

## 3. Phase 40 — the ignore rules: vendor patterns, not files

**`github/gitignore`** is the canonical template set (it feeds GitHub.com's own
`.gitignore` chooser) — 150+ templates in three tiers (root = languages,
`Global/` = editors and OSes, `community/` = frameworks) — and it is licensed
**CC0-1.0**, i.e. public domain, "allowing unrestricted use in your projects
without attribution requirements" [deepwiki.com/github/gitignore (citing the
repo's LICENSE/README), instagit.com summary of the same repo]. That clears the
`rule.md` bar (CC0 is not `CC BY-SA`; no ShareAlike clause travels with it),
and 40.2 takes **the pattern names only** — one curated list per language
CodeC actually runs (C/C++, Python, Node/JS/TS, HTML/CSS, Java/Kotlin, Lua,
Go/Rust where trivial) — rather than dropping whole template files into a
user's project, which is what GitHub's own docs warn against ("merge with
existing rules if necessary").

The mechanism stays **git's own, and stays private to the device**:

- `.git/info/exclude` for the patterns (already CodeC's law: "the user's own
  `.gitignore` always wins"), so CodeC never edits a user's tracked file;
- `git rm --cached` for what an earlier commit already carried, which is the
  only thing that stops a previously-committed `a.out` from keeping travelling
  (already implemented in `BuildArtifactIgnore.untrackTracked`, but wired to
  one call site — 40.2 moves it to the choke point `stageAll`);
- `git status --porcelain=v1` / `git check-ignore -v` as the two data sources
  for "what will be committed" and "why is this file not in the commit" — no
  custom matcher needed for the truth, and `matchesPatterns()` stays only as
  the *pre-decision* helper it already is.

On "treat outputs as temporary": nothing in OSS can be vendored (it is the
app's own file policy), so the research question became *where do mature
toolchains put outputs?* — Gradle (`build/`), Cargo (`target/`), npm
(`node_modules/`, `dist/`) all put them **inside** the project and rely on
ignore files; Termux/CodeC's own compiler services use a temp dir. 40.1
chooses the CodeC-native answer (app-private temp + GC) because our outputs
are created by *us* on the user's behalf, one `RUN` at a time, and are never
worth keeping in the repo — while explicitly *not* fighting the user's own
`cc … -o bin/menu`, which stays in the project and is covered by 40.2.

---

## 4. Phase 41 — feedback without an SDK

**WhatsApp click-to-chat** is a plain URL, no API, no key, and it works for
personal numbers as well as Business ones: `https://wa.me/<number>` where the
number is **full international E.164 digits — no `+`, no spaces, no dashes, no
leading trunk 0** (a `+91 98765 43210` becomes `919876543210`), optionally
with `?text=` carrying a **URL-encoded** message (spaces `%20`, newlines
`%0A`) that lands in the composer un-sent [u2l.ai wa.me guide (2026-06/07),
marketing.help.dotdigital.com click-to-chat article, support.wati.io
instructions, linklyhq.com "What is a wa.me link"]. The number being wrong is
the documented #1 failure of this feature, so `FeedbackDraft` normalises and
validates it as pure code with tests, instead of pasting a string.

Rejected alternatives, with reasons worth keeping:

| Option | Why not |
|---|---|
| Firebase Crashlytics / any APM | Adds Google mobile-services surface to an **offline-first** app, needs a project per install, and carries a data-collection disclosure the owner would have to write; the in-app crash record + `AppLogger` already hold what we need to *send*. |
| Telegram bot (`sendMessage` + bot token) | A bot token inside a distributed APK is a secret in the wild; also rate-limited per chat and one-way. |
| Google Form / web widget | No device context, needs a browser session, and gives the owner a mailbox of screenshots instead of a chat. |
| Prefilled GitHub issue as the *only* route | Correct for bugs, wrong for a first-time tester with no GitHub account. Kept as a **secondary** action: `https://github.com/<owner>/<repo>/issues/new?title=&body=` is plain URL encoding, and needs no token at all for a public repo. (`POST /repos/{o}/{r}/issues` — the API route — would need a token with *Issues: write*, so it stays out of scope and the doc says so.) |
| `ACTION_SENDTO` mailto | Kept, as a fallback: it needs no account and composes offline. |

Design honesty recorded for the phase: a personal WhatsApp number published in
an app means strangers can message that number and **WhatsApp shows them the
device's contact-free profile** — that is the owner's choice to make, so the
number is a *setting*, the row says plainly what it does, and CodeC itself
transmits nothing except the URL the user taps.

---

## 5. Phase 42 — the icon: AOSP rules, not taste

The current `ic_launcher_foreground.xml` / `ic_launcher_background.xml` are the
**Android Studio template art** (the `#3DDC84` field and the robot-head paths),
and `mipmap-*/ic_launcher.webp` are its bitmaps — the first thing any tester
sees. The rules that must be obeyed, from the adaptive-icon spec as
summarised by AOSP-derived guidance:

- canvas **108 × 108 dp**; the launcher masks down to a **72 dp viewport**, and
  because masks can reach as little as 33 dp from the centre, the safe area is
  a **66 dp-diameter circle** — key art goes inside it and the outer 18 dp per
  side is reserved for parallax/pulse [stackoverflow.com/q/49661571 citing
  "Designing Adaptive Icons"; anything.com Android icon guide;
  iconikai.com size chart].
- **`monochrome` is effectively mandatory now**: Android 13 tints it for themed
  icons, and **Android 16 QPR 2 auto-generates a themed icon for apps that
  ship none**, which is how you end up with someone else's interpretation of
  your brand [anything.com guide]. CodeC's `mipmap-anydpi-v26/ic_launcher.xml`
  currently points `monochrome` at the *full-colour* foreground — it renders,
  but it is not a monochrome design; 42.1 supplies a real one.
- legacy bitmaps are still needed for API 24–25 (48/72/96/144/192 px across
  mdpi…xxxhdpi) [iconikai chart], and the **Play / release icon is a separate
  512 × 512 px** asset [same].
- the notification small icon must be a simple silhouette — reusing the
  adaptive *foreground* layer (what both `RunForegroundService` and
  `TerminalForegroundService` do today, `setSmallIcon(R.drawable.ic_launcher_foreground)`)
  is the classic "white blob in the status bar" bug.

Rasterisation tooling (build-time only, outputs committed, so CI and
`assembleDebug` never need the tool): **`sharp`** (BSD-3) can render SVG →
WebP/PNG at every density; **`@resvg/resvg-js`** is **MPL-2.0** and its
maintained path is SVG → PNG (lossless formats such as webp sit on its
roadmap, not in v2.6.2) [npmx.dev/@resvg/resvg-js]. 42.1 therefore commits
`mipmap-*` **PNG**s (Android accepts PNG in mipmap dirs) and keeps the master
SVG in-repo, with `scripts/render_icon.mjs` documented for regeneration —
one art source, no binary drift. Clean-room + trademark law decides the design
itself: an original CodeC mark (no Android robot, no GitHub Octocat, no VS Code
glyph), simple enough to survive 48 px.

---

## 6. Phase 43 — distribution: what the ecosystem actually allows

- **The channel is GitHub Releases, and the machinery exists.** The workflow's
  `Attach APK to GitHub Release` step runs on `release` events and skips
  `userland-*` tags; `README.md` already tells users to install from a release
  and Settings already has *Install APK from GitHub* (`ApkUpdateManager` →
  `GET /repos/pabi277/CodeC/releases/latest`). No OSS is needed for the
  channel; what is missing is publishing to it for the *app* (only
  `userland-v1`/`userland-v2-dev` exist, and `userland-v1` is marked
  **"Latest"** — so the in-app updater's `latest` currently points at a
  bootstrap release, and it grabs "first asset ending in `.apk`" with no
  version or digest check). 43.1 is mostly *making what we built honest*.
- **Play is not on the table while `targetSdk = 28`.** The build file documents
  the reason (API 29+ SELinux denies `execve` of app-data files; Termux uses
  the same 28 compatibility mode), and its own comment says "CodeC is
  distributed via GitHub (not Play)". Play would additionally demand an AAB
  (you cannot install an `.aab` directly), API-target policy (Android 16 /
  API 36 from 2026-08-31), a privacy policy, and — for new personal accounts —
  **closed testing with 12 opted-in testers for 14 continuous days**
  (reduced from 20 on 2024-12-11), with 2026 engagement checks
  [testerscommunity.com closed-testing-requirements + 12-testers policy pages;
  appisto.app tracks comparison]. Recorded so the "just put it on the
  Play Store" idea gets an answer with numbers rather than a shrug.
- **R8 is the cheap win.** `isMinifyEnabled = false` today; enabling
  `minifyEnabled` + `shrinkResources` (with `proguard-android-optimize.txt`
  already wired) is the standard 30–50 % APK reduction lever, whose whole risk
  is reflection-heavy libraries losing classes — which for CodeC means
  sora-editor, the TextMate grammar loader, `BuildConfig`/DataStore
  entry points — and the standard mitigation is keep-rules plus
  `-keepattributes SourceFile,LineNumberTable` so the crash record stays
  readable [medium.com "Unmasking minifyEnabled"; dev.to RN 50 % size case
  study for the option set]. 43.2 makes this measurement-led (before/after
  artifact bytes, like the Phase 37 +355 660 B discipline), never hopeful.
- **ABI weight.** `ndk { abiFilters += [arm64-v8a, armeabi-v7a, x86_64, x86] }`
  means every tester downloads four ABIs of native libraries; `splits.abi`
  (AOSP-supported, no dependency) is how 43.2 cuts the phone download roughly
  in half without touching `useLegacyPackaging` (which the TCC `libtcc.so`
  extraction depends on).
- **Backup is currently mis-declared.** `allowBackup="true"` with
  `fullBackupContent="@xml/backup_rules"` and `dataExtractionRules="@xml/data_extraction_rules"`,
  where **both XML files are still the Android Studio samples with TODOs and
  nothing excluded** — i.e. the whole userland prefix (`$PREFIX`, dpkg db,
  installed packages) is backup/device-transfer eligible. AOSP's own guidance
  is to scope these files or disable backup; 43.3 does the latter with a
  reason, because a restored prefix on a different ABI is worse than no
  restore. (Not an OSS adoption — a licence-free correctness fix found by
  reading our own `res/xml/`.)
- **Crash-loop safety net.** Nothing existing to take; the app already keeps a
  header-first crash record (Phase 29) and an `AppLogger` ring of 1 000 lines,
  so 43.3 is a pure "how many consecutive launch crashes before we open in safe
  mode" policy plus the feedback hand-off from 41 — no dependency, and the
  count lives in app-private prefs that survive a crash but not an uninstall.

---

## 7. Open-source items deliberately *not* adopted (so nobody re-buys them)

| Idea | Candidate | Why not |
|---|---|---|
| Git engine | JGit (EDL) | §1 — Java-11 BREE vs API 24, weight, forked semantics. |
| Git engine | libgit2 / git2-rs (GPL-2.0 + linking exception) | Licence class `rule.md` §6 keeps out; NNI surface + a `.so` per ABI. |
| Repo creation | JGit `GitHubClient`, hub4j | Dead-ish, and it drags GitHub's whole API model in for two calls. |
| File picking | `NoNonsense-FilePicker`, `android-storage-access-framework` wrappers, RnR/MaterialFiles patterns | SAF itself is the platform API; a wrapper hides exactly the provider quirks 39 must handle explicitly. |
| Whole-tree copy | `DocumentFile`-recursive copy (the Stack Overflow idiom) | Provider-query per child = unusable on big trees (§2). |
| Ignore data | `kagome/gitignore`, `.gitignore` generators as a service | Our need is a *pattern list* + git's own `check-ignore`; a matcher library would compete with git. |
| Feedback | Sentry / GlitchTip self-host | Requires a server the owner must run; a WhatsApp chat is what the owner asked for and costs nothing. |
| Icons | any icon pack (Material Symbols, VS Code Seti, Simple Icons) | Seti was vendored for **file** icons in Phase 34 (MIT) — but a *launcher* mark must be original: Simple Icons are brand marks (trademark), and a Material glyph as "the app's logo" is what makes an app look like a template. |
