# CodeC Website Phase W5.5 — Chapter 11: Git & GitHub

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
