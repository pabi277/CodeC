# CodeC — data & privacy (what the app touches, and what it never does)

> Phase 42.3. Two claims CodeC can actually make, one honest correction,
> and the permission table a build test pins against the manifest
> (`app/src/test/java/com/codeci/ide/ManifestPermissionsTest.kt` — a
> permission with no row here, or a row with no reader in
> `app/src/main/java`, fails the build). This page is why the About screen
> can say these things with a straight face, and what an F-Droid-style
> reviewer reads first.

## The two claims — and how to check them yourself

1. **No telemetry.** CodeC contains no analytics, crash-reporting, ad, or
   tracking SDK — no Firebase, no Crashlytics, no AdMob, no amplitude
   (verify: `grep -ri "firebase\|crashlytics\|admob\|amplitude" app/`).
   There is no statistics screen, no "anonymous usage" toggle, nothing to
   opt out of. A crash log exists (see below) and it leaves the device
   ONLY when you choose a Share/Copy/Send-report action.
2. **Nothing leaves the device unless you start it.** Every outbound
   connection CodeC makes is one you triggered, listed in the table below
   under INTERNET. If you never clone/fetch/install/update or send an AI
   helper request, CodeC never
   sends a byte (verify: watch a cold start + a file open through a proxy
   or logcat — only `api.github.com`/your git host/curl mirrors appear,
   and only from the actions named).

## The honest correction — yes, with ONE approval it can read your files

"CodeC cannot read your files" would be **false**. The honest sentence:

> **With your approval, CodeC can open any folder on shared storage;
> decline it and it still works, one folder at a time through the picker.**

The all-files grant (`MANAGE_EXTERNAL_STORAGE`) exists because a mobile
IDE asked "open this project folder" cannot ship folder-by-folder SAF
scans only — builds (`gcc src/*.c -o app`) touch the whole tree. You give
it once, in Android Settings, and you can take it back any time without
breaking already-open projects. **42.3 does not hide this row:** Settings
→ About → Privacy & permissions lists it first.

## First-run privacy acknowledgement (2026-09-30)

A fresh install shows a short introduction before entering the editor. Its final
screen presents the following acknowledgement: projects live in app storage by
default; CodeC has no ads, analytics, tracking or automatic crash-report
uploads; Git/package/update network work starts only when the user starts it;
shared-folder access is optional; and crash records stay local unless shared by
the user.

This is an acknowledgement of that plain-language summary, **not** a claim that
CodeC has a separate Terms of Service. It grants no Android permission and
starts no network request. Outside the crash-recovery safe-mode bypass, users
can skip the educational pages but cannot skip the summary acknowledgement.
Safe mode intentionally bypasses onboarding and sample seeding so recovery is
not blocked. **Settings → About → Replay the CodeC introduction** shows the flow
again on the next launch. The full permission table and
the implementation behind every claim remain below.

## CodeC AI (Phases 76–86 / 82B) — what it sends, where, and what it keeps

