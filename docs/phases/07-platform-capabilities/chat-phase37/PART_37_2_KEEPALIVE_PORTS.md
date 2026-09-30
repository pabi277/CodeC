# CodeC Phase 37.2 — Foreground keep-alive + port lifecycle

**Status:** ✅ DEVICE-PASSED — implemented and CI green (2026-09-09, owner:
"Start Phase 37"; `Build APK` `34393543928`), and the four exit checks below
were run on the owner's phone + a second device and reported **"All pass"
(2026-09-10)** ·
**Cost:** `[client-only]` · **Effort:** M

## Symptom (owner)

A phone server is only useful if it **stays up** while the user does other
things (another app, screen off) — and if it can be found again and stopped
cleanly. Today a server dies when the user leaves the editor tab or Android
kills the backgrounded app, and there is no single place that knows "what is
serving on which port".

## Design

1. **Reuse `RunForegroundService`** (Phase 24.2) — when a **LAN** server run
   passes the existing >5 s threshold, promote it with a notification titled
   `Serving <project> on <lan-ip>:<port>` + Stop action. (Batch runs already
   do this; this part extends the title/action to the server, no second
   service type.)
2. **`ServerRegistry` (pure state + Android adapter)** — a single owner of
   "what is serving": `(project, runKind, bind, port, url, startedAt, alive)`.
   The Output Panel, the preview screen and the notification all read it, so
   there is exactly one truth about the running server and its port.
3. **Lifecycle** — leaving the editor no longer kills a **LAN** server (the
   foreground service keeps the process); the Stop button / notification
   action / a new "stop all servers" in the registry tears it down and
   releases the port. Loopback-only previews keep today's behaviour (die with
   the screen).
4. **Port reuse** — bind failures (port already taken) surface a clear message
   ("port 8080 in use — stop the other server or change the port") instead of
   a silent exit; the registry owns the port map.

## Exit condition

```text
(Device + a second device)
1. Start a LAN server, switch to another app, wait > 5 s: the notification
   appears and the server keeps answering the second device.
2. Screen off / back: the server is still reachable (within Android's
   foreground-service allowances).
3. Stop via the notification: the server exits and the port is released
   (a re-run works immediately).
4. Two servers on the same port: the second shows the "port in use" message.
PASS = all four.
```

**Result (2026-09-10): ✅ PASS — "All pass"** in the owner's device round: the
server kept answering after the app went to the background, survived the screen
off, released its port on the notification's Stop and was immediately re-runnable,
and a second server on the same port reported the clash instead of failing
silently. No device-side fix came out of that round.


## Tests (plan — what landed is in §Result)

- `ServerRegistryTest` (host): add/update/stop; duplicate port detection;
  the single-truth accessors.
- The notification title/action mapping is host-tested through a small pure
  formatter (no Service in the unit test).

## Result (2026-09-09)

**One owner of port truth.** `ui/services/ServerRegistry.kt` (223 LOC, pure,
`synchronized LinkedHashMap`) holds `(id, project, kind, port, bind,
lanShared, startedAt, lanAddress, alive, endpoints, conflictWith)` and the
single-truth API the UI reads: `snapshot()`, `allEntries()` (live +
recently-stopped, for the "what is serving" line), `liveCount()`, `findById`,
`stoppableIds()`, `nextFreePort()`, `ownerOfPort`/`findByUrl`, plus the
`portInUseMessage(port, owner)` sentence. `port <= 0` claims **nothing** and
publishes **no endpoints** (so "starting" can never advertise a URL that does
not exist yet); a second claimer of the same port is tagged with
`conflictWith` instead of overwriting the winner.

**One owner of the process.** `ui/services/ServerHost.kt` (321 LOC) is the
process/socket holder — *not* the ViewModel — so leaving the editor tab (or
Android destroying the activity) cannot kill a LAN server:

- `attachProcess(id, project, runner, lan)` starts the collector in the host's
  own scope and hands the caller a per-session
  `MutableSharedFlow(replay = 96, DROP_OLDEST)`; a re-attaching screen gets the
  log tail **and** the `Ready` event, which is what lets the Output Panel
  navigate back to a server it did not start.
- `serveStatic(staticId, project, root, lan, forceRestart)` is idempotent per
  `static:<project>`: the preview screen re-attaches to the live socket
  (same port, same QR) instead of binding a second one; a LAN-flag change is
  the only thing that rebinds. Preferred LAN ports come from
  `LanAddress.LAN_PORT_POOL` (8100–8199 — never < 1024) with one ephemeral
  retry if the whole pool is busy.
