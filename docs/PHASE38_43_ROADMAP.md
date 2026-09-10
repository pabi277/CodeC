# CodeC — Phases 38–43 · share-readiness roadmap

> **Owner (2026-09-10, verbatim):** *"Yes working — But before i merge i have some
> new future phases idea analysis every idea research throughly and also check
> open source for solutions, my ideas- … Please you also full thought one time i
> will it use now so what should i add more before sharing the app. Everything
> research throughly than write new phases"*
>
> Six ideas came in; they became **five phases (38–42)**. The sixth thing asked
> for — *my* honest list of what still blocks a share — became **Phase 43**, and
> it is not decoration: the app testers would install today is a **debug-signed,
> debuggable, un-minified APK whose in-app updater points at a bootstrap
> release**. That has to be fixed before any outsider sees it.
>
> **This file is a plan. No app code was written for it.** Research dossier with
> licences and sources: [`PHASE34_37_OSS_RESEARCH.md`](PHASE34_37_OSS_RESEARCH.md)
> is the 34–37 twin; the new one is
> [`PHASE38_43_OSS_RESEARCH.md`](PHASE38_43_OSS_RESEARCH.md). Each phase has a
> `docs/chat-phaseNN/` directory with a README (evidence + risks) and one part
> doc per part, in the house format (symptom → design → exit condition → tests
> → deferred-with-reasons → sources).

---

## The owner's six rows → phases

