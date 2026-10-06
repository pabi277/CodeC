# CodeC Website Phase W4.1 — Course home (`/learn`)

> **Binding v2.3 amendment — 2026-10-07 (scope supplied 2026-10-06).**
> Apply [WEBSITE_PLAN.md](../WEBSITE_PLAN.md) and the current instructions below
> before the retained prior design. They supersede conflicting old facts and exits:
> **29 pages / 9 product / 19 chapters**; app-v1.3.18 **7 217 532 B, versionCode 22**;
> AI in header, Privacy every footer; contacts in-app only; README then AI/privacy
> guides; Phases 1–96 shipped except cancelled proposals, 97+ roadmap. Never pair
> current APK size with historical -74%. No picker, Open Folder, ProjectLink,
> first-hour tiles, mandatory tour or automatic userland setup in visitor teaching.
> Earlier session-folder references are historical: preserve existing records;
> W1 uses `chat-web4/W1_*`. No next phase, PR, merge or deploy without command.

**Current status:** PLANNED; not authorized by the W1 command.

## Current v2.3 implementation requirements

Run W4.2 first. Chapter table has 19 rows, ending Ask about your code and Agent,
tools and approvals. All links use ch-NN.html. Explain current Arcade intro,
optional Linux setup, hands-on examples and expected results. O7 license remains
pending; don't invent a different license. 19-chapter scope was already chosen
by owner; only verified teaching details are resolved at the gate.

## Current acceptance additions (binding)

1. Follow the current requirements above and the master plan, not superseded UI/copy.
2. Preserve each non-conflicting numbered exit below, including device gates.
3. Record source file/line/sha, responsive checks and self-dependent sweep; use 19-chapter crumbs where applicable.
4. No app/workflow changes or phase expansion; update web living docs and stop at merge gate.

---

## Prior design detail — retained v2.2 record

**Historical reference, not authority for superseded facts or exit counts.**

> **v2.2 update (2026-09-12):** synced with app Phases 21–43 — universal APK 6.6 MB signed SHA256, Auto engine only picker deleted Termux card deleted fallback automatic error-path Output Panel, >_ mark icon, official file icons Seti MIT, typing feel, terminal multi-session + LAN server opt-in 0.0.0.0 two URLs QR ZXing open-in-browser keep-alive, outputs temporary RunArtifacts+RepoHygiene ~60 patterns .codec/ user .gitignore wins, GitHub truth GitReadiness+PushOutcome+Publish POST /user/repos, feedback hardcoded +91 62967 46606 / email + FeedbackScreen + exit survey + crash-log header-first + OpenInBrowser no telemetry, backup include-list-only FullBackupContent law crash-loop guard safe mode 3rd launch export-all over both roots 11 privacy rows RELEASE_NOTES template {{SHA256_LINES}}, safe walk Throwable-safe TreeWalkPolicy ProjectLink persisted SAF grant noexec mirror, BETA B-1…B-8, TCC null on armeabi-v7a/x86.

**Status:** 📋 **PLANNED** · **Cost:** `[static]` · **Effort:** S
· **Depends on:** W1 + W4.2 (the locked chapter table)
· **Target file:** `website/learn.html`

> Structure mirror: the owner's Termux-Mastery course home (about / why /
> how to use / course-structure table / roadmap / disclaimer / license).

---

## 1. Design — page structure

1. **Hero:** "Master CodeC from Zero to Advanced." + subline: "A complete,
   beginner-friendly, hands-on course — learn the terminal, C, packages,
   git, and real projects, directly on your Android phone, free & open
   source." (Termux-Mastery's "Free & Open Source · 15 Chapters ·
   Hands-On Projects" pattern → here: **Free & Open Source · 17 Chapters ·
   Hands-On Projects** — count from W4.2).
2. **About this course** — structured, text-based guide for complete
   beginners (no command line, no C); read like a book, chapter by chapter;
   every step is typed into the CodeC app on your phone; each chapter
   builds on the previous ones.
3. **Why learn with CodeC?** — compiler + terminal + signed packages + web
   preview in one app; offline; no computer required; the course is the
   app's own on-ramp, not a generic Linux tutorial.
4. **What you need** — an Android phone (arm64 best), the CodeC APK
   (→ `/install`), 10–20 minutes per chapter. That's it.
5. **Course structure** — `.table` with **all locked chapters** from W4.2:
   # · Title · "You will be able to…". Every row links to its chapter file.
6. **Learning roadmap** — short inline section (mirrors Termux-Mastery's
   roadmap link but self-contained): zero → terminal & C basics (01–08) →
   languages & tools (09–12) → device, web & power (13–15) → projects &
   survival (16–17).
7. **Disclaimer** — educational and utility purposes only; no hacking,
   exploitation or attack-oriented content; use responsibly (mirrors
   Termux-Mastery's disclaimer in spirit, reworded for CodeC).
8. **License** — "Course content licensed under <repo license unless O7
   answered>" (record assumption in `chat-web4/`).
9. **Start button** — "Begin Chapter 01" → `ch-01.html`.

### Meta: title "Learn CodeC — from zero to advanced".

## 2. Implementation steps

1. Build the page (active nav: Learn) using the W4.2 chapter table.
2. Source lines + the O7 license assumption recorded in `chat-web4/`.
3. Self-dependent sweep (plan §5.5).

## 3. Exit condition

```text
1. Chapter table rows == locked set from W4.2 (diff recorded); every row
   links to a chapter file that will exist by W6 (final URLs).
2. All 9 sections present; 360/1440 clean; "Begin Chapter 01" → ch-01.html.
3. Disclaimer + license present; sweep PASS.
```
