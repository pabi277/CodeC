# Phase 4 Part 4.7 — Android-integration foundation slice (clipboard bridge)

**Status: ✅ DONE (device-verified 2026-08-26).** The reusable bridge
protocol (`CodeCApi`) and the first capability (`codec-clipboard`) are
implemented, host-tested in CI, and verified on a real arm64 phone —
§5.1 and §5.2 pass every primary check, including the piped/redirected
channel fix. The two remaining optional negatives (§7: non-text clipboard
read, airplane-mode restart smoke) were **explicitly waived by the owner
(2026-08-26)** after the primary checks passed; the non-text read is
covered by host tests and the restart smoke cannot regress (clipboard is
app-side, no network). Review also found and fixed one robustness gap
after device acceptance (§5.3, F2): bridge requests are now consumed in
the activity-scoped `TerminalViewModel` instead of the Terminal screen, so
a `codec-clipboard` running from another tab or a queued initial command is
never dropped. Exit condition met; next slices (4.8, 4.9, …) extend the
same protocol.

---

## 1. Decision D1 — first capability: clipboard

The roadmap left the exact first capability open. Chosen: **clipboard access**
(`codec-clipboard get|set|clear|status`).

| Candidate | Why not first |
|---|---|
| **clipboard** ✅ chosen | zero runtime permission, no notification/channel complexity, immediately useful in a C IDE (copy compile output to a comment, paste snippets into `nano`), and it is the roadmap's own example. |
| vibrate | needs `VIBRATE` permission + a pattern API; near-zero user value in a terminal. |
| notify/toast | `POST_NOTIFICATIONS` runtime permission on 13+ adds a permission-flow requirement better proven with a capability that needs it; keep it for 4.8. |
| share sheet / open URL | requires launching an Activity from the bridge; done as a later slice. |

The point of 4.7 is the *pattern*, not breadth: one capability that proves
permission-free operation end to end, with the extension points documented so
4.8+ capabilities (notify, vibrate, share, open URL, toast) add one wire op +
one CLI script and reuse the plumbing.

## 2. Protocol v1 — in-band OSC 1337 + private file exchange

A terminal program requests an Android capability by printing:

```text
ESC ] 1337 ; CodeCApi:<op>:<requestFile>:<responseFile> BEL
```

- `<op>` is one of `clipboard.get`, `clipboard.set`, `clipboard.clear`,
  `clipboard.status` (the wire form; new capabilities extend this set).
- `<requestFile>` is the path of an app-private payload file. `clipboard set`
  reads its content from here. Empty for ops that need no payload.
- `<responseFile>` is where the app writes the outcome. The CLI polls for
  the *file to exist* (the app creates it via an atomic rename), so an empty
  clipboard on `get` is a valid empty response — no content in the escape
  sequence, no reliance on PTY echo. The response name is derived from the
  request temp file (`${req}.out`), so it is unique and **not pre-created**
  (`mktemp` is used only for the request file; a pre-created response file
  would break the existence-based handshake).
- Both paths must be **direct children** of `$PREFIX/tmp/codec-api`
  (canonicalized, symlinks resolved). The app refuses anything else.
- CLI subcommand names map to wire names: `get` → `clipboard.get`,
  `set` → `clipboard.set`, etc.
- The CLI emits the sequence to the **controlling terminal** (`/dev/tty`)
  when it has one and falls back to stdout otherwise, so piped/redirected
  stdout (`codec-clipboard get | head`, `codec-clipboard get > file`) still
  reaches the emulator. The fallback is engaged by *attempting* the write
  (a `[ -w /dev/tty ]` test is unreliable: `access(2)` reports success even
  when there is no controlling terminal and the real `open()` then fails
  with ENXIO).
- Content never travels inside the OSC payload (only ~120 bytes of paths),
  keeping it far under the emulator's 1024-byte OSC cap and avoiding
  base64/binary issues.

Response conventions:

| Op | Success body | Failure |
|---|---|---|
| `clipboard.get` | raw clipboard text (`""` if the clipboard is empty) | `ERR:<message>` |
| `clipboard.set` | `OK` | `ERR:<message>` |
| `clipboard.clear` | `OK` | `ERR:<message>` |
| `clipboard.status` | `clipboard: text\|empty\|non-text` + `length: N` | `ERR:<message>` |

