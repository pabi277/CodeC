# CodeC Phase 19.3 — Live render cadence & streaming output

**Status:** IMPLEMENTED (2026-08-31, `arena/01a056aa-codec`) — host tests written, CI pending · **Cost:** `[client-only]`
· **Depends on:** none (independent; safe to do first)
· **Fixes bug #1:** *"if I download something it prints everything after the
download"* — progress bars / streaming output only appear at the end.
· **Primary target files:** `ui/terminal/TerminalSession.kt`,
`ui/viewmodels/TerminalViewModel.kt`

---

## 1. Evidence — why output isn't live

The read loop publishes a snapshot after **every** 4 KB chunk, into a
**conflating** `StateFlow`:

```kotlin
// TerminalSession.readLoop()
synchronized(emulator) { emulator.feed(buf, 0, n) }
publish()                         // → _snapshot.value = emulator.snapshot()

// TerminalSession
private val _snapshot = MutableStateFlow(emulator.snapshot())

// TerminalViewModel
val snapshot: StateFlow<TerminalSnapshot> =
    activeItem.flatMapLatest { it?.session?.snapshot ?: flowOf(empty) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, empty)
```

Two problems during fast output (a download writing a progress bar with `\r`):

1. **`StateFlow` conflates.** It only keeps the **latest** value. During a burst
   the reader overwrites `_snapshot.value` far faster than Compose recomposes, so
   Compose collects **one** value — usually the final state. Every intermediate
   progress frame is dropped → "everything appears at the end."
2. **Snapshot cost per tiny chunk.** `emulator.snapshot()` rebuilds every line
   (`rowToLine` over all rows + full scrollback) and bumps `generation`. Doing
   that per 4 KB chunk during a burst is wasteful; doing it *and* dropping the
   result (problem 1) is doubly wasteful. There's no frame-paced coalescing like
   Termux's `notifyScreenUpdate` → throttled render.

> Note: `\r`-style progress bars overwrite the same line, so "live" means we must
> actually **render intermediate frames at a steady cadence**, not just deliver
> the final buffer. Conflation is the root cause.

---

## 2. Design — decouple reading from rendering (Termux-style cadence)

Re-implement Termux's decoupling: the reader keeps feeding the emulator as fast
as the PTY delivers; a **separate, frame-paced emitter** publishes snapshots at a
steady rate (~30–60 Hz) whenever the buffer changed.

### 2.1 A "dirty" flag + paced emitter

- The read loop **stops calling `publish()` per chunk**. Instead it sets an
  atomic `dirty = true` after `emulator.feed(...)`.
- A **single render coroutine** (per session) loops: if `dirty`, clear it, take
  one `emulator.snapshot()`, publish it, then `delay(frameIntervalMs)`
  (~16–33 ms). This coalesces a burst of chunks into at most one snapshot per
  frame — smooth, cheap, and it **guarantees intermediate frames are emitted**
  (one per frame) so a progress bar animates.
- On stream **idle** (no dirty for a while) the emitter parks cheaply (suspend
  until signaled) so it costs nothing when the shell is quiet.
- On process exit / final notice, force one last publish so the terminal ends on
  the true final state.

### 2.2 Keep flow semantics correct

- The public `snapshot` can stay a `StateFlow` **as long as** the paced emitter
  is what writes it (at most ~60/s), because each written value is now a distinct
  frame the UI can keep up with. (Conflation is fine at frame rate; it was only
  harmful at burst rate.)
- Alternatively expose the frames via a `SharedFlow(extraBufferCapacity=1,
  onBufferOverflow = DROP_OLDEST)` fed by the emitter and keep a `StateFlow`
  mirror for the initial value. Either works; the **paced emitter** is the fix,
  not the flow type. Prefer the minimal change (keep `StateFlow`, add the
  emitter).

### 2.3 Coalescing threshold / large bursts

- Cap the emulator's work: if a single `read()` returns a big chunk, still
  `feed()` it whole (the parser is streaming), just set `dirty`. The emitter's
  fixed cadence naturally throttles snapshot cost regardless of chunk size —
  this is what makes a `yes`-flood or a `cat bigfile` stay responsive instead of
  freezing the UI thread with thousands of snapshots.
- `generation` still increments per snapshot; `TerminalEmulatorView`'s
  `LaunchedEffect(snapshot.generation)` (auto-scroll to live) keeps working and
  now fires at frame cadence, not per chunk.

### 2.4 Interactions to preserve

