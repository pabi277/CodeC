# CodeC Website Phase W3.2 — Package hub & repository (`/packages`)

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

Extract package inventory from actual codec-packages build config at writing
time, name sha/date and real count; verify executables used by chapters. README
examples alone do not prove a full list. Preserve signed metadata, CodeC-only
packages and atomic bootstrap. Userland is optional, not a first-launch gate.
Feed the exact table to W4.2; don't run a package build or alter deployment.

## Current acceptance additions (binding)

1. Follow the current requirements above and the master plan, not superseded UI/copy.
2. Preserve each non-conflicting numbered exit below, including device gates.
3. Record source file/line/sha, responsive checks and self-dependent sweep; use 19-chapter crumbs where applicable.
4. No app/workflow changes or phase expansion; update web living docs and stop at merge gate.

---

## Prior design detail — retained v2.2 record

**Historical reference, not authority for superseded facts or exit counts.**

**Status:** 📋 **PLANNED** · **Cost:** `[static]` · **Effort:** S
· **Depends on:** W1
· **Target file:** `website/packages.html`

> Source: `README.md` — "Package & Command Hub (Packages tab)" + "In-app
> terminal & Package Manager (Mini-Termux)" repo paragraphs;
> `docs/phases/02-packages-toolchains/chat-phase3/` for signing depth; **the repo's own build config in
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
     (depth link → `docs/phases/02-packages-toolchains/chat-phase3/REPOSITORY_SIGNING.md`).
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
2. Signing/bootstrap facts match README + docs/phases/02-packages-toolchains/chat-phase3 (source lines
   recorded); signed-by wording exact.
3. Repo URL + all internal links resolve (internal targets per phase
   plan); sweep PASS.
```
