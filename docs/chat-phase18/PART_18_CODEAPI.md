# CodeC Phase 18 — CodeCApi Device Capabilities & Final System Polish

**Status:** ✅ IMPLEMENTED & CI-GREEN (2026-09-01, `arena/01a05b12-codec` `4460306`, `Build APK` `33468442063` — assemble + unit tests + lint; one red round fixed a lint ERROR with `uses-feature`). **Device recipe §4 pending (owner).** · **Cost:** `[client-only]` · **Depends on:** Phase 7 (Multi-Terminal Routing) + Phase 6 (Terminal UX)  
**Target Files:** `CodecApiBridge.kt`, `CodecApiProtocol.kt`, `ShellEnvironment.kt`, `MainActivity.kt`, `AndroidManifest.xml`

---

## 1. Context & Motivation

Phase 4.7, 4.8, and 5.3 established the in-band OSC 1337 `CodeCApi` protocol bridge (`codec-clipboard`, `codec-notify`, `codec-toast`, `codec-share`, `codec-open-url`, `codec-vibrate`).

Phase 18 completes the `CodeCApi` device capabilities suite (sensors, battery, text-to-speech, camera, intent dispatch) and applies final cross-app polish.

---

## 2. Architectural Design (Decision D1)

### 2.1 Additional `CodeCApi` Protocol Commands

| CLI Script | Wire Operation | Description | Permission |
|---|---|---|---|
| `codec-battery` | `battery.status` | Returns JSON with percentage, charging state, temperature, health | Normal |
| `codec-sensor` | `sensor.read` | Reads Accelerometer, Gyroscope, or Light sensor values | Normal |
| `codec-tts` | `tts.speak` | Speaks text aloud using Android TextToSpeech engine | Normal |
| `codec-camera` | `camera.capture` | Captures a photo and saves to target file path | Runtime `CAMERA` |
| `codec-intent` | `intent.send` | Dispatches Android explicit/implicit Intent | Normal |

### 2.2 Bridge Routing Architecture
- All requests route through `$PREFIX/tmp/codec-api/` with atomic response rename (`.out`).
- Runtime permissions (like `CAMERA`) park the request, trigger Android runtime dialog, and resume automatically upon user choice.

---

## 3. Implementation Steps

1. **Step 1:** Add CLI scripts (`codec-battery`, `codec-sensor`, `codec-tts`, `codec-camera`, `codec-intent`) to `ShellEnvironment.kt`.
2. **Step 2:** Implement protocol handlers in `CodecApiBridge.kt`.
3. **Step 3:** Add permission launcher and handlers in `MainActivity.kt`.
4. **Step 4:** Write unit tests in `CodecApiBridgeFullTest.kt`.

---

## 4. Exit Condition & Verification Recipe

A fresh APK passes the following recipe on device:

```sh
# Setup & CodeCApi Tail Test
# 1. In terminal: codec-battery -> Verify JSON: {"percentage": 85, "status": "charging", ...}
# 2. In terminal: codec-sensor accelerometer -> Verify: {"x": 0.12, "y": 9.81, "z": 0.05}
# 3. In terminal: codec-tts "Hello from CodeC terminal" -> Verify audio spoken.
# 4. In terminal: codec-intent view "geo:0,0?q=restaurants" -> Verify maps app opens.
# PASS
```

Optional runtime-permission check (not in the original recipe; needs a dialog + camera):

```sh
# 5. codec-camera shot.jpg -> grant CAMERA in the dialog -> confirm in the
#    camera app -> Verify: OK:<prefix>/tmp/codec-api/camera/shot.jpg and the
#    file exists (cat, or open it via web preview / file manager).
```

---

## 5. Implementation record (2026-09-01)

**Implemented on `arena/01a05b12-codec`** (owner: "Start 18"), client-only,
no `[repo-build]`, no package-repository changes, no official Termux anything.
Clean-room: battery/sensor/TTS/camera/intent are standard Android framework
APIs (no Termux:API source).

