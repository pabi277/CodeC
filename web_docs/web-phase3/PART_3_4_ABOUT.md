# CodeC Website Phase W3.4 — About CodeC (`/about`)

**Status:** 📋 **PLANNED** · **Cost:** `[static]` · **Effort:** S
· **Depends on:** W1
· **Target file:** `website/about.html`

> Source: `README.md` (whole), `docs/JOURNEY.md` (story),
> `docs/TERMINAL_PLAN.md` + `docs/chat-phase3/` (engineering facts).

---

## 1. Design — page structure

1. **H1:** "CodeC is a C programming IDE for Android." One section: what it
   is (write C in projects or single files, tap RUN, or type `cc file.c` in
   the in-app terminal) and who it's for (learners, tinkerers, people who
   want a terminal + compiler + packages on a phone with zero setup).
2. **Feature tour** (short bullets, each linking the relevant page):
   - Spck-grade editor: file tree with git status letters, tabs, extra-keys
     row, autosave, error squiggles (→ `/start`, chapter 07).
   - Projects hub: card list, `+` sheet (New / Clone / Import ZIP / Open
     Folder) (→ chapter 06).
   - Honest git: staged per-file, conflicts block commits, failed push never
     looks successful, unpushed badge (→ chapter 11).
   - Web preview: loopback server over the project folder, live reload,
     console (→ `/start`, chapter 14).
   - Device APIs (CodeCApi): battery, sensors, TTS, camera, intents —
     CodeC's answer to Termux:API (→ chapter 13).
3. **The story** — built in public: phases 0 through 19+ device-accepted
   and merged, from a first compiler round to a signed package
   repository; link `docs/JOURNEY.md` as the full timeline. No ego, no
   metrics fluff.
4. **Engineering facts** (what makes it different): static musl TCC
   toolchain embedded in the APK (arm64 + x86_64); signed package
   repository (never `trusted=yes`); CI-built APK (GitHub Actions, no
   store); clean-room approach — features replicated from public behavior
   and specs, code never copied (state the law plainly).
5. **Links block:** Repo · README · JOURNEY · Releases · Issues ·
   Package repo.

### Meta: title "About CodeC".

## 2. Implementation steps

1. Build the page (active nav: About).
2. Source line per section recorded in `chat-web3/`; JOURNEY link points at
   the file in the repo (stable URL).
3. Self-dependent sweep (plan §5.5).

## 3. Exit condition

```text
1. Render clean at 360/1440; all 5 sections present in order.
2. Every feature claim matches README (spot-check recorded in chat-web3/);
   JOURNEY/TERMINAL_PLAN links resolve.
3. Clean-room paragraph present and accurate (mirrors rule.md §6 wording in
   spirit).
4. Sweep PASS. W3 total: product wing 7/7 pages → report + merge gate.
```
