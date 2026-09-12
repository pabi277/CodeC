<p align="center">
  <img src="docs/icon/codec-512.png" width="112" alt="CodeC — the >_ mark">
</p>

# CodeC IDE

> **CodeC — write and run C, Python, JavaScript, and HTML on your phone. C works offline with no setup.**

An Android IDE for your phone: a built-in C compiler (TCC) that works offline
with no download, Python and Node runtimes you install from the **Packages**
tab, an in-app VT/ANSI terminal with a signed `pkg` repository, and an HTML
preview served by a local loopback server. Write code in projects or as single
files and tap **RUN**.

> **🔒 STANDING RULE (owner, 2026-08-26):** agents/session branches must
> **not open a PR or merge anything without the owner's explicit command** in
> chat. Committing to and pushing the session branch is fine; PR creation and
> merging wait for the owner.
>
> **Future updates (owner, 2026-09-01):** **all phases are complete.** The
> agent waits for the owner to report a bug — it listens carefully, finds the
> underlying code problem, and solves it; it does not start new work on its
> own. The owner merges to `main`. Operating manual: [`rule.md`](rule.md) —
> start there.

## Install the APK from GitHub

**The release channel (Phase 42.1)** — for anyone who just wants the app:

1. Open https://github.com/pabi277/CodeC/releases and pick the newest
   **CodeC IDE** release (`app-v…` tag).
2. Download **`CodeC-IDE-<version>-universal.apk`** — the only APK, and
   always the right one (per-ABI variants were measured and reverted in
   Phase 42.2). The release notes carry `sha256:` lines if you want to
   verify the download first.
3. On your phone: download the APK → allow **Install unknown apps** → install.

**Developer / branch builds:** push a branch (or merge to `main`) → GitHub
Actions builds the APKs → **Actions** → the **Build APK** run → **Artifacts**
→ `CodeC-IDE-debug` (unsigned-key debug build; updates only over other debug
builds) and `CodeC-IDE-release` (signed with the upload key). **A release is
published only from an `app-v<X.Y.Z>` tag** — the tag must equal
`versionName`, and `versionCode` must exceed every shipped one (the publish
step checks both before attaching anything).

In the app: **Settings → About → Check for updates** looks only at `app-v*`
releases, compares versions numerically ("up to date" / a named refusal when
older), verifies the `sha256:` line before installing, and opens the Releases
page instead whenever the release publishes no checksum. It never installs a
`userland-*` bootstrap as an app and never phones home by itself.

Direct releases page: https://github.com/pabi277/CodeC/releases

## Run C on the phone

1. Install the APK, open the editor, tap **RUN**. That's it.

CodeC ships with a **built-in C compiler** (TCC, Tiny C Compiler — the same approach as
apps like Coding C / C4droid): a static musl toolchain is embedded in the APK for
**arm64-v8a** and **x86_64** devices, so compiling works **offline, instantly, with no
downloads, no Termux and no setup**. Programs are compiled to fully static executables.

### Compiler engines (automatic — the picker left in Phase 21)

There is nothing to pick: every RUN uses **Auto** — built-in TCC first
(offline, instant); if that's unavailable, the Clang module from
**Packages**; and if Android blocks the downloaded compiler (Android 10+
W^X policy, noexec storage, CPU mismatch, broken toolchain), CodeC
compiles and runs through a compatible terminal app's **Termux Clang**
automatically. The four setup steps for that last fallback appear in the
Output Panel exactly when they are needed — see
[TROUBLESHOOTING.md §27](docs/TROUBLESHOOTING.md).

The bundled Clang module (optional) must be **arm64**; an x86 emulator can't run it — but
the built-in TCC covers x86_64 emulators automatically.

### In-app terminal & Package Manager (Mini-Termux)

CodeC ships a real **VT/ANSI terminal** (Canvas grid + PTY via JNI `openpty`):

1. Open the **Term** tab, or tap the terminal icon in the editor toolbar.
2. A login shell starts under `$PREFIX` (`/data/data/com.codeci.ide/files/usr`).
3. `cc` is the built-in TCC. Type **one command per line**, Enter each time:

   ```
   cc hello.c -o a.out
   ```

   ```
   ./a.out
   ```

   The `./` is required (cwd is not on `PATH`). Projects live in app-private storage so `./a.out` is executable.
4. Programs that use `scanf` / `getchar` must run in **Term**, not the editor RUN button (RUN has no keyboard into the process).
5. `pkg` is a guarded CodeC-only frontend for the Phase 3 apt/dpkg repository
   (`https://pabi277.github.io/CodeC/dev`). The app installs the Phase 3
   bootstrap release `userland-v2-dev` (SHA-256 verified, staged, atomic) and
   provides 25+ packages including `git`, `python`, `clang`, `nano`, `make`, `ripgrep`, `tmux`, and more.

