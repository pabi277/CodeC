# CodeC Phase 7 — Design decisions (open questions resolved before coding)

**Status:** Decided 2026-08-28 · **Part:** Phase 7 multi-terminal sessions ·
**Depends on:** Phase 6 (merged PR #25) · **Cost:** `[client-only]`

Per the order of work (verify → decide open questions → code → host-test → CI →
device), this file records every open technical question found while reading
`PART_7_MULTI_TERMINAL.md` against the *actual* code on `main` (`2ca8612`),
the decision, and the evidence behind it. The plan document (D1) is amended
where the code contradicted it — the code wins (self-distrust protocol §1).

Evidence examined:

- `ui/viewmodels/TerminalViewModel.kt` — activity-scoped, owns exactly one
  `TerminalSession`; per-session `codecApiRequests` collected in
  `viewModelScope`; wake lock bound to the single `session.alive`.
- `ui/terminal/TerminalSession.kt` — one PTY + one `TerminalEmulator` + reader
  coroutine; `start(prepared)` / `stop()` / `resetEmulator()`; safe to
  construct without spawning a PTY (host tests can instance it freely).
- `ui/terminal/CodecApiProtocol.kt` + `ShellEnvironment.clipboardScript()`
  (and the other `codec-*` scripts) — every CLI invocation `mktemp`s its **own**
  `req.XXXXXX`/`res.XXXXXX` pair under the shared `$PREFIX/tmp/codec-api`; the
  app writes the outcome into the *named response file*; the CLI polls that
  file. Nothing is ever written back through the PTY.
- `ui/screens/TerminalScreen.kt` — renders one `snapshot`; `TopAppBar` title
  from `snapshot.title`; five action icons; `ModulesScreen` and the
  `Screen.Terminal` route feed commands via `sendCommand`.
- `ui/components/TerminalEmulatorView.kt` — `onResize` fires from
  `LaunchedEffect(ptyCols, ptyRows)` only, i.e. **only when the computed grid
  dims change**, not when the bound snapshot is swapped.

---

## D1 — Session state model (from the plan; confirmed)

`TerminalSessionItem(id, sessionNumber, customTitle, session, createdAt)` with
`displayTitle = customTitle → live shell title → "Session N"`. Verified:
`TerminalBuffer`'s default title is exactly `"Terminal"` (TerminalBuffer.kt
`var title: String = "Terminal"`), so the plan's fallback predicate
(`!= "Terminal"`) is correct as written. `customTitle` is an immutable `val`;
rename = `copy()` (keeps `StateFlow<List<TerminalSessionItem>>` semantics
clean). Session numbers are **monotonic per app process and never reused**
(Termux behavior) — closing session 2 does not renumber session 3.

## D2 — Manager placement & purity

`TerminalSessionManager` lives in `ui/terminal/` as **pure Kotlin** (no Android
imports): `synchronized` state, an injectable session factory
`createTerminalSession: () -> TerminalSession` (default `{ TerminalSession() }`),
an injectable `aliveOf` accessor for tests, and its own `SupervisorJob` scope
only for alive-watcher coroutines. Rationale: `TerminalSession` is already
host-constructible (no PTY until `start`), so the whole manager is
unit-testable on the JVM without Robolectric. The **ViewModel keeps every
Android concern** (settings, `ShellBootstrap`, `UserlandInstaller`, wake lock,
permission flows) and delegates state to the manager — this is the plan's
Step 2 ("refactor TerminalViewModel to delegate") with the seam drawn at the
Android boundary.

## D3 — Non-suspend manager API

All manager operations (`createSession`, `switchSession`, `closeSession`,
`renameSession`, `closeAll`) are plain blocking calls guarded by a Java lock —
they only copy small lists and launch watcher jobs. No Kotlin `Mutex`/`suspend`:
the ViewModel calls them from `viewModelScope.launch(Dispatchers.IO)` where
needed, and `onCleared()` (which runs *after* `viewModelScope` is cancelled)
can still shut down deterministically without racing a cancelled coroutine
scope. This avoids the cancelled-scope `Mutex` hang on process teardown.

## D4 — CodeCApi routing: **no protocol change** (plan §2.4 amended)

The plan said "Responses are delivered strictly to the requesting session's
PTY" and implied per-session API directories. **The code says otherwise and the
code wins**: responses are delivered through the per-invocation response *file*
(`mktemp "$API_DIR/res.XXXXXX"`), which the requesting CLI polls — the PTY is
never a response channel. Because every concurrent `codec-*` call already owns
unique request/response files, two sessions invoking the bridge simultaneously
cannot collide. Decision:

- keep the single shared `$PREFIX/tmp/codec-api` directory and the existing
  protocol byte-for-byte (no bootstrap, script, or invariant churn);
- launch **one `codecApiRequests` collector per session** in the ViewModel
  (today there is exactly one, for the single session), so a background
  session's `codec-toast` still executes while another session is foregrounded;
- `storagePermissionRequests` / `bellEvents` / notification-permission flows are
  likewise relayed per session and merged at the ViewModel.

