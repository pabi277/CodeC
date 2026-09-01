# CodeC Phase E.2 — Background-Run Notification (Foreground Service)

**Status:** 📋 **PLANNED** · **Cost:** `[client-only]` · **Effort:** S
· **Depends on:** nothing (Phase 4.8 already declared `POST_NOTIFICATIONS`)
· **Target files:** `ui/services/CompilerService.kt`, `AndroidManifest.xml`

---

## 1. Design

Long-running programs (server presets, Go/Rust builds) are killed by Android
when the user switches away. A **foreground-service notification** keeps the
run alive and lets the user return.

### Notification spec

```
┌──────────────────────────────────────────────┐
│ CodeC  ▶ Running: server.py                  │
│         12 seconds · tap to return           │
│                          [ Stop ]            │
└──────────────────────────────────────────────┘
```

- **Title:** "Running: `<filename>`"
- **Body:** elapsed time (updated every second while running).
- **Action:** "Stop" → calls `ExecutionRunner.cancel()` / `InteractiveRunSession.kill()`.
- **Tap on notification** → brings the CodeC app to foreground, navigates to the
  editor screen with the Output Panel open.
- Notification is posted when run duration exceeds **5 seconds** (short runs
  don't need a notification — avoids noise for hello-world programs).
- Notification is **cancelled** when the run finishes (any `RunFinished` / `Failed`).

### Implementation approach

Extend `CompilerService` (already a bound service) to call
`startForeground(id, notification)` when the 5-second threshold is crossed.
Use `NotificationCompat.Builder` with `FOREGROUND_SERVICE_TYPE_SPECIAL_USE`
(or `DATA_SYNC` — choose based on API level requirements; research in §4).

`POST_NOTIFICATIONS` runtime permission is already requested in Phase 4.8. No
new permission needed.

---

## 2. Implementation steps

1. Add a 5-second `delay` coroutine in `CompilerService` that calls
   `startForeground` if the run is still in progress.
2. Add a "Stop" `PendingIntent` action that broadcasts to `CompilerService`
   to cancel the run.
3. Add tap `PendingIntent` that opens the editor screen.
4. Cancel the notification on `RunFinished` / `Failed`.
5. Declare `FOREGROUND_SERVICE` + `FOREGROUND_SERVICE_SPECIAL_USE` in
   `AndroidManifest.xml` (check which type is needed for the targetSdk).
6. Write a host unit test — mock the 5-second timer; verify `startForeground`
   is called iff the run exceeds the threshold.

---

## 3. Exit condition

```text
1. Start a long-running Python server (e.g. Flask, port 5000).
2. Switch to another app.
   EXPECT: notification appears after ~5 seconds:
   "CodeC ▶ Running: app.py — X seconds"
3. Tap "Stop" in the notification.
   EXPECT: server stops; notification disappears.
4. Run hello-world (exits in < 1 second).
   EXPECT: no notification appears.
5. Start a long run; let it finish naturally.
   EXPECT: notification disappears automatically on completion.
PASS = steps 1–5 behave as described.
```

---

## 4. Research notes (fill in before implementing)

> **TODO:**
> - Check whether `CompilerService` is already a started/bound Service or a plain
>   `ViewModel` coroutine. If it is only a coroutine, escalating to a foreground
>   service requires wrapping in a proper `Service` first.
> - Look up `FOREGROUND_SERVICE_TYPE_*` requirements for targetSdk 28 vs. 34+.
> - Confirm `POST_NOTIFICATIONS` is the only permission needed (no
>   `FOREGROUND_SERVICE_DATA_SYNC` or similar required for SDK 28).
