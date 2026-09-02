# CodeC Website Phase W2.2 — Getting started, first hour (`/start`)

**Status:** 📋 **PLANNED** · **Cost:** `[static]` · **Effort:** S
· **Depends on:** W1
· **Target file:** `website/start.html`

> Source: `README.md` — "Run C on the phone" (terminal section), "Package &
> Command Hub", "Editor, Projects & Web preview".

---

## 1. Design — page structure (top to bottom)

1. **H1:** "Your first hour with CodeC." + one line: assume the app is
   installed (link → `/install`); you'll type everything below into the app.
2. **Step 1 — Your first compile (editor):** create a file `hello.c` with
   the canonical `#include <stdio.h>` + `printf` program (`.code-block`),
   tap **RUN** ▶, read the output under the editor. One paragraph on what
   just happened (built-in TCC, static executable, instant, offline).
3. **Step 2 — The terminal loop:** open **Term** (tab or the toolbar
   terminal icon), then the two commands —
   `cc hello.c -o a.out` / `./a.out` — each in its own code block, one
   command per line, Enter after each. **Teach the `./` rule** (the current
   directory is not on `PATH`; `./` names the file you just built) and
   where it lives (projects are in app-private storage, which is why
   `./a.out` is executable).
4. **Step 3 — Programs that read input:** a program using `scanf` /
   `getchar` must run in **Term**, not via RUN (RUN has no keyboard into
   the process). Show the tiny scanf variant and where you type the input.
5. **Step 4 — Install a package (1 tap):** Packages tab → find **nano** (or
   ripgrep) → **INSTALL** → **RUN**; show the live status badge flipping to
   `INSTALLED ✓`. One link to the full `/packages` page.
6. **Step 5 — Preview a web page:** open an HTML file → **RUN** is the
   preview (no separate button); edit → save → **live reload**; console
   shows under the page. One link to chapter 14 (`learn.html` anchor) for
   the deep dive.
7. **Where things live** (mini map of the bottom bar): Projects · Editor ·
   **Terminal (middle)** · Packages · Settings — one line each.
8. **Cross-links strip:** "The full course →" `/learn` · "Which compiler
   engine? →" `/engines` · "Stuck? →" `/faq`.

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
