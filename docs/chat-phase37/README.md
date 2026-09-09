# CodeC Phase 37 — Device as server (localhost on the LAN)

> **Status:** 🚧 IMPLEMENTED on `arena/01a0872e-codec` (2026-09-09, owner:
> "Start Phase 37") — both parts, 62 new host-test cases in nine new test
> classes, local pre-validation 96/96 green over the real production files.
> **CI is ✅ GREEN** — `Build APK` run `34393543928` on tip `6d36a83`
> (`:app:assembleDebug` + `:app:testDebugUnitTest` + `:app:lintDebug`; APK
> 24 844 344 B, +355 660 B vs the `main` build), after two for-cause red
> rounds recorded in `PART_37_1_LAN_SERVER.md` §Tests. **A real device pass is
> still required** (owner's phone + a second device on the same Wi-Fi, all
> eight exit checks); no device acceptance is claimed until the owner reports
> it, and merge is held for the owner's command.
> **Cost:** `[client-only]` · **Effort:** M/L · **Owner row:** *"Like spck or
> Termux we can use a device as a server and run our files at localhost i want
> to implement that feature"*

```text
  37.1  LAN server (bind 0.0.0.0) + URL / QR / network UX
  37.2  Foreground keep-alive + port lifecycle
```

| Part | Title | Cost | Effort | Status |
|---|---|---|---|---|
| [37.1](PART_37_1_LAN_SERVER.md) | LAN server + URL/QR | client-only | M | 🚧 implemented — device pass required |
| [37.2](PART_37_2_KEEPALIVE_PORTS.md) | Keep-alive + ports | client-only | M | 🚧 implemented — device pass required |

## What exists today (evidence)

- `WebPreviewServer` (Phase 9.1) serves the project folder over
  `http://127.0.0.1:<ephemeral>/` — **loopback only**, plain GET/HEAD,
  path-traversal-guarded. This is the static preview.
- `ServerRunner` (Phase 14) runs a **user process** (Flask/FastAPI/
  `http.server`/C microservice) and `ServerPortDetector` detects its bind line,
  **rewriting any `0.0.0.0` to `127.0.0.1`** so the on-device WebView can load
  it. The project templates bind `127.0.0.1`.
- `RunForegroundService` (Phase 24.2) already promotes a long run (>5 s) to a
  foreground service with a Stop action — the keep-alive foundation exists.

So the *on-device* preview is done; what is missing is exactly the owner's ask:
**other devices on the same network reaching the phone's server.**

## Research (record of sources)

- **Bind `0.0.0.0`** so the socket accepts LAN connections, not just loopback.
- **Discover the LAN IP** — `WifiManager.connectionInfo.ipAddress` (the
  classic path) or, modern + API-24-safe,
  `ConnectivityManager.getLinkProperties(activeNetwork).linkAddresses`
  (needs `ACCESS_NETWORK_STATE`); `Formatter.formatIpAddress` for display.
  Choose the non-link-local IPv4; if none exists (no Wi-Fi), degrade with a
  clear "no network address" message rather than showing a dead URL.
- **Ports < 1024 need root** on Android — always use high ephemeral ports
  (the existing server already uses `ServerSocket(0)`; the process templates
  use 5000/8000/8080 — all fine).
- **Keep-alive** — the process must survive the user switching apps / the
  screen sleeping: promote the server to the existing `RunForegroundService`
  with a "Serving <project> on <ip>:<port>" notification + Stop.
- **QR code** for the URL (Spck shows one) so a second device can join by
  scanning — **ZXing `core` (Apache-2.0, zero-dependency)**: `MultiFormatWriter()
  .encode(url, QR_CODE, w, h)` → `BitMatrix` → bitmap. Confirmed by
  open-source-first research (`docs/PHASE34_37_OSS_RESEARCH.md` §4); no custom
  QR encoder needed.
- **Discovery (optional)** — framework **NSD** (`_http._tcp`, no dependency);
  **JmDNS** only as a fallback **and only after verifying the exact version's
  licence** (older releases are LGPL — never pull those).
- **HTTP reference** — **NanoHTTPD (BSD-3-Clause)** documents range/ETag/
  directory serving; CodeC keeps its leaner, path-confined
  `WebPreviewServer` and borrows the technique list, not the code.
- **Security posture** — LAN exposure is opt-in: loopback stays the default;
  the network toggle is per-run and off by default; the served root remains
  path-confined (`WebPreviewServer.resolveServedFile` already refuses escapes).

## Cross-device risks to watch

- OEM Wi-Fi/`LinkProperties` quirks and VPN/tethering addresses (the URL must
  be the address *peers* can reach — the hotspot gateway case differs from
  home Wi-Fi).
- Foreground-service launch restrictions on newer Android (the existing
  Phase 24.2 service already navigates these; reuse it, don't add a second
  service type).
- WebView vs external browser: the on-device preview keeps using 127.0.0.1;
  the **LAN URL** is what peers use — never mix the two.

## Result (2026-09-09) — what "the phone is a server" became

```text
  opt-in LAN switch (OFF by default, app-wide, not persisted)
        │
        ├─ static preview  → WebPreviewServer binds 0.0.0.0, pool 8100..8199
        └─ process server  → CODEC_SERVER_HOST=0.0.0.0 for the run
        │
  ServerRegistry  ← the one owner of "what serves on which port"
  ServerHost      ← the one owner of the socket / the process (survives the editor)
        │
  ServerEndpoints.of(port, bind, lanAddress, lanShared)   ← the one place URLs exist
        │
  ServerSharePanel (Output Panel header+body, Web Preview address bar)
     http://127.0.0.1:<port>  ·  http://<lan-ip>:<port>  ·  copy  ·  QR (ZXing core)
        │
  RunForegroundService.startServing(...)  ← same service, same Stop, no second type
```

Rules the implementation locked in (all host-tested):

- **The phone never dials the LAN URL.** The WebView keeps loading
  `127.0.0.1`; the LAN URL and the QR are peer-facing only — mixing them was
  the spec's named risk and it cannot happen by construction.
- **A LAN URL exists only if it can be opened**: LAN on, wildcard bind, and a
  peer-reachable IPv4. Otherwise the panel states the reason
  ("no Wi-Fi address" / "this server answers on the phone only").
- **Loopback keeps the pre-37 behaviour**: a loopback preview dies with the
  screen; only a LAN server survives leaving the editor, and Stop / Clear /
  STOP ALL always kill.
- **No port below 1024** anywhere (`LanAddress.MIN_SHARE_PORT`, and the
  templates' own defaults stay 5000/8000/8080).

## Deferred, recorded on purpose

- **NSD / mDNS discovery** (`_http._tcp`) — the spec called it optional and it
  adds no reachability the URL does not already give; it stays a future part.
- **`CODEC_SERVER_PORT`** — templates read `CODEC_SERVER_HOST` only; a port is
  a *config* value, and letting a template silently move the port would
  invalidate the URL CodeC just advertised. A clash surfaces as
  `ServerEvent.BindFailed` with an actionable message instead.
- **A second foreground-service type** — the spec forbids it; the existing
  `RunForegroundService` carries the server.
- **JmDNS** — not pulled (licence of older releases is LGPL; the rule is
  verify-before-adopt, and there is no need today).