| # | Owner's words (verbatim) | Phase | Title | Effort |
|---|---|---|---|---|
| 1 | "Github integration update now Github is working but it's not user friendly if i try to clone a repo and didn't download the git it shows error in the background i can't see it, sometimes it's push stay local, new branch create mostly stays local" | **38** | GitHub that tells the truth | M/L |
| 2 | "Now the file system is good but i want it more stronger, i can't open a project in the editor from project folder, open a folder crash the app" | **39** | File system strength (open any folder) | L |
| 3 | "I the output files as temporarily file and don't come to add in github push find all languages temporarily file and remove from git push also the .codec file" | **40** | Outputs are temporary, never in your repo | S/M |
| 4 | "For testing i have to add a feedback page give the best way, i am willing to give my WhatsApp number" | **41** | Feedback that reaches you (WhatsApp) | S/M |
| 5 | "From the settings remove unessesary Termux bridge" | **42** | Identity & Settings trim (icon first) | S/M |
| 6 | "I have to set a app icon" | **42** | (same phase — 42.1 is the icon) | |
| — | "what should i add more before sharing the app" (agent's own review) | **43** | Share-readiness: signing, size, updates, safety | M |

Phase numbers 38+ continue the 34–37 UX/UI series; 37 is done (✅ CI green +
✅ device-passed, merge held at `04f336f`), so these start from that tip.

---

## Recommended order

**42 → 40 → 38 → 41 → 43 → 39.** Small, visible and embarrassing-things-first,
deep refactor last:

1. **42 — the icon and the Settings trim.** The launcher icon on a tester's home
   screen is currently the **Android Studio template icon** (green `#3DDC84` +
   the Android robot paths) and the notification small icon reuses that same
   full-colour vector. Cheapest thing in the list, biggest first impression.
2. **40 — outputs never reach the repo.** Small, mechanical, and it protects
   *other people's* repositories: today `git add -A` in COMMIT & PUSH can carry
   `a.out`, `bin/menu` or `.codec/` to GitHub depending on what was committed
   before the ignore rules existed.
3. **38 — GitHub that tells the truth.** The owner's loudest complaint, and the
   diagnosis is already in the code (see §"Evidence" per phase): failures happen
   in a path whose message is rendered *behind the open dialog*.
4. **41 — feedback channel.** Needed the day a second person installs the app;
   it is a Settings row, a pure draft builder and a `wa.me` link.
5. **43 — share-readiness.** Signing, non-debuggable release artifact, size, the
   broken in-app updater, backup rules. This is the gate for "sharing" itself.
6. **39 — file system strength.** The only phase that changes an architectural
   law ("a project lives under `filesDir/CodeC/projects`"), so it goes last and
   gets the most test surface. Its 39.1 half (the crash) is separately
   shippable and can be pulled forward if the owner prefers.

The owner may reorder by saying **"Start Phase N"** for any of them (the 34–37
law the owner reinstated: one phase at a time, `Start Phase N`, and the merge
gate is the owner's).

---

## What each phase actually changes (one paragraph each)

**38 — GitHub that tells the truth.** A pre-flight **readiness** answer
(`GitReadiness`: is `git` installed · is a token stored · is this a repo · does
it have a remote · does the branch have an upstream · is there anything to
push), computed as pure data and *rendered before the user taps*, with one-tap
"Install Git" routed to the existing Modules installer. Every git failure gets
a visible home: the clone dialog shows its own inline error (the bug today is
that the dialog stays open on failure and the snackbar fires behind it —
`FileManagerScreen` closes `showCloneDialog` only in the success callback).
Commit/push ends in a **result card** built from git's own stdout
(`PushOutcome`: what was pushed, to which remote/branch, `Everything
up-to-date`, `* [new branch]`, or "still local because X"), and a project with
no remote gets a **Publish to GitHub** action that creates the repository
through `POST /user/repos` — or, when the token cannot, says precisely which
permission is missing (GitHub returns it in `X-Accepted-GitHub-Permissions`)
and offers the browser fallback.

**39 — File system strength.** Two halves. **39.1** makes "Open folder" never
crash: the SAF copy today is a plain recursion (`ProjectTransfer.copyDocumentChildren`)
with no visited set, no depth/file/byte budget, no cancellation, and its
caller catches only `Exception` — a provider that returns the parent as its own
child is a `StackOverflowError`, which *is* the crash. Replaced with an
iterative walk planned by a pure `TreeWalkPolicy`, budgets, progress + cancel,
per-provider failure as a message, `Throwable` at the boundary, and the grant
persisted (`takePersistableUriPermission`, never called today). **39.2** makes
a folder *usable as a project without copying it into app storage first* —
`ProjectLink(projectName, treeUri, …)` + a pure `ProjectLinkPolicy.decide`
(take / refuse-with-reason / re-pick when the grant is gone), with the honest
limitation recorded up front: emulated storage is mounted `noexec`, so a linked
project **runs from an internal mirror** (sync in on open/save, the push-set on
save-back excludes CodeC's own outputs) while the user's folder stays the source
of truth — **40.1 must land first**, or the mirror would push build artifacts
into a folder the user owns. The all-files-access "open in place" variant is
documented in 39.2 as *considered and rejected as the default*, with the four
reasons and the one per-project flag that may still earn its place.

**40 — Outputs are temporary, never in your repo.** Generated files stop
landing in the project at all: one `RunArtifacts` policy routes every
language's build/run output to `filesDir/CodeC/temp/runs/<stamp>/`, and a
`TempGc` policy prunes it (age/capacity/newest-N) on start and after Stop. For
files CodeC cannot move — the user's own `cc … -o bin/menu`, an app that writes
`__pycache__` — the exclusion table grows from 12 patterns to a **per-language
set derived from the CC0 `github/gitignore` templates**, plus CodeC's own
(`.codec/`, `.codec.json`), applied at the single choke point (`stageAll`)
instead of one call site in `GitControlViewModel.refresh()`, with `git rm
--cached` for anything already tracked and a **"what will be committed"** list
before the commit button. The user's own `.gitignore` always wins — that law is
kept and tested.

**41 — Feedback that reaches you.** Settings → **Feedback & support**: a
one-tap WhatsApp chat (the owner's number, click-to-chat `wa.me/<E.164
digits>?text=<url-encoded report>`), with email, "copy report" and a prefilled
GitHub issue as fallbacks, and the same no-handler-means-nothing-is-lost
discipline the Phase 37 browser button established (the launcher
`OpenInBrowser` is already there). The report itself is built by a pure
`FeedbackDraft` (app version + CI run, Android/API, device, ABI, the user's
text, an *opt-in* redacted log tail). Nothing is sent automatically; there is
no telemetry to add; the number lives in Settings so it is never welded into
the APK.

**42 — Identity & Settings trim.** **42.1**: an original CodeC mark, built the
adaptive-icon way — 108 dp canvas, key art inside the 66 dp safe zone, real
`monochrome` layer for themed icons, legacy bitmaps for API 24–25, plus
`ic_launcher_round`, a 512×512 store/release asset, and **a proper
black-on-transparent notification small icon** replacing
`ic_launcher_foreground` in both foreground services. Master art lives in the
repo as one SVG; the rasters are generated by a build-time script (never a
runtime dependency). **42.2**: remove the "Termux Engine" card and its
four-step prose from Settings while keeping `TermuxCompiler` as the silent
fallback it already is inside `CompilerService` — guidance then appears in the
error path that actually needs it, and the same audit is run across the rest of
Settings (any row with no effect goes).

**43 — Share-readiness.** A release **channel**: `assembleRelease` in CI,
signed with an upload key kept in Actions secrets (the build file already
reads `KEYSTORE_PATH`/`STORE_PASSWORD`/`KEY_PASSWORD`; `my-upload-key.jks` is
not in the repo, so today a release build simply cannot run in CI), published
as a GitHub Release with `app-v…` tags — which also **fixes the in-app
updater**, because `GET /repos/…/releases/latest` currently resolves to
`userland-v1` (a *bootstrap* release, marked "Latest") and
`ApkUpdateManager` takes the first asset ending in `.apk` with no version or
checksum check. Then size: R8 + `shrinkResources` (both off today) and per-ABI
artifacts (four ABIs declared, `libtcc.so` shipped for two of them, and — the
finding that changes the work — `assets/` is **not** ABI-filtered, so splitting
alone saves far less than it looks). Then safety: crash-loop guard bolted onto
the *existing* `CrashReportOverlay`, real backup rules (today they are the AS
samples, so the userland prefix **and the GitHub token's DataStore file** are
backup-eligible), an "export everything" ZIP, a permission list that matches
reality, and a written known-issues page for testers.

---

## Laws these phases inherit (from `rule.md`)

- **Clean-room**: replicate features, never copy code. Licences gate what may be
  *depended on or vendored*: MIT · Apache-2.0 · BSD · **CC0** (the
  `github/gitignore` templates are CC0, which is why 40 can use them and no
  `CC BY-SA` asset appears anywhere). No GPL/LGPL paste, no `com.termux`
  packages or repos, no trademarked logos — which is precisely why 42.1 draws
  an original mark instead of shipping the Android robot.
- **Android-free core**: every rule that decides something (readiness, push
  outcome, tree-walk budgets, temp GC, ignore patterns, feedback redaction)
  lands as a pure Kotlin object with a host test; the Android half is the thin
  edge that calls it. This is what made 37 debuggable and it is non-negotiable
  here.
- **CI is the only executor of record** (`Build APK`, whose
  `gradle-bootstrap` bridge runs `:app:assembleDebug :app:testDebugUnitTest
  :app:lintDebug`). A red run uploads no APK.
- **No `.` on `PATH`**, never `build-package.sh -I`, never overwrite `cc` or
  the real ELF `bash` with a shim, TCC `-o` last, repo metadata stays
  `signed-by=`, the bootstrap never enters the APK.
- **Open-source first**: for each phase the dossier records what already exists,
  its licence, and either "use it", "vendor it with attribution" or "behaviour
  reference only, because …".
- **Device gates are the owner's**: 38.3 (real push to a real repo), 39
  (real device file providers), 41 (real WhatsApp install) and 42.1 (real
  launcher masks) cannot be settled in the sandbox and are flagged as
  **device pass required**.

---

## State of record (when this plan was written)

- Phase 37 (device as server / LAN) — ✅ IMPLEMENTED, ✅ CI green
  (`Build APK` `34393543928` on `6d36a83`, `34398031696` on the follow-up
  `98cb2b4`), ✅ **DEVICE-PASSED by the owner's two-device round** ("All pass",
  2026-09-10), plus the round's 🌐 open-in-browser follow-up. **Merge held** at
  tip `04f336f` on `arena/01a0872e-codec`.
- Phases 34–36 are merged to `main`; the cross-device round that ended that
  series is complete. 38–43 start **only** on the owner's "Start Phase N".
- Deliberate non-goals, recorded so nobody re-litigates them: no GitHub OAuth
  app dance (a token the user pastes stays the model, 38 documents why); no
  *dependence* on `MANAGE_EXTERNAL_STORAGE` (39 — the app already declares and
  offers it; what is forbidden is making it required for opening a folder); no
  telemetry of any kind (41); no Play Store path while `targetSdk = 28` is what
  keeps downloaded compilers executable (43 documents the trade-off instead of
  pretending it away).

## Corrections found while writing the parts (2026-09-10)

Writing a part doc means reading the code, and reading it corrected ten
assumptions carried in from the research round. They are recorded here because
each one changes an implementation decision:

| # | Assumption going in | What the tree actually shows | Consequence |
|---|---|---|---|
| 1 | the userland tarball is packaged in the APK, which is why it is 24 MB | `app/src/main/assets/` = `licenses` 76 K, `snippets` 332 K, `tcc` 7.3 M + 3.6 M, `textmate` 2.3 M — no bootstrap. The rootfs is **downloaded** by `UserlandInstaller` from `releases/download/<tag>/userland-<arch>.tar.gz` | 43.2's "package vs download" question was already answered years ago; the size work is DEX/ABI, not the toolchain |
| 2 | `libtcc.so` ships for all four `abiFilters` | `EmbeddedCompiler.ABI_DIRS = listOf("arm64-v8a", "x86_64")`, and `jniLibs` has exactly those two | on `armeabi-v7a`/`x86` `tccBinary()` is `null` **by design**; armv7 testers download ~7.3 MB of C runtime they cannot use → `docs/BETA.md` known-issues + per-ABI artifacts |
| 3 | `splits.abi` shrinks everything per ABI | AGP filters `jniLibs`/CMake output, **not `assets/`** | 43.2 needs a packaging exclude or per-flavor `assets.srcDirs`, or the splits are pointless; exit item 2 got a "revert if <15 %" rule |
| 4 | the app should avoid `MANAGE_EXTERNAL_STORAGE` | it is declared (`:12`) and offered at three call sites | 39's design must not make it required, and 43.3's privacy text has to be honest about what the grant means; the diet is `ACCESS_WIFI_STATE`/`WAKE_LOCK`/`VIBRATE` (no readers found) |
| 5 | there is no crash-report surface to build on | `MainActivity.installCrashLog()` (`:125-175`) + `ui/crash/CrashReportOverlay` already show the newest header-first record before anything else with COPY ALL/Share/Clear, and literally say "paste it into the chat" | 41 becomes the *link*, not the pipeline; 43.3 extends the overlay instead of adding a second modal |
| 6 | `GitRedactor` scrubs token shapes | it scrubs **the literal token it was constructed with** + `user:pass@` URLs (`GitManager.kt:911-932`) | 41.1 needs its own `TokenShapes` table *plus* `GitRedactor`, and says why two redaction sources are not allowed |
| 7 | the token is a file under `files/CodeC/keys` | it is `git_token` in the shared `preferencesDataStore(name = "settings")` → `files/datastore/settings.preferences_pb` (and `GitCredentialsStore`'s KDoc names the wrong file) | 43.3's backup exclude list is written around the real path, plus a comment fix |
| 8 | projects live in one root | `ProjectManager.projectsRoot()` = `filesDir/CodeC/projects`, **and** `FileManager.projectDirCandidates()` adds `getExternalFilesDir(null)/CodeC/projects` | "export all" must take the candidate list or it silently omits projects — 43.3 §3 got a test for exactly that |
| 9 | the SAF folder picker is absent | `FileManagerScreen.kt:177` already uses `ActivityResultContracts.OpenDocumentTree()`; only `takePersistableUriPermission` is missing (zero call sites) | 39.1/39.2 change from "add SAF" to "persist the grant we already get" — a 3-line fix, not a subsystem |
| 10 | OkHttp is needed by something | `implementation(libs.logging.interceptor)` + `implementation(libs.okhttp)` (`:201-202`) with **no `okhttp3` import in `app/src`**; `app/proguard-rules.pro` is the untouched template (every rule commented) | 43.2 deletes provable dead weight before asking R8 to shrink it, and the keep-rule work starts from an empty file |
