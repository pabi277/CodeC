# CodeC Website Phase W4.6 — Chapter 04: Compiler Engines (Auto only)

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

Auto only; table conceptual, not selector. No Settings engine picker, Termux
card or CHECK BRIDGE. Fallback four steps appear when a build needs them.
Verify exact commands against current README and W4.2. Crumb Chapter 4 of 19;
prev ch-03, next ch-05.

## Current acceptance additions (binding)

1. Follow the current requirements above and the master plan, not superseded UI/copy.
2. Preserve each non-conflicting numbered exit below, including device gates.
3. Record source file/line/sha, responsive checks and self-dependent sweep; use 19-chapter crumbs where applicable.
4. No app/workflow changes or phase expansion; update web living docs and stop at merge gate.

---

## Prior design detail — retained v2.2 record

**Historical reference, not authority for superseded facts or exit counts.**

**Status:** 📋 **PLANNED** · **Cost:** `[static]` · **Effort:** S
· **Depends on:** W4.2 gate v2.2
· **Target file:** `website/ch-04.html`

> **v2.2 update (2026-09-12):** Phase 21 picker deleted, Phase 38.2 Termux card deleted, Auto is only mode, CHECK BRIDGE no longer in Settings, four setup steps appear in Output Panel only when build fails with Permission denied signature via CompilerRemediation. Table is conceptual not selectable. TCC null on armeabi-v7a/x86 by design.

---

## 1. Content

- **Goal box:** explain which engine compiled your last program; understand Auto fallback chain; know your device's limits; know where Termux guidance appears now (error path, not Settings).
- **Need:** Chapters 02–03 done (you've compiled and used Term).

### Steps

1. **You already used Auto** — chapter 02's RUN used **Auto** (the ONLY mode since Phase 21): built-in TCC first, fallbacks automatic. One paragraph: most of the time, Auto is the whole story — there is nothing to pick anymore (picker deleted Phase 21, Termux card deleted Phase 38.2). This chapter is for understanding, not switching.
2. **The four engines, in one table (conceptual, same truth as `/engines` v2.2, lesson phrasing):** Auto (default, only) / Built-in (TCC) / Bundled Clang / Termux — what each does + when it would be used in fallback chain (W4.2 v2.2 facts, verbatim in scope). Note TCC null on armeabi-v7a/x86, Clang module arm64-only, per-ABI splits reverted because assets/tcc not filtered.
3. **What TCC covers, honestly** — ANSI C + most of C99; learning and everyday code; when C11/C17 feature appears → Bundled Clang (arm64) or Termux (install Clang module or Termux). One real example snippet that TCC does handle (simple struct + function pointers is fine — keep it ANSI C). Mention static musl, native lib dir allowed at any targetSdk, targetSdk 28 deliberate why GitHub not Play.
4. **Where Termux guidance appears now (not Settings)** — Old: Settings → Compiler Engine → CHECK BRIDGE. **New v2.2:** Settings card deleted Phase 38.2 (13→11 sections, Compiler Settings + Built-in Compiler + Termux Engine merged into one Compiler, TermuxUiState etc removed, mechanism untouched). Four setup steps moved to `ui/editor/CompilerRemediation.kt` pure: failed build whose output carries exec Permission denied signature (shell/wrapper wording, exec markers, no error: diagnostic lines) gets SYSTEM remedy line in Output Panel (EditorViewModel.finishFailedBuild next to Phase-33 no-main hint) wording source TROUBLESHOOTING.md §27 CompilerRemediationTest pins both directions. Walk through a simulated failure: what green would have meant, what failure points at (link `/install` §Termux setup).
5. **Know your device v2.2** — arm64 phone: everything best built-in TCC + all engines universal APK 6.6 MB; x86_64 emulator: TCC covers it, arm64 Clang module won't run (no problem); 32-bit: built-in TCC null by design Phase 33.3 typed null, Termux engine or Clang module recommended. Device table = `/install`'s v2.2, one line + link + per-ABI reverted reason.
6. **No switching on purpose anymore — but understand fallback** — Previously Settings → Compiler Engine → pick; now Auto does it. Recompile chapter 02's `hello.c` and confirm output, then deliberately explain what would happen if Android blocked downloaded compiler (W^X, noexec, CPU mismatch) — Output Panel would print four setup steps.

- **Try it:** (1) compile `hello.c` and note that no picker exists anymore — Auto is the only mode; (2) write a 5-line program using a struct + a function, compile with built-in TCC (conceptually), run it; (3) read TROUBLESHOOTING.md §27 and copy the four Termux setup steps onto paper (you won't need them now, but you know where they appear).
- **Mistakes:** expecting Clang module on x86 emulator (it's arm64, TCC covers); thinking Auto "breaks" when it falls back (fallback = success); looking for CHECK BRIDGE in Settings (deleted Phase 38.2, now error-path Output Panel line); looking for Compiler Engine picker (deleted Phase 21); expecting built-in TCC on 32-bit ARM (null by design, use Clang module or Termux).

## 2. Implementation steps

1. Build `ch-04.html` (crumb "Chapter 4 of 17") with v2.2 facts.
2. Table cross-checked with `/engines` page v2.2 (same source — W4.2 v2.2 facts: Auto only, picker deleted, Termux card deleted, fallback automatic, four steps appear in Output Panel, TCC null on armeabi-v7a/x86, universal APK 6.6 MB per-ABI reverted).
3. Source notes in `chat-web4/`; self-dependent sweep.

## 3. Exit condition

```text
1. Template complete; prev → ch-03, next → ch-05.
2. Engine table == /engines page v2.2 == README v2.2 (triple source noted) — Auto only, no picker, Termux card deleted but mechanism stays, four steps appear in Output Panel, TCC null on armeabi-v7a/x86.
3. No instruction to switch engine in Settings or tap CHECK BRIDGE as Settings button — both deleted, now error-path.
4. 360/1440 clean; sweep PASS.
```
