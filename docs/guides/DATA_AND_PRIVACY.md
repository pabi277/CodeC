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

## The AI helper (Phases 76–77) — what it sends, where, and what it keeps

The AI helper (setup in the side panel's ✨ slot; chat in a bottom sheet opened by a small floating button) is an optional, read-only Gemini helper that uses
**your own** Google Gemini API key. Your use of Gemini is covered by your own
agreement with Google ([Gemini API terms](https://ai.google.dev/gemini-api/terms)).

- **Sent only when you tap Send** (or **Test connection**, which sends one
  fixed line and no code). Every request is shown first, word for word: the
  selection you chose or the tail of a failed run, plus CodeC's fixed
  instruction. Nothing is sent in the background.
- **Sent only to** `generativelanguage.googleapis.com`, with your key in a
  request header (never in the URL), and `store:false` so Google does not keep
  the conversation server-side. On Google's free tier Google may use what you
  send to improve its products and human reviewers may read it; the panel says
  so at setup and in every preview.
- **Kept on the phone:** your key, encrypted with an Android Keystore
  AES-256-GCM key, in `no_backup/ai/` — never backed up, never written in
  plain text; if it can't be decrypted it is deleted and you are asked again.
  Beside it, the non-secret model name, the date you confirmed 18+ and
  Google's terms, and (Phase 77) where you left the floating AI button and
  whether it is shown — and how the chat sheet treats an open Output panel — non-secret layout values that **Delete key** keeps. **Delete key** removes the key, its Keystore entry and that
  confirmation.
- **Not kept:** questions and answers live in memory only and vanish on New
  question, a project switch, or when the app closes.
- **Not available** in single-file mode — only inside an open CodeC project.
- Feedback reports scrub Google API keys (`AIza…`) and your stored key.

## Permissions — every one, why, and the code that uses it

| Permission (short name) | Why it exists (one line) | Reader in `app/src/main/java` |
|---|---|---|
| `INTERNET` | Repo/package downloads, git clone/fetch/push, the SHA-256-verified updater (42.1), the LAN dev-server surface, the AI helper's Gemini requests (Phase 76, only on Send / Test connection) | `HttpURLConnection` |
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
- `no_backup/ai/` — the AI helper's encrypted key + its non-secret settings
  (Phase 76); never backed up

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
