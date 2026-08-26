# Phase 4 Part 4.8 — Android-integration slice 2 (notifications)

**Status: 🚧 IN PROGRESS (2026-08-26).** Decision D1 is locked; code and
host tests are written; the CLI was validated end-to-end with a local
fake-app harness (real `sh`, real OSC bytes, including the Android 13+
permission dance) and CI is green (run `32920735841`: assemble + unit
tests + lint). Only the **device transcript is pending** — this record
will be updated with it.

Part 4.7 already established the reusable `CodeCApi` bridge (`OSC 1337;
CodeCApi:<op>:<req>:<res>BEL` + app-private files under
`$PREFIX/tmp/codec-api`). Per the 4.7 §8 recipe, 4.8 adds **one capability**:
one wire op group, one CLI script, one `BOOTSTRAP_VERSION` bump — and, as
planned, it exercises the **runtime-permission path** that 4.7 deliberately
deferred.

---

## 1. Decision D1 — capability: notifications

| Candidate | Why not now |
|---|---|
| **notifications** ✅ chosen | each new capability should add a *new* bridge concern; notification permission is the runtime-permission path 4.7 explicitly deferred. Also genuinely useful from a C IDE (`cc` finished / `pkg` job done from a terminal). |
| vibrate | needs `VIBRATE` permission but has near-zero value in a shell; no richer flow than notifications. |
| share sheet / open URL | requires launching an Activity from the bridge; keep for a later slice (4.9+). |
| toast | no permission, but a toast is invisible unless the terminal is on screen — notifications are the better primitive. |

Scope discipline (standing rule): **only notifications** in this part. No
vibrate/share/open-URL/notification-channel-settings UI.

## 2. Protocol additions

Wire ops (same OSC shape as 4.7, same `codec-api` dir, same path
confinement):

| CLI | Wire op | Payload | Response |
|---|---|---|---|
| `codec-notify send TITLE [BODY]` | `notify.send` | file: first line = title (trimmed), remainder = body | `OK`, or `ERR:...` / `NEED_PERMISSION:...` |
| `codec-notify clear` | `notify.clear` | none | `OK` |
| `codec-notify status` | `notify.status` | none | two lines: permission + channel |

- `send` joins argv with spaces: **the title must be quoted as one argv**
  (`codec-notify send "Build done" "3 files compiled"`).
- Title empty → `ERR:notification title is empty`. Payload > 8 KiB →
  `ERR:notification content too large (max 8192 bytes)`.
- `NEED_PERMISSION:<permission>` is a **marker, not a result**: the CLI
  prints a hint to stderr and keeps polling while the app rewrites the file
  with the real outcome (`OK` / `ERR:...`), ~10s window (200 × 0.05s).

## 3. Permission design (the new bit vs 4.7)

CodeC targets SDK **28 on purpose** (W^X exec-of-app-data, Termux-style), so
on Android 13+ `POST_NOTIFICATIONS` is a **runtime** permission even though
the app does not target 33:

1. `codec-notify send` arrives → `CodecApiBridge.handle` checks
   `NotificationManagerCompat.areNotificationsEnabled()`.
2. Disabled + API ≥ 33 → the bridge **creates the channel**
   (`codec-terminal`, importance default; for a targetSdk ≤ 32 app the
   system shows the permission dialog on first channel creation while the
   activity is started), writes `NEED_PERMISSION:android.permission.POST_NOTIFICATIONS`
   into the response file, and emits the request through the
   activity-scoped `TerminalViewModel` flow.
3. `TerminalScreen` forwards it to `MainActivity.requestNotificationPermission`,
   which parks the request and launches
   `ActivityResultContracts.RequestPermission`.
4. The launcher callback calls `CodecApiBridge.resumeAfterPermission`, which
   **atomically rewrites the response file**: granted → `OK` (the CLI's
   waiting loop stops); denied → `ERR:notification permission denied — enable
   notifications in Android Settings > CodeC > Notifications` and the CLI
   exits 1 without posting anything.
5. Pre-33 devices with notifications disabled in Settings → immediate
   `ERR:` guidance (no runtime permission exists there).

