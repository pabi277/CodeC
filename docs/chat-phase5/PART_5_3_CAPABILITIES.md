# Phase 5 Part 5.3 — CodeCApi capability batch: toast, share, open URL, vibrate

**Status: 🚧 IN PROGRESS (design recorded; code + host tests next).** This is
the "more Termux:API-style capabilities" candidate from
[`../PHASE5_ROADMAP.md`](../PHASE5_ROADMAP.md). The owner chose to add **all
four** in one part rather than one at a time. Each capability = one `CodeCApi`
wire op group + one CLI script + one `BOOTSTRAP_VERSION` bump, reusing the
proven 4.7/4.8 bridge (clipboard, notifications) unchanged.

---

## 1. Decision D1 — the four capabilities and their shapes

| CLI | Wire op | Payload | Response | Permission |
|---|---|---|---|---|
| `codec-toast MESSAGE` | `toast.show` | message text | `OK` / `ERR:` | none |
| `codec-share TEXT` | `share.text` | text to share | `OK` / `ERR:` | none |
| `codec-open-url URL` | `url.open` | `http(s)` URL | `OK` / `ERR:` | none |
| `codec-vibrate [MS]` | `vibrate` | duration ms (default 500) | `OK` / `ERR:` | `VIBRATE` (normal, install-time — manifest only, no runtime dialog) |

Key facts that shaped the design:

- **`VIBRATE` is a normal (install-time) permission**, not a runtime one, so
  none of these four needs the 4.8-style `POST_NOTIFICATIONS` dialog/launcher/
  `NEED_PERMISSION` flow. All four are "fire and answer OK".
- **toast** must run on the main thread → the bridge posts to the main looper.
- **share / open URL** start an Activity from a background context (the bridge
  runs on `Dispatchers.IO` with the `Application` context) → `FLAG_ACTIVITY_NEW_TASK`
  on the `ACTION_SEND` chooser and the `ACTION_VIEW` intent.
- **open URL** is validated in the android-free core to accept only `http`/`https`
  (so it is host-testable and cannot be abused for arbitrary `intent:`/`file:`
  schemes).
- **vibrate** duration is parsed/clamped in the core (blank → 500 ms;
  non-numeric → `ERR`; clamped to `[1, 10000]` ms).

### Security posture (unchanged)

All four go through the existing `execute()` confinement check: the request
and response paths must be direct children of `$PREFIX/tmp/codec-api`
(`canonicalUserPrefix`-normalized), and the payload never travels inside the
OSC sequence — only file paths. `allowUniversalAccessFromFileURLs` is not
involved (no WebView here).

## 2. Implementation map

- `CodecApiProtocol.kt` — `Op` += `TOAST_SHOW/SHARE_TEXT/OPEN_URL/VIBRATE`
  (+ `isTermuxApiOperation`); new `MAX_TOAST_BYTES` / `MAX_SHARE_BYTES` /
  `MAX_URL_BYTES` / `MAX_VIBRATE_MS` / `DEFAULT_VIBRATE_MS` constants.
- `CodecApiBridge.kt` — new android-free `TermuxApiOps` adapter; `execute()`
  gains a `termuxApi` parameter and four new branches (validation in the
  core); `handle()` dispatches the new ops to `androidTermuxApiOps(context)`;
  `androidTermuxApiOps` = Toast (main-looper), `ACTION_SEND` chooser,
  `ACTION_VIEW`, and `Vibrator.vibrate(VibrationEffect.createOneShot)` with a
  pre-26 fallback.
- `ShellEnvironment.kt` — four new CLI scripts (`toastScript`, `shareScript`,
  `openUrlScript`, `vibrateScript`), each the same `/dev/tty`-first + stdout
  fallback + poll-for-response shape as clipboard; registered in
  `ShellBootstrap.prepare`; `BOOTSTRAP_VERSION` 25 → **26**.
- `AndroidManifest.xml` — `<uses-permission android:name="android.permission.VIBRATE" />`.
- Tests — `CodecApiProtocolTest` (parse the new ops), `CodecApiBridgeTest`
  (execute each op: success, blank/oversize/non-URL/non-numeric error paths),
  `ShellEnvironmentTest` (script content + a full toast round-trip).

## 3. Invariants (none weakened — checked)

- No userland/package/repository change; no bootstrap rebuild. The CLI scripts
  live in `$PREFIX/bin` and are rewritten by the existing app bootstrap.
- The bridge's path confinement is unchanged and still applies to the new ops.
- `VIBRATE` is a normal permission; nothing new is requested at runtime.

## 4. Exit condition

On a real device with the new APK:

1. `codec-toast "hello from CodeC"` → a transient toast appears; CLI prints `OK`.
2. `codec-share "https://github.com/pabi277/CodeC"` → the Android share sheet
   opens with that text; CLI prints `OK`.
3. `codec-open-url "https://example.com"` → the browser opens; CLI prints `OK`;
   `codec-open-url "file:///etc/passwd"` → `ERR:` (non-http(s) rejected).
4. `codec-vibrate 300` → the device vibrates ~300 ms; CLI prints `OK`;
   `codec-vibrate` → default 500 ms; `codec-vibrate abc` → `ERR:`.

### Device verification recipe (for the owner — exact copy-paste)

```sh
codec-toast "hello from CodeC"; echo "exit=$?"      # expect: toast + OK, exit 0
codec-share "https://github.com/pabi277/CodeC"      # expect: share sheet + OK
codec-open-url "https://example.com"                # expect: browser opens + OK
codec-open-url "file:///etc/passwd"                 # expect: ERR (http/https only)
codec-vibrate 300                                    # expect: vibrate + OK
codec-vibrate                                        # expect: vibrate ~500ms + OK
codec-vibrate abc                                    # expect: ERR + exit 1
```

## 5. Evidence

### 5.1 Host

Pure-core unit tests (op parse, execute validation/recording).

### 5.2 CI

Push → "Build APK" (assemble + unit tests + lint) must be green.

### 5.3 Device

The §4 recipe; to be filled in with the owner's transcript.

## 6. Out of scope

- Sensors / camera / intents (named "later" in the roadmap).
- A vibrate permission-prompt UI (none exists — it is a normal permission).
- File sharing (share a project file via FileProvider) — `share.text` only.
