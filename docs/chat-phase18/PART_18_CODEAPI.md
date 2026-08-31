# CodeC Phase 18 — CodeCApi Device Capabilities & Final System Polish

**Status:** Planned · **Cost:** `[client-only]` · **Depends on:** Phase 7 (Multi-Terminal Routing) + Phase 6 (Terminal UX)  
**Target Files:** `CodecApiBridge.kt`, `CodecApiProtocol.kt`, `ShellEnvironment.kt`, `MainActivity.kt`

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
