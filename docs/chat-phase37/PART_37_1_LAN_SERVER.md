# CodeC Phase 37.1 — LAN server + URL / QR UX

**Status:** ✅ DEVICE-PASSED — implemented, CI green (2026-09-09, owner: "Start
Phase 37"), then run on the owner's phone **and** a second device on the same
Wi-Fi and reported **"All pass" (2026-09-10)** — all eight exit checks of the
phase. That round asked for one more thing, now shipped: **the panel's links
open in the phone's browser, not just copy** (see §Result, "Open in browser") ·
**Cost:** `[client-only]` · **Effort:** M

## Symptom (owner)

*"we can use a device as a server and run our files at localhost"* — make the
running server reachable from **other devices** (Spck/Termux-style), not just
the phone's own WebView.

## Design

1. **Opt-in network mode** — a per-run "LAN" switch (default **off** =
   today's loopback). When on, the static preview binds `0.0.0.0` (one-line
   change in `WebPreviewServer.start`), and the process-server templates gain
   a `0.0.0.0` bind variant (the `ServerPortDetector` already recognises
   `0.0.0.0` lines; it stops rewriting them to 127.0.0.1 for the LAN URL).
2. **`ui/services/LanAddress.kt` (pure-ish, host-testable)** — resolves the
   device's best IPv4: try `ConnectivityManager.getLinkProperties(activeNetwork)`
   `linkAddresses` (skip link-local/loopback), fall back to
   `WifiManager.connectionInfo.ipAddress`, return null when none. The
   injectable sources keep it testable on the host.
3. **URL / QR panel** — when a server is Ready, the Output Panel (and the
   preview screen) shows **two** addresses:
   - `http://127.0.0.1:<port>` — on-device preview (unchanged);
   - `http://<lan-ip>:<port>` — for other devices, with a **QR code**
     (ZXing `core`, Apache-2.0) and a copy button. If no LAN address exists,
     show "connect the phone to Wi-Fi" instead of a dead URL.
4. **Security** — LAN mode stays off by default, is shown clearly while on
   ("anyone on this Wi-Fi can open these files"), and the served root stays
   path-confined (the existing traversal guard). No port < 1024.
5. **On-device viewing** (owner, after the device round: *"on same device
   directly open in default browser not copy the link"*) — a row the phone can
   open itself must be openable with one tap, not only copyable.

## Exit condition

```text
(Device + a second device on the same Wi-Fi)
1. RUN a Python/HTML/C server with LAN ON: the panel shows the 127.0.0.1 URL
   AND a <lan-ip>:<port> URL with a working QR code.
2. The second device opens the LAN URL in its browser and sees the page.
3. The on-device preview still works via 127.0.0.1 (no regression).
4. LAN OFF (default): the second device cannot connect (loopback only).
PASS = all four.
```

**Result (2026-09-10): ✅ PASS — "All pass"** on the owner's device round: the
server started on the LAN address, both URLs and the QR were shown, the second
device opened the LAN URL, the on-device loopback preview still worked, and
with LAN off the peer could not connect. The second device reaching the phone
also means client isolation on the router was not in play. The round's single
follow-up (open in browser on the same device) is implemented here; the 37.2
half of the checklist (keep-alive, Stop, re-run, port clash) passed in the same
report.

## Tests (plan — see "Tests (as shipped)" at the end for what landed)

- `LanAddressTest` (host): injected link-addresses → best IPv4 chosen;
  link-local/loopback filtered; empty → null.
- `ServerPortDetectorTest` additions: a `0.0.0.0` bind line now yields both
  the loopback URL and the LAN URL (detector stays pure).
- `WebPreviewServer` still refuses `..` escapes in LAN mode (existing test
  re-pinned).

## Result (2026-09-09)

**Pure core (`ui/services/`, host-tested, no Android types):**

- `LanAddress.kt` (102 LOC) — the address *rule*: IPv4 shape checks,
  loopback / link-local / wildcard classification, `fromWifiAddress`
  (little-endian packed int), `pickBest` over candidate lists, and `url()`,
  which now gates **both** halves (a peer URL needs a peer-reachable host
  *and* a real port — `127.0.0.1:8080` is not a share address).
- `LanAddressProvider.kt` (95 LOC, the only Android half) — candidates in
  trust order: `ConnectivityManager.getLinkProperties(activeNetwork)` →
  `NetworkInterface` enumeration (catches `ap0`/`rndis0` tethering) →
  deprecated `WifiManager.connectionInfo.ipAddress`. It only *pushes* strings
  into `ServerHost.publishLanAddress`, so the rule and the registry stay
  Android-free and host-tested. Needs `ACCESS_NETWORK_STATE` (already
  declared) + `ACCESS_WIFI_STATE` (added; install-time, no runtime dialog).
- `ServerEndpoints.kt` (81 LOC) — **the one place the two URLs are composed**:
  `of(port, bind, lanAddress, lanShared)`. `lanUrl` exists only when LAN is
  on **and** the socket bound the wildcard (`0.0.0.0` / `::` / `*`) **and**
  the address is peer-reachable; otherwise `notice()` says which of the two
  reasons it is empty (`NO_ADDRESS` → "connect the phone to Wi-Fi",
  `NOT_WILDCARD` → "this server answers on the phone only"). `badge()` is the
  one-word header marker (`LAN :8100` / `device :8100`).
- `LanSharePolicy.kt` (40 LOC) — the app-wide switch, **default OFF**, not
  persisted (a LAN bind must never survive an app restart silently). Both the
  Output Panel and the Web Preview read the same `StateFlow`, so the panel and
  the notification cannot disagree. `set()`/`toggle()` report whether anything
  changed, which is what makes a toggle idempotent from two surfaces.

**The socket:** `WebPreviewServer` gained `LAN_HOST` + `startAt(root, host,
port) -> PreviewStart` (`Ready` / `PortInUse(port)` / `Failed(message)`);
loopback stays the default and `resolveServedFile` is untouched, so LAN mode
adds a bind address and nothing else. The path-traversal guard is re-tested
**in LAN mode** with percent-encoded `/%2e%2e/secret.txt` and `/..%2f`
against a secret written *outside* the served root (`ServerLanTest`).

**The process servers:** the flask / fastapi / C templates now read
`CODEC_SERVER_HOST` (set to `0.0.0.0` only for a LAN run) and print their real
bind address; the C template binds `INADDR_ANY` through the same helper.
**Deliberate scope call: the templates do NOT read `CODEC_SERVER_PORT`.** The
port stays the value in `.codec.json`/`ProjectConfig`, because a template that
silently moved to another port would break the URL the app just advertised —
and a clash is instead surfaced as `ServerEvent.BindFailed` with the
"Port N is in use — stop the other server or change the port" message
(`ServerScaffoldE2ETest` pins both the wildcard bind and the clash).

**UI:** `ui/components/ServerSharePanel.kt` (303 LOC) renders the two rows
(copy on tap, QR toggle, the amber notice, the multi-server line + STOP ALL)
and nothing at all when there is no server. It is embedded in the Output Panel
header (badge) + body (dense panel, both the expanded and collapsed call
sites in `EditorScreen`) and under the Web Preview's address bar, where
`showSwitch = !isLive` — a surface that does not own the server never renders
a switch that could pretend to rebind it.

**UI: "Open in browser" — the device round's one follow-up.** Each URL row in
`ServerSharePanel` now carries a 🌐 button that hands the link to the phone's
**default browser**: `On this device` opens the *loopback* URL (the phone opens
its own server — no Wi-Fi, no router, nothing to share) and `Other devices`
opens the *LAN* URL, the same string the QR encodes. Decisions live in the pure
`ui/services/ShareActions.kt` (`browserUrl` / `isHttpUrl` / `label` /
`fallbackMessage`); the Android half is exactly one function,
`ui/services/OpenInBrowser.kt` (`ACTION_VIEW` + `FLAG_ACTIVITY_NEW_TASK`,
`runCatching`, returns whether it launched), which is now the **only** browser
launcher in the app — the terminal's link handler (`openTerminalUrl`) delegates
to it, so there is one place that can open a URL and one place that decides what
happens when none can. Two rules keep the affordance honest: the button exists
**only** for a row that holds a real `http(s)://` URL (never a disabled or dead
button, and never a bare `127.0.0.1:8100` handed to a browser where it would
silently become a web search), and if the launch fails — no browser app, or one
that cannot be started — the URL is copied and the toast says
`No browser on this device — link copied: <url>`, so the link is never lost.
Copy remains (tap-the-URL still copies in the dense Output Panel row), and
loopback keeps 👁 for the in-app preview: the browser button is an extra door,
not a replacement.

**QR:** ZXing `core` **3.5.4** (`com.google.zxing:core`, Apache-2.0, zero
transitive deps) — `QrCode.encode(text) -> QrModules(size, dark)` returns the
raw module grid, `null` on failure, and Compose turns the grid into an ARGB
`Bitmap` (`QrCode` never touches Android). Attribution: full license text +
note in `app/src/main/assets/licenses/ZXING_APACHE2.txt`, and the Settings →
About "Open-source licenses" line now says "QR encoding — Apache-2.0
(zxing/zxing core)". **Clean-room law honoured:** the dependency is used
through its public API only; no code or assets were copied, and the custom
encoder the spec allowed was rejected in favour of the OSS option (owner's
open-source-first directive, `docs/PHASE34_37_OSS_RESEARCH.md` §4).

### Sources (record)

- `com.google.zxing:core` version + dependency list: libraries.io /
  central.sonatype.com — latest release **3.5.4** (2025-11-11), test-scope
  junit only, i.e. one artifact and nothing else to vendor; the project is in
  maintenance mode, which is fine for a frozen 20-line API surface.
- `QRCodeWriter` / `BitMatrix` / `RGBLuminanceSource` signatures:
  `https://javadoc.io/doc/com.google.zxing/core/3.5.4/` — `encode(text,
  BarcodeFormat, w, h[, hints])`, `RGBLuminanceSource(int w, int h, int[]
  argb)`. Both used: the encoder in production, the reader in the round-trip
  test.
- `EncodeHintType` constants (`CHARACTER_SET`, `MARGIN`, `ERROR_CORRECTION`) and
  `LuminanceSource`/`HybridBinarizer` package placement: the published API docs
  (`https://zxing.github.io/zxing/apidocs/com/google/zxing/class-use/LuminanceSource.html`,
  `…/EncodeHintType.html`). **`CHARACTER_SET` is spelled that way — there is no
  `CHARSET` key**, and the first `Build APK` run (`34391044725`) was RED on
  exactly that line. Worth recording as a lesson: the local shim had been
  written from the same wrong assumption, so the harness could not see it — a
  shim only protects you if it mirrors reality, and now the shim carries that
  note with it.
- Apache-2.0 text for the notice asset:
  `https://www.apache.org/licenses/LICENSE-2.0.txt`.
- `LinkProperties.getLinkAddresses` / `WifiManager` IP discovery and the
  API-24 safety of both: the spec's own research list (unchanged), verified
  against `minSdk = 24` in `app/build.gradle.kts`.

## Tests (as shipped)

- `LanAddressTest` (8), `ServerEndpointsTest` (5), `LanSharePolicyTest` (3) —
  pure rules; `ServerRegistryTest` (12) and `ServerNotificationTest` (5) are
  37.2's but share the endpoint projection.
- `ServerLanTest` (9) — detector bind lines (wildcard kept as a wildcard,
  loopback never yields a LAN URL, foreign addresses rejected, bind-failure
  lines and their port) **plus a real LAN-mode preview socket**: serves the
  folder, answers on loopback too, refuses traversal, and a taken port is
  reported rather than swallowed.
- `QrCodeTest` (5) — encodes, checks the quiet zone + finder pattern, and
  **decodes the bitmap back** with `MultiFormatReader`/`RGBLuminanceSource`
  to assert the exact URL (this is the CI-only test: the local harness has no
  real zxing to decode with).
- `ProjectScaffoldTest` (+1: every server template defaults to loopback and
  honours the LAN env) and `ServerScaffoldE2ETest` (+2: the wildcard bind
  round-trip asserting `ready.bind == "0.0.0.0"`, and the clash message).
- `ShareActionsTest` (6) — the browser button's policy: the loopback URL for
  the on-device row and the LAN URL for the peer row, no endpoints → nothing to
  open, only `http`/`https` may reach a browser (`file://` and `javascript:`
  refused, a host:port string without a scheme refused), a malformed URL yields
  no button, and the failure path keeps the link.
- Local pre-validation (JVM harness over the real production files, not a
  replacement for CI): 96 cases green — 80 in the service set and 16 in the
  scaffold set; re-run after the browser-button follow-up: **86 in the service
  set (80 + `ShareActionsTest` 6), 0 failed**. The Compose half of the change
  (`ServerSharePanel`, the terminal launcher) has no unit-testable logic — it
  is compile- and lint-checked by CI and was checked on the device.
- **CI (`Build APK`, the executor of record): ✅ GREEN** — run `34393543928`
  (9 m 6 s) on tip `6d36a83`: `:app:assembleDebug` + `:app:testDebugUnitTest`
  (all 11 Phase-37 classes, incl. the real zxing decode round trip) +
  `:app:lintDebug`, artifact `CodeC-IDE` **24 844 344 B**.
  **APK delta +355 660 B (+347 KiB, ≈ +1.45 %)** against the `main` build
  `34381534118` (tip `4529e3c`, 24 488 684 B) — that is ZXing `core` 3.5.4
  (one jar, no transitive deps) plus the phase's own code; the bench artifact
  moved by 12 B. Stated up front so the device round sees the same number on
  the artifact.

**Two test-only bugs CI caught that the harness could not** (both recorded so
the next phase does not repeat them):

1. `ServerLanTest` asked `TemporaryFolder.newFolder("site")` three times in one
   test (bind → clash → rebind needs the same folder), and real JUnit answers an
   existing name with `IOException: a folder with the path 'site' already
   exists`. The folder is now built once under `tmp.root` and reused — the rule
   still owns the tree, so cleanup is unchanged.
2. `QrCodeTest`'s finder-pattern check assumed a fixed pixel offset. zxing
   scales the code to an **integer number of pixels per module and centres it**
   in the requested square, so the offset is `left = (width − (dim + 2·margin)·
   multiple) / 2` — a guess is wrong. The test now *finds* the first dark pixel
   and derives the module scale from the 7-module run, which asserts the real
   property (quiet zone present, the eye's ring/pupil geometry) instead of a
   coincidence. The local shim's fake writer was rewritten to reproduce that
   geometry, so this class of mistake is visible in the sandbox again.
