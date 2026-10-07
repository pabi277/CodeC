# CodeC Website Phase W5.5 — Chapter 11: Git & GitHub

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

Teach GitReadiness, inline clone error, local commit versus successful remote
push, truthful PushOutcome and publish via POST /user/repos private by default.
No token in screenshots/examples, no success assumed after failed push. Outputs
temporary, user's .gitignore wins. Chapter 11 of 19.

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
· **Depends on:** W4 (W4.2 facts: the Phase-13 in-app GitHub UI exists —
  Settings → GitHub Account, Clone from GitHub, Source Control pane,
  COMMIT & PUSH)
· **Target file:** `website/ch-11.html`

> Source: `README.md` — "Honest git", "Switch Branch", Projects hub (↑N
> badge), Phase 13 GitHub UI bullets.

---

## 1. Content

- **Goal box:** connect GitHub in the app; clone a repo; commit and push
  from your phone; read honest-git signals; survive a branch switch.
- **Need:** Chapters 06 (projects), 09 (comfortable in Term) done.

### Steps

1. **Connect GitHub** — Settings → **GitHub Account** (the exact path from
   W4.2 facts); what the app does with it (clone, push — no secrets typed
   by hand in scripts).
2. **Clone a repo** — Files → ⋮ → **Clone from GitHub** (or Projects `+`
   sheet → Clone Git Repo): URL, where it lands (a project appears in the
   hub with the branch · files · age card).
3. **The Source Control pane** — the git-status letters in the tree; the
   per-file **stage toggle**; what staged vs unstaged means in one
   paragraph (colored, not lectured).
4. **Commit & push** — the **COMMIT & PUSH** flow: message, commit; then
   the two honest outcomes — success (badge updates) or
   **"Committed locally ✓ — NOT pushed: …"** with a **PUSH** retry button.
   Teach the rule: *a failed push never looks like a successful one.*
   The **↑N** badge on the project card = N commits that never reached the
   remote.
5. **Branches** — **Switch Branch**: the list (local + remote + New
   branch…); the promise: dirty work is **stashed and restored** when you
   come back; a branch with no upstream is published on first push
   (`--set-upstream` happens for you).
6. **Conflicts, honestly** — a real conflict groups its files in **purple**
   with **Mark Resolved** and **blocks the commit**; resolve in the editor,
   mark resolved, commit. (Two-branch toy scenario, step by step.)
7. **What the ignore does for you** — build outputs stay out of git via the
   repo-local ignore; **your `.gitignore` is never touched** (restated from
   chapter 06 with the git framing).

- **Try it:** (1) clone a small public repo, edit one file, stage it,
  commit, push — watch the card's badge move; (2) create a branch
  "experiment", dirty a file, switch away, switch back — the file's edits
  are back (the stash promise); (3) push with the phone offline (or a
  revoked token) and read the NOT-pushed sheet — then PUSH-retry.
- **Mistakes:** expecting the Play-Store-style "sync" (there is none —
  commit + push is deliberate); a branch that "disappears" (it's a remote
  branch you didn't check out — it's in the list); conflicts looking scary
  (purple = the app doing its job, blocking a broken commit).

## 2. Implementation steps

1. Build `ch-11.html` (crumb "Chapter 11 of 17").
2. UI names/phrases verbatim from W4.2 facts (especially the NOT-pushed
   sheet wording); source notes in `chat-web5/`.
3. Self-dependent sweep.

## 3. Exit condition

```text
1. Template complete; prev → ch-10, next → ch-12.
2. Every UI phrase == README/W4.2 wording (diff noted in chat-web5/).
3. 360/1440 clean; sweep PASS.
```