#### The one-time setup you can see (Phase 44)

The first launch downloads CodeC's Linux tools **once** (SHA-256-verified,
staged, atomic). While that runs:

- a slim **setup bar sits at the top of every tab** —
  `Setting up CodeC's Linux tools — 42 % · C works right now`;
- a **fresh install opens the Terminal tab first**, with
  `Don't close CodeC — it is finishing a one-time setup (42 %)` above a status
  chip that reads `downloading userland 42 %`;
- the **status bar shows the same percentage** for the whole download (a
  foreground service + wake lock keep it alive while the screen is off);
- **C keeps working** — `RUN ▶` on a `.c` file and `cc` in the terminal are
  never gated by setup, at any percentage;
- the **Packages** tab refuses an install with one honest sentence and a
  **VIEW SETUP** button, instead of silently queuing `pkg` into a prefix that
  has none;
- if the app **is** killed mid-install, the next launch repairs the prefix (an
  interrupted swap is renamed back, orphan `usr.old-*` / `.userland-staging-*`
  directories are swept) and says so once in the bar.

Offline during setup: `CodeC needs the network once to finish setting up its
Linux tools. C works offline right now.` — tap **⬇** in the terminal toolbar
when you are back online.

#### The guide: five slides, then five spotlights (Phase 45)

The first launch teaches the app in two layers, and neither one nags:

- **Five slides** after the starter tiles — *your files live in the ☰ menu* →
  *RUN ▶ compiles and runs (C works offline)* → *one download, one time* →
  *a real terminal* → *projects vs single files* — with **SKIP** on every slide
  (back is SKIP too). They appear once; an upgrade shows them once as well.
- **Five spotlights** (coach marks), the first time you reach each screen: the
  editor's **☰** and **RUN ▶**, the **Show tabs** handle that appears while you
  type, the **Packages** install card (*adding a language downloads once*), and
  the terminal's **status chip**. At most two per visit, one per control for the
  app's life, and a spotlight never points at a control that is not on screen.
  Tapping the highlighted control does its own job *and* closes the mark.

**See the guide again** from any of three places: Settings → About → **Help &
guide**, the Projects tab's **⋮ → Guide**, or the editor's **☰** drawer footer →
**Guide**. Settings → About → **Reset tips** brings the slides and every
spotlight back (it changes nothing else — no project, file or other setting).

### Package & Command Hub (Packages tab)

The **Packages** tab provides a visual 1-tap package manager and command hub:
- **1-Tap Install & Run:** Tap **INSTALL** or **RUN** on any package (`git`, `nano`, `python3`, `clang`, `make`, etc.) to execute the command directly in the live terminal.
- **Quick System Actions:** 1-tap buttons for `pkg update`, `pkg upgrade -y`, `codec-setup-storage`, `pkg status`, `pkg heal`, and `pkg repair`.
- **Live Status Badges:** Checks real-time installation status in `$PREFIX/bin` (`INSTALLED ✓` / `AVAILABLE`).
- **Interactive Command Runner:** Execute custom commands directly from the UI.

The extra-keys row (ESC, TAB, CTRL, ALT, arrows) and custom macros in Settings make mobile keyboard input seamless. Smooth 60fps pinch-to-zoom and long-press selection with word boundary detection and copy/paste contextual menu are fully supported.

## Editor, Projects & Web preview

Since Phases 8–9 (all device-accepted, 2026-08-29) CodeC is a full little IDE around that
terminal — and since Phases 15–17 (device-accepted, merged 2026-08-31) it wears a
Spck-grade skin:

- **Projects Hub**: the Projects tab is a card list (type mark, `⌥ branch · N
  files · age`, change badge, amber **↑N** when commits never reached the
  remote) with filter chips + search and ONE `+` sheet —
  New Project / Clone Git Repo / Import ZIP / Open Folder.
- **Spck-style editor**: nav-drawer file tree with in-tree git status letters,
  tabs in the app bar (dirty dot, close), a snippet/extra-keys row above the
  status bar, and a Source Control sheet with per-file stage toggle. The app
  **opens straight into the file you left in** (first launch → Projects hub),
  and edits **autosave** ~2 s after you stop typing.
- **Switch Branch**: branch list (local + remote, plus **New branch…**) with
  Spck's promise — dirty work is stashed and restored when you come back.
