# CodeC Website Phase W3.2 — Package hub & repository (`/packages`)

**Status:** 📋 **PLANNED** · **Cost:** `[static]` · **Effort:** S
· **Depends on:** W1
· **Target file:** `website/packages.html`

> Source: `README.md` — "Package & Command Hub (Packages tab)" + "In-app
> terminal & Package Manager (Mini-Termux)" repo paragraphs;
> `docs/chat-phase3/` for signing depth; **the repo's own build config in
> `codec-packages/` for the authoritative package list.**

---

## 1. Design — page structure

1. **H1:** "A small, signed package universe — verified, not bloated."
   One paragraph: `pkg` is a guarded, CodeC-only frontend over the signed
   apt/dpkg repository; 25+ packages; nothing here is official Termux
   software.
2. **The package table** (`.table`) — pulled **from the repo build config**
   at implementation time (never guessed from memory): name · what it gives
   you · typical use. The README names at least `git`, `python`, `clang`,
   `nano`, `make`, `ripgrep`, `tmux`; the table lists **every** package the
   config ships, with the count stated as "N packages" (N from the config) —
   the recorded list is also written into `chat-web3/` (feeds W4.2).
3. **How it works** (three short blocks):
   - **Signed repository** — `https://pabi277.github.io/CodeC/dev`;
     metadata stays signed (`signed-by=`, never `trusted=yes`); the
     bootstrap `userland-v2-dev` is SHA-256 verified, staged, atomic
     (depth link → `docs/chat-phase3/REPOSITORY_SIGNING.md`).
   - **1-tap from the Packages tab** — INSTALL / RUN buttons, live status
     badges (`INSTALLED ✓` / `AVAILABLE`), quick system actions (`pkg
     update`, `pkg upgrade -y`, `codec-setup-storage`, `pkg status`,
     `pkg heal`, `pkg repair`), interactive command runner.
   - **Guarded** — only CodeC's own repo, guarded `pkg`; never official
     `com.termux` packages or repositories (invariant, stated plainly).
4. **Honest scope box:** "This is a small, curated, verified repository —
   not the full Termux package universe. If a package isn't here, it isn't
   here (yet) — report it on GitHub Issues."

### Meta: title "Packages — CodeC package hub".

## 2. Implementation steps

1. Read `codec-packages/` build config; extract the package list (record
   file + commit sha in `chat-web3/`).
2. Build the page (active nav: Packages) with the full table.
3. Self-dependent sweep (plan §5.5); verify the repo URL resolves (200).

## 3. Exit condition

```text
1. Table = config list, one-for-one (recorded in chat-web3/ with sha);
   count stated matches.
2. Signing/bootstrap facts match README + docs/chat-phase3 (source lines
   recorded); signed-by wording exact.
3. Repo URL + all internal links resolve (internal targets per phase
   plan); sweep PASS.
```
