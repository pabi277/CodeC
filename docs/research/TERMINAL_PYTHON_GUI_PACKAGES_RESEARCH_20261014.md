# Research — Power terminal, Python GUI (tkinter/turtle) and the ~150-package program

> **Date:** 2026-10-14 · **Status:** research dossier for the Phase 103–108 series (see
> [`docs/roadmaps/PHASE103_108_ROADMAP.md`](../roadmaps/PHASE103_108_ROADMAP.md)).
> **Owner rows, in order:** *"No, no code. I want to do more research and make the CodeC
> terminal as good as Termux. Find every inch of Termux and compare with terminal of
> CodeC."* → *"the most used and working pkg will be great and a powerful terminal
> directly connected with editor just like i want"* → (scope pick: **plan in chat first**,
> first quick win **selection + scroll**) → *"How should i create a python tinker view
> model in the app … for example pydroid can do it"* → (clarified: *"a complete gui that
> can handle tinker, turtle etc all"*) → *"create phases plan with all new pkgs but the
> pkg build will be after everything ends in other phases … and write all ~150 pkgs name
> that are most used and useful"*.
>
> **Evidence law (house format):** every claim about CodeC is a `file:line` read or a
> command run this session against `main` @ `5cbc1c0` (session branch
> `arena/0ebf69d7-codec`, tree clean before this dossier). Every external claim names its
> source below. Upstream package claims were verified mechanically against the pinned
> `TERMUX_PACKAGES_REF 1bbe6690` (2026-08-20), not from memory: the full recipe census
> (3,145 package directories across `packages/`, `x11-packages/`, `root-packages/`) was
> listed with `git ls-tree` and every name in §5 was matched against it.
>
> **Clean-room law:** Termux and termux-packages are GPL-3.0; behaviour and package
> *recipes* are reused exactly as the existing `codec-packages/` overlay already does
> (it is the same upstream, rebuilt for the CodeC prefix). No Termux app code is copied;
> terminal behaviour is re-implemented from the public VT behaviour, per `rule.md` §6.

---

## 1. What the owner is asking for, decomposed

Three deliverables, one product idea — **an editor with a Termux-class terminal inside
it, a Python GUI capability rivalling Pydroid, and the packages a developer actually
installs**:

| # | Deliverable | Owner's words | Phase |
|---|---|---|---|
| 1 | Termux-felt terminal parity (selection, scroll, shell) | "as good as Termux" | 103, 104 |
| 2 | Terminal *"directly connected with editor"* | "just like i want" | 105 |
| 3 | Packages tell the truth and install as working sets | (the npm lesson) | 106 |
| 4 | *Complete* Python GUI — tkinter, turtle, "etc all" | "pydroid can do it" | 107 (+ 108 chunk K13) |
| 5 | ~150 most-used packages, built last, in small chunks | "the pkg build will be after everything ends" | 108 |

**Sequencing law (owner, 2026-10-14):** the package *builds* happen after all other
phases in this series finish. The app-side work (103–107) must therefore never depend on
a not-yet-built package; where a feature needs one (the GUI spike needs chunk K13), the
acceptance row is explicitly deferred into Phase 108 and says so.

## 2. Where CodeC stands today (verified this session)

**Terminal.** A real PTY (`app/src/main/cpp/pty.c`), a Kotlin VT emulator
(`ui/terminal/`), batched 60 fps rendering with full resize **reflow** (ahead of Termux,
whose `TerminalBuffer.resize` does not reflow columns), multi-session, mouse reporting,
bracketed paste. The inch-by-inch Termux comparison (full audit, delivered in chat
2026-10-14; sources: local clones of `termux/termux-app @ 8629e63` and
`termux/termux-packages`, read file-by-file) found the remaining gaps concentrated in
the *view and shell layers*, not the core: selection has no drag handles and its copy
path falls back to the whole transcript (`TerminalScreen.kt:334`); `scrollSubPx` is
never reset on finger-lift, producing the first-open blank band the owner reported;
the shell is launched as `sh -c ". profile; exec bash -i"` so `~/.bashrc` never loads;
`pty.c` leaves IXON enabled (Ctrl-S can freeze output; Termux's JNI disables it);
alternate-screen scroll is not translated to arrow keys (mode 1007 is parsed but never
read by the view); fixed 8-column tab stops; no DECSCUSR cursor styles; faint/italic/
strikethrough parsed but not rendered; cursor is a static block; scrollback hard-capped
at 2,000 lines (`TerminalBuffer.kt:179`).

**Python GUI: deliberately absent today.** `codec-packages/scripts/apply-recipe-overrides.sh`
excludes the `python-tkinter` subpackage, and `properties.codec.sh` records why:
*"tk would pull the whole X11 closure; CodeC has no X11 use for Tkinter"*. Verified
consequence at the pinned ref: `packages/python/python-tkinter.subpackage.sh` is the
subpackage that ships `lib/python3.x/tkinter/`, `lib-dynload/_tkinter.*.so`,
**`turtle.py`**, `turtledemo` and `idlelib` — so CodeC's published Python has **no
tkinter, no turtle, and no IDLE**, and this is a one-line policy exclusion, not a
technical wall.

**Packages.** 33 published roots (`properties.codec.sh`, `CODEC_REPOSITORY_PACKAGES`):
`nano less coreutils grep sed gawk gzip tar make libmagic git wget bat ripgrep fd htop
tmux tree patch diffutils zstd m4 autoconf automake libtool python python-pip libllvm
nodejs npm php ruby lua54`. Three CI groups exist (`base`, `llvm`, `langs`) because one
6-hour GitHub job cannot build everything (Phase 20.1: dispatches killed at the
360-minute ceiling; libllvm is the long pole). The Packages-tab catalog
(`ModuleCatalog.kt`
...[truncated 15851 chars]