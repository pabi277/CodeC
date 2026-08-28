# CodeC Phase 5 — Part 5.4 (draft — await owner confirmation)

**Status:** 🚧 PLANNING ONLY (2026-08-26). Not started until owner confirms.
**Depends on:** Phase 5.3 (code bridge/protocol proven; `codec-share` text-only).
**Invariants:** No `.` on PATH; no `build-package.sh -I`; no `com.termux`; `signed-by=`; TCC link `-o` last; no bootstrap in APK.

## 0. Context / why this part

Phase 5.3 delivered four permission-light capabilities over the 4.7/4.8 `CodeCApi` bridge: `toast.show`, `share.text`, `url.open`, `vibrate`. The roadmap (`PHASE5_ROADMAP.md` §Candidate areas) names the next batch as sensors / camera / intents / file-share. This draft picks **file-share of a project file via FileProvider** (`codec-share-file PATH`) because:

- It extends the already-proven `share.text` wire op rather than introducing a new permission model.
- The FileManager UI (`FileManagerScreen.kt` lines 405–410) already uses `Intent.ACTION_SEND` + `EXTRA_STREAM`; integrating it into the CLI/bridge is a small, verifiable step.
- It avoids the deferred GUI/catalog/root areas and avoids the more complex `POST_NOTIFICATIONS` / runtime-permission flow used in 4.8.

If the owner wants sensors / camera / intents / a different capability instead, this doc is discarded and replaced.

## 1. Decision D1 — file-share capability

| CLI | Wire op | Payload | Response | Permission | Notes |
|---|---|---|---|---|---|
| `codec-share-file PATH` | `share.file` | absolute path under `$PREFIX` (or `/data/user/0/…` resolved) | `OK` / `ERR:` | none (FileProvider grants temporary URI access) | Path must be a regular file; directory rejected at CLI; non-http(s) / non-file schemes rejected |

Key design choices:

- **FileProvider** (`res/xml/file_paths.xml` or similar) exposes only files under `files/userland/project/` or the userland tree, not the whole device.
- The bridge writes the absolute path into a request file under `$PREFIX/tmp/codec-api/` (same pattern as 5.3: `mktemp`, atomic rename, exist-based handshake).
- The app resolves the path, verifies it's a regular file (not symlink-to-directory, not outside userland), creates a `content://` URI via FileProvider, and fires `ACTION_SEND` with `FLAG_GRANT_READ_URI_PERMISSION`.
- CLI validates at parse time: must contain one arg, file must exist and be readable (`[ -f "$1" ]`), else `usage:` + exit 2 (defense-in-depth, same as `codec-vibrate abc` in 5.3).
- No bootstrap / package rebuild required; this is client-side APK + CLI script + docs only, consistent with 5.1–5.3.

## 2. Protocol extension (reuses v1 from 4.7 / 5.3)

No protocol version change needed; new wire op group:

```text
ESC ] 1337 ; CodeCApi:share.file:<req>:<resp> BEL
```

- `<req>`: file containing the absolute path (one line, no newlines inside).
- `<resp>`: app writes `OK\n` or `ERR: <reason>\n` after `ACTION_SEND` chooser is launched (or fails). The response is written by the same bridge task that handles 4.8 notifications (activity-scoped `TerminalViewModel`).
- Because `ACTION_SEND` is fire-and-forget (chooser is system-owned), the bridge writes `OK` immediately after confirming the intent was started, not after the user picks a target app. This matches `codec-share` behavior in 5.3.

## 3. Exit condition (must be met before "done")

A fresh APK build (run `32949467172`-style or later CI) + device verification recipe passes:

```sh
# setup — write a test file into userland
mkdir -p "$PREFIX/project/test"
echo '{"test":1}' > "$PREFIX/project/test/sample.json"

# pass 1 — file exists, valid
codec-share-file "$PREFIX/project/test/sample.json"; echo "exit=$?"
# expect: share sheet opens; CLI prints OK; exit 0

# pass 2 — directory rejected at CLI (before bridge)
codec-share-file "$PREFIX/project/test"; echo "exit=$?"
# expect: usage / ERR: not a file; exit 2

# pass 3 — missing file rejected at CLI
codec-share-file "$PREFIX/project/test/nope.json"; echo "exit=$?"
# expect: usage / ERR: not found; exit 2

# pass 4 — text-shared still works (no regression)
codec-share "hello"; echo "exit=$?"
# expect: OK; exit 0
```

Evidence sections: `§5.1 Host`, `§5.2 CI`, `§5.3 Device` to match 5.3 format.

## 4. Implementation steps (only after owner confirms D1)

1. Read 4.7 bridge protocol (`CodeCApi` file exchange) and 5.3 script template (`codec-share`) to copy formatting.
2. Add `share.file` to bridge op dispatcher (same file as 5.3: `CodeCApi.kt` or equivalent).
3. Add `res/xml/file_paths.xml` allowing `files/userland/project/` and `tmp/codec-api/`.
4. Add CLI script `codec-share-file` in `codec-packages/scripts/` (or `assets/`) with parse/validate/OSC sequence emission.
5. Host-test the CLI parse/OSC emission (pure shell, no Android runtime needed).
6. CI build (`gradle-bootstrap`) + device verification with exact recipe in §3.
7. Write final evidence (`§5.1–5.3`) and commit with message naming Part 5.4.
8. **No PR / no merge without explicit owner command.** Push to session branch only.

## 5. Out of scope (deliberately deferred)

- Sensors / camera / intents (named "later" in roadmap).
- File-sharing a directory (only regular files).
- Sharing files outside the userland / project tree (FileProvider restriction).
- Changing `share.text` behavior (no regression allowed).
- Any rebuild / republish of bootstrap / package archive (client-only like 5.1–5.3).

## 6. Decision / open questions for owner

- **Confirm D1:** file-share via FileProvider, or switch to sensors / camera / intents?
- **Scope:** only `codec-share-file` (one CLI) or also expand `codec-share` to auto-detect file vs text?
- **FileProvider paths:** only `project/` or full userland tree?
- **Evidence depth:** full device recipe (§3) required, or host-only acceptable?
