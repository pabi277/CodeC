# CodeC Phase 43 — Share-readiness: signing, size, updates, and not eating anyone's data

> **Status:** 📋 PLANNED (researched + specced, no code) · **Cost:**
> `[client-only]` + CI/workflow · **Effort:** M · **Owner row:** *"Please you
> also full thought one time i will it use now so what should i add more before
> sharing the app"*

This is the agent's own review, written against the same standard as the
owner's six ideas: read the code, find what actually breaks for a stranger
installing this APK, and spec the fix. Six findings are hard defects **today**;
the rest of the checklist is judged "keep / defer / reject" below.

```text
  43.1  A real release channel: release-signed, non-debuggable APK + a fixed updater
  43.2  Weight: R8 + resource shrink + per-ABI artifacts (measured, not hoped)
  43.3  Safety net: crash-loop guard, backup rules, export-everything, first-run
```

| Part | Title | Effort | Status |
|---|---|---|---|
| [43.1](PART_43_1_RELEASE_CHANNEL.md) | Release build, signing, GitHub Release, updater | M | 📋 PLANNED |
| [43.2](PART_43_2_APK_WEIGHT.md) | Size: R8, shrink, ABI splits | M | 📋 PLANNED |
| [43.3](PART_43_3_LAUNCH_SAFETY.md) | Crash loop, backup, export, permissions | S/M | 📋 PLANNED |

## The six hard findings (evidence, read 2026-09-10)

1. **Testers get a debuggable build.** CI's only app task is
   `gradle :app:assembleDebug` (`.github/workflows/build-apk.yml:30`, via the
   `gradle-bootstrap` bridge), and `README.md` tells users to install exactly
   that artifact from an Actions run. A debug APK means `android:debuggable=true`
   (any adb/root on the device can `run-as com.codeci.ide` and read the app's
   whole sandbox — including a stored GitHub token), no optimisation, and debug
   runtime behaviour. There is no release artifact at all.
2. **A release build cannot currently run in CI.** `build.gradle.kts` release
   `signingConfig` reads `KEYSTORE_PATH` (default `${rootDir}/my-upload-key.jks`)
   + `STORE_PASSWORD`/`KEY_PASSWORD` env — and `my-upload-key.jks` is **not in
   the repo** (verified) with no workflow reference to it. So
   `:app:assembleRelease` in CI would fail on a missing keystore. (The debug
   path works because `debug.keystore` is deliberately pinned and committed.)
3. **The in-app updater points at the wrong release, and installs whatever it
   finds.** `ApkUpdateManager` fetches
   `GET /repos/pabi277/CodeC/releases/latest`, then takes the **first asset
   whose name ends in `.apk`** — no version comparison against
   `BuildConfig`, no size cap, no checksum, no cleanup of
   `cacheDir/updates/CodeC-IDE.apk`. Meanwhile the only releases that exist are
   **`userland-v1` (marked "Latest") and `userland-v2-dev`** — *bootstrap*
   releases — and the workflow's own release step deliberately skips
   `userland-*` tags. So the one button built for "stay current" is pointed at
   a channel that carries no app APK, and it would happily install an
   *older* build if one were attached.
4. **Everything in app storage is backup-eligible — including the GitHub
   token.** `AndroidManifest.xml:46-49` has `android:allowBackup="true"` with
   `fullBackupContent="@xml/backup_rules"` and
   `dataExtractionRules="@xml/data_extraction_rules"`, and **both XML files are
   the untouched Android Studio samples**: `backup_rules.xml` is a
   `<full-backup-content>` with two commented-out lines, and
   `data_extraction_rules.xml` has an empty `<cloud-backup>` with a `TODO` plus
   a fully commented-out `<device-transfer>` block. So nothing is excluded:
   `files/usr` (the whole downloaded toolchain + dpkg state + installed
   packages), `files/home`, `files/CodeC/{projects,temp,modules,tcc}`,
   `files/git-askpass.sh`, `files/crash-log.txt` **and**
   `files/datastore/settings.preferences_pb` — the DataStore that holds
   `git_token`. Hundreds of MB of device- and ABI-specific state in a cloud
   backup is at best a failed backup; restoring it onto a different-ABI phone is
   a hole shaped exactly like `$PREFIX`; and a token quietly riding to another
   device is the one thing `GitRedactor` exists to prevent elsewhere.
