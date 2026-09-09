# CodeC Phase 37.1 — LAN server + URL / QR UX

**Status:** 📋 PLANNED · **Cost:** `[client-only]` · **Effort:** M

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
   - `http://<lan-ip>:<port>` — for other devices, with a **QR code** and a
     copy button. If no LAN address exists, show "connect the phone to Wi-Fi"
     instead of a dead URL.
4. **Security** — LAN mode stays off by default, is shown clearly while on
   ("anyone on this Wi-Fi can open these files"), and the served root stays
   path-confined (the existing traversal guard). No port < 1024.

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

## Tests

- `LanAddressTest` (host): injected link-addresses → best IPv4 chosen;
  link-local/loopback filtered; empty → null.
- `ServerPortDetectorTest` additions: a `0.0.0.0` bind line now yields both
  the loopback URL and the LAN URL (detector stays pure).
- `WebPreviewServer` still refuses `..` escapes in LAN mode (existing test
  re-pinned).
