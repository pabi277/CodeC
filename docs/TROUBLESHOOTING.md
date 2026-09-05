# CodeC — "Compiler blocked / Permission denied" & the Termux way

> **Bug-report mode (2026-09-01):** all phases are complete. Report a bug with
> the exact symptom and device output; the agent listens carefully, finds the
> underlying code problem, and solves it — see [`rule.md`](../rule.md).

> In-app **Term** tab (`cc` / `./a.out` / scanf): see
> [docs/chat-phase1/README.md](chat-phase1/README.md) for the Phase 1 problem list
> and what a new chat must not regress.

> Applies to the error:
> `error: This device is blocking execution of the downloaded compiler … Compilation failed.`

Short version: on **Android 10+** the OS refuses to run downloaded binaries that an app
keeps in its own storage **if the app targets API 29 or higher** (the "W^X" security rule).
The old CodeC builds targeted API 34, so Android blocked the downloaded Clang on *any*
Android 10+ phone — not only emulators.

**The current CodeC builds don't need any download at all.** They ship a complete C
compiler (TCC, statically linked against musl) inside the APK — the same approach as
offline IDE apps like **Coding C** and **C4droid**. You install the app, write C, tap RUN:
it compiles offline and instantly, no Termux, no module download, no network. The
downloaded Clang module and Termux are only optional fallbacks for advanced code.

---

## 0. Built-in compiler (TCC) — the "Coding C" way

- Works out of the box on **arm64-v8a** (all modern phones) and **x86_64** (emulators,
  cloud phones, Chromebooks).
- The compiler binary ships in the APK's native library directory — the one place
  Android always allows `exec()`, at any targetSdk — and the musl headers/libs are
  extracted to app storage on first use.
- Compiled programs are **fully static** (no libc dependency), so they run on any
  Android version.
- TCC covers ANSI C and most of C99 (a C99-focused subset of C11). If you need stricter
  C11/C17, **C++**, or more warnings, install the full LLVM toolchain — **Packages →
  Clang / LLVM**, or `pkg install clang` in the terminal. Since Phase 21 (2026-09-03)
  RUN ▶ offers that install automatically the first time you run a `.cpp` file, and a
  plain `.c` file keeps using the built-in TCC with no download at all.
  *(Historical note: older builds also had a downloadable Clang module — needs
  the module download) or **→ Termux**.

How the bundles are built: `scripts/build-tcc.sh` (tinycc "mob" branch at the same commit
Termux ships, cross-compiled with musl-cross toolchains). The script is CI-ready.

---

## 1. Why this happens (the real technical reason)

