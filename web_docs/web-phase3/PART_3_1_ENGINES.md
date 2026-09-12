# CodeC Website Phase W3.1 — Compiler engines (`/engines`)

**Status:** 📋 **PLANNED** · **Cost:** `[static]` · **Effort:** S
· **Depends on:** W1
· **Target file:** `website/engines.html`

> Source: `README.md` — "Compiler engines (automatic — the picker left in Phase 21)" + surrounding prose; Termux setup section for fallback; `docs/TROUBLESHOOTING.md` §27 (four steps appear in Output Panel); `docs/chat-phase38/PART_38_2_SETTINGS_TRIM.md` (Termux card deleted, guidance moved to CompilerRemediation → finishFailedBuild); `docs/chat-phase21/PART_21_IMPLEMENTATION.md` (picker deleted, Auto only, TCC default, -o-last invariant permanent).
> **v2.2 update:** picker deleted Phase 21, Termux Engine card deleted Phase 38.2, Auto is only mode, CHECK BRIDGE no longer in Settings, four steps appear in Output Panel only when build fails with Permission denied signature.

---

## 1. Design — page structure

1. **H1:** "One button. Auto does everything — here's what it can fall back on."
   One paragraph: tap RUN and **Auto** does everything — built-in TCC first (offline instant, from native library dir allowed at any targetSdk), if unavailable Clang module from Packages, if Android blocks downloaded compiler (Android 10+ W^X policy, noexec storage, CPU mismatch, broken toolchain) automatically compiles through Termux's Clang. **There is nothing to pick** — Settings → Compiler Engine picker and COMPILER_BACKEND preference were deleted in Phase 21 (D22), Termux Engine card deleted in Phase 38.2 (D17) — mechanism untouched, guidance moved to error path. This page explains the chain for learning purposes.

2. **The engine table** (`.table`, conceptual not selectable, mirrors README order v2.2):

   | Engine (conceptual) | What it does (v2.2) |
   |---|---|
   | **Auto (default, only mode)** | Built-in TCC first (offline, instant, static musl, arm64-v8a + x86_64, null on armeabi-v7a/x86); if unavailable, Clang module from Packages (full C11/C17, arm64 only); if Android blocks it (W^X, noexec, CPU mismatch, broken toolchain), automatically compiles through **Termux's Clang** (needs Termux setup, link → `/install` §Termux) — four setup steps appear in Output Panel exactly when needed (TROUBLESHOOTING.md §27) |
   | **Built-in (TCC)** | Only compiler embedded in APK; ANSI C + most C99; learning & everyday code; static executables; works offline instantly |
   | **Bundled Clang** | Only Clang from **Packages** (full C11/C17, stricter warnings); **arm64 only**; x86_64 emulator can't run it — TCC covers x86_64 automatically |
   | **Termux** | Always would compile with Termux's Clang (needs Termux setup, link → `/install`); in Auto it's automatic fallback, not a mode you pick |

3. **Why TCC comes first** — 3 short paragraphs: it's inside APK (no download, no network, no Termux); Android lets it run from native library directory at any targetSdk (W^X problem, targetSdk 28 compatibility mode that Termux uses, linked to FAQ); fully static executables; `tccBinary()` returns null on armeabi-v7a/x86 by design so app never pretends otherwise (Phase 33.3 typed null).
4. **Coverage honesty box:** TCC covers ANSI C + most C99 — if you hit C11/C17 feature, switch conceptually to Bundled Clang (arm64) or Termux (install Clang module or Termux). One real example snippet that TCC does handle (simple struct + function pointers is fine — keep it ANSI C).
5. **No picker anymore — what changed:** Settings → Compiler Engine picker deleted Phase 21, Termux Engine card deleted Phase 38.2 (13→11 sections, Compiler Settings + Built-in Compiler + Termux Engine merged into one Compiler, TermuxUiState/loadTermuxState/buildTermuxStatusText/formatProbe + TermuxCompiler import removed). **Mechanism untouched:** TermuxCompiler, every CompilerService fallback call, RUN_COMMAND permission and <queries> entry all stay. The four setup steps moved to `ui/editor/CompilerRemediation.kt` pure CompilerDiagnostics-style: failed build whose output carries exec "Permission denied" signature (shell/wrapper wording, exec markers, no `error:` diagnostic lines) gets SYSTEM remedy line in Output Panel (EditorViewModel.finishFailedBuild next to Phase-33 no-main hint) wording source TROUBLESHOOTING.md §27 CompilerRemediationTest pins both directions. CHECK BRIDGE no longer in Settings — Output Panel prints steps when actually needed.
6. **Emulator note:** x86_64 emulators can't run arm64 Clang module — TCC covers them automatically; 32-bit → Clang module or Termux engine (both already on `/install`, linked) because built-in TCC null on armeabi-v7a/x86.
7. **Link block:** /install (Termux setup), /faq (W^X, Permission denied, Exec format error, Runtime libraries missing).

### Meta: title "Compiler engines — Auto only, TCC default — CodeC", description mentions Auto only, picker deleted, Termux card deleted but fallback automatic, four steps appear in Output Panel.

## 2. Implementation steps

1. Build the page (active nav: Engines, >_ mark).
2. Transcribe table verbatim in scope from README v2.2 (Auto only); record source lines in `chat-web3/` (README Compiler engines automatic — the picker left in Phase 21 + Phase 38.2 Settings trim + TROUBLESHOOTING §27).
3. Ensure no wording implies a selectable picker or CHECK BRIDGE button in Settings — both deleted, now error-path.
4. Self-dependent sweep (plan §5.5).

## 3. Exit condition

```text
1. Table matches README.md v2.2 row-for-row in scope (Auto only, TCC default, Clang module arm64 only, Termux fallback automatic, 4 steps appear in Output Panel) — diff recorded in chat-web3/; 360/1440 render clean.
2. Page states picker deleted Phase 21 and Termux Engine card deleted Phase 38.2 but mechanism stays; no "switch engine in Settings" instruction; CHECK BRIDGE not described as Settings button but as old path now error-path Output Panel line.
3. Every "how do I" on page links to right page (/install for Termux setup, /faq for W^X, Permission denied).
4. Sweep PASS; no store mention.
5. Facts match v2.2: Auto only, TCC null on armeabi-v7a/x86, universal APK, per-ABI splits reverted reason, Termux fallback automatic.
```