- **Honest git**: conflicts are grouped in purple with **Mark Resolved** and
  block the commit; a branch with no upstream is published on first push
  (`--set-upstream`); and a failed push never looks like a successful one —
  the sheet says **"Committed locally ✓ — NOT pushed: …"** and offers a
  **PUSH** retry.
- **Bottom bar**: Projects · Editor · **Terminal (middle)** · Packages ·
  Settings.
- **RUN ▶** builds & runs C/Python (or launches your web page); on an HTML
  file it **is** the preview — no separate preview button. Build outputs
  (`a.out`, `bin/`, `dist/`, …) are kept out of git automatically (repo-local
  ignore; your `.gitignore` is never touched).

The earlier foundations:

- **Projects**: private project folders (`files/CodeC/projects/<name>`) with a hierarchical
  tree, SAF folder/file/ZIP import & export, breadcrumbs, per-project run configuration,
  and **"Run in terminal"** on any `.c` file straight from the tree.
- **Editor foundation**: multi-file tabs (per-tab undo/redo + dirty state, save-all,
  reload), undo/redo with typing-burst coalescing, find & replace (literal + regex,
  highlights, replace-all), Format (`clang-format` bridge with built-in C-indenter
  fallback), bracket matching, compiler-error squiggles with tap-to-inspect and a
  missing-`;` quick fix, and a Ln/Col status bar.
- **VS Code colour (Phase 29)**: syntax highlighting runs on the same TextMate
  engine VS Code uses (sora `language-textmate`), with MIT grammar files for
  C, C++, Python, JavaScript/TypeScript, HTML, CSS, JSON, Shell, Markdown, Go,
  Rust, PHP, Ruby, Lua, XML and YAML. The default editor theme is **VS Code
  Dark+** (real `dark_plus` token colors); Monokai, Dracula and GitHub Dark
  remain in Settings. Grammars load once per process, lazily per language,
  off the UI thread.
