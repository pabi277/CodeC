# CodeC Phase 7 — Multi-Terminal Sessions

**Status:** IMPLEMENTED (2026-08-28, `arena/01a048df-codec`; CI compile-green run `33185424586`) — unit tests written (not yet executed by CI — see `README.md` follow-ups) and **device verification pending** · **Cost:** `[client-only]` · **Depends on:** Phase 6 (Terminal UX & Insets verified)  
**Invariants:** no `.` on `PATH`; no `build-package.sh -I`; TCC `-o` last; no `com.termux`; signed repo only; no bootstrap in APK.

> Implementation decisions D1–D12 (resolved against the code *before* coding,
> including the §2.4 amendment: CodeCApi responses are per-invocation
> `mktemp` files, so the shared-`codec-api` protocol is unchanged and
> cross-talk is impossible by construction) are in
> [`PART_7_DESIGN_DECISIONS.md`](PART_7_DESIGN_DECISIONS.md). The switcher UI
> option chosen is the **dropdown** (D9), not a drawer.

---

## 1. Context & Motivation

In Phase 1–6, `TerminalViewModel` owns a single `TerminalSession`. While this works for single-task terminal usage, a real mobile developer workflow requires multitasking:
- Running a long-running process in one session (e.g. `make`, `python server.py`, `top`, or `pkg install`).
- Interactively exploring files, editing code in `nano`, or running `git status` in a second session.
- Termux provides a left-side session drawer where sessions can be created, switched, renamed, and terminated.

Phase 7 introduces `TerminalSessionManager` to manage $N$ concurrent PTY sessions, provides an intuitive session drawer / switcher UI, and routes in-band `CodeCApi` protocol requests per active session without cross-talk.

---

## 2. Architectural Design (Decision D1)

### 2.1 Session State Model
```kotlin
data class TerminalSessionItem(
    val id: String = UUID.randomUUID().toString(),
    val sessionNumber: Int,
    var customTitle: String? = null,
    val session: TerminalSession,
    val createdAt: Long = System.currentTimeMillis()
) {
    val displayTitle: String
        get() = customTitle?.takeIf { it.isNotBlank() }
            ?: session.snapshot.value.title.takeIf { it.isNotBlank() && it != "Terminal" }
            ?: "Session $sessionNumber"
}
```

### 2.2 `TerminalSessionManager`
- Manages an observable list of `TerminalSessionItem` (`StateFlow<List<TerminalSessionItem>>`).
- Tracks the `activeSessionId` (`StateFlow<String>`).
- Operations:
  - `createSession(initialCommand: String? = null): TerminalSessionItem`: Spawns a new PTY, starts shell bootstrap, assigns session index, and selects it.
  - `switchSession(id: String)`: Updates `activeSessionId`.
  - `closeSession(id: String)`: Sends SIGKILL/close to the session's PTY, cleans up buffers, and switches active selection to adjacent session.
  - `renameSession(id: String, newName: String)`: Allows user to label sessions (e.g., "Build", "Server", "Git").
- Concurrency & Lifecycle:
  - Activity-scoped lifecycle so background sessions continue executing when switching tabs or sessions.
  - `WakeLock` stays held as long as at least one active session has `alive == true`.

### 2.3 UI & Navigation
- **TopAppBar Session Switcher:**
  - Drawer icon / session badge in TopAppBar showing active session name (e.g., `[1] main.c` or `[2] pkg install`).
  - Dropdown / Drawer modal listing all active sessions with status badges (running/idle/exited) and a `+ NEW SESSION` button.
- **Swipe or Drawer Switching:**
  - Drawer gestures / horizontal tab bar to switch between active sessions in 1 tap.
  - Quick action to terminate/close individual sessions with confirmation if running.

### 2.4 CodeCApi Routing
- Each session's OSC 1337 requests include the session's private API directory or tag.
- Responses are delivered strictly to the requesting session's PTY without interference.

---

## 3. Implementation Steps

1. **Step 1:** Create `TerminalSessionManager.kt` in `app/src/main/java/com/codeci/ide/ui/terminal/`.
2. **Step 2:** Refactor `TerminalViewModel.kt` to delegate session creation, switching, and termination to `TerminalSessionManager`.
3. **Step 3:** Update `TerminalScreen.kt` to include the session drawer / top dropdown menu, "+ New Session" button, session renaming dialog, and close actions.
4. **Step 4:** Add unit tests in `TerminalSessionManagerTest.kt` verifying multi-session creation, switching, closing, and automatic adjacent selection.
5. **Step 5:** Build and verify via Gradle unit tests and GitHub Actions CI.

---

## 4. Exit Condition & Verification Recipe

**Status: PENDING — code + CI compile-green; needs the owner on a real device
with the branch APK (artifact of run `33185424586`).**

A fresh APK passes the following recipe on a real Android device:

```sh
# Setup & Multi-Session Test
# 1. Open Terminal (Session 1 starts).
# 2. In Session 1, run: bash -c 'for i in $(seq 1 30); do echo "tick $i"; sleep 1; done'
# 3. Tap Session Drawer / "+" button -> Create Session 2.
# 4. In Session 2, run: ls -la && echo "session 2 working"
# 5. Switch back to Session 1: observe ticker is still counting (e.g. "tick 12").
# 6. In Session 2, test CodeCApi: codec-toast "Hello from Session 2" -> Toast appears.
# 7. Close Session 1 -> Active view smoothly transitions to Session 2 without crash.
# PASS
```

---

## 5. Non-Goals & Invariants

- **Not in Phase 7:** Projects / folder tree (Phase 8), Editor split (Phase 9/11).
- **Session Persistence:** Termux does not persist PTY processes across full app termination/process kill (Linux OS limitation); sessions persist across screen/tab navigation during app runtime.
