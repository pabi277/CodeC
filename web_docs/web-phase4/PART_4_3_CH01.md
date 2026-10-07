# CodeC Website Phase W4.3 — Chapter 01: Getting Started

> **Current batch status — 2026-10-07, D33: IMPLEMENTED FOR REVIEW.**
> Owner authorized W3–W6 content together, then one commit/watch; separate start
> commands/intermediate commits below are superseded. W4.2 verification ran FIRST.
> [Batch record](../chat-web6/BATCH_SUMMARY.md) · [checks](../chat-web6/CHECKS.md) ·
> [device gates](../chat-web6/DEVICE_TESTS.md) · [current full ZIP](../chat-web6/review-zips/CodeC-website-W6-review.zip).
> D31 download law retained. No device pass, PR, merge or public deployment implied.

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

**Current status:** IMPLEMENTED FOR REVIEW; owner-authorized combined batch (D33).

## Current v2.3 implementation requirements

Current fresh install: intro/privacy acknowledgement → CodeC Arcade; returning
launch resumes last file. Install v1.3.18 via GitHub. Terminal commands require
explicit optional Linux setup, not assumed first-run userland. Teach tab map,
APK source, basic project access, backup/export. Crumb Chapter 1 of 19;
prev learn.html, next ch-02.html; no old first-hour tiles.

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
· **Depends on:** W4.2 gate (verified facts)
· **Target file:** `website/ch-01.html`

---

## 1. Content (chapter template, plan §3.2)

- **Goal box:** install CodeC on a fresh phone; open it; name all five tabs
  of the bottom bar; open the editor and the terminal once.
- **Need:** an Android phone (arm64 best), 10–20 minutes, a browser for the
  APK download.

### Steps

1. **Get the APK** — brief 3-path recap (GitHub Actions artifact / Release /
   in-app later), then: tap the file, allow *Install unknown apps*, open.
   (Details live on `/install` — linked, not duplicated beyond two lines.)
2. **First screen** — what opens (Projects hub on first launch), one
   paragraph, no fear: "Everything you'll ever need is in this bottom bar."
3. **The bottom bar map** — the five tabs, one line each: **Projects**
   (your work) · **Editor** (where files are written) · **Terminal**
   (middle — the real terminal) · **Packages** (install & run software) ·
   **Settings** (engines, keys, updates, logs).
4. **Touch the editor** — open the single-files sheet, create a file named
   `note.txt`, type one line, watch **autosave** (~2 s) do its job.
5. **Touch the terminal** — open Term (tab or the editor's terminal icon);
   read the prompt; type `ls` and `pwd`; read where you are (the `$PREFIX`
   home, per W4.2 facts).

- **Try it:** (1) open Packages tab and read one status badge aloud;
  (2) create `note.txt`, type "hello codec", switch tabs, switch back —
  it's still there; (3) in Term: `pwd` — find `files/usr` in the output.
- **Mistakes:** app won't open (reinstall once — Android's sandbox
  labeling); prompt looks different on your model (normal — what matters is
  the command, not the colors); tapping RUN before writing a file.

## 2. Implementation steps

1. Build `ch-01.html` on the canonical template; crumb "Chapter 1 of 17".
2. Facts taken from `VERIFIED_FACTS.md` (W4.2); source notes in
   `chat-web4/`.
3. Self-dependent sweep (plan §5.5).

## 3. Exit condition

```text
1. Template complete (crumb, goal box, 5 steps, 3 try-its, mistakes,
   prev/next — next → ch-02.html, prev → learn.html).
2. Every command (`ls`, `pwd`, file creation) works on a fresh install per
   verified facts.
3. 360/1440 clean; sweep PASS.
```