This satisfies §2.4's intent (no cross-talk) with zero wire-format change.
Verified by reading `CodecApiProtocol.kt` header comment and
`ShellEnvironment.clipboardScript()` (`req="$(mktemp …req.XXXXXX)"`).

## D5 — External command routing (Modules/Hub/Editor handoff)

`sendCommand` from `ModulesScreen`, the `Screen.Terminal(cmd=…)` route, and the
Editor handoff continue to target **the active session** (previous behavior
was "the only session"; active-session routing preserves it exactly). If the
active session has exited, the old code queued the command and restarted the
shell; the new code keeps that contract for the active item (stop → reset →
start → run queued). Line discipline (`command + "\r"`) is untouched — Phase
10's dispatch behavior is unchanged.

## D6 — Close semantics, adjacent selection, last-session behavior

- `closeSession(id)`: stop the PTY, drop the item, select the **adjacent**
  session (same index in the shortened list, else the new last) — plan §2.2.
- If the closed session was still `alive`, the UI asks for confirmation
  first (plan §2.3), because `stop()` SIGKILLs the process group.
- **Closing the last session auto-creates a fresh one** (next monotonic
  number) instead of showing an empty screen: the Terminal tab always has a
  usable shell, matching `ensureStarted()`'s guarantee today and Termux's
  "always at least one session" feel. A naturally *exited* session (user typed
  `exit`) is **not** auto-closed: it stays listed with an "exited" badge so the
  final output stays readable, exactly like today's "— exited" title suffix.

## D7 — Session cap

`maxSessions = 8` (constant in the manager, injectable for tests). A PTY costs
two fds + a reader thread + a scrollback buffer; 8 is far beyond a phone
workflow and safely below fd pressure. At the cap, `createSession()` returns
`null` and the UI shows a toast instead of silently ignoring the tap.

## D8 — Wake lock

One partial wake lock, held while **any** session is alive (plan §2.2), reusing
the existing `CodeC::TerminalWake` tag and 10-minute renew-acquire pattern from
Phase 6.1. The manager exposes `anyAlive: StateFlow<Boolean>` (recomputed on
add/remove and on every member's `alive` emission); the ViewModel's existing
collector just switches source from `session.alive` to `manager.anyAlive`.

## D9 — Switcher UI (plan §2.3 option chosen)

Material3 **DropdownMenu** anchored on a session-number badge in the
TopAppBar's `navigationIcon` slot (chosen over a drawer: no new dependency, no
gesture conflicts with Phase 6's pinch-to-zoom/scroll pointers, one tap to
switch). Rows: status dot (green = running, gray = exited), number,
`displayTitle`, rename pencil, close ✕. Footer row: "+ New session". Rename =
`AlertDialog` + `OutlinedTextField`; close-while-running = `AlertDialog`
confirm. The TopAppBar title shows the active `displayTitle` (+ " — exited"
suffix as today).

## D10 — Resize on session switch ( latent bug found by review )

`TerminalEmulatorView` reports `onResize` only when its own computed
`ptyCols/ptyRows` **change** (`LaunchedEffect(ptyCols, ptyRows)`). Switching
sessions does not change view size, so a freshly created (or long-backgrounded)
session would keep the emulator-default 80×24 grid while the view lays out
e.g. 52×28 → cursor drift, the exact class of bug Phase 6.1 killed. Fix:
`TerminalEmulatorView` gains an optional `resizeKey: Any? = null` included in
the `LaunchedEffect` keys; `TerminalScreen` passes the active session id so a
switch re-applies the current dims to the newly active PTY. (Default `null`
keeps every other call site byte-identical.)

## D11 — `installUserland` (force) and `restart`

- `restart()` (toolbar) restarts **only the active session** in place
  (stop → `resetEmulator()` → start, same id/number/scrollback-cleared
  semantics as today).
- `installUserland()` (force re-install) stops **all** sessions and returns to
  exactly one fresh session: the userland under every running shell was just
  been replaced, so old PTYs hold stale `$PATH`/env; pretending their tabs
  survived would be a lie. (Previous behavior: the single session was
  restarted; with N sessions the honest equivalent is a full reset.)

## D12 — Persistence & lifecycle (from the plan §5; confirmed)

Sessions persist across tab/screen navigation (already true: the ViewModel is
activity-scoped and outlives `TerminalScreen`). Nothing persists across app
process death — a PTY's child processes cannot survive the app anyway (Linux /
Android OOM+kill semantics; same as Termux). `onCleared()` stops every session
and releases the wake lock.

## Invariants check

None of the law invariants are touched: no `PATH` changes, no
`build-package.sh`, no `cc`/`bash` shim changes, TCC link order untouched, no
official Termux packages/repositories, no bootstrap in APK, repository
signing untouched. The CodeCApi wire protocol is byte-identical (D4). The
`pkg install -y` / `\r` dispatch from Phase 10 is unchanged (D5). Client-only
Kotlin + one test file.

## Exit condition (unchanged, from PART_7_MULTI_TERMINAL.md §4)

Device recipe: two sessions, ticker keeps counting in the background, `ls`
works in session 2, `codec-toast` fires from session 2, closing session 1
transitions cleanly. Code + host unit tests + CI green are this chat's
deliverable; the device pass needs the owner on hardware.
