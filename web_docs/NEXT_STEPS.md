# NEXT_STEPS.md — website head state

> **HEAD STATE (2026-09-12, v2.2): W0 (planning) COMPLETE — zero website
> code exists, ALL PHASES FULLY SPEC'D and SYNCED with app Phases 21–43.**
> The website is spec'd in `WEBSITE_PLAN.md` v2.2: **two wings** — product site (7 pages) +
> **learning wing** ("Master CodeC from Zero to Advanced", 17 chapters,
> modelled on the owner's Termux-Mastery), **fully self-dependent** (zero
> external resources, offline-complete, course completable without the
> repo). Stack: static HTML/CSS, GitHub Pages. **Phases W1–W6 are fully
> spec'd** in `web-phase1/` … `web-phase6/` (35 docs with exit conditions
> per part; W4.2 verification gate runs first in W4; device passes due in
> W5 ch-08 and W6 P1+P5; W6.7 verifies before the site goes live).
> **Sync v2.2 (2026-09-12):** app Phases 34–43 researched:
> - 34 official file icons (Seti MIT)
> - 35 editor typing feel (keyboard stay open, smooth typing, non-blinky caret, no cursor on open)
> - 36 terminal speed & feel (multi-session, session switcher, progressive apt)
> - 37 device as server LAN (opt-in 0.0.0.0, two URLs, QR ZXing Apache-2.0, keep-alive foreground service, open in browser)
> - 38 identity (>_ mark adaptive+monochrome+PNGs, ic_stat_codec notification, Settings 13→11, Termux card deleted, guidance moved to Output Panel error path)
> - 39 outputs temporary (RunArtifacts temp/runs/<stamp>/ + TempGc, RepoHygiene ~60 patterns incl .codec/ enforced in stageAll, user's .gitignore wins)
> - 40 GitHub truth (GitReadiness pre-flight, inline clone error, PushOutcome/PushParser result card, Publish via POST /user/repos private by default)
> - 41 feedback (hardcoded DeveloperContact +91 62967 46606 / chakraborttypabi2772006@gmail.com, FeedbackScreen, exit survey "Enjoying CodeC? 💚", crash-log.txt header-first, OpenInBrowser policy, no telemetry)
> - 42 share-readiness (release channel app-v* tags only, universal APK 6.6 MB -74% via R8+shrink, per-ABI splits measured <2% and reverted because assets/tcc not filtered, okhttp removed, backup include-list-only FullBackupContent law, crash-loop guard safe mode 3rd launch, export-all ZIP, 11 privacy rows, RELEASE_NOTES template with SHA256)
> - 43 planned (safe folder walk Throwable-safe TreeWalkPolicy, ProjectLink + persisted SAF grant, noexec mirror)
> plus earlier Phase 21 Auto engine only (picker deleted), Phase 22–33 (smoothness, IME keys, insets, inline input, run keys, desktop-class editor quality, sora 0.24.6, ghost text, suggestion strip, TextMate Dark+, 29 snippet packs MIT + Emmet, 50 items, LSP as Packages, phone canvas, RUN chooser, first-hour tiles).
> **Install facts updated:** universal APK `CodeC-IDE-<version>-universal.apk` only, signed non-debuggable, SHA256 lines in release notes, updater looks only at app-v* releases, compares numerically, verifies SHA256, refuses downgrades, opens Releases page when no checksum; debug artifacts `CodeC-IDE-debug`/`release` for branch builds; debug→release = fresh install, export first; device support arm64 best, x86_64 emulator via built-in TCC, 32-bit built-in TCC null → Clang module or Termux fallback.
> **Engines facts updated:** Auto only, no picker, Termux card deleted, fallback automatic, four setup steps appear in Output Panel only when build fails with Permission denied (CompilerRemediation).
> **Repo facts updated:** original >_ mark docs/icon/codec-512.png, Settings trim, official file icons, typing feel, LAN server, outputs temporary, GitHub truth, feedback hardcoded, export-all, backup include-list, crash-loop guard, safe folder walk planned.
> Nothing is built, nothing is deployed, there is no `website/` folder.
> **App head:** Phase 42 COMPLETE & MERGED to `main` via PR #71 (app-v1.3.17 published), Phase 43 PLANNED. Website awaits owner's implementation command.

## What the owner says next

| Owner says in chat | Agent does |
|---|---|
| **"Build the website"** / **"Start W1"** | Begin W1 strictly per `web_docs/web-phase1/` (README + PART_1_1 + PART_1_2) updated to v2.2: create `website/`, shared chrome (incl. Learn nav item + >_ mark), stylesheet, Home page with learning banner + updated cards (file icons, LAN, safe features); self-dependent rule (§5) in force from first file; record in `web_docs/chat-web2/`; update living docs; commit + push; report; stop at merge gate. |
| **"Start W2"** … **"Start W6"** | Execute that phase strictly per its `web_docs/web-phaseN/` folder — one phase at a time, in order (W4.2 verification gate runs first inside W4 and must re-verify v2.2 facts: icon, backup rules, export-all, LAN, feedback, outputs temporary, GitHub truth, safe walk, universal APK size); phase records in `chat-webN+1/`; device passes (W5 ch-08, W6 P1+P5) reported as **device pass required** with owner's transcript. W2.1 install and W3.1 engines specs now reflect Phase 42.1 + 21/38.2. |
| "Change page X / the design / the stack" | Update `WEBSITE_PLAN.md` + affected `web-phaseN/` part docs + `DECISIONS.md` in same commit — still no code until implementation is commanded. |
| (answers O1–O8) | Record answer in `DECISIONS.md` (close open item), adjust plan/part docs if needed. O8 new: feedback contact display on website? |
| "Create PR and merge" | Open/merge PR for current session branch per `rule.md` §3 — **only on this exact class of command.** |

## Phase queue (implementation — not started, but specs refreshed to v2.2)

- [ ] **W1** — scaffold + Home (updated cards: file icons, LAN, safe features, >_ mark) · spec: [`web-phase1/`](web-phase1/README.md)
- [ ] **W2** — Install (universal APK 6.6 MB, SHA256, updater version guard, debug vs release, export-all note, Termux fallback automatic) + Getting Started (first-hour tiles, open into last file) · spec: [`web-phase2/`](web-phase2/README.md)
- [ ] **W3** — Engines (Auto only, picker deleted, Termux card deleted, fallback automatic, CHECK BRIDGE now error-path) + Packages + FAQ (BETA B-1…B-8 + backup + crash-loop + export-all + debug/release + 32-bit null) + About ( >_ icon + Settings trim + feedback hardcoded + LAN + file icons + outputs temporary + GitHub truth + backup + crash-loop + export-all) · spec: [`web-phase3/`](web-phase3/README.md)
- [ ] **W4** — verification gate v2.2 (re-verify README + package config + new facts: icon, backup rules, export-all, LAN, feedback, outputs temporary, GitHub truth, safe walk, universal APK) → course home + ch 01–06 (ch-04 Auto only, ch-06 safe walk + ProjectLink + export-all) · spec: [`web-phase4/`](web-phase4/README.md)
- [ ] **W5** — ch 07–12 (Editor with file icons + typing feel + ghost + strip + snippets 29 packs + Emmet + TextMate + 50 items, C Basics TCC-safe, Scripting, Python, Git with readiness + push truth + publish, Networking + LAN server QR + open in browser) · spec: [`web-phase5/`](web-phase5/README.md) · device pass: ch-08
- [ ] **W6** — ch 13–17 (Device APIs 8 scripts, Web Projects + LAN, Custom/Advanced with export-all + backup + crash-loop + feedback, Real Projects with LAN share, Troubleshooting with BETA + crash-log + feedback) + polish + deploy + verification · spec: [`web-phase6/`](web-phase6/README.md) · device pass: P1+P5

## Verification before any implementation session

1. `git status` — on an `arena/*` session branch.
2. `web_docs/` and `web_prompt.md` exist and match this head state (v2.2 as of 2026-09-12; if newer session doc says otherwise, trust newest dated entry in `WEB_JOURNEY.md`).
3. `README.md` + `docs/BETA.md` + `docs/RELEASE_NOTES.md` + `docs/TROUBLESHOOTING.md` re-read before writing any page — content rules §4 bind (v2.2 facts).

## History pointer

Full narrative: [`WEB_JOURNEY.md`](WEB_JOURNEY.md). Session records:
`chat-web1/` (W0 planning, 2026-09-02) + `chat-web2/` will be W1 scaffold.
Sync record: W0.3 (2026-09-12) — Phases 21–43.