- **Snippets, Emmet & suggestions (Phase 30)**: completions come from **29 MIT
  [friendly-snippets](https://github.com/rafamadriz/friendly-snippets) packs**
  bundled in the APK — C, C++, Python, JavaScript / TypeScript / React, HTML,
  CSS, Go, Rust, PHP, Ruby, Lua, Markdown and Shell, with no download and no
  server — so typing `for`, `#inc`, `def` or `main` offers the real snippet and
  parks the caret at its first hole. HTML, CSS and JSX also understand
  **Emmet** (clean-room, built in): `ul>li*3`, `div.card>p{Hello}` or `!` on a
  fresh line expands into full markup; `m10`, `d:f`, `p10-20` expand into CSS
  declarations. Up to **50 candidates** per keystroke — 8 chips above the
  keyboard, **⌄ more** for the rest, plus the inline ghost (**TAB ▸** accepts;
  **Enter always inserts a newline**, it never accepts). Accepting replaces
  exactly what you typed (`#in` → `#include <stdio.h>`), and the built-in
  **CodeC Keys** keyboard auto-closes `()` `[]` `{}` `""` `''` — `{` + Enter
  leaves you on an indented line with `}` below it.
- **Single files without a project**: the editor's file sheet treats the shared
  single-files folder as a first-class context — new file, open, run, delete, and
  "Save to project…" when a file graduates.
- **Open a project from the editor**: folder button / breadcrumb → Files & Projects
  sheet → *Change* folder picker (projects ⇄ single files); tabs re-key and the terminal
  follows.
- **Web preview**: HTML files preview in-app served by a loopback HTTP server over the
  whole project folder, so relative CSS/JS, `fetch("data.json")` and ES modules work;
  live reload on save. Console output shows under the page.

To rebuild the embedded TCC bundles (e.g. to add more ABIs), run `scripts/build-tcc.sh`
with a musl cross toolchain — the script is self-contained and CI-ready.

To use the optional Termux engine, install **Termux 0.109+** from
[F-Droid](https://f-droid.org/packages/com.termux/) or
[GitHub](https://github.com/termux/termux-app/releases) (the Play Store version is outdated
and does not support this), then in Termux run:

```bash
echo "allow-external-apps=true" >> ~/.termux/termux.properties
termux-reload-settings
pkg update && pkg install clang
```

and grant CodeC the **"Run commands in Termux environment"** permission
(Android Settings → Apps → CodeC IDE → Permissions → Additional permissions).
There is no Settings card for this any more (Phase 38.2 removed the Termux
bridge UI — the engine is fully automatic): when a build actually needs the
fallback, the Output Panel prints these same four steps
([TROUBLESHOOTING.md §27](docs/TROUBLESHOOTING.md)).

## Troubleshooting

> **Roadmap (historical):** Mini-Termux plan — [docs/TERMINAL_PLAN.md](docs/TERMINAL_PLAN.md).  
> **Full journey (phases 0–19, authoritative timeline):** [docs/JOURNEY.md](docs/JOURNEY.md).  
> **What's next:** [docs/NEXT_STEPS.md](docs/NEXT_STEPS.md).  
> **New-chat prompt (paste this first):** [prompt.md](prompt.md).  
> **Editor/Projects/preview record (Phase 9 rounds):** [docs/chat-phase9/](docs/chat-phase9/).  
> **Projects record (Phase 8):** [docs/chat-phase8/](docs/chat-phase8/).  
> **Phase 3 status:** [docs/chat-phase3/PHASE3_STATUS.md](docs/chat-phase3/PHASE3_STATUS.md) · **Phase 4 roadmap:** [docs/chat-phase4/PHASE4_ROADMAP.md](docs/chat-phase4/PHASE4_ROADMAP.md) · **Phase 5 roadmap (complete):** [docs/PHASE5_ROADMAP.md](docs/PHASE5_ROADMAP.md).  
> **Phase 1 device log (problems + solutions):** [docs/chat-phase1/README.md](docs/chat-phase1/README.md).

### "The built-in compiler could not start"

Only possible when the APK's TCC binary doesn't match the device CPU (an exotic ABI, or a
corrupted install). Reinstall the app; the Auto engine falls back to the Clang module /
Termux in the meantime.

### "Permission denied" when compiling — Android blocks the downloaded compiler

This error has two real causes:

1. **Android 10+ (API 29+) W^X policy.** Since Android 10, the system refuses `exec()`
   of downloaded binaries stored in an app's own data directory **when the app targets
   API 29 or higher** (see the [Android 10 behavior change](https://developer.android.com/about/versions/10/behavior-changes-10#execute-permission)).
   This affects *real phones*, not only emulators — the old build of CodeC targeted
   API 34, so on any Android 10+ phone the downloaded Clang was blocked with
   "Permission denied". The new builds use the same **targetSdk 28 compatibility mode
   that Termux uses**, which keeps downloaded binaries executable.
2. **noexec app storage.** Some emulators, cloud phones and managed devices mount app
   storage with the `noexec` flag. No app-side change can fix that — nothing can execute
   there, Termux included.

**Fixes, in order:**

1. **Update CodeC** to the latest APK (Settings → Install APK from GitHub). The new
   builds don't use the downloaded Clang at all by default: **Auto** engine compiles with
   the **built-in TCC compiler** that ships inside the APK and runs from the native
   library directory, which Android allows at any targetSdk — no module, no Termux, no
   network. If the error persists after updating, **uninstall and reinstall the app
   once** — Android labels the sandbox at install time and an in-place update may keep the
   old restriction.
2. **Switch the engine to Termux** (Settings → Compiler Engine → Termux, setup above).
   Termux's own storage is exempt, so this works even when the bundled compiler is
   blocked.
3. **Use Termux directly** — see [docs/TROUBLESHOOTING.md](docs/TROUBLESHOOTING.md) for a
   complete step-by-step C workflow in Termux.
4. On a truly `noexec` device (cloud phones, some enterprise ROMs) no local compiler can
   run; use a real phone or an online compiler.

If you still see this error, open **Logs** (Settings → Developer Options) and include the
"Device: …" line (ABI + app storage mount flags) in a bug report.

### "Exec format error" when compiling

CPU mismatch. CodeC ships an ARM64 compiler, so **x86/x86_64 emulators and 32-bit devices
can't run it directly**. Use a real ARM64 phone, or switch **Settings → Compiler Engine →
Termux** — Termux ships a native Clang for x86_64 and 32-bit ARM too. On an ARM64 phone,
reinstall the module (Uninstall → Download) to rule out a corrupted download.

### "Runtime libraries missing" when compiling

The toolchain is incomplete or corrupted. Open **Modules** → **Uninstall** → **Download**
again (the download is checksum-verified, so this usually means the install was
interrupted), or switch to the Termux engine.

### Install or compile hangs

Compilation is capped at 30s and program execution at 10s; both are killed
automatically. A compile that "hangs" for exactly 30s usually means the
toolchain can't start — check the error text above.

Editor **RUN** on a program that calls `scanf` will hit the 10s cap (exit 124).
That is waiting for input, not an infinite loop. Run it in **Term** with `./a.out`.

## Build locally

Android Studio: open this folder and run the `app` debug configuration.

Command line (needs JDK 17; the checked-in Gradle wrapper downloads the AGP-compatible Gradle 9.3.1):

```bash
./gradlew :app:assembleDebug
```

The APK is written to `app/build/outputs/apk/debug/`.