- **Phase 7 multi-session:** the emitter is per `TerminalSession`; the ViewModel
  still `flatMapLatest`es the active session's `snapshot`. Background sessions
  can pause their emitter (nobody's collecting) and resume on activation — a nice
  extra, but at minimum they must not block.
- **Bell/CodecApi/storage** SharedFlows are unaffected (separate channels).
- **Interactive runs / PtySession** input latency: input write path is unchanged;
  only the *output* publish cadence changes.

---

## 3. Implementation steps

1. In `TerminalSession`: add `private val dirty = AtomicBoolean(false)` and a
   `Channel`/`signal` the reader sets; the read loop calls
   `synchronized(emulator){ emulator.feed(...) }` then `signalDirty()` instead of
   `publish()`.
2. Launch a per-session **render coroutine** (in `start()`, cancelled in
   `stopLocked()`): await dirty → snapshot → set `_snapshot.value` →
   `delay(~16ms)`. Force a final publish in the `finally`/exit path.
3. Keep `resize()`/`notice()`/`resetEmulator()` calling an **immediate** publish
   (user-visible, low frequency) — or route them through `signalDirty()` + a
   nudge; immediate is fine for these.
4. Leave `TerminalViewModel.snapshot` as-is (StateFlow); confirm it now receives
   ~frame-rate frames during a download.
5. Tune `frameIntervalMs` (start 16 ms; expose a constant). Ensure the emitter
   doesn't spin when idle (suspend on the signal).

## 4. Host unit tests (CI-run)

The cadence uses coroutines/time — test with `kotlinx-coroutines-test`
(`runTest`, virtual time), asserting *behavior* not pixels:
- `SnapshotCadenceTest` — feeding N bursts within one frame window yields **one**
  published snapshot; bursts across M frames yield ~M snapshots (coalescing).
- `intermediate frames are emitted` — feed "10%\r", advance a frame, feed
  "50%\r", advance a frame, feed "100%\r": three distinct snapshots observed
  (proves progress animates, not just the final).
- `final state always published on exit` — after the loop ends, the last
  snapshot equals the final buffer even if it arrived mid-frame.
- `idle emitter parks` — no snapshots emitted while nothing is fed.
Keep the existing `AnsiParserTest`/`TerminalBufferTest` green (feed semantics
unchanged).

## 5. Exit condition & device recipe

```text
1. Terminal: pkg install something sizable, or:
     python3 -c "import time,sys
     for i in range(0,101,2):
         sys.stdout.write('\rDownloading %3d%%' % i); sys.stdout.flush(); time.sleep(0.05)
     print()"
   EXPECT: the percentage counts up SMOOTHLY on one line in real time
   (not a single jump to 100% at the end).
2. Run `yes | head -100000` or `cat /usr/bin/large` → output streams smoothly;
   UI stays responsive; no freeze; ends on the correct final screen.
3. Run `apt update` / a real download → progress/percentages animate live.
4. top/htop refresh smoothly at their interval.
PASS = streaming output and progress bars animate live like Termux (step 1 & 3).
```

## 6. Invariants

Client-only; Kotlin coroutines only; no native/PTY/parser changes; Phase 7
multi-session routing, bell/CodecApi channels, and wake-lock behavior preserved.
No `.` on PATH.


---

## 7. Research notes (2026-08-31)

* Confirmed the conflation mechanism against kotlinx.coroutines docs:
  `MutableStateFlow` "conflates … updates always update the value and are
  not coalesced" — intermediate values are skipped for slow collectors, so
  the reader MUST NOT be the publisher during a burst.
  (kotlinx.coroutines StateFlow documentation.)
* The paced-emitter pattern used (`Channel(CONFLATED)` as a dirty signal +
  `receive()` → `publish()` → `delay(frameIntervalMs)` loop) is the standard
  frame-coalescer shape: conflated signals arriving during the `delay` are
  absorbed by the next `receive()`, so each published value carries the
  LATEST state while guaranteeing one publication per frame.
* `runTest` virtual time (`advanceTimeBy`/`advanceUntilIdle`,
  kotlinx-coroutines-test 1.10.2) exercises the cadence without real time.

## 8. Implementation record (2026-08-31, commits 865fa79)

* **New `RenderPump.kt`** (pure Kotlin): `markDirty()` never blocks; a
  single coroutine parks on `receive()`, publishes, sleeps
  `DEFAULT_FRAME_INTERVAL_MS = 16` (~60 fps), and repeats. Idle cost: zero.
* **`TerminalSession`**: the read loop now calls `renderPump.markDirty()`
  after `emulator.feed(...)`; a per-session render job is started in
  `start()` and cancelled in `stopLocked()`. **Immediate publishes are kept
  for low-frequency, user-visible events** — `resize()`, `notice()`,
  `resetEmulator()`, the start-failure path, and the reader's `finally`
  block (the true final state must land even mid-frame) — exactly the
  design in §2.4. Phase 7 multi-session routing is untouched (one pump per
  `TerminalSession`; the ViewModel still `flatMapLatest`es the active
  session's `StateFlow`).
* **Tests** — `RenderPumpTest` (6, `runTest` virtual time): burst-in-one-
  frame → 1 publish; bursts across M frames → M publishes; intermediate
  states `[10, 50, 100]` are all observed (the pre-fix behavior collapsed
  them to a single 100); idle publishes nothing; newest state wins inside a
  busy window; a pre-start dirty mark publishes once started.
* **Perf note:** while touching the view, the per-frame
  `snapshot.scrollbackLines + snapshot.lines` concatenation (an O(2000)
  list copy every draw at 60 fps) was replaced by a `lineAt(extY)` index
  helper (draw loop, tap, long-press, `selectedText`).

**Device gate (owner):** §5 recipe unchanged. PASS = progress bars count up
smoothly (steps 1 & 3) and `yes | head -100000` stays responsive.
