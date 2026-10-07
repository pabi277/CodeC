# CodeC Website Phase W6.1 — Chapter 13: Device APIs (CodeCApi)

> **Owner rounds1–3: required device checks PASSED.**
> [Evidence](../chat-web6/DEVICE_ROUND_1.md): Chapter8/P1/P5 including audible speech;
> Android16 / aarch64, CodeC “latest” (exact build unspecified). No retest needed.
> Review/license and explicit PR/merge/deploy authorization are separate.

> **Current batch status — 2026-10-07, D33: CONTENT IMPLEMENTED; REQUIRED DEVICE CHECKS PASSED.**
> Owner authorized W3–W6 content together, then one commit/watch; separate start
> commands/intermediate commits below are superseded. W4.2 verification ran FIRST.
> [Batch record](../chat-web6/BATCH_SUMMARY.md) · [checks](../chat-web6/CHECKS.md) ·
> [device gates](../chat-web6/DEVICE_TESTS.md) · [current full ZIP](../chat-web6/review-zips/CodeC-website-W6-review.zip).
> D31 download law retained. Device pass recorded above; no PR, merge or deployment implied.

> **Binding v2.3 amendment — 2026-10-07 (scope supplied 2026-10-06).**
> Apply [WEBSITE_PLAN.md](../WEBSITE_PLAN.md) and the current instructions below
> before the retained prior design. They supersede conflicting old facts and exits:
> **29 pages / 9 product / 19 chapters**; app-v1.3.18 **7 217 532 B, versionCode 22**;
> AI in header, Privacy every footer; contacts in-app only; README then AI/privacy
> guides; Phases 1–96 shipped except cancelled proposals, 97+ roadmap. Never pair
> current APK size with historical -74%. No picker, Open Folder, ProjectLink,
> first-hour tiles, mandatory tour or automatic userland setup in visitor teaching.
> Earlier session-folder references are historical: preserve existing records;
> W1 uses `chat-web4/W1_*`. No next phase, PR, merge or deploy without command.

**Current status:** CONTENT IMPLEMENTED; REQUIRED DEVICE CHECKS PASSED; owner-authorized combined batch (D33).

## Current v2.3 implementation requirements

Use the real script inventory/markers/paths from W4.2, not inconsistent old
'5'/'8' counts. Teach explicit permission requests and refusals; no secret or
background exfiltration. All examples self-contained with expected outputs.
Chapter 13 of 19.

## Current acceptance additions (binding)

1. Follow the current requirements above and the master plan, not superseded UI/copy.
2. Preserve each non-conflicting numbered exit below, including device gates.
3. Record source file/line/sha, responsive checks and self-dependent sweep; use 19-chapter crumbs where applicable.
4. No app/workflow changes or phase expansion; update web living docs and stop at merge gate.

---

## Prior design detail — retained v2.2 record

**Historical reference, not authority for superseded facts or exit counts.**

> **v2.2 update (2026-09-12):** synced with app Phases 21–43 — universal APK 6.6 MB signed SHA256, Auto engine only picker deleted Termux card deleted fallback automatic error-path Output Panel, >_ mark icon, official file icons Seti MIT, typing feel, terminal multi-session + LAN server opt-in 0.0.0.0 two URLs QR ZXing open-in-browser keep-alive, outputs temporary RunArtifacts+RepoHygiene ~60 patterns .codec/ user .gitignore wins, GitHub truth GitReadiness+PushOutcome+Publish POST /user/repos, feedback hardcoded +91 62967 46606 / email + FeedbackScreen + exit survey + crash-log header-first + OpenInBrowser no telemetry, backup include-list-only FullBackupContent law crash-loop guard safe mode 3rd launch export-all over both roots 11 privacy rows RELEASE_NOTES template {{SHA256_LINES}}, safe walk Throwable-safe TreeWalkPolicy ProjectLink persisted SAF grant noexec mirror, BETA B-1…B-8, TCC null on armeabi-v7a/x86.

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