**Denial finality (recorded from Android docs):** for an app targeting
≤ 32, one "Don't allow" is **final** until reinstall or targetSdk ≥ 33 —
the bridge must therefore *never* silently retry; the error directs the user
to Settings. `notify.clear`/`notify.status` need no permission and are not
gated.

## 4. Implementation map

- `CodecApiProtocol.kt` — `Op` += `NOTIFY_SEND/CLEAR/STATUS` (+
  `isNotifyOperation`), `MAX_NOTIFY_BYTES`, `NEED_PERMISSION_PREFIX`,
  `permissionNotice()`.
- `CodecApiBridge.kt` — `NotifyOps` adapter (android-free), permission gate
  in `handle` + `onPermissionRequired` callback, `resumeAfterPermission`
  (activity result), pure `resumeResponse` for host tests; Android side:
  channel creation, `NotificationCompat` post with BigText style + tap
  opens MainActivity, `manager.cancel` for clear, status lines.
- `ShellEnvironment.kt` — `notifyScript()` (same `/dev/tty`-first attempted
  write + stdout fallback as 4.7), written in `ShellBootstrap.prepare`,
  `BOOTSTRAP_VERSION` 23 → **24**.
- `TerminalViewModel.kt` — `notificationPermissionRequests` SharedFlow fed
  by the bridge callback (activity scope, 4.7 F2 pattern).
- `TerminalScreen.kt` — collector forwarding to the activity (4.7 storage
  pattern).
- `MainActivity.kt` — `RequestPermission` launcher, parked request,
  `resumeAfterPermission` on result.
- `AndroidManifest.xml` — `<uses-permission android:name=
  "android.permission.POST_NOTIFICATIONS" />` (no-op pre-33).
- Tests: `CodecApiProtocolTest` (+notify ops, marker), `CodecApiBridgeTest`
  (+send/clear/status/resume/denial/oversize/empty-title),
  `ShellEnvironmentTest` (+script content + full permission-dance round
  trip + denial round trip).

## 5. Evidence so far

### 5.1 Host-side CLI harness (no JDK on the host; CI compiles the APK)

Generated `codec-notify` exercised under real `sh` with a fake app (byte
at a time):

- permission dance (`NEED_PERMISSION` → hint → `OK`): **PASS**, exit 0;
- denial (`ERR:...`): **PASS**, exit 1, raw `ERR:` never printed;
- `clear` → `OK`, `status` → status text, usage error → exit 2,
  timeout → exit 3: **PASS**;
- PTY run (controlling terminal): OSC reached `/dev/tty` (no stdout
  fallback), hint + final `OK`: **PASS**;
- `codec-clipboard` regression harness (get/set/clear/status/ERR + piped
  get): **PASS**.

### 5.2 CI (run [`32920735841`](https://github.com/pabi277/CodeC/actions/runs/32920735841), green — 2026-08-26)

The legacy workflow invokes `gradle :app:assembleDebug`, which the
`gradle-bootstrap` compatibility bridge turns into
`:app:assembleDebug :app:testDebugUnitTest :app:lintDebug` — so the whole
4.8 test suite compiled **and passed** (the first push, `e20d2d8`, caught a
real test-compile error this way: `NotifyOps({}, {}, …)` send lambda arity;
fixed in `c90965e`). APK artifact `CodeC-IDE` produced.

### 5.3 Pending — device transcript (user's phone)

- `notify status` (permission off) → `notify send` → **system dialog** →
  allow → notification visible + tap opens the app → `notify status` shows
  enabled → `notify clear` →
  denial-path check (if the owner wants it: Settings → turn off → `send`
  → `ERR:`).

## 6. Invariants maintained from 4.7

- Both paths are direct children of `$PREFIX/tmp/codec-api` (canonical).
- Response written via `.partial` + atomic rename; `res="${req}.out"`
  derived, never pre-created.
- OSC is emitted as real ESC/BEL bytes (single backslash in source).
- Requests are consumed in activity scope (no drops when the Terminal tab
  is not composed); the UI forwarding of a permission request still lives in
  `TerminalScreen` (same limitation as storage — command is meant to run
  from the terminal).
- No new dependency (NotificationCompat via existing `androidx-core-ktx`).
