# CodeC Website Phase W2.2 — Getting started, first hour (`/start`)

**Status:** 📋 **PLANNED** · **Cost:** `[static]` · **Effort:** S
· **Depends on:** W1
· **Target file:** `website/start.html`

> Source: `README.md` — "Run C on the phone" (terminal section), "Package &
> Command Hub", "Editor, Projects & Web preview".

---

## 1. Design — page structure (top to bottom)

1. **H1:** "Your first hour with CodeC — 6.6 MB universal, offline C, no setup." + one line: assume the app is installed via universal APK 6.6 MB signed SHA256 (link → `/install` v2.2: release channel app-v* tags only, debug vs release, export-all note); you'll type everything below into the app.
2. **Step 1 — Your first compile (editor):** create a file `hello.c` with canonical `#include <stdio.h>` + `printf` program (`.code-block`), tap **RUN** ▶ (Auto engine — built-in TCC first offline instant, no picker — Phase 21), read output under editor. One paragraph on what just happened (built-in TCC static executable instant offline, arm64-v8a + x86_64 null on armeabi-v7a/x86, universal APK 6.6 MB -74% via R8+shrinkResources, per-ABI splits reverted assets/tcc not filtered).
3. **Step 2 — The terminal loop:** open **Term** (tab or toolbar terminal icon), then two commands — `cc hello.c -o a.out` / `./a.out` — each in own code block, one command per line, Enter after each. **Teach the `./` rule** (current directory not on PATH; `./` names file you just built) and where it lives (projects in app-private storage `filesDir/CodeC/projects/<name>` + externalFilesDir candidate, which is why `./a.out` executable; noexec physics for linked folders — mirror). Mention multi-terminal sessions (TerminalSessionManager 8-cap, session switcher dropdown + rename/close).
4. **Step 3 — Programs that read input:** program using `scanf` / `getchar` must run in **Term**, not via RUN (RUN has no keyboard into process — Phase 23 inline PTY input InlineInputRow last LazyColumn item when waitingForInput sendLine + context-aware run keys ↵ Enter/Ctrl+C/Tab/↑↓ while waiting). Show tiny scanf variant and where you type input.
5. **Step 4 — Install a package (1 tap):** Packages tab → find **nano** (or ripgrep) → **INSTALL** → **RUN**; show live status badge flipping to `INSTALLED ✓` + quick system actions pkg update/upgrade/heal/status + interactive command runner + extra-keys row + custom macros. One link to full `/packages` page.
6. **Step 5 — Preview a web page + LAN share:** open HTML file → **RUN** is preview (no separate button); edit → save → **live reload**; console shows under page; plus LAN server opt-in 0.0.0.0 bind pool 8100–8199 two URLs (loopback + LAN) + QR ZXing + open in browser via OpenInBrowser copy fallback + keep-alive foreground service (Phase 37). One link to ch-12 and ch-14 (learn.html anchor) for deep dive.
7. **Where things live** (mini map bottom bar): Projects · Editor · **Terminal (middle)** · Packages · Settings — one line each + first-hour tiles Phase 33.1 three starter tiles C/Python/HTML DataStore first_launch_complete + opens straight into file you left in first launch → Projects hub autosave ~2s + export-all ZIP + backup include-list-only + crash-loop guard safe mode 3rd launch + feedback hardcoded +91 62967 46606 / email.
8. **Cross-links strip:** "The full course →" `/learn` · "Which compiler engine? →" `/engines` (Auto only, picker deleted) · "Stuck? →" `/faq` (BETA B-1…B-8 + backup + crash-loop + export-all) · "Install details →" `/install` (universal APK).

> **v2.2 update:** first hour now includes universal APK 6.6 MB, Auto engine only no picker, multi-session terminal, LAN server, first-hour tiles, open into last file, autosave, export-all, backup include-list, crash-loop guard, feedback hardcoded, inline PTY input + run keys.

### Meta: title "Getting started with CodeC — first hour".

## 2. Implementation steps

1. Build the page in the W1.1 chrome (active nav: Start).
2. Write the 5 steps with code blocks; every behavior claim gets a source
   line recorded in `chat-web3/`.
3. Self-dependent sweep (plan §5.5).

## 3. Exit condition

```text
1. Render at 360/1440; the five steps read as a continuous first-hour path;
   code blocks match README examples.
2. The scanf-in-Term rule and the ./ rule are both stated explicitly.
3. Cross-links: /install, /engines, /faq, /learn, /packages all use final
   URLs (404s for unbuilt pages expected until their phase — noted).
4. Sweep PASS; source lines recorded in chat-web3/.
```
