# Phase 4 Part 4.7 — Android-integration foundation slice (clipboard bridge)

**Status: 🚧 IN PROGRESS (2026-08-26).** The reusable bridge protocol
(`CodeCApi`) and the first capability (`codec-clipboard`) are implemented and
covered by host tests that run in CI. **The device gate has not been run
yet** — per this project's discipline this part is *not* claimed DONE until
the transcript in §7 passes on a real phone.

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

## 7. Device acceptance transcript (NOT RUN YET — run on a real arm64 phone)

Install the CI `CodeC-IDE` artifact from a green `Build APK` run of this
branch. With the pinned `debug.keystore` in place this installs **in place**
(no wipe). Open Term (the shell rewrite happens on start) and run each line
separately:

```sh
which codec-clipboard                       # $PREFIX/bin/codec-clipboard
codec-clipboard status                      # clipboard: text|empty|non-text + length
codec-clipboard set "hello from codec"      # OK
codec-clipboard get                         # hello from codec
# Paste check: long-press the terminal -> Paste, or open another app and paste.
codec-clipboard clear                       # OK
codec-clipboard get; echo "exit=$?"         # blank output, exit=0
codec-clipboard set "$(cat /etc/profile)"   # multi-line content round-trip
codec-clipboard get | head -3
# Negative: non-text clip (copy an image from Photos, then):
codec-clipboard get; echo "exit=$?"         # ERR: clipboard does not contain text, exit=1
pkg update && dpkg --audit                  # must stay clean (no regression)
echo 'int main(void){printf("clip-ok\n");return 0;}' > t.c
cc t.c -o a.out && ./a.out                  # TCC regression: clip-ok
```

Pass criteria: every command above prints the expected line; `cc` still
compiles/runs; `dpkg --audit` silent; `codec-clipboard` still works after
airplane-mode restart (clipboard is app-side, no network involved).

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
