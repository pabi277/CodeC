# CodeC Website Phase W5.1 — Chapter 07: The Editor

> **Owner rounds1–3: required device checks PASSED.**
> [Evidence](../chat-web6/DEVICE_ROUND_1.md): Chapter8/P1/P5 including audible speech;
> Android16 / aarch64, CodeC “latest” (exact build unspecified). No retest needed.
> Review/license and explicit PR/merge/deploy authorization are separate.

> **Current batch status — 2026-10-07, D33: CONTENT IMPLEMENTED; REQUIRED DEVICE CHECKS PASSED.**
> Owner authorized W3–W6 content together, then one commit/watch; separate start
> commands/intermediate commits below are superseded. W4.2 verification ran FIRST.
> [Batch record](../chat-web6/BATCH_SUMMARY.md) · [checks](../chat-web6/CHECKS.md) ·
> [device gates](../chat-web6/DEVICE_TESTS.md) · [current full ZIP](../chat-web6/review-zips/CodeC-website-W6-review.zip).
> D31 download law retained. Device pass recorded above; no PR, merge or deployment implied.

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

**Current status:** CONTENT IMPLEMENTED; REQUIRED DEVICE CHECKS PASSED; owner-authorized combined batch (D33).

## Current v2.3 implementation requirements

Verify current editor typing behavior against README and Phase 75 acceptance,
not old UX promises alone. Include official Seti file icons, ghost TAB ▸,
suggestion strip, 29 MIT snippet packs, Emmet, TextMate Dark+, autosave and
optional CodeC Keys (system keyboard default). Crumb Chapter 7 of 19.

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
· **Depends on:** W4
· **Target file:** `website/ch-07.html`

> Source: `README.md` — "Editor foundation" + "Spck-style editor" bullets;
> W4.2 verified facts (exact UI names, autosave delay).

---

## 1. Content

- **Goal box:** edit like the editor was made for it — tabs, undo, find &
  replace, format, error squiggles, the extra-keys row.
- **Need:** Chapters 02, 06 done.

### Steps

1. **Tabs are your open files** — open three files in a project; the tabs
   in the app bar; the **dirty dot** means unsaved (autosave saves ~2 s
   after you stop typing — the dot is a *preview*, not an alarm); close
   tabs; save-all; per-tab undo/redo (undo groups your typing bursts).
2. **Find & replace** — open find; literal search with highlights; the
   regex toggle with one real example (`\bint\b` → find every `int` word);
   replace-all; close, and the editor is fast again.
3. **Format** — the Format action: `clang-format` bridge when the clang
   module is present, built-in C indenter fallback otherwise (W4.2 facts);
   format a deliberately messy C snippet and watch it straighten; one-tap
   undo reverts the whole format.
4. **The extra-keys row** — above the keyboard: ESC, TAB, CTRL, ALT,
   arrows; custom macros in Settings (preview of chapter 15); why it
   matters for C (TAB for indentation, ESC for the terminal).
5. **Errors you can see** — compiler-error squiggles under the exact line;
   tap the squiggle to inspect; the missing-`;` quick fix; the Ln/Col
   status bar so you can quote a location in a bug report.
6. **Zoom & select** — 60 fps pinch-to-zoom on long lines; long-press
   selection with word-boundary detection and the copy/paste contextual
   menu.

- **Try it:** (1) write a 10-line C file with deliberate indentation chaos,
  Format it, undo it once, re-Format; (2) use regex find to count every
  `printf(` in the file; (3) break a semicolon, read the squiggle, apply
  the quick fix, recompile.
- **Mistakes:** closing a tab with the dirty dot (autosave already covered
  it ~2 s ago — but save-all before big operations is a good habit); regex
  search with a literal `.` (escape it: `\.out`); expecting desktop
  keyboard shortcuts that aren't on the extra-keys row (they're in
  Settings as macros — chapter 15).

## 2. Implementation steps

1. Build `ch-07.html` (crumb "Chapter 7 of 17").
2. UI names from W4.2 facts; source notes in `chat-web5/`.
3. Self-dependent sweep.

## 3. Exit condition

```text
1. Template complete; prev → ch-06, next → ch-08.
2. Every editor feature named matches README/W4.2 (spot-check noted).
3. 360/1440 clean; sweep PASS.
```
