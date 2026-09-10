# CodeC Phase 42.3 — The safety net: crash loops, backup, export, and what we promise

> **Status:** 📋 PLANNED · **Cost:** `[client-only]` · **Effort:** S/M

Three small things that separate "a tool I trust" from "an app I'll delete
after it eats something". None of them is glamorous; item 1 and 2 are the two
highest-value minutes in the whole 38-43 set, because they are the only
protection against a *reputation* event rather than a bug.

## 1. Backup: stop shipping Android's TODO template

**Symptom (evidence).** `AndroidManifest.xml:42-46` declares
`android:allowBackup="true"` with `fullBackupContent="@xml/backup_rules"` and
`dataExtractionRules="@xml/data_extraction_rules"`, and both files contain only
the Android Studio sample's comment (`TODO: declare your rules here…`). The
default that follows is *"back up essentially everything in app storage"* —
which for CodeC means the whole userland prefix: `$PREFIX` with dpkg's
database and installed packages, hundreds of MB, ABI- and device-specific.
Verified 2026-09-10: there is **no** `<exclude>` and no `<include>` in either
file (the `backup_rules.xml` body is a commented-out sample pair, and
`data_extraction_rules.xml` has `<cloud-backup>` with a `TODO` and its whole
`<device-transfer>` block commented out), so the effective rule is "back up
essentially everything in app storage". For CodeC that is
`files/usr` (`ShellEnvironment.PREFIX_NAME = "usr"` — the whole downloaded
toolchain, `HOME_NAME = "home"` beside it, dpkg state and installed packages
included), `files/CodeC/{projects,temp,modules,tcc}`, `files/git-askpass.sh`,
`files/crash-log.txt` and `files/datastore/settings.preferences_pb`. Two failure
modes, both bad: a "restore" onto a different-ABI phone gives the user an
exec'ing-`$PREFIX` shaped hole (the class of bug 33.2.2 and
`EmbeddedCompiler.abiDir()`'s `null` exist to detect), and a cloud backup of that
much data either fails or silently eats the user's quota. There is also a
**security** reading: the DataStore file is where the GitHub token actually lives
(`GitCredentialsStore`'s `GIT_TOKEN` key, on the shared
`preferencesDataStore(name = "settings")` from `ui/theme/ThemeManager.kt:12`),
so **a GitHub token is backup- and device-transfer-eligible today**. (While
reading it: `GitCredentialsStore`'s KDoc names the file
`files/datastore/user.preferences_pb`, which is wrong — the store is named
`settings`. Fix the comment in this part: a security review starts from that
sentence, and a wrong path in it means the reviewer checks the wrong file.)

**Design.** No new dependency, no new API: `res/xml/backup_rules.xml` and
`res/xml/data_extraction_rules.xml` become *real* rules —

