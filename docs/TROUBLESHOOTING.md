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

**Fix:** a pure `ProjectRunTarget.isRunnableSource(rel)` — a file with a run profile that is NOT the web preview (main.c/main.py/… run; index.html/style.css don't) — now gates both the editor's RUN dispatch and the ViewModel's web-project early-return, so RUN on a C file in an HTML project compiles/runs it in the panel. `runMainFile` no longer previews a non-HTML entry.

**How to verify (device):** in an HTML project, open a `main.c` (or `main.py`) file → tap RUN ▶ → the chooser offers "Run index.html / Run main.c" → **Run main.c** compiles and runs it in the Output Panel (the open tab stays). Opening `index.html` and tapping RUN still previews it.