AI is optional, set up in ✨ home and used in the floating button's chat sheet.
**Your own** provider key is required. Gemini is the default; NVIDIA Build is an
explicit manual selection for **internal testing/evaluation only, not production**
(including generated output), under its [API Trial Terms](https://assets.ngc.nvidia.com/products/api-catalog/legal/NVIDIA%20API%20Trial%20Terms%20of%20Service.pdf).
Google use remains under your [Gemini API agreement](https://ai.google.dev/gemini-api/terms).
Accepting Google terms does not accept NVIDIA terms; each key needs its own
adult/terms confirmation. There is no bundled/shared key or remote proxy.

- **Only the task you start:** Send on a preview approves the actual named
  provider/model and exact system instruction + user string. Selection/error
  helpers send selected code or a bounded failed-run tail. Project tasks send
  a bounded map and admitted text/file ranges; CodeC's own filter excludes
  `.env`, `.npmrc`, private keys, `.git/`, `.codec/`, symlink escapes and build
  output. Read-only agent follow-ups after task Send may send admitted file
  results without a fresh tap, as disclosed in the memory-only timeline.
  Each turn's exact two strings are there. Every run and file Apply still needs
  separate approval; nothing schedules or resumes a hidden task after Stop,
  project change or app death.
- **Retry and Continue are disclosed:** a pre-text rate failure may resend the
  **same approved request once**, with a visible Stop-cancelable countdown.
  Provider minimum delays are honored, not shortened; daily-without-reset and
  waits over 120s do not get an early retry. No replay after partial output.
  Continue builds another exact preview and still needs Send; combined answer
  ≤64,000 chars and eight continuations. Global per-request caps are 32,768
  output tokens / 48,000 visible chars (remaining room limits Continue).
  Larger caps can cost more time/tokens/quota, not increase free allowances.
- **Test connection** is its own disclosed tap and sends only a fixed one-word
  request, no code or project. It uses the same cancelable one-retry policy.
  NVIDIA 202/pending or empty responses are not reported as answered; CodeC
  does not poll a pending invocation.
- **Changes only on reviewed Apply:** the model proposes local text edit/create/
  delete diffs; you select files and tap Apply through `AiEditApplier` or Reject
  everything. An agent can request CodeC's normal **RUN ▶** pipeline, but cannot
  run before you approve that exact action; Skip runs nothing. Only bounded
  real output/exit codes return to the selected model. No terminal typing,
  package installs or automatic Git stage/commit. Provider/model capability
  flags never grant additional file/tool/run permissions.
- **Two recipients, never fallback:** Gemini uses
  `generativelanguage.googleapis.com` with a header key and stateless requests
  (`store:false`, no shared conversation handle). This is **not zero retention**:
  free-tier Google may use input/output for product improvement or human
  review. NVIDIA uses `integrate.api.nvidia.com/v1/chat/completions` with Bearer
  header and only explicit messages, no conversation id. NVIDIA is a separate
  organization, not Google; its trial terms allow retention exceptions/security
  or abuse logging. No unsupported Google `store` flag is sent to NVIDIA.
  **Do not send secrets, confidential/sensitive material or personal data.**
  The UI repeats recipient/terms/data-use notes. Failures never silently send
  your project to another provider; provider/model changes are manual and
  require a fresh preview. No availability, unlimited-use or quality guarantee.
- **Kept on the phone, outside backups:** independent AES-256-GCM Keystore
  aliases and ciphertext files `no_backup/ai/gemini_key.bin` and
  `nvidia_key.bin`; never plaintext. Non-secret per-provider model, terms version
  and acceptance time sit beside existing floating-button/sheet layout values.
  Existing Gemini slot names stay compatible. Serialized, atomic metadata writes
  preserve the other slot/layout even with concurrent store instances. Delete/decrypt failure removes
  only that provider's key/alias/acceptance, preserving the other credential and
  layout; key deletion also clears the existing last-task AI undo journal.
- **Undo journal:** only the last applied task's bounded preimages (≤5 files,
  ≤256 KB) at `no_backup/ai/undo/<project>/task.journal`, outside the project and
  excluded from backups. Deleted on undo/replacement/project deletion or any
  provider key deletion; user-edit conflicts still prevent overwrite. Undo
  reverses AI file changes, not runs/network/package/Git side effects.
- **Level 9 task memory — narrow D6 exception:** a bounded device-local cache of
  admitted non-secret file snapshots plus structured findings, decisions and
  plan may persist at `no_backup/ai/task/<project>/task-memory.bin`. Android's
  no-backup directory excludes it from Auto Backup; it is outside the project
  tree, secret-filtered before writes, and capped at five files / 160 KiB of file
  content / 256 KiB total. Dirty-buffer text is never persisted. Project deletion
  and any provider-key deletion clear it. No raw chat, prompt, answer, map,
  tool result or timeline is stored. The persistent-off path keeps only the
  current task's in-memory state; the user-facing switch is reserved for Level 10.
- **Not kept:** questions, replies, maps, timeline, tool/run results, countdowns
  and active provider selection are memory-only. Clear/New question/project
  switch/app death discard them; reopening defaults selection to Gemini, never
  resumes a request. Saved per-provider credentials/model/layout are settings,
  not task history. Single-file mode has no AI access.
- **Support export:** feedback scrubs both stored key literals and common
  Google `AIza…` / NVIDIA `nvapi-…` shapes from included log/crash text. No key
  is placed in AiUiState, URLs, prompts, project files or app logs. Reports still
  leave only when you choose to share them; inspect before sharing.

Phase 86 adds the owner-authorized, bounded task-memory exception described
above. Source implementation is on the session branch; local host pre-validation
passed 72/72 selected host methods across eight classes. Build APK run [`37106545726`](https://github.com/pabi277/CodeC/actions/runs/37106545726) is green on fix commit `ddb75d3`; it followed and corrected the three regressions found by run `37106180481`. The focused host harness is not the full Gradle/Android suite. Formal device acceptance remains **POSTPONED
until Level 12**, not passed. The existing
[Phase 82/82B ledger](../phases/03-editor/chat-phase82/README.md) records its
provider/optimization state; [Phase 86](../phases/03-editor/chat-phase86/README.md)
is the task-memory implementation and verification ledger. This does not claim
successful Keystore/live-vendor/device acceptance or production NVIDIA
entitlement.

## Permissions — every one, why, and the code that uses it

| Permission (short name) | Why it exists (one line) | Reader in `app/src/main/java` |
|---|---|---|
| `INTERNET` | Repo/package downloads, git clone/fetch/push, the SHA-256-verified updater (42.1), the LAN dev-server surface, CodeC AI Gemini/NVIDIA requests (disclosed task Send / Test; scoped read follow-ups and one visible rate retry) | `HttpURLConnection` |
| `ACCESS_NETWORK_STATE` | "Is any network up?" before fetches; the LAN-address hint | `ConnectivityManager` |
| `ACCESS_WIFI_STATE` | The classic Wi-Fi fallback for `LanAddressProvider` (37.1) | `WifiManager` |
| `READ_EXTERNAL_STORAGE` | Opening your project folders on legacy devices | `READ_EXTERNAL_STORAGE` |
| `WRITE_EXTERNAL_STORAGE` | Same, **capped `maxSdkVersion="32"`** (scoped-storage boundary) | `WRITE_EXTERNAL_STORAGE` |
| `MANAGE_EXTERNAL_STORAGE` | Whole-tree builds over a project you approved; the honest-correction row | `isExternalStorageManager` |
| `REQUEST_INSTALL_PACKAGES` | **Only** the two install paths you start: the userland bootstrap and the verified app update | `canRequestPackageInstalls` |
| `WAKE_LOCK` | A running terminal/compile must not die with the screen off | `PowerManager` |
| `FOREGROUND_SERVICE` | The visible run service that keeps a build alive | `RunForegroundService` |
| `FOREGROUND_SERVICE_DATA_SYNC` | The same service's API 34+ type | `startForegroundService` |
| `POST_NOTIFICATIONS` | codec-notify ("build finished", "run exited") — works denied, just silently | `NotificationManagerCompat` |
| `VIBRATE` | codec-vibrate haptics (terminal bell, key feedback) — works denied | `Vibrator` |
| `CAMERA` | The photo-capture bridge asks Android to take a picture into a scratch file you then attach — **hardware marked optional**; camera-less devices install fine | `TakePicture` |
| `com.termux.permission.RUN_COMMAND` | The optional Termux compiler bridge (declared by Termux, guarded everywhere) | `RUN_COMMAND` (CompilerService) |

The data-sewer drain is simply not there: no `SMS`, `CONTACTS`,
`LOCATION`, `GET_ACCOUNTS`, `RECORD_AUDIO`, `BLUETOOTH`, `READ_PHONE_STATE`
in the manifest — the test above fails if any ever is.

## What the backup/restore actually carries (Phase 42.3 §1)

- **Carries:** `CodeC/projects/**` — your source, and ONLY your source.
  That one include is the whole backup scope.
- **Never carries:** `CodeC/tmp/**` (temp scratch), `CodeC/tcc/**`
  (build outputs), `usr/**` (the downloaded userland — re-downloaded
  after restore), `home/**` (its dpkg state), `CodeC/modules/**`
  (re-downloaded), `crash-log.txt` (a crash record belongs to the build
  that wrote it, not to the next install), and
  `datastore/settings.preferences_pb` — the settings DataStore, which is
  where the GitHub token lives: **your token never rides a backup; a
  fresh install asks you to sign in again, and that is intentional.**

## The crash log — yours, and only sent when you say so

- Written to `files/crash-log.txt` (a text file) ONLY on an uncaught
  crash: the Java stack trace + device line (model, Android, build
  fingerprint, ABI, versionName). No location, no contacts, no account —
  look at it before you share it: Settings → Feedback & support → Open →
  or the 📧 brand/icon after a crash. Deleting the file deletes the evidence.
- **42.3:** the crash overlay offers Send a report, which opens the
  feedback screen with the crash record + log ticked in advance.
  The app itself still sends nothing automatically.
- Two crashes in a row → 42.3's crash-loop guard offers "Starting without
  my settings" (safe mode: no saved preferences, no saved session state —
  never your files; safe mode is temporary for one launch).

## Where things live on disk

- `files/CodeC/projects/` — your work, the one thing that must never be
  lost (42.3 §3 export: the hub ⋮ menu's "Export all projects" makes one
  ZIP, re-importable into a clean install byte for byte)
- `files/CodeC/tmp/` — scratch; wiped by 39.1's temp GC
- `files/CodeC/tcc out/` — build output; clears with your project clean
- `files/CodeC/usr/` + `files/CodeC/modules/` — the compiler userland +
  packages; re-downloadable
- `no_backup/ai/` — independently encrypted AI provider keys, terms/model/layout settings
  and the bounded last-task undo journal (Phases 76–82B); never backed up

## How to verify a claim in this document

- Manifest ↔ table: `ManifestPermissionsTest` (host test) + `grep -n
  uses-permission app/src/main/AndroidManifest.xml`
- No telemetry: the grep at the top of this file + the dependency list in
  `app/build.gradle.kts` (no third-party embeds beyond the ones with
  About/licence rows)
- Backup contents: `app/src/main/res/xml/backup_rules.xml` +
  `data_extraction_rules.xml` + `BackupRulesTest`
- Crash log contents: `files/crash-log.txt` after a crash — plain text,
  read it yourself
