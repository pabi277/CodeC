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

### 5.3 First on-device run (2026-08-26) — permission flow works, two fixes found

User ran the corrected recipe (`codec-notify`, not `notify`):

1. `which codec-notify` → `/data/user/0/com.codeci.ide/files/usr/bin/codec-notify`
   — bootstrap v24 deployed; **PASS**.
2. `codec-notify status` → `notification permission: disabled` /
   `channel: codec-terminal (not created)` — the exact Android 13+ new-install
   state; **PASS**.
3. `codec-notify send "Build done" "3 files compiled"` → the CLI printed
   `Android notification permission: allow it in the dialog (CodeC >
   Notifications)` **~25 times**, then the command ended — **no OK, no
   notification**. Post-run `codec-notify status` showed
   `permission: enabled` + `channel: ready`, and `codec-notify clear`
   returned `OK` — so the permission/channel machinery worked, but the
   send never completed in the CLI's window.
4. Diagnosis: (a) the hint was printed **every 50 ms poll** and the
   permission wait was only ~10 s — too short for answering the system
   dialog (especially since the user may answer it after the CLI has
   already timed out); (b) for a targetSdk ≤ 32 app the Android 13+
   dialog can be **system-owned** (shown on first channel creation), and
   in that path **no `ActivityResult` reaches our launcher**, so the
   parked request was never completed → the CLI timed out.

**Fixes (commit `7a321ad`, CI green run `32922988131`):**
- **F1 (CLI):** the hint is printed **once**, and once `NEED_PERMISSION`
  is seen the poll window extends to ~30 s (600 × 0.05 s); without any
  response file the CLI still gives up after ~2.5 s (no-response case).
- **F2 (activity):** `MainActivity.onResume` now recovers a parked notify
  request: after the dialog closes (or the user returns from Settings)
  it re-checks `areNotificationsEnabled()` and completes the request with
  `OK`/`ERR`, regardless of which dialog answered it. The launcher result
  still wins when it arrives (it clears the parked request first).

### 5.4 Pending — device retest (user's phone)

The device permission is now **enabled**; to re-exercise the dialog, first
turn it off: **Android Settings → Apps → CodeC → Notifications → off**,
then in the terminal:

```sh
codec-notify status        # expect: disabled
codec-notify send "Build done" "3 files compiled"
# dialog should appear; tap Allow; notification should show; tap it -> CodeC opens
codec-notify status        # expect: enabled / channel ready
codec-notify clear         # expect: OK
```

Expected after the fix: exactly **one** hint line, then `OK` once the
dialog is answered. Denial-path check optional (owner decision).

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
