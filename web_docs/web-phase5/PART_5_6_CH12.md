# CodeC Website Phase W5.6 — Chapter 12: Networking & SSH

**Status:** 📋 **PLANNED** · **Cost:** `[static]` · **Effort:** S
· **Depends on:** W4 — **content variant decided by W4.2's verified table**
  (which networking tools the package repo actually ships)
· **Target file:** `website/ch-12.html`

> Honesty rule: this chapter teaches **only what the CodeC package repo
> ships today** (plan §3.2). The W4.2 gate records the tool list; the
> variant below that matches it is the one implemented. The choice is
> recorded in `chat-web5/`.

---

## 1. Content — Variant A (if the verified table ships curl/wget/openssh
or similar network tools)

- **Goal box:** fetch from the internet in Term; save a file; talk to a
  server over SSH — with the tools *this* repo provides.
- **Steps:**
  1. Install the network package(s) from the verified list (Packages tab /
     `pkg install …` — exact names from W4.2).
  2. `curl <url>` — print a page to the terminal; `curl -o file <url>` —
     save it; `curl -I <url>` — read the headers (three blocks, expected
     output).
  3. `wget <url>` (if shipped) — the other classic, in two lines.
  4. **SSH** (if `openssh` shipped): generate a key, add it to a GitHub
     account (two lines + link to GitHub's docs as "go deeper"),
     `ssh -T git@github.com` to verify; clone over ssh; one caveat:
     phone sleep kills idle connections (keep the screen on / Term
     foregrounded).
  5. **What the network is doing for you already** — git clone/push (ch-11)
     and the web preview's loopback server (ch-14) both ride your phone's
     connection; `127.0.0.1` is the phone itself, not the internet.
- **Try it:** fetch a known small URL and save it; `curl -I` the site
  itself (`https://pabi277.github.io/CodeC/` once live — or the repo's
  releases page in the meantime) and name two header lines; ssh-verify to
  GitHub if the tool ships.
- **Mistakes:** expecting a desktop-grade proxy setup (this is a curated
  repo — if a tool isn't here, it isn't taught); idle SSH sessions dying
  (phone sleep — expected, not a bug); DNS "failures" on metered/locked
  networks (the network, not the app).

## 2. Content — Variant B (if the verified table ships no dedicated
network tools)

- **Goal box:** understand *what* networking the phone-side CodeC does
  today — honestly — and use what's there.
- **Steps:**
  1. **The honest inventory** — from the W4.2 verified list, the
     network-capable things that ship: git (over https, chapter 11), the
     web preview's **loopback server** (`http://127.0.0.1:<port>` — the
     phone itself; chapter 14), and whatever else the table says. One
     table: tool · what network it uses · why you'd use it.
  2. **What 127.0.0.1 means** — the loopback: the web preview's server
     binds to the phone itself, so relative CSS/JS and `fetch("data.json")`
     work inside the app without a public server (two paragraphs — this is
     the concept ch-14 builds on).
  3. **How to check for yourself** — `pkg status` / the Packages tab: how
     to verify what *your* build ships (the repo grows; the site's package
     page is the current truth, linked).
  4. **Where this is heading** — the package repo is curated and
     verifiable; network tools land there first (link `/packages` + Issues
     for requests). Roadmap-honest, not a promise with a date.
- **Try it:** run the web preview on a 3-file page and open
  `127.0.0.1:<port>` thinking from the terminal side (identify the port in
  the preview header); `pkg status` and list three installed packages;
  open `/packages` and compare with what your phone shows.
- **Mistakes:** assuming Termux's network tools exist here (they don't —
  different repo); typing `curl` out of muscle memory (not installed — the
  tab will tell you); treating a slow fetch as a CodeC bug (it's the
  network).

## 3. Implementation steps

1. Read W4.2's verified table → **choose variant A or B**; record the
   choice + the exact tool list in `chat-web5/`.
2. Build `ch-12.html` (crumb "Chapter 12 of 17") with the chosen variant.
3. Self-dependent sweep.

## 4. Exit condition

```text
1. Variant choice + tool list recorded in chat-web5/ (one line each).
2. Template complete; prev → ch-11, next → ch-13.
3. Every tool named == W4.2 verified table (no unshipped tool appears);
   360/1440 clean; sweep PASS.
```