### 5.1 Delivered (spec §3 steps 1–4)

| Spec step | What shipped |
|---|---|
| Step 1 — CLI scripts | `codec-battery`, `codec-sensor`, `codec-tts`, `codec-camera`, `codec-intent` in `ShellEnvironment.kt`, written by `ShellBootstrap.prepare()`; `BOOTSTRAP_VERSION` 26 → 27. Same poll discipline as `codec-clipboard` (50 ms × 50; camera: 50 ms × 1200 + one-shot permission/capture hints). |
| Step 2 — bridge handlers | `BatterySnapshot`, `SensorReading`, `DeviceApiOps` (android-free adapters), `battery.status`/`sensor.read`/`tts.speak`/`camera.capture`/`intent.send` in `CodecApiBridge.execute`; camera park/resume via `handle` → activity → `completeCameraCapture`. |
| Step 3 — activity | `MainActivity`: `RequestPermission(CAMERA)` launcher + `TakePicture` launcher, parked-request state, `requestCameraPermission`/`completeCameraPermission`/`startCameraCapture`/`completeCameraCapture`; `TerminalViewModel.permissionRequests` relay now dispatches by op in `TerminalScreen`. |
| Step 4 — tests | New `app/src/test/java/com/codeci/ide/CodecApiBridgeFullTest.kt` (22 tests) + Phase 18 additions to `CodecApiProtocolTest` (3) and `ShellEnvironmentTest` (1). |

Wire ops (protocol): `battery.status`, `sensor.read`, `tts.speak`,
`camera.capture`, `intent.send`; new caps `MAX_TTS_BYTES` (32 KiB),
`MAX_INTENT_BYTES` (64 KiB), `MAX_CAMERA_NAME_BYTES` (256),
`MAX_SENSOR_TYPE_BYTES` (64); new interim marker `CAPTURING:`.

### 5.2 Design decisions

- **D1 — extend, do not fork:** the five capabilities ride the existing
  Phase 4.7 in-band OSC 1337 `CodeCApi:` bridge and the existing
  `$PREFIX/tmp/codec-api` file/atomic-rename discipline; no new channel.
- **D2 — android-free core:** `DeviceApiOps` mirrors `TermuxApiOps`/`NotifyOps`.
  Validation (payload caps, filename sanitization, action/scheme allow-lists)
  and JSON formatting (`batteryResponse`, `sensorResponse`, `formatDouble`)
  live in `CodecApiBridge` so host tests prove them; the lambdas only touch the
  device.