Android 10 introduced this documented behavior change:
["Removed execute permission for app home directory"](https://developer.android.com/about/versions/10/behavior-changes-10#execute-permission):

> Untrusted apps that target Android 10 cannot invoke exec() on files within the app's
> home directory. This execution of files from the writable app home directory is a W^X
> violation.

In practice the kernel/SELinux answers `execve()` with **EACCES (Permission denied)** for
binaries inside `/data/user/0/<app>/files/...` when the app's targetSdk ≥ 29. The error
looks like this in logcat:

```
avc: denied { execute_no_trans } for path="/data/user/0/<pkg>/files/..." tclass=file
```

This is **not** a corrupted download and **not** fixable by re-extracting the module —
reinstalling the module changes file permissions, not the OS policy.

There are two more, rarer causes with the same symptom:

- **`noexec` app storage**: some emulators, cloud phones and enterprise-managed devices
  mount `/data` (or the app data volume) with the `noexec` flag. Nothing can execute
  there — not CodeC's compiler, not Termux's. Use a real phone.
- **CPU mismatch** produces `Exec format error` (a different message): the bundled Clang
  is ARM64-only, so x86/x86_64 emulators can't run it.

### How to check your device (for bug reports)

Open **Settings → Developer Options → View App Logs** (tap "App Version" 7× in Settings →
About to unlock it on debug builds) and look for the `Device:` line logged before every
compile, e.g.:

```
Device: Android 13 (API 33), ABI: arm64-v8a, app storage mount: /dev/block/sda3 on /data/user/0/com.codeci.ide/files [rw,nosuid,nodev,relatime]
```

- ABI contains `x86` → the bundled ARM64 Clang can never run → use Termux engine.
- App storage mount flags contain `noexec` → nothing local can run → real phone needed.
- ABI is arm64 and mount is `exec` → W^X policy is the blocker → update/reinstall CodeC,
  or use the Termux engine.

---

## 2. Fix A — Update CodeC (targetSdk 28 build)

1. Open **Settings → Install APK from GitHub** (or grab the APK from the repo's
   **Actions → Build APK → Artifacts** / **Releases**).
2. Install the update.
3. **Important:** if the error persists after updating, **uninstall CodeC and install it
   again**. Android decides whether the W^X rule applies at *install* time; an in-place
   update may keep the old restricted sandbox.
4. Open **Modules → Uninstall → Download** the Clang module once more and tap RUN.

On a stock Android 10–15 phone this makes the bundled compiler work exactly like Termux's.

## 3. Fix B — Termux engine inside CodeC (recommended fallback)

> **⚠️ Changed in Phase 21 (2026-09-03):** the **Settings → Compiler Engine**
> dropdown no longer exists. TCC is always the default C compiler and CodeC
> falls back internally (built-in → downloaded Clang → Termux) without asking.
> Steps 1–3 below still apply if you want the Termux bridge available; step 4
> is obsolete — there is nothing to select.

The Termux bridge setup:

1. Install **Termux 0.109+**:
   - F-Droid: https://f-droid.org/packages/com.termux/  (recommended)
   - GitHub: https://github.com/termux/termux-app/releases
   - ⚠️ The Play Store build is outdated and does not support this integration.
2. Open Termux once (it finishes first-time setup), then run:

   ```bash
   echo "allow-external-apps=true" >> ~/.termux/termux.properties
   termux-reload-settings
   pkg update && pkg install clang
   ```

3. Grant CodeC the permission **"Run commands in Termux environment"**:
   Android Settings → Apps → CodeC IDE → Permissions → Additional permissions.
4. In CodeC: **Settings → Termux Engine** → tap **CHECK BRIDGE** — it should say
   "Ready ✓". CodeC uses Termux's Clang automatically when its own engines are
   blocked (this is also what makes x86_64 emulators work).

How it works: CodeC sends the source code to Termux through Termux's documented
`RUN_COMMAND` intent, Termux compiles it with its own Clang inside its own storage (which
Android allows because Termux targets API 28), and streams the output and the program
result back into the editor's terminal. No root needed.

## 4. Fix C — Use Termux directly (the classic "Termux way")

Termux is a full Linux environment on Android — this is the most reliable way to write and
run C on a phone, and it works on every real device regardless of CodeC.

### Install Termux

Use **F-Droid** or **GitHub Releases** (not the Play Store version):

- https://f-droid.org/packages/com.termux/
- https://github.com/termux/termux-app/releases

### Install the C toolchain

```bash
pkg update
pkg install clang
```

This installs Clang (the compiler), lld (the linker) and the standard headers.

### Your first program

```bash
nano hello.c
```

Paste:

```c
#include <stdio.h>

int main(void) {
    printf("Hello from Termux!\n");
    return 0;
}
```

Save with `Ctrl+O`, Enter, then `Ctrl+X`. Compile and run:

```bash
clang hello.c -o hello
./hello
```

### Everyday workflow

```bash
clang program.c -o program      # compile
./program                       # run
clang -Wall -Wextra -std=c11 program.c -o program   # strict checks
```

- **Edit files**: `nano` (simple) or `vim` (powerful).
- **List/copy/rename**: `ls`, `cp`, `mv`, `rm`.
- **Install more tools**: `pkg search gcc`, `pkg install git make gdb` — `gdb` is a
  debugger, `make` automates builds.
- **Get files off the phone**: `termux-setup-storage` links `~/storage` to your shared
  storage; then `cp hello.c ~/storage/downloads/`.
- **64-bit ARM check**: `uname -m` should print `aarch64` (arm64) or `x86_64` on
  emulators. Termux has packages for both.

> Note: emulators and "cloud phones" that mount storage `noexec` break Termux the same
> way they break CodeC — no local binary can run there at all. Use a real phone.

## 5. In-app terminal (`cc` / `./a.out`)

These are **not** the downloaded-Clang W^X error. Full table:
[chat-phase1/PROBLEMS.md](chat-phase1/PROBLEMS.md).

| You see | Do this |
|---|---|
| `./a.out: Permission denied` | Need **1.3.11+**. Uninstall + reinstall. CWD must be app-private `filesDir`, not `/storage/emulated/0`. |
| `libtcc1.a: unrecognized file type` | Need **1.3.12+**. Term → refresh, compile again. |
| Prompt appears *after* you type at `scanf` | Need **1.3.13+**. Recompile. Do not add `fflush` to user C. |
| `input.out: inaccessible or not found` | Run `./input.out`, not `input.out`. |
| RUN: `exit 124` / “infinite loop” on `scanf` | Not a loop. RUN has no stdin. Use **Term**. |
| `[process exited with 137]` on refresh | Fixed in 1.3.11. |
| Keyboard delayed ~a minute | Need **1.3.11+**. Tap the terminal once. |

## 6. Frequently asked questions

**Q: I reinstalled the module in Modules — why does it still fail?**
A: Reinstalling the module only fixes file permissions. The W^X block is an OS policy on
the app's whole sandbox (because the app targeted API 29+). Update + reinstall CodeC
(Fix A) or use Termux (Fix B/C).

**Q: My phone is a real ARM64 phone — why is the error still shown?**
A: Because the old APK targeted API 34, Android 10+ blocks it on real phones too. Update
to the targetSdk-28 build (Fix A). If the mount is genuinely `noexec` (some cloud phones),
no local compiler works — that is what the "Device:" log line reveals.

**Q: Can I run the compiled program if I only have the Termux engine?**
A: Yes — CodeC executes it inside Termux too and shows the output in the terminal, with
the same 10-second execution limit.

**Q: Does CodeC still work without Termux?**
A: Yes, on any device where the bundled Clang runs (real ARM64 phone with the updated
build). Termux is only used when you choose it or when the bundled compiler is blocked.

**Q: Why does Termux not have this problem?**
A: Termux targets API 28 on purpose (see its [FAQ](https://github.com/termux/termux-packages/wiki/FAQ)),
which keeps `exec()` of downloaded binaries legal. CodeC's new builds follow the same
approach.

## 7. Related resources

- Android 10 "Removed execute permission for app home directory":
  https://developer.android.com/about/versions/10/behavior-changes-10#execute-permission
- Termux wiki (RUN_COMMAND API used by the Termux engine):
  https://github.com/termux/termux-app/wiki/RUN_COMMAND-Intent
- Termux FAQ: https://github.com/termux/termux-packages/wiki/FAQ

---

## 8. Web Preview says "File not found: <name>" (2026-09-01, fixed `d49ac47`)

**Symptom:** an HTML file opened in the editor (typically imported/copied from
storage) shows **`File not found: <name>`** in the Web Preview when launched
with the 👁/launch action or the RUN ▶ button.

**Cause (fixed):** the preview resolved the file inside the project named by
the **editor's Nav route argument**, which becomes stale when you switch the
editor's working folder in-place — via the folder button → *Open folder*
picker, or *Save to project…*. The imported HTML lived in the new project
folder (e.g. `CodeC/projects/<imported>/index.html`) but the preview looked in
the projects root (or the previously open project) and reported it missing.

**Fix:** preview navigation now carries the real project — the drawer's launch
action passes the file entry's own project, RUN ▶ passes the editor's current
project, and live-server/auto-web previews carry the project they were started
for. Installed in the build from run `33471103959`.

**If it still happens:** tell us *where the file actually lives* (`ls` in the
terminal from the folder it was created in) — the preview only serves files
inside app-managed projects under `files/CodeC/projects/<project>/`; a file
created in the terminal's `$HOME` (or with a stray `cp` into the projects
root) is not inside a project folder and must be moved/saved into one first.

---

## 9. How to run the Phase 25.1 editor bench (owner runbook, 2026-09-04)

**What this is:** the Phase 25.1 spike measures the three editor cores
(C-now = today's editor, C-sora = sora-editor, C-compose2 = the visible-window
sketch) on YOUR phone with identical scripted input. The decision on which
core CodeC adopts is made from YOUR numbers — nothing is decided by feel.

**Steps:**

1. Open this repo on github.com → **Actions** → the latest green **Build APK**
   run → **Artifacts** → download **`CodeC-Bench`**, unzip, and install the
   APK on the phone (app name: **CodeC Bench**; it installs alongside CodeC
   and cannot touch it).
2. Battery above 30 %, phone cool, nothing else in the foreground.
3. Home screen → leave **Input mode: keys** (only flip to `direct` if a
   candidate reports `typed=0` for a typing scenario — that means it ignored
   the simulated keyboard).
4. Open a candidate card (e.g. **C-now · bench.c**), wait for the corpus to
   load and "ready — run a scenario", then tap the four scenario buttons one
   at a time. Each runs 3 repetitions with cool-downs. Don't touch the screen
   while a scenario runs.
   The first screen you open also records the **cold-open** time automatically.
5. Repeat for the other candidates (bench.c first — it's the big-file case;
   bench.html is the span-density worst case).
6. Back on Home → **Copy all** → paste the markdown into the chat. That's the
   whole device round.

**If something misbehaves:** the status line under the candidate name shows
what the harness is doing ("rep 2/3…", "scenario failed: …"). The raw numbers
also live at `Android/data/com.codeci.bench/files/bench-results.md`
(exported by Copy/Share regardless).

## 10. How to run the Phase 28.1 CodeC Keys spike (owner runbook, 2026-09-05)

**What this is:** the 28.1 gate answers ONE question — can CodeC-drawn keys
feed the editor at typing speed with the system IME never opening, and does it
FEEL instant? The 25.1 bench grew two spike cores for it: **K1-codecgrid**
(today's Compose document path) and **K2-codecgrid** (the shipping sora core).
Nothing here is in the IDE; the decision lands in
`docs/EDITOR_MOBILE_RESEARCH.md` §9.1.

**Steps (full detail in `docs/chat-phase28/PART_28_1_SPIKE.md` §5):**

1. Actions → latest green **Build APK** on the session branch → Artifacts →
   **`CodeC-Bench`** → install (updates the 25.1 bench in place).
2. Write your three answers + verdict in the notes box on Home FIRST (they
   ride the export): Q1 Bluetooth keyboard while suppressed? Q2 stdin-route
   kept working? Q3 TalkBack reads editor + caps?
3. Per K-screen: run the four scripted scenarios, do the "IME: allowed"
   control check (the live `ime=` number must go > 0 — that proves the
   flicker detector), then the 5-min human session on the grid.
4. `tap=…/64 drop=0 dup=0 swap=no` + `p95 ≤ 16.7 ms` + `ime max=0px` on BOTH
   cores + "feels instant" ⇒ **GO** (28.2 starts on your word). Any red ⇒
   record the no-go; the strip (L0) stays the product answer.
5. Home → **Copy all** → paste into the chat.

## 11. How to run the Phase 28.2 CodeC Keys device round (owner runbook, 2026-09-05)

> **Merged 2026-09-05** (owner command after rounds 1–3). This card stays as the standing regression pass — if a check ever fails, file it like any other bug.

**What this is:** the first time the keyboard ships INSIDE the IDE — the
28.1 spike proved the latency law; 28.2 proves the layout engine under real
use. **Since owner round 2 (2026-09-05) CodeC Keys is DEFAULT ON** ("make the
keyboard default, user can off it") — with it OFF, everything else about the
editor must be exactly as 22.x–27.x shipped.

**Steps:**

1. Actions → latest green **Build APK** → Artifacts → **CodeC-IDE** (the
   debug APK is `:app`). Install over the current build.
2. Settings → **CodeC Keys** (ships ON; the preview keyboard renders in
   Settings itself — taps there are inert by design; haptics + row-height
   slider under the same toggle). Back to the editor: the grid docks where
   the soft keyboard was; the system IME must NOT open at all. **Round-2
   checks:** every rapid arrow tap moves the caret exactly once (live-buffer
   commit — old bug: same-frame taps collapsed); hold-repeat arrows glide;
   **hold SPACE until "⇄ caret" → slide → release: the caret followed and NO
   space was typed** (hold-without-slide still inserts one); arrow FLICKS
   jump Home/End/PgUp/PgDn.
3. Run the five exit checks (`PART_28_2_LAYOUT_ENGINE.md` §3):
   - **200-char C program** with symbols, all on the grid: flick-up rows for
     digits/brackets, `SYM` for the rest; no IME opens once.
   - **Gestures:** flick-up on `p` = `0`; flick-down on `()` inserts `)` only;
     long-press TAB still indents raw while a ghost is up (then TAB ▸ accepts).
     Round-1 changes to check: caps show their release in the corner (`q¹`,
     `;:`); HOLDING `;` swaps the big label to `:` before you release; arrow
     FLICKS = Home/End/PgUp/PgDn (hold still repeats); no popup bubbles
     anymore — if any draw crash persists, send the log's FIRST lines
     (exception class + message, above these frames).
   - **Layers:** `SYM` ⇄ `ABC` one tap each. Round 3 law: EVERY symbol-layer
     key is a single character — check `%`, `*`, `(`, `)` tap exactly one
     char; `->` = `-` then `>`; the letters layer no longer changes with the
     file's language (no more `->` tail on the arrow row).
   - **Deletion:** `⌫` tap deletes, hold repeats (~150 ms/40 ms), flick-up
     deletes a word; no double-deletes.
   - **OFF switch:** flip it OFF in Settings → the system IME returns exactly
     as before (22.x intact: suggestions, autocorrect, run-stdin all normal).
4. The waived four (ride this round): the OFF→ON flip above IS the
   self-check (IME visible one way, absent the other); a **real Bluetooth
   keyboard** must still type while the grid is up; caret/selection handles
   still work; and the feel line — *does it feel instant?*
5. Paste notes to the same table shape as 28.1's round (§6 of the spike doc).

**Known costs recorded up front (not bugs):** programmatic edits = no IME
composing span (28.1 S2 verdict), no caret/selection announcements by TalkBack
until 28.4, no swipe-space between letters (spacedBy 4.dp only), and popup
bubbles overflow their cap without clipping — on purpose (strip clip bug
class — superseded after round 1: bubbles were removed entirely, previews live
IN the cap (`CodecKeyboard`'s round-1 note).
