# CodeC Website Phase W6.1 — Chapter 13: Device APIs (CodeCApi)

**Status:** 📋 **PLANNED** · **Cost:** `[static]` · **Effort:** S
· **Depends on:** W4 (W4.2 verified facts: the five Phase-18 scripts)
· **Target file:** `website/ch-13.html`

> Source: `README.md` (Phase 18 record pointers) + W4.2 facts: ops
> `battery.status` / `sensor.read` / `tts.speak` / `camera.capture` /
> `intent.send`; scripts `codec-battery`, `codec-sensor`, `codec-tts`,
> `codec-camera`, `codec-intent`; markers `NEED_PERMISSION:` / `CAPTURING:`;
> camera output in `$PREFIX/tmp/codec-api/camera/`; TTS 32 KiB cap.

---

## 1. Content

- **Goal box:** talk to the phone itself — battery, sensors, speech,
  camera, intents — from the terminal. This is CodeC's answer to
  Termux:API.
- **Need:** Chapters 03, 05 done (comfortable in Term + packages).

### Steps

1. **What CodeCApi is** — a bridge from the terminal to real Android
  capabilities, exposed as five CLI scripts; permissions are handled
  in-app (you'll see the marker, then grant, then it just works).
2. **Battery** — `codec-battery`: prints sticky battery state as JSON
   (level, charging, temperature — exact fields from W4.2 facts); read it
   in a script (`grep -o` one line for the level).
3. **Sensors** — `codec-sensor`: accelerometer / gyroscope / light in one
   read; move the phone while it runs (or tilt it — the light sensor
   reacts to covering the front).
4. **Speech** — `codec-tts "hello from the phone"`: TextToSpeech with
   QUEUE_FLUSH and a 32 KiB cap (state the cap plainly: long text gets
   truncated — write short lines); the permission flow on first use.
5. **Camera** — `codec-camera`: runtime CAMERA permission (park/resume —
   the app briefly takes the camera and gives it back), `TakePicture` via
   FileProvider → the photo lands in `$PREFIX/tmp/codec-api/camera/` with
   an `OK:<path>` reply (the `ERR` case: name rules — the output name must
   be a simple `[A-Za-z0-9._-]` filename with .jpg/.jpeg/.png — shown, not
   lectured); `CAPTURING:` marker while it works.
6. **Intents** — `codec-intent`: send a view/dial/send intent; the
   URI-scheme allow-list (only the safe ones — why this guard exists, two
   lines); example: open a URL in the browser from a script.
7. **The permission pattern** — when you see `NEED_PERMISSION:` — grant in
   the dialog, rerun the command; the state sticks for the app's lifetime
   (one short worked example using battery or TTS).

- **Try it:** (1) a 3-line bash script that prints the battery level and
  speaks "battery at N percent" (glue battery + tts); (2) take one photo
  with `codec-camera`, `ls` the camera folder, and name the file; (3)
  trigger a light-sensor read while covering/uncovering the phone.
- **Mistakes:** `NEED_PERMISSION:` read as an error (it's the app asking —
  grant and rerun); TTS silent (permission or an empty/over-long string —
  check both); camera `ERR` with a creative filename (use the safe pattern
  from step 5); expecting a live video stream (it's a still capture — say
  it plainly).

## 2. Implementation steps

1. Build `ch-13.html` (crumb "Chapter 13 of 17").
2. Every path/marker/limit from W4.2 facts (diff noted in `chat-web6/`).
3. Self-dependent sweep.

## 3. Exit condition

```text
1. Template complete; prev → ch-12, next → ch-14.
2. All five scripts named + all markers/paths/limits == W4.2 facts (noted).
3. The glue-script try-it is valid bash against the documented JSON (shape
   from W4.2 facts).
4. 360/1440 clean; sweep PASS.
```
