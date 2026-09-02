# CodeC Website Phase W4.6 — Chapter 04: Compiler Engines

**Status:** 📋 **PLANNED** · **Cost:** `[static]` · **Effort:** S
· **Depends on:** W4.2 gate
· **Target file:** `website/ch-04.html`

---

## 1. Content

- **Goal box:** explain which engine compiled your last program; run
  CHECK BRIDGE; switch engines on purpose; know your device's limits.
- **Need:** Chapters 02–03 done (you've compiled and used Term).

### Steps

1. **You already used one** — chapter 02's RUN used **Auto** (the default):
   built-in TCC first, fallbacks automatic. One paragraph: most of the
   time, Auto is the whole story — this chapter is for *on purpose*.
2. **The four engines, in one table** (same truth as `/engines`, lesson
   phrasing): Auto / Built-in (TCC) / Bundled Clang / Termux — what each
   does + when you'd pick it (W4.2 facts, verbatim in scope).
3. **What TCC covers, honestly** — ANSI C + most of C99; learning and
   everyday code; when a C11/C17 feature appears → Bundled Clang (arm64)
   or Termux. One real example snippet that TCC *does* handle (simple
   struct + function pointers is fine — keep it ANSI C).
4. **Run CHECK BRIDGE** — Settings → Compiler Engine → **CHECK BRIDGE**:
   walk the tap path, read what a green result means (Termux chain intact),
   what a failure points at (link `/install` §Termux setup).
5. **Know your device** — arm64 phone: everything; x86_64 emulator: TCC
   covers it, the arm64 Clang module won't run (no problem); 32-bit: Termux
   engine recommended. (Device table = `/install`'s, one line + link.)
6. **Switching on purpose** — Settings → Compiler Engine → pick; recompile
   chapter 02's `hello.c` under the chosen engine; confirm output.

- **Try it:** (1) run CHECK BRIDGE and screenshot-free describe what it
  reported; (2) switch to Built-in (TCC) explicitly and recompile
  `hello.c`; (3) write a 5-line program using a struct + a function,
  compile with TCC, run it.
- **Mistakes:** expecting the Clang module on an x86 emulator (it's
  arm64); thinking Auto "breaks" when it falls back (fallback = success);
  blaming the engine for a missing `;` (that's your code, chapter 02).

## 2. Implementation steps

1. Build `ch-04.html` (crumb "Chapter 4 of 17").
2. Table cross-checked with `/engines` page (same source — W4.2 facts);
   source notes in `chat-web4/`.
3. Self-dependent sweep.

## 3. Exit condition

```text
1. Template complete; prev → ch-03, next → ch-05.
2. Engine table == /engines page == README (triple source noted).
3. CHECK BRIDGE tap path matches Settings wording (W4.2 facts).
4. 360/1440 clean; sweep PASS.
```
