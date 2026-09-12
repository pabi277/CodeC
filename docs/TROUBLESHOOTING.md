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

## 12. How to run the Phase 29 VS Code colour device round (owner runbook, 2026-09-05) — ✅ PASSED 2026-09-06

> Phase 29 = TextMate (VS Code grammars + themes) as the editor's analyzer.
> This card is the **exit gate**: all three parts (29.1 core / 29.2 language
> parity / 29.3 regex retirement) ship in ONE build.

**What you should see change:** a `.c` / `.py` / `.html` / `.ts` file now
colours like VS Code Dark+ (the new default editor theme) instead of the old
approximation; Go / Rust / PHP / Ruby / Lua / XML / YAML files are coloured
for the first time (they were plain before); CSS no longer looks like HTML.

**Steps:**

1. Actions → latest green **Build APK** on the session branch → Artifacts →
   **CodeC-IDE** → install.
   *(APK size: measured at **+2.10 MiB** vs `main` (22.0 → 24.2 MB) —
   over the planned +1.5 MiB budget; the weight is the TextMate engine
   chain (joni/jcodings/gson/tm4e), not the grammars (~250 KB). This
   deviation is flagged for the owner's verdict — PART_29_1 §4.5 —
   please confirm accept/direct-a-strip when reporting the round.)*
2. **Dark+ look:** open a C file with keywords, strings, comments,
   preprocessor lines, numbers. Compare with VS Code Dark+ on desktop —
   keywords blue `#569CD6`, strings orange `#CE9178`, comments green
   `#6A9955`, numbers `#B5CEA8`, functions `#DCDCAA`, background `#1E1E1E`,
   caret `#AEAFAD`, current line `#282826`, selection `#264F78`.
3. **Typing still smooth:** type ~60 keys in a long `.c` file (bench.c if
   you still have it) — no stuck keys, no lag, same feel as 28.2 (the
   analyzer is INCREMENTAL now; it should feel no worse, ideally better on
   very long files).
4. **Every language:** open (or create) one file each of `.py`, `.html`,
   `.css`, `.ts`, `.tsx`, `.go`, `.rs`, `.php`, `.rb`, `.lua`, `.xml`,
   `.yaml`, `.md`, `.sh`, `.json` — each has its own distinct VS Code-like
   colour (CSS clearly different from HTML; `.lua`/`.php`/`.rb` no longer
   plain white). A plain `.txt` file must stay UNcoloured.
5. **Theme switching:** Settings → Editor Theme → switch **Monokai**,
   **Dracula**, **GitHub Dark**, back to **VS Code Dark+** — the editor
   recolors immediately, no restart, no wrong-then-right flash of token
   colors after the switch settles.
6. **Nothing else regressed:** completions/ghost/strip still appear while
   typing (engine untouched), CodeC Keys still types (its edits go through
   the same sora buffer), find/replace + selection highlighting still work.
7. Optional but useful: the FIRST open of a `.php` or `.rb` file after a
   fresh app start may take a beat longer than usual once (their grammar
   sets are ~1 MB and load lazily — PHP/Ruby warm-up was deliberately
   deferred to keep app start light). Everything after that first open is
   instant.

**Report:** PASS/FAIL per numbered item (screenshots of the C file vs
desktop VS Code are perfect evidence for item 2). Any wrong-looking color:
name the file type + what you expected vs saw.

**If the app CRASHES during this round (2026-09-06 update):** relaunch it —
the crash-report overlay appears. **COPY ALL and paste it in chat.** The
report now starts at the record's HEADER (the `java.…Exception` line +
the first ~80 frames) — earlier builds showed only a byte-tail of the
whole log file, which cut off exactly the lines that diagnose the crash;
fixed in `591be79`. Optional before reproducing: tap **CLEAR** so the
overlay shows only the new crash. Also note the file type of the file
you opened when it crashed.

**2026-09-06 update — the open-file crash is FIXED (two layers,
`288b760` + `db56824`):** the full crash record named it:
`IllegalStateException: LayoutNode should be attached to an owner` —
the editor Column (under `imePadding()`, inside the nav transition)
measuring a detached child; a Compose 1.7.1-era bug family, fixed by
bumping the Compose BOM to 2024.12.01 (1.7.6). The CI nav-transition
repro test then caught a second, deeper bug: the VM→sora full replay's
incremental delete-all dispatched `afterDelete` into a layout whose
per-line width lists sora rebuilds ASYNCHRONOUSLY after any
`createLayout()` (font-size/language-config effects) →
`BlockIntList.removeRange` on an empty list. Fixed by making the
replay an atomic `setText`. If ANY crash recurs in this round, the
§-above COPY ALL flow still applies — the report now always starts at
the exception line.

---

## 13. How to run the Phase 30 snippets + Emmet device round (owner runbook, 2026-09-06) — ✅ PASSED 2026-09-07

> Phase 30 = **what is offered** (27 fixed *how you accept*). All three parts
> ship in ONE build: 30.1 MIT snippet packs as assets, 30.2 clean-room Emmet
> for HTML/CSS/JSX, 30.3 the engine cap 8 → 50. This card is the exit gate —
> the three parts' exit conditions are merged into one pass below.
>
> **Ran 2026-09-07 on build `ca8ec57` (run `34041185149`): the owner reported
> TWO failures — accepting a suggestion kept the typed prefix (`#in` →
> `##include <stdio.h>`) and CodeC Keys closed no brackets. No other item was
> reported failing.** Both were fixed in `d63a645` and re-tested through §14
> below: **PASSED (owner: "Yes working")**, and Phase 30 merged via PR #55.
> This card stays as the standing regression pass for the offer world.
>
> **Every chip list below was MEASURED on a host JVM driving the real engine
> over the real pack assets**, so a FAIL on the phone is a real signal and not
> a guess about what the packs contain. Writing this card also caught five
> strings the as-committed build got wrong — Markdown `head` returned **zero**
> items, Python `pr` lost `print(...)`, shell `if ` dumped 16 snippets with **no
> if-block** among them, Python `def ` offered `deft`/`defs`/`defst` but not
> `def`, and `nav>ul>li*2>a[href=#]{Link $}` was unreachable when *typed* (the
> walk-back stopped at the space inside the text node). All five are fixed in
> the build this card points at; see PART_30_1 §3.5 and PART_30_2 §3.5.

**What you should see change:** typing `for` in C now offers **5** snippets
instead of one; `i` in Python offers **13** candidates and the chip row scrolls;
`ul>li*3` on a fresh line of an HTML file expands into a real nested list; `m10`
inside a CSS rule expands into `margin: 10px;`. Snippet **labels are now mostly
the short prefixes you type** (`for`, `#inc`, `main`, `bg`) — the old tables
showed whole body lines (`for (int i = 0; i < n; i++) {`); those long labels
still appear, but only as the LAST chip of a row, where the pack has nothing
shorter to offer.

**Steps** (each is one line — type exactly what is in backticks; "chips" = the
row above the keyboard, in order):