- `stop(id)` / `stopAll()` (returns how many it tore down — the panel's
  STOP ALL) / `entryFor` / `endpointsOf` / `isServing` / `refreshLan(flag)`.
- `EditorViewModel` keys its run as `process:<project>`, keeps the loopback
  pre-37 behaviour on `onCleared` (a loopback preview dies with the screen)
  and keeps a LAN server serving with the notification's Stop still wired to
  `ServerHosts.shared.stop(id)`; Stop / Clear / "stop all" always kill, even
  for a LAN server — only *leaving the editor* is allowed to keep it alive.

**Keep-alive without a second service.** `RunForegroundService` is unchanged
in kind: `startServing(context, title, body)` reuses the same channel
(`codec_runs`), the same id (1001) and the same Stop action; the title comes
from the pure `ServerNotification.title(project, endpoints)`
(`Serving <project> on <ip>:<port>`, loopback form when LAN is off) and
`summary(list)` collapses many servers into `N servers serving · tap to stop
all`. While a server is *serving*, the per-second elapsed-clock ticker is
suppressed — re-notifying every second for a static sentence is a battery
burn with no information in it. `EditorViewModel` promotes a `serverRun` past
the existing >5 s threshold and re-issues the notification on the bind line
(`refreshServingNotification`), because the URL usually lands after the
promotion.

### Three real bugs the host tests caught (fixed, not worked around)

1. **`Content-Length` with no body.** 404/405/500 responses from
   `WebPreviewServer` announced their length and then sent nothing →
   `HttpURLConnection` failed with "Premature EOF" instead of showing the
   error (it broke the LAN traversal test first). Now every non-HEAD response
   carries its body; HEAD stays body-less, which is the correct answer.
2. **Stop did not release the port.** `sh -c "python3 app.py"` leaves the
   *shell* as the child: `destroy()` killed the shell and orphaned the server,
   which kept answering on the port with nothing left to stop it (reproduced
   on the host: `python3` reparented to pid 1, HTTP 200 after Stop). New pure
   `ServerLaunch` (in `ServerRunner.kt`) prefixes a **single simple command**
   with `exec` — so the pid the runner kills *is* the server — and refuses to
   touch anything with shell structure (`&&`, `;`, pipes, redirections,
   builtins, `FOO=bar …`), because rewriting those is how a working run
   becomes a broken one. `ServerRunner` also waits for its reader thread
   before completing the flow, so the last lines (the bind line, an
   `Address already in use`) are never dropped by a fast exit.
3. **A stopped preview could not be re-bound on the first try.**
   `stop()` closed the socket while a thread sat blocked in `accept()`, so
   "Stop, then re-run on the same port" raced the fd release. The accept loop
   now polls with a bounded `soTimeout`, closes the socket itself, and
   `stop()` waits on a latch — the port is free when `stop()` returns
   (deterministic in 5 consecutive local runs; previously flaky). Failed
   binds also stopped leaking their fd: `startAt` binds in two steps and
   closes on failure, so a LAN pool walk does not fight its own leftovers.

Plus a pinned regex fix: uvicorn's `attempt(ing)? to bind on address
('0.0.0.0', 8080)` line was captured as port `"0"` (the greedy prefix split
`808|0`), which turned a clash into a nameless message.

## Tests (as shipped)

- `ServerRegistryTest` (12) — claim/release, the `port <= 0` rule, conflict
  tagging + message, `stoppableIds`, `allEntries` keeping a stopped row
  (`assertFalse(entry.alive)`), the recomputed-endpoints rule, the clock
  injection.
- `ServerNotificationTest` (5) — title/body/summary mapping, including the
  "endpoints not published yet" promotion (no URL, still honest) and the
  many-servers summary.
- `ServerHostTest` (11) — **real sockets + a real `/bin/sh python3
  dev_server.py` child**: loopback serve + body; LAN wildcard with an
  unadvertised `127.0.0.1` then a real `192.168.1.20`; re-attach idempotence;
  a LAN flip frees the old socket; stop → the same preferred port is
  claimable again; an unreadable root is refused with a message; **a server
  outlives its first observer and the next observer sees the tail**;
  `stopAll`; a process that dies on `Address already in use` produces
  `BindFailed` (not an anonymous `Exited(1)`); `refreshLan` in both
  directions; an unusable address means no `lanUrl`.
- `ServerLaunchTest` (4) — the three preset server commands are exec-able,
  every compound command is passed through untouched, no double `exec`,
  whitespace tolerated.
- Negative socket assertions go through `assertClosed(port)` (poll until the
  port stops answering, with the LISTENing ports in the failure message) —
  the honest shape of "the port was released", immune to teardown timing and
  to another class's leftover.
