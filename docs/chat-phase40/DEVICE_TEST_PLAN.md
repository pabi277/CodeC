# CodeC Phase 40 — device test plan (owner runbook)

> **Branch:** `arena/01a08c04-codec` · **Functional round:** `e623a16`
> (impl `29e175e`) · **Colour round (40.5):** `8648314` · **CI ✅ GREEN:**
> `Build APK` `34499964179`, `34500719045`, `34509510002` ·
> **Status:** ✅ **checks 1–8 PASSED** on the owner's device (2026-09-10);
> **checks C1–C3 (colour look-over) are new in 40.5 — run them with the same
> APK** (Actions → latest green run on this branch → `CodeC-IDE`).
>
> Every check below has a **PASS looks like** line with the exact on-screen
> text. If a line differs, that is the bug report — copy the text verbatim and
> say which check number it was.

## 0. Before you start (5 minutes)

1. **Get the APK** — GitHub → **Actions** → `Build APK` run **`34500719045`**
   (or any green run on this branch) → **Artifacts** → **`CodeC-IDE`** →
   unzip → `app-debug.apk` → install (allow *Install unknown apps*).
   *Do not use Settings → Install APK from GitHub for this round* — it reads
   `releases/latest`, which today resolves to the `userland-v1` release, not
   this build (that mismatch is Phase 42's job).
2. **Install git** — **Packages** tab → **Git** → install. (CodeC → `pkg
   install git` in the terminal does the same thing.)
3. **Token** — **Settings → GitHub Account** → paste a token and save.
   - For **push**: a fine-grained token with **Contents → Read and write**, or
     a classic token with `repo`.
   - For **Publish** (check 6): the token must be allowed to **create
     repositories** — fine-grained tokens need *Administration: write* and
     *All repositories* scope. A token that cannot is fine: check 6b tests the
     browser fallback instead.
4. **Safety** — publish creates a **real** GitHub repository (private by
   default). Use a project you don't mind uploading, and skip the checks that
   need a token if you'd rather not create anything today. Nothing in this
   round deletes or renames anything.

## 1. The original bug — clone failure must be visible *in the dialog*

**Steps:** uninstall `git` (Packages → Git → uninstall) → **Files** → **⋮** →
**Clone from GitHub** → paste `https://github.com/pabi277/CodeC.git` → **Clone**.

**PASS looks like:** the dialog **stays open** and a **red line appears inside
it**: `Clone failed: git is not installed. Install it from the Modules tab (Git)
or run “pkg install -y git” in the terminal, then come back.` The user never
has to look behind the dialog (this was the owner's *"error in the background i
can't see it"*).

**Also in this check:** tap outside the dialog while a clone is running → the
dialog must **not** close (it is non-dismissable while busy), and the **Clone**
button is greyed out.

**Then:** reinstall git, repeat the clone → dialog closes, project opens,
snackbar says `Cloned CodeC`. (This is also the regression check for cloning.)

## 2. Readiness row — no remote yet

**Steps:** **Files** → **+** → new project (any type) → open its **⋮** →
**Source Control**. (Or the editor drawer → **Source Control**.)

**PASS looks like (token saved, no remote):** an amber row at the top of the
sheet:

```text
!  This project has no GitHub remote yet, so there is nowhere to push.
   Tap Publish to create the repository.                    [ PUBLISH ]
```

**With no token saved**, the same row must instead say `No GitHub token is
connected. Add one in Settings → GitHub Account …` with a tappable
**Create a GitHub token ↗** link, and **no** PUBLISH button (token comes first
— that ordering is deliberate).

**With a token and a remote** (e.g. the project cloned in check 1):
`✓ GitHub ready`, no PUBLISH button.

## 3. Push truth — what actually reached GitHub

**Steps:** in the cloned project, edit any file, then Source Control → type a
message → **COMMIT & PUSH**.

**PASS looks like:** message line `Committed & pushed to main ✓` **and** a
result card under it:

```text
✓  Pushed main → github.com/pabi277/CodeC
   3f1a2b4..9c8d7e6
Dismiss
```

Open github.com on any browser: the commit must be there, on that branch.

**A new branch:** **Switch Branch** → create `test-1` → edit a file → COMMIT &
PUSH. The card must say `✓ Pushed test-1 → github.com/…` and the second line
`new branch on GitHub`; GitHub must show a `test-1` branch.

**The "still local" case:** put the phone in **airplane mode**, edit a file,
COMMIT & PUSH. **PASS looks like:** `Committed locally ✓ — NOT pushed: …` plus
a red card with the reason, and — importantly — **no green tick anywhere**.
Turn data back on → **PUSH** → card turns green, GitHub shows the commit.

**Dismiss:** tap **Dismiss** under the card → it disappears. Then **REFRESH** →
it must stay gone (an explicit refresh clears the card).

## 4. Rejection is named, not silent (the sharpest parser test)

**Steps:** on github.com, edit the same file in the browser and commit it there
(so GitHub is ahead). On the phone, edit a file → COMMIT & PUSH.

**PASS looks like:** `✗ Push rejected — GitHub has commits this device does not
have. Pull first, then push again.` (git's non-fast-forward `! [rejected]` line
was parsed). Then **PULL** → **PUSH** → green again. If instead you see
`✗ The push did not succeed.` with a raw git line under it, that is a real bug —
send me that line verbatim.

## 5. Missing/bad token

**Steps:** Settings → GitHub Account → clear the token → Source Control →
**COMMIT & PUSH**.

**PASS looks like:** the commit still happens locally, and the message reads
`Committed locally ✓ — NOT pushed: GitHub rejected the token…` (or *No GitHub
token is connected…*) with a **Create a GitHub token ↗** link. The change list
clears, but nothing claims it reached GitHub.

## 6. Publish to GitHub (new in 40.3) — needs a repo-creating token

**6a. Create.** Source Control → **PUBLISH** (from the readiness row) →
dialog opens with the project's name filled in, **Private repository ON** →
**PUBLISH**.

**PASS looks like:** the dialog closes by itself; the sheet shows
`Published to https://github.com/<you>/<name> ✓` and a green card
`✓ Pushed main → github.com/<you>/<name>`. On github.com: the repository
exists, is **private**, and contains the commit. In the project's Source
Control, PULL/PUSH now work normally.

**6b. Token that cannot create repos.** Re-run 6a with a token lacking
*Administration: write*: **PASS looks like** an inline red line inside the
dialog — `Your token can't create repositories: …` — plus `GitHub asked for:
administration=write` when GitHub sent it, and an **Open github.com/new ↗**
link. Create the repo in the browser, paste its URL into
**“Already created it in the browser?”**, tap **ATTACH** → the remote is
attached only after `git ls-remote` confirms access, then the branch is pushed.

**6c. Name already taken.** Publish with a name you already own: **PASS looks
like** an inline error naming GitHub's own words (`… name already exists …`),
the dialog stays open, and changing the name and publishing again works.

## 7. Phase 40.5 — colour look-over (3 checks, same APK)

The functional round passed. These three are the visual half: they are what the
owner's report was about (*"Now it is violet 💜 but not very good to read"*).
Background and measurements: [PART_40_5](PART_40_5_COLOUR_REPAIR.md).

**C1 — the Accent Color row shows a colour, not a hex.** Settings → Appearance.
**PASS looks like:** the row reads **Accent Color**, then a round **violet
dot**, then the word **Violet**. Tapping it opens exactly six entries — **Violet,
Teal, Red, Blue, Orange, CodeC green** — each with its own dot. Pick **CodeC
green**: the row then shows a green dot and **CodeC green**, and the app's
accent-coloured controls (buttons, toggles, keyboard highlights) turn green.
*FAIL:* the row still shows a hex string like `#FF6200EE`, or the list has no
dots.

**C2 — dark theme, the original complaint.** Dark theme, a code file open, tap
into it so the keyboard and status bar are visible. **PASS looks like:** the
status bar reads `Ln 1, Col 1 · UTF-8 · Kotlin · Spaces: 4 · LF` — the **LF**
segment is as easy to read as **UTF-8**. The bottom bar's unselected labels
(Files, Editor, Terminal, Packages, Settings) are all equally legible. The small
corner hints on the keys (`q¹`, `;:`) are visible without leaning in, and a
held/special key is a lighter violet with a readable label. *FAIL:* anything
violet or grey that needs squinting.

**C3 — light theme.** Settings → App Theme → **Light**, then repeat C2's glance.
**PASS looks like:** the bottom-bar labels and the whole `Ln … · LF` line stay
readable on the pale strip (this is where 3.53:1 and 3.78:1 used to be), the
project tiles keep white letters/icons on their fills, and the Output panel
stays dark by design with readable hint text.

## 8. Not part of this round (don't chase these)

- `↑ Everything up-to-date — nothing was pushed` is covered by the host test
  suite; the PUSH row only appears when there is something to push, so it is
  hard to reach by hand.
- Rate-limit (`403`) and 5xx handling in Publish are host-tested; you'd need to
  trigger GitHub's limit to see them.
- The in-app APK updater still points at `releases/latest` (Phase 42).

## 9. What to send back

For each check: **number + PASS/FAIL + the exact on-screen text** (or a
screenshot of the sheet/dialog). Useful extras: phone model, Android version,
and whether the token was classic or fine-grained. If something fails, the
screenshot of the **whole Source Control sheet** is worth more than the line
alone — the readiness row and the result card are the two things this phase
added, so both being visible in one shot tells me most of the story.

> Reminder: CI is green and the branch is pushed, but **no PR/merge happens
> without your explicit command** (`rule.md` §3).
