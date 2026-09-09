# CodeC Phase 37.2 — Foreground keep-alive + port lifecycle

**Status:** 📋 PLANNED · **Cost:** `[client-only]` · **Effort:** M

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

## Tests

- `ServerRegistryTest` (host): add/update/stop; duplicate port detection;
  the single-truth accessors.
- The notification title/action mapping is host-tested through a small pure
  formatter (no Service in the unit test).
