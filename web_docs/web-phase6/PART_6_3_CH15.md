# CodeC Website Phase W6.3 — Chapter 15: Custom Setup & Advanced Tools

**Status:** 📋 **PLANNED** · **Cost:** `[static]` · **Effort:** S
· **Depends on:** W4 (W4.2 verified facts: Settings items, per-project
  run config, the advanced tool packages that ship: `make`, `clang`,
  `ripgrep`, `tmux`, `nano`)
· **Target file:** `website/ch-15.html`

> Scope law: only **shipped** features/tools. Planned app phases 20–24
> (formatter UI, notifications, adaptive theme, …) may appear at most as
> one "coming" line — never as content.

---

## 1. Content

- **Goal box:** make the app yours (keys, theme, per-project config) and
  graduate to the power tools: make, clang, ripgrep, tmux.
- **Need:** Chapters 05, 09, 10 done.

### Steps

1. **Your extra-keys row** — Settings → extra-keys: the default row
   (ESC, TAB, CTRL, ALT, arrows) and **custom macros** — build one: a key
   that types `cc ` (the compile prefix) or one that saves (the platform's
   save gesture) — exact Settings path from W4.2 facts; two custom keys,
   shown end to end.
2. **Theme & appearance** — what the app ships today (the dark
   Spck-grade skin; what is adjustable in Settings per W4.2 — be literal;
   if only dark exists, say "dark, by design, for now — an adaptive theme
   is on the project's roadmap" as the single allowed one-liner).
3. **Per-project run configuration** — the project-level run config (W4.2
   facts): set it once, RUN behaves per project (e.g. a C++-style project
   or a web project); show where the setting lives.
4. **make** — install it (Packages tab); a 3-target `Makefile`
   (`all` / `run` / `clean`) for a two-file C program (`.c` + a header);
   `make`, `make run`, `make clean` — the output of each; why a Makefile
   beats re-typing `cc` (two lines).
5. **clang, properly** — the bundled Clang module (arm64) or Termux engine
   (chapter 04): compile with `-Wall` and *read* one real warning it
   produces (a deliberately-uninitialized-variable snippet, fixed by the
   warning); the difference from TCC in one honest paragraph.
6. **ripgrep** — install it; `rg "TODO" .` in a real project; the flags
   you'll actually use (`-n` line numbers, `-t c` type filter if supported
   per W4.2 — keep to flags verified in the package) — one search, read
   one hit.
7. **tmux** — install it; the three things that matter: start a session
   (`tmux`), detach (the prefix + d), reattach (`tmux a`) — why: a build
   keeps running when the app backgrounded; one split example
   (`prefix + %`); nothing more (honest scope: tmux is deep — this is the
   entry, not a course).

- **Try it:** (1) add a custom extra-key that types `./` and use it in
  Term; (2) turn a chapter-16-P1-style two-file program into a Makefile
  and drive it with three commands; (3) start a `sleep 60` in a tmux
  session, detach, reattach — it's still running.
- **Mistakes:** custom macro not firing (the row must be enabled for the
  editor/terminal context it's meant for — Settings wording per W4.2);
  `make: not found` (install the package first — chapter 05); clang
  "Permission denied" (chapter 04/FAQ — W^X story); tmux prefix confusion
  (Ctrl-b is the default prefix — the extra-keys row has CTRL).

## 2. Implementation steps

1. Build `ch-15.html` (crumb "Chapter 15 of 17").
2. Every Settings path + every tool's availability from W4.2 facts; the
   one allowed roadmap line kept to one line; source notes in
   `chat-web6/`.
3. Self-dependent sweep.

## 3. Exit condition

```text
1. Template complete; prev → ch-14, next → ch-16.
2. Zero unshipped features taught (roadmap mention ≤ 1 line — noted).
3. Makefile + clang warning + tmux blocks valid per W4.2 facts;
   360/1440 clean; sweep PASS.
```
