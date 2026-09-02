# CodeC Website Phase W4.1 — Course home (`/learn`)

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