CLI conventions: success prints the body (adding a trailing newline for
terminal readability) and exits 0; an `ERR:` body prints the message to
stderr and exits 1; no response after ~2.5 s exits 3 with an actionable
message. `set` content is limited to 4 MiB (the app's memory bound).

### Why not a unix socket (Termux:API style)?

Termux:API uses a socket + C binaries. CodeC's userland has no socket client
that a POSIX script can use (busybox `nc` is TCP-only), and shipping a JNI/C
client binary per capability in the curated catalog is a much bigger lift
than the value of a synchronous socket. The file-exchange shape gives the
same request/response semantics through the already-proven OSC path from
Part 4.1.

## 3. Security model

- **Path confinement is the boundary.** The only thing a terminal-emitted
  payload can name is a direct child of `$PREFIX/tmp/codec-api`; canonical
  resolution defeats symlink escapes; nested paths are rejected so the API
  directory stays flat and predictable.
- **No code from the payload is ever executed.** Side effects are limited to
  a clipboard write (surfaced by Android's standard "copied" system toast)
  and file writes inside the private API directory.
- **In-band injection is accepted for v1** — any text rendered in the
  terminal that embeds `ESC]1337;CodeCApi:...` will be honored. Blast radius:
  same-UID files under the private prefix + the user's clipboard (user sees
  the system toast on writes). This is the same risk class as the Part 4.1
  `CodeCRequestStorage` control. Direction for 4.8+: an opt-in flag or a
  confirmation gate for sensitive capabilities.
- **Android clipboard policy:** reads require the app to be foreground
  (always true when the command runs in the terminal) and Android 13+ shows
  the system "pasted from CodeC" notice. Both are accepted, documented
  behavior (same as Termux:API).

## 4. Architecture (where each piece lives)

| Piece | Path |
|---|---|
| Protocol (parse/build/confinement, pure Kotlin) | `app/.../ui/terminal/CodecApiProtocol.kt` |
| Handler (android adapter + pure `execute` core) | `app/.../ui/terminal/CodecApiBridge.kt` |
| OSC dispatch (`1337` → `CodeCApi:` callback) | `app/.../ui/terminal/TerminalEmulator.kt` |
| Request flow to the UI | `TerminalSession.codecApiRequests` (`SharedFlow`, buffer 16) → `TerminalViewModel.codecApiRequests` |
| Execution | `TerminalScreen` `LaunchedEffect` → `CodecApiBridge.handle(context, payload, codecApiDir)` |
| CLI emitter | `ShellEnvironment.clipboardScript()` → `$PREFIX/bin/codec-clipboard` (written by `prepare()`) |
| API dir | `$PREFIX/tmp/codec-api` (`ShellEnvironment.codecApiDir(prefix)`) |

`BOOTSTRAP_VERSION` 22 → 23: like previous parts, the bump marks the
`prepare()` script refresh so an APK update rewrites `codec-clipboard` into
already-installed userlands (the marker itself is informational, per the
Part 4.5/4.6 review's M2 finding).

## 5. Evidence so far (host side)

New tests (run by CI's `:app:testDebugUnitTest`):

- `CodecApiProtocolTest` — parse/build for all ops; rejection of foreign
  payloads, malformed field counts, unknown ops, empty response paths;
  confinement (direct child only, sibling/`..`/nested/api-dir-self rejected,
  symlink escape resolved); emulator dispatch for `CodeCApi:` and the legacy
  `CodeCRequestStorage` control.
- `CodecApiBridgeTest` — `execute` semantics: get text/empty/non-text, set
  reads the request file and calls the writer, missing/oversized/request
  escape errors, clear, status, unconfined response rejection.
- `ShellEnvironmentTest` — script content (usage, API dir, `mktemp`,
  `trap`, 2.5 s poll, single-backslash ESC/BEL bytes) plus a **full
  round-trip process test**: the real generated script is executed and a
  fake app scans its stdout for the OSC request, writes the response file,
  and the script prints it.

### Verification note on escape bytes

The script must emit real ESC (`\033`) / BEL (`\007`) bytes. This was
byte-verified for both the new `clipboardScript()` and the existing
Part 4.1 `setupStorageScript()` line (single `0x5C` before `033`/`007` in
the source; the shell `printf` then emits the control bytes — a doubled
backslash would print a literal `\033` that the emulator would render as
text, and a byte-level `ord()` check rules that out for both).

## 6. Invariants maintained

- No `.` added to `PATH`.
- `cc`, embedded musl TCC link order (`-o` last, archives twice), and the
  real ELF Bash are untouched.
- `pkg` / APT signing (`signed-by=`, `gpgv`) untouched.
- Part 4.1 storage scripts, symlinks and the `CodeCRequestStorage` control
  unchanged.
- No new package catalog / repository / bootstrap work — nothing to rebuild.
  (`codec-packages/`, the bootstrap, and the repository are untouched; the
  build side of this part does not exist by design.)

## 5.1 First on-device run (2026-08-26) — everything unpiped passed

APK from Build APK run `32915432981` (signed with the pinned debug key,
installed in place). Shell restart rewrote `codec-clipboard`
(`BOOTSTRAP_VERSION` 23) into the existing `userland-v2-dev` userland.

| Command | Result |
|---|---|
| `which codec-clipboard` | ✅ `…/usr/bin/codec-clipboard` |
| `codec-clipboard status` | ✅ `clipboard: text` / `length: 434` (the pasted command block — real text, proving content end-to-end) |
| `codec-clipboard set "hello from codec"` | ✅ `OK` |
| `codec-clipboard get` | ✅ `hello from codec` |
| `codec-clipboard clear` | ✅ `OK` |
| `codec-clipboard get; echo "exit=$?"` | ✅ blank, `exit=0` (empty body = valid response) |
| `codec-clipboard set ""` (empty content via failed `cat`) | ✅ `OK` |
| `pkg update` | ✅ signed channel, no warnings |
| `dpkg --audit` | ✅ silent (clean) |
| `cc` + `./a.out` | ✅ `clip-ok` (warning was the transcript's own missing `#include <stdio.h>`, not a product issue) |

**F1 — piped output swallowed the request channel (REAL DEFECT, fixed).**
`codec-clipboard get | head -3` timed out with `no response from CodeC`.
The request OSC was `printf`-ed to **stdout**, so when stdout is a pipe
`head` buffers it (no newline until the response, which never comes) and
the emulator never sees the request. Same breakage for `get > file`.
**Fix (this PR's follow-up commit):** emit the OSC to `/dev/tty` first and
fall back to stdout only when the write cannot be delivered. Verified
locally under a real PTY for all three channels: direct (OSC + body via
tty), `get | head -3` (OSC via `/dev/tty`, body through the pipe), and
`get > file` (OSC via `/dev/tty`, content in the file) — all exit 0.
On-device re-confirmation of the piped/redirected case is the remaining
step (CI's test process has no controlling terminal, so the round-trip
JVM test exercises the stdout fallback path, not `/dev/tty`).

**Test-command corrections (transcript author's errors, no product fix):**
the userland profile is `$PREFIX/etc/profile`, not `/etc/profile`; the
smoke C file must `#include <stdio.h>`.

### 5.2 Final confirmation of the F1 fix — piped & redirected channels PASSED (2026-08-26)

APK from Build APK run `32916275655` (contains the `/dev/tty` fix),
installed in place on the same `userland-v2-dev` device. Terminal output
(the long lines are wrapped by the narrow terminal grid — the content is
identical in both channels; no data corruption):

```text
codec $ codec-clipboard set "$(cat "$PREFIX/etc/profile")"   # OK
codec-clipboard get | head -3
# CodeC login profile (Phase 1)
export PREFIX='/data/user/0/com.codeci.ide/files/usr'
export HOME='/data/user/0/com.codeci.ide/files/home'
codec-clipboard get > "$HOME/clip.out"; head -3 "$HOME/clip.out"; rm -f "$HOME/clip.out"
# CodeC login profile (Phase 1)
export PREFIX='/data/user/0/com.codeci.ide/files/usr'
export HOME='/data/user/0/com.codeci.ide/files/home'
codec-clipboard clear
OK
```

Result mapping:

| Command | Result |
|---|---|
| `codec-clipboard set "$(cat "$PREFIX/etc/profile")"` | ✅ `OK` — multi-line content (5 lines) set |
| `codec-clipboard get \| head -3` | ✅ first 3 profile lines, exit 0 — **F1 fixed on device** (previously timed out) |
| `codec-clipboard get > "$HOME/clip.out"` … `head -3`, `rm -f` | ✅ same 3 lines from the file; removal clean |
| `codec-clipboard clear` | ✅ `OK` |

**All primary device checks are now green** (existence, `status`, `set`,
`get`, `clear`, empty-`get` exit 0, multi-line content, piped channel,
redirected channel, signed `pkg update`, silent `dpkg --audit`, embedded
`cc` smoke). Combined with the §5.1 run, the only things never exercised
on hardware are the two optional checks below.

### 5.3 Post-acceptance review — F2: request dispatch moved to activity scope

The bridge requests were consumed by a `LaunchedEffect` collector in
`TerminalScreen`. That works while the Terminal tab is composed, but the
PTY/session is **activity-scoped and survives tab switches**, and a
`MutableSharedFlow` emission is silently dropped when no collector is
active (`extraBufferCapacity` does not replay to a late subscriber).
Consequence: a `codec-clipboard` run from another tab — or a queued
initial command racing the screen's collector — would time out. The device
transcripts above stayed green because the commands ran inside the
Terminal tab.

**Fix (committed in this PR, no protocol change):** `TerminalViewModel`
(activity-scoped) now owns the collector — it starts in `init`, is alive
for the whole activity, and calls `CodecApiBridge.handle` with the
application context; `TerminalScreen` no longer collects. Host behaviour
unchanged; rerun of the device transcript is not required because the
protocol, script, and handler are untouched (only **who** consumes the
flow changed). Final CI: Build APK runs `32916275655` (fix) and
`32917705206` (docs) green.

## 7. Device acceptance — remaining optional checks

Status of each primary check (all run on the two artifacts above, on a real
arm64 phone): ✅ `which` (§5.1) · ✅ `status` §5.1) · ✅ `set` (§5.1+5.2) ·
✅ `get` (§5.1) · ✅ `clear` (§5.1+5.2) · ✅ empty-`get` exit 0 (§5.1) ·
✅ multi-line round-trip (§5.2) · ✅ piped `get | head -3` (§5.2) ·
✅ redirected `get > file` (§5.2) · ✅ `pkg update` signed, no warnings
(§5.1) · ✅ `dpkg --audit` silent (§5.1) · ✅ `cc` smoke `clip-ok` (§5.1).
The long-press → Paste UI check was not separately exercised but is
covered by the same `ClipboardManager` set() path that `get` verifies.

**Owner waiver (2026-08-26): both optional checks were skipped by explicit
decision** after all primary checks passed.

```sh
# O1 — non-text clipboard (copy an image from Photos/Gallery first, then):
codec-clipboard get; echo "exit=$?"       # expected: ERR: clipboard does not contain text, exit=1
#   — NOT RUN on device. Covered by CodecApiBridgeTest
#     `get reports non-text clipboard as an error` (host).
# O2 — airplane-mode restart smoke:
#   close CodeC, enable airplane mode, reopen -> Term, then:
codec-clipboard status                    # text/empty + length (no network involved)
codec-clipboard set "after-restart"; codec-clipboard get
which bash cc pkg
echo '#include <stdio.h>
int main(void){printf("clip-ok\n");return 0;}' > t.c && cc t.c -o a.out && ./a.out
#   — NOT RUN. Clipboard/script state is app-private, no network path, and
#     the userland/Bash/cc paths these commands exercise were already
#     verified post-restart in Phase 3's airplane-mode acceptance.
```


## 8. What a later capability adds (4.8+)

1. A `CodeCApi` op (e.g. `notify.show`, `vibrate.buzz`) in
   `CodecApiProtocol.Op` + a branch in `CodecApiBridge.execute`.
2. A `codec-<capability>` script in `ShellEnvironment` (same request/response
   shape, same `codec-api` dir) + a `BOOTSTRAP_VERSION` bump.
3. If the capability needs a runtime permission: `execute`/`handle` can
   return a `NEED_PERMISSION` response and the `TerminalScreen` collector can
   dispatch a `MainActivity` action (the Part 4.1 storage pattern) instead of
   failing — the wiring point already exists.
4. Host tests mirroring `CodecApiProtocolTest`/`CodecApiBridgeTest`/the CLI
   round-trip test, then the same device transcript discipline.
