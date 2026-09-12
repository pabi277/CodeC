# CodeC Website Phase W2.1 — Install guide (`/install`)

**Status:** 📋 **PLANNED** · **Cost:** `[static]` · **Effort:** S
· **Depends on:** W1
· **Target file:** `website/install.html`

> Source: `README.md` — "Install the APK from GitHub" (Phase 42.1 release channel: app-v* tags only, universal APK 6.6 MB signed non-debuggable SHA256 lines, debug artifacts, updater version guard + checksum + refusal, debug→release fresh install), `docs/BETA.md` (B-1…B-8), `docs/RELEASE_NOTES.md` template, `docs/TROUBLESHOOTING.md` §27 (Termux fallback automatic, four steps appear in Output Panel), `docs/PHASE38_43_ROADMAP.md` (42.1/42.2 facts).
> **v2.2 update:** three paths reordered to release-first (owner's share-readiness), universal APK only, per-ABI splits measured <2% and reverted because `assets/tcc/` not filtered, Termux card deleted Phase 38.2 but mechanism stays, guidance moved to error path.

---

## 1. Design — page structure (top to bottom)

1. **H1 + one line:** "Get the CodeC APK — one universal file, 6.6 MB." (no store — GitHub only, D9, D21).
2. **Three paths, README order (v2.2), numbered cards:**
   1. **Release (everyone): GitHub Releases → `app-v*` tag → universal APK** — Open `https://github.com/pabi277/CodeC/releases` → pick newest **CodeC IDE** release (`app-vX.Y.Z` tag) → download **`CodeC-IDE-<version>-universal.apk`** — the ONLY APK, always right one (per-ABI variants measured and reverted Phase 42.2: 0.94–1.77% saving vs 15% law because `assets/tcc/<abi>` rides every split). Release notes carry `sha256:` lines + versionCode + date + changelog (template `docs/RELEASE_NOTES.md` asserted by `scripts/check_release_notes.sh`). Verify with `sha256sum <file>` or let in-app updater check for you. **32-bit ARM note:** universal APK includes native libs for armeabi-v7a but built-in TCC toolchain ships only arm64-v8a/x86_64 (`tccBinary()` null on other ABIs) — C works via Clang module or Termux fallback, everything else same.
   2. **Developer / branch builds:** push a branch (or merge to main) → GitHub Actions builds APKs → Actions → Build APK run → Artifacts → `CodeC-IDE-debug` (debug key, updates only over debug) and `CodeC-IDE-release` (signed with upload key). A release is published only from `app-v*` tag — tag must equal versionName, versionCode must exceed shipped (publish step checks both before attaching).
   3. **In the app (updates): Settings → About → Check for updates** — looks only at `app-v*` releases, compares versions numerically ("up to date" / named refusal when older), verifies `sha256:` line before installing, opens Releases page when no checksum. Never installs `userland-*` bootstrap as app, never phones home. Also note **debug→release signing change = fresh install** — export projects first (Files → Export all, or per-project Export ZIP) and import back afterwards (BETA.md).
3. **First install note:** Android will ask to allow *Install unknown apps* for the browser — allow it once. **Upgrading/downgrading:** install APK over existing — Android updates in place and keeps projects as long as signing key same; first release-signed build does NOT share key with old debug-signed CI builds: switching debug→release is fresh install. Export first. App's own Check for updates never installs older release over newer and says why (no silent downgrade) — UpdatePolicy refusal logic host-test pinned.
4. **Device support table** (`.table`, updated v2.2):

   | Device | Experience |
   |---|---|
   | arm64 phone/tablet | Best — built-in TCC + all engines, universal APK 6.6 MB |
   | x86_64 emulator | Good — built-in TCC covers it; bundled Clang module is arm64-only |
   | 32-bit ARM / x86 | Universal APK runs, but built-in TCC **not bundled** (`tccBinary()` null) — C via Clang module (Packages) or Termux fallback; everything else same |

5. **Optional: the Termux engine (collapsible section, updated v2.2 — Phase 38.2 deleted Settings card)** — When you'd want it: full C11/C17 via Clang, blocked-toolchain fallback (Android 10+ W^X, noexec, CPU mismatch). **Mechanism untouched** (TermuxCompiler fallback + RUN_COMMAND permission + <queries> stay) but **Settings UI removed** — guidance appears in Output Panel exactly when build needs fallback (CompilerRemediation → finishFailedBuild SYSTEM remedy line, wording source TROUBLESHOOTING.md §27). The four setup steps (mirrors README, only F-Droid/GitHub external references allowed by plan §5.2): install Termux 0.109+ from F-Droid https://f-droid.org/packages/com.termux/ or GitHub https://github.com/termux/termux-app/releases (Play Store version outdated), then in Termux: `echo "allow-external-apps=true" >> ~/.termux/termux.properties`, `termux-reload-settings`, `pkg update && pkg install clang`; grant CodeC "Run commands in Termux environment" permission (Android Settings → Apps → CodeC IDE → Permissions → Additional permissions). No Settings card for this anymore — Output Panel prints same four steps when actually needed (TROUBLESHOOTING.md §27).
6. **Footer links block:** Releases page · README · BETA.md · TROUBLESHOOTING §27 · RELEASE_NOTES template · In-app updater recap.

### Meta: title "Install CodeC — universal APK 6.6 MB from GitHub", description from §1 (mention universal, signed, SHA256, updater).

## 2. Implementation steps

1. Build the page in the W1.1 chrome (active nav: Install, >_ mark favicon).
2. Transcribe the three paths + Termux fallback automatic + device table from README + BETA.md + RELEASE_NOTES.md; record source line per section in `chat-web3/` (traceability: README Install the APK from GitHub + Phase 42.1/42.2 facts).
3. Verify universal APK size 6 630 554 B (-74% from 25 553 564) and per-ABI reverted reason (assets/tcc not filtered) noted in chat-web3/.
4. Self-dependent sweep (plan §5.5); verify two external README links (F-Droid + GitHub Termux) resolve 200.

## 3. Exit condition

```text
1. Render at 360/1440; numbered paths (release universal + debug artifacts + in-app updater), device table (arm64 best, x86_64 good, 32-bit TCC null), Termux fallback automatic section (no Settings card, error-path guidance) all present in order.
2. Every command block matches README wording exactly (universal APK name, SHA256 verification, version guard, debug→release fresh install warning, four Termux setup steps) — diff in chat-web3/.
3. A reader with fresh arm64 phone can follow page to downloaded APK without opening any other site (manual trace in chat-web3/), and understands debug vs release and export-all before switching.
4. No store mention anywhere on page; sweep PASS; external links only two Termux links allowed by §5.2.
5. Facts match v2.2: universal APK only, per-ABI splits reverted <2% reason recorded, Termux card deleted but mechanism stays, updater checks SHA256 and refuses downgrade.
```