```xml
<!-- backup_rules.xml: include, explicitly, ONLY what a person would call
     "my settings and my code" — a file list, not a wildcard -->
<full-backup-content>
  <include domain="file" path="CodeC/projects"/>
  <exclude domain="file" path="usr"/>      <!-- the whole toolchain, always -->
  <exclude domain="file" path="home"/>
  <exclude domain="file" path="CodeC/temp"/>
  <exclude domain="file" path="CodeC/modules"/>
  <exclude domain="file" path="CodeC/tcc"/>
  <exclude domain="file" path="crash-log.txt"/>
</full-backup-content>
```
(`<data-extraction-rules>` gets the same excludes inside `<cloud-backup>` **and**
`<device-transfer>`.) And note what is deliberately **not** in the include list:
`datastore/settings.preferences_pb`, the file that holds the GitHub token.
Including it would restore the user's theme, keys and sign-in — *and the token
with it, into somebody else's cloud*. This part leaves it out on purpose, says so
in `docs/DATA_AND_PRIVACY.md` ("a fresh install asks you to sign in to GitHub
again — that is deliberate"), and lets the device round decide whether the
*device-transfer* path (direct phone-to-phone, no cloud) may carry it while the
cloud path never does.
with the include-list shape enforced by a test rather than reviewed by eye:
`BackupRulesTest` (host, parses the two XMLs — the
`EditorKeySetTest`/`DemoProjectSeedTest` habit of reading real repo files from a
JVM test) asserts (a) `usr`, `home`, `CodeC/temp`, `CodeC/modules`, `CodeC/tcc`
and `crash-log.txt` are excluded **and not** included, (b) no rule includes
`datastore` (the token decision above), (c) `dataExtractionRules` has a
`<device-transfer>` block with the same list (the sample's second file is exactly
where people forget — today its whole block is commented out), and (d) the
manifest still points at both files.
If the include-list turns out not to cover DataStore (which lives in
`data/data/…/datastore/`), the rule adds that domain — decided at
implementation by *testing a real restore* (`adb backup` is deprecated, so the
check is `allowBackup=false` vs the include list, and the honest fallback if
inclusion proves unreliable is **`android:allowBackup="false"` + 42.3's export
feature as the sanctioned path**, which is the safer of the two and the one
this part recommends: a phone IDE that says *"your code leaves this device only
when you export it"* is a better promise than a partial backup).

## 2. Crash-loop guard + hand-off

**What already works (read 2026-09-10, and it is better than expected).**
`MainActivity.installCrashLog()` installs a
`Thread.setDefaultUncaughtExceptionHandler` that appends a header-first record
(`==== <timestamp> thread=<name> ====` + `appendThrowable(frameCap = 80)` + up
to 5 `Caused by:` chains) to `filesDir/crash-log.txt`, bounds the file to 60 KB
keeping the newest records, then hands off to the previous handler.
`ui/crash/CrashReportOverlay` reads the **newest record from its header** (not a
byte tail) on the next launch, shows it *before anything else*
(`MainActivity.kt:266`) with the exception line promoted into the dialog title,
and offers **COPY ALL / Share / Clear** — its own body text says *"please COPY
ALL and paste it into the chat"*. So the crash half of "share-readiness" is
already built; what is missing is the loop guard and the one-tap hand-off.

**Symptom (the remaining gap).** Nothing guards a crash **during startup** (a
corrupt settings row, a half-written project file, a module the new device can't
load) repeated on every launch: the overlay appears again, the user dismisses it,
the app dies again. The options left are "clear data" (losing every project) or
uninstall (also losing every project — finding 5 in this phase's README).

**Design — small, no magic.**
- `StartupWatchdog` (pure `StartupLedger` + a ~4-line hook in the existing crash
  plumbing): write a `starting = true` marker (SharedPreferences — not DataStore,
  which needs a coroutine — synchronously, before `onCreate`'s work) and clear it
  once the main screen has been drawn (the same point where `CrashReportOverlay`
  is mounted today). On launch, if the marker is still set **twice in a row**, the
  next launch runs `SafeMode`: the app starts with (i) the persisted inputs that
  could be the culprit bypassed (theme/editor/terminal settings read from
  defaults **for this session only** — never written back), (ii) projects and
  files fully readable — safe mode is a *reduced startup*, not a different app,
  and (iii) the export row still reachable, so "get my code out" works in the
  worst state the app can be in.
- **Extend `CrashReportOverlay`, do not add a second modal.** It already owns
  this moment and already appears before anything else, so the loop state is one
  more argument to it: `CrashReportOverlay(loopDetected = true)` adds a sentence —
  *"CodeC failed again while starting. Nothing has been changed or deleted."* —
  and one button, **[Try starting without my settings]**; COPY ALL / Share /
  Clear stay exactly as they are. A second dialog stacked on the first is how a
  safety feature becomes the thing people screenshot and complain about.
- **`[Send a report]`** is added to that same overlay *and* to Phase 41's
  section: it hands the crash record to 41.2's feedback screen with both
  attachments pre-ticked — replacing today's manual "copy this and find me in a
  chat" instruction with a tap. The loop closes: the one moment a user is
  motivated to tell you is right after you failed them.
- A `SafeModeBanner` must be dismissible and must never hide the export
  button; and safe mode writes **nothing** back to storage (asserted by test),
  because "repaired by deleting your settings" is what these guards actually do
  when nobody checks.
- Explicitly **not** a watchdog daemon or a restart-service: two counters in a
  file, no background process. (The foreground services stay exactly as 37.3
  designed them.)

## 3. Export-everything, and a first-run sentence about it

**Symptom.** `ProjectManager.projectsRoot()` is `filesDir`-based →
**uninstall deletes every project**, with no warning anywhere. Per-project ZIP
export exists (`ProjectTransfer.exportZip`, `exportZipToCache` for share) but
there is no "all of it", and no discoverability.

**Design.**
- `ProjectTransfer.exportAllZip(out: OutputStream, roots: List<File>)` — no new
  I/O surface: `exportZip(projectRoot, OutputStream)` already exists (with the
  `MAX_ZIP_ENTRIES = 10_000` / `MAX_ZIP_ENTRY_BYTES = 128 MiB` limits 43.1's
  review found, and a `finally` delete on the cache path), so "all" is a loop
  over the roots writing `projects/<name>/…` entries — **exactly the layout
  `importZip(InputStream, destination)` reads** — with the caps becoming
  *budgets shared across projects* (today they are per-project: 12 projects ×
  9 999 entries sail through what no phone should unpack; 43.1 makes the budget
  explicit, this part reuses it). Round-trip is the requirement, not the archive
  format.
- **Two roots, not one — a data-loss trap found while reading.** Projects live
  at `filesDir/CodeC/projects` (`ProjectManager.projectsRoot()`), but
  `FileManager.projectDirCandidates()` also lists
  `getExternalFilesDir(null)/CodeC/projects` (`:51`). An "export all" of
  `projectsRoot()` alone would therefore **silently omit** projects the app has
  been showing from that second root. So `exportAllZip` takes the candidate
  list, not a single path, records which root each entry came from
  (`projects/<root-tag>/<name>/…`), and the host test that pins the trap is:
  two roots, one project in each, export → import → both present.
- The SAF route already exists and is used verbatim: `FileManagerScreen`'s
  `exportLauncher` (`ActivityResultContracts.CreateDocument("application/zip")`,
  `:206-210`) → `viewModel.exportProject(context, name, uri)`. Export-all reuses
  that launcher with one more suggested filename — no new permission, no new
  FileProvider path. Writing to shared storage outside it
  (`DownloadManager.kt:40,49`'s `Environment.getExternalStorageDirectory()/CodeC/…`
  route) is offered **only when all-files access is already granted**, because
  without it the write fails with an opaque `FileNotFoundException`.
- The first-run flow gets one sentence and one button (it already has 37.3's
  notice and 33.1's three tiles — this is *not* a fourth dialog, it is a line
  inside the existing About-first-run card or the storage section): *"CodeC
  keeps your projects in app storage. Uninstalling CodeC deletes them — export
  a backup ZIP first."* Same sentence, in the release notes and in
  `docs/BETA.md`.
- `Storage` section gains the size line 39.1 already adds ("CodeC uses
  X MB: projects / temp / userland"), plus `[EXPORT ALL]`.
- **Exit condition, because this is data safety:** 3 projects + a nested folder
  + a 20 MB asset file → export → uninstall → fresh install → import →
  `diff -r` clean on 5 spot-picked files (one per project + one nested), the
  `.codec/project.json` present, and an over-cap export failing with a visible
  error rather than a partial file.

## 4. What we promise: privacy, permissions, and honesty in About

No new mechanism; three sentences that must exist somewhere a first-time
tester will see them, plus the checks that keep them true:

- **Privacy** (About + release notes): *"CodeC runs entirely on your device. It
  has no analytics, no crash reporting, no ads, no account. The only network
  requests it makes are the ones you start: the bootstrap download, the package
  installers you approve, `git`/`gh` to the repository you configured, and the
  update check if you tap it. If a project code sends data (a URL you write),
  that's your code."* — every clause is falsifiable and matches the manifest;
  the phase adds a test that the **permission list** in the manifest is exactly
  the documented set (below), so the sentence cannot rot.
- **Permissions — first the diet, then the list.** `grep uses-permission` on the
  manifest returns **13**, and this part's first job is to *verify the premise*
  instead of reciting a boilerplate privacy line: CodeC **already declares and
  already offers `MANAGE_EXTERNAL_STORAGE`** — `MainActivity.kt:354-360`,
  `SettingsScreen.kt:846-852` and `ShellEnvironment.kt:1771-1775` each check
  `Environment.isExternalStorageManager()` and route to
  `ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION`, with `READ_EXTERNAL_STORAGE`
  and `WRITE_EXTERNAL_STORAGE maxSdkVersion="32"` as the legacy pair, `CAMERA`
  for Phase 18's `camera.capture` bridge, plus `INTERNET`,
  `ACCESS_NETWORK_STATE`, `FOREGROUND_SERVICE`(+`…_DATA_SYNC`),
  `POST_NOTIFICATIONS`, `REQUEST_INSTALL_PACKAGES`, `com.termux.permission.RUN_COMMAND`.
  So the sentence "CodeC cannot read your files" would be **false**, and the
  honest one is: *with your approval CodeC can open any folder on shared storage;
  decline it and it still works, one folder at a time through the picker.* About
  gets a row for each (one line each, not a dialog storm), including *why* the
  notification exists while a run lives and why the install-permission is only
  for the update path. **Delete only what has no reader:** a first grep finds no
  reference to `ACCESS_WIFI_STATE`, `WAKE_LOCK` or `VIBRATE` in
  `app/src/main/java` — each is removed in this part, or the row in
  `docs/DATA_AND_PRIVACY.md` names the code that uses it (if `WAKE_LOCK` turns
  out to be required by a vendored Termux-derived service, *that* is the reason
  to write down, not to keep it silently). 43.1's revocable per-folder grant
  gets its own row too. And `ManifestPermissionsTest` pins the declared set
  against a table in `docs/DATA_AND_PRIVACY.md`: a permission with no row, or a
  row with no reader, **fails the build** — the only way this list stays true
  after the beta.
- **Licences** — already covered (Phase 30's `LICENSES.md` + snippets, Phase
  37's zxing line in About); 42.3 only adds a `docs/DATA_AND_PRIVACY.md` page
  the About row points to, so the claim has a source and F-Droid-style reviewers
  have something to read.
- **`BETA.md`** (repo + linked from the release notes): the known-issues list
  written for a stranger ("C projects with spaces in the folder name work;
  opening a huge folder may still be slow; here's what to do if it hangs"),
  what a tester should report, and how (Phase 41). This is the difference
  between a beta and a dump.

## Exit condition

```text
1. Backup: with the include-list (or the `allowBackup="false"` variant, if the
   owner picks it), a real device-to-device transfer / `bmgr` run carries NO
   `usr/`, NO `home/`, NO `CodeC/temp|modules|tcc`, NO `crash-log.txt` and NO
   `datastore/settings.preferences_pb` (so no token); a restored
   `CodeC/projects` is byte-identical for 3 spot-picked files; lint's
   `AllowBackup`/`DataExtractionRules` family is silent; and neither XML
   contains the string `TODO` any more.
2. Crash loop: force a startup crash twice (a debug-only test hook, e.g. the
   existing `Codec-Trigger-Crash` mechanism from the device rounds, thrown early
   enough to count as a startup failure) → on the third launch the existing
   crash overlay carries the loop sentence and **[Try starting without my
   settings]**, which boots the app; **[Send a report]** arrives in Phase 41's
   screen with both attachments ticked; every project is still there; a normal
   launch afterwards clears the counter. Safe mode deleted nothing (before/after
   `ls -R` of `files/` on device + the host test that asserts no write-back),
   and exporting from inside safe mode works.
3. Export: the round-trip above, on device, clean.
4. The privacy text, the permission table, the About row,
   `docs/DATA_AND_PRIVACY.md` and `docs/BETA.md` exist and match a `grep` of the
   manifest (the test): every declared permission has a row **and** a reader in
   `app/src/main/java`, and the two claims the app can actually make — no
   telemetry, nothing leaves the device unless the user starts it — hold when
   checked against a proxy/logcat during a cold start + bootstrap + package
   install. A second person reading only the release notes answers "does this
   phone home?" correctly, and answers "can it read all my files?" the honest
   way: only with the all-files grant the user chose to give.
PASS = all four; 2 and 3 are the owner's device, 1 has an on-device half too.
```

## Tests (plan)

- `BackupRulesTest` (host, parses `res/xml/*.xml`) — the four assertions above;
  plus "no `<include domain="external-files">` without a path" (an
  external-files include with no path would drag `usr/` in).
- `StartupLedgerTest` (host, pure, `TemporaryFolder` pattern as used by
  `CEntryWrapperTest`/`ModuleInstallerTest`): first launch → no safe mode; crash once → normal again;
  crash twice → safe mode; successful launch clears the counter; a corrupt
  ledger file → treated as *no* crash (never trap the user in safe mode because
  a counter was unreadable); and "safe mode writes nothing" asserted on the
  pure state machine.
- `ProjectExportTest` additions (host): a fake projects root with 3 projects,
  nested dirs, an over-cap case, a dangling name collision (`a/b` vs `a_b`
  flattening — the same hazard the import side already refuses), and
  **round-trip byte equality** of the entries' CRCs (a test of the zip's
  fidelity, not of zip4j).
- `ManifestPermissionsTest` (host, reads the merged manifest text as source —
  the exact-set assertion behind the privacy sentence).
- `SafeModeBanner`/`AboutText`: string presence + no hardcoded-English
  regressions in the *new* strings (38's audit already established the
  `strings.xml` habit; the new rows go through `stringResource`).

## Sources (record)

- `AndroidManifest.xml:42-46` (`allowBackup`, `fullBackupContent`,
  `dataExtractionRules`), `res/xml/backup_rules.xml` +
  `res/xml/data_extraction_rules.xml` (verified: both are the AS sample with
  `TODO` comments and no rules), the permission set at `:14-27,40`
  (`INTERNET`, `ACCESS_NETWORK_STATE`, `POST_NOTIFICATIONS`,
  `FOREGROUND_SERVICE`(+`…DATA_SYNC`), `REQUEST_INSTALL_PACKAGES`,
  `com.termux.permission.RUN_COMMAND`, `queries` block),
  `ProjectTransfer.{exportZip,exportZipToCache,importZip,importFromUri}` + its
  `MAX_ZIP_ENTRIES`/`MAX_ZIP_ENTRY_BYTES`/`BUFFER_SIZE`,
  `ProjectManager.projectsRoot()` (`filesDir/CodeC/projects`), `AppLogger`
  (1 000-line ring), `SettingsScreen`'s Storage section (`:810-880`: the
  all-files row + the `getExternalFilesDir(null)` path line),
  `MainActivity.kt:125-175` (`installCrashLog`, `appendThrowable(frameCap = 80)`,
  the 60 KB bound) and `:266` (where `CrashReportOverlay()` is mounted),
  `ui/crash/CrashReportOverlay.kt:30-125` (newest-record-from-header read, the
  9 000-char cap, the exception line in the title, COPY ALL / Share / Clear and
  the "paste it into the chat" instruction), `ShellEnvironment`'s
  `PREFIX_NAME = "usr"` / `HOME_NAME = "home"` / `prefixDir(filesDir)` /
  `:1771-1775`, `EmbeddedCompiler.abiDir()` + `ABI_DIRS`,
  `FileManager.projectDirCandidates()` (`:51`, the *second* projects root),
  `DownloadManager.kt:40,49`, `FileManagerScreen.kt:177-210` (the four
  launchers), `GitCredentialsStore` + `ui/theme/ThemeManager.kt:12`
  (`preferencesDataStore(name = "settings")`), and — noting that
  `android:debuggable` is **absent** from the source manifest, which is why
  42.1's exit check tests the built APK's flags rather than trusting the XML, and
  `app/build.gradle.kts:62-79`'s "uninstall wiped it" story — the same failure
  mode, observed from the developer's side, which is why item 3 is not
  optional.
- `adb backup` being deprecated (hence "the honest fallback is
  `allowBackup=false` + explicit export") — Android platform release notes for
  `adb backup/restore` deprecation.

## Deferred / rejected with reasons

- **A full "sync to Drive/Dropbox" feature** — a different product; the ZIP is
  the portable unit, and 43.1's folder picker already gives users a place to
  put it.
- **Restoring projects from an Android auto-backup** — fragile across ABIs and
  versions by construction; explicit export is the promise.
- **A crash-reporting service (Firebase/ACRA)** — see 41's rejection; ACRA (MIT)
  was the closest fit and still needs an endpoint or an email intent, so the
  chat hand-off is the leaner design.
- **Auto-safe-mode after ANY error** — no: only *repeated* startup failure
  triggers it. Turning a one-off crash into a scary screen is how users stop
  trusting the app *and* the banner.
- **A "reset settings" button in Settings** — tempting and cheap, but it is
  the button that deletes someone's LSP config while they're panicking; safe
  mode's *session-only* override is the same relief without the destruction.
  (If the owner wants it after the beta, it belongs next to 39.1's cache
  controls, with a preview of exactly what resets.)
