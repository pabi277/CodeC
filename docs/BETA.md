# CodeC is in beta — what works, what still bites, and how to report it

> Phase 42.3. This is the page the release notes link to. It is written
> for a stranger who just installed the APK: the honest list of what
> bitler testers so far, what never worked, and exactly what to do when
> something hangs. A beta is not a dump — this file is the difference.

## What "beta" means here

- The app is feature-complete for the advertised job (edit C/C++ on the
  phone, compile+run in an APK-bundled compiler stack, sync to GitHub) and
  the core paths are tested in CI and by device rounds — but the owners'
  device matrix is still one phone family, and edge devices keep finding
  bugs.
- Your work is safe: projects live under
  `Android/data/…/files/CodeC/projects/`, they are the only thing the
  [42.3 backup rules](chat-phase42/PART_42_3_LAUNCH_SAFETY.md) carry, and
  the Backup section below makes a byte-identical ZIP of everything.
- Signature note (42.1): debug→release or a lost key forces a fresh
  install; see [UPLOAD_KEY_SETUP.md](UPLOAD_KEY_SETUP.md) — testers on
  early debug builds will need ONE clean install over to the signed
  channel once the release key lands, and never again after that.

## Known issues (the honest list — updated at every device round)

| # | Symptom | What it is | What to do |
|---|---|---|---|
| B-1 | Opening a **huge folder** (thousands of files, node_modules-sized) stalls the file tree | The tree scan is single-threaded on purpose; the storage document APIs are slow per file | Open a subfolder instead of the repo root; the tree rescans on demand — if it sits 30 s+, force-stop and reopen (your files are fine; the scan is read-only) |
| B-2 | **32-bit ARM older tablets**: install or boot fails | Older SoCs need a 32-bit (`armeabi-v7a`) APK; some third-party native libs in the stack are 64-bit-first | Use the release asset the updater picks (ABI-matched with universal fallback, 42.1); if your SoC is 32-bit-only, install the `-armv7` (or universal) APK from the release page |
| B-4 | **32-bit ARM devices**: the built-in C compiler is not available | The bundled offline TCC toolchain ships only for `arm64-v8a` / `x86_64` (Phase 33.3 typed null: `EmbeddedCompiler.tccBinary()` returns null on other ABIs; the app never pretends otherwise) | Install the C toolchain module from the modules screen, or use Termux; everything except offline C compilation works |
| B-3 | Long-running builds die when the screen turns off | The OS kills the process despite the foreground notification | Keep the notification visible (tap it; don't swipe it away); the foreground service + wake lock already hold — do-not-disturb is fine, battery-saver levels are the killer |
| B-4 | `git push` asks for the key again after an update | The credentials store is wiped if Android's backup restore only partially ran | Settings → GitHub Account → reconnect; PATs are never uploaded anywhere |
| B-5 | "Parse error / package appears invalid" on install | The download was interrupted or a partial APK got handed to the installer | The updater verifies SHA-256 **before** any install since 42.1; if it refuses, re-download from the release page in a browser — the checksums are in the release notes |
| B-6 | Cursor/highlight wrong on **very long lines** (>10 kB single line) | The editor's line-length guard (39.x) shows a readable substitute | Split the line; the file on disk is untouched (the guard is a display throttle, never an edit) |
| B-7 | Crash-loop after update: the app closes at the splash twice | Persisted settings from a mismatched build are the #1 cause proven across the device rounds | Third launch: the loop sentence + **[Try starting without my settings]** (42.3) boots the app without your settings — your projects are untouched; the counter resets on a clean start |

### Things that EARNED their way off this list

- Space-in-folder-name C projects (the classic) — fixed, regression-pinned
- `arm64` emulator cold-boots sometimes paused the device round — they were
  the sandbox, not the app
- "Crash reports are binary garbage from your app when they're not mine" —
  the crash log is plain text at `files/crash-log.txt`, read it before
  you paste it (42.3 lists exactly what one contains)

## If you hit something not on this list

1. **First rule from the device rounds:** screenshot + exact steps. One
   hang taught more than ten "it crashed" messages. Include the version
   (Settings → About — "1.3.17 (build NNNNN)") and Android level.
2. Report channels, in order of what gets fixed fastest:
   - In-app: Settings → Feedback & Support → Send feedback (since 41.2) —
     attaches the crash log if you tick it
   - After a crash: the 📧 **crash report overlay** — `COPY ALL` pastes
     into any chat; **[Send a report]** (42.3) opens the feedback form
     with the record attached
   - GitHub issue: <https://github.com/pabi277/CodeC/issues> — same
     template info
3. **Never paste a token.** GitHub PATs live in the app's credential
   store and never appear in the crash log — but screenshots of YOUR own
   terminal can contain one, and the internet never forgets.

## Your backup is one tap (42.3 §3)

Projects hub → ⋮ → **Export all projects (backup ZIP)**. That ZIP
re-imports (**Import projects backup**) into a clean install and comes
out byte-identical — the round-trip that has a host test. Keep one ZIP
per someone-else's-custody (Drive, a laptop, a mem stick), not just on
the same phone.

## What a tester should compare, every time

1. Cold install → a new empty C project compiles+runs on the bundled
   stack (the "Hello" in the templates)
2. Open a project you care about from git
3. The backup ZIP round-trip above on a spare folder
4. Settings → About reads the version the release notes announce

Anything else that misbehaves — see the list above, and if it's not
there, it IS a report. Thank you for testing.
