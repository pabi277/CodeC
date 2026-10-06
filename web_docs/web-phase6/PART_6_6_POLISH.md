# CodeC Website Phase W6.6 — Polish pass (all 25 pages)

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

Depends on W6.8 AND W6.9 as well as W6.1–5. Polish all 29 pages, 19 chapter
crumbs, AI nav and Privacy footer. Check ch-17→18→19→course navigation; all
internal pages/anchors exist. Audit factual source drift, keyboard/contrast,
360/1440/zoom/reduced motion, banned privacy phrase, personal contacts, no picker/
Open Folder/old first-run tiles, no wrong APK size/-74% pairing. Course license
O7 and screenshot O1 status explicit. Full sweeps before W6.7.

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
· **Depends on:** W6.1–W6.5 (all pages exist)
· **Target files:** any/all of `website/*.html` + `style.css` (cosmetic +
  correctness edits only — no new sections, no new features)

---

## 1. Design — the pass, in order

1. **Content re-verification (first — not last):** re-read `README.md` and
   the W4.2 verified-facts table; sweep every page for drift (package
   count, engine table, install paths, CodeCApi facts). Drift → fix to the
   repo's current truth in the same commit; note each fix in
   `chat-web6/POLISH_LOG.md`.
2. **Meta consistency:** every page has a unique `<title>` + `description`
   (≤160 chars); the pattern is recorded (so future pages stay consistent).
3. **Navigation audit:** every internal link on every page resolves to an
   existing file (final URLs — no 404s left); every chapter's prev/next +
   crumb "Chapter N of 17" is correct; the course table on `/learn` and
   the chapter crumbs agree; the header active-nav state is right on all
   25 pages.
4. **Visual pass at 360 px and 1440 px** (all pages): overflow, table
   wrapping, code-block scrolling, contrast (WCAG AA spot-check on body
   text, muted text, accent-on-dark), focus visibility for the mobile nav
   toggle, footer consistency.
5. **Tone pass:** no marketing fluff slipped in; roadmap mentions stay at
   ≤1 line per page; "Termux" is only ever the *optional engine* (never
   a prerequisite framing); install copy never implies a store.
6. **Self-dependent re-sweep** (plan §5.5) after every edit — the polish
   phase is where stray external assets sneak in; final result recorded.

## 2. Implementation steps

1. Run the six sub-passes in order; keep a running `POLISH_LOG.md`
   (page · change · reason) in `chat-web6/`.
2. No new pages, no new CSS components (fixes use existing classes).
3. Commit at the end of the pass (one coherent "polish" commit is fine).

## 3. Exit condition

```text
1. POLISH_LOG.md committed; drift fixes (if any) verified against README.
2. 25/25 pages: unique meta, correct nav, no internal 404s (checked with
   the file list as ground truth).
3. Contrast spot-check recorded (pass/fail per token); overflow clean at
   both widths.
4. Final self-dependent sweep PASS (recorded) → ready for W6.7 deploy.
```