1. Actions → **Build APK** run **`34041185149`** (tip `ca8ec57`, GREEN 4m51s —
   the build that carries the §3.5 amendment; any LATER green run on the
   session branch is equally good) → Artifacts → **CodeC-IDE** → install, then
   Settings → About and check the version line carries that run's number (29.4
   — a stale APK is the classic false FAIL, and this card's chip lists only
   match the amended build).
   *(Artifact delta vs `main`: **+116 886 B (+0.11 MiB / +0.117 MB)** — 29
   snippet JSONs, 277 KB raw ≈ 54 KB deflated, plus the resolver/Emmet code;
   Phase 29's TextMate assets and engine are unchanged.)*
2. **C snippets (30.1):** open or create `main.c`, type `for` → **5 chips**
   `for` `fora` `forc` `forg` `for (int i = 0; i < n; i++) {`. Tap the first →
   a real loop appears and the caret sits at its first hole. Then type `#inc` →
   **3** (`#inc` `#incl` `#include <stdio.h>`) → tap → `#include <stdio.h>`.
   Then `main` → **3** (`main` `mainn` `int main(void) {`).
3. **Python snippets (30.1):** create `a.py`, type `for` → **3** (`for` `forr`
   `for item in iterable:`); then type `def` followed by a SPACE → **5** (`def`
   `deft` `defs` `defst` `def function():`) — the FIRST chip must be `def`
   (trigger words still work with an empty prefix).
4. **The tail (30.1 §3.5 — four strings the pack alone broke):** in `notes.md`
   type `head` → **1** chip `# Heading` (pack-only build: nothing at all); in
   `a.py` type `pr` → **2** (`property` `print(...)`); in `run.sh` type `if`
   followed by a SPACE → **exactly 2** chips, both if-blocks (`if`,
   `if [ cond ]; then ... fi`) and NOT a 16-item dump of `echo`/`read`/`else`;
   in `site.css` type `@med` → **2** (`med` = the pack's
   `@media screen and (max-width: 300px) {…}`, then CodeC's
   `@media (max-width: 600px)`).
5. **HTML regression (30.1 exit 2):** create `index.html`, type `doc` → **2**
   (`doctype` **and** `<!DOCTYPE html> skeleton`); tap the skeleton → a full
   HTML5 document with the caret inside `<body>`.
6. **Emmet markup (30.2 exit 1):** inside `<body>` of `index.html`, press Enter
   so the caret is on a FRESH line, type `ul>li*3` → **ONE** chip labelled
   `ul>li*3` → tap → a three-item nested list, caret inside the first `<li>`,
   and the typed `ul>li*3` is GONE (replaced, not appended).
7. **Emmet, more shapes** (each on a fresh line, one chip unless noted):
   `div.card>p{Hello}` → `<div class="card">` + `<p>Hello</p>`;
   `table>tr*2>td` → **2** chips (the expansion, then the pack's `td`);
   `nav>ul>li*2>a[href=#]{Link $}` → the two links must read `Link 1` and
   `Link 2`; `a{Link $}` → `<a>Link 1</a>`; `p{Hello World}` → a space inside
   `{text}` is part of the abbreviation (30.2 §3.5); and `!` alone → the HTML5
   skeleton (30.2 exit 2).
8. **Emmet CSS (30.2):** in `site.css`, inside a rule (`div {` then a new line),
   type `m10` → **1** chip `m10` → tap → `margin: 10px;` with the caret right
   after the colon. Also `d:f` → `display: flex;` (**28** candidates: the
   expansion first, then the pack's `f…` snippets, because the prefix left
   after `d:` is `f`), `p10-20` → `padding: 10px 20px;`, `m10+p20` → two
   declarations, `mt-5` → `margin-top: -5px;`, `m10!` →
   `margin: 10px !important;`.
9. **The C gate (30.2 exit 3):** in `main.c` type `ul>li` and then `!` on a
   fresh line → **no** expansion chip (ordinary snippets/keywords only). Repeat
   in a plain `.js` file: `ul>li*3` must NOT fire there either — only `.jsx`
   and `.tsx` files do (rename the same buffer to `app.jsx` and it fires).
10. **Capacity (30.3 exit 1):** in `a.py` type `i` → **13** candidates, **8**
    chips, the row scrolls sideways; tap **⌄ more** → the panel lists **13**
    rows (MORE THAN 8). Same in `main.c`: `i` → **13**.
11. **The laws (30.3 exit 2 + Phase 27):** Enter still inserts a newline and
    never accepts a suggestion; **TAB ▸** accepts the ghost in full and parks
    the caret at the snippet's first hole; **→▸** accepts one word; ESC
    rejects; swiping down on the chip row dismisses it for that identifier.
12. **Master switch (30.1 exit 3):** Settings → Editor Settings → completion
    master OFF → type `for` in `main.c` → no chips, no ghost, ⌄ does nothing.
    Switch it back ON → everything returns.
13. **Feel:** type ~60 keys in a long file (`bench.c` if you still have it) —
    completions must feel exactly as before (the engine still runs off the main
    thread behind the 120/240 ms debounce; the pack is parsed once).
14. **Attribution:** Settings → About → the licence list mentions
    "snippet packs — MIT (rafamadriz/friendly-snippets)".

**By design, NOT bugs:** one accept parks the caret at the snippet's first hole
and there is **no Tab-stop walking** (on a phone keyboard TAB is the
indent/accept cap — Phase 27 invariant 2); a snippet inserted from sora's ⌄
panel parks the caret at the END of the insert (its item type has no
caret field) while the strip and ghost honour the hole; `.json` and `.txt`
files still get no completions at all; an abbreviation glued to a tag
(`<div>ul>li*3`, no space) fires **nothing** — the walk-back would read it as
inside the tag, so start abbreviations on a fresh line; a space ends an
abbreviation EXCEPT inside `{text}` (`ul> li*2` offers only `li*2`).

**Short version (~5 min) if you only have one coffee:** steps 1, 2 (`for` in C
→ 5 chips), 4 (all four — this is the amendment that exists because of this
round), 6 (`ul>li*3`), 7's `nav>…{Link $}`, 8's `m10`, 10 (`i` → 13 rows behind
⌄), 11 (Enter = newline), 12 (master OFF = nothing).

**Report:** PASS/FAIL per numbered item, and for any FAIL the exact text you
typed, the file name, and what appeared instead (a photo of the chip row is
perfect evidence). If the app crashes, the §12 COPY ALL flow still applies —
the report starts at the exception line; paste it in chat.

## 14. Phase 30 device round 1 — the two fixes (owner runbook, 2026-09-07) — ✅ PASSED 2026-09-07

> **PASSED 2026-09-07 (owner: "Yes working") on the `c2b392e` build — CI run
> `34078739941` GREEN, artifact `CodeC-IDE` 24 375 211 B (+117 409 B / +0.11 MiB
> vs `main`).** Phase 30 is closed and merged to `main` via PR #55 on the
> owner's command. This card stays as the standing regression pass for the two
> fixes — if a check ever fails again, file it like any other bug.
>
> You reported two bugs on the `ca8ec57` build. Both were fixed in `d63a645`
> and both were reproduced + re-measured on a host JVM against the real
> production files first. This card is ONLY those two — §13 is the full
> Phase 30 pass.
>
> **Which build:** Actions → **Build APK** → run **`34078739941`** (tip
> `c2b392e`, GREEN) → Artifacts → **CodeC-IDE**. (`34077539890` is RED on a
> sora/Robolectric flake unrelated to the fixes, and a red run uploads NO APK
> artifact.) Settings → About must show the new version (it carries the CI run
> number — a stale APK is how round-1 reports went sideways in Phase 29).

**Fix 1 — accepting a suggestion now deletes what you typed (all surfaces).**

What was wrong: the accept span was the *identifier* run at the caret, and `#`
is not an identifier character — so typing `#in` and tapping
`#include <stdio.h>` replaced only `in` and left the `#` you had typed, giving
`##include <stdio.h>`. Fine while every snippet began with the word you typed;
the packs and Emmet ship items that don't.

1. `main.c`, type `#in` → tap the **`#include <stdio.h>`** chip → the line reads
   **`#include <stdio.h>`** — exactly one `#`, nothing left of `in`.
2. Same, but accept from the **⌄ more** panel (CodeC Analyzer list) → same result.
3. `int mai` → tap **`main`** → **`int main() {`** … (the whole `int mai` you
   typed is gone, not just `mai`).
4. `site.css`, type `@med` → tap **`@media`** → one `@`, no `@med@media …`.
5. `index.html`, type `doc` → tap **`doctype`** → one `<!DOCTYPE html>`, no
   `doc<!DOCTYPE html>`.
6. **Ghost law unchanged:** type `doc` in `index.html` and the ghost still shows
   the rest of `<!DOCTYPE html>`; **TAB ▸** accepts it in full; **→▸** takes one
   word; **ESC** rejects; **Enter inserts a newline and never accepts** (27.x).
7. Regression half: in `main.c` type `for` → tap a chip → the snippet replaces
   `for` (not more, not less); type `ma` where the document also contains `main`
   elsewhere → accepting an identifier must not eat other text.

**Fix 2 — CodeC Keys now auto-closes brackets, and `{` + Enter splits the pair.**

What was wrong: auto-pairing was suppressed for anything that wasn't the system
IME's text field — a leftover parameter named `isStrip` from when the only
non-IME surface was the key strip. CodeC Keys is a full typing surface (28.2),
so its brackets got strip behaviour: one character, no closer.

8. With **CodeC Keys ON** (Settings → CodeC Keys), in `main.c` type each of
   `(`, `[`, `{`, `"`, `'` → each inserts the **matching closer** and the caret
   lands **inside** the pair: `()`, `[]`, `{}`, `""`, `''`.
9. Type `int main()` then Enter, then `{` → you get

   ```c
   int main()
   {
   }
   ```

   with the caret between the braces; press **Enter** again →

   ```c
   int main()
   {
       |
   }
   ```

   the caret **indented on its own line** and `}` on the line after — this is
   the exact shape you asked for. Keep typing `return 0;`: it stays at that
   indent.
10. The pair is not doubled: with the caret between an existing `{` and `}`,
    pressing Enter still splits it (old behaviour kept); typing `{` when the
    closer is already the next character does not add a second one.
11. Python: `def f():` then Enter → the next line is indented (the `:` rule is
    untouched).
12. **The strip stays single-character:** tap a `{` or `(` chip on the key strip
    above the keyboard → it inserts **one** character (a strip tap that quietly
    adds a closer is a surprise; that half is deliberately unchanged).
13. **OFF switch:** Settings → CodeC Keys OFF → the system IME returns and
    pairing behaves exactly as it did before this round.

**By design, NOT bugs:** the ghost paints only what is byte-true after the
caret, so `<!doc` shows no ghost (case differs from `<!DOCTYPE`) while the chip
and the panel still accept it and still delete all five typed characters;
`<` pairs in markup/C-like files as it always did (SmartTyping's own language
gate decides), and a strip chip never auto-pairs (step 12).

**Report:** PASS/FAIL per numbered step; for any FAIL the exact keys you typed,
the file name, what appeared, and whether CodeC Keys was ON or OFF.

## 15. Phase 31.5/31.6 — LSP chips in the editor (owner runbook, 2026-09-08)

**Goal:** prove IntelliSense items appear in the **chip strip** (not only ⌄ more) after a language server is installed.

1. Install the C/C++ IntelliSense card: Packages → **C/C++ IntelliSense (clangd)** → install (`pkg install -y clang`). Confirm `clangd` exists (`which clangd` in Terminal).
2. Open or create a project file `main.c`. Type a small program that uses `stdio.h` (`#include <stdio.h>` then `int main() { fr`).
3. Wait ~1 s after the last key (the LSP merge is the **debounced** completion pass). The chip strip should offer `fread` / `free` / similar **members snippets cannot invent**.
4. Tap a chip → it inserts. Ghost (if visible) should match rank-0.
5. Settings → Completions **master OFF** → chips fall back to snippets only; no clangd process should stay (check Terminal `ps` if you want).
6. Master ON again → type; chips return after the debounce.

**PASS:** step 3 shows an LSP member in the chip strip. **FAIL:** chips stay snippet-only after 2 s, or the editor hangs on a keystroke.

**By design:** the first completion after install may take ~1.5 s (clangd index); later keystrokes stay snippet-instant and refresh chips after debounce. JSON files now have chips only after the vscode-json-language-server card is installed (the engine has no JSON snippets).

## 16. Phase 32 phone canvas — device round (owner runbook, 2026-09-09) — ✅ PASSED 2026-09-09

**Goal:** the editor regains screen while typing, and the hidden 5-tab bar comes back when you want it.

1. Open a C file in the editor. Tap into the code so the **soft IME** (or CodeC Keys, if Keys is default ON) appears.
   - The **5-tab bar must disappear** and those lines become editor. With CodeC Keys up, a thin **handle** ("Show tabs" + pill) sits at the very bottom.
2. **Tap the handle** (or swipe it up) — the 5-tab bar **returns** while the editor buffer is untouched (no lost text/undo).
3. **Leave the editor** (tap Terminal, then back to Editor) — the bar is **back** normally; when you focus the editor + keyboard again it hides again (reveal is not sticky across navigation).
4. **One meaning row:** with CodeC Keys ON, type a prefix that opens chips — at most **one** row of chips between the code and the letter keys; the Phase 27 utility-key strip must NOT also appear.
5. **Output peek + jump:** RUN a file with a deliberate error (e.g. `int main( {`). The output panel expands with the error. **Tap the `file:line` error** in the output — the caret lands on that line in the OPEN file (the user's file, never `source_<stamp>.c`).
6. With Keys OFF (Settings) confirm the L0 strip/IME behaviour is unchanged (the 22.x recipes still hold).

**PASS:** steps 1–6 all hold. **FAIL:** the bar stays during Keys/IME, the handle does not restore it, the buffer is lost, two chip/key rows stack, or a diagnostic tap opens/jumps to a temp `source_*.c` file.

**Result (2026-09-09, owner): "All test passed on device"** — steps 1–6 PASSED.

## 17. Phase 33 — "can't run a C file in my HTML project" (2026-09-09, fixed on `arena/01a083fc-codec`)

**Symptom (owner):** "I have a html project and where i have c files but i can't run the c file it's opening the index.html."

**Root cause:** `EditorViewModel.runActiveFile` returned early for any project typed `web` (`if (web) return`), so a C/Python file inside an HTML project could never run; and the RUN chooser's "run current file" arm still routed web projects through the old "preview the web entry" branch.

**Fix:** a pure `ProjectRunTarget.isRunnableSource(rel)` — a file with a run profile that is NOT the web preview (main.c/main.py/… run; index.html/style.css don't) — now gates both the editor's RUN dispatch (`runOpenFile`) and the ViewModel's web-project early-return, so RUN on a C file in an HTML project compiles/runs it in the panel. Superseded by §18: the chooser is now driven by the user-set default, not the project entry.

**How to verify (device):** in an HTML project, open a `main.c` (or `main.py`) file → tap RUN ▶ → it compiles and runs in the Output Panel (the open tab stays). Opening `index.html` and tapping RUN still previews it. (With a launch default set, RUN instead asks "Run default / Run open" — see §18.)

**CI:** `34309463999` ✅ GREEN first try (tip `9a11382`, 5m58s).

## 18. Phase 33 — RUN runs the open file by its type; a user-set default adds "default vs open" (2026-09-09, `arena/01a083fc-codec`)

**Owner's model:** a project that is a web showcase (`index.html` + css/js) whose real content is a folder of `.c` practice files (e.g. `Code-with-C`).

**Behaviour now:**
- RUN ▶ on a `.c` (or `.py`/`.js`) file compiles/runs it in the Output Panel **even inside a `web` project** — it never opens `index.html` for a source file.
- RUN ▶ on an `.html` file previews it.
- If you set a **default** (open a runnable file → ⋮ → **Set as launch default**), then RUN ▶ on a *different* file asks **"Run `<default>` / Run `<open>`"**. No default set → RUN just runs the open file. Clear it with ⋮ → **Clear launch default**.

**How to verify (device):**
1. In a web project with `index.html` and a `main.c`: open `main.c`, tap RUN ▶ → it compiles and runs (Output Panel), the open tab stays.
2. Set `main.c` as the launch default (⋮ → Set as launch default), open another `.c`, tap RUN ▶ → dialog offers "Run main.c / Run <other>".
3. Open `main.c` itself, tap RUN ▶ → runs directly, no dialog.
4. Open `index.html`, tap RUN ▶ → previews it (and 👁 preview still works).

## 19. Phase 33 — cloned/imported ("auto") projects: RUN on a C file opened index.html (2026-09-09, `arena/01a083fc-codec`)

**Owner bug:** "Still same problem index.html opening not the file i am in." The
`Code-with-C` repo is cloned/imported, and clone/import writes the project as
type `auto` (spec §2.3.2). RUN ▶ with a `.c` file open still previewed
`index.html`.

**Root cause (device-log + code):** for an `auto` project, `EditorViewModel.runFile`
calls `ProjectRunDetector.detect(root, activeFile)`. The detector only special-cased
`app.py` / `server.c` / `main.py` / `.html` as the active file; a plain
`C Programming/01_…_conversion.c` gave "no hint", so the root scan ran and hit
`index.html` first → `AutoRunPlan.Web("index.html")` → `webPreviewHandler` →
preview. The active file was silently shadowed by the root `index.html`.

**Fix:** `ProjectRunDetector.detect` now treats ANY other runnable source open
in an `auto` project (a registry run profile that is not the web preview —
C/C++/Python/JS/shell, via the pure `ProjectRunTarget.isRunnableSource`) as
`AutoRunPlan.Project(c|python)`, so it falls through to the normal registry
run on the open file. The no-active-file scan (Projects-hub card) is unchanged
and still reports the web family for a repo whose root has `index.html`.

**How to verify (device):** clone `Code-with-C`, open `C Programming/01_…_conversion.c`,
tap RUN ▶ → it compiles and runs in the Output Panel (tab stays on the C file);
`index.html` still previews when you open it and tap RUN ▶.

**CI:** `34317268507` ✅ GREEN first try (tip `270c70d`, 5m45s).

## 20. Phase 33 — "C Programming/02_…_of_three.c": `tcc: error: file '…/Code-with-C/C' not found` (2026-09-09, `arena/01a083fc-codec`)

**Owner bug:** after §19 (the open file now runs instead of previewing index.html),
compiling a file whose PATH has a space failed: the Output Panel showed
`cc 'C Programming/02_largest_smallest_of_three.c' -o bin/…` and TCC reported
`tcc: error: file '…/Code-with-C/C' not found` — the space path had been
word-split into `C` + `Programming/…`.

**Root cause:** the outer shell quoting was correct end-to-end (`planFor` and
`TerminalHandoff` both `shellEscape` the source), and the `cc` frontend
received the source as ONE argument. But the `cc` script itself flattened the
converted arguments into a string (`converted="$converted $arg"`) and expanded
it UNQUOTED (`… $converted …`) in the TCC invocation — `$converted` held the
absolute path `/data/user/0/…/Code-with-C/C Programming/02_…_of_three.c`, so
unquoted expansion split it at the space and TCC only saw `C`.

**Fix:** `ShellEnvironment.ccScript()` now rebuilds the argument list with
`set -- "$@" "$arg"` while iterating a saved arg count, and passes them to TCC
as `"$@"` (each converted argument stays its own word, spaces intact). The
`-o` value is still captured to `outfile` and quoted. `ShellBootstrap.prepare`
rewrites the script on every RUN, so the fix lands with the next APK.

**How to verify (device):** clone `Code-with-C`, open
`C Programming/02_largest_smallest_of_three.c`, tap RUN ▶ → it compiles and
runs in the Output Panel. Host regression: `ShellEnvironmentTest.cc script
passes a source path containing spaces as one argument` (fake TCC records its
argv; the space path must arrive as a single `ARG:` line).

**CI:** `34321154191` ✅ GREEN first try (tip `fa34750`).

## 21. Phase 33 — "tcc: error: undefined symbol 'main'" on `C Programming/01_…_conversion.c` (2026-09-09, `arena/01a083fc-codec`)

**Not a CodeC bug — the repo's structure.** `Code-with-C` is a multi-file MENU
project: `C Programming/main.c` is the ONLY file with `int main()`; each
numbered file defines `void programNN(void)` (called from the menu). Compiling
one fragment on its own links no `main`, so TCC correctly reports
`undefined symbol 'main'`.

**What CodeC now does:** when a build fails with the "no main" linker
signature (`undefined symbol 'main'` from TCC, or ``undefined reference to
`main'`` from ld), the Output Panel appends a plain-language hint —
"This file has no main(). It looks like part of a multi-file project…"
(pure `CompilerDiagnostics.looksLikeMissingMain`, host-tested).

**How to actually run the menu project (multi-file build):**
1. Open the project, tap ⋮ → **Edit run config** (the `.codec.json` override).
2. Build: `mkdir -p bin && cc 'C Programming/'*.c -o bin/menu` (compiles
   `main.c` + all the program files together).
3. Run: `./bin/menu` — the menu starts; pick a program number.
For the owner's OWN practice projects, each `.c` file should carry its own
`main()` so RUN ▶ compiles and runs that single file directly.

**How to verify (device):** open `C Programming/01_…_conversion.c` → RUN ▶ →
build fails and the hint line appears; then set the `.codec.json` above → RUN ▶
compiles all files and runs the menu.

**CI:** `34323755844` ✅ GREEN first try (tip `6a6d36b`).

## 22. Phase 33 — self-contained C files whose entry is not `main` now RUN automatically (2026-09-09, `arena/01a083fc-codec`)

**Owner model:** every `.c` practice file is a complete program; its entry may
be named `program01` / `solve` / `run` / … rather than `main`.

**Behaviour now:** RUN ▶ on a single C file with no `main` but EXACTLY one
function compiles it through a generated wrapper (written under the app cache,
never the project) that `#include`s the file and supplies `main()`, then runs
the result. So `C Programming/01_number_base_conversion.c` runs `program01()`
directly — no `.codec.json` needed. A file that defines `main` (or several
functions with no `main`) is left on the normal path; the §21 hint still
covers the genuinely ambiguous cases.

**How to verify (device):** clone `Code-with-C`, open
`C Programming/01_number_base_conversion.c`, tap RUN ▶ → the Output Panel runs
program 01 ("Enter the number: …") and accepts scanf input. Opening
`C Programming/main.c` still runs the menu.

**CI:** `34330372322` ✅ GREEN first try (tip `36e4fda`).

## 23. Phase 33 device round ✅ PASSED (2026-09-09, `arena/01a083fc-codec`, owner: "Ok working as i wanted")

The owner's practice-project model, end to end, on the Code-with-C repo:

1. Open `C Programming/01_number_base_conversion.c` in the web showcase → RUN ▶
   compiles and runs the file (the auto-project fix §19 + space-path fix §20 +
   non-`main` entry wrapper §22 all hold together), never previewing `index.html`.
2. Set a launch default (⋮ → Set as launch default) → RUN ▶ on a different file
   asks "Run default / Run open".
3. Open `index.html` → RUN ▶ still previews it.

**Result:** all steps behave as the owner described; the owner commanded the
merge ("Then merge") → **✅ MERGED to `main` via PR #57**.

## 24. Phase 33.1–33.3 first-hour UX — device round (owner runbook, 2026-09-09)

**Goal:** a fresh install opens on three starter tiles, the Packages hub opens
on Languages, and the app no longer calls itself a C-only IDE.

1. **Fresh install** (uninstall + reinstall, or Settings → About → "Show the
   welcome screen again" → restart). The app opens on the **welcome**: three
   tiles only — **C / Python / HTML** — with no bottom tab bar.
2. **Tap C.** A "C Starter" project with `main.c` opens in the editor; **RUN ▶**
   compiles with the built-in TCC and prints `Hello, CodeC!` — **no Packages
   speech, no install dialog**.
3. **Second launch.** Kill and reopen the app — it opens the `main.c` you left
   in (last file), **no tiles**.
4. **Python gate.** Settings → About → "Show the welcome screen again" → tap
   **Python**. `main.py` opens; RUN ▶ shows the install sheet **only if
   `python` is not installed** (with python installed it just runs).
5. **Packages hub.** Open the **Packages** tab. It opens on a
   **Languages & IntelliSense** section (python, nodejs, clang, TCC, the
   `intellisense-*` cards); **Unix tools** is a collapsed header that expands
   on tap; INSTALL python still streams `pkg install -y python` in the
   Terminal.
6. **Identity.** Settings → About shows "CodeC — write and run C, Python,
   JavaScript, and HTML on your phone. C works offline with no setup."
   (not C-only). Delete every project so the Projects list is empty — the
   empty state shows the three starter tiles, and tapping one creates the
   project and opens its file.

**PASS:** steps 1–6 all hold. **FAIL:** the welcome does not show on a fresh
install, RUN ▶ on the C tile asks for packages, the second launch shows tiles
again, the Packages hub opens on Unix tools, or About/README still read
C-only.

**Result (2026-09-09):** CI `34346424311` ✅ GREEN first try (tip `5db9e33`;
assemble + unit tests + lint); **device round ✅ PASSED** (owner: "Device
test pass"). Merge HELD on the owner's command while the UX/UI phases 34–37
are queued.

## 25. Phase 35 editor typing feel — device round (owner runbook, 2026-09-09)

**Status: ✅ DEVICE-PASSED by owner report ("Device test pass").** The
implementation tip is `317b89a`, documentation follow-up `88839cd`, and Build
APK CI `34367008019` plus current-tip CI `34367770583` are GREEN. The owner did
not provide device/model/measurement details; this record intentionally does
not invent them. The checklist below remains the reproducible recipe for a
future regression round.

Install the CI APK from the Phase 35 build, then run this matrix on the
slowest available phone and at least one other Android device:

1. Settings → CodeC Keys → leave **Keep the code keyboard open while editing**
   ON. Open a long file, type a burst, switch to Output and back, and confirm
   CodeC Keys stays mounted without re-tapping. Collapse it from ⋮, confirm the
   explicit collapse wins, then leave and re-enter the editor and confirm the
   ON default returns. Turn the setting OFF and confirm the system IME is
   usable again.
2. Run an interactive program. While it waits for stdin, confirm the system
   IME appears; after the run ends, confirm CodeC Keys returns when the setting
   is ON.
3. Type a burst with the system IME and with CodeC Keys. The caret should stay
   solid and glide with the text, without a per-key jump; after about 0.5 s of
   idle it should resume its normal blink. Tap/drag to select text and confirm
   selection handles and editing still work.
4. Open a file without tapping the code area: confirm there is no insertion
   caret, no forced scroll-to-caret, and no `Ln 1, Col 1` status bar. Tap near
   the middle of a line and type; the caret must appear at that exact tap.
   Press a CodeC Keys cap before tapping and confirm it starts at the end of
   line one and the edit is retained.
5. Complete the measurement card in `docs/chat-phase35/MEASUREMENT_CARD.md`
   at small, 2,000-line and 10,000-line files with p50/p95/p99. Also verify
   undo/redo, `()` pairing, `{` + Enter splitting, ghost/chip acceptance,
   Gboard/Samsung/SwiftKey composing text, and tab switching.

Record Android version, OEM, keyboard, density/screen size, file sizes, the
numbers, and any failure here before changing the phase to DEVICE-PASSED.

## 26. Phase 36 terminal speed & feel — device round (owner runbook, 2026-09-09)

**Status: ✅ DEVICE-PASSED by owner report.** The original Phase 36
acceptance and the follow-up checks for the three reported terminal
regressions passed after fixes `da126cf` and `11fe8d7` on
`arena/01a086a0-codec`; Build APK CI `34374983032` and `34375710614` are GREEN.
The owner authorized merge; **PR #60 is MERGED to `main`** after its final
checks passed.

### 26.1 Cold-start measurement and state

1. Install the Phase 36 APK on the slowest available phone and one other
   Android device. Clear/force-stop CodeC, then open Terminal cold. Confirm the
   terminal immediately shows **starting shell…**, the first usable prompt is
   followed by **running**, and no blank/exited-looking gap is mistaken for a
   dead shell.
2. Open Logs and capture the startup card containing tap→userland-done,
   userland-done→prepare-done, prepare-done→prompt, and tap→prompt. Record
   device model, Android version, cold/warm result, and the numbers. Do not
   choose a latency optimization by intuition; identify the largest measured
   split.
3. Re-open Terminal warm several times. Confirm a marked valid userland is not
   re-extracted or release-probed on every open, compiler-setting changes cause
   a fresh preparation, and a forced userland reinstall invalidates the
   preparation and replaces the shell.

### 26.2 Session behavior and handoff

4. While a fresh shell is starting, dispatch at least three ordered commands
   from Packages/run handoff (for example install, `cd`, then a run command).
   Confirm each runs once, in order, only after the actual first prompt; there
   must be no fixed-delay race or dropped command. Repeat with two sessions and
   confirm each session keeps its own queue and interactive input.
5. Verify Ctrl+C, terminal rendering, resize, paste/typing, restart, close,
   session switching, and the session cap. A normal shell exit must display
   **exited (code)**; a live shell must display **running**.
6. Trigger the forced userland reinstall. Confirm every old shell is stopped,
   the replacement visibly says **userland was updated — session restarted**,
   and the new prompt remains usable. Confirm `cc` still compiles with the
   configured standard/warnings/optimization and package/run handoff still
   reaches the intended project.

The owner reports the original Phase 36 and follow-up device checks passed.
The exact device matrix and measurements were not supplied, so none are
invented here. Merge is authorized by the owner after the green PR checks.

### 26.3 Owner follow-up: streaming, background survival, and switching

The original Phase 36 device acceptance passed, and the owner then validated
these three follow-up regressions separately from the cold-start recipe:

7. **Progressive package output.** Install a package that is not already in the
   userland cache, or run a download large enough to last several seconds.
   Apt/dpkg download and install progress must visibly update while the command
   is running, including carriage-return progress rewrites; it must not appear
   as one final batch. The fix removes `friendly_apt()` command substitution,
   so the PTY and existing emulator/RenderPump remain the streaming path.
8. **Background survival.** Start a long-running download or command, lock the
   screen or background CodeC for longer than ten minutes, and return. The
   command must still be running and its output/history must be intact. An
   active session promotes `TerminalForegroundService` and uses the app's
   process-priority contract; the old ten-minute wake-lock timeout is gone.
   Close/stop every session and confirm the foreground notification and wake
   lock are released rather than leaving CodeC alive indefinitely.
9. **Session switching.** Open two sessions, produce distinct output in each,
   and switch repeatedly while each is idle at `codec $` and while one is
   running a command. There must be no injected blank lines, duplicate
   `codec $` prompts, lost history, or cross-session command delivery. Terminal
   geometry is propagated to every session and seeded before a new PTY starts,
   avoiding a switch-only `SIGWINCH`/readline redraw. Explicit restart and
   close behavior must still work.

**Result:** items 7–9 passed on the owner's device validation. The owner
reported device acceptance and authorized merge; the exact device matrix was
not supplied, so none is invented here.

## 27. Phase 38 — "Permission denied" when a build runs the downloaded compiler: the Output Panel now prints the Termux-fallback steps (2026-09-10)

**Symptom.** A C/C++ build fails and the Output Panel shows a line like
`sh: /data/.../bin/clang: Permission denied` (or
`compiler-wrapper.sh[11]: …clang: Permission denied`). This is Android
refusing to *execute* a downloaded toolchain binary — the W^X policy for
apps targeting API 29+, a `noexec` mount (some emulators / cloud phones),
or a broken toolchain.

**What changed in Phase 38.2.** The four setup steps used to live in
Settings → "Termux Engine" → "How to enable". That card is **gone** (the
engine was never user-selectable — Phase 21 removed the picker). The
steps now appear **in the Output Panel, on the failed build itself**, as
a `SYSTEM` line under the error — generated by the pure
`CompilerRemediation` helper, so the text has exactly one home in code.

**The steps (quoted verbatim from `CompilerRemediation.STEPS` — this doc
is the wording source; keep them in sync):**

1. Install Termux 0.109+ from F-Droid or GitHub (termux.dev).
2. In Termux run: `echo "allow-external-apps=true" >> ~/.termux/termux.properties && termux-reload-settings`
3. Grant CodeC the **"Run commands in Termux environment"** permission
   (Android Settings → Apps → CodeC IDE → Permissions → Additional
   permissions).
4. In Termux run: `pkg update && pkg install clang`

**Verification.** `CompilerRemediationTest` (host): the shell and wrapper
signatures yield the remedy; ordinary compile errors (`error: unknown
type name`), a compiler "cannot open output file … Permission denied"
diagnostic, and git's `Permission denied (publickey)` yield **nothing**
(the detector requires an exec marker and refuses `error:` lines).

**Notes.** The fallback engine itself (`TermuxCompiler`,
`CompilerService` AUTO chain), the `com.termux.permission.RUN_COMMAND`
permission and the `<queries>` entry are all **unchanged** — Phase 38.2
removed the *panel*, not the mechanism.

## 28. A red `Build APK` you cannot read → read its annotations (agent runbook; Phase 40.4, 2026-09-10)

**Symptom (what happened on `arena/01a08b68-codec`):** 16 consecutive red
`Build APK` runs, 2 hours, every one failing in the Kotlin front end in under
2.5 minutes, with the *same* errors repeating across runs — because nobody read
the output. The sandbox cannot open CI logs (`gh run view --log-failed` and
`gh api …/jobs/<id>/logs` both fail: the log blob host is unreachable), but the
errors are still readable — GitHub keeps them as check-run **annotations**.

**Read them (sandbox):**

```
python3 scripts/ci_annotations.py                 # latest run on this branch
python3 scripts/ci_annotations.py 34489229134     # a specific run
```

**Read them (owner's browser):** repo → **Actions** → the `Build APK` run →
the `Assemble debug APK` step → **Annotations** (the same `e: file:///…` lines).

**Rules that prevent the loop:**

1. Compile the pure Kotlin locally first (`rule.md` §9: jdk4py + kotlinc; the
   harness in the Phase 40.4 record compiles the real `ui/projects` files).
2. Never push a second time with the same error signature: read the annotation,
   fix *that line*, push once.
3. Fix in place. 11 "Rewrite X" commits in that loop moved the errors instead of
   removing them.
4. Only call APIs that exist in this repo; open the owning file first
   (`GitRedactor`, `GitErrors`, `GitManager` are all Android-free and testable).
5. Two red runs, same signature = wrong strategy. Stop, re-derive from the
   phase doc.

## 29. Text is hard to read / "it is violet 💜 but not very good to read" (owner report; Phase 40.5, 2026-09-10)

**What it was.** The accent was stored as a raw `#AARRGGBB` (default
`#FF6200EE`, the Android Studio template violet) and pushed straight into
`colorScheme.primary` for **both** themes. `#6200EE` is a light-theme colour:
on the dark surface it is **2.25:1**, where WCAG 2.2 AA (§1.4.3) wants 4.5:1
for body text — and white on it was 1.72:1, so accent buttons were as bad. In
the light theme the same value is a fine 7.44:1, which is why only dark mode
looked broken. Material 3 solves this with tonal roles (primary = tone 40 in
light, tone 80 in dark); the app never applied that to a user's accent.

**The fix (what to check if it ever regresses).**

1. `AccentPalette.rolesFor(seed, dark, surface)` (`ui/theme/CodecPalette.kt`)
   keeps the accent's hue and moves only its lightness until it clears 4.5:1 on
   the surface the theme actually draws; `onPrimary`/`onContainer` are measured
   (`Contrast.onColorFor`), never assumed. All twelve roles come from the
   accent (`secondary` = less chroma, `tertiary` = hue +60°).
2. Colours drawn on a **translucent** surface must be derived from that surface,
   not from the base surface: the editor status bar (`surfaceVariant` @0.5 over
   `surface`) and the key caps (`surface` @0.9, or the accent tint) compute the
   composited colour and correct their text against it (`Contrast.ensureReadable`).
   This is the rule that was missing in nine places.
3. Every token in `CodecPalette` carries its measured ratio in KDoc, and
   `AppContrastTest` (16 cases) + `ChromeContrastTest` (7) re-derive all of
   them — the chrome test reads the alphas **out of the UI sources**, so raising
   one fails the build. `:app:testDebugUnitTest` runs both in CI through the
   `gradle-bootstrap` bridge.

**"It is still violet" — read this before filing it.** The default accent is now
**CodeC green `#3DDC84`**, but a value already stored on the device is never
rewritten (a user's choice must survive an update). Settings → Appearance will
show the stored accent; tap **CodeC green** to switch. A clean install starts
green.

**Not covered on purpose (WCAG 2.2 §1.4.3 exceptions):** disabled controls
(they must read as disabled), and decorations with no information of their own
(the status bar's `·` separators, the bottom-sheet drag handle, the keyboard's
pressed-state tint — the pressed cap's *label* is still 6.01:1 dark / 6.45:1
light).

**Measured before → after highlights:** muted panel text 2.90:1 → 6.31:1 ·
output-header legend 4.44 → 5.80 · QR fallback 4.16 → 5.44 · terminal
"shell failed" 4.17 → 4.63 · project tiles 2.57/2.78/4.32/4.23 → 5.02/5.13/5.72/5.10 ·
hub rows 3.68/4.47 → 5.17/5.90 · editor comments (monokai/dracula/github)
3.95/3.03/3.05 → 4.99/5.47/4.77 · bottom-nav labels (light) 3.53 → 5.23 ·
status "LF" (light) 3.78 → 8.17 · key-cap tints and hints, badges (3.46) and
55 %-alpha borders (2.25, need 3:1) — all fixed and pinned. Full table:
[`chat-phase40/PART_40_5_COLOUR_REPAIR.md`](chat-phase40/PART_40_5_COLOUR_REPAIR.md).

## 30. Sending feedback / where the WhatsApp button is (Phase 41, 2026-09-10; follow-up round same day)

**Settings → Feedback & Support → OPEN** (its own screen, also reachable
from the exit popup): type what happened, optionally tick *Include the
last 120 log lines* (redacted — no tokens, paths shortened) and *Include
the last crash* (prefilled after a crash), then CHAT ON WHATSAPP / COPY
REPORT / EMAIL / GITHUB ISSUE. The developer's number (+91 62967 46606)
and email are HARDCODED in the app (round 2, owner decision: feedback
always goes to the developer; there is nothing to configure and no
number/email field in the UI).

- **"A popup asks how it went when I close the app."** — that is the exit
  survey (owner-requested for the testing phase): rate with stars, SHARE
  EXPERIENCE opens the Feedback page with the rating in the report, GIVE A
  REVIEW opens the GitHub repo, and **tap back again (or EXIT) to close**.
  It is off-able: the Feedback page's *Ask for feedback on exit* switch.
- **"There is no CHAT ON WHATSAPP button."** — there is nothing to
  configure (the number is built into the app), so a missing row is a BUG:
  report it via GITHUB ISSUE. On a device without WhatsApp the button is
  still shown, but tapping it copies the report and names the number to
  write to instead of opening a dead link.
- **"CHAT is greyed out."** — write what happened first (non-empty text
  enables CHAT); COPY REPORT and GITHUB ISSUE always work.
- **"Tapping CHAT copied the report instead of opening WhatsApp."** —
  WhatsApp is not installed on the device (or could not open). The toast
  says so and the number to write to is shown under the button — the
  report is never lost.
- **"Nothing was sent, right?"** — right. CodeC only opens the target app
  with the message typed; you read it and press send yourself. The
  checkboxes reset to a fresh choice every visit — nothing is attached
  silently.
- **"The report is cut short."** — the WhatsApp draft is budgeted (1 800
  chars) so the URL survives OEM browsers; the report says
  `[log trimmed — use COPY FULL REPORT for the whole thing]`. COPY REPORT
  has no budget.

## 31. `UserlandInstallerTest` red on "Connection reset" — a loopback keep-alive race (2026-09-10)

**Symptom:** `Build APK` red on one test only —
`UserlandInstallerTest.missing shared library is diagnosed and phase 2 fallback works`:
`expected fallback Installed, got Failed(message=Connection reset)`.

**Cause (read from the code, not guessed):** the test serves the bootstrap
over an in-process loopback `HttpServer`; `DownloadManager` downloads with
`HttpURLConnection` + `Range` resume. The JVM keep-alive pool reuses a
connection the test server has just closed (idle timeout under CI load) →
the ranged GET goes down a stale socket → "Connection reset". Ranged
requests are not transparently retried, so it surfaces as `Failed`.
Timing-dependent: the same suite passed on the four previous runs of this
branch (`34525080153`, `34525817215`, `34529280630`, `34530054784`).

**Not a Phase 41 fault** — the diff touches feedback UI only; the download
path is Phase 2-era and unchanged.

**Disposition:** retrigger (docs-only commit / new run of the same code).
If it recurs, the for-cause fix is a bounded retry-on-reset around
`DownloadManager`'s connect (which would ALSO make real-device downloads
more robust on flaky mobile networks) — recorded here so the next
occurrence starts at the root cause, not at the symptom.

## 32. `pkg: not found` after you closed CodeC during the one-time install — and the setup bar that prevents it (Phase 44, 2026-09-12)

**Symptom (the owner's report, verbatim):** *"Userland is installing but the test
user don't know it's installing so they close app before it complete than letter
when they try to install any other pkg got errors"* → *"If it opens the terminal
1st and show a warning don't close the terminal while userland is installi[ng]"*.

**What was actually wrong — three separate things, all in the code:**

1. The download started at *process start* (`TerminalViewModel.init`) and its
   progress only ever reached the terminal emulator's buffer, so a user who
   never opened the **Term** tab never saw it happening.
2. No foreground service and no wake lock during the download (both were gated
   on "a session is alive", which is false while the install runs), so Android
   could doze or kill it mid-flight.
3. `UserlandInstaller.swapPrefix` had a kill window between its two renames:
   killed there, `usr` is **gone** and an orphan `usr.old-*` stays forever —
   next launch offline → the install is skipped → `pkg: not found`. That is the
   owner's exact symptom, and nothing ever cleaned the orphan.

**What you see now (any build containing Phase 44):**

| Surface | Text |
|---|---|
| Setup bar, **every tab** | `Setting up CodeC's Linux tools — 42 % · C works right now` (no ✕ until setup is finished and the tools really work) |
| Fresh install | the **Terminal tab opens first**; above the chip: `Don't close CodeC — it is finishing a one-time setup (42 %)`; the chip itself: `downloading userland 42 %` → `verifying download…` → `unpacking userland…` → `running` |
| Status bar | `Downloading CodeC's Linux tools — 42 %` with a progress bar, for the whole download (when notifications are allowed; nothing crashes when they are not) |
| Packages tab | one honest sentence — `CodeC is downloading its Linux tools (58 %). Don't close the app — this happens once.` — plus **VIEW SETUP**. Nothing is silently queued |
| Editor (non-C run / install) | the same sentence in the Output Panel, ending `C works offline right now.` |
| Offline | `CodeC needs the network once to finish setting up its Linux tools. C works offline right now.` — never "using built-in cc" |
| After a kill | on the next launch CodeC repairs itself and says so **once**: `Setup was interrupted; CodeC restored your Linux tools.` |

**C is never gated.** `RUN ▶` on a `.c` file and `cc hello.c -o hello` in the
terminal work at every percentage of the download (TCC lives in the APK).

**If you still get `pkg: not found`:** open the **Term** tab and tap **⬇**
(install / repair the Linux tools). If the bar claims the tools are ready but
`pkg` still fails, that is a Phase 44 bug worth reporting — the gate now checks
the **real** `$PREFIX/bin/pkg` (exists, executable, non-empty) instead of a
marker file, so a false "ready" means the check itself is wrong. Include the bar
text and Settings → **Logs**.

**Why the repair is safe:** the ledger records the attempt phase with
`commit()` (never `apply()`), the boot repair restores the newest `usr.old-*`
only when `usr` is missing, and the sweep touches **only** `usr.old-<digits>`
and `.userland-staging-<digits>` directories directly inside the app's files
directory — never `projects/`, never a live prefix, never anything newer than
the repair itself.

**Device round:** [`chat-phase44/DEVICE_ROUND.md`](chat-phase44/DEVICE_ROUND.md)
— 12 rows including the three kill points (mid-download, mid-extract, mid-swap)
and the owner's `pkg install python` afterwards. **Not yet run** (no device in
the agent sandbox), so Phase 44 is 🚧 IMPLEMENTED, not tested on hardware.

## 33. Seven `Unresolved reference` errors from ONE missing constructor parameter (agent runbook; Phase 44, 2026-09-12)

**Symptom:** `Build APK` red with a burst of errors that all point at *member
accesses* in a single file — run `34692621773` (tip `e3d1e64`):

```text
TerminalViewModel.kt:88:28   None of the following candidates is applicable:
TerminalViewModel.kt:515:22  Unresolved reference 'installIfNeeded'.
TerminalViewModel.kt:521:17  Cannot infer type for this parameter. Specify it explicitly.
TerminalViewModel.kt:537:56  Unresolved reference 'releaseTag'.
TerminalViewModel.kt:549:60  Unresolved reference 'message'.
TerminalViewModel.kt:550:55  Unresolved reference 'message'.
TerminalViewModel.kt:672:32  Unresolved reference 'installedRelease'.
```

Every one of those members **exists**: `installIfNeeded` and `installedRelease`
are public in `UserlandInstaller`, `releaseTag` and `message` are `UserlandStatus`
properties that the same file had been using since Phase 2.

**Cause:** the error with the **smallest line number** was the only real one.
Line 88 was `UserlandInstaller(application, ledger = setupLedger)`; Phase 44.2
added `ledger` to the *primary* constructor but not to the secondary
`(Context)` constructor the ViewModel actually uses. The call did not resolve →
`userland`'s type was unknown → **every** member access on it reported
`Unresolved reference`. One missing parameter, seven errors, six of them noise.

**The rule (read before fixing any red run):**

1. **Sort by line number and fix the first error.** A receiver whose
   construction failed makes all of its members look missing; the member errors
   are consequences, not faults.
2. `None of the following candidates is applicable` / `Cannot infer type for
   this parameter` on a **construction** line is a *signature* fault, not a
   missing import and not a missing dependency.
3. When a parameter is added to a class the local harness cannot compile
   (anything needing `android.*` or Compose), add it to **every** constructor —
   secondary constructors are the ones that get forgotten, because the primary
   is where the new field lives.
4. **Pin the signature** with a source-scan case (`SetupGateWiringTest`'s *the
   installer's Context constructor accepts and forwards the ledger*) so the next
   refactor fails on the host instead of on CI.

**Why the local pre-validation did not catch it:** the `rule.md` §9 harness
compiles the pure policy files and the real test sources; `UserlandInstaller`
and `TerminalViewModel` need `android.content.Context`, so they are outside it.
A `kotlinc` pass over the Android files *without* `android.jar` does surface
these, but inside a flood of unresolved-type cascades — and the filter used
("show me syntax errors only") hid them. The durable pre-push check for this
class of fault is cheaper than a compiler: **when a change touches a
constructor signature, grep every call site of that constructor and read each
one.** For Phase 44 that is two sites (`TerminalViewModel.kt:88` and
`UserlandInstallerTest`, which uses the primary constructor and was unaffected).

## 34. `NewApi` lint errors in a "pure" Kotlin file — the host harness cannot see `minSdk` (agent runbook; Phase 44, 2026-09-12)

**Symptom:** `Build APK` gets **past** the Kotlin compile and the unit tests and
then dies in `:app:lintDebug` (run `34692963464`):

```text
Lint found 2 errors and 210 warnings. First failure:
LINT ERROR [NewApi] app/src/main/java/com/codeci/ide/ui/terminal/SetupRecovery.kt:248:
  Call requires API level 26, or core library desugaring (current min is 24):
  java.nio.file.Files#isSymbolicLink
  … java.io.File#toPath
```

**Cause:** the new "pure" policy file used `java.nio.file` for a symlink check.
It compiles and passes on the host JVM (where `java.nio.file` exists), and it
compiles fine under `kotlinc` in the `rule.md` §9 harness — **but lint checks
every file in `app/src/main` against `minSdk 24`, pure or not.** "No Android
imports" is not the same as "no Android rules".

**The law (already written in the codebase, now written here too):** in
`app/src/main`, **no `java.nio.file.*` and no `java.time.*`** — both are API 26.
`TarGzExtractor.kt:64` carries the comment `/** minSdk 24 — avoid java.nio.file
(API 26). */`, and `ShellEnvironment` reaches `java.nio.file.Files` **only
through reflection** (`Class.forName("java.nio.file.Files")`, with a comment
saying why). Test sources may use them (`CodecApiProtocolTest`, `CrashLogTest`,
`SwapRecoveryTest` do) — unit tests run on the host JVM and lint does not apply
`NewApi` there.

**API-1 replacements that came out of this fix:**

| Needed | Not allowed (26+) | Use instead |
|---|---|---|
| "is this a symlink?" | `Files.isSymbolicLink(f.toPath())` | `f.canonicalFile.name != f.name` (a link's canonical file is its target); treat an unresolvable path as a link |
| "delete this tree without following links" | `Files.walk` / `walkFileTree` | `f.delete()` **first** — it `unlink`s a symlink without following it — and only then `f.deleteRecursively()` (which DOES follow a link to a directory) |
| "copy/move a file" | `Files.copy` / `Files.move` | `FileInputStream`/`FileOutputStream`, `File.renameTo` |
| "now, as an instant" | `java.time.*` | `System.currentTimeMillis()`, `SystemClock.elapsedRealtime()` |

**Pre-push check that costs nothing** (a red CI round is ~6 minutes):

```bash
git diff --name-only <base> -- 'app/src/main/**.kt' |
  xargs grep -n "java\.nio\|java\.time\|List\.of(\|Map\.of(\|Set\.of(\|toPath()\|Files\."
```

Anything it prints in `app/src/main` is either a lint error or needs an
`SDK_INT` guard. Guarded calls (`if (Build.VERSION.SDK_INT >= O)`) are accepted
by lint; that is why `TerminalForegroundService`'s `createNotificationChannel`
and `startForegroundService` never trip it.

## 35. "The bar says open Terminal but the terminal doesn't open — the editor does" (owner device report; Phase 44 round 1, 2026-09-12)

**The owner's words:** *"It have bugs · I couldn't not open the terminal it's
opening the editor · The top a massage 'C works right now. The linux tool need
one install- open terminal and tap download' · But it's not closing or opening
terminal · Every package saying view setup but terminal not opening editor
opening."*

He had installed the Phase 44 build **over an existing install** (no first-run
welcome), on a phone whose userland had been killed mid-install during earlier
testing — the original bug, already baked into the data directory.

**What was actually wrong (three faults, in the order they bite):**

1. **The install marker lied.** `UserlandInstaller.installIfNeeded(force = false)`
   answers `AlreadyInstalled` from the **release marker alone** — the fast
   warm-open path, which must not touch the network or launch a probe. A pre-44
   build wrote that marker **before** the two-rename swap, so a kill between them
   left a valid marker over a prefix with no working `bin/pkg`. The setup stage
   therefore said `READY` while the disk said *not usable*, and the bar showed the
   only sentence it had for that: *"C works right now · the Linux tools still need
   one install — open Terminal and tap ⬇"*. Nothing ever re-decided it, so it
   stayed forever.
2. **That bar was a wall.** Its action button was rendered only while the setup
   was *in flight* or `FAILED` — not in this state — and its ✕ called the
   *note*-dismiss, which cleared a note that was not there. So: a sentence with no
   button and a close button that did nothing. Exactly *"it's not closing or
   opening terminal"*.
3. **"Go to the terminal" arrived at the editor.** Every such navigation used the
   bottom-nav idiom `popUpTo(startDestination) { saveState = true }` **plus
   `restoreState = true`**. `restoreState` does not mean "show that tab": it
   restores the *whole previously saved sub-stack* for that destination — and if
   the user had opened a file in the editor after using the terminal, the editor
   was the top of that saved stack. Hence *"terminal not opening editor opening"*.

**What to do now (build with the round-1 fixes):**

- Update to the new build. On launch CodeC checks the **disk**, not the marker:
  if `$PREFIX/bin/pkg` or a shell is missing, the app **opens the Terminal tab
  itself** and the bar says *"Setup didn't finish — the Linux tools aren't
  working. Open the Terminal tab and tap ⬇ to install them again."*
- Tap **anywhere on the bar** (or **VIEW SETUP**, now always present) → the
  terminal opens and stays open.
- Tap **⬇** in the terminal toolbar → the download runs with a visible
  percentage → afterwards `pkg --version` works and the bar disappears.
- The ✕ now really removes a settled bar; it returns as soon as the state
  changes (a new install, a repair).
- C keeps working the whole time (`RUN ▶` on a `.c` file, `cc` in the terminal) —
  that was never gated and still is not.

**Which build to install for round 2:** the fixes are in CI run
[`34695797493`](https://github.com/pabi277/CodeC/actions/runs/34695797493)
(commit `4bf3c4c`, green) → **Artifacts** → `CodeC-IDE-debug`
(or `CodeC-IDE-release`, signed, 6.65 MB, v1.3.17). Install it **over** the
current install and **do not clear app data first**: recognising the
marker-only prefix is the whole point of the fix, and wiping data would hide it.

**If it still misbehaves:** Settings → **Logs** → COPY (or the crash dialog →
COPY REPORT) and send it. The lines that matter are tagged `SetupRecovery`,
`TerminalViewModel` (`setup keep-alive …`, `post-repair setup refresh failed`)
and `TerminalFgs`.

**Law recorded for future work (nav):** *`restoreState = true` restores a
sub-stack, not a destination.* Any navigation whose meaning is "show me X now"
must not use it. Phase 49 (`BackRouter`) is where the whole navigation model gets
the systematic pass; until then the Terminal-tab navigations are pinned by
`SetupGateWiringTest`'s `every go-to-the-terminal navigation arrives at the
terminal`.

## 36. "The guide boxes felt random, half-missing, and one was cut in half" (owner device report; Phase 45 round 1 → round 2, 2026-09-12)

**The report:** *"Working but some problem the guided box are not consistent with
flow like / Not showing the full box guide at one and you didn't add all / remove
the next option only the guide will show click the option where showing the guide to
the next / make it like demo_flask is always present."*

**What was actually wrong (four things, all in the shipped code — not the phone):**

1. **A fresh install showed almost nothing.** Phase 44's download counts as
   "someone else owns the screen", and on a first run the download *is* most of the
   session. Correct rule, wrong net effect: the guide was silent exactly when the
   user was newest.
2. **Two boxes per screen, per visit.** `MAX_PER_ARRIVAL = 2` plus a surface filter
   meant the editor taught ☰ and RUN ▶ and then stopped; the *Show tabs* beat needs
   the keyboard up, and Packages/Terminal need you to walk there. Hence *"you didn't
   add all"* — five beats existed, but rarely in one sit.
3. **The card was placed using a guessed height (150dp).** A taller card fell into
   the "neither above nor below fits" branch and was clamped over its own hole —
   *"Not showing the full box guide at one"*.
4. **A box could be cut behind a dialog or a closed drawer.** The scrim is drawn in
   the activity window; an `AlertDialog` is a window of its own, and Material keeps a
   closed drawer's rows laid out (so they still report a position). Both produce a
   hole you cannot see — *"not consistent with flow"*.

**What it does now:** one **tour of ten beats** in the order you would use the app
(☰ → change project → `demo_flask` → `app.py` → RUN ▶ → the preview's Back → the
reveal-tabs handle → the Packages tab → its install card → the Terminal tab → its
status chip), each card labelled `Tour · n of 10`. **The highlighted control is the
only way forward** — there is no NEXT/GOT IT button, a tap outside does nothing at
all, and **SKIP TOUR** (or back) ends the whole tour in one tap. A beat whose control
is not on screen is either waited for (the four controls that are always there) or
passed over **without being spent**, so the tour can neither stall nor point at
nothing. No box is drawn while a dialog, the drawer, the exit survey, safe mode or a
moving download owns the screen. And **`demo_flask` is always present** — delete it
and it comes back on the next list refresh (your edits to it are never touched),
because beats 2-3 teach it by name.

**If you are re-testing on a phone that already ran round 1:** tap **Settings → About
→ Reset tips** and kill the app first. The five round-1 beat ids are unchanged, so an
upgraded install would otherwise show only the five NEW beats.

**Known limit (recorded, not hidden):** a box cannot point *into* a dialog — Compose
dialogs are their own window, so a hole cut from the activity window would land in the
wrong place. The two beats that live in dialogs (the "Open folder" project picker and
the *Install Python?* prompt) are therefore taught by the copy of the beat before them
(*"choose demo_flask"*, *"tap **Install**"*). Lifting the limit means publishing every
anchor in absolute screen coordinates; that is a deliberate follow-up, not an
oversight (`docs/chat-phase45/PART_45_2_COACH_MARKS.md`, deviation 9).

**Rows:** `docs/chat-phase45/DEVICE_ROUND.md` G1-G23 (G9-G23 are the tour).

## 37. "The tour had a skip on it, so it got cut" (owner device report; Phase 45 round 2 → round 3, 2026-09-12)

**The report:** *"You add the skip option and it's not a trough guide mean it got cut /
I want a full process 1st to last without skip anything in this / At the end option to
close and view again."*

**What was wrong (four things, all in round 2's shipped code):**

1. **SKIP TOUR was the most visible thing on the card.** Every one of the ten beats
   carried a button whose only job was to end the tour, and one tap on it wrote all ten
   beat ids as seen. The guide read as something to dismiss, not something to walk.
2. **Beats were passed over the moment their control was not laid out.** Round 2 split
   the tour into four "waiting" beats and six "pass over" beats so it could never
   stall. Passing over is instant and silent, so: tap RUN ▶, the Flask server takes a
   few seconds, and the tour had already walked on to the tab bar and the Packages tab.
   Boxes arrived out of the order the app was doing things in — a *full process* with
   the middle missing.
3. **Two beats had no target exactly when they were needed.** The project-name box was
   published only when switching projects would teach something, so a phone already in
   `demo_flask` (or in scratch mode) never saw it. The *tabs are here* box was anchored
   to the thin reveal handle, which exists only while the keyboard hides the bar.
4. **The project picker closes the drawer** (it always has), and the next beat is a row
   *inside* that drawer — so after choosing `demo_flask` the tour went silent and waited
   for the user to work out which button brings the files back.

**What it does now:** ten beats, in order, and **nothing is skipped**. A tour card has
no button at all: the highlighted control is the only way on, a tap outside does
nothing, and the back button navigates instead of ending anything (the same beat is
there when you come back, unspent). While a beat waits for its control, **nothing is
drawn** — no scrim, no card — so the app is never covered and never trapped. The card
at the end is the only one with buttons: **VIEW AGAIN** (all ten beats from the first,
and the app takes you to the editor where beat 1 lives) and **CLOSE**.

**The one thing that can still move the tour past a beat** is a 20-second *stall
guard*, and it is narrow on purpose: it fires only for a control that **could be on
this screen and is not** (the Packages card behind a collapsed section, the `app.py`
row after you picked some other project). It never fires for a beat you have to travel
to or wait an install out for — timing those out is what would cascade (the Flask
preview stalls during a Python install, then every beat behind it, and the tour
"finishes" on a screen the flow never reached). A stalled beat is **passed, not spent**:
it is never written to the seen set, so it comes back on the next launch or on VIEW
AGAIN.

**If you are re-testing on a phone that ran round 1 or 2:** Settings → About → **Reset
tips**, then kill the app. The beat ids are unchanged, so without a reset you would
only see the beats you never reached.

**Known limits (recorded, not hidden):** a box still cannot point *into* a dialog
(Compose dialogs are their own window), so the project picker and the *Install Python?*
prompt are taught by the copy of the beat before them; and a tour parked on a beat whose
control never appears — no Python and the install declined, so no Flask preview — waits
there silently, with the beats behind it, until it does. Both are deliberate: the
alternative is the skipping the owner just reported
(`docs/chat-phase45/PART_45_2_COACH_MARKS.md`, deviations 9 and 13-16).

**Rows:** `docs/chat-phase45/DEVICE_ROUND.md` G1-G28 (G9-G28 are the tour).

## 38. "The first tap only closed the box", and "an install should pause the rest" (owner device report; Phase 45 round 3 → round 4, 2026-09-12)

**The report:** *"It's good but i have 2 request- / 1. When the userland is installing
and unpacking the user can not access any other other option and it will show a sweet
massage of why can't access any other option / 2. Now the steps feel like an overlay on
the botton so 1st click disappear the massage and i have to click 2nd time to really
work but if someone don't click 2nd time it just cut off the flow of tutorial / So do
something"*

**What was wrong (request 2 — a bug in round 3's own mechanism):** the tour's box left
your tap **unconsumed** and advanced on the **press**, trusting the framework to hand
the rest of the gesture to the control underneath. But advancing writes state, the app
recomposes from it (the next box, the drawer plumbing), and a control rebuilt in the
middle of a gesture is **cancelled** — so the box went away, the beat was recorded as
taught, and nothing opened. The second tap was not the same lesson again: the tour had
already moved to a beat whose control was not on screen, and it then waits in silence.
One dead tap = one lost lesson and a parked tour.

**What it does now:** every control the tour spotlights **publishes its own click**, and
the box performs that click and swallows the gesture — so **one tap does both halves**
(the drawer opens, the file opens, RUN ▶ runs, the tab switches) and the tour moves on
in the same tap, with the click first and the advance after it. Nothing can fire twice.
Two honest exceptions: the terminal's status chip is a **label**, so its box advances
and performs nothing; and a Packages card whose language is **already installed**
publishes no click either, because its buttons are RUN / UNINSTALL / REINSTALL and none
of them is what the box is teaching — your tap behaves like an ordinary tap on the card.
A **drag** that starts inside the hole and lifts outside is not a tap: nothing is
performed and no beat is spent (that is a scroll, and answering a scroll with an
install would be worse than the bug). Beat 6's copy now says *"tap this handle"* instead
of *"swipe up"*, because while a box is up the tour only accepts a tap — the swipe still
works the moment the tour is over.

**What it does now (request 1 — the chrome lock):** while an install is really moving,
the options that cannot work are **paused**, and a paused option **says why** instead of
doing nothing.

- **The one-time Linux tools** (downloading / checking / unpacking, with no working
  prefix yet): the four tabs that are not **Terminal** are dimmed with a small 🔒, and
  a tap on one shows *"Hang tight — CodeC is downloading its Linux tools (NN %). Other
  options are paused for a moment so this one-time setup finishes cleanly. The Terminal
  tab shows every step."* and does not navigate. The Terminal tab is never paused — that
  is where the download, the percentage, the "don't close" line and the ⬇ retry live.
- **A language or tool you asked for** (RUN ▶ → **Install**, streaming into the Output
  Panel): the other four tabs pause — including Terminal — and **the Editor stays
  open**, because the Output Panel is where that install can be watched. Inside the
  editor, ☰ and RUN ▶ answer with the same sentence, and the drawer's edge swipe is
  closed (a lock you can swipe around is not a lock).
- The sentence appears **once when the pause begins** and again on every refused tap.
- **Nothing is paused** for: a run of your own program, a Flask server you started (a
  run is not an install), the startup probe, a setup that has already finished or
  failed, or an in-flight **upgrade** of a working tool set (its own line is
  *"everything still works"*). And the older guarantee stands: **typing and `cc` never
  wait for a download** — while the Linux tools are being built the editor keeps both.
- The guided tour pauses with the chrome: a box on a paused control could only be spent
  by a tap that merely shows the sentence.

**Known limits (recorded, not hidden):** the lock answers taps on **chrome** — the tabs,
☰, RUN ▶, the edge swipe. It is not a full-screen wall: the file you have open stays
open and editable, a `.c` file still compiles, and the setup bar's VIEW action still
works, because a pause with no way to watch the install is how an app looks bricked. If
you want the harder version (one "setting up" screen with nothing else tappable), that
is a small change to one pure function and it is worth knowing that a first launch
starts this download automatically.

**Rows:** `docs/chat-phase45/DEVICE_ROUND.md` **G29-G38** (G29-G33 the one-tap rule,
G34-G38 the lock). Specification: `docs/chat-phase45/PART_45_2_COACH_MARKS.md` §Round 4
and `docs/chat-phase44/PART_44_1_VISIBLE_SETUP.md` §"Phase 45 round 4 — the chrome
lock" (deviations 17-20).

## 39. "The lock arrives a beat late — make it instant" (owner device report; Phase 45 round 4 → round 5, 2026-09-12)

**The report:** *"The lock option is good but still it late user can switch before the
start of userland download because is takes a little time to connect and user can switch
task between them / Make it instantly after 1st open and others are ok"*

**What was wrong:** round 4's lock waited for a stage that means *work is moving* —
downloading, checking the download, unpacking. Before the first byte there is a window
where the app is reading the disk, reaching the network and waiting for the server's
first answer, and in that window nothing was paused. On a cold phone with a cold radio
that window is seconds long, which is exactly how long it takes to tap **Packages** and
read *"not installed"* about tools that are already on their way — and then to be
looking at the wrong screen when the download starts.

**What it does now:** the pause is decided by **your prefix, not by the stage label**.
From the very first frame after the app opens, if the Linux tools are not usable yet and
the setup has not given up, the four other tabs are already dimmed with their 🔒 and
already answer a tap with *"Hang tight — CodeC is getting ready to set up its Linux
tools. Other options are paused for a moment so this one-time setup finishes cleanly.
The Terminal tab shows every step."* The **Terminal** tab is open from frame one, which
is also where the download, the percentage, the *don't close the app* line and the ⬇
retry live.

**Three things still pause nothing,** and each one protects a phone you can use:

- **A working tool set.** If your Linux tools are installed, nothing is paused at any
  stage — including while an **upgrade** of them downloads and unpacks (*"everything
  still works"*). This is read from the disk when the app starts, not guessed, so an
  installed phone never sees a pause flash on launch.
- **A setup that has stopped.** Failed (offline, out of disk, corrupt download) or
  unsupported (no bootstrap for this device) have their own sentence and their own ⬇
  retry, and **C still compiles offline** — so the app stays open.
- **Safe mode.** After three failed launches the app starts reduced so you can export
  your projects and report the crash, both of which live in Settings. A crash-loop phone
  is the last phone that should be funnelled to one tab, so the startup pause does not
  apply there. An install you asked for still pauses the rest, even in safe mode.

**Also covered by the same rule, without being asked for:** a boot-time **repair** of an
install a process kill interrupted (44.2's restore) now pauses the other tabs while it
puts the prefix back — same reason, same sentence, Terminal open.

**Rows:** `docs/chat-phase45/DEVICE_ROUND.md` **G34** (amended: the pause is there before
the first percentage) and **G39** (the first-second window). Specification:
`docs/chat-phase44/PART_44_1_VISIBLE_SETUP.md` §"Round 5 — *Make it instantly after 1st
open*".
