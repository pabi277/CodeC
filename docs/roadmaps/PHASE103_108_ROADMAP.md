# Roadmap — Phases 103–108: power terminal, editor connection, Python GUI, and the ~150-package program

> **Status:** 📋 PLANNED (2026-10-14, in chat; **no code, no package builds authorized
> by this document**). A roadmap is a proposal, not authorization (`docs/roadmaps/README.md`);
> each phase starts only on the owner's "Start Phase N".
> **Owner rows:** "as good as Termux" · "the most used and working pkg will be great and
> a powerful terminal directly connected with editor just like i want" · "a complete gui
> that can handle tinker, turtle etc all" · "create phases plan with all new pkgs but
> the pkg build will be after everything ends in other phases … write all ~150 pkgs
> name that are most used and useful".
> **Research:** [`docs/research/TERMINAL_PYTHON_GUI_PACKAGES_RESEARCH_20261014.md`](../research/TERMINAL_PYTHON_GUI_PACKAGES_RESEARCH_20261014.md)
> (terminal audit summary §2–3, editor connection §4, GUI routes §5, package census and
> chunking law §6 — every package name below verified against the pinned upstream ref).

## The order law (owner, 2026-10-14)

**All app work first (103–107). Package builds last (108).** No app phase may depend on
a package that does not exist yet; the one genuine dependency (Python GUI acceptance
needs chunk K13's packages) is written as a deferred acceptance row in 107 and executed
inside 108. Within 108, chunks are separate dispatches so no CI leg approaches the
6-hour ceiling — the Phase 20.1 lesson that created the group mechanism.

## Series at a glance

| Phase | Title | Category | Parts | Depends on |
|---|---|---|---|---|
| **103** | Terminal selection + scroll rebuild (owner's first quick win) | 01-terminal-userland | 103.1 selection · 103.2 scroll physics | — |
| **104** | Shell & session truth (login shell, PTY truth, alt-scroll) | 01-terminal-userland | 104.1 login shell · 104.2 PTY truth · 104.3 alt-scroll + session facts | 103 (same files, sequential) |
| **105** | Editor ⇄ terminal connection | 05-run-output-preview | 105.1 tap-to-source · 105.2 send-to-terminal + cwd · 105.3 select-URL + share | 103 (selection/copy paths) |
| **106** | Packages tell the truth (app-only) | 02-packages-toolchains | 106.1 manifest-driven catalog · 106.2 install sets + honest probes | — |
| **107** | Python GUI — Route A viewer & orchestration (app-only) | 07-platform-capabilities | 107.1 display screen · 107.2 run-with-GUI · 107.3 input/scaling | 104 (session/process care), 106 (install sets) |
| **108** | The curated package program — **built last** | 02-packages-toolchains | 108.1–108.15 = chunks K1–K15 | 103–107 merged; per-chunk owner command |

---

## Phase 103 — Terminal selection + scroll rebuild ⭐ first

**Owner pick for first quick win (2026-10-14).** Fixes reported bugs 1–2 the Termux way.

- **103.1 Selection.** Long-press → word → two draggable handles → floating toolbar
  (Copy / Select all / Paste) → edge auto-scroll; copy re-joins soft-wrapped lines;
  toolbar Copy never falls back to the whole transcript. Pure policy (selection model,
  join rules) + Compose wiring, host-testable. Replaces the one-word-popup model in
  `TerminalEmulatorView.kt`.
- **103.2 Scroll physics.** Sub-pixel remainder reset on finger-lift (kills the
  first-open blank band), fling/inertia, proportional scrollbar thumb; selection stays
  anchored to its text under scrolling output. Pure scroll-math policy + wiring.
- **Exit:** device rows pass — copy *part* of a wrapped line exactly; fresh terminal has
  zero blank band; mid-output selection survives scrolling. *(Brief written with this
  roadmap: `docs/phases/01-terminal-userland/chat-phase103/`.)*

## Phase 104 — Shell & session truth

- **104.1 Real login shell.** bash exec'd as a login shell (argv[0] convention) so
  `~/.bashrc` loads; the `". profile; exec bash -i"` chain and the `pj()` hack it kills
  are removed; CodeC's own profile contributions (prefix env, `cc`, `pkg`) preserved.
- **104.2 PTY truth.** IXON/IXOFF off (Ctrl-S can never freeze output — Termux's JNI
  does this), IUTF8 on, winsize carries pixel dimensions (`pty.c` + native bridge).
- **104.3 Alt-scroll + session facts.** In alternate screen, wheel/fling emit ↑/↓ (mode
  1007 honoured by the view at last); new sessions inherit `/proc/<pid>/cwd`; dead
  sessions say so ("Process completed (code N) — press Enter") instead of swallowing
  input; Keep-screen-on and Kill-process join the terminal menu.
- **Exit:** `.bashrc` alias works on first open; Ctrl-S/Q harmless; `less` scrolls with
  the wheel; new tab opens in the previous tab's directory.

## Phase 105 — Editor ⇄ terminal connection (the differentiator)

- **105.1 Tap `file:line` → editor.** Output spans matching `path:line[:col]` (compiler
  errors, tracebacks, `grep -n`) become tappable and open the editor at that line.
  Pure span-detection policy, tested against gcc/python/node output shapes.
- **105.2 Send to terminal.** Editor actions: send current file path, send selection as
  a command line; terminal cwd follows the open project (and new-session inherit from
  104.3 composes with it).
- **105.3 Select URL + share.** Long-press menu gains Termux's Select-URL dialog (all
  URLs in the visible buffer + scrollback), Share transcript / Share selected text.
- **Exit:** run a Python file with an error → tap the traceback line → editor opens at
  that exact line.

## Phase 106 — Packages tell the truth (app-only; builds nothing)

- **106.1 Manifest-driven catalog.** The Packages tab is generated from the published
  repository manifest (`ModuleCatalog.kt` stops hardcoding); a card cannot exist for a
  package that is not published; subpackage names (`file` from `libmagic`, `curl` from
  `libcurl`) become installable cards.
- **106.2 Install sets + honest probes.** Cards install *sets*: Node = `nodejs`+`npm`
  (the owner's device-confirmed npm fix, productized); ✓ shown only when every probe
  in the set passes. This phase is what makes Phase 108 chunks appear in the app the
  moment they publish — no app release per chunk.
- **Exit:** every visible card installs; every ✓ is probe-backed; removing a package
  from the manifest removes its card with no code change.

## Phase 107 — Python GUI, Route A (app-only; spike deferred to 108·K13)

Route A per research §5: real Tcl/Tk against a virtual X display (TigerVNC Xvnc),
shown in-app. This phase builds everything that does not need the packages to exist.

- **107.1 Display screen.** A `GuiDisplay` screen rendering an RFB stream
  (noVNC/WebView first; native RFB client as the recorded upgrade), touch→pointer and
  IME→keysym mapping, display scaling. Pure protocol/policy pieces host-tested.
- **107.2 Run-with-GUI orchestration.** Run profiles gain a GUI mode; `import tkinter`
  / `import turtle` detection offers it (Pydroid's behavioural trick, clean-room);
  server lifecycle bound to the run (start on demand, localhost-only RFB, always
  reaped — including under phantom-process pressure, with the foreground service).
- **107.3 Input & scaling polish.** Tk scaling defaults for touch-sized widgets, soft
  keyboard show/hide tied to Entry/Text focus where the protocol allows, rotation.
- **Exit (split honestly):** app-side exit here = viewer renders an RFB stream from a
  developer-supplied server + orchestration starts/stops cleanly. **GUI device
  acceptance (tkinter hello-world, turtle demo, IDLE) is a deferred row executed in
  108·K13**, the first moment the packages exist — per the owner's order law.

## Phase 108 — The curated package program (LAST; one part per chunk)

**Entry gate:** 103–107 merged **and** an owner command ("start packages" or per-chunk).
Each part repeats one proven motion: add roots + one `CODEC_REPOSITORY_GROUP_<NAME>`
in `properties.codec.sh` → recipe-override/security review → one CI dispatch (2 arch
legs, target ≪ 6 h/arch) → publish → **device smoke round** (every root's binary runs).
Bootstrap archives stay byte-identical throughout (repository-only law). Cards appear
automatically via Phase 106. Per-chunk exit: `pkg install <roots>` on device + smoke
tests pass; a red chunk is fixed for-cause, never papered over.

**The ~150.** 33 roots already published + **126 new roots below = 159 named roots.**
Every name verified at pinned ref `1bbe6690` (2026-08-20); roots named, installable
subpackages noted. Chunk order is value-first; heavies are isolated so no leg nears
the ceiling (estimates from Phase 20.1 actuals; per-chunk re-measure is part of each
part's record).

| Part | Chunk (CI group) | Roots | Why / smoke test |
|---|---|---|---|
| 108.1 | **K1 shell-core** (14) | `zsh` `fish` `bash-completion` `starship` `util-linux` `findutils` `procps` `inetutils` `mandoc` `bc` `dialog` `dos2unix` `which` `time` | The shell people actually live in; `zsh -c exit`, `man ls` renders |
| 108.2 | **K2 files·text·search** (14) | `zip` `unzip` `7zip` `unrar` `liblzma` (→`xz-utils`) `jq` `yq` `fzf` `zoxide` `eza` `ncdu` `pv` `parallel` `moreutils` | `jq .` on real JSON, `7z` round-trip |
| 108.3 | **K3 network·ssh** (14) | `openssh` `rsync` `aria2` `lftp` `socat` `websocat` `mosh` `dnsutils` `netcat-openbsd` `whois
...[truncated 3881 chars]