5. **Uninstall deletes their work, and nothing says so.** Projects live under
   `filesDir/CodeC/projects` (`ProjectManager.projectsRoot()`), so uninstalling
   wipes every project (the `debug.keystore` note in `build.gradle.kts:62-79`
   records exactly this hazard from the *developer* side: an incompatible update
   → uninstall → whole sandbox gone). Per-project ZIP export exists
   (`ProjectTransfer.exportZip` + `exportZipToCache`, driven by
   `FileManagerScreen`'s `CreateDocument("application/zip")` launcher) but there
   is no "export everything", and the first-run flow never mentions it. Note the
   trap for whoever implements it: `FileManager.projectDirCandidates()` also
   lists `getExternalFilesDir(null)/CodeC/projects`, so "all projects" means
   both roots or it silently omits some.
6. **Four ABIs declared, two ABIs actually supported, and the biggest payload
   is not split-filtered.** `build.gradle.kts:42` lists
   `arm64-v8a, armeabi-v7a, x86_64, x86`, `isMinifyEnabled = false`
   (`:86`), `isCrunchPngs = false` (`:85`), and `app/proguard-rules.pro` is the
   untouched template. 24 847 906 B today (run `34399227052`). Measured payload:
   `assets/tcc/{arm64-v8a 3.7 M, x86_64 3.6 M}` +
   `jniLibs/{arm64-v8a,x86_64}/libtcc.so` (581 600 + 405 352 B) +
   `assets/textmate` 2.3 M. Two consequences 43.2 owns: **`assets/` is not
   filtered by `splits.abi`** (so per-ABI artifacts still carry every ABI's TCC
   runtime unless a packaging exclude or per-flavor `assets.srcDirs` is added),
   and `EmbeddedCompiler.ABI_DIRS` covers only arm64+x86_64 — so on an
   `armeabi-v7a` device the app carries ~7.3 MB of C runtime it cannot use and
   has no built-in compiler at all. Both are fixable and neither is visible
   without measuring.

Also worth stating as a *non*-defect: `targetSdk = 28` is **deliberate** and
documented (API 29+ SELinux denies exec'ing app-data binaries; Termux uses the
same compatibility mode), and it is the reason CodeC is distributed from GitHub
and not Google Play (Play additionally demands an AAB, API-36 targeting from
2026-08-31, a privacy policy, and — for new personal accounts — **12 opted-in
closed-testers for 14 continuous days**). Phase 43 records that trade-off so
"just publish it on Play" gets an answer with numbers, and keeps GitHub
Releases as the channel.

## Checklist reviewed, with verdicts

| Candidate | Verdict | Where |
|---|---|---|
| Release signing + non-debuggable artifact | ✅ **do** | 43.1 |
| Fix the updater (version check, channel, digest, cleanup) | ✅ **do** | 43.1 |
| Release notes / known-issues page for testers | ✅ **do** | 43.1 |
| R8 + `shrinkResources` | ✅ do, measured; `proguard-rules.pro` is the untouched template today | 43.2 |
| Delete provably-unused dependencies (OkHttp + logging-interceptor, `material-icons-extended`) | ✅ do, before shrinking anything | 43.2 |
| Permission diet (`ACCESS_WIFI_STATE`/`WAKE_LOCK`/`VIBRATE` have no reader; `MANAGE_EXTERNAL_STORAGE` genuinely does) | ✅ do, with the honest sentence instead of a boilerplate one | 43.3 |
| CI artifact hygiene: `CodeC-IDE` **and** `CodeC-Bench` are both downloadable, and the bench block's own comment says "REMOVE THIS BLOCK when Phase 28 closes" | ✅ do — a tester installing `CodeC-Bench` is a support ticket about a different app | 43.1 |
| ABI splits (per-device artifacts) | ✅ do | 43.2 |
| PNG crunch (revisit `isCrunchPngs=false`) | ⚠ evaluate in 43.2, keep the reason if it must stay off | 43.2 |
| `allowBackup` / backup rules | ✅ fix | 43.3 |
| Crash-loop safe mode + hand-off to feedback | ✅ do | 43.3 |
| Export-all-projects ZIP | ✅ do | 43.3 |
| First-run permissions explainer (what the app can and cannot touch) | ✅ do (3 lines, no dialog storm) | 43.3 |
| "Check for updates" one-liner in About | ✅ falls out of 43.1 | 43.1 |
| Privacy policy / no-telemetry statement | ✅ one paragraph in About + the release text | 43.3 |
| Licenses screen | ✔ **already** (Phase 30 snippets + Phase 37 zxing lines in About) — no work | — |
| Version-in-About (CI run number) | ✔ already (Phase 29's lesson) | — |
| Feedback channel | → **Phase 41** (not duplicated here) | 41 |
| Localization / translations | ❌ defer — one language, one audience; a machine-translated UI is worse than none | — |
| Onboarding tour with coach marks | ❌ defer — Phase 33.1's three starter tiles + a runnable demo project already do the job; a tour would be the 4th first-run interruption | — |
| In-app "what's new" changelog popup | ❌ reject — the release notes page exists; a popup steals the first launch | — |
| Analytics / crash upload | ❌ reject — offline-first is the product; the crash record + Phase 41's chat is the telemetry, human-powered | — |
| Play Store listing assets (feature graphic, screenshots) | ⚠ defer with Play (see the targetSdk law above) | 43.1 note |
| Auto-updater that installs silently | ❌ reject — Android won't allow it quietly, and a phone IDE must not replace its own binary behind the user's back | — |
| F-Droid metadata (`Repo.yml`, anti-tracked-libs) | ⚠ optional; needs a release-signed source build, so it rides *after* 43.1 — recorded, not planned | — |

## Exit condition (whole phase; the owner's own install test)

```text
1. `git tag app-v1.3.17 && git push --tags` produces a GitHub Release with
   `CodeC-IDE-1.3.17-arm64-v8a.apk` (+ the other ABIs, and a universal one)
   attached, release-signed, NOT debuggable, with written release notes.
2. On a phone: uninstall CodeC → install that APK → every first-run flow works
   (bootstrap download, permissions, a demo project runs) → then
   Settings → Check for updates says "up to date". Downgrade check: installing
   an OLDER artifact over a newer one is refused by the app's own updater and
   says why (no silent downgrade).
3. `adb shell dumpsys package com.codeci.ide | grep flags` shows no DEBUGGABLE
   flag; the APK installs on API 24, 26 and a current device.
4. With R8 on, every language path still works on device (TCC compile, clang
   module, Python/Node run, LSP servers, TextMate colouring, sora editor,
   preview, packages install) — measured artifact bytes recorded before/after
   in the part doc, and the delta is reported to the owner.
5. Backup: `adb backup`/device-transfer no longer tries to move the userland;
   no `allowBackup` warning in lint.
6. Crash-loop guard: a test crash on launch twice leaves the app unusable today;
   after 43.3 the third launch opens in safe mode with the crash text and a
   one-tap hand-off to Phase 41's report — and the user's files are intact.
7. Uninstall safety: "Export all projects" produces a ZIP that re-imports into
   a clean install and the files are byte-identical (spot-check 3).
PASS = all seven, on the owner's device + one other if available. 4 is the one
most likely to bite, so it is the one that gets the most test time.
```

## Risks to watch (multi-device round)

- **R8 removing something that looked unused** — the realistic victims are
  sora-editor's component/plugin entry points, the TextMate grammar/theme
  loader, flexmark/`org.json`-style reflection, DataStore serializers, and
  Compose-only classes referenced from `AndroidManifest`-adjacent XML. Rule
  for this phase: **keep rules minimal and named**; every added `-keep` needs a
  comment saying which crash it prevented, or it becomes permanent weight.
- **A debug→release signing change is an uninstall for existing testers** (same
  signature = same key forever; a *new* upload key means they lose the app's
  data). This must be said in the release notes *before* the switch, and the
  upload key backed up in a password manager, because **losing it means no
  updates for anyone, ever**.
- **Per-ABI artifacts multiply what testers can install wrongly** (x86_64 on an
  emulator, armeabi-v7a on an old phone) — the release text must name which
  file to pick, and About should show the device's ABI (already available via
  `DeviceDiagnostics.abiSummary()`), so a mismatch is diagnosable in one
  screenshot.
- **A universal APK is the safe default** if the owner wants one link for
  everyone: 43.2's exit condition keeps `CodeC-IDE-universal.apk` as the primary
  asset and the split ones as "advanced".

## Deferred, recorded on purpose

- **Play Store** (see the `targetSdk = 28` law above) — not a "later polish"
  item, a *different product decision*: to be on Play CodeC must give up
  exec'ing downloaded compilers or move to a different execution strategy
  entirely. If the owner ever wants Play, that becomes its own phase with that
  question answered first.
- **An `update.json` manifest** instead of GitHub's release API — a server to
  run, for no benefit while GitHub is the channel.
- **Delta/patch updates** — meaningless at these sizes for a hand-full of
  testers.
- **App Bundle (`.aab`) for direct install** — you cannot install an AAB, so it
  would be a file nobody can use; `bundletool`'s universal-APK output is
  covered by 43.2 instead.
- **Dependency audit / SBOM tooling** — worth doing before a *public* release
  to a stranger audience; recorded as the first item of whatever phase
  follows, not folded into a beta-prep phase.