- **D3 — intent is never explicit:** only implicit `view`/`dial`/`send` are
  allowed; no component/class targeting (a terminal script must not be able to
  start another app's private activity). `view`/`dial` URIs are restricted to
  `http, https, geo, mailto, tel, sms, market`; `send` carries plain text.
  `intent.send` payload = first line action, remainder data (CLI writes it).
- **D4 — sensors:** `accelerometer` (x,y,z), `gyroscope` (x,y,z), `light`
  (lux). One sample via `SensorEventListener` + `SENSOR_DELAY_UI` on the main
  looper, bounded wait 1.5 s, then unregister. No permission needed.
- **D5 — battery:** the sticky `ACTION_BATTERY_CHANGED` broadcast (no
  `BATTERY_STATS`, which is signature-protected). JSON:
  `percentage`, `status`, `temperature` (°C, 1 decimal), `health`, `voltage`
  (mV), `plugged`; unknown fields → `null`/`"unknown"`.
- **D6 — TTS is app-lifetime:** one `TextToSpeech` instance held by
  `CodecApiBridge` (a local instance would release its engine binding and cut
  speech off when the request handler returns), `QUEUE_FLUSH`, 3 s init wait,
  32 KiB cap, blank text rejected before any engine call.
- **D7 — camera = second runtime-permission op (mirrors Phase 4.8):**
  `handle` parks with `NEED_PERMISSION:android.permission.CAMERA` (or
  `CAPTURING:` when already granted), the activity's `RequestPermission`
  launcher answers, `resumeAfterPermission` writes the interim `CAPTURING:`
  marker, and the `TakePicture` contract drives the photo; `completeCameraCapture`
  writes the final `OK:<path>`/`ERR:`. Denial → actionable `ERR:` (no loop).
- **D8 — camera output is prefix-confined:** the CLI passes a *file name*
  (never a path); the app sanitizes it (`[A-Za-z0-9._-]`, `.jpg/.jpeg/.png`
  only, no `..`/`/`), writes under `$PREFIX/tmp/codec-api/camera/`, and serves
  it to the camera app through the existing `FileProvider` (`files-path`).
  The CLI prints `OK:<abs path>`; the photo is readable from the same prefix.
- **D9 — bridge surface unchanged:** `execute()` gained only a defaulted
  `deviceApi` parameter; existing callers and all Phase 4.7/4.8/5.3 tests are
  untouched. `TerminalViewModel._notificationPermissionRequests` generalized
  to `permissionRequests` (the flow already carried the generic `Request`).

### 5.3 Research notes (linked sources)

- **Battery reading without permissions:** `BatteryManager` extras come from
  the sticky `ACTION_BATTERY_CHANGED` broadcast; registering with a `null`
  receiver needs no permission and is the documented pattern
  (developer.android.com/reference/android/os/BatteryManager). `EXTRA_TEMPERATURE`
  is tenths of °C.
- **Sensor sampling:** `SensorManager.getDefaultSensor` +
  `registerListener(listener, sensor, SENSOR_DELAY_UI, handler)`; values are
  raw floats (developer.android.com/guide/topics/sensors/sensors_overview).
- **TextToSpeech:** engine binding lives on the `TextToSpeech` object, init is
  async (`OnInitListener`), `speak(CharSequence, int, Bundle?, String?)` is
  API 21+; Android 11+ package visibility needs the
  `android.intent.action.TTS_SERVICE` query for engine enumeration
  (developer.android.com/reference/android/speech/tts/TextToSpeech).
- **Camera via `ActivityResultContracts.TakePicture`:** the contract creates
  an `ACTION_IMAGE_CAPTURE` intent and grants write access to the FileProvider
  URI; declaring `CAMERA` without holding it can make the intent throw on
  M+, so the runtime dialog *must* come first
  (developer.android.com/training/camera/photobasics, androidx.activity docs).
- **UI parity:** the empty-request-file, derived-response-name and
  `/dev/tty`-with-stdout-fallback patterns are the Phase 4.7 conventions
  (verified in `codec-clipboard`/`codec-notify`); camera reuses notify's
  long-poll + one-shot-hint pattern so the CLI never depends on terminal echo.

### 5.4 Files changed

`CodecApiProtocol.kt`, `CodecApiBridge.kt`, `ShellEnvironment.kt`,
`MainActivity.kt`, `TerminalViewModel.kt`, `TerminalScreen.kt`,
`AndroidManifest.xml`; tests in `CodecApiBridgeFullTest.kt` (new),
`CodecApiProtocolTest.kt`, `ShellEnvironmentTest.kt`.

### 5.5 Exit-condition status

- **CI GREEN:** `Build APK` `33468442063` on `4460306` (assemble +
  `testDebugUnitTest` + `lintDebug`; the first round `33468153580` failed only
  on the lint ERROR `PermissionImpliesUnsupportedChromeOsHardware` — fixed by
  `<uses-feature android:name="android.hardware.camera" android:required="false"/>`).
- Device recipe §4 steps 1–4 (+optional step 5) **pending owner** on the
  green artifact.
- Invariants: client-only; no `.` on PATH; nothing in `$PREFIX/bin` that is
  not an app-written `codec-*` script; no `com.termux` identity; no new
  packages/repository changes.
