# CodeC Website Phase W3.1 — Compiler engines (`/engines`)

**Status:** 📋 **PLANNED** · **Cost:** `[static]` · **Effort:** S
· **Depends on:** W1
· **Target file:** `website/engines.html`

> Source: `README.md` — "Compiler engines (Settings → Compiler Engine)"
> table + surrounding prose; Termux setup section (for the Termux row).

---

## 1. Design — page structure

1. **H1:** "Four ways to compile. One button you usually never touch."
   One paragraph: tap RUN and **Auto** does everything — this page explains
   what it can fall back on and when you'd choose an engine yourself.
2. **The engine table** (`.table`, mirrors README order):

   | Engine | What it does |
   |---|---|
   | **Auto** (default) | Built-in TCC first (offline, instant); if unavailable, the Clang module; if Android blocks it (Android 10+ W^X, noexec storage, CPU mismatch), automatically compiles through **Termux's Clang** |
   | **Built-in (TCC)** | Only the compiler embedded in the APK; ANSI C + most of C99; learning & everyday code |
   | **Bundled Clang** | Only the Clang from **Modules** (full C11/C17, stricter warnings); **arm64 only** |
   | **Termux** | Always compiles with Termux's Clang (needs the Termux setup, link → `/install`) |

3. **Why TCC comes first** — 3 short paragraphs: it's inside the APK (no
   download, no network, no Termux); Android lets it run from the native
   library directory at any targetSdk (the W^X problem, linked to FAQ);
   fully static executables.
4. **Coverage honesty box:** TCC covers ANSI C + most of C99 — if you hit a
   C11/C17 feature, switch to Bundled Clang (arm64) or Termux.
5. **CHECK BRIDGE** — one paragraph: Settings → Compiler Engine →
   **CHECK BRIDGE** verifies the whole Termux chain in one tap.
6. **Emulator note:** x86_64 emulators can't run the arm64 Clang module —
   TCC covers them automatically; 32-bit → Termux engine (both already on
   `/install`, linked).

### Meta: title "Compiler engines — CodeC".

## 2. Implementation steps

1. Build the page (active nav: Engines).
2. Transcribe the table verbatim in scope from the README; record source
   lines in `chat-web3/`.
3. Self-dependent sweep (plan §5.5).

## 3. Exit condition

```text
1. Table matches README.md row-for-row in scope (diff recorded in
   chat-web3/); 360/1440 render clean.
2. Every "how do I" on the page links to the right page (/install for
   Termux setup, /faq for W^X).
3. Sweep PASS.
```